package com.qian.qianaiagent.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgePointCatalogTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsAndQueriesByDimension() {
        KnowledgePointCatalog catalog = KnowledgePointCatalog.buildFromStems(
                Map.of("Java并发", List.of(
                        new KnowledgePointCatalog.StemEntry("线程池核心参数有哪些？", "bagu"),
                        new KnowledgePointCatalog.StemEntry("今天吃什么？", "bagu")
                )),
                tempDir,
                "hash1"
        );

        List<KnowledgePoint> all = catalog.getByTopic("Java并发");
        assertEquals(2, all.size());

        long unclassified = all.stream()
                .filter(p -> KnowledgePoint.UNCLASSIFIED.equals(p.dimension()))
                .count();
        assertEquals(1, unclassified);

        KnowledgePoint labeled = all.stream()
                .filter(p -> !KnowledgePoint.UNCLASSIFIED.equals(p.dimension()))
                .findFirst()
                .orElseThrow();
        assertTrue(labeled.dimension().contains("线程池"));
        assertFalse(catalog.getByDimension("Java并发", labeled.dimension()).isEmpty());
    }

    @Test
    void diskRoundTripKeepsStableIds() {
        Map<String, List<KnowledgePointCatalog.StemEntry>> stems = Map.of(
                "JVM", List.of(new KnowledgePointCatalog.StemEntry("什么是 G1 垃圾收集器？", "bagu"))
        );
        KnowledgePointCatalog first = KnowledgePointCatalog.buildFromStems(stems, tempDir, "h1");
        String id1 = first.getByTopic("JVM").get(0).id();

        KnowledgePointCatalog second = KnowledgePointCatalog.buildFromStems(stems, tempDir, "h1");
        assertEquals(id1, second.getByTopic("JVM").get(0).id());
        assertTrue(second.loadedFromCache());
    }
}
