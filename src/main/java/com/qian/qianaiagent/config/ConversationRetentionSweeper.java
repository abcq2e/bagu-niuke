package com.qian.qianaiagent.config;

import com.qian.qianaiagent.memory.FileBasedChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

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

    public ConversationRetentionSweeper(FileBasedChatMemory memory, StorageProperties storage) {
        this.memory = memory;
        this.storage = storage;
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
                total += memory.enforceRetention(userId, maxConversations, maxAgeDays, grace);
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
}
