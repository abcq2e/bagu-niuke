package com.qian.qianaiagent.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.Collections;
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
     * 最大保留的消息条数（20 条 = 约 10 轮对话，给面试点评提供充足上下文）
     */
    private static final int DEFAULT_MAX_MESSAGES = 20;

    /**
     * 默认 token 预算。条数阈值拦不住「少条数、超长文本」（例如一条 8000 字的 RAG 检索结果）
     */
    private static final int DEFAULT_MAX_TOKENS = 8000;

    /**
     * 默认单条消息字符上限。token 预算拦不住「一条就超预算」的消息 ——
     * 「最新一条即使超预算也保留」的规则会让它原样送进模型，因此需要单条封顶兜底。
     */
    private static final int DEFAULT_MAX_CHARS_PER_MESSAGE = 12000;

    /** 截断标记 —— 让模型和人都能看出这里被裁过。 */
    private static final String TRUNCATION_SUFFIX = "\n…[内容过长，已截断]";

    /** 中文约 1.5 字/token 的折中估计；不引入 tokenizer 库，避免虚假的精确感。 */
    private static final double CHARS_PER_TOKEN = 1.5;
    /** 安全余量：宁可估多，不可估少。 */
    private static final double SAFETY_FACTOR = 1.3;

    private final FileBasedChatMemory delegate;
    private final ConversationSummarizer summarizer;
    private final int maxMessages;
    private final int maxTokens;
    private final int maxCharsPerMessage;

    /**
     * 估算一段文本占用的 token 数。
     * <p>
     * 用字符启发式而非真实 tokenizer：DeepSeek 的 tokenizer 非公开稳定，
     * 引入 jtokkit 之类只会给出一个看似精确、实则同样偏差的数字。
     */
    static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN * SAFETY_FACTOR);
    }

    /**
     * 摘要缓存 —— key = conversationId，value = 上次计算的摘要结果
     * 新消息到达（add）时作废对应缓存
     */
    private final Map<String, CachedSummary> summaryCache = new ConcurrentHashMap<>();
    public SummarizingChatMemory(FileBasedChatMemory delegate, ConversationSummarizer summarizer) {
        this(delegate, summarizer, DEFAULT_MAX_MESSAGES, DEFAULT_MAX_TOKENS);
    }
    public SummarizingChatMemory(FileBasedChatMemory delegate, ConversationSummarizer summarizer,
                                  int maxMessages) {
        this(delegate, summarizer, maxMessages, DEFAULT_MAX_TOKENS);
    }
    public SummarizingChatMemory(FileBasedChatMemory delegate, ConversationSummarizer summarizer,
                                  int maxMessages, int maxTokens) {
        this(delegate, summarizer, maxMessages, maxTokens, DEFAULT_MAX_CHARS_PER_MESSAGE);
    }
    public SummarizingChatMemory(FileBasedChatMemory delegate, ConversationSummarizer summarizer,
                                  int maxMessages, int maxTokens, int maxCharsPerMessage) {
        this.delegate = delegate;
        this.summarizer = summarizer;
        this.maxMessages = maxMessages;
        this.maxTokens = maxTokens;
        this.maxCharsPerMessage = maxCharsPerMessage;
        log.info("SummarizingChatMemory 初始化完成，窗口: {} 条消息 / {} token / 单条上限 {} 字",
                maxMessages, maxTokens > 0 ? maxTokens : "不限",
                maxCharsPerMessage > 0 ? maxCharsPerMessage : "不限");
    }

    /**
     * 获取窗口化的对话历史 —— 超过阈值时返回 [受保护消息] + [摘要] + [最近 N 条原文]，
     * 最后再按 token 预算裁剪。
     * <p>
     * 🔴 包含【方向切换】的 SystemMessage 永远不被摘要、也不被裁剪，始终保留。
     */
    @Override
    public List<Message> get(String conversationId) {
        List<Message> all = delegate.get(conversationId);
        if (all.isEmpty()) {
            return all;
        }
        // 先按条数取窗口，再按 token 裁剪 —— 顺序不能反：
        // 原实现在条数未超时直接 return，会整个绕过 token 预算
        List<Message> windowed = all.size() <= maxMessages
                ? new ArrayList<>(all)
                : windowWithSummary(conversationId, all);
        return trimToTokenBudget(windowed);
    }

    /** 按条数取窗口：超阈值时 [受保护消息] + [摘要] + [最近 N 条原文]。 */
    private List<Message> windowWithSummary(String conversationId, List<Message> all) {
        CachedSummary cached = summaryCache.get(conversationId);
        if (cached != null && cached.totalMessageCount == all.size()) {
            log.debug("使用缓存的对话摘要: chatId={}, summaryLen={}",
                    conversationId, cached.summary.length());
            return buildResult(cached.summary, cached.recentMessages);
        }
        // 🔴 保护【方向切换】消息不被摘要（判定与 FileBasedChatMemory 共用同一份）
        List<Message> protectedMsgs = new ArrayList<>();
        int firstUnprotected = 0;
        for (int i = 0; i < all.size(); i++) {
            if (ProtectedMessages.isProtected(all.get(i))) {
                protectedMsgs.add(all.get(i));
                firstUnprotected = i + 1;
            } else {
                break; // 只在开头连续查找，遇到非保护消息就停止
            }
        }
        int effectiveMax = maxMessages - protectedMsgs.size();
        List<Message> rest = all.subList(firstUnprotected, all.size());
        if (rest.size() <= effectiveMax) {
            // 减去受保护消息后不超阈值，全量返回
            List<Message> result = new ArrayList<>(protectedMsgs);
            result.addAll(rest);
            return result;
        }
        int summaryCount = rest.size() - effectiveMax;
        List<Message> toSummarize = rest.subList(0, summaryCount);
        List<Message> recent = new ArrayList<>(rest.subList(summaryCount, rest.size()));
        log.info("触发对话摘要: chatId={}, 总消息={}, 受保护={}, 需摘要={}, 保留={}",
                conversationId, all.size(), protectedMsgs.size(), summaryCount, recent.size());
        String summary = summarizer.summarize(toSummarize);
        summaryCache.put(conversationId,
                new CachedSummary(summary, recent, all.size()));
        // 受保护消息放在摘要前面，确保 AI 优先看到
        List<Message> result = new ArrayList<>(protectedMsgs);
        result.addAll(buildResult(summary, recent));
        return result;
    }

    /**
     * 按 token 预算裁剪。
     * <p>
     * 两条规则：
     * <ul>
     *   <li>受保护消息（{@code 【方向切换】} 锚点）无条件保留 —— 它们通常在最早处，
     *       一旦丢弃，AI 会拿旧方向的题目点评新方向的回答</li>
     *   <li>普通消息取<b>连续后缀</b>：从最新往旧累加，第一条放不下就<b>停止</b>。
     *       不能跳过它继续往旧处收 —— 那会在窗口中间挖空，甚至「丢掉最新的、留下更旧的」</li>
     * </ul>
     * 唯一的例外是<b>最新那一条</b>：即使它自身就超预算也保留，
     * 否则一次超长检索结果就能让整个对话尾巴消失。
     */
    private List<Message> trimToTokenBudget(List<Message> messages) {
        if (maxTokens <= 0 && maxCharsPerMessage <= 0) {
            return messages;
        }
        // 🔴 先做单条封顶：否则一条超长消息会原样送进模型，
        //    「最新一条即使超预算也保留」的规则会让整个预算形同虚设
        List<Message> capped = new ArrayList<>(messages.size());
        for (Message m : messages) {
            capped.add(capMessageLength(m));
        }

        if (maxTokens <= 0) {
            return capped;
        }
        int total = capped.stream().mapToInt(m -> estimateTokens(m.getText())).sum();
        if (total <= maxTokens) {
            return capped;
        }

        List<Message> protectedMsgs = new ArrayList<>();
        int reserved = 0;
        for (Message m : capped) {
            if (ProtectedMessages.isProtected(m)) {
                protectedMsgs.add(m);
                reserved += estimateTokens(m.getText());
            }
        }

        int budget = maxTokens - reserved;
        List<Message> recent = new ArrayList<>();
        int used = 0;
        for (int i = capped.size() - 1; i >= 0; i--) {
            Message m = capped.get(i);
            if (ProtectedMessages.isProtected(m)) {
                continue;   // 已在 protectedMsgs 中单独保留
            }
            int cost = estimateTokens(m.getText());
            // recent 为空时无条件收下最新那条，避免整个窗口被裁空
            if (!recent.isEmpty() && used + cost > budget) {
                break;
            }
            recent.add(m);
            used += cost;
        }
        Collections.reverse(recent);

        List<Message> kept = new ArrayList<>(protectedMsgs);
        kept.addAll(recent);
        if (kept.size() < capped.size()) {
            log.info("token 预算裁剪: {} 条 → {} 条（预算 {} token，实际约 {}）",
                    capped.size(), kept.size(), maxTokens, used + reserved);
        }
        return kept;
    }

    /**
     * 单条消息长度封顶。
     * <p>
     * 只重建结构简单的类型（USER / SYSTEM / 无 toolCalls 的 ASSISTANT）——
     * {@code TOOL} 消息与带 {@code toolCalls} 的 ASSISTANT 结构复杂，
     * 重建会丢掉工具调用信息，宁可放过也不破坏。
     */
    private Message capMessageLength(Message message) {
        if (maxCharsPerMessage <= 0) {
            return message;
        }
        String text = message.getText();
        if (text == null || text.length() <= maxCharsPerMessage) {
            return message;
        }

        String capped = text.substring(0, maxCharsPerMessage) + TRUNCATION_SUFFIX;
        MessageType type = message.getMessageType();
        if (type == MessageType.USER) {
            return new UserMessage(capped);
        }
        if (type == MessageType.SYSTEM) {
            return new SystemMessage(capped);
        }
        if (type == MessageType.ASSISTANT) {
            if (message instanceof AssistantMessage am && !am.getToolCalls().isEmpty()) {
                return message;     // 带工具调用的不能重建
            }
            return new AssistantMessage(capped);
        }
        return message;             // TOOL 等：不动
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

    /** 换方向裁剪后作废摘要缓存 */
    public void invalidateSummary(String conversationId) {
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
     * 列出会话概览（当前用户自己的 + 无主的）
     *
     * @param userId 当前登录用户，用于归属过滤；列表接口不会认领无主会话
     */
    public List<FileBasedChatMemory.ConversationInfo> listConversations(Long userId) {
        return delegate.listConversations(userId);
    }

    /** 建立/继续可写会话（转发给底层做归属判定） */
    public boolean startSession(String chatId, Long userId) {
        return delegate.startSession(chatId, userId);
    }

    /** 打开既有会话（转发给底层做归属判定 + 首次访问认领） */
    public boolean accessConversation(String chatId, Long userId) {
        return delegate.accessConversation(chatId, userId);
    }

    /** 严格归属判定（删除/重命名/导出等破坏性操作） */
    public boolean canManage(String chatId, Long userId) {
        return delegate.canManage(chatId, userId);
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
