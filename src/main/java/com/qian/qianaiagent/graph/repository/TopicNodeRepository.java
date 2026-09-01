package com.qian.qianaiagent.graph.repository;

import com.qian.qianaiagent.graph.entity.TopicNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 话题节点仓库
 *
 * @author yupi
 */
@Repository
public interface TopicNodeRepository extends Neo4jRepository<TopicNode, Long> {

    /** 按话题名查找 */
    TopicNode findByName(String name);

    /** 查所有话题 */
    @Query("MATCH (t:Topic) RETURN t ORDER BY t.firstMentionedAt DESC")
    List<TopicNode> findAllTopics();
}
