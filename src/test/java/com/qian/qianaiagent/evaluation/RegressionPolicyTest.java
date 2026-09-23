package com.qian.qianaiagent.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RegressionPolicyTest {

    @ParameterizedTest(name = "确定性 Δ{0} → 回归={1}")
    @CsvSource({
            "-1, true",    // 规则打分无方差（实测两轮 Δ 全为 0），掉 1 分就是真回归
            "-10, true",
            "0, false",
            "5, false"
    })
    @DisplayName("确定性分数：任何下降都算回归")
    void deterministicRegression(int delta, boolean expected) {
        assertThat(RegressionPolicy.isDeterministicRegressed(delta)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "Rubric Δ{0} → 值得提示={1}")
    @CsvSource({
            "-14, false",
            "-15, true",
            "-30, true",
            "-10, false",   // 实测同版本重跑就有 -10，这个量级不该当成异常
            "0, false",
            "20, false"     // 只有下降才值得提示
    })
    @DisplayName("Rubric 波动提示：只作展示，不参与判定，阈值 15")
    void rubricNoteworthy(int delta, boolean expected) {
        assertThat(RegressionPolicy.isRubricNoteworthy(delta)).isEqualTo(expected);
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
    @DisplayName("Rubric 大幅下降不算回归 —— 实测同版本重跑波动就有 ±13 分")
    void largeRubricDropIsNotRegression() {
        var c = BaselineManager.ComparisonReport.builder()
                .caseName("x").hasBaseline(true)
                .deltaDeterministic(0).deltaRubric(-30).build();

        assertThat(RegressionPolicy.isRegressed(c))
                .as("Rubric 噪声与要检测的信号同量级，接进判定只会制造假警报")
                .isFalse();
    }

    @Test
    @DisplayName("Rubric 大幅上升同样不能判成「改好了」—— 只有确定性分数能下结论")
    void largeRubricRiseIsNotRise() {
        var c = BaselineManager.ComparisonReport.builder()
                .caseName("x").hasBaseline(true)
                .deltaDeterministic(0).deltaRubric(30).build();

        assertThat(RegressionPolicy.isRegressed(c)).isFalse();
        assertThat(RegressionPolicy.verdictOf(0, 30)).startsWith("🟡");
    }

    @Test
    @DisplayName("判定口径的说明里写明了它只看确定性分数")
    void describeMentionsScope() {
        assertThat(RegressionPolicy.describe()).contains("确定性").contains("Rubric");
    }

    @ParameterizedTest(name = "Δdet={0}, Δrubric={1} → {2}")
    @CsvSource({
            "-1,   0,  🔴",   // 确定性掉 1 分 → 说改坏了
            "-1, -30,  🔴",   // 确定性下降就是回归，与 Rubric 无关
            " 5, -30,  🟢",   // 确定性上升 → 上升，即便 Rubric 大跌
            " 0, -30,  🟡",   // 确定性没变，Rubric 大跌 → 仍判持平（只是带上提示）
            " 0,  -5,  🟡",
            " 0,   0,  🟡"
    })
    @DisplayName("verdictOf 只认确定性分数，Rubric 仅影响文案提示")
    void verdictMatchesPolicy(int deltaDet, int deltaRubric, String expectedPrefix) {
        assertThat(RegressionPolicy.verdictOf(deltaDet, deltaRubric)).startsWith(expectedPrefix);
    }

    @Test
    @DisplayName("Rubric 大跌但确定性没动时，文案要说明「这是波动不是回归」")
    void verdictExplainsRubricNoise() {
        assertThat(RegressionPolicy.verdictOf(0, -30))
                .contains("波动")
                .contains("不作为判定依据");
    }
}
