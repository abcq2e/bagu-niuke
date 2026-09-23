package com.qian.qianaiagent.evaluation;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 离线评估报告 —— 一次 {@link EvalRunner} 运行的完整结果，负责渲染成可读文本。
 *
 * <p>渲染刻意不追求边框对齐：中文在终端占两个字符宽，按 {@code String.length()} 补空格会错位。
 * 改用整行分隔 + 缩进层级，任何终端下都整齐。
 */
@Data
@Builder
public class EvalReport {

    /** 报告生成时间 */
    private LocalDateTime generatedAt;

    /** 各用例结果，顺序与用例文件加载顺序一致 */
    @Builder.Default
    private List<CaseOutcome> outcomes = new ArrayList<>();

    /** 稳定性测试结果；未跑则为 null */
    private StabilityTester.StabilityReport stability;

    /** 总耗时（毫秒） */
    private long elapsedMs;

    // ============================================================
    // 统计
    // ============================================================

    /** 通过数（仅统计实际评分的用例） */
    public int passCount() {
        return (int) outcomes.stream().filter(o -> o.isScored() && o.isPassed()).count();
    }

    /** 已评分用例的平均确定性分数；无已评分用例返回 0 */
    public double avgScore() {
        return outcomes.stream()
                .filter(CaseOutcome::isScored)
                .mapToInt(CaseOutcome::getDeterministicScore)
                .average()
                .orElse(0.0);
    }

    /** 是否存在回归的用例。判定规则见 {@link RegressionPolicy}。 */
    public boolean hasRegression() {
        return outcomes.stream().anyMatch(o -> RegressionPolicy.isRegressed(o.getComparison()));
    }

    /** 进程退出码：有回归 → 1，否则 → 0（便于接 CI） */
    public int exitCode() {
        return hasRegression() ? 1 : 0;
    }

    /** 一句话结论 */
    public String conclusion() {
        if (outcomes.isEmpty()) {
            return "⚠️ 未找到任何用例，请检查 evaluation/baselines/ 目录";
        }
        long regressions = outcomes.stream()
                .filter(o -> RegressionPolicy.isRegressed(o.getComparison()))
                .count();
        if (regressions > 0) {
            return "🔴 " + regressions + " 个用例分数下降，请检查最近改动"
                    + "（" + RegressionPolicy.describe() + "）";
        }
        long newly = outcomes.stream()
                .filter(o -> o.getComparison() != null && o.getComparison().isNewBaseline())
                .count();
        if (newly == outcomes.size()) {
            return "🆕 全部用例为新建立基线，下次运行起开始对比";
        }
        return "🟢 无回归";
    }

    // ============================================================
    // 渲染
    // ============================================================

    public String render() {
        String rule = "══════════════════════════════════════════════════════════\n";
        String thin = "──────────────────────────────────────────────────────────\n";
        StringBuilder sb = new StringBuilder();
        sb.append(rule);
        sb.append("  Agent 评估报告   ").append(generatedAt).append("\n");
        sb.append(rule);
        sb.append(String.format("  总览  %d 个用例 | 通过 %d | 未通过 %d | 平均 %.1f | 耗时 %s%n",
                outcomes.size(), passCount(), outcomes.size() - passCount(),
                avgScore(), humanElapsed()));

        for (int i = 0; i < outcomes.size(); i++) {
            appendOutcome(sb, thin, i + 1, outcomes.get(i));
        }
        if (stability != null) {
            sb.append(thin);
            sb.append(String.format("  稳定性  pass^%d = %d/%d (%.0f%%)  %s%n",
                    stability.getRunCount(), stability.getPassCount(), stability.getRunCount(),
                    stability.getPassK() * 100,
                    stability.isStable() ? "✅ 稳定（失败率 ≤ 10%）" : "❌ 不稳定（失败率 > 10%）"));
        }
        sb.append(rule);
        sb.append("  结论  ").append(conclusion()).append("\n");
        sb.append(rule);
        return sb.toString();
    }

    private void appendOutcome(StringBuilder sb, String thin, int index, CaseOutcome o) {
        sb.append(thin);
        sb.append("  [").append(index).append("] ").append(o.getCaseName()).append("\n");

        if (o.getError() != null && !o.getError().isBlank()) {
            sb.append("      ❌ 执行异常: ").append(o.getError()).append("\n");
            return;
        }
        if (!o.isScored()) {
            sb.append("      ⚠️ 跳过评分: ").append(o.getSkipReason()).append("\n");
            return;
        }

        sb.append(String.format("      %s 确定性 %d/100 | Rubric %d/100%n",
                o.isPassed() ? "✅" : "❌", o.getDeterministicScore(), o.getRubricScore()));

        BaselineManager.ComparisonReport c = o.getComparison();
        if (c != null && c.isNewBaseline()) {
            sb.append("      │ 🆕 已自动建立基线\n");
        } else if (c != null && c.isHasBaseline()) {
            sb.append(String.format("      │ 基线 确定性 %d → %d (Δ %+d) | Rubric %d → %d (Δ %+d)%n",
                    c.getBaselineDeterministicScore(), c.getNewDeterministicScore(), c.getDeltaDeterministic(),
                    c.getBaselineRubricScore(), c.getNewRubricScore(), c.getDeltaRubric()));
            sb.append("      │ ").append(c.getSummary()).append("\n");
        }

        for (String d : o.getDeductions()) {
            sb.append("      │ ").append(d).append("\n");
        }
    }

    private String humanElapsed() {
        long totalSeconds = elapsedMs / 1000;
        if (totalSeconds < 60) {
            return totalSeconds + "s";
        }
        return (totalSeconds / 60) + "m" + (totalSeconds % 60) + "s";
    }

    // ============================================================
    // 数据模型
    // ============================================================

    /** 单个用例的执行与评分结果 */
    @Data
    @Builder
    public static class CaseOutcome {
        private String caseName;
        private String query;

        /** 是否完成了评分（轨迹不完整或执行异常时为 false） */
        private boolean scored;

        /** scored=false 的原因 */
        private String skipReason;

        private int deterministicScore;
        private int rubricScore;
        private boolean passed;

        /** 确定性评分的扣分明细 */
        @Builder.Default
        private List<String> deductions = new ArrayList<>();

        /** 基线对比结果；无基线且文件不存在时为 null */
        private BaselineManager.ComparisonReport comparison;

        /** 执行异常信息 */
        private String error;
    }
}
