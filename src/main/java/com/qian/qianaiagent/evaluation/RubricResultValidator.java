package com.qian.qianaiagent.evaluation;

import java.util.Optional;

/**
 * 评分结果的语义不变量校验。
 *
 * <h2>为什么不是 JSON Schema 校验</h2>
 * 结构问题（字段缺失、类型不符）Jackson 的 {@code readValue} 已经会抛异常了，
 * 再套一层 JSON Schema 是重复劳动。真正会静默出错的是<b>结构合法但语义违规</b> ——
 * 例如 LLM 返回 {@code totalScore: 120} 而四个维度加起来只有 60，
 * 这种数据会被原样写进 {@code data/evals/}，污染所有下游统计。
 *
 * <h2>为什么要有这个类</h2>
 * 改造前解析失败会静默兜底成 {@code totalScore=0}，把「解析问题」伪装成「评分为 0」。
 * 有了校验器，调用方才能区分「真的 0 分」和「没解析出来」，进而决定是否重试。
 */
final class RubricResultValidator {

    private static final int DIMENSION_MAX = 25;

    private RubricResultValidator() {
    }

    /**
     * @return 空表示合规；非空是<b>可直接回灌给 LLM 的失败原因</b>
     */
    static Optional<String> validate(RubricScorer.RubricResult result) {
        if (result == null) {
            return Optional.of("评分结果为 null");
        }
        String comment = result.getOverallComment();
        if (comment == null || comment.isBlank()) {
            return Optional.of("缺少 overallComment 字段");
        }
        // callLLMAndParse 的兜底路径会写入这两种前缀，可据此识别「其实没解析成功」
        if (comment.startsWith("JSON 解析失败")) {
            return Optional.of(comment);
        }
        if (comment.startsWith("LLM 返回空响应")) {
            return Optional.of(comment);
        }

        int sum = result.getReasoningQuality() + result.getFaithfulness()
                + result.getCompleteness() + result.getToolUsage();
        if (result.getTotalScore() != sum) {
            return Optional.of("totalScore=%d 与四维度之和 %d 不一致"
                    .formatted(result.getTotalScore(), sum));
        }

        if (result.getReasoningQuality() < 0 || result.getReasoningQuality() > DIMENSION_MAX) {
            return Optional.of("reasoningQuality 超出 0-%d：%d"
                    .formatted(DIMENSION_MAX, result.getReasoningQuality()));
        }
        if (result.getFaithfulness() < 0 || result.getFaithfulness() > DIMENSION_MAX) {
            return Optional.of("faithfulness 超出 0-%d：%d"
                    .formatted(DIMENSION_MAX, result.getFaithfulness()));
        }
        if (result.getCompleteness() < 0 || result.getCompleteness() > DIMENSION_MAX) {
            return Optional.of("completeness 超出 0-%d：%d"
                    .formatted(DIMENSION_MAX, result.getCompleteness()));
        }
        if (result.getToolUsage() < 0 || result.getToolUsage() > DIMENSION_MAX) {
            return Optional.of("toolUsage 超出 0-%d：%d"
                    .formatted(DIMENSION_MAX, result.getToolUsage()));
        }
        return Optional.empty();
    }
}
