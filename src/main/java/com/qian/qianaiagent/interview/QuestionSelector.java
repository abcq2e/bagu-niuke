package com.qian.qianaiagent.interview;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import com.qian.qianaiagent.interview.rotation.TopicRotationService;
import com.qian.qianaiagent.knowledge.KnowledgePoint;
import com.qian.qianaiagent.knowledge.KnowledgePointCatalog;
import com.qian.qianaiagent.knowledge.TopicDimensions;

/**
 * 唯一选题权威：优先从未考知识点中选题，维度只用于「剩余未考更多的维优先」。
 * <p>
 * 覆盖目标：同方向上百题跨次累计考完——已考 ID 永不入选，直到该方向题库耗尽。
 */
@Component
@Slf4j
public class QuestionSelector {

    private final KnowledgePointCatalog catalog;
    private final TopicRotationService topicRotationService;

    public QuestionSelector(KnowledgePointCatalog catalog,
                            TopicRotationService topicRotationService) {
        this.catalog = catalog;
        this.topicRotationService = topicRotationService;
    }

    public Optional<KnowledgePoint> select(String chatId, String topic, Set<String> askedIds) {
        // dimCounts 保留兼容；主策略改为按「剩余未考量」排序
        Map<String, Integer> counts = topicRotationService != null
                ? topicRotationService.getDimQuestionCounts(chatId, topic)
                : Map.of();
        return selectWith(topic, askedIds, counts);
    }

    /**
     * 选题：只从未考 ID 中选；优先剩余未考最多的维度（兼顾广度）；
     * 未分类维度与正式维度混合排序，确保全题库所有题目都有机会被考察。
     */
    public Optional<KnowledgePoint> selectWith(
            String topic,
            Set<String> askedIds,
            Map<String, Integer> dimCounts) {
        if (catalog == null || catalog.isEmpty(topic)) {
            return Optional.empty();
        }
        Set<String> asked = askedIds != null ? askedIds : Set.of();

        List<String> dims = new ArrayList<>(TopicDimensions.getDimensions(topic));
        dims.add(KnowledgePoint.UNCLASSIFIED);
        // 剩余未考多的维优先；同量则权重大的优先；同权重则本会话出题少的优先（dimCounts）
        dims.sort((a, b) -> {
            int ra = remainingUnasked(topic, a, asked);
            int rb = remainingUnasked(topic, b, asked);
            if (ra != rb) return Integer.compare(rb, ra);
            int wa = weightOf(topic, a);
            int wb = weightOf(topic, b);
            if (wa != wb) return Integer.compare(wb, wa);
            int ca = dimCounts != null ? dimCounts.getOrDefault(a, 0) : 0;
            int cb = dimCounts != null ? dimCounts.getOrDefault(b, 0) : 0;
            return Integer.compare(ca, cb);
        });

        Optional<KnowledgePoint> fromDims = pickUnasked(topic, dims, asked);
        if (fromDims.isPresent()) return fromDims;

        // 兜底：任意未考（含未挂靠到 dims 列表的异常情况）
        List<KnowledgePoint> any = catalog.getByTopic(topic).stream()
                .filter(p -> !asked.contains(p.id()))
                .toList();
        if (!any.isEmpty()) {
            return Optional.of(pickRandom(any));
        }

        return Optional.empty();
    }

    private int remainingUnasked(String topic, String dim, Set<String> asked) {
        return (int) catalog.getByDimension(topic, dim).stream()
                .filter(p -> !asked.contains(p.id()))
                .count();
    }

    private Optional<KnowledgePoint> pickUnasked(String topic, List<String> dims, Set<String> asked) {
        for (String dim : dims) {
            List<KnowledgePoint> candidates = catalog.getByDimension(topic, dim).stream()
                    .filter(p -> !asked.contains(p.id()))
                    .toList();
            if (!candidates.isEmpty()) {
                return Optional.of(pickRandom(candidates));
            }
        }
        return Optional.empty();
    }

    private static int weightOf(String topic, String dim) {
        Map<String, Integer> weights = TopicDimensions.SUB_DIMENSION_WEIGHTS.getOrDefault(topic, Map.of());
        return weights.getOrDefault(dim, 1);
    }

    private static KnowledgePoint pickRandom(List<KnowledgePoint> list) {
        if (list.size() == 1) return list.get(0);
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
}
