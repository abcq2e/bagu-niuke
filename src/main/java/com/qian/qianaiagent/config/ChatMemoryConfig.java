package com.qian.qianaiagent.config;

import com.qian.qianaiagent.memory.ConversationSummarizer;
import com.qian.qianaiagent.memory.FileBasedChatMemory;
import com.qian.qianaiagent.memory.SummarizingChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
 *
 * <p>🔴 {@code @EnableConfigurationProperties(MemoryProperties.class)} 不能省：
 * 主类上的 {@code @ConfigurationPropertiesScan} 只在<b>全量启动</b>时生效，
 * 像 {@code StorageWiringTest} 那样用 {@code ApplicationContextRunner} 单独加载本配置的切片里
 * 不做组件扫描，{@code MemoryProperties} 就没人注册，{@code chatMemory} Bean 直接装配失败。
 * 两处注册同名同类型，Spring 不会产生重复 Bean。
 */
@Configuration
@EnableConfigurationProperties(MemoryProperties.class)
public class ChatMemoryConfig {

    @Bean
    public FileBasedChatMemory fileBasedChatMemory(StorageProperties storage) {
        // 🔴 走统一配置而非硬编码 System.getProperty("user.dir")：
        // user.dir 是进程工作目录，IDE / java -jar / 容器三种启动方式可能不同，
        // 会让历史数据「凭空消失」且不报错
        return new FileBasedChatMemory(storage.chatMemoryPath().toString());
    }
    @Bean
    public ChatMemory chatMemory(FileBasedChatMemory fileBasedChatMemory,
                                  ConversationSummarizer conversationSummarizer,
                                  MemoryProperties memoryProperties) {
        MemoryProperties.Window window = memoryProperties.getWindow();
        return new SummarizingChatMemory(fileBasedChatMemory, conversationSummarizer,
                window.getMaxMessages(), window.getMaxTokens());
    }
}
