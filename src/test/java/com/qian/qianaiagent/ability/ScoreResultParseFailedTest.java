package com.qian.qianaiagent.ability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ScoreResultParseFailedTest {

    private UserAbilityService service;

    @BeforeEach
    void setUp() {
        service = new UserAbilityService(mock(ChatModel.class));
        // 🔴 必须显式注入：构造函数不设 objectMapper，不注入则解析路径会因 NPE
        //    而非 JSON 解析失败走到 fallback，测试会因错误的原因通过
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void parseFailedDefaultsToFalse() {
        assertFalse(new UserAbilityProfile.ScoreResult().isParseFailed());
    }

    @Test
    void unparsableJsonMarksParseFailed() {
        UserAbilityProfile.ScoreResult sr = ReflectionTestUtils.invokeMethod(
                service, "parseScoreResult", "这根本不是 JSON", "Java基础与集合");
        assertNotNull(sr);
        assertTrue(sr.isParseFailed(), "解析失败必须标记为不可信");
    }

    @Test
    void parsableJsonDoesNotMarkParseFailed() {
        UserAbilityProfile.ScoreResult sr = ReflectionTestUtils.invokeMethod(
                service, "parseScoreResult",
                "{\"score\":4,\"weakPoints\":[\"值传递\"]}", "Java基础与集合");
        assertNotNull(sr);
        assertFalse(sr.isParseFailed(), "解析成功不应标记为不可信");
    }

    @Test
    void emptyJsonObjectIsParsableButHasNoScore() {
        // 兜底默认 score=3，用于确认默认值与标记正交
        UserAbilityProfile.ScoreResult sr = ReflectionTestUtils.invokeMethod(
                service, "parseScoreResult", "{}", "JVM");
        assertNotNull(sr);
        assertFalse(sr.isParseFailed());
    }
}
