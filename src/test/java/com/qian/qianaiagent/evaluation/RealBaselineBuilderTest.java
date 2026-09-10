package com.qian.qianaiagent.evaluation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qian.qianaiagent.agent.YuManus;
import com.qian.qianaiagent.agent.trace.AgentTrace;
import com.qian.qianaiagent.agent.trace.TraceStep;
import jakarta.annotation.Resource;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * 真实基线构建器（评估用，非常规单测）—— 跑真实的 YuManus Agent，产出细粒度轨迹，
 * 用 Deterministic + Rubric 双评分器打分，并把"证据 + 建议分数"完整打印出来，供人工确认。
 *
 * <p><b>为什么不是普通单测：</b>它会真实调用 LLM（DeepSeek）与工具（Tavily 搜索），
 * 有网络与 token 成本。请只在需要建立/更新基线时手动运行（IDEA 中直接 Run 该方法）。
 *
 * <p><b>人工确认流程（配合 {@link #confirmWebSearchBaseline()} 使用）：</b>
 * <pre>
 *   1. 运行本测试 → 控制台打印该用例的：最终回答 + 扣分明细 + Rubric 四维分 + 建议 det/rubric 分
 *   2. 人工查看"最终回答"是否确实正确、是否真的调用了期望工具
 *   3. 确认后，由人来落盘：把建议分数写进 evaluation/baselines/*.json
 * </pre>
 */
@Slf4j
@SpringBootTest
class RealBaselineBuilderTest {

    @Resource
    private ObjectProvider<YuManus> yuManusProvider;

    @Resource
    private RubricScorer rubricScorer;

    // 每次运行打分结果的数据载体，便于打印
    @Builder
    public record CaseResult(String caseName,
                             String prompt,
                             AgentTrace trace,
                             DeterministicScorer.ScoreResult det,
                             RubricScorer.RubricResult rubric,
                             String finalAnswer) {
    }

    // ============================================================
    // 用例 1（web 搜索）—— 真实建基线的第一个用例
    // ============================================================

    @Test
    void buildRealBaseline_webSearch() throws Exception {
        CaseSpec spec = CaseSpec.builder()
                .caseName("搜索SpringAI官方文档")
                .prompt("请使用搜索引擎（searchWeb 工具）查找 Spring AI 官方文档，"
                        + "并告诉我官方文档中大致包含哪些内容模块，用两三句话概括即可。")
                .expected(ExpectedBehavior.builder()
                        .expectedToolCalls(List.of(
                                ExpectedBehavior.ToolCallExpectation.builder()
                                        .toolName("searchWeb")
                                        .paramContains("Spring AI")
                                        .build()
                        ))
                        .expectedResponseKeywords(List.of("Spring AI"))
                        .maxToolCalls(5)
                        .build())
                .build();

        CaseResult result = runCase(spec);
        printEvidence(result);

        // ⚠️ 不自动落盘：分数是否正确、回答是否合格，需要人工确认后再 saveBaseline。
    }

    // ============================================================
    // 核心：跑一次真实 Agent 并评分
    // ============================================================

    private CaseResult runCase(CaseSpec spec) throws IOException {
        log.info("══════════════════════════════════════════════");
        log.info("开始真实评估用例：{}", spec.caseName);
        log.info("Prompt：{}", spec.prompt);

        // 每个用例拿一个全新的 Agent 实例（BaseAgent 要求从 IDLE 状态启动）
        YuManus agent = yuManusProvider.getObject();
        agent.run(spec.prompt);

        // 加载本次刚保存的轨迹（yuManus_{时间戳}.json，取最新）
        AgentTrace trace = loadLatestTrace("yuManus");
        if (trace == null || trace.getSteps().isEmpty()) {
            throw new IllegalStateException("未找到 YuManus 的轨迹文件（logs/traces/yuManus_*.json）");
        }
        log.info("轨迹已加载：共 {} 步", trace.getSteps().size());

        // 确定性评分（纯代码，快）
        DeterministicScorer detScorer = new DeterministicScorer();
        DeterministicScorer.ScoreResult det = detScorer.score(trace, spec.expected);

        // Rubric 评分（LLM-as-Judge，含幻觉专项检测）
        RubricScorer.RubricResult rubric = rubricScorer.score(spec.prompt, trace);

        String finalAnswer = extractFinalAnswer(trace);
        return CaseResult.builder()
                .caseName(spec.caseName)
                .prompt(spec.prompt)
                .trace(trace)
                .det(det)
                .rubric(rubric)
                .finalAnswer(finalAnswer)
                .build();
    }

    // ============================================================
    // 打印完整证据，供人工确认
    // ============================================================

    private void printEvidence(CaseResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n")
                .append("╔══════════════════════════════════════════════════════════╗\n")
                .append("║ 用例 [").append(r.caseName()).append("] 评估证据（请人工确认）\n")
                .append("╠══════════════════════════════════════════════════════════╣\n")
                .append("▶ 一、Agent 执行轨迹（细粒度）\n");
        for (TraceStep s : r.trace().getSteps()) {
            sb.append("    ").append(s.getStepNumber()).append(". [").append(s.getStepType()).append("]");
            if (s.getToolName() != null) {
                sb.append(" 工具=").append(s.getToolName());
            }
            if (s.getToolInput() != null) {
                sb.append(" 入参=").append(s.getToolInput());
            }
            if (s.getResultSummary() != null) {
                sb.append(" 结果=").append(s.getResultSummary());
            }
            sb.append("\n");
        }

        sb.append("\n▶ 二、最终回答（请重点人工核对此处是否真实正确）\n")
                .append(r.finalAnswer()).append("\n\n");

        sb.append("▶ 三、确定性评分：").append(r.det().getScore()).append("/100")
                .append(r.det().isPassed() ? " ✅通过" : " ❌未通过").append("\n");
        for (String d : r.det().getDeductions()) {
            sb.append("      - ").append(d).append("\n");
        }

        sb.append("▶ 四、Rubric 评分(LLM裁判)：").append(r.rubric().getTotalScore()).append("/100\n");
        sb.append("      推理 ").append(r.rubric().getReasoningQuality()).append("/25  ")
                .append(r.rubric().getReasoningComment()).append("\n")
                .append("      忠实 ").append(r.rubric().getFaithfulness()).append("/25  ")
                .append(r.rubric().getFaithfulnessComment()).append("\n")
                .append("      完整 ").append(r.rubric().getCompleteness()).append("/25  ")
                .append(r.rubric().getCompletenessComment()).append("\n")
                .append("      工具 ").append(r.rubric().getToolUsage()).append("/25  ")
                .append(r.rubric().getToolUsageComment()).append("\n")
                .append("      幻觉率 ").append(String.format("%.0f%%", r.rubric().getHallucinationRate() * 100)).append("\n")
                .append("      总评 ").append(r.rubric().getOverallComment()).append("\n\n");

        sb.append("▶ 五、建议写入基线的分数（人工确认后落盘）：\n")
                .append("      caseName = ").append(r.caseName()).append("\n")
                .append("      baselineDeterministicScore = ").append(r.det().getScore()).append("\n")
                .append("      baselineRubricScore = ").append(r.rubric().getTotalScore()).append("\n")
                .append("╚══════════════════════════════════════════════════════════╝\n");

        // 同时写入文件，避免 Windows 控制台编码问题导致中文证据乱码
        try {
            Path evalDir = Paths.get("logs", "eval");
            Files.createDirectories(evalDir);
            Path out = evalDir.resolve(r.caseName() + ".txt");
            Files.writeString(out, sb.toString());
            log.info("评估证据已写入：{}", out.toAbsolutePath());
        } catch (IOException e) {
            log.warn("评估证据写文件失败：{}", e.getMessage());
        }

        log.info(sb.toString());
        System.out.println(sb);
    }

    // ============================================================
    // 工具方法
    // ============================================================

    /** 兼容 hutool 序列化（时间存 epoch 毫秒数字）与 Jackson 标准（ISO 字符串）的 LocalDateTime 反序列化器 */
    private ObjectMapper traceObjectMapper() {
        SimpleModule timeModule = new SimpleModule();
        timeModule.addDeserializer(LocalDateTime.class, new JsonDeserializer<>() {
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                    long ms = p.getLongValue();
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
                }
                return LocalDateTime.parse(p.getText());
            }
        });
        return new ObjectMapper().registerModule(new JavaTimeModule()).registerModule(timeModule);
    }

    /** 从 logs/traces 读取指定 Agent 最新保存的一条轨迹 */
    private AgentTrace loadLatestTrace(String agentPrefix) throws IOException {
        ObjectMapper objectMapper = traceObjectMapper();
        Path traceDir = Paths.get("logs/traces");
        if (!Files.exists(traceDir)) {
            return null;
        }
        List<File> files;
        try (var stream = Files.list(traceDir)) {
            files = stream
                    .filter(p -> p.getFileName().toString().startsWith(agentPrefix + "_"))
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(Path::toFile)
                    .sorted(Comparator.comparingLong(File::lastModified))
                    .toList();
        }
        if (files.isEmpty()) {
            return null;
        }
        File latest = files.get(files.size() - 1);
        log.info("读取轨迹文件：{}", latest.getAbsolutePath());
        return objectMapper.readValue(latest, AgentTrace.class);
    }

    /** 取最终回答：优先最后一个 LLM_CALL（doTerminate 的工具返回不算），老轨迹兜底取最后非空 */
    private String extractFinalAnswer(AgentTrace trace) {
        return trace.getSteps().stream()
                .filter(s -> "LLM_CALL".equals(s.getStepType()))
                .sorted(Comparator.comparingInt(TraceStep::getStepNumber).reversed())
                .filter(s -> s.getResultSummary() != null && !s.getResultSummary().isBlank())
                .map(TraceStep::getResultSummary)
                .findFirst()
                .orElseGet(() -> trace.getSteps().stream()
                        .sorted(Comparator.comparingInt(TraceStep::getStepNumber).reversed())
                        .filter(s -> s.getResultSummary() != null && !s.getResultSummary().isBlank())
                        .map(TraceStep::getResultSummary)
                        .findFirst()
                        .orElse("（未找到最终回答）"));
    }

    /** 用例描述 */
    @Builder
    private record CaseSpec(String caseName, String prompt, ExpectedBehavior expected) {
    }
}
