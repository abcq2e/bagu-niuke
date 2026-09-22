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
 *
 * <h2>为什么不必额外排除「自己」（已实测，勿凭直觉加代码）</h2>
 * 有个看起来很像坑的地方：{@code allChatModels.stream()} 会不会把<b>正在创建中的
 * {@code resilientChatModel} 自己</b>也枚举出来？真如此，下一行的
 * {@code filter(m -> !(m == primary))} 就可能选中自己当备模型，得到
 * 「主模型失败 → 降级到自己 → 又失败 → 降级到自己」的<b>无限递归</b>。
 *
 * <p>实测（{@code ResilientChatModelConfigTest}，配主备两个 bean、在 stream 上打点枚举）
 * 该担忧<b>不成立</b>：枚举结果恰好是两个裸 {@code ChatModel}，没有 {@code ResilientChatModel}。
 * 原因是 Spring 在 {@code isSelfReference} 里排除了「当前正在创建的 bean 名」，
 * 且 {@code @Bean} 方法的 singleton factory 要等工厂方法返回后才注册，
 * 此刻 {@code getBean} 自己也拿不到 early reference。
 *
 * <p>因此这里<b>刻意不加</b> {@code filter(m -> !(m instanceof ResilientChatModel))} ——
 * 一行永远不会命中的防御代码，只会让下一个人以为它保护了什么。
 * 真正的保护是那条断言 {@code isNotInstanceOf(ResilientChatModel.class)} 的测试：
 * 哪天 Spring 改了行为，它会变红，而不是靠这行代码静默兜住。
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
