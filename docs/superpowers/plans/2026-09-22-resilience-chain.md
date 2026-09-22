# LLM 韧性链与状态持久化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给所有 LLM 调用套上「超时→重试→熔断→降级」四级保护，并补上会话状态的两处持久化缺口。

**Architecture:** `ResilientChatModel` 装饰 `ChatModel` 接口，用 resilience4j 显式嵌套装饰器组合韧性链；主模型链失败后切到备模型（Qwen）链，两条链各自独立熔断计数。`ActiveSpecManager` 从纯内存改为文件持久化，复用项目既有的 `StorageProperties` + 原子写模式。

**Tech Stack:** Java 21（虚拟线程）、Spring Boot 3.4.4、Spring AI 1.0.0、resilience4j 2.2.0

> **对应 spec**：`docs/superpowers/specs/2026-09-22-agent-resilience-guardrails-design.md` §3.3、§3.4、§3.5
>
> ⚠️ **禁止改动任何 `🎯 Task N` 练习骨架**（spec §1.1）。本计划全部是新建文件 + 修改 `ActiveSpecManager`/`StorageProperties`/`InterviewChatController`，不触碰骨架文件。

---

## 关键事实（已实测）

| 事实 | 值 | 来源 |
|---|---|---|
| `ChatModel` 抽象方法 | **只有 `ChatResponse call(Prompt)` 一个**，其余全是 default | 反编译 `spring-ai-model-1.0.0.jar` |
| 主模型 bean | `openAiChatModel`（DeepSeek，`api.deepseek.com`） | `application.yml` + Spring AI starter |
| 备模型配置 | `spring.ai.dashscope.chat.options.model=qwen-plus` 已配好，**但从未用于对话** | `application.yml` |
| 现有 LLM 超时配置 | **完全没有**，单次卡死最长阻塞 5 分钟 | `application.yml:44-58` 只有 `model` |
| 可恢复异常判定 | 已有字符串判定逻辑 | `ToolCallAgent.java:133-156` |
| `StorageProperties` 启用方式 | `@ConfigurationPropertiesScan`，**无** `@EnableConfigurationProperties` | `QianAiAgentApplication.java:13` |
| 项目配置注入风格 | `@Resource` 字段注入 + `@PostConstruct` 解析路径 | `SequentialRotationService.java:173-184` |
| SSE 端点返回类型 | `Flux<String>`（**不是** `SseEmitter`，源码树零 `SseEmitter` 引用） | `InterviewChatController.java:60-92` |

---

## File Structure

```
src/main/java/com/qian/qianaiagent/agent/llm/
├── ResilienceChain.java        四级装饰器组合，可独立单测
├── ResilienceChainFactory.java 从配置构造两条链（主/备）
└── ResilientChatModel.java     实现 ChatModel，编排主链→备链→兜底

src/main/java/com/qian/qianaiagent/config/
└── LlmResilienceProperties.java 超时/重试/熔断阈值配置

src/test/java/com/qian/qianaiagent/agent/llm/
├── ResilienceChainTest.java
└── ResilientChatModelTest.java

src/test/java/com/qian/qianaiagent/interview/progress/
└── ActiveSpecManagerPersistenceTest.java
```

**修改**：
- `pom.xml`（加 resilience4j 依赖）
- `config/StorageProperties.java`（加 `activeSpec` 属性）
- `interview/progress/ActiveSpecManager.java`（落盘）
- `controller/InterviewChatController.java`（SSE 断流中止）

---

## Task 1: 引入 resilience4j

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: 加依赖**

在 `pom.xml` 的 `<dependencies>` 中，紧跟 Sentinel 依赖之后（第 50-56 行那块）插入：

```xml
        <!-- resilience4j：LLM 调用的超时/重试/熔断/舱壁 -->
        <!-- Spring Boot 的 dependencyManagement 不含 resilience4j，必须显式指定版本 -->
        <!-- 2.2.0 是适配 Spring Boot 3.4 / Jakarta 的版本线 -->
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-spring-boot3</artifactId>
            <version>2.2.0</version>
        </dependency>
```

