package com.qian.qianaiagent.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RegressionPolicyTest {

    @ParameterizedTest(name = "确定性 Δ{0} → 回归={1}")
    @CsvSource({
            "-1, true",    // 规则打分无方差，掉 1 分就是真回归
            "-10, true",
            "0, false",
            "5, false"
    })
    @DisplayName("确定性分数：任何下降都算回归")
    void deterministicRegression(int delta, boolean expected) {
        assertThat(RegressionPolicy.isDeterministicRegressed(delta)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "Rubric Δ{0} → 回归={1}")
    @CsvSource({
            "-9, false",    // 阈值以内，视为 LLM 打分的自然波动
            "-10, true",    // 恰好到阈值，算回归（边界取闭区间）
            "-11, true",
            "-1, false",    // 关键：掉 1 分不再报回归，否则报警全是噪声
            "0, false",
            "5, false"
    })
    @DisplayName("Rubric 分数：需降幅达到阈值才算回归")
    void rubricRegression(int delta, boolean expected) {
        assertThat(RegressionPolicy.isRubricRegressed(delta)).isEqualTo(expected);
    }

    @Test
    @DisplayName("无可比基线时一律不算回归")
    void noBaselineMeansNoRegression() {
        BaselineManager.ComparisonReport noBaseline = BaselineManager.ComparisonReport.builder()
                .caseName("x").hasBaseline(false).deltaDeterministic(-50).build();

        assertThat(RegressionPolicy.isRegressed(noBaseline)).isFalse();
    }

    @Test
    @DisplayName("comparison 为 null 时不算回归（用例未评分）")
    void nullComparisonMeansNoRegression() {
        assertThat(RegressionPolicy.isRegressed(null)).isFalse();
    }

    @Test
    @DisplayName("确定性下降 → 回归")
    void detectsDeterministicDrop() {
        var c = BaselineManager.ComparisonReport.builder()
                .caseName("x").hasBaseline(true)
                .deltaDeterministic(-5).deltaRubric(0).build();

        assertThat(RegressionPolicy.isRegressed(c)).isTrue();
    }

    @Test
    @DisplayName("Rubric 大幅下降 → 回归（这正是修复前测不出来的情况）")
    void detectsLargeRubricDrop() {
        var c = BaselineManager.ComparisonReport.builder()
                .caseName("x").hasBaseline(true)
                .deltaDeterministic(0).deltaRubric(-20).build();

        assertThat(RegressionPolicy.isRegressed(c)).isTrue();
    }

    @Test
    @DisplayName("Rubric 小幅波动 → 不回归（修复前会误报）")
    void ignoresSmallRubricNoise() {
        var c = BaselineManager.ComparisonReport.builder()
                .caseName("x").hasBaseline(true)
                .deltaDeterministic(0).deltaRubric(-5).build();

        assertThat(RegressionPolicy.isRegressed(c)).isFalse();
    }

    @Test
    @DisplayName("阈值常量与提示文案中的数字一致")
    void thresholdIsExposed() {
        assertThat(RegressionPolicy.RUBRIC_REGRESSION_THRESHOLD).isEqualTo(10);
        assertThat(RegressionPolicy.describe()).contains("10");
    }

    @ParameterizedTest(name = "Δdet={0}, Δrubric={1} → {2}")
    @CsvSource({
            "-1,   0,  🔴",   // 确定性掉 1 分 → 说改坏了
            " 0, -20,  🔴",   // Rubric 掉 20 分 → 说改坏了
            " 0,  -5,  🟡",   // Rubric 掉 5 分 → 说持平（不再误报改坏）
            " 5,  10,  🟢",   // 上升
            " 0,   0,  🟡"    // 持平
    })
    @DisplayName("verdictOf 文案与判定口径一致")
    void verdictMatchesPolicy(int deltaDet, int deltaRubric, String expectedPrefix) {
        assertThat(RegressionPolicy.verdictOf(deltaDet, deltaRubric)).startsWith(expectedPrefix);
    }
}
