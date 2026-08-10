package com.qian.qianaiagent.app;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SequentialRotationServiceTest {

    @Autowired
    private SequentialRotationService service;

    @Autowired
    private TopicDocumentCache cache;

    private String key;

    @AfterEach
    void cleanup() throws Exception {
        if (key != null) {
            Files.deleteIfExists(Path.of(".quiz-cursor", key + ".json"));
        }
    }

    @Test
    void newSessionStartsAtZeroWithoutHardcodedProgress() {
        key = "chat_test_fresh_" + System.currentTimeMillis();
        service.initSession(key, cache);

        assertEquals(0, service.getCurrentRound(key));
        assertEquals("Java基础与集合", service.currentTopic(key));
        assertEquals(0, service.getTotalAskedThisDirection(key));
    }

    @Test
    void rollbackRestoresAskedIndexAfterMark() {
        key = "chat_test_rollback_" + System.currentTimeMillis();
        service.initSession(key, cache);
        int before = service.getTotalAskedThisDirection(key);
        SequentialRotationService.SequentialCursor snap = service.snapshotCursor(key);

        service.markQuestionAsked(key);
        assertEquals(before + 1, service.getTotalAskedThisDirection(key));

        service.restoreCursor(snap);
        assertEquals(before, service.getTotalAskedThisDirection(key));
        assertEquals("Java基础与集合", service.currentTopic(key));
    }

    @Test
    void skipThenMarkConsumesFirstQuestionOfNewDirection() {
        key = "chat_test_skip_" + System.currentTimeMillis();
        service.initSession(key, cache);
        assertEquals("Java基础与集合", service.currentTopic(key));

        service.skipCurrentDirection(key);
        assertEquals("JVM", service.currentTopic(key));
        int[] range = service.getCurrentQuestionRange(key);
        assertNotNull(range);
        assertEquals(0, range[0]);

        service.markQuestionAsked(key);
        int[] after = service.getCurrentQuestionRange(key);
        assertNotNull(after);
        assertEquals(1, after[0]);
    }

    @Test
    void lastShownStemSurvivesReload() throws Exception {
        key = "chat_test_lastshown_" + System.currentTimeMillis();
        service.initSession(key, cache);
        service.saveLastShown(key, "计算机网络", "除了 ARP 协议还有什么地址转换手段？");

        // 模拟重启：清内存会话后再 init
        service.evictSessionForTest(key);
        service.initSession(key, cache);

        assertEquals("除了 ARP 协议还有什么地址转换手段？", service.getLastShownStem(key));
        assertEquals("计算机网络", service.getLastShownTopic(key));
    }
}
