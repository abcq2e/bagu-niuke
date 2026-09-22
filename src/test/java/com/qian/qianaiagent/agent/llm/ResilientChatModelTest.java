package com.qian.qianaiagent.agent.llm;

import com.qian.qianaiagent.config.LlmResilienceProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResilientChatModelTest {

    private static final String UNAVAILABLE = "[ERROR] AI 服务暂时不可用";

    private LlmResilienceProperties props() {
        LlmResilienceProperties p = new LlmResilienceProperties();
        p.setMaxAttempts(1);                       // 测试里不重试，便于断言调用次数
        p.setTimeout(Duration.ofSeconds(2));
        p.setMinimumNumberOfCalls(100);            // 测试里不触发熔断
        return p;
    }

    private Prompt prompt() {
        return new Prompt(List.of(new UserMessage("测试")));
    }

    private ChatResponse ok(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private ResilientChatModel model(ChatModel primary, ChatModel fallback) {
        return new ResilientChatModel(primary, fallback, props(), UNAVAILABLE);
    }

    @Test
    @DisplayName("主模型正常时不走备模型")
    void usesPrimaryWhenHealthy() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);
        when(primary.call(any(Prompt.class))).thenReturn(ok("主模型回答"));

        ChatResponse response = model(primary, fallback).call(prompt());

        assertThat(response.getResult().getOutput().getText()).isEqualTo("主模型回答");
        verify(fallback, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("主模型可恢复异常耗尽重试后，切备模型")
    void fallsBackWhenPrimaryFails() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);
        when(primary.call(any(Prompt.class))).thenThrow(new RuntimeException("connection reset"));
        when(fallback.call(any(Prompt.class))).thenReturn(ok("备模型回答"));

        ChatResponse response = model(primary, fallback).call(prompt());

        assertThat(response.getResult().getOutput().getText()).isEqualTo("备模型回答");
        verify(fallback).call(any(Prompt.class));
    }

    @Test
    @DisplayName("主备都失败时返回兜底话术，不向上抛异常")
    void returnsFallbackReplyWhenBothFail() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);
        when(primary.call(any(Prompt.class))).thenThrow(new RuntimeException("connection reset"));
        when(fallback.call(any(Prompt.class))).thenThrow(new RuntimeException("connection reset"));

        ChatResponse response = model(primary, fallback).call(prompt());

        assertThat(response.getResult().getOutput().getText()).isEqualTo(UNAVAILABLE);
    }

    @Test
    @DisplayName("备模型为 null 时跳过降级，直接返回兜底话术")
    void toleratesMissingFallback() {
        ChatModel primary = mock(ChatModel.class);
        when(primary.call(any(Prompt.class))).thenThrow(new RuntimeException("connection reset"));

        ChatResponse response = model(primary, null).call(prompt());

        assertThat(response.getResult().getOutput().getText()).isEqualTo(UNAVAILABLE);
    }

    @Test
    @DisplayName("不可恢复异常仍走降级（备模型可能没这个问题）")
    void fallsBackOnUnrecoverableToo() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);
        when(primary.call(any(Prompt.class))).thenThrow(new IllegalArgumentException("400 invalid model"));
        when(fallback.call(any(Prompt.class))).thenReturn(ok("备模型回答"));

        assertThat(model(primary, fallback).call(prompt()).getResult().getOutput().getText())
                .isEqualTo("备模型回答");
    }

    @Test
    @DisplayName("IOException 被视为可恢复")
    void treatsIoExceptionAsRecoverable() {
        assertThat(ResilienceChain.isRecoverable(new IOException("boom"))).isTrue();
    }

    @Test
    @DisplayName("400 参数错误不被视为可恢复")
    void treatsBadRequestAsUnrecoverable() {
        assertThat(ResilienceChain.isRecoverable(new IllegalArgumentException("400 Bad Request"))).isFalse();
    }
}
