package com.qian.qianaiagent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话归属（owner 旁路文件）的行为契约。
 *
 * <p>背景：此前 {@code ConversationController} / {@code ExportController} 只有路径遍历校验，
 * 没有任何归属判断 —— 任何登录用户传别人的 chatId 就能读改删对方的记录，
 * 而 {@code /ai/conversations} 更是直接枚举所有人的会话。
 */
class ConversationOwnershipTest {

    @TempDir
    Path tempDir;

    private FileBasedChatMemory memory() {
        return new FileBasedChatMemory(tempDir.toString());
    }

    /** 造一个「有数据但无主」的 legacy 会话，模拟历史数据 */
    private void legacyConversation(FileBasedChatMemory memory, String chatId) {
        memory.add(chatId, List.of(new UserMessage("历史提问"), new AssistantMessage("历史回答")));
    }

    // ===== 三种访问语义 =====

    @Test
    void startSessionClaimsNewConversation() {
        FileBasedChatMemory memory = memory();
        assertTrue(memory.startSession("chat_new", 7L), "新会话应允许建立");
        assertEquals(7L, memory.ownerOf("chat_new"), "建立即归属创建者");
    }

    @Test
    void startSessionRejectsAnotherUser() {
        FileBasedChatMemory memory = memory();
        memory.startSession("chat_new", 7L);
        assertFalse(memory.startSession("chat_new", 9L), "已归属他人的会话应拒绝接入");
        assertEquals(7L, memory.ownerOf("chat_new"), "被拒绝的请求不得改变归属");
    }

    @Test
    void accessConversationAdoptsOwnerlessLegacyData() {
        FileBasedChatMemory memory = memory();
        legacyConversation(memory, "chat_legacy");
        assertNull(memory.ownerOf("chat_legacy"), "前置：旧数据应无主");

        assertTrue(memory.accessConversation("chat_legacy", 7L), "首次访问应认领");
        assertEquals(7L, memory.ownerOf("chat_legacy"));
    }

    @Test
    void accessConversationRejectsSecondUserAfterAdoption() {
        FileBasedChatMemory memory = memory();
        legacyConversation(memory, "chat_legacy");
        memory.accessConversation("chat_legacy", 7L);

        assertFalse(memory.accessConversation("chat_legacy", 9L), "认领后第二人应被拒");
        assertEquals(7L, memory.ownerOf("chat_legacy"), "归属不得被第二人改写");
    }

    @Test
    void accessConversationDoesNotClaimNonexistentData() {
        FileBasedChatMemory memory = memory();
        // 🔴 反预抢占：聊天 ID 可能是可预测的时间戳，若无条件认领，
        // 攻击者能对任意未来的 chatId 提前占坑
        assertTrue(memory.accessConversation("chat_ghost", 7L), "无数据的会话按可访问处理");
        assertNull(memory.ownerOf("chat_ghost"), "无数据时绝不能写下归属");
        assertFalse(Files.exists(tempDir.resolve("chat_ghost.owner")));
    }

    @Test
    void canManageIsStrictAndNeverAdopts() {
        FileBasedChatMemory memory = memory();
        legacyConversation(memory, "chat_legacy");

        assertFalse(memory.canManage("chat_legacy", 7L), "无主会话不能被 manage：否则删除会成为认领旁路");
        assertNull(memory.ownerOf("chat_legacy"), "manage 不得认领");

        memory.accessConversation("chat_legacy", 7L);   // 先打开（认领），才能管理
        assertTrue(memory.canManage("chat_legacy", 7L));
        assertFalse(memory.canManage("chat_legacy", 9L));
        assertFalse(memory.canManage("chat_legacy", null));
    }

    // ===== 列表 =====

