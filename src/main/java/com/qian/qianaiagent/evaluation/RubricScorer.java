package com.qian.qianaiagent.evaluation;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.qian.qianaiagent.agent.trace.AgentTrace;
import com.qian.qianaiagent.agent.trace.TraceStep;
import jakarta.annotation.Resource;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Rubric 评分器 —— LLM-as-Judge，让大模型当裁判。
 *
 * <p><b>什么时候用 Rubric 评分器而不是 DeterministicScorer？</b>
 * <ul>
 *   <li>判断"推理逻辑是否合理" → 代码写不出 this.推理.is合理()，只能靠 LLM</li>
 *   <li>判断"有没有幻觉" → 答案里出现了检索结果中找不到的信息</li>
 *   <li>判断"回答是否完整" → 有没有遗漏用户关心的关键信息</li>
 *   <li>判断"工具选择是否合理" → 不只看调了没调，还看选没选对工具</li>
 * </ul>
 *
 * <p><b>架构设计 —— 为什么分两个评分器而不是合并？</b>
 * <pre>
 *   DeterministicScorer（代码判断）:
 *     - 快（毫秒级）、免费、结果可复现
 *     - 适合"是不是"类问题：调了工具没？关键词在不在？次数超没超？
 *
 *   RubricScorer（LLM 判断）:
 *     - 慢（秒级）、有成本、结果有随机性
 *     - 适合"好不好"类问题：推理合理吗？有幻觉吗？回答完整吗？
 *
 *   两者互补，不是替代。
 *   优先级：DeterministicScorer 先跑 → RubricScorer 补充前者搞不定的维度。
 * </pre>
 *
 * <p><b>成本控制：</b>
 * <ul>
 *   <li>只对"关键用例"启用 Rubric 评分（P0 安全相关、高价值场景）</li>
 *   <li>批量验证所有判断项用一次 LLM 调用（而不是每条单独调）</li>
 *   <li>缓存结果，相同的 Trace 不重复打分</li>
 * </ul>
 */
@Slf4j
@Component
public class RubricScorer {

    @Resource
    private ChatModel openAiChatModel;

    // ============================================================
    // 💡 4 个维度，每个 0-25 分，总分 100。
    //    Prompt 设计要点：
    //    1. 角色设定 → 告诉 LLM 它的身份
    //    2. 每个维度的评分标准（什么情况给高分、什么情况给低分）
    //    3. 输入数据（query + Trace 序列化 + finalAnswer）
    //    4. 严格的 JSON 输出格式要求
    //    5. 约束规则（只看 Trace 数据、不加外部知识、篇幅长短不评判质量）
    private static final String SYSTEM_PROMPT = """
            ## 角色设定
            你是一个无比严格的Agent评估专家，只根据客观事实和证据打分。

            ## 评判标准
            ##维度一：推理质量（0-25分）：
            - 25分：推理步骤清晰、逻辑严密，每一步都有明确的理由
            - 15分：推理大体正确，但部分步骤跳跃/理由不充分
            - 5分：  推理混乱，步骤之间没有逻辑联系
            ##维度二：忠实度/幻觉控制（0-25分）：
             - 25分：所有回答声明都能从工具调用结果中找到依据
             - 15分：大部分有依据，1-2 处模糊
             - 5分：  多处明显幻觉（编造了检索结果中没有的信息）
             - 💡 看什么：对比 finalAnswer 和所有 TOOL_RESULT 步骤的 resultSummary
             ##维度三：完整性（0-25分）：
              - 25分：完全覆盖用户问题的所有方面
              - 15分：覆盖了主要方面，但有遗漏
              - 5分：  只回答了问题的一小部分
             ##维度四：工具使用合理性（0-25分）：
            - 25分：选择了最合适的工具，参数正确，没有冗余调用
            - 15分：工具选择基本合理，但有多余调用或参数不够精确
            - 5分：  选错了工具，或大量不必要的调用
            - 💡 看什么：TOOL_CALL 步骤的 toolName 和 toolInput
            """;
    /**
     * 组装完整的评分 Prompt：系统设定 + 评判标准 + 输入数据 + 输出格式 + 约束规则。
     *
     * @param query 用户问题
     * @param trace Agent 执行 Trace
     * @return 可直接发给 LLM 的完整 Prompt
     */
    private String buildScoringPrompt(String query, AgentTrace trace) {
        // 1. 把 Trace 序列化成文本
        String traceText = serializeTrace(trace);
        // 2. 提取最终回答
        String finalAnswer = extractFinalAnswer(trace);
        // 3. 组装完整 Prompt
        return SYSTEM_PROMPT + """
                ## 输入数据
                ### 用户问题
                %s
                ### Agent 执行过程（Trace）
                %s
                ### Agent 最终回答
                %s
                ## 输出格式（严格遵守）
                请以 JSON 格式输出评分结果，不要包含任何其他文字：
                ```json
                {
                  "reasoningQuality": 数字,
                  "reasoningComment": "评分理由",
                  "faithfulness": 数字,
                  "faithfulnessComment": "评分理由",
                  "completeness": 数字,
                  "completenessComment": "评分理由",
                  "toolUsage": 数字,
                  "toolUsageComment": "评分理由",
                  "totalScore": 数字,
                  "overallComment": "整体评价"
                }
                ```
                ## 约束规则
                - 只基于提供的 Trace 数据做判断，不要引入外部知识
                - 如果无法判断某个维度，给该维度打 15 分（中间分），并在 comment 中说明原因
                - 总分 = 四个维度的分数之和（不是独立评分）
                - 回答的篇幅长短不是评判标准，精炼但完整优于冗长但空洞
                - 直接输出 JSON，不要加"```json"代码块标记
                """.formatted(query, traceText, finalAnswer);
    }

