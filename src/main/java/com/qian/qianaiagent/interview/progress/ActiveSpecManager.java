package com.qian.qianaiagent.interview.progress;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.qian.qianaiagent.config.StorageProperties;
import com.qian.qianaiagent.util.ChatIdValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 当前有效项目描述管理器（覆盖式 vs 对话历史的追加式）
 * <p>
 * 职责：
 * <ul>
 *   <li>每个会话独立维护"当前生效的项目描述"</li>
 *   <li>覆盖语义 —— {@code updateSpec()} 会自动覆盖旧值，而非追加</li>
 *   <li>构建用于注入 System Prompt 的内容块，明确告诉 LLM"以此为准"</li>
 * </ul>
 * <p>
 * 设计原则：
 * <ul>
 *   <li>落盘持久化 —— 服务重启后仍能恢复「当前项目描述」，避免面试官失忆</li>
 *   <li>与 ChatMemory 分离 —— ActiveSpec 管"当前是什么"，ChatMemory 管"聊过什么"</li>
 *   <li>不重置已考题指纹 —— 用户答过的知识点即使换项目也视为已掌握</li>
 * </ul>
 */
@Slf4j
@Component
public class ActiveSpecManager {

    /** 每个会话 -> 当前生效的项目描述 + 最后访问时间 */
    private final Map<String, Entry> activeSpecs = new ConcurrentHashMap<>();

    /**
     * 统一存储路径配置。
     * <p>
     * 此前本类<b>不做持久化</b>（见类注释），导致服务重启后面试官忘记候选人项目背景 ——
     * 用户可直接感知的功能缺陷。改为落盘后，该注释已同步更新。
     */
    @Resource
    private StorageProperties storage;

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private Path specDir;

    @PostConstruct
    public void init() {
        specDir = storage.activeSpecPath();
        try {
            Files.createDirectories(specDir);
        } catch (IOException e) {
            log.warn("无法创建项目描述目录: {}", e.getMessage());
        }
    }

    /**
     * 空闲多久后<b>从内存缓存逐出</b>。对齐 QuizApp.evictExpiredEntries 的既有口径（2 小时）。
     * <p>
     * ⚠️ 这里不是「清理/删除」：落盘之后磁盘才是真相源，被逐出的条目下次
     * {@link #getSpec(String)} 未命中时会由 {@link #loadSpec(String)} 原样回填，
     * 用户不会因为 2 小时没说话就丢掉项目描述。<b>这是刻意的</b> ——
     * 做了持久化之后，TTL 的职责就只剩「别让内存里的 {@code Map} 无界增长」。
     * <p>
     * 真正的删除只有两条路径：{@link #remove(String)}（用户删会话）与
     * {@code ConversationRetentionSweeper}（保留策略清会话时同步清理）。
     */
    private static final Duration IDLE_TTL = Duration.ofHours(2);

    /**
     * 项目描述 + 最后访问时间。
     *
     * <p>⚠️ {@code lastAccess} <b>只活在内存里</b>：{@link #touch(String, Entry)}
     * 从不回写磁盘，{@link #saveSpec(String)} 只在 {@code updateSpec} 时被调用。
     * 所以盘上序列化的 {@code lastAccess} 会<b>永久冻结</b>在最后一次 {@code updateSpec} 的时刻。
     * <p>
     * 后果：它<b>不是</b>一个可用的 TTL 时钟。服务重启后回填的条目，其 {@code lastAccess}
     * 是陈旧的写入时间 —— 可能立刻满足 {@code lastAccess < cutoff} 而被逐出。这不影响正确性
     * （逐出只是丢内存缓存，下次读照样回填），但别拿它做「多久没被访问」的统计或判定。
     */
    private record Entry(String spec, long lastAccess) {
    }

    /**
     * 更新或覆盖当前项目描述（天然覆盖语义）
     *
     * @param chatId 会话 ID
     * @param spec   完整项目描述文本
     */
    public void updateSpec(String chatId, String spec) {
        if (chatId == null || spec == null || spec.isBlank()) {
            return;
        }
        Entry old = activeSpecs.put(chatId, new Entry(spec.trim(), System.currentTimeMillis()));
        if (old != null) {
            log.info("🔄 项目描述已覆盖: chatId={}, oldLen={}, newLen={}",
                    chatId, old.spec().length(), spec.length());
        } else {
            log.info("📋 新项目描述已设置: chatId={}, specLen={}", chatId, spec.length());
        }
        saveSpec(chatId);
    }

