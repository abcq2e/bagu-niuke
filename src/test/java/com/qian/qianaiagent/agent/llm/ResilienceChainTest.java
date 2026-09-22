package com.qian.qianaiagent.agent.llm;

import com.qian.qianaiagent.config.LlmResilienceProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    // ===== 装饰顺序契约（守护 ResilienceChain 唯一的"技术卖点"）=====

    @Test
    @DisplayName("装饰顺序：Retry 在 CircuitBreaker 外侧 —— 单次 execute 的 3 次重试各自计入熔断统计")
    void retrySitsOutsideCircuitBreaker() throws Exception {
        LlmResilienceProperties p = fastProps();
        p.setMaxAttempts(3);            // 一次 execute 内部共 3 次尝试
        p.setMinimumNumberOfCalls(3);   // 熔断窗口刚好等于重试次数
        p.setSlidingWindowSize(3);
        ResilienceChain chain = new ResilienceChain("order-test", p);

        Callable<String> fail = () -> {
            throw new java.net.SocketTimeoutException("read timed out");
        };

        // 只调用一次 execute
        try {
            chain.execute(fail);
        } catch (Exception ignored) {
            // 预期失败
        }

        // 顺序正确 Retry → CircuitBreaker：3 次尝试各计一次失败 → 打满窗口 → 打开
        // 顺序反转 CircuitBreaker → Retry：熔断器只看到 1 次调用 → 不足 minimumNumberOfCalls → 仍关闭
        assertThat(chain.isCircuitOpen())
                .as("Retry 必须在 CircuitBreaker 外侧：单次 execute 的 3 次重试应各自计入熔断统计")
                .isTrue();
    }

    @Test
    @DisplayName("Bulkhead 生效：并发超过上限的调用被直接拒绝")
    void bulkheadRejectsOverflowConcurrentCall() throws Exception {
        LlmResilienceProperties p = fastProps();
        p.setMaxAttempts(1);
        p.setMaxConcurrentCalls(1);
        p.setTimeout(Duration.ofSeconds(10));
        ResilienceChain chain = new ResilienceChain("bulkhead-test", p);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread first = new Thread(() -> {
            try {
                chain.execute(() -> {
                    entered.countDown();
                    release.await();
                    return "done";
                });
            } catch (Exception ignored) {
                // 主线程放行后正常返回
            }
        });
        first.start();

        assertThat(entered.await(5, TimeUnit.SECONDS))
                .as("第一个调用应已进入被装饰方法").isTrue();

        // 此时并发额度已被第一个调用占满，第二个并发调用应被 Bulkhead 拒绝
        assertThatThrownBy(() -> chain.execute(() -> "second"))
                .as("超过 maxConcurrentCalls 的调用必须被 Bulkhead 拒绝，而不是放行去执行")
                .isInstanceOf(io.github.resilience4j.bulkhead.BulkheadFullException.class);

        release.countDown();
        first.join(5_000);
    }

    @Test
    @DisplayName("舱壁限流不计入熔断：限流是主动保护动作，不应把熔断器打开")
    void bulkheadOverflowDoesNotOpenCircuitBreaker() throws Exception {
        LlmResilienceProperties p = fastProps();
        p.setMaxAttempts(1);
        p.setMaxConcurrentCalls(1);
        p.setTimeout(Duration.ofSeconds(10));
        p.setMinimumNumberOfCalls(2);     // 窗口极小：若不忽略限流，2 次拒绝即可熔断
        p.setSlidingWindowSize(2);
        ResilienceChain chain = new ResilienceChain("bulkhead-cb-test", p);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread first = new Thread(() -> {
            try {
                chain.execute(() -> {
                    entered.countDown();
                    release.await();
                    return "done";
                });
            } catch (Exception ignored) {
                // 主线程放行后正常返回
            }
        });
        first.start();

        assertThat(entered.await(5, TimeUnit.SECONDS))
                .as("第一个调用应已进入被装饰方法，占满并发额度").isTrue();

        // 并发额度已被占满，随后的调用只会被 Bulkhead 拒绝（各抛 BulkheadFullException）
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> chain.execute(() -> "rejected"))
                    .as("超过 maxConcurrentCalls 的调用必须被 Bulkhead 拒绝")
                    .isInstanceOf(io.github.resilience4j.bulkhead.BulkheadFullException.class);
        }

        assertThat(chain.isCircuitOpen())
                .as("舱壁限流是主动保护动作，不是后端故障；连续限流不应打开熔断器")
                .isFalse();

        release.countDown();
        first.join(5_000);
    }

    // ===== 熔断的行为契约（不依赖 isCircuitOpen() 状态标志）=====

    @Test
    @DisplayName("熔断打开后快速失败：不再打到被装饰方法，抛 CallNotPermittedException")
    void failsFastAfterCircuitOpens() throws Exception {
        LlmResilienceProperties p = fastProps();
        p.setMaxAttempts(1);
        p.setMinimumNumberOfCalls(3);
        p.setSlidingWindowSize(3);
        ResilienceChain chain = new ResilienceChain("fastfail-test", p);

        AtomicInteger calls = new AtomicInteger();
        Callable<String> fail = () -> {
            calls.incrementAndGet();
            throw new java.net.SocketTimeoutException("read timed out");
        };

        // 打满窗口触发熔断
        for (int i = 0; i < 3; i++) {
            try {
                chain.execute(fail);
            } catch (Exception ignored) {
                // 预期失败
            }
        }
        assertThat(calls).as("熔断前 3 次调用都应真的打到被装饰方法").hasValue(3);

        // 熔断已打开，再调用应被快速拒绝，且不再触达被装饰方法
        assertThatThrownBy(() -> chain.execute(fail))
                .isInstanceOf(CallNotPermittedException.class);
        assertThat(calls).as("被拒绝的调用不应再打到被装饰方法").hasValue(3);
    }
}
