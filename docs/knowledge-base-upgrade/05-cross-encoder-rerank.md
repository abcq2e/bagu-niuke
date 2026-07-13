# 第 5 篇：Cross-Encoder 重排序 — 从 100 条结果中挑出最好的 5 条

> 难度：⭐⭐⭐⭐ ｜ 学习价值：⭐⭐⭐⭐ ｜ 核心概念：Bi-Encoder vs Cross-Encoder、重排序

---

## 📖 先读这个：向量检索为什么不够精确？

### Bi-Encoder 的局限性

你当前使用的向量检索（`similaritySearch`）底层是 **双编码器** 架构：

```
Query ──→ Embedding Model ──→ 向量_A
                                     ├── 余弦相似度 → 分数
Document → Embedding Model ──→ 向量_B
```

Query 和 Document 是 **分别独立编码** 的，它们之间没有直接交互。
就像两个人分别写下对同一件事的理解，然后比较笔记——信息在编码过程中就丢失了。

### Cross-Encoder 的解决方式

```
Query + Document ──→ Transformer 模型 ──→ 相关性分数
```

Query 和 Document **拼接后联合编码**，Transformer 的注意力机制让每个 token 
都能"看到"对方， 捕捉到更细粒度的语义交互。

### 核心权衡

| | Bi-Encoder（向量检索） | Cross-Encoder（重排序） |
|---|---|---|
| 速度 | 快（向量索引 + ANN） | 慢（每对 Query-Doc 都要过模型） |
| 精度 | 一般 | 高 |
| 适用规模 | 百万级文档 | 几十到几百条 |
| 角色 | 粗筛（召回） | 精排 |

知识库文档中的经典流程：

```
向量检索 Top-100 → Reranker 精排 → Top-5 → 送入 LLM
```

---

## 🧠 先想一想

1. 你当前 `buildContext` 中 `topK=5`，直接取向量检索的前 5 条。如果有 100 条候选文档，前 5 条真的是最好的 5 条吗？
2. 如果你把 topK 调到 100，用 Reranker 精排后取前 5，会不会比直接取 Top-5 更好？
3. Reranker 需要比较每一对 (Query, Document)，如果召回 100 条，就要跑 100 次模型。这个成本你能接受吗？

---

## 📚 基础补充：理解 Reranker 的工作原理

Reranker 不生成新的向量，它是这样工作的：

```
输入：Query "线程池满了怎么办"
      Candidate Docs [Doc1, Doc2, Doc3, ..., Doc100]

对每个 Candidate Doc：
    Text = "[CLS] 线程池满了怎么办 [SEP] Doc内容 [SEP]"
    送入 Cross-Encoder Transformer
    输出：一个 0~1 的相关性分数

按分数重新排序 → 取 Top-5
```

### 关键认知

Reranker **不存储索引**，它每次都是全新计算。这意味着：
- 它不能替代向量数据库（速度太慢）
- 它只适合在召回之后做精排
- 召回数量 K 的选择直接影响延迟和精度的平衡

文档给出的经验值：**K=50~100 是常见选择，根据延迟要求调整。**

---

## 🏗️ 动手升级：设计方案

### 整体架构

```
现有流程：
  VectorStore.similaritySearch(topK=5) → 5 条文档 → LLM

升级后流程：
  VectorStore.similaritySearch(topK=50) → 50 条文档
      → Reranker.rerank(query, 50 docs) → 取 Top-5 → LLM
```

### 引导问题（先设计再动手）

1. Reranker 是一个独立的类还是一个方法？它需要什么依赖？
2. 你的项目中用什么模型做 Rerank？有以下选择：
   - 用 LLM（DeepSeek）做 Rerank（通过 Prompt 打分）
   - 用 DashScope 提供的 Rerank API
   - 用开源 Reranker 模型（如 bge-reranker）
3. 你觉得对初学者来说，哪个方案最容易实现且学习价值最大？

> **提示**：用 LLM 做 Rerank 是最容易入手的方案。你只需要写一个 Prompt，让 LLM 给每个 (Query, Document) 对打分。

---

