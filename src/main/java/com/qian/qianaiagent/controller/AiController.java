package com.qian.qianaiagent.controller;

import com.qian.qianaiagent.agent.YuManus;
import com.qian.qianaiagent.annotation.RateLimit;
import com.qian.qianaiagent.interview.QuizApp;
import com.qian.qianaiagent.ability.UserAbilityProfile;
import com.qian.qianaiagent.ability.UserAbilityService;
import com.qian.qianaiagent.interview.review.WrongQuestionReviewService;
import com.qian.qianaiagent.memory.FileBasedChatMemory;
import com.qian.qianaiagent.context.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.qian.qianaiagent.memory.SummarizingChatMemory;

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

    @Resource
    private UserAbilityService userAbilityService;

    @Resource
    private WrongQuestionReviewService wrongQuestionReviewService;

    /** 每次请求获取新的 Agent 实例（Prototype Scope），避免多请求间状态冲突 */
    @Resource
    private ObjectProvider<YuManus> yuManusProvider;

    /** 完整对话历史（管理接口：列表/导出/删除/重命名） */
    @Resource(name = "fileBasedChatMemory")
    private FileBasedChatMemory fileBasedChatMemory;

    /** Agent 多轮对话记忆（滑动窗口 + 摘要压缩） */
    @Resource(name = "chatMemory")
    private ChatMemory agentChatMemory;

    @Resource(name = "taskExecutor")
    private Executor taskExecutor;

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
        // 🔴 在进入响应式流之前捕获 userId（ThreadLocal 在异步线程中不可用）
        final Long userId = UserContext.getCurrentUserId();
        log.info("📨 收到对话请求: message={}, chatId={}, userId={}", message, finalChatId, userId);
        // 🔴 [P2] 使用 taskExecutor 线程池处理 SSE 流（替代 boundedElastic，线程参数可控）
        return quizApp.doUnifiedChat(message, finalChatId, userId)
                .concatWith(Flux.just("[DONE]"))
                .subscribeOn(Schedulers.fromExecutor(taskExecutor))
                .doOnError(e -> log.error("❌ SSE 流异常: {}", e.getMessage(), e))
                .doOnComplete(() -> {
                    // 🔴 流结束后同步保存画像（含 userId 冗余，跨会话恢复用）
                    userAbilityService.saveProfile(finalChatId, userId);
                    log.info("✅ SSE 流完成: chatId={}", finalChatId);
                });
    }

    /**
     * 🔴 错题复习对话 SSE 流式。
     * <p>
     * chatId 格式：review_{原始面试chatId}。后端通过 chatId 前缀识别复习会话，
     * 用 sourceChatId 参数读取原始面试的能力画像。
     *
     * @param message      用户消息
     * @param chatId       复习会话 ID（review_xxx）
     * @param sourceChatId 原始面试 chatId（读画像用）
     */
    @RateLimit(maxRequests = 10, timeWindow = 60)
    @GetMapping(value = "/review/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doReviewChat(@RequestParam String message,
                                     @RequestParam(required = false) String chatId,
                                     @RequestParam(required = false) String sourceChatId) {
        if (message == null || message.isBlank()) {
            return Flux.just("[ERROR] 消息不能为空", "[DONE]");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return Flux.just("[ERROR] 消息过长，最多 " + MAX_MESSAGE_LENGTH + " 字", "[DONE]");
        }
        final String finalChatId = (chatId == null || chatId.isBlank())
                ? "review_" + System.currentTimeMillis()
                : chatId;
        // sourceChatId 若未传，尝试从 chatId 推导（去掉 review_ 前缀）
        final String finalSourceId = (sourceChatId != null && !sourceChatId.isBlank())
                ? sourceChatId
                : (finalChatId.startsWith("review_") ? finalChatId.substring(7) : finalChatId);
        final Long userId = UserContext.getCurrentUserId();
        log.info("📨 收到复习对话请求: message={}, chatId={}, sourceChatId={}",
                message, finalChatId, finalSourceId);
        return wrongQuestionReviewService.doReviewChat(message, finalChatId, finalSourceId, userId)
                .concatWith(Flux.just("[DONE]"))
                .subscribeOn(Schedulers.fromExecutor(taskExecutor))
                .doOnComplete(() -> {
                    userAbilityService.saveProfile(finalSourceId, userId);
                    log.info("✅ 复习 SSE 流完成: chatId={}", finalChatId);
                })
                .doOnError(e -> log.error("❌ 复习 SSE 流异常: {}", e.getMessage(), e));
    }

    /**
     * 🔴 获取错题池预览（方向列表 + 各方向题数）。
     */
    @GetMapping("/review/pool/{sourceChatId}")
    public java.util.Map<String, Object> getReviewPool(@PathVariable String sourceChatId) {
        return wrongQuestionReviewService.getPoolPreview(sourceChatId);
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
     * 窗口策略：{@link com.qian.qianaiagent.memory.SummarizingChatMemory}
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

    // ===== 个性化画像接口 =====

    /**
     * 获取用户能力画像（雷达图数据）— 返回展示清洗后的副本
     * <p>
     * 清洗规则：过滤评价词（"态度敷衍""概念混淆"等）、跨方向词，
     * 确保前端展示的薄弱项均为具体可学习的技术知识点。
     * 不修改历史 .ability-profiles/*.json 文件。
     */
    @GetMapping("/quiz/profile/{chatId}")
    public UserAbilityProfile getProfile(@PathVariable String chatId) {
        return userAbilityService.getDisplayProfile(chatId, UserContext.getCurrentUserId());
    }

    /**
     * LLM 生成文字版考察总结
     */
    @GetMapping("/quiz/profile/{chatId}/summary")
    public java.util.Map<String, String> getProfileSummary(@PathVariable String chatId) {
        String summary = userAbilityService.buildSummary(chatId);
        String aiSuggestion = "";
        try {
            // 调用 LLM 生成学习建议
            aiSuggestion = userAbilityService.generateAISuggestion(chatId);
        } catch (Exception e) {
            aiSuggestion = "基于您的考察数据生成个性化建议失败，请稍后重试。";
            log.warn("生成 AI 建议失败: {}", e.getMessage());
        }
        return java.util.Map.of(
                "summary", summary,
                "aiSuggestion", aiSuggestion
        );
    }

    /**
     * 强制清理跨方向弱点评（立即执行，不依赖自动清理）
     */
    @PostMapping("/quiz/profile/{chatId}/cleanup")
    public java.util.Map<String, Object> cleanupProfile(@PathVariable String chatId) {
        Long userId = UserContext.getCurrentUserId();
        UserAbilityProfile profile = userAbilityService.getOrCreateProfile(chatId, userId);
        int moved = userAbilityService.cleanCrossTopicWeakPoints(profile);
        userAbilityService.saveProfile(chatId, userId);
        return java.util.Map.of("success", true, "chatId", chatId, "moved", moved);
    }

    /**
     * 🔴 [调试] 测试弱点评路由结果（不修改数据）
     */
    @PostMapping("/debug/route")
    public java.util.Map<String, Object> debugRoute(
            @RequestParam String topic,
            @RequestBody java.util.List<String> weakPoints) {
        java.util.Map<String, java.util.List<String>> result =
                userAbilityService.routeWeakPointsPublic(weakPoints, topic);
        return java.util.Map.of("topic", topic, "routed", result);
    }

    /**
     * 重置画像（开始新一轮面试）
     */
    @PostMapping("/quiz/profile/{chatId}/reset")
    public java.util.Map<String, Object> resetProfile(@PathVariable String chatId) {
        Long userId = UserContext.getCurrentUserId();
        userAbilityService.resetProfile(chatId, userId);
        return java.util.Map.of("success", true, "chatId", chatId);
    }

}
