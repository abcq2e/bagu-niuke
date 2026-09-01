package com.qian.qianaiagent.controller;

import com.qian.qianaiagent.graph.service.ConversationAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 对话知识图谱分析 API
 * <p>
 * 基于 Neo4j 图数据库的对话分析接口：
 * - 对话路径追踪：完整还原用户对话链路
 * - 话题跳转分析：用户在不同话题间如何切换
 * - 追问链深度：统计用户连续追问轮次
 * - 意图演变：追踪用户意图如何随时间变化
 * - 综合报告：一键生成对话分析报告
 * <p>
 * 面试演示：打开 Neo4j Browser，运行 MATCH (n)-[r]->(m) RETURN n,r,m LIMIT 50
 * 即可看到对话知识图谱的可视化效果
 * <p>
 * 仅在 Neo4j Driver 可用时启用
 *
 * @author yupi
 */
@RestController
@RequestMapping("/analysis/conversation")
@ConditionalOnClass(Driver.class)
@Slf4j
public class ConversationAnalysisController {

    private final ConversationAnalysisService analysisService;

    public ConversationAnalysisController(ConversationAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * 🔥 对话路径追踪 — 按时间顺序还原完整对话链路
     * <p>
     * 面试话术：
     * "这个接口展示了 Neo4j 图遍历的能力。传统关系型数据库要查对话历史需要
     * ORDER BY timestamp，而图数据库通过节点间的关系链直接走一遍就是完整路径，
     * 而且在 Neo4j Browser 里可视化出来就是一张对话流程图。"
     */
    @GetMapping("/{conversationId}/path")
    public Map<String, Object> tracePath(@PathVariable String conversationId) {
        log.info("🔍 对话路径追踪: conversationId={}", conversationId);
        List<Map<String, Object>> path = analysisService.traceDialoguePath(conversationId);
        return Map.of(
                "conversationId", conversationId,
                "totalMessages", path.size(),
                "dialoguePath", path
        );
    }

    /**
     * 🔥 话题跳转分析 — 用户在不同话题间的切换路径
     */
    @GetMapping("/{conversationId}/topics")
    public Map<String, Object> analyzeTopics(@PathVariable String conversationId) {
        log.info("🔍 话题跳转分析: conversationId={}", conversationId);
        List<Map<String, Object>> transitions = analysisService.analyzeTopicTransitions(conversationId);
        return Map.of(
                "conversationId", conversationId,
                "topicCount", transitions.size(),
                "topicTransitions", transitions
        );
    }

    /**
     * 🔥 追问链深度 — 统计全局用户连续追问深度排行
     */
    @GetMapping("/follow-up-depth")
    public Map<String, Object> followUpDepth() {
        log.info("🔍 追问链深度分析");
        List<Map<String, Object>> depth = analysisService.analyzeFollowUpDepth();
        return Map.of(
                "topFollowUpChains", depth,
                "averageDepth", depth.stream()
                        .mapToInt(m -> (int) m.getOrDefault("followUpDepth", 0))
                        .average()
                        .orElse(0)
        );
    }

    /**
     * 🔥 意图演变 — 追踪用户意图如何随时间变化
     */
    @GetMapping("/{conversationId}/intent")
    public Map<String, Object> traceIntent(@PathVariable String conversationId) {
        log.info("🔍 意图演变分析: conversationId={}", conversationId);
        List<Map<String, Object>> evolution = analysisService.traceIntentEvolution(conversationId);
        return Map.of(
                "conversationId", conversationId,
                "userMessages", evolution.size(),
                "intentEvolution", evolution
        );
    }

    /**
     * 🔥 全局话题热力图 — 所有对话中话题分布
     */
    @GetMapping("/topic-heatmap")
    public Map<String, Object> topicHeatmap() {
        log.info("🔍 全局话题热力图");
        List<Map<String, Object>> heatmap = analysisService.getTopicHeatmap();
        return Map.of(
                "totalTopics", heatmap.size(),
                "heatmap", heatmap
        );
    }

    /**
     * 🔥 综合对话分析报告 — 一键生成
     * <p>
     * 面试话术：
     * "这个接口一次性返回对话路径、话题跳转、意图演变、追问深度的综合分析。
     * 底层数据全部来自 Neo4j 图数据库，每个维度的分析都是一个 Cypher 图查询，
     * 如果用 MySQL 做同样的事情，需要多表 JOIN + 递归查询，SQL 会非常复杂。"
     */
    @GetMapping("/{conversationId}/report")
    public Map<String, Object> fullReport(@PathVariable String conversationId) {
        log.info("📊 生成综合对话分析报告: conversationId={}", conversationId);
        return analysisService.generateAnalysisReport(conversationId);
    }
}
