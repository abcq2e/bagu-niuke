package com.qian.qianaiagent.app;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 方向轮转服务 — 维护每个会话的方向覆盖状态
 *
 * <p>保证一轮内 16 个方向不重不漏，轮完重新洗牌进入下一轮。
 * 方向推进由三个信号驱动：
 * <ol>
 *   <li>AI 主动信号：System Prompt 要求 AI 在方向考察完毕时输出 [NEXT_TOPIC]</li>
 *   <li>自动驻留控制：最少驻留 {@link #MIN_STAY_ROUNDS} 轮，
 *       连续回答差提前解锁，连续回答好延长驻留</li>
 *   <li>热力图出题引导：展示各子领域覆盖状态 + 权重，驱动 AI 优先覆盖空白高权重子领域</li>
 * </ol>
 */
@Component
@Slf4j
public class TopicRotationService {

    /** @deprecated 请使用 {@link SequentialRotationService#TOPIC_NAMES} */
    @Deprecated
    public static final List<String> TOPICS = SequentialRotationService.TOPIC_NAMES;

    /** 可选：UserAbilityService 引用（通过 setter 注入，避免循环依赖） */
    private UserAbilityService userAbilityService;

    /**
     * 设置能力画像服务（可选注入，避免构造函数循环依赖）
     */
    public void setUserAbilityService(UserAbilityService service) {
        this.userAbilityService = service;
    }

    /** 话题 → ASCII 文件名映射（避免中文文件名在 ClassPathResource 中加载失败） */
    private static final java.util.Map<String, String> TOPIC_TO_FILENAME =
            java.util.Map.ofEntries(
                    java.util.Map.entry("Java基础与集合", "01-bagu-java-basics"),
                    java.util.Map.entry("Java并发", "03-bagu-java-concurrency"),
                    java.util.Map.entry("JVM", "02-bagu-jvm"),
                    java.util.Map.entry("Spring框架", "09-bagu-spring"),
                    java.util.Map.entry("MySQL", "06-bagu-mysql"),
                    java.util.Map.entry("Redis", "07-bagu-redis"),
                    java.util.Map.entry("消息队列", "08-bagu-mq"),
                    java.util.Map.entry("计算机网络", "05-bagu-network"),
                    java.util.Map.entry("操作系统与Linux", "04-bagu-os-linux"),
                    java.util.Map.entry("分布式与微服务", "11-bagu-distributed"),
                    java.util.Map.entry("算法与数据结构", "13-bagu-algorithm"),
                    java.util.Map.entry("设计模式", "10-bagu-design-patterns"),
                    java.util.Map.entry("系统设计与场景", "12-bagu-system-design"),
                    java.util.Map.entry("Docker与运维", "14-bagu-docker"),
                    java.util.Map.entry("ES与搜索", "15-bagu-es-search"),
                    java.util.Map.entry("Agent与AI应用", "16-bagu-agent-ai"));

    /** 话题 → 对应的面渣逆袭文件名列表（可能为空，即该方向没有面渣逆袭补充） */
    private static final java.util.Map<String, java.util.List<String>> TOPIC_TO_MIANZHA =
            java.util.Map.ofEntries(
                    java.util.Map.entry("Java基础与集合", java.util.List.of("01-面渣逆袭-Java基础", "01-面渣逆袭-集合框架")),
                    java.util.Map.entry("Java并发", java.util.List.of("03-面渣逆袭-并发编程")),
                    java.util.Map.entry("JVM", java.util.List.of("02-面渣逆袭-JVM")),
                    java.util.Map.entry("Spring框架", java.util.List.of("09-面渣逆袭-Spring")),
                    java.util.Map.entry("MySQL", java.util.List.of("06-面渣逆袭-MySQL")),
                    java.util.Map.entry("Redis", java.util.List.of("07-面渣逆袭-Redis")),
                    java.util.Map.entry("消息队列", java.util.List.of("08-面渣逆袭-RocketMQ")),
                    java.util.Map.entry("计算机网络", java.util.List.of("05-面渣逆袭-计算机网络")),
                    java.util.Map.entry("操作系统与Linux", java.util.List.of("04-面渣逆袭-操作系统")),
                    java.util.Map.entry("分布式与微服务", java.util.List.of("11-面渣逆袭-分布式", "11-面渣逆袭-微服务")));

    /** 🔴 [Hotfix-RAG联动] 文件名（不含扩展名）→ 方向名逆向映射（用于文档元数据打标） */
    public static final java.util.Map<String, String> FILENAME_TO_TOPIC = buildFilenameToTopic();

    private static java.util.Map<String, String> buildFilenameToTopic() {
        java.util.HashMap<String, String> map = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, String> e : TOPIC_TO_FILENAME.entrySet()) {
            map.put(e.getValue(), e.getKey()); // "bagu-java-concurrency" → "Java并发"
        }
        for (java.util.Map.Entry<String, java.util.List<String>> e : TOPIC_TO_MIANZHA.entrySet()) {
            for (String mz : e.getValue()) {
                map.put(mz, e.getKey()); // "面渣逆袭-并发编程" → "Java并发"
            }
        }
        return java.util.Map.copyOf(map);
    }

    /**
     * 🔴 [Hotfix-RAG联动] 从文件名推断所属方向名。
     * 优先查 {@link #FILENAME_TO_TOPIC}，找不到才 fallback 到默认值。
     *
     * @param filename 完整文件名（如 "bagu-java-concurrency.md"）
     * @return 方向名（如 "Java并发"），未知返回 "default"
     */
    public static String topicFromFilename(String filename) {
        if (filename == null || filename.isBlank()) return "default";
        int dotIndex = filename.lastIndexOf('.');
        String base = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        return FILENAME_TO_TOPIC.getOrDefault(base, "default");
    }

    /** 话题 → 纯英文文件名（xxx.md），供 ClassPathResource 使用 */
    public static String topicToFilename(String topic) {
        return TOPIC_TO_FILENAME.getOrDefault(topic, "bagu-" + topic) + ".md";
    }

    /** 话题 → 面渣逆袭补充文件名列表（带 .md 后缀） */
    public static java.util.List<String> topicToMianzhaFilenames(String topic) {
        java.util.List<String> names = TOPIC_TO_MIANZHA.get(topic);
        if (names == null) return java.util.List.of();
        return names.stream().map(n -> n + ".md").toList();
    }

    // ===== 🔴 [Hotfix-驻留轮次] 动态驻留常量 =====

    /** 同一方向最少驻留轮次（保证面试连续性，避免频繁切换） */
    private static final int MIN_STAY_ROUNDS = 2;
    /** 连续差回答触发提前解锁的阈值 */
    private static final int WEAK_ANSWER_UNLOCK_THRESHOLD = 2;
    /** 连续好回答触发延长驻留的阈值 */
    private static final int STRONG_ANSWER_EXTEND_THRESHOLD = 2;
    /** 好回答延长的额外轮次 */
    private static final int STRONG_ANSWER_EXTEND_ROUNDS = 1;

    // ===== 🔴 [Hotfix-DETAIL归一化] DETAIL 去重阈值 =====

    /** DETAIL 归一化合并的相似度阈值（trigram Jaccard），高于此值判为同一考点 */
    private static final double DETAIL_NORMALIZE_THRESHOLD = 0.85;

    /** 🔴 [Hotfix-热力图裁剪] 热力图最多展示的 DETAIL 条目数 */
    private static final int HEATMAP_MAX_DETAILS = 30;

    /** 🔴 [Hotfix-DETAIL兜底] DIM 标记提取正则 */
    private static final Pattern DIM_MARKER_PATTERN = Pattern.compile(
            "\\[DIM:([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);

    /** DETAIL 行提取正则（兼容旧版 [DETAIL]xxx,yyy 格式） */
    private static final Pattern DETAIL_SIMPLE_LINE = Pattern.compile(
            "\\[DETAIL\\](.*?)(\\n|$)", Pattern.CASE_INSENSITIVE);

    /** 🔴 [增强] ---DETAIL--- JSON 块提取正则（支持难度等级） */
    private static final Pattern DETAIL_JSON_BLOCK = Pattern.compile(
            "---DETAIL---\\s*\\n?\\[[\\s\\S]*?\\]\\s*\\n?---DETAIL---",
            Pattern.CASE_INSENSITIVE);
    /** JSON 块内提取 detail 和 level 字段 */
    private static final Pattern JSON_DETAIL_FIELD = Pattern.compile(
            "\"detail\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_LEVEL_FIELD = Pattern.compile(
            "\"level\"\\s*:\\s*(\\d+)");

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    /**
     * 🔴 [增强] 细分考点记录
     * @param detail 考点名称（归一化后）
     * @param level  难度等级（1-3，默认1）
     */
    public record DetailedPoint(String detail, int level) {}

    private class SessionState {
        final String chatId;
        final Deque<String> remaining;
        final List<String> covered;
        String currentTopic;
        int exchangesOnCurrent;
        /** 🔴 [Hotfix-驻留轮次] 当前大类的停留轮次计数 */
        int stayRounds;
        /** 🔴 [终版-双链路] 预选出题维度（本轮 pendingDim） */
        String pendingDimension;
        long lastAccess;
        /** 🔴 [四大改进-P2] 已问题目指纹（从 AI 实际提问中提取） */
        final Set<String> askedQuestionFingerprints = new HashSet<>();
        /** 🔴 [四大改进-3.4] 方向 → 已覆盖的知识点维度（跨轮次持久化，保留兼容） */
        final Map<String, Set<String>> topicCoveredDimensions = new HashMap<>();

        // ===== 🔴 [Hotfix-DETAIL] 细分考点与热力图（新增） =====

        /** 🔴 [全覆盖] 按 DIM 标签的出题计数（topic → subDim → 出题数）。
         *  独立于 DETAIL 计数：topicSubDimCount 按 DETAIL 细粒度增，
         *  这个按 DIM 标签每道题一增。饱和度判断基于 dimQuestionCount。 */
        final Map<String, Map<String, Integer>> dimQuestionCount = new HashMap<>();

        /** 方向 → 已考细分考点集合（归一化后，用于去重和展示） */
        final Map<String, Set<String>> topicDetailedRecords = new HashMap<>();
        /** 🔴 [增强] 方向 → 二级子领域 → 已考 DETAIL 集合（含难度等级） */
        final Map<String, Map<String, Set<DetailedPoint>>> topicSubDimDetailedRecords = new HashMap<>();
        /** 方向 → 二级子领域 → 出题计数（热力图数据源） */
        final Map<String, Map<String, Integer>> topicSubDimCount = new HashMap<>();
        /** 最近回答质量记录（前端轮次有限队列），true=好, false=差 */
        final LinkedList<Boolean> recentAnswerQuality = new LinkedList<>();
        /** 连续差回答次数 */
        int consecutiveWeakAnswers = 0;
        /** 连续好回答次数 */
        int consecutiveStrongAnswers = 0;

        SessionState(String chatId) {
            this.chatId = chatId;
            remaining = new ArrayDeque<>(shuffledWithWeights(chatId));
            covered = new ArrayList<>();
            currentTopic = remaining.pollFirst();
            exchangesOnCurrent = 0;
            stayRounds = 0;
            lastAccess = System.currentTimeMillis();
        }
    }

    /**
     * 加权随机洗牌：掌握度越低的方向越靠前（优先考察薄弱方向）
     */
    private List<String> shuffledWithWeights(String chatId) {
        List<String> topics = new ArrayList<>(TOPICS);
        if (userAbilityService == null) {
            Collections.shuffle(topics);
            return topics;
        }
        // 按权重升序排列（权重越高越薄弱，排越前面）
        topics.sort(java.util.Comparator.comparingDouble(
                t -> -userAbilityService.getTopicWeight(chatId, t)));
        // 保持排序大体稳定但加入少量随机扰动，避免每次顺序完全一样
        for (int i = 0; i < topics.size(); i++) {
            int swapRange = Math.min(3, topics.size() - i);
            int j = i + (int) (Math.random() * swapRange);
            java.util.Collections.swap(topics, i, j);
        }
        return topics;
    }

    /**
     * 🔴 [Hotfix-驻留轮次] 每条用户消息调用一次：返回当前方向并计数。
     * <p>
     * 自动推进判断：
     * - 最少驻留 {@link #MIN_STAY_ROUNDS} 轮保证面试连续性
     * - 连续差回答 ≥ {@link #WEAK_ANSWER_UNLOCK_THRESHOLD} → 提前解锁切换
     * - 连续好回答 ≥ {@link #STRONG_ANSWER_EXTEND_THRESHOLD} → 延长驻留
     * - AI 主动输出 [NEXT_TOPIC] 可随时切换（advance 方法）
     * <p>
     * remaining 弹空后：covered 清空、重新洗牌全部方向、开始新一轮。
     */
    public String currentTopic(String chatId) {
        SessionState state = sessions.computeIfAbsent(chatId, SessionState::new);
        synchronized (state) {
            state.lastAccess = System.currentTimeMillis();
            // 🔴 [全覆盖] 切换话题时自动迁移旧覆盖数据
            String prevTopic = state.currentTopic;
            // 🔴 动态驻留：自动推进判断
            if (shouldAutoAdvance(state)) {
                advanceInternal(state);
            }
            // 🔴 [全覆盖] 话题变化时触发旧数据迁移
            if (state.currentTopic != null && !state.currentTopic.equals(prevTopic)) {
                migrateLegacyCoverage(chatId, state.currentTopic);
            }
            state.exchangesOnCurrent++;
            state.stayRounds++;
            return state.currentTopic;
        }
    }

    /**
     * 🔴 [Hotfix-驻留轮次] 判断是否应当自动推进到下一方向。
     * <p>
     * 优先级：
     * 1. 连续差回答 ≥ 阈值 → 提前解锁（即使驻留不足）
     * 2. 驻留轮次 ≥ 有效上限 → 自动推进
     */
    private boolean shouldAutoAdvance(SessionState state) {
        // 连续答差 → 提前解锁切换（但至少问过 2 题，避免一轮游）
        if (state.consecutiveWeakAnswers >= WEAK_ANSWER_UNLOCK_THRESHOLD
                && state.exchangesOnCurrent >= 2) {
            log.debug("⏭️ 连续{}次回答质量差，提前解锁方向切换: topic={}",
                    state.consecutiveWeakAnswers, state.currentTopic);
            return true;
        }
        // 驻留轮次 ≥ 有效上限 → 正常推进
        int effectiveMax = MIN_STAY_ROUNDS;
        if (state.consecutiveStrongAnswers >= STRONG_ANSWER_EXTEND_THRESHOLD) {
            effectiveMax = MIN_STAY_ROUNDS + STRONG_ANSWER_EXTEND_ROUNDS;
            log.debug("🔁 连续{}次回答优秀，延长驻留至{}轮: topic={}",
                    state.consecutiveStrongAnswers, effectiveMax, state.currentTopic);
        }
        return state.exchangesOnCurrent >= effectiveMax;
    }

    /**
     * 🔴 [Hotfix-驻留轮次] 记录用户回答质量。
     * <p>
     * 由 QuizApp 在异步评分完成后调用。
     * 维护 {@code recentAnswerQuality} 滑动窗口，更新连续好/差计数。
     *
     * @param chatId 会话 ID
     * @param isGood true=回答好（评分≥3），false=回答差（评分<3）
     */
    public void recordAnswerQuality(String chatId, boolean isGood) {
        SessionState state = sessions.get(chatId);
        if (state == null) return;
        String currentTopic = null;
        synchronized (state) {
            // 滑动窗口，最多保留 5 条记录
            state.recentAnswerQuality.addLast(isGood);
            if (state.recentAnswerQuality.size() > 5) {
                state.recentAnswerQuality.removeFirst();
            }
            // 🔴 [Bug修复] 只统计从最新端开始的连续运行，而非遍历全部后取最旧值
            java.util.Iterator<Boolean> it = state.recentAnswerQuality.descendingIterator();
            if (it.hasNext()) {
                boolean first = it.next();
                int count = 1;
                while (it.hasNext() && it.next() == first) {
                    count++;
                }
                if (first) {
                    state.consecutiveStrongAnswers = count;
                    state.consecutiveWeakAnswers = 0;
                } else {
                    state.consecutiveWeakAnswers = count;
                    state.consecutiveStrongAnswers = 0;
                }
            }
        }
    }

    /** AI 输出了 [NEXT_TOPIC] 标记时调用 */
    public void advance(String chatId) {
        SessionState state = sessions.get(chatId);
        if (state == null) return;
        synchronized (state) {
            advanceInternal(state);
        }
    }

    /**
     * 🔴 [Hotfix-用户干预] 用户主动要求「换个方向」，强制推进到下一大类。
     * 与 {@link #advance(String)} 行为相同，但日志区分触发来源。
     */
    public void forceAdvance(String chatId) {
        SessionState state = sessions.get(chatId);
        if (state == null) return;
        synchronized (state) {
            log.info("⏭️ [用户操作] 强制切换方向: {} → 下一方向", state.currentTopic);
            advanceInternal(state);
        }
    }

    /**
     * 🔴 [Hotfix-用户干预] 用户主动要求「继续当前方向」，重置驻留计数。
     * 重置后相当于在当前方向又获得了最少 MIN_STAY_ROUNDS 轮的驻留时间，
     * 不会被自动推进切换。
     */
    public void resetStay(String chatId) {
        SessionState state = sessions.get(chatId);
        if (state == null) return;
        synchronized (state) {
            log.info("🔁 [用户操作] 重置驻留计数，继续深挖当前方向: {}", state.currentTopic);
            state.exchangesOnCurrent = 0;
            state.stayRounds = 0;
            state.consecutiveWeakAnswers = 0;
            state.consecutiveStrongAnswers = 0;
            state.recentAnswerQuality.clear();
        }
    }

    /**
     * 🔴 [Hotfix-驻留轮次] 内部推进逻辑：重置驻留相关计数
     */
    private void advanceInternal(SessionState state) {
        if (state.currentTopic != null) {
            state.covered.add(state.currentTopic);
        }
        // 🔴 [终版] 切换方向时清除预选维度
        state.pendingDimension = null;
        if (state.remaining.isEmpty()) {
            // 一轮完成，重新洗牌（加权）
            state.remaining.addAll(shuffledWithWeights(state.chatId));
            state.covered.clear();
            log.info("🔄 所有方向已考察完毕，重新加权洗牌开始新一轮");
        }
        state.currentTopic = state.remaining.pollFirst();
        state.exchangesOnCurrent = 0;
        state.stayRounds = 0;
        state.consecutiveWeakAnswers = 0;
        state.consecutiveStrongAnswers = 0;
        state.recentAnswerQuality.clear();
    }

    /** 本轮已考察完的方向（不含当前方向），用于注入 prompt */
    public List<String> coveredTopics(String chatId) {
        SessionState state = sessions.get(chatId);
        if (state == null) return List.of();
        synchronized (state) {
            return new ArrayList<>(state.covered);
        }
    }

    // ===== 🔴 [四大改进-P2][题目重复-修复] 已问题目去重与持久化 =====

    /**
     * 从 AI 回复中提取所有实际提问的题目指纹并记录（内存 + 持久化双写）。
     * 在 doOnComplete() 中调用。
     */
    public void recordAskedQuestion(String chatId, String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) return;
        Set<String> fingerprints = extractAllQuestionFingerprints(aiResponse);
        if (fingerprints.isEmpty()) return;

        SessionState state = sessions.get(chatId);
        if (state == null) return;

        synchronized (state) {
            state.askedQuestionFingerprints.addAll(fingerprints);
        }

        // 持久化到 UserAbilityProfile（跨会话/跨重启保留）
        if (userAbilityService != null) {
            try {
                UserAbilityProfile profile = userAbilityService.getOrCreateProfile(chatId);
                profile.getAskedQuestionFingerprints().addAll(fingerprints);
            } catch (Exception e) {
                log.warn("持久化已问题目指纹失败: {}", e.getMessage());
            }
        }

        log.debug("📝 记录 {} 个已问题目指纹 (累计 {})",
                fingerprints.size(),
                state != null ? state.askedQuestionFingerprints.size() : "?");
    }

    /**
     * 从 AI 回复中提取所有提问句的指纹（以？/? 结尾的句子，前 40 字）
     */
    private Set<String> extractAllQuestionFingerprints(String text) {
        Set<String> fingerprints = new HashSet<>();
        for (String q : extractAllQuestions(text)) {
            if (q.length() > 40) {
                fingerprints.add(q.substring(0, 40));
            } else {
                fingerprints.add(q);
            }
        }
        return fingerprints;
    }

    /**
     * 从 AI 回复中提取所有提问句（完整句子），用于维度推断。
     */
    private List<String> extractAllQuestions(String text) {
        List<String> questions = new ArrayList<>();
        String[] sentences = text.split("(?<=[。！？!?\\n])");
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.endsWith("？") || trimmed.endsWith("?")) {
                String clean = trimmed.replaceAll("^[\\s\\n\\r]+", "").replaceAll("[\\s\\n\\r]+$", "");
                if (clean.length() > 10) {
                    questions.add(clean);
                }
            }
        }
        return questions;
    }

    /** [全覆盖] 去重相似度阈值 - 严格模式（同一知识点去重用，0.25 ≈ 共享 1/4 三字组合即拦截） */
    private static final double DEDUP_THRESHOLD_STRICT = 0.25;

    /** [全覆盖] 去重相似度阈值 - 宽松模式（同维度不同知识点用，0.6 ≈ 极高相似才拦截） */
    private static final double DEDUP_THRESHOLD_RELAXED = 0.6;

    /**
     * 过滤已问题目：将候选题与 askedQuestionFingerprints 做 trigram 相似度匹配，
     * 排除已被 AI 问过的题（含相同知识点的不同问法）。全部排完才允许重复。
     * <p>
     * 改用 trigram（3-gram）替代旧版 bigram，配合更低的 0.25 阈值：
     * - 中文技术词多为 2-4 字（如"线程池"、"ConcurrentHashMap"），trigram 能更好捕捉
     * - "HashMap put流程中如何计算下标" vs "HashMap put操作的下标计算方式"：
     *   bigram(0.44) 漏判 → trigram(0.27) 拦截 ✅
     * - "线程池核心参数" vs "线程池拒绝策略"：trigram(~0.05) 通过 ✅
     */

    /**
     * [全覆盖] 解析动态去重阈值。
     * 根据当前维度的饱和度决定使用严格还是宽松阈值：
     * - 维度未饱和（还需补题）→ 宽松阈值 0.6，减少拦截
     * - 维度已饱和或无预选维度 → 严格阈值 0.25，严格去重
     */
    private double resolveDynamicThreshold(SessionState state) {
        if (state == null || state.pendingDimension == null || state.currentTopic == null) {
            return DEDUP_THRESHOLD_STRICT;
        }
        // 预选维度未饱和 → 放宽阈值，让更多题目通过
        if (!isDimensionSaturated(state.chatId, state.currentTopic, state.pendingDimension)) {
            return DEDUP_THRESHOLD_RELAXED;
        }
        return DEDUP_THRESHOLD_STRICT;
    }

    public List<String> filterAskedQuestions(String chatId, List<String> candidates) {
        SessionState state = sessions.get(chatId);
        if (state == null || candidates == null || candidates.isEmpty()) return candidates;
        synchronized (state) {
            if (userAbilityService != null && state.askedQuestionFingerprints.isEmpty()) {
                try {
                    Set<String> profileFps = userAbilityService.getOrCreateProfile(chatId).getAskedQuestionFingerprints();
                    if (!profileFps.isEmpty()) {
                        state.askedQuestionFingerprints.addAll(profileFps);
                        log.debug("📝 从持久化加载 {} 个已问题目指纹", profileFps.size());
                    }
                } catch (Exception e) {
                    log.warn("加载持久化指纹失败: {}", e.getMessage());
                }
            }
            if (state.askedQuestionFingerprints.isEmpty()) return candidates;
            List<String> filtered = new ArrayList<>();
            String bestCandidate = null;
            double bestSim = Double.MAX_VALUE;
            for (String candidate : candidates) {
                String candKey = candidate.substring(0, Math.min(30, candidate.length()));
                double maxSim = 0;
                for (String fingerprint : state.askedQuestionFingerprints) {
                    // 等长比较（前 30 字），避免长短对比时 union 膨胀导致相似度被低估
                    String fpKey = fingerprint.length() > 30 ? fingerprint.substring(0, 30) : fingerprint;
                    double sim = trigramSimilarity(candKey, fpKey);
                    if (sim > maxSim) maxSim = sim;
                }
                if (maxSim <= resolveDynamicThreshold(state)) {
                    filtered.add(candidate);
                } else if (maxSim < bestSim) {
                    bestSim = maxSim;
                    bestCandidate = candidate;
                }
            }
            if (filtered.isEmpty()) {
                if (bestCandidate != null) {
                    log.debug("📝 当前方向题目重复度高，仅保留最不相似题: sim={}", String.format("%.2f", bestSim));
                    return List.of(bestCandidate);
                }
                log.debug("📝 当前方向题目已全部问完，返回空让 AI 自行出题: topic={}", state.currentTopic);
                return List.of();
            }
            log.debug("📝 过滤已问题目: {}/{} 未问过", filtered.size(), candidates.size());
            return filtered;
        }
    }

    // ===== 🔴 [题目去重] 基于 "方向::序号" 的布尔标记去重 =====

    /**
     * 🔴 题目唯一键格式："{topic}::{index}"，如 "Java基础与集合::15"。
     * 直接用 Set.contains() 做 O(1) 查重，零误判零漏判。
     */

    /**
     * 🔴 检查某道题是否已经出过（按题目序号精确判重）。
     *
     * @param chatId 会话/用户标识
     * @param topic  方向名
     * @param index  题目在有序列表中的序号（0-based）
     * @return true=已出过，false=未出过
     */
    public boolean isQuestionAsked(String chatId, String topic, int index) {
        String key = topic + "::" + index;

        // 查内存
        SessionState state = sessions.get(chatId);
        if (state != null) {
            synchronized (state) {
                if (state.askedQuestionFingerprints.contains(key)) return true;
            }
        }

        // 查持久化
        if (userAbilityService != null) {
            try {
                if (userAbilityService.getOrCreateProfile(chatId)
                        .getAskedQuestionFingerprints().contains(key)) return true;
            } catch (Exception e) {
                log.warn("查重失败: {}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * 🔴 记录已出题目（布尔标记，双写内存+持久化）。
     *
     * @param chatId 会话/用户标识
     * @param topic  方向名
     * @param index  题目在有序列表中的序号（0-based）
     */
    public void recordQuestionAsked(String chatId, String topic, int index) {
        String key = topic + "::" + index;

        // 写内存
        SessionState state = sessions.computeIfAbsent(chatId, SessionState::new);
        synchronized (state) {
            state.askedQuestionFingerprints.add(key);
        }

        // 写持久化
        if (userAbilityService != null) {
            try {
                userAbilityService.getOrCreateProfile(chatId)
                        .getAskedQuestionFingerprints().add(key);
            } catch (Exception e) {
                log.warn("记录已出题目失败: {}", e.getMessage());
            }
        }

        log.debug("📝 记录已出: {}", key);
    }

    /**
     * 🔴 [Bug修复-并发] 移除已出题目标记（AI 调用失败时回滚去重预占）。
     * 从内存 + 持久化中删除指定题目的记录。
     *
     * @param chatId 会话/用户标识
     * @param topic  方向名
     * @param index  题目在有序列表中的序号（0-based）
     */
    public void removeQuestionAsked(String chatId, String topic, int index) {
        String key = topic + "::" + index;

        // 删内存
        SessionState state = sessions.get(chatId);
        if (state != null) {
            synchronized (state) {
                state.askedQuestionFingerprints.remove(key);
            }
        }

        // 删持久化
        if (userAbilityService != null) {
            try {
                userAbilityService.getOrCreateProfile(chatId)
                        .getAskedQuestionFingerprints().remove(key);
            } catch (Exception e) {
                log.warn("移除已出题目标记失败: {}", e.getMessage());
            }
        }

        log.debug("⏪ 移除已出标记: {}", key);
    }

    /**
     * 🔴 获取已记录题目数量（用于判断是否需要历史迁移）。
     */
    public int getFingerprintCount(String chatId) {
        SessionState state = sessions.get(chatId);
        if (state == null) return 0;
        synchronized (state) {
            return state.askedQuestionFingerprints.size();
        }
    }

    /**
     * 计算两个字符串的语义相似度（trigram Jaccard）
     * <p>
     * 用连续 3 字组合（trigram）的交并比。相比 bigram，trigram 对中文技术词的区分力更强：
     * - 同一知识点的不同问法（"HashMap put流程"vs"HashMap put方法详解"）：~0.25-0.35
     * - 同一方向的不同知识点（"线程池参数"vs"线程池拒绝策略"）：~0.05-0.15
     * - 完全不同的知识点：~0
     */
    private double trigramSimilarity(String a, String b) {
        if (a == null || b == null || a.length() < 3 || b.length() < 3) {
            return fallbackSimilarity(a, b);
        }
        Set<String> trigramsA = new HashSet<>();
        Set<String> trigramsB = new HashSet<>();
        for (int i = 0; i < a.length() - 2; i++) {
            trigramsA.add(a.substring(i, i + 3));
        }
        for (int i = 0; i < b.length() - 2; i++) {
            trigramsB.add(b.substring(i, i + 3));
        }
        if (trigramsA.isEmpty() || trigramsB.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(trigramsA);
        intersection.retainAll(trigramsB);
        Set<String> union = new HashSet<>(trigramsA);
        union.addAll(trigramsB);
        return (double) intersection.size() / union.size();
    }

    /** 短文本降级：单字级 Jaccard */
    private double fallbackSimilarity(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0;
        Set<Character> charsA = new HashSet<>();
        Set<Character> charsB = new HashSet<>();
        for (int i = 0; i < a.length(); i++) charsA.add(a.charAt(i));
        for (int i = 0; i < b.length(); i++) charsB.add(b.charAt(i));
        Set<Character> intersection = new HashSet<>(charsA);
        intersection.retainAll(charsB);
        Set<Character> union = new HashSet<>(charsA);
        union.addAll(charsB);
        return (double) intersection.size() / union.size();
    }

    // ===== 🔴 [四大改进-3.4][题目重复-修复] 方向内维度追踪 =====

    /**
     * 🔴 [废弃] 旧维度记录方法，已被 {@link #confirmDimension} 替代。
     * <p>
     * 保留方法签名兼容外部调用。<br>
     * 核心流程不再使用此方法，统一走双链路确认 {@link #confirmDimension}。
     */
    @Deprecated(since = "1.0")
    public void recordCoveredDimension(String chatId, String aiResponse, String topic) {
        if (aiResponse == null || aiResponse.isBlank() || topic == null) return;

        // 改用新确认流程（含维度名验证门）
        confirmDimension(chatId, topic, aiResponse, null, null);
    }

    /**
     * 老接口兼容 — 仅记录覆盖维度，不保证 topic 准确（advance 时序风险）。
     * 建议迁移到 {@link #recordCoveredDimension(String, String, String)}。
     */
    public void recordCoveredDimension(String chatId, String aiResponse) {
        SessionState state = sessions.get(chatId);
        if (state == null) return;
        synchronized (state) {
            if (state.currentTopic == null) return;
            recordCoveredDimension(chatId, aiResponse, state.currentTopic);
        }
    }

    /**
     * 合并获取指定方向的已覆盖维度（持久化 + 内存）
     */
    private Set<String> getMergedCoveredDimensions(String chatId, String topic) {
        Set<String> persisted = userAbilityService != null
                ? userAbilityService.getOrCreateProfile(chatId).getCoveredDimensions(topic)
                : Set.of();
        Set<String> inMemory = Set.of();
        SessionState state = sessions.get(chatId);
        if (state != null) {
            synchronized (state) {
                inMemory = state.topicCoveredDimensions.getOrDefault(topic, Set.of());
            }
        }
        Set<String> all = new HashSet<>(persisted);
        all.addAll(inMemory);
        return all;
    }

    /**
     * 获取指定方向已覆盖的维度列表（持久化 + 内存合并）
     */
    public List<String> getCoveredDimensions(String chatId, String topic) {
        SessionState state = sessions.get(chatId);
        if (state == null) {
            if (userAbilityService != null) {
                return List.copyOf(userAbilityService.getOrCreateProfile(chatId).getCoveredDimensions(topic));
            }
            return List.of();
        }
        synchronized (state) {
            Set<String> persisted = userAbilityService != null
                    ? userAbilityService.getOrCreateProfile(chatId).getCoveredDimensions(topic)
                    : Set.of();
            Set<String> inMemory = state.topicCoveredDimensions.getOrDefault(topic, Set.of());
            Set<String> all = new HashSet<>(persisted);
            all.addAll(inMemory);
            return List.copyOf(all);
        }
    }

    // ===== 🔴 [全覆盖] 维度饱和度判断 + 旧数据迁移 =====

    /**
     * 获取指定维度已出题数（基于 DIM 标签计数）。
     * 合并内存 dimQuestionCount + 持久化 topicCoveredDimensions 的旧数据作为基线。
     */
    public int getDimensionQuestionCount(String chatId, String topic, String dim) {
        SessionState state = sessions.get(chatId);
        if (state == null) {
            // 无会话状态时，查持久化：如果旧数据标记了覆盖，算 1 题
            if (userAbilityService != null) {
                Set<String> covered = userAbilityService.getOrCreateProfile(chatId).getCoveredDimensions(topic);
                if (covered.contains(dim)) return 1;
            }
            return 0;
        }
        synchronized (state) {
            Map<String, Integer> dimCounts = state.dimQuestionCount.get(topic);
            int count = (dimCounts != null) ? dimCounts.getOrDefault(dim, 0) : 0;
            // 旧数据兼容：如果 topicCoveredDimensions 标记了该维度但 dimQuestionCount 为 0，算 1 题
            Set<String> coveredDims = state.topicCoveredDimensions.get(topic);
            if (count == 0 && coveredDims != null && coveredDims.contains(dim)) {
                // 自动迁移：写入 dimQuestionCount 避免下次重复迁移
                dimCounts = state.dimQuestionCount.computeIfAbsent(topic, k -> new HashMap<>());
                dimCounts.put(dim, 1);
                return 1;
            }
            return count;
        }
    }

    /** 硬选题：直接写入 pendingDimension（不增加出题计数） */
    public void setPendingDimension(String chatId, String dim) {
        SessionState state = sessions.get(chatId);
        if (state == null || dim == null) return;
        synchronized (state) {
            state.pendingDimension = dim;
        }
    }

    /** 供 QuestionSelector：返回某方向各维出题计数快照 */
    public Map<String, Integer> getDimQuestionCounts(String chatId, String topic) {
        Map<String, Integer> result = new HashMap<>();
        for (String dim : TopicDimensions.getDimensions(topic)) {
            result.put(dim, getDimensionQuestionCount(chatId, topic, dim));
        }
        return result;
    }

    /** 硬选题结束一个知识点时，按绑定维度记账 */
    public void recordDimensionAsked(String chatId, String topic, String dim) {
        if (dim == null || KnowledgePoint.UNCLASSIFIED.equals(dim)) {
            return;
        }
        incrementDimQuestionCount(chatId, topic, dim);
        SessionState state = sessions.get(chatId);
        if (state != null) {
            synchronized (state) {
                state.pendingDimension = dim;
            }
        }
    }

    /**
     * 🔴 [全覆盖] 判断指定维度是否已达到出题饱和度。
     * <p>
     * 饱和度 = dimQuestionCount >= 期望值。
     * 期望值 = TopicDimensions.getExpectedMinQuestions(topic, dim)。
     * 达到饱和度的维度才标记为"已覆盖"，否则即使出过题也继续开放。
     */
    public boolean isDimensionSaturated(String chatId, String topic, String dim) {
        int count = getDimensionQuestionCount(chatId, topic, dim);
        int expected = TopicDimensions.getExpectedMinQuestions(topic, dim);
        return count >= expected;
    }

    /**
     * 🔴 [全覆盖] 获取指定方向中所有未饱和维度（含空白 + 出题不足）。
     * <p>
     * 用于 selectNextDimension 的候选项。
     */
    private List<String> getUnsaturatedDimensions(String chatId, String topic) {
        List<String> allDims = TopicDimensions.getDimensions(topic);
        if (allDims.isEmpty()) return List.of();

        return allDims.stream()
                .filter(dim -> !isDimensionSaturated(chatId, topic, dim))
                .toList();
    }

    /**
     * 🔴 [全覆盖] 旧数据兼容迁移：启动时将旧的"全覆盖"降级为"已出 1 题"。
     * <p>
     * 调用时机：会话首次进入某方向时由 currentTopic 或 selectNextDimension 触发。
     * 迁移规则：已在 topicCoveredDimensions 中的维度 → 在 dimQuestionCount 中设 count=1，
     * 从未 topicCoveredDimensions 移除（降级为"未饱和"），下次可继续出题补全知识点。
     */
    public void migrateLegacyCoverage(String chatId, String topic) {
        SessionState state = sessions.get(chatId);
        if (state == null) return;
        synchronized (state) {
            Set<String> coveredDims = state.topicCoveredDimensions.get(topic);
            if (coveredDims == null || coveredDims.isEmpty()) return;

            Map<String, Integer> dimCounts = state.dimQuestionCount
                    .computeIfAbsent(topic, k -> new HashMap<>());

            for (String dim : coveredDims) {
                // 仅迁移 dimQuestionCount 中还没有记录的维度
                dimCounts.putIfAbsent(dim, 1);
                log.info("🔄 [全覆盖] 旧数据迁移: topic={}, dim={}, 降级为已出1题", topic, dim);
            }
            // 清空旧覆盖记录 → 重新开放所有维度
            state.topicCoveredDimensions.remove(topic);
            log.info("🔄 [全覆盖] topic={} 旧覆盖记录已清除, 释放{}个维度重新出题",
                    topic, coveredDims.size());
        }

        // 同时迁移持久化中的旧数据
        if (userAbilityService != null) {
            try {
                UserAbilityProfile profile = userAbilityService.getOrCreateProfile(chatId);
                Set<String> persistedDims = profile.getCoveredDimensions(topic);
                if (!persistedDims.isEmpty()) {
                    // 清空持久化覆盖记录，降级为"未饱和"，下次自然重新补题
                    profile.clearCoveredDimensions(topic);
                    log.info("🔄 [全覆盖] 持久化覆盖记录迁移: topic={}, 已清除{}个旧维度",
                            topic, persistedDims.size());
                }
            } catch (Exception e) {
                log.warn("持久化旧数据迁移失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 🔴 [全覆盖] 当维度达到饱和度时，记录到 topicCoveredDimensions（双写内存 + 持久化）。
     */
    private void markDimensionSaturated(String chatId, String topic, String dim) {
        if (topic == null || dim == null) return;

        // 写内存
        SessionState state = sessions.get(chatId);
        if (state != null) {
            synchronized (state) {
                state.topicCoveredDimensions
                        .computeIfAbsent(topic, k -> new HashSet<>())
                        .add(dim);
            }
        }

        // 写 profile（持久化）
        if (userAbilityService != null) {
            try {
                userAbilityService.getOrCreateProfile(chatId)
                        .addCoveredDimension(topic, dim);
            } catch (Exception e) {
                log.warn("持久化维度覆盖失败: {}", e.getMessage());
            }
        }

        log.info("✅ [全覆盖] 维度达到饱和度: topic={}, dim={}", topic, dim);
    }

    /**
     * [全覆盖] 增加维度出题计数（dimQuestionCount）。
     * 每次 confirmDimension 调用一次。
     * 当维度达到饱和度时自动调用 markDimensionSaturated。
     */
    private void incrementDimQuestionCount(String chatId, String topic, String dim) {
        if (topic == null || dim == null) return;
        SessionState state = sessions.get(chatId);
        if (state == null) return;
        synchronized (state) {
            Map<String, Integer> dimCounts = state.dimQuestionCount
                    .computeIfAbsent(topic, k -> new HashMap<>());
            int newCount = dimCounts.merge(dim, 1, Integer::sum);
            log.debug("[全覆盖] dimQuestionCount 增加: topic={}, dim={}, count={}", topic, dim, newCount);

            // 检查是否达到饱和度
            if (isDimensionSaturated(chatId, topic, dim)) {
                markDimensionSaturated(chatId, topic, dim);
            }
        }
    }


    // ===== 🔴 [终版-双链路] 预选出题 + DIM确认 + 互证门 =====

    /**
     * 🔴 [终版-双链路] 预选下一个出题维度。
     * <p>
     * 策略：从当前方向的所有维度中，取权重最高的未覆盖维度。
     * 所有维度均已覆盖 → 返回 null（AI 自由出题，走链路B兜底）。
     *
     * @param chatId 会话 ID
     * @param topic  当前方向
     * @return 预选维度名（完整维度名，含括号内容），或 null
     */
    /**
     * [全覆盖] 预选下一个出题维度（基于饱和度打分）。
     * <p>
     * 优先级规则：
     * 1. 空白维度（出题数=0）：基础分 500 + 权重微调
     * 2. 未饱和维度（出题数>0 但未达期望）：基础分 300 + 靠近饱和的补分
     * 3. 所有维度均已饱和 -> 返回 null（AI 自由出题）。
     * <p>
     * 同优先级下，用户薄弱项小幅加分（不超过 50），避免无限优先。
     */
    public String selectNextDimension(String chatId, String topic) {
        List<String> allDims = TopicDimensions.getDimensions(topic);
        if (allDims.isEmpty()) return null;

        String selected = allDims.stream()
                .max(java.util.Comparator.comparingDouble(
                        d -> computeDimensionPriority(chatId, topic, d)))
                .orElse(null);

        // 检查最高分维度是否已饱和
        if (selected != null && isDimensionSaturated(chatId, topic, selected)) {
            log.info("所有维度已饱和 [{}]，AI 自由出题", topic);
            return null;
        }

        // 写入会话状态（pendingDimension）
        if (selected != null) {
            SessionState state = sessions.get(chatId);
            if (state != null) {
                synchronized (state) {
                    state.pendingDimension = selected;
                }
            }
            int count = getDimensionQuestionCount(chatId, topic, selected);
            int expected = TopicDimensions.getExpectedMinQuestions(topic, selected);
            log.debug("[全覆盖] 预选 [{}]: {} (已出{}题/期望{}题)", topic, selected, count, expected);
        }
        return selected;
    }



    /**
     * [全覆盖] 计算维度优先级分数。
     * 得分越高越优先出题。
     * 分数 = 空白基础分(500) 或 未饱和基础分(300) + 填充率加分 + 权重分 + 薄弱微调
     * 薄弱微调上限 50，避免偏执追打。
     */
    private double computeDimensionPriority(String chatId, String topic, String dim) {
        int count = getDimensionQuestionCount(chatId, topic, dim);
        int expected = TopicDimensions.getExpectedMinQuestions(topic, dim);
        int weight = TopicDimensions.getSubDimensionWeightValue(topic, dim);

        double score;
        if (count == 0) {
            // 空白维度：最高优先级
            score = 500 + weight * 10;
        } else if (count < expected) {
            // 未饱和维度：越靠近饱和优先级越低
            double fillRate = (double) count / expected;
            score = 300 * (1 - fillRate) + weight * 10;
        } else {
            // 已饱和维度：分数最低
            score = weight * 5;
        }

        // 薄弱微调（不超过 50 分）
        if (userAbilityService != null) {
            try {
                UserAbilityProfile profile = userAbilityService.getOrCreateProfile(chatId);
                UserAbilityProfile.TopicScore ts = profile.getTopicScores().get(topic);
                if (ts != null) {
                    double avg = ts.getAverageScore();
                    if (avg > 0 && avg < 3.0) {
                        double weakBonus = Math.min(50, (3.0 - avg) * 20);
                        score += weakBonus;
                    }
                }
            } catch (Exception e) {
                // 忽略，不影响主流程
            }
        }

        return score;
    }
    /**
     * 🔴 [终版-双链路] 获取当前会话的预选维度。
     *
     * @param chatId 会话 ID
     * @return 预选维度名，或 null
     */
    public String getPendingDimension(String chatId) {
        SessionState state = sessions.get(chatId);
        if (state == null) return null;
        synchronized (state) {
            return state.pendingDimension;
        }
    }

    /**
     * 🔴 [终版-双链路] 维度确认：解析 AI 回复中的 DIM 标签，
     * 验证合法性，与预选维度交叉比对，生成置信度分类，记录覆盖。
     * <p>
     * 完整替代旧的 {@link #recordCoveredDimension(String, String, String)}。
     * 不实时修正任何标签，仅记录分类结果。
     *
     * @param chatId       会话 ID
     * @param topic        当前方向
     * @param aiResponse   AI 完整回复
     * @param linkBDims    链路B（评分AI）输出的维度数组，可为 null
     * @param bConfidence  链路B自评置信度（"high"/"medium"/"low"），可为 null
     * @return 分类结果
     */
    public DimensionClassification confirmDimension(
            String chatId, String topic, String aiResponse,
            List<String> linkBDims, String bConfidence) {
        return confirmDimension(chatId, topic, aiResponse, linkBDims, bConfidence, true);
    }

    /**
     * 🔴 [终版-双链路] 维度确认（增强版）：可通过 countQuestions 控制是否更新出题计数。
     * <p>
     * 硬选题模式下（由 closeActivePoint/recordDimensionAsked 负责计数），
     * 传入 countQuestions=false 可只做 DIM 标签校验而不重复计数。
     *
     * @param chatId         会话 ID
     * @param topic          当前方向
     * @param aiResponse     AI 完整回复
     * @param linkBDims      链路B（评分AI）输出的维度数组，可为 null
     * @param bConfidence    链路B自评置信度，可为 null
     * @param countQuestions 是否更新 dimQuestionCount（硬选题模式 false，自由出题 true）
     * @return 分类结果
     */
    public DimensionClassification confirmDimension(
            String chatId, String topic, String aiResponse,
            List<String> linkBDims, String bConfidence, boolean countQuestions) {

        // 1. 提取链路A的 DIM 标签
        List<String> rawDimTags = DimensionValidator.extractDimTags(aiResponse);

        // 2. 维度名验证门：只保留合法维度名
        List<String> validDims = DimensionValidator.filterValidDimensions(topic, rawDimTags);

        // 3. 无有效 DIM 标签时，用 pendingDimension 兜底
        if (validDims.isEmpty()) {
            SessionState state = sessions.get(chatId);
            if (state != null && state.pendingDimension != null) {
                String pending = state.pendingDimension;
                if (TopicDimensions.getDimensions(topic).contains(pending)) {
                    validDims = List.of(pending);
                    log.warn("⚠️ [终版] AI 未输出有效 DIM 标签，使用预选维度兜底: topic={}, pending={}",
                            topic, pending);
                }
            }
        }

        // 4. 统计偏离与幻觉
        if (!rawDimTags.isEmpty() && validDims.isEmpty()) {
            log.warn("⚠️ [终版] AI 输出的 DIM 标签均为幻觉维度名: topic={}, tags={}",
                    topic, rawDimTags);
        }
        if (validDims.size() < rawDimTags.size()) {
            log.debug("📐 [终版] 部分 DIM 标签被过滤: raw={}, valid={}", rawDimTags, validDims);
        }

        // 5. 执行双链路互证门
        DimensionClassification classification = DimensionValidator.classify(
                validDims, linkBDims, bConfidence);

        // 6. [全覆盖] 有效分类 → 更新 dimQuestionCount（出题计数）
        // countQuestions=false 时（硬选题模式），只校验 DIM 标签合法性，不计入出题计数
        //（由 closeActivePoint/recordDimensionAsked 负责计数，避免双计）
        if (countQuestions && classification.isValid() && !classification.effectiveDimensions().isEmpty()) {
            for (String dim : classification.effectiveDimensions()) {
                incrementDimQuestionCount(chatId, topic, dim);
            }
            log.debug("📐 [终版] 记录维度覆盖 [{}]: {} (置信度={}, 来源={})",
                    topic, classification.effectiveDimensions(),
                    classification.confidence(), classification.source());
        } else if (!countQuestions && classification.isValid() && !classification.effectiveDimensions().isEmpty()) {
            log.debug("📐 [终版] DIM 校验通过（硬选题-不计数） [{}]: {} (置信度={}, 来源={})",
                    topic, classification.effectiveDimensions(),
                    classification.confidence(), classification.source());
        }

        // 7. 日志：低置信度告警
        if (classification.confidence() == ConfidenceLevel.CONFLICT) {
            log.warn("⚠️ [终版] 双链路标签冲突: linkA={}, linkB={}, 采用linkA",
                    classification.linkADimensions(), classification.linkBDimensions());
        }
        if (classification.confidence() == ConfidenceLevel.INVALID) {
            log.warn("⚠️ [终版] 维度确认失败（INVALID）: topic={}, 无有效标签", topic);
        }

        return classification;
    }

    /**
     * 🔴 [终版-双链路] 内部：直接记录维度覆盖（无推断，无关键词匹配）。
     * <p>
     * 写入内存 + 持久化 profile。
     */
    private void recordCoveredDimensionInternal(String chatId, String topic, String dimension) {
        if (topic == null || dimension == null) return;

        // 写内存
        SessionState state = sessions.get(chatId);
        if (state != null) {
            synchronized (state) {
                state.topicCoveredDimensions
                        .computeIfAbsent(topic, k -> new HashSet<>())
                        .add(dimension);
            }
        }

        // 写 profile
        if (userAbilityService != null) {
            try {
                userAbilityService.getOrCreateProfile(chatId)
                        .addCoveredDimension(topic, dimension);
            } catch (Exception e) {
                log.warn("持久化维度覆盖失败: {}", e.getMessage());
            }
        }
    }

    // ===== 🔴 [Hotfix-DETAIL] 细分考点提取、归一化、热力图 =====

    // ===== 🔴 [增强] ---DETAIL--- JSON 块解析（支持难度等级） =====

    /**
     * 🔴 解析 ---DETAIL--- JSON 块中的 detail 条目。
     * <p>
     * 格式约定：
     * <pre>{@code
     * ---DETAIL---
     * [{"detail":"synchronized锁升级","level":2},{"detail":"偏向锁撤销","level":1}]
     * ---DETAIL---
     * }</pre>
     * level 字段可选（默认 1）。兼容旧版 {@code [DETAIL]xxx,yyy} 格式。
     *
     * @param jsonText JSON 数组字符串（含外层方括号）
     * @return 解析到的 DetailedPoint 列表
     */
    private List<DetailedPoint> parseJsonDetailBlock(String jsonText) {
        List<DetailedPoint> result = new ArrayList<>();
        Matcher detailMatcher = JSON_DETAIL_FIELD.matcher(jsonText);
        Matcher levelMatcher = JSON_LEVEL_FIELD.matcher(jsonText);

        // 分别找到所有 detail 和 level
        List<String> details = new ArrayList<>();
        while (detailMatcher.find()) {
            details.add(detailMatcher.group(1));
        }
        List<Integer> levels = new ArrayList<>();
        while (levelMatcher.find()) {
            levels.add(Integer.parseInt(levelMatcher.group(1)));
        }

        for (int i = 0; i < details.size(); i++) {
            int level = i < levels.size() ? levels.get(i) : 1;
            level = Math.max(1, Math.min(3, level)); // 约束到 1-3
            result.add(new DetailedPoint(details.get(i), level));
        }
        return result;
    }

    /**
     * 🔴 从 AI 回复中提取细分考点列表（含难度等级）。
     * <p>
     * 支持两种格式（按优先级降序）：
     * <ol>
     *   <li>{@code ---DETAIL---} JSON 数组（含 level）</li>
     *   <li>{@code [DETAIL]xxx,yyy} 简单文本（level 默认 1）</li>
     * </ol>
     *
     * @param aiResponse AI 完整回复文本
     * @return 提取到的细分考点列表（含等级），可能为空
     */
    public List<DetailedPoint> extractDetailedPoints(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) return List.of();
        List<DetailedPoint> result = new ArrayList<>();

        // 1. 尝试解析 ---DETAIL--- JSON 块（含 level）
        Matcher jsonMatcher = DETAIL_JSON_BLOCK.matcher(aiResponse);
        while (jsonMatcher.find()) {
            String block = jsonMatcher.group();
            // 去掉 ---DETAIL--- 标记，保留 JSON 部分
            String json = block.replaceAll("(?i)---DETAIL---", "").trim();
            if (json.startsWith("[") && json.endsWith("]")) {
                result.addAll(parseJsonDetailBlock(json));
            }
        }
        if (!result.isEmpty()) return result;

        // 2. 回退旧版 [DETAIL]xxx,yyy 格式（level 默认 1）
        Matcher simpleMatcher = DETAIL_SIMPLE_LINE.matcher(aiResponse);
        while (simpleMatcher.find()) {
            String raw = simpleMatcher.group(1).trim();
            if (!raw.isEmpty()) {
                String[] parts = raw.split("[,，]");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        result.add(new DetailedPoint(trimmed, 1));
                    }
                }
            }
        }
        return result;
    }

    /**
     * 🔴 [Hotfix-DETAIL兜底] 当 AI 完全未输出 ---DETAIL--- 时，从 DIM 标记 + 提问句提取考点。
     * <p>
     * 策略：
     * 1. 取最后一个 [DIM:xxx] 标记为子领域名
     * 2. 取最后一个提问句作为考点描述文本
     * 3. 组合为 level=2 的 DETAIL（原理理解级别，比 level=1 更匹配面试场景）
     *
     * @param aiResponse AI 完整回复文本
     * @param topic     当前方向
     * @return 兜底提取的考点列表（1 条），或空列表
     */
    public List<DetailedPoint> extractDetailFallback(String aiResponse, String topic) {
        if (aiResponse == null || aiResponse.isBlank()) return List.of();

        // 1. 取最后一个 [DIM:xxx] 标记
        String dimName = null;
        Matcher dimMatcher = DIM_MARKER_PATTERN.matcher(aiResponse);
        while (dimMatcher.find()) {
            dimName = dimMatcher.group(1).trim();
        }

        // 2. 取最后一个提问句
        String lastQuestion = null;
        List<String> questions = extractAllQuestions(aiResponse);
        if (!questions.isEmpty()) {
            lastQuestion = questions.get(questions.size() - 1);
            // 截取前 40 字做考点名
            if (lastQuestion.length() > 40) {
                lastQuestion = lastQuestion.substring(0, 40) + "…";
            }
        }

        // 3. 组合考点描述（优先 dimName + 提问句）
        String detailText;
        if (dimName != null && lastQuestion != null) {
            detailText = dimName + " - " + lastQuestion;
        } else if (dimName != null) {
            detailText = dimName;
        } else if (lastQuestion != null) {
            detailText = lastQuestion;
        } else {
            return List.of();
        }

        log.warn("⚠️ AI 未输出 DETAIL，已从 DIM/提问句兜底提取: {}", detailText);
        return List.of(new DetailedPoint(detailText, 2));
    }

    /**
     * 🔴 [终版] 记录 AI 回复中的细分考点，支持传入预选维度。
     *
     * @param chatId      会话 ID
     * @param topic       当前方向
     * @param points      细分考点列表（含难度等级）
     * @param selectedDim 预选出题维度，可为 null（自由出题时）
     */
    public void recordDetailedPoints(String chatId, String topic, List<DetailedPoint> points,
                                      String selectedDim) {
        if (chatId == null || topic == null || points == null || points.isEmpty()) return;
        SessionState state = sessions.get(chatId);
        if (state == null) return;

        synchronized (state) {
            // 🔴 [Hotfix-DETAIL持久化] 首次写入前从 profile 加载已持久化的 DETAIL 数据
            ensureDetailedDataLoaded(chatId, state);

            Set<String> existing = state.topicDetailedRecords
                    .computeIfAbsent(topic, k -> new HashSet<>());
            Map<String, Set<DetailedPoint>> subDimDetails = state.topicSubDimDetailedRecords
                    .computeIfAbsent(topic, k -> new HashMap<>());
            Map<String, Integer> subDimCount = state.topicSubDimCount
                    .computeIfAbsent(topic, k -> new HashMap<>());

            for (DetailedPoint point : points) {
                // 归一化：与已有记录比较，相似则跳过
                String normalized = normalizeDetail(point.detail(), existing);
                if (normalized != null) continue; // 已存在，跳过
                existing.add(point.detail());

                // 🔴 [终版] 映射到二级子领域：优先用预选维度，回退主体名包含检查
                String subDim = mapDetailToSubDimension(topic, selectedDim, point.detail());
                if (subDim == null) {
                    log.debug("⚠️ 细分考点 [{}] 无法映射到子领域，使用 topic 兜底: topic={}", point.detail(), topic);
                }

                // 🔴 [Hotfix-难度约束] 校验跳级：子领域 Lv.1 空白时警告 Lv.2/Lv.3
                if (subDim != null && point.level() > 1) {
                    boolean hasLv1 = subDimDetails.getOrDefault(subDim, Set.of())
                            .stream().anyMatch(dp -> dp.level() == 1);
                    if (!hasLv1) {
                        log.warn("⚠️ [跳级出题] {} 尚未覆盖 Lv.1，直接出了 Lv.{}: {}",
                                subDim, point.level(), point.detail());
                    }
                }

                // 写入子领域明细（subDim=null 时归入 topic 层级兜底）
                String target = subDim != null ? subDim : topic;
                subDimDetails.computeIfAbsent(target, k -> new HashSet<>()).add(point);
                subDimCount.merge(target, 1, Integer::sum);
            }

            // 🔴 [Hotfix-热力图裁剪] 限制每个方向的 DETAIL 存储上限
            capDetailedRecords(state, topic);
        }

        // 🔴 [Hotfix-DETAIL持久化] 同步写入 profile 持久化
        if (userAbilityService != null) {
            try {
                UserAbilityProfile profile = userAbilityService.getOrCreateProfile(chatId);
                for (DetailedPoint point : points) {
                    profile.getTopicDetailedRecords()
                            .computeIfAbsent(topic, k -> new HashSet<>())
                            .add(point.detail());
                }
            } catch (Exception e) {
                log.warn("持久化细分考点失败: {}", e.getMessage());
            }
        }

        log.debug("📝 记录细分考点 [{}]: {} (已累计{}个)", topic,
                points.stream().map(DetailedPoint::detail).toList(),
                state.topicDetailedRecords.getOrDefault(topic, Set.of()).size());
    }

    /** 🔴 向后兼容：无预选维度时调用 */
    public void recordDetailedPoints(String chatId, String topic, List<DetailedPoint> points) {
        recordDetailedPoints(chatId, topic, points, null);
    }

    /**
     * 🔴 [Hotfix-热力图裁剪] 限制每个方向的 DETAIL 最大数量，超过时淘汰最旧条目。
     * 防止长会话内存膨胀和热力图 token 爆炸。超出 2 倍上限时触发裁剪。
     */
    private void capDetailedRecords(SessionState state, String topic) {
        Set<String> existing = state.topicDetailedRecords.get(topic);
        if (existing == null || existing.size() <= HEATMAP_MAX_DETAILS * 2) return;

        // 保留最近 HEATMAP_MAX_DETAILS 条（LinkedHashSet 按插入顺序淘汰前面的）
        Set<String> trimmed = new LinkedHashSet<>();
        int skip = existing.size() - HEATMAP_MAX_DETAILS;
        int i = 0;
        for (String d : existing) {
            if (i++ < skip) continue;
            trimmed.add(d);
        }
        state.topicDetailedRecords.put(topic, trimmed);
        log.debug("✂️ 裁剪方向 [{}]: {} → {} 条", topic, existing.size(), trimmed.size());

        // 重建子领域计数
        state.topicSubDimCount.remove(topic);
        state.topicSubDimDetailedRecords.remove(topic);
        for (String d : trimmed) {
            String subDim = mapDetailToSubDimension(topic, d);
            if (subDim != null) {
                state.topicSubDimCount
                        .computeIfAbsent(topic, k -> new HashMap<>())
                        .merge(subDim, 1, Integer::sum);
            }
        }
    }

    /**
     * 🔴 [Hotfix-DETAIL持久化] 从 profile 加载持久化的 DETAIL 数据到 SessionState。
     * <p>
     * 类似 {@link #filterAskedQuestions} 中加载指纹的模式。
     * 在上次会话崩溃重启后，重建热力图数据。
     */
    private void ensureDetailedDataLoaded(String chatId, SessionState state) {
        if (userAbilityService == null) return;
        // 只在内存无数据时加载
        if (!state.topicDetailedRecords.isEmpty()) return;
        try {
            UserAbilityProfile profile = userAbilityService.getOrCreateProfile(chatId);
            Map<String, Set<String>> persisted = profile.getTopicDetailedRecords();
            if (persisted.isEmpty()) return;

            log.debug("📝 从持久化加载 {} 个方向的细分考点", persisted.size());
            for (Map.Entry<String, Set<String>> entry : persisted.entrySet()) {
                String t = entry.getKey();
                Set<String> details = entry.getValue();
                if (details == null || details.isEmpty()) continue;

                state.topicDetailedRecords.computeIfAbsent(t, k -> new HashSet<>()).addAll(details);

                // 重建子领域计数
                for (String d : details) {
                    String subDim = mapDetailToSubDimension(t, d);
                    if (subDim != null) {
                        state.topicSubDimCount
                                .computeIfAbsent(t, k -> new HashMap<>())
                                .merge(subDim, 1, Integer::sum);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("加载持久化细分考点失败: {}", e.getMessage());
        }
    }

    /**
     * 🔴 归一化考点名称：与已有记录做相似度匹配，找到相似则返回已存标准名。
     *
     * @param detail   新考点
     * @param existing 已有考点集合
     * @return 若与已有考点相似度超过阈值，返回已存的标准名；否则返回 null
     */
    private String normalizeDetail(String detail, Set<String> existing) {
        if (existing.isEmpty()) return null;
        for (String existed : existing) {
            double sim = trigramSimilarity(detail, existed);
            if (sim >= DETAIL_NORMALIZE_THRESHOLD) {
                return existed; // 合并：返回已存的标准名
            }
        }
        return null;
    }

    /**
     * 🔴 [终版-简化] 将细分考点映射到二级子领域（用于热力图计数）。
     * <p>
     * 优先用预选维度（selectedDim 参数），否则用维度主体名包含检查。
     * 不再使用任何关键词匹配。
     *
     * @param topic       方向
     * @param selectedDim 预选维度（可为 null）
     * @param detail      细分考点名
     * @return 二级子领域名，映射失败返回 null
     */
    private String mapDetailToSubDimension(String topic, String selectedDim, String detail) {
        if (topic == null || detail == null) return null;

        // 1. 优先用预选维度
        if (selectedDim != null && TopicDimensions.matchesDimension(selectedDim, detail)) {
            return selectedDim;
        }

        // 2. 遍历维度主体名匹配（纯包含检查，无关键词拆解）
        List<String> dims = TopicDimensions.getDimensions(topic);
        if (dims.isEmpty()) return null;
        String lowerDetail = detail.toLowerCase(java.util.Locale.ROOT);
        for (String dim : dims) {
            String subject = TopicDimensions.dimensionSubject(dim);
            if (!subject.isEmpty() && lowerDetail.contains(subject.toLowerCase(java.util.Locale.ROOT))) {
                return dim;
            }
        }
        return null;
    }

    /** 向后兼容：无预选维度时调用 */
    private String mapDetailToSubDimension(String topic, String detail) {
        return mapDetailToSubDimension(topic, null, detail);
    }

    // ===== 🔴 [Hotfix-热力图] 热力图 Prompt 构建（树形 DETAIL 粒度展开） =====

    /**
     * 🔴 [增强-DETAIL粒度] 构建树形热力图，展示每个子领域的 DETAIL 覆盖状况。
     * <p>
     * 格式：
     * <pre>{@code
     * 📊 【Java并发】考察进度（3个细分考点）：
     * 🔴 空白高权重：AQS、JMM、线程池
     *   ■ 锁机制 — 高 ｜ 2题
     *   │  ✅ synchronized锁升级(Lv.2)
     *   │  ⬜ 推荐：synchronized原理、ReentrantLock、读写锁
     *   ■ AQS — 高 ｜ 0题 ← 优先
     * }</pre>
     * AI 可直观看到每个子领域下已覆盖哪些考点、还有哪些未覆盖的考点可出。
     *
     * @param chatId 会话 ID
     * @param topic  当前方向
     * @return 热力图文本（不含外层换行），若无数据则返回空字符串
     */
        public String buildHeatmapPrompt(String chatId, String topic) {
        SessionState state = sessions.get(chatId);
        if (state == null) return "";

        List<String> dims = TopicDimensions.getDimensions(topic);
        if (dims.isEmpty()) return "";

        synchronized (state) {
            ensureDetailedDataLoaded(chatId, state);
            Set<String> details = state.topicDetailedRecords.getOrDefault(topic, Set.of());
            Map<String, Set<DetailedPoint>> subDimDetails =
                    state.topicSubDimDetailedRecords.getOrDefault(topic, Map.of());

            StringBuilder sb = new StringBuilder();
            int totalDetails = details.size();
            sb.append("\n[").append(topic).append("]考察进度(")
                    .append(totalDetails).append("个细分考点/").append(dims.size()).append("个子领域):\n");

            // === 第一段：按优先级展示空白 + 未饱和维度 ===
            List<String> blankDims = new ArrayList<>();
            List<String> unsaturatedDims = new ArrayList<>();
            List<String> saturatedDims = new ArrayList<>();
            for (String dim : dims) {
                int qCount = getDimensionQuestionCount(chatId, topic, dim);
                if (qCount == 0) {
                    blankDims.add(dim);
                } else if (!isDimensionSaturated(chatId, topic, dim)) {
                    unsaturatedDims.add(dim);
                } else {
                    saturatedDims.add(dim);
                }
            }

            if (!blankDims.isEmpty()) {
                sb.append("空白维度(优先出题):");
                sb.append(String.join(", ", blankDims));
                sb.append("\n");
            }
            if (!unsaturatedDims.isEmpty()) {
                sb.append("待补全维度(已出题但未达饱和):\n");
                for (String dim : unsaturatedDims) {
                    int qCount = getDimensionQuestionCount(chatId, topic, dim);
                    int expected = TopicDimensions.getExpectedMinQuestions(topic, dim);
                    sb.append("  - ").append(dim).append("(").append(qCount).append("/").append(expected).append("题)\n");
                }
            }

            // === 第二段：树形展开每个子领域 ===
            int totalLines = 0;
            int maxLines = 40;
            for (String dim : dims) {
                if (totalLines > maxLines) {
                    sb.append("  ... 剩余").append(dims.size() - totalLines + 1).append("个子领域略\n");
                    break;
                }

                int qCount = getDimensionQuestionCount(chatId, topic, dim);
                int expected = TopicDimensions.getExpectedMinQuestions(topic, dim);
                boolean saturated = qCount >= expected;

                Map<String, Integer> subDimCount = state.topicSubDimCount.getOrDefault(topic, Map.of());
                String weightLabel = TopicDimensions.getSubDimensionWeight(topic, dim);
                String dimShort = dim.length() > 28 ? dim.substring(0, 28) + "..." : dim;

                String statusMark;
                if (qCount == 0) {
                    statusMark = "[优先]";
                } else if (!saturated) {
                    statusMark = "[待补" + (expected - qCount) + "题]";
                } else {
                    statusMark = "[OK]";
                }

                sb.append("  * ").append(dimShort).append(" - ").append(weightLabel)
                        .append(" | ").append(qCount).append("/").append(expected).append("题 ")
                        .append(statusMark).append("\n");
                totalLines++;

                Set<DetailedPoint> covered = subDimDetails.getOrDefault(dim, Set.of());
                boolean hasLv1 = covered.stream().anyMatch(dp -> dp.level() == 1);
                boolean hasHigherLv = covered.stream().anyMatch(dp -> dp.level() >= 2);

                if (!hasLv1 && hasHigherLv && qCount > 0) {
                    sb.append("  |  [警告]已跳过Lv.1基础考点出了高阶题\n");
                    totalLines++;
                }

                if (!covered.isEmpty()) {
                    List<DetailedPoint> sorted = new ArrayList<>(covered);
                    sorted.sort(Comparator.comparingInt(DetailedPoint::level));
                    int showCnt = 0;
                    for (DetailedPoint dp : sorted) {
                        if (showCnt++ >= 4) {
                            sb.append("  |  ...等").append(sorted.size() - 4).append("条\n");
                            break;
                        }
                        sb.append("  |  [已考]").append(dp.detail()).append("(Lv.").append(dp.level()).append(")\n");
                        totalLines++;
                    }
                }

                if (qCount > 0 && !hasLv1 && !covered.isEmpty()) {
                    sb.append("  |  [建议]先出Lv.1基础题\n");
                    totalLines++;
                }

                List<String> suggested = TopicDimensions.getSubDimensionKeywords(dim);
                if (!suggested.isEmpty()) {
                    List<String> coveredNames = covered.stream().map(DetailedPoint::detail).toList();
                    List<String> available = new ArrayList<>();
                    for (String s : suggested) {
                        boolean isCovered = coveredNames.stream()
                                .anyMatch(c -> trigramSimilarity(s, c) > 0.6);
                        if (!isCovered) available.add(s);
                    }
                    if (!available.isEmpty()) {
                        sb.append("  |  推荐:");
                        int showCnt = 0;
                        for (String a : available) {
                            if (showCnt++ >= 4) { sb.append("..."); break; }
                            sb.append(a).append(", ");
                        }
                        int len = sb.length();
                        if (sb.substring(len-2).equals(", ")) sb.setLength(len-2);
                        sb.append("\n");
                        totalLines++;
                    }
                }
            }

            // === 第三段：覆盖概览 ===
            sb.append("覆盖概览: 空白").append(blankDims.size()).append("个/待补全")
                    .append(unsaturatedDims.size()).append("个/已饱和").append(saturatedDims.size()).append("个\n");

            sb.append("驻留: 当前第").append(state.stayRounds).append("轮(最少").append(MIN_STAY_ROUNDS).append("轮)");
            if (state.consecutiveWeakAnswers >= WEAK_ANSWER_UNLOCK_THRESHOLD) {
                sb.append(", 连续答差, 可切换方向");
            } else if (state.consecutiveStrongAnswers >= STRONG_ANSWER_EXTEND_THRESHOLD) {
                sb.append(", 连续答好, 已延长驻留");
            }
            if (totalDetails > HEATMAP_MAX_DETAILS) {
                sb.append("(展示").append(HEATMAP_MAX_DETAILS).append("/").append(totalDetails).append("条考点)");
            }
            sb.append("\n");

            return sb.toString();
        }
    }

    /** 定时清理过期会话（每 30 分钟） */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void evictExpiredSessions() {
        long now = System.currentTimeMillis();
        long timeout = 2 * 60 * 60 * 1000; // 2 小时
        sessions.entrySet().removeIf(entry -> {
            SessionState state = entry.getValue();
            synchronized (state) {
                return (now - state.lastAccess) > timeout;
            }
        });
        log.info("🧹 过期会话清理完成，剩余活跃会话数: {}", sessions.size());
    }
}