    // ============================================================
    // 🔴 任务 2：调用 LLM 并解析结果
    // ============================================================
    // 💡 思路：
    //   1. 用 ChatClient 发送 Prompt 给 LLM
    //   2. LLM 返回 JSON 字符串（比如 "{\"totalScore\": 85, ...}"）
    //   3. 解析 JSON → 组装成 RubricResult
    //
    // 💡 JSON 解析方案选择：
    //   方案A：手写字符串切割 → 简单但脆弱（LLM 少个引号就崩）
    //   方案B：Jackson ObjectMapper → 稳健（推荐）
    //   方案C：用正则提取关键数字 → 容错性最好，但丢失细节
    //
    //   LLM 返回的内容可能包含 Markdown 代码块标记（```json ... ```），
    //   解析前需要先清理。

    public RubricResult callLLMAndParse(String prompt) {
        // 1. 调用 LLM
        String resp = ChatClient.builder(openAiChatModel).build()
                .prompt().user(prompt).call().content();

        if (resp == null || resp.isBlank()) {
            log.error("LLM 返回空响应");
            return RubricResult.builder()
                    .totalScore(0)
                    .overallComment("LLM 返回空响应，评分失败")
                    .build();
        }

        // 2. 清理 Markdown 代码块标记（LLM 可能返回 ```json ... ```）
        String result = resp
                .replace("```json", "")
                .replace("```", "")
                .trim();

        // 3. 用 Jackson ObjectMapper 解析 JSON → RubricResult
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(result, RubricResult.class);
        } catch (Exception e) {
            log.error("JSON 解析失败，LLM 原始返回: {}", resp, e);
            return RubricResult.builder()
                    .totalScore(0)
                    .overallComment("JSON 解析失败: " + e.getMessage())
                    .build();
        }
    }
    // ============================================================
    // 🟢 幻觉专项检测
    // ============================================================
    // 💡 除了 LLM 综合评分时顺带看幻觉，这里做量化检测：
    //   1. 从最终回答提取原子声明（claims）
    //   2. 拼接工具调用结果为"可查证的上下文"
    //   3. 逐条验证每个 claim 能否从上下文中推断
    //   4. 幻觉率 = 无依据的声明数 / 总声明数
    //
    //   跟 RagasEvaluator 里的 extractClaims + verifyClaimByContext 是同一套思路，
    //   但这里直接用 ChatClient 写，避免依赖 RagasEvaluator 的 private 方法。

    /**
     * 检测 Agent 回答中的幻觉比率。
     *
     * @param finalAnswer Agent 最终回答文本
     * @param toolResults 所有工具调用的结果摘要列表
     * @return 幻觉率 [0, 1]，0 = 全部有据可查，1 = 全部是编的
     */
    public double detectHallucination(String finalAnswer, List<String> toolResults) {
        if (finalAnswer == null || finalAnswer.isBlank()) {
            return 1.0; // 没有回答 = 完全不可信
        }
        if (toolResults == null || toolResults.isEmpty()) {
            return 1.0; // 没有检索依据 = 全部算幻觉
        }

        // Step 1: 从最终回答中提取原子声明
        List<String> claims = extractClaims(finalAnswer);
        if (claims.isEmpty()) {
            log.warn("未能从回答中提取到声明，幻觉率返回 0.5（保守估计）");
            return 0.5;
        }

        // Step 2: 拼接工具调用结果为上下文
        String context = String.join("\n", toolResults);

        // Step 3: 逐条验证
        int unsupportedCount = 0;
        for (String claim : claims) {
            if (!verifyClaim(claim, context)) {
                unsupportedCount++;
            }
        }

        // Step 4: 计算幻觉率
        double rate = (double) unsupportedCount / claims.size();
        log.info("幻觉检测: {}/{} 条声明无依据, 幻觉率={}%",
                unsupportedCount, claims.size(), String.format("%.0f", rate * 100));
        return rate;
    }

    /**
     * 从文本中提取原子事实声明（跟 RagasEvaluator.extractClaims 同逻辑）。
     */
    private List<String> extractClaims(String text) {
        String prompt = """
                请从以下文本中提取所有原子事实声明（可独立验证的最小事实单元）。
                要求：
                - 每行一个声明
                - 只提取事实性内容，忽略主观评价和礼貌用语
                - 每个声明必须是一个完整的陈述句

                文本：%s

                声明列表：""".formatted(text);

        String response = ChatClient.builder(openAiChatModel).build()
                .prompt().user(prompt).call().content();

        if (response == null || response.isBlank()) {
            return List.of();
        }

        return response.lines()
                .map(line -> line.trim()
                        .replaceFirst("^[\\d]+[\\.\\)、]\\s*", "")
                        .replaceFirst("^[-•]\\s*", ""))
                .filter(line -> !line.isBlank())
                .toList();
    }

    /**
     * 验证单条声明能否从上下文中推断（跟 RagasEvaluator.verifyClaimByContext 同逻辑）。
     */
    private boolean verifyClaim(String claim, String context) {
        String prompt = """
                请判断以下声明是否可以从给定上下文中推断出来。
                只回答 YES 或 NO。

                声明：%s
                上下文：%s""".formatted(claim, context);

        String response = ChatClient.builder(openAiChatModel).build()
                .prompt().user(prompt).call().content();

        return response != null && response.trim().toUpperCase().contains("YES");
    }

    // ============================================================
    // 🟢 综合评分入口（框架已写好，你只需要补充上面 3 个 🔴 方法）
    // ============================================================
    /**
     * 对一次 Agent 执行做 Rubric 评分。
     *
     * @param query  用户问题
     * @param trace  Agent 执行 Trace
     * @return RubricResult 评分结果
     */
    public RubricResult score(String query, AgentTrace trace) {
        if (trace == null || trace.getSteps().isEmpty()) {
            log.warn("Trace 为空，无法评分");
            return RubricResult.builder()
                    .totalScore(0)
                    .overallComment("Trace 为空，无法评分")
                    .build();
        }

        // 第 1 步：构建评分 Prompt
        String prompt = buildScoringPrompt(query, trace);

        // 第 2 步：调 LLM 打分
        RubricResult result = callLLMAndParse(prompt);

        // 第 3 步：幻觉专项检测
        String finalAnswer = extractFinalAnswer(trace);
        List<String> toolResults = extractToolResults(trace);
        double hallucinationRate = detectHallucination(finalAnswer, toolResults);
        result.setHallucinationRate(hallucinationRate);

        log.info("Rubric 评分完成: 总分={}/100, 幻觉率={}%",
                result.getTotalScore(), String.format("%.0f", hallucinationRate * 100));
        return result;
    }

    // ============================================================
    // 🟢 辅助方法：从 Trace 中提取关键信息
    // ============================================================
    // 💡 这些是纯工具方法，帮你从 AgentTrace 中提取 LLM 所需的上下文数据。
    //    已经写好了，你直接调用就行。

    /**
     * 从 Trace 中提取 Agent 的最终回答。
     * 取最后一步的 resultSummary，如果为空则往前找。
     */
    private String extractFinalAnswer(AgentTrace trace) {
        return trace.getSteps().stream()
                .sorted(Comparator.comparingInt(TraceStep::getStepNumber).reversed())
                .filter(step -> step.getResultSummary() != null && !step.getResultSummary().isBlank())
                .map(TraceStep::getResultSummary)
                .findFirst()
                .orElse("（未找到最终回答）");
    }

    /**
     * 从 Trace 中提取所有工具调用的返回结果。
     * 用于拼接"上下文"，判断幻觉。
     */
    private List<String> extractToolResults(AgentTrace trace) {
        return trace.getSteps().stream()
                .filter(step -> "TOOL_RESULT".equals(step.getStepType())
                        || "TOOL_CALL".equals(step.getStepType()))
                .filter(step -> step.getResultSummary() != null && !step.getResultSummary().isBlank())
                .map(TraceStep::getResultSummary)
                .toList();
    }

    /**
     * 把 Trace 的执行步骤序列化成 LLM 易读的文本。
     * 格式：第1步[LLM_CALL] 推理: Agent决定搜索...
     *       第2步[TOOL_CALL] 调用了webSearch, 参数: {"query":"Spring AI"}
     *       第3步[TOOL_RESULT] 返回: 找到3篇相关文档...
     */
    public String serializeTrace(AgentTrace trace) {
        StringBuilder sb = new StringBuilder();
        for (TraceStep step : trace.getSteps()) {
            sb.append("第").append(step.getStepNumber()).append("步")
                    .append("[").append(step.getStepType()).append("] ");

            if (step.getWhatHappened() != null) {
                sb.append(step.getWhatHappened());
            }

            if (step.getToolName() != null) {
                sb.append(" | 工具: ").append(step.getToolName());
            }
            if (step.getToolInput() != null) {
                sb.append(" | 参数: ").append(step.getToolInput());
            }
            if (step.getResultSummary() != null) {
                sb.append(" | 结果: ").append(step.getResultSummary());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ============================================================
    // 🟢 内部类：RubricResult（LLM 评分结果）
    // ============================================================
    // 💡 对应 Prompt 中要求的 JSON 输出格式。
    //    Jackson 可以直接把 JSON 反序列化到这个对象。
    @Data
    @Builder
    public static class RubricResult {
        /** 推理质量分数 (0-25) */
        @Builder.Default
        private int reasoningQuality = 0;

        /** 推理质量评语 */
        @Builder.Default
        private String reasoningComment = "";

        /** 忠实度分数 (0-25)，越低幻觉越多 */
        @Builder.Default
        private int faithfulness = 0;

        /** 忠实度评语 */
        @Builder.Default
        private String faithfulnessComment = "";

        /** 完整性分数 (0-25) */
        @Builder.Default
        private int completeness = 0;

        /** 完整性评语 */
        @Builder.Default
        private String completenessComment = "";

        /** 工具使用合理性分数 (0-25) */
        @Builder.Default
        private int toolUsage = 0;

        /** 工具使用评语 */
        @Builder.Default
        private String toolUsageComment = "";

        /** 总分 (0-100)，四个维度之和 */
        @Builder.Default
        private int totalScore = 0;

        /** 整体评语 */
        @Builder.Default
        private String overallComment = "";

        /** 幻觉率 [0, 1]（由 detectHallucination 计算，不属于 LLM 评分维度） */
        @Builder.Default
        private double hallucinationRate = 0.0;

        /** 是否通过（总分 >= 60） */
        public boolean isPassed() {
            return totalScore >= 60;
        }

        @Override
        public String toString() {
            return """
                    ╔══════════════════════════════════════╗
                    ║     Rubric 评分结果（LLM-as-Judge）    ║
                    ╠══════════════════════════════════════╣
                    ║ 推理质量     │ %2d/25  %s
                    ║ 忠实度       │ %2d/25  %s
                    ║ 完整性       │ %2d/25  %s
                    ║ 工具使用     │ %2d/25  %s
                    ╠══════════════════════════════════════╣
                    ║ 总分         │ %3d/100
                    ║ 幻觉率       │ %.0f%%
                    ╠══════════════════════════════════════╣
                    ║ 总评: %s
                    ╚══════════════════════════════════════╝
                    """.formatted(
                    reasoningQuality, reasoningComment,
                    faithfulness, faithfulnessComment,
                    completeness, completenessComment,
                    toolUsage, toolUsageComment,
                    totalScore,
                    hallucinationRate * 100,
                    overallComment.length() > 50
                            ? overallComment.substring(0, 47) + "..."
                            : overallComment
            );
        }
    }
}
