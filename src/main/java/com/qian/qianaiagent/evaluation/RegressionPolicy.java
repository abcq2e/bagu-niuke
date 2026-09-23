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
 * 连跑三轮评测，<b>代码一行没改</b>，Rubric 分数的波动是
 * <pre>
 *   多工具串联           60 → 50  → 50    (最大 Δ -10)
 *   对比SpringAI与LangChain4j  63 → 76  → 70    (最大 Δ +13)
 *   工具失败自愈          94 → 100 → 100   (最大 Δ +6)
 *   知识库RAG检索         55 → 60  → 60    (最大 Δ +5)
 *   文件读写往返         100 → 100 → 100   (Δ 0)
 * </pre>
 * <b>±13 分的漂移</b>，与要检测的信号同量级。让它参与阻断性判定，结果就是
 * 「每次运行都在报假警报」—— 而一个总在响的警报等于没有警报。
 * <b>因此它只展示、不判定。</b>
 *
 * <h2>⚠️ 但确定性分数也不是完全无方差，别把它想得太干净</h2>
 * 三轮里有两轮，{@code 对比SpringAI与LangChain4j} 与 {@code 知识库RAG检索}
 * 的确定性分数在 <b>90 ↔ 100 之间摆动</b>，来源是这条扣分：
 * <pre>
 *   [-10] 工具调用次数超标: 实际 12 次, 上限 8 次
 * </pre>
 * 也就是说：<b>打分规则本身是确定的，但喂给它的轨迹不是</b> ——
 * 工具调用几次由 LLM 决定，同样的问题每次可能不同。
 *
 * <p>影响：确定性分数偶尔会真的下降 10 分并触发一次 🔴。这<b>不是</b>「代码改坏了」，
 * 但在现有设计下会被当成回归。<b>它比 Rubric 的漂移更可接受</b> ——
 * 因为原因具体、可解释，指向一个真实的行为变化（Agent 多调了几次工具），
 * 而不是一次不可解释的 LLM 打分抖动。
 *
 * <p>若将来这个误报变得频繁，正确的解法是收紧评测的<b>轨迹</b>侧（重新标定用例的
 * {@code maxToolCalls} 预算，或把「轨迹相关」的扣分排除出回归判定），
 * 而不是回头去调 Rubric 的阈值。
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

    /**
     * 确定性分数：任何下降都是回归。
     *
     * <p>打分规则本身无方差；但**轨迹**由 LLM 决定，偶尔会变（例如工具调用次数超标
     * 触发 -10），此时会真的报一次回归。取舍见类注释「确定性分数也不是完全无方差」一节。
     */
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
