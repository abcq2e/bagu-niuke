package com.qian.qianaiagent.agent.llm;

import com.qian.qianaiagent.config.LlmResilienceProperties;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    // ==================================================================
    //  流式路径
    //
    //  背景：Spring AI 的 stream() 返回冷流，调用它不发起网络请求，故障只在订阅时
    //  暴露。因此「try/catch 包住 primary.stream(prompt)」的降级分支是死代码 ——
    //  下面每条测试都必须在「订阅之后」断言，否则钉不住真实行为。
    //
    //  没有引入 reactor-test（类路径上没有 StepVerifier），统一用
    //  collectList().block(Duration) 断言；带 Duration 是刻意的：
    //  实现回归时应当「失败」而不是把构建挂死。
    // ==================================================================

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(10);

    private ResilientChatModel modelWithFirstTokenTimeout(ChatModel primary, ChatModel fallback,
                                                          Duration firstTokenTimeout) {
        LlmResilienceProperties p = props();
        p.setTimeout(firstTokenTimeout);
        return new ResilientChatModel(primary, fallback, p, UNAVAILABLE);
    }

    /** 订阅后把每个元素的文本收集起来。实现回归时抛异常而非挂死。 */
    private List<String> textsOf(Flux<ChatResponse> flux) {
        List<ChatResponse> list = flux.collectList().block(BLOCK_TIMEOUT);
        return list == null ? List.of()
                : list.stream().map(r -> r.getResult().getOutput().getText()).toList();
    }

    /** 一个「被订阅时才计数」的流，用来钉住「备模型到底有没有被碰过」。 */
    private Flux<ChatResponse> counted(String text, AtomicInteger subscriptions) {
        return Flux.just(ok(text)).doOnSubscribe(s -> subscriptions.incrementAndGet());
    }

    @Test
    @DisplayName("流式：主模型正常时原样透传，备模型一次都不被订阅")
    void streamPassesThroughWhenPrimaryHealthy() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);
        AtomicInteger fallbackSubscriptions = new AtomicInteger();

        when(primary.stream(any(Prompt.class)))
                .thenReturn(Flux.just(ok("第1段"), ok("第2段")));
        when(fallback.stream(any(Prompt.class)))
                .thenReturn(counted("备模型流式回答", fallbackSubscriptions));

        List<String> texts = textsOf(model(primary, fallback).stream(prompt()));

        assertThat(texts).containsExactly("第1段", "第2段");
        assertThat(fallbackSubscriptions.get()).isZero();
    }

    @Test
    @DisplayName("流式：建流那一刻不订阅上游（冷流惰性），订阅后才真正调用主模型")
    void streamIsLazyUntilSubscribed() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);
        AtomicInteger primarySubscriptions = new AtomicInteger();

        when(primary.stream(any(Prompt.class)))
                .thenReturn(Flux.just(ok("回答")).doOnSubscribe(s -> primarySubscriptions.incrementAndGet()));
        // 抹掉打桩自身的调用记录，让下面的 verify(never()) 只反映真实调用。
        clearInvocations(primary, fallback);

        Flux<ChatResponse> stream = model(primary, fallback).stream(prompt());

        // 拿到 Flux 时既没调用 primary.stream()、也没订阅上游 ——
        // 这正是原 try/catch 降级分支成为死代码的根因。
        verify(primary, never()).stream(any(Prompt.class));
        assertThat(primarySubscriptions.get()).isZero();

        assertThat(textsOf(stream)).containsExactly("回答");
        verify(primary).stream(any(Prompt.class));
        assertThat(primarySubscriptions.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("流式：主模型在首元素到达前出错，降级到备模型流")
    void streamFallsBackWhenPrimaryFailsBeforeFirstElement() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);

        when(primary.stream(any(Prompt.class)))
                .thenReturn(Flux.<ChatResponse>error(new RuntimeException("connection reset")));
        when(fallback.stream(any(Prompt.class)))
                .thenReturn(Flux.just(ok("备模型流式回答")));

        List<String> texts = textsOf(model(primary, fallback).stream(prompt()));

        assertThat(texts).containsExactly("备模型流式回答");
        verify(fallback).stream(any(Prompt.class));
    }

    @Test
    @DisplayName("流式：主模型建流时同步抛异常，同样降级")
    void streamFallsBackWhenPrimaryThrowsSynchronously() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);

        when(primary.stream(any(Prompt.class)))
                .thenThrow(new IllegalStateException("建立流时就炸"));
        when(fallback.stream(any(Prompt.class)))
                .thenReturn(Flux.just(ok("备模型流式回答")));

        assertThat(textsOf(model(primary, fallback).stream(prompt())))
                .containsExactly("备模型流式回答");
    }

    @Test
    @DisplayName("流式：主模型首 token 超时，降级到备模型流")
    void streamFallsBackOnFirstTokenTimeout() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);

        when(primary.stream(any(Prompt.class))).thenReturn(Flux.never());
        when(fallback.stream(any(Prompt.class)))
                .thenReturn(Flux.just(ok("备模型流式回答")));

        ResilientChatModel model = modelWithFirstTokenTimeout(
                primary, fallback, Duration.ofMillis(200));

        assertThat(textsOf(model.stream(prompt()))).containsExactly("备模型流式回答");
        verify(fallback).stream(any(Prompt.class));
    }

    @Test
    @DisplayName("流式：首个元素之后的停顿不受首 token 超时约束（长回答不被误杀）")
    void streamDoesNotTimeOutAfterFirstElement() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);

        // 首元素立刻到，第二个元素 600ms 后才到 —— 远超 200ms 的首 token 阈值。
        when(primary.stream(any(Prompt.class))).thenReturn(Flux.concat(
                Flux.just(ok("首段")),
                Flux.just(ok("停顿后的第2段")).delayElements(Duration.ofMillis(600))));
        when(fallback.stream(any(Prompt.class)))
                .thenReturn(Flux.just(ok("备模型流式回答")));

        ResilientChatModel model = modelWithFirstTokenTimeout(
                primary, fallback, Duration.ofMillis(200));

        assertThat(textsOf(model.stream(prompt()))).containsExactly("首段", "停顿后的第2段");
        verify(fallback, never()).stream(any(Prompt.class));
    }

    @Test
    @DisplayName("流式：备模型也失败，返回兜底话术")
    void streamReturnsUnavailableWhenFallbackAlsoFails() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);

        when(primary.stream(any(Prompt.class)))
                .thenReturn(Flux.<ChatResponse>error(new RuntimeException("primary down")));
        when(fallback.stream(any(Prompt.class)))
                .thenReturn(Flux.<ChatResponse>error(new RuntimeException("fallback down")));

        assertThat(textsOf(model(primary, fallback).stream(prompt())))
                .containsExactly(UNAVAILABLE);
    }

    @Test
    @DisplayName("流式：备模型为 null 且主模型失败，返回兜底话术")
    void streamReturnsUnavailableWhenFallbackIsNull() {
        ChatModel primary = mock(ChatModel.class);

        when(primary.stream(any(Prompt.class)))
                .thenReturn(Flux.<ChatResponse>error(new RuntimeException("primary down")));

        assertThat(textsOf(model(primary, null).stream(prompt())))
                .containsExactly(UNAVAILABLE);
    }

    @Test
    @DisplayName("流式：主模型吐过首元素后中途出错，不降级，错误向上传播")
    void streamDoesNotFallBackAfterFirstElementEmitted() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);
        AtomicInteger fallbackSubscriptions = new AtomicInteger();

        when(primary.stream(any(Prompt.class))).thenReturn(Flux.concat(
                Flux.just(ok("前半段")),
                Flux.<ChatResponse>error(new RuntimeException("mid-stream boom"))));
        when(fallback.stream(any(Prompt.class)))
                .thenReturn(counted("备模型流式回答", fallbackSubscriptions));

        List<String> received = new ArrayList<>();
        Flux<ChatResponse> stream = model(primary, fallback).stream(prompt());

        assertThatThrownBy(() -> stream
                .doOnNext(r -> received.add(r.getResult().getOutput().getText()))
                .collectList()
                .block(BLOCK_TIMEOUT))
                .hasMessageContaining("mid-stream boom");

        // 已推送过的内容不做二次拼接，也不切模型。
        assertThat(received).containsExactly("前半段");
        assertThat(fallbackSubscriptions.get()).isZero();
    }

    @Test
    @DisplayName("getDefaultOptions 委派给主模型；主模型返回 null 时退回接口默认值")
    void delegatesDefaultOptionsToPrimary() {
        ChatModel primary = mock(ChatModel.class);
        ChatOptions options = mock(ChatOptions.class);
        when(primary.getDefaultOptions()).thenReturn(options);

        assertThat(model(primary, null).getDefaultOptions()).isSameAs(options);

        // 主模型没有默认 options 时不能把 null 漏给消费方。
        assertThat(model(mock(ChatModel.class), null).getDefaultOptions()).isNotNull();
    }

    // ==================================================================
    //  降级原因的可观测性
    //
    //  「熔断打开」「舱壁满」都是主模型<b>根本没被调用</b>，与「主模型真的失败了」
    //  是两回事。日志是降级路径上唯一的线索，所以这里直接断言日志文本 ——
    //  否则排查时会把限流误读成后端持续故障。
    // ==================================================================

    private ListAppender<ILoggingEvent> captureLogs() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger().addAppender(appender);
        return appender;
    }

    private void detachLogs(ListAppender<ILoggingEvent> appender) {
        logbackLogger().detachAppender(appender);
    }

    private Logger logbackLogger() {
        return (Logger) LoggerFactory.getLogger(ResilientChatModel.class);
    }

    private List<String> messagesOf(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    @DisplayName("熔断器打开时日志说明「主模型未被调用」，并照常降级到备模型")
    void distinguishesCircuitOpenFromRealFailure() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);
        when(primary.call(any(Prompt.class))).thenThrow(new RuntimeException("connection reset"));
        when(fallback.call(any(Prompt.class))).thenReturn(ok("备模型回答"));

        // 窗口=1、失败率阈值=1%：一次失败即可打开熔断器。
        LlmResilienceProperties p = props();
        p.setMinimumNumberOfCalls(1);
        p.setSlidingWindowSize(1);
        p.setFailureRateThreshold(1f);
        ResilientChatModel model = new ResilientChatModel(primary, fallback, p, UNAVAILABLE);

        ListAppender<ILoggingEvent> logs = captureLogs();
        try {
            model.call(prompt());             // 第 1 次：主模型真的被调用并失败 -> 熔断器打开
            logs.list.clear();

            ChatResponse response = model.call(prompt());   // 第 2 次：熔断器已开

            assertThat(response.getResult().getOutput().getText()).isEqualTo("备模型回答");
            assertThat(messagesOf(logs)).anyMatch(m -> m.contains("熔断器打开"));
            assertThat(messagesOf(logs)).noneMatch(m -> m.contains("主模型调用失败"));
            // 熔断打开 = 主模型根本没被碰 -> 仍然只有第 1 次那一回。
            verify(primary, times(1)).call(any(Prompt.class));
        } finally {
            detachLogs(logs);
        }
    }

    @Test
    @DisplayName("舱壁已满时日志说明「主模型未被调用」，并照常降级到备模型")
    void distinguishesBulkheadFullFromRealFailure() throws Exception {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(primary.call(any(Prompt.class))).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return ok("主模型回答");
        });
        when(fallback.call(any(Prompt.class))).thenReturn(ok("备模型回答"));

        LlmResilienceProperties p = props();
        p.setMaxConcurrentCalls(1);
        ResilientChatModel model = new ResilientChatModel(primary, fallback, p, UNAVAILABLE);

        ListAppender<ILoggingEvent> logs = captureLogs();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ChatResponse> occupier = pool.submit(() -> model.call(prompt()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();   // 唯一舱壁位已被占住
            logs.list.clear();

            ChatResponse degraded = model.call(prompt());              // 舱壁满 -> 不再调用主模型

            assertThat(degraded.getResult().getOutput().getText()).isEqualTo("备模型回答");
            assertThat(messagesOf(logs)).anyMatch(m -> m.contains("舱壁已满"));
            assertThat(messagesOf(logs)).noneMatch(m -> m.contains("主模型调用失败"));

            release.countDown();
            assertThat(occupier.get(5, TimeUnit.SECONDS).getResult().getOutput().getText())
                    .isEqualTo("主模型回答");
        } finally {
            release.countDown();
            pool.shutdownNow();
            detachLogs(logs);
        }
    }

    @Test
    @DisplayName("主模型调用被中断时恢复中断标志，且不把中断吞掉")
    void restoresInterruptFlagWhenPrimaryCallIsInterrupted() throws Exception {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);
        // ChatModel.call 未声明受检异常，thenThrow 会被 Mockito 拒绝；
        // thenAnswer 的 answer() 声明了 throws Throwable，可以如实抛出中断。
        when(primary.call(any(Prompt.class))).thenAnswer(invocation -> {
            throw new InterruptedException("interrupted");
        });
        when(fallback.call(any(Prompt.class))).thenReturn(ok("备模型回答"));

        ListAppender<ILoggingEvent> logs = captureLogs();
        try {
            ChatResponse response = model(primary, fallback).call(prompt());

            assertThat(messagesOf(logs)).anyMatch(m -> m.contains("被中断"));
            // 中断标志必须被恢复：吞掉它会让应用关闭时的工作线程无法正确收尾。
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            // 标志恢复后，备模型链自身的 TimeLimiter 也会立刻收到中断 ——
            // 即「被中断时不再启动新的下游调用」，最终落到兜底话术。
            assertThat(response.getResult().getOutput().getText()).isEqualTo(UNAVAILABLE);
        } finally {
            // 不能把中断标志漏给同一线程上的后续测试。
            Thread.interrupted();
            detachLogs(logs);
        }
    }
}
