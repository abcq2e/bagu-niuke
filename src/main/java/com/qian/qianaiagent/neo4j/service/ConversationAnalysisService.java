package com.qian.qianaiagent.neo4j.service;

import com.qian.qianaiagent.neo4j.entity.ConversationNode;
import com.qian.qianaiagent.neo4j.entity.MessageNode;
import com.qian.qianaiagent.neo4j.entity.TopicNode;
import com.qian.qianaiagent.neo4j.repository.ConversationGraphRepository;
import com.qian.qianaiagent.neo4j.repository.MessageNodeRepository;
import com.qian.qianaiagent.neo4j.repository.TopicNodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话分析服务 — Neo4j 图谱构建 & 智能分析
 * <p>
 * 核心能力：
 * 1. 实时将聊天消息写入 Neo4j 图数据库
 * 2. 自动提取话题关键词（基于知识考察领域词典）
 * 3. 分析对话路径、话题跳转、追问深度、意图演变
 * <p>
 * 面试亮点：
 * - 将非结构化的聊天文本建模为结构化知识图谱
 * - 用 Cypher 图查询实现传统 SQL 难以表达的"N 度关系"分析
 * - 图可视化直观展示用户对话行为模式
 * <p>
 * 仅在 Neo4j Driver 可用时启用
 *
 * @author yupi
 */
@Service
@ConditionalOnClass(Driver.class)
@Slf4j
public class ConversationAnalysisService {

    private final ConversationGraphRepository graphRepository;
    private final MessageNodeRepository messageNodeRepository;
    private final TopicNodeRepository topicNodeRepository;

    /** 每个对话的消息序号计数器 */
    private final Map<String, Integer> messageIndexCounter = new ConcurrentHashMap<>();

    /** 上一条消息节点 ID（用于建立 NEXT 关系） */
    private final Map<String, Long> lastMessageNodeId = new ConcurrentHashMap<>();

    /** 上一条用户消息节点（用于建立 REPLIES_TO） */
    private final Map<String, MessageNode> lastUserMessage = new ConcurrentHashMap<>();

    /**
     * 知识点考察话题词典 — 用于自动识别对话所属话题
     * 实际项目中可接入 LLM 做更精准的话题提取
     */
    private static final Map<String, List<String>> TOPIC_KEYWORDS = new LinkedHashMap<>() {{
        // ===== Java 核心 =====
        put("Java基础", List.of("面向对象", "继承", "多态", "封装", "接口", "抽象类", "final", "static", "String", "equals", "hashCode"));
        put("Java集合", List.of("HashMap", "ArrayList", "LinkedList", "HashSet", "TreeMap", "ConcurrentHashMap", "CopyOnWriteArrayList", "集合框架"));
        put("Java并发", List.of("线程", "锁", "synchronized", "volatile", "AQS", "线程池", "并发", "CAS", "ReentrantLock", "CountDownLatch", "Semaphore", "CompletableFuture"));
        put("Java IO/网络", List.of("NIO", "BIO", "AIO", "Netty", "Socket", "序列化", "零拷贝", "epoll"));
        // ===== JVM =====
        put("JVM", List.of("JVM", "GC", "内存", "垃圾回收", "类加载", "OOM", "调优", "堆", "栈", "元空间", "CMS", "G1", "ZGC", "字节码"));
        // ===== 数据结构与算法 =====
        put("数据结构", List.of("数组", "链表", "HashMap", "树", "红黑树", "堆", "栈", "队列", "图", "B+树", "跳表", "布隆过滤器"));
        put("算法基础", List.of("排序", "快排", "归并", "算法", "动态规划", "递归", "二分", "贪心", "回溯", "双指针", "滑动窗口"));
        // ===== Spring 生态 =====
        put("Spring框架", List.of("Spring", "IoC", "AOP", "Bean", "事务", "Boot", "MVC", "SpringCloud", "循环依赖", "自动配置"));
        put("SpringCloud微服务", List.of("微服务", "Nacos", "Gateway", "Feign", "Sentinel", "Seata", "OpenFeign", "Ribbon", "Hystrix", "配置中心", "注册中心"));
        // ===== 数据库 =====
        put("MySQL数据库", List.of("MySQL", "索引", "事务", "SQL", "MVCC", "锁", "B+树", "分库分表", "主从", "explain", "慢查询", "InnoDB", "隔离级别", "redo", "undo", "binlog"));
        put("Redis缓存", List.of("Redis", "缓存", "String", "List", "Set", "ZSet", "Hash", "Stream", "分布式锁", "缓存雪崩", "缓存穿透", "持久化", "RDB", "AOF", "哨兵", "集群"));
        // ===== 中间件 =====
        put("消息队列", List.of("Kafka", "RocketMQ", "RabbitMQ", "消息队列", "MQ", "削峰", "异步", "解耦", "可靠性", "幂等", "顺序消息"));
        put("搜索引擎", List.of("Elasticsearch", "Lucene", "倒排索引", "分词", "ES", "全文检索"));
        // ===== 分布式系统 =====
        put("分布式系统", List.of("CAP", "BASE", "一致性", "分布式", "分布式事务", "分布式锁", "分布式ID", "限流", "熔断", "降级", "幂等"));
        put("RPC与通信", List.of("RPC", "Dubbo", "gRPC", "HTTP", "HTTPS", "TCP", "DNS", "WebSocket", "序列化", "Protobuf"));
        // ===== 架构与设计 =====
        put("设计模式", List.of("单例", "工厂", "代理", "观察者", "策略", "模板方法", "装饰器", "适配器", "责任链", "建造者", "设计模式", "依赖倒置", "开闭原则"));
        put("系统设计", List.of("系统设计", "架构", "高并发", "高可用", "秒杀", "亿级", "扩缩容", "容量规划", "DDD", "领域驱动"));
        // ===== 运维与DevOps =====
        put("DevOps与容器", List.of("Docker", "Kubernetes", "k8s", "CI/CD", "Jenkins", "GitLab", "Nginx", "负载均衡", "容器", "镜像"));
        put("Linux", List.of("Linux", "Shell", "进程", "线程", "文件系统", "IO", "top", "ps", "netstat", "grep", "awk", "管道"));
        // ===== 项目经验 =====
        put("项目管理", List.of("Git", "敏捷", "Scrum", "codeReview", "重构", "单元测试", "集成测试", "TDD", "DDD"));
        put("AI大模型", List.of("LLM", "RAG", "Agent", "Prompt", "Embedding", "向量", "MCP", "LangChain", "大模型", "AI"));
    }};

