package com.qian.qianaiagent.rag.retrieval;

import com.qian.qianaiagent.agent.llm.ResilientChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 查询重写器的兜底话术防护。
 *
 * <p>LLM 全挂时 {@code ResilientChatModel} 返回兜底话术而不是抛异常。
 * 若不识别，这串错误文案会被当成「重写后的查询」甚至「假设答案」去检索 ——
 * 后者更糟：HyDESearchService 的 try/catch 只兜异常，兜不住这个返回值。
 */
class QueryRewriterTest {

    private QueryRewriter rewriterReturning(String text) {
        ChatModel model = mock(ChatModel.class);
        when(model.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));

        QueryRewriter rewriter = new QueryRewriter(model);
        // 这两个字段是 @Value 注入的，直接 new 出来时是 0（int 默认值）——
        // 不设的话 maxQueryLength=0 会把所有输入都判成「过长」直接短路，压根走不到 LLM。
        ReflectionTestUtils.setField(rewriter, "minQueryLength", 1);
        ReflectionTestUtils.setField(rewriter, "maxQueryLength", 10_000);
        return rewriter;
    }

    @Test
    @DisplayName("正常重写时用 LLM 的返回")
    void usesRewrittenQuery() {
        assertThat(rewriterReturning("Spring AI 的核心抽象有哪些")
                .doQueryRewrite("spring ai 是啥"))
                .isEqualTo("Spring AI 的核心抽象有哪些");
    }

    @Test
    @DisplayName("LLM 全挂时降级为原句，绝不把兜底话术当查询、也不缓存它")
    void degradesToOriginalQueryOnUnavailableReply() {
        QueryRewriter rewriter = rewriterReturning(ResilientChatModel.UNAVAILABLE_REPLY);

        assertThat(rewriter.doQueryRewrite("spring ai 是啥"))
                .as("兜底话术会被拿去向量检索，必须降级为原句")
                .isEqualTo("spring ai 是啥");

        // 第二次调用走的仍是 LLM 分支（说明错误文案没被写进缓存）
        assertThat(rewriter.doQueryRewrite("spring ai 是啥")).isEqualTo("spring ai 是啥");
    }

    @Test
    @DisplayName("HyDE：LLM 全挂时返回 null，让调用方降级为原始问题检索")
    void hydeReturnsNullOnUnavailableReply() {
        assertThat(rewriterReturning(ResilientChatModel.UNAVAILABLE_REPLY)
                .generateHypotheticalAnswer("什么是 Spring AI"))
                .as("HyDESearchService 只对 null/异常降级，拿兜底话术去检索会检索到不相干内容")
                .isNull();
    }

    @Test
    @DisplayName("HyDE：正常时原样返回假设答案")
    void hydeReturnsAnswerWhenHealthy() {
        assertThat(rewriterReturning("Spring AI 是 Spring 生态的 AI 应用框架")
                .generateHypotheticalAnswer("什么是 Spring AI"))
                .isEqualTo("Spring AI 是 Spring 生态的 AI 应用框架");
    }
}
