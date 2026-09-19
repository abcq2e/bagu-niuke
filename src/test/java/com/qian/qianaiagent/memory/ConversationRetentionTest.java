package com.qian.qianaiagent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 保留策略的删除边界。
 *
 * <p>这里最重要的不是「能不能删」，而是<b>绝不能删什么</b> —— 无主的历史数据和别人的会话
 * 一旦被误删不可恢复。所以每个「该删」的用例都配了「不该删」的反向断言。
 *
 * <p>mtime 一律显式设置：快速连续创建的文件在同一毫秒内落盘时
 * {@code File.lastModified()} 相同，按 mtime 排序的结果会不确定。
 */
class ConversationRetentionTest {

    @TempDir
    Path tempDir;

    /** 一个「很久以前」的基准时间，确保所有造出来的会话都远在宽限期之外 */
    private static final long OLD = 1_600_000_000_000L;

    private FileBasedChatMemory memory() {
        // 时钟拨到真实当前时间：mtime 设在 OLD（2020 年）自然超出宽限期
        return new FileBasedChatMemory(tempDir.toString(), Clock.systemDefaultZone());
    }

    /** 造一个属于 owner 的会话，并把 mtime 固定成给定值 */
    private void conversation(FileBasedChatMemory memory, String chatId, Long owner,
                              long epochMillis) throws IOException {
        memory.startSession(chatId, owner);
        memory.add(chatId, List.of(new UserMessage("问"), new org.springframework.ai.chat.messages.AssistantMessage("答")));
        Files.setLastModifiedTime(tempDir.resolve(chatId + ".json"), FileTime.fromMillis(epochMillis));
    }

    /** 造一个无主的 legacy 会话 */
    private void legacy(FileBasedChatMemory memory, String chatId, long epochMillis) throws IOException {
        memory.add(chatId, List.of(new UserMessage("旧数据")));
        Files.setLastModifiedTime(tempDir.resolve(chatId + ".json"), FileTime.fromMillis(epochMillis));
    }

    private Set<String> remainingChatIds() throws IOException {
        try (var stream = Files.list(tempDir)) {
            return stream.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .collect(Collectors.toSet());
        }
    }

    private static Duration noGrace() {
        return Duration.ZERO;
    }

    // ===== 该删的 =====

    @Test
    void keepsNewestNByLastModified() throws Exception {
        FileBasedChatMemory memory = memory();
        for (int i = 0; i < 5; i++) {
            conversation(memory, "chat_" + i, 7L, OLD + i * 1000L);
        }

        int deleted = memory.enforceRetention(7L, 2, 0, Duration.ofMinutes(60));

        assertEquals(3, deleted);
        assertEquals(Set.of("chat_3", "chat_4"), remainingChatIds(), "应保留 mtime 最新的两个");
    }

    @Test
    void maxAgeDaysDeletesEvenWhenUnderQuota() throws Exception {
        FileBasedChatMemory memory = memory();
        conversation(memory, "chat_ancient", 7L, OLD);                 // 2020 年
        conversation(memory, "chat_recent", 7L, System.currentTimeMillis() - 60_000L);

        // 配额足够大，但有一条超过保留天数
        int deleted = memory.enforceRetention(7L, 100, 30, Duration.ofMinutes(60));

        assertEquals(1, deleted);
        assertEquals(Set.of("chat_recent"), remainingChatIds());
    }

    @Test
    void deletesSidecarsButNotLockFile() throws Exception {
        FileBasedChatMemory memory = memory();
        conversation(memory, "chat_0", 7L, OLD);
        memory.updateTitle("chat_0", "标题");
        // 触发一次加锁，确保 .lock 文件已被创建
        memory.get("chat_0");
        assertTrue(Files.exists(tempDir.resolve(".locks").resolve("chat_0.lock")),
                "前置：锁文件应已存在");

        assertEquals(1, memory.enforceRetention(7L, 0, 30, Duration.ofMinutes(60)));

        assertFalse(Files.exists(tempDir.resolve("chat_0.json")), ".json 应被删除");
        assertFalse(Files.exists(tempDir.resolve("chat_0.title")), ".title 应被删除");
        assertFalse(Files.exists(tempDir.resolve("chat_0.owner")), ".owner 应被删除");
        // 🔴 锁文件不删：Windows 上删除被持有的锁文件必定失败，只会刷日志
        assertTrue(Files.exists(tempDir.resolve(".locks").resolve("chat_0.lock")),
                "锁文件不应被删除");
    }

    // ===== 绝不能删的 =====

