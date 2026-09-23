package com.qian.qianaiagent.config;

import com.qian.qianaiagent.agent.llm.ResilientChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

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
                .isInstanceOf(ResilientChatModel.class);
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
                .isInstanceOf(ResilientChatModel.class);
        ctx.close();
    }

    // ================================================================
    // Step 3.5：备模型解析的陷阱 —— 绝不能把自己当成备模型
    // ================================================================

    /**
     * 配了主备两个 bean 时，{@code fallback} 必须指向那个真正的备模型
     * （此处 bean 名为 {@code dashscopeChatModel}），而不是空、也不是
     * {@link ResilientChatModel} 自己。
     *
     * <p>若选到自己，运行期就是无限递归：主模型失败 → 「降级」到自己 →
     * 又走一遍主模型 → 又失败 → ……，直到栈溢出。
     */
    @Test
    @DisplayName("主备都在时，fallback 指向真正的备模型而非自己")
    void fallbackIsTheOtherBeanNotSelf() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ChatModel primary = mock(ChatModel.class);
        ChatModel secondary = mock(ChatModel.class);
        ctx.registerBean("openAiChatModel", ChatModel.class, () -> primary);
        ctx.registerBean("dashscopeChatModel", ChatModel.class, () -> secondary);
        ctx.registerBean(LlmResilienceProperties.class, LlmResilienceProperties::new);
        ctx.register(ResilientChatModelConfig.class);
        ctx.refresh();

        ResilientChatModel resilient = ctx.getBean(ResilientChatModel.class);
        Object fallback = ReflectionTestUtils.getField(resilient, "fallback");

        assertThat(fallback)
                .as("备模型缺失说明 ObjectProvider 没枚举到 dashscopeChatModel")
                .isNotNull();
        assertThat(fallback)
                .as("fallback 不能是 ResilientChatModel 自己 —— 那是无限递归")
                .isNotInstanceOf(ResilientChatModel.class);
        assertThat(fallback)
                .as("fallback 应当是第二个 ChatModel bean（dashscopeChatModel）")
                .isSameAs(secondary);

        ctx.close();
    }

    /**
     * 项目真实拓扑：自动装配出<b>三个</b> ChatModel（{@code openAiChatModel}、{@code dashscopeChatModel}、
     * {@code ollamaChatModel}），备模型只能是 {@code dashscopeChatModel}。
     *
     * <p>回归的是一个「碰巧对了」的选法：早先的实现是
     * {@code allChatModels.stream().filter(m -> m != primary).findFirst()}，
     * 谁当备模型<b>只取决于自动配置的注册顺序</b>。它当时选中 DashScope，纯粹因为
     * {@code com.alibaba...} 排在 {@code org.springframework.ai.model.ollama...} 前面 ——
     * 包名字母序的巧合，不是意图。
     *
     * <p>后果：备模型一旦静默变成 {@code ollamaChatModel}（本地 {@code localhost:11434}），
     * 而那个服务通常没跑，于是主模型每次故障都要白烧 {@code maxAttempts × 退避}
     * 才落到兜底话术 —— 用户等待被拉长数倍，且没有任何报错。
     *
     * <p>这个测试<b>就是</b>那个巧合的哨兵：加了第三个 bean 之后，
     * 「取注册顺序里第一个非主模型」的写法会选到别的实例，本断言立刻变红。
     */
    @Test
    @DisplayName("三个 ChatModel bean 时，fallback 是 dashscopeChatModel，而非 ollamaChatModel")
    void fallbackIsDashscopeWhenThreeProvidersPresent() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ChatModel openAi = mock(ChatModel.class);
        ChatModel dashscope = mock(ChatModel.class);
        ChatModel ollama = mock(ChatModel.class);
        // 注册顺序<b>刻意</b>让 ollama 排在 dashscope 前面。
        //
        // 生产里的真实顺序恰好是反的（dashscope 在前），所以「按顺序取第一个非主模型」
        // 这种写法在生产上<b>看起来是对的</b> —— 这正是它危险的地方。要让测试真的
        // 守得住，就必须用那个会把顺序依赖暴露出来的排列，而不是复刻一个碰巧没事的排列。
        ctx.registerBean("openAiChatModel", ChatModel.class, () -> openAi);
        ctx.registerBean("ollamaChatModel", ChatModel.class, () -> ollama);
        ctx.registerBean("dashscopeChatModel", ChatModel.class, () -> dashscope);
        ctx.registerBean(LlmResilienceProperties.class, LlmResilienceProperties::new);
        ctx.register(ResilientChatModelConfig.class);
        ctx.refresh();

        ResilientChatModel resilient = ctx.getBean(ResilientChatModel.class);
        Object fallback = ReflectionTestUtils.getField(resilient, "fallback");

        assertThat(fallback)
                .as("备模型必须按 bean 名绑定到 dashscopeChatModel")
                .isSameAs(dashscope);
        assertThat(fallback)
                .as("备模型绝不能是 ollamaChatModel（本地服务，通常没跑：每次故障都白烧重试与退避）")
                .isNotSameAs(ollama);

        ctx.close();
    }

    @Test
    @DisplayName("备模型 bean 名不存在时，fallback 为 null 且启动不失败")
    void fallbackProviderResolvesToNullWhenNameMissing() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean("openAiChatModel", ChatModel.class, () -> mock(ChatModel.class));
        // 只有 ollama，没有 dashscopeChatModel：@Qualifier 按名字找不到 → 退化为「无备选」。
        // 这是有意的：备模型可以缺失，主模型不行。
        ctx.registerBean("ollamaChatModel", ChatModel.class, () -> mock(ChatModel.class));
        ctx.registerBean(LlmResilienceProperties.class, LlmResilienceProperties::new);
        ctx.register(ResilientChatModelConfig.class);
        ctx.refresh();

        ResilientChatModel resilient = ctx.getBean(ResilientChatModel.class);
        assertThat(ReflectionTestUtils.getField(resilient, "fallback"))
                .as("备模型按名字绑定：没有 dashscopeChatModel 就是没有备选，不能顺手拿 ollama 顶替")
                .isNull();

        ctx.close();
    }

    @Test
    @DisplayName("只有主模型时，fallback 为 null（退化为「主模型 + 兜底话术」）")
    void fallbackIsNullWhenOnlyPrimaryPresent() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean("openAiChatModel", ChatModel.class, () -> mock(ChatModel.class));
        ctx.registerBean(LlmResilienceProperties.class, LlmResilienceProperties::new);
        ctx.register(ResilientChatModelConfig.class);
        ctx.refresh();

        ResilientChatModel resilient = ctx.getBean(ResilientChatModel.class);
        assertThat(ReflectionTestUtils.getField(resilient, "fallback")).isNull();

        ctx.close();
    }

    // ================================================================
    // 装配缺口回归：4 处 ChatModel 必须是构造器注入
    // ================================================================

    /**
     * 这 4 个类原先都是 {@code @Resource private ChatModel openAiChatModel;}。
     * {@code @Resource} <b>先按字段名匹配</b>，会在 {@code @Primary} 生效之前
     * 命中名为 {@code openAiChatModel} 的裸 bean —— 于是这些路径完全没有韧性保护。
     *
     * <p>改为构造器注入后按<b>类型</b>解析，{@code @Primary} 的
     * {@link ResilientChatModel} 才会被选中。
     *
     * <p><b>职责边界：</b>「不得出现注入注解的 ChatModel 字段」那半边已经提升为
     * 全局 ArchUnit 规则（{@code PackageDependencyTest.chatModel_fields_must_not_be_*}），
     * 那边能扫到全代码库、不限于硬编码的 4 个类名，故此处不再重复。
     * 留在这里的是 ArchUnit 不好表达的那条：<b>public 构造器参数里确实有 ChatModel</b>
     * —— 规则只能证明「没有坏字段」，证明不了「注入真的走了构造器」。
     */
    @Test
    @DisplayName("4 处 ChatModel 依赖是构造器注入（字段为 final，且 public 构造器接了 ChatModel）")
    void chatModelDependenciesAreConstructorInjected() {
        List<Class<?>> classes = List.of(
                com.qian.qianaiagent.evaluation.RubricScorer.class,
                com.qian.qianaiagent.rag.evaluation.RagasEvaluator.class,
                com.qian.qianaiagent.rag.ingestion.MyKeywordEnricher.class,
                com.qian.qianaiagent.rag.retrieval.LLMReranker.class);

        for (Class<?> type : classes) {
            assertConstructorInjected(type);
        }
    }

    private static void assertConstructorInjected(Class<?> type) {
        String name = type.getSimpleName();

        boolean hasChatModelCtorParam = false;
        for (Constructor<?> ctor : type.getDeclaredConstructors()) {
            if (!Modifier.isPublic(ctor.getModifiers())) {
                continue;
            }
            for (Class<?> param : ctor.getParameterTypes()) {
                if (ChatModel.class.isAssignableFrom(param)) {
                    hasChatModelCtorParam = true;
                }
            }
        }
        assertThat(hasChatModelCtorParam)
                .as("%s 的 public 构造器参数里必须有 ChatModel（构造器注入按类型解析，@Primary 才生效）", name)
                .isTrue();

        for (Field field : type.getDeclaredFields()) {
            if (!ChatModel.class.isAssignableFrom(field.getType())) {
                continue;
            }
            // 「字段上不得有 @Resource / @Autowired」由全局 ArchUnit 规则守护，此处不重复
            assertThat(Modifier.isFinal(field.getModifiers()))
                    .as("%s 的 ChatModel 字段 %s 应当是 final（构造器注入的标志）", name, field.getName())
                    .isTrue();
        }
    }
}
