package com.qian.qianaiagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对话记忆窗口配置。
 *
 * <p>默认值与改造前逐字节一致（20 条），因此不配置任何属性时行为不变。
 * token 预算默认 8000，{@code <= 0} 表示关闭该维度、退回按条数裁剪。
 *
 * <h2>为什么需要 token 预算</h2>
 * 只按条数裁剪是不够的：一条超长的 RAG 检索结果（8000 字）就足以把上下文打爆，
 * 而消息条数可能只有 3 条 —— 条数阈值根本拦不住它。
 */
@ConfigurationProperties(prefix = "qian.memory")
public class MemoryProperties {

    private final Window window = new Window();

    public Window getWindow() {
        return window;
    }

    /** 记忆窗口。 */
    public static class Window {

        /** 最大保留消息条数。 */
        private int maxMessages = 20;

        /** 最大估算 token 数，{@code <= 0} 表示不限制。 */
        private int maxTokens = 8000;

        public int getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        /** 单条消息最大字符数，超出部分截断。<= 0 表示不限制。 */
        private int maxCharsPerMessage = 12000;

        public int getMaxCharsPerMessage() {
            return maxCharsPerMessage;
        }

        public void setMaxCharsPerMessage(int maxCharsPerMessage) {
            this.maxCharsPerMessage = maxCharsPerMessage;
        }
    }
}
