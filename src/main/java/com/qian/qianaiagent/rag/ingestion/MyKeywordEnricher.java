package com.qian.qianaiagent.rag.ingestion;

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

    /**
     * 构造器注入而非 {@code @Resource} 字段注入。
     *
     * <p>原先字段名 {@code openAiChatModel}，{@code @Resource} <b>先按字段名匹配</b>，
     * 会直接命中同名裸 bean，{@code @Primary} 上的 {@code ResilientChatModel} 不生效 ——
     * 逐条文档的关键词提取会绕过超时/重试/熔断/降级（这是最容易被打爆的批量路径）。
     * 构造器注入按<b>类型</b>解析，{@code @Primary} 才会赢。
     */
    private final ChatModel chatModel;

    public MyKeywordEnricher(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public List<Document> enrichDocuments(List<Document> documents) {
        log.info("开始关键词增强，共 {} 条文档，逐条调 LLM 提取关键词...", documents.size());
        KeywordMetadataEnricher keywordMetadataEnricher =
                new KeywordMetadataEnricher(chatModel, 5);
        List<Document> result = keywordMetadataEnricher.apply(documents);
        log.info("关键词增强完成");
        return result;
    }
}
