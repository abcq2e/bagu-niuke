package com.qian.qianaiagent.evaluation;

import com.qian.qianaiagent.agent.llm.ResilientChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code RubricScorer} 拿到「兜底话术」时必须失败，而不是把它当成评分 JSON 去解析。
 *
 * <p>回归背景：{@code ResilientChatModel} 在主备全挂时<b>返回文案而不抛异常</b>
 * （为 SSE 前端契约而做的有意设计）。评估路径不是 SSE，把那段文案当模型输出往下走，
 * 会得到一份 {@code totalScore=0, overallComment="JSON 解析失败: ..."} 的评分记录 ——
 * 格式合法、能落盘、能进 baseline，只是内容全是假的。
 * 改动前这里注入的是裸模型，模型挂掉会直接抛异常，所以这是本次装配改动引入的回归。
 */
class RubricScorerTest {

    private ChatModel chatModelReturning(String text) {
        ChatModel chatModel = mock(ChatModel.class);
        // ChatClient.builder(...) 会读一次默认 options；给个非 null 免得走到空指针上，
        // 与本测试要验证的行为无关。
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
        return chatModel;
    }

    @Test
    @DisplayName("模型返回兜底话术时抛异常，而不是产出一份假的 0 分评分")
    void throwsWhenModelReturnsUnavailableReply() {
        RubricScorer scorer = new RubricScorer(
                chatModelReturning(ResilientChatModel.UNAVAILABLE_REPLY));

        assertThatThrownBy(() -> scorer.callLLMAndParse("给这次 Agent 执行打分"))
                .as("兜底话术不是评分 JSON，必须响亮失败（调用方会 catch Exception 并放弃本次评分）")
                .isInstanceOf(ResilientChatModel.UnavailableReplyException.class)
                .hasMessageContaining("兜底话术");
    }

    @Test
    @DisplayName("对照：模型返回真正（但格式不对）的内容时，仍走既有的『解析失败』软处理")
    void stillSoftlyHandlesGenuineButUnparsableReply() {
        // 说明异常只针对兜底话术，没有把「LLM 输出不规范」一并升级为异常 ——
        // 那是既有行为，评测系统要靠它容错。
        RubricScorer scorer = new RubricScorer(chatModelReturning("这不是 JSON"));

        RubricScorer.RubricResult result = scorer.callLLMAndParse("给这次 Agent 执行打分");

        assertThat(result.getTotalScore()).isZero();
        assertThat(result.getOverallComment()).contains("JSON 解析失败");
    }
}