- [ ] **Step 2: 验证依赖可解析且不引入冲突**

Run: `./mvnw dependency:tree -Dincludes=io.github.resilience4j`
Expected: 输出包含 `io.github.resilience4j:resilience4j-spring-boot3:jar:2.2.0:compile` 及其子模块，**无** `omitted for conflict` 警告

- [ ] **Step 3: 验证现有测试仍全绿（引入新依赖不能改变既有行为）**

Run: `./mvnw test`
Expected: 全部 PASS

- [ ] **Step 4: 提交**

```bash
git add pom.xml
git commit -m "build: 引入 resilience4j 用于 LLM 调用韧性链"
```

---

## Task 2: 配置类

**Files:**
- Create: `src/main/java/com/qian/qianaiagent/config/LlmResilienceProperties.java`
- Test: `src/test/java/com/qian/qianaiagent/config/LlmResiliencePropertiesTest.java`

- [ ] **Step 1: 写失败的测试**

创建 `src/test/java/com/qian/qianaiagent/config/LlmResiliencePropertiesTest.java`：

```java
package com.qian.qianaiagent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LlmResiliencePropertiesTest {

    @Test
    @DisplayName("默认值符合 spec：30s 超时、3 次重试、50% 失败率熔断")
    void hasSpecDefaults() {
        var p = new LlmResilienceProperties();

        assertThat(p.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(p.getMaxAttempts()).isEqualTo(3);
        assertThat(p.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(p.getSlidingWindowSize()).isEqualTo(10);
        assertThat(p.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(p.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(60));
        assertThat(p.getMaxConcurrentCalls()).isEqualTo(8);
    }

    @Test
    @DisplayName("退避基数与倍率可配，默认 500ms / 2.0")
    void hasBackoffDefaults() {
        var p = new LlmResilienceProperties();

        assertThat(p.getBackoffBase()).isEqualTo(Duration.ofMillis(500));
        assertThat(p.getBackoffMultiplier()).isEqualTo(2.0);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw test -Dtest=LlmResiliencePropertiesTest`
Expected: 编译失败，`LlmResilienceProperties` 找不到符号

- [ ] **Step 3: 写实现**

创建 `src/main/java/com/qian/qianaiagent/config/LlmResilienceProperties.java`：

```java
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
```

- [ ] **Step 4: 运行确认通过**

Run: `./mvnw test -Dtest=LlmResiliencePropertiesTest`
Expected: PASS（2 个测试）

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/config/LlmResilienceProperties.java \
        src/test/java/com/qian/qianaiagent/config/LlmResiliencePropertiesTest.java
git commit -m "feat(resilience): LLM 韧性参数配置类"
```

---

## Task 3: `ResilienceChain` —— 四级装饰器组合

**Files:**
- Create: `src/main/java/com/qian/qianaiagent/agent/llm/ResilienceChain.java`
- Test: `src/test/java/com/qian/qianaiagent/agent/llm/ResilienceChainTest.java`

- [ ] **Step 1: 写失败的测试**

创建 `src/test/java/com/qian/qianaiagent/agent/llm/ResilienceChainTest.java`：

```java
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
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw test -Dtest=ResilienceChainTest`
Expected: 编译失败，`ResilienceChain` 找不到符号

- [ ] **Step 3: 写实现**

创建 `src/main/java/com/qian/qianaiagent/agent/llm/ResilienceChain.java`：

```java
package com.qian.qianaiagent.agent.llm;

