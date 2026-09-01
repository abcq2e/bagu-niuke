package com.qian.qianaiagent.interview.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.qian.qianaiagent.ability.UserAbilityProfile;
import com.qian.qianaiagent.ability.UserAbilityService;
import com.qian.qianaiagent.interview.QuizCommandMatcher;
import com.qian.qianaiagent.interview.rotation.SequentialRotationService;

/**
 * 错题复习服务 —— 独立会话模式。
 * <p>
 * 从用户能力画像的 wrongQuestions 中提取全部错题，
 * 按 16 方向严格顺序轮询出题。答对后从错题本移除。
 */
@Component
@Slf4j
public class WrongQuestionReviewService {

    private static final String REVIEW_SYSTEM_PROMPT = """
            你是错题复习导师。按以下流程回复：
            1️⃣回顾：简短点评上一题（答对则肯定，答错则指出问题）
            2️⃣讲解：上一题答错时讲解正确答案（100-200字），答对跳过
            3️⃣出题：用【本轮必考题干】提问
            🚫禁止讲解本轮题目（泄题！）| 语气耐心鼓励 | 代码```包裹 | 禁止寒暄""";

    @Resource
    private UserAbilityService userAbilityService;

    private final ChatClient reviewChatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, ReviewSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> previousStemMap = new ConcurrentHashMap<>();
    private final Map<String, QEntry> previousEntryMap = new ConcurrentHashMap<>();
    private final Map<String, String> lastRetryKeys = new ConcurrentHashMap<>();
    private final Map<String, Object> reviewLocks = new ConcurrentHashMap<>();

