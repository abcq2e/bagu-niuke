package com.qian.qianaiagent.app;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方向轮转服务 — 维护每个会话的方向覆盖状态
 *
 * <p>保证一轮内 16 个方向不重不漏，轮完重新洗牌进入下一轮。
 * 方向推进由两个信号驱动：
 * <ol>
 *   <li>AI 主动信号：System Prompt 要求 AI 在方向考察完毕时输出 [NEXT_TOPIC]</li>
 *   <li>硬上限兜底：同一方向连续 4 条用户消息后强制推进</li>
 * </ol>
 */
@Component
@Slf4j
public class TopicRotationService {

    /** 必须与 document 目录下 bagu-*.md 的方向名逐字一致 */
    public static final List<String> TOPICS = List.of(
            "Java基础与集合", "Java并发", "JVM", "Spring框架",
            "MySQL", "Redis", "消息队列", "计算机网络",
            "操作系统与Linux", "分布式与微服务", "算法与数据结构", "设计模式",
            "系统设计与场景", "Docker与运维", "ES与搜索", "Agent与AI应用");

    /** 话题 → ASCII 文件名映射（避免中文文件名在 ClassPathResource 中加载失败） */
    private static final java.util.Map<String, String> TOPIC_TO_FILENAME =
            java.util.Map.ofEntries(
                    java.util.Map.entry("Java基础与集合", "bagu-java-basics"),
                    java.util.Map.entry("Java并发", "bagu-java-concurrency"),
                    java.util.Map.entry("JVM", "bagu-jvm"),
                    java.util.Map.entry("Spring框架", "bagu-spring"),
                    java.util.Map.entry("MySQL", "bagu-mysql"),
                    java.util.Map.entry("Redis", "bagu-redis"),
                    java.util.Map.entry("消息队列", "bagu-mq"),
                    java.util.Map.entry("计算机网络", "bagu-network"),
                    java.util.Map.entry("操作系统与Linux", "bagu-os-linux"),
                    java.util.Map.entry("分布式与微服务", "bagu-distributed"),
                    java.util.Map.entry("算法与数据结构", "bagu-algorithm"),
                    java.util.Map.entry("设计模式", "bagu-design-patterns"),
                    java.util.Map.entry("系统设计与场景", "bagu-system-design"),
                    java.util.Map.entry("Docker与运维", "bagu-docker"),
                    java.util.Map.entry("ES与搜索", "bagu-es-search"),
                    java.util.Map.entry("Agent与AI应用", "bagu-agent-ai"));

    /** 话题 → 纯英文文件名（xxx.md），供 ClassPathResource 使用 */
    public static String topicToFilename(String topic) {
        return TOPIC_TO_FILENAME.getOrDefault(topic, "bagu-" + topic) + ".md";
    }

    /** 同一方向最多停留的用户消息数（追问预算），超过则强制换方向 */
    private static final int MAX_EXCHANGES_PER_TOPIC = 4;

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    private static class SessionState {
        final Deque<String> remaining;
        final List<String> covered;
        String currentTopic;
        int exchangesOnCurrent;
        long lastAccess;

        SessionState() {
            List<String> shuffled = new ArrayList<>(TOPICS);
            Collections.shuffle(shuffled);
            remaining = new ArrayDeque<>(shuffled);
            covered = new ArrayList<>();
            currentTopic = remaining.pollFirst();
            exchangesOnCurrent = 0;
            lastAccess = System.currentTimeMillis();
        }
    }

    /**
     * 每条用户消息调用一次：返回当前方向并计数。
     * 若 exchangesOnCurrent 已达 MAX_EXCHANGES_PER_TOPIC，先内部推进再返回新方向。
     * remaining 弹空后：covered 清空、重新洗牌全部方向、开始新一轮。
     */
    public String currentTopic(String chatId) {
        SessionState state = sessions.computeIfAbsent(chatId, k -> new SessionState());
        synchronized (state) {
            state.lastAccess = System.currentTimeMillis();
            // 硬上限兜底：超限则自动推进
            if (state.exchangesOnCurrent >= MAX_EXCHANGES_PER_TOPIC) {
                advanceInternal(state);
            }
            state.exchangesOnCurrent++;
            return state.currentTopic;
        }
    }

    /** AI 输出了 [NEXT_TOPIC] 标记时调用 */
    public void advance(String chatId) {
        SessionState state = sessions.get(chatId);
        if (state == null) return;
        synchronized (state) {
            advanceInternal(state);
        }
    }

    private void advanceInternal(SessionState state) {
        if (state.currentTopic != null) {
            state.covered.add(state.currentTopic);
        }
        if (state.remaining.isEmpty()) {
            // 一轮完成，重新洗牌
            List<String> shuffled = new ArrayList<>(TOPICS);
            Collections.shuffle(shuffled);
            state.remaining.addAll(shuffled);
            state.covered.clear();
            log.info("🔄 所有方向已考察完毕，重新洗牌开始新一轮");
        }
        state.currentTopic = state.remaining.pollFirst();
        state.exchangesOnCurrent = 0;
    }

    /** 本轮已考察完的方向（不含当前方向），用于注入 prompt */
    public List<String> coveredTopics(String chatId) {
        SessionState state = sessions.get(chatId);
        if (state == null) return List.of();
        synchronized (state) {
            return new ArrayList<>(state.covered);
        }
    }

    /** 定时清理过期会话（每 30 分钟） */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void evictExpiredSessions() {
        long now = System.currentTimeMillis();
        long timeout = 2 * 60 * 60 * 1000; // 2 小时
        sessions.entrySet().removeIf(entry -> {
            SessionState state = entry.getValue();
            synchronized (state) {
                return (now - state.lastAccess) > timeout;
            }
        });
        log.info("🧹 过期会话清理完成，剩余活跃会话数: {}", sessions.size());
    }
}
