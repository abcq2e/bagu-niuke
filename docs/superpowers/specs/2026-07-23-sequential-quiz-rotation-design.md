# 严格顺序轮询考察机制 — 设计文档

## 目标

将当前复杂的加权随机轮询机制改造为**完全确定性**的顺序轮询。所有操作可预测、可复现、易于理解和维护。

## 核心原则

- **确定性**：没有随机、没有加权、没有动态停留控制
- **顺序轮询**：16个方向按学习路线顺序依次考察
- **题目序号化**：每个方向的题目严格按序号出题，一轮取一个区段
- **自动淘汰**：题目耗尽的方���自动退出轮询

## 16个方向定义（学习路线顺序）

| # | 方向名 | 每轮题数 | 源文件 |
|---|--------|---------|--------|
| 1 | Java基础与集合 | 3 | bagu-java-basics → 面渣逆袭-Java基础 → 面渣逆袭-集合框架 |
| 2 | JVM | 3 | bagu-jvm → 面渣逆袭-JVM |
| 3 | Java并发 | 3 | bagu-java-concurrency → 面渣逆袭-并发编程 |
| 4 | 操作系统与Linux | 3 | bagu-os-linux → 面渣逆袭-操作系统 |
| 5 | 计算机网络 | 3 | bagu-network → 面渣逆袭-计算机网络 |
| 6 | MySQL | 3 | bagu-mysql → 面渣逆袭-MySQL |
| 7 | Redis | 3 | bagu-redis → 面渣逆袭-Redis |
| 8 | 消息队列 | 3 | bagu-mq → 面渣逆袭-RocketMQ |
| 9 | Spring框架 | 3 | bagu-spring → 面渣逆袭-Spring → 面渣逆袭-MyBatis |
| 10 | 设计模式 | 2 | bagu-design-patterns |
| 11 | 分布式与微服务 | 2 | bagu-distributed → 面渣逆袭-分布式 → 面渣逆袭-微服务 |
| 12 | 系统设计与场景 | 2 | bagu-system-design |
| 13 | 算法与数据结构 | 2 | bagu-algorithm |
| 14 | Docker与运维 | 2 | bagu-docker |
| 15 | ES与搜索 | 2 | bagu-es-search |
| 16 | Agent与AI应用 | 2 | bagu-agent-ai |

## 游标模型

```
SequentialCursor {
  round: 0,                    // 当前第几轮
  activeDirectionIndex: 0,     // 当前在活跃方向列表中的位置
  activeDirections: [          // 尚未耗尽的活跃方向
    { name: "Java基础与集合", questionsPerRound: 3, totalQuestions: 234, nextStartIndex: 0 },
    ...
  ]
}
```

## 考察流程

```
每轮 (round N):
  for each 活跃方向:
    取题: [nextStartIndex, nextStartIndex + questionsPerRound)
    逐题考察（一问一答）
    nextStartIndex += questionsPerRound
    如果 nextStartIndex >= totalQuestions → 标记 exhausted，移出活跃列表
  
  所有活跃方向考察完毕 → round++, 从活跃方向列表第0个重新开始
```

## 需要改动的文件

### 新增
- `SequentialRotationService.java` — 精简的顺序轮询服务（~200行）

### 修改
- `QuizApp.java` — 移除热力图/DETAIL/DIM/维度相关逻辑，对接新服务
- `TopicDocumentCache.java` — 新增有序题目获取方法
- `TopicRotationService.java` — 精简为纯静态映射容器

### 删除/归档
- `QuestionSelector.java` — 不再需要
- `DimensionLabeler.java` — 不再需要
- `DimensionValidator.java` — 不再需要
- `TopicDimensions.java` — 不再需要

### 保留不变
- `UserAbilityService.java` — 能力评分功能保留
- `AskedPointTracker.java` — 已考点追踪保留
- `TopicMemoryTrimmer.java` — 记忆裁剪保留
- `KnowledgePointCatalog.java` — 保留但简化
- RAG 检索、ChatMemory — 完全不变
