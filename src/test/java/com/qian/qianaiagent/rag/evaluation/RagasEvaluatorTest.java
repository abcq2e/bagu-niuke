package com.qian.qianaiagent.rag.evaluation;

import com.qian.qianaiagent.agent.llm.ResilientChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code RagasEvaluator} 拿到「兜底话术」时必须失败，而不是拿它算 RAGAS 指标。
 *
 * <p>回归背景：兜底话术会被当成「反向生成的问题」「提取出的声明」参与计算，
 * 产出一串<b>看起来正常</b>的分数（如 faithfulness=0.0、answerRelevance≈0）
 * 并写进 {@code data/evals/*.json}。这些数字与「RAG 质量真的差」无法区分，
 * 会污染 baseline 与质量趋势判断。
 */
class RagasEvaluatorTest {

    private ChatModel chatModelReturning(String text) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
        return chatModel;
    }

    private RagasEvaluator evaluatorReturning(String text) {
        RagasEvaluator evaluator = new RagasEvaluator(chatModelReturning(text));
        // Answer Relevance 还要用 EmbeddingModel。生产里它是 @Resource 注入的，
        // 单测里手动塞一个 mock —— 否则「没有拦截」时会以 NPE 的形式变红，
        // 那就分不清「守卫失效」和「测试自己没装配好」了。
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(new float[] {1f, 0f});
        ReflectionTestUtils.setField(evaluator, "primaryEmbeddingModel", embeddingModel);
        return evaluator;
    }

    @Test
    @DisplayName("Faithfulness：模型返回兜底话术时抛异常（声明提取这一步就失败）")
    void faithfulnessThrowsOnUnavailableReply() {
        RagasEvaluator evaluator = evaluatorReturning(ResilientChatModel.UNAVAILABLE_REPLY);
        List<Document> docs = List.of(new Document("检索到的上下文"));

        assertThatThrownBy(() -> evaluator.calculateFaithfulness("模型的答案", docs))
                .isInstanceOf(ResilientChatModel.UnavailableReplyException.class)
                .hasMessageContaining("兜底话术");
    }

    @Test
    @DisplayName("Answer Relevance：模型返回兜底话术时抛异常，不会算出一份假的相似度")
    void answerRelevanceThrowsOnUnavailableReply() {
        RagasEvaluator evaluator = evaluatorReturning(ResilientChatModel.UNAVAILABLE_REPLY);

        assertThatThrownBy(() -> evaluator.calculateAnswerRelevance("原问题", "模型的答案"))
                .isInstanceOf(ResilientChatModel.UnavailableReplyException.class)
                .hasMessageContaining("兜底话术");
    }

    @Test
    @DisplayName("evaluate()：兜底话术让整次评估失败，而不是落一份假指标")
    void evaluateThrowsOnUnavailableReply() {
        RagasEvaluator evaluator = evaluatorReturning(ResilientChatModel.UNAVAILABLE_REPLY);

        assertThatThrownBy(() -> evaluator.evaluate(
                "原问题", List.of(new Document("检索到的上下文")), "模型的答案", null))
                .isInstanceOf(ResilientChatModel.UnavailableReplyException.class);
    }

    @Test
    @DisplayName("对照：模型正常返回时评估照常跑完（异常只针对兜底话术）")
    void normalReplyStillEvaluates() {
        // 让 LLM 对每种提示都回一句正常文本：声明提取会拿到一行声明，
        // 声明验证答不出 YES → faithfulness=0，但流程本身必须跑完、不抛异常。
        RagasEvaluator evaluator = evaluatorReturning("这是一条声明");

        double faithfulness = evaluator.calculateFaithfulness(
                "模型的答案", List.of(new Document("检索到的上下文")));

        assertThat(faithfulness).isBetween(0.0, 1.0);
    }
}
