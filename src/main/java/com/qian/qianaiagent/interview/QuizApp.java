package com.qian.qianaiagent.interview;

import com.qian.qianaiagent.advisor.MyLoggerAdvisor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.qian.qianaiagent.ability.UserAbilityService;
import com.qian.qianaiagent.interview.progress.ActiveSpecManager;
import com.qian.qianaiagent.interview.progress.TopicMemoryTrimmer;
import com.qian.qianaiagent.interview.rotation.SequentialRotationService;
import com.qian.qianaiagent.interview.rotation.TopicRotationService;
import com.qian.qianaiagent.knowledge.TopicDocumentCache;

/**
 * AI 技术面试官应用（面试模式）—— 严格顺序轮询版。
 *
 * <p>16个方向严格按学习路线顺序轮询，每个方向的题目严格按序号出题。
 * 每道题遵循：候选人回答 → AI 点评 → AI 讲解（如果答得差）→ AI 出下一题。
 */
@Component
@Slf4j
public class QuizApp {

    private final ChatClient quizChatClient;

    /**
     * 面试官提示词 —— AI 只负责点评+讲解，绝对禁止出题。
     *
     * <p>🔴 架构级铁律：出题权完全在后端，AI 永远不参与出题。
     * AI 输出结束后，后端自动拼接下一题的题本，保证顺序 100% 严格。
     */
    private static final String SYSTEM_PROMPT = """
            你是大厂技术面试官。系统自动出题，你永远不出题。
            你要做两件事：先点评用户回答的对错，再给出正确答案和简要解析。
            格式：
            1️⃣【点评】一句话指出用户回答对不对、缺什么（≤2句）
            2️⃣【参考答案】给出核心正确答案（1-3句话）
            3️⃣【要点】bullet列出2-3个关键点，每点≤2句
            风格：精简，讲核心结论，不要长篇大论，不要展开深入分析。
            禁令：不说面试结束、不出题、不预告、不寒暄、不用代码块、不宣布方向切换。
            🔴 铁律：你只点评用户消息开头【待点评的题目】指定的这一道题，严禁点评历史对话中的其他题目。
            你的点评和参考答案必须与【待点评的题目】严格对应。
            🚫 历史对话中可能包含之前的题目和点评，全部忽略！只看【待点评的题目】这一道！
            如果用户回答和当前题目无关，直接指出用户跑题了。""";

    @Resource
    private SequentialRotationService sequentialRotationService;
    @Resource
    private ActiveSpecManager activeSpecManager;
    @Resource
    private TopicDocumentCache topicDocumentCache;
    @Resource
    private TopicMemoryTrimmer topicMemoryTrimmer;
    @Resource
    private UserAbilityService userAbilityService;
    @Resource
    private TopicRotationService topicRotationService;

    private final ChatMemory chatMemory; // 🔴 保存引用，用于持久化题目到聊天记录

