package com.qian.qianaiagent.graph.repository;

import com.qian.qianaiagent.graph.entity.MessageNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 消息节点仓库
 *
 * @author yupi
 */

//消息相关的仓储
@Repository
public interface MessageNodeRepository extends Neo4jRepository<MessageNode, Long> {

    /** 按 messageId 查找 */
    MessageNode findByMessageId(String messageId);

    /** 查某个对话中角色为 user 的所有消息 */
    @Query("MATCH (c:Conversation {conversationId: $conversationId}) " +
           "-[:HAS_MESSAGE]->(m:Message {role: 'user'}) " +
           "RETURN m " +
           "ORDER BY m.messageIndex ASC")
    List<MessageNode> findUserMessagesByConversationId(@Param("conversationId") String conversationId);
}
