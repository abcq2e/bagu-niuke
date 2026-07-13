package com.qian.qianaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;

/**
 * 向量数据库配置 —— 基于 PGVector 持久化存储
 *
 * <p>📖 ETL 流水线：
 * <pre>
 *   Load（加载文档） ──→ Split（文档切分） ──→ Enrich（关键词增强） ──→ Store（存入 PGVector）
 * </pre>
 *
 * <p>🧠 初始化策略（增量更新）：
 *   首次启动：数据库为空 → 执行完整 ETL
 *   后续启动：对比源文档文件名与已入库文件名 → 只对新增/更新的文件做 ETL
 *   无新文件 → 秒级跳过，零成本
 */
@Configuration
@Slf4j
public class QuizVectorStoreConfig {

    @Resource
    private QuizDocumentLoader quizDocumentLoader;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Value("${pgvector.datasource.url}")
    private String pgVectorUrl;

    @Value("${pgvector.datasource.username}")
    private String pgVectorUsername;

    @Value("${pgvector.datasource.password}")
    private String pgVectorPassword;

    // 🔴 不暴露为 @Bean，否则 Spring Boot 看到已有 DataSource Bean
    //    就不会创建主数据源（MySQL），导致 MyBatis 错连 PGVector
    private DataSource pgVectorDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(pgVectorUrl);
        dataSource.setUsername(pgVectorUsername);
        dataSource.setPassword(pgVectorPassword);
        return dataSource;
    }

    @Bean
    public JdbcTemplate pgVectorJdbcTemplate() {
        return new JdbcTemplate(pgVectorDataSource());
    }

    @Bean
    VectorStore quizVectorStore(
            @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel,
            JdbcTemplate pgVectorJdbcTemplate) {

        // 🔴 手动确保 pgvector 扩展和表结构存在
        //    因为主数据源是 MySQL，PGVector 自动配置不会生效，所以需手动处理
        pgVectorJdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        pgVectorJdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS vector_store (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    content TEXT NOT NULL,
                    metadata JSONB,
                    embedding vector(1536)
                )
                """);

        // 构建 PgVectorStore（Spring AI 1.0.0 的 builder 接受 JdbcTemplate + EmbeddingModel）
        PgVectorStore pgVectorStore = PgVectorStore.builder(pgVectorJdbcTemplate, embeddingModel)
                .vectorTableName("vector_store")
                .dimensions(1536)
                .build();

        // 🔴 增量 ETL：对比源文档和已入库文档，只处理新增/更新的文件
        int existingCount = pgVectorJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store", Integer.class);
        log.info("PGVector 中已有 {} 条向量", existingCount);

        // 🔴 旧向量修复：删除没有 topic 元数据的八股旧向量
        //    改造前的向量只有 filename/category，没有 topic，导致方向精确检索永远返回空
        int noTopicCount = pgVectorJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'topic' IS NULL AND metadata->>'filename' LIKE '八股-%'",
                Integer.class);
        if (noTopicCount > 0) {
            int deleted = pgVectorJdbcTemplate.update(
                    "DELETE FROM vector_store WHERE metadata->>'topic' IS NULL AND metadata->>'filename' LIKE '八股-%'");
            log.info("🔧 删除 {} 条无 topic 的旧向量（来自 {} 条待修复记录），ETL 将重新嵌入",
                    deleted, noTopicCount);
        } else {
            log.info("✅ 所有向量已有 topic 字段，无需修复");
        }

        // 1. 获取已入库的文档文件名（通过 metadata 中的 filename 字段）
        //    注意：这个查询在旧向量删除之后执行，所以已删除文件的文件名不再出现
        List<String> existingFilenames = pgVectorJdbcTemplate.queryForList(
                "SELECT DISTINCT metadata->>'filename' AS filename FROM vector_store WHERE metadata->>'filename' IS NOT NULL",
                String.class);
        log.info("现有文件列表: {}", existingFilenames);

        // 2. 加载所有源文档
        List<Document> allDocuments = quizDocumentLoader.loadDocuments();

        // 孤儿清理：删除源目录中已不存在的文件对应的向量
        List<String> sourceFilenames = allDocuments.stream()
                .map(doc -> (String) doc.getMetadata().get("filename"))
                .distinct().toList();

        for (String existing : existingFilenames) {
            if (!sourceFilenames.contains(existing)) {
                int deleted = pgVectorJdbcTemplate.update(
                        "DELETE FROM vector_store WHERE metadata->>'filename' = ?", existing);
                log.info("🗑️ 源文件已删除，清理孤儿向量: {} ({} 条)", existing, deleted);
            }
        }

        // 3. 按文件名分组，找出新增/更新的文件
        var docGroups = allDocuments.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        doc -> (String) doc.getMetadata().get("filename")));

        List<String> newOrUpdatedFiles = new java.util.ArrayList<>();
        List<Document> newDocuments = new java.util.ArrayList<>();

        for (var entry : docGroups.entrySet()) {
            String filename = entry.getKey();
            if (!existingFilenames.contains(filename)) {
                newOrUpdatedFiles.add(filename);
                newDocuments.addAll(entry.getValue());
            }
        }

        if (newDocuments.isEmpty()) {
            log.info("✅ 知识库已是最新，无需增量更新");
        } else {
            log.info("🆕 检测到 {} 个新文件需要入库: {}", newOrUpdatedFiles.size(), newOrUpdatedFiles);

            // 4. 只对新文档做切分 + 嵌入
            List<Document> splitDocuments = myTokenTextSplitter.splitForKnowledgeBase(newDocuments);
            log.info("新文档切分完成，共 {} 条片段，开始嵌入...", splitDocuments.size());

            int batchSize = 25;
            int total = splitDocuments.size();
            for (int i = 0; i < total; i += batchSize) {
                int end = Math.min(i + batchSize, total);
                List<Document> batch = splitDocuments.subList(i, end);
                pgVectorStore.add(batch);
                log.info("ETL 进度: {}/{} 条已入库", end, total);
            }
            log.info("✅ 增量 ETL 完成，新增 {} 条向量（来自 {} 个文件）",
                    total, newOrUpdatedFiles.size());
        }

        return pgVectorStore;
    }
}
