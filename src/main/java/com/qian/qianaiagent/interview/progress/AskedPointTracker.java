package com.qian.qianaiagent.interview.progress;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话级已考知识点 + 追问配额。
 */
@Component
public class AskedPointTracker {

    public static final int DEFAULT_FOLLOW_UP = 1;

    public enum TurnIntent {
        ANSWER,
        SWITCH_OR_NEXT,
        DEEP_DIVE
    }

    public enum TurnDecision {
        CONTINUE_FOLLOW_UP,
        END_POINT,
        DEEP_DIVE_SAME,
        DEEP_DIVE_NEED_NEXT
    }

    public record TurnResult(
            TurnDecision decision,
            String endedPointId,
            String endedTopic,
            String endedDimension
    ) {
        static TurnResult of(TurnDecision d) {
            return new TurnResult(d, null, null, null);
        }
    }

    private static final class SessionPoints {
        final Set<String> askedPointIds = ConcurrentHashMap.newKeySet();
        volatile String activePointId;
        volatile String activeTopic;
        volatile String activeDimension;
        volatile int followUpRemain;
    }

    private final Map<String, SessionPoints> sessions = new ConcurrentHashMap<>();

    private SessionPoints state(String chatId) {
        return sessions.computeIfAbsent(chatId, k -> new SessionPoints());
    }

    public void hydrateAsked(String chatId, Set<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        state(chatId).askedPointIds.addAll(ids);
    }

    public void beginPoint(String chatId, String pointId, String topic, String dimension) {
        SessionPoints s = state(chatId);
        s.activePointId = pointId;
        s.activeTopic = topic;
        s.activeDimension = dimension;
        s.followUpRemain = DEFAULT_FOLLOW_UP;
    }

    public TurnResult onUserTurn(String chatId, TurnIntent intent) {
        SessionPoints s = state(chatId);
        if (s.activePointId == null) {
            return TurnResult.of(TurnDecision.END_POINT);
        }
        if (intent == TurnIntent.SWITCH_OR_NEXT) {
            return endActiveResult(s, TurnDecision.END_POINT);
        }
        if (intent == TurnIntent.DEEP_DIVE) {
            if (s.followUpRemain > 0) {
                return TurnResult.of(TurnDecision.DEEP_DIVE_SAME);
            }
            return endActiveResult(s, TurnDecision.DEEP_DIVE_NEED_NEXT);
        }
        s.followUpRemain--;
        if (s.followUpRemain > 0) {
            return TurnResult.of(TurnDecision.CONTINUE_FOLLOW_UP);
        }
        return endActiveResult(s, TurnDecision.END_POINT);
    }

    private TurnResult endActiveResult(SessionPoints s, TurnDecision decision) {
        String id = s.activePointId;
        String topic = s.activeTopic;
        String dim = s.activeDimension;
        if (id != null) {
            s.askedPointIds.add(id);
        }
        s.activePointId = null;
        s.activeTopic = null;
        s.activeDimension = null;
        s.followUpRemain = 0;
        return new TurnResult(decision, id, topic, dim);
    }

    public String forceEndActive(String chatId) {
        SessionPoints s = state(chatId);
        TurnResult r = endActiveResult(s, TurnDecision.END_POINT);
        return r.endedPointId();
    }

    /** 结束并返回完整结果（供记账） */
    public TurnResult forceEndActiveDetailed(String chatId) {
        return endActiveResult(state(chatId), TurnDecision.END_POINT);
    }

    public boolean isAsked(String chatId, String pointId) {
        return state(chatId).askedPointIds.contains(pointId);
    }

    public Set<String> getAskedPointIds(String chatId) {
        return Collections.unmodifiableSet(state(chatId).askedPointIds);
    }

    public String getActivePointId(String chatId) {
        return state(chatId).activePointId;
    }

    public String getActiveTopic(String chatId) {
        return state(chatId).activeTopic;
    }

    public String getActiveDimension(String chatId) {
        return state(chatId).activeDimension;
    }

    public int getFollowUpRemain(String chatId) {
        return state(chatId).followUpRemain;
    }
}
