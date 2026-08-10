package com.qian.qianaiagent.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WrongQuestionReviewServiceTest {

    @Test
    void reviewScoresPreviousNotCurrent() {
        WrongQuestionReviewService.QEntry prev =
                new WrongQuestionReviewService.QEntry("JVM", "GC", "什么是GC？");
        WrongQuestionReviewService.QEntry curr =
                new WrongQuestionReviewService.QEntry("JVM", "CMS", "CMS有什么问题？");

        var target = WrongQuestionReviewService.resolveEvalTarget(prev, curr, false);
        assertNotNull(target);
        assertEquals("什么是GC？", target.questionText());
        assertNotEquals(curr.questionText(), target.questionText());
        assertEquals("GC", target.knowledgePoint());
    }

    @Test
    void firstMessageHasNoEvalTarget() {
        WrongQuestionReviewService.QEntry curr =
                new WrongQuestionReviewService.QEntry("JVM", "CMS", "CMS有什么问题？");
        assertNull(WrongQuestionReviewService.resolveEvalTarget(null, curr, true));
    }
}
