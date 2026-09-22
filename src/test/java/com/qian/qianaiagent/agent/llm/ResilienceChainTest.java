package com.qian.qianaiagent.agent.llm;

import com.qian.qianaiagent.config.LlmResilienceProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilienceChainTest {

    private ResilienceChain chain() {
        return new ResilienceChain("test", new LlmResilienceProperties());
    }

    private LlmResilienceProperties fastProps() {
        LlmResilienceProperties p = new LlmResilienceProperties();
        p.setTimeout(Duration.ofMillis(200));
        p.setBackoffBase(Duration.ofMillis(10));
        p.setMaxAttempts(3);
        return p;
    }

    @Test
    @DisplayName("可恢复异常（超时）会被重试至 maxAttempts 次")
    void retriesRecoverableException() throws Exception {
        ResilienceChain chain = new ResilienceChain("test", fastProps());
        AtomicInteger calls = new AtomicInteger();

        Callable<String> alwaysTimeout = () -> {
            calls.incrementAndGet();
            throw new java.net.SocketTimeoutException("read timed out");
        };

        assertThatThrownBy(() -> chain.execute(alwaysTimeout)).isInstanceOf(Exception.class);
        assertThat(calls).as("3 次尝试全部执行").hasValue(3);
    }

    @Test
    @DisplayName("不可恢复异常（如 400 参数错误）不重试，立即抛出")
    void doesNotRetryUnrecoverable() {
        ResilienceChain chain = new ResilienceChain("test", fastProps());
        AtomicInteger calls = new AtomicInteger();

        Callable<String> badRequest = () -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("400 Bad Request: invalid parameter");
        };

        assertThatThrownBy(() -> chain.execute(badRequest))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).as("只尝试 1 次").hasValue(1);
    }

    @Test
    @DisplayName("连续失败达阈值后熔断器打开，后续调用快速失败")
    void opensCircuitAfterRepeatedFailures() throws Exception {
        LlmResilienceProperties p = fastProps();
        p.setMaxAttempts(1);              // 每次调用只尝试一次，便于快速累积失败
        p.setMinimumNumberOfCalls(3);     // 3 次起算失败率
        p.setSlidingWindowSize(3);
        ResilienceChain chain = new ResilienceChain("test", p);

        Callable<String> fail = () -> {
            throw new java.net.SocketTimeoutException("read timed out");
        };

        // 打满窗口，触发熔断
        for (int i = 0; i < 3; i++) {
            try {
                chain.execute(fail);
            } catch (Exception ignored) {
                // 预期失败
            }
        }

        assertThat(chain.isCircuitOpen()).as("熔断器应已打开").isTrue();
    }

    @Test
    @DisplayName("成功调用正常返回结果")
    void returnsResultOnSuccess() throws Exception {
        assertThat(chain().execute(() -> "ok")).isEqualTo("ok");
    }

    @Test
    @DisplayName("超过超时上限的调用被中断，不会无限阻塞")
    void timesOutLongRunningCall() {
        LlmResilienceProperties p = fastProps();
        p.setMaxAttempts(1);
        ResilienceChain chain = new ResilienceChain("test", p);

        Callable<String> slow = () -> {
            Thread.sleep(5_000);
            return "never";
        };

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> chain.execute(slow)).isInstanceOf(Exception.class);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).as("应在超时后很快返回，而非等满 5 秒")
                .isLessThan(2_000);
    }
}
