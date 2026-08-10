package com.qian.qianaiagent.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * 用户能力画像服务 —— 管理所有会话的能力画像、异步评分、智能选题建议。
 * <p>
 * 评分是异步执行，不在 SSE 流的关键路径上。
 * <p>
 * 持久化策略（三级缓存）：
 * 1. 本地内存（最快，当前请求）
 * 2. 文件系统（可靠，跨请求/跨重启）← 主要持久化手段
 * 3. Redis（可选，分布式环境共享）
 */
@Service
@Slf4j
public class UserAbilityService {

    /** 画像文件存储目录 */
    private static final String PROFILE_DIR = ".ability-profiles";

    /** 画像文件名模板 */
    private static final String PROFILE_FILE_PREFIX = "profile_";
    private static final String PROFILE_FILE_SUFFIX = ".json";

    /** 🔴 Redis 存储前缀（可选） */
    private static final String REDIS_KEY_PREFIX = "ability:profile:";
    private static final long REDIS_TTL_DAYS = 30;

    /** 🔴 用户 ID → chatId 映射（按 userId 查找/保存画像） */
    private static final String USER_PROFILE_PREFIX = "profile_user_";

    /** 所有会话的能力画像（chatId → profile）— 本地一级缓存 */
    private final Map<String, UserAbilityProfile> localCache = new ConcurrentHashMap<>();

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource(name = "scoringExecutor")
    private Executor scoringExecutor;

    @Resource
    private ObjectMapper objectMapper;

    /** 🔴 [Hotfix-驻留轮次] 回答质量回调：评分完成后将质量信号回传（chatId, isGood） */
    private BiConsumer<String, Boolean> answerQualityCallback;

    /** 🔴 [全覆盖] 薄弱点提示注入计数器（chatId → 累计注入次数），控制注入频率 */
    private final Map<String, Integer> weakHintInjectionCount = new ConcurrentHashMap<>();

    /** 🔴 [503修复] AI分类并发控制信号量（限制同时调用 DeepSeek 的请求数） */
    private final java.util.concurrent.Semaphore aiCallSemaphore = new java.util.concurrent.Semaphore(3);

    /** 🔴 [503修复] AI分类结果本地缓存（weakPoint → 方向名），避免对同一点反复调用 */
    private final Map<String, String> classifyCache = new ConcurrentHashMap<>();

    // ===== 🔴 [熔断器] 防止 DeepSeek 503 时持续重试轰炸 =====
    /** 熔断器：连续失败次数 */
    private volatile int circuitBreakerFailCount = 0;
    /** 熔断器：最后一次失败的时间戳 */
    private volatile long circuitBreakerLastFailTime = 0;
    /** 熔断器：连续失败阈值（超过此次数则熔断） */
    private static final int CIRCUIT_BREAKER_THRESHOLD = 3;
    /** 熔断器：冷却时间（毫秒），熔断后等待此时间再尝试恢复 */
    private static final long CIRCUIT_BREAKER_COOLDOWN_MS = 30_000;

    /** 🔴 [503修复] 跨方向清理节流：记录每个 chatId 最后一次清理时间，避免频繁提交 */
    private final Map<String, Long> lastCleanupTime = new ConcurrentHashMap<>();
    private static final long CLEANUP_THROTTLE_MS = 60_000; // 同一会话60秒内只清理一次

    private final ChatClient chatClient;

    /** 画像文件目录（支持外部配置） */
    @Value("${ability.profile.dir:}")
    private String customProfileDir;

    private Path profileDir;

    /**
     * 🔴 [Hotfix-驻留轮次] 设置回答质量回调。
     * 由 QuizApp 在初始化时注入，将评分结果同步到 TopicRotationService 的驻留控制。
     */
    public void setAnswerQualityCallback(BiConsumer<String, Boolean> callback) {
        this.answerQualityCallback = callback;
    }

    /** 🔴 [终版-V2] 评分 Prompt — 不告知考卷方向，避免 AI 方向偏见 */
    private static final String SCORE_PROMPT = """
            题目：{question}
            回答：{answer}
            评分(1-5)：5=全面深入含实战,4=懂原理,3=基本正确缺细节,2=有偏差,1=错误无关
            返回JSON：{{"score":1-5, "weakPoints":["具体知识点名1","具体知识点名2"], "weakPointDetails":{{"知识点名":"详细分析"}}, "comment":"..."}}
            ⚠️ 重要格式要求：
            weakPoints 是面试者没答好的具体技术概念名称。
            必须是具体的技术知识点，如 "synchronized锁升级"、"ConcurrentHashMap扩容"。
            每个知识点名称 ≤15 个字，只写名称，禁止写评价语！
            正确示例：["索引最左前缀原则","线程池核心参数","HashMap扩容机制"]
            错误示例（禁止）：["回答内容重复/未提供有效答案", "概念混淆", "重复/雷同"]
            ⚠️ 禁止过度拆分：如果题目考察的是一个大的主题（如"单例模式"），
            多个弱点评都围绕同一主题的不同方面时，应合并为一个概括性知识点名（如"单例模式"），
            不要拆分成"单例模式实现方式"、"单例模式作用"、"单例模式应用场景"等子项。
            ⚠️ weakPointDetails 的 key 用知识点名，value 写该知识点的具体分析（可以详细）。
            ⚠️ weakPointDetails 必须使用结构化排版：按要点分行，每个要点占一行，使用换行符\\n分隔。
            ⚠️ 如果涉及代码示例，必须用 Markdown 代码块包裹（```代码```）。""";

    public UserAbilityService(ChatModel openAiChatModel) {
        this.chatClient = ChatClient.builder(openAiChatModel).build();
    }

    @PostConstruct
    public void init() {
        if (customProfileDir != null && !customProfileDir.isBlank()) {
            profileDir = Paths.get(customProfileDir);
        } else {
            profileDir = Paths.get(System.getProperty("user.dir"), PROFILE_DIR);
        }
        try {
            Files.createDirectories(profileDir);
        } catch (Exception e) {
            log.error("无法创建能力画像目录: {}", profileDir, e);
        }
        log.info("✅ 能力画像初始化: dir={}", profileDir.toAbsolutePath());
    }
    @PreDestroy
    public void shutdown() {
        log.info("🛑 服务关闭，保存所有能力画像...");
        for (Map.Entry<String, UserAbilityProfile> entry : localCache.entrySet()) {
            saveToFile(entry.getKey(), entry.getValue());
            saveToRedis(entry.getKey(), entry.getValue());
        }
        log.info("✅ 能力画像保存完成，共 {} 个", localCache.size());
    }

    /**
     * 🔴 定时自动保存所有已加载的画像（每 10 秒一次），确保即使异步评分异常也不会丢数据
     */
    @Scheduled(fixedRate = 10000)
    public void autoSaveAllProfiles() {
        for (Map.Entry<String, UserAbilityProfile> entry : localCache.entrySet()) {
            saveToFile(entry.getKey(), entry.getValue());
            saveToRedis(entry.getKey(), entry.getValue());
        }
        if (!localCache.isEmpty()) {
            log.debug("💾 定时保存 {} 个画像完成", localCache.size());
        }
    }

    /**
     * 获取或创建会话的能力画像
     * <p>
     * 三级缓存：本地内存 → 文件 → Redis → 新建
     */
    public UserAbilityProfile getOrCreateProfile(String chatId) {
        return getOrCreateProfile(chatId, null);
    }

