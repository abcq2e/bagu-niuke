package com.qian.qianaiagent.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 存储配置的默认值契约。
 *
 * <p>这里守的是一条硬要求：<b>不配置任何属性时，解析出的路径必须与改造前逐字节一致</b>。
 * 路径一旦漂移，用户的历史数据会「凭空消失」且不报错 —— 只是文件读不到，
 * 所以这个断言比它看起来重要。
 */
class StoragePropertiesTest {

    @Test
    void defaultsMatchThePreRefactorHardcodedPaths() {
        StorageProperties props = new StorageProperties();
        String userDir = System.getProperty("user.dir");

        assertEquals(Paths.get(userDir, "data/chat-memory"), props.chatMemoryPath());
        assertEquals(Paths.get(userDir, "data/evals"), props.evalsPath());
        assertEquals(Paths.get(userDir, ".ability-profiles"), props.abilityProfilesPath());
        assertEquals(Paths.get(userDir, ".quiz-cursor"), props.quizCursorPath());
        assertEquals(Paths.get(userDir, ".review-cursor"), props.reviewCursorPath());
    }

    @Test
    void rootOverrideRebasesEverySubdirectory() {
        StorageProperties props = new StorageProperties();
        props.setRoot("/srv/qian");

        assertEquals(Paths.get("/srv/qian/data/chat-memory"), props.chatMemoryPath());
        assertEquals(Paths.get("/srv/qian/.quiz-cursor"), props.quizCursorPath());
    }

    @Test
    void individualSubdirectoryCanBeOverriddenIndependently() {
        StorageProperties props = new StorageProperties();
        // 用相对子目录而非 "/mnt/..."：后者在 Windows 上会变成盘符相对路径，
        // 断言会依赖平台语义而不是被测逻辑
        props.setChatMemory("custom/chat");

        assertEquals(Paths.get(System.getProperty("user.dir"), "custom", "chat"),
                props.chatMemoryPath());
        // 其余仍走 root
        assertEquals(Paths.get(System.getProperty("user.dir"), "data/evals"), props.evalsPath());
    }

    @Test
    void retentionIsOffByDefault() {
        // 会删数据的策略绝不能在用户没明确开启时自动跑
        assertFalse(new StorageProperties().getRetention().isEnabled());
    }
}
