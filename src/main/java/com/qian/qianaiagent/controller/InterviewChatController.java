package com.qian.qianaiagent.controller;

import com.qian.qianaiagent.ability.UserAbilityService;
import com.qian.qianaiagent.annotation.RateLimit;
import com.qian.qianaiagent.context.UserContext;
import com.qian.qianaiagent.interview.QuizApp;
import com.qian.qianaiagent.interview.review.WrongQuestionReviewService;
import com.qian.qianaiagent.memory.ConversationAccess;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 面试对话与复盘控制器（智能对话 / 错题复习 / 错题池预览）。
 * <p>
 * 从原 {@code AiController} 拆出，URL 保持 {@code /ai/...} 不变。
 */
@RestController
@RequestMapping("/ai")
@Slf4j
public class InterviewChatController {

    @Resource
    private QuizApp quizApp;

    @Resource
    private WrongQuestionReviewService wrongQuestionReviewService;

    @Resource
    private UserAbilityService userAbilityService;

    /** 会话归属守卫 —— 写侧越权只能在这里挡（响应式线程里 UserContext 已失效） */
    @Resource
    private ConversationAccess conversationAccess;

    @Resource(name = "taskExecutor")
    private Executor taskExecutor;

    /**
     * 💬 智能对话（面试官模式） SSE 流式
     * <p>
     * 适用于**技术面试考察**场景，面试官人设 + RAG 知识库 + 联网搜索。
     *
     * @param message 用户消息
     * @param chatId  会话 ID（可选，不传则自动生成）
     * @return SSE 流式响应
     */
    // ⚠️ 该注解当前不生效，见 RateLimit javadoc（Task 13 练习未完成）
    @RateLimit(maxRequests = 10, timeWindow = 60)
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChat(@RequestParam String message,
                               @RequestParam(required = false) String chatId) {
        // 输入校验
        if (message == null || message.isBlank()) {
            return Flux.just("[ERROR] 消息不能为空", "[DONE]");
        }
        if (message.length() > AiChatConstants.MAX_MESSAGE_LENGTH) {
            return Flux.just("[ERROR] 消息过长，最多 " + AiChatConstants.MAX_MESSAGE_LENGTH + " 字", "[DONE]");
        }
        final String finalChatId = (chatId == null || chatId.isBlank())
                ? "chat_" + System.currentTimeMillis()
                : chatId;
        // 🔴 在进入响应式流之前捕获 userId（ThreadLocal 在异步线程中不可用）
        final Long userId = UserContext.getCurrentUserId();
        // 🔴 归属校验必须在这里做：流一旦进入 taskExecutor，UserContext 已被 JwtAuthFilter 清掉，
        // 之后就再也没有用户身份可用（详见 FileBasedChatMemory 类注释的残留风险）
        if (!conversationAccess.startSession(finalChatId, userId)) {
            log.warn("🚫 拒绝接入他人会话: chatId={}, userId={}", finalChatId, userId);
            return Flux.just("[ERROR] 无权访问该会话", "[DONE]");
        }
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
    // ⚠️ 该注解当前不生效，见 RateLimit javadoc（Task 13 练习未完成）
    @RateLimit(maxRequests = 10, timeWindow = 60)
    @GetMapping(value = "/review/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doReviewChat(@RequestParam String message,
                                     @RequestParam(required = false) String chatId,
                                     @RequestParam(required = false) String sourceChatId) {
        if (message == null || message.isBlank()) {
            return Flux.just("[ERROR] 消息不能为空", "[DONE]");
        }
        if (message.length() > AiChatConstants.MAX_MESSAGE_LENGTH) {
            return Flux.just("[ERROR] 消息过长，最多 " + AiChatConstants.MAX_MESSAGE_LENGTH + " 字", "[DONE]");
        }
        final String finalChatId = (chatId == null || chatId.isBlank())
                ? "review_" + System.currentTimeMillis()
                : chatId;
        // sourceChatId 若未传，尝试从 chatId 推导（去掉 review_ 前缀）
        final String finalSourceId = (sourceChatId != null && !sourceChatId.isBlank())
                ? sourceChatId
                : (finalChatId.startsWith("review_") ? finalChatId.substring(7) : finalChatId);
        final Long userId = UserContext.getCurrentUserId();
        // 🔴 复习会话本身要认领；sourceChatId 只用来读原始面试的画像。
        // 这里用 open 而非 manage：sourceChatId 可能是从前端 review_ 前缀推导出来的
        // （页面刷新后 reviewSourceChatId 这个 ref 会丢失），并非真实会话，严格判定会误伤该功能。
        if (!conversationAccess.startSession(finalChatId, userId)
                || !conversationAccess.open(finalSourceId, userId)) {
            log.warn("🚫 拒绝接入他人复习会话: chatId={}, sourceChatId={}, userId={}",
                    finalChatId, finalSourceId, userId);
            return Flux.just("[ERROR] 无权访问该会话", "[DONE]");
        }
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
    public Map<String, Object> getReviewPool(@PathVariable String sourceChatId) {
        Long userId = UserContext.getCurrentUserId();
        if (!conversationAccess.open(sourceChatId, userId)) {
            log.warn("🚫 拒绝访问他人错题池: sourceChatId={}, userId={}", sourceChatId, userId);
            return Map.of("topics", List.of(), "totalQuestions", 0);
        }
        return wrongQuestionReviewService.getPoolPreview(sourceChatId);
    }
}