    /**
     * 将用户级已考知识点 ID 合并进当前 chat 画像（多会话累计覆盖）。
     */
    public void mergeAskedPointIdsFromUser(String chatId, Long userId) {
        if (userId == null || chatId == null) return;
        try {
            UserAbilityProfile chatProfile = getOrCreateProfile(chatId, userId);
            UserAbilityProfile userProfile = loadFromUserFile(userId);
            if (userProfile == null) {
                userProfile = loadFromUserRedis(userId);
            }
            if (userProfile == null || userProfile.getAskedPointIds() == null
                    || userProfile.getAskedPointIds().isEmpty()) {
                return;
            }
            if (chatProfile.getAskedPointIds() == null) {
                chatProfile.setAskedPointIds(new java.util.HashSet<>());
            }
            int before = chatProfile.getAskedPointIds().size();
            chatProfile.getAskedPointIds().addAll(userProfile.getAskedPointIds());
            int after = chatProfile.getAskedPointIds().size();
            if (after > before) {
                log.info("📌 合并用户已考知识点: chatId={}, userId={}, {}→{}",
                        chatId, userId, before, after);
                saveToFile(chatId, chatProfile);
            }
        } catch (Exception e) {
            log.warn("合并 askedPointIds 失败: {}", e.getMessage());
        }
    }

    /**
     * 获取或创建会话的能力画像（支持按 userId 回退）
     * <p>
     * 如果按 chatId 找不到已有数据，会尝试按 userId 恢复，
     * 确保登录用户切换对话/刷新页面后画像不丢失。
     */
    public UserAbilityProfile getOrCreateProfile(String chatId, Long userId) {
        // 🔴 每次加载都重新执行跨方向清理（消除历史脏数据）
        UserAbilityProfile cached = localCache.get(chatId);
        if (cached != null) {
            // 🔴 [503修复] 缓存命中时异步执行清理，避免阻塞请求线程等待 AI 调用
            // 当 DeepSeek 503 时，同步清理会导致接口超时/卡死
            // 节流：同一会话60秒内只提交一次清理任务
            Long lastCleanup = lastCleanupTime.get(chatId);
            long now = System.currentTimeMillis();
            if (lastCleanup == null || (now - lastCleanup) > CLEANUP_THROTTLE_MS) {
                lastCleanupTime.put(chatId, now);
                final String finalChatId = chatId;
                scoringExecutor.execute(() -> {
                    try {
                        int moved = cleanCrossTopicWeakPoints(cached);
                        if (moved > 0) saveToFile(finalChatId, cached);
                    } catch (Exception e) {
                        log.warn("异步跨方向清理失败: {}", e.getMessage());
                    }
                });
            }
            return cached;
        }
        // 缓存未命中：从文件/Redis加载
        UserAbilityProfile loaded = loadFromFile(chatId);
        if (loaded != null) {
            log.info("📂 从文件恢复能力画像: chatId={}", chatId);
            rebuildFreq(loaded);
            cleanCrossTopicWeakPoints(loaded);
            localCache.put(chatId, loaded);
            return loaded;
        }
        if (userId != null) {
            UserAbilityProfile fromUserFile = loadFromUserFile(userId);
            if (fromUserFile != null) {
                log.info("📂 从用户文件恢复能力画像: userId={}", userId);
                rebuildFreq(fromUserFile);
                cleanCrossTopicWeakPoints(fromUserFile);
                localCache.put(chatId, fromUserFile);
                saveToFile(chatId, fromUserFile);
                return fromUserFile;
            }
            UserAbilityProfile fromUserRedis = loadFromUserRedis(userId);
            if (fromUserRedis != null) {
                log.info("📂 从用户 Redis 恢复能力画像: userId={}", userId);
                rebuildFreq(fromUserRedis);
                cleanCrossTopicWeakPoints(fromUserRedis);
                localCache.put(chatId, fromUserRedis);
                saveToFile(chatId, fromUserRedis);
                saveToUserFile(userId, fromUserRedis);
                return fromUserRedis;
            }
        }
        UserAbilityProfile fromRedis = loadFromRedis(chatId);
        if (fromRedis != null) {
            log.info("📂 从 Redis 恢复能力画像: chatId={}", chatId);
            rebuildFreq(fromRedis);
            cleanCrossTopicWeakPoints(fromRedis);
            localCache.put(chatId, fromRedis);
            saveToFile(chatId, fromRedis);
            return fromRedis;
        }
        log.info("📂 未找到画像文件，创建新画像: chatId={}", chatId);
        UserAbilityProfile newProfile = new UserAbilityProfile(chatId);
        localCache.put(chatId, newProfile);
        return newProfile;
    }

    /**
     * 保存画像（文件 + Redis 双写），同时按 userId 冗余保存
     */
    public void saveProfile(String chatId) {
        saveProfile(chatId, null);
    }

    /**
     * 保存画像（文件 + Redis 双写），同时按 userId 冗余保存
     */
    public void saveProfile(String chatId, Long userId) {
        UserAbilityProfile profile = localCache.get(chatId);
        if (profile == null) return;
        saveToFile(chatId, profile);
        saveToRedis(chatId, profile);
        // 按 userId 冗余保存，确保 chatId 变了也能恢复
        if (userId != null) {
            saveToUserFile(userId, profile);
            saveToUserRedis(userId, profile);
        }
    }

    private void rebuildFreq(UserAbilityProfile profile) {
        if (profile.getTopicScores() != null) {
            for (UserAbilityProfile.TopicScore ts : profile.getTopicScores().values()) {
                ts.rebuildFreqAfterDeserialization();
                // 🔴 旧数据迁移：weakPointQuestions → wrongQuestions
                if (ts.getWrongQuestions() == null || ts.getWrongQuestions().isEmpty()) {
                    Map<String, String> oldWq = ts.getWeakPointQuestions();
                    if (oldWq != null && !oldWq.isEmpty()) {
                        for (Map.Entry<String, String> e : oldWq.entrySet()) {
                            ts.recordWrongQuestion(e.getKey(), e.getValue());
                        }
                    }
                }
                // 🔴 去重：同一道题只保留一个知识点条目
                dedupWrongQuestions(ts);
            }
        }
    }

    /** 移除 wrongQuestions 中同一道题重复的知识点条目 */
    private void dedupWrongQuestions(UserAbilityProfile.TopicScore ts) {
        Map<String, String> wq = ts.getWrongQuestions();
        if (wq == null || wq.size() <= 1) return;
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.List<String> toRemove = new java.util.ArrayList<>();
        for (Map.Entry<String, String> e : wq.entrySet()) {
            if (!seen.add(e.getValue())) {
                toRemove.add(e.getKey());
            }
        }
        for (String key : toRemove) {
            wq.remove(key);
            log.info("🧹 去重错题: {}", key);
        }
    }

    // ===== 文件持久化 =====

    private Path profilePath(String chatId) {
        return profileDir.resolve(PROFILE_FILE_PREFIX + chatId + PROFILE_FILE_SUFFIX);
    }

    private void saveToFile(String chatId, UserAbilityProfile profile) {
        try {
            Path path = profilePath(chatId);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(profile);
            Files.writeString(path, json, StandardCharsets.UTF_8);
            log.info("💾 画像已保存到文件: {} (话题数: {})", path.toAbsolutePath(), profile.getTopicScores().size());
        } catch (Exception e) {
            log.warn("保存画像到文件失败: {}", e.getMessage());
        }
    }

