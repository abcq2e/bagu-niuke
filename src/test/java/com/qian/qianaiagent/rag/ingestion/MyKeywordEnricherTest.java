package com.qian.qianaiagent.rag.ingestion;

import com.qian.qianaiagent.agent.llm.ResilientChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code MyKeywordEnricher} 拿到「兜底话术」时必须抛异常，而不是把它当关键词写进向量库。
 *
 * <p>回归背景：{@code KeywordMetadataEnricher} 逐条文档调 {@code ChatModel.call(...)}，
 * 把返回文本当关键词解析。模型全挂时返回的是兜底话术 —— 它会被切碎成「关键词」
 * 塞进 {@code metadata}，然后<b>随向量一起持久化</b>。这是「写错了要重跑 ETL 才能修」
 * 的污染，而在检索侧只表现为召回变差，没有任何报错。
 *
 * <p><b>这是恢复既有行为，不是新增行为</b>：本次装配改动之前，这里注入的是裸
 * {@code openAiChatModel}，模型挂掉时 Spring AI 直接抛异常，异常一路冒到
 * {@code QuizVectorStoreConfig.quizVectorStore()} 工厂方法，「摄入失败」是响亮的。
 * 韧性装饰器把那个异常换成了返回值，这里把它换回异常。
 *
 * <p>注：{@code enrichDocuments} 当前<b>还没有生产调用方</b>
 * （{@code QuizVectorStoreConfig} 只注入未调用），所以这个修复目前是「防患于未然」；
 * 但一旦接上摄入流水线，它就是唯一拦得住持久化污染的地方。
 */
class MyKeywordEnricherTest {

    private static final String REAL_KEYWORDS = "Spring, 事务, 传播行为, 隔离级别, AOP";

    private ChatModel chatModelReturning(String text) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
        return chatModel;
    }

    @Test
    @DisplayName("模型返回兜底话术时 enrichDocuments 抛异常，不落脏元数据")
    void enrichThrowsOnUnavailableReply() {
        MyKeywordEnricher enricher = new MyKeywordEnricher(
                chatModelReturning(ResilientChatModel.UNAVAILABLE_REPLY));

        assertThatThrownBy(() -> enricher.enrichDocuments(List.of(new Document("一段文档内容"))))
                .as("兜底话术必须抛异常，否则会被当成关键词写进向量库 metadata")
                .isInstanceOf(ResilientChatModel.UnavailableReplyException.class)
                .hasMessageContaining("兜底话术");
    }

    @Test
    @DisplayName("对照：模型正常返回关键词时装饰器不影响结果")
    void decoratorDoesNotBreakHappyPath() {
        MyKeywordEnricher enricher = new MyKeywordEnricher(chatModelReturning(REAL_KEYWORDS));

        List<Document> enriched = enricher.enrichDocuments(List.of(new Document("一段文档内容")));

        assertThat(enriched).hasSize(1);
        assertThat(enriched.get(0).getMetadata())
                .containsKey("excerpt_keywords");
        assertThat(String.valueOf(enriched.get(0).getMetadata().get("excerpt_keywords")))
                .contains("Spring");
    }
}
