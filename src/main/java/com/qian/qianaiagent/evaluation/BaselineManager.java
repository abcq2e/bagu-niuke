package com.qian.qianaiagent.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

/**
 * 基线管理器 —— 保存、加载、对比评估基线。
 *
 * <p><b>基线是什么？</b>
 * <pre>
 *   对一个用例"手工跑一次 + 人工确认结果正确" → 保存为基线（快照）。
 *   以后每次改代码后跑同一个用例 → 拿新分数跟基线对比 → 分数跌了说明改坏了。
 * </pre>
 *
 * <p><b>数据存储：</b>
 * <pre>
 *   每个基线存为一个 JSON 文件，放在 evaluation/baselines/ 目录下。
 *   文件名 = 用例名称（安全化处理，去掉特殊字符）.json
 *
 *   示例：evaluation/baselines/搜索Java教程.json
 *   {
 *     "caseName": "搜索Java教程",
 *     "query": "帮我搜一下Spring AI最新教程",
 *     "createdAt": "2026-06-24T10:00:00",
 *     "expectedBehavior": { ... },
 *     "baselineDeterministicScore": 90,
 *     "baselineRubricScore": 85
 *   }
 * </pre>
 *
 * <p><b>什么时候更新基线？</b>
 * <ul>
 *   <li>模型升级了（GPT-4 → GPT-5）→ 重新建立基线</li>
 *   <li>Prompt 大改 → 旧的基线可能不再适用</li>
 *   <li>新增了工具 → 旧用例的预期行为可能变化</li>
 *   <li>⚠️ 不要因为"分数跌了"就改基线来让分数好看——那是掩耳盗铃</li>
 * </ul>
 */
@Slf4j
public class BaselineManager {

    /** 基线文件存储目录 */
    public static final String DEFAULT_BASELINES_DIR = "evaluation/baselines/";

    private final ObjectMapper objectMapper;
    private final Path baselinesDir;

    public BaselineManager() {
        this(DEFAULT_BASELINES_DIR);
    }

