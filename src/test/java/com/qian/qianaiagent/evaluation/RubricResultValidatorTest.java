package com.qian.qianaiagent.evaluation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RubricResultValidatorTest {

    private static RubricScorer.RubricResult valid() {
        return RubricScorer.RubricResult.builder()
                .reasoningQuality(20).faithfulness(20).completeness(20).toolUsage(20)
                .totalScore(80)
                .overallComment("ok")
                .build();
    }

    @Test
    void acceptsWellFormedResult() {
        assertFalse(RubricResultValidator.validate(valid()).isPresent());
    }

    @Test
    void rejectsNull() {
        assertTrue(RubricResultValidator.validate(null).isPresent());
    }

    @Test
    void rejectsParseFailureComment() {
        RubricScorer.RubricResult r = valid();
        r.setOverallComment("JSON 解析失败: unexpected token");
        assertTrue(RubricResultValidator.validate(r).isPresent());
    }

    @Test
    void rejectsEmptyResponseComment() {
        RubricScorer.RubricResult r = valid();
        r.setOverallComment("LLM 返回空响应，评分失败");
        assertTrue(RubricResultValidator.validate(r).isPresent());
    }

    @Test
    void rejectsTotalScoreNotEqualToSum() {
        RubricScorer.RubricResult r = valid();
        r.setTotalScore(99);
        assertTrue(RubricResultValidator.validate(r).isPresent());
    }

    @Test
    void rejectsDimensionOutOfRange() {
        RubricScorer.RubricResult r = RubricScorer.RubricResult.builder()
                .reasoningQuality(150).faithfulness(10).completeness(10).toolUsage(10)
                .totalScore(180)
                .overallComment("ok")
                .build();
        assertTrue(RubricResultValidator.validate(r).isPresent());
    }

    @Test
    void rejectsBlankOverallComment() {
        RubricScorer.RubricResult r = valid();
        r.setOverallComment("  ");
        assertTrue(RubricResultValidator.validate(r).isPresent());
    }
}
