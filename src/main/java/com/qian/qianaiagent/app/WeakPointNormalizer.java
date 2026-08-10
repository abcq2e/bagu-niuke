package com.qian.qianaiagent.app;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将评分模型给出的候选弱项限制为当前考卷和题目维度内的具体知识点。
 * 不调用模型，同时用于新评分写入和历史画像展示清洗。
 */
public class WeakPointNormalizer {

    private static final List<String> EVALUATION_MARKERS = List.of(
            "态度", "敷衍", "偏离", "为空", "空或", "无关", "基础知识点缺失",
            "概念混淆", "不够全面", "不够深入", "过于简略", "重复", "雷同",
            "未理解", "不了解", "回答内容", "回答过于", "回答错误");

    public NormalizedWeakPoints normalize(String topic, String dimension,
                                          List<String> candidates,
                                          Map<String, String> details) {
        Set<String> accepted = new LinkedHashSet<>();
        Map<String, String> acceptedDetails = new LinkedHashMap<>();
        for (String candidate : candidates == null ? List.<String>of() : candidates) {
            String weakPoint = candidate == null ? "" : candidate.trim();
            if (!isUsable(topic, dimension, weakPoint)) {
                continue;
            }
            accepted.add(weakPoint);
            String detail = details == null ? null : details.get(candidate);
            if (detail != null && !detail.isBlank()) {
                acceptedDetails.put(weakPoint, detail.trim());
            }
        }
        if (accepted.isEmpty()) {
            String fallback = TopicDimensions.dimensionSubject(dimension);
            if (!fallback.isBlank()) {
                accepted.add(fallback);
            } else if (topic != null && !topic.isBlank()) {
                // 维度为空时，用方向名做兜底（如"Redis基础知识"）
                accepted.add(topic + "基础知识");
            }
        }
        return new NormalizedWeakPoints(List.copyOf(accepted), Map.copyOf(acceptedDetails));
    }

    private boolean isUsable(String topic, String dimension, String weakPoint) {
        if (weakPoint.isBlank() || weakPoint.length() > 60) {
            return false;
        }
        String normalized = weakPoint.toLowerCase(java.util.Locale.ROOT);
        if (EVALUATION_MARKERS.stream().anyMatch(marker -> normalized.contains(marker.toLowerCase(java.util.Locale.ROOT)))) {
            return false;
        }
        // 🔴 不再用 containsForeignTopicKeyword 做展示过滤：
        //   cleanCrossTopicWeakPoints 在每次加载时已用 routeWeakPoints 统一清理，
        //   展示层再做二次过滤会导致与路由结果不一致——清理移对了但展示滤掉了
        if (dimension == null || dimension.isBlank()) {
            return true;
        }
        return TopicDimensions.matchesDimension(dimension, weakPoint);
    }

    public record NormalizedWeakPoints(List<String> weakPoints, Map<String, String> weakPointDetails) {
    }
}
