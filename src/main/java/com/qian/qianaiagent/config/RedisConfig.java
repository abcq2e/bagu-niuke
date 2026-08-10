package com.qian.qianaiagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 *
 * ===== 🎯 Task 9 第一部分: 你来完成 =====
 * 目标：配置 RedisTemplate 的序列化方式，让存入 Redis 的数据可读。
 *
 * <h2>📖 为什么要配置序列化？</h2>
 * Spring Data Redis 默认使用 JDK 序列化（把对象转成 Java 特有的二进制格式）。
 * 问题：
 * 1. 存入 Redis 的数据是乱码（二进制），用 redis-cli 看不到原文
 * 2. JDK 序列化要求类实现 Serializable 接口
 * 3. 跨语言不兼容（Python/Go 无法读取）
 * 4. 序列化后的体积大
 *
 * 更好的方案：用 JSON 序列化（Jackson2JsonRedisSerializer）或 String 序列化。
 *
 * <h2>📖 RedisTemplate 是什么？</h2>
 * RedisTemplate 是 Spring Data Redis 的核心类，相当于 JDBC 的 JdbcTemplate。
 * 它封装了所有 Redis 操作：set/get/del/expire/hash/list/set/zset...
 *
 * <h2>📖 序列化器的选择</h2>
 * <table border="1">
 *   <tr><th>序列化器</th><th>优点</th><th>缺点</th></tr>
 *   <tr><td>JdkSerializationRedisSerializer</td><td>Java 原生，支持所有可序列化对象</td><td>二进制乱码、体积大、跨语言不兼容</td></tr>
 *   <tr><td>StringRedisSerializer</td><td>人类可读、轻量</td><td>只能存 String</td></tr>
 *   <tr><td>Jackson2JsonRedisSerializer</td><td>JSON 格式、人类可读、跨语言</td><td>需要指定类型，泛型处理稍复杂</td></tr>
 *   <tr><td>GenericJackson2JsonRedisSerializer</td><td>JSON 格式、自动处理类型</td><td>会在 JSON 中嵌入 @class 字段（安全风险）</td></tr>
 * </table>
 *
 * <h2>📖 常用模式</h2>
 * Key 用 String 序列化 → 人类可读
 * Value 用 JSON 序列化 → 人类可读 + 支持复杂对象
 * HashKey 用 String 序列化
 * HashValue 用 JSON 序列化
 *
 * 💡 引导问题：
 * 1. RedisTemplate 的 setKeySerializer() / setValueSerializer() 分别控制什么？
 * 2. setHashKeySerializer() / setHashValueSerializer() 又控制什么？
 *    （提示：opsForHash() 操作 Hash 结构时用这两个序列化器）
 * 3. 为什么 Key 用 StringRedisSerializer 就够了？
 *    （提示：Key 通常就是字符串，不需要复杂序列化）
 * 4. Value 用什么序列化器？你选的序列化器有什么优缺点？
 * 5. RedisConnectionFactory 是什么？Spring 怎么自动注入它？
 *
 * ⚠️ 注意：RedisTemplate 配置好后，你在 SemanticCacheService 里注入它就能用了
 */
@Configuration
public class RedisConfig {

    /**
     * 创建并配置 RedisTemplate
     *
     * Key 用 StringRedisSerializer → 人类可读
     * Value 用 GenericJackson2JsonRedisSerializer → JSON 格式，支持复杂对象
     *
     * @param connectionFactory Redis 连接工厂（Spring Boot 自动创建）
     * @return 配置好的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key 用 String 序列化（保证可读性）
        RedisSerializer<String> stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);

        // Value 用 JSON 序列化（支持对象，人类可读）
        RedisSerializer<Object> jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);

        // HashKey 也用 String 序列化
        template.setHashKeySerializer(stringSerializer);

        // HashValue 也用 JSON 序列化
        template.setHashValueSerializer(jsonSerializer);

        // 让上面的配置生效
        template.afterPropertiesSet();
        return template;
    }
}
