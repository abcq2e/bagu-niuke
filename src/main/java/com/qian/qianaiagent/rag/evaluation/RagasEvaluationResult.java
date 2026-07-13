package com.qian.qianaiagent.rag.evaluation;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * RAGAS 评估结果 —— 4 个核心指标的一次评估快照
 *
 * <p>每个字段范围 [0, 1]，0 = 最差，1 = 最好：
 * <pre>
 *   检索质量（输入侧）：
 *     - contextPrecision: 检索到的文档中，相关的排在前面了吗？
 *     - contextRecall:    回答问题需要的信息，检索到了多少？

 *   生成质量（输出侧）：
 *     - faithfulness:     LLM 的答案有没有瞎编？能追溯到上下文吗？
 *     - answerRelevance:  答案是否直接回应了问题？
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagasEvaluationResult {

    /** 上下文精确率 [0, 1] —— 相关文档是否排在前面 */
    private double contextPrecision;

    /** 上下文召回率 [0, 1] —— 需要的信息是否都检索到了 */
    private double contextRecall;

    /** 忠实度 [0, 1] —— 答案是否有据可查（核心指标） */
    private double faithfulness;

    /** 答案相关性 [0, 1] —— 是否答为所问 */
    private double answerRelevance;

    /** 评估的问题原文（调试用） */
    private String question;

    @Override
    public String toString() {
        return """
                ╔══════════════════════════════════════╗
                ║       RAGAS  评估结果                  ║
                ╠══════════════════════════════════════╣
                ║ Context Precision  │ %5.1f%%         ║
                ║ Context Recall     │ %5.1f%%         ║
                ║ Faithfulness       │ %5.1f%%         ║
                ║ Answer Relevance   │ %5.1f%%         ║
                ╚══════════════════════════════════════╝
                """.formatted(
                        contextPrecision * 100,
                        contextRecall * 100,
                        faithfulness * 100,
                        answerRelevance * 100
                );
    }
}