    public QuizApp(ChatModel openAiChatModel, ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        List<Advisor> quizAdvisors = new ArrayList<>();
        quizAdvisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build());
        quizAdvisors.add(new MyLoggerAdvisor());
        quizChatClient = ChatClient.builder(openAiChatModel)
                .defaultAdvisors(quizAdvisors.toArray(new Advisor[0]))
                .build();
    }

    @PostConstruct
    public void init() {

        log.info("✅ QuizApp 初始化完成（严格顺序轮询模式）");
    }

    /**
     * 🔴 [Bug修复-内存泄漏] 定时清理过期会话的 Map 条目（每 30 分钟）。
     * 清理 chatLocks、lastShownStems 等所有按 stateKey 存储的 Map，
     * 防止长时间运行后 OOM。
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30 * 60 * 1000)
    public void evictExpiredEntries() {
        long now = System.currentTimeMillis();
        long timeout = 2 * 60 * 60 * 1000; // 2 小时无访问则清理
        // 收集过期 key
        List<String> expiredKeys = new ArrayList<>();
        for (Map.Entry<String, Long> entry : lastAccessByKey.entrySet()) {
            if (now - entry.getValue() > timeout) {
                expiredKeys.add(entry.getKey());
            }
        }
        for (String key : expiredKeys) {
            chatLocks.remove(key);
            lastShownIndices.remove(key);
            lastRetryKeys.remove(key);
            lastShownStems.remove(key);
            pendingEvalStemMap.remove(key);
            pendingEvalTopicMap.remove(key);
            messageCounts.remove(key);
            lastQuestions.remove(key);
            previousStemMap.remove(key);
            previousTopicMap.remove(key);
            lastAccessByKey.remove(key);
            // 🔴 [Bug修复] migratedTopics使用stateKey格式，清理时需匹配stateKey前缀
            migratedTopics.removeIf(mt -> mt.startsWith(key + ":"));
        }
        if (!expiredKeys.isEmpty()) {
            log.info("🧹 QuizApp 过期条目清理完成: {}个会话, 剩余{}个活跃会话",
                    expiredKeys.size(), lastAccessByKey.size());
        }
    }

    // ========================================================================
    // 核心方法
    // ========================================================================

    //这个才是主方法
    public Flux<String> doUnifiedChat(String message, String chatId, Long userId) {
        return Flux.defer(() -> {
            // Step 1: 初始化会话游标（用户绑定：用 userId 做 key）
            final String ck = cursorKey(chatId, userId);
            // 会话态 Map 与游标同源，避免多对话框评题串台
            final String stateKey = ck;
            final String migrationSource = (!ck.equals(chatId)) ? chatId : null;
            sequentialRotationService.initSession(ck, migrationSource, topicDocumentCache);
            String persistedLastStem = sequentialRotationService.getLastShownStem(ck);
            String persistedLastTopic = sequentialRotationService.getLastShownTopic(ck);
            if (persistedLastStem != null && !persistedLastStem.isBlank()) {
                lastShownStems.putIfAbsent(stateKey, persistedLastStem);
                if (persistedLastTopic != null && !persistedLastTopic.isBlank()) {
                    previousTopicMap.putIfAbsent(stateKey, persistedLastTopic);
                }
                lastQuestions.putIfAbsent(stateKey,
                        new QuestionContext(persistedLastTopic != null ? persistedLastTopic : "", ""));
            }
            boolean isFirstMessage = !lastQuestions.containsKey(stateKey);
            boolean isNextCmd = QuizCommandMatcher.isNext(message);
            boolean isSkipDir = QuizCommandMatcher.isSkipDir(message);
            boolean isResetMemory = QuizCommandMatcher.isResetMemory(message);
            boolean isRetry = message != null && message.equals(lastRetryKeys.get(stateKey))
                    && !isNextCmd && !isSkipDir && !isResetMemory;
            Integer cachedCount = messageCounts.get(stateKey);
            if (cachedCount == null || cachedCount > 4) {
                int actualCount = topicMemoryTrimmer.trimToRecentN(chatId, 4);
                messageCounts.put(stateKey, Math.min(actualCount, 4));
            }
            if (isResetMemory) {
                String currentTopic = sequentialRotationService.currentTopic(ck);
                topicMemoryTrimmer.forceCleanMemory(chatId, currentTopic);
                log.info("🧹 用户重置记忆: chatId={}, topic={}", chatId, currentTopic);
            }
            if (isNextCmd) {
                log.info("⏭️ 用户说下一题: chatId={}", chatId);
            }
            String topic;
            String currentStem = null;
            final String questionBlock;
            final String topicSnapshot;
            final String stemSnapshot;
            final String evalStem;
            final String evalTopic;
            final SequentialRotationService.SequentialCursor cursorSnapshot;
            synchronized (chatLocks.computeIfAbsent(ck, k -> new Object())) {
                // 🔴 [Bug修复-内存泄漏] 更新最后访问时间，供定时清理使用
                lastAccessByKey.put(ck, System.currentTimeMillis());
                if (isSkipDir) {
                    String oldTopic = sequentialRotationService.currentTopic(ck);
                    sequentialRotationService.skipCurrentDirection(ck);
                    String newTopic = sequentialRotationService.currentTopic(ck);
                    if (oldTopic != null && newTopic != null && !oldTopic.equals(newTopic)) {
                        topicMemoryTrimmer.trimAfterAdvance(chatId, oldTopic, newTopic);
                    }
                    log.info("⏭️ 用户跳过方向: {} → {}", oldTopic, newTopic);
                }
                topic = sequentialRotationService.currentTopic(ck);
                if (topic == null) {
                    log.info("🏁 所有方向题目已考完: ck={}, chatId={}", ck, chatId);
                    return Flux.just("🎉 恭喜！所有方向的题目已全部考察完毕！\n\n"
                            + "16个知识方向，共计2285道面试题，你已全部完成。\n"
                            + "如果你需要重新开始，请刷新页面或新建对话。");
                }
                boolean shouldAdvance = !isRetry && !isFirstMessage && !isResetMemory;
                if (isRetry) {
                    String pendingStem = pendingEvalStemMap.get(stateKey);
                    currentStem = (pendingStem != null && !pendingStem.isBlank())
                            ? pendingStem : lastShownStems.get(stateKey);
                    if (currentStem == null || currentStem.isBlank()) {
                        // 🔴 [题目去重] retry回退路径也需去重，防止绕过检查
                        int retrySkips = 0;
                        while (retrySkips < 5) {
                            int[] range = sequentialRotationService.getCurrentQuestionRange(ck);
                            if (range == null || range[0] >= range[1]) break;
                            // 🔴 [Bug修复] 每次循环从游标获取最新方向，防止 markQuestionAsked 切换方向后使用旧值
                            String checkTopic = sequentialRotationService.currentTopic(ck);
                            if (checkTopic == null) break;
                            List<String> allQuestions = topicDocumentCache.getOrderedQuestions(checkTopic);
                            if (range[0] >= allQuestions.size()) break;
                            String candidate = allQuestions.get(range[0]);
                            if (!topicRotationService.isQuestionAsked(ck, checkTopic, range[0])) {
                                currentStem = candidate;
                                lastShownIndices.put(stateKey, range[0]);
                                // 🔴 [Bug修复] 同步更新 topic 变量，确保后续 questionBlock 显示正确方向
                                topic = checkTopic;
                                break;
                            }
                            log.info("🔍 [retry回退] 跳过重复题目: {}...",
                                    candidate.length() > 40 ? candidate.substring(0, 40) : candidate);
                            sequentialRotationService.markQuestionAsked(ck);
                            retrySkips++;
                        }
                        // 兜底：全部重复则取当前位置题目
                        if (currentStem == null || currentStem.isBlank()) {
                            int[] range = sequentialRotationService.getCurrentQuestionRange(ck);
                            String fallbackTopic = sequentialRotationService.currentTopic(ck);
                            if (range != null && range[0] < range[1] && fallbackTopic != null) {
                                List<String> allQuestions = topicDocumentCache.getOrderedQuestions(fallbackTopic);
                                if (range[0] < allQuestions.size()) {
                                    currentStem = allQuestions.get(range[0]);
                                    lastShownIndices.put(stateKey, range[0]);
                                    topic = fallbackTopic;
                                }
                            }
                        }
                    }
                } else {
                    // 🔴 [题目去重] 循环跳过已问过/错题本中的重复题目
                    int maxSkips = 20; // 安全上限，防止死循环
                    int skips = 0;
                    currentStem = null;

                    // 🔴 [Bug修复] 历史迁移key使用stateKey而非ck，确保用户登录状态变化时迁移记录仍有效
                    String migrationKey = stateKey + ":" + topic;
                    if (migratedTopics.add(migrationKey)) {
                        int[] preRange = sequentialRotationService.getCurrentQuestionRange(ck);
                        if (preRange != null && preRange[0] > 0) {
                            List<String> allQ = topicDocumentCache.getOrderedQuestions(topic);
                            int seedCount = Math.min(preRange[0], allQ.size());
                            for (int i = 0; i < seedCount; i++) {
                                topicRotationService.recordQuestionAsked(ck, topic, i);
                            }
                            log.info("🌱 [历史迁移] [{}] 前{}题→指纹（修复前已出题目）",
                                    topic, seedCount);
                        }
                    }
                    while (skips < maxSkips) {
                        int[] range = sequentialRotationService.getCurrentQuestionRange(ck);
                        if (range == null || range[0] >= range[1]) break;
                        String checkTopic = sequentialRotationService.currentTopic(ck);
                        if (checkTopic == null) break;
                        List<String> allQuestions = topicDocumentCache.getOrderedQuestions(checkTopic);
                        if (range[0] >= allQuestions.size()) break;
                        String candidate = allQuestions.get(range[0]);
                        // 检查是否与已问题目/错题本重复
                        if (!topicRotationService.isQuestionAsked(ck, checkTopic, range[0])) {
                            currentStem = candidate;
                            lastShownIndices.put(stateKey, range[0]); // 记录序号供 doOnComplete 持久化
                            // 🔴 去重可能导致方向已切换，同步更新 topic
                            if (!checkTopic.equals(topic)) {
                                log.info("🔍 去重导致方向切换: {} → {}", topic, checkTopic);
                                topic = checkTopic;
                            }
                            break;
                        }
                        // 🔴 [Bug修复] 跳过重复题目时不调用markQuestionAsked，避免触发方向切换破坏严格顺序
                        // 只记录跳过计数，由外层统一推进游标
                        log.info("🔍 跳过重复题目 [{}]: {}...", checkTopic,
                                candidate.length() > 40 ? candidate.substring(0, 40) : candidate);
                        skips++;
                    }
                    // 全部重复时的兜底：取当前位置的题目（即使重复也比无题可出强）
                    if (currentStem == null) {
                        int[] fallbackRange = sequentialRotationService.getCurrentQuestionRange(ck);
                        String fallbackTopic = sequentialRotationService.currentTopic(ck);
                        if (fallbackRange != null && fallbackTopic != null
                                && fallbackRange[0] < fallbackRange[1]) {
                            List<String> allQuestions = topicDocumentCache.getOrderedQuestions(fallbackTopic);
                            if (fallbackRange[0] < allQuestions.size()) {
                                currentStem = allQuestions.get(fallbackRange[0]);
                                // 🔴 [Bug修复] 兜底路径也需记录序号，防止 doOnComplete 记录错误题目
                                lastShownIndices.put(stateKey, fallbackRange[0]);
                                if (!fallbackTopic.equals(topic)) {
                                    log.info("🔍 兜底取题致方向切换: {} → {}", topic, fallbackTopic);
                                    topic = fallbackTopic;
                                }
                                log.info("🔍 当前范围题目全部重复({}题)，取兜底: [{}] {}...",
                                        skips, fallbackTopic,
                                        currentStem.length() > 40
                                                ? currentStem.substring(0, 40) : currentStem);
                            }
                        }
                    }
                }
                if (currentStem == null || currentStem.isBlank()) {
                    log.error("❌ 无法获取题 stem: chatId={}, topic={}", chatId, topic);
                    return Flux.just("[ERROR] 题库数据异常，请联系管理员。");
                }

                {
                    String lastShown = lastShownStems.get(stateKey);
                    if (lastShown == null || lastShown.isBlank()) {
                        lastShown = sequentialRotationService.getLastShownStem(ck);
                    }
                    if (lastShown == null || lastShown.isBlank()) {
                        // 最后兜底：从 ChatMemory 里最近的【本轮考题】恢复
                        lastShown = TopicMemoryTrimmer.extractLastExamStem(chatMemory.get(chatId));
                    }
                    evalStem = (lastShown != null && !lastShown.isBlank()) ? lastShown : currentStem;
                    String lastTopic = previousTopicMap.get(stateKey);
                    if (lastTopic == null || lastTopic.isBlank()) {
                        lastTopic = sequentialRotationService.getLastShownTopic(ck);
                    }
                    evalTopic = (lastTopic != null && !lastTopic.isBlank()) ? lastTopic : topic;
                }

                // 🔴 [Bug修复] AI 失败恢复后，evalStem（待点评题）可能与当前新题相同
                // （doOnError 恢复了 lastShownStems，同时 removeQuestionAsked 取消标记致其被重新选中）
                // 此时不应推进游标：题目虽被重新展示但用户尚未回答，应按首条消息处理
                if (!isRetry && evalStem != null && evalStem.equals(currentStem)) {
                    shouldAdvance = false;
                    log.info("🔧 [错误恢复] evalStem==currentStem，抑制游标推进: ck={}, stem={}", ck,
                            currentStem.length() > 40 ? currentStem.substring(0, 40) : currentStem);
                }

                lastShownStems.put(stateKey, currentStem);
                // 🔴 [Bug修复] 与游标一并持久化，重启后仍能评对「用户正在答的那题」
                sequentialRotationService.saveLastShown(ck, topic, currentStem);

                // 🔴 [Bug修复-并发] 去重预占提前到同步块内，防止并发请求取到同一题
                // recordQuestionAsked 立即标记题目为"已出"，但游标推进延迟到 AI 成功回调
                int reservedIdx = lastShownIndices.getOrDefault(stateKey, -1);
                if (reservedIdx >= 0 && topic != null) {
                    topicRotationService.recordQuestionAsked(ck, topic, reservedIdx);
                }

                // 🔴 [Bug修复-并发] 快照在去重预占前拍摄，确保AI失败回滚时不会回滚去重标记
                // 这样可以避免去重预占被清理后题目被重复选中
                cursorSnapshot = shouldAdvance ? sequentialRotationService.snapshotCursor(ck) : null;

                // 🔴 [Bug修复-并发] 游标推进移到 doOnComplete（AI 成功后），此处只处理因去重跳题导致的推进
                // markQuestionAsked 不应在此处调用——如果 AI 调用失败，游标已推进无法回滚

                questionBlock = "\n\n━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                        + "【本轮考题】" + topic + "\n"
                        + currentStem + "\n"
                        + "━━━━━━━━━━━━━━━━━━━━━━━━━";

                topicSnapshot = topic;
                stemSnapshot = currentStem;

                previousStemMap.put(stateKey, stemSnapshot);
                previousTopicMap.put(stateKey, topicSnapshot);
                lastQuestions.putIfAbsent(stateKey, new QuestionContext(topic, ""));

                log.info("🚀 AI调用: ck={}, chatId={}, cursorTopic={}, retry={}, adv={}",
                        ck, chatId, topic, isRetry, shouldAdvance);
            }
            // ====== 关键区结束 ======

            if (activeSpecManager.isProjectDescription(message)) {
                activeSpecManager.updateSpec(chatId, message);
            }

            StringBuilder ctx = new StringBuilder();

            final String effectiveEvalStem;
            final String effectiveEvalTopic;
            // 🔴 [Bug修复] isRetry 也需要 pendingEvalStemMap 做容错恢复：
            // 当 AI 调用成功但用户刷新页面导致重试时，lastShownStems 已指向下一题，
            // 此时必须从 pendingEvalStemMap 恢复到正确题目，否则点评和题目不对应。
            if (isNextCmd || isRetry) {
                String pendingStem = pendingEvalStemMap.get(stateKey);
                String pendingTopic = pendingEvalTopicMap.get(stateKey);
                if (pendingStem != null && !pendingStem.isBlank()) {
                    log.info("🔧 [{}-修复] evalStem从pending恢复: {} → {}",
                            isRetry ? "重试" : "继续", evalStem, pendingStem);
                    effectiveEvalStem = pendingStem;
                    effectiveEvalTopic = pendingTopic != null ? pendingTopic : evalTopic;
                } else {
                    effectiveEvalStem = evalStem;
                    effectiveEvalTopic = evalTopic;
                }
            } else {
                effectiveEvalStem = evalStem;
                effectiveEvalTopic = evalTopic;
            }

            pendingEvalStemMap.put(stateKey, effectiveEvalStem);
            pendingEvalTopicMap.put(stateKey, effectiveEvalTopic);

            log.info("🔍 上下文: chatId={}, evalTopic={}, evalStem={}, cursorTopic={}, cursorStem={}",
                    chatId, effectiveEvalTopic,
                    effectiveEvalStem != null && effectiveEvalStem.length() > 50 ? effectiveEvalStem.substring(0, 50) + "..." : effectiveEvalStem,
                    topicSnapshot,
                    stemSnapshot != null && stemSnapshot.length() > 50 ? stemSnapshot.substring(0, 50) + "..." : stemSnapshot);

            boolean directionChanged = effectiveEvalTopic != null && !effectiveEvalTopic.equals(topicSnapshot);
            // 🔴 [Bug修复] AI 失败恢复后，evalStem（待点评题）可能与当前新题相同（因去重标记已清除），
            // 此时应按首条消息处理，只展示题目不做评价
            boolean evalSameAsNew = effectiveEvalStem != null && effectiveEvalStem.equals(stemSnapshot);

            ctx.append("【当前方向】" + effectiveEvalTopic + "\n");
            ctx.append("【当前题目】" + effectiveEvalStem + "\n");
            ctx.append("【用户回答】" + message + "\n");

            // 🔴 [Bug修复] isFirstMessage 时用户还未见过任何题目，即使消息是"下一题"等指令，
            // 也应作为首条消息欢迎，不应评价一个没被问过的题（否则会泄露答案）
            // evalSameAsNew：错误恢复后同题双展，也按首条处理
            if (isFirstMessage || evalSameAsNew) {
                ctx.append("（首条消息，欢迎即可）\n");
            } else if (isNextCmd) {
                ctx.append("（用户放弃本题，请直接给出本题的正确答案和要点，不要说「跳过」之类的话）\n");
            } else if (isSkipDir) {
                ctx.append("（用户换方向，简评后过渡）\n");
            } else {
                ctx.append("（请根据用户回答进行点评，然后给出正确答案和要点）\n");
                if (directionChanged) {
                    ctx.append("（方向已切换，评完即止）\n");
                }
            }

            String specPrompt = activeSpecManager.buildSpecPrompt(chatId);
            if (!specPrompt.isEmpty()) {
                ctx.append(specPrompt).append("\n");
            }

            // 🔴 [RAG接入] 用待点评题目检索知识库，让 AI 的参考答案有据可依。
            // 不做查询改写（多查询扩展性价比低），直接单次检索；失败降级为纯 LLM 点评。
            if (effectiveEvalStem != null && !effectiveEvalStem.isBlank()) {
                try {
                    List<Document> ragDocs = quizVectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query(effectiveEvalStem)
                                    .topK(3)
                                    .similarityThreshold(0.3)
                                    .build());
                    if (!ragDocs.isEmpty()) {
                        ctx.append("\n【知识库参考】（供给出参考答案时参考，仍保持精简风格）\n");
                        for (Document doc : ragDocs) {
                            ctx.append("- ").append(doc.getText()).append("\n");
                        }
                    }
                } catch (Exception e) {
                    log.warn("⚠️ RAG 检索失败，降级为纯 LLM 点评: {}", e.getMessage());
                }
            }

            if (message != null) {
                lastRetryKeys.put(stateKey, message);
            }

            String enhancedSystem = SYSTEM_PROMPT + "\n\n" + ctx.toString();
            StringBuilder fullTextBuilder = new StringBuilder();

            // 🔴 [Bug修复] 将待点评的题目嵌入用户消息，双重锚定防止 AI 跑题到历史中的其他题目
            // system prompt 中有【当前题目】但 AI 可能忽略长 system prompt，
            // 把题目放在用户消息开头能确保 AI 100% 看到并以此为点评目标。
            String wrappedMessage;
            // 🔴 [Bug修复] isFirstMessage 时用户还未见过题目，即使消息是指令也不应包装为"跳过"
            // evalSameAsNew：错误恢复后同题双展，也不包装
            if (isFirstMessage || evalSameAsNew) {
                wrappedMessage = message;
            } else if (isNextCmd) {
                wrappedMessage = "【被跳过的题目】" + effectiveEvalStem + "\n（用户要求跳过，请直接给参考答案）";
            } else if (isSkipDir) {
                wrappedMessage = "【被跳过的方向】" + effectiveEvalTopic + "\n【题目】" + effectiveEvalStem + "\n【用户说】" + message;
            } else {
                wrappedMessage = "【待点评的题目】" + effectiveEvalStem + "\n【我的回答】" + message;
            }

            return quizChatClient.prompt()
                    .user(wrappedMessage)
                    .system(enhancedSystem)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .content()
                    .doOnNext(chunk -> fullTextBuilder.append(chunk))
                    .doOnComplete(() -> {
                        // 🔴 [Bug修复] concatWith 的 questionBlock 在 ChatClient Advisor 之外，
                        // 不会自动进 ChatMemory。必须手动写入，否则换方向裁剪后待答题目丢失 → 点评串题。
                        if (questionBlock != null && !questionBlock.isBlank()) {
                            try {
                                chatMemory.add(chatId, List.of(new AssistantMessage(questionBlock.trim())));
                            } catch (Exception e) {
                                log.warn("持久化【本轮考题】失败: chatId={}, err={}", chatId, e.getMessage());
                            }
                        }
                        // 🔴 [Bug修复] evalSameAsNew：AI失败恢复后同题双展，跳过评分
                        if (!isFirstMessage && !evalSameAsNew && !isNextCmd && !isSkipDir && !isResetMemory) {
                            userAbilityService.scoreAnswerAsync(
                                    chatId, effectiveEvalTopic, null, effectiveEvalStem, message, userId);
                        }
                        String aiText = fullTextBuilder.toString();
                        if (!aiText.isEmpty()) {
                            lastQuestions.put(stateKey, new QuestionContext(topicSnapshot,
                                    aiText.length() > 400 ? aiText.substring(0, 400) : aiText));
                        }
                        messageCounts.merge(stateKey, 2, Integer::sum);
                        pendingEvalStemMap.remove(stateKey);
                        pendingEvalTopicMap.remove(stateKey);

                        // 🔴 [Bug修复-并发] 游标推进移到 AI 成功后执行
                        // 去重预占已在同步块内完成，此处只推进游标到下一题
                        // cursorSnapshot 非 null 等价于 shouldAdvance==true（快照仅在需要推进时拍摄）
                        if (cursorSnapshot != null) {
                            synchronized (chatLocks.computeIfAbsent(ck, k -> new Object())) {
                                boolean advanced = sequentialRotationService.markQuestionAsked(ck);
                                String newTopic = sequentialRotationService.currentTopic(ck);
                                // 🔴 [Bug修复] 出示方向末题时不要立刻 trim：用户还没答这题。
                                // 等到「评完旧方向题 + 已出示新方向题」后再裁（见下方 directionChanged）。
                                if (advanced && newTopic != null && !newTopic.equals(topicSnapshot)) {
                                    log.info("➡️ 方向推进(延迟裁记忆): {} → {}", topicSnapshot, newTopic);
                                }
                            }
                        }

                        // 旧方向最后一题已点评完毕，此时再裁记忆，并保留刚写入的【本轮考题】
                        if (directionChanged && !isFirstMessage && !evalSameAsNew) {
                            topicMemoryTrimmer.trimAfterAdvance(chatId, effectiveEvalTopic, topicSnapshot);
                        }

                        log.info("✅ 完成: ck={}, chatId={}, topic={}, aiLen={}, 活跃方向={}",
                                ck, chatId, topicSnapshot, aiText.length(),
                                sequentialRotationService.getActiveDirectionNames(ck).size());
                    })
                    .concatWith(Flux.just(questionBlock))
                    .doOnError(e -> {
                        log.error("❌ 流式对话异常，清理去重预占: {}", e.getMessage(), e);
                        // 🔴 [Bug修复-并发] AI 失败时清理去重预占标记，避免题目被永久标记为"已出"
                        int reservedIdx = lastShownIndices.getOrDefault(stateKey, -1);
                        if (reservedIdx >= 0 && topicSnapshot != null) {
                            topicRotationService.removeQuestionAsked(ck, topicSnapshot, reservedIdx);
                            log.info("⏪ 已清理去重预占: topic={}, idx={}", topicSnapshot, reservedIdx);
                        }
                        // 游标回滚（仅回滚去重跳题导致的推进，不含最终 markQuestionAsked）
                        if (cursorSnapshot != null) {
                            synchronized (chatLocks.computeIfAbsent(ck, k -> new Object())) {
                                sequentialRotationService.restoreCursor(cursorSnapshot);
                                if (effectiveEvalStem != null) {
                                    lastShownStems.put(stateKey, effectiveEvalStem);
                                    sequentialRotationService.saveLastShown(ck, effectiveEvalTopic, effectiveEvalStem);
                                }
                            }
                        }
                    })
                    .onErrorResume(e -> Flux.just("[ERROR] AI 服务暂时不可用：" + e.getMessage()));
        }).onErrorResume(e -> {
            log.error("❌ 流式对话异常: {}", e.getMessage(), e);
            return Flux.just("[ERROR] AI 服务暂时不可用：" + e.getMessage());
        });
    }

    // ========================================================================
    // 依赖与状态
    // ========================================================================

    /** 知识库向量检索（RAG）：AI 点评时检索参考答案依据，不做查询改写 */
    @Resource private VectorStore quizVectorStore;

    private final Map<String, QuestionContext> lastQuestions = new ConcurrentHashMap<>();
    /** 上轮题干（供 AI 区分"要讲解的上一题"和"要问的下一题"） */
    private final Map<String, String> previousStemMap = new ConcurrentHashMap<>();
    /** 上轮方向名（供 AI 点评时锚定方向，防止因考生回答中关键词跑偏） */
    private final Map<String, String> previousTopicMap = new ConcurrentHashMap<>();
    /** 跟踪每个 chatId 的消息数（避免每次都从文件读取来判断是否需要裁剪） */
    private final Map<String, Integer> messageCounts = new ConcurrentHashMap<>();
    /** 🔴 防止并发：每个 chatId 一个锁，保证同一会话的请求串行处理 */
    private final Map<String, Object> chatLocks = new ConcurrentHashMap<>();
    /** 🔴 [题目去重] 追踪当前出示题目的序号（topic::index 去重用） */
    private final Map<String, Integer> lastShownIndices = new ConcurrentHashMap<>();
    /** 🔴 [历史迁移] 追踪已迁移指纹的方向（ck:topic → true），避免重复迁移 */
    private final Set<String> migratedTopics = ConcurrentHashMap.newKeySet();
    /** 🔴 重连检测：记录上一条消息文本，相同则判为页面刷新 */
    private final Map<String, String> lastRetryKeys = new ConcurrentHashMap<>();
    /** 🔴 记录最近一次出示的题目，重连时直接复用，不从游标读 */
    private final Map<String, String> lastShownStems = new ConcurrentHashMap<>();
    /** 🔴 [Bug修复] 追踪当前正在评估的题目（AI成功后清除）。用户说"继续"时，
     *  如果 pendingEvalMap 还有值说明上次 AI 调用失败了，用这个而不是 previousStemMap */
    private final Map<String, String> pendingEvalStemMap = new ConcurrentHashMap<>();
    private final Map<String, String> pendingEvalTopicMap = new ConcurrentHashMap<>();
    /** 🔴 [Bug修复-内存泄漏] 记录每个 stateKey 的最后访问时间，用于定时清理过期条目 */
    private final Map<String, Long> lastAccessByKey = new ConcurrentHashMap<>();
    record QuestionContext(String topic, String question) {}

    /**
     * 🔴 生成游标 key：用户绑定模式。
     * 如果用户已登录，进度与 userId 绑定（切换对话框不重置进度）；
     * 如果未登录，回退到 chatId（每个对话框独立进度）。
     */
    private String cursorKey(String chatId, Long userId) {
        return userId != null && userId > 0 ? "user_" + userId : chatId;
    }

    /** "下一题/跳过"类指令（转发至整句匹配器，供外部兼容引用） */
    public static final Set<String> NEXT_CMDS = QuizCommandMatcher.NEXT_CMDS;
}
