package com.qian.qianaiagent.config;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.Neo4jTemplate;

/**
 * Neo4j 对话知识图谱分析配置
 * <p>
 * 独立于主聊天记忆存储的图分析模块：
 * - 对话节点建模（Conversation → Message → Topic）
 * - 关系建模（HAS_MESSAGE, REPLIES_TO, NEXT, BELONGS_TO, TRANSITIONS_TO）
 * - 对话路径追踪、话题跳转分析、追问链深度、意图演变
 * <p>
 * 仅在 Neo4j 依赖可用且连接配置正确时启用
 *
 * @author yupi
 */
@Configuration
@ConditionalOnClass(Driver.class)
@Slf4j
public class Neo4jAnalysisConfig {

    /**
     * 验证 Neo4j 连接状态
     * <p>
     * Spring Boot 的 Neo4jAutoConfiguration 会自动创建 Driver 和 Neo4jTemplate Bean。
     * 本配置仅做连接验证和日志输出。
     */
    //下面的就是验证连接状态
    @Bean
    public String neo4jConnectionValidator(Neo4jTemplate neo4jTemplate) {
        try {
            neo4jTemplate.count("MATCH (n) RETURN count(n)");
            log.info("✅ Neo4j 对话图谱分析模块已启用 — 图数据库连接正常");
        } catch (Exception e) {
            log.warn("⚠️ Neo4j 连接失败，对话图谱分析功能暂不可用: {}", e.getMessage());
        }
        return "neo4j-verified";
    }
}