    public WrongQuestionReviewService(ChatModel openAiChatModel) {
        this.reviewChatClient = ChatClient.builder(openAiChatModel)
                .defaultSystem(REVIEW_SYSTEM_PROMPT)
                .build();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Path.of(".review-cursor"));
        } catch (Exception e) {
            log.warn("无法创建复习游标目录: {}", e.getMessage());
        }
        log.info("✅ WrongQuestionReviewService 初始化完成");
    }

    /**
     * 解析本轮应评分的目标：评上一题，不是本轮新题。
     */
    static EvalTarget resolveEvalTarget(QEntry previous, QEntry current, boolean isFirst) {
        if (isFirst || previous == null) return null;
        return new EvalTarget(previous.topicName, previous.knowledgePoint, previous.questionText);
    }

    record EvalTarget(String topic, String knowledgePoint, String questionText) {}

    public Flux<String> doReviewChat(String message, String chatId, String sourceChatId, Long userId) {
        return Flux.defer(() -> {
            ReviewSession session = getOrCreateSession(chatId, sourceChatId);

            if (session.pool.isEmpty()) {
                return Flux.just("🎉 没有需要复习的错题！所有错题已经清除完毕。");
            }

            boolean isFirstMessage;
            boolean isNextCmd;
            boolean isRetry;
            final QEntry currentQ;
            final EvalTarget eval;
            final String topicSnapshot;
            final String stemSnapshot;

            final Object lock = reviewLocks.computeIfAbsent(chatId, k -> new Object());
            synchronized (lock) {
                isFirstMessage = !previousStemMap.containsKey(chatId);
                isNextCmd = QuizCommandMatcher.isNext(message);
                isRetry = message != null && message.equals(lastRetryKeys.get(chatId));

                currentQ = getCurrentQuestion(session);
                if (currentQ == null) {
                    return Flux.just("🎉 所有错题已复习完毕！太棒了！");
                }

                QEntry prevEntry = previousEntryMap.get(chatId);
                eval = resolveEvalTarget(prevEntry, currentQ, isFirstMessage);

                topicSnapshot = currentQ.topicName;
                stemSnapshot = currentQ.questionText;
            }

            StringBuilder ctx = new StringBuilder();
            String prevStem = previousStemMap.get(chatId);

            if (prevStem != null && !prevStem.isBlank()) {
                ctx.append("上一题:" + prevStem + "\n");
                ctx.append("考生答:" + message + "\n");
            }

            ctx.append("本轮必考:" + currentQ.questionText + "\n");
            ctx.append("(" + currentQ.topicName + ")\n");

            if (isFirstMessage) {
                ctx.append("首条:欢迎并直接出题\n");
            } else if (isNextCmd) {
                ctx.append("跳过点评，直接出题\n");
            }

            String enhancedSystem = REVIEW_SYSTEM_PROMPT + "\n" + ctx;
            StringBuilder fullTextBuilder = new StringBuilder();

            log.info("🚀 复习出题: chatId={}, topic={}, question={}",
                    chatId, topicSnapshot,
                    stemSnapshot.length() > 60 ? stemSnapshot.substring(0, 60) + "…" : stemSnapshot);

            final boolean scoreNow = !isFirstMessage && !isNextCmd && !isRetry && eval != null;
            final boolean advanceNow = !isRetry;

            return reviewChatClient.prompt()
                    .user(message)
                    .system(enhancedSystem)
                    .stream()
                    .content()
                    .doOnNext(chunk -> fullTextBuilder.append(chunk))
                    .doOnComplete(() -> {
                        synchronized (lock) {
                            if (scoreNow) {
                                userAbilityService.scoreAnswerReviewAsync(
                                        sourceChatId, eval.topic(), eval.questionText(), message,
                                        eval.knowledgePoint(), userId);
                            }
                            if (advanceNow) {
                                previousStemMap.put(chatId, stemSnapshot);
                                previousEntryMap.put(chatId, currentQ);
                                advanceCursor(session);
                                saveSession(session);
                            }
                            if (message != null) {
                                lastRetryKeys.put(chatId, message);
                            }
                            log.info("✅ 复习完成一轮: chatId={}, topic={}, len={}, scored={}",
                                    chatId, topicSnapshot, fullTextBuilder.length(), scoreNow);
                        }
                    })
                    .doOnError(e -> log.error("❌ 复习流式异常: {}", e.getMessage(), e))
                    .onErrorResume(e -> Flux.just("[ERROR] AI 服务暂时不可用：" + e.getMessage()));
        });
    }

    public Map<String, Object> getPoolPreview(String sourceChatId) {
        List<TopicQuestions> pool = buildPool(sourceChatId);
        int total = pool.stream().mapToInt(t -> t.questions().size()).sum();
        List<Map<String, Object>> topics = pool.stream()
                .map(tq -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", tq.topic());
                    m.put("questionCount", tq.questions().size());
                    return m;
                })
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalQuestions", total);
        result.put("topics", topics);
        return result;
    }

    List<TopicQuestions> buildPool(String sourceChatId) {
        UserAbilityProfile profile = userAbilityService.getOrCreateProfile(sourceChatId);
        List<TopicQuestions> pool = new ArrayList<>();

        for (String topic : SequentialRotationService.TOPIC_NAMES) {
            UserAbilityProfile.TopicScore ts = profile.getTopicScores().get(topic);
            if (ts == null || ts.getWrongQuestions() == null || ts.getWrongQuestions().isEmpty()) {
                continue;
            }
            List<QEntry> entries = new ArrayList<>();
            for (Map.Entry<String, String> e : ts.getWrongQuestions().entrySet()) {
                entries.add(new QEntry(topic, e.getKey(), e.getValue()));
            }
            if (!entries.isEmpty()) {
                pool.add(new TopicQuestions(topic, entries));
            }
        }

        if (!pool.isEmpty()) {
            log.info("📋 错题池方向顺序: {}",
                    pool.stream().map(tq -> tq.topic() + "(" + tq.questions().size() + "题)")
                            .toList());
        }
        return pool;
    }

    QEntry getCurrentQuestion(ReviewSession session) {
        if (session.topicIdx >= session.pool.size()) return null;
        TopicQuestions tq = session.pool.get(session.topicIdx);
        if (session.questionIdx >= tq.questions().size()) return null;
        return tq.questions().get(session.questionIdx);
    }

    void advanceCursor(ReviewSession session) {
        if (session.topicIdx >= session.pool.size()) return;
        session.questionIdx++;

        while (session.topicIdx < session.pool.size()) {
            TopicQuestions current = session.pool.get(session.topicIdx);
            if (session.questionIdx < current.questions().size()) {
                return;
            }
            session.topicIdx++;
            session.questionIdx = 0;
        }
    }

    private ReviewSession getOrCreateSession(String chatId, String sourceChatId) {
        ReviewSession existing = sessions.get(chatId);
        if (existing != null) return existing;

        ReviewSession loaded = loadSession(chatId);
        if (loaded != null) {
            sessions.put(chatId, loaded);
            return loaded;
        }

        List<TopicQuestions> pool = buildPool(sourceChatId);
        ReviewSession session = new ReviewSession(
                chatId, sourceChatId, pool, 0, 0,
                pool.stream().mapToInt(t -> t.questions().size()).sum());
        sessions.put(chatId, session);
        log.info("🆕 新建复习会话: chatId={}, sourceChatId={}, totalQuestions={}",
                chatId, sourceChatId, session.totalAtStart);
        return session;
    }

    private void saveSession(ReviewSession session) {
        try {
            Path file = Path.of(".review-cursor", session.chatId + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), session);
        } catch (Exception e) {
            log.warn("保存复习游标失败: {}", e.getMessage());
        }
    }

    private ReviewSession loadSession(String chatId) {
        try {
            Path file = Path.of(".review-cursor", chatId + ".json");
            if (!Files.exists(file)) return null;
            return objectMapper.readValue(file.toFile(), ReviewSession.class);
        } catch (Exception e) {
            log.warn("加载复习游标失败: {}", e.getMessage());
            return null;
        }
    }

    public static class ReviewSession {
        public String chatId;
        public String sourceChatId;
        public List<TopicQuestions> pool;
        public int topicIdx;
        public int questionIdx;
        public int totalAtStart;

        public ReviewSession() {}

        public ReviewSession(String chatId, String sourceChatId, List<TopicQuestions> pool,
                             int topicIdx, int questionIdx, int totalAtStart) {
            this.chatId = chatId;
            this.sourceChatId = sourceChatId;
            this.pool = pool;
            this.topicIdx = topicIdx;
            this.questionIdx = questionIdx;
            this.totalAtStart = totalAtStart;
        }
    }

    public record TopicQuestions(String topic, List<QEntry> questions) {}

    public record QEntry(String topicName, String knowledgePoint, String questionText) {}
}