## 🏗️ 动手升级：第 1 步 — 用 LLM 实现简单 Reranker

### Prompt 设计指导

你需要设计一个 Prompt，让 LLM 输出一个结构化的相关性分数。

> **你的任务 1**：设计 Rerank Prompt。思考以下问题：
> - 怎么让 LLM 只输出分数，不输出解释？（节省 token）
> - 一次能比较多少个文档？（一次比较太多，LLM 可能搞混）
> - 分数应该是整数还是小数？范围是多少？

### 批量处理策略

如果你召回 50 条文档，不可能一次全塞给 LLM。你需要分批。常见的做法是：

```
方式 A：每条单独打分（精确但慢，50 条 = 50 次 LLM 调用）
方式 B：每 5 条一组打分（快但不够精确，50 条 = 10 次 LLM 调用）
方式 C：全部一次打分（最快但最不精确，50 条 = 1 次 LLM 调用但有 token 限制）
```

> **你的任务 2**：选择合适的批量处理策略，实现 Reranker 的核心逻辑。建议从方式 B 开始。

---

## 🏗️ 动手升级：第 2 步 — 创建 Reranker 类

在 `rag` 包下新建类。

### 核心方法签名参考

```java
/**
 * 对检索结果进行重排序
 * @param query 用户查询
 * @param documents 粗筛结果（50-100 条）
 * @param topN 精排后保留的数量（通常 3-5 条）
 * @return 精排后的 Top-N 文档列表
 */
public List<Document> rerank(String query, List<Document> documents, int topN)
```

### 引导问题

1. 这个类需要注入什么 Spring Bean？你需要调用 LLM 来打分。
2. 第一次检索召回 50 条 → Rerank → 取 5 条。这个流程是在 `Reranker` 类内部完成，还是在 `QuizApp` 中编排？
3. 如果 Rerank 失败（比如 LLM 调用超时），怎么降级？直接返回原始的前 5 条？

> **你的任务 3**：新建 `LLMReranker` 类，实现上述逻辑。包含异常处理和降级策略。

---

## 🏗️ 动手升级：第 3 步 — 集成到 QuizApp

修改 `buildContext` 方法，在向量检索后加入 Rerank 步骤。

### 引导问题

1. Rerank 是可选的还是必须的？你需要一个开关来控制是否启用 Rerank。
2. Rerank 会增加多少延迟？如果用户等不及，怎么处理？
3. 你怎么验证 Rerank 确实提升了检索质量？——用第 4 篇的 RAGAS 评估！

> **你的任务 4**：在 `QuizApp.buildContext` 中集成 Reranker，添加启用/禁用的配置项（通过 `application.yml` 控制）。

---

## 📊 文档建议的 Rerank 实践要点

| 要点 | 说明 |
|------|------|
| 召回数量 K | K 太小漏文档，K 太大延迟高。K=50~100 是常见选择 |
| 截断阈值 | Reranker 输出的分数可以设阈值，低于阈值的结果直接丢弃 |
| 多路召回 + RRF | 如果你实现了混合检索（BM25 + Dense），可以先用 RRF 融合排名，再送 Reranker |
| 缓存 | 相同的 (Query, Document) 对，可以缓存 Rerank 分数 |

---

## ✅ 自我检验清单

- [ ] 能用自己的话解释 Bi-Encoder 和 Cross-Encoder 的区别
- [ ] 设计了合理的 Rerank Prompt
- [ ] 实现了 `LLMReranker` 类，包含批量处理和降级策略
- [ ] 在 `QuizApp` 中集成了 Reranker
- [ ] 添加了 Rerank 的启用/禁用配置
- [ ] 用 RAGAS 评估对比了 Rerank 前后的 Context Precision 变化

---

## 💡 延伸思考

文档提到了"多路召回 + RRF 融合"后再 Rerank。如果你同时有 BM25 检索结果和向量检索结果，如何用 RRF 融合排名？RRF 公式中的 k=60 是什么意思？这些是你可以进一步探索的方向。

---

> 📌 下一篇：[第 6 篇：PGVector 向量存储持久化](./06-pgvector-persistence.md) — 从"玩具"到"能用"
