package com.qian.qianaiagent.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基线管理单测 —— 覆盖 "-1 哨兵 → 自动建基线" 与 "已有基线 → 对比" 两条路径。
 * 全程使用 @TempDir 隔离文件系统，不触碰仓库的真实 evaluation/baselines/。
 */
class BaselineManagerTest {

    private BaselineManager.Baseline caseWithScore(String name, int det, int rubric) {
        return BaselineManager.Baseline.builder()
                .caseName(name)
                .query("测试问题")
                .expectedBehavior(ExpectedBehavior.builder()
                        .expectedToolCalls(List.of(ExpectedBehavior.ToolCallExpectation.builder()
                                .toolName("searchWeb").paramContains("Spring AI").build()))
                        .expectedResponseKeywords(List.of("Spring AI"))
                        .maxToolCalls(5)
                        .build())
                .baselineDeterministicScore(det)
                .baselineRubricScore(rubric)
                .build();
    }

    private BaselineManager.EvaluationResult result(int det, int rubric) {
        return BaselineManager.EvaluationResult.builder()
                .deterministicScore(det).rubricScore(rubric).build();
    }

    @Test
    void missingFileReturnsNullBaseline(@TempDir Path dir) {
        BaselineManager manager = new BaselineManager(dir.toString());

        assertNull(manager.loadBaseline("不存在"));
    }

    @Test
    void missingFileComparisonReportsNoBaseline(@TempDir Path dir) {
        BaselineManager manager = new BaselineManager(dir.toString());

        BaselineManager.ComparisonReport report = manager.compareWithBaseline("不存在", result(80, 70));

        assertFalse(report.isHasBaseline());
        assertFalse(report.isNewBaseline());
        assertTrue(report.getSummary().contains("用例文件不存在"));
    }

    @Test
    void sentinelMinusOneAutoEstablishesBaseline(@TempDir Path dir) {
        BaselineManager manager = new BaselineManager(dir.toString());
        manager.saveBaseline(caseWithScore("用例A", -1, -1));

        BaselineManager.ComparisonReport report = manager.compareWithBaseline("用例A", result(85, 77));

        assertTrue(report.isNewBaseline());
        assertFalse(report.isHasBaseline());
        assertEquals(85, report.getNewDeterministicScore());

        // 基线已落盘为本次分数
        BaselineManager.Baseline saved = manager.loadBaseline("用例A");
        assertEquals(85, saved.getBaselineDeterministicScore());
        assertEquals(77, saved.getBaselineRubricScore());
    }

    @Test
    void autoEstablishPreservesCaseDefinition(@TempDir Path dir) {
        BaselineManager manager = new BaselineManager(dir.toString());
        manager.saveBaseline(caseWithScore("用例A", -1, -1));

        manager.compareWithBaseline("用例A", result(85, 77));

        BaselineManager.Baseline saved = manager.loadBaseline("用例A");
        assertEquals("测试问题", saved.getQuery());
        assertNotNull(saved.getExpectedBehavior());
        assertEquals("searchWeb", saved.getExpectedBehavior().getExpectedToolCalls().get(0).getToolName());
    }

    @Test
    void existingBaselineComparesAndComputesDelta(@TempDir Path dir) {
        BaselineManager manager = new BaselineManager(dir.toString());
        manager.saveBaseline(caseWithScore("用例B", 90, 80));

        BaselineManager.ComparisonReport report = manager.compareWithBaseline("用例B", result(70, 60));

        assertTrue(report.isHasBaseline());
        assertFalse(report.isNewBaseline());
        assertEquals(-20, report.getDeltaDeterministic());
        assertEquals(-20, report.getDeltaRubric());
        assertEquals(90, report.getBaselineDeterministicScore());
        assertEquals(70, report.getNewDeterministicScore());
        assertTrue(report.getSummary().contains("分数下降"));
    }

    @Test
    void improvedScoreIsReportedAsRise(@TempDir Path dir) {
        BaselineManager manager = new BaselineManager(dir.toString());
        manager.saveBaseline(caseWithScore("用例C", 70, 60));

        BaselineManager.ComparisonReport report = manager.compareWithBaseline("用例C", result(90, 85));

        assertTrue(report.getSummary().contains("分数上升"));
        assertEquals(20, report.getDeltaDeterministic());
    }

    @Test
    void equalScoreIsReportedAsFlat(@TempDir Path dir) {
        BaselineManager manager = new BaselineManager(dir.toString());
        manager.saveBaseline(caseWithScore("用例D", 80, 70));

        BaselineManager.ComparisonReport report = manager.compareWithBaseline("用例D", result(80, 70));

        assertTrue(report.getSummary().contains("持平"));
    }

    @Test
    void loadAllBaselinesReadsEveryJsonFile(@TempDir Path dir) throws Exception {
        BaselineManager manager = new BaselineManager(dir.toString());
        manager.saveBaseline(caseWithScore("用例甲", 80, 70));
        manager.saveBaseline(caseWithScore("用例乙", 60, 50));
        // 非 json 文件应被忽略
        Files.writeString(dir.resolve("readme.txt"), "忽略我");

        List<BaselineManager.Baseline> all = manager.loadAllBaselines();

        assertEquals(2, all.size());
    }

    @Test
    void caseNameIsSanitizedInFileName(@TempDir Path dir) {
        BaselineManager manager = new BaselineManager(dir.toString());
        manager.saveBaseline(caseWithScore("搜索/Spring AI 教程", 80, 70));

        // 路径分隔符被替换，不会穿越目录
        assertNotNull(manager.loadBaseline("搜索/Spring AI 教程"));
        assertEquals(1, dir.toFile().listFiles().length);
    }
}
