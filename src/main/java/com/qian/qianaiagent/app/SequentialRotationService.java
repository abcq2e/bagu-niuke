package com.qian.qianaiagent.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 严格顺序轮询服务 —— 替代旧的 TopicRotationService。
 *
 * <p>核心规则：
 * <ol>
 *   <li>16个方向按学习路线固定顺序轮询</li>
 *   <li>每个方向的题目严格按序号出题：bagu在前，面渣逆袭接后</li>
 *   <li>每轮每个方向考 N 题（基础方向3题，冷门方向2题）</li>
 *   <li>第R轮取题号为 [R*N, (R+1)*N)，全部严格按顺序</li>
 *   <li>方向题目耗尽后自动退出轮询</li>
 * </ol>
 *
 * <p>完全确定性 —— 无随机、无加权、无动态停留。
 */
@Component
@Slf4j
public class SequentialRotationService {

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

    // ===== 静态工具方法（兼容旧调用） =====

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

    // ===== 游标状态 =====

    /**
     * 单个方向的进度。
     */
    public static class DirectionProgress {
        public String name;
        public int questionsPerRound;
        public int totalQuestions;
        public int nextStartIndex;   // 下一轮从第几题开始（0-based）
        public boolean exhausted;

        // Jackson 需要无参构造
        public DirectionProgress() {}

        public DirectionProgress(String name, int questionsPerRound, int totalQuestions) {
            this.name = name;
            this.questionsPerRound = questionsPerRound;
            this.totalQuestions = totalQuestions;
            this.nextStartIndex = 0;
            this.exhausted = false;
        }

        /** 本轮应取 [nextStartIndex, endIndex) 的题目 */
        public int endIndex() {
            return Math.min(nextStartIndex + questionsPerRound, totalQuestions);
        }

        /** 本轮实际取几题 */
        public int countThisRound() {
            return endIndex() - nextStartIndex;
        }

        /** 完成一轮后推进 */
        public void advanceRound() {
            nextStartIndex = endIndex();
            if (nextStartIndex >= totalQuestions) {
                exhausted = true;
            }
        }
    }

    /**
     * 会话级游标。
     */
    public static class SequentialCursor {
        public String chatId;
        public int round;
        public int activeDirIndex;          // 当前在活跃方向列表中的位置
        public int askedThisDirection;      // 当前方向本轮已问几题
        public List<DirectionProgress> activeDirections;  // 尚未耗尽的活跃方向
        public long lastAccess;
        /** 🔴 循环次数：所有方向耗尽后自动重置，cycle 递增（0-based，首次为第1轮大循环） */
        public int cycle;
        /** 🔴 最近一次出示给用户的题干（持久化，防重启后点评串题） */
        public String lastShownStem;
        /** 🔴 最近一次出示题目所属方向 */
        public String lastShownTopic;

        // Jackson 需要无参构造
        public SequentialCursor() {}

        public SequentialCursor(String chatId, List<DirectionProgress> activeDirections) {
            this.chatId = chatId;
            this.round = 0;
            this.activeDirIndex = 0;
            this.askedThisDirection = 0;
            this.activeDirections = activeDirections;
            this.lastAccess = System.currentTimeMillis();
            this.cycle = 0;
        }

        /** 当前活跃方向 */
        public DirectionProgress currentDirection() {
            if (activeDirections.isEmpty()) return null;
            if (activeDirIndex >= activeDirections.size()) {
                // 一轮结束，回到开头
                return activeDirections.get(0);
            }
            return activeDirections.get(activeDirIndex);
        }

        /** 当前方向名 */
        public String currentTopic() {
            DirectionProgress dp = currentDirection();
            return dp != null ? dp.name : null;
        }

