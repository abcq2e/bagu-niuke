package com.qian.qianaiagent.evaluation;

import com.qian.qianaiagent.agent.trace.AgentTrace;
import com.qian.qianaiagent.agent.trace.TraceStep;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 确定性评分器 —— 纯代码逻辑，不调 LLM，快且便宜。
 *
 * <p>评分规则：满分 100，扣分制。每个检查不通过扣相应的分数。
 * 最后返回一个 {@link }，包含总分和扣分明细。
 */
@Slf4j
public class DeterministicScorer {

    // ============================================================
    // 扣分权重（你来定，但要能解释"为什么扣这么多"）
    // ============================================================

    // 🔴 你的代码：定义扣分常量
    // private static final int PENALTY_TOOL_CALL_MISSING = ___;   // 缺少期望的工具调用，扣几分？
    // private static final int PENALTY_PARAM_MISMATCH    = ___;   // 工具参数不符合预期
    // private static final int PENALTY_KEYWORD_MISSING   = ___;   // 回答缺少关键词
    // private static final int PENALTY_TOO_MANY_CALLS    = ___;   // 工具调用次数超标
    private static final int PENALTY_TOOL_CALL_MISSING = 20;
    private static final int PENALTY_PARAM_MISMATCH = 10;
    private static final int PENALTY_KEYWORD_MISSING = 20;
    private static final int PENALTY_TOO_MANY_CALLS = 10;
    // ============================================================
    // 🔴 任务 1：检查工具调用是否匹配
    // ============================================================
    // 💡 思路：
    //   1. 从 AgentTrace 的 steps 中，找出所有"工具调用"类型的步骤
    //   2. 遍历 expectedBehavior.getExpectedToolCalls()
    //   3. 对每个期望 → 检查是否在 Trace 中找到匹配的工具调用
    //   4. 匹配条件：工具名一样 && 参数包含 paramContains && 结果包含 resultContains
    //
    // 📖 提示：
    //   - TraceStep 有 .getStepType() 可以判断是不是工具调用步骤
    //   - TraceStep 有 .getWhatHappened() 包含工具调用的描述文字
    //   - String.contains() 可以做关键词匹配（大小写不想敏感 → .toLowerCase()）
    //
    // 🔴 你的代码 ↓
    // public List<String> checkToolCalls(AgentTrace trace, ExpectedBehavior expected) {
    //     List<String> failures = new ArrayList<>();
    //     // 1. 从 trace 收集所有工具调用的名称和内容
    //     // 2. 逐个检查 expected 中的期望
    //     // 3. 不通过 → failures.add("缺少工具调用: webSearch")
    //     return failures;
    // }
    public List<String> checkToolCalls(AgentTrace trace, ExpectedBehavior expected) {
        List<String> failures = new ArrayList<>();

        // 1. 从 trace 筛出所有 TOOL_CALL 类型的步骤
        List<TraceStep> toolCallSteps = trace.getSteps().stream()
                .filter(step -> "TOOL_CALL".equals(step.getStepType()))
                .toList();

        // 2. 遍历期望的工具调用，检查是否有匹配的步骤
        if (expected.getExpectedToolCalls() != null) {
            for (ExpectedBehavior.ToolCallExpectation expectedCall : expected.getExpectedToolCalls()) {
                boolean matched = toolCallSteps.stream().anyMatch(step -> {
                    // 工具名匹配（忽略大小写）
                    boolean nameMatch = expectedCall.getToolName() == null
                            || expectedCall.getToolName().equalsIgnoreCase(step.getToolName());
                    // 参数包含关键词（忽略大小写）
                    boolean paramMatch = expectedCall.getParamContains() == null
                            || (step.getToolInput() != null
                                    && step.getToolInput().toLowerCase()
                                            .contains(expectedCall.getParamContains().toLowerCase()));
                    // 结果包含关键词（忽略大小写）
                    boolean resultMatch = expectedCall.getResultContains() == null
                            || (step.getResultSummary() != null
                                    && step.getResultSummary().toLowerCase()
                                            .contains(expectedCall.getResultContains().toLowerCase()));
                    return nameMatch && paramMatch && resultMatch;
                });

                if (!matched) {
                    failures.add("缺少工具调用: " + expectedCall.getToolName());
                }
            }
        }
        return failures;
    }
    // ============================================================
    // 🔴 任务 2：检查最终回答是否包含关键词
    // ============================================================
    // 💡 思路：
    //   1. 从 AgentTrace 的 steps 中，找最后一步的 resultSummary
    //   2. 检查所有 expectedResponseKeywords 是否都出现在回答里
    //   3. 缺一个关键词就记一条 failure
    //
    // 🔴 你的代码 ↓
    // public List<String> checkResponseKeywords(AgentTrace trace, ExpectedBehavior expected) {
    //     List<String> failures = new ArrayList<>();
    //     // 1. 取 Agent 最终回答文本
    //     // 2. 逐个检查关键词是否包含
    //     // 3. 不包含 → failures.add("缺少关键词: Spring AI")
    //     return failures;
    // }
    public List<String> checkResponseKeywords(AgentTrace trace, ExpectedBehavior expected) {
        List<String> failures = new ArrayList<>();

        // 1. 取最后一步的 resultSummary 作为 Agent 最终回答
        String finalAnswer = trace.getSteps().stream()
                .max(Comparator.comparingInt(TraceStep::getStepNumber))
                .map(TraceStep::getResultSummary)
                .orElse("");

        // 2. 逐个检查关键词是否包含
        if (expected.getExpectedResponseKeywords() != null && !finalAnswer.isEmpty()) {
            String lowerAnswer = finalAnswer.toLowerCase();
            for (String keyword : expected.getExpectedResponseKeywords()) {
                if (!lowerAnswer.contains(keyword.toLowerCase())) {
                    failures.add("缺少关键词: " + keyword);
                }
            }
        }
        return failures;
    }
    // ============================================================
    // 🔴 任务 3：检查工具调用次数是否超标
    // ============================================================
    // 💡 思路：
    //   1. 从 AgentTrace 统计所有工具调用步骤的数量
    //   2. 跟 expectedBehavior.getMaxToolCalls() 比较
    //   3. 超过 → 记一条 failure
    //
    // 🔴 你的代码 ↓
    // public String checkMaxToolCalls(AgentTrace trace, ExpectedBehavior expected) {
    //     // 1. 统计工具调用次数
    //     // 2. 如果 > maxToolCalls → return "工具调用次数超标: ..."
    //     // 3. 否则 return null（表示通过）
    // }

