package com.qian.qianaiagent.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qian.qianaiagent.agent.BaseAgent;
import com.qian.qianaiagent.agent.trace.AgentTrace;
import com.qian.qianaiagent.evaluation.*;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

/**
 * Agent 评估体系演示 —— 把评估的 5 个阶段串起来跑一遍。
 *
 * <pre>
 *   流程：
 *   1. 加载 3 个评估用例（AgentEvalCase）
 *   2. 对每个用例：跑 Agent → 确定性评分 → Rubric 评分 → 保存/对比基线
 *   3. 选一个用例跑稳定性测试（pass^k）
 *   4. 输出完整评估报告
 * </pre>
 */
@Slf4j
public class AgentEvaluationDemo {

    private final BaseAgent agent;
    private final DeterministicScorer deterministicScorer;
    private final RubricScorer rubricScorer;
    private final BaselineManager baselineManager;
    private final StabilityTester stabilityTester;
    private final ObjectMapper objectMapper;

    public AgentEvaluationDemo(BaseAgent agent,
                               DeterministicScorer deterministicScorer,
                               RubricScorer rubricScorer) {
        this.agent = agent;
        this.deterministicScorer = deterministicScorer;
        this.rubricScorer = rubricScorer;
        this.baselineManager = new BaselineManager();
        this.stabilityTester = new StabilityTester(5);
        this.objectMapper = new ObjectMapper();
    }

    // ============================================================
    // 入口：跑一次完整的评估
    // ============================================================

    public void runFullEvaluation() {
        log.info("=== Agent 评估体系 完整演示 ===");

        // Step 1: 加载评估用例
        List<AgentEvalCase> cases = loadEvalCases();
        log.info("加载了 {} 个评估用例", cases.size());

        // Step 2: 对每个用例跑评估
        for (AgentEvalCase evalCase : cases) {
            log.info("--- 评估用例: {} ---", evalCase.getName());
            try {
                evaluateCase(evalCase);
            } catch (Exception e) {
                log.error("用例 [{}] 评估异常: {}", evalCase.getName(), e.getMessage());
            }
        }

        // Step 3: 稳定性测试（选第一个用例跑 5 次）
        if (!cases.isEmpty()) {
            runStabilityTest(cases.get(0));
        }

        // Step 4: 回归测试（对比基线）
        runRegressionCheck();

        log.info("=== 评估演示完成 ===");
    }

    // ============================================================
    // 单个用例评估
    // ============================================================

    private void evaluateCase(AgentEvalCase evalCase) throws Exception {
        log.info("用例: {} | 问题: {}", evalCase.getName(), evalCase.getPrompt());

        // 1. 跑 Agent
        log.info("  执行 Agent...");
        agent.run(evalCase.getPrompt());

        // 2. 从日志文件加载 AgentTrace（Agent 内部已保存到 logs/traces/）
        AgentTrace trace = loadLatestTrace();
        if (trace == null) {
            log.warn("  未找到 Trace 文件，跳过评分");
            return;
        }
        log.info("  Trace 加载成功: {} 步, 最终状态={}", trace.getSteps().size(), trace.getFinalState());

        // 3. 确定性评分
        DeterministicScorer.ScoreResult detResult = deterministicScorer.score(
                trace, evalCase.getExpectedBehavior());
        log.info("  确定性评分: {}/100 (通过={})", detResult.getScore(), detResult.isPassed());
        for (String deduction : detResult.getDeductions()) {
            log.info("    {}", deduction);
        }

        // 4. Rubric 评分（LLM-as-Judge，较慢）
        log.info("  Rubric 评分中（调用 LLM）...");
        RubricScorer.RubricResult rubricResult = rubricScorer.score(
                evalCase.getPrompt(), trace);
        log.info("  Rubric 评分: {}/100 (幻觉率={}%)",
                rubricResult.getTotalScore(),
                String.format("%.0f", rubricResult.getHallucinationRate() * 100));

        // 5. 与基线对比
        BaselineManager.EvaluationResult evalResult = BaselineManager.EvaluationResult.builder()
                .deterministicScore(detResult.getScore())
                .rubricScore(rubricResult.getTotalScore())
                .build();

        BaselineManager.ComparisonReport report = baselineManager.compareWithBaseline(
                evalCase.getName(), evalResult);
        log.info("  基线对比:\n{}", report);
    }

    // ============================================================
    // 稳定性测试
    // ============================================================

    private void runStabilityTest(AgentEvalCase evalCase) {
        log.info("--- 稳定性测试: {} (连续跑 5 次) ---", evalCase.getName());

        StabilityTester.StabilityReport report = stabilityTester.test(
                evalCase.getName(),
                () -> {
                    try {
                        // 跑 Agent
                        agent.run(evalCase.getPrompt());

                        // 加载 Trace
                        AgentTrace trace = loadLatestTrace();
                        if (trace == null) {
                            return StabilityTester.TestResult.builder()
                                    .totalScore(0)
                                    .passed(false)
                                    .errorMessage("Trace 加载失败")
                                    .build();
                        }

                        // 确定性评分
                        DeterministicScorer.ScoreResult detResult = deterministicScorer.score(
                                trace, evalCase.getExpectedBehavior());

                        return StabilityTester.TestResult.builder()
                                .totalScore(detResult.getScore())
                                .passed(detResult.isPassed())
                                .build();
                    } catch (Exception e) {
                        return StabilityTester.TestResult.builder()
                                .totalScore(0)
                                .passed(false)
                                .errorMessage(e.getMessage())
                                .build();
                    }
                });

        System.out.println(report);
    }