    /**
     * 获取当前项目描述，用于场景不关心是否有描述时
     */
    public String getSpec(String chatId) {
        Entry entry = activeSpecs.get(chatId);
        if (entry == null) {
            // 内存未命中 —— 可能是服务刚重启，尝试从磁盘恢复
            entry = loadSpec(chatId);
            if (entry == null) {
                return null;
            }
            activeSpecs.put(chatId, entry);
            log.info("📂 磁盘加载项目描述: chatId={}, specLen={}", chatId, entry.spec().length());
        }
        touch(chatId, entry);
        return entry.spec();
    }

    /**
     * 续期 —— 被访问过就不算空闲。
     * <p>
     * ⚠️ 只改内存，<b>不落盘</b>：每次读都写一次磁盘会让热路径付 IO 代价，不划算。
     * 代价是盘上的 {@code lastAccess} 不随访问前进（详见 {@link Entry} 的说明）。
     */
    private void touch(String chatId, Entry entry) {
        activeSpecs.replace(chatId, entry, new Entry(entry.spec(), System.currentTimeMillis()));
    }

    /**
     * 构建注入 System Prompt 的内容块
     * <p>
     * 返回格式（注意前后空行分隔，便于 LLM 识别边界）：
     * <pre>
     * 📋 【当前项目描述】（唯一有效版本，以此为准）
     * 对话历史中的旧项目描述已过时，请完全忽略。
     * 用户之前回答过的知识点仍视为已掌握，禁止重复出题。
     *
     * [实际描述文本]
     * </pre>
     *
     * @return 空字符串表示无可注入内容
     */
    public String buildSpecPrompt(String chatId) {
        String spec = getSpec(chatId);
        if (spec == null || spec.isBlank()) {
            return "";
        }
        return """

                📋 【当前项目描述】（唯一有效版本）
                以下为用户最新的项目描述，对话历史中的旧项目描述已过时，请完全忽略。
                但用户之前回答过的知识点仍视为已掌握，禁止重复出题。

                %s
                """.formatted(spec);
    }

    /**
     * 粗略检测用户消息是否在描述/更新项目
     * <p>
     * 启发式规则 —— 匹配常见的"陈述项目"句式。误判率很低但允许：
     * - 误判：一条非项目消息被当成项目描述 → 只是覆盖了旧值，不产生副作用
     * - 漏判：用户用非标准句式更新项目 → 旧描述保留，不会丢失信息
     */
    public boolean isProjectDescription(String message) {
        if (message == null || message.isBlank()) return false;
        String m = message.trim();

        // 常见项目描述开头句式
        String[] patterns = {
                "我的项目是", "我的项目", "我做的项目是", "我做的项目",
                "我最近在", "我正在做", "我目前在",
                "项目是", "项目描述",
                "我的项目改成", "项目改为", "项目变更为",
                "我之前做", "我以前做",
                "我负责的项目", "我参与的项目",
                "介绍一下我的项目", "说下我的项目",
                "我过去做",
        };
        for (String p : patterns) {
            if (m.startsWith(p)) return true;
        }
        return false;
    }

    /**
     * 判断指定会话是否有有效项目描述。
     * <p>
     * ⚠️ <b>只查内存缓存，不回落磁盘</b> —— 与 {@link #getSpec(String)} 不对称：
     * 服务刚重启、条目还没被读过时，这里会返回 {@code false}，而 {@code getSpec} 能拿到值。
     * 需要「磁盘也算数」的语义请直接用 {@code getSpec(chatId) != null}。
     * <p>
     * 故意保持这个行为：改成回落磁盘会让「本次进程内是否已加载」这个既有语义变化，
     * 而现有调用方（测试）依赖的正是前者。
     */
    public boolean hasSpec(String chatId) {
        Entry entry = activeSpecs.get(chatId);
        return entry != null && !entry.spec().isBlank();
    }

    /**
     * 清理会话（会话删除时调用）
     * <p>
     * 内存与磁盘<b>一并</b>清除。只清内存是不够的：磁盘文件还在时，后续任何
     * {@link #getSpec(String)} 都会走 {@link #loadSpec(String)} 把它「复活」——
     * 用户删了会话，项目描述却又回来了；而 {@code .active-specs/} 没有配额兜底
     * （{@code evictExpired} 只逐出内存缓存），漏删就会长期无界增长。
     * <p>
     * 因此这里做成「谁删会话谁负责调」：目前仅有的两条会话删除路径 ——
     * 用户删会话、以及 {@code ConversationRetentionSweeper} 的保留策略 —— 都必须调它。
     */
    public void remove(String chatId) {
        activeSpecs.remove(chatId);
        deleteSpecFiles(chatId);
        log.info("🗑️ 已清除项目描述: chatId={}", chatId);
    }

