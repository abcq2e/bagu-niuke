package com.qian.qianaiagent.app;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面试题目文档缓存 — 启动时一次性加载所有方向题目。
 * <p>
 * 🔴 不再做题目→维度分类（维度分类导致大量题目被丢弃）。
 * 所有题目直接缓存，按方向提供给 AI 选题。
 * 维度覆盖追踪由 AI 回复中的 [DIM:xxx] 标记独立处理。
 */
@Component
@Slf4j
public class TopicDocumentCache {

    /** topic → 该方向所有题目的混合池（bagu + 面渣逆袭） */
    private final Map<String, List<String>> mixedCache = new ConcurrentHashMap<>();
    /** topic → 有序题目列表（bagu在前，面渣逆袭按顺序接后，严格保序） */
    private final Map<String, List<String>> orderedCache = new ConcurrentHashMap<>();
    /** 🔴 topic → bagu 题目数量（bagu 在前，所以 index < baguCount 都是 bagu 题） */
    private final Map<String, Integer> baguCountCache = new ConcurrentHashMap<>();
    /** 🔴 不设上限，所有题目全部喂给 AI 自由选题 */
    private static final int MAX_QUESTIONS_PER_TOPIC = Integer.MAX_VALUE;
    @PostConstruct
    public void init() {
        long start = System.currentTimeMillis();
        int totalBagus = 0, totalMianzha = 0;
        for (String topic : SequentialRotationService.TOPIC_NAMES) {
            // 加载 bagu 文档
            String baguFile = SequentialRotationService.topicToFilename(topic);
            List<String> baguQuestions = loadQuestionsFromFile(baguFile);
            // 加载面渣逆袭文档
            List<String> mianzhaFiles = SequentialRotationService.topicToMianzhaFilenames(topic);
            List<String> mianzhaQuestions = new ArrayList<>();
            for (String mf : mianzhaFiles) {
                mianzhaQuestions.addAll(loadMianzhaQuestions(mf));
            }
            // 合并去重（基于文本相同判断）
            Set<String> uniqueSet = new LinkedHashSet<>(baguQuestions);
            uniqueSet.addAll(mianzhaQuestions);
            List<String> allQuestions = new ArrayList<>(uniqueSet);
            mixedCache.put(topic, allQuestions);
            totalBagus += baguQuestions.size();
            totalMianzha += mianzhaQuestions.size();

            // 🔴 [Bug修复] 构建有序列表（bagu在前 + 面渣逆袭接后，严格保序，去重）
            // 面渣逆袭中可能与bagu有重复题目，需跳过已在bagu中的题
            Set<String> baguSet = new HashSet<>(baguQuestions);
            List<String> ordered = new ArrayList<>(baguQuestions);
            int dupCount = 0;
            for (String mq : mianzhaQuestions) {
                if (!baguSet.contains(mq)) {
                    ordered.add(mq);
                } else {
                    dupCount++;
                }
            }
            orderedCache.put(topic, List.copyOf(ordered));
            // 🔴 记录 bagu 题目数（用于判断题目来源）
            baguCountCache.put(topic, baguQuestions.size());

            log.info("📚 [{}] 加载: bagu={}题(纯题目), 面渣={}题(QA提取), 去重{}题, 有序合计={}题",
                    topic, baguQuestions.size(), mianzhaQuestions.size(), dupCount, ordered.size());
        }
        log.info("✅ 题库加载完成: {}个方向, bagu共{}题, 面渣共{}题, 耗时{}ms",
                SequentialRotationService.TOPIC_NAMES.size(), totalBagus, totalMianzha,
                System.currentTimeMillis() - start);
    }

