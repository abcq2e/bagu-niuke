package com.qian.qianaiagent.config;

import com.qian.qianaiagent.agent.llm.ResilientChatModel;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
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
     * {@link ResilientChatModel} 才会被选中。这个测试就是防止将来有人改回
     * {@code @Resource} 导致缺口复现。
     */
    @Test
    @DisplayName("4 处 ChatModel 依赖是构造器注入，而非 @Resource/@Autowired 字段注入")
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
            for (java.lang.annotation.Annotation annotation : field.getAnnotations()) {
                assertThat(annotation.annotationType())
                        .as("%s 的 ChatModel 字段 %s 不得再用注入注解（字段注入会绕过 @Primary）",
                                name, field.getName())
                        .isNotIn(Resource.class, Autowired.class);
            }
            assertThat(Modifier.isFinal(field.getModifiers()))
                    .as("%s 的 ChatModel 字段 %s 应当是 final（构造器注入的标志）", name, field.getName())
                    .isTrue();
        }
    }
}
