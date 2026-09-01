package com.qian.qianaiagent.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.qian.qianaiagent.ability.DimensionLabeler;
import com.qian.qianaiagent.interview.rotation.SequentialRotationService;

/**
 * 知识点目录：稳定 ID + 维度挂靠 + 落盘缓存。
 */
@Component
@Slf4j
public class KnowledgePointCatalog {

    public record StemEntry(String stem, String source) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TopicDocumentCache documentCache;
    private final Path cacheDir;

    /** topic → points */
    private final Map<String, List<KnowledgePoint>> byTopic = new ConcurrentHashMap<>();
    private boolean loadedFromCache;

    /** 🔴 Spring 构造注入 */
    public KnowledgePointCatalog(TopicDocumentCache documentCache) {
        this.documentCache = documentCache;
        this.cacheDir = Path.of(".knowledge-catalog");
    }

    /** 🔴 保护无参构造，供 Spring CGLIB 代理使用 */
    protected KnowledgePointCatalog() {
        this.documentCache = null;
        this.cacheDir = null;
    }

    /** 测试 / 离线构建入口 */
    public static KnowledgePointCatalog buildFromStems(
            Map<String, List<StemEntry>> stemsByTopic,
            Path cacheDir,
            String sourceHash) {
        KnowledgePointCatalog catalog = new KnowledgePointCatalog(null, cacheDir);
        if (catalog.tryLoadCache(sourceHash)) {
            return catalog;
        }
        catalog.rebuild(stemsByTopic, sourceHash);
        return catalog;
    }

    private KnowledgePointCatalog(TopicDocumentCache documentCache, Path cacheDir) {
        this.documentCache = documentCache;
        this.cacheDir = cacheDir;
    }

    @PostConstruct
    public void init() {
        if (documentCache == null) {
            log.warn("KnowledgePointCatalog 通过无参构造器创建，跳过初始化");
            return;
        }
        Map<String, List<StemEntry>> stems = new LinkedHashMap<>();
        StringBuilder hashInput = new StringBuilder();
        for (String topic : SequentialRotationService.TOPIC_NAMES) {
            List<String> raw = documentCache.getRawQuestions(topic);
            List<StemEntry> entries = new ArrayList<>();
            for (String stem : raw) {
                entries.add(new StemEntry(stem, "mixed"));
                hashInput.append(topic).append('\0').append(stem).append('\n');
            }
            stems.put(topic, entries);
        }
        String hash = sha256Hex(hashInput.toString());
        if (!tryLoadCache(hash)) {
            rebuild(stems, hash);
        }
        logDistribution();
    }

    public boolean loadedFromCache() {
        return loadedFromCache;
    }

    public List<KnowledgePoint> getByTopic(String topic) {
        return byTopic.getOrDefault(topic, List.of());
    }

    public List<KnowledgePoint> getByDimension(String topic, String dimension) {
        return getByTopic(topic).stream()
                .filter(p -> Objects.equals(p.dimension(), dimension))
                .toList();
    }

    public List<KnowledgePoint> getUnclassified(String topic) {
        return getByDimension(topic, KnowledgePoint.UNCLASSIFIED);
    }

    public Optional<KnowledgePoint> findById(String topic, String id) {
        return getByTopic(topic).stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public boolean isEmpty(String topic) {
        return getByTopic(topic).isEmpty();
    }

    private void rebuild(Map<String, List<StemEntry>> stemsByTopic, String sourceHash) {
        byTopic.clear();
        loadedFromCache = false;
        for (Map.Entry<String, List<StemEntry>> e : stemsByTopic.entrySet()) {
            String topic = e.getKey();
            List<KnowledgePoint> points = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();
            for (StemEntry entry : e.getValue()) {
                String id = KnowledgePoint.stableId(topic, entry.stem());
                if (!seenIds.add(id)) continue;
                String dim = DimensionLabeler.label(topic, entry.stem());
                points.add(new KnowledgePoint(
                        id, topic, dim, KnowledgePoint.normalizeStem(entry.stem()),
                        entry.source(), DimensionLabeler.labelSourceFor(dim)));
            }
            byTopic.put(topic, List.copyOf(points));
        }
        saveCache(sourceHash);
        log.info("📚 KnowledgePointCatalog 重建完成: topics={}, points={}",
                byTopic.size(), byTopic.values().stream().mapToInt(List::size).sum());
    }

    private boolean tryLoadCache(String sourceHash) {
        Path meta = cacheDir.resolve("meta.json");
        Path data = cacheDir.resolve("points.json");
        if (!Files.exists(meta) || !Files.exists(data)) return false;
        try {
            Map<String, String> metaMap = MAPPER.readValue(meta.toFile(), new TypeReference<>() {});
            if (!sourceHash.equals(metaMap.get("sourceHash"))) return false;
            Map<String, List<KnowledgePoint>> loaded = MAPPER.readValue(data.toFile(), new TypeReference<>() {});
            byTopic.clear();
            byTopic.putAll(loaded);
            loadedFromCache = true;
            log.info("📚 KnowledgePointCatalog 命中缓存: topics={}, points={}",
                    byTopic.size(), byTopic.values().stream().mapToInt(List::size).sum());
            return true;
        } catch (Exception e) {
            log.warn("Catalog 缓存读取失败，将重建: {}", e.getMessage());
            return false;
        }
    }

    private void saveCache(String sourceHash) {
        try {
            Files.createDirectories(cacheDir);
            MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(cacheDir.resolve("meta.json").toFile(), Map.of("sourceHash", sourceHash));
            MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(cacheDir.resolve("points.json").toFile(), byTopic);
        } catch (IOException e) {
            log.warn("Catalog 缓存写入失败: {}", e.getMessage());
        }
    }

    private void logDistribution() {
        for (String topic : byTopic.keySet()) {
            Map<String, Long> dist = getByTopic(topic).stream()
                    .collect(Collectors.groupingBy(KnowledgePoint::dimension, Collectors.counting()));
            log.info("📊 Catalog [{}] 维度分布: {}", topic, dist);
        }
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
