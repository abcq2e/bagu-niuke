package com.qian.qianaiagent.interview.progress;

import org.junit.jupiter.api.Test;

import java.time.Duration;

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
}
