package com.qian.qianaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自定义基于 Token 的切词器
 *
 * <p>🔍 学习指引：TokenTextSplitter 的 5 个构造参数代表什么？
 * <pre>
 *   TokenTextSplitter(
 *       defaultChunkSize,   // ① 每个文本块的目标 token 数，默认 800
 *       minChunkSizeChars,  // ② 每个文本块的最小字符数，防止切出空块
 *       minChunkLengthToEmbed, // ③ 短于这个长度的块不向量化（太小没意义）
 *       maxNumChunks,       // ④ 单篇文档最多切成多少块（防止文档爆炸）
 *       keepSeparator       // ⑤ 是否在切分后的 chunk 中保留分隔符
 *   )
 * </pre>
 * 去 Spring AI 源码中验证这些参数的含义，然后想想你的中文文档适合什么值。
 */
@Component
class MyTokenTextSplitter {

    /**
     * 使用默认参数切分（TokenTextSplitter 内置默认值）
     */
    public List<Document> splitDocuments(List<Document> documents) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        return splitter.apply(documents);
    }

    /**
     * 使用自定义参数切分
     *
     * <p>🤔 思考：当前参数 (200, 100, 10, 5000, true) 的含义是：
     *    - 每块 200 token ≈ ? 个中文字
     *    - 重叠 100 token = ?% 的 overlap
     *    - 知识库文档建议 overlap 为 10%-20%，你的 overlap 比例合理吗？
     */
    public List<Document> splitCustomized(List<Document> documents) {
        TokenTextSplitter splitter = new TokenTextSplitter(200, 100, 10, 5000, true);
        return splitter.apply(documents);
    }

    // ================================================================
    // 🧠 你的任务：新增一个针对知识库文档优化的切分方法
    // ================================================================
    // 思考以下问题后再动手：
    // 1. 你的知识库文档是中文技术文章，"一个知识点"大概需要多少字说清楚？
    // 2. chunk_overlap 应该设为 chunk_size 的百分之多少？为什么文档建议 10-20%？
    // 3. 切分后怎么验证效果？—— 随机抽几个 chunk 看看是否包含完整知识点
    //
    // 方法签名建议：
    //   public List<Document> splitForKnowledgeBase(List<Document> documents) { ... }
    //
    // 💡 提示：new TokenTextSplitter(chunkSize, overlap, ...)
    // ================================================================

    // TODO: 在这里编写你的 splitForKnowledgeBase 方法
    //       参考上面 splitCustomized 的写法，但参数要自己设计

    public List<Document> splitForKnowledgeBase(List<Document> documents) {
        TokenTextSplitter splitter = new TokenTextSplitter(200, 30, 10, 5000, true);
        return splitter.apply(documents);
    }
}
