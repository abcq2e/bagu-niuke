package com.qian.qianaiagent.neo4j.repository;

import com.qian.qianaiagent.neo4j.entity.ConversationNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 对话知识图谱仓库
 * <p>
 * 核心分析查询：
 * 1. 对话路径追踪 — 完整还原用户对话链路
 * 2. 话题跳转分析 — 用户在哪些话题间切换
 * 3. 追问链深度 — 用户连续追问了多少轮
 * 4. 意图演变路径 — 用户意图如何随时间变化
 *
 * @author yupi
 */


//会话相关的仓储
@Repository
public interface ConversationGraphRepository extends Neo4jRepository<ConversationNode, Long> {

    /** 按 conversationId 查对话节点 */
    ConversationNode findByConversationId(String conversationId);

    // ==================== 对话路径追踪 ====================

    /**
     * 完整对话路径 — 按时间顺序还原整个对话链
     * MATCH 从 conversation 出发，沿 HAS_MESSAGE 和 NEXT 关系遍历
     * 返回有序的消息列表，可直接用于可视化对话流程图
     */
    @Query("MATCH (c:Conversation {conversationId: $conversationId}) " +
           "-[:HAS_MESSAGE]->(m:Message) " +
           "OPTIONAL MATCH (m)-[r:NEXT]->(next:Message) " +
           "RETURN m.messageIndex AS messageIndex, " +
           "m.role AS role, " +
           "m.contentSummary AS content, " +
           "m.timestamp AS timestamp, " +
           "m.messageId AS messageId, " +
           "next.messageId AS nextMessageId " +
           "ORDER BY m.messageIndex ASC")
    List<Map<String, Object>> traceDialoguePath(@Param("conversationId") String conversationId);

    // ==================== 话题跳转分析 ====================

    /**
     * 话题跳转图谱 — 分析用户在不同话题间的切换路径
     */
    @Query("MATCH (c:Conversation {conversationId: $conversationId}) " +
           "-[:HAS_MESSAGE]->(m:Message)-[:BELONGS_TO]->(t:Topic) " +
           "OPTIONAL MATCH (t)-[r:TRANSITIONS_TO]->(nextTopic:Topic) " +
           "RETURN DISTINCT t.name AS topicName, " +
           "t.keywords AS keywords, " +
           "t.firstMentionedAt AS firstMentioned, " +
           "collect(DISTINCT nextTopic.name) AS transitionedTo, " +
           "count(DISTINCT m) AS messageCount " +
           "ORDER BY t.firstMentionedAt ASC")
    List<Map<String, Object>> analyzeTopicTransitions(@Param("conversationId") String conversationId);

    // ==================== 追问链深度分析 ====================

    /**
     * 追问链深度 — 统计用户连续追问的轮次
     * 通过 REPLIES_TO 关系链计算最长追问路径
     */
    @Query("MATCH path = (m1:Message)-[:REPLIES_TO*]->(m2:Message) " +
           "WHERE m1.role = 'user' " +
           "RETURN m1.messageId AS startMessageId, " +
           "m1.contentSummary AS question, " +
           "length(path) AS followUpDepth " +
           "ORDER BY followUpDepth DESC " +
           "LIMIT 10")
    List<Map<String, Object>> analyzeFollowUpDepth();

    // ==================== 话题聚类 ====================

    /**
     * 全局话题热度 — 统计所有对话中最常出现的话题
     */
    @Query("MATCH (t:Topic)<-[:BELONGS_TO]-(m:Message) " +
           "RETURN t.name AS topicName, " +
           "count(m) AS messageCount, " +
           "t.keywords AS keywords " +
           "ORDER BY messageCount DESC")
    List<Map<String, Object>> getTopicHeatmap();

    // ==================== 意图演变路径 ====================

    /**
     * 用户意图演变 — 追踪用户在整个对话中的意图变化
     */
    @Query("MATCH (c:Conversation {conversationId: $conversationId}) " +
           "-[:HAS_MESSAGE]->(m:Message) " +
           "WHERE m.role = 'user' " +
           "OPTIONAL MATCH (m)-[:BELONGS_TO]->(t:Topic) " +
           "RETURN m.messageIndex AS index, " +
           "m.contentSummary AS userMessage, " +
           "m.timestamp AS time, " +
           "collect(t.name) AS topics " +
           "ORDER BY m.messageIndex ASC")
    List<Map<String, Object>> traceIntentEvolution(@Param("conversationId") String conversationId);
}
