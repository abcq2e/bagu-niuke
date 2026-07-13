package com.qian.qianaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 AI 的文档元信息增强器（为文档补充元信息）
 * <p>⚠️ 会逐条调 LLM 提取关键词，文档量大时非常耗时。
 * 如需启用，确保数据量和 LLM 并发配额允许。
 */
@Slf4j
@Component
public class MyKeywordEnricher {

    @Resource
    private ChatModel openAiChatModel;

    public List<Document> enrichDocuments(List<Document> documents) {
        log.info("开始关键词增强，共 {} 条文档，逐条调 LLM 提取关键词...", documents.size());
        KeywordMetadataEnricher keywordMetadataEnricher =
                new KeywordMetadataEnricher(openAiChatModel, 5);
        List<Document> result = keywordMetadataEnricher.apply(documents);
        log.info("关键词增强完成");
        return result;
    }
}
