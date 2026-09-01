package com.qian.qianaiagent.interview.progress;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 当前有效项目描述管理器（覆盖式 vs 对话历史的追加式）
 * <p>
 * 职责：
 * <ul>
 *   <li>每个会话独立维护"当前生效的项目描述"</li>
 *   <li>覆盖语义 —— {@code updateSpec()} 会自动覆盖旧值，而非追加</li>
 *   <li>构建用于注入 System Prompt 的内容块，明确告诉 LLM"以此为准"</li>
 * </ul>
 * <p>
 * 设计原则：
 * <ul>
 *   <li>不做持久化 —— 会话结束或超时后自然失效，也不影响磁盘文件</li>
 *   <li>与 ChatMemory 分离 —— ActiveSpec 管"当前是什么"，ChatMemory 管"聊过什么"</li>
 *   <li>不重置已考题指纹 —— 用户答过的知识点即使换项目也视为已掌握</li>
 * </ul>
 */
@Slf4j
@Component
public class ActiveSpecManager {

    /** 每个会话 -> 当前生效的项目描述文本 */
    private final Map<String, String> activeSpecs = new ConcurrentHashMap<>();

    /**
     * 更新或覆盖当前项目描述（天然覆盖语义）
     *
     * @param chatId 会话 ID
     * @param spec   完整项目描述文本
     */
    public void updateSpec(String chatId, String spec) {
        if (chatId == null || spec == null || spec.isBlank()) {
            return;
        }
        String old = activeSpecs.put(chatId, spec.trim());
        if (old != null) {
            log.info("🔄 项目描述已覆盖: chatId={}, oldLen={}, newLen={}",
                    chatId, old.length(), spec.length());
        } else {
            log.info("📋 新项目描述已设置: chatId={}, specLen={}", chatId, spec.length());
        }
    }

    /**
     * 获取当前项目描述，用于场景不关心是否有描述时
     */
    public String getSpec(String chatId) {
        return activeSpecs.get(chatId);
    }

    /**
     * 构建注入 System Prompt 的内容块
     * <p>
     * 返回格式（注意前后空行分隔，便于 LLM 识别边界）：
     * <pre>
     * 📋 【当前项目描述】（唯一有效版本，以此为准）
     * 对话历史中的旧项目描述已过时，请完全忽略。
     * 用户之前回答过的知识点仍视为已掌握，禁止重复出题。
     *
     * [实际描述文本]
     * </pre>
     *
     * @return 空字符串表示无可注入内容
     */
    public String buildSpecPrompt(String chatId) {
        String spec = activeSpecs.get(chatId);
        if (spec == null || spec.isBlank()) {
            return "";
        }
        return """

                📋 【当前项目描述】（唯一有效版本）
                以下为用户最新的项目描述，对话历史中的旧项目描述已过时，请完全忽略。
                但用户之前回答过的知识点仍视为已掌握，禁止重复出题。

                %s
                """.formatted(spec);
    }

    /**
     * 粗略检测用户消息是否在描述/更新项目
     * <p>
     * 启发式规则 —— 匹配常见的"陈述项目"句式。误判率很低但允许：
     * - 误判：一条非项目消息被当成项目描述 → 只是覆盖了旧值，不产生副作用
     * - 漏判：用户用非标准句式更新项目 → 旧描述保留，不会丢失信息
     */
    public boolean isProjectDescription(String message) {
        if (message == null || message.isBlank()) return false;
        String m = message.trim();

        // 常见项目描述开头句式
        String[] patterns = {
                "我的项目是", "我的项目", "我做的项目是", "我做的项目",
                "我最近在", "我正在做", "我目前在",
                "项目是", "项目描述",
                "我的项目改成", "项目改为", "项目变更为",
                "我之前做", "我以前做",
                "我负责的项目", "我参与的项目",
                "介绍一下我的项目", "说下我的项目",
                "我过去做",
        };
        for (String p : patterns) {
            if (m.startsWith(p)) return true;
        }
        return false;
    }

    /**
     * 判断指定会话是否有有效项目描述
     */
    public boolean hasSpec(String chatId) {
        String spec = activeSpecs.get(chatId);
        return spec != null && !spec.isBlank();
    }

    /**
     * 清理会话（会话删除时调用）
     */
    public void remove(String chatId) {
        activeSpecs.remove(chatId);
        log.info("🗑️ 已清除项目描述: chatId={}", chatId);
    }
}
