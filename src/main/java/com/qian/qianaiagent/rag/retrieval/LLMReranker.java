package com.qian.qianaiagent.rag.retrieval;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 基于 LLM 的 Cross-Encoder 重排序器
 *
 * <p>📖 核心概念 —— Bi-Encoder vs Cross-Encoder：
 * <pre>
 *   Bi-Encoder（向量检索）：
 *     Query ──→ Embed ──→ 向量A
 *     Doc   ──→ Embed ──→ 向量B   ├── 余弦相似度
 *     → 各自独立编码，Query 和 Doc 之间没有直接交互 → 速度快但精度有限
 *
 *   Cross-Encoder（重排序）：
 *     Query + Doc ──→ 拼接后联合编码 ──→ 相关性分数
 *     → Query 和 Doc 的每个 token 互相"看到"对方 → 精度高但速度慢
 * </pre>
 *
 * <p>🔧 典型流程（文档建议）：
 * <pre>
 *   VectorStore.similaritySearch(topK=50)  →  Reranker.rerank()  →  取 Top-5 送 LLM
 *        (粗筛，快但不够准)                    (精排，准但慢)         (最优质上下文)
 * </pre>
 *
 * <p>参考教程：docs/knowledge-base-upgrade/05-cross-encoder-rerank.md
 */
@Service
@Slf4j
public class LLMReranker {

    @Resource
    private ChatModel openAiChatModel;

    // ================================================================
    // 🧠 任务 ①：设计 Rerank Prompt
    // ================================================================
    // 这是整个 Reranker 最核心的部分。你需要一个 Prompt 让 LLM 给
    // (查询, 文档) 对打分。
    //
    // 🤔 Prompt 设计的关键问题：
    //   1. 怎么让 LLM 只输出分数，不输出解释？（节省 token 且方便解析）
    //   2. 分数的范围是多少？0-10 还是 0-1？整数还是小数？
    //   3. 如果文档完全不相关，应该输出什么？（是不是可以提前用
    //      similarityThreshold 过滤掉明显不相关的）
    //   4. 一次让 LLM 比较几个文档？太多会混乱，太少调用次数多
    //
    // 💡 经验值：每 5 个文档一组让 LLM 打分是比较好的平衡点
    // ================================================================

    /**
     * 对粗筛结果进行重排序
     *
     * <p>🤔 方法签名设计思考：
     *    - 为什么用 List<Document> 而不是数组？
     *    - 为什么需要 topN 参数而不是写死为 5？
     *    - 返回值为什么也是 List<Document>？和输入有什么区别？
     *
     * @param query      用户查询
     * @param documents  粗筛结果（建议 30-50 条，由调用方控制）
     * @param topN       精排后保留的数量（建议 3-5 条）
     * @return 精排后的 Top-N 文档列表
     */
    public List<Document> rerank(String query, List<Document> documents, int topN) {
        // ================================================================
        // 🧠 你需要实现的逻辑：
        // ================================================================
        //
        // Step 1: 参数校验
        //         - 如果 documents 数量 <= topN，不需要重排序，直接返回
        //         - 如果 documents 为空，直接返回空列表
        //
        // Step 2: 分批打分
        //         - 每 5 个文档一组（或其他你认为合理的批量大小）
        //         - 为每组构造 Prompt，调用 LLM
        //         - 🤔 每组的 Prompt 结构建议：
        //           "请为以下文档与查询的相关性打分（0-10分，只输出分数）：
        //            查询：{query}
        //            文档1：{doc1_text}
        //            文档2：{doc2_text}
        //            ...
        //            请按顺序输出分数，每行一个数字："
        //
        // Step 3: 解析 LLM 返回的分数
        //         - LLM 可能返回 "8\n3\n6\n..." 需要按行解析
        //         - 如果解析失败怎么处理？跳过？给默认分 0？
        //
        // Step 4: 按分数重新排序
        //         - 用 Comparator.comparingDouble() 配合 Document 的 score
        //         - 或者把分数存入 Document 的 metadata
        //
        // Step 5: 截取 Top-N 返回
        //
        // Step 6: 异常处理和降级
        //         - LLM 调用失败 → 降级为直接返回前 topN 条（原始排序）
        //         - 分数解析失败 → 给该批文档保留原始排序
        // ================================================================

        // TODO: 在这里实现完整的 Rerank 逻辑

        throw new UnsupportedOperationException("TODO: 实现 LLM Reranker —— 参考 docs/knowledge-base-upgrade/05-cross-encoder-rerank.md");
    }

    // ================================================================
    // 🧠 进阶设计思考（不需要编码，但值得想）：
    // ================================================================
    // 1. 为什么 K=50~100 是经验值？K 太小漏文档，K 太大 Rerank 慢
    // 2. 可以加一个"截断阈值"：分数低于某个值的文档直接丢弃
    // 3. 如果以后有了 BM25 + Dense 双路召回，怎么在 Rerank 前融合结果？
    //    文档介绍了 RRF（Reciprocal Rank Fusion）方法
    // 4. 相同 (query, doc) 对可以缓存 Rerank 分数——用 Redis？
    // ================================================================

}