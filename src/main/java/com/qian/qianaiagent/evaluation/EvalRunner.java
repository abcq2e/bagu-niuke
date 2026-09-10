package com.qian.qianaiagent.evaluation;

import com.qian.qianaiagent.agent.YuManus;
import com.qian.qianaiagent.agent.trace.AgentTrace;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 离线回归评测入口 —— 项目里唯一的"跑一次评测集"的地方。
 *
 * <p><b>怎么触发：</b>
 * <pre>
 *   全量跑（3 个用例）：        mvn spring-boot:run -Peval
 *   含稳定性测试（连跑 5 次）：  mvn spring-boot:run -Peval -Dspring-boot.run.arguments=--qian.eval.stability=5
 * </pre>
 *
 * <p><b>做什么：</b>从 {@code evaluation/baselines/} 读出用例（用例定义与基线分数同文件），
 * 逐个跑真实 Agent → 确定性评分 + Rubric 评分 → 与基线对比（无基线则自动建立）→ 渲染报告。
 *
 * <p><b>为什么用 {@code @ConditionalOnProperty}：</b>正常启动应用时这个 Bean 根本不存在，
 * 不会因为误触发而消耗 LLM 调用。只有 eval profile 才把它打开。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "qian.eval.enabled", havingValue = "true")
public class EvalRunner implements CommandLineRunner {

    /** 报告落盘目录（同时打印到控制台，避免 Windows 控制台中文乱码） */
    private static final String REPORT_DIR = "logs/eval";

    @Resource
    private ObjectProvider<YuManus> yuManusProvider;

    @Resource
    private DeterministicScorer deterministicScorer;

    @Resource
    private RubricScorer rubricScorer;

    @Resource
    private BaselineManager baselineManager;

    private final ApplicationContext applicationContext;

    /** 稳定性测试重复次数，0 = 不跑 */
    @Value("${qian.eval.stability:0}")
    private int stabilityRuns;

    public EvalRunner(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) {
        log.info("════ 离线评估开始 ════");
        EvalReport report = evaluate();
        String rendered = report.render();

        System.out.println(rendered);
        writeReportFile(rendered);

        int exitCode = report.exitCode();
        log.info("════ 离线评估结束，退出码 {} ════", exitCode);

        // 评测跑完就该退出，不能让 Web 服务挂着
        SpringApplication.exit(applicationContext, () -> exitCode);
        System.exit(exitCode);
    }

    // ============================================================
    // 编排
    // ============================================================

    private EvalReport evaluate() {
        long start = System.currentTimeMillis();

        List<BaselineManager.Baseline> cases = baselineManager.loadAllBaselines();
        log.info("加载了 {} 个评估用例", cases.size());

        List<EvalReport.CaseOutcome> outcomes = new ArrayList<>();
        for (BaselineManager.Baseline evalCase : cases) {
            outcomes.add(runOneCase(evalCase));
        }

        StabilityTester.StabilityReport stability = null;
        if (stabilityRuns >= 2 && !cases.isEmpty()) {
            // 用第一条用例做稳定性样本；顺序由文件名决定，不额外挑拣
            stability = runStabilityTest(cases.get(0));
        }

        return EvalReport.builder()
                .generatedAt(LocalDateTime.now())
                .outcomes(outcomes)
                .stability(stability)
                .elapsedMs(System.currentTimeMillis() - start)
                .build();
    }

    /** 跑单个用例。任何异常都被收敛成"该用例失败"，绝不让整轮评测中断。 */
    private EvalReport.CaseOutcome runOneCase(BaselineManager.Baseline evalCase) {
        String caseName = evalCase.getCaseName();
        log.info("▶ 用例 [{}]：{}", caseName, evalCase.getQuery());

        try {
            // 每个用例取全新实例（BaseAgent 要求从 IDLE 状态启动）
            YuManus agent = yuManusProvider.getObject();
            agent.run(evalCase.getQuery());
            AgentTrace trace = agent.getCurrentTrace();

            if (trace == null || trace.getEndTime() == null
                    || trace.getSteps() == null || trace.getSteps().isEmpty()) {
                log.warn("⚠️ 用例 [{}] 轨迹不完整，跳过评分", caseName);
                return EvalReport.CaseOutcome.builder()
                        .caseName(caseName).query(evalCase.getQuery())
                        .scored(false).skipReason("轨迹不完整（endTime 为空或无步骤）")
                        .build();
            }

            DeterministicScorer.ScoreResult det =
                    deterministicScorer.score(trace, evalCase.getExpectedBehavior());
            log.info("  确定性评分 {}/100", det.getScore());

            int rubricScore = 0;
            boolean rubricFailed = false;
            try {
                rubricScore = rubricScorer.score(evalCase.getQuery(), trace).getTotalScore();
                log.info("  Rubric 评分 {}/100", rubricScore);
            } catch (Exception e) {
                rubricFailed = true;
                log.warn("  Rubric 评分失败（保留确定性分）: {}", e.getMessage());
            }

            BaselineManager.EvaluationResult result = BaselineManager.EvaluationResult.builder()
                    .deterministicScore(det.getScore())
                    .rubricScore(rubricScore)
                    .build();
            BaselineManager.ComparisonReport comparison =
                    baselineManager.compareWithBaseline(caseName, result);

            return EvalReport.CaseOutcome.builder()
                    .caseName(caseName).query(evalCase.getQuery())
                    .scored(true)
                    .deterministicScore(det.getScore())
                    .rubricScore(rubricScore)
                    .passed(det.isPassed() && !rubricFailed)
                    .deductions(det.getDeductions())
                    .comparison(comparison)
                    .build();

        } catch (Exception e) {
            log.error("❌ 用例 [{}] 执行异常: {}", caseName, e.getMessage(), e);
            return EvalReport.CaseOutcome.builder()
                    .caseName(caseName).query(evalCase.getQuery())
                    .scored(false).skipReason("执行异常")
                    .error(e.getMessage())
                    .build();
        }
    }

    private StabilityTester.StabilityReport runStabilityTest(BaselineManager.Baseline evalCase) {
        log.info("▶ 稳定性测试 [{}]：连跑 {} 次", evalCase.getCaseName(), stabilityRuns);

        StabilityTester tester = new StabilityTester(stabilityRuns);
        return tester.test(evalCase.getCaseName(), () -> {
            try {
                YuManus agent = yuManusProvider.getObject();
                agent.run(evalCase.getQuery());
                AgentTrace trace = agent.getCurrentTrace();

                if (trace == null || trace.getSteps() == null || trace.getSteps().isEmpty()) {
                    return StabilityTester.TestResult.builder()
                            .totalScore(0).passed(false).errorMessage("轨迹为空").build();
                }

                DeterministicScorer.ScoreResult det =
                        deterministicScorer.score(trace, evalCase.getExpectedBehavior());
                return StabilityTester.TestResult.builder()
                        .totalScore(det.getScore()).passed(det.isPassed()).build();
            } catch (Exception e) {
                return StabilityTester.TestResult.builder()
                        .totalScore(0).passed(false).errorMessage(e.getMessage()).build();
            }
        });
    }

    // ============================================================
    // 报告落盘
    // ============================================================

    /** 报告写文件，避免 Windows 控制台编码问题导致中文乱码 */
    private void writeReportFile(String rendered) {
        try {
            Path dir = Paths.get(REPORT_DIR);
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path out = dir.resolve("报告_" + stamp + ".txt");
            Files.writeString(out, rendered);
            log.info("📄 评估报告已写入：{}", out.toAbsolutePath());
        } catch (IOException e) {
            log.warn("写入评估报告失败：{}", e.getMessage());
        }
    }
}
