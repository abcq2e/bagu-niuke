package com.qian.qianaiagent.advisor;

import com.qian.qianaiagent.advisor.guardrail.InputRuleSet;
import com.qian.qianaiagent.advisor.guardrail.LengthRule;
import com.qian.qianaiagent.advisor.guardrail.OutputRuleSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 锁死护栏在 Advisor 链中的相对位置。
 *
 * <p>顺序排错时护栏不会报错、也不会失效得明显 —— 它只是「看不到该看的东西」，
 * 静默失效。所以必须有测试锁住。
 */
class AdvisorChainOrderTest {

    @Test
    @DisplayName("输入护栏必须在记忆 Advisor 之外（先于它执行）")
    void inputGuardrailRunsBeforeMemory() {
        InputGuardrailAdvisor input = new InputGuardrailAdvisor(
                new InputRuleSet(List.of(new LengthRule())), "拒绝");

        // 向 Spring AI 真实实例取 order，而不是硬编码魔数 ——
        // 上游升级改了默认值时会立刻测出来，而不是静默失效
        int memoryOrder = MessageChatMemoryAdvisor
                .builder(mock(ChatMemory.class))
                .build()
                .getOrder();

        assertThat(input.getOrder())
                .as("输入护栏 order 必须小于 MessageChatMemoryAdvisor 的默认 order(%d)", memoryOrder)
                .isLessThan(memoryOrder);
    }

    @Test
    @DisplayName("输出护栏必须在日志 Advisor 之内（模型原始输出最先经过它）")
    void outputGuardrailRunsAfterLogger() {
        OutputGuardrailAdvisor output = new OutputGuardrailAdvisor(
                new OutputRuleSet(List.of("片段")), "兜底");

        assertThat(output.getOrder())
                .as("输出护栏 order 必须大于 MyLoggerAdvisor 的 0")
                .isGreaterThan(new MyLoggerAdvisor().getOrder());
    }

    @Test
    @DisplayName("按 order 升序排序后，护栏位于链的两端")
    void guardrailsSitAtBothEnds() {
        List<Advisor> chain = new ArrayList<>(List.of(
                new OutputGuardrailAdvisor(new OutputRuleSet(List.of("片段")), "兜底"),
                new MyLoggerAdvisor(),
                new InputGuardrailAdvisor(new InputRuleSet(List.of(new LengthRule())), "拒绝")));

        chain.sort(Comparator.comparingInt(Advisor::getOrder));

        assertThat(chain.get(0)).isInstanceOf(InputGuardrailAdvisor.class);
        assertThat(chain.get(chain.size() - 1)).isInstanceOf(OutputGuardrailAdvisor.class);
    }
}
