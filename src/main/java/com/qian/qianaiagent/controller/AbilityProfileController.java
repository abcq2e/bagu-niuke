package com.qian.qianaiagent.controller;

import com.qian.qianaiagent.ability.UserAbilityProfile;
import com.qian.qianaiagent.ability.UserAbilityService;
import com.qian.qianaiagent.context.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户能力画像控制器（画像展示 / 总结建议 / 清理 / 重置 / 调试路由）。
 * <p>
 * 从原 {@code AiController} 拆出，URL 保持 {@code /ai/quiz/profile...}、{@code /ai/debug/route} 不变。
 */
@RestController
@RequestMapping("/ai")
@Slf4j
public class AbilityProfileController {

    @Resource
    private UserAbilityService userAbilityService;

    /**
     * 获取用户能力画像（雷达图数据）— 返回展示清洗后的副本
     * <p>
     * 清洗规则：过滤评价词（"态度敷衍""概念混淆"等）、跨方向词，
     * 确保前端展示的薄弱项均为具体可学习的技术知识点。
     * 不修改历史 .ability-profiles/*.json 文件。
     */
    @GetMapping("/quiz/profile/{chatId}")
    public UserAbilityProfile getProfile(@PathVariable String chatId) {
        return userAbilityService.getDisplayProfile(chatId, UserContext.getCurrentUserId());
    }

    /**
     * LLM 生成文字版考察总结
     */
    @GetMapping("/quiz/profile/{chatId}/summary")
    public Map<String, String> getProfileSummary(@PathVariable String chatId) {
        // 🔴 必须传当前用户：此前传 null 会让 resolveKey 回退成 chatId 作 key，读到别人的画像
        Long userId = UserContext.getCurrentUserId();
        String summary = userAbilityService.buildSummary(chatId, userId);
        String aiSuggestion = "";
        try {
            // 调用 LLM 生成学习建议
            aiSuggestion = userAbilityService.generateAISuggestion(chatId, userId);
        } catch (Exception e) {
            aiSuggestion = "基于您的考察数据生成个性化建议失败，请稍后重试。";
            log.warn("生成 AI 建议失败: {}", e.getMessage());
        }
        return Map.of(
                "summary", summary,
                "aiSuggestion", aiSuggestion
        );
    }

    /**
     * 强制清理跨方向弱点评（立即执行，不依赖自动清理）
     */
    @PostMapping("/quiz/profile/{chatId}/cleanup")
    public Map<String, Object> cleanupProfile(@PathVariable String chatId) {
        Long userId = UserContext.getCurrentUserId();
        UserAbilityProfile profile = userAbilityService.getOrCreateProfile(chatId, userId);
        int moved = userAbilityService.cleanCrossTopicWeakPoints(profile);
        userAbilityService.saveProfile(chatId, userId);
        return Map.of("success", true, "chatId", chatId, "moved", moved);
    }

    /**
     * 🔴 [调试] 测试弱点评路由结果（不修改数据）
     */
    @PostMapping("/debug/route")
    public Map<String, Object> debugRoute(
            @RequestParam String topic,
            @RequestBody List<String> weakPoints) {
        Map<String, List<String>> result =
                userAbilityService.routeWeakPointsPublic(weakPoints, topic);
        return Map.of("topic", topic, "routed", result);
    }

    /**
     * 重置画像（开始新一轮面试）
     */
    @PostMapping("/quiz/profile/{chatId}/reset")
    public Map<String, Object> resetProfile(@PathVariable String chatId) {
        Long userId = UserContext.getCurrentUserId();
        userAbilityService.resetProfile(chatId, userId);
        return Map.of("success", true, "chatId", chatId);
    }
}
