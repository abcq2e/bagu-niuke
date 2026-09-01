package com.qian.qianaiagent.ability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.qian.qianaiagent.knowledge.TopicDimensions;

class WeakPointRoutingTest {

    private UserAbilityService userAbilityService;

    @BeforeEach
    void setUp() {
        ChatModel mockChatModel = mock(ChatModel.class);
        userAbilityService = new UserAbilityService(mockChatModel);
    }

    // ========================================================================
    // 测试 1: CROSS_TOPIC_CONCEPTS 硬编码映射（最高优先级）
    // ========================================================================

    @Test
    void crossTopicConceptMappingWorks() {
        // "值传递" 应该映射到 Java基础与集合，即使当前方向是 JVM
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("值传递"),
                "JVM",
                null
        );

        assertTrue(result.containsKey("Java基础与集合"),
                "值传递应该路由到Java基础与集合");
        assertFalse(result.containsKey("JVM"),
                "值传递不应该留在JVM方向");
        assertEquals(1, result.get("Java基础与集合").size());
        assertEquals("值传递", result.get("Java基础与集合").get(0));
    }

    @Test
    void crossTopicConceptSubstringMatching() {
        // "Java的值传递机制" 包含 "值传递"，应该也能映射
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("Java的值传递机制"),
                "JVM",
                null
        );

        assertTrue(result.containsKey("Java基础与集合"),
                "包含值传递的文本也应该路由到Java基础与集合");
    }

    @Test
    void aqsMapsToConcurrent() {
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("AQS原理"),
                "JVM",
                null
        );

        assertTrue(result.containsKey("Java并发"),
                "AQS应该路由到Java并发");
    }

    @Test
    void mvccMapsToMysql() {
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("MVCC实现原理"),
                "Redis",
                null
        );

        assertTrue(result.containsKey("MySQL"),
                "MVCC应该路由到MySQL");
    }

    // ========================================================================
    // 测试 2: 正向匹配 — 明显属于当前方向的知识点
    // ========================================================================

    @Test
    void mysqlIndexStaysInMysql() {
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("B+树索引结构"),
                "MySQL",
                null
        );

        assertTrue(result.containsKey("MySQL"),
                "B+树索引应该留在MySQL方向");
        assertEquals(1, result.size(),
                "只应该在MySQL方向有记录");
    }

    @Test
    void threadPoolStaysInConcurrent() {
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("线程池核心参数"),
                "Java并发",
                null
        );

        assertTrue(result.containsKey("Java并发"),
                "线程池应该留在Java并发方向");
    }

    @Test
    void redisPersistenceStaysInRedis() {
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("RDB和AOF持久化"),
                "Redis",
                null
        );

        assertTrue(result.containsKey("Redis"),
                "RDB/AOF应该留在Redis方向");
    }

    // ========================================================================
    // 测试 3: 跨方向知识点 — 应该被路由到正确方向
    // ========================================================================

    @Test
    void hashMapInJvmShouldRouteToJavaBase() {
        // 在JVM方向答差了，但知识点是HashMap，应该路由到Java基础与集合
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("HashMap扩容机制"),
                "JVM",
                null
        );

        assertTrue(result.containsKey("Java基础与集合"),
                "HashMap应该路由到Java基础与集合");
        assertFalse(result.containsKey("JVM"),
                "HashMap不应该留在JVM方向");
    }

    @Test
    void synchronizedInSpringShouldRouteToConcurrent() {
        // 在Spring方向答差了，但知识点是synchronized，应该路由到Java并发
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("synchronized锁升级"),
                "Spring框架",
                null
        );

        assertTrue(result.containsKey("Java并发"),
                "synchronized应该路由到Java并发");
    }

    @Test
    void tcpHandshakeInOsShouldRouteToNetwork() {
        // 在操作系统方向答差了，但知识点是TCP三次握手，应该路由到计算机网络
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("TCP三次握手过程"),
                "操作系统与Linux",
                null
        );

        assertTrue(result.containsKey("计算机网络"),
                "TCP三次握手应该路由到计算机网络");
    }

    // ========================================================================
    // 测试 4: 边界情况 — 模糊/通用的知识点
    // ========================================================================

    @Test
    void veryGenericConceptMayBeDropped() {
        // "性能优化" 太通用了，无法确定方向 → 应该丢弃
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("性能优化"),
                "JVM",
                null
        );

        // 可能匹配上多个方向，但分数都不高，或者匹配不上
        // 这里不做严格断言，只验证不会崩溃
        assertNotNull(result);
    }

    @Test
    void emptyWeakPointsReturnsEmpty() {
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of(),
                "MySQL",
                null
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void nullWeakPointsReturnsEmpty() {
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                null,
                "MySQL",
                null
        );

        assertTrue(result.isEmpty());
    }

    // ========================================================================
    // 测试 5: 多个知识点混合 — 部分本方向，部分跨方向
    // ========================================================================

    @Test
    void mixedWeakPointsRouteCorrectly() {
        // 在 JVM 方向答题，答差了 3 个知识点：
        // 1. GC算法 → JVM（本方向）
        // 2. 值传递 → Java基础与集合（跨方向）
        // 3. 双亲委派 → JVM（本方向）
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("GC算法", "值传递", "双亲委派"),
                "JVM",
                null
        );

        assertTrue(result.containsKey("JVM"),
                "GC算法和双亲委派应该留在JVM");
        assertTrue(result.containsKey("Java基础与集合"),
                "值传递应该路由到Java基础与集合");

        assertEquals(2, result.get("JVM").size(),
                "JVM方向应该有2个弱点评");
        assertTrue(result.get("JVM").contains("GC算法"));
        assertTrue(result.get("JVM").contains("双亲委派"));

        assertEquals(1, result.get("Java基础与集合").size(),
                "Java基础方向应该有1个弱点评");
        assertEquals("值传递", result.get("Java基础与集合").get(0));
    }

    // ========================================================================
    // 测试 6: 算法方向 vs Spring 方向 — 验证用户反馈的场景
    // ========================================================================

    @Test
    void quickSortInSpringShouldRouteToAlgorithm() {
        // 用户反馈的场景：答的是算法，但显示在Spring下面
        // 快速排序应该路由到算法与数据结构，而不是Spring
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("快速排序时间复杂度"),
                "Spring框架",
                null
        );

        assertTrue(result.containsKey("算法与数据结构"),
                "快速排序应该路由到算法与数据结构");
        assertFalse(result.containsKey("Spring框架"),
                "快速排序不应该留在Spring框架方向");
    }

    @Test
    void mergeSortInMysqlShouldRouteToAlgorithm() {
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("归并排序原理"),
                "MySQL",
                null
        );

        assertTrue(result.containsKey("算法与数据结构"),
                "归并排序应该路由到算法与数据结构");
    }

    @Test
    void binarySearchInRedisShouldRouteToAlgorithm() {
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("二分查找"),
                "Redis",
                null
        );

        assertTrue(result.containsKey("算法与数据结构"),
                "二分查找应该路由到算法与数据结构");
    }

    @Test
    void springBeanLifecycleStaysInSpring() {
        // 确保Spring的知识点不会被错误路由
        Map<String, List<String>> result = userAbilityService.routeWeakPoints(
                List.of("Bean生命周期"),
                "Spring框架",
                null
        );

        assertTrue(result.containsKey("Spring框架"),
                "Bean生命周期应该留在Spring框架");
    }

    // ========================================================================
    // 测试 7: containsForeignTopicKeyword 兼容性测试
    // ========================================================================

    @Test
    void foreignKeywordDetectionStillWorks() {
        // 确保旧的 containsForeignTopicKeyword 方法仍然正常工作
        assertTrue(TopicDimensions.containsForeignTopicKeyword(
                "JVM",
                "值传递机制"
        ), "值传递在JVM方向应该被检测为外来概念");

        assertFalse(TopicDimensions.containsForeignTopicKeyword(
                "Java基础与集合",
                "值传递机制"
        ), "值传递在Java基础方向不应该被检测为外来概念");
    }

    @Test
    void aqsIsForeignToJvm() {
        assertTrue(TopicDimensions.containsForeignTopicKeyword(
                "JVM",
                "AQS原理"
        ), "AQS在JVM方向应该被检测为外来概念");
    }
}
