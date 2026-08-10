package com.qian.qianaiagent.app;

import com.qian.qianaiagent.chatmemory.FileBasedChatMemory;
import com.qian.qianaiagent.chatmemory.SummarizingChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 换方向时裁剪旧方向细问答，保留短摘要 + 待回答的【本轮考题】。
 *
 * <p>🔴 换方向时清理旧方向细问答，但必须保留最新【本轮考题】块：
 * 题目通过 concatWith 拼到流尾，若裁剪时丢掉，用户作答时 AI 会失锚并点评错题。
 */
@Component
@Slf4j
public class TopicMemoryTrimmer {

    /** 方向内保留消息数 */
    private static final int KEEP_RECENT_IN_TOPIC = 10;
    private static final String EXAM_MARKER = "【本轮考题】";

    private final FileBasedChatMemory fileBasedChatMemory;
    private final ChatMemory chatMemory;

    @Autowired
    public TopicMemoryTrimmer(FileBasedChatMemory fileBasedChatMemory, ChatMemory chatMemory) {
        this.fileBasedChatMemory = fileBasedChatMemory;
        this.chatMemory = chatMemory;
    }

    /** 单测用 */
    TopicMemoryTrimmer(FileBasedChatMemory fileBasedChatMemory) {
        this.fileBasedChatMemory = fileBasedChatMemory;
        this.chatMemory = null;
    }

    /**
     * 换方向时裁剪旧方向细问答，保留短摘要 + 最近若干条。
     *
     * @param chatId   会话 ID
     * @param leftTopic 刚结束的旧方向名
     * @param newTopic  即将开始的新方向名（用于告知 AI 当前方向）
     */
    public void trimAfterAdvance(String chatId, String leftTopic, String newTopic) {
        if (chatId == null || leftTopic == null || leftTopic.isBlank()) return;
        try {
            List<Message> all = fileBasedChatMemory.get(chatId);
            if (all == null || all.isEmpty()) return;

            String newTopicName = (newTopic != null && !newTopic.isBlank()) ? newTopic : "下一方向";
            String summaryText = "【方向切换】" + leftTopic + " 考察结束。"
                    + " 当前方向：【" + newTopicName + "】。"
                    + " 以上旧方向（" + leftTopic + "）对话历史已全部清除。"
                    + " 🔴 从现在开始，只讨论【" + newTopicName + "】的知识点。"
                    + " 若下方仍保留【本轮考题】，请先按该题点评用户回答，评完后再只讨论新方向。"
                    + " 严禁主动引用旧方向其它题目。";

            List<Message> replaced = new ArrayList<>();
            replaced.add(new SystemMessage(summaryText));
            // 🔴 必须保留最新【本轮考题】，否则待回答题目从记忆消失 → 点评串题
            Message pendingExam = findLatestExamMessage(all);
            if (pendingExam != null) {
                replaced.add(pendingExam);
            }
            fileBasedChatMemory.replaceMessages(chatId, replaced);

            if (chatMemory instanceof SummarizingChatMemory summarizing) {
                summarizing.invalidateSummary(chatId);
            }
            log.info("✂️ 换方向裁记忆: chatId={}, {}→{}, 旧{}条→摘要{}+本轮考题",
                    chatId, leftTopic, newTopicName, all.size(),
                    pendingExam != null ? 1 : 0);
        } catch (Exception e) {
            log.warn("换方向裁记忆失败: {}", e.getMessage());
        }
    }

    /** 从对话中提取最近一次【本轮考题】后的题干（不含方向名行）。 */
    public static String extractLastExamStem(List<Message> messages) {
        Message exam = findLatestExamMessage(messages);
        if (exam == null || exam.getText() == null) return null;
        String text = exam.getText();
        int marker = text.lastIndexOf(EXAM_MARKER);
        if (marker < 0) return null;
        String after = text.substring(marker + EXAM_MARKER.length()).trim();
        String[] lines = after.split("\\R");
        // 行0=方向名，行1+=题干；若只有一行则整行当题干
        if (lines.length >= 2) {
            StringBuilder stem = new StringBuilder();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty() || line.contains("━━")) continue;
                if (stem.length() > 0) stem.append('\n');
                stem.append(line);
            }
            String result = stem.toString().trim();
            return result.isEmpty() ? null : result;
        }
        String only = lines.length == 1 ? lines[0].trim() : "";
        return only.isEmpty() ? null : only;
    }

    static Message findLatestExamMessage(List<Message> messages) {
        if (messages == null) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            String text = m != null ? m.getText() : null;
            if (text != null && text.contains(EXAM_MARKER)) {
                return m;
            }
        }
        return null;
    }

    /**
     * 🔴 强制清除会话记忆并注入干净的方向上下文。
     * 用于手动修复被污染的会话（如旧代码下 AI 跑偏导致记忆混乱）。
     *
     * @param chatId 会话 ID
     * @param topic  当前方向名
     */
    public void forceCleanMemory(String chatId, String topic) {
        if (chatId == null) return;
        try {
            String cleanMsg = "【会话重置】记忆已清空。"
                    + "当前方向：【" + (topic != null ? topic : "面试考察") + "】。"
                    + "请基于此消息和后续上下文进行点评，忽略任何旧对话。";
            fileBasedChatMemory.replaceMessages(chatId,
                    List.of(new SystemMessage(cleanMsg)));
            if (chatMemory instanceof SummarizingChatMemory summarizing) {
                summarizing.invalidateSummary(chatId);
            }
            log.info("🧹 强制清除会话记忆: chatId={}, topic={}", chatId, topic);
        } catch (Exception e) {
            log.warn("强制清除记忆失败: {}", e.getMessage());
        }
    }

    /**
     * 方向内历史截断：将当前会话的 ChatMemory 截断为最近 n 条消息。
     * 在 QuizApp 每次对话前调用，防止同一方向内历史无限累积干扰出题。
     * 注意：与 trimAfterAdvance 并存，两者用途不同：
     *   - trimAfterAdvance：换方向时触发，激进清理（保留4条+摘要）
     *   - trimToRecentN：每轮对话前触发，方向内保留最近n条
     */
    public int trimToRecentN(String chatId, int n) {
        if (chatId == null || n <= 0) return 0;
        try {
            List<Message> all = fileBasedChatMemory.get(chatId);
            if (all == null || all.size() <= n) return all == null ? 0 : all.size();
            List<Message> recent = new ArrayList<>(all.subList(all.size() - n, all.size()));
            fileBasedChatMemory.replaceMessages(chatId, recent);
            // 🔴 [Bug修复] 截断后必须清除摘要缓存，否则 SummarizingChatMemory 可能
            // 通过旧摘要向 AI 泄漏已被截断的历史内容（含旧题目和旧点评）。
            if (chatMemory instanceof SummarizingChatMemory summarizing) {
                summarizing.invalidateSummary(chatId);
            }
            log.info("✂️ 方向内历史截断: chatId={}, 原{}条→保留{}条", chatId, all.size(), recent.size());
            return recent.size();
        } catch (Exception e) {
            log.warn("方向内历史截断失败（不影响主流程）: {}", e.getMessage());
        }
        return 0;
    }
}
