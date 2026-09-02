package com.qian.qianaiagent.rag.ingestion;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class MyTokenTextSplitter {

    /**
     * 知识库切分：使用中文友好的 {@link ChineseTextSplitter}。
     * <p>中文面试八股文场景下，按句末标点（。！？；）切句 + 字符数分块 + 相邻 chunk 重叠，
     * 解决 Spring AI TokenTextSplitter 只认英文标点、无 overlap 导致的句子被拦腰切断问题。
     *
     * @param documents 待切分文档
     * @return 切分后的片段（chunkSize=600 字，overlap=120 字，短于等于 10 字的丢弃）
     */
    public List<Document> splitForKnowledgeBase(List<Document> documents) {
        ChineseTextSplitter splitter = new ChineseTextSplitter(600, 120, 10);
        return splitter.apply(documents);
    }
}
