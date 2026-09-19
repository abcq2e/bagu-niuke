package com.qian.qianaiagent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 验证 {@code application.yml} 里的 {@code qian.storage.*} 真的能绑定到 {@link StorageProperties}。
 *
 * <p>为什么单独写这个：整个应用上下文需要 PostgreSQL 才能起来（本地没起就会失败），
 * 所以「配置真的绑上了吗」在实际运行中是个盲区 —— YAML 缩进写错、属性名拼错、
 * 或 {@code ${user.dir}} 解析不了，都要等部署才暴露。这里绕开数据库直接做绑定，
 * 把这类错误挡在提交之前。
 */
class StoragePropertiesBindingTest {

    private StorageProperties bindFromApplicationYml() throws Exception {
        MutablePropertySources sources = new MutablePropertySources();
        List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        loaded.forEach(sources::addLast);

        Binder binder = new Binder(
                ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources));

        return binder.bind("qian.storage", Bindable.of(StorageProperties.class)).orElse(null);
    }

    @Test
    void applicationYmlBindsStorageProperties() throws Exception {
        StorageProperties props = bindFromApplicationYml();
        assertNotNull(props, "application.yml 中的 qian.storage 应能绑定到 StorageProperties");
    }

    /**
     * root 刻意不在 yml 里配置 —— 一旦写成 {@code root: ${user.dir}}，占位符解析失败时
     * 值会原样停留在字符串 {@code "${user.dir}"}，变成一个极难排查的路径错误。
     * 这里守住「未配置时回落到 Java 默认值」。
     */
    @Test
    void rootFallsBackToJavaDefaultWhenAbsentFromYml() throws Exception {
        StorageProperties props = bindFromApplicationYml();
        assertNotNull(props);

        String userDir = System.getProperty("user.dir");
        assertFalse(props.getRoot().contains("${"),
                "root 不应是未解析的占位符字面量，实际: " + props.getRoot());
        assertEquals(Paths.get(userDir), Paths.get(props.getRoot()));

        // 各子目录（yml 里显式配置）解析结果必须与改造前的硬编码字面量一致
        assertEquals(Paths.get(userDir, "data/chat-memory"), props.chatMemoryPath());
        assertEquals(Paths.get(userDir, "data/evals"), props.evalsPath());
        assertEquals(Paths.get(userDir, ".quiz-cursor"), props.quizCursorPath());
        assertEquals(Paths.get(userDir, ".review-cursor"), props.reviewCursorPath());
        assertEquals(Paths.get(userDir, ".ability-profiles"), props.abilityProfilesPath());
    }

    @Test
    void ymlRetentionDefaultsToDisabled() throws Exception {
        StorageProperties props = bindFromApplicationYml();
        assertNotNull(props.getRetention());
        // 会删数据的策略默认必须是关的
        assertFalse(props.getRetention().isEnabled());
        assertEquals(100, props.getRetention().getMaxConversationsPerUser());
        assertEquals(60, props.getRetention().getGracePeriodMinutes());
    }
}