    @Test
    void listReturnsMineAndOwnerlessButNotOthers() {
        FileBasedChatMemory memory = memory();
        memory.startSession("chat_mine", 7L);
        memory.add("chat_mine", List.of(new UserMessage("u")));

        legacyConversation(memory, "chat_legacy");          // 无主
        memory.startSession("chat_other", 9L);
        memory.add("chat_other", List.of(new UserMessage("secret of user 9")));

        Set<String> visible = memory.listConversations(7L).stream()
                .map(FileBasedChatMemory.ConversationInfo::chatId)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.of("chat_mine", "chat_legacy"), visible,
                "只能看到自己的 + 无主的；别人的会话不得出现在列表里");
    }

    @Test
    void listNeverAdoptsOwnerlessConversations() {
        FileBasedChatMemory memory = memory();
        legacyConversation(memory, "chat_legacy");

        memory.listConversations(7L);
        memory.listConversations(9L);

        // 🔴 若列表会认领，第一个打开侧边栏的人就把所有旧会话据为己有
        assertNull(memory.ownerOf("chat_legacy"), "列表接口绝不能认领");
        assertFalse(Files.exists(tempDir.resolve("chat_legacy.owner")));
    }

    // ===== 并发认领 =====

    @Test
    void concurrentAdoptionAcrossInstancesHasExactlyOneWinner() throws Exception {
        legacyConversation(memory(), "chat_race");

        // 🔴 两个独立实例指向同一目录：监视器注册表若是实例字段就会各自为政，
        // 这里同时压到 JVM 内监视器和跨进程 FileLock
        FileBasedChatMemory a = memory();
        FileBasedChatMemory b = memory();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Set<Long> winners = ConcurrentHashMap.newKeySet();

        try {
            for (int i = 0; i < threads; i++) {
                long userId = 100L + i;
                FileBasedChatMemory target = (i % 2 == 0) ? a : b;
                pool.submit(() -> {
                    try {
                        start.await();
                        if (target.accessConversation("chat_race", userId)) {
                            winners.add(userId);
                        }
                    } catch (Exception e) {
                        // 断言在下面统一做，这里不能让异常静默吞掉线程
                        winners.add(-1L);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "并发认领应在超时前完成");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, winners.size(), "恰好一个赢家，实际: " + winners);
        assertTrue(winners.iterator().next() > 0, "不应出现异常");
        assertEquals(winners.iterator().next(), memory().ownerOf("chat_race"),
                "落盘归属必须与唯一赢家一致");
    }

    // ===== 生命周期 =====

    @Test
    void deleteRemovesOwnerButClearKeepsIt() throws Exception {
        FileBasedChatMemory memory = memory();

        memory.startSession("chat_a", 7L);
        memory.add("chat_a", List.of(new UserMessage("u")));
        memory.clear("chat_a");
        // 🔴 清空记忆 ≠ 删除会话：归属要留着，否则重置后会话变无主会被他人认领
        assertTrue(Files.exists(tempDir.resolve("chat_a.owner")), "clear 必须保留 .owner");
        assertEquals(7L, memory.ownerOf("chat_a"));

        memory.startSession("chat_b", 7L);
        memory.add("chat_b", List.of(new UserMessage("u")));
        assertTrue(memory.deleteConversation("chat_b"));
        assertFalse(Files.exists(tempDir.resolve("chat_b.owner")), "删除必须连带清掉 .owner");
        assertFalse(Files.exists(tempDir.resolve("chat_b.json")));
    }

    // ===== 脏数据 =====

    @Test
    void malformedOwnerFileIsTreatedAsOwnerless() throws Exception {
        FileBasedChatMemory memory = memory();
        legacyConversation(memory, "chat_bad");

        for (String junk : List.of("", "   ", "abc", "12.5", "-", "9223372036854775808")) {
            Files.writeString(tempDir.resolve("chat_bad.owner"), junk);
            assertNull(memory.ownerOf("chat_bad"), "非法 .owner 内容应按无主处理: [" + junk + "]");
            // 无主 → 可被认领，且认领会覆盖掉脏内容
            assertTrue(memory.accessConversation("chat_bad", 7L));
            assertEquals(7L, memory.ownerOf("chat_bad"));
            assertTrue(memory.deleteConversation("chat_bad"));
            legacyConversation(memory, "chat_bad");
        }
    }

    @Test
    void ownerFileToleratesSurroundingWhitespace() throws Exception {
        FileBasedChatMemory memory = memory();
        legacyConversation(memory, "chat_ws");
        // 人工编辑 .owner 常会带上行尾换行，不该因此判定为损坏
        Files.writeString(tempDir.resolve("chat_ws.owner"), "  7\n");
        assertEquals(7L, memory.ownerOf("chat_ws"));
    }

    @Test
    void unsafeChatIdRejectedByEveryOwnershipEntryPoint() {
        FileBasedChatMemory memory = memory();
        for (String bad : List.of("../evil", "a/b", "a\\b", "")) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> memory.startSession(bad, 7L), "startSession 应拒绝: " + bad);
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> memory.accessConversation(bad, 7L), "accessConversation 应拒绝: " + bad);
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> memory.ownerOf(bad), "ownerOf 应拒绝: " + bad);
        }
    }
}
