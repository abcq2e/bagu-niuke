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

import java.util.ArrayList;
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

    /**
     * 检查本轮 prompt 里的<b>每一条</b> UserMessage，命中任一规则即拦截；
     * 没有用户消息时放行（不因上游异常路径误拦）。
     *
     * <p><b>为什么不能只看最后一条</b>：Agent 的 ReAct 循环每一步都会把「下一步提示词」
     * 作为新的 {@link UserMessage} 追加到消息列表末尾（见 {@code ToolCallAgent#think}），
     * 于是「最后一条 UserMessage」变成了框架自己注入的提示词，用户真正的输入被挤到前面去 ——
     * 只看最后一条会让<b>整条 Agent 路径的输入护栏完全失效</b>。
     *
     * <p>这不是纸上推演：2026-09-23 端到端验收实测，三种注入
     * （指令覆盖 / 角色劫持 / 提示词刺探）打到 {@code /api/ai/agent/chat}
     * <b>全部直达模型</b>，而面试路径 {@code /api/ai/chat} 全部正确拦截。
     * 更隐蔽的是模型「自己拒绝了」这类注入，看着像护栏在起作用，
     * 其实只是模型自身的对齐 —— 换个话术就可能绕过去。
     *
     * <p>代价：框架自己注入的提示词也会被检查。已确认它们很短
     * （六十字上下，远低于 {@code LengthRule} 的两千字阈值）且不含规则关键词，不会误伤。
     */
    private Optional<GuardrailVerdict> inspect(ChatClientRequest request) {
        for (String userInput : userTexts(request)) {
            Optional<GuardrailVerdict> verdict = ruleSet.check(userInput);
            if (verdict.isPresent()) {
                log.warn("🛡️ 输入护栏拦截: rule={}, reason={}, inputLen={}",
                        verdict.get().ruleName(), verdict.get().reason(), userInput.length());
                return verdict;
            }
        }
        return Optional.empty();
    }

    /** 取 prompt 里所有非空 UserMessage 的文本，按出现顺序。 */
    private List<String> userTexts(ChatClientRequest request) {
        List<String> texts = new ArrayList<>();
        for (Message m : request.prompt().getInstructions()) {
            if (m.getMessageType() == MessageType.USER) {
                String text = m.getText();
                if (text != null && !text.isBlank()) {
                    texts.add(text);
                }
            }
        }
        return texts;
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