    private UserAbilityProfile loadFromFile(String chatId) {
        try {
            Path path = profilePath(chatId);
            if (Files.exists(path)) {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                UserAbilityProfile profile = objectMapper.readValue(json, UserAbilityProfile.class);
                log.info("📂 从文件加载画像成功: {} (话题数: {})", path.toAbsolutePath(),
                        profile.getTopicScores() != null ? profile.getTopicScores().size() : 0);
                return profile;
            } else {
                log.info("📂 画像文件不存在: {}", path.toAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("从文件加载画像失败: {}", e.getMessage(), e);
        }
        return null;
    }

    private void deleteProfileFile(String chatId) {
        try {
            Path path = profilePath(chatId);
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("删除画像文件失败: {}", e.getMessage());
        }
    }

    // ===== Redis 持久化（可选）=====

    private UserAbilityProfile loadFromRedis(String chatId) {
        try {
            String key = REDIS_KEY_PREFIX + chatId;
            Object obj = redisTemplate.opsForValue().get(key);
            if (obj instanceof UserAbilityProfile profile) {
                return profile;
            }
        } catch (Exception e) {
            log.debug("Redis 不可用（不影响主流程）: {}", e.getMessage());
        }
        return null;
    }

    private void saveToRedis(String chatId, UserAbilityProfile profile) {
        try {
            String key = REDIS_KEY_PREFIX + chatId;
            redisTemplate.opsForValue().set(key, profile, REDIS_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.debug("Redis 不可用，跳过: {}", e.getMessage());
        }
    }

    private void deleteFromRedis(String chatId) {
        try {
            String key = REDIS_KEY_PREFIX + chatId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("Redis 不可用，跳过: {}", e.getMessage());
        }
    }

    // ===== 按 userId 冗余持久化（登录用户跨会话恢复）=====

    private Path userProfilePath(Long userId) {
        return profileDir.resolve(USER_PROFILE_PREFIX + userId + PROFILE_FILE_SUFFIX);
    }

    private void saveToUserFile(Long userId, UserAbilityProfile profile) {
        try {
            Path path = userProfilePath(userId);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(profile);
            Files.writeString(path, json, StandardCharsets.UTF_8);
            log.debug("💾 用户画像已保存: {}", path.toAbsolutePath());
        } catch (Exception e) {
            log.warn("保存用户画像到文件失败: {}", e.getMessage());
        }
    }

    private UserAbilityProfile loadFromUserFile(Long userId) {
        try {
            Path path = userProfilePath(userId);
            if (Files.exists(path)) {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                return objectMapper.readValue(json, UserAbilityProfile.class);
            }
        } catch (Exception e) {
            log.warn("从用户文件加载画像失败: {}", e.getMessage(), e);
        }
        return null;
    }

    private void saveToUserRedis(Long userId, UserAbilityProfile profile) {
        try {
            String key = REDIS_KEY_PREFIX + "user:" + userId;
            redisTemplate.opsForValue().set(key, profile, REDIS_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.debug("Redis 不可用，跳过: {}", e.getMessage());
        }
    }

    private UserAbilityProfile loadFromUserRedis(Long userId) {
        try {
            String key = REDIS_KEY_PREFIX + "user:" + userId;
            Object obj = redisTemplate.opsForValue().get(key);
            if (obj instanceof UserAbilityProfile profile) {
                return profile;
            }
        } catch (Exception e) {
            log.debug("Redis 不可用（不影响主流程）: {}", e.getMessage());
        }
        return null;
    }

    private void deleteUserProfileFile(Long userId) {
        try {
            Path path = userProfilePath(userId);
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("删除用户画像文件失败: {}", e.getMessage());
        }
    }

    private void deleteFromUserRedis(Long userId) {
        try {
            String key = REDIS_KEY_PREFIX + "user:" + userId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("Redis 不可用，跳过: {}", e.getMessage());
        }
    }

    /**
     * 🔴 [Bug修复] 清理跨方向弱点评：扫描加载后的画像，将明显属于另一方向的弱点评
     * 移动到正确方向下。此方法用于修复历史脏数据，在每次画像从磁盘/Redis加载时自动执行。
     * <p>
     * 检测依据：{@link TopicDimensions#containsForeignTopicKeyword} +
     * {@link TopicDimensions#CROSS_TOPIC_CONCEPTS} 短概念映射表。
     * <p>
     * 保守策略：对不确定的概念不做移动，仅处理明确归属其他方向的脏数据。
     *
     * @param profile 待清理的能力画像（直接修改，无需返回值）
     */
    /**
     * 🔴 跨方向弱点评清理：使用与 routeWeakPoints 完全一致的路由逻辑，
     * 确保清理结果与评分时路由结果一致。
     * <p>
     * 每次画像加载时自动执行，不再依赖一次性守卫。
     *
     * @param profile 待清理的能力画像（直接修改）
     * @return 移动的弱点评数量
     */
    public int cleanCrossTopicWeakPoints(UserAbilityProfile profile) {
        if (profile == null || profile.getTopicScores().isEmpty()) return 0;

        int totalMoved = 0;
        int totalRemoved = 0;

        // 收集所有需要移动的弱点评
        java.util.Map<String, java.util.List<WeakPointMove>> moves = new java.util.HashMap<>();
        java.util.List<String> toRemove = new java.util.ArrayList<>();

        for (java.util.Map.Entry<String, UserAbilityProfile.TopicScore> entry : profile.getTopicScores().entrySet()) {
            String currentTopic = entry.getKey();
            UserAbilityProfile.TopicScore ts = entry.getValue();

            if (ts.getWeakPoints() == null || ts.getWeakPoints().isEmpty()) continue;

            for (String wp : new java.util.ArrayList<>(ts.getWeakPoints())) {
                if (wp == null || wp.isBlank()) continue;

                // 🔴 使用与 routeWeakPoints 相同的路由逻辑，但不加偏置（清理要找到真正归属）
                java.util.Map<String, java.util.List<String>> routed =
                        routeWeakPoints(java.util.List.of(wp), currentTopic, null, false);

                // 找到弱点评被路由到的目标方向
                String targetTopic = null;
                for (java.util.Map.Entry<String, java.util.List<String>> re : routed.entrySet()) {
                    if (re.getValue().contains(wp)) {
                        targetTopic = re.getKey();
                        break;
                    }
                }

                if (targetTopic == null) {
                    // 路由结果为空 → 无法确定归属 → 删除
                    toRemove.add(currentTopic + ":" + wp);
                    log.info("🗑️ 清理无法归类的弱点评: [{}]{} → 删除", currentTopic, wp);
                } else if (!targetTopic.equals(currentTopic)) {
                    // 路由到不同方向 → 移动
                    moves.computeIfAbsent(targetTopic, k -> new java.util.ArrayList<>())
                         .add(new WeakPointMove(currentTopic, wp));
                    log.info("🔄 清理跨方向弱点评: [{}]{} → 移动到 [{}]", currentTopic, wp, targetTopic);
                }
                // targetTopic == currentTopic → 正确归属，不动
            }
        }

        // 执行移动
        for (java.util.Map.Entry<String, java.util.List<WeakPointMove>> moveEntry : moves.entrySet()) {
            String targetTopic = moveEntry.getKey();
            UserAbilityProfile.TopicScore targetTs = profile.getTopicScores()
                    .computeIfAbsent(targetTopic, UserAbilityProfile.TopicScore::new);

            for (WeakPointMove move : moveEntry.getValue()) {
                UserAbilityProfile.TopicScore sourceTs = profile.getTopicScores().get(move.sourceTopic);
                if (sourceTs == null) continue;

                sourceTs.getWeakPoints().remove(move.weakPoint);
                if (!targetTs.getWeakPoints().contains(move.weakPoint)) {
                    targetTs.getWeakPoints().add(move.weakPoint);
                }

                // 迁移关联数据
                if (sourceTs.getWrongQuestions() != null && sourceTs.getWrongQuestions().containsKey(move.weakPoint)) {
                    String question = sourceTs.getWrongQuestions().remove(move.weakPoint);
                    targetTs.recordWrongQuestion(move.weakPoint, question);
                }
                if (sourceTs.getWeakPointDetails() != null && sourceTs.getWeakPointDetails().containsKey(move.weakPoint)) {
                    String detail = sourceTs.getWeakPointDetails().remove(move.weakPoint);
                    if (targetTs.getWeakPointDetails() == null) {
                        targetTs.setWeakPointDetails(new java.util.LinkedHashMap<>());
                    }
                    targetTs.getWeakPointDetails().put(move.weakPoint, detail);
                }
                if (sourceTs.getWeakPointAnswers() != null && sourceTs.getWeakPointAnswers().containsKey(move.weakPoint)) {
                    java.util.List<String> answers = sourceTs.getWeakPointAnswers().remove(move.weakPoint);
                    if (targetTs.getWeakPointAnswers() == null) {
                        targetTs.setWeakPointAnswers(new java.util.LinkedHashMap<>());
                    }
                    targetTs.getWeakPointAnswers().put(move.weakPoint, answers);
                }

                totalMoved++;
            }
        }

        // 🔴 [Bug修复] 重建受影响方向的 weakPointFreq，防止 update() 从旧 freq 重建 weakPoints 时复活已移走的弱点评
        for (java.util.Map.Entry<String, java.util.List<WeakPointMove>> moveEntry : moves.entrySet()) {
            UserAbilityProfile.TopicScore targetTs = profile.getTopicScores().get(moveEntry.getKey());
            if (targetTs != null) targetTs.rebuildFreqAfterDeserialization();
            for (WeakPointMove move : moveEntry.getValue()) {
                UserAbilityProfile.TopicScore sourceTs = profile.getTopicScores().get(move.sourceTopic);
                if (sourceTs != null) sourceTs.rebuildFreqAfterDeserialization();
            }
        }

        // 删除无法归类的
        java.util.Set<String> deletedTopics = new java.util.HashSet<>();
        for (String key : toRemove) {
            int colonIdx = key.indexOf(':');
            String srcTopic = key.substring(0, colonIdx);
            String wpName = key.substring(colonIdx + 1);
            UserAbilityProfile.TopicScore srcTs = profile.getTopicScores().get(srcTopic);
            if (srcTs != null) {
                srcTs.getWeakPoints().remove(wpName);
                if (srcTs.getWrongQuestions() != null) srcTs.getWrongQuestions().remove(wpName);
                if (srcTs.getWeakPointDetails() != null) srcTs.getWeakPointDetails().remove(wpName);
                if (srcTs.getWeakPointAnswers() != null) srcTs.getWeakPointAnswers().remove(wpName);
                deletedTopics.add(srcTopic);
                totalRemoved++;
            }
        }
        // 🔴 删除后重建 freq，防止 update() 复活已删除的弱点评
        for (String t : deletedTopics) {
            UserAbilityProfile.TopicScore ts = profile.getTopicScores().get(t);
            if (ts != null) ts.rebuildFreqAfterDeserialization();
        }

        if (totalMoved > 0 || totalRemoved > 0) {
            log.info("✅ 画像清理完成: 移动{}个, 删除{}个", totalMoved, totalRemoved);
        }
        return totalMoved;
    }

    /** 弱点评移动记录 */
    private record WeakPointMove(String sourceTopic, String weakPoint) {}

    // ===== 🔴 AI 知识点分类器 =====

    /** AI 分类 Prompt——极简，只返回方向名 */
    private static final String CLASSIFY_PROMPT = """
            你是技术知识点分类器。判断以下知识点属于16个方向中的哪一个。
            可选方向：Java基础与集合、Java并发、JVM、Spring框架、MySQL、Redis、
            消息队列、计算机网络、操作系统与Linux、分布式与微服务、算法与数据结构、
            设计模式、系统设计与场景、Docker与运维、ES与搜索、Agent与AI应用
            只返回方向名，不要解释，不要标点。""";

    /**
     * 🔴 [熔断器] 检查 AI 调用是否已被熔断。
     * 熔断条件：连续失败达到阈值，且冷却时间未过。
     * 返回 true 表示熔断中，应跳过 AI 调用。
     */
    private boolean isCircuitBreakerOpen() {
        if (circuitBreakerFailCount < CIRCUIT_BREAKER_THRESHOLD) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - circuitBreakerLastFailTime;
        if (elapsed >= CIRCUIT_BREAKER_COOLDOWN_MS) {
            // 冷却时间已过 → 半开状态，允许尝试一次
            log.info("🔧 熔断器半开：冷却时间已过，尝试恢复 AI 调用");
            circuitBreakerFailCount = 0; // 重置，允许下一次尝试
            return false;
        }
        return true;
    }

    /** 🔴 [熔断器] 记录 AI 调用成功，重置熔断计数 */
    private void recordAiCallSuccess() {
        circuitBreakerFailCount = 0;
        circuitBreakerLastFailTime = 0;
    }

    /** 🔴 [熔断器] 记录 AI 调用失败（503等），递增熔断计数 */
    private void recordAiCallFailure() {
        circuitBreakerFailCount++;
        circuitBreakerLastFailTime = System.currentTimeMillis();
        if (circuitBreakerFailCount >= CIRCUIT_BREAKER_THRESHOLD) {
            log.warn("⚠️ AI 调用熔断器触发！连续失败 {} 次，冷却 {} 秒",
                    circuitBreakerFailCount, CIRCUIT_BREAKER_COOLDOWN_MS / 1000);
        }
    }

    /**
     * 用 AI 判断弱点评属于哪个方向。
     * 仅当关键词匹配区分度低时调用（top2 差距≤2），作为最终裁判。
     * <p>
     * 🔴 [熔断器] 连续 3 次 503 后自动熔断 30 秒，期间直接降级到关键词路由。
     */
    private String aiClassifyTopic(String weakPoint) {
        // 🔴 [503修复] 先查缓存，避免对同一弱点评重复调用 AI
        if (classifyCache.containsKey(weakPoint)) {
            String cached = classifyCache.get(weakPoint);
            log.debug("🤖 AI分类(缓存命中): [{}] → {}", weakPoint, cached);
            return cached;
        }

        // 🔴 [熔断器] 熔断中直接降级，不尝试 AI 调用
        if (isCircuitBreakerOpen()) {
            log.debug("🔴 熔断器中，跳过 AI 分类: [{}]", weakPoint);
            return null;
        }

        boolean acquired = false;
        try {
            // 🔴 [503修复] 获取信号量，限制并发 AI 调用数（最多3个并发）
            acquired = aiCallSemaphore.tryAcquire(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("AI分类信号量超时，降级关键词: [{}]", weakPoint);
                return null;
            }

            String prompt = CLASSIFY_PROMPT + "\n知识点：" + weakPoint;
            String result = chatClient.prompt().user(prompt).call().content();
            if (result != null && !result.isBlank()) {
                String cleaned = result.trim().replaceAll("[，,。.!！\\s]", "");
                // 检查返回的方向名是否在合法列表中
                for (String t : SequentialRotationService.TOPIC_NAMES) {
                    if (cleaned.equals(t) || cleaned.contains(t) || t.contains(cleaned)) {
                        log.info("🤖 AI分类: [{}] → {}", weakPoint, t);
                        recordAiCallSuccess(); // 🔴 [熔断器] 记录成功
                        // 🔴 [503修复] 缓存分类结果（最多 500 条，防止内存膨胀）
                        if (classifyCache.size() < 500) {
                            classifyCache.put(weakPoint, t);
                        }
                        return t;
                    }
                }
                log.info("🤖 AI分类: [{}] → {} (无法匹配合法方向，丢弃)", weakPoint, cleaned);
                recordAiCallSuccess(); // 有返回结果也算成功（只是不匹配）
            }
        } catch (Exception e) {
            log.warn("AI分类失败（降级关键词）: {}", e.getMessage());
            // 🔴 [熔断器] 记录失败，触发熔断逻辑
            if (e.getMessage() != null && e.getMessage().contains("503")) {
                recordAiCallFailure();
            }
        } finally {
            if (acquired) {
                aiCallSemaphore.release();
            }
        }
        return null;
    }

    // ===== 🔴 [Bug修复-V2] 后端确定性弱点评路由 =====

    /**
     * 🔴 后端独立弱点评路由：CROSS_TOPIC_CONCEPTS(硬映射) → 关键词打分 → AI兜底(区分度低时)。
     * 综合三种策略，确保每个弱点评归属到正确方向。
     * <p>
     * 返回 Map<方向名, 弱点评列表>，包含当前方向和其他方向。
     * 无法确定方向的弱点评直接丢弃（不进入任何方向的画像）。
     *
     * @param rawWeakPoints  评分 AI 输出的原始弱点评列表（不含管道符）
     * @param currentTopic   后端当前考察方向
     * @param weakPointDetails 薄弱点详细分析（用于同步迁移）
     * @return 路由后的 map（方向 → 弱点评列表），可能包含 currentTopic 之外的方向
     */
    /**
     * 🔴 [调试] 公开路由方法，供调试端点调用。
     */
    /**
     * 🔴 方向锚定关键词：弱点评文本包含这些词时，直接锁定当前方向，不参与跨方向打分。
     * 解决"Redis单节点QPS"被路由到"系统设计与场景"的串题问题。
     */
    private static final java.util.Map<String, java.util.Set<String>> TOPIC_ANCHORS = buildTopicAnchors();

    private static java.util.Map<String, java.util.Set<String>> buildTopicAnchors() {
        java.util.Map<String, java.util.Set<String>> map = new java.util.LinkedHashMap<>();
        map.put("Java基础与集合", java.util.Set.of("java基础", "集合框架", "集合类", "hashmap", "arraylist"));
        map.put("JVM", java.util.Set.of("jvm", "垃圾回收", "gc", "类加载", "内存模型", "堆", "栈"));
        map.put("Java并发", java.util.Set.of("并发", "线程", "死锁", "synchronized", "volatile", "aqs", "线程池"));
        map.put("操作系统与Linux", java.util.Set.of("操作系统", "linux", "进程", "内存管理", "文件系统", "内核"));
        map.put("计算机网络", java.util.Set.of("网络", "tcp", "http", "dns", "ip地址", "三次握手", "四次挥手"));
        map.put("MySQL", java.util.Set.of("mysql", "sql", "索引", "事务", "b+树", "innodb"));
        map.put("Redis", java.util.Set.of("redis", "缓存", "跳表", "zset", "rdb", "aof", "哨兵", "集群"));
        map.put("消息队列", java.util.Set.of("消息队列", "mq", "rabbitmq", "rocketmq", "kafka", "死信"));
        map.put("Spring框架", java.util.Set.of("spring", "ioc", "aop", "mvc", "mybatis", "boot"));
        map.put("设计模式", java.util.Set.of("设计模式", "单例", "工厂", "代理", "观察者", "策略"));
        map.put("分布式与微服务", java.util.Set.of("分布式", "微服务", "rpc", "注册中心", "配置中心", "网关"));
        map.put("系统设计与场景", java.util.Set.of("系统设计", "秒杀", "短链", "限流", "降级", "熔断"));
        map.put("算法与数据结构", java.util.Set.of("算法", "数据结构", "排序", "二叉树", "动态规划", "哈希"));
        map.put("Docker与运维", java.util.Set.of("docker", "k8s", "部署", "ci/cd", "容器", "镜像"));
        map.put("ES与搜索", java.util.Set.of("es", "elasticsearch", "搜索", "倒排", "分词", "lucene"));
        map.put("Agent与AI应用", java.util.Set.of("agent", "ai", "大模型", "rag", "prompt", "llm"));
        return java.util.Map.copyOf(map);
    }

    /**
     * 检查弱点评是否可以直接锚定到当前方向。
     * 如果弱点评文本包含当前方向的锚定关键词，直接返回当前方向名，跳过跨方向打分。
     */
    private String anchorToCurrentTopic(String weakPoint, String currentTopic) {
        if (currentTopic == null || weakPoint == null) return null;
        java.util.Set<String> anchors = TOPIC_ANCHORS.get(currentTopic);
        if (anchors == null) return null;
        String lower = weakPoint.toLowerCase(java.util.Locale.ROOT);
        for (String anchor : anchors) {
            if (lower.contains(anchor)) {
                return currentTopic;
            }
        }
        return null;
    }

    public Map<String, List<String>> routeWeakPointsPublic(
            List<String> rawWeakPoints, String currentTopic) {
        return routeWeakPoints(rawWeakPoints, currentTopic, null, false);
    }

    /**
     * 评分时路由（带当前方向偏置）。
     */
    Map<String, List<String>> routeWeakPoints(
            List<String> rawWeakPoints, String currentTopic,
            Map<String, String> weakPointDetails) {
        return routeWeakPoints(rawWeakPoints, currentTopic, weakPointDetails, true);
    }

    /**
     * 弱点评路由核心逻辑。
     *
     * @param applyBias true=评分场景，当前方向有自然分时 +2（防止通用词被路由走）；
     *                  false=清理场景，不加偏置（目的是把放错位置的弱点评移回正确方向）
     */
    Map<String, List<String>> routeWeakPoints(
            List<String> rawWeakPoints, String currentTopic,
            Map<String, String> weakPointDetails, boolean applyBias) {
        Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        if (rawWeakPoints == null || rawWeakPoints.isEmpty()) return result;

        for (String wp : rawWeakPoints) {
            if (wp == null || wp.isBlank()) continue;
            String trimmed = wp.trim();

            // 步骤 1: 查 CROSS_TOPIC_CONCEPTS 映射表（最高优先级，硬编码映射最准确）
            String mappedTopic = matchCrossTopicConcept(trimmed);
            if (mappedTopic != null) {
                result.computeIfAbsent(mappedTopic, k -> new java.util.ArrayList<>()).add(trimmed);
                continue;
            }

            // 🔴 步骤 1.5: 锚定检查 — 弱点评文本是否直接包含当前方向关键词
            // 例如 "Redis单节点QPS" 包含 "redis" → 直接锚定到 Redis 方向，不走跨方向打分
            String anchored = anchorToCurrentTopic(trimmed, currentTopic);
            if (anchored != null) {
                result.computeIfAbsent(anchored, k -> new java.util.ArrayList<>()).add(trimmed);
                continue;
            }

            // 步骤 2: 正向匹配打分 — 给每个方向计算匹配度，选最高分的方向
            java.util.Map<String, Integer> scores = scoreAgainstAllTopics(trimmed);
            // 🔴 评分场景才加当前方向偏置；清理场景不加（避免锁定在错误方向）
            if (applyBias) {
                Integer currentNatural = scores.get(currentTopic);
                if (currentNatural != null) {
                    scores.put(currentTopic, currentNatural + 2);
                }
            }
            String bestTopic = null;
            int bestScore = 0;
            int secondScore = 0;
            for (java.util.Map.Entry<String, Integer> entry : scores.entrySet()) {
                if (entry.getValue() > bestScore) {
                    secondScore = bestScore;
                    bestScore = entry.getValue();
                    bestTopic = entry.getKey();
                } else if (entry.getValue() > secondScore) {
                    secondScore = entry.getValue();
                }
            }

            // 🔴 关键词区分度低时（top2 差距≤2），用 AI 做最终裁判
            if (bestTopic != null && (bestScore - secondScore) <= 2 && bestScore > 0) {
                String aiTopic = aiClassifyTopic(trimmed);
                if (aiTopic != null) {
                    bestTopic = aiTopic;
                    bestScore = 99; // AI 判定为最高置信度
                }
            }

            if (bestTopic != null && bestScore >= 1) {
                result.computeIfAbsent(bestTopic, k -> new java.util.ArrayList<>()).add(trimmed);
            } else {
                log.warn("🗑️ 丢弃无法确定方向的弱点评: [{}]", trimmed);
            }
        }
        return result;
    }

    /**
     * 正向匹配打分：给每个方向计算匹配度分数。
     * <p>
     * 🔴 [权重优化] 评分规则（主体名权重低、关键词权重高，提升区分度）：
     * - 匹配维度主体名（如"锁机制"）: +1 分（主体名跨方向共享率高，降权避免干扰）
     * - 匹配维度关键词（如"synchronized"、"行锁"）: +3 分（关键词区分度高，升权）
     * - 匹配 CROSS_TOPIC_CONCEPTS 硬映射（如"AQS"）: +5 分（最强信号，人工标注）
     */
    private java.util.Map<String, Integer> scoreAgainstAllTopics(String text) {
        java.util.Map<String, Integer> scores = new java.util.HashMap<>();
        if (text == null || text.isBlank()) return scores;
        String lowerText = text.toLowerCase(java.util.Locale.ROOT).trim();

        for (String topic : TopicRotationService.TOPICS) {
            int score = 0;
            List<String> dims = TopicDimensions.DIMENSIONS.get(topic);
            if (dims == null) continue;

            for (String dim : dims) {
                // 维度主体名匹配（权重低：主体名跨方向共享率高，如"锁机制"同时出现在Java并发/MySQL/Redis）
                String subject = TopicDimensions.dimensionSubject(dim);
                if (!subject.isEmpty()) {
                    String lowerSubject = subject.toLowerCase(java.util.Locale.ROOT);
                    if (lowerText.contains(lowerSubject) || lowerSubject.contains(lowerText)) {
                        score += 1;
                    }
                }
                // 维度关键词匹配（权重高：关键词区分度强，如"synchronized"→Java并发，"行锁"→MySQL）
                for (String kw : TopicDimensions.getSubDimensionKeywords(dim)) {
                    if (kw.length() >= 2) {
                        String lowerKw = kw.toLowerCase(java.util.Locale.ROOT);
                        if (lowerText.contains(lowerKw) || lowerKw.contains(lowerText)) {
                            score += 3;
                        }
                    }
                }
            }

            // CROSS_TOPIC_CONCEPTS 硬映射（权重最高：人工标注，100%确定）
            for (java.util.Map.Entry<String, String> entry : TopicDimensions.CROSS_TOPIC_CONCEPTS.entrySet()) {
                if (entry.getValue().equals(topic)) {
                    String concept = entry.getKey().toLowerCase(java.util.Locale.ROOT);
                    if (lowerText.contains(concept) || concept.contains(lowerText)) {
                        score += 5;
                    }
                }
            }

            if (score > 0) {
                scores.put(topic, score);
            }
        }
        return scores;
    }

    /**
     * 在 CROSS_TOPIC_CONCEPTS 中查找匹配：优先精确匹配，降级子串匹配。
     * 例如 "值传递" 精确匹配 → "Java基础与集合"；
     * 例如 "Java中的值传递概念" → 子串包含 "值传递" → "Java基础与集合"。
     */
    private String matchCrossTopicConcept(String text) {
        if (text == null || text.isBlank()) return null;
        String lowerText = text.toLowerCase(java.util.Locale.ROOT).trim();

        // 1. 精确匹配
        String exact = TopicDimensions.CROSS_TOPIC_CONCEPTS.get(lowerText);
        if (exact != null) return exact;

        // 2. 子串匹配：取最长匹配（最具体的概念名），保证确定性
        // 🔴 [Bug修复] 之前用 HashMap 遍历取第一个匹配 → 非确定性结果（如"事务隔离"vs"事务隔离级别"）
        if (lowerText.length() > 4) {
            String bestMatch = null;
            String bestTopic = null;
            for (Map.Entry<String, String> entry : TopicDimensions.CROSS_TOPIC_CONCEPTS.entrySet()) {
                String concept = entry.getKey();
                if (concept.length() >= 2 && lowerText.contains(concept)) {
                    if (bestMatch == null || concept.length() > bestMatch.length()) {
                        bestMatch = concept;
                        bestTopic = entry.getValue();
                    }
                }
            }
            if (bestTopic != null) return bestTopic;
        }

        return null;
    }

    // ===== 评分逻辑 =====

    /**
     * 🔴 [Bug修复-V2] 异步评分 — 不传递方向给评分 AI，后端独立路由弱点评。
     * <p>
     * 设计原则：
     * 1. 评分 AI 只需要看到问题和回答，不需要知道当前考察方向
     * 2. 弱点评的方向归属由后端通过 CROSS_TOPIC_CONCEPTS + 维度关键词确定
     * 3. 方向不明确的弱点评直接丢弃，不污染能力画像
     *
     * @param chatId   会话 ID
     * @param topic    方向名（仅用于记录分数，不传入评分 Prompt）
     * @param dimension 出题维度（不再传入评分 Prompt）
     * @param question AI 出的题目
     * @param answer   用户的回答
     */
    public CompletableFuture<Void> scoreAnswerAsync(String chatId, String topic, String dimension,
                                                      String question, String answer) {
        return CompletableFuture.runAsync(() -> {
            try {
                long start = System.currentTimeMillis();
                // 🔴 [Bug修复-V2] 不传入 topic 和 dimension，避免 AI 方向偏见
                String prompt = SCORE_PROMPT
                        .replace("{question}", question != null ? question : "")
                        .replace("{answer}", answer != null ? answer : "");
                String result = chatClient.prompt().user(prompt).call().content();

                if (result != null && !result.isBlank()) {
                    UserAbilityProfile.ScoreResult sr = parseScoreResult(result, topic);
                    if (sr != null) {
                        UserAbilityProfile profile = getOrCreateProfile(chatId);

                        // 🔴 [诊断] 记录评分入参
                        log.info("📊 [评分入参] topic={}, question={}, rawWeakPoints={}",
                                topic,
                                question != null && question.length() > 60
                                        ? question.substring(0, 60) + "…" : question,
                                sr.getWeakPoints());

                        // 🔴 [Bug修复-V2] 后端确定性路由：将每个弱点评路由到正确方向
                        Map<String, List<String>> routed = routeWeakPoints(
                                sr.getWeakPoints(), topic, sr.getWeakPointDetails());

                        // 🔴 [诊断] 记录路由结果
                        routed.forEach((t, wps) -> {
                            if (!wps.isEmpty()) {
                                log.info("📊 [路由结果] {} → {}: {}", topic, t, wps);
                            }
                        });

                        // 当前方向的弱点评
                        List<String> currentWps = routed.getOrDefault(topic, List.of());
                        boolean hasWeakPoints = !currentWps.isEmpty();
                        boolean isGoodAnswer = sr.getScore() >= 4 && !hasWeakPoints;

                        // 🔴 [Hotfix-驻留轮次] 回答质量回调 → TopicRotationService
                        if (answerQualityCallback != null) {
                            try {
                                answerQualityCallback.accept(chatId, isGoodAnswer);
                            } catch (Exception e) {
                                log.warn("回答质量回调失败: {}", e.getMessage());
                            }
                        }

                        String debugAnswer = isGoodAnswer ? null : (answer != null && answer.length() > 500 ? answer.substring(0, 500) : answer);
                        String wqText = isGoodAnswer ? null : ((question != null && question.length() > 200) ? question.substring(0, 200) + "…" : question);

                        // ① 在当前方向下记录分数（不含弱点评）
                        profile.updateTopicScore(topic, sr.getScore(), currentWps, debugAnswer, sr.getWeakPointDetails(), wqText);

                        // ② 跨方向弱点评路由到正确方向下
                        for (Map.Entry<String, List<String>> entry : routed.entrySet()) {
                            String resolvedTopic = entry.getKey();
                            List<String> wps = entry.getValue();
                            if (wps.isEmpty() || resolvedTopic.equals(topic)) continue;

                            log.info("🔄 路由跨方向弱点评 [{}] → [{}]: {}", topic, resolvedTopic, wps);
                            // 在目标方向下记录弱点评（不产生额外评分记录）
                            profile.addRoutedWeakPoints(resolvedTopic, wps, sr.getWeakPointDetails(), wqText, debugAnswer);
                        }

                        // ③ 错题本：低分必记（即使弱点被路由走也保留题干）
                        if (sr.getScore() < 4) {
                            UserAbilityProfile.TopicScore ts = profile.getTopicScores().get(topic);
                            if (ts != null) {
                                List<String> keys = !currentWps.isEmpty()
                                        ? currentWps
                                        : List.of("综合薄弱");
                                for (String wp : keys) {
                                    ts.recordWrongQuestion(wp, wqText != null ? wqText : question);
                                }
                            }
                        }

                        saveProfile(chatId);

                        UserAbilityProfile.TopicScore ts = profile.getTopicScores().get(topic);
                        String level = ts != null ? ts.getScoreLevel() : "?";
                        log.info("📊 评分: chatId={}, topic={}, rawScore={}/5, level={}, 当前方向弱点评={}, 路由到其他方向={}, 耗时={}ms",
                                chatId, topic, sr.getScore(), level,
                                currentWps.size(),
                                routed.entrySet().stream().filter(e -> !e.getKey().equals(topic))
                                        .map(e -> e.getKey() + ":" + e.getValue().size()).toList(),
                                System.currentTimeMillis() - start);
                    }
                }
            } catch (Exception e) {
                log.warn("❌ 评分异常（不影响主流程）: {}", e.getMessage());
            }
        }, scoringExecutor);
    }

    /**
     * 🔴 [Bug修复-V2] 解析评分 JSON — 不再 override topic，也不再追踪 originalTopic。
     * topic 字段仅用于 ScoreResult 内部完整解析，路由逻辑在 routeWeakPoints 中独立完成。
     */
    private UserAbilityProfile.ScoreResult parseScoreResult(String json, String defaultTopic) {
        try {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String jsonStr = json.substring(start, end + 1);
                UserAbilityProfile.ScoreResult sr = objectMapper.readValue(jsonStr, UserAbilityProfile.ScoreResult.class);
                // 🔴 [Bug修复-V2] 不再 override topic，不再追踪 originalTopic
                // 路由在 routeWeakPoints 中后端独立完成
                return sr;
            }
        } catch (Exception e) {
            log.warn("评分 JSON 解析失败，使用默认值: {}", e.getMessage());
        }
        UserAbilityProfile.ScoreResult fallback = new UserAbilityProfile.ScoreResult();
        fallback.setTopic(defaultTopic);
        return fallback;
    }

    /**
     * 🔴 [终版] 向后兼容：无 dimension 的重载。
     */
    public CompletableFuture<Void> scoreAnswerAsync(String chatId, String topic, String question, String answer) {
        return scoreAnswerAsync(chatId, topic, null, question, answer);
    }

    /**
     * 兼容旧签名评分（无 topic 参数时使用）
     */
    public CompletableFuture<Void> scoreAnswerAsync(String chatId, String question, String answer) {
        return scoreAnswerAsync(chatId, "", null, question, answer);
    }

    /**
     * 错题复习模式评分：答对（score >= 4）时按知识点/题干从错题本移除。
     */
    public CompletableFuture<Void> scoreAnswerReviewAsync(String chatId, String topic,
                                                            String question, String answer) {
        return scoreAnswerReviewAsync(chatId, topic, question, answer, null);
    }

    public CompletableFuture<Void> scoreAnswerReviewAsync(String chatId, String topic,
                                                            String question, String answer,
                                                            String knowledgePoint) {
        return CompletableFuture.runAsync(() -> {
            try {
                long start = System.currentTimeMillis();
                String prompt = SCORE_PROMPT
                        .replace("{question}", question != null ? question : "")
                        .replace("{answer}", answer != null ? answer : "");
                String result = chatClient.prompt().user(prompt).call().content();

                if (result != null && !result.isBlank()) {
                    UserAbilityProfile.ScoreResult sr = parseScoreResult(result, topic);
                    if (sr != null && sr.getScore() >= 4) {
                        UserAbilityProfile profile = getOrCreateProfile(chatId);
                        UserAbilityProfile.TopicScore ts = profile.getTopicScores().get(topic);
                        if (ts != null) {
                            boolean removed = false;
                            if (knowledgePoint != null && !knowledgePoint.isBlank()) {
                                ts.removeWrongQuestion(knowledgePoint);
                                removed = true;
                            }
                            removed = ts.removeWrongQuestionByText(question) || removed;
                            if (removed) {
                                saveProfile(chatId);
                                log.info("✅ 复习答对，移除错题: chatId={}, topic={}, score={}/5, q={}, 耗时={}ms",
                                        chatId, topic, sr.getScore(),
                                        question != null && question.length() > 40
                                                ? question.substring(0, 40) : question,
                                        System.currentTimeMillis() - start);
                            } else {
                                log.warn("⚠️ 复习答对但未匹配到错题本条目: chatId={}, topic={}, q={}",
                                        chatId, topic, question);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("复习评分异常（不影响主流程）: {}", e.getMessage());
            }
        }, scoringExecutor);
    }

    /**
     * [全覆盖] 获取指定方向在当前会话中的权重（0.3 ~ 2.0），用于加权选题。
     * 增加出题次数衰减：同一方向出题>=5次后权重递减，避免偏执追打。
     */
    public double getTopicWeight(String chatId, String topic) {
        UserAbilityProfile profile = getOrCreateProfile(chatId);
        double baseWeight;
        int score = profile.getTopicScore(topic);
        if (score <= 0) baseWeight = 2.0;
        else if (score < 30) baseWeight = 2.0;
        else if (score < 50) baseWeight = 1.5;
        else if (score < 70) baseWeight = 1.0;
        else baseWeight = 0.5;

        // 出题次数衰减：已出超过5题后权重减半
        UserAbilityProfile.TopicScore ts = profile.getTopicScores().get(topic);
        if (ts != null && ts.getQuestionCount() >= 5) {
            double attenuation = Math.max(0.3, 1.0 - (ts.getQuestionCount() - 5) * 0.1);
            baseWeight *= attenuation;
            log.debug("[全覆盖] 出题衰减: topic={}, 出题{}题, 衰减后权重={}",
                    topic, ts.getQuestionCount(), String.format("%.2f", baseWeight));
        }

        return Math.max(0.3, Math.min(2.0, baseWeight));
    }

    /**
     * 获取展示安全的能力画像（深拷贝并清洗薄弱点，不修改原始数据）
     * <p>
     * 过滤评价词、跨方向词，确保前端展示的弱项均为具体可学习的技术知识点。
     * 不调用 saveProfile，不修改 .ability-profiles/ 下的历史文件。
     */
    public UserAbilityProfile getDisplayProfile(String chatId, Long userId) {
        UserAbilityProfile source = getOrCreateProfile(chatId, userId);
        UserAbilityProfile display = new UserAbilityProfile(source.getChatId());
        display.setLastUpdatedAt(source.getLastUpdatedAt());
        display.setTopicCoveredDimensions(new java.util.HashMap<>(source.getTopicCoveredDimensions()));
        display.setAskedQuestionFingerprints(new java.util.HashSet<>(source.getAskedQuestionFingerprints()));

        WeakPointNormalizer normalizer = new WeakPointNormalizer();
        java.util.Map<String, UserAbilityProfile.TopicScore> copiedScores = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, UserAbilityProfile.TopicScore> entry : source.getTopicScores().entrySet()) {
            String topic = entry.getKey();
            UserAbilityProfile.TopicScore original = entry.getValue();
            UserAbilityProfile.TopicScore copy = new UserAbilityProfile.TopicScore();
            copy.setTopic(original.getTopic());
            copy.setScoreHistory(new java.util.ArrayList<>(original.getScoreHistory()));

            Map<String, String> originalDetails = original.getWeakPointDetails() == null
                    ? Map.of() : original.getWeakPointDetails();
            Map<String, List<String>> originalAnswers = original.getWeakPointAnswers() == null
                    ? Map.of() : original.getWeakPointAnswers();
            Map<String, String> originalQuestions = original.getWeakPointQuestions() == null
                    ? Map.of() : original.getWeakPointQuestions();

            WeakPointNormalizer.NormalizedWeakPoints normalized = normalizer.normalize(
                    topic, "", original.getWeakPoints(), originalDetails);
            copy.setWeakPoints(new java.util.ArrayList<>(normalized.weakPoints()));
            copy.setWeakPointDetails(new java.util.LinkedHashMap<>(normalized.weakPointDetails()));

            // 🔴 错题本不再做跨方向过滤（cleanCrossTopicWeakPoints 已在加载时统一清理）
            java.util.Map<String, String> filteredWq = new java.util.LinkedHashMap<>();
            if (original.getWrongQuestions() != null) {
                for (java.util.Map.Entry<String, String> e : original.getWrongQuestions().entrySet()) {
                    filteredWq.put(e.getKey(), e.getValue());
                }
            }
            copy.setWrongQuestions(filteredWq);

            java.util.Map<String, List<String>> answerMap = new java.util.LinkedHashMap<>();
            java.util.Map<String, String> questionMap = new java.util.LinkedHashMap<>();
            for (String weakPoint : normalized.weakPoints()) {
                List<String> answers = originalAnswers.getOrDefault(weakPoint, List.of());
                answerMap.put(weakPoint, new java.util.ArrayList<>(answers));
                String question = originalQuestions.getOrDefault(weakPoint, "");
                if (!question.isBlank()) {
                    questionMap.put(weakPoint, question);
                }
            }
            copy.setWeakPointAnswers(answerMap);
            copy.setWeakPointQuestions(questionMap);
            copiedScores.put(topic, copy);
        }
        display.setTopicScores(copiedScores);
        return display;
    }

    /**
     * 🔴 [全覆盖] 薄弱点注入提示（降低频率 + 减弱语气）。
     * <p>
     * 每 3 轮注入一次，不再每次强制"优先考察"，改为"可适当穿插"。
     */
    public String buildWeakPointHint(String chatId, String topic) {
        UserAbilityProfile profile = getOrCreateProfile(chatId);
        UserAbilityProfile.TopicScore ts = profile.getTopicScores().get(topic);
        if (ts == null) return "";

        double avg = ts.getAverageScore();
        if (avg >= 3.0) return "";

        // [全覆盖] 每 3 轮注入一次，避免连续追打薄弱点
        int count = weakHintInjectionCount.merge(chatId, 1, Integer::sum);
        if (count % 3 != 1) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("【").append(topic).append("】").append(ts.getScoreEmoji()).append(ts.getScoreLevel())
          .append("(").append(String.format("%.1f", avg)).append("/5)");

        List<String> weakPoints = ts.getWeakPoints();
        if (!weakPoints.isEmpty()) {
            sb.append(" 薄弱点：").append(String.join("、", weakPoints));
        }
        sb.append(" → 可适当穿插考察以上薄弱点\n");

        return sb.toString();
    }

    /**
     * 获取画像总结文本（使用展示清洗后的画像）
     */
    public String buildSummary(String chatId) {
        UserAbilityProfile profile = getDisplayProfile(chatId, null);
        if (profile.getTopicScores().isEmpty()) return "暂无考察数据";

        StringBuilder sb = new StringBuilder();
        sb.append("综合评分：").append(profile.getOverallScore()).append("/100\n\n");
        sb.append("已考察方向：\n");
        for (UserAbilityProfile.TopicScore ts : profile.getAllTopicScores()) {
            sb.append(ts.getScoreEmoji()).append(" ").append(ts.getTopic())
              .append("：").append(ts.getScoreLevel())
              .append("（").append(String.format("%.1f", ts.getAverageScore())).append("/5")
              .append("，已考").append(ts.getQuestionCount()).append("题）");
            if (!ts.getWeakPoints().isEmpty()) {
                sb.append(" 薄弱点：").append(String.join("、", ts.getWeakPoints()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 生成 AI 学习建议（使用展示清洗后的画像）
     */
    public String generateAISuggestion(String chatId) {
        UserAbilityProfile profile = getDisplayProfile(chatId, null);
        if (profile.getTopicScores().isEmpty()) return "暂无考察数据，无法生成建议。";

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位资深技术面试教练。基于以下候选人的技术考察数据，生成个性化学习建议。\n\n");
        prompt.append(buildSummary(chatId));
        prompt.append("\n请给出针对性的学习建议（200字以内），指出优先攻克的方向和具体知识点。");

        try {
            return chatClient.prompt().user(prompt.toString()).call().content();
        } catch (Exception e) {
            log.warn("生成 AI 建议失败: {}", e.getMessage());
            return "基于您的考察数据生成个性化建议失败，请稍后重试。";
        }
    }

    /**
     * 🔴 错题复习：从指定方向的错题本中移除知识点。
     * 在复习模式中答对时调用。
     */
    public void removeWrongQuestion(String chatId, String topic, String knowledgePoint) {
        UserAbilityProfile profile = getOrCreateProfile(chatId);
        UserAbilityProfile.TopicScore ts = profile.getTopicScores().get(topic);
        if (ts != null) {
            ts.removeWrongQuestion(knowledgePoint);
            saveProfile(chatId);
            log.info("🗑️ 错题移除: chatId={}, topic={}, kp={}", chatId, topic, knowledgePoint);
        }
    }

    /**
     * 重置指定会话的能力画像（同时清理 userId 冗余存储）
     */
    public void resetProfile(String chatId) {
        resetProfile(chatId, null);
    }

    /**
     * 重置指定会话的能力画像（同时清理 userId 冗余存储）
     */
    public void resetProfile(String chatId, Long userId) {
        localCache.remove(chatId);
        deleteProfileFile(chatId);
        deleteFromRedis(chatId);
        // 同时清理 userId 级别的冗余存储，避免重置后又从 userId 恢复
        if (userId != null) {
            deleteUserProfileFile(userId);
            deleteFromUserRedis(userId);
            log.info("🔄 重置用户画像冗余存储: userId={}", userId);
        }
        log.info("🔄 重置能力画像: chatId={}", chatId);
    }
}
