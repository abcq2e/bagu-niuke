package com.qian.qianaiagent.chatmemory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话摘要器 —— 将历史对话压缩为结构化摘要
 * <p>
 * 用在对话轮数超过窗口时，把早期对话提炼为要点，与最近 N 轮原文一起传给模型。
 * 保证模型既能看到完整近期上下文，又能记住早期对话的关键信息。
 */
@Slf4j
@Component
public class ConversationSummarizer {

    private final ChatClient chatClient;

    private static final String SUMMARY_PROMPT = """
            你是一个对话摘要助手。请把以下面试对话压缩为简洁的摘要，保留关键信息。

            要求：
            1. 列出讨论了哪些技术知识点
            2. 记录了哪些关键概念和原理
            3. 有哪些重要的代码示例或结论

            用中文，控制在 300 字以内。不要加客套话，直接输出要点。

            对话内容：
            %s

            摘要：""";

    public ConversationSummarizer(ChatModel openAiChatModel) {
        this.chatClient = ChatClient.builder(openAiChatModel).build();
    }

    /**
     * 将一段历史对话压缩为摘要
     *
     * @param messages 需要摘要的消息列表
     * @return 摘要文本
     */
    public String summarize(List<Message> messages) {
        if (messages.isEmpty()) {
            return "";
        }

        // 拼接对话文本
        String conversationText = messages.stream()
                .map(m -> {
                    String role = m.getMessageType().name();
                    String text = m.getText();
                    if (text == null || text.isBlank()) {
                        return role + ": [非文本内容]";
                    }
                    // 截断过长的单条消息，控制摘要输入 token
                    String truncated = text.length() > 500 ? text.substring(0, 500) + "…" : text;
                    return role + ": " + truncated;
                })
                .collect(Collectors.joining("\n"));

        String prompt = SUMMARY_PROMPT.formatted(conversationText);

        try {
            String summary = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            log.info("对话摘要完成，原始 {} 条消息 → {} 字摘要", messages.size(),
                    summary != null ? summary.length() : 0);
            return summary != null ? summary : "";
        } catch (Exception e) {
            log.error("对话摘要生成失败: {}", e.getMessage());
            // 降级：返回简单的统计信息
            return "（历史对话 " + messages.size() + " 条消息，摘要生成失败）";
        }
    }
}
