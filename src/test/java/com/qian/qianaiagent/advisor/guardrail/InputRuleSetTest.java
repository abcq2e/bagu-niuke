package com.qian.qianaiagent.advisor.guardrail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputRuleSetTest {

    /** 可记录调用次数的假规则，用于验证短路。 */
    private static class CountingRule implements InputRule {
        private final String name;
        private final boolean hits;
        int calls = 0;

        CountingRule(String name, boolean hits) {
            this.name = name;
            this.hits = hits;
        }

        @Override
        public Optional<GuardrailVerdict> check(String userInput) {
            calls++;
            return hits ? Optional.of(new GuardrailVerdict(name, "命中")) : Optional.empty();
        }
    }

    @Test
    @DisplayName("按注册顺序执行，第一条命中即短路")
    void shortCircuitsOnFirstHit() {
        CountingRule first = new CountingRule("First", true);
        CountingRule second = new CountingRule("Second", true);
        InputRuleSet set = new InputRuleSet(List.of(first, second));

        var verdict = set.check("任意输入").orElseThrow();

        assertThat(verdict.ruleName()).isEqualTo("First");
        assertThat(first.calls).isEqualTo(1);
        assertThat(second.calls).isZero();   // 未被执行
    }

    @Test
    @DisplayName("全不命中返回空")
    void returnsEmptyWhenNoRuleHits() {
        InputRuleSet set = new InputRuleSet(List.of(
                new CountingRule("A", false), new CountingRule("B", false)));

        assertThat(set.check("正常问题")).isEmpty();
    }

    @Test
    @DisplayName("传入 null 输入不抛异常，按空串处理")
    void handlesNullSafely() {
        InputRuleSet set = new InputRuleSet(List.of(new LengthRule()));
        assertThat(set.check(null)).isPresent();
    }

    @Test
    @DisplayName("某条规则抛异常时跳过它，不影响其余规则")
    void toleratesRuleFailure() {
        InputRule broken = input -> {
            throw new IllegalStateException("规则写错了");
        };
        CountingRule after = new CountingRule("After", true);
        InputRuleSet set = new InputRuleSet(List.of(broken, after));

        assertThat(set.check("输入").orElseThrow().ruleName()).isEqualTo("After");
    }

    @Test
    @DisplayName("规则列表为空时拒绝构造")
    void rejectsEmptyRuleList() {
        assertThatThrownBy(() -> new InputRuleSet(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
