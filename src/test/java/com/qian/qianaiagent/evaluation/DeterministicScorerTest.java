package com.qian.qianaiagent.evaluation;

import com.qian.qianaiagent.agent.trace.AgentTrace;
import com.qian.qianaiagent.agent.trace.TraceStep;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 确定性评分器单测 —— 纯代码逻辑，不调 LLM、不访问网络。
 * 扣分规则：缺工具调用 -20、缺关键词 -20、调用次数超标 -10，下限 0。
 */
class DeterministicScorerTest {

    private final DeterministicScorer scorer = new DeterministicScorer();

    private AgentTrace traceOf(TraceStep... steps) {
        return AgentTrace.builder().steps(new ArrayList<>(List.of(steps))).build();
    }

    private TraceStep toolCall(int n, String toolName, String input, String result) {
        return TraceStep.builder()
                .stepNumber(n).stepType("TOOL_CALL")
                .toolName(toolName).toolInput(input).resultSummary(result)
                .build();
    }

    private TraceStep llmCall(int n, String answer) {
        return TraceStep.builder()
                .stepNumber(n).stepType("LLM_CALL").resultSummary(answer)
                .build();
    }

    private ExpectedBehavior expectedOneTool(String toolName, String param, List<String> keywords, int maxCalls) {
        return ExpectedBehavior.builder()
                .expectedToolCalls(List.of(ExpectedBehavior.ToolCallExpectation.builder()
                        .toolName(toolName).paramContains(param).build()))
                .expectedResponseKeywords(keywords)
                .maxToolCalls(maxCalls)
                .build();
    }

    /**
     * 只校验关键词、不校验工具的期望。
     * <p>注意 expectedToolCalls 必须为 null —— 传空 List 会让 checkToolCalls 进入循环，
     * 而轨迹里没有 TOOL_CALL 步骤时任何期望都匹配不上，会平白扣 20 分。
     */
    private ExpectedBehavior expectedKeywordsOnly(List<String> keywords, int maxCalls) {
        return ExpectedBehavior.builder()
                .expectedResponseKeywords(keywords)
                .maxToolCalls(maxCalls)
                .build();
    }

    @Test
    void fullMatchScores100() {
        AgentTrace trace = traceOf(
                toolCall(1, "searchWeb", "Spring AI 教程", "搜索结果"),
                llmCall(2, "Spring AI 的教程包含代码示例"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedOneTool("searchWeb", "Spring AI", List.of("Spring AI", "教程"), 5));

        assertEquals(100, r.getScore());
        assertTrue(r.isPassed());
        assertTrue(r.getDeductions().isEmpty());
    }

    @Test
    void missingToolCallDeducts20() {
        AgentTrace trace = traceOf(llmCall(1, "Spring AI 的教程"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedOneTool("searchWeb", "Spring AI", List.of(), 5));

        assertEquals(80, r.getScore());
        assertTrue(r.getDeductions().stream().anyMatch(d -> d.contains("缺少工具调用: searchWeb")));
    }

    @Test
    void wrongToolNameDeducts20() {
        // 这正是本次发现的历史 bug：基线写 webSearch，实际工具是 searchWeb
        AgentTrace trace = traceOf(
                toolCall(1, "searchWeb", "Spring AI", "结果"),
                llmCall(2, "回答"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedOneTool("webSearch", "Spring AI", List.of(), 5));

        assertEquals(80, r.getScore());
    }

    @Test
    void toolNameMatchIsCaseInsensitive() {
        AgentTrace trace = traceOf(toolCall(1, "SearchWeb", "Spring AI", "结果"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedOneTool("searchweb", "Spring AI", List.of(), 5));

        assertEquals(100, r.getScore());
    }

    @Test
    void paramMustContainKeyword() {
        AgentTrace trace = traceOf(toolCall(1, "searchWeb", "今天天气", "结果"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedOneTool("searchWeb", "Spring AI", List.of(), 5));

        assertEquals(80, r.getScore());
    }

    @Test
    void missingKeywordDeducts20() {
        AgentTrace trace = traceOf(llmCall(1, "这是一段无关的回答"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedKeywordsOnly(List.of("Spring AI", "教程"), 10));

        // 两个关键词都缺 → 扣 40
        assertEquals(60, r.getScore());
        assertEquals(2, r.getDeductions().size());
        assertTrue(r.getDeductions().get(0).contains("缺少关键词: Spring AI"));
    }

    @Test
    void keywordMatchIsCaseInsensitive() {
        AgentTrace trace = traceOf(llmCall(1, "spring ai 很好用"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedKeywordsOnly(List.of("Spring AI"), 10));

        assertEquals(100, r.getScore());
    }

    @Test
    void finalAnswerComesFromLastLlmCallNotToolResult() {
        // 尾部的 doTerminate 工具返回不应被当成最终回答
        AgentTrace trace = traceOf(
                llmCall(1, "Spring AI 教程在这"),
                toolCall(2, "doTerminate", "{}", "任务终止"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedKeywordsOnly(List.of("Spring AI"), 10));

        assertEquals(100, r.getScore());
    }

    @Test
    void exceedingMaxToolCallsDeducts10() {
        AgentTrace trace = traceOf(
                toolCall(1, "searchWeb", "Spring AI", "r"),
                toolCall(2, "searchWeb", "Spring AI", "r"),
                toolCall(3, "searchWeb", "Spring AI", "r"),
                toolCall(4, "searchWeb", "Spring AI", "r"),
                llmCall(5, "Spring AI 回答"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedOneTool("searchWeb", "Spring AI", List.of("Spring AI"), 3));

        assertEquals(90, r.getScore());
        assertTrue(r.getDeductions().stream().anyMatch(d -> d.contains("工具调用次数超标: 实际 4 次, 上限 3 次")));
    }

    @Test
    void scoreNeverGoesBelowZero() {
        AgentTrace trace = traceOf(llmCall(1, "无关回答"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedOneTool("searchWeb", null, List.of("A", "B", "C", "D", "E", "F"), 0));

        assertEquals(0, r.getScore());
        assertFalse(r.isPassed());
    }
}
