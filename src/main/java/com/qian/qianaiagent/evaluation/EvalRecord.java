package com.qian.qianaiagent.evaluation;

import com.qian.qianaiagent.rag.evaluation.RagasEvaluationResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 一次运行期评估的记录（落盘到 data/evals/{chatId}.json，按会话累积）。
 *
 * <p>type=agent：评估 Agent 链路，携带 {@link RubricScorer.RubricResult}
 * （LLM 四维裁判：推理/幻觉/完整性/工具使用，总分 0-100 + 幻觉率）。
 * <p>type=rag：评估面试/RAG 链路，携带 {@link RagasEvaluationResult}
 * （contextPrecision/faithfulness/answerRelevance 等 [0,1] 分，contextRecall 因无参考答案不产）。
 *
 * <p>两个结果对象各自内嵌而非平铺字段，避免同名语义冲突：
 * agent 的 faithfulness 是 0-25 的 int，rag 的 faithfulness 是 0-1 的 double。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalRecord {

    /** "agent" | "rag" */
    private String type;

    /** 会话 ID（与聊天链路同一 chatId） */
    private String chatId;

    /** 评估完成时间 */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** agent=用户消息；rag=题目题干（effectiveEvalStem） */
    private String question;

    /** 回答/点评原文摘要（截断，列表页预览用） */
    private String answerExcerpt;

    /** type=agent 时填充 */
    private RubricScorer.RubricResult rubric;

    /** agent 执行轨迹文本（serializeTrace 结果，截断） */
    private String traceText;

    /** type=rag 时填充 */
    private RagasEvaluationResult ragas;
}