import com.qian.qianaiagent.config.LlmResilienceProperties;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
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
```

- [ ] **Step 4: 运行确认通过**

Run: `./mvnw test -Dtest=ResilienceChainTest`
Expected: PASS（5 个测试）

> **若 `retriesRecoverableException` 失败且 `calls==1`**：说明 `retryOnException` 谓词没生效，检查 `isRecoverable` 是否把 `SocketTimeoutException` 判为可恢复（它是 `IOException` 子类，应命中第一个分支）。
>
> **若 `doesNotRetryUnrecoverable` 失败**：说明 `IllegalArgumentException` 被误判为可恢复。检查 `isRecoverable` —— 消息里的 "400 Bad Request: invalid parameter" 不含任何可恢复关键词，**不应**命中。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/agent/llm/ResilienceChain.java \
        src/test/java/com/qian/qianaiagent/agent/llm/ResilienceChainTest.java
git commit -m "feat(resilience): 四级韧性链，显式嵌套保证装饰顺序"
```

---

## Task 4: `ResilientChatModel` —— 主备降级

**Files:**
- Create: `src/main/java/com/qian/qianaiagent/agent/llm/ResilientChatModel.java`
- Test: `src/test/java/com/qian/qianaiagent/agent/llm/ResilientChatModelTest.java`

- [ ] **Step 1: 写失败的测试**

创建 `src/test/java/com/qian/qianaiagent/agent/llm/ResilientChatModelTest.java`：

```java
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
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw test -Dtest=ResilientChatModelTest`
Expected: 编译失败，`ResilientChatModel` 找不到符号

- [ ] **Step 3: 写实现**

创建 `src/main/java/com/qian/qianaiagent/agent/llm/ResilientChatModel.java`：

```java
package com.qian.qianaiagent.agent.llm;

import com.qian.qianaiagent.config.LlmResilienceProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 给 LLM 调用套上韧性链的装饰器，实现「主模型 → 备模型 → 兜底话术」三级降级。
 *
 * <h2>两条链相互独立</h2>
 * 主模型与备模型各持一条 {@link ResilienceChain}，熔断器与重试计数互不干扰。
 * 若共用一条，主模型的故障计数会污染备模型 —— 表现为「刚切到备模型就被熔断」。
 *
 * <h2>全挂时为什么不抛异常</h2>
 * 返回兜底话术而非抛异常，是为了保持与 {@code QuizApp.java:584} 现有
 * {@code onErrorResume} 行为一致、前端契约不变。
 * <b>代价是调用方无法感知失败，日志成为唯一线索</b>，因此此处必须记 ERROR 级。
 *
 * <h2>流式路径</h2>
 * {@link #stream(Prompt)} 不做重试（流已经吐了一半再重试会造成内容重复），
 * 只在<b>建立流之前</b>的失败上降级。建立之后的错误由调用方既有的
 * {@code onErrorResume} 处理。
 */
@Slf4j
public class ResilientChatModel implements ChatModel {

    private final ChatModel primary;
    private final ChatModel fallback;
    private final ResilienceChain primaryChain;
    private final ResilienceChain fallbackChain;
    private final String unavailableReply;

    /**
     * @param primary          主模型
     * @param fallback         备模型，可为 null（表示无备选，直接走兜底）
     * @param props            韧性参数
     * @param unavailableReply 主备全挂时返回的话术
     */
    public ResilientChatModel(ChatModel primary, ChatModel fallback,
                              LlmResilienceProperties props, String unavailableReply) {
        if (primary == null) {
            throw new IllegalArgumentException("primary 不能为 null");
        }
        if (unavailableReply == null || unavailableReply.isBlank()) {
            throw new IllegalArgumentException("unavailableReply 不能为空白");
        }
        this.primary = primary;
        this.fallback = fallback;
        this.primaryChain = new ResilienceChain("primary", props);
        this.fallbackChain = fallback == null ? null : new ResilienceChain("fallback", props);
        this.unavailableReply = unavailableReply;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            return primaryChain.execute(() -> primary.call(prompt));
        } catch (Exception primaryEx) {
            log.warn("⚠️ 主模型调用失败，准备降级: err={}", primaryEx.toString());
        }

        if (fallback != null) {
            try {
                ChatResponse response = fallbackChain.execute(() -> fallback.call(prompt));
                log.info("✅ 已降级至备模型并成功返回");
                return response;
            } catch (Exception fallbackEx) {
                log.error("❌ 备模型也失败，返回兜底话术: err={}", fallbackEx.toString());
            }
        }

        return unavailableResponse();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // 先在主模型上尝试「建立流」；建立失败才降级。
        // 已建立的流中途出错不重试 —— 会导致已推送内容重复。
        try {
            return primary.stream(prompt);
        } catch (Exception primaryEx) {
            log.warn("⚠️ 主模型建流失败，准备降级: err={}", primaryEx.toString());
        }

        if (fallback != null) {
            try {
                return fallback.stream(prompt);
            } catch (Exception fallbackEx) {
                log.error("❌ 备模型建流也失败: err={}", fallbackEx.toString());
            }
        }

        return Flux.just(unavailableResponse()).subscribeOn(Schedulers.boundedElastic());
    }

    /** 供健康检查/验收观察主链熔断状态。 */
    public boolean isPrimaryCircuitOpen() {
        return primaryChain.isCircuitOpen();
    }

    private ChatResponse unavailableResponse() {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(unavailableReply))));
    }

    @PreDestroy
    public void shutdown() {
        primaryChain.shutdown();
        if (fallbackChain != null) {
            fallbackChain.shutdown();
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./mvnw test -Dtest=ResilientChatModelTest`
Expected: PASS（7 个测试）

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/agent/llm/ResilientChatModel.java \
        src/test/java/com/qian/qianaiagent/agent/llm/ResilientChatModelTest.java
