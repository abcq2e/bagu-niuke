package com.qian.qianaiagent.interview.progress;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.qian.qianaiagent.config.StorageProperties;
import com.qian.qianaiagent.util.ChatIdValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveSpecManagerPersistenceTest {

    /** 用反射注入 storage 并手动调 init()，复现 Spring 的装配效果。 */
    private ActiveSpecManager newManager(Path root) {
        StorageProperties storage = new StorageProperties();
        storage.setRoot(root.toString());
        storage.setActiveSpec(".active-specs");

        ActiveSpecManager manager = new ActiveSpecManager();
        ReflectionTestUtils.setField(manager, "storage", storage);
        manager.init();
        return manager;
    }

    @Test
    @DisplayName("写入后重建实例（模拟重启）仍能读到项目描述")
    void survivesRestart(@TempDir Path root) {
        ActiveSpecManager first = newManager(root);
        first.updateSpec("chat_1", "我的项目是一个电商秒杀系统");

        // 模拟服务重启：全新实例，内存缓存为空
        ActiveSpecManager second = newManager(root);

        assertThat(second.getSpec("chat_1")).isEqualTo("我的项目是一个电商秒杀系统");
    }

    @Test
    @DisplayName("更新是覆盖语义，重启后读到最新值")
    void overwritesOnRestart(@TempDir Path root) {
        ActiveSpecManager first = newManager(root);
        first.updateSpec("chat_2", "旧项目描述");
        first.updateSpec("chat_2", "新项目描述");

        assertThat(newManager(root).getSpec("chat_2")).isEqualTo("新项目描述");
    }

    @Test
    @DisplayName("未写入过的会话返回 null，不因缺文件报错")
    void returnsNullForUnknownChat(@TempDir Path root) {
        assertThat(newManager(root).getSpec("never_seen")).isNull();
    }

    // ==================================================================
    //  路径穿越：这个测试的写法本身踩过一次坑，别再退回去
    // ==================================================================
    //
    // 上一版写的是「Files.list(specDir) 的每个名字都不含 .. / \」。
    // 它对**空流**恒为真，而 init() 又无条件 createDirectories，于是
    // `assertThat(dir).exists()` 恒真、`allMatch` 恒真 —— 把 fileOf 里的
    // safeFileName 换成裸 chatId（即重新引入路径穿越）它照样全绿。
    //
    // 反过来写：断言**该存在的文件确实存在**。净化一旦失效，落盘位置就变了，
    // 这条必然红；「目录里恰好只有它一个」再堵住「净化文件写了、同时还漏了别的」。

    @Test
    @DisplayName("畸形 chatId 被净化：文件落在目录内、能按同一规则读回、不逃出目录")
    void sanitizesMaliciousChatId(@TempDir Path root) throws Exception {
        String malicious = "../../etc/passwd";
        String safeName = ChatIdValidator.safeFileName(malicious);

        // 前提断言：净化结果本身不含路径元字符（safeFileName 失效时先红在这么直白的地方）
        assertThat(safeName).doesNotContain("..").doesNotContain("/").doesNotContain("\\");

        ActiveSpecManager manager = newManager(root);
        manager.updateSpec(malicious, "恶意内容");

        Path dir = root.resolve(".active-specs");

        // 1. 真正落盘的文件名是净化后的，且就在 .active-specs 里
        Path expected = dir.resolve(safeName + ".json");
        assertThat(expected).exists();

        // 2. 能按同一规则读回（净化前后一致）—— 用全新实例走 loadSpec，绕开内存缓存
        assertThat(newManager(root).getSpec(malicious)).isEqualTo("恶意内容");

        // 3. 目录内恰好只有这一个文件 —— 空的 Files.list 不再能蒙混过关
        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString()))
                    .containsExactly(safeName + ".json");
        }

        // 4. 没有任何东西逃到 .active-specs 之外（真逃逸会在 root 下造出 etc/）
        assertThat(root.resolve("etc")).doesNotExist();
        try (var children = Files.list(root)) {
            assertThat(children.map(p -> p.getFileName().toString()))
                    .containsExactly(".active-specs");
        }
    }

    @Test
    @DisplayName("空白描述不写入，也不改变已有值")
    void ignoresBlankSpec(@TempDir Path root) {
        ActiveSpecManager manager = newManager(root);
        manager.updateSpec("chat_3", "有效描述");
        manager.updateSpec("chat_3", "   ");

        assertThat(manager.getSpec("chat_3")).isEqualTo("有效描述");
    }

    @Test
    @DisplayName("remove 同步删除磁盘文件（含残留的 .tmp）")
    void removeAlsoDeletesFile(@TempDir Path root) throws Exception {
        ActiveSpecManager manager = newManager(root);
        manager.updateSpec("chat_4", "待删除的项目描述");

        Path dir = root.resolve(".active-specs");
        Path file = dir.resolve("chat_4.json");
        assertThat(file).exists();

        // 模拟 saveSpec 的 ATOMIC_MOVE 失败后留在盘上的临时文件
        Path tmp = dir.resolve("chat_4.json.tmp");
        java.nio.file.Files.writeString(tmp, "{}");

        manager.remove("chat_4");

        assertThat(file).doesNotExist();
        assertThat(tmp).doesNotExist();
    }

    @Test
    @DisplayName("remove 后重建实例（模拟重启）不会把描述从磁盘复活")
    void removePreventsResurrection(@TempDir Path root) {
        ActiveSpecManager first = newManager(root);
        first.updateSpec("chat_5", "删除后不该复活");

        first.remove("chat_5");

        // 重启：全新实例内存为空，若磁盘文件还在就会被 loadSpec 复活
        assertThat(newManager(root).getSpec("chat_5")).isNull();
    }

    // ==================================================================
    //  落盘失败的日志级别
    // ==================================================================

    @Test
    @DisplayName("写盘失败记 error —— 用户更新了描述却没落盘是功能回归，不是缓存未命中")
    void saveFailureIsLoggedAtError(@TempDir Path root) throws Exception {
        // 让 specDir 落在一个「普通文件」底下：createDirectories 与后续写文件都必然失败
        Files.writeString(root.resolve("blocker"), "not a directory");

        StorageProperties storage = new StorageProperties();
        storage.setRoot(root.toString());
        storage.setActiveSpec("blocker/sub");

        ActiveSpecManager manager = new ActiveSpecManager();
        ReflectionTestUtils.setField(manager, "storage", storage);
        manager.init();

        ListAppender<ILoggingEvent> logs = captureLogs();
        try {
            manager.updateSpec("chat_savefail", "写不进磁盘的项目描述");

            List<ILoggingEvent> events = logs.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("保存项目描述失败"))
                    .toList();
            assertThat(events)
                    .as("保存失败必须留下痕迹（否则下面两条断言会因为「什么都没记」而假绿）")
                    .isNotEmpty();
            assertThat(events)
                    .as("保存失败是功能回归（重启即失忆），必须是 error")
                    .allMatch(e -> e.getLevel() == Level.ERROR);
        } finally {
            logbackLogger().detachAppender(logs);
        }
    }

    private ListAppender<ILoggingEvent> captureLogs() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger().addAppender(appender);
        return appender;
    }

    private Logger logbackLogger() {
        return (Logger) LoggerFactory.getLogger(ActiveSpecManager.class);
    }
}
