package com.qian.qianaiagent.rag.evaluation;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAGAS 评估器 —— 计算 4 个核心指标
 *
 * <p>📖 算法来源：docs/knowledge-base-upgrade/04-ragas-evaluation.md
 *
 * <p>架构设计思路：
 * <pre>
 *   为什么把 4 个指标放在同一个类而不是拆成 4 个类？
 *   → 它们共享同一个 ChatModel 注入、同一种调用模式，
 *     拆太散反而增加复杂度。如果以后某个指标变得很复杂再拆。
 *
 *   为什么每个方法接收原始数据而不是 RagasEvaluationResult？
 *   → 职责分离：计算方法只关心"输入→分数"，组装 Result 是调用方的事。
 * </pre>
 */
@Service
@Slf4j
public class RagasEvaluator {

    @Resource
    private ChatModel openAiChatModel;

    @Resource
    private EmbeddingModel primaryEmbeddingModel;
    // ================================================================
    // 指标 1：Context Precision（上下文精确率）
    // ================================================================
    // 问题：检索到的文档中，相关的排在前面了吗？
    //
    // 算法步骤：
    //   Step 1: 遍历检索结果列表（按分数从高到低排序）
    //   Step 2: 对每个位置 K，判断前 K 个文档中有几个与 query 相关
    //   Step 3: 计算 precision@k = 前K个中相关的数量 / K
    //   Step 4: 对所有 K 的 precision@k 加权平均
    //
    // 关键设计决策 —— 怎么判断"相关"？
    //   - 方案A（简单）：关键词匹配。统计 query 中的词在文档中出现的比例
    //   - 方案B（精确）：用 LLM 判断。Prompt: "文档是否与问题相关？只答 YES/NO"
    //
    // 关键设计决策 —— 权重函数怎么设计？
    //   - 排在越前面的文档权重应该越大（因为用户先看到前面的）
    //   - 常见做法：weight_k = 1/k 或 1/log(k+1)
    //   - 最后归一化让所有 weight 加起来等于 1
    //
    // 💡 建议：先用方案A跑通流程，再替换为方案B
    // ================================================================