    public BaselineManager(String baselinesDirPath) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.baselinesDir = Paths.get(baselinesDirPath);
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(baselinesDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建基线目录: " + baselinesDir, e);
        }
    }

    // ============================================================
    // 保存基线
    // ============================================================

    /**
     * 保存一个基线快照。
     *
     * @param baseline 基线数据
     */
    public void saveBaseline(Baseline baseline) {
        Path filePath = toFilePath(baseline.getCaseName());
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(baseline);
            Files.writeString(filePath, json);
            log.info("基线已保存: {} → {}", baseline.getCaseName(), filePath);
        } catch (IOException e) {
            throw new RuntimeException("保存基线失败: " + filePath, e);
        }
    }

    // ============================================================
    // 加载基线
    // ============================================================

    /**
     * 根据用例名称加载单个基线。
     *
     * @param caseName 用例名称
     * @return 基线，不存在则返回 null
     */
    public Baseline loadBaseline(String caseName) {
        Path filePath = toFilePath(caseName);
        if (!Files.exists(filePath)) {
            log.warn("基线文件不存在: {}", filePath);
            return null;
        }
        try {
            String json = Files.readString(filePath);
            return objectMapper.readValue(json, Baseline.class);
        } catch (IOException e) {
            throw new RuntimeException("加载基线失败: " + filePath, e);
        }
    }

    /**
     * 加载所有基线。
     *
     * @return 所有基线的列表
     */
    public List<Baseline> loadAllBaselines() {
        List<Baseline> baselines = new ArrayList<>();
        File dir = baselinesDir.toFile();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) {
            return baselines;
        }
        for (File file : files) {
            try {
                String json = Files.readString(file.toPath());
                baselines.add(objectMapper.readValue(json, Baseline.class));
            } catch (IOException e) {
                log.error("加载基线文件失败: {}", file.getName(), e);
            }
        }
        log.info("加载了 {} 条基线", baselines.size());
        return baselines;
    }

    /**
     * 列出所有已保存的基线名称。
     */
    public List<String> listBaselineNames() {
        File dir = baselinesDir.toFile();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) {
            return List.of();
        }
        return Stream.of(files)
                .map(f -> f.getName().replace(".json", ""))
                .toList();
    }

    // ============================================================
    // 对比新结果与基线
    // ============================================================

    /**
     * 将新的评分结果与基线对比，输出差异报告。
     *
     * @param caseName     用例名称
     * @param newResult    新跑出来的评分结果
     * @return 对比报告
     */
    public ComparisonReport compareWithBaseline(String caseName, EvaluationResult newResult) {
        Baseline baseline = loadBaseline(caseName);
        if (baseline == null) {
            return ComparisonReport.builder()
                    .caseName(caseName)
                    .hasBaseline(false)
                    .summary("⚠️ 没有基线数据，无法对比。请先建立基线。")
                    .build();
        }

        int deltaDeterministic = newResult.getDeterministicScore() - baseline.getBaselineDeterministicScore();
        int deltaRubric = newResult.getRubricScore() - baseline.getBaselineRubricScore();

        String verdict;
        if (deltaDeterministic < 0 || deltaRubric < 0) {
            verdict = "🔴 分数下降！改坏了，请检查最近的改动。";
        } else if (deltaDeterministic > 0 || deltaRubric > 0) {
            verdict = "🟢 分数上升！改好了。";
        } else {
            verdict = "🟡 分数持平。";
        }

        return ComparisonReport.builder()
                .caseName(caseName)
                .hasBaseline(true)
                .deltaDeterministic(deltaDeterministic)
                .deltaRubric(deltaRubric)
                .baselineDeterministicScore(baseline.getBaselineDeterministicScore())
                .baselineRubricScore(baseline.getBaselineRubricScore())
                .newDeterministicScore(newResult.getDeterministicScore())
                .newRubricScore(newResult.getRubricScore())
                .summary(verdict)
                .build();
    }

    // ============================================================
    // 工具方法
    // ============================================================

    /** 用例名称 → 文件路径 */
    private Path toFilePath(String caseName) {
        // 安全化文件名：只保留中文、字母、数字
        String safeName = caseName.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", "_");
        return baselinesDir.resolve(safeName + ".json");
    }

    // ============================================================
    // 数据模型
    // ============================================================

    /**
     * 基线数据 —— 一个人工确认过的"正确答案"快照。
     */
    @Data
    @Builder
    public static class Baseline {
        /** 用例名称 */
        private String caseName;

        /** 用户问题 */
        private String query;

        /** 创建时间 */
        @Builder.Default
        private LocalDateTime createdAt = LocalDateTime.now();

        /** 期望行为 */
        private ExpectedBehavior expectedBehavior;

        /** 基线确定性评分（人工确认过的分数） */
        private int baselineDeterministicScore;

        /** 基线 Rubric 评分（人工确认过的分数） */
        private int baselineRubricScore;

        /** 备注（如"基于 GPT-4o 2026-06-24 版本建立"） */
        private String notes;
    }

    /**
     * 对比报告 —— 新结果 vs 基线的差异。
     */
    @Data
    @Builder
    public static class ComparisonReport {
        private String caseName;
        private boolean hasBaseline;
        private int deltaDeterministic;
        private int deltaRubric;
        private int baselineDeterministicScore;
        private int baselineRubricScore;
        private int newDeterministicScore;
        private int newRubricScore;
        private String summary;

        @Override
        public String toString() {
            if (!hasBaseline) {
                return summary;
            }
            return """
                    ╔══════════════════════════════════════╗
                    ║     回归测试对比报告: %s
                    ╠══════════════════════════════════════╣
                    ║ 确定性评分 │ %3d → %3d  (%+d)
                    ║ Rubric评分 │ %3d → %3d  (%+d)
                    ╠══════════════════════════════════════╣
                    ║ %s
                    ╚══════════════════════════════════════╝
                    """.formatted(
                    caseName,
                    baselineDeterministicScore, newDeterministicScore, deltaDeterministic,
                    baselineRubricScore, newRubricScore, deltaRubric,
                    summary
            );
        }
    }

    /**
     * 统一的评估结果（确定性 + Rubric 两个分数）。
     */
    @Data
    @Builder
    public static class EvaluationResult {
        private int deterministicScore;
        private int rubricScore;
    }
}
