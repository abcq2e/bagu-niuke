package com.qian.qianaiagent.chatmemory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 带自动摘要的对话记忆 wrapper
 * <p>
 * 包装 {@link FileBasedChatMemory}，在消息超过窗口上限时自动调用 LLM
 * 将早期对话压缩为摘要，与最近 N 轮原文一起返回给调用方。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>存储层不变 —— 完整历史仍然保留在磁盘文件中</li>
 *   <li>仅影响检索 —— 只在 {@code get()} 时做窗口 + 摘要</li>
 *   <li>缓存摘要 —— 避免每次请求重复调 LLM，新消息到达时作废缓存</li>
 * </ul>
 */
@Slf4j
public class SummarizingChatMemory implements ChatMemory {

    /**
     * 最大保留的消息条数（40 条 = 约 20 轮对话）
     */
    private static final int DEFAULT_MAX_MESSAGES = 40;

    private final FileBasedChatMemory delegate;
    private final ConversationSummarizer summarizer;
    private final int maxMessages;

    /**
     * 摘要缓存 —— key = conversationId，value = 上次计算的摘要结果
     * 新消息到达（add）时作废对应缓存
     */
    private final Map<String, CachedSummary> summaryCache = new ConcurrentHashMap<>();

    public SummarizingChatMemory(FileBasedChatMemory delegate, ConversationSummarizer summarizer) {
        this(delegate, summarizer, DEFAULT_MAX_MESSAGES);
    }

    public SummarizingChatMemory(FileBasedChatMemory delegate, ConversationSummarizer summarizer,
                                  int maxMessages) {
        this.delegate = delegate;
        this.summarizer = summarizer;
        this.maxMessages = maxMessages;
        log.info("SummarizingChatMemory 初始化完成，窗口大小: {} 条消息（约 {} 轮）",
                maxMessages, maxMessages / 2);
    }

    /**
     * 获取窗口化的对话历史 —— 超过阈值时返回 [摘要] + [最近 N 条原文]
     */
    @Override
    public List<Message> get(String conversationId) {
        List<Message> all = delegate.get(conversationId);

        if (all.isEmpty() || all.size() <= maxMessages) {
            return all;
        }

        // 检查缓存
        CachedSummary cached = summaryCache.get(conversationId);
        if (cached != null && cached.totalMessageCount == all.size()) {
            log.debug("使用缓存的对话摘要: chatId={}, summaryLen={}",
                    conversationId, cached.summary.length());
            return buildResult(cached.summary, cached.recentMessages);
        }

        // 需要重新生成摘要
        int summaryCount = all.size() - maxMessages;
        List<Message> toSummarize = all.subList(0, summaryCount);
        List<Message> recent = new ArrayList<>(all.subList(summaryCount, all.size()));

        log.info("触发对话摘要: chatId={}, 总消息={}, 需摘要={}, 保留={}",
                conversationId, all.size(), summaryCount, recent.size());

        String summary = summarizer.summarize(toSummarize);

        // 更新缓存
        summaryCache.put(conversationId,
                new CachedSummary(summary, recent, all.size()));

        return buildResult(summary, recent);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        delegate.add(conversationId, messages);
        // 新消息到达 → 作废摘要缓存
        summaryCache.remove(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        delegate.clear(conversationId);
        summaryCache.remove(conversationId);
    }

    // ===== 委托方法 —— 暴露底层 FileBasedChatMemory 的能力给管理接口 =====

    /**
     * 删除指定会话（永久删除）
     */
    public boolean deleteConversation(String chatId) {
        summaryCache.remove(chatId);
        return delegate.deleteConversation(chatId);
    }

    /**
     * 列出所有会话概览
     */
    public List<FileBasedChatMemory.ConversationInfo> listConversations() {
        return delegate.listConversations();
    }

    /**
     * 获取原始完整消息（不受窗口限制，用于导出/管理）
     */
    public List<Message> getConversation(String chatId) {
        return delegate.getConversation(chatId);
    }

    // ===== 内部方法 =====

    private List<Message> buildResult(String summary, List<Message> recent) {
        List<Message> result = new ArrayList<>();
        if (summary != null && !summary.isBlank()) {
            result.add(new SystemMessage(
                    "【历史对话摘要】以下是之前对话的要点总结，请结合这些上下文理解用户当前问题。\n"
                            + summary));
        }
        result.addAll(recent);
        return result;
    }

    /**
     * 摘要缓存条目
     */
    private record CachedSummary(
            String summary,
            List<Message> recentMessages,
            int totalMessageCount) {
    }
}
