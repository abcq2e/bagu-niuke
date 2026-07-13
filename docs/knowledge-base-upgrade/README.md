# 知识库升级教程 —— 从"能用"到"好用"

本目录包含 6 篇升级教程，基于《如何构建一个好的"知识库"》文档中的最佳实践，
对你当前 qian-ai-agent 项目的 RAG 知识库进行系统性升级。

## 学习路线图

```
第 1 篇 ──→ 第 2 篇 ──→ 第 3 篇 ──→ 第 4 篇 ──→ 第 5 篇 ──→ 第 6 篇
(基础)      (进阶)      (核心)      (评估)      (精排)      (生产)
  │           │           │           │           │           │
  ▼           ▼           ▼           ▼           ▼           ▼
文档切分    Multi-Query   HyDE      RAGAS评估    Rerank    PGVector
                                                              │
            ┌─────────────────────────────────────────────────┘
            ▼
第 7 篇 ──→ 第 8 篇 ──→ 第 9 篇 ──→ 第 10 篇
(可靠性)     (智能化)     (架构)      (评估)
  │           │           │           │
  ▼           ▼           ▼           ▼
Tool设计    Prompt工程  Plan-Execute  端到端评估
+ Trace     + 自愈       + 协作        + 基线
```

## 教程列表

| # | 教程 | 难度 | 核心学习点 |
|---|------|------|-----------|
| 1 | [启用文档切分](./01-chunking-strategy.md) | ⭐ | Token 概念、Chunking 参数、权衡思维 |
| 2 | [Multi-Query 查询扩展](./02-multi-query-expansion.md) | ⭐⭐ | 多角度检索、结果去重、List 操作 |
| 3 | [HyDE 假设性文档嵌入](./03-hyde-implementation.md) | ⭐⭐⭐ | 语义空间、生成式检索、设计模式 |
| 4 | [RAGAS 评估体系](./04-ragas-evaluation.md) | ⭐⭐⭐ | 评估指标、LLM-as-Judge、测试思维 |
| 5 | [Cross-Encoder 重排序](./05-cross-encoder-rerank.md) | ⭐⭐⭐⭐ | Bi-Encoder vs Cross-Encoder、排序策略 |
| 6 | [PGVector 向量存储持久化](./06-pgvector-persistence.md) | ⭐⭐⭐⭐ | 向量数据库、Docker 部署、生产化 |
| 7 | [Agent 工具设计与可观测性](./07-agent-tool-observability.md) | ⭐⭐⭐ | Tool 设计原则、错误处理模式、执行追踪 |
| 8 | [结构化 Prompt 工程与自愈循环](./08-prompt-engineering-self-healing.md) | ⭐⭐⭐ | 模块化 Prompt、自我反思、错误恢复 |
| 9 | [Agent 架构升级：Plan-and-Execute](./09-agent-architecture-upgrade.md) | ⭐⭐⭐⭐ | 任务分解、结构化工作流、面向对象设计 |
| 10 | [Agent 端到端评估体系](./10-agent-evaluation-system.md) | ⭐⭐⭐⭐ | 评分器设计、基线管理、稳定性评估 |

## 使用方式

1. **按顺序学习**：每篇教程依赖前一篇的知识
2. **先思考再动手**：每篇开头有"先想一想"环节，不要跳过
3. **完成检验标准**：每篇结尾有自我检验清单
4. **遇到困难**：卡住 15 分钟后回来看提示，不要直接看答案

## 重要原则

- 🔴 **核心代码你自己写** —— 教程只给思路和引导，不给完整实现
- 🟡 **样板代码我来生成** —— 不影响学习的机械性代码直接提供
- 🟢 **注释是思考的引子** —— 源码中的注释帮你回忆关键概念
