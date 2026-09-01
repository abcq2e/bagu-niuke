package com.qian.qianaiagent.interview.rotation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 题目去重功能单元测试 — 基于 "方向::序号" 布尔标记机制。
 * <p>
 * 直接测试 TopicRotationService 的核心去重方法（不依赖 Spring 容器），
 * 验证查重、记录、隔离、迁移等场景。
 */
class DedupTest {

    private TopicRotationService service;
    private static final String CHAT_ID = "test_user_001";
    private static final String TOPIC_JAVA = "Java基础与集合";
    private static final String TOPIC_JVM = "JVM";

    @BeforeEach
    void setUp() {
        service = new TopicRotationService();
    }

    // ==================== 基础查重 ====================

    @Test
    void shouldReturnFalseForUnaskedQuestion() {
        assertFalse(service.isQuestionAsked(CHAT_ID, TOPIC_JAVA, 0));
        assertFalse(service.isQuestionAsked(CHAT_ID, TOPIC_JAVA, 15));
        assertFalse(service.isQuestionAsked(CHAT_ID, TOPIC_JAVA, 99));
    }

    @Test
    void shouldReturnTrueAfterRecording() {
        service.recordQuestionAsked(CHAT_ID, TOPIC_JAVA, 5);
        assertTrue(service.isQuestionAsked(CHAT_ID, TOPIC_JAVA, 5));
    }

    @Test
    void shouldNotAffectOtherIndices() {
        service.recordQuestionAsked(CHAT_ID, TOPIC_JAVA, 5);
        assertFalse(service.isQuestionAsked(CHAT_ID, TOPIC_JAVA, 6));
    }

    // ==================== 跨方向隔离 ====================

    @Test
    void shouldIsolateAcrossTopics() {
        service.recordQuestionAsked(CHAT_ID, TOPIC_JAVA, 3);
        service.recordQuestionAsked(CHAT_ID, TOPIC_JVM, 3);

        // 同序号、不同方向 → 各自独立
        assertTrue(service.isQuestionAsked(CHAT_ID, TOPIC_JAVA, 3));
        assertTrue(service.isQuestionAsked(CHAT_ID, TOPIC_JVM, 3));

        // 不同方向的其他序号不互相影响
        assertFalse(service.isQuestionAsked(CHAT_ID, TOPIC_JAVA, 10));
        assertFalse(service.isQuestionAsked(CHAT_ID, TOPIC_JVM, 10));
    }

    // ==================== 批量记录 ====================

    @Test
    void shouldHandleBatchRecording() {
        // 模拟历史迁移：批量记录前 50 题
        for (int i = 0; i < 50; i++) {
            service.recordQuestionAsked(CHAT_ID, TOPIC_JAVA, i);
        }

        // 前 50 题全部已出
        for (int i = 0; i < 50; i++) {
            assertTrue(service.isQuestionAsked(CHAT_ID, TOPIC_JAVA, i),
                    "第" + i + "题应该已被标记");
        }

        // 第 50 题之后未出
        assertFalse(service.isQuestionAsked(CHAT_ID, TOPIC_JAVA, 50));
    }

    // ==================== 重复记录幂等 ====================

    @Test
    void shouldBeIdempotentOnRepeatedRecord() {
        service.recordQuestionAsked(CHAT_ID, TOPIC_JAVA, 7);
        service.recordQuestionAsked(CHAT_ID, TOPIC_JAVA, 7); // 重复记录
        service.recordQuestionAsked(CHAT_ID, TOPIC_JAVA, 7); // 再次重复

        assertTrue(service.isQuestionAsked(CHAT_ID, TOPIC_JAVA, 7));
        // Set 去重，指纹计数应为 1 而非 3
        assertEquals(1, service.getFingerprintCount(CHAT_ID));
    }

    // ==================== 多用户隔离 ====================

    @Test
    void shouldIsolateAcrossUsers() {
        service.recordQuestionAsked("user_A", TOPIC_JAVA, 0);
        service.recordQuestionAsked("user_A", TOPIC_JAVA, 1);
        service.recordQuestionAsked("user_B", TOPIC_JAVA, 0);

        // user_A 的第 0、1 题已出
        assertTrue(service.isQuestionAsked("user_A", TOPIC_JAVA, 0));
        assertTrue(service.isQuestionAsked("user_A", TOPIC_JAVA, 1));
        assertFalse(service.isQuestionAsked("user_A", TOPIC_JAVA, 2));

        // user_B 只有第 0 题已出（第 1 题未出）
        assertTrue(service.isQuestionAsked("user_B", TOPIC_JAVA, 0));
        assertFalse(service.isQuestionAsked("user_B", TOPIC_JAVA, 1));
    }

    // ==================== 边界条件 ====================

    @Test
    void shouldHandleZeroIndex() {
        service.recordQuestionAsked(CHAT_ID, TOPIC_JAVA, 0);
        assertTrue(service.isQuestionAsked(CHAT_ID, TOPIC_JAVA, 0));
    }

    @Test
    void shouldHandleLargeIndex() {
        service.recordQuestionAsked(CHAT_ID, TOPIC_JAVA, Integer.MAX_VALUE);
        assertTrue(service.isQuestionAsked(CHAT_ID, TOPIC_JAVA, Integer.MAX_VALUE));
    }

    @Test
    void shouldReturnZeroForNewSession() {
        assertEquals(0, service.getFingerprintCount("unknown_user"));
    }

    // ==================== 模拟完整出题流程 ====================

    @Test
    void shouldSimulateFullQuizFlow() {
        // 模拟 SequentialRotationService 游标推进出题 15 题
        int questionsAsked = 0;
        int questionsSkipped = 0;

        for (int cursorPos = 0; cursorPos < 30; cursorPos++) {
            if (service.isQuestionAsked(CHAT_ID, TOPIC_JAVA, cursorPos)) {
                questionsSkipped++; // 重复 → 跳过
                continue;
            }
            // 新题 → 出题 → 记录
            service.recordQuestionAsked(CHAT_ID, TOPIC_JAVA, cursorPos);
            questionsAsked++;
        }

        // 30 个题全部是新题（因为游标一路向前，不会回头）
        assertEquals(30, questionsAsked);
        assertEquals(0, questionsSkipped);
        assertEquals(30, service.getFingerprintCount(CHAT_ID));

        // 第二轮：如果游标重置，重新从 0 开始 → 全部被拦截
        int secondRoundSkipped = 0;
        for (int cursorPos = 0; cursorPos < 30; cursorPos++) {
            if (service.isQuestionAsked(CHAT_ID, TOPIC_JAVA, cursorPos)) {
                secondRoundSkipped++;
            }
        }
        assertEquals(30, secondRoundSkipped,
                "第二轮大循环时，前30题应全部被去重拦截");
    }
}