    /**
     * 🔴 获取指定方向的所有题目（混洗后取前 N 题），不分维度。
     * 让 AI 从完整的题目池中自由选题，通过 [DIM:xxx] 标记追踪维度覆盖。
     */
    public List<DimensionQuestion> getQuestionsByDimension(String topic, List<String> coveredDimensions) {
        List<String> pool = mixedCache.get(topic);
        if (pool == null || pool.isEmpty()) return List.of();

        // 混洗后取前 N 题，保证每次出题不一样
        List<String> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        int count = Math.min(MAX_QUESTIONS_PER_TOPIC, shuffled.size());

        List<DimensionQuestion> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // dimension 传空字符串，前端/上下文不展示维度标签，但兼容现有 DimensionQuestion 类型
            result.add(new DimensionQuestion("", shuffled.get(i)));
        }
        return result;
    }

    /** 带维度标签的题目（dimension 可为空，仅保持接口兼容） */
    public record DimensionQuestion(String dimension, String question) {}

    // ===== 向后兼容方法 =====

    public List<String> getBaguQuestions(String topic) {
        return mixedCache.getOrDefault(topic, List.of());
    }

    public List<String> getMianzhaQuestions(String topic) {
        return mixedCache.getOrDefault(topic, List.of());
    }

    /** 原始题干列表（供 KnowledgePointCatalog 构建，不做混洗） */
    public List<String> getRawQuestions(String topic) {
        return List.copyOf(mixedCache.getOrDefault(topic, List.of()));
    }

    /**
     * 🔴 [顺序轮询] 获取某方向的有序题目列表。
     * 顺序：bagu文件题目在前（按文件行序），面渣逆袭题目在后（按文件行序）。
     * 不混洗，严格保序。
     */
    public List<String> getOrderedQuestions(String topic) {
        return orderedCache.getOrDefault(topic, List.of());
    }

    /**
     * 🔴 [顺序轮询] 获取某方向的有序题目总数。
     */
    public int getOrderedQuestionCount(String topic) {
        List<String> ordered = orderedCache.get(topic);
        return ordered != null ? ordered.size() : 0;
    }

    /**
     * 🔴 获取某方向的 bagu（纯题目）数量。
     * 有序列表中 index &lt; getBaguCount(topic) 的都是 bagu 题，
     * index &gt;= getBaguCount(topic) 的是面渣逆袭提取题。
     */
    public int getBaguCount(String topic) {
        return baguCountCache.getOrDefault(topic, 0);
    }

    /**
     * 🔴 判断有序列表中指定索引的题目来源。
     * @param topic 方向名
     * @param index 在有序列表中的索引（0-based）
     * @return "📖 bagu" 或 "📝 面渣逆袭"
     */
    public String getQuestionSource(String topic, int index) {
        int baguCount = getBaguCount(topic);
        return index < baguCount ? "📖 bagu（纯题目）" : "📝 面渣逆袭（QA提取）";
    }

    /**
     * 🔴 [顺序轮询] 获取有序题目列表中指定范围的题目。
     * @param topic 方向名
     * @param startIndex 起始索引（0-based，包含）
     * @param endIndex 结束索引（0-based，不包含）
     * @return 题目列表（不可变），索引越界时返回空或部分列表
     */
    public List<String> getOrderedQuestionRange(String topic, int startIndex, int endIndex) {
        List<String> ordered = orderedCache.get(topic);
        if (ordered == null || ordered.isEmpty()) return List.of();
        int start = Math.max(0, startIndex);
        int end = Math.min(endIndex, ordered.size());
        if (start >= end) return List.of();
        return List.copyOf(ordered.subList(start, end));
    }

    /** @deprecated 所有题目已混合，不再区分维度 */
    @Deprecated
    public List<String> getMixedQuestions(String topic, int totalQuestions) {
        List<String> pool = mixedCache.get(topic);
        if (pool == null || pool.isEmpty()) return List.of();
        List<String> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, Math.min(totalQuestions, shuffled.size()));
    }

    /** @deprecated 不再区分维度 */
    @Deprecated
    public List<String> getDimensionAwareMixedQuestions(String topic, int totalQuestions,
                                                         List<String> coveredDimensions) {
        return getMixedQuestions(topic, totalQuestions);
    }

    // ===== 文件加载方法 =====

    /** 去掉行首序号 "1. ", "123. " 等 */
    private static final java.util.regex.Pattern LEADING_NUMBER = java.util.regex.Pattern.compile("^\\d+\\.\\s*");

    private static List<String> loadQuestionsFromFile(String filename) {
        List<String> questions = new ArrayList<>();
        try {
            Resource resource = new ClassPathResource("document/" + filename);
            if (!resource.exists()) return questions;
            // 🔴 [Bug修复] 统一换行符为 \n，防止 CRLF 导致 \n---\n 分割失败
            String content = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace("\r", "\n");
            for (String section : content.split("\n---\n")) {
                for (String line : section.split("\n")) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("---")
                            || line.startsWith("（注：") || line.startsWith("(注：")) continue;
                    // 去掉序号前缀，如 "1. "、"123. "
                    line = LEADING_NUMBER.matcher(line).replaceFirst("");
                    if (!line.isEmpty()) {
                        questions.add(line);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("文档加载失败 [{}]: {}", filename, e.getMessage());
        }
        return questions;
    }

    /** 面渣逆袭中常见答案开头模式（这些行不是题目，是答案） */
    private static final java.util.Set<String> ANSWER_START_PATTERNS = java.util.Set.of(
            "换句话说", "换句话", "所谓的", "举个", "例如", "比如说",
            "简单来说", "注意", "总结", "推荐", "需要", "可以",
            "所谓", "也就是", "总之", "原因", "核心",
            "本质", "底层", "源码", "实现", "这里", "上面", "下面",
            "Java 是", "Java 语", "当", "在 Java", "它", "我们",
            "因此", "所以", "但是", "不过", "另外", "此外", "当然",
            "实际", "其中", "以上", "这段", "这个", "那个");

    private static List<String> loadMianzhaQuestions(String filename) {
        List<String> questions = new ArrayList<>();
        try {
            Resource resource = new ClassPathResource("document/" + filename);
            if (!resource.exists()) return questions;
            String content = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace("\r", "\n");
            for (String line : content.split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(">")
                        || line.startsWith("memo") || line.startsWith("推荐阅读")) continue;
                if (line.contains("面试指南") || line.contains("面渣逆袭") || line.contains("No.")) {
                    int colonIdx = line.indexOf('：');
                    if (colonIdx < 0) colonIdx = line.indexOf(':');
                    if (colonIdx >= 0 && colonIdx < line.length() - 1) {
                        String afterColon = line.substring(colonIdx + 1).trim();
                        if (afterColon.length() > 5 && afterColon.length() < 150
                                && (afterColon.endsWith("？") || afterColon.endsWith("?")
                                    || afterColon.endsWith("（补充）") || afterColon.endsWith("(补充)")
                                    || afterColon.endsWith("?）") || afterColon.endsWith("?)"))) {
                            questions.add(afterColon);
                            continue;
                        }
                    }
                    continue;
                }
                String text = line.replaceFirst("^\\d+(\\.\\d+)*\\.?\\s*", "");
                if (text.matches("\\d+") || text.isBlank()) continue;
                // 🔴 严格过滤：题目长度 8-150 字，且必须像问句
                if (text.length() < 8 || text.length() > 150) continue;
                // 🔴 排除答案特征行：以答案常见词开头
                // 🔴 [Bug修复] 但如果以问号结尾，说明是问题而非答案，不过滤（如"Java 是什么语言？"以"Java 是"开头但仍是问题）
                boolean looksLikeAnswer = false;
                boolean endsWithQuestionMark = text.endsWith("？") || text.endsWith("?") || text.endsWith("?)") || text.endsWith("?）");
                if (!endsWithQuestionMark) {
                    for (String pattern : ANSWER_START_PATTERNS) {
                        if (text.startsWith(pattern)) {
                            looksLikeAnswer = true;
                            break;
                        }
                    }
                }
                if (looksLikeAnswer) continue;
                // 🔴 排除含代码特征的行（{}、(); 等代码片段）
                if (text.contains("{") || text.contains("}") || text.contains("();")) continue;
                // 必须以问号或以补充/加餐/增补结尾
                if (text.endsWith("？") || text.endsWith("?") || text.endsWith("?)") || text.endsWith("?）")
                        || text.endsWith("吗？") || text.endsWith("呢？")
                        || text.endsWith("（补充）") || text.endsWith("(补充)")
                        || text.endsWith("（加餐）") || text.endsWith("（增补）")) {
                    questions.add(text);
                }
            }
        } catch (Exception e) {
            log.warn("面渣逆袭加载失败 [{}]: {}", filename, e.getMessage());
        }
        return questions;
    }
}
