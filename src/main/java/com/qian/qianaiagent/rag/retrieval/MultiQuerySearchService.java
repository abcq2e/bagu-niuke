package com.qian.qianaiagent.rag.retrieval;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Multi-Query 检索服务 —— 一个问题，多种问法，合并结果
 *
 * <p>📖 核心原理：
 * <pre>
 *   传统：用户问题 → 1 次检索 → 1 组结果
 *   Multi-Query：用户问题 → 生成 N 个变体 → N 次检索 → 合并去重排序 → 1 组结果
 * </pre>
 *
 * <p>🧠 关键设计决策（动手前必须想清楚）：
 *   <ol>
 *     <li>变体数量 N 设多少？文档建议 3。N 越大覆盖越广，但延迟和 API 调用成本线性增长</li>
 *     <li>多个变体的检索结果怎么合并？直接拼一起？按分数排序？去重后再排序？</li>
 *     <li>去重用哪个字段判断"重复"？Document.getId() 还是 getText() 的内容哈希？</li>
 *     <li>同一个 Document 被不同 query 以不同分数检索到，保留哪个分数？最高分？最新分？</li>
 *   </ol>
 *
 * <p>参考教程：docs/knowledge-base-upgrade/02-multi-query-expansion.md
 */
@Slf4j
@Service
public class MultiQuerySearchService {

    @Resource
    private VectorStore quizVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    /** 多查询守卫：低于此长度的查询视为指令/短关键词，不值得多查询扩展 */
    @Value("${rag.query-rewrite.min-length:15}")
    private int minQueryLength;

    /** 多查询守卫：超过此长度的查询视为回答/粘贴内容，不值得多查询扩展 */
    @Value("${rag.query-rewrite.max-length:100}")
    private int maxQueryLength;

    /**
     * 对话指令黑名单：命中（精确匹配）的查询不做多查询扩展，直接单次检索。
     * 用精确匹配而非包含匹配，避免误伤「请继续讲解线程池原理」这类含指令词的技术问题。
     */
    private static final Set<String> COMMAND_WORDS = Set.of(
            "继续", "换一个", "换一题", "换一道", "下一题", "下一道", "下一个", "跳过",
            "好的", "嗯", "嗯嗯", "好", "可以", "行", "对", "是",
            "不知道", "不会", "再来", "再问", "谢谢", "你好", "在吗", "退出", "结束",
            "pass", "ok", "okay", "yes", "no");

    /**
     * 使用 Multi-Query 策略检索知识库
     *
     * @param userQuery          用户原始问题
     * @param numberOfQueries    生成的查询变体数量（建议 3）
     * @param topK               最终返回的最大文档数
     * @param similarityThreshold 相似度阈值
     * @return 去重排序后的文档列表
     */
    public List<Document> multiQuerySearch(String userQuery, int numberOfQueries,
                                           int topK, double similarityThreshold) {
        // 守卫：对话指令/过短/过长的查询不值得多查询扩展，退化为单次检索
        if (!shouldUseMultiQuery(userQuery)) {
            log.debug("查询不适合多查询扩展，退化为单次检索: {}", userQuery);
            return singleQuerySearch(userQuery, topK, similarityThreshold);
        }

        // Step 1: 对用户问题生成多个变体
        List<String> queryVariants = queryRewriter.doMultiQueryExpand(userQuery, numberOfQueries);
        // Step 2: 对每个变体执行检索，收集所有结果
        List<Document> allResults = new ArrayList<>();
        for (String variant : queryVariants) {
            allResults.addAll(quizVectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(variant)
                            .topK(topK)
                            .similarityThreshold(similarityThreshold)
                            .build()));
        }
        // Step 3: 按 ID 归并去重，保留最高分数
        Map<String, Document> seen = new LinkedHashMap<>();
        for (Document doc : allResults) {
            seen.merge(doc.getId(), doc,
                    (old, neu) -> neu.getScore() > old.getScore() ? neu : old);
        }
        // Step 4: 按分数降序排序，截取 topK
        return seen.values().stream()
                .sorted(Comparator.comparing(Document::getScore).reversed())
                .limit(topK)
                .toList();
    }

    /**
     * 判断查询是否值得做多查询扩展。
     * <p>
     * 按顺序短路：空白 → 命中对话指令黑名单（语义层）→ 长度越界（形式层）。
     * 返回 {@code false} 表示应退化为单次检索。
     */
    boolean shouldUseMultiQuery(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return false;
        }
        String trimmed = userQuery.trim();
        if (COMMAND_WORDS.contains(trimmed)) {
            return false;
        }
        return trimmed.length() >= minQueryLength && trimmed.length() <= maxQueryLength;
    }

    /**
     * 单次向量检索（降级路径）：直接用原 query 检索，不做任何查询增强。
     * 与面试主链路 {@code QuizApp} 的单次检索行为一致。
     */
    private List<Document> singleQuerySearch(String query, int topK, double similarityThreshold) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return quizVectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .build());
    }
}
