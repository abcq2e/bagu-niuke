package com.qian.qianaiagent.evaluation;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 稳定性测试器 —— 衡量 Agent"每次都跑通"的能力。
 *
 * <p><b>核心概念：</b>
 * <pre>
 *   pass^k  : 连续 k 次试验全部通过的概率 → 衡量<b>稳定性</b>
 *            "能不能每次都做对？"
 *
 *   pass@k  : k 次中至少 1 次通过的概率 → 衡量<b>峰值能力</b>
 *            "偶尔能不能做对一次？"
 *
 *   对生产级 Agent 来说，pass^k 远比 pass@k 重要。
 *   "偶尔能跑通"和"每次都跑通"天差地别。
 * </pre>
 *
 * <p><b>容忍阈值（来自文档）：</b>
 * <pre>
 *   关键决策类 Agent（金融、医疗、安全）: 容忍度 = 0%（必须全部通过）
 *   辅助分析类 Agent（搜索、总结、翻译）: 容忍度 ≤ 10%
 *   创意生成类 Agent（写作、绘图）     : 容忍度 ≤ 30%
 * </pre>
 *
 * <p><b>使用方式：</b>
 * <pre>
 *   StabilityTester tester = new StabilityTester(5); // 跑 5 次
 *   StabilityReport report = tester.test("搜索Java教程", () -> {
 *       AgentTrace trace = agent.run(query);
 *       return scorer.score(query, trace);
 *   });
 *   System.out.println(report);
 * </pre>
 */
@Slf4j
public class StabilityTester {

    /** 重复次数 */
    private final int runCount;

    public StabilityTester() {
        this(3); // 默认跑 3 次
    }

    public StabilityTester(int runCount) {
        if (runCount < 2) {
            throw new IllegalArgumentException("至少跑 2 次才能评估稳定性");
        }
        this.runCount = runCount;
    }

    // ============================================================
    // 🔴 任务 5（核心）：稳定性测试
    // ============================================================

    /**
     * 对同一个用例连续跑 N 次，统计稳定性和分数分布。
     *
     * @param caseName    用例名称
     * @param caseRunner  执行一次用例的函数（返回 ScoreResult 或 RubricResult）
     *                    用 Supplier 包装，每次 .get() 就跑一次
     * @return 稳定性报告
     */
    public StabilityReport test(String caseName, Supplier<TestResult> caseRunner) {
        List<TestResult> results = new ArrayList<>();
        int passCount = 0;
        int totalScoreSum = 0;

        log.info("=== 开始稳定性测试: {} (共 {} 次) ===", caseName, runCount);

        for (int i = 1; i <= runCount; i++) {
            log.info("第 {}/{} 次运行...", i, runCount);
            try {
                TestResult result = caseRunner.get();
                results.add(result);
                if (result.isPassed()) {
                    passCount++;
                }
                totalScoreSum += result.getTotalScore();
                log.info("  第 {} 次: 分数={}, 通过={}", i, result.getTotalScore(), result.isPassed());
            } catch (Exception e) {
                log.error("第 {} 次运行异常: {}", i, e.getMessage());
                results.add(TestResult.builder()
                        .totalScore(0)
                        .passed(false)
                        .errorMessage(e.getMessage())
                        .build());
            }
        }

        // 计算统计指标
        double passK = (double) passCount / runCount;           // pass^k
        double avgScore = (double) totalScoreSum / runCount;

        // 计算标准差（衡量分数波动）
        double variance = 0;
        for (TestResult r : results) {
            variance += Math.pow(r.getTotalScore() - avgScore, 2);
        }
        double stdDev = Math.sqrt(variance / runCount);

        // 判断是否稳定
        double failureRate = 1.0 - passK;
        boolean isStable = failureRate <= 0.1; // 失败率 ≤ 10% 算稳定

        log.info("=== 稳定性测试完成: pass^{} = {}/{} ({}%), 平均分={:.1f}, 标准差={:.1f} ===",
                runCount, passCount, runCount, String.format("%.0f", passK * 100),
                avgScore, stdDev);

        return StabilityReport.builder()
                .caseName(caseName)
                .runCount(runCount)
                .passCount(passCount)
                .passK(passK)
                .avgScore(avgScore)
                .stdDev(stdDev)
                .isStable(isStable)
                .results(results)
                .build();
    }

    // ============================================================
    // 数据模型
    // ============================================================

    /**
     * 单次测试结果。
     */
    @Data
    @Builder
    public static class TestResult {
        /** 总分 (0-100) */
        private int totalScore;

        /** 是否通过（分数 >= 60） */
        private boolean passed;

        /** 错误信息（如果这次运行抛异常了） */
        @Builder.Default
        private String errorMessage = "";

        public boolean isPassed() {
            return passed;
        }
    }

    /**
     * 稳定性报告。
     */
    @Data
    @Builder
    public static class StabilityReport {
        /** 用例名称 */
        private String caseName;

        /** 跑了多少次 */
        private int runCount;

        /** 通过次数 */
        private int passCount;

        /** pass^k = 通过次数 / 总次数 */
        private double passK;

        /** 平均分数 */
        private double avgScore;

        /** 分数标准差（越大越不稳定） */
        private double stdDev;

        /** 是否稳定 */
        private boolean isStable;

        /** 每次的详细结果 */
        @Builder.Default
        private List<TestResult> results = new ArrayList<>();

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("""
                    ╔══════════════════════════════════════╗
                    ║     稳定性测试报告: %s
                    ╠══════════════════════════════════════╣
                    ║ pass^%d = %d/%d (%.0f%%)
                    ║ 平均分   = %.1f
                    ║ 标准差   = %.1f
                    ║ 稳定性   = %s
                    ╠══════════════════════════════════════╣
                    """.formatted(
                    caseName,
                    runCount, passCount, runCount, passK * 100,
                    avgScore,
                    stdDev,
                    isStable ? "✅ 稳定（失败率 ≤ 10%）" : "❌ 不稳定（失败率 > 10%）"
            ));

            sb.append("║ 各次详情:\n");
            for (int i = 0; i < results.size(); i++) {
                TestResult r = results.get(i);
                String status = r.isPassed() ? "✅" : "❌";
                sb.append(String.format("║   第%d次: %s 分数=%d",
                        i + 1, status, r.getTotalScore()));
                if (!r.getErrorMessage().isEmpty()) {
                    sb.append(" 异常: ").append(r.getErrorMessage());
                }
                sb.append("\n");
            }
            sb.append("╚══════════════════════════════════════╝");
            return sb.toString();
        }
    }
}
