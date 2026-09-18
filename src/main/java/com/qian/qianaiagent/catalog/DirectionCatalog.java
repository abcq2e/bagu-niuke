package com.qian.qianaiagent.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试方向目录 —— 16 个学习方向的单一真源。
 * <p>
 * 原散落在 {@code interview.rotation.SequentialRotationService} 中，被 knowledge / ability / rag
 * 反向 import 引用形成循环依赖。现下沉为纯静态叶子包：任何包都只单向引用它，它不依赖任何领域包。
 * <p>
 * 承载两类语义（代码里两种称呼并存，实为同一物）：
 * <ul>
 *   <li><b>direction</b>：面试侧叫"方向"（{@link #DIRECTIONS} / {@link #DIRECTION_MAP}）</li>
 *   <li><b>topic</b>：knowledge / ability / rag 侧叫"主题"（{@link #TOPIC_NAMES} / 文件名映射）</li>
 * </ul>
 */
public final class DirectionCatalog {

    private DirectionCatalog() {}

    // ===== 方向定义（学习路线顺序） =====

    /** 方向定义：名称、每轮题数、源文件列表（有序：bagu在前，面渣逆袭接后） */
    public record DirectionDef(String name, int questionsPerRound, List<String> sourceFiles) {}

    public static final List<DirectionDef> DIRECTIONS = List.of(
            new DirectionDef("Java基础与集合", 3,
                    List.of("01-bagu-java-basics", "01-面渣逆袭-Java基础", "01-面渣逆袭-集合框架")),
            new DirectionDef("JVM", 3,
                    List.of("02-bagu-jvm", "02-面渣逆袭-JVM")),
            new DirectionDef("Java并发", 3,
                    List.of("03-bagu-java-concurrency", "03-面渣逆袭-并发编程")),
            new DirectionDef("操作系统与Linux", 3,
                    List.of("04-bagu-os-linux", "04-面渣逆袭-操作系统")),
            new DirectionDef("计算机网络", 3,
                    List.of("05-bagu-network", "05-面渣逆袭-计算机网络")),
            new DirectionDef("MySQL", 3,
                    List.of("06-bagu-mysql", "06-面渣逆袭-MySQL")),
            new DirectionDef("Redis", 3,
                    List.of("07-bagu-redis", "07-面渣逆袭-Redis")),
            new DirectionDef("消息队列", 3,
                    List.of("08-bagu-mq", "08-面渣逆袭-RocketMQ")),
            new DirectionDef("Spring框架", 3,
                    List.of("09-bagu-spring", "09-面渣逆袭-Spring", "09-面渣逆袭-MyBatis")),
            new DirectionDef("设计模式", 2,
                    List.of("10-bagu-design-patterns")),
            new DirectionDef("分布式与微服务", 2,
                    List.of("11-bagu-distributed", "11-面渣逆袭-分布式", "11-面渣逆袭-微服务")),
            new DirectionDef("系统设计与场景", 2,
                    List.of("12-bagu-system-design")),
            new DirectionDef("算法与数据结构", 2,
                    List.of("13-bagu-algorithm")),
            new DirectionDef("Docker与运维", 2,
                    List.of("14-bagu-docker")),
            new DirectionDef("ES与搜索", 2,
                    List.of("15-bagu-es-search")),
            new DirectionDef("Agent与AI应用", 2,
                    List.of("16-bagu-agent-ai"))
    );

    /** 方向名 → 方向定义 */
    public static final Map<String, DirectionDef> DIRECTION_MAP = buildDirectionMap();

    /** 方向名列表（保持顺序） */
    public static final List<String> TOPIC_NAMES = DIRECTIONS.stream()
            .map(DirectionDef::name).toList();

    /** 方向名 → bagu文件名（不带.md扩展名） */
    public static final Map<String, String> TOPIC_TO_BAGU_FILENAME = buildTopicToBagu();

    /** 方向名 → 面渣逆袭文件名列表（不带.md扩展名） */
    public static final Map<String, List<String>> TOPIC_TO_MIANZHA_FILENAMES = buildTopicToMianzha();

    /** 文件名（不带扩展名）→ 方向名 逆向映射（给文档元数据打标用） */
    public static final Map<String, String> FILENAME_TO_TOPIC = buildFilenameToTopic();

    private static Map<String, DirectionDef> buildDirectionMap() {
        Map<String, DirectionDef> map = new LinkedHashMap<>();
        for (DirectionDef d : DIRECTIONS) {
            map.put(d.name(), d);
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> buildTopicToBagu() {
        Map<String, String> map = new LinkedHashMap<>();
        for (DirectionDef d : DIRECTIONS) {
            String baguFile = d.sourceFiles().get(0); // 第一个总是bagu文件
            map.put(d.name(), baguFile);
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, List<String>> buildTopicToMianzha() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (DirectionDef d : DIRECTIONS) {
            List<String> mianzha = d.sourceFiles().size() > 1
                    ? d.sourceFiles().subList(1, d.sourceFiles().size())
                    : List.of();
            map.put(d.name(), Collections.unmodifiableList(mianzha));
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> buildFilenameToTopic() {
        Map<String, String> map = new LinkedHashMap<>();
        for (DirectionDef d : DIRECTIONS) {
            for (String file : d.sourceFiles()) {
                map.put(file, d.name());
            }
        }
        return Collections.unmodifiableMap(map);
    }

    // ===== 静态工具方法 =====

    public static String topicToFilename(String topic) {
        String name = TOPIC_TO_BAGU_FILENAME.get(topic);
        return (name != null ? name : "bagu-" + topic) + ".md";
    }

    public static List<String> topicToMianzhaFilenames(String topic) {
        List<String> names = TOPIC_TO_MIANZHA_FILENAMES.get(topic);
        if (names == null) return List.of();
        return names.stream().map(n -> n + ".md").toList();
    }

    public static String topicFromFilename(String filename) {
        if (filename == null || filename.isBlank()) return "default";
        int dotIndex = filename.lastIndexOf('.');
        String base = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        return FILENAME_TO_TOPIC.getOrDefault(base, "default");
    }
}
