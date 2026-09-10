package com.qian.qianaiagent.evaluation;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评估报告渲染单测 —— 结论文案与退出码推导，不涉及 LLM。
 */
class EvalReportTest {

    private EvalReport.CaseOutcome scored(String name, int det, int rubric,
                                          BaselineManager.ComparisonReport comparison) {
        return EvalReport.CaseOutcome.builder()
                .caseName(name).query("问题")
                .scored(true)
                .deterministicScore(det).rubricScore(rubric)
                .passed(det >= 60)
                .comparison(comparison)
                .build();
    }

    private BaselineManager.ComparisonReport compared(int baseline, int now, int delta, String summary) {
        return BaselineManager.ComparisonReport.builder()
                .caseName("x").hasBaseline(true).newBaseline(false)
                .baselineDeterministicScore(baseline).newDeterministicScore(now)
                .deltaDeterministic(delta)
                .baselineRubricScore(baseline).newRubricScore(now).deltaRubric(delta)
                .summary(summary)
                .build();
    }

    private BaselineManager.ComparisonReport newlyEstablished(int now) {
        return BaselineManager.ComparisonReport.builder()
                .caseName("x").hasBaseline(false).newBaseline(true)
                .newDeterministicScore(now).newRubricScore(now)
                .summary("🆕 已自动建立基线")
                .build();
    }

    private EvalReport reportOf(EvalReport.CaseOutcome... outcomes) {
        return EvalReport.builder()
                .generatedAt(LocalDateTime.of(2026, 9, 10, 15, 4, 22))
                .outcomes(List.of(outcomes))
                .elapsedMs(252_000)
                .build();
    }

    @Test
    void regressionYieldsExitCode1() {
        EvalReport report = reportOf(scored("用例A", 40, 30,
                compared(80, 40, -40, "🔴 分数下降！改坏了，请检查最近的改动。")));

        assertEquals(1, report.exitCode());
        assertTrue(report.conclusion().contains("分数下降"));
    }

    @Test
    void noRegressionYieldsExitCode0() {
        EvalReport report = reportOf(scored("用例A", 90, 85,
                compared(80, 90, 10, "🟢 分数上升！改好了。")));

        assertEquals(0, report.exitCode());
        assertTrue(report.conclusion().contains("无回归"));
    }

    @Test
    void allNewBaselinesYieldsExitCode0WithHint() {
        EvalReport report = reportOf(scored("用例A", 90, 85, newlyEstablished(90)));

        assertEquals(0, report.exitCode());
        assertTrue(report.conclusion().contains("新建立基线"));
    }

    @Test
    void emptyReportIsNotAnError() {
        EvalReport report = reportOf();

        assertEquals(0, report.exitCode());
        assertTrue(report.conclusion().contains("未找到任何用例"));
    }

    @Test
    void passCountAndAverageOnlyCountScoredOutcomes() {
        EvalReport report = reportOf(
                scored("A", 100, 90, null),
                scored("B", 60, 50, null),
                EvalReport.CaseOutcome.builder()
                        .caseName("C").scored(false).skipReason("轨迹不完整").build());

        // A、B 均 >= 60 及格线；C 未评分不计入
        assertEquals(2, report.passCount());
        assertEquals(80.0, report.avgScore(), 0.01);
    }

    @Test
    void renderIncludesCaseNamesScoresAndElapsed() {
        EvalReport report = reportOf(scored("搜索Spring AI教程", 90, 85,
                compared(88, 90, 2, "🟢 分数上升！改好了。")));

        String text = report.render();

        assertTrue(text.contains("搜索Spring AI教程"));
        assertTrue(text.contains("确定性 90/100"));
        assertTrue(text.contains("Rubric 85/100"));
        assertTrue(text.contains("基线 确定性 88 → 90"));
        assertTrue(text.contains("4m12s"), "耗时应渲染为 4m12s，实际:\n" + text);
    }

    @Test
    void renderIncludesDeductions() {
        EvalReport.CaseOutcome outcome = EvalReport.CaseOutcome.builder()
                .caseName("A").query("问题").scored(true)
                .deterministicScore(80).rubricScore(70).passed(true)
                .deductions(List.of("[-20] 缺少关键词: 教程"))
                .build();

        String text = reportOf(outcome).render();

        assertTrue(text.contains("[-20] 缺少关键词: 教程"));
    }

    @Test
    void renderMarksSkippedAndErroredCases() {
        EvalReport report = reportOf(
                EvalReport.CaseOutcome.builder()
                        .caseName("跳过用例").scored(false).skipReason("轨迹不完整（endTime 为空或无步骤）").build(),
                EvalReport.CaseOutcome.builder()
                        .caseName("异常用例").scored(false).error("连接超时").build());

        String text = report.render();

        assertTrue(text.contains("跳过评分: 轨迹不完整"));
        assertTrue(text.contains("执行异常: 连接超时"));
    }

    @Test
    void renderIncludesStabilityWhenPresent() {
        StabilityTester.StabilityReport stability = StabilityTester.StabilityReport.builder()
                .caseName("文件读写往返").runCount(5).passCount(4)
                .passK(0.8).avgScore(88.0).stdDev(5.0).isStable(false)
                .build();
        EvalReport report = EvalReport.builder()
                .generatedAt(LocalDateTime.of(2026, 9, 10, 15, 4, 22))
                .outcomes(List.of(scored("A", 90, 85, null)))
                .stability(stability)
                .elapsedMs(1000)
                .build();

        String text = report.render();

        assertTrue(text.contains("pass^5 = 4/5 (80%)"));
        assertTrue(text.contains("不稳定"));
    }

    @Test
    void elapsedFormatsSubMinuteAsSeconds() {
        EvalReport report = EvalReport.builder()
                .generatedAt(LocalDateTime.now())
                .outcomes(List.of())
                .elapsedMs(12_400)
                .build();

        assertTrue(report.render().contains("12s"), "预期 12s，实际:\n" + report.render());
    }
}
