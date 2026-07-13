package com.qian.qianaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * HyDE（假设性文档嵌入）检索服务
 *
 * <p>📖 核心原理（来自 2022 年的经典论文）：
 * <pre>
 *   传统做法：用户问题 → Embedding → 向量检索
 *   HyDE 做法：用户问题 → LLM 生成"假设答案" → 拿答案的 Embedding → 向量检索
 *
 *   为什么有效？"答案的语义空间"和"答案的语义空间"更近。
 *   假设答案即使有错误，也比原始问题包含更多信息量 → 向量表示更精确。
 * </pre>
 *
 * <p>⚠️ 注意事项：
 *   1. 假设答案只用于检索，不要展示给用户（可能包含错误信息）
 *   2. HyDE 多做了一次 LLM 调用，延迟会翻倍，不是所有场景都适用
 *   3. 对"开放式问题"（需要长回答的）效果最好，对简单 fact 问题效果不明显
 *
 * <p>参考教程：docs/knowledge-base-upgrade/03-hyde-implementation.md
 */
@Service
public class HyDESearchService {

    @Resource
    private QueryRewriter queryRewriter;

    @Resource
    private VectorStore quizVectorStore;

    // ================================================================
    // 🧠 你的任务：实现 HyDE 检索的 5 个步骤
    // ================================================================
    //
    // Step 1: 用 queryRewriter.generateHypotheticalAnswer(question) 生成假设答案
    //         （如果 QueryRewriter 中还没写这个方法，先去那边完成）
    //
    // Step 2: 用假设答案文本（不是原始问题！）调用 quizVectorStore.similaritySearch()
    //         关键：SearchRequest.query() 应该传假设答案，不是原始问题
    //
    // Step 3: 返回检索到的文档列表
    //
    // 🤔 设计思考：
    //   - topK 设多少？HyDE 的检索精度更高，topK 是否可以比普通检索小？
    //   - similarityThreshold 设多少？HyDE 生成的长文本和知识库短 chunk 的
    //     相似度可能会偏低，阈值要不要调低？
    //   - 假设答案生成失败时怎么降级？直接用原始问题做普通检索？
    // ================================================================
    /**
     * 使用 HyDE 策略检索知识库
     *
     * @param question            用户原始问题
     * @param topK                返回的最大文档数
     * @param similarityThreshold 相似度阈值
     * @return 检索到的文档列表（按相似度降序）
     */
    public List<Document> searchWithHyDE(String question, int topK, double similarityThreshold) {
        // Step 1: 调用 QueryRewriter 生成假设答案
        // 用 try-catch 兜底：LLM 调用失败时降级为原始问题检索
        String hypotheticalAnswer;
        try {
            hypotheticalAnswer = queryRewriter.generateHypotheticalAnswer(question);
        } catch (Exception e) {
            // 降级：假设答案生成失败，直接用原始问题检索
            hypotheticalAnswer = null;
        }

        // ❌ 错误写法（已注释）：
        // if (request == null) {  ← Builder 的 .build() 永远不会返回 null！
        //      SearchRequest.builder()...build();  ← 创建了对象但没有赋值，等于白写

        // Step 2: 用假设答案做向量检索
        // ✅ 关键：query() 传的是假设答案，不是原始问题
        List<Document> results = quizVectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(hypotheticalAnswer)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .build());

        // Step 3: 如果假设答案没搜到结果，降级用原始问题重试
        // 🤔 思考：什么情况下假设答案会搜不到？阈值太高？答案方向偏了？
        if (results.isEmpty() && hypotheticalAnswer != null) {
            results = quizVectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(question)
                            .topK(topK)
                            .similarityThreshold(similarityThreshold)
                            .build());
        }

        return results;
    }

    // ================================================================
    // 🧠 进阶挑战：判断问题是否适合用 HyDE
    // ================================================================
    // HyDE 不是万能的。判断标准：
    //   - ✅ 适合：开放性问题、需要长回答的、概念性解释
    //   - ❌ 不适合：简单事实查询、"是什么"的封闭问题
    //
    // 你可以设计一个简单规则来判断：
    //   问题长度 > 10 字？包含"为什么"/"怎么"/"原理"等关键词？
    //
    // 方法签名建议：
    //   public boolean shouldUseHyDE(String question) { ... }
    // ================================================================

}