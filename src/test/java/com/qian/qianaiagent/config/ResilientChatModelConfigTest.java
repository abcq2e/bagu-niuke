package com.qian.qianaiagent.config;

import com.qian.qianaiagent.agent.llm.ResilientChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

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
}