    // ============================================================
    // 回归测试
    // ============================================================

    private void runRegressionCheck() {
        log.info("--- 回归测试 ---");
        List<String> baselineNames = baselineManager.listBaselineNames();
        if (baselineNames.isEmpty()) {
            log.warn("没有基线数据。请先建立基线：跑一次 Agent → 人工确认 → baselineManager.saveBaseline()");
            return;
        }
        log.info("已有 {} 条基线: {}", baselineNames.size(), baselineNames);
        for (String name : baselineNames) {
            BaselineManager.Baseline baseline = baselineManager.loadBaseline(name);
            if (baseline.getBaselineDeterministicScore() < 0) {
                log.warn("  基线 [{}] 分数为 {}，尚未人工确认，跳过对比",
                        name, baseline.getBaselineDeterministicScore());
            }
        }
    }

    // ============================================================
    // 加载 Trace（从 Agent 保存的日志文件中读取最新的）
    // ============================================================

    private AgentTrace loadLatestTrace() {
        try {
            Path traceDir = Paths.get("logs/traces");
            if (!Files.exists(traceDir)) {
                log.warn("Trace 目录不存在: {}", traceDir);
                return null;
            }
            return Files.list(traceDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .map(p -> {
                        try {
                            return objectMapper.readValue(p.toFile(), AgentTrace.class);
                        } catch (IOException e) {
                            log.error("解析 Trace 文件失败: {}", p, e);
                            return null;
                        }
                    })
                    .orElse(null);
        } catch (IOException e) {
            log.error("加载 Trace 失败", e);
            return null;
        }
    }

    // ============================================================
    // 加载评估用例
    // ============================================================

    private List<AgentEvalCase> loadEvalCases() {
        // 用例 1：搜索 Spring AI 教程
        AgentEvalCase case1 = AgentEvalCase.builder()
                .name("搜索Spring AI教程")
                .prompt("帮我搜一下Spring AI最新教程，要有代码示例的那种")
                .expectedBehavior(ExpectedBehavior.builder()
                        .expectedToolCalls(List.of(
                                ExpectedBehavior.ToolCallExpectation.builder()
                                        .toolName("webSearch")
                                        .paramContains("Spring AI")
                                        .build()
                        ))
                        .expectedResponseKeywords(List.of("Spring AI", "教程", "代码"))
                        .maxToolCalls(5)
                        .build())
                .build();

        // 用例 2：读取代码文件并总结
        AgentEvalCase case2 = AgentEvalCase.builder()
                .name("读取代码文件并总结")
                .prompt("帮我读一下 WebSearchTool.java 这个文件，总结它的功能")
                .expectedBehavior(ExpectedBehavior.builder()
                        .expectedToolCalls(List.of(
                                ExpectedBehavior.ToolCallExpectation.builder()
                                        .toolName("fileRead")
                                        .paramContains("WebSearchTool")
                                        .build()
                        ))
                        .expectedResponseKeywords(List.of("WebSearchTool", "搜索", "工具"))
                        .maxToolCalls(3)
                        .build())
                .build();

        // 用例 3：知识库 RAG 检索
        AgentEvalCase case3 = AgentEvalCase.builder()
                .name("知识库RAG检索")
                .prompt("Spring AI 1.0 版本有哪些新特性？")
                .expectedBehavior(ExpectedBehavior.builder()
                        .expectedToolCalls(List.of(
                                ExpectedBehavior.ToolCallExpectation.builder()
                                        .toolName("ragSearch")
                                        .paramContains("Spring AI 1.0")
                                        .build()
                        ))
                        .expectedResponseKeywords(List.of("Spring AI", "1.0", "特性"))
                        .maxToolCalls(5)
                        .build())
                .build();

        return List.of(case1, case2, case3);
    }

    // ============================================================
    // main（通过 Spring 上下文启动）
    // ============================================================

    /**
     * 使用方式（在 Spring Boot 应用中）：
     * <pre>
     *   &#64;Autowired private ToolCallAgent agent;
     *   &#64;Autowired private DeterministicScorer deterministicScorer;
     *   &#64;Autowired private RubricScorer rubricScorer;
     *
     *   new AgentEvaluationDemo(agent, deterministicScorer, rubricScorer)
     *       .runFullEvaluation();
     * </pre>
     *
     * 基线建立后，更新基线分数：
     * <pre>
     *   BaselineManager manager = new BaselineManager();
     *   BaselineManager.Baseline baseline = manager.loadBaseline("搜索Spring AI教程");
     *   baseline.setBaselineDeterministicScore(85);  // 人工确认后的分数
     *   baseline.setBaselineRubricScore(80);
     *   baseline.setNotes("GPT-4o 2026-06 版本，人工确认通过");
     *   manager.saveBaseline(baseline);
     * </pre>
     */
    public static void main(String[] args) {
        log.info("请通过 Spring 上下文注入 Agent 和 Scorer 后使用，或参考 main 方法上的 JavaDoc 示例。");
    }
}
