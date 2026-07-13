# 🔴 Redis 缓存层集成教程

> Task 9 | 难度 ⭐⭐⭐ | 涉及文件: `RedisConfig.java`、`SemanticCacheService.java`、`JwtAuthFilter.java`

---

## 一、为什么要学 Redis？

### 面试场景

面试官："你们项目里用了缓存吗？"
你："用了 Redis。把 L1 内存缓存迁移到了 Redis，还做了 Token 黑名单。"
面试官："Redis 有哪些数据结构？缓存穿透怎么解决？Redis 和数据库一致性怎么保证？"

**Redis 是 Java 后端面试最高频的问题，没有之一。**

### 你到底要做什么

当前项目的 L1 缓存存在内存里（`ConcurrentHashMap`）：
- 服务重启 → 缓存全丢
- 不能多实例共享
- 没有持久化
- TTL 要自己写代码判断

你需要把它迁移到 Redis。

---

## 二、Redis 基础概念

### Redis 是什么？

Redis 是一个**内存数据库**。把它想象成一个"全局的 HashMap"，所有服务实例都能访问它：

```
服务器 A ──→ Redis（共享缓存）←── 服务器 B
              ↑
              所有实例读写同一份数据
```

### Redis 的 5 种基本数据结构

| 结构 | 类比 Java | 常用场景 |
|------|-----------|----------|
| String | `Map<String, String>` | 缓存、计数器、分布式锁 |
| Hash | `Map<String, Map<String,String>>` | 存储对象（用户信息） |
| List | `LinkedList<String>` | 消息队列、时间线 |
| Set | `HashSet<String>` | 标签、去重、共同好友 |
| ZSet | `TreeSet`（带分数） | 排行榜、优先级队列 |

**你当前任务主要用 String 结构**（存缓存数据）。

### Redis TTL（过期时间）

Redis 可以为每个 key 设 TTL，到期自动删除——不需要你写 `isExpired()` 判断！

```bash
# Redis 命令（帮你理解概念，不是 Java 代码）
SET user:1 "zhangsan"    # 存入
EXPIRE user:1 1800        # 30 分钟后自动删除
TTL user:1                # 查看还剩多少秒
```

---

## 三、Spring Data Redis 入门

### 核心类: RedisTemplate

`RedisTemplate` 是 Spring 对 Redis 操作的封装。注入它后，你能用面向对象的方式操作 Redis。

> 🧠 **思考**：你需要在代码里注入 `RedisTemplate`。它有哪些常用方法？去 IDEA 里 Ctrl+N 搜 `RedisTemplate`，看看它的 API。
>
> 几个你需要用到的操作：
> - `opsForValue()` — String 操作（get/set/increment）
> - `opsForHash()` — Hash 操作
> - `opsForSet()` — Set 操作
> - `expire(key, timeout, unit)` — 设置过期时间

### 序列化问题（你需要在 RedisConfig 里配置）

Java 对象不能直接存到 Redis（Redis 只认字节）。序列化器负责 Java 对象 ↔ 字节的转换。

**默认序列化器问题**: Spring Data Redis 默认用 JDK 序列化，存在 Redis 里的是二进制乱码。

你的任务：在 `RedisConfig.java` 中把序列化器换成 JSON。

> 🧠 **引导**：
> 1. `StringRedisSerializer` — 用于序列化 String（key 和简单 value）
> 2. `GenericJackson2JsonRedisSerializer` — 用于序列化复杂对象
> 3. Key 为什么用 String 序列化就够了？
> 4. `setKeySerializer` 和 `setHashKeySerializer` 有什么区别？
> 5. `afterPropertiesSet()` 为什么必须调用？
>
> 📚 **基础补充**：`GenericJackson2JsonRedisSerializer` 存到 Redis 的 JSON 里会自带 `@class` 字段标记 Java 类型，反序列化时自动还原成正确的类。缺点是暴露了类的全限定名。

---

## 四、你的三个子任务

### 子任务 1: 配置 RedisTemplate 序列化（RedisConfig.java）

**文件**: `config/RedisConfig.java`（已生成骨架）

**目标**: 让 Redis 存的 key 和 value 都是人类可读的 JSON。

> 🧠 **引导问题**（按顺序解决）：
> 1. 你需要创建一个 `RedisTemplate<String, Object>` Bean
> 2. 设置 key 的序列化器 → 用什么？
> 3. 设置 value 的序列化器 → 用什么？
> 4. 设置 hash key 和 hash value 的序列化器
> 5. 调 `afterPropertiesSet()` 初始化

