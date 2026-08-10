package com.qian.qianaiagent.app;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AskedPointTrackerTest {

    @Test
    void followUpQuotaConsumesThenEndsPoint() {
        AskedPointTracker tracker = new AskedPointTracker();
        String chatId = "c1";

        tracker.beginPoint(chatId, "pid-1", "Java并发", "线程池（参数/提交/拒绝策略/动态调整）");
        assertEquals(2, tracker.getFollowUpRemain(chatId));
        assertEquals("pid-1", tracker.getActivePointId(chatId));

        AskedPointTracker.TurnDecision d1 = tracker.onUserTurn(chatId, AskedPointTracker.TurnIntent.ANSWER).decision();
        assertEquals(AskedPointTracker.TurnDecision.CONTINUE_FOLLOW_UP, d1);
        assertEquals(1, tracker.getFollowUpRemain(chatId));
        assertFalse(tracker.getAskedPointIds(chatId).contains("pid-1"));

        AskedPointTracker.TurnDecision d2 = tracker.onUserTurn(chatId, AskedPointTracker.TurnIntent.ANSWER).decision();
        assertEquals(AskedPointTracker.TurnDecision.END_POINT, d2);
        assertTrue(tracker.getAskedPointIds(chatId).contains("pid-1"));
        assertNull(tracker.getActivePointId(chatId));
    }

    @Test
    void switchIntentEndsImmediately() {
        AskedPointTracker tracker = new AskedPointTracker();
        tracker.beginPoint("c1", "pid-2", "JVM", "GC 算法（标记清除/标记整理/复制/三色标记）");
        AskedPointTracker.TurnDecision d = tracker.onUserTurn("c1", AskedPointTracker.TurnIntent.SWITCH_OR_NEXT).decision();
        assertEquals(AskedPointTracker.TurnDecision.END_POINT, d);
        assertTrue(tracker.getAskedPointIds("c1").contains("pid-2"));
    }

    @Test
    void hydrateFromPersistedIds() {
        AskedPointTracker tracker = new AskedPointTracker();
        tracker.hydrateAsked("c1", Set.of("a", "b"));
        assertTrue(tracker.isAsked("c1", "a"));
        assertFalse(tracker.isAsked("c1", "c"));
    }
}
