package com.qian.qianaiagent.advisor;

import com.qian.qianaiagent.advisor.guardrail.GuardrailVerdict;
import com.qian.qianaiagent.advisor.guardrail.OutputRuleSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

/**
 * 输出侧护栏 Advisor —— 检查模型返回内容，命中则替换为兜底话术。
 *
 * <h2>为什么放在链的最内层</h2>
 * 内层 advisor 的后置逻辑最先拿到模型原始输出。现有链中
 * {@code MyLoggerAdvisor} 的 order 是 0，本类用
 * {@link Integer#MAX_VALUE} 确保排在最内、看到未经其他 advisor 改写的响应。
 *
 * <h2>⚠️ 流式路径是尽力而为，不是强保证</h2>
 * 流式下响应是逐块到达的。本实现<b>不缓冲整个流</b>（缓冲会摧毁流式体验，
 * 而流式是本项目的核心交互）。因此命中泄漏时，<b>已经推送给前端的块无法收回</b>，
 * 本类只能在剩余流上截断。
 *
 * <p>这是刻意的取舍，不是遗漏。要获得强保证，只能改为全量缓冲后统一检测。
 */
@Slf4j
public class OutputGuardrailAdvisor implements CallAdvisor, StreamAdvisor {

    /** 排在 {@code MyLoggerAdvisor}(0) 之内侧，即链的最内层。 */
    private static final int ORDER = Integer.MAX_VALUE;

    /**
     * 滑动窗口大小。必须大于最长待检测片段的长度：
     * PII 正则最长约 20 字符，提示词片段最长约 15 字，64 有充分余量。
     */
    private static final int TAIL_WINDOW = 64;

    private final OutputRuleSet ruleSet;
    private final String fallbackReply;

    /**
     * @param ruleSet       输出规则集，不可为 null
     * @param fallbackReply 命中后替换的固定话术，不可为空白
     */
    public OutputGuardrailAdvisor(OutputRuleSet ruleSet, String fallbackReply) {
        if (ruleSet == null) {
            throw new IllegalArgumentException("ruleSet 不能为 null");
        }
        if (fallbackReply == null || fallbackReply.isBlank()) {
            throw new IllegalArgumentException("fallbackReply 不能为空白");
        }
        this.ruleSet = ruleSet;
        this.fallbackReply = fallbackReply;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        // 下游异常路径可能给出 null 或空 chatResponse —— 此时无从检测，直接放行
        if (response == null || response.chatResponse() == null) {
            return response;
        }
        String text = textOf(response);
        return ruleSet.check(text)
                .map(v -> {
                    log.warn("🛡️ 输出护栏拦截: rule={}, reason={}, outputLen={}",
                            v.ruleName(), v.reason(), text == null ? 0 : text.length());
                    return replaceWithFallback(response);
                })
                .orElse(response);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        // Flux.defer：每次订阅各自持有独立的窗口缓冲，避免多个流互相污染
        return Flux.defer(() -> {
            StringBuilder tail = new StringBuilder();
            boolean[] truncated = {false};

            return chain.nextStream(request)
                    .handle((response, sink) -> {
                        if (truncated[0]) {
                            return;   // 已截断，丢弃剩余所有块
                        }
                        String text = textOf(response);
                        if (text != null && !text.isEmpty()) {
                            // 滑动窗口：拼接「上一块的尾部」与「本块」。
                            // 逐块单独检测会漏掉跨块边界的匹配（如手机号被切成两半），
                            // 拼接后检测才能捕获。
                            String window = tail + text;
                            Optional<GuardrailVerdict> verdict = ruleSet.check(window);
                            if (verdict.isPresent()) {
                                log.warn("🛡️ 流式输出护栏命中，已截断剩余流: rule={}",
                                        verdict.get().ruleName());
                                truncated[0] = true;
                                sink.next(replaceWithFallback(response));
                                sink.complete();
                                return;
                            }
                            // 滚动窗口：只保留尾部 TAIL_WINDOW 个字符，内存不随响应长度增长
                            tail.setLength(0);
                            tail.append(window.length() > TAIL_WINDOW
                                    ? window.substring(window.length() - TAIL_WINDOW)
                                    : window);
                        }
                        sink.next(response);
                    });
        });
    }

    /** 取响应文本；取不到返回 null。 */
    private String textOf(ChatClientResponse response) {
        if (response.chatResponse() == null || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null) {
            return null;
        }
        return response.chatResponse().getResult().getOutput().getText();
    }

    /** 用兜底话术替换响应内容，保留原 context。 */
    private ChatClientResponse replaceWithFallback(ChatClientResponse original) {
        ChatResponse replaced = new ChatResponse(
                List.of(new Generation(new AssistantMessage(fallbackReply))));
        return ChatClientResponse.builder()
                .chatResponse(replaced)
                .context(original.context())
                .build();
    }
}
