package com.qian.qianaiagent.agent.llm;

import com.qian.qianaiagent.config.LlmResilienceProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 给 LLM 调用套上韧性链的装饰器，实现「主模型 → 备模型 → 兜底话术」三级降级。
 *
 * <h2>两条链相互独立</h2>
 * 主模型与备模型各持一条 {@link ResilienceChain}，熔断器与重试计数互不干扰。
 * 若共用一条，主模型的故障计数会污染备模型 —— 表现为「刚切到备模型就被熔断」。
 *
 * <h2>全挂时为什么不抛异常</h2>
 * 返回兜底话术而非抛异常，是为了保持与 {@code QuizApp.java:584} 现有
 * {@code onErrorResume} 行为一致、前端契约不变。
 * <b>代价是调用方无法感知失败，日志成为唯一线索</b>，因此此处必须记 ERROR 级。
 *
 * <h2>流式路径</h2>
 * {@link #stream(Prompt)} 不做重试（流已经吐了一半再重试会造成内容重复），
 * 只在<b>建立流之前</b>的失败上降级。建立之后的错误由调用方既有的
 * {@code onErrorResume} 处理。
 */
@Slf4j
public class ResilientChatModel implements ChatModel {

    private final ChatModel primary;
    private final ChatModel fallback;
    private final ResilienceChain primaryChain;
    private final ResilienceChain fallbackChain;
    private final String unavailableReply;

    /**
     * @param primary          主模型
     * @param fallback         备模型，可为 null（表示无备选，直接走兜底）
     * @param props            韧性参数
     * @param unavailableReply 主备全挂时返回的话术
     */
    public ResilientChatModel(ChatModel primary, ChatModel fallback,
                              LlmResilienceProperties props, String unavailableReply) {
        if (primary == null) {
            throw new IllegalArgumentException("primary 不能为 null");
        }
        if (unavailableReply == null || unavailableReply.isBlank()) {
            throw new IllegalArgumentException("unavailableReply 不能为空白");
        }
        this.primary = primary;
        this.fallback = fallback;
        this.primaryChain = new ResilienceChain("primary", props);
        this.fallbackChain = fallback == null ? null : new ResilienceChain("fallback", props);
        this.unavailableReply = unavailableReply;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            return primaryChain.execute(() -> primary.call(prompt));
        } catch (Exception primaryEx) {
            log.warn("⚠️ 主模型调用失败，准备降级: err={}", primaryEx.toString());
        }

        if (fallback != null) {
            try {
                ChatResponse response = fallbackChain.execute(() -> fallback.call(prompt));
                log.info("✅ 已降级至备模型并成功返回");
                return response;
            } catch (Exception fallbackEx) {
                log.error("❌ 备模型也失败，返回兜底话术: err={}", fallbackEx.toString());
            }
        }

        return unavailableResponse();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // 先在主模型上尝试「建立流」；建立失败才降级。
        // 已建立的流中途出错不重试 —— 会导致已推送内容重复。
        try {
            return primary.stream(prompt);
        } catch (Exception primaryEx) {
            log.warn("⚠️ 主模型建流失败，准备降级: err={}", primaryEx.toString());
        }

        if (fallback != null) {
            try {
                return fallback.stream(prompt);
            } catch (Exception fallbackEx) {
                log.error("❌ 备模型建流也失败: err={}", fallbackEx.toString());
            }
        }

        return Flux.just(unavailableResponse()).subscribeOn(Schedulers.boundedElastic());
    }

    /** 供健康检查/验收观察主链熔断状态。 */
    public boolean isPrimaryCircuitOpen() {
        return primaryChain.isCircuitOpen();
    }

    private ChatResponse unavailableResponse() {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(unavailableReply))));
    }

    @PreDestroy
    public void shutdown() {
        primaryChain.shutdown();
        if (fallbackChain != null) {
            fallbackChain.shutdown();
        }
    }
}