git commit -m "feat(resilience): ResilientChatModel 主备降级，双链独立熔断"
```

---

## Task 5: 装配为默认 ChatModel

**Files:**
- Create: `src/main/java/com/qian/qianaiagent/config/ResilientChatModelConfig.java`
- Test: `src/test/java/com/qian/qianaiagent/config/ResilientChatModelConfigTest.java`

- [ ] **Step 1: 写失败的测试**

创建 `src/test/java/com/qian/qianaiagent/config/ResilientChatModelConfigTest.java`：

```java
package com.qian.qianaiagent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ResilientChatModelConfigTest {

    @Test
    @DisplayName("配好主备两个 ChatModel bean 时，@Primary 解析到 ResilientChatModel")
    void registersResilientChatModelAsPrimary() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean("openAiChatModel", ChatModel.class, () -> mock(ChatModel.class));
        ctx.registerBean("dashscopeChatModel", ChatModel.class, () -> mock(ChatModel.class));
        ctx.registerBean(LlmResilienceProperties.class, LlmResilienceProperties::new);
        ctx.register(ResilientChatModelConfig.class);
        ctx.refresh();

        assertThat(ctx.getBean(ChatModel.class))
                .isInstanceOf(com.qian.qianaiagent.agent.llm.ResilientChatModel.class);
        ctx.close();
    }

    @Test
    @DisplayName("备模型缺失时仍能启动（不因缺 bean 而失败）")
    void startsWithoutFallback() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean("openAiChatModel", ChatModel.class, () -> mock(ChatModel.class));
        ctx.registerBean(LlmResilienceProperties.class, LlmResilienceProperties::new);
        ctx.register(ResilientChatModelConfig.class);
        ctx.refresh();

        assertThat(ctx.getBean(ChatModel.class))
                .isInstanceOf(com.qian.qianaiagent.agent.llm.ResilientChatModel.class);
        ctx.close();
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw test -Dtest=ResilientChatModelConfigTest`
Expected: 编译失败，`ResilientChatModelConfig` 找不到符号

- [ ] **Step 3: 写实现**

创建 `src/main/java/com/qian/qianaiagent/config/ResilientChatModelConfig.java`：

```java
package com.qian.qianaiagent.config;

