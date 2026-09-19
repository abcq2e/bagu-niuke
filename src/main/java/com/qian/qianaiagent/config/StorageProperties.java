package com.qian.qianaiagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件存储路径与保留策略配置。
 *
 * <h2>为什么要有这个类</h2>
 * 此前 5 个存储目录各自硬编码在业务类里（{@code System.getProperty("user.dir") + "/data/chat-memory"}
 * 或裸相对路径 {@code Path.of(".quiz-cursor")}），而 {@code user.dir} 是<b>进程工作目录</b> ——
 * 从 IDE 启动、{@code java -jar} 启动、容器里启动，三者可能不同，
 * 历史数据会「凭空消失」且<b>不报任何错</b>，只是文件读不到。
 *
 * <h2>默认行为与改造前逐字节一致</h2>
 * {@code root} 默认就是 {@code user.dir}，各子目录默认值也与原先的硬编码字面量相同，
 * 因此不配置任何属性时解析出的绝对路径完全不变，无需迁移。
 *
 * <h2>⚠️ 生产环境不要设 {@code qian.storage.root}</h2>
 * {@code docker-compose.prod.yml} 把 {@code quiz_cursor:/app/.quiz-cursor} 等挂成 named volume，
 * 一旦把 root 改到别处，挂载点与实际读写路径就分叉了 —— 数据会看起来「凭空消失」。
 * 保持默认即可，compose 里的挂载布局就是按 {@code user.dir} 设计的。
 */
@ConfigurationProperties(prefix = "qian.storage")
public class StorageProperties {

    /** 存储根目录。默认 {@code user.dir}，即改造前的行为。 */
    private String root = System.getProperty("user.dir");

    /** 对话记忆（相对 root） */
    private String chatMemory = "data/chat-memory";

    /** 评估记录（相对 root） */
    private String evals = "data/evals";

    /** 能力画像（相对 root） */
    private String abilityProfiles = ".ability-profiles";

    /** 出题游标（相对 root） */
    private String quizCursor = ".quiz-cursor";

    /** 复习游标（相对 root） */
    private String reviewCursor = ".review-cursor";

    private final Retention retention = new Retention();

    public Path chatMemoryPath() {
        return resolve(chatMemory);
    }

    public Path evalsPath() {
        return resolve(evals);
    }

    public Path abilityProfilesPath() {
        return resolve(abilityProfiles);
    }

    public Path quizCursorPath() {
        return resolve(quizCursor);
    }

    public Path reviewCursorPath() {
        return resolve(reviewCursor);
    }

    private Path resolve(String subDir) {
        return Paths.get(root).resolve(subDir);
    }

    // ===== getter / setter（Spring Boot 绑定需要）=====

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public String getChatMemory() {
        return chatMemory;
    }

    public void setChatMemory(String chatMemory) {
        this.chatMemory = chatMemory;
    }

    public String getEvals() {
        return evals;
    }

    public void setEvals(String evals) {
        this.evals = evals;
    }

    public String getAbilityProfiles() {
        return abilityProfiles;
    }

    public void setAbilityProfiles(String abilityProfiles) {
        this.abilityProfiles = abilityProfiles;
    }

    public String getQuizCursor() {
        return quizCursor;
    }

    public void setQuizCursor(String quizCursor) {
        this.quizCursor = quizCursor;
    }

    public String getReviewCursor() {
        return reviewCursor;
    }

    public void setReviewCursor(String reviewCursor) {
        this.reviewCursor = reviewCursor;
    }

    public Retention getRetention() {
        return retention;
    }

    /**
     * 会话保留策略。
     *
     * <p>默认<b>关闭</b>：清理是会删数据的操作，不该在用户没明确开启时自动跑。
     */
    public static class Retention {

        /** 是否启用自动清理。默认 false —— 删数据必须先显式开启。 */
        private boolean enabled = false;

        /** 每用户最多保留的会话数。<= 0 表示不限制。 */
        private int maxConversationsPerUser = 100;

        /** 最长保留天数。<= 0 表示不按时间限制。 */
        private int maxAgeDays = 0;

        /** 宽限期：最近这么多分钟内修改过的会话永不清理（保护进行中的会话）。 */
        private int gracePeriodMinutes = 60;

        /** 清扫间隔（毫秒），默认 1 小时。 */
        private long sweepIntervalMs = 3_600_000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxConversationsPerUser() {
            return maxConversationsPerUser;
        }

        public void setMaxConversationsPerUser(int maxConversationsPerUser) {
            this.maxConversationsPerUser = maxConversationsPerUser;
        }

        public int getMaxAgeDays() {
            return maxAgeDays;
        }

        public void setMaxAgeDays(int maxAgeDays) {
            this.maxAgeDays = maxAgeDays;
        }

        public int getGracePeriodMinutes() {
            return gracePeriodMinutes;
        }

        public void setGracePeriodMinutes(int gracePeriodMinutes) {
            this.gracePeriodMinutes = gracePeriodMinutes;
        }

        public long getSweepIntervalMs() {
            return sweepIntervalMs;
        }

        public void setSweepIntervalMs(long sweepIntervalMs) {
            this.sweepIntervalMs = sweepIntervalMs;
        }
    }
}
