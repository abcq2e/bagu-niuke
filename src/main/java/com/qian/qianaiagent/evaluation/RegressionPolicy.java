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
 * <h2>为什么只有确定性分数参与判定</h2>
 * 这条是<b>实测结论</b>，不是拍脑袋：2026-09-23 建基线当天，同一个 commit
 * 连跑两轮评测，两轮的 Rubric 分数是
 * <pre>
 *   多工具串联           60 → 50   (Δ -10)
 *   对比SpringAI与LangChain4j  63 → 76   (Δ +13)
 *   工具失败自愈          94 → 100  (Δ +6)
 *   知识库RAG检索         55 → 60   (Δ +5)
 *   文件读写往返         100 → 100  (Δ  0)
 * </pre>
 * <b>代码一行没改</b>，Rubric 就波动到了 ±13 分。而六条用例的确定性分数
 * 两轮<b>全部 Δ=0</b>，零方差。所以：
 * <ul>
 *   <li><b>确定性分数</b>由规则算出，同样输入必得同样结果 —— 任何下降都是真回归，
 *       用严格 {@code < 0}</li>
 *   <li><b>Rubric 分数</b>由 LLM 打分，噪声已经大到与要检测的信号同量级。
 *       让它参与阻断性判定，结果就是「每次运行都在报假警报」——
 *       而一个总在响的警报等于没有警报。<b>因此它只展示、不判定。</b></li>
 * </ul>
 *
 * <h2>⚠️ 这里曾经走过一条弯路，别再走回去</h2>
 * 最初的设计是「给 Rubric 一个容差阈值」（{@code RUBRIC_REGRESSION_THRESHOLD = 10}），
 * 理由是「噪声大的报警等于没有报警，所以给容差」。方向是对的，
 * 但 10 分这个值没有实测依据 —— 实测下来噪声本身就 ≥10 分，容差根本盖不住。
 *
 * <p>当时类注释还留了一句警告，现在回过头看完全应验：
 * <blockquote>
 *   若实测波动接近或超过 10 分，本类的方案不成立……<b>阈值调大到能吞下噪声的程度，
 *   就等于关掉了这项检查。</b>
 * </blockquote>
 * 正确的方向是把 Rubric 移出判定，而不是把阈值从 10 调到 20。
 *
 * <h2>代价（知情取舍）</h2>
 * 「模型回答质量明显下降、但规则分恰好不变」的场景会<b>漏报</b>。
 * 要覆盖它，需要的是提升 Rubric 打分的稳定性（例如用 {@link StabilityTester}
 * 做重复采样取中位数），而不是把不稳定的一次采样接进阻断判定。
 */
public final class RegressionPolicy {

    /**
     * Rubric 波动提示阈值：超过它就在文案里提示一句，<b>但不参与判定</b>。
     * <p>
     * 实测同版本波动可达 ±13 分，所以这个值只用于「值得看一眼」的提示，
     * 不代表「超过它就是回归」—— 它不阻断、不影响退出码。
     */
    public static final int RUBRIC_NOTEWORTHY_DELTA = 15;

    private RegressionPolicy() {
    }

    /** 确定性分数：任何下降都是回归（规则打分无方差，实测两轮 Δ 全为 0）。 */
    public static boolean isDeterministicRegressed(int deltaDeterministic) {
        return deltaDeterministic < 0;
    }

    /**
     * Rubric 是否下降明显。<b>仅供参考，不参与回归判定</b> ——
     * 原因见类注释里那组实测数据。
     */
    public static boolean isRubricNoteworthy(int deltaRubric) {
        return deltaRubric <= -RUBRIC_NOTEWORTHY_DELTA;
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
        // 只有确定性分数参与判定。Rubric 是参考信息，理由见类注释。
        return isDeterministicRegressed(comparison.getDeltaDeterministic());
    }

    /**
     * 依据两个 delta 给出判定文案。
     *
     * <p>按原始 delta 而非 {@link BaselineManager.ComparisonReport} 取参：
     * {@code BaselineManager} 在算出 delta 的那一刻就要这句话，
     * 此时 {@code ComparisonReport} 尚未构造完成，传对象会迫使调用方先造一个半成品。
     *
     * @param deltaDeterministic 唯一参与判定的量
     * @param deltaRubric        仅用于文案提示，不影响 🔴/🟢/🟡 的归属
     */
    public static String verdictOf(int deltaDeterministic, int deltaRubric) {
        if (isDeterministicRegressed(deltaDeterministic)) {
            return "🔴 分数下降！改坏了，请检查最近的改动。";
        }
        if (deltaDeterministic > 0) {
            return "🟢 分数上升！改好了。";
        }
        // 确定性分数没变。Rubric 若有明显波动，提示一句供人工留意，但不改判定。
        if (isRubricNoteworthy(deltaRubric)) {
            return "🟡 分数持平（Rubric " + deltaRubric
                    + "，属 LLM 打分波动，不作为判定依据）。";
        }
        return "🟡 分数持平。";
    }

    /** 供报告与日志展示的判定口径说明。 */
    public static String describe() {
        return "回归只由确定性分数判定（任何下降即回归）；"
                + "Rubric 由 LLM 打分，实测同版本波动可达 ±13 分，仅作参考";
    }
}
