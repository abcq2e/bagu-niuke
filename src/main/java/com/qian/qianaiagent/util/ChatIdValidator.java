package com.qian.qianaiagent.util;

/**
 * 会话 ID 的路径安全工具 —— 全项目唯一一份。
 *
 * <h2>为什么必须收拢</h2>
 * chatId 由客户端提供，且会被直接拼进文件名（{@code data/chat-memory/{chatId}.json}、
 * {@code .review-cursor/{chatId}.json}、{@code data/evals/{chatId}.json} …）。
 * 此前校验逻辑散落成多份 private 副本，有的地方<b>一份都没有</b> ——
 * 例如 {@code WrongQuestionReviewService} 的 chatId 完全未校验，
 * 构造 {@code chatId=../data/evals/<victim>} 就能覆写任意已存在的 .json。
 *
 * <h2>两个方法的分工</h2>
 * <ul>
 *   <li>{@link #isUnsafe} —— <b>拒绝</b>：用于用户可控、且语义上必须是合法会话 ID 的入口。</li>
 *   <li>{@link #sanitize} —— <b>净化</b>：用于「有则更好」的旁路数据（游标、评估记录），
 *       畸形 ID 不应该让功能失效，但绝不能逃出目录。</li>
 * </ul>
 */
public final class ChatIdValidator {

    private ChatIdValidator() {
    }

    /**
     * 是否是不安全的会话 ID（会被拒绝）。
     * <p>
     * 只校验「形状」不校验归属 —— 归属判定见 {@code ConversationAccess}。
     */
    public static boolean isUnsafe(String chatId) {
        return chatId == null || chatId.isBlank()
                || chatId.contains("..") || chatId.contains("/") || chatId.contains("\\");
    }

    /**
     * 把会话 ID 净化成安全的文件名字符串：白名单 {@code [a-zA-Z0-9_-]}，其余替换为 {@code _}。
     * <p>
     * 注意 {@code .} 不在白名单内，所以 {@code ../x} 会变成 {@code ___x} —— 无法逃出目录。
     * 调用方读写必须使用<b>同一个</b>净化后的名字，否则取不回来。
     */
    public static String sanitize(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return "unknown";
        }
        return chatId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * 净化后的文件名（不含扩展名）。
     * <p>
     * 与 {@link #sanitize} 的区别：当净化结果可能退化成一个纯 {@code _} 序列（原 ID 全是非法字符）时，
     * 用 chatId 的哈希做后缀保证唯一，避免不同畸形 ID 互相覆盖。
     */
    public static String safeFileName(String chatId) {
        String sanitized = sanitize(chatId);
        if (sanitized.chars().allMatch(c -> c == '_')) {
            return sanitized + Math.abs(chatId.hashCode());
        }
        return sanitized;
    }
}
