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
import reactor.core.publisher.Signal;

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
 * 返回兜底话术而非抛异常，是为了与 {@code QuizApp} 的 SSE 错误文案保持同一形态、
 * 前端契约不变。
 * <b>代价是调用方无法感知失败，日志成为唯一线索</b>，因此此处必须记 ERROR 级。
 * <p>这个取舍<b>只对 SSE 路径成立</b>。评估 / 摄入等<b>非 SSE 路径</b>拿到那段文案时
 * 必须显式失败 —— 它们是「把模型输出当数据用」的消费方（JSON 解析、RAGAS 指标、
 * 关键词元数据），把「服务不可用」当成模型答案会静默污染评分与向量库。
 * 它们应当用 {@link #isUnavailableReply(String)} 识别并抛异常（见
 * {@link UnavailableReplyException}）。
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

    /**
     * 主备全挂时返回的兜底话术。
     *
     * <p>非 SSE 路径（评估、摄入等）必须显式识别它并失败 ——
     * 否则错误文案会被当成模型答案，静默污染评估分数与向量库元数据。
     *
     * <p>内容是<b>前端契约</b>的一部分（与 {@code QuizApp} 的 SSE 错误文案同形），
     * 改动会直接改变用户看到的文案。
     */
    public static final String UNAVAILABLE_REPLY = "[ERROR] AI 服务暂时不可用，请稍后重试";

    /** 判断一段模型输出是否为兜底话术。 */
    public static boolean isUnavailableReply(String text) {
        return UNAVAILABLE_REPLY.equals(text);
    }

    /**
     * 「兜底话术被当成模型答案」时抛出的异常。
     *
     * <p>为什么需要一个专属类型而不是裸的 {@link IllegalStateException}：
     * {@code LLMReranker} 对本就是「尽力而为」的调用失败有既有的降级分支
     * （{@code catch (Exception)} 后退回原始排序）。这层降级对「某批分数没解析出来」
     * 是对的，但会把「模型整体不可用」一起吞掉，让故障重新变回静默。
     * 专属类型让那两处降级分支能<b>只放行它</b>，其余异常照旧降级。
     */
    public static class UnavailableReplyException extends IllegalStateException {
        public UnavailableReplyException(String message) {
            super(message);
        }
    }

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
     * @param unavailableReply 主备全挂时返回的话术。生产装配处固定传
     *                         {@link #UNAVAILABLE_REPLY}；{@link #isUnavailableReply(String)}
     *                         也只认这个值，因此该参数只作测试替身用，不要传别的文案
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
     * 流式降级：<b>首 token 超时</b>、<b>首 token 到达前的错误</b>、
     * 或 <b>首 token 到达前的空完成</b> 时切备模型。
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
     *
     * <p>为什么判据是 {@code isOnNext()} 而不是 {@code !isOnError()}：{@code switchOnFirst}
     * 对<b>空源</b>会以 {@code onComplete} 作为首个信号调用转换器（不是 onNext、也不是
     * onError）。若按「不是 error 就透传」处理，上游静默断连造成的空完成会被当成一次
     * 正常结束，用户看到的就是一片空白 —— 既不是错误、也没超时，无处兜底。
     * 因此只有「首信号确实是数据」才透传，error 与 complete 一律降级。
     */
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> primary.stream(prompt))
                .timeout(Mono.delay(firstTokenTimeout), item -> Mono.never())
                .switchOnFirst((signal, inner) -> {
                    if (signal.isOnNext()) {
                        // 首信号确实是数据 —— 原样透传，之后即使出错也不降级，
                        // 避免用户看到两段拼接的内容。
                        return inner;
                    }
                    // 首信号是 error，或是空完成（一个元素都没发）—— 都走降级。
                    return degradeStream(prompt, causeOf(signal));
                });
    }

    /**
     * 首个元素到达前主模型流失败 —— 切备模型；备模型也失败则返回兜底话术。
     *
     * <p>备模型侧同样套一层 {@code switchOnFirst}，且判据同样收紧为 {@code isOnNext()}：
     * 备模型吐过首个元素之后再出错时不再追加兜底话术（否则用户会看到
     * 「半个回答 + 服务不可用」）；而备模型<b>吐首个元素之前</b>就空完成时，
     * 透传空流等于让用户等完主模型 30s 又等备模型，最后仍是空白 —— 必须落到兜底话术。
     *
     * <p>备模型侧也套了与主模型同形的首 token 超时。缺了它，最坏路径是
     * 「主模型超时 → 备模型不发首 token → 一直等到 {@code spring.mvc.async.request-timeout}
     * （5 分钟）」，等待时间无上界；补上后最坏路径变成
     * 「主模型超时 → 备模型超时 → 兜底话术」，<b>有界</b>优于无界。
     * 注意这里仍是 {@code timeout(firstTimeout, item -> Mono.never())} 这个形态，
     * 不是 {@code timeout(Duration)} —— 理由与主模型那层完全一致：中途超时切走会让
     * 用户看到截断/拼接的内容，所以超时只允许作用于首个元素。
     */
    private Flux<ChatResponse> degradeStream(Prompt prompt, String primaryCause) {
        log.warn("⚠️ 主模型流在首元素到达前失败（error 或空完成），准备降级: cause={}", primaryCause);

        if (fallback == null) {
            return Flux.just(unavailableResponse());
        }

        // defer 让 fallback.stream() 只在订阅时调用 —— 主模型正常时备模型一次都不碰。
        return Flux.defer(() -> fallback.stream(prompt))
                .timeout(Mono.delay(firstTokenTimeout), item -> Mono.never())
                .switchOnFirst((signal, inner) -> {
                    if (signal.isOnNext()) {
                        return inner;
                    }
                    log.error("❌ 备模型流也失败（error 或空完成），返回兜底话术: cause={}",
                            causeOf(signal));
                    return Flux.just(unavailableResponse());
                });
    }

    /**
     * 把首个信号渲染成一句可读的失败原因，供降级日志区分故障形态。
     *
     * <p>空完成（首信号即 {@code onComplete}）没有 throwable，但它和 error 一样是
     * 「上游在给出任何数据前就结束了」。日志里必须能把两者分开，否则静默断连会被
     * 误读成「对端报了错」，排查方向就偏了。
     */
    private static String causeOf(Signal<? extends ChatResponse> signal) {
        return signal.isOnError()
                ? String.valueOf(signal.getThrowable())
                : "首信号即 complete（上游在首元素到达前就结束了，未发出任何元素）";
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