        /** 移动到下一个活跃方向 */
        public void advanceDirection() {
            activeDirIndex++;
            askedThisDirection = 0;

            // 跳过已耗尽的
            while (activeDirIndex < activeDirections.size()
                    && activeDirections.get(activeDirIndex).exhausted) {
                activeDirIndex++;
            }

            // 一轮结束：清理耗尽的方向，开始新一轮
            if (activeDirIndex >= activeDirections.size()) {
                activeDirections.removeIf(d -> d.exhausted);
                if (activeDirections.isEmpty()) {
                    // 🔴 所有方向全部考完，面试自然结束（不再自动重置）
                    log.info("🏁 第{}轮大循环完成，所有方向题目已全部考完！共{}轮", cycle + 1, round + 1);
                    return;
                }
                round++;
                activeDirIndex = 0;
                askedThisDirection = 0;
                log.info("🔄 第{}轮完成，开始第{}轮，剩余{}个方向（已淘汰耗尽方向）",
                        round, round + 1, activeDirections.size());
            }
        }
    }

    // ===== 会话状态管理 =====

    private final Map<String, SequentialCursor> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path cursorDir = Path.of(".quiz-cursor");

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(cursorDir);
        } catch (IOException e) {
            log.warn("无法创建游标目录: {}", e.getMessage());
        }
    }

    /**
     * 获取或创建会话游标。
     *
     * @param cursorKey      游标存储 key
     * @param fallbackChatId 迁移源 key（用户登录场景：从 chat_X → user_Y）
     */
    private SequentialCursor getOrCreateCursor(String cursorKey, String fallbackChatId,
                                               TopicDocumentCache docCache) {
        // === Step 1: 内存缓存 ===
        SequentialCursor cursor = sessions.get(cursorKey);
        if (cursor != null) {
            cursor.lastAccess = System.currentTimeMillis();
            return cursor;
        }

        // === Step 2: 磁盘加载（校验失败不删文件，留给后续恢复步骤） ===
        cursor = loadCursor(cursorKey);
        if (cursor != null) {
            cursor.lastAccess = System.currentTimeMillis();
            cursor = validateAndRepairCursor(cursor, cursorKey);
            if (cursor != null) {
                // 🔴 [Bug修复] 刷新 totalQuestions（文档可能已更新，如去重后题目数减少）
                refreshTotalQuestions(cursor, docCache);
                sessions.put(cursorKey, cursor);
                log.info("📂 磁盘加载游标: key={}, round={}, dirs={}, idx={}",
                        cursorKey, cursor.round, cursor.activeDirections.size(), cursor.activeDirIndex);
                return cursor;
            }
            log.warn("⚠️ 游标校验失败，保留文件继续恢复: key={}", cursorKey);
        }

        // === Step 3: 迁移恢复（仅精确匹配 fallbackChatId，禁止全局偷进度） ===
        cursor = tryMigrateCursor(cursorKey, fallbackChatId, docCache);
        if (cursor != null) {
            sessions.put(cursorKey, cursor);
            return cursor;
        }

        // === Step 4: 创建全新游标（不再紧急扫盘 / 硬编码污染） ===
        List<DirectionProgress> progresses = new ArrayList<>();
        for (DirectionDef def : DIRECTIONS) {
            int total = docCache != null ? docCache.getOrderedQuestionCount(def.name()) : 0;
            if (total > 0) {
                progresses.add(new DirectionProgress(def.name(), def.questionsPerRound(), total));
            } else {
                log.warn("⚠️ 方向 [{}] 无题目，跳过", def.name());
            }
        }
        cursor = new SequentialCursor(cursorKey, progresses);
        sessions.put(cursorKey, cursor);
        saveCursor(cursor);
        log.info("🆕 新建游标: key={}, dirs={}, 第0轮开始", cursorKey, progresses.size());
        return cursor;
    }

    /**
     * 游标迁移：仅精确匹配 sourceKey（当前请求 chatId → user_X），禁止全局扫盘偷进度。
     */
    private SequentialCursor tryMigrateCursor(String targetKey, String sourceKey,
                                              TopicDocumentCache docCache) {
        if (sourceKey == null || sourceKey.isBlank()) {
            log.debug("🔍 游标迁移: 无 sourceKey，跳过");
            return null;
        }

        SequentialCursor source = loadCursor(sourceKey);
        if (source == null) {
            log.debug("🔍 游标迁移: 源游标不存在: {}", sourceKey);
            return null;
        }
        log.info("🔍 游标迁移-精确匹配: {}", sourceKey);

        // 刷新每个方向的 totalQuestions（文档可能已更新）
        if (source.activeDirections != null) {
            for (DirectionProgress dp : source.activeDirections) {
                int freshTotal = docCache != null ? docCache.getOrderedQuestionCount(dp.name) : dp.totalQuestions;
                if (freshTotal > 0 && freshTotal != dp.totalQuestions) {
                    log.info("🔄 迁移刷新 [{}]: totalQuestions {} → {}", dp.name, dp.totalQuestions, freshTotal);
                    dp.totalQuestions = freshTotal;
                    if (dp.nextStartIndex >= freshTotal) {
                        dp.exhausted = true;
                        log.info("  └─ [{}] nextStartIndex({}) >= 新总数({}), 标记耗尽",
                                dp.name, dp.nextStartIndex, freshTotal);
                    }
                }
            }
        }

        source.chatId = targetKey;
        source.lastAccess = System.currentTimeMillis();
        saveCursor(source);

        try {
            Files.deleteIfExists(cursorDir.resolve(sourceKey + ".json"));
        } catch (IOException e) {
            log.warn("删除旧游标文件失败: {}", e.getMessage());
        }

        log.info("🔄 游标迁移成功: {} → {}, round={}, 保留{}个方向进度, 当前方向={}",
                sourceKey, targetKey, source.round,
                source.activeDirections != null ? source.activeDirections.size() : 0,
                source.currentTopic());
        return source;
    }

    /**
     * 🔴 校验并修复从磁盘加载的游标状态。
     *
     * <p>修复场景：
     * <ol>
     *   <li>activeDirections 为空 → 标记为全部考完（返回原游标，后续 currentTopic 返回 null）</li>
     *   <li>activeDirIndex 越界 → 尝试回绕到 round 开头</li>
     *   <li>当前指向的 direction 已耗尽 → 跳过所有已耗尽方向</li>
     *   <li>跳过耗尽方向后越界 → 触发 round wrap + 清理耗尽方向</li>
     * </ol>
     */
    private SequentialCursor validateAndRepairCursor(SequentialCursor cursor, String chatId) {
        if (cursor.activeDirections == null || cursor.activeDirections.isEmpty()) {
            log.warn("⚠️ 游标 activeDirections 为空: chatId={}，将重建游标", chatId);
            // 🔴 旧版游标可能因全部耗尽而变空，自动重建以支持无限循环
            return null; // 返回 null 让 getOrCreateCursor 新建
        }

        // 1. 修复越界的 activeDirIndex
        if (cursor.activeDirIndex >= cursor.activeDirections.size()) {
            log.warn("⚠️ 游标 activeDirIndex 越界 ({} >= {}), 回绕到开头: chatId={}",
                    cursor.activeDirIndex, cursor.activeDirections.size(), chatId);
            // 清理耗尽方向后回绕
            cursor.activeDirections.removeIf(d -> d.exhausted);
            if (cursor.activeDirections.isEmpty()) {
                log.info("🏁 游标修复: 所有方向已耗尽: chatId={}，返回null触发重建（支持无限循环）", chatId);
                return null;  // 🔴 [Bug修复] 统一返回null触发重建，与路径2保持一致
            }
            cursor.round++;
            cursor.activeDirIndex = 0;
            cursor.askedThisDirection = 0;
        }

        // 2. 跳过当前及后续已耗尽的方向
        int skipped = 0;
        while (cursor.activeDirIndex < cursor.activeDirections.size()
                && cursor.activeDirections.get(cursor.activeDirIndex).exhausted) {
            cursor.activeDirIndex++;
            skipped++;
        }

        // 3. 跳过耗尽方向后越界 → wrap
        if (cursor.activeDirIndex >= cursor.activeDirections.size()) {
            cursor.activeDirections.removeIf(d -> d.exhausted);
            if (cursor.activeDirections.isEmpty()) {
                log.info("🏁 游标修复: 跳过耗尽方向后无剩余，返回null触发重建: chatId={}", chatId);
                return null;  // 🔴 返回null让getOrCreateCursor新建游标（支持无限循环）
            }
            cursor.round++;
            cursor.activeDirIndex = 0;
            cursor.askedThisDirection = 0;
            log.info("🔄 游标修复: wrap 到第{}轮, chatId={}", cursor.round, chatId);
        }

        if (skipped > 0) {
            cursor.askedThisDirection = 0; // 重置当前方向已问题数
            log.warn("⚠️ 游标修复: 跳过 {} 个已耗尽方向 → 当前 index={} ({}) : chatId={}",
                    skipped, cursor.activeDirIndex,
                    cursor.activeDirections.get(cursor.activeDirIndex).name, chatId);
            saveCursor(cursor); // 修复后持久化
        }

        return cursor;
    }

    /**
     * 🔴 [Bug修复] 刷新游标中每个方向的 totalQuestions。
     * 当文档更新（如去重后题目数减少）时，旧游标的 totalQuestions 可能过大，
     * 导致 QuizApp 中 allQuestions.get(range[0]) 抛出 IndexOutOfBoundsException。
     */
    private void refreshTotalQuestions(SequentialCursor cursor, TopicDocumentCache docCache) {
        if (cursor == null || cursor.activeDirections == null || docCache == null) return;
        for (DirectionProgress dp : cursor.activeDirections) {
            int freshTotal = docCache.getOrderedQuestionCount(dp.name);
            if (freshTotal > 0 && freshTotal != dp.totalQuestions) {
                log.info("🔄 刷新游标 [{}]: totalQuestions {} → {}",
                        dp.name, dp.totalQuestions, freshTotal);
                dp.totalQuestions = freshTotal;
                // 如果当前进度已超过新总数，标记耗尽
                if (dp.nextStartIndex >= freshTotal) {
                    dp.exhausted = true;
                    log.info("  └─ [{}] nextStartIndex({}) >= 新总数({}), 标记耗尽",
                            dp.name, dp.nextStartIndex, freshTotal);
                }
            }
        }
    }

    // ===== 核心方法 =====

    /**
     * 初始化会话（在首次对话时调用，确保 TopicDocumentCache 可用）。
     *
     * @param cursorKey      游标存储 key（user_X 或 chatId）
     * @param fallbackChatId 当 cursorKey 找不到游标时，尝试从此 key 迁移（用户登录场景）
     * @param docCache       题目缓存
     */
    public void initSession(String cursorKey, String fallbackChatId, TopicDocumentCache docCache) {
        getOrCreateCursor(cursorKey, fallbackChatId, docCache);
    }

    /** 向后兼容：无迁移源的初始化 */
    public void initSession(String chatId, TopicDocumentCache docCache) {
        initSession(chatId, null, docCache);
    }

    /**
     * 深拷贝当前游标（用于 AI 流失败时回滚）。
     */
    public SequentialCursor snapshotCursor(String chatId) {
        SequentialCursor c = sessions.get(chatId);
        if (c == null) return null;
        try {
            return mapper.readValue(mapper.writeValueAsBytes(c), SequentialCursor.class);
        } catch (IOException e) {
            throw new IllegalStateException("snapshot failed: " + chatId, e);
        }
    }

    /**
     * 用快照覆盖内存游标并持久化（AI 流失败回滚）。
     */
    public void restoreCursor(SequentialCursor snapshot) {
        if (snapshot == null || snapshot.chatId == null) return;
        sessions.put(snapshot.chatId, snapshot);
        saveCursor(snapshot);
        log.info("⏪ 游标已回滚: key={}, round={}, asked={}, topic={}",
                snapshot.chatId, snapshot.round, snapshot.askedThisDirection, snapshot.currentTopic());
    }

    /**
     * 获取当前考察方向名。
     */
    public String currentTopic(String chatId) {
        SequentialCursor cursor = sessions.get(chatId);
        if (cursor == null) {
            log.warn("游标未初始化: chatId={}", chatId);
            return DIRECTIONS.get(0).name(); // 兜底
        }
        cursor.lastAccess = System.currentTimeMillis();
        String topic = cursor.currentTopic();
        if (topic == null) {
            log.info("🏁 所有方向已考完: chatId={}", chatId);
        }
        return topic;
    }

    /**
     * 获取当前方向本轮应考的题目序号范围。
     * @return int[2] {startInclusive, endExclusive}，无题返回 null
     */
    public int[] getCurrentQuestionRange(String chatId) {
        SequentialCursor cursor = sessions.get(chatId);
        if (cursor == null) return null;
        DirectionProgress dp = cursor.currentDirection();
        if (dp == null) return null;
        int start = dp.nextStartIndex + cursor.askedThisDirection;
        int end = dp.endIndex();
        if (start >= end) return null;
        return new int[]{start, end};
    }

    /**
     * 获取当前方向本轮已问了几题（即下一题在本轮内的索引）。
     */
    public int getAskedThisDirection(String chatId) {
        SequentialCursor cursor = sessions.get(chatId);
        if (cursor == null) return 0;
        return cursor.askedThisDirection;
    }

    /**
     * 标记当前方向本轮已问一题。返回 true 表示当前方向本轮题目已问完，需要换方向。
     */
    public boolean markQuestionAsked(String chatId) {
        SequentialCursor cursor = sessions.get(chatId);
        if (cursor == null) return true;
        cursor.lastAccess = System.currentTimeMillis();
        cursor.askedThisDirection++;

        DirectionProgress dp = cursor.currentDirection();
        if (dp == null) return true;

        boolean done = cursor.askedThisDirection >= dp.countThisRound();
        if (done) {
            dp.advanceRound();
            cursor.advanceDirection();
            if (dp.exhausted) {
                log.info("✅ 方向 [{}] 题目已用完，退出轮询。剩余活跃方向: {}",
                        dp.name, cursor.activeDirections.size());
            }
        }
        // 🔴 每次都持久化，防止重启丢进度
        saveCursor(cursor);
        return done;
    }

    /**
     * 用户主动跳过当前方向，直接进入下一方向。
     */
    public void skipCurrentDirection(String chatId) {
        SequentialCursor cursor = sessions.get(chatId);
        if (cursor == null) return;
        cursor.lastAccess = System.currentTimeMillis();

        DirectionProgress dp = cursor.currentDirection();
        if (dp != null) {
            dp.advanceRound(); // 跳过本轮剩余题目
            log.info("⏭️ 用户跳过方向 [{}]，已推进到题号{}", dp.name, dp.nextStartIndex);
        }
        cursor.advanceDirection();
        saveCursor(cursor);
    }

    /**
     * 获取所有活跃方向名列表（用于前端展示）。
     */
    public List<String> getActiveDirectionNames(String chatId) {
        SequentialCursor cursor = sessions.get(chatId);
        if (cursor == null) return List.of();
        return cursor.activeDirections.stream()
                .map(d -> d.name).toList();
    }

    /**
     * 获取当前轮次号（从0开始）。
     */
    public int getCurrentRound(String chatId) {
        SequentialCursor cursor = sessions.get(chatId);
        return cursor != null ? cursor.round : 0;
    }

    /**
     * 🔴 获取当前大循环次数（从0开始，0=首次遍历所有方向）。
     * 所有方向耗尽后自动重置，cycle+1。
     */
    public int getCurrentCycle(String chatId) {
        SequentialCursor cursor = sessions.get(chatId);
        return cursor != null ? cursor.cycle : 0;
    }

    /**
     * 🔴 判断当前是否刚完成一轮大循环重置（用于前端提示）。
     * 当 cycle > 0 且 round == 0 且 activeDirIndex == 0 时为 true。
     */
    public boolean isFreshCycle(String chatId) {
        SequentialCursor cursor = sessions.get(chatId);
        return cursor != null && cursor.cycle > 0 && cursor.round == 0 && cursor.activeDirIndex == 0;
    }

    /**
     * 获取指定方向的总题目数。
     */
    public int getTotalQuestions(String chatId, String topic) {
        SequentialCursor cursor = sessions.get(chatId);
        if (cursor == null) return 0;
        return cursor.activeDirections.stream()
                .filter(d -> d.name.equals(topic))
                .findFirst()
                .map(d -> d.totalQuestions)
                .orElse(0);
    }

    /**
     * 🔴 获取当前方向本轮的总题数（countThisRound）。
     * 用于前端/提示中显示"第X/Y题"。
     */
    public int getQuestionsThisRound(String chatId) {
        SequentialCursor cursor = sessions.get(chatId);
        if (cursor == null) return 0;
        DirectionProgress dp = cursor.currentDirection();
        return dp != null ? dp.countThisRound() : 0;
    }

    /**
     * 🔴 获取当前题目在本轮中的序号（1-based）。
     * 即下一题是当前方向本轮的第几题。
     */
    public int getCurrentQuestionNumber(String chatId) {
        SequentialCursor cursor = sessions.get(chatId);
        if (cursor == null) return 1;
        return cursor.askedThisDirection + 1; // 1-based
    }

    /**
     * 🔴 获取当前方向已考总题数（跨轮次累计）。
     */
    public int getTotalAskedThisDirection(String chatId) {
        SequentialCursor cursor = sessions.get(chatId);
        if (cursor == null) return 0;
        DirectionProgress dp = cursor.currentDirection();
        return dp != null ? dp.nextStartIndex + cursor.askedThisDirection : 0;
    }

    // ===== 状态持久化 =====

    private void saveCursor(SequentialCursor cursor) {
        try {
            Path file = cursorDir.resolve(cursor.chatId + ".json");
            mapper.writeValue(file.toFile(), cursor);
        } catch (IOException e) {
            log.warn("保存游标失败: chatId={}, err={}", cursor.chatId, e.getMessage());
        }
    }

    private SequentialCursor loadCursor(String chatId) {
        try {
            Path file = cursorDir.resolve(chatId + ".json");
            if (!Files.exists(file)) return null;
            return mapper.readValue(file.toFile(), SequentialCursor.class);
        } catch (IOException e) {
            log.warn("加载游标失败: chatId={}, err={}", chatId, e.getMessage());
            return null;
        }
    }

    /**
     * 持久化最近出示的题干（与游标同文件，重启不丢，避免点评串题）。
     */
    public void saveLastShown(String chatId, String topic, String stem) {
        SequentialCursor cursor = sessions.get(chatId);
        if (cursor == null) return;
        cursor.lastShownTopic = topic;
        cursor.lastShownStem = stem;
        cursor.lastAccess = System.currentTimeMillis();
        saveCursor(cursor);
    }

    public String getLastShownStem(String chatId) {
        SequentialCursor cursor = sessions.get(chatId);
        return cursor != null ? cursor.lastShownStem : null;
    }

    public String getLastShownTopic(String chatId) {
        SequentialCursor cursor = sessions.get(chatId);
        return cursor != null ? cursor.lastShownTopic : null;
    }

    /** 单测用：从内存移除会话，模拟重启后仅从磁盘恢复。 */
    void evictSessionForTest(String chatId) {
        sessions.remove(chatId);
    }

    // ===== 定时清理 =====

    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void evictExpiredSessions() {
        long now = System.currentTimeMillis();
        long timeout = 2 * 60 * 60 * 1000;
        sessions.entrySet().removeIf(entry -> {
            SequentialCursor cursor = entry.getValue();
            boolean expired = (now - cursor.lastAccess) > timeout;
            if (expired) {
                saveCursor(cursor); // 清理前保存
            }
            return expired;
        });
        if (!sessions.isEmpty()) {
            log.debug("🧹 过期会话清理完成，剩余活跃会话: {}", sessions.size());
        }
    }
}
