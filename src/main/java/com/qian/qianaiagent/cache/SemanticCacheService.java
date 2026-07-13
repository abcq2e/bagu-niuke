package com.qian.qianaiagent.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多层缓存策略 — Embedding 缓存 + 语义缓存（Semantic Cache）
 * <p>
 * ==================== AI 应用为什么需要缓存 ====================
 * 1. LLM API 调用成本高（每次几分钱到几毛钱）
 * 2. LLM 推理延迟大（几百毫秒到几秒）
 * 3. 很多问题是重复的或高度相似的（用户问的核心问题就那些）
 * <p>
 * ==================== 三层缓存架构 ====================
 * L1 精确缓存：问题完全相同 → 直接返回缓存（命中率低但速度最快）
 * L2 语义缓存：问题语义相似（相似度 > 0.95）→ 返回缓存（命中率中等）
 * L3 LLM 调用：缓存未命中 → 调用 LLM → 结果写入 L1 + L2
 * <p>
 * ===== 🎯 Task 9 第二部分：将 L1 缓存从 ConcurrentHashMap 迁移到 Redis =====
 * 当前 L1 用 ConcurrentHashMap（内存缓存），服务重启后全部丢失。
 * 升级为 Redis 后：
 *   1. 服务重启 → 缓存还在
 *   2. 多实例共享同一份缓存
 *   3. 可以设 TTL 自动过期（不用自己写 isExpired() 逻辑）
 *   4. 支持持久化到磁盘（Redis RDB/AOF）
 *
 * 💡 引导问题：
 * 1. 当前 exactCache.put() 和 exactCache.get() 在哪里？分别改成什么 Redis 操作？
 * 2. Redis 的 TTL 怎么设？（提示：redisTemplate.opsForValue().set(key, value, ttl, TimeUnit)）
 * 3. CacheEntry 类的 timestamp 和 hitCount 字段在 Redis 方案中还必要吗？
 *    （提示：TTL 替代了手动过期检查，但 hitCount 统计要另外设计）
 * 4. 如何注入 RedisTemplate？用 @Resource 还是构造函数注入？
 * 5. getStats() 方法（第171行）里统计 exactCache.size()，用 Redis 后怎么获取缓存大小？
 *    （提示：Redis 的 KEYS 命令可以匹配模式，但生产环境慎用）
 *
 * ⚠️ 注意事项：
 *   - Redis 的 key 建议加前缀（如 "cache:exact:"），方便管理和清理
 *   - 迁移后 isExpired() 方法可以删掉了（Redis TTL 自动处理）
 *   - 不需要改 L2 语义缓存（它用 VectorStore，独立于 Redis）
 * <p>
 * ==================== 面试亮点 ====================
 * - 语义缓存是 AI 应用的"特有缓存模式"（区别于传统 Redis 的 Key-Value 缓存）
 * - 用向量相似度判断两个问题是否"相同"，而不是字符串比较
 * - 这是 GPTCache（开源项目）的核心设计思想
 *
 * @author yupi
 */
@Service
@Slf4j
public class SemanticCacheService {

    private final VectorStore cacheVectorStore;

    /** L1 精确缓存：问题原文 → 回答 */
    // ===== 🎯 Task 9: 将 ConcurrentHashMap 改为 Redis =====
    // 💡 思考：如果改用 Redis，这个字段改成什么？
    // 提示：注入 private final RedisTemplate<String, String> redisTemplate;
    private final Map<String, CacheEntry> exactCache = new ConcurrentHashMap<>();

    /** 语义相似度阈值（超过此值视为相同问题） */
    private static final double SEMANTIC_THRESHOLD = 0.95;

    /** 缓存过期时间（毫秒），默认 30 分钟 */
    private static final long TTL_MS = 30 * 60 * 1000;

    /**
     * 构造函数
     * <p>
     * PgVectorVectorStoreConfig 已删除，改为注入当前可用的唯一 VectorStore bean（quizVectorStore）。
     * 语义缓存的 L2 向量检索使用内存向量库，重启后缓存丢失（可接受，因 L1 也基于内存）。
     * </p>
     */
    public SemanticCacheService(VectorStore vectorStore) {
        this.cacheVectorStore = vectorStore;
    }

    /**
     * 缓存条目
     */
    private static class CacheEntry {
        String answer;
        String queryEmbedding; // JSON 格式的向量（用于调试）
        long timestamp;
        int hitCount;

