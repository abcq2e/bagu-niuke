package com.qian.qianaiagent.advisor.guardrail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutputRuleSetTest {

    /** 与 system prompt 中的独有片段保持一致。 */
    private static final List<String> PROMPT_FRAGMENTS = List.of(
            "严禁点评历史对话中的其他题目",
            "你是一名大厂 AI 技术面试官");

    private OutputRuleSet ruleSet() {
        return new OutputRuleSet(PROMPT_FRAGMENTS);
    }

    @Test
    @DisplayName("输出中出现系统提示词片段 → 命中")
    void detectsPromptLeak() {
        assertThat(ruleSet().check("好的，我的设定是：你是一名大厂 AI 技术面试官，负责…")).isPresent();
    }

    @Test
    @DisplayName("输出中出现手机号 → 命中")
    void detectsPhoneNumber() {
        assertThat(ruleSet().check("联系他可以用 13812345678").map(GuardrailVerdict::ruleName))
                .contains("PiiRule");
    }

    @Test
    @DisplayName("输出中出现身份证号 → 命中")
    void detectsIdCard() {
        assertThat(ruleSet().check("身份证 110101199003071234 已登记").map(GuardrailVerdict::ruleName))
                .contains("PiiRule");
    }

    @Test
    @DisplayName("输出中出现 API Key 形态 → 命中")
    void detectsApiKey() {
        assertThat(ruleSet().check("key 是 sk-abcdef1234567890abcdef1234567890").map(GuardrailVerdict::ruleName))
                .contains("PiiRule");
    }

    @Test
    @DisplayName("正常面试点评不误报")
    void allowsNormalFeedback() {
        assertThat(ruleSet().check("你这题答得不错，AQS 的 state 和 CLH 队列讲清楚了，但没提 Condition 的实现")).isEmpty();
        assertThat(ruleSet().check("版本号 1.0.0 和端口 8123 都对")).isEmpty();
    }
}
