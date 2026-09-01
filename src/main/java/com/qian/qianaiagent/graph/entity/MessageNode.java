package com.qian.qianaiagent.graph.entity;

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
 * 消息节点 — 对话中的单条消息
 * <p>
 * 关系链：(:Message) -[:REPLIES_TO]-> (:Message)
 * (:Message) -[:NEXT]-> (:Message)
 * (:Message) -[:BELONGS_TO]-> (:Topic)
 * <p>
 * 通过图遍历可以完整还原用户对话路径、追问深度、话题跳转
 *
 * @author yupi
 */

//消息节点，对话中的单条消息
@Node("Message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageNode {

    @Id
    @GeneratedValue
    private Long id;

    /** 消息唯一标识 */
    private String messageId;

    /** 角色：user / assistant */
    private String role;

    /** 消息内容摘要（截取前 200 字符，避免全文撑爆图） */
    private String contentSummary;

    /** 消息发送时间 */
    private LocalDateTime timestamp;

    /** 在对话中的序号（第几轮） */
    private Integer messageIndex;

    /** 当前消息回复哪条消息 */
    @Relationship(type = "REPLIES_TO", direction = Relationship.Direction.OUTGOING)
    private MessageNode repliesTo;

    /** 下一条消息（对话链） */
    @Relationship(type = "NEXT", direction = Relationship.Direction.OUTGOING)
    private MessageNode next;

    /** 消息所属话题 */
    @Relationship(type = "BELONGS_TO", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private List<TopicNode> topics = new ArrayList<>();
}
