package com.qian.qianaiagent.rag.retrieval;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** 每批交给 LLM 打分的文档数（文档建议经验值：5 条是速度与精度的平衡点） */
    private static final int BATCH_SIZE = 5;

    /** 单个文档送入 Prompt 的最大字符数，防止超长文档撑爆上下文 */
    private static final int MAX_DOC_LENGTH = 500;

    /** 从 LLM 返回文本中提取数字的正则（兼容 8、8.5 等写法） */
    private static final Pattern SCORE_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /**
     * Rerank 打分 Prompt 头部。
     *
     * <p>设计要点：让 LLM 只输出分数、不输出解释（省 token 且方便解析）；
     * 分数范围 0~10；按文档顺序每行一个数字，方便与文档对齐。
     * 文档列表不放进模板占位符，而是由 {@link #buildPrompt} 拼接，避免文档文本中的
     * 「%」干扰 {@code String.formatted} 的占位符解析。
     */
    private static final String RERANK_PROMPT = """
            你是面试官应用中的相关性评分助手。请评估下面每个文档与用户查询的相关程度。

            评分规则：
            - 0 分 = 完全不相关，10 分 = 高度相关
            - 只判断语义相关性，与文档出现顺序无关
            - 严格按文档顺序，每行只输出一个 0~10 之间的数字（可为小数），不要输出任何解释、标点或其他文字

            用户查询：%s

            """;

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
        // Step 1: 参数校验 —— 空列表 / 非法 topN / 数量不足 topN 时无需重排，直接返回
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        if (topN <= 0) {
            return List.of();
        }
        if (documents.size() <= topN) {
            return new ArrayList<>(documents);
        }

        try {
            // Step 2-3: 分批打分 —— 每 BATCH_SIZE 条一组，独立调用 LLM
            List<Document> scored = new ArrayList<>(documents.size());
            for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
                List<Document> batch = documents.subList(i, Math.min(i + BATCH_SIZE, documents.size()));
                scored.addAll(scoreBatch(query, batch));
            }

            // Step 4-5: 按分数降序排序，截取 Top-N
            return scored.stream()
                    .sorted(Comparator.comparingDouble(LLMReranker::scoreOf).reversed())
                    .limit(topN)
                    .toList();
        } catch (Exception e) {
            // Step 6: 降级 —— LLM 整体调用失败，退回原始排序的前 topN 条
            log.warn("LLM Reranker 调用失败，降级为原始排序前 {} 条：{}", topN, e.getMessage());
            return new ArrayList<>(documents.subList(0, Math.min(topN, documents.size())));
        }
    }

    /**
     * 对单批文档打分。
     *
     * <p>单批失败不影响其他批：LLM 异常或分数解析失败时，返回该批原始顺序，
     * 保证重排序是「尽力而为」而非「全有或全无」。
     */
    private List<Document> scoreBatch(String query, List<Document> batch) {
        try {
            String raw = openAiChatModel.call(buildPrompt(query, batch));
            List<Double> scores = parseScores(raw);

            // 解析出的分数个数必须与文档数对齐，否则视为解析失败，保留原始顺序
            if (scores.size() != batch.size()) {
                log.warn("Rerank 分数解析失败（期望 {} 个，实际 {} 个），该批保留原始顺序",
                        batch.size(), scores.size());
                return batch;
            }

            List<Document> scored = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                // Document 无 setScore，用 mutate().score() 生成带新分数的新实例（保留 id/text/metadata）
                scored.add(batch.get(i).mutate().score(scores.get(i)).build());
            }
            return scored;
        } catch (Exception e) {
            log.warn("Rerank 单批打分失败，该批 {} 条保留原始顺序：{}", batch.size(), e.getMessage());
            return batch;
        }
    }

    /** 构造单批打分 Prompt：把查询填进模板，文档列表用 StringBuilder 拼接 */
    private String buildPrompt(String query, List<Document> batch) {
        StringBuilder docs = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            docs.append("文档").append(i + 1).append("：").append(truncate(batch.get(i).getText())).append('\n');
        }
        return RERANK_PROMPT.formatted(query) + docs;
    }

    /** 截断超长文档，避免撑爆 Prompt 上下文 */
    private String truncate(String text) {
        if (text == null || text.length() <= MAX_DOC_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_DOC_LENGTH);
    }

    /** 从 LLM 返回文本中按行提取数字，返回与文档顺序对应的分数列表 */
    private List<Double> parseScores(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Double> scores = new ArrayList<>();
        for (String line : raw.split("\\R")) {
            Matcher matcher = SCORE_PATTERN.matcher(line);
            if (matcher.find()) {
                try {
                    scores.add(Double.parseDouble(matcher.group()));
                } catch (NumberFormatException ignored) {
                    // 忽略无法解析的行
                }
            }
        }
        return scores;
    }

    /** 取文档分数，null 视为负无穷（排序时垫底） */
    private static double scoreOf(Document doc) {
        Double score = doc.getScore();
        return score == null ? Double.NEGATIVE_INFINITY : score;
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
