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
 * 对话会话节点 — 图数据库中的一次完整对话
 * <p>
 * 图模型：(:Conversation) -[:HAS_MESSAGE]-> (:Message) -[:NEXT]-> (:Message)
 * 用于追踪完整的对话链路、分析用户意图演变路径
 *
 * @author yupi
 */

//对话会话节点
//entity包下面就是图模型有关的元素
@Node("Conversation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationNode {

    @Id
    @GeneratedValue
    private Long id;

    /** 对话 ID，对应 ChatMemory 中的 conversationId */
    private String conversationId;
    //private String conversationId  会话ID
    /** 对话开始时间 */
    private LocalDateTime startTime;

    /** 对话最后活跃时间 */
    private LocalDateTime lastActiveTime;

    /** 消息总数 */
    private Integer messageCount;

    /** 用户 ID（如有） */
    private String userId;

    /** 对话 → 消息 */
    @Relationship(type = "HAS_MESSAGE", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private List<MessageNode> messages = new ArrayList<>();
}
