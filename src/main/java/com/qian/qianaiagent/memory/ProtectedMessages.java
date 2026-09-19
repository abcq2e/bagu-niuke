package com.qian.qianaiagent.memory;

import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 受保护消息的判定与截断 —— {@link FileBasedChatMemory} 与 {@link SummarizingChatMemory} 共用。
 *
 * <h2>为什么必须共用一份</h2>
 * 「哪些消息不能丢」这条语义此前只写在 {@code SummarizingChatMemory} 里（按文本匹配
 * {@code 【方向切换】}）。{@code FileBasedChatMemory} 的截断完全不知道它，
 * 于是同一份数据在两层有<b>两套互相矛盾的保留规则</b> —— 摘要层小心翼翼保住的锚点，
 * 会被存储层按条数无差别删掉。判定逻辑收到这里，两层用同一份。
 *
 * <h2>锚点为什么丢不得</h2>
 * {@code 【方向切换】} 一旦丢失，AI 会拿旧方向的题目点评新方向的回答 ——
 * 这是 {@code TopicMemoryTrimmer} 整个类存在的原因。
 */
public final class ProtectedMessages {

    /** 方向切换标记。改这里等于改所有保留语义，务必同步 {@code TopicMemoryTrimmer}。 */
    public static final String TOPIC_SWITCH_MARKER = "【方向切换】";

    private ProtectedMessages() {
    }

    /** 该消息是否受保护（任何截断都不得丢弃）。 */
    public static boolean isProtected(Message message) {
        String text = message != null ? message.getText() : null;
        return text != null && text.contains(TOPIC_SWITCH_MARKER);
    }

    /**
     * 截断到 {@code max} 条，但受保护消息永不丢弃。
     * <p>
     * 受保护消息排在结果最前（它们语义上就属于会话开头），其余配额按「从新到旧」填充。
     * 若受保护消息自身已超过 {@code max}，只保留最新的 {@code max} 条。
     */
    public static List<Message> truncate(List<Message> messages, int max) {
        if (messages.size() <= max) {
            return new ArrayList<>(messages);
        }

        List<Message> protectedMsgs = new ArrayList<>();
        List<Message> normal = new ArrayList<>();
        for (Message m : messages) {
            if (isProtected(m)) {
                protectedMsgs.add(m);
            } else {
                normal.add(m);
            }
        }

        if (protectedMsgs.size() >= max) {
            return new ArrayList<>(protectedMsgs.subList(protectedMsgs.size() - max, protectedMsgs.size()));
        }

        int quota = max - protectedMsgs.size();
        List<Message> result = new ArrayList<>(protectedMsgs);
        result.addAll(normal.subList(normal.size() - quota, normal.size()));
        return result;
    }
}