    public ConversationAnalysisService(ConversationGraphRepository graphRepository,
                                       MessageNodeRepository messageNodeRepository,
                                       TopicNodeRepository topicNodeRepository) {
        this.graphRepository = graphRepository;
        this.messageNodeRepository = messageNodeRepository;
        this.topicNodeRepository = topicNodeRepository;
    }

    // ==================== 消息实时同步 ====================
    //
    // ===== 🎯 Task 12 第二部分: 将 Neo4j 写入改为异步 =====
    // Neo4j 写入是 I/O 操作（网络请求），不需要阻塞 AI 对话的响应。
    // 把 recordUserMessage() 和 recordAssistantMessage() 加上 @Async 后，
    // 调用方立即返回，写入在后台线程中执行。
    //
    // 💡 引导问题：
    // 1. @Async 应该加在哪个类的方法上？直接在 Service 方法上加就行吗？
    //    （提示：要确保调用方是通过 Spring 代理来调这个方法）
    // 2. Neo4j 写入失败怎么办？异步方法的异常在哪里处理？
    //    （提示：实现 AsyncUncaughtExceptionHandler）
    // 3. 异步方法的返回值 void 够吗？什么时候需要返回 CompletableFuture？
    // 4. 如果快速连续发多条消息，线程池会不会爆？（提示：之前配的 AsyncConfig 在这里起作用）
    // 5. 同一个对话的消息顺序重要吗？异步执行会不会导致消息顺序错乱？

