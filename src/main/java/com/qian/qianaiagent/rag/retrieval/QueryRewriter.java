package com.qian.qianaiagent.rag.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询重写器 —— RAG 检索前的查询预处理
 *
 * <p>查询增强技术的演进层次：
 * <pre>
 *   L1: 查询重写（当前类）   —— 把口语改成规范表达
 *   L2: Multi-Query 扩展     —— 1 个问题变 N 个变体检索
 *   L3: HyDE 假设答案        —— 先生成答案再拿答案检索
 * </pre>
 * 每往上一层，检索质量可能更好，但同时也多了一次 LLM 调用（延迟 + 成本）。
 */
@Component
@Slf4j
public class QueryRewriter {

    private final ChatClient.Builder chatClientBuilder;

    /** L1 查询重写 LRU 缓存：同一条消息不重复调 LLM */
    private final Map<String, String> rewriteCache;

    /** 缓存最大条目数（128 条 ≈ 覆盖常见面试场景的循环追问） */
    private static final int CACHE_MAX_SIZE = 128;

    /** 短消息下限：低于此长度视为指令或已足够简洁的查询，无需重写 */
    @Value("${rag.query-rewrite.min-length:15}")
    private int minQueryLength;

    /** 长消息上限：超过此长度视为回答而非提问，无需重写 */
    @Value("${rag.query-rewrite.max-length:100}")
    private int maxQueryLength;

    /**
     * 智能重写 prompt：把「是否需要重写」的判断也交给 LLM，
     * 替代硬编码关键词 —— 让模型自己区分「提问」与「回答 / 指令 / 代码」。
     */
    private static final String REWRITE_PROMPT = """
            你是面试官应用中的查询重写助手。请判断下面这条用户消息是否需要重写为知识库检索查询：

            - 如果它是一条需要检索知识库的技术提问（可能口语化、模糊、含冗余），请重写成简洁、规范、关键词明确的技术查询。
            - 如果它是一条回答、指令（如「继续」「换一个」「自己出题」）、代码片段，或已经是清晰简洁的技术关键词，请原样返回原文，不要做任何改动。

            只输出重写后的查询或原文，不要任何解释、引号或前后缀。

            用户消息：%s
            """;

    public QueryRewriter(ChatModel openAiChatModel) {
        this.chatClientBuilder = ChatClient.builder(openAiChatModel);
        // 基于 LinkedHashMap 的 LRU 缓存（access-order=true）
        this.rewriteCache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > CACHE_MAX_SIZE;
            }
        };
    }

    /**
     * L1：执行查询重写 —— 把用户口语化问题改成更适合检索的规范表达。
     * <p>
     * 只保留两个客观守卫（过短=指令/已简洁，过长=回答），
     * 「是否需要重写」的主观判断交给 LLM 完成，不再硬编码关键词。
     */
    public String doQueryRewrite(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return prompt;
        }
        String trimmed = prompt.trim();

        // 客观守卫：过短（指令/已简洁的查询）或过长（回答）都不需要重写
        if (trimmed.length() < minQueryLength || trimmed.length() > maxQueryLength) {
            return trimmed;
        }

        // LRU 缓存命中 → 直接返回，省 1 次 LLM 调用
        synchronized (rewriteCache) {
            String cached = rewriteCache.get(trimmed);
            if (cached != null) {
                log.debug("♻️ 查询重写缓存命中: len={}", trimmed.length());
                return cached;
            }
        }

        // 让 LLM 判断并重写：提问 → 规范查询；回答/指令/代码 → 原样返回
        String result = chatClientBuilder.build()
                .prompt()
                .user(REWRITE_PROMPT.formatted(trimmed))
                .call()
                .content();

        if (result == null || result.isBlank()) {
            return trimmed;
        }
        String cleaned = result.trim();

        // 缓存结果（仅缓存与原文不同的结果）
        if (!cleaned.equals(trimmed)) {
            synchronized (rewriteCache) {
                rewriteCache.put(trimmed, cleaned);
                log.debug("💾 查询重写缓存写入: {} → {}", trimmed.length(), cleaned.length());
            }
        }
        return cleaned;
    }

    /**
     * L2：Multi-Query 扩展 —— 1 个问题生成 N 个不同角度的查询变体。
     */
    public List<String> doMultiQueryExpand(String userQuery, int numberOfQueries) {
        // 步骤 1: 先重写查询，提升语义质量
        String rewritten = doQueryRewrite(userQuery);
        // 步骤 2: 基于重写后的查询扩展出 N 个变体
        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                .numberOfQueries(numberOfQueries)
                .build();
        List<Query> queries = queryExpander.expand(new Query(rewritten));
        return queries.stream().map(Query::text).toList();
    }

    /**
     * L3：HyDE 假设答案生成 —— 用 LLM 生成一段假设性回答，拿这段回答去检索。
     *
     * <p>为什么不用原始问题检索？因为「答案的语义空间」和「答案的语义空间」更近。
     * 生成的假设答案只用于检索，<b>不要展示给用户</b>（可能包含错误信息）。
     *
     * @param question 用户原始问题
     * @return LLM 生成的假设性答案文本（用于后续向量检索）
     */
    public String generateHypotheticalAnswer(String question) {
        String hydePrompt = """
                你是一位知识渊博的专家。请针对以下问题，写一段假设性的百科词条式回答。

                要求：
                - 像写百科一样，用陈述句展开说明，而不是用"建议你..."的口吻
                - 包含相关的关键概念、原理、具体例子，让回答信息量丰富
                - 控制在 150~300 字，不要太短（信息不够）也不要太长（token 浪费）
                - 不需要保证完全正确，这只是一个假设性草稿

                问题：%s

                假设性回答：""".formatted(question);

        return chatClientBuilder.build()
                .prompt()
                .user(hydePrompt)
                .call()
                .content();
    }

}
