package com.qian.qianaiagent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发写入与跨实例互斥。
 *
 * <p>这些是唯一能真正压到 {@link ChatStoreLockManager} 的用例：多个 {@code FileBasedChatMemory}
 * 实例指向同一目录时，实例字段的监视器是不共享的 —— 如果注册表不是 static，
 * 这里会直接抛 {@code OverlappingFileLockException}。
 */
class ChatStoreLockManagerTest {

    @TempDir
    Path tempDir;

    /**
     * 两个实例 × 多线程并发写同一会话，一条都不能丢。
     *
     * <p>{@code add} 是「读全量 → 追加 → 全量重写」，没有互斥时并发写会互相覆盖。
     */
    @Test
    void concurrentAddsFromTwoInstancesLoseNoMessages() throws Exception {
        FileBasedChatMemory a = new FileBasedChatMemory(tempDir.toString());
        FileBasedChatMemory b = new FileBasedChatMemory(tempDir.toString());
        String chatId = "chat_concurrent";

        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        try {
            for (int i = 0; i < threads; i++) {
                int idx = i;
                FileBasedChatMemory target = (idx % 2 == 0) ? a : b;
                pool.submit(() -> {
                    try {
                        start.await();
                        target.add(chatId, List.of(new UserMessage("m" + idx)));
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS), "并发写入应在超时前完成");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, failures.get(), "并发写入不应抛异常（OverlappingFileLockException 是 RuntimeException）");
        assertEquals(threads, a.get(chatId).size(),
                "32 次追加后必须恰好 32 条 —— 少了说明发生了丢更新");
    }

    /**
     * 写的同时持续读，永远不能读到半截或空文件。
     *
     * <p>原子替换保证读者看到的要么是旧内容要么是新内容；同时 {@code get} 也在锁内，
     * 避免 Windows 上「替换被打开读的文件」抛 AccessDeniedException 导致写入静默丢失。
     */
    @Test
    void readersNeverObserveTornWrites() throws Exception {
        FileBasedChatMemory writer = new FileBasedChatMemory(tempDir.toString());
        FileBasedChatMemory reader = new FileBasedChatMemory(tempDir.toString());
        String chatId = "chat_tearing";

        writer.add(chatId, List.of(new UserMessage("seed"), new AssistantMessage("seed-a")));

        AtomicInteger tornReads = new AtomicInteger();
        AtomicInteger reads = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch stop = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);

        try {
            pool.submit(() -> {
                try {
                    while (stop.getCount() > 0) {
                        List<Message> messages = reader.get(chatId);
                        int size = messages.size();
                        // 每次写入都是成对追加（问 + 答），所以条数应恒为偶数且 ≥ 2
                        if (size == 0 || size % 2 != 0) {
                            tornReads.incrementAndGet();
                        }
                        reads.incrementAndGet();
                    }
                } catch (Exception e) {
                    tornReads.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });

            for (int i = 0; i < 30; i++) {
                writer.add(chatId, List.of(new UserMessage("q" + i), new AssistantMessage("a" + i)));
            }
            stop.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "读线程应在超时前结束");
        } finally {
            pool.shutdownNow();
        }

        assertTrue(reads.get() > 0, "前置：应发生了若干次读取");
        assertEquals(0, tornReads.get(), "不应读到半截或空文件（读取 " + reads.get() + " 次）");
        assertEquals(62, writer.get(chatId).size());
    }

    /** 拿不到锁时返回 acquired=false 且不执行 action，绝不降级成无锁执行。 */
    @Test
    void lockOutcomeReportsSkipped() throws Exception {
        Path lockPath = tempDir.resolve("x.lock");
        List<String> sideEffects = new ArrayList<>();

        ChatStoreLockManager.Locked<String> ran =
                ChatStoreLockManager.withLock(lockPath, () -> {
                    sideEffects.add("ran");
                    return "ok";
                });

        assertTrue(ran.acquired());
        assertEquals("ok", ran.value());
        assertEquals(List.of("ran"), sideEffects);
    }
}
