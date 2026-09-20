package com.qian.qianaiagent.controller;

import com.qian.qianaiagent.agent.YuManus;
import com.qian.qianaiagent.annotation.RateLimit;
import com.qian.qianaiagent.context.UserContext;
import com.qian.qianaiagent.evaluation.EvaluationRecorder;
import com.qian.qianaiagent.memory.ConversationAccess;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 自主规划对话控制器（通用智能体）。
 * <p>
 * 从原 {@code AiController} 拆出，URL 保持 {@code /ai/agent/chat} 不变。
 */
@RestController
@RequestMapping("/ai")
@Slf4j
public class AgentChatController {

    /** 每次请求获取新的 Agent 实例（Prototype Scope），避免多请求间状态冲突 */
    @Resource
    private ObjectProvider<YuManus> yuManusProvider;

    /** 会话归属守卫 */
    @Resource
    private ConversationAccess conversationAccess;

    /** Agent 多轮对话记忆（滑动窗口 + 摘要压缩） */
    @Resource(name = "chatMemory")
    private ChatMemory agentChatMemory;

    /** 回答结束后的异步评估记录器（Agent/RAG 质量监控，落盘 data/evals） */
    @Resource
    private EvaluationRecorder evaluationRecorder;

    /**
     * 🤖 Agent 自主规划对话（通用智能体） SSE 流式 + 多轮记忆
     * <p>
     * 适用于**编程辅助、信息查询、自动化任务**等通用场景，无面试官人设。
     * <p>
     * 记忆架构采用 OpenAI Threads 模式：
     * <ol>
     *   <li>从 {@link ChatMemory} 加载历史消息（滑动窗口 + 摘要压缩）</li>
     *   <li>预加载到 Agent 的 messageList，作为推理上下文</li>
     *   <li>Agent 执行 ReAct 循环（思考 → 行动 → 观察 → ...）</li>
     *   <li>流式推送每一步结果</li>
     *   <li>完成后将本轮新消息持久化到文件</li>
     * </ol>
     *
     * @param message 用户消息
     * @param chatId  会话 ID（可选，不传则自动生成新会话）
     * @return SSE 流式响应（每步执行结果作为独立事件推送）
     */
    // ⚠️ 该注解当前不生效，见 RateLimit javadoc（Task 13 练习未完成）
    @RateLimit(maxRequests = 10, timeWindow = 60)
    @GetMapping(value = "/agent/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter doAgentChat(@RequestParam String message,
                                  @RequestParam(required = false) String chatId) {
        // 输入校验：失败时立即返回错误 SSE 响应
        if (message == null || message.isBlank()) {
            return buildErrorEmitter("消息不能为空");
        }
        if (message.length() > AiChatConstants.MAX_MESSAGE_LENGTH) {
            return buildErrorEmitter("消息过长，最多 " + AiChatConstants.MAX_MESSAGE_LENGTH + " 字");
        }
        // 生成或复用 chatId
        final String finalChatId = (chatId == null || chatId.isBlank())
                ? "agent_" + System.currentTimeMillis()
                : chatId;
        // 🔴 在进入异步流之前捕获 userId：ThreadLocal 在 SseEmitter 的回调线程上不可用，
        // 且归属校验必须在这里做（响应式线程里已无用户身份）
        final Long userId = UserContext.getCurrentUserId();
        if (!conversationAccess.startSession(finalChatId, userId)) {
            log.warn("🚫 拒绝接入他人 Agent 会话: chatId={}, userId={}", finalChatId, userId);
            return buildErrorEmitter("无权访问该会话");
        }
        log.info("📨 收到 Agent 对话请求: message={}, chatId={}, userId={}", message, finalChatId, userId);

        // 获取新的 Agent 实例
        YuManus agent = yuManusProvider.getObject();
        // 预加载对话历史到 Agent 的记忆中
        List<Message> history = agentChatMemory.get(finalChatId);
        int historySize = 0;
        if (!history.isEmpty()) {
            agent.getMessageList().addAll(history);
            historySize = history.size();
            log.info("📂 加载 Agent 历史消息: chatId={}, count={}", finalChatId, historySize);
        }
        final int savedHistorySize = historySize;
        SseEmitter emitter = agent.runStream(message);
        // 流结束（正常完成 / 异常完成）后保存本轮新消息
        emitter.onCompletion(() -> {
            saveAgentMessages(agent, finalChatId, savedHistorySize, "completed");
            log.info("✅ Agent SSE 流完成: chatId={}", finalChatId);
            // 🔴 [评估接入] 回答完整产出后（trace 已在异步线程内封口）异步做 Rubric 评分并落盘。
            // trace 未封口（超时/异常路径）会在 Recorder 内静默跳过，不在此补跑。
            evaluationRecorder.submitAgentEval(finalChatId, message, agent.getCurrentTrace());
        });
        // 超时兜底保存
        emitter.onTimeout(() -> {
            saveAgentMessages(agent, finalChatId, savedHistorySize, "timeout");
            log.warn("⏱ Agent SSE 流超时: chatId={}", finalChatId);
        });
        return emitter;
    }

    /**
     * 构造一个立即返回错误信息的 SSE 响应（输入校验失败时使用）
     */
    private SseEmitter buildErrorEmitter(String msg) {
        SseEmitter emitter = new SseEmitter(300000L);
        try {
            emitter.send("[ERROR] " + msg);
            emitter.send("[DONE]");
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * 保存本轮 Agent 对话新增消息到记忆（排除预加载的历史，避免重复存储）
     */
    private void saveAgentMessages(YuManus agent, String chatId, int savedHistorySize, String signal) {
        List<Message> fullList = agent.getMessageList();
        if (fullList.size() > savedHistorySize) {
            List<Message> newMessages = new ArrayList<>(fullList.subList(savedHistorySize, fullList.size()));
            agentChatMemory.add(chatId, newMessages);
            log.info("💾 保存 Agent 对话: chatId={}, newMessages={}, signal={}",
                    chatId, newMessages.size(), signal);
        }
    }
}
