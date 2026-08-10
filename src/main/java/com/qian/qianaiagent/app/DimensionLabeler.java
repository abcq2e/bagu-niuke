package com.qian.qianaiagent.app;

import java.util.List;
import java.util.Locale;

/**
 * 关键词维度挂靠：用 TopicDimensions 主体名 + 括号内关键词打分。
 * 多级匹配策略确保高挂靠率：主体全匹配 > 主体部分匹配 > 括号关键词匹配。
 */
public final class DimensionLabeler {

    private static final int MIN_SCORE = 1;
    private static final int SUBJECT_HIT = 4;
    private static final int SUBJECT_PARTIAL_HIT = 1;
    private static final int KEYWORD_HIT = 2;

    private DimensionLabeler() {}

    /**
     * @return 正式维度全名，或 {@link KnowledgePoint#UNCLASSIFIED}
     */
    public static String label(String topic, String stem) {
        if (topic == null || stem == null || stem.isBlank()) {
            return KnowledgePoint.UNCLASSIFIED;
        }
        String text = stem.toLowerCase(Locale.ROOT);
        List<String> dims = TopicDimensions.getDimensions(topic);
        if (dims.isEmpty()) {
            return KnowledgePoint.UNCLASSIFIED;
        }

        String bestDim = KnowledgePoint.UNCLASSIFIED;
        int bestScore = 0;
        for (String dim : dims) {
            int score = score(dim, text);
            if (score > bestScore) {
                bestScore = score;
                bestDim = dim;
            }
        }
        return bestScore >= MIN_SCORE ? bestDim : KnowledgePoint.UNCLASSIFIED;
    }

    public static String labelSourceFor(String dimension) {
        return KnowledgePoint.UNCLASSIFIED.equals(dimension)
                ? KnowledgePoint.LABEL_UNCLASSIFIED
                : KnowledgePoint.LABEL_KEYWORD;
    }

    private static int score(String dim, String lowerStem) {
        int score = 0;
        String subject = TopicDimensions.dimensionSubject(dim);
        if (!subject.isEmpty()) {
            String lowerSubject = subject.toLowerCase(Locale.ROOT);
            if (lowerStem.contains(lowerSubject)) {
                score += SUBJECT_HIT;
            } else {
                int partialHits = countPartialSubjectHits(lowerSubject, lowerStem);
                score += partialHits * SUBJECT_PARTIAL_HIT;
            }
        }
        for (String kw : TopicDimensions.getSubDimensionKeywords(dim)) {
            if (kw.length() >= 2 && lowerStem.contains(kw.toLowerCase(Locale.ROOT))) {
                score += KEYWORD_HIT;
            }
        }
        return score;
    }

    private static int countPartialSubjectHits(String lowerSubject, String lowerStem) {
        int hits = 0;
        int len = lowerSubject.length();
        if (len < 2) return 0;
        for (int i = 0; i <= len - 2; i++) {
            String sub = lowerSubject.substring(i, i + 2);
            if (lowerStem.contains(sub)) {
                hits++;
            }
        }
        return Math.min(hits, 3);
    }
}