import com.qian.qianaiagent.agent.llm.ResilientChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 把 {@link ResilientChatModel} 注册为默认注入的 {@link ChatModel}。
 *
 * <h2>为什么要 @Primary</h2>
 * 项目里注入 {@code ChatModel} 的地方有多处（{@code QuizApp}、{@code RubricScorer}、
 * {@code RagasEvaluator} 等）。用 @Primary 让它们<b>自动</b>获得保护，
 * 而不必逐个改注入点 —— 漏改一个就是「有的路径没保护」的隐形缺口。
 *
 * <h2>备模型用 ObjectProvider 而非直接注入</h2>
 * DashScope 的 ChatModel bean 由 starter 自动装配，bean 名未经运行验证。
 * 用 {@link ObjectProvider} 按类型惰性取，取不到就退化为「无备选」，
 * <b>不会因为备模型缺失导致应用起不来</b>。
 */
@Slf4j
@Configuration
public class ResilientChatModelConfig {

    /** 主备全挂时返回的话术。与 QuizApp.java:584 现有的错误文案保持同一形态。 */
    private static final String UNAVAILABLE_REPLY =
            "[ERROR] AI 服务暂时不可用，请稍后重试";

    @Bean
    @Primary
    public ChatModel resilientChatModel(
            @Qualifier("openAiChatModel") ChatModel primary,
            ObjectProvider<ChatModel> allChatModels,
            LlmResilienceProperties props) {

        // 按 bean 名找备模型；找不到则无备选
        ChatModel fallback = allChatModels.stream()
                .filter(m -> !(m == primary))
                .findFirst()
                .orElse(null);

        if (fallback == null) {
            log.warn("⚠️ 未找到备选 ChatModel，降级链退化为「主模型 + 兜底话术」");
        } else {
            log.info("✅ 备选 ChatModel 已就绪: {}", fallback.getClass().getSimpleName());
        }

        return new ResilientChatModel(primary, fallback, props, UNAVAILABLE_REPLY);
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./mvnw test -Dtest=ResilientChatModelConfigTest`
Expected: PASS（2 个测试）

- [ ] **Step 5: 跑全量测试**

Run: `./mvnw test`
Expected: 全部 PASS

> **若失败**：现有测试**无任何 `@MockBean`**（已核查），因此不应因 `@Primary` 改变注入结果。
> 若确有失败，大概率是某个测试直接 `new` 了 ChatModel 相关的类 —— 逐个看错误信息处理，
> **不要**通过去掉 `@Primary` 来绕过。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/config/ResilientChatModelConfig.java \
        src/test/java/com/qian/qianaiagent/config/ResilientChatModelConfigTest.java
git commit -m "feat(resilience): 装配 ResilientChatModel 为默认 ChatModel"
```

---

## Task 6: `ActiveSpecManager` 落盘

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/config/StorageProperties.java`
- Modify: `src/main/java/com/qian/qianaiagent/interview/progress/ActiveSpecManager.java`
- Test: `src/test/java/com/qian/qianaiagent/interview/progress/ActiveSpecManagerPersistenceTest.java`

- [ ] **Step 1: 给 `StorageProperties` 加属性**

在 `StorageProperties.java` 中，`reviewCursor` 字段（第 45 行）之后追加：

```java
    /** 当前生效的项目描述（相对 root） */
    private String activeSpec = ".active-specs";
```

在 `reviewCursorPath()` 方法（第 65-67 行）之后追加：

```java
    public Path activeSpecPath() {
        return resolve(activeSpec);
    }
```

在 `getReviewCursor()`/`setReviewCursor()`（第 115-121 行）之后追加：

```java
    public String getActiveSpec() {
        return activeSpec;
    }

    public void setActiveSpec(String activeSpec) {
        this.activeSpec = activeSpec;
    }
```

- [ ] **Step 2: 写失败的测试**

创建 `src/test/java/com/qian/qianaiagent/interview/progress/ActiveSpecManagerPersistenceTest.java`：

```java
package com.qian.qianaiagent.interview.progress;

import com.qian.qianaiagent.config.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveSpecManagerPersistenceTest {

    /** 用反射注入 storage 并手动调 init()，复现 Spring 的装配效果。 */
    private ActiveSpecManager newManager(Path root) {
        StorageProperties storage = new StorageProperties();
        storage.setRoot(root.toString());
        storage.setActiveSpec(".active-specs");

        ActiveSpecManager manager = new ActiveSpecManager();
        ReflectionTestUtils.setField(manager, "storage", storage);
        manager.init();
        return manager;
    }

    @Test
    @DisplayName("写入后重建实例（模拟重启）仍能读到项目描述")
    void survivesRestart(@TempDir Path root) {
        ActiveSpecManager first = newManager(root);
        first.updateSpec("chat_1", "我的项目是一个电商秒杀系统");

        // 模拟服务重启：全新实例，内存缓存为空
        ActiveSpecManager second = newManager(root);

        assertThat(second.getSpec("chat_1")).isEqualTo("我的项目是一个电商秒杀系统");
    }

    @Test
    @DisplayName("更新是覆盖语义，重启后读到最新值")
    void overwritesOnRestart(@TempDir Path root) {
        ActiveSpecManager first = newManager(root);
        first.updateSpec("chat_2", "旧项目描述");
        first.updateSpec("chat_2", "新项目描述");

        assertThat(newManager(root).getSpec("chat_2")).isEqualTo("新项目描述");
    }

    @Test
    @DisplayName("未写入过的会话返回 null，不因缺文件报错")
    void returnsNullForUnknownChat(@TempDir Path root) {
        assertThat(newManager(root).getSpec("never_seen")).isNull();
    }

    @Test
    @DisplayName("畸形 chatId 被净化，不会逃出目录")
    void sanitizesMaliciousChatId(@TempDir Path root) {
        ActiveSpecManager manager = newManager(root);
        manager.updateSpec("../../etc/passwd", "恶意内容");

        // 净化后文件名不含路径分隔符，且能按同一规则读回
        Path dir = root.resolve(".active-specs");
        assertThat(dir).exists();
        try (var files = java.nio.file.Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString()))
                    .allMatch(name -> !name.contains("..") && !name.contains("/") && !name.contains("\\"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("空白描述不写入，也不改变已有值")
    void ignoresBlankSpec(@TempDir Path root) {
        ActiveSpecManager manager = newManager(root);
        manager.updateSpec("chat_3", "有效描述");
        manager.updateSpec("chat_3", "   ");

        assertThat(manager.getSpec("chat_3")).isEqualTo("有效描述");
    }
}
```

- [ ] **Step 3: 运行确认失败**

Run: `./mvnw test -Dtest=ActiveSpecManagerPersistenceTest`
Expected: 失败 —— `updateSpec` 后新建实例读不到（当前纯内存实现）

- [ ] **Step 4: 改造 `ActiveSpecManager`**

在 `ActiveSpecManager.java` 中：

**追加 import：**

```java
import com.qian.qianaiagent.config.StorageProperties;
import com.qian.qianaiagent.util.ChatIdValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
```

**在类字段区（第 34 行 `activeSpecs` 声明之后）追加：**

```java
    /**
     * 统一存储路径配置。
     * <p>
     * 此前本类<b>不做持久化</b>（见类注释），导致服务重启后面试官忘记候选人项目背景 ——
     * 用户可直接感知的功能缺陷。改为落盘后，该注释已同步更新。
     */
    @Resource
    private StorageProperties storage;

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private Path specDir;

    @PostConstruct
    public void init() {
        specDir = storage.activeSpecPath();
        try {
            Files.createDirectories(specDir);
        } catch (IOException e) {
            log.warn("无法创建项目描述目录: {}", e.getMessage());
        }
    }
```

**把 `updateSpec` 方法体改为（在原有日志之后追加写盘）：**

```java
    public void updateSpec(String chatId, String spec) {
        if (chatId == null || spec == null || spec.isBlank()) {
            return;
        }
        Entry old = activeSpecs.put(chatId, new Entry(spec.trim(), System.currentTimeMillis()));
        if (old != null) {
            log.info("🔄 项目描述已覆盖: chatId={}, oldLen={}, newLen={}",
                    chatId, old.spec().length(), spec.length());
        } else {
            log.info("📋 新项目描述已设置: chatId={}, specLen={}", chatId, spec.length());
        }
        saveSpec(chatId);
    }
```

**把 `getSpec` 方法改为（内存未命中时从磁盘加载）：**

```java
    public String getSpec(String chatId) {
        Entry entry = activeSpecs.get(chatId);
        if (entry == null) {
            // 内存未命中 —— 可能是服务刚重启，尝试从磁盘恢复
            entry = loadSpec(chatId);
            if (entry == null) {
                return null;
            }
            activeSpecs.put(chatId, entry);
            log.info("📂 磁盘加载项目描述: chatId={}, specLen={}", chatId, entry.spec().length());
        }
        touch(chatId, entry);
        return entry.spec();
    }
```

**在类末尾（`touch` 方法之后）追加存储方法：**

```java
    // ===== 持久化 =====

    /**
     * 原子写盘：先写临时文件再 {@code ATOMIC_MOVE}。
     *
     * <p>不沿用 {@code SequentialRotationService.saveCursor} 的直接覆盖写 ——
     * 那是既有的非原子写（写到一半崩溃会留下截断的 JSON），本类不扩散该模式。
     */
    private void saveSpec(String chatId) {
        Entry entry = activeSpecs.get(chatId);
        if (entry == null || specDir == null) {
            return;
        }
        Path file = fileOf(chatId);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            mapper.writeValue(tmp.toFile(), entry);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // 少数文件系统不支持原子移动，退化为普通替换（仍是先写临时文件）
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warn("保存项目描述失败: chatId={}, err={}", chatId, e.getMessage());
        }
    }

    private Entry loadSpec(String chatId) {
        if (specDir == null) {
            return null;
        }
        Path file = fileOf(chatId);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return mapper.readValue(file.toFile(), Entry.class);
        } catch (IOException e) {
            log.warn("加载项目描述失败: chatId={}, err={}", chatId, e.getMessage());
            return null;
        }
    }

    /**
     * 文件名用 {@link ChatIdValidator#safeFileName} 净化 —— 与
     * {@code .quiz-cursor/}、{@code .review-cursor/} 同模式，chatId 由客户端提供，
     * 不净化就能靠 {@code ../} 写到目录外。
     */
    private Path fileOf(String chatId) {
        return specDir.resolve(ChatIdValidator.safeFileName(chatId) + ".json");
    }
```

> **注意**：`Entry` 是 `record`，Jackson 反序列化 record 需要 Jackson 2.12+ 且开启参数名模块。Spring Boot 3.4 自带的 Jackson 支持 record 反序列化，**但需要 record 的组件名可见**。若 `loadSpec` 报 `InvalidDefinitionException`，把 `Entry` 从 record 改为带 `@NoArgsConstructor`/`@AllArgsConstructor` 的普通类。**这一步留到运行时验证**，测试会直接暴露。

**同步更新类注释**（第 23-24 行的「不做持久化」一条）：

```java
 *   <li>落盘持久化 —— 服务重启后仍能恢复「当前项目描述」，避免面试官失忆</li>
```

- [ ] **Step 5: 运行确认通过**

Run: `./mvnw test -Dtest=ActiveSpecManagerPersistenceTest`
Expected: PASS（5 个测试）

- [ ] **Step 6: 跑全量测试（改动了正在使用的类）**

Run: `./mvnw test`
Expected: 全部 PASS。特别关注 `ability/`、`interview/` 下涉及 `ActiveSpecManager` 的既有测试

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/config/StorageProperties.java \
        src/main/java/com/qian/qianaiagent/interview/progress/ActiveSpecManager.java \
        src/test/java/com/qian/qianaiagent/interview/progress/ActiveSpecManagerPersistenceTest.java
git commit -m "fix: ActiveSpecManager 落盘，修复重启后面试官失忆"
```

---

## Task 7: 面试主路径 SSE 断流中止

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/controller/InterviewChatController.java`

- [ ] **Step 1: 先看 Agent 路径怎么做的（对齐既有做法，不发明新机制）**

读 `src/main/java/com/qian/qianaiagent/agent/BaseAgent.java` 第 345-360 行的 `safeSend` 与第 225-235 行的调用方。

现有做法：发送失败时返回 `false`，调用方据此抛 `IllegalStateException("SSE 连接已断开")` 中止执行。类注释说明这是为修复「在死连接上跑完整个循环」而加。

- [ ] **Step 2: 给面试路径加等价的断流检测**

`InterviewChatController.doChat` 当前返回 `Flux<String>`（第 60-92 行）：Spring 的响应式适配器负责把 Flux 写成 SSE，客户端断线时该 Flux 会被取消（cancel），但 `doOnComplete` 里的画像保存**不会触发**（取消不等于完成）。

把方法体的 `return` 段（第 84-92 行）改为：

```java
        return quizApp.doUnifiedChat(message, finalChatId, userId)
                .concatWith(Flux.just("[DONE]"))
                .subscribeOn(Schedulers.fromExecutor(taskExecutor))
                .doOnError(e -> log.error("❌ SSE 流异常: {}", e.getMessage(), e))
                // 🔴 客户端断线时 Flux 被 cancel 而非 complete：
                //    - doOnComplete 不会触发 → 画像保存被跳过（有 10s 定时兜底，但会延迟）
                //    - doFinally 无论完成/取消/出错都会触发 → 在这里补保存
                .doFinally(signal -> {
                    if (signal == reactor.core.publisher.SignalType.CANCEL) {
                        log.warn("🔌 客户端断线，流被取消: chatId={}", finalChatId);
                    }
                    userAbilityService.saveProfile(finalChatId, userId);
                    log.info("✅ SSE 流结束({}), chatId={}", signal, finalChatId);
                });
```

**同时删除原来的 `doOnComplete` 块** —— 保留会与 `doFinally` 重复保存（`saveProfile` 应幂等，但不必要的重复写盘要避免）。

对应地追加 import：

```java
import reactor.core.publisher.SignalType;
```

- [ ] **Step 3: 编译并跑相关测试**

Run: `./mvnw test -Dtest='*InterviewChat*'`
Expected: PASS

- [ ] **Step 4: 人工验证断流行为**

Run: `./mvnw spring-boot:run`

1. 前端发起一次面试对话
2. 回答流尚未结束时就关闭浏览器标签页（或强制断开网络）
3. 观察服务端日志

Expected: 出现 `🔌 客户端断线，流被取消: chatId=...`，随后是 `✅ SSE 流结束(CANCEL)`

> 若日志显示流一直跑到 `ON_COMPLETE`，说明断线未被检测到 —— 检查是否真的走了 `Flux` 返回路径（而非某个 `SseEmitter` 分支）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/controller/InterviewChatController.java
git commit -m "fix: 面试主路径 SSE 断流时补保存画像，对齐 Agent 路径行为"
```

---

## 完成标准

- [ ] `./mvnw test` 全绿（新增 5 个测试类）
- [ ] 人工验证：故意把 `SPRING_AI_OPENAI_BASE_URL` 改成一个不可达地址，发消息后日志出现 `⚠️ 主模型调用失败，准备降级` 与 `✅ 已降级至备模型并成功返回`，用户仍能收到回答
- [ ] 人工验证：把主备地址都改坏，用户收到 `[ERROR] AI 服务暂时不可用`，日志有 ERROR 级记录
- [ ] 人工验证：重启服务后，面试官仍记得之前的项目描述
- [ ] 人工验证：断线时日志出现 `🔌 客户端断线`
- [ ] 未改动任何 `🎯 Task N` 骨架文件
