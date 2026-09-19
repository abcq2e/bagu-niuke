package com.qian.qianaiagent.memory;

import com.qian.qianaiagent.util.ChatIdValidator;
import org.springframework.stereotype.Component;

/**
 * 会话访问守卫 —— Controller 的统一入口。
 *
 * <h2>为什么需要这一层</h2>
 * 此前「会话 ID 合法性校验」在 {@code ConversationController}、{@code ExportController}
 * 各复制了一份 private {@code isUnsafeChatId}，而 {@code InterviewChatController} /
 * {@code AgentChatController} 一份都没有 —— 安全边界靠复制粘贴维护，必然漏。
 * 这里收成全项目唯一一份，并用三个语义明确的方法区分「认领」「读取」「管理」。
 *
 * <h2>三种访问语义</h2>
 * <ul>
 *   <li>{@link #startSession} —— 建立/继续可写会话。<b>无主即认领</b>（创建路径）。</li>
 *   <li>{@link #open} —— 打开既有会话。无主<b>且已有数据</b>才认领（防预抢占）。</li>
 *   <li>{@link #manage} —— 删除/重命名/导出/评估等破坏性或旁路操作。<b>无主一律拒绝</b>，且不认领。</li>
 * </ul>
 *
 * <p>⚠️ 使用顺序：想删一个无主旧会话，得先用 {@link #open} 打开它（触发认领），之后
 * {@link #manage} 才会放行。这是刻意的 —— 避免「删除」成为认领任意旧数据的旁路。
 */
@Component
public class ConversationAccess {

    private final FileBasedChatMemory memory;

    public ConversationAccess(FileBasedChatMemory memory) {
        this.memory = memory;
    }

    /**
     * 🔴 全项目唯一的会话 ID 合法性校验：会直接参与文件名拼接，必须挡在路径遍历之前。
     * <p>
     * 仅校验「形状」，不校验归属 —— 归属由下面三个方法负责。
     */
    public static boolean isUnsafeChatId(String chatId) {
        return ChatIdValidator.isUnsafe(chatId);
    }

    /**
     * 建立或继续一个可写会话（对话接口在进入响应式流之前调用）。
     *
     * @return false 表示 ID 非法或该会话已归属他人，应拒绝本次请求
     */
    public boolean startSession(String chatId, Long userId) {
        return !isUnsafeChatId(chatId) && memory.startSession(chatId, userId);
    }

    /**
     * 打开一个既有会话（恢复历史时调用）。
     *
     * @return false 表示 ID 非法或该会话已归属他人，应拒绝本次请求
     */
    public boolean open(String chatId, Long userId) {
        return !isUnsafeChatId(chatId) && memory.accessConversation(chatId, userId);
    }

    /**
     * 严格归属判定，用于删除/重命名/导出/评估查询。
     * <p>
     * 与 {@link #open} 的区别：无主一律拒绝，且<b>不认领</b>。
     *
     * @return false 表示 ID 非法、无主、或归属他人
     */
    public boolean manage(String chatId, Long userId) {
        return !isUnsafeChatId(chatId) && memory.canManage(chatId, userId);
    }
}
