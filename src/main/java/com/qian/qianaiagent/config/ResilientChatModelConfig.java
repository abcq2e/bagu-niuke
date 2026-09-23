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
 * <h2>主模型是硬依赖，备模型是软依赖</h2>
 * <ul>
 *   <li><b>主模型（{@code openAiChatModel}）是硬依赖</b>：缺失即<b>启动失败</b>，
 *       这是<b>有意</b>的 —— 没有主模型的「韧性链」只是掩盖配置错误。
 *       注意 Spring 此时的报错信息具有误导性（"expected at least 1 bean"），
 *       即使容器里明明还有别的 {@code ChatModel}：{@code @Qualifier} 是<b>按名字</b>
 *       限定，找不到那个名字就是找不到，与类型的候选数量无关。</li>
 *   <li><b>备模型（{@code dashscopeChatModel}）是软依赖</b>：用 {@link ObjectProvider}
 *       惰性取，取不到就退化为「主模型 + 兜底话术」，<b>不会因为备模型缺失导致应用起不来</b>。</li>
 * </ul>
 *
 * <h2>备模型按 bean 名显式绑定，不靠自动配置顺序</h2>
 * 类型枚举 + {@code findFirst()} 曾在这里埋过一个静默陷阱：项目实际有<b>三个</b>
 * 自动装配出来的 {@code ChatModel}（{@code openAiChatModel}、{@code dashscopeChatModel}、
 * {@code ollamaChatModel}），{@code findFirst()} 取到谁<b>只取决于自动配置的注册顺序</b> ——
 * 它当时选中 DashScope，仅仅因为 {@code com.alibaba...} 排在
 * {@code org.springframework.ai.model.ollama...} 前面，是包名字母序的巧合。
 * 任何一条变化（新增排序更靠前的 provider、starter 改名/移除）都会让备模型
 * <b>静默变成 {@code ollamaChatModel}</b>：一个本地 {@code localhost:11434} 的服务，
 * 而它没跑，于是每次故障都要白烧 {@code maxAttempts × 退避} 才落到兜底话术。
 * 因此这里改为按名字绑定：意图写在代码里，而不是交给注册顺序。
 *
 * <h2>为什么不会把「自己」当备模型</h2>
 * {@code @Qualifier("dashscopeChatModel")} 是<b>按名字</b>取 bean，而本 {@code @Bean}
 * 方法注册的名字是 {@code resilientChatModel} —— 名字不同，拿不到自己。
 * 相比之下，曾经的「按类型枚举再过滤掉主模型」写法就得靠「排除自己」的额外保证。
 * {@code fallbackIsTheOtherBeanNotSelf} 仍保留作回归：它断言 {@code fallback}
 * 既非空、也{@code isNotInstanceOf(ResilientChatModel.class)}，
 * 哪天绑定方式被改回类型枚举，它会变红。
 */
@Slf4j
@Configuration
public class ResilientChatModelConfig {

    @Bean
    @Primary
    public ChatModel resilientChatModel(
            @Qualifier("openAiChatModel") ChatModel primary,
            @Qualifier("dashscopeChatModel") ObjectProvider<ChatModel> fallbackProvider,
            LlmResilienceProperties props) {

        // 按 bean 名取备模型；该名字不存在时 getIfAvailable() 返回 null（不会启动失败）
        ChatModel fallback = fallbackProvider.getIfAvailable();

        if (fallback == null) {
            log.warn("⚠️ 未找到备选 ChatModel(dashscopeChatModel)，降级链退化为「主模型 + 兜底话术」");
        } else {
            log.info("✅ 备选 ChatModel 已就绪: {}", fallback.getClass().getSimpleName());
        }

        return new ResilientChatModel(primary, fallback, props, ResilientChatModel.UNAVAILABLE_REPLY);
    }
}
