package com.qian.qianaiagent.interview.progress;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveSpecManagerEvictionTest {

    @Test
    void evictsEntriesIdleBeyondTtl() {
        ActiveSpecManager manager = new ActiveSpecManager();
        manager.updateSpec("stale", "旧项目描述");
        manager.updateSpec("fresh", "新项目描述");

        long future = System.currentTimeMillis() + Duration.ofHours(3).toMillis();
        int evicted = manager.evictExpired(future, Duration.ofHours(2));

        assertEquals(2, evicted, "两个条目都已空闲超过 2 小时");
        assertFalse(manager.hasSpec("stale"));
        assertFalse(manager.hasSpec("fresh"));
    }

    @Test
    void keepsEntriesWithinTtl() {
        ActiveSpecManager manager = new ActiveSpecManager();
        manager.updateSpec("recent", "项目描述");

        int evicted = manager.evictExpired(System.currentTimeMillis(), Duration.ofHours(2));

        assertEquals(0, evicted);
        assertTrue(manager.hasSpec("recent"));
    }

    @Test
    void touchOnReadExtendsLifetime() throws Exception {
        ActiveSpecManager manager = new ActiveSpecManager();
        manager.updateSpec("c1", "描述");

        Thread.sleep(30);
        manager.getSpec("c1");          // 访问即续期

        // 用「更新后 20ms」作为 now 去淘汰 TTL=10ms：若读访问没有续期，条目早已过期
        long now = System.currentTimeMillis();
        int evicted = manager.evictExpired(now, Duration.ofMillis(10));

        assertEquals(0, evicted, "刚被读取过的条目不应过期");
        assertTrue(manager.hasSpec("c1"));
    }

    @Test
    void removeClearsSingleEntry() {
        ActiveSpecManager manager = new ActiveSpecManager();
        manager.updateSpec("c1", "描述");
        manager.remove("c1");
        assertFalse(manager.hasSpec("c1"));
    }

    @Test
    void negativeOrNullTtlIsNoOp() {
        ActiveSpecManager manager = new ActiveSpecManager();
        manager.updateSpec("c1", "描述");
        assertEquals(0, manager.evictExpired(System.currentTimeMillis(), null));
        assertEquals(0, manager.evictExpired(System.currentTimeMillis(), Duration.ofHours(-1)));
        assertTrue(manager.hasSpec("c1"));
    }

    // ==================================================================
    //  日志口径：逐出 ≠ 清理
    // ==================================================================
    //
    //  落盘之后 evictExpired 只动内存 Map，磁盘文件原样保留、下次 getSpec 会回填。
    //  日志若还写「清理空闲项目描述 N 个」，排障的人会以为数据被删了 ——
    //  这正是本次审查点名的「文档/日志在说谎」。所以直接断言日志文本。

    @Test
    void evictionLogSaysEvictedFromCacheNotCleaned() {
        ActiveSpecManager manager = new ActiveSpecManager();
        manager.updateSpec("stale", "旧项目描述");

        ListAppender<ILoggingEvent> logs = captureLogs();
        try {
            long future = System.currentTimeMillis() + Duration.ofHours(3).toMillis();
            assertEquals(1, manager.evictExpired(future, Duration.ofHours(2)));

            List<String> messages = logs.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
            assertTrue(messages.stream().anyMatch(m -> m.contains("逐出")),
                    "日志必须说明是「逐出内存缓存」: " + messages);
            assertFalse(messages.stream().anyMatch(m -> m.contains("清理空闲项目描述")),
                    "旧口径「清理」会让人以为数据被删，必须消失: " + messages);
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
