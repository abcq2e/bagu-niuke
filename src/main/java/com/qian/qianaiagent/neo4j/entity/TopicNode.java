package com.qian.qianaiagent.neo4j.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 话题节点 — 对话中自动识别的话题
 * <p>
 * 关系链：(:Topic) -[:TRANSITIONS_TO]-> (:Topic)  话题跳转路径
 * (:Message) -[:BELONGS_TO]-> (:Topic)   消息归类
 * <p>
 * 用于分析用户在不同话题间如何跳转、每个话题聊了多久
 *
 * @author yupi
 */

//话题节点，对话中自动识别的话题
@Node("Topic")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicNode {

    @Id
    @GeneratedValue
    private Long id;

    /** 话题唯一标识 */
    private String topicId;

    /** 话题名称（如 "表白焦虑"、"沟通矛盾"、"家庭压力"） */
    private String name;

    /** 话题关键词 */
    private List<String> keywords;

    /** 首次被提及的时间 */
    private LocalDateTime firstMentionedAt;

    /** 话题跳转到哪些话题 */
    @Relationship(type = "TRANSITIONS_TO", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private List<TopicNode> nextTopics = new ArrayList<>();

    /** 从哪些话题跳转过来 */
    @Relationship(type = "TRANSITIONS_TO", direction = Relationship.Direction.INCOMING)
    @Builder.Default
    private List<TopicNode> prevTopics = new ArrayList<>();
}
