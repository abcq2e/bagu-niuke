package com.qian.qianaiagent.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionSelectorTest {

    @TempDir
    Path tempDir;

    @Test
    void neverReselectsAskedIds() {
        KnowledgePointCatalog catalog = KnowledgePointCatalog.buildFromStems(
                Map.of("Java并发", List.of(
                        new KnowledgePointCatalog.StemEntry("线程池核心参数有哪些？", "bagu"),
                        new KnowledgePointCatalog.StemEntry("AQS 的独占模式怎么实现？", "bagu"),
                        new KnowledgePointCatalog.StemEntry("完全无关的闲聊题干xyz", "bagu")
                )),
                tempDir,
                "cov1"
        );
        QuestionSelector selector = new QuestionSelector(catalog, null);
        Set<String> asked = new HashSet<>();
        Map<String, Integer> counts = new HashMap<>();

        Optional<KnowledgePoint> first = selector.selectWith("Java并发", asked, counts);
        assertTrue(first.isPresent());
        asked.add(first.get().id());

        Optional<KnowledgePoint> second = selector.selectWith("Java并发", asked, counts);
        assertTrue(second.isPresent());
        assertTrue(!asked.contains(second.get().id()));
        asked.add(second.get().id());

        Optional<KnowledgePoint> third = selector.selectWith("Java并发", asked, counts);
        assertTrue(third.isPresent());
        asked.add(third.get().id());

        assertTrue(selector.selectWith("Java并发", asked, counts).isEmpty());
    }

    @Test
    void prefersDimensionWithMoreRemainingUnasked() {
        // 造两道同属线程池、一道 AQS，已考掉线程池一道后应优先剩余更多的维或继续该维
        KnowledgePointCatalog catalog = KnowledgePointCatalog.buildFromStems(
                Map.of("Java并发", List.of(
                        new KnowledgePointCatalog.StemEntry("线程池核心参数有哪些？", "bagu"),
                        new KnowledgePointCatalog.StemEntry("线程池拒绝策略有哪些？", "bagu"),
                        new KnowledgePointCatalog.StemEntry("AQS 的独占模式怎么实现？", "bagu")
                )),
                tempDir,
                "cov2"
        );
        QuestionSelector selector = new QuestionSelector(catalog, null);
        List<KnowledgePoint> all = catalog.getByTopic("Java并发");
        KnowledgePoint pool1 = all.stream().filter(p -> p.stem().contains("核心参数")).findFirst().orElseThrow();
        Set<String> asked = new HashSet<>(Set.of(pool1.id()));

        Optional<KnowledgePoint> next = selector.selectWith("Java并发", asked, Map.of());
        assertTrue(next.isPresent());
        // 线程池还剩 1 题、AQS 剩 1 题；权重都高，只要未考即可
        assertTrue(!asked.contains(next.get().id()));
    }

    @Test
    void unclassifiedAfterFormalUnaskedExhausted() {
        KnowledgePointCatalog catalog = KnowledgePointCatalog.buildFromStems(
                Map.of("Java并发", List.of(
                        new KnowledgePointCatalog.StemEntry("今天吃什么？", "bagu")
                )),
                tempDir,
                "cov3"
        );
        QuestionSelector selector = new QuestionSelector(catalog, null);
        Optional<KnowledgePoint> p = selector.selectWith("Java并发", Set.of(), Map.of());
        assertTrue(p.isPresent());
        assertEquals(KnowledgePoint.UNCLASSIFIED, p.get().dimension());
    }
}
