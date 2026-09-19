package com.qian.qianaiagent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 会话文件的互斥执行器 —— JVM 内监视器 + 跨进程 {@link FileLock} 双层保护。
 *
 * <h2>为什么锁的是独立的 {@code .lock} 文件，而不是数据文件本身</h2>
 * <ul>
 *   <li><b>Linux</b>：{@code fcntl}/{@code flock} 的锁挂在 <i>inode</i> 上，而
 *       {@link java.nio.file.Files#move} 的 {@code ATOMIC_MOVE} 走的是 rename —— 目录项指向
 *       新 inode，旧 inode 上的锁随 unlink <b>静默失效</b>，新文件完全裸奔。
 *       锁静默失效比加锁失败难查得多，这是必须锁独立文件最硬的理由。</li>
 *   <li><b>Windows</b>：{@code FileLock} 是强制的，锁住数据文件的字节区间会让别的进程
 *       读同一区间直接失败。</li>
 * </ul>
 *
 * <h2>为什么还要保留 JVM 内的 {@code synchronized}</h2>
 * {@link OverlappingFileLockException} 是 <b>JVM 级、按文件</b>判定的（不是按 channel、
 * 也不是按实例）：同一个 JVM 里对同一文件重复加锁会直接抛。多个
 * {@link FileBasedChatMemory} 实例（{@code @SpringBootTest} 多 context、单测里手 new）
 * 各自持有实例字段的监视器是<b>不共享</b>的，所以监视器注册表必须是 static。
 *
 * <p>另外三条容易踩的细节，都在下面的实现里落实了：
 * <ol>
 *   <li>channel 的 {@code close()} 必须在监视器内 —— JDK 的 lock table 是
 *       {@code channel.close()} 时才释放的，在外层 close 会让下一个进监视器的线程
 *       拿到 {@link OverlappingFileLockException}。</li>
 *   <li>{@link OverlappingFileLockException} 是 {@link RuntimeException}，
 *       {@code catch (IOException)} 抓不住，会击穿整个写路径。</li>
 *   <li>拿不到锁时<b>不执行</b> action，绝不降级成无锁执行 —— 那等于锁白加。</li>
 * </ol>
 */
final class ChatStoreLockManager {

    private static final Logger log = LoggerFactory.getLogger(ChatStoreLockManager.class);

    /**
     * 监视器注册表：key = 规范化绝对路径。
     * <p>
     * <b>static 且永不淘汰</b>。带 LRU 淘汰会让同一个路径出现两个监视器，锁当场失效；
     * 条目数与会话数同阶（每会话至多一个），量级可接受。
     */
    private static final Map<String, Object> MONITORS = new ConcurrentHashMap<>();

    /** 尝试获取跨进程锁的次数与间隔 —— 上限 20 × 50ms ≈ 1s，超时即放弃。 */
    private static final int MAX_ATTEMPTS = 20;
    private static final long RETRY_MILLIS = 50;

    private ChatStoreLockManager() {
    }

    /**
     * 执行结果：{@code acquired=false} 表示没拿到锁、action 未被调用。
     */
    record Locked<T>(boolean acquired, T value) {

        static <T> Locked<T> skipped() {
            return new Locked<>(false, null);
        }

        static <T> Locked<T> ran(T value) {
            return new Locked<>(true, value);
        }
    }

    /**
     * 在双层锁保护下执行 {@code action}。
     *
     * @param lockPath 独立锁文件路径（约定为 {@code {baseDir}/.locks/{chatId}.lock}）
     * @return 拿不到锁时返回 {@code acquired=false}，action 不会被执行
     */
    static <T> Locked<T> withLock(Path lockPath, Supplier<T> action) {
        Object monitor = MONITORS.computeIfAbsent(canonicalKey(lockPath), k -> new Object());
        synchronized (monitor) {
            try {
                Path parent = lockPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
            } catch (IOException e) {
                log.error("创建锁目录失败: {} — {}", lockPath, e.getMessage());
                return Locked.skipped();
            }

            // ⚠️ channel 必须在这个 synchronized 块内关闭（try-with-resources 的作用域就是它）
            try (FileChannel channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {

                FileLock lock = tryLockWithRetry(channel);
                if (lock == null) {
                    log.warn("获取跨进程锁超时（{}ms），跳过本次操作: {}",
                            MAX_ATTEMPTS * RETRY_MILLIS, lockPath);
                    return Locked.skipped();
                }
                try {
                    return Locked.ran(action.get());
                } finally {
                    lock.release();
                }
            } catch (OverlappingFileLockException e) {
                // RuntimeException —— catch (IOException) 抓不住
                log.warn("同 JVM 内重复锁定同一文件，跳过本次操作: {}", lockPath);
                return Locked.skipped();
            } catch (IOException e) {
                log.error("会话文件加锁失败: {} — {}", lockPath, e.getMessage());
                return Locked.skipped();
            }
        }
    }

    private static FileLock tryLockWithRetry(FileChannel channel) throws IOException {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            FileLock lock = channel.tryLock();
            if (lock != null) {
                return lock;
            }
            if (attempt < MAX_ATTEMPTS - 1) {
                try {
                    Thread.sleep(RETRY_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private static String canonicalKey(Path lockPath) {
        try {
            return lockPath.toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return lockPath.toString();
        }
    }
}