        CacheEntry(String answer, long timestamp) {
            this.answer = answer;
            this.timestamp = timestamp;
            this.hitCount = 1;
        }
    }

    /**
     * 🔥 三层缓存查询
     * <p>
     * 先查 L1（精确匹配），未命中再查 L2（语义匹配），
     * 仍未命中返回 null（由调用方去调用 LLM 并回写缓存）
     *
     * @param query 用户问题
     * @return 缓存的回答，null 表示未命中
     */
    public String get(String query) {
        // L1: 精确缓存
        CacheEntry exactHit = exactCache.get(query);
        if (exactHit != null && !isExpired(exactHit)) {
            exactHit.hitCount++;
            log.info("🎯 L1 缓存命中 (第{}次): {}", exactHit.hitCount, query);
            return exactHit.answer;
        }

        // 清理过期条目
        if (exactHit != null) {
            exactCache.remove(query);
        }

        // L2: 语义缓存
        String semanticHit = semanticSearch(query);
        if (semanticHit != null) {
            log.info("🔍 L2 语义缓存命中: {}", query);
            // 回写 L1 缓存
            exactCache.put(query, new CacheEntry(semanticHit, System.currentTimeMillis()));
            return semanticHit;
        }

        log.info("❌ 缓存未命中，准备调用 LLM: {}", query);
        return null;
    }

    /**
     * 写入缓存（LLM 调用后回写）
     *
     * @param query  用户问题
     * @param answer LLM 回答
     */
    public void put(String query, String answer) {
        // L1: 写入精确缓存
        exactCache.put(query, new CacheEntry(answer, System.currentTimeMillis()));

        // L2: 写入语义缓存（将 query+answer 作为文档向量化并存入向量库）
        try {
            // ===== 🎯 Task 1: 你来完成 =====
            // 目标：把 query + answer 存到向量库，让 L2 缓存生效
            //
            // 💡 引导问题（先想清楚再写代码）：
            // 1. Document 构造函数接收哪两个参数？类型分别是什么？
            // 2. 去 semanticSearch() 方法（本文件第142行）看看，读缓存时从 metadata 里取的是什么 key？
            // 3. 写入时 metadata 的 key 必须和读取时一致，你打算用什么 key？
            // 4. cacheVectorStore.add() 接收什么参数类型？单个 Document 还是 List<Document>？
            //
            // 📖 基础知识: Map.of("key1", val1, "key2", val2) 可以快速创建不可变 Map
            // 📖 基础知识: List.of(item) 创建只有一个元素的不可变 List
            //
            // ⚠️ 注意: 写入后 semanticSearch() 应该能查到刚写入的内容
            //
            // 你的代码写在这里 ↓



            // 你的代码写在这里 ↑
            log.debug("语义缓存写入: {}", query.substring(0, Math.min(50, query.length())));
        } catch (Exception e) {
            log.warn("语义缓存写入失败: {}", e.getMessage());
        }
    }

    /**
     * 🔥 语义检索 — 用向量相似度找"相同意思"的缓存问题
     * <p>
     * 【提示词留给你完善】
     * 考察点：相似度阈值的调优
     * - 阈值太高（0.98）→ 很多语义相同的问题不会命中缓存
     * - 阈值太低（0.85）→ 不同问题可能被错误匹配
     * - 最佳实践：根据业务场景做 A/B 测试确定最优阈值
     */
    private String semanticSearch(String query) {
        try {
            List<Document> results = cacheVectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(1)
                            .similarityThreshold(SEMANTIC_THRESHOLD)
                            .build()
            );
            if (!results.isEmpty()) {
                // 从匹配文档的元数据中取出缓存的回答
                return (String) results.get(0).getMetadata().get("cachedAnswer");
            }
        } catch (Exception e) {
            log.debug("语义缓存检索失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 检查缓存是否过期
     */
    private boolean isExpired(CacheEntry entry) {
        return System.currentTimeMillis() - entry.timestamp > TTL_MS;
    }

    /**
     * 获取缓存统计
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("exactCacheSize", exactCache.size());
        stats.put("totalHits", exactCache.values().stream().mapToInt(e -> e.hitCount - 1).sum());
        return stats;
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        exactCache.clear();
        log.info("缓存已清空");
    }
}
