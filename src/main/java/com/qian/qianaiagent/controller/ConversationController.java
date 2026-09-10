package com.qian.qianaiagent.controller;

import com.qian.qianaiagent.evaluation.EvalRecord;
import com.qian.qianaiagent.evaluation.EvaluationRecorder;
import com.qian.qianaiagent.memory.FileBasedChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 会话与评估记录管理控制器（列表 / 历史 / 删除 / 重命名 / 评估查询）。
 * <p>
 * 从原 {@code AiController} 拆出，URL 保持 {@code /ai/conversations...}、{@code /ai/evals...} 不变。
 */
@RestController
@RequestMapping("/ai")
@Slf4j
public class ConversationController {

    /** 完整对话历史（管理接口：列表/导出/删除/重命名） */
    @Resource(name = "fileBasedChatMemory")
    private FileBasedChatMemory fileBasedChatMemory;

    /** 回答结束后的异步评估记录器（Agent/RAG 质量监控，落盘 data/evals） */
    @Resource
    private EvaluationRecorder evaluationRecorder;

    /**
     * 🔴 防路径遍历攻击：校验会话 ID 不含路径分隔符 / 上级目录跳转。
     */
    private boolean isUnsafeChatId(String chatId) {
        return chatId == null || chatId.contains("..") || chatId.contains("/") || chatId.contains("\\");
    }

    /**
     * 获取所有会话列表
     * <p>
     * 返回每个会话的 chatId、标题、最后修改时间和消息数，
     * 按最后修改时间倒序排列。
     */
    @GetMapping("/conversations")
    public List<FileBasedChatMemory.ConversationInfo> listConversations() {
        return fileBasedChatMemory.listConversations();
    }

    /**
     * 获取指定会话的历史消息
     * <p>
     * 返回该会话的完整消息列表，用于前端恢复对话上下文。
     */
    @GetMapping("/conversations/{chatId}")
    public List<Map<String, Object>> getConversationMessages(@PathVariable String chatId) {
        return fileBasedChatMemory.getConversation(chatId)
                .stream()
                .map(msg -> Map.of(
                        "messageType", (Object) msg.getMessageType().name(),
                        "content", (Object) msg.getText()
                ))
                .toList();
    }

    /**
     * 获取某会话的评估记录（只读质量监控数据，Agent/RAG 各轮打分）
     * <p>
     * 默认返回最近 20 条，上限 100；数据来自 data/evals/{chatId}.json。
     */
    @GetMapping("/evals/{chatId}")
    public List<EvalRecord> getEvals(@PathVariable String chatId,
                                     @RequestParam(defaultValue = "20") int limit) {
        // 🔴 防路径遍历攻击
        if (isUnsafeChatId(chatId)) {
            return List.of();
        }
        return evaluationRecorder.listRecent(chatId, Math.min(Math.max(limit, 1), 100));
    }

    /**
     * 获取跨会话的评估汇总（全部会话的评估次数、平均分、时间跨度）
     * <p>
     * 区别于 {@code /ai/evals/{chatId}} 的单会话明细，这个接口回答"整体情况怎么样"。
     */
    @GetMapping("/evals/summary")
    public EvaluationRecorder.EvalSummary getEvalSummary() {
        return evaluationRecorder.summarize();
    }

    /**
     * 删除指定会话（永久删除）
     */
    @DeleteMapping("/conversations/{chatId}")
    public Map<String, Object> deleteConversation(@PathVariable String chatId) {
        // 🔴 防路径遍历攻击
        if (isUnsafeChatId(chatId)) {
            return Map.of("success", false, "message", "无效的会话 ID");
        }
        boolean deleted = fileBasedChatMemory.deleteConversation(chatId);
        return Map.of("success", deleted, "chatId", chatId);
    }

    /**
     * 重命名会话标题
     */
    @PutMapping("/conversations/{chatId}/title")
    public Map<String, Object> renameConversation(@PathVariable String chatId,
                                                  @RequestParam String title) {
        if (isUnsafeChatId(chatId)) {
            return Map.of("success", false, "message", "无效的会话 ID");
        }
        if (title == null || title.isBlank() || title.length() > 30) {
            return Map.of("success", false, "message", "标题长度需要在 1-30 字之间");
        }
        fileBasedChatMemory.updateTitle(chatId, title.trim());
        return Map.of("success", true, "chatId", chatId, "title", title.trim());
    }
}