    @Test
    void neverTouchesOtherUsersConversations() throws Exception {
        FileBasedChatMemory memory = memory();
        for (int i = 0; i < 5; i++) {
            conversation(memory, "mine_" + i, 7L, OLD + i * 1000L);
        }
        for (int i = 0; i < 3; i++) {
            conversation(memory, "theirs_" + i, 9L, OLD + i * 1000L);
        }

        memory.enforceRetention(7L, 2, 0, noGrace());

        assertEquals(Set.of("mine_3", "mine_4", "theirs_0", "theirs_1", "theirs_2"),
                remainingChatIds(), "别人的会话一个都不能动");
        for (int i = 0; i < 3; i++) {
            assertEquals(9L, memory.ownerOf("theirs_" + i));
        }
    }

    @Test
    void neverTouchesOwnerlessLegacyData() throws Exception {
        FileBasedChatMemory memory = memory();
        for (int i = 0; i < 4; i++) {
            legacy(memory, "legacy_" + i, OLD + i * 1000L);
        }
        conversation(memory, "mine_0", 7L, OLD);

        int deleted = memory.enforceRetention(7L, 0, 30, noGrace());

        assertEquals(1, deleted, "只应删掉属于该用户的那条");
        // 🔴 少了这条约束，第一次清扫就会把全部历史数据抹掉且不可恢复
        assertTrue(remainingChatIds().containsAll(Set.of("legacy_0", "legacy_1", "legacy_2", "legacy_3")),
                "无主 legacy 数据永不自动删除");
    }

    @Test
    void gracePeriodProtectsRecentlyModifiedConversations() throws Exception {
        FileBasedChatMemory memory = memory();
        for (int i = 0; i < 5; i++) {
            conversation(memory, "chat_" + i, 7L, System.currentTimeMillis() - i * 1000L);
        }

        // 配额 0（即全部超出），但都在 60 分钟宽限期内 —— 保护进行中的会话
        int deleted = memory.enforceRetention(7L, 0, 0, Duration.ofMinutes(60));

        assertEquals(0, deleted, "宽限期内不应删除任何会话");
        assertEquals(5, remainingChatIds().size(), "宽限期内不应删除任何会话");
    }

    @Test
    void noOpWhenUnderQuota() throws Exception {
        FileBasedChatMemory memory = memory();
        conversation(memory, "chat_0", 7L, OLD);
        conversation(memory, "chat_1", 7L, OLD + 1000L);

        assertEquals(0, memory.enforceRetention(7L, 100, 0, noGrace()));
        assertEquals(2, remainingChatIds().size());
    }

    @Test
    void noOpWhenPolicyDisabled() throws Exception {
        FileBasedChatMemory memory = memory();
        conversation(memory, "chat_0", 7L, OLD);

        assertEquals(0, memory.enforceRetention(7L, 0, 0, noGrace()), "上限为 0 且不限天数 = 不删");
        assertEquals(1, remainingChatIds().size());
        assertEquals(0, memory.enforceRetention(null, 0, 0, noGrace()), "无 userId 不删");
    }

    // ===== 锁不可用 =====

    @Test
    void skipsInsteadOfCrashingWhenLockIsAlreadyHeld() throws Exception {
        FileBasedChatMemory memory = memory();
        conversation(memory, "chat_locked", 7L, OLD);

        Path lockFile = tempDir.resolve(".locks").resolve("chat_locked.lock");
        Files.createDirectories(lockFile.getParent());

        // 绕过锁管理器直接持有 FileLock：同 JVM 再次 tryLock 会抛
        // OverlappingFileLockException —— 它是 RuntimeException，catch(IOException) 抓不住，
        // 若没被正确处理会击穿整个清扫流程
        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock held = channel.lock()) {

            int deleted = memory.enforceRetention(7L, 0, 30, noGrace());

            assertEquals(0, deleted, "拿不到锁应跳过，而不是强删或抛异常");
            assertTrue(Files.exists(tempDir.resolve("chat_locked.json")), "文件应原样保留");
        }
    }

    // ===== 时钟注入 =====

    @Test
    void graceIsEvaluatedAgainstInjectedClock() throws Exception {
        // 把时钟拨到一天后：2020 年的文件自然远超 60 分钟宽限期
        Clock tomorrow = Clock.offset(Clock.systemDefaultZone(), Duration.ofDays(1));
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString(), tomorrow);
        conversation(memory, "chat_old", 7L, OLD);

        assertEquals(1, memory.enforceRetention(7L, 0, 30,
                Duration.ofMinutes(60)), "相对注入的时钟已过宽限期，应删除");
    }

    @Test
    void unknownOwnerIdsComeFromDisk() throws Exception {
        FileBasedChatMemory memory = memory();
        conversation(memory, "a", 7L, OLD);
        conversation(memory, "b", 9L, OLD);
        legacy(memory, "c", OLD);

        // 无主会话不产生 owner，因此不在结果里（它们也永不自动删）
        assertEquals(Set.of(7L, 9L), memory.knownOwnerIds());
    }
}
