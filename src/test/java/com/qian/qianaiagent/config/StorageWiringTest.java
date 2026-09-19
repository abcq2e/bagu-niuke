package com.qian.qianaiagent.config;

import com.qian.qianaiagent.memory.ConversationAccess;
import com.qian.qianaiagent.memory.ConversationSummarizer;
import com.qian.qianaiagent.memory.FileBasedChatMemory;
import com.qian.qianaiagent.memory.SummarizingChatMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 存储相关的 Spring 装配切片测试。
 *
 * <p>为什么需要：全量 {@code @SpringBootTest} 需要 PostgreSQL 才能启动（本地未起会直接失败），
 * 所以「这些 Bean 到底能不能装配起来」在本地是个盲区 —— {@code StorageProperties} 没被扫描到、
 * {@code @Resource} 名字对不上这类问题会一直潜伏到部署才炸。这里用 {@link ApplicationContextRunner}
 * 只加载相关配置，绕开数据库把装配验证掉。
 */
class StorageWiringTest {

    @TempDir
    Path storageRoot;

    @Configuration
    @EnableConfigurationProperties(StorageProperties.class)
    // ConversationAccess / ConversationRetentionSweeper 是 @Component，
    // ApplicationContextRunner 不做组件扫描，必须显式导入才验证得到
    @Import({ChatMemoryConfig.class, ConversationAccess.class, ConversationRetentionSweeper.class})
    static class TestConfig {

        /** ConversationSummarizer 需要一个 ChatModel，切片测试里不需要真的调 LLM */
        @Bean
        ConversationSummarizer conversationSummarizer() {
            return new ConversationSummarizer(mock(ChatModel.class));
        }
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues("qian.storage.root=" + storageRoot.toString());
    }

    @Test
    void storagePropertiesIsPickableUpAsABean() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(StorageProperties.class);
            assertThat(context.getBean(StorageProperties.class).chatMemoryPath())
                    .isEqualTo(storageRoot.resolve("data/chat-memory"));
        });
    }

    @Test
    void chatMemoryBeansWireUnderTheNamesOtherClassesInject() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            // ConversationController 等按名字注入 fileBasedChatMemory，名字不能变
            assertThat(context).hasBean("fileBasedChatMemory");
            assertThat(context.getBean("fileBasedChatMemory")).isInstanceOf(FileBasedChatMemory.class);
            // ChatMemory 接口的实现仍是包装层（注意 FileBasedChatMemory 自己也是 ChatMemory，
            // 所以按类型查不是单例，必须按名字查）
            assertThat(context).hasBean("chatMemory");
            assertThat(context.getBean("chatMemory")).isInstanceOf(SummarizingChatMemory.class);
        });
    }

    @Test
    void conversationAccessWiresAgainstTheMemoryBean() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            ConversationAccess access = context.getBean(ConversationAccess.class);

            // 守卫真的连到了同一个存储实例上
            assertThat(access.startSession("chat_wiring", 7L)).isTrue();
            assertThat(access.startSession("chat_wiring", 9L)).isFalse();
        });
    }

    @Test
    void sweeperWiresAndDoesNothingWhileDisabled() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            ConversationRetentionSweeper sweeper = context.getBean(ConversationRetentionSweeper.class);

            // 默认关闭：即使有超额会话也不该删
            context.getBean(FileBasedChatMemory.class).startSession("chat_1", 7L);
            sweeper.sweep();
            assertThat(context.getBean(FileBasedChatMemory.class).ownerOf("chat_1")).isEqualTo(7L);
        });
    }

    @Test
    void retentionSweepsWhenEnabledByConfiguration() {
        runner()
                .withPropertyValues(
                        "qian.storage.retention.enabled=true",
                        "qian.storage.retention.grace-period-minutes=0",
                        "qian.storage.retention.max-age-days=30")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FileBasedChatMemory memory = context.getBean(FileBasedChatMemory.class);
                    memory.startSession("chat_old", 7L);

                    context.getBean(ConversationRetentionSweeper.class).sweep();

                    // 无数据的会话没有可删的东西，但流程必须跑通且不抛异常
                    assertThat(memory.ownerOf("chat_old")).isEqualTo(7L);
                });
    }
}
