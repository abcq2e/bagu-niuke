package com.qian.qianaiagent.controller;

import com.qian.qianaiagent.agent.YuManus;
import com.qian.qianaiagent.annotation.RateLimit;
import com.qian.qianaiagent.app.QuizApp;
import com.qian.qianaiagent.chatmemory.FileBasedChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 对话控制器
 * <p>
 * 双模式架构：
 * - 智能对话模式（/ai/chat）：RAG 知识库 + 联网搜索 + 多轮记忆，适合快速问答
 * - Agent 自主规划模式（/ai/agent/chat）：ReAct 循环 + 工具调用，适合复杂多步任务
 */
@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {

    @Resource
    private QuizApp quizApp;

    /** 每次请求获取新的 Agent 实例（Prototype Scope），避免多请求间状态冲突 */
    @Resource
    private ObjectProvider<YuManus> yuManusProvider;

    /** 完整对话历史（管理接口：列表/导出/删除/重命名） */
    @Resource(name = "fileBasedChatMemory")
    private FileBasedChatMemory fileBasedChatMemory;

    /** Agent 多轮对话记忆（滑动窗口 + 摘要压缩） */
    @Resource(name = "chatMemory")
    private ChatMemory agentChatMemory;

    /** 消息最大长度 */
    private static final int MAX_MESSAGE_LENGTH = 2000;

    /**
     * 💬 智能对话（面试官模式） SSE 流式
     * <p>
     * 适用于**技术面试考察**场景，面试官人设 + RAG 知识库 + 联网搜索。
     * <p>
     * 合并了原知识考察和超级智能体的全部能力：
     * - 向量知识库 RAG 检索（Java、Spring、数据结构等）
     * - 工具调用（搜索、文件读写、PDF 生成等）
     * - 多轮对话记忆
     *
     * @param message 用户消息
     * @param chatId  会话 ID（可选，不传则自动生成）
     * @return SSE 流式响应
     */
    @RateLimit(maxRequests = 10, timeWindow = 60)
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChat(@RequestParam String message,
                                @RequestParam(required = false) String chatId) {
        // 输入校验
        if (message == null || message.isBlank()) {
            return Flux.just("[ERROR] 消息不能为空", "[DONE]");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return Flux.just("[ERROR] 消息过长，最多 " + MAX_MESSAGE_LENGTH + " 字", "[DONE]");
        }
        final String finalChatId = (chatId == null || chatId.isBlank())
                ? "chat_" + System.currentTimeMillis()
                : chatId;
        log.info("📨 收到对话请求: message={}, chatId={}", message, finalChatId);
        return quizApp.doUnifiedChat(message, finalChatId)
                .doOnError(e -> log.error("❌ SSE 流异常: {}", e.getMessage(), e))
                .doOnComplete(() -> log.info("✅ SSE 流完成: chatId={}",
                        finalChatId));
    }

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
     * <p>
     * 窗口策略：{@link com.qian.qianaiagent.chatmemory.SummarizingChatMemory}
     * 保留最近 40 条消息（约 20 轮），超出部分自动调用 LLM 压缩为摘要。
     *
     * @param message 用户消息
     * @param chatId  会话 ID（可选，不传则自动生成新会话）
     * @return SSE 流式响应（每步执行结果作为独立事件推送）
     */
    @RateLimit(maxRequests = 10, timeWindow = 60)
    @GetMapping(value = "/agent/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doAgentChat(@RequestParam String message,
                                    @RequestParam(required = false) String chatId) {
        if (message == null || message.isBlank()) {
            return Flux.just("[ERROR] 消息不能为空", "[DONE]");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return Flux.just("[ERROR] 消息过长，最多 " + MAX_MESSAGE_LENGTH + " 字", "[DONE]");
        }
        // 生成或复用 chatId
        final String finalChatId = (chatId == null || chatId.isBlank())
                ? "agent_" + System.currentTimeMillis()
                : chatId;
        log.info("📨 收到 Agent 对话请求: message={}, chatId={}", message, finalChatId);

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
        return agent.runStreamAsFlux(message)
                .doFinally(signalType -> {
                    // 保存本轮新消息（排除预加载的历史，避免重复存储）
                    List<Message> fullList = agent.getMessageList();
                    if (fullList.size() > savedHistorySize) {
                        List<Message> newMessages = new ArrayList<>(
                                fullList.subList(savedHistorySize, fullList.size()));
                        agentChatMemory.add(finalChatId, newMessages);
                        log.info("💾 保存 Agent 对话: chatId={}, newMessages={}, signal={}",
                                finalChatId, newMessages.size(), signalType);
                    }
                })
                .doOnError(e -> log.error("❌ Agent SSE 流异常: chatId={}, error={}",
                        finalChatId, e.getMessage(), e))
                .doOnComplete(() -> log.info("✅ Agent SSE 流完成: chatId={}", finalChatId));
    }

    /**
     * 获取所有会话列表
     * <p>
     * 返回每个会话的 chatId、标题、最后修改时间和消息数，
     * 按最后修改时间倒序排列。
     */
    @GetMapping("/conversations")
    public List<FileBasedChatMemory.ConversationInfo> listConversations() {
        return fileBasedChatMemory.listConversations();
    }

    /**
     * 获取指定会话的历史消息
     * <p>
     * 返回该会话的完整消息列表，用于前端恢复对话上下文。
     */
    @GetMapping("/conversations/{chatId}")
    public List<Map<String, Object>> getConversationMessages(@PathVariable String chatId) {
        return fileBasedChatMemory.getConversation(chatId)
                .stream()
                .map(msg -> Map.of(
                        "messageType", (Object) msg.getMessageType().name(),
                        "content", (Object) msg.getText()
                ))
                .toList();
    }

    /**
     * 删除指定会话（永久删除）
     */
    @DeleteMapping("/conversations/{chatId}")
    public Map<String, Object> deleteConversation(@PathVariable String chatId) {
        // 🔴 防路径遍历攻击
        if (chatId == null || chatId.contains("..") || chatId.contains("/") || chatId.contains("\\")) {
            return Map.of("success", false, "message", "无效的会话 ID");
        }
        boolean deleted = fileBasedChatMemory.deleteConversation(chatId);
        return Map.of("success", deleted, "chatId", chatId);
    }

    /**
     * 重命名会话标题
     */
    @PutMapping("/conversations/{chatId}/title")
    public Map<String, Object> renameConversation(@PathVariable String chatId,
                                                   @RequestParam String title) {
        if (chatId == null || chatId.contains("..") || chatId.contains("/") || chatId.contains("\\")) {
            return Map.of("success", false, "message", "无效的会话 ID");
        }
        if (title == null || title.isBlank() || title.length() > 30) {
            return Map.of("success", false, "message", "标题长度需要在 1-30 字之间");
        }
        fileBasedChatMemory.updateTitle(chatId, title.trim());
        return Map.of("success", true, "chatId", chatId, "title", title.trim());
    }

    // ===== 以下为保留的旧接口，向后兼容 =====

    /**
     * @deprecated 使用 /ai/chat 替代
     */
    @GetMapping(value = "/quiz/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doQuizStream(String message, String chatId) {
        return quizApp.doChatByStream(message, chatId);
    }
}
