# 第 6 篇：PGVector 向量存储持久化 — 从"玩具"到"能用"

> 难度：⭐⭐⭐⭐ ｜ 学习价值：⭐⭐⭐ ｜ 核心概念：向量数据库、Docker、生产化部署

---

## 📖 先读这个：你当前的向量存储有什么问题？

打开你的 `QuizVectorStoreConfig.java`：

```java
SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
```

`SimpleVectorStore` 是 **基于内存** 的向量存储。每次重启应用，所有向量数据全部丢失，需要重新加载、重新向量化、重新入库。

### 这意味着什么？

1. **启动慢**：每次启动都要重新加载所有文档、调用 Embedding API 向量化
2. **成本高**：Embedding API 按 token 计费，每次重启都在烧钱
3. **不能动态更新**：加一篇新文档需要重启应用
4. **不支持水平扩展**：多个实例各自维护一份内存向量库

### PGVector 是什么？

PostgreSQL 的向量扩展（pgvector），让你的关系型数据库支持向量存储和相似度检索。它是目前最流行的开源向量数据库方案之一。

---

## 🧠 先想一想

1. 你现在的 `SimpleVectorStore` 中的数据存在哪里？（提示：不是硬盘）
2. 如果你有 100 篇文档要索引，每次重启都重新 Embedding 100 次，你的 DashScope API 配额够用吗？
3. 向量数据存到数据库后，你还需要每次启动时重新加载文档吗？还是只需要加载一次？

---

## 📚 基础补充：Docker 快速入门

PGVector 最简单的安装方式是通过 Docker。如果你还没用过 Docker，这里给你一个最小入门：

```bash
# 一条命令启动 PostgreSQL + PGVector
docker run -d \
  --name pgvector \
  -e POSTGRES_PASSWORD=your_password \
  -e POSTGRES_DB=yu_ai_agent \
  -p 5432:5432 \
  pgvector/pgvector:pg16
```

**关键概念**：
- `-d`：后台运行
- `--name`：给容器起个名字
- `-e`：环境变量（配置数据库密码等）
- `-p`：端口映射（主机端口:容器端口）

---

## 🏗️ 动手升级：第 1 步 — 环境准备

### 你需要做的事情

1. 安装 Docker Desktop（如果还没装）
2. 启动 PGVector 容器
3. 用数据库客户端（如 DBeaver、Navicat）连接验证
4. 在数据库中启用 pgvector 扩展：`CREATE EXTENSION IF NOT EXISTS vector;`

> **你的任务 1**：完成上述环境准备。这是唯一不能跳过的一步。

---

## 🏗️ 动手升级：第 2 步 — 修改 application.yml

你的 `application.yml` 中 PGVector 配置已经被注释掉了（第 65-71 行）：

```yaml
#    vectorstore:
#      pgvector:
#        index-type: HNSW
#        dimensions: 1536
#        max-document-batch-size: 10000
```

### 引导问题

1. 除了取消注释，你还需要添加什么配置？数据库连接信息在哪里配？
2. `index-type: HNSW` 是什么意思？为什么不用 IVFFlat？（提示：文档里提到了这个）
3. `dimensions: 1536` 和你的 Embedding 模型匹配吗？你去 DashScope 文档查一下 `text-embedding-v2` 的维度。

> **你的任务 2**：补全 PGVector 配置。你需要添加数据库连接 URL、用户名、密码等配置。

---

## 🏗️ 动手升级：第 3 步 — 修改 QuizVectorStoreConfig

当前代码：

```java
@Bean
VectorStore quizVectorStore(@Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel) {
    SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
    // ... ETL 流程
    simpleVectorStore.add(enrichedDocuments);
    return simpleVectorStore;
}
```

你需要改成返回 PGVector 的 VectorStore 实现。

### 引导问题

1. Spring AI 中 PGVector 的 VectorStore 实现类叫什么？（提示：去 Spring AI 文档搜索 "PgVectorStore"）
2. 你怎么创建这个 Bean？是用 `PgVectorStore.builder()` 还是 Spring Boot 自动配置？
3. 切换到 PGVector 后，你还需要每次都执行 ETL 流程（加载文档 → 切分 → 增强 → 入库）吗？如果数据已经入库了，怎么做增量更新？

> **你的任务 3**：修改 `QuizVectorStoreConfig`，让 VectorStore Bean 从 PGVector 数据库读取，而不是内存。同时设计一个初始化策略：首次启动时执行完整 ETL，后续启动时只做增量更新或跳过。

---

## 🏗️ 动手升级：第 4 步 — 处理数据迁移

你已经有了 `SimpleVectorStore` 中的数据。切换到 PGVector 后：

### 引导问题

1. 你需要把内存中的数据迁到 PGVector 吗？还是直接重新加载更好？
2. 如果直接重新加载（让 ETL 重新跑一次），会有什么问题？你的 Embedding API 调用次数够吗？
3. 文档中提到的 `max-document-batch-size: 10000` 参数是做什么用的？一次插入 10000 条和一次插入 100 条有什么区别？

> **你的任务 4**：设计数据初始化策略。建议：使用 `@PostConstruct` 注解的方法，在 Bean 初始化后判断数据库是否为空，如果为空则执行完整 ETL。

---

## 🏗️ 动手升级：第 5 步（挑战）— 动态文档管理

有了持久化存储，你可以做更多事情：动态添加、删除、更新文档，而不需要重启应用。

### 引导问题

1. 如果要加一个 REST 接口来上传文档，你需要做哪些步骤？（接收文件 → 加载 → 切分 → 向量化 → 入库）
2. 如果要删除某篇文档的所有向量，怎么操作？PGVector 支持按 metadata 过滤删除吗？

> **你的任务 5（挑战）**：在 `AiController` 中添加一个上传文档的 REST 接口。

---

## ✅ 自我检验清单

- [ ] PGVector 环境搭建成功，数据库可连接
- [ ] `application.yml` 中 PGVector 配置已启用
- [ ] `QuizVectorStoreConfig` 改为返回 PGVector 的 Bean
- [ ] 应用重启后，向量数据仍然存在（不需要重新 Embedding）
- [ ] 能用 RAGAS 评估验证：切换到 PGVector 后检索质量没有下降
- [ ] （挑战）实现了动态文档上传接口

---

## 💡 延伸思考

文档中提到了一些更前沿的存储方案，比如 OpenViking 的"文件系统范式"（Filesystem Paradigm），它把知识库组织成文件系统一样的层级结构，支持更复杂的上下文管理。等你以后做更复杂的 Agent 项目时，可以回来看看这个方向。

---

## 🎓 系列总结

恭喜你完成了全部 6 篇升级教程！回顾你学到了什么：

| 环节 | 你做了什么 | 对应的编程能力 |
|------|-----------|--------------|
| 切分 | 理解 Chunking 原理，调整参数 | 参数调优思维 |
| Multi-Query | 集成查询扩展，List 操作 | Java 集合操作 |
| HyDE | 从零实现生成式检索 | 理解论文 + 代码实现 |
| RAGAS | 建立评估体系 | 测试思维 + LLM-as-Judge |
| Rerank | 实现精排序 | 架构设计 + Prompt 工程 |
| PGVector | 持久化部署 | Docker + 数据库 + 生产化 |

现在你的知识库从"玩具级"提升到了"接近生产级"。去试试吧！