    /**
     * 逐出空闲超过 {@code ttl} 的<b>内存缓存条目</b>（磁盘文件原样保留）。
     * <p>
     * 传的是 {@code activeSpecs}（内存 Map）而不是 {@link #remove(String)}，差别就在这里：
     * 磁盘是真相源，被逐出的条目下次 {@link #getSpec(String)} 会由
     * {@link #loadSpec(String)} 回填 —— 所以「逐出」不丢数据，只是释放内存。
     * <p>
     * 抽成纯方法（注入 {@code now}）以便单测，<b>不</b>把 {@code System.currentTimeMillis()}
     * 写死在内部 —— 那会让 2 小时的等待变成测试不可承受的成本。
     *
     * @return 实际逐出的条目数
     */
    public int evictExpired(long now, Duration ttl) {
        if (ttl == null || ttl.isNegative()) {
            return 0;
        }
        long cutoff = now - ttl.toMillis();
        List<String> expired = activeSpecs.entrySet().stream()
                .filter(e -> e.getValue().lastAccess() < cutoff)
                .map(Map.Entry::getKey)
                .toList();
        expired.forEach(activeSpecs::remove);
        if (!expired.isEmpty()) {
            // 用「逐出」而不是「清理」：磁盘文件没动，下次 getSpec 会回填。
            // 写成「清理」会让排障的人以为数据被删了（本项目就踩过这个措辞的坑）
            log.info("🧹 逐出空闲项目描述内存缓存 {} 个（磁盘文件保留，下次读取回填）", expired.size());
        }
        return expired.size();
    }

    /** 定时逐出空闲条目。间隔可配，默认 30 分钟。 */
    @Scheduled(fixedDelayString = "${qian.memory.active-spec.evict-interval-ms:1800000}")
    public void evictExpiredScheduled() {
        evictExpired(System.currentTimeMillis(), IDLE_TTL);
    }

    // ===== 持久化 =====

    /**
     * 原子写盘：先写临时文件再 {@code ATOMIC_MOVE}。
     *
     * <p>不沿用 {@code SequentialRotationService.saveCursor} 的直接覆盖写 ——
     * 那是既有的非原子写（写到一半崩溃会留下截断的 JSON），本类不扩散该模式。
     */
    private void saveSpec(String chatId) {
        Entry entry = activeSpecs.get(chatId);
        if (entry == null || specDir == null) {
            return;
        }
        Path file = fileOf(chatId);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            mapper.writeValue(tmp.toFile(), entry);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // 少数文件系统不支持原子移动，退化为普通替换（仍是先写临时文件）
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // error 而非 warn：这不是「缓存未命中」那类可容忍的降级 ——
            // 用户刚更新了项目描述，但没落盘，下次重启就失忆（本类落盘要修的原始缺陷）。
            // loadSpec/deleteSpecFiles 仍用 warn，它们的容错语义是对的。
            log.error("保存项目描述失败: chatId={}, err={}", chatId, e.getMessage());
        }
    }

    private Entry loadSpec(String chatId) {
        if (specDir == null) {
            return null;
        }
        Path file = fileOf(chatId);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return mapper.readValue(file.toFile(), Entry.class);
        } catch (IOException e) {
            log.warn("加载项目描述失败: chatId={}, err={}", chatId, e.getMessage());
            return null;
        }
    }

    /**
     * 删除会话对应的描述文件，以及 {@link #saveSpec(String)} 可能残留的
     * {@code .tmp}（那次 {@code move} 失败过的话，临时文件会留在盘上）。
     * <p>
     * {@code specDir == null}（未初始化）时静默跳过，与 {@code saveSpec}/{@code loadSpec}
     * 同口径；{@code IOException} 只记 warn 不抛 —— 删会话不该因为一个描述文件删不掉而失败。
     */
    private void deleteSpecFiles(String chatId) {
        if (specDir == null) {
            return;
        }
        Path file = fileOf(chatId);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        for (Path path : List.of(file, tmp)) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("删除项目描述文件失败: chatId={}, path={}, err={}",
                        chatId, path, e.getMessage());
            }
        }
    }

    /**
     * 文件名用 {@link ChatIdValidator#safeFileName} 净化 —— 与
     * {@code .quiz-cursor/}、{@code .review-cursor/} 同模式，chatId 由客户端提供，
     * 不净化就能靠 {@code ../} 写到目录外。
     */
    private Path fileOf(String chatId) {
        return specDir.resolve(ChatIdValidator.safeFileName(chatId) + ".json");
    }
}
