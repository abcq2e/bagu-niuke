package com.qian.qianaiagent.rag.ingestion;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * 创建自定义的知识考察 RAG 检索增强顾问的工厂
 * <p>
 * 支持按知识点分类（category）过滤文档，缩小检索范围、提高检索精度
 */
public class QuizRagCustomAdvisorFactory {

    /**
     * 创建自定义的 RAG 检索增强顾问
     *
     * @param vectorStore 向量存储
     * @param category    知识点分类（如 "Java"、"Spring"、"算法"）
     * @return 自定义的 RAG 检索增强顾问
     */
    public static Advisor createQuizRagCustomAdvisor(VectorStore vectorStore, String category) {
        // 按分类过滤文档
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("category", category)
                .build();
        // 创建文档检索器
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expression)
                .similarityThreshold(0.5)
                .topK(3)
                .build();
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(QuizContextualQueryAugmenterFactory.createInstance())
                .build();
    }
}
