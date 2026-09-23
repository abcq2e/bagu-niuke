package com.qian.qianaiagent.evaluation;

/**
 * 回归判定 —— 全项目<b>唯一</b>的判定出处。
 *
 * <h2>为什么必须收拢</h2>
 * 修复前，判定散落在两处且口径不一致：
 * <ul>
 *   <li>{@code EvalReport.hasRegression()} 只看确定性分数</li>
 *   <li>{@code BaselineManager.compareWithBaseline()} 的 verdict 却把 Rubric 也算进去</li>
 * </ul>
 * 后果是一次 Rubric 掉 1 分的运行，报告正文逐条打印「🔴 分数下降！改坏了」，
 * 而结论行说「🟢 无回归」、退出码为 0 —— <b>同一份报告自相矛盾</b>。
 *
 * <h2>为什么两类分数用不同阈值</h2>
 * <ul>
 *   <li><b>确定性分数</b>由规则算出，同样输入必得同样结果，无方差 ——
 *       因此任何下降都是真回归，用严格 {@code < 0}</li>
 *   <li><b>Rubric 分数</b>由 LLM 打分，代码一行没动、重跑也可能掉几分 ——
 *       直接用 {@code < 0} 会让报警充满噪声。噪声大的报警等于没有报警，
 *       所以必须给容差</li>
 * </ul>
 *
 * <h2>⚠️ 阈值的取值依据，以及一个前置依赖</h2>
 * 10 分这个值是<b>待验证的默认值</b>，不是实测结论。正确做法是用
 * {@link StabilityTester} 跑重复实验，观测<b>同版本</b>分数的自然波动幅度，再定阈值。
 *
 * <p><b>若实测波动接近或超过 10 分，本类的方案不成立</b> —— 那说明 Rubric 打分本身
 * 不稳定，此时应优先解决打分稳定性，而不是调大阈值。
 * <b>阈值调大到能吞下噪声的程度，就等于关掉了这项检查。</b>
 */
public final class RegressionPolicy {

    /**
     * Rubric 回归阈值。降幅达到该值（含）才算回归。
     * <p>
     * 依据见类注释 —— 这是待实测验证的默认值。
     */
    public static final int RUBRIC_REGRESSION_THRESHOLD = 10;

    private RegressionPolicy() {
    }

    /** 确定性分数：任何下降都是回归（规则打分无方差）。 */
    public static boolean isDeterministicRegressed(int deltaDeterministic) {
        return deltaDeterministic < 0;
    }

    /** Rubric 分数：降幅达到阈值才算回归。 */
    public static boolean isRubricRegressed(int deltaRubric) {
        return deltaRubric <= -RUBRIC_REGRESSION_THRESHOLD;
    }

    /**
     * 综合判定。
     *
     * @param comparison 基线对比结果，可为 null（用例未评分或无基线）
     * @return 是否构成回归
     */
    public static boolean isRegressed(BaselineManager.ComparisonReport comparison) {
        if (comparison == null || !comparison.isHasBaseline()) {
            return false;
        }
        return isDeterministicRegressed(comparison.getDeltaDeterministic())
                || isRubricRegressed(comparison.getDeltaRubric());
    }

    /**
     * 依据两个 delta 给出判定文案。
     *
     * <p>按原始 delta 而非 {@link BaselineManager.ComparisonReport} 取参：
     * {@code BaselineManager} 在算出 delta 的那一刻就要这句话，
     * 此时 {@code ComparisonReport} 尚未构造完成，传对象会迫使调用方先造一个半成品。
     */
    public static String verdictOf(int deltaDeterministic, int deltaRubric) {
        if (isDeterministicRegressed(deltaDeterministic) || isRubricRegressed(deltaRubric)) {
            return "🔴 分数下降！改坏了，请检查最近的改动。";
        }
        if (deltaDeterministic > 0 || deltaRubric > 0) {
            return "🟢 分数上升！改好了。";
        }
        return "🟡 分数持平。";
    }

    /** 供报告与日志展示的阈值说明。 */
    public static String describe() {
        return "确定性分数任何下降即回归；Rubric 降幅 ≥ " + RUBRIC_REGRESSION_THRESHOLD + " 分才算回归";
    }
}