    /**
     * 计算 Context Precision
     *
     * @param query      用户问题
     * @param documents  检索到的文档列表（已按相似度降序排列）
     * @return [0, 1] 之间的分数，越高越好
     */
    public double calculateContextPrecision(String query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            log.warn("文档列表为空，Context Precision 返回 0");
            return 0.0;
        }
        // 🔴 你的任务：实现 Context Precision 算法
        // ============================================================
        // Step 1: 对每个文档判断是否相关 → boolean[] relevant
        //         提示：先用关键词匹配（方案A），后面可以替换成 isRelevantByLLM()
        // Step 2: 计算每个位置 K 的 precision@k
        //         precisionAtK = 前K个中相关的数量 / K
        // Step 3: 加权平均
        //         方案1（推荐）：weight_k = 1/k，然后归一化
        //         方案2：直接用 precision@N（只看最后一个位置）
        // Step 4: 返回最终分数
        //
        // 伪代码：
        // double weightedSum = 0;
        // double weightSum = 0;
        // int relevantCount = 0;
        // for (int k = 1; k <= documents.size(); k++) {
        //     if (isRelevant(query, documents.get(k-1))) relevantCount++;
        //     double precisionAtK = (double) relevantCount / k;
        //     double weight = 1.0 / k;  // 排名靠前权重更大
        //     weightedSum += precisionAtK * weight;
        //     weightSum += weight;
        // }
        // return weightedSum / weightSum;
        // ============================================================
        double weightedSum = 0;
        double weightSum = 0;
        int relevantCount = 0;
        for (int k = 1; k <= documents.size(); k++) {
            if (isRelevantByKeyword(query, documents.get(k - 1).getText(), 0.3))  relevantCount++;
            double precisionAtK = (double) relevantCount / k;
            double weight = 1.0 / k;  // 排名靠前权重更大
            weightedSum += precisionAtK * weight;
            weightSum += weight;
        }
        return weightedSum / weightSum;
        //这里算的是加权平均值，可以看看其定义
        // TODO: 实现 Context Precision 计算
        //throw new UnsupportedOperationException("TODO: 实现 Context Precision");
    }
    // ================================================================
    // 指标 2：Context Recall（上下文召回率）
    // ================================================================
    // 问题：回答问题需要的信息，检索到了多少？
    //
    // 算法步骤（需要参考答案）：
    //   Step 1: 把参考答案拆成多个独立的"声明（claims）"
    //           用 LLM: "请将以下答案拆分为原子事实声明，每行一个"
    //   Step 2: 对每个声明，判断能否从检索文档中找到依据
    //           用 LLM: "声明 X 能否从上下文中推断？YES/NO"
    //   Step 3: Recall = 被支持的声明数 / 总声明数
    //
    // ⚠️ 注意：Context Recall 依赖参考答案（ground truth），
    //   如果你没有参考答案数据集，这个指标暂时算不了。
    //   可以先用 Context Precision 和 Faithfulness 建立 baseline。
    // ================================================================
    /**
     * 计算 Context Recall
     *
     * @param referenceAnswer  参考答案（ground truth）
     * @param documents        检索到的文档列表
     * @return [0, 1] 之间的分数
     */
    public double calculateContextRecall(String referenceAnswer, List<Document> documents) {
        if (referenceAnswer == null || referenceAnswer.isBlank()) {
            log.warn("参考答案为空，Context Recall 返回 0");
            return 0.0;
        }
        if (documents == null || documents.isEmpty()) {
            return 0.0;
        }

        // Step 1: 提取参考答案中的原子声明
        List<String> claims = extractClaims(referenceAnswer);
        if (claims.isEmpty()) {
            log.warn("未能从参考答案中提取到声明，Context Recall 返回 0");
            return 0.0;
        }

        // Step 2: 拼接所有检索文档为上下文
        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        // Step 3: 逐条验证每个声明是否被上下文支持
        int supportedCount = 0;
        for (String claim : claims) {
            if (verifyClaimByContext(claim, context)) {
                supportedCount++;
            }
        }

        // Step 4: 计算召回率
        double recall = (double) supportedCount / claims.size();
        log.info("Context Recall: {}/{} = {}", supportedCount, claims.size(), String.format("%.2f", recall));
        return recall;
    }

    // ================================================================
    // 指标 3：Faithfulness（忠实度）⭐ 核心指标
    // ================================================================
    // 问题：LLM 生成的答案有没有瞎编？
    //
    // 算法步骤：
    //   Step 1: 从 LLM 答案中提取所有"声明（claims）"
    //   Step 2: 对每个声明，判断能否从检索上下文中找到依据
    //   Step 3: Faithfulness = 有依据的声明数 / 总声明数
    //
    // 这是 RAGAS 4 个指标中最重要的 —— 直接衡量幻觉程度
    // ================================================================

    /**
     * 计算 Faithfulness（忠实度）
     *
     * @param generatedAnswer  LLM 生成的答案
     * @param documents        检索到的上下文文档
     * @return [0, 1] 之间的分数，越高说明幻觉越少
     */
    public double calculateFaithfulness(String generatedAnswer, List<Document> documents) {
        if (generatedAnswer == null || generatedAnswer.isBlank()) {
            log.warn("生成答案为空，Faithfulness 返回 0");
            return 0.0;
        }
        if (documents == null || documents.isEmpty()) {
            log.warn("文档列表为空，Faithfulness 返回 0");
            return 0.0;
        }

        // ============================================================
        // 🔴 你的任务（核心）：实现 Faithfulness 算法
        // ============================================================
        //
        // Step 1: 从生成答案中提取 claims → extractClaims(generatedAnswer)
        //
        // Step 2: 拼接上下文
        //         String context = documents.stream()
        //             .map(Document::getText)
        //             .collect(Collectors.joining("\n"));
        //
        // Step 3: 逐条验证每个 claim → verifyClaimByContext(claim, context)
        //
        // Step 4: 计算比例
        //
        // 💡 关键优化思考：
        //   - 一次 LLM 调用验证所有 claims vs 逐条调用？哪个更省？
        //     答案：批量验证（一次调用处理所有 claims）更省 token，
        //     但可能降低准确度。建议先逐条验证保证质量。
        //   - 如果 claim 数量很多（>10），可以分批验证
        // ============================================================
        List<String> claims = extractClaims(generatedAnswer);
        if (claims.isEmpty()) {
            log.warn("答案的忠实度第，Faithfulness 返回 0");
            return 0.0;
        }
        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        int supportedCount = 0;
        for (String claim : claims) {
            if (verifyClaimByContext(claim, context)) {
                supportedCount++;
            }
        }
        return (double)supportedCount/claims.size();
        // TODO: 实现 Faithfulness 计算
       // throw new UnsupportedOperationException("TODO: 实现 Faithfulness —— 这是最关键的指标，参考教程 Step 1-3");
    }

    // ================================================================
    // 指标 4：Answer Relevance（答案相关性）
    // ================================================================
    // 问题：答案是否直接回应了问题？
    //
    // 算法思路（RAGAS 原版）：
    //   Step 1: 用 LLM 根据答案反向生成 N 个问题
    //   Step 2: 计算生成的问题与原问题的语义相似度
    //   Step 3: 取平均相似度作为分数
    //
    // 简化版思路：
    //   直接用 LLM 打分："答案是否直接回应了问题？0-10分"
    // ================================================================

    /**
     * 计算 Answer Relevance
     *
     * @param question         用户原始问题
     * @param generatedAnswer  LLM 生成的答案
     * @return [0, 1] 之间的分数
     */
    public double calculateAnswerRelevance(String question, String generatedAnswer) {
        if (question == null || question.isBlank() ||
                generatedAnswer == null || generatedAnswer.isBlank()) {
            return 0.0;
        }

        // Step 1: 让 LLM 根据答案反向生成 3 个问题
        String reverseGenPrompt = """
                请根据以下答案，反向生成 3 个用户可能会问的问题。
                要求：
                - 每个问题一行
                - 问题应该能够被这份答案完整回答
                - 不要加序号或任何前缀

                答案：%s

                生成的问题：""".formatted(generatedAnswer);

        String response = ChatClient.builder(openAiChatModel).build()
                .prompt()
                .user(reverseGenPrompt)
                .call()
                .content();

        if (response == null || response.isBlank()) {
            log.warn("LLM 未能反向生成问题，Answer Relevance 返回 0");
            return 0.0;
        }

        // 按行拆分生成的问题，过滤空行
        List<String> generatedQuestions = new ArrayList<>();
        for (String line : response.split("\n")) {
            String trimmed = line.trim()
                    .replaceFirst("^[\\d]+[\\.\\)、]\\s*", "")  // 去序号
                    .replaceFirst("^[-•]\\s*", "");
            if (!trimmed.isBlank()) {
                generatedQuestions.add(trimmed);
            }
        }

        if (generatedQuestions.isEmpty()) {
            log.warn("未能解析 LLM 反向生成的问题");
            return 0.0;
        }

        // Step 2: 获取原问题的 Embedding
        float[] originalEmbedding = primaryEmbeddingModel.embed(question);

        // Step 3: 对每个生成问题，算它和原问题的余弦相似度，取平均
        double totalSimilarity = 0;
        for (String genQuestion : generatedQuestions) {
            float[] genEmbedding = primaryEmbeddingModel.embed(genQuestion);
            double similarity = cosineSimilarity(originalEmbedding, genEmbedding);
            totalSimilarity += similarity;
            log.debug("生成问题「{}」与原问题的余弦相似度: {}", genQuestion, String.format("%.3f", similarity));
        }

        double relevance = totalSimilarity / generatedQuestions.size();
        log.info("Answer Relevance: {}", String.format("%.2f", relevance));
        return relevance;
    }

    /**
     * 计算两个 Embedding 向量的余弦相似度
     *
     * <p>公式：cos(θ) = (A·B) / (|A| × |B|)
     * <p>取值范围 [-1, 1]，但 Embedding 向量通常非负，实际在 [0, 1]
     *
     * @param a 向量 A
     * @param b 向量 B
     * @return 余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不一致: " + a.length + " vs " + b.length);
        }

        double dotProduct = 0;
        double normA = 0;
        double normB = 0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ================================================================
    // 🛠 辅助方法：你可以直接用的工具函数
    // ================================================================

    /**
     * 用 LLM 判断文档是否与查询相关（方案B —— 精确版）
     *
     * <p>Prompt 设计要点：
     *   - 要求只输出 YES/NO，方便解析
     *   - 给一个"不相关"的例子，帮助 LLM 理解边界
     *
     * @param query    用户问题
     * @param content  文档内容
     * @return true=相关, false=不相关
     */
    private boolean isRelevantByLLM(String query, String content) {
        String prompt = """
                请判断以下文档内容是否与用户问题相关。
                只回答 YES 或 NO，不要解释。

                用户问题：%s
                文档内容：%s
                """.formatted(query, content);

        String response = ChatClient.builder(openAiChatModel).build()
                .prompt()
                .user(prompt)
                .call()
                .content();

        boolean relevant = response != null && response.trim().toUpperCase().contains("YES");
        log.debug("LLM 相关性判断: {} → {}", relevant ? "YES" : "NO",
                content.length() > 50 ? content.substring(0, 50) + "..." : content);
        return relevant;
    }

    /**
     * 从一段文本中提取原子事实声明（claims）
     *
     * <p>Prompt 设计要点：
     *   - "原子" = 不能再拆的最小事实单元
     *   - 每行一个，方便后续按行解析
     *   - 排除主观评价，只提取可验证的事实
     *
     * @param text 待提取的文本（可以是答案或参考答案）
     * @return 声明列表
     */
    private List<String> extractClaims(String text) {
        String prompt = """
                请从以下文本中提取所有原子事实声明（可独立验证的最小事实单元）。
                要求：
                - 每行一个声明
                - 只提取事实性内容，忽略主观评价和礼貌用语
                - 每个声明必须是一个完整的陈述句

                文本：%s

                声明列表：
                """.formatted(text);

        String response = ChatClient.builder(openAiChatModel).build()
                .prompt()
                .user(prompt)
                .call()
                .content();

        if (response == null || response.isBlank()) {
            log.warn("LLM 未返回任何声明");
            return List.of();
        }

        // 按行分割，过滤空行
        List<String> claims = new ArrayList<>();
        for (String line : response.split("\n")) {
            String trimmed = line.trim();
            // 去掉列表符号（1. / - / •）
            trimmed = trimmed.replaceFirst("^[\\d]+[\\.\\)、]\\s*", "")
                    .replaceFirst("^[-•]\\s*", "")
                    .trim();
            if (!trimmed.isEmpty()) {
                claims.add(trimmed);
            }
        }
        log.debug("提取到 {} 条声明", claims.size());
        return claims;
    }

    /**
     * 验证单条声明是否可以从上下文中推断出来
     *
     * @param claim   待验证的声明
     * @param context 检索到的上下文字符串
     * @return true=可推断, false=不可推断（可能是幻觉）
     */
    private boolean verifyClaimByContext(String claim, String context) {
        String prompt = """
                请判断以下声明是否可以从给定上下文中推断出来。
                只回答 YES 或 NO。

                声明：%s
                上下文：%s
                """.formatted(claim, context);

        String response = ChatClient.builder(openAiChatModel).build()
                .prompt()
                .user(prompt)
                .call()
                .content();

        return response != null && response.trim().toUpperCase().contains("YES");
    }

    /**
     * 关键词匹配版的相关性判断（方案A —— 快速版）
     *
     * <p>原理：统计 query 中有多少词出现在文档中。
     * <p>优势：快、零成本。劣势：语义相近但用词不同会漏判。
     *
     * @param query    用户问题
     * @param content  文档内容
     * @param threshold 命中比例阈值（比如 0.3 表示 30% 的词匹配就算相关）
     * @return true=相关
     */
    private boolean isRelevantByKeyword(String query, String content, double threshold) {
        if (query == null || content == null) return false;

        String lowerQuery = query.toLowerCase();
        String lowerContent = content.toLowerCase();

        // 简单分词（按空格和标点分割）
        String[] words = lowerQuery.split("[\\s，。！？、；：\"'（）\\[\\]【】,.!?;:'\"()\\[\\]{}]+");
        if (words.length == 0) return false;

        int hitCount = 0;
        for (String word : words) {
            if (word.length() >= 2 && lowerContent.contains(word)) {  // 忽略单字词
                hitCount++;
            }
        }

        double ratio = (double) hitCount / words.length;
        return ratio >= threshold;
    }

    // ================================================================
    // 🧠 便捷方法：一次性跑完 4 个指标
    // ================================================================

    /**
     * 一站式评估：输入一次对话的所有数据，输出完整评估结果
     *
     * @param question          用户问题
     * @param documents         检索到的文档列表
     * @param generatedAnswer   LLM 生成的答案
     * @param referenceAnswer   参考答案（可选，没有就跳过 Context Recall）
     * @return RagasEvaluationResult 包含所有能计算的指标
     */
    public RagasEvaluationResult evaluate(String question,
                                           List<Document> documents,
                                           String generatedAnswer,
                                           String referenceAnswer) {
        RagasEvaluationResult.RagasEvaluationResultBuilder builder = RagasEvaluationResult.builder();

        // Context Precision 和 Faithfulness 不依赖参考答案，总是可以计算
        builder.contextPrecision(calculateContextPrecision(question, documents));
        builder.faithfulness(calculateFaithfulness(generatedAnswer, documents));

        // Context Recall 需要参考答案
        if (referenceAnswer != null && !referenceAnswer.isBlank()) {
            builder.contextRecall(calculateContextRecall(referenceAnswer, documents));
        }

        // Answer Relevance 只需要问题和答案
        builder.answerRelevance(calculateAnswerRelevance(question, generatedAnswer));

        return builder.build();
    }
}
