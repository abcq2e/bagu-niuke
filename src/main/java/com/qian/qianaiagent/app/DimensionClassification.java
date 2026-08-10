package com.qian.qianaiagent.app;

import java.util.List;

/**
 * 🔴 [终版-双链路] 单条数据的维度分类结果
 *
 * <p>由互证门在流程出口生成，包含最终采用的维度、置信度、数据来源。</p>
 *
 * @param effectiveDimensions 最终确定的维度列表（支持多维度）
 * @param confidence          置信度等级
 * @param source              数据来源（link_a / link_b / ground_truth / fallback）
 * @param linkADimensions     链路A的 DIM 标签列表（原始输出，未校验前）
 * @param linkBDimensions     链路B评分 AI 标注的维度列表（原始输出）
 */
public record DimensionClassification(
        List<String> effectiveDimensions,
        ConfidenceLevel confidence,
        String source,
        List<String> linkADimensions,
        List<String> linkBDimensions
) {

    /** 当仅有一个维度时的便捷构造 */
    public static DimensionClassification single(String dim, ConfidenceLevel confidence, String source) {
        return new DimensionClassification(
                dim == null ? List.of() : List.of(dim),
                confidence, source, List.of(), List.of());
    }

    /** 当没有有效维度时的空分类 */
    public static DimensionClassification empty() {
        return new DimensionClassification(List.of(), ConfidenceLevel.INVALID, "none", List.of(), List.of());
    }

    public boolean isValid() {
        return confidence != ConfidenceLevel.INVALID
                && confidence != ConfidenceLevel.FALLBACK
                && !effectiveDimensions.isEmpty();
    }
}