    public String checkMaxToolCalls(AgentTrace trace, ExpectedBehavior expected) {
        // 1. 统计 TOOL_CALL 类型的步骤数
        long toolCallCount = trace.getSteps().stream()
                .filter(step -> "TOOL_CALL".equals(step.getStepType()))
                .count();

        // 2. 与上限比较
        if (expected.getMaxToolCalls() != null && toolCallCount > expected.getMaxToolCalls()) {
            return "工具调用次数超标: 实际 " + toolCallCount + " 次, 上限 " + expected.getMaxToolCalls() + " 次";
        }
        // 3. 通过 → 返回 null
        return null;
    }
    // ============================================================
    // 任务 4：综合打分
    // ============================================================
    public ScoreResult score(AgentTrace trace, ExpectedBehavior expected) {
        int score = 100;
        List<String> details = new ArrayList<>();

        // 1. 工具调用检查
        List<String> toolFailures = checkToolCalls(trace, expected);
        for (String failure : toolFailures) {
            score -= PENALTY_TOOL_CALL_MISSING;
            details.add("[-" + PENALTY_TOOL_CALL_MISSING + "] " + failure);
        }

        // 2. 关键词检查
        List<String> keywordFailures = checkResponseKeywords(trace, expected);
        for (String failure : keywordFailures) {
            score -= PENALTY_KEYWORD_MISSING;
            details.add("[-" + PENALTY_KEYWORD_MISSING + "] " + failure);
        }

        // 3. 工具调用次数检查
        String maxCallsFailure = checkMaxToolCalls(trace, expected);
        if (maxCallsFailure != null) {
            score -= PENALTY_TOO_MANY_CALLS;
            details.add("[-" + PENALTY_TOO_MANY_CALLS + "] " + maxCallsFailure);
        }

        // 分数不低于 0
        score = Math.max(0, score);

        return ScoreResult.builder()
                .score(score)
                .deductions(details)
                .build();
    }

    // ============================================================
    // 内部类：评分结果
    // ============================================================
    @Data
    @Builder
    public static class ScoreResult {
        /** 最终分数（0~100） */
        private int score;

        /** 扣分明细（每一条说明扣了几分、为什么扣） */
        @Builder.Default
        private List<String> deductions = new ArrayList<>();

        /** 是否通过（分数 >= 60 及格线） */
        public boolean isPassed() {
            return score >= 60;
        }
    }

}
