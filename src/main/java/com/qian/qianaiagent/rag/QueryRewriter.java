package com.qian.qianaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询重写器 —— RAG 检索前的查询预处理
 *
 * <p>🔍 学习指引：查询增强技术的演进层次
 * <pre>
 *   L1: 查询重写（当前已实现）  —— 把口语改成规范表达
 *   L2: Multi-Query 扩展（第 2 篇）—— 1 个问题变 N 个变体检索
 *   L3: HyDE 假设答案（第 3 篇）  —— 先生成答案再拿答案检索
 * </pre>
 *
 * <p>每往上一层，检索质量可能更好，但同时也多了一次 LLM 调用（延迟 + 成本）。
 *    思考：什么场景下值得付出这个代价？
 */
@Component
@Slf4j
public class QueryRewriter {

    private final QueryTransformer queryTransformer;
    private final ChatClient.Builder chatClientBuilder;

    /** L1 查询重写 LRU 缓存：同一条消息不重复调 LLM */
    private final Map<String, String> rewriteCache;

    /** 缓存最大条目数（128 条 ≈ 覆盖常见面试场景的循环追问） */
    private static final int CACHE_MAX_SIZE = 128;

    public QueryRewriter(ChatModel openAiChatModel) {
        ChatClient.Builder builder = ChatClient.builder(openAiChatModel);
        this.chatClientBuilder = builder;
        // 创建查询重写转换器（L1 基础增强）
        queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder)
                .build();
        // 基于 LinkedHashMap 的 LRU 缓存（access-order=true）
        this.rewriteCache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > CACHE_MAX_SIZE;
            }
        };
    }

    /**
     * L1：执行查询重写 —— 把用户口语化问题改成更适合检索的规范表达
     * <p>
     * 🔴 守护逻辑：短回答、不会/忘了类回复、纠偏指令不调 LLM 重写，直接返回原文。
     * 避免 LLM 拒绝重写时返回英文废话污染后续流程。
     */
    public String doQueryRewrite(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return prompt;
        }
        String trimmed = prompt.trim();

        // 统一快速跳过判断：短消息/长回答/代码片段不调 LLM 重写
        if (shouldSkipFastPath(trimmed)) {
            return trimmed;
        }

        // 不会/忘了/不知道类回复不重写
        if (trimmed.contains("不记得") || trimmed.contains("不会")
                || trimmed.contains("忘了") || trimmed.contains("不知道")
                || trimmed.contains("没学过") || trimmed.contains("不了解")) {
            return trimmed;
        }
        // 纠偏/指令类消息不重写
        if (trimmed.contains("自己出题") || trimmed.contains("别问")
                || trimmed.contains("不要问") || trimmed.contains("换一个")
                || trimmed.contains("继续") || trimmed.startsWith("你")) {
            return trimmed;
        }

        // 🔴 LRU 缓存命中 → 直接返回，省 1 次 LLM 调用（节省 1-3s）
        synchronized (rewriteCache) {
            String cached = rewriteCache.get(trimmed);
            if (cached != null) {
                log.debug("♻️ 查询重写缓存命中: len={}", trimmed.length());
                return cached;
            }
        }

        Query query = new Query(prompt);
        Query transformedQuery = queryTransformer.transform(query);
        String result = transformedQuery.text();

        // 缓存结果（仅缓存非空且与原文不同的结果）
        if (result != null && !result.isEmpty() && !result.equals(trimmed)) {
            synchronized (rewriteCache) {
                rewriteCache.put(trimmed, result);
                log.debug("💾 查询重写缓存写入: {} → {}", trimmed.length(), result.length());
            }
        }

        return result;
    }

    /**
     * L2：Multi-Query 扩展 —— 1 个问题生成 N 个不同角度的查询变体
     *
     * <p>设计决策：
     * <ul>
     *   <li>设置 {@code doQueryRewrite(true)} + {@code queryTransformer}：
     *       先生成 1 个改写查询，再基于改写结果扩展出 numberOfQueries 个变体。
     *       两步串行，共 2 次 LLM 调用。如果不需要改写直接扩展，去掉这两个配置即可</li>
     *   <li>numberOfQueries 建议 3~5：太少覆盖不足，太多延迟和成本线性增长且边际收益递减</li>
     * </ul>
     *
     * @param userQuery       用户原始问题
     * @param numberOfQueries 生成的查询变体数量
     * @return 多个查询变体文本
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
     * 快速跳过判断：以下情况不调用 LLM 重写，直接返回原文。
     * <p>
     * 面试场景下约 80% 的用户消息（长回答/代码）可跳过 LLM 重写，直接节省 1-3s。
     * 提取为 static package-private 方法，方便单元测试验证规则而无需 Spring 容器。
     *
     * @param trimmed 已 trim 的用户消息
     * @return true 表示应跳过 LLM 重写
     */
    static boolean shouldSkipFastPath(String trimmed) {
        if (trimmed == null || trimmed.isBlank()) return true;
        // 短消息（< 15 字）：无足够语义做重写，直接返回
        if (trimmed.length() < 15) return true;
        // 长回答（> 100 字）：用户在回答面试题，不是在提问，重写无助于检索
        if (trimmed.length() > 100) return true;
        // 含代码关键词：用户在写代码示例，跳过重写
        if (trimmed.contains("public ") || trimmed.contains("class ")
                || trimmed.contains("return ") || trimmed.contains("{")
                || trimmed.contains("->") || trimmed.contains("//")) {
            return true;
        }
        return false;
    }

    // ================================================================
    // 🧠 任务 ②（第 3 篇）：HyDE 假设答案生成
    // ================================================================
    // 核心思想：问题和答案在"语义空间"中距离较远（问题是询问式短文本，
    // 答案是陈述式长文本），用 LLM 先生成一段假设性回答，拿这段回答的
    // Embedding 去检索，比直接用原始问题匹配得更准。
    //
    // Prompt 设计要点：
    // 1. 要求 LLM 像写百科词条一样回答，而不是像聊天 —— 信息密度更高
    // 2. 引导展开细节、举例、列出关键概念 —— 向量表示更精确
    // 3. 控制在 150~300 字：太短信息不够，太长 token 浪费
    // 4. 假设答案可能有事实错误 → 不影响检索！因为检索匹配的是
    //    "语义方向"不是"事实正确性"，错误的细节依然能提供语义信号
    //
    // ⚠️ 生成的假设答案只用于检索，不要展示给用户
    // ================================================================

    /**
     * L3：HyDE 假设答案生成 —— 用 LLM 生成一段假设性回答，拿这段回答去检索
     *
     * <p>为什么不直接用原始问题检索？因为"答案的语义空间"和"答案的语义空间"更近。
     *
     * <p>生成的假设答案只用于检索，<b>不要展示给用户</b>（可能包含错误信息）。
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
