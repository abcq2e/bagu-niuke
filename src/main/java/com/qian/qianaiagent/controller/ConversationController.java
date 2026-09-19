package com.qian.qianaiagent.controller;

import com.qian.qianaiagent.context.UserContext;
import com.qian.qianaiagent.evaluation.EvalRecord;
import com.qian.qianaiagent.evaluation.EvaluationRecorder;
import com.qian.qianaiagent.interview.progress.ActiveSpecManager;
import com.qian.qianaiagent.memory.ConversationAccess;
import com.qian.qianaiagent.memory.FileBasedChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 *
 * <h2>归属校验</h2>
 * 每个接口在动数据之前都必须过 {@link ConversationAccess}。此前这里只有路径遍历校验
 * （{@code isUnsafeChatId}），没有任何归属判断 —— 任何登录用户传别人的 chatId 就能读、
 * 改、删对方的全部面试记录，而 {@code /ai/conversations} 更是直接列出所有人的会话。
 */
@RestController
@RequestMapping("/ai")
@Slf4j
public class ConversationController {

    /** 完整对话历史（管理接口：列表/导出/删除/重命名） */
    @Resource(name = "fileBasedChatMemory")
    private FileBasedChatMemory fileBasedChatMemory;

    /** 会话归属守卫（统一路径校验 + 归属判定） */
    @Resource
    private ConversationAccess conversationAccess;

    /** 回答结束后的异步评估记录器（Agent/RAG 质量监控，落盘 data/evals） */
    @Resource
    private EvaluationRecorder evaluationRecorder;

    /** 当前生效项目描述（会话级内存状态，删除会话时需一并清理） */
    @Resource
    private ActiveSpecManager activeSpecManager;

    /**
     * 获取所有会话列表 —— 只返回<b>当前用户自己的</b>和无主（legacy）会话。
     * <p>
     * 🔴 列表接口不会认领无主会话：否则第一个打开侧边栏的人会把所有旧会话据为己有。
     */
    @GetMapping("/conversations")
    public List<FileBasedChatMemory.ConversationInfo> listConversations() {
        return fileBasedChatMemory.listConversations(UserContext.getCurrentUserId());
    }

    /**
     * 获取指定会话的历史消息。
     * <p>
     * 打开既有会话是「首次访问即认领」的唯一入口：无主且已有数据的会话在此归属当前用户。
     */
    @GetMapping("/conversations/{chatId}")
    public ResponseEntity<List<Map<String, Object>>> getConversationMessages(@PathVariable String chatId) {
        Long userId = UserContext.getCurrentUserId();
        if (!conversationAccess.open(chatId, userId)) {
            log.warn("🚫 拒绝访问他人会话: chatId={}, userId={}", chatId, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<Map<String, Object>> messages = fileBasedChatMemory.getConversation(chatId)
                .stream()
                .map(msg -> Map.of(
                        "messageType", (Object) msg.getMessageType().name(),
                        "content", (Object) msg.getText()
                ))
                .toList();
        return ResponseEntity.ok(messages);
    }

    /**
     * 获取某会话的评估记录（只读质量监控数据，Agent/RAG 各轮打分）
     * <p>
     * 默认返回最近 20 条，上限 100；数据来自 data/evals/{chatId}.json。
     */
    @GetMapping("/evals/{chatId}")
    public ResponseEntity<List<EvalRecord>> getEvals(@PathVariable String chatId,
                                                     @RequestParam(defaultValue = "20") int limit) {
        Long userId = UserContext.getCurrentUserId();
        if (!conversationAccess.manage(chatId, userId)) {
            log.warn("🚫 拒绝访问他人评估记录: chatId={}, userId={}", chatId, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(
                evaluationRecorder.listRecent(chatId, Math.min(Math.max(limit, 1), 100)));
    }

    /**
     * 获取跨会话的评估汇总（当前用户自己的会话的评估次数、平均分、时间跨度）
     * <p>
     * 区别于 {@code /ai/evals/{chatId}} 的单会话明细，这个接口回答"整体情况怎么样"。
     * <p>
     * 🔴 必须按归属过滤：{@code summarize()} 会扫描整个 {@code data/evals} 目录，
     * 不做过滤等于把所有用户的评分聚合后返回给任何人。
     */
    @GetMapping("/evals/summary")
    public EvaluationRecorder.EvalSummary getEvalSummary() {
        return evaluationRecorder.summarize(
                fileBasedChatMemory.chatIdsOwnedBy(UserContext.getCurrentUserId())::contains);
    }

    /**
     * 删除指定会话（永久删除）
     * <p>
     * 🔴 严格归属判定且不认领：想删一个无主旧会话，得先打开它（触发认领）再删。
     */
    @DeleteMapping("/conversations/{chatId}")
    public ResponseEntity<Map<String, Object>> deleteConversation(@PathVariable String chatId) {
        Long userId = UserContext.getCurrentUserId();
        if (!conversationAccess.manage(chatId, userId)) {
            log.warn("🚫 拒绝删除他人会话: chatId={}, userId={}", chatId, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "无权操作该会话"));
        }
        boolean deleted = fileBasedChatMemory.deleteConversation(chatId);
        if (deleted) {
            // 🔴 项目描述是独立的会话级状态，不清理会在内存里永久驻留
            activeSpecManager.remove(chatId);
        }
        return ResponseEntity.ok(Map.of("success", deleted, "chatId", chatId));
    }

    /**
     * 重命名会话标题
     */
    @PutMapping("/conversations/{chatId}/title")
    public ResponseEntity<Map<String, Object>> renameConversation(@PathVariable String chatId,
                                                                  @RequestParam String title) {
        Long userId = UserContext.getCurrentUserId();
        if (!conversationAccess.manage(chatId, userId)) {
            log.warn("🚫 拒绝重命名他人会话: chatId={}, userId={}", chatId, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "无权操作该会话"));
        }
        if (title == null || title.isBlank() || title.length() > 30) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "标题长度需要在 1-30 字之间"));
        }
        fileBasedChatMemory.updateTitle(chatId, title.trim());
        return ResponseEntity.ok(Map.of("success", true, "chatId", chatId, "title", title.trim()));
    }
}
