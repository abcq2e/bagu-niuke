package com.qian.qianaiagent.config;

import com.qian.qianaiagent.chatmemory.ConversationSummarizer;
import com.qian.qianaiagent.chatmemory.FileBasedChatMemory;
import com.qian.qianaiagent.chatmemory.SummarizingChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆配置
 * <p>
 * 双层架构：
 * <ul>
 *   <li>{@link FileBasedChatMemory}：底层文件持久化，完整历史不丢失</li>
 *   <li>{@link SummarizingChatMemory}：wrapper 层，超过 40 条消息时自动摘要早期对话，
 *       传给模型的是 [摘要] + [最近 20 轮原文]，节省 token 的同时保留关键上下文</li>
 * </ul>
 * FileBasedChatMemory 保留为独立 Bean，供管理接口（列表/导出/删除）直接使用原始数据。
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public FileBasedChatMemory fileBasedChatMemory() {
        String fileDir = System.getProperty("user.dir") + "/data/chat-memory";
        return new FileBasedChatMemory(fileDir);
    }
    @Bean
    public ChatMemory chatMemory(FileBasedChatMemory fileBasedChatMemory,
                                  ConversationSummarizer conversationSummarizer) {
        return new SummarizingChatMemory(fileBasedChatMemory, conversationSummarizer);
    }
}