<details>
<summary>🆘 提示——思考方向，不是代码</summary>

```
1. @Bean 方法返回 RedisTemplate<String, Object>
2. redisTemplate.setKeySerializer(用什么序列化器？)
3. redisTemplate.setValueSerializer(用什么序列化器？)
4. redisTemplate.setHashKeySerializer(用什么序列化器？)
5. redisTemplate.setHashValueSerializer(用什么序列化器？)
6. redisTemplate.afterPropertiesSet() — 必须调
```
</details>

---

### 子任务 2: L1 缓存迁移到 Redis（SemanticCacheService.java）

**文件**: `cache/SemanticCacheService.java`（已有引导注释）

**目标**: 把 `ConcurrentHashMap` 的 `get()`/`put()` 替换成 Redis 操作。

> 🧠 **引导问题**（不要同时想，一个一个来）：
>
> **问题 1**：当前 `exactCache.get(query)` 改成什么 Redis 操作？
> - 提示：Redis 的 GET 对应哪个方法？
>
> **问题 2**：当前 `exactCache.put(query, entry)` 改成什么？
> - 需要同时设值和 TTL 吗？还是分两步？
> - TTL 设多少？60 分钟？还是更久？
>
> **问题 3**：`CacheEntry` 类的 `timestamp` 和 `hitCount` 字段还需要吗？
> - `timestamp` → Redis TTL 替代了，不需要
> - `hitCount` → 你想保留吗？可以用什么 Redis 命令替代？
>
> **问题 4**：`isExpired()` 方法还要吗？（提示：Redis TTL 自动过期）
>
> **问题 5**：`clear()` 和 `getStats()` 方法怎么改？
>
> 📚 **基础补充**：Redis key 命名规范
> ```
> cache:exact:<问题原文>
> 例如: cache:exact:怎么学Java
> ```
> 用冒号分隔层级，加业务前缀方便清理。

---

### 子任务 3: Token 黑名单（JwtAuthFilter.java）

**文件**: `filter/JwtAuthFilter.java`（已有引导注释）

**场景**: 用户点"退出登录"，前端删了 Token。但如果 Token 被截获，还能用。后台需要一个黑名单。

**目标**: 在 Token 校验通过后、放行之前，检查 Token 是否在黑名单中。

> 🧠 **引导问题**：
>
> **问题 1**：黑名单用什么 Redis 数据结构？
> - Set：`SISMEMBER` 检查是否存在，O(1)
> - String：`GET` 检查是否为 "1"，O(1)
> - 两种方案各自的优缺点？
>
> **问题 2**：黑名单 key 命名怎么设计？
> - 是存完整 Token 还是存 JWT 的 jti（JWT ID）？
> - 提示：Token 很长，存完整 Token 占内存
>
> **问题 3**：黑名单条目的 TTL 设多久？
> - 应该和 JWT 剩余有效期一致
> - 怎么获取 JWT 的过期时间？
>
> **问题 4**：如果 Redis 连接失败，应该放行还是拦截？
> - 放行 → 安全风险（退出后的 Token 可能还能用）
> - 拦截 → 可用性风险（所有人登录不了）
> - **面试标准答案**：记录日志 + 降级放行（优先保证可用性）

---

## 五、验证方法

1. **启动 Redis**: `docker-compose up -d redis`
2. **启动项目**: `mvn spring-boot:run`
3. **测试缓存**: 问 AI 同一个问题两次，日志应显示第二次命中 L1 缓存
4. **验证 Redis 有数据**: `docker exec -it yu-ai-redis redis-cli` → `KEYS *` 看到缓存 key
5. **验证 TTL**: `TTL cache:exact:xxx` 看到剩余秒数
6. **验证持久化**: 重启应用，再问同一个问题，缓存仍然命中

---

## 六、面试常见追问（学完后去了解）

- Redis 缓存穿透/击穿/雪崩是什么？怎么解决？
- Redis 和数据库的数据一致性怎么保证？
- Redis 的持久化 RDB 和 AOF 有什么区别？
- 为什么 Redis 单线程还这么快？

---

## 🧠 自检清单

- [ ] 能解释为什么 Redis 比 ConcurrentHashMap 更适合做缓存
- [ ] 知道 Redis 5 种数据结构分别在什么场景使用
- [ ] 能配置 RedisTemplate 的 JSON 序列化
- [ ] 理解 TTL 替代手动过期检查
- [ ] 能设计 Token 黑名单的 Redis 存储方案
- [ ] 知道缓存穿透/击穿/雪崩的概念
