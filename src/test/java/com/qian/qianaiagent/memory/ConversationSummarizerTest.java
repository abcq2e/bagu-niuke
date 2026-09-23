package com.qian.qianaiagent.memory;

import com.qian.qianaiagent.agent.llm.ResilientChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationSummarizerTest {

    private ConversationSummarizer summarizerReturning(String text) {
        ChatModel model = mock(ChatModel.class);
        when(model.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
        return new ConversationSummarizer(model);
    }

    private List<Message> someMessages() {
        return List.of(
                new UserMessage("我的项目是一个电商秒杀系统"),
                new AssistantMessage("了解，我们聊聊秒杀的超卖问题"));
    }

    @Test
    @DisplayName("正常返回时摘要原样透传")
    void returnsSummaryAsIs() {
        assertThat(summarizerReturning("讨论了秒杀与超卖").summarize(someMessages()))
                .isEqualTo("讨论了秒杀与超卖");
    }

    @Test
    @DisplayName("模型全挂时返回失败提示，绝不把兜底话术当成摘要写进记忆")
    void treatsUnavailableReplyAsFailure() {
        String summary = summarizerReturning(ResilientChatModel.UNAVAILABLE_REPLY)
                .summarize(someMessages());

        assertThat(summary)
                .as("兜底话术会随之后的每一轮对话注入上下文，绝不能当成摘要存下来")
                .doesNotContain(ResilientChatModel.UNAVAILABLE_REPLY)
                .contains("摘要生成失败");
    }

    @Test
    @DisplayName("空消息列表不调用模型，直接返回空串")
    void returnsEmptyForNoMessages() {
        assertThat(summarizerReturning("不该被用到").summarize(List.of())).isEmpty();
    }
}
