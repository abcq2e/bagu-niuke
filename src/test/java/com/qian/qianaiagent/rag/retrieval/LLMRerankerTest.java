package com.qian.qianaiagent.rag.retrieval;

import com.qian.qianaiagent.agent.llm.ResilientChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code LLMReranker} 拿到「兜底话术」时必须穿出它自己的降级分支。
 *
 * <p>这个类的失败处理分两层，都<b>刻意</b>吞掉了异常：单批打分失败保留原序、
 * 整体调用失败退回原序前 N 条。对「某批没解析好」这是对的（尽力而为），
 * 但对「模型整体不可用」就是错的：每一批都会拿到同一段兜底话术，
 * 整次重排退化成「不看分数、保持原序」的哑操作，日志只是一行普通的 warn，
 * 调用方完全无感。所以兜底话术走专属异常类型，两层都只放行它。
 */
class LLMRerankerTest {

    /** 6 条文档 = 2 批（BATCH_SIZE=5），且 6 > topN=3，保证真的会去调模型 */
    private List<Document> documents() {
        List<Document> docs = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            docs.add(new Document("文档内容 " + i));
        }
        return docs;
    }

    /**
     * 用真实实现而不是 Mockito mock 当替身。
     *
     * <p>{@code LLMReranker} 调的是 {@code chatModel.call(String)}，它是接口的
     * {@code default} 方法（内部再转成 {@code call(Prompt)}）。Mockito 会把 default 方法
     * 一起 mock 掉、直接返回 null，桩在 {@code call(Prompt)} 上的返回值永远不会被走到 ——
     * 测试会「因为没调用到模型」而假绿。真实匿名类则保留 default 方法的转发，
     * 与生产里 {@code ResilientChatModel} 被调用的方式一致。
     */
    private LLMReranker rerankerReturning(String text) {
        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))));
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return response;
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(response);
            }
        };
        return new LLMReranker(chatModel);
    }

    @Test
    @DisplayName("模型返回兜底话术时 rerank 抛异常，而不是静默保持原序")
    void rerankThrowsOnUnavailableReply() {
        LLMReranker reranker = rerankerReturning(ResilientChatModel.UNAVAILABLE_REPLY);

        assertThatThrownBy(() -> reranker.rerank("用户查询", documents(), 3))
                .as("兜底话术必须穿出 scoreBatch 与 rerank 两层降级 catch")
                .isInstanceOf(ResilientChatModel.UnavailableReplyException.class)
                .hasMessageContaining("兜底话术");
    }

    @Test
    @DisplayName("对照：普通的分数解析失败仍走既有降级（保留原序，不抛异常）")
    void parseFailureStillDegradesSilently() {
        // 证明确属「兜底话术」的那条支路是特例：LLM 输出乱七八糟（不是兜底话术）时，
        // 既有契约不变 —— 退回原始排序前 topN 条。
        LLMReranker reranker = rerankerReturning("模型今天不太想打分");

        List<Document> result = reranker.rerank("用户查询", documents(), 3);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getText()).isEqualTo("文档内容 1");
    }
}
