package com.qian.qianaiagent.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.qian.qianaiagent.knowledge.TopicDimensions;

class WeakPointNormalizerTest {

    @Test
    void dimensionSubjectRemovesParenthesizedKeywords() {
        assertEquals("锁机制", TopicDimensions.dimensionSubject("锁机制（synchronized/ReentrantLock/读写锁）"));
    }

    @Test
    void concurrentThreadPoolIsNotClassifiedAsForeignOperatingSystemTopic() {
        assertFalse(TopicDimensions.containsForeignTopicKeyword("Java并发", "线程池的核心参数"));
    }

    @Test
    void dimensionSubjectMatching() {
        // 🔴 [终版] matchesDimension 只检查维度主体名，不检查括号内关键词
        assertTrue(TopicDimensions.matchesDimension(
                "锁机制（synchronized/ReentrantLock/读写锁）",
                "锁机制 synchronized 区别"),
            "matchesDimension should match dimension subject '锁机制'");
        assertFalse(TopicDimensions.matchesDimension(
                "锁机制（synchronized/ReentrantLock/读写锁）",
                "synchronized 与 ReentrantLock 区别"),
            "matchesDimension without dimension subject should fail");
    }

    @Test
    void noForeignKeywordInReentrantLockText() {
        assertFalse(TopicDimensions.containsForeignTopicKeyword(
                "Java并发",
                "锁机制 ReentrantLock 与 synchronized 区别"),
            "should not contain foreign keywords");
    }

    @Test
    void normalizeWithOnlyReentrantLockCandidate() {
        WeakPointNormalizer normalizer = new WeakPointNormalizer();
        String topic = "Java并发";
        String dimension = "锁机制（synchronized/ReentrantLock/读写锁）";
        String candidate = "锁机制 ReentrantLock 与 synchronized 区别";

        WeakPointNormalizer.NormalizedWeakPoints result = normalizer.normalize(
                topic, dimension,
                java.util.List.of(candidate),
                java.util.Map.of(candidate, "遗漏公平锁、可中断锁和 Condition"));

        assertTrue(result.weakPoints().contains(candidate));
        assertEquals("遗漏公平锁、可中断锁和 Condition",
                result.weakPointDetails().get(candidate));
    }

    @Test
    void normalizeWithAllEvaluativeCandidates() {
        WeakPointNormalizer.NormalizedWeakPoints result = new WeakPointNormalizer().normalize(
                "Java并发",
                "锁机制（synchronized/ReentrantLock/读写锁）",
                java.util.List.of("态度敷衍", "概念混淆"),
                java.util.Map.of());
        assertEquals(java.util.List.of("锁机制"), result.weakPoints());
    }

    @Test
    void normalizesEvaluationAndCrossTopicWeakPoints() {
        WeakPointNormalizer.NormalizedWeakPoints result = new WeakPointNormalizer().normalize(
                "Java并发",
                "锁机制（synchronized/ReentrantLock/读写锁）",
                java.util.List.of("态度敷衍", "MySQL索引失效场景",
                        "锁机制 ReentrantLock 与 synchronized 区别"),
                java.util.Map.of("锁机制 ReentrantLock 与 synchronized 区别",
                        "遗漏公平锁、可中断锁和 Condition"));

        assertEquals(java.util.List.of("锁机制 ReentrantLock 与 synchronized 区别"), result.weakPoints());
        assertEquals("遗漏公平锁、可中断锁和 Condition",
                result.weakPointDetails().get("锁机制 ReentrantLock 与 synchronized 区别"));
    }
}
