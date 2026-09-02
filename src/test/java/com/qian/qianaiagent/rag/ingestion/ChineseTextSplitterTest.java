package com.qian.qianaiagent.rag.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChineseTextSplitterTest {

    // chunkSize=20 字，overlap=6 字，短于等于 3 字的丢弃
    private final ChineseTextSplitter splitter = new ChineseTextSplitter(20, 6, 3);

    @Test
    void 按中文标点切分且不拦腰断句() {
        String text = "这是第一句话。这是第二句话。这是第三句话。这是第四句话。";
        List<String> chunks = splitter.splitText(text);

        assertEquals(2, chunks.size());
        // 每个 chunk 都以完整句子的句末标点结尾
        for (String chunk : chunks) {
            assertTrue(chunk.endsWith("。"), "chunk 应以句号结尾: " + chunk);
        }
        // 所有句子至少出现一次
        assertEquals("这是第一句话。这是第二句话。", chunks.get(0));
        assertEquals("这是第二句话。这是第三句话。这是第四句话。", chunks.get(1));
    }

    @Test
    void 相邻chunk有完整句子的重叠() {
        String text = "这是第一句话。这是第二句话。这是第三句话。这是第四句话。";
        List<String> chunks = splitter.splitText(text);

        // 第二块以第一块的最后一句开头（重叠）
        assertTrue(chunks.get(1).startsWith("这是第二句话。"),
                "相邻 chunk 应有重叠句子: " + chunks);
    }

    @Test
    void 短于最小长度的碎片被过滤() {
        // "标题" 只有 2 字，短于 minChunkChars(3) 的过滤阈值，应被丢弃
        List<String> chunks = splitter.splitText("标题");
        assertTrue(chunks.isEmpty());
    }

    @Test
    void 超长单句只出现一次() {
        String text = "这是一句超过chunkSize的超长句子内容。";
        ChineseTextSplitter small = new ChineseTextSplitter(10, 5, 3);
        List<String> chunks = small.splitText(text);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0));
    }

    @Test
    void apply保留父文档metadata() {
        Document doc = new Document(
                "这是第一句话。这是第二句话。这是第三句话。这是第四句话。",
                Map.of("filename", "bagu-java-concurrency.md", "topic", "Java并发"));

        List<Document> chunks = splitter.apply(List.of(doc));

        assertFalse(chunks.isEmpty());
        for (Document chunk : chunks) {
            assertEquals("bagu-java-concurrency.md", chunk.getMetadata().get("filename"));
            assertEquals("Java并发", chunk.getMetadata().get("topic"));
        }
    }
}
