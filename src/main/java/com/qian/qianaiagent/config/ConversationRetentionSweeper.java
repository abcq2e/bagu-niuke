package com.qian.qianaiagent.config;

import com.qian.qianaiagent.interview.progress.ActiveSpecManager;
import com.qian.qianaiagent.memory.FileBasedChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * 会话保留策略清扫器。
 *
 * <h2>为什么是定时任务，而不是在对话结束时顺便清</h2>
 * 把清理挂在 SSE 收尾回调（{@code doOnComplete}）上有四个问题：
 * <ol>
 *   <li><b>边界不止一个</b>：面试对话、复习对话、Agent 对话的 {@code onCompletion}，
 *       外加 Agent 的 {@code onTimeout} —— 漏一处行为就不一致，而超时那条最容易忘。</li>
 *   <li><b>只在用户说话时才触发</b>：攒了几百个会话然后不说话的账号永远清不掉。</li>
 *   <li><b>给流收尾路径加目录扫描延迟</b>，还和同在该回调里的画像保存抢线程。</li>
 *   <li><b>不可单测</b>：埋在 Flux 回调里的逻辑要么起整个上下文，要么测不到。</li>
 * </ol>
 * 反过来，{@link FileBasedChatMemory#enforceRetention} 是纯方法，配合注入的 {@code Clock}
 * 可以直接用 {@code @TempDir} 单测。
 *
 * <h2>默认关闭</h2>
 * 它会<b>删数据</b>，所以 {@code qian.storage.retention.enabled} 默认为 false，
 * 必须显式开启。删除范围也只限于「归属该用户」的会话 —— 无主的历史数据永不自动删。
 */
@Component
@Slf4j
public class ConversationRetentionSweeper {

    private final FileBasedChatMemory memory;
    private final StorageProperties storage;

    /**
     * 会话被清掉后，挂在它下面的「当前项目描述」也得跟着走。
     * <p>
     * {@code data/chat-memory/} 有「每用户最多 N 个会话」的配额，而
     * {@code .active-specs/} 除了 {@link ActiveSpecManager#remove(String)} 之外没有任何
     * 删除路径 —— 不在这里收尾，它就是一份只增不减的孤儿文件。
     */
    private final ActiveSpecManager activeSpecs;

    public ConversationRetentionSweeper(FileBasedChatMemory memory, StorageProperties storage,
                                        ActiveSpecManager activeSpecs) {
        this.memory = memory;
        this.storage = storage;
        this.activeSpecs = activeSpecs;
    }

    /**
     * 按配置间隔清扫。使用 fixedDelay（上一轮跑完再计时），避免清扫变慢时任务堆积重入。
     */
    @Scheduled(fixedDelayString = "${qian.storage.retention.sweep-interval-ms:3600000}",
            initialDelayString = "${qian.storage.retention.initial-delay-ms:60000}")
    public void sweep() {
        StorageProperties.Retention policy = storage.getRetention();
        if (!policy.isEnabled()) {
            log.debug("保留策略未启用，跳过清扫");
            return;
        }

        int maxConversations = policy.getMaxConversationsPerUser();
        int maxAgeDays = policy.getMaxAgeDays();
        Duration grace = Duration.ofMinutes(Math.max(policy.getGracePeriodMinutes(), 0));

        int total = 0;
        for (Long userId : memory.knownOwnerIds()) {
            try {
                total += sweepUser(userId, maxConversations, maxAgeDays, grace);
            } catch (Exception e) {
                // 单个用户清理失败不能中断其余用户
                log.warn("清理用户 {} 的会话失败: {}", userId, e.getMessage());
            }
        }
        if (total > 0) {
            log.info("🧹 保留策略清扫完成: 删除 {} 个会话（上限/用户={}, 最长保留={}天, 宽限={}分钟）",
                    total, maxConversations, maxAgeDays, policy.getGracePeriodMinutes());
        }
    }

    /**
     * 清扫单个用户，并顺手清掉被删会话的项目描述文件。
     * <p>
     * <b>为什么要「先快照再取差集」而不是让 {@code enforceRetention} 返回被删的 chatId：</b>
     * 后者要动 {@link FileBasedChatMemory#enforceRetention} 的返回类型（{@code int} → 集合），
     * 而它有 11 处既有测试断言 {@code int} —— 为一个旁路副作用去翻删除层的抽象，不划算。
     * {@link FileBasedChatMemory#chatIdsOwnedBy(Long)} 本来就是公开的枚举接口，
     * 用它做差集把改动限制在本类内部。
     * <p>
     * 顺序不能反：删完之后再快照，就永远拿不到「删的是谁」了。两次快照都只在
     * 确实删了东西（{@code deleted > 0}）时才多扫一次目录。
     *
     * @return 实际删除的会话数
     */
    private int sweepUser(Long userId, int maxConversations, int maxAgeDays, Duration grace) {
        Set<String> ownedBefore = memory.chatIdsOwnedBy(userId);
        int deleted = memory.enforceRetention(userId, maxConversations, maxAgeDays, grace);
        if (deleted <= 0) {
            return 0;
        }
        ownedBefore.removeAll(memory.chatIdsOwnedBy(userId));
        for (String chatId : ownedBefore) {
            // remove 内部对「文件已不在」是幂等的（deleteIfExists），不需要先判断存在性
            activeSpecs.remove(chatId);
        }
        return deleted;
    }
}
