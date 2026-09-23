package com.qian.qianaiagent.interview.progress;

import com.qian.qianaiagent.config.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    @DisplayName("畸形 chatId 被净化，不会逃出目录")
    void sanitizesMaliciousChatId(@TempDir Path root) {
        ActiveSpecManager manager = newManager(root);
        manager.updateSpec("../../etc/passwd", "恶意内容");

        // 净化后文件名不含路径分隔符，且能按同一规则读回
        Path dir = root.resolve(".active-specs");
        assertThat(dir).exists();
        try (var files = java.nio.file.Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString()))
                    .allMatch(name -> !name.contains("..") && !name.contains("/") && !name.contains("\\"));
        } catch (Exception e) {
            throw new AssertionError(e);
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
}
