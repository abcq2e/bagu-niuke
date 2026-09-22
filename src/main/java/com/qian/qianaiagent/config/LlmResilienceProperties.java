package com.qian.qianaiagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LLM 调用韧性参数。
 *
 * <p>默认值对应 spec §3.3 的取舍：
 * <ul>
 *   <li>{@code timeout=30s} —— 现有配置<b>完全没有 LLM 超时</b>，单次卡死最长阻塞 5 分钟
 *       （受 {@code spring.mvc.async.request-timeout} 兜底）。30s 是「正常回答够用、
 *       卡死能及时发现」的折中</li>
 *   <li>{@code maxAttempts=3} —— 含首次，即最多重试 2 次。再多只是把用户等待时间线性拉长</li>
 *   <li>{@code failureRateThreshold=50} + {@code minimumNumberOfCalls=5} ——
 *       5 次是「够样本量」与「及时发现故障」的折中，避免一两次偶发失败就熔断</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "qian.llm.resilience")
public class LlmResilienceProperties {

    /** 单次 LLM 调用的超时。按单次尝试计算，不含重试耗时。 */
    private Duration timeout = Duration.ofSeconds(30);

    /** 最大尝试次数（含首次）。3 = 首次 + 最多 2 次重试。 */
    private int maxAttempts = 3;

    /** 重试退避基数。 */
    private Duration backoffBase = Duration.ofMillis(500);

    /** 重试退避乘数（指数退避）。 */
    private double backoffMultiplier = 2.0;

    /** 熔断失败率阈值（百分比）。 */
    private float failureRateThreshold = 50f;

    /** 熔断滑动窗口大小（次数）。 */
    private int slidingWindowSize = 10;

    /** 达到该调用次数后才开始计算失败率，避免样本过少误熔断。 */
    private int minimumNumberOfCalls = 5;

    /** 熔断打开后多久进入半开状态。 */
    private Duration waitDurationInOpenState = Duration.ofSeconds(60);

    /** 并发舱壁上限，防止 LLM 调用堆积。 */
    private int maxConcurrentCalls = 8;

    // ===== getter / setter（Spring Boot 绑定需要）=====

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getBackoffBase() {
        return backoffBase;
    }

    public void setBackoffBase(Duration backoffBase) {
        this.backoffBase = backoffBase;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public void setBackoffMultiplier(double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }

    public float getFailureRateThreshold() {
        return failureRateThreshold;
    }

    public void setFailureRateThreshold(float failureRateThreshold) {
        this.failureRateThreshold = failureRateThreshold;
    }

    public int getSlidingWindowSize() {
        return slidingWindowSize;
    }

    public void setSlidingWindowSize(int slidingWindowSize) {
        this.slidingWindowSize = slidingWindowSize;
    }

    public int getMinimumNumberOfCalls() {
        return minimumNumberOfCalls;
    }

    public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
        this.minimumNumberOfCalls = minimumNumberOfCalls;
    }

    public Duration getWaitDurationInOpenState() {
        return waitDurationInOpenState;
    }

    public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
        this.waitDurationInOpenState = waitDurationInOpenState;
    }

    public int getMaxConcurrentCalls() {
        return maxConcurrentCalls;
    }

    public void setMaxConcurrentCalls(int maxConcurrentCalls) {
        this.maxConcurrentCalls = maxConcurrentCalls;
    }
}
