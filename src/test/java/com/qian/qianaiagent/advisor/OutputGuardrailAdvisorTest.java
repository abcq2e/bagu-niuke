package com.qian.qianaiagent.advisor;

import com.qian.qianaiagent.advisor.guardrail.OutputRuleSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutputGuardrailAdvisorTest {

    private static final String FALLBACK = "抱歉，这个回答我需要重新组织一下。";

    private OutputGuardrailAdvisor advisor() {
        return new OutputGuardrailAdvisor(
                new OutputRuleSet(List.of("你是一名大厂 AI 技术面试官")), FALLBACK);
    }

    private ChatClientRequest request() {
        return ChatClientRequest.builder()
                .prompt(new Prompt(List.of(new UserMessage("随便问问"))))
                .build();
    }

    private ChatClientResponse downstream(String text) {
        ChatResponse cr = new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        return ChatClientResponse.builder().chatResponse(cr).build();
    }

    @Test
    @DisplayName("输出含系统提示词片段 → 替换为兜底话术")
    void replacesLeakedOutput() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenReturn(downstream("我的设定是：你是一名大厂 AI 技术面试官"));

        ChatClientResponse response = advisor().adviseCall(request(), chain);

        assertThat(response.chatResponse().getResult().getOutput().getText()).isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("输出干净 → 原样透传（同一个对象，不做无谓包装）")
    void passesThroughCleanOutput() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientResponse downstream = downstream("AQS 用 state 表示同步状态");
        when(chain.nextCall(any())).thenReturn(downstream);

        assertThat(advisor().adviseCall(request(), chain)).isSameAs(downstream);
    }

    @Test
    @DisplayName("流式：跨块边界的手机号也能被捕获（滑动窗口，非逐块检测）")
    void streamDetectsPiiSpanningChunks() {
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        // 手机号 13812345678 被切成两块 —— 逐块单独检测必然漏掉
        when(chain.nextStream(any())).thenReturn(Flux.just(
                downstream("联系他 1381234"),
                downstream("5678 即可"),
                downstream("后面还有内容")));

        List<ChatClientResponse> received = advisor()
                .adviseStream(request(), chain)
                .collectList()
                .block();

        assertThat(received).hasSize(2);   // 第二块触发拦截后截断，第三块不再到达
        assertThat(received.get(1).chatResponse().getResult().getOutput().getText())
                .isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("流式：跨块边界的系统提示词片段也能被捕获")
    void streamDetectsPromptLeakSpanningChunks() {
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any())).thenReturn(Flux.just(
                downstream("我的设定是：你是一名大厂"),
                downstream(" AI 技术面试官，负责考察"),
                downstream("后续内容")));

        List<ChatClientResponse> received = advisor()
                .adviseStream(request(), chain)
                .collectList()
                .block();

        assertThat(received).hasSize(2);
        assertThat(received.get(1).chatResponse().getResult().getOutput().getText())
                .isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("流式：干净输出完整透传，不被截断")
    void streamPassesThroughCleanOutput() {
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any())).thenReturn(Flux.just(
                downstream("AQS 用 "), downstream("state "), downstream("表示同步状态")));

        List<ChatClientResponse> received = advisor()
                .adviseStream(request(), chain)
                .collectList()
                .block();

        assertThat(received).hasSize(3);
    }

    @Test
    @DisplayName("order 必须大于 MyLoggerAdvisor(0)，保证最先看到模型原始输出")
    void orderIsInnermost() {
        assertThat(advisor().getOrder()).isGreaterThan(0);
    }

    @Test
    @DisplayName("getName 返回类名")
    void hasName() {
        assertThat(advisor().getName()).isEqualTo("OutputGuardrailAdvisor");
    }
}