    /**
     * 记录用户消息到 Neo4j
     */
    // TODO: Task 12 — 加 @Async("taskExecutor") 注解
    public void recordUserMessage(String conversationId, String content) {
        try {
            int index = messageIndexCounter.merge(conversationId, 1, (k, v) -> v + 1);
            String messageId = conversationId + "-msg-" + index;

            // 创建消息节点
            MessageNode msg = MessageNode.builder()
                    .messageId(messageId)
                    .role("user")
                    .contentSummary(truncate(content, 200))
                    .timestamp(LocalDateTime.now())
                    .messageIndex(index)
                    .build();

            // 提取话题
            List<TopicNode> topics = extractTopics(content);
            msg.setTopics(topics);

            // 保存到图数据库
            ConversationNode conv = getOrCreateConversation(conversationId);
            msg.setRepliesTo(findPreviousAssistant(conversationId));

            // 先保存消息节点，让 Neo4j 生成 ID
            saveMessageNode(conversationId, msg);

            // 建立 NEXT 关系（在 save 之后调用，此时 msg.getId() 已有值）
            Long prevId = lastMessageNodeId.put(conversationId, msg.getId());

            // 记录最新的用户消息
            lastUserMessage.put(conversationId, msg);

            // 更新对话统计
            updateConversationStats(conversationId);

            log.debug("📝 Neo4j: 用户消息已同步 → conversationId={}, index={}, topics={}",
                    conversationId, index, topics.stream().map(TopicNode::getName).toList());
        } catch (Exception e) {
            log.error("记录用户消息到 Neo4j 失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 记录 AI 回复到 Neo4j
     */
    public void recordAssistantMessage(String conversationId, String content) {
        try {
            int index = messageIndexCounter.merge(conversationId, 1, (k, v) -> v + 1);
            String messageId = conversationId + "-msg-" + index;

            MessageNode msg = MessageNode.builder()
                    .messageId(messageId)
                    .role("assistant")
                    .contentSummary(truncate(content, 200))
                    .timestamp(LocalDateTime.now())
                    .messageIndex(index)
                    .build();

            // 建立对用户消息的回复关系
            MessageNode userMsg = lastUserMessage.get(conversationId);
            if (userMsg != null) {
                msg.setRepliesTo(userMsg);
            }

            saveMessageNode(conversationId, msg);
            updateConversationStats(conversationId);

            log.debug("🤖 Neo4j: AI回复已同步 → conversationId={}, index={}",
                    conversationId, index);
        } catch (Exception e) {
            log.error("记录 AI 回复到 Neo4j 失败: conversationId={}", conversationId, e);
        }
    }

    // ==================== 分析查询 API ====================

    /**
     * 🔥 对话路径追踪 — 返回完整对话链路
     */
    public List<Map<String, Object>> traceDialoguePath(String conversationId) {
        return graphRepository.traceDialoguePath(conversationId);
    }

    /**
     * 🔥 话题跳转分析 — 用户在不同话题间的切换
     */
    public List<Map<String, Object>> analyzeTopicTransitions(String conversationId) {
        return graphRepository.analyzeTopicTransitions(conversationId);
    }

    /**
     * 🔥 追问链深度分析 — 用户最长连续追问
     */
    public List<Map<String, Object>> analyzeFollowUpDepth() {
        return graphRepository.analyzeFollowUpDepth();
    }

    /**
     * 🔥 全局话题热力图
     */
    public List<Map<String, Object>> getTopicHeatmap() {
        return graphRepository.getTopicHeatmap();
    }

    /**
     * 🔥 意图演变追踪
     */
    public List<Map<String, Object>> traceIntentEvolution(String conversationId) {
        return graphRepository.traceIntentEvolution(conversationId);
    }

    /**
     * 🔥 综合对话分析报告
     */
    public Map<String, Object> generateAnalysisReport(String conversationId) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("conversationId", conversationId);
        report.put("dialoguePath", traceDialoguePath(conversationId));
        report.put("topicTransitions", analyzeTopicTransitions(conversationId));
        report.put("intentEvolution", traceIntentEvolution(conversationId));
        report.put("followUpDepth", analyzeFollowUpDepth());
        report.put("generatedAt", LocalDateTime.now().toString());
        return report;
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 获取或创建对话节点
     */
    private ConversationNode getOrCreateConversation(String conversationId) {
        ConversationNode existing = graphRepository.findByConversationId(conversationId);
        if (existing != null) {
            return existing;
        }
        ConversationNode conv = ConversationNode.builder()
                .conversationId(conversationId)
                .startTime(LocalDateTime.now())
                .lastActiveTime(LocalDateTime.now())
                .messageCount(0)
                .build();
        return graphRepository.save(conv);
    }

    /**
     * 保存消息节点并建立关系
     */
    private void saveMessageNode(String conversationId, MessageNode msg) {
        // 独立保存消息节点（MessageNodeRepository 负责 Message 节点的 CRUD）
        messageNodeRepository.save(msg);
    }

    /**
     * 找上一条 AI 回复（作为 REPLIES_TO 的目标）
     */
    private MessageNode findPreviousAssistant(String conversationId) {
        // 简化实现：返回 null 表示新的对话轮次
        return null;
    }

    /**
     * 更新对话统计信息
     */
    private void updateConversationStats(String conversationId) {
        ConversationNode conv = graphRepository.findByConversationId(conversationId);
        if (conv != null) {
            conv.setLastActiveTime(LocalDateTime.now());
            conv.setMessageCount((conv.getMessageCount() != null ? conv.getMessageCount() : 0) + 1);
            graphRepository.save(conv);
        }
    }

    /**
     * 基于话题词典自动提取话题
     */
    private List<TopicNode> extractTopics(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }
        List<TopicNode> matched = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : TOPIC_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (content.contains(keyword)) {
                    matched.add(TopicNode.builder()
                            .topicId(UUID.randomUUID().toString())
                            .name(entry.getKey())
                            .keywords(entry.getValue())
                            .firstMentionedAt(LocalDateTime.now())
                            .build());
                    break; // 一个话题只匹配一次
                }
            }
        }
        return matched;
    }

    /**
     * 截取字符串前 n 个字符
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
