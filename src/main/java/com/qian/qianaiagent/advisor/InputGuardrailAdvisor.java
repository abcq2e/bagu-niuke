package com.qian.qianaiagent.advisor;

import com.qian.qianaiagent.advisor.guardrail.GuardrailVerdict;
import com.qian.qianaiagent.advisor.guardrail.InputRuleSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

/**
 * 输入侧护栏 Advisor —— 在请求到达模型前拦截 prompt 注入。
 *
 * <h2>为什么放在链的最外层</h2>
 * Advisor 链按 order 升序执行，值最小者最外层、最先拿到请求。
 * 现有链中 {@code MessageChatMemoryAdvisor} 的 order 是 -2147482648
 * （{@code Ordered.HIGHEST_PRECEDENCE + 1000}，由反编译 spring-ai-client-chat 1.0.0 所得），
 * 因此本类用 {@link Integer#MIN_VALUE} 确保排在它之外。
 *
 * <p>最外层还带来一个副作用：<b>命中拦截时，被拦的消息不会写入对话记忆</b>
 * （记忆 Advisor 在内层，根本没执行）。这是刻意的 —— 注入内容不该进入历史，
 * 否则下一轮模型会看到它，反而成为后续越狱的上下文。
 *
 * <h2>为什么命中后不调 LLM 而是直接返回</h2>
 * 省 token，且被污染的内容不会进入模型。代价是这种行为对调用方是隐式的。
 */
@Slf4j
public class InputGuardrailAdvisor implements CallAdvisor, StreamAdvisor {

    /** 排在 {@code MessageChatMemoryAdvisor}(-2147482648) 之外，即链的最外层。 */
    private static final int ORDER = Integer.MIN_VALUE;

    private final InputRuleSet ruleSet;
    private final String blockedReply;

    /**
     * @param ruleSet      输入规则集，不可为 null
     * @param blockedReply 命中后返回给用户的固定话术，不可为空白
     */
    public InputGuardrailAdvisor(InputRuleSet ruleSet, String blockedReply) {
        if (ruleSet == null) {
            throw new IllegalArgumentException("ruleSet 不能为 null");
        }
        if (blockedReply == null || blockedReply.isBlank()) {
            throw new IllegalArgumentException("blockedReply 不能为空白");
        }
        this.ruleSet = ruleSet;
        this.blockedReply = blockedReply;
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
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        Optional<GuardrailVerdict> verdict = inspect(chatClientRequest);
        if (verdict.isPresent()) {
            return blockedResponse(chatClientRequest, verdict.get());
        }
        return chain.nextCall(chatClientRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        Optional<GuardrailVerdict> verdict = inspect(chatClientRequest);
        if (verdict.isPresent()) {
            // 拦截时不进入下游流，直接发一个只含话术的单元素流
            return Flux.just(blockedResponse(chatClientRequest, verdict.get()));
        }
        return chain.nextStream(chatClientRequest);
    }

    /** 取本轮用户输入做检测；取不到用户消息时放行（不因上游异常路径误拦）。 */
    private Optional<GuardrailVerdict> inspect(ChatClientRequest request) {
        String userInput = lastUserText(request);
        if (userInput == null) {
            return Optional.empty();
        }
        Optional<GuardrailVerdict> verdict = ruleSet.check(userInput);
        verdict.ifPresent(v -> log.warn("🛡️ 输入护栏拦截: rule={}, reason={}, inputLen={}",
                v.ruleName(), v.reason(), userInput.length()));
        return verdict;
    }

    /** 取最后一条 UserMessage 的文本；没有用户消息返回 null。 */
    private String lastUserText(ChatClientRequest request) {
        List<Message> messages = request.prompt().getInstructions();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m.getMessageType() == MessageType.USER) {
                return m.getText();
            }
        }
        return null;
    }

    /** 构造一个不经过模型的响应，沿用原请求的 context 以免下游依赖丢失。 */
    private ChatClientResponse blockedResponse(ChatClientRequest request, GuardrailVerdict verdict) {
        log.info("🛡️ 已拦截，未调用 LLM: rule={}", verdict.ruleName());
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(blockedReply))));
        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(request.context())
                .build();
    }
}
