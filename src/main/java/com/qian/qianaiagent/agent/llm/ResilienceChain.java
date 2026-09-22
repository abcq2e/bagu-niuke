package com.qian.qianaiagent.agent.llm;

import com.qian.qianaiagent.config.LlmResilienceProperties;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

/**
 * LLM 调用的四级韧性链，显式嵌套而非用 {@code Decorators} 组合。
 *
 * <h2>为什么显式嵌套</h2>
 * {@code Decorators.ofCallable(...).withA().withB()} 的嵌套方向由库的约定决定，
 * 读代码时无法一眼看出谁在最外层。显式一层层包起来，顺序写在代码里，不可能看错。
 *
 * <h2>顺序（外 → 内）与理由</h2>
 * <pre>
 *   Retry → CircuitBreaker → Bulkhead → TimeLimiter → 真实调用
 * </pre>
 * <ul>
 *   <li><b>Retry 最外层</b>：若把 TimeLimiter 放在 Retry 之外，超时会直接穿透出去、
 *       根本不会被重试 —— 而超时恰恰是最该重试的故障之一</li>
 *   <li><b>TimeLimiter 最内层</b>：使超时<b>按单次尝试</b>计算，
 *       而不是把三次重试的总时长算作一次</li>
 *   <li><b>CircuitBreaker 在中间</b>：每次重试尝试单独计入熔断统计，
 *       避免「重试成功掩盖底层持续失败」被熔断器忽略</li>
 * </ul>
 *
 * <h2>TimeLimiter 的载体</h2>
 * resilience4j 的 TimeLimiter 要求被装饰者返回 {@code CompletableFuture}，
 * 而 {@code ChatModel.call()} 是阻塞方法。这里用 <b>Java 21 虚拟线程</b>执行器承载 ——
 * 虚拟线程创建代价极低，且不会与业务线程池争资源。
 * 超时后虚拟线程会被中断，但底层 HTTP 调用能否立即响应中断取决于客户端实现，
 * 因此超时保证的是<b>调用方不再等待</b>，而非一定终止底层请求。
 */
@Slf4j
public class ResilienceChain {

    private final String name;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final TimeLimiter timeLimiter;
    private final ExecutorService virtualExecutor;

    public ResilienceChain(String name, LlmResilienceProperties props) {
        this.name = name;

        this.retry = Retry.of(name + "-retry", RetryConfig.custom()
                .maxAttempts(props.getMaxAttempts())
                // 指数退避 + 随机抖动：抖动避免多个实例同时重试造成尖峰。
                // ⚠️ 若该重载不存在（resilience4j 各版本签名有差异），编译期即报错；
                //    改用带 randomizationFactor 的三参重载即可，不要退回无抖动的版本
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                        props.getBackoffBase(), props.getBackoffMultiplier()))
                .retryOnException(ResilienceChain::isRecoverable)
                .build());

        this.circuitBreaker = CircuitBreaker.of(name + "-cb", CircuitBreakerConfig.custom()
                .failureRateThreshold(props.getFailureRateThreshold())
                .slidingWindowSize(props.getSlidingWindowSize())
                .minimumNumberOfCalls(props.getMinimumNumberOfCalls())
                .waitDurationInOpenState(props.getWaitDurationInOpenState())
                // 舱壁限流是主动的保护动作，不是后端故障；计入会与熔断器互相拆台：
                // 限流 → 熔断计数上涨 → 熔断打开 → 连本该成功的调用也被拒。
                // 实测：不忽略时 2 次限流即可打满窗口把 CB 打开，忽略后 CB 计数纹丝不动。
                .ignoreExceptions(BulkheadFullException.class)
                .build());

        this.bulkhead = Bulkhead.of(name + "-bulkhead", BulkheadConfig.custom()
                .maxConcurrentCalls(props.getMaxConcurrentCalls())
                .build());

        this.timeLimiter = TimeLimiter.of(name + "-timeout", TimeLimiterConfig.custom()
                .timeoutDuration(props.getTimeout())
                .build());

        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 判定异常是否值得重试。
     *
     * <p>口径与 {@code ToolCallAgent.java:133-156} 保持一致（timeout / 429 / rate / limit /
     * connection），不另造一套 —— 两处判定分叉会导致「Agent 层认为可恢复、韧性链认为不可恢复」
     * 这类难查的不一致。
     *
     * <p>实现用异常类型 + 消息双重判定：类型明确时最可靠，但 Spring AI 会把部分错误
     * 包成通用异常，此时只能回退到消息匹配。
     */
    static boolean isRecoverable(Throwable t) {
        if (t instanceof java.net.SocketTimeoutException
                || t instanceof java.net.ConnectException
                || t instanceof java.io.IOException) {
            return true;
        }
        String msg = t.getMessage();
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase();
        return lower.contains("timeout")
                || lower.contains("429")
                || lower.contains("rate")
                || lower.contains("limit")
                || lower.contains("connection");
    }

    /** 按韧性链执行；失败时抛原始异常（由调用方决定是否降级）。 */
    public <T> T execute(Callable<T> action) throws Exception {
        // 最内层：超时（虚拟线程承载阻塞调用）
        Callable<T> withTimeout = () -> {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return action.call();
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            }, virtualExecutor);
            return TimeLimiter.decorateFutureSupplier(timeLimiter, () -> future).call();
        };

        // 由内向外逐层包裹
        Callable<T> withBulkhead = Bulkhead.decorateCallable(bulkhead, withTimeout);
        Callable<T> withCircuitBreaker = CircuitBreaker.decorateCallable(circuitBreaker, withBulkhead);
        Callable<T> withRetry = Retry.decorateCallable(retry, withCircuitBreaker);

        return withRetry.call();
    }

    /** 熔断器是否处于打开状态（供健康检查与验收观察）。 */
    public boolean isCircuitOpen() {
        return circuitBreaker.getState() == CircuitBreaker.State.OPEN;
    }

    public String getName() {
        return name;
    }

    /** 释放虚拟线程执行器。由持有者（{@code ResilientChatModel}）在销毁时调用。 */
    public void shutdown() {
        virtualExecutor.shutdown();
    }
}
