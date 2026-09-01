package com.qian.qianaiagent.interview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuizCommandMatcherTest {

    @Test
    void exactNextCommandsMatch() {
        assertTrue(QuizCommandMatcher.isNext("过"));
        assertTrue(QuizCommandMatcher.isNext("下一题"));
        assertTrue(QuizCommandMatcher.isNext("SKIP"));
        assertTrue(QuizCommandMatcher.isNext("  继续  "));
    }

    @Test
    void answerContainingSubstringMustNotMatch() {
        assertFalse(QuizCommandMatcher.isNext("线程的创建过程是这样的"));
        assertFalse(QuizCommandMatcher.isNext("可以通过线程池来创建"));
        assertFalse(QuizCommandMatcher.isNext("过滤掉无效请求"));
        assertFalse(QuizCommandMatcher.isNext("I will pass the object"));
        assertFalse(QuizCommandMatcher.isSkipDir("我想换个方向深入理解一下"));
    }

    @Test
    void exactSkipAndResetMatch() {
        assertTrue(QuizCommandMatcher.isSkipDir("换个方向"));
        assertTrue(QuizCommandMatcher.isResetMemory("重置记忆"));
        assertFalse(QuizCommandMatcher.isResetMemory("请帮我重置记忆再继续"));
    }
}
