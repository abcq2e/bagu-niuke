package com.qian.qianaiagent.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionLabelerTest {

    @Test
    void threadPoolStemLabelsToThreadPoolDimension() {
        String dim = DimensionLabeler.label("Java并发", "线程池的核心参数有哪些？拒绝策略怎么选？");
        assertTrue(dim.contains("线程池"), "expected 线程池 dimension, got: " + dim);
        assertNotEquals(KnowledgePoint.UNCLASSIFIED, dim);
    }

    @Test
    void unrelatedStemGoesUnclassified() {
        String dim = DimensionLabeler.label("Java并发", "今天天气怎么样？");
        assertEquals(KnowledgePoint.UNCLASSIFIED, dim);
    }

    @Test
    void stableIdIsDeterministicForSameStem() {
        String a = KnowledgePoint.stableId("Java并发", "什么是 AQS？");
        String b = KnowledgePoint.stableId("Java并发", "什么是 AQS？");
        String c = KnowledgePoint.stableId("Java并发", "  什么是 AQS？  ");
        assertEquals(a, b);
        assertEquals(a, c);
        assertNotEquals(a, KnowledgePoint.stableId("JVM", "什么是 AQS？"));
    }
}
