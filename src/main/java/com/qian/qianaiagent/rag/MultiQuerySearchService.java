package com.qian.qianaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
@Service
public class MultiQuerySearchService {

    @Resource
    private VectorStore quizVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

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
        // Step 1: 生成多个查询变体
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
        // 为什么不用 distinct()？Document 的 equals 可能不重写，
        // 且 distinct 保留第一个出现的结果，不一定是分数最高的
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

    // ================================================================
    // 🧠 进阶挑战（第 2 篇任务 4）：并行检索优化
    // ================================================================
    // 当前 N 次检索是串行的（for 循环），总耗时 = N × 单次检索耗时。
    // 改成并行：每个变体独立检索，互不依赖，天然可并行化。
    //
    // 方案：@Async 让 Spring 代理接管异步执行，方法内部直接返回结果，
    //       Spring 自动包装成 CompletableFuture。调用方用 allOf() 等所有结果。
    //
    // 前提：启动类需要 @EnableAsync（本项目已开启）
    // ================================================================

    /**
     * 按分类过滤的 Multi-Query 检索 —— 锁定知识域，提高检索精度
     *
     * @param userQuery           用户原始问题
     * @param numberOfQueries     生成的查询变体数量（建议 3）
     * @param topK                最终返回的最大文档数
     * @param similarityThreshold 相似度阈值
     * @param category            知识分类（如 "八股"、"Agent"、"实践"）
     * @return 指定分类下去重排序后的文档列表
     */
    public List<Document> multiQuerySearchWithCategory(String userQuery, int numberOfQueries,
                                                        int topK, double similarityThreshold,
                                                        String category) {
        List<String> queryVariants = queryRewriter.doMultiQueryExpand(userQuery, numberOfQueries);

        Filter.Expression filter = new FilterExpressionBuilder()
                .eq("category", category)
                .build();

        List<Document> allResults = new ArrayList<>();
        for (String variant : queryVariants) {
            allResults.addAll(quizVectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(variant)
                            .topK(topK)
                            .similarityThreshold(similarityThreshold)
                            .filterExpression(filter)
                            .build()));
        }

        Map<String, Document> seen = new LinkedHashMap<>();
        for (Document doc : allResults) {
            seen.merge(doc.getId(), doc,
                    (old, neu) -> neu.getScore() > old.getScore() ? neu : old);
        }

        return seen.values().stream()
                .sorted(Comparator.comparing(Document::getScore).reversed())
                .limit(topK)
                .toList();
    }

    /**
     * 异步检索单个查询 —— 配合 {@code CompletableFuture.allOf()} 实现并行检索
     *
     * <p>{@code @Async} 让 Spring 在异步线程池中执行，方法返回后 Spring 自动
     * 包装为 {@link CompletableFuture}，无需手动 {@code supplyAsync}。
     *
     * @param query               单个查询文本
     * @param numberOfQueries     变体数量
     * @param topK                返回文档数
     * @param similarityThreshold 相似度阈值
     * @return 检索结果的 CompletableFuture
     */
    @Async
    public CompletableFuture<List<Document>> searchAsync(String query, int numberOfQueries,
                                                          int topK, double similarityThreshold) {
        return CompletableFuture.completedFuture(
                multiQuerySearch(query, numberOfQueries, topK, similarityThreshold));
    }

}