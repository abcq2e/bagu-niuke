package com.qian.qianaiagent.agent.llm;

import com.qian.qianaiagent.config.LlmResilienceProperties;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * 给 LLM 调用套上韧性链的装饰器，实现「主模型 → 备模型 → 兜底话术」三级降级。
 *
 * <h2>两条链相互独立</h2>
 * 主模型与备模型各持一条 {@link ResilienceChain}，熔断器与重试计数互不干扰。
 * 若共用一条，主模型的故障计数会污染备模型 —— 表现为「刚切到备模型就被熔断」。
 *
 * <h2>{@link ResilienceChain} 只覆盖阻塞的 {@link #call(Prompt)}</h2>
 * 四级链（Retry / CircuitBreaker / Bulkhead / TimeLimiter）建立在 {@code Callable} 之上，
 * 是<b>阻塞式</b>语义；而 {@code Flux} 是惰性的 —— 把 {@code Callable} 装饰器套在
 * 一个「返回 Flux 的方法」上，只会阻塞建流线程、把建流耗时计入熔断统计，
 * 对流本身的执行毫无保护作用。因此四级链<b>刻意只覆盖 {@code call()}</b>，
 * 流式路径另用 Reactor 原生算子实现保护（见下）。这是有意的边界，不是遗漏。
 *
 * <h2>全挂时为什么不抛异常</h2>
 * 返回兜底话术而非抛异常，是为了保持与 {@code QuizApp.java:584} 现有
 * {@code onErrorResume} 行为一致、前端契约不变。
 * <b>代价是调用方无法感知失败，日志成为唯一线索</b>，因此此处必须记 ERROR 级。
 *
 * <h2>流式路径</h2>
 * Spring AI 的 {@code stream()} 返回的是冷流（{@code Flux.deferContextual}）：
 * 调用它的那一刻<b>不会发起任何网络请求</b>，真正的 HTTP 调用发生在<b>订阅时</b>。
 * 因此「用 try/catch 包住 {@code primary.stream(prompt)}」永远不会捕获到网络故障 ——
 * 那样的降级分支是死代码。流式路径必须用 {@code Flux.defer} 把建流推迟到订阅时，
 * 再用算子表达降级。
 *
 * <p>流式路径的三条取舍：
 * <ol>
 *   <li><b>不重试</b> —— 流一旦开始吐字，重试会让用户看到重复内容</li>
 *   <li><b>降级只发生在「首个元素到达之前」</b> —— 一旦已向用户推送过内容，
 *       切换模型会让用户看到两段拼接的内容。因此<b>中途出错不降级</b>，
 *       让错误向上传播，由调用方既有的 {@code onErrorResume} 处理</li>
 *   <li><b>首 token 超时</b> —— 用 {@code props.getTimeout()} 兜住「对端接受了连接
 *       却迟迟不发第一个元素」这种情况。注意该超时<b>只作用于首个元素</b>：
 *       首个元素之后的停顿不设超时（长回答里正常的思考停顿不该被判死）</li>
 * </ol>
 */
@Slf4j
public class ResilientChatModel implements ChatModel {

    private final ChatModel primary;
    private final ChatModel fallback;
    private final ResilienceChain primaryChain;
    private final ResilienceChain fallbackChain;
    private final String unavailableReply;

    /**
     * 流式路径的首 token 超时。取 {@code props.getTimeout()} 而非另设参数 ——
     * 「一次调用最多等多久」对阻塞与流式是同一个语义，分成两个旋钮只会让配置项失控。
     */
    private final Duration firstTokenTimeout;

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
        this.firstTokenTimeout = props.getTimeout();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // 先分别接住「没真正调用主模型」的两类拒绝，再落到通用失败分支。
        // 合并成一条日志会误导排查：熔断打开时主模型根本没被调用，
        // 但日志说「主模型调用失败」，看起来像主模型一直在挂。
        try {
            return primaryChain.execute(() -> primary.call(prompt));
        } catch (CallNotPermittedException e) {
            log.warn("⚡ 主模型熔断器打开，直接使用备模型");
        } catch (BulkheadFullException e) {
            log.warn("🚧 主模型舱壁已满，降级使用备模型");
        } catch (InterruptedException e) {
            // 中断标志必须恢复：吞掉它会让应用关闭时的工作线程无法正确收尾。
            Thread.currentThread().interrupt();
            log.warn("⚠️ 主模型调用被中断，准备降级");
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

    /**
     * 流式降级：<b>首 token 超时</b> 或 <b>首 token 到达前的错误</b> 时切备模型。
     *
     * <p>为什么必须 {@code Flux.defer}：Spring AI 的 {@code stream()} 返回冷流，
     * 建流那一刻不发起网络调用，故障只在订阅时暴露。不 defer 就没有「订阅时才知道
     * 主模型建流失败」这个时机，降级也就无从谈起。
     *
     * <p>为什么超时用 {@code timeout(firstTimeout, nextTimeoutFactory)} 而不是
     * {@code timeout(Duration)}：后者是<b>逐元素</b>间隔超时，会把长回答里正常的
     * 停顿也判死。这里让 {@code nextTimeoutFactory} 对每个后续元素返回
     * {@code Mono.never()} —— 实测（Q3）该返回值表示「后续不再设超时」，
     * 于是超时只作用于第一个元素。{@code Mono.empty()} 不具同样效果：
     * 实测（Q2）它会让超时立即触发。
     */
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> primary.stream(prompt))
                .timeout(Mono.delay(firstTokenTimeout), item -> Mono.never())
                .switchOnFirst((signal, inner) -> {
                    if (!signal.isOnError()) {
                        // 首元素已到达（或空流正常结束）—— 原样透传，
                        // 之后即使出错也不降级，避免用户看到两段拼接的内容。
                        return inner;
                    }
                    return degradeStream(prompt, signal.getThrowable());
                });
    }

    /**
     * 首个元素到达前主模型流失败 —— 切备模型；备模型也失败则返回兜底话术。
     *
     * <p>备模型侧同样套一层 {@code switchOnFirst}：备模型<b>吐过首个元素之后</b>
     * 再出错时不再追加兜底话术（否则用户会看到「半个回答 + 服务不可用」）。
     */
    private Flux<ChatResponse> degradeStream(Prompt prompt, Throwable primaryCause) {
        log.warn("⚠️ 主模型流在首元素到达前失败，准备降级: err={}", primaryCause.toString());

        if (fallback == null) {
            return Flux.just(unavailableResponse());
        }

        // defer 让 fallback.stream() 只在订阅时调用 —— 主模型正常时备模型一次都不碰。
        return Flux.defer(() -> fallback.stream(prompt))
                .switchOnFirst((signal, inner) -> {
                    if (!signal.isOnError()) {
                        return inner;
                    }
                    log.error("❌ 备模型流也失败，返回兜底话术: err={}",
                            signal.getThrowable().toString());
                    return Flux.just(unavailableResponse());
                });
    }

    /**
     * 委派给主模型，而不是走 {@code ChatModel} 接口的空实现。
     *
     * <p>接口 default 返回 {@code ChatOptions.builder().build()}，会把被装饰模型自带的
     * 默认 options 静默丢弃 —— 任何直接读 {@code getDefaultOptions()} 的消费方
     * 都会拿到一份空配置。
     */
    @Override
    public ChatOptions getDefaultOptions() {
        ChatOptions options = primary.getDefaultOptions();
        return options != null ? options : ChatModel.super.getDefaultOptions();
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
