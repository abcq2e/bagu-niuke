package com.qian.qianaiagent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 基于本地文件的对话记忆实现（Spring AI {@link ChatMemory}）。
 *
 * <h2>存储布局</h2>
 * <pre>
 * {baseDir}/
 *   ├── {chatId}.json     消息正文
 *   ├── {chatId}.title    用户自定义标题（旁路元数据，混进消息列表会污染反序列化）
 *   ├── {chatId}.owner    会话归属者 userId（纯文本）
 *   └── .locks/{chatId}.lock   跨进程锁，独立子目录，保留策略清扫器永不触碰
 * </pre>
 *
 * <h2>归属模型</h2>
 * 详见 {@link #startSession} / {@link #accessConversation} / {@link #canManage} 的注释。
 * 一句话：owner 文件由文件系统用 {@code CREATE_NEW} 裁定唯一赢家，无主数据永不自动删除。
 *
 * <h2>⚠️ 已知残留（不要假装它不存在）</h2>
 * <ol>
 *   <li><b>写侧越权防不住。</b>真正的写入口是 {@code ChatMemoryAdvisor}，跑在响应式线程上，
 *       那时 {@code JwtAuthFilter} 的 finally 已经清掉 ThreadLocal 里的 UserContext。
 *       本类的归属校验只能保证「读不越权」，写侧只到 Controller 门卫为止，
 *       门卫与写入之间的 TOCTOU 窗口无法消除。缓解手段是让 chatId 不可预测。</li>
 *   <li><b>跨进程锁只保证「不写坏文件」</b>，不解决跨实例状态一致性 ——
 *       {@code messageCounts}、摘要缓存、各 Service 的 per-chatId 内存状态全是 JVM 内的。
 *       本类定位是「单机 + 跨进程防损坏」，不是「已支持多实例」。</li>
 * </ol>
 */
public class FileBasedChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(FileBasedChatMemory.class);

    /** 单个会话文件最大消息数 */
    private static final int MAX_MESSAGES_PER_FILE = 200;
    /** 建议最大会话文件数 */
    private static final int MAX_RECOMMENDED_FILES = 100;

    private static final String JSON_SUFFIX = ".json";
    private static final String TITLE_SUFFIX = ".title";
    private static final String OWNER_SUFFIX = ".owner";
    private static final String LOCK_DIR = ".locks";

    /** 原子替换的有限重试次数与间隔（详见 {@link #moveWithRetry}）。 */
    private static final int MOVE_ATTEMPTS = 3;
    private static final long MOVE_RETRY_MILLIS = 50;

    private final String baseDir;
    private final Path lockDir;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 消息数缓存：避免 {@link #listConversations} 为统计条数把每个会话文件全量反序列化。
     * <p>
     * 🔴 只在<b>写入真正成功</b>后才更新 —— 否则写失败时会与文件内容永久不一致。
     */
    private final Map<String, Integer> messageCounts = new ConcurrentHashMap<>();

    public FileBasedChatMemory(String baseDir) {
        this(baseDir, Clock.systemDefaultZone());
    }

    /** 注入 {@link Clock} 供保留策略的 mtime 比较使用（便于测试）。 */
    public FileBasedChatMemory(String baseDir, Clock clock) {
        this.baseDir = baseDir;
        this.clock = clock;
        this.lockDir = Paths.get(baseDir, LOCK_DIR);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        try {
            Files.createDirectories(Paths.get(baseDir));
            Files.createDirectories(lockDir);
        } catch (IOException e) {
            log.error("创建对话存储目录失败: {} — {}", baseDir, e.getMessage());
        }
        log.info("对话存储目录: {}", Paths.get(baseDir).toAbsolutePath());
    }

    // ============================================================
    // ChatMemory 接口实现
    // ============================================================

    @Override
    public void add(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        Boolean written = withLock(conversationId, () -> {
            List<Message> existing;
            if (file.exists()) {
                existing = readFromFile(file);
                if (existing == null) {
                    log.error("读取对话文件失败，跳过本次写入保护历史数据: {}", file.getPath());
                    return false;
                }
            } else {
                existing = new ArrayList<>();
            }
            existing.addAll(messages);
            if (existing.size() > MAX_MESSAGES_PER_FILE) {
                int before = existing.size();
                existing = ProtectedMessages.truncate(existing, MAX_MESSAGES_PER_FILE);
                log.warn("对话 {} 超过 {} 条消息，已截断 {} → {} 条（受保护消息保留）",
                        conversationId, MAX_MESSAGES_PER_FILE, before, existing.size());
            }
            if (!writeToFile(file, existing)) {
                return false;
            }
            // 🔴 只有落盘成功才更新缓存，否则计数会与文件永久漂移
            messageCounts.put(conversationId, existing.size());
            return true;
        }, false);

        if (!Boolean.TRUE.equals(written)) {
            log.warn("对话 {} 本次消息未写入（加锁失败或落盘失败）", conversationId);
        }
    }

    /**
     * 读取会话消息。
     * <p>
     * 🔴 与写入共用同一把锁：{@code Files.move(REPLACE_EXISTING)} 在 Windows 上遇到
     * 并发<b>读</b>句柄会抛 {@code AccessDeniedException}（POSIX 允许替换被打开读的文件，
     * Windows 不允许），读不加锁会让并发写入静默丢失。
     */
    @Override
    public List<Message> get(String conversationId) {
        File file = getConversationFile(conversationId);
        return withLock(conversationId, () -> {
            if (!file.exists()) {
                return new ArrayList<Message>();
            }
            List<Message> messages = readFromFile(file);
            return messages != null ? messages : new ArrayList<Message>();
        }, new ArrayList<>());
    }

    /**
     * 清空会话消息（记忆重置语义）。
     * <p>
     * 🔴 <b>保留</b> {@code .owner}：清空记忆 ≠ 删除会话，归属信息要留着，
     * 否则重置后会话会变成无主、被他人认领。彻底删除请用 {@link #deleteConversation}。
     */
    @Override
    public void clear(String conversationId) {
        File file = getConversationFile(conversationId);
        withLock(conversationId, () -> {
            messageCounts.remove(conversationId);
            File titleFile = titleFile(conversationId);
            if (titleFile.exists()) {
                titleFile.delete();
            }
            if (file.exists() && file.delete()) {
                log.info("已清空对话: {}", conversationId);
                return true;
            }
            return false;
        }, false);
    }

    // ============================================================
    // 归属（owner 旁路文件）
    // ============================================================

    /**
     * 建立或继续一个<b>可写</b>会话 —— 对话接口在进入响应式流之前调用。
     * <p>
     * 无主则认领（这是创建路径，<b>不要求</b> {@code .json} 已存在）。
     * 认领发生在 Controller 边界，因此 {@code .owner} 一定先于 {@code .json} 落盘 ——
     * 反过来会在「数据已落盘、owner 未写」的窗口里被别人合法认领，即会话劫持。
     *
     * @return false 表示该会话已归属他人，应拒绝本次请求
     */
    public boolean startSession(String chatId, Long userId) {
        if (userId == null) {
            return true;    // 无用户上下文（理论上被 JwtAuthFilter 拦住）：保持旧行为
        }
        return withLock(chatId, () -> userId.equals(claimLocked(chatId, userId)), false);
    }

    /**
     * 打开一个<b>既有</b>会话（恢复历史时调用）—— 「首次访问即认领」的<b>唯一</b>入口。
     * <p>
     * 🔴 必须 {@code .json} 已存在才认领：否则任何人都能对任意 chatId 预抢占
     * （「新对话」按钮用的是可预测的时间戳 ID）。
     *
     * @return false 表示该会话已归属他人，应拒绝本次请求
     */
    public boolean accessConversation(String chatId, Long userId) {
        if (userId == null) {
            return true;
        }
        return withLock(chatId, () -> {
            Long owner = ownerOf(chatId);
            if (owner != null) {
                return owner.equals(userId);
            }
            if (!getConversationFile(chatId).exists()) {
                return true;    // 无数据的会话：没有可泄露的内容，认领留给 startSession
            }
            return userId.equals(claimLocked(chatId, userId));
        }, false);
    }

    /**
     * 严格归属判定 —— 删除 / 重命名 / 导出 / 评估查询这类破坏性或旁路操作使用。
     * <p>
     * 与 {@link #accessConversation} 的区别：<b>无主一律拒绝</b>，且不认领。
     * 想删掉一个无主旧会话，得先打开它（触发认领）再删。
     */
    public boolean canManage(String chatId, Long userId) {
        return userId != null && userId.equals(ownerOf(chatId));
    }

    /** 读取会话归属者。文件不存在、内容非法、读取失败都返回 {@code null}（按无主处理）。 */
    public Long ownerOf(String chatId) {
        File ownerFile = ownerFile(chatId);
        if (!ownerFile.exists()) {
            return null;
        }
        try {
            String raw = Files.readString(ownerFile.toPath()).trim();
            if (raw.isEmpty()) {
                return null;
            }
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            log.warn("会话 {} 的 .owner 内容非法，按无主处理: {}", chatId, raw(ownerFile));
            return null;
        } catch (IOException e) {
            log.warn("读取会话 {} 归属失败，按无主处理: {}", chatId, e.getMessage());
            return null;
        }
    }

    /**
     * 在<b>已持有</b>会话锁的前提下认领归属。
     * <p>
     * 用 {@code CREATE_NEW} 让文件系统裁定唯一赢家 —— 只靠锁是不够的，
     * 锁本身可能因 {@code ATOMIC_MOVE} 换 inode 等原因失效。
     * {@link FileAlreadyExistsException} 是 TOCTOU 的兜底：即使有人绕过锁，也只有一个赢家。
     */
    private Long claimLocked(String chatId, Long userId) {
        Long existing = ownerOf(chatId);
        if (existing != null) {
            return existing;    // 以既有 owner 为准
        }
        File ownerFile = ownerFile(chatId);
        try {
            Files.writeString(ownerFile.toPath(), userId.toString(),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            log.info("会话 {} 已归属用户 {}", chatId, userId);
            return userId;
        } catch (FileAlreadyExistsException e) {
            Long raced = ownerOf(chatId);
            if (raced != null) {
                return raced;    // 并发下输了，以既有 owner 为准
            }
            // 文件在、但内容不可解析（被截断/手改/写入中断）——没有合法归属者。
            // 此时若不覆盖，该会话将永久无法被认领，也无法通过 canManage 被管理。
            // 并发写同一畸形文件的极端情况下，最终以回读到的值为准。
            try {
                Files.writeString(ownerFile.toPath(), userId.toString(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
                log.warn("会话 {} 的 .owner 内容不可解析，已重新认领给用户 {}", chatId, userId);
                return ownerOf(chatId);
            } catch (IOException ioe) {
                log.error("修复会话 {} 归属失败: {}", chatId, ioe.getMessage());
                return null;
            }
        } catch (IOException e) {
            log.error("写入会话 {} 归属失败: {}", chatId, e.getMessage());
            return null;
        }
    }

    // ============================================================
    // 会话管理（管理接口用）
    // ============================================================

    /**
     * 覆盖写入会话消息（换方向裁剪用）。不走归属校验，与 {@link #add} 同一口径 ——
     * 写侧归属只由 Controller 门卫保证，详见类注释的残留风险说明。
     */
    public void replaceMessages(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        withLock(conversationId, () -> {
            List<Message> copy = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
            if (copy.size() > MAX_MESSAGES_PER_FILE) {
                copy = ProtectedMessages.truncate(copy, MAX_MESSAGES_PER_FILE);
            }
            if (!writeToFile(file, copy)) {
                return false;
            }
            messageCounts.put(conversationId, copy.size());
            log.info("对话 {} 已替换为 {} 条消息（裁剪/覆盖）", conversationId, copy.size());
            return true;
        }, false);
    }

    /** 删除指定会话（永久删除，连带标题与归属）。 */
    public boolean deleteConversation(String chatId) {
        return withLock(chatId, () -> deleteFilesLocked(chatId), false);
    }

    /**
     * 在<b>已持有</b>会话锁的前提下删除消息文件 + 标题 + 归属。
     * <p>
     * 三者必须同生共死：只删 {@code .json} 会让 {@code .title} 残留（重建同名会话会继承旧标题），
     * 或让 {@code .owner} 残留（孤儿归属文件会一直累积）。
     * <p>
     * 不删 {@code .lock} —— Windows 上删除被持有的锁文件必定失败，只会刷日志。
     */
    private boolean deleteFilesLocked(String conversationId) {
        messageCounts.remove(conversationId);
        for (File sidecar : new File[]{titleFile(conversationId), ownerFile(conversationId)}) {
            if (sidecar.exists()) {
                sidecar.delete();
            }
        }
        File file = getConversationFile(conversationId);
        if (file.exists() && file.delete()) {
            log.info("已删除对话: {}", conversationId);
            return true;
        }
        return false;
    }

    /**
     * 列出会话概览：<b>我自己的 + 无主的</b>。
     * <p>
     * 🔴 绝不在此认领 —— 否则第一个打开侧边栏的人会把所有无主旧会话据为己有。
     * 无主条目仍然列出（{@code ownerless=true}），这样旧数据不会在 UI 上凭空消失，
     * 用户点开时再由 {@link #accessConversation} 认领。
     *
     * @param userId 当前登录用户；null 时只返回无主会话
     */
    public List<ConversationInfo> listConversations(Long userId) {
        File[] files = new File(baseDir).listFiles((d, name) -> name.endsWith(JSON_SUFFIX));
        if (files == null) {
            return Collections.emptyList();
        }

        // 🔴 文件数量预警
        if (files.length > MAX_RECOMMENDED_FILES) {
            log.warn("对话文件数量 ({}) 超过建议上限 ({})，建议清理旧文件",
                    files.length, MAX_RECOMMENDED_FILES);
        }

        return Arrays.stream(files)
                .map(file -> {
                    String chatId = chatIdOf(file);
                    if (chatId == null) {
                        return null;
                    }
                    Long owner = ownerOf(chatId);
                    boolean ownerless = owner == null;
                    boolean mine = userId != null && userId.equals(owner);
                    // 🔴 优先用自定义标题，否则固定为「对话」
                    String customTitle = getCustomTitle(chatId);
                    String title = customTitle != null ? customTitle : "对话";
                    long lastModified = file.lastModified();
                    // 🔴 走计数缓存：只需条数，不必把每个文件全量反序列化成 Message
                    int messageCount = messageCounts.computeIfAbsent(chatId, k -> countMessages(file));
                    return new ConversationInfo(chatId, title, lastModified, messageCount, mine, ownerless);
                })
                .filter(Objects::nonNull)
                .filter(info -> info.mine() || info.ownerless())
                .sorted((a, b) -> Long.compare(b.lastModified(), a.lastModified()))
                .collect(Collectors.toList());
    }

    /**
     * 保留策略：按「数量上限」和「最长保留天数」清理属于 {@code userId} 的会话。
     *
     * <h2>硬约束</h2>
     * <ul>
     *   <li><b>只删 owner == userId 的文件</b>，绝不碰他人的。</li>
     *   <li><b>无主文件永不自动删</b> —— 少了这条，第一次清扫就把全部 legacy 数据抹掉且不可恢复。</li>
     *   <li>宽限期内的文件不删（保护正在进行但暂时没写的会话）。</li>
     *   <li>拿不到该会话的锁就<b>跳过</b>，不强删不抛异常。</li>
     *   <li><b>一次只锁一个 chatId</b>，避免锁序死锁。</li>
     * </ul>
     *
     * @param maxConversations 每用户会话数上限，&lt;= 0 表示不按数量限制
     * @param maxAgeDays       最长保留天数，&lt;= 0 表示不按时间限制
     * @param grace            宽限期，null 或负值表示无宽限；宽限内的会话无论如何都保留
     * @return 实际删除的会话数
     */
    public int enforceRetention(Long userId, int maxConversations, int maxAgeDays, Duration grace) {
        if (userId == null || (maxConversations <= 0 && maxAgeDays <= 0)) {
            return 0;
        }
        List<File> owned = ownedFiles(userId);      // 已按 mtime 降序
        if (owned.isEmpty()) {
            return 0;
        }

        Instant now = clock.instant();
        Instant graceCutoff = grace != null && !grace.isNegative() ? now.minus(grace) : null;
        Instant ageCutoff = maxAgeDays > 0 ? now.minus(Duration.ofDays(maxAgeDays)) : null;

        int deleted = 0;
        for (int i = 0; i < owned.size(); i++) {
            File file = owned.get(i);
            Instant modified = Instant.ofEpochMilli(file.lastModified());

            // 两条独立触发条件：超出数量上限（按 mtime 降序的位置），或超过最长保留天数
            boolean overCount = maxConversations > 0 && i >= maxConversations;
            boolean tooOld = ageCutoff != null && modified.isBefore(ageCutoff);
            if (!overCount && !tooOld) {
                continue;
            }
            if (graceCutoff != null && modified.isAfter(graceCutoff)) {
                continue;   // 宽限期内，跳过
            }

            String chatId = chatIdOf(file);
            if (chatId == null) {
                continue;
            }
            if (Boolean.TRUE.equals(withLock(chatId, () -> deleteFilesLocked(chatId), false))) {
                deleted++;
                log.info("保留策略清理会话: chatId={}, userId={}", chatId, userId);
            }
        }
        return deleted;
    }

    /**
     * 当前磁盘上出现过的全部归属者。
     * <p>
     * 供保留策略清扫器遍历使用 —— 它需要知道「有哪些用户存在」才能逐个配额清理。
     * 无主文件不产生任何 owner，因此不在结果中（它们也永不自动删）。
     */
    public Set<Long> knownOwnerIds() {
        File[] files = new File(baseDir).listFiles((d, name) -> name.endsWith(JSON_SUFFIX));
        if (files == null) {
            return Set.of();
        }
        Set<Long> owners = new HashSet<>();
        for (File file : files) {
            String chatId = chatIdOf(file);
            if (chatId == null) {
                continue;
            }
            Long owner = ownerOf(chatId);
            if (owner != null) {
                owners.add(owner);
            }
        }
        return owners;
    }

    /**
     * 归属该用户的全部会话 ID。
     * <p>
     * 供跨会话聚合接口做隔离用（如 {@code /ai/evals/summary} —— 它此前扫描整个
     * {@code data/evals} 目录，把所有用户的评分聚合在一起返回给任何人）。
     */
    public Set<String> chatIdsOwnedBy(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (File file : ownedFiles(userId)) {
            String chatId = chatIdOf(file);
            if (chatId != null) {
                ids.add(chatId);
            }
        }
        return ids;
    }

    /** 按最后修改时间倒序列出归属该用户的会话文件。 */
    private List<File> ownedFiles(Long userId) {
        File[] files = new File(baseDir).listFiles((d, name) -> name.endsWith(JSON_SUFFIX));
        if (files == null) {
            return List.of();
        }
        List<File> owned = new ArrayList<>();
        for (File file : files) {
            String chatId = chatIdOf(file);
            if (chatId == null) {
                continue;
            }
            if (userId.equals(ownerOf(chatId))) {
                owned.add(file);
            }
        }
        owned.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return owned;
    }

    /** 获取指定会话的完整消息列表（{@link #get} 的别名，供管理接口使用）。 */
    public List<Message> getConversation(String chatId) {
        return get(chatId);
    }

    // ============================================================
    // 标题（旁路元数据）
    // ============================================================

    /** 更新对话标题（存到旁路元数据文件）。 */
    public void updateTitle(String conversationId, String newTitle) {
        File titleFile = titleFile(conversationId);
        withLock(conversationId, () -> {
            try {
                Files.writeString(titleFile.toPath(), newTitle);
                log.info("标题已更新: {} → {}", conversationId, newTitle);
                return true;
            } catch (IOException e) {
                log.error("保存标题失败: {}", e.getMessage());
                return false;
            }
        }, false);
    }

    /** 读取用户手动设置的自定义标题。 */
    public String getCustomTitle(String conversationId) {
        File titleFile;
        try {
            // 读路径保持宽松：listConversations 由文件名反推 chatId，
            // 个别畸形文件名不应让整个列表接口挂掉。
            titleFile = titleFile(conversationId);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (titleFile.exists()) {
            try {
                return Files.readString(titleFile.toPath()).trim();
            } catch (IOException e) {
                // 读失败，降级
            }
        }
        return null;
    }

    // ============================================================
    // 文件读写
    // ============================================================

    /**
     * 从文件读取消息列表。读取失败返回 null（而非空列表），
     * 让调用方可以区分"文件不存在"和"文件损坏"。
     */
    private List<Message> readFromFile(File file) {
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(
                    file, new TypeReference<List<Map<String, Object>>>() {});
            return raw.stream()
                    .map(this::mapToMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            log.error("读取对话文件失败: {} — {}", file.getPath(), e.getMessage());
            return null;
        }
    }

    /**
     * 写入文件，先写临时文件再原子替换，防止写入中断损坏原文件。
     *
     * @return 是否写入成功。🔴 调用方必须据此决定是否更新计数缓存。
     */
    private boolean writeToFile(File file, List<Message> messages) {
        Path dir = file.getParentFile().toPath();
        Path tmpFile = null;
        try {
            // 🔴 唯一临时名：原先固定的 {chatId}.json.tmp 在跨进程时会互相覆盖，内容交错损坏
            tmpFile = Files.createTempFile(dir, file.getName() + ".", ".tmp");
            // 机器读的文件不需要缩进美化：prettyPrint 只增大体积与 CPU（61 条消息可到 36KB）
            objectMapper.writeValue(tmpFile.toFile(), messages);
            moveWithRetry(tmpFile, file.toPath());
            return true;
        } catch (IOException e) {
            log.error("保存对话文件失败: {} — {}", file.getPath(), e.getMessage());
            return false;
        } finally {
            if (tmpFile != null) {
                try {
                    Files.deleteIfExists(tmpFile);
                } catch (IOException ignored) {
                    // 已成功 move 走时是 no-op；失败时残留的 tmp 会被下次 createTempFile 的新名字避开
                }
            }
        }
    }

    /**
     * 原子替换，带有限重试。
     * <p>
     * 即便持有锁，Windows 上仍可能偶发 {@code AccessDeniedException}：
     * 延迟删除（JDK-8252883）、杀软扫描、以及并发读句柄（POSIX 允许替换被打开读的文件，
     * Windows 不允许）。所以重试是必需的，不能只依赖锁。
     */
    private void moveWithRetry(Path source, Path target) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < MOVE_ATTEMPTS; attempt++) {
            try {
                Files.move(source, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (IOException e) {
                last = e;
                if (attempt < MOVE_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(MOVE_RETRY_MILLIS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw last;
    }

    /** 只数条数，不做 Message 还原 —— {@link #listConversations} 计数缓存的冷启动兜底。 */
    private int countMessages(File file) {
        try {
            return objectMapper.readTree(file).size();
        } catch (IOException e) {
            log.warn("统计对话条数失败: {} — {}", file.getPath(), e.getMessage());
            return 0;
        }
    }

    // ============================================================
    // 消息反序列化
    // ============================================================

    /**
     * 将落盘的 Map 还原为 Spring AI Message。
     * <p>
     * 🔴 {@code messageType} 的取值是 {@code MessageType} 枚举的 {@code name()}：
     * {@code USER} / {@code ASSISTANT} / {@code SYSTEM} / {@code TOOL}。
     * 注意是 {@code TOOL} —— Spring AI 1.0.0 里<b>没有</b> {@code TOOL_RESPONSE} 这个取值
     * （{@code MessageType.TOOL} 的 {@code getValue()} 才是小写 {@code "tool"}，
     * 而 Jackson 默认按 {@code name()} 序列化，所以文件里是大写 {@code "TOOL"}）。
     * <p>
     * 角色必须逐个还原，不能只区分 USER / 其它：
     * <ul>
     *   <li>{@code SYSTEM} 被读成 Assistant 会让【方向切换】这类系统指令降级成 AI 发言</li>
     *   <li>带 {@code toolCalls} 的 Assistant 丢掉工具调用后，其后配对的 TOOL 消息
     *       就找不到对应 callId，回放给模型时上下文是断裂的</li>
     * </ul>
     */
    private Message mapToMessage(Map<String, Object> map) {
        try {
            String messageType = (String) map.get("messageType");
            String text = extractText(map);
            if (MessageType.USER.name().equals(messageType)) {
                return new UserMessage(text);
            }
            if (MessageType.SYSTEM.name().equals(messageType)) {
                return new SystemMessage(text);
            }
            if (MessageType.TOOL.name().equals(messageType)) {
                return new ToolResponseMessage(toToolResponses(map));
            }
            // ASSISTANT，以及历史数据里 messageType 缺失的情况（保持原有的兜底语义）
            List<AssistantMessage.ToolCall> toolCalls = toToolCalls(map);
            return toolCalls.isEmpty()
                    ? new AssistantMessage(text)
                    : new AssistantMessage(text, Map.of(), toolCalls, List.of());
        } catch (Exception e) {
            log.warn("消息反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 取消息正文：优先 {@code text}，回退到 {@code content}
     * （字符串，或 OpenAI 风格的 {@code [{"type":"text","text":"..."}]} 分段数组）。
     */
    private String extractText(Map<String, Object> map) {
        if (map.get("text") instanceof String text) {
            return text;
        }
        Object content = map.get("content");
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof List<?> parts) {
            for (Object part : parts) {
                if (part instanceof Map<?, ?> p
                        && "text".equals(p.get("type"))
                        && p.get("text") instanceof String text) {
                    return text;
                }
            }
        }
        return "";
    }

    /** 还原 Assistant 消息里的工具调用（{@code {"id","type","name","arguments"}}）。 */
    private List<AssistantMessage.ToolCall> toToolCalls(Map<String, Object> map) {
        if (!(map.get("toolCalls") instanceof List<?> raw)) {
            return List.of();
        }
        List<AssistantMessage.ToolCall> calls = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map<?, ?> call) {
                calls.add(new AssistantMessage.ToolCall(
                        asString(call.get("id")),
                        asString(call.get("type")),
                        asString(call.get("name")),
                        asString(call.get("arguments"))));
            }
        }
        return calls;
    }

    /** 还原 TOOL 消息里的工具返回（{@code {"id","name","responseData"}}）。 */
    private List<ToolResponseMessage.ToolResponse> toToolResponses(Map<String, Object> map) {
        if (!(map.get("responses") instanceof List<?> raw)) {
            return List.of();
        }
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map<?, ?> response) {
                responses.add(new ToolResponseMessage.ToolResponse(
                        asString(response.get("id")),
                        asString(response.get("name")),
                        asString(response.get("responseData"))));
            }
        }
        return responses;
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : "";
    }

    private static String raw(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            return "<不可读>";
        }
    }

    // ============================================================
    // 路径与校验
    // ============================================================

    /**
     * 在会话锁保护下执行。拿不到锁时返回 {@code onUnavailable}，action 不会被执行。
     * <p>
     * ⚠️ <b>不可嵌套</b>：同一个 chatId 上重复调用会触发
     * {@code OverlappingFileLockException}（JVM 级、按文件判定）。
     * 私有方法一律以 {@code Locked} 结尾表示「调用方已持有锁」。
     */
    private <T> T withLock(String chatId, Supplier<T> action, T onUnavailable) {
        ChatStoreLockManager.Locked<T> locked =
                ChatStoreLockManager.withLock(lockPath(chatId), action);
        if (!locked.acquired()) {
            log.warn("会话 {} 加锁失败，本次操作跳过", chatId);
            return onUnavailable;
        }
        return locked.value();
    }

    /**
     * 🔴 会话 ID 直接参与文件名拼接，必须挡在路径遍历之前。
     * <p>
     * Controller 已经对入参做过校验，这里是兜底：本类还有别的调用方
     * （如 {@code TopicMemoryTrimmer}），不该把安全边界只押在 Web 层。
     * {@code .json} / {@code .title} / {@code .owner} / {@code .lock} 四条路径共用。
     */
    private static void validateConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()
                || conversationId.contains("..")
                || conversationId.contains("/")
                || conversationId.contains("\\")) {
            throw new IllegalArgumentException("非法会话 ID: " + conversationId);
        }
    }

    /** 从文件名反推 chatId；文件名不合法时返回 null（列表/清扫路径用它跳过脏文件）。 */
    private static String chatIdOf(File file) {
        String name = file.getName();
        if (!name.endsWith(JSON_SUFFIX)) {
            return null;
        }
        String chatId = name.substring(0, name.length() - JSON_SUFFIX.length());
        try {
            validateConversationId(chatId);
            return chatId;
        } catch (IllegalArgumentException e) {
            log.warn("跳过文件名非法的会话文件: {}", name);
            return null;
        }
    }

    private File getConversationFile(String conversationId) {
        validateConversationId(conversationId);
        return new File(baseDir, conversationId + JSON_SUFFIX);
    }

    /** 标题旁路文件（与消息文件同名）。 */
    private File titleFile(String conversationId) {
        validateConversationId(conversationId);
        return new File(baseDir, conversationId + TITLE_SUFFIX);
    }

    /** 归属旁路文件。 */
    private File ownerFile(String conversationId) {
        validateConversationId(conversationId);
        return new File(baseDir, conversationId + OWNER_SUFFIX);
    }

    /** 跨进程锁文件 —— 独立子目录，保留策略清扫器永不触碰。 */
    private Path lockPath(String conversationId) {
        validateConversationId(conversationId);
        return lockDir.resolve(conversationId + ".lock");
    }

    /**
     * 会话概览信息。
     *
     * @param mine       是否归当前用户所有
     * @param ownerless  是否无主（legacy 数据，点开即认领）
     */
    public record ConversationInfo(String chatId, String title, long lastModified, int messageCount,
                                   boolean mine, boolean ownerless) {
    }
}
