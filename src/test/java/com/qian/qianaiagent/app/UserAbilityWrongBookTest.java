package com.qian.qianaiagent.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserAbilityWrongBookTest {

    @Test
    void lowScoreRecordsWrongQuestionWithFallbackKey() {
        UserAbilityProfile.TopicScore ts = new UserAbilityProfile.TopicScore();
        ts.recordWrongQuestion("综合薄弱", "HashMap 为什么线程不安全？");
        assertTrue(ts.getWrongQuestions().containsValue("HashMap 为什么线程不安全？"));
        assertTrue(ts.getWrongQuestions().containsKey("综合薄弱"));
    }

    @Test
    void removeByQuestionTextDeletesEntry() {
        UserAbilityProfile.TopicScore ts = new UserAbilityProfile.TopicScore();
        ts.recordWrongQuestion("AQS", "什么是AQS？");
        assertTrue(ts.removeWrongQuestionByText("什么是AQS？"));
        assertTrue(ts.getWrongQuestions().isEmpty());
    }

    @Test
    void removeByTruncatedStoredText() {
        UserAbilityProfile.TopicScore ts = new UserAbilityProfile.TopicScore();
        String full = "这是一道很长的面试题用于验证截断匹配逻辑是否正常工作当入库被截断时仍能删除";
        String stored = full.substring(0, 20) + "…";
        ts.recordWrongQuestion("长题", stored);
        assertTrue(ts.removeWrongQuestionByText(full));
        assertTrue(ts.getWrongQuestions().isEmpty());
    }
}
