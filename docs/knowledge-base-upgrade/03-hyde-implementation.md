# 第 3 篇：HyDE 假设性文档嵌入 — RAG 领域最精妙的思想

> 难度：⭐⭐⭐ ｜ 学习价值：⭐⭐⭐⭐⭐ ｜ 核心概念：语义空间转换、生成式检索

---

## 📖 先读这个：RAG 领域最重要的论文思想之一

### 一个反直觉的洞察

HyDE（Hypothetical Document Embeddings）的核心思想来自 2022 年的一篇论文，它发现了一个反直觉但极其有效的现象：

```
传统做法：用户问题 → Embedding → 向量检索 → 找相关文档
HyDE 做法：用户问题 → LLM 生成假设性答案 → 拿"假答案"的 Embedding 去检索

结果：用假答案检索比用真问题检索更准！
```

### 为什么会这样？

想象你要找一篇关于"Java 线程池拒绝策略"的文章。

- **你用问题检索**："线程池满了怎么办？"→ 问题很短，信息量少，向量表示不精确
- **你用答案检索**：先生成一个假设答案"线程池满了有 4 种拒绝策略：AbortPolicy 抛出异常、
- CallerRunsPolicy 由调用线程执行、DiscardPolicy 直接丢弃、DiscardOldestPolicy 丢弃最旧任务..." → 答案很长，信息量大，向量表示精确

答案的语义空间和答案的语义空间更近！你用"假答案"的向量去匹配"真答案"（知识库），比用"问题"匹配"答案"效果好得多。

> 知识库文档引用论文：*Precise Zero-Shot Dense Retrieval without Relevance Labels* (https://arxiv.org/abs/2212.10496)

---

## 🧠 先想一想（这题很重要）

1. 用户问"HashMap 为什么是线程不安全的？"，请你先用大脑生成一个 3-5 句话的"假设答案"。
2. 这个假设答案中包含了哪些关键词？（比如：扩容、死循环、数据覆盖、CAS 没有）
3. 对比原始问题"HashMap 为什么是线程不安全的"，假设答案的文本长度和信息密度有什么不同？
4. 如果 LLM 生成的假设答案本身有错误（比如把 HashMap 和 ConcurrentHashMap 的机制搞混了），用它去做检索会怎样？

第 4 个问题是一个真实的风险，文档中没有明确讨论，但你应该思考。

---

## 📚 基础补充：设计模式 — 策略模式预览

HyDE 的实现很适合用一种叫"策略模式"的设计思想（你还不需要学完整设计模式，先感受一下）：

```
检索策略接口
    ├── 普通向量检索（你现在用的）
    ├── Multi-Query 检索（第 2 篇学的）
    └── HyDE 检索（本篇要写的）
```

为什么要这样设计？因为 `QuizApp` 中的 `buildContext` 方法不需要关心用的是哪种检索策略，它只管拿到结果。这样你可以随时切换或组合策略。

> **这不是你必须要做的**，但如果你以后想写出更优雅的代码，可以想想怎么把这三篇学的检索策略统一起来。

---

## 🔧 先理解当前的 Embedding 流程

你的项目中使用 Embedding 的地方：

1. **离线**：`QuizVectorStoreConfig` → `simpleVectorStore.add(docs)` → Spring AI 自动调用 DashScope Embedding API 向量化
2. **在线**：`quizVectorStore.similaritySearch(query)` → Spring AI 自动将 query 向量化 → 检索

你需要理解的是：**调用 `similaritySearch` 时，Spring AI 在内部会自动调用 Embedding 模型将 query 转成向量。** 你不需要手动调用 Embedding API。

所以 HyDE 的核心逻辑是：

```
用户问题 → LLM 生成假设答案 → 用假设答案文本调用 similaritySearch → 返回检索结果
```

注意：传给 `similaritySearch` 的是 **假设答案**，不是原始问题！


---


## 🏗️ 动手升级：第 1 步 — 设计 HyDE 检索类

你需要在 `rag` 包下新建一个类。这个类需要做以下几件事：

### 步骤分解

```
Step 1: 接收用户问题
Step 2: 构造一个 Prompt，让 LLM 生成"假设性答案"
Step 3: 调用 LLM（ChatModel），拿到假设答案文本
Step 4: 用假设答案文本调用 VectorStore.similaritySearch()
Step 5: 返回检索结果
```

### 引导问题

1. Step 2 的 Prompt 怎么写？下面给你一个提示，但请你用自己的话补全：

```
你是一个______。请根据以下问题，生成一段______的文字来回答。
不需要保证答案的正确性，只需要______。

问题：{用户问题}
```

> 关键点：Prompt 要让 LLM 生成一段 **详细、有信息量** 的文字，而不是简单的"是"或"否"。信息量越大，向量表示越精确。

2. Step 3 中，你怎么调用 LLM？回顾你的项目，`ChatModel` 是哪里注入的？怎么用它生成文本？（提示：查看 `QueryRewriter` 是怎么用 `ChatClient` 的）
3. Step 5 中，`similaritySearch` 的参数怎么设置？topK 和 similarityThreshold 应该和普通检索一样还是不同？

> **你的任务 1**：在 `rag` 包下新建 `HyDESearchService` 类，实现上述 5 个步骤。

---

## 🏗️ 动手升级：第 2 步 — 集成到 QueryRewriter

回顾你的 `QueryRewriter`：

```java
@Component
public class QueryRewriter {
    private final QueryTransformer queryTransformer;
    
    public String doQueryRewrite(String prompt) {
        Query query = new Query(prompt);
        Query transformedQuery = queryTransformer.transform(query);
        return transformedQuery.text();
    }
}
```

它是把"查询重写"作为一个独立的小工具。HyDE 也可以类似地集成进来，或者直接在 HyDE 检索类中完成。

### 引导问题

1. HyDE 中的 LLM 调用生成的是"答案文本"而不是"重写后的查询"。这两者有什么本质区别？
2. 你的 `QueryRewriter` 中用 `RewriteQueryTransformer` 包装了 LLM 调用。HyDE 中你打算也用一个 Transformer，还是直接用 `ChatClient`？

> **你的任务 2**：完善 `HyDESearchService`，使其可以作为 Spring Bean 注入到 `QuizApp` 中使用。

---

## 🏗️ 动手升级：第 3 步 — 集成到统一对话

修改 `QuizApp` 的 `buildContext` 方法或新增一个方法，支持使用 HyDE 检索。

### 引导问题

1. HyDE 检索比普通检索多了一次 LLM 调用（生成假设答案），这意味着什么？延迟会增加多少？
2. 什么情况下适合用 HyDE，什么情况下不适合？
3. 文档中提到" HyDE 对开放式问题效果显著 "，什么是"开放式问题"？
3. 你打算让用户选择检索策略，还是自动判断？

> **你的任务 3**：在 `QuizApp` 中集成 HyDE 检索，并添加一个简单的判断逻辑：如果问题属于"开放式问题"就用 HyDE，否则用普通检索。（"开放式问题"的定义你自己设计）

---

## ⚠️ 重要提醒：HyDE 的风险

文档中没有详细讨论，但你需要知道：

| 风险 | 说明 | 缓解方式 |
|------|------|---------|
| 假设答案可能错误 | LLM 生成的答案包含事实错误 | 只用它做检索，不展示给用户 |
| 生成偏置 | LLM 倾向生成某种类型的答案 | 在 Prompt 中引导多样性 |
| 延迟翻倍 | 多了一次 LLM 调用 | 只在必要时使用 |
| 成本增加 | 每次检索多消耗 tokens | 可以缓存假设答案 |

---

## ✅ 自我检验清单

- [ ] 能用自己的话解释"为什么用假答案检索比用真问题检索更准"
- [ ] 新建了 `HyDESearchService` 类，实现了 5 个步骤的完整流程
- [ ] Prompt 设计合理，能生成信息量丰富的假设答案
- [ ] 集成到了 `QuizApp` 中，可以切换检索策略
- [ ] 用同一个开放式问题，对比普通检索和 HyDE 检索的结果差异
- [ ] 理解了 HyDE 的适用场景和风险

---

## 💡 延伸思考

HyDE 论文是 2022 年的，知识库文档中还提到了更新的技术：EAR（Expand, Rerank, and Retrieve）。EAR 的核心发现是"贪婪解码往往选不到最佳查询扩展"。HyDE 生成的假设答案同样有这个问题——一次生成可能不是最优的。想想怎么改进？

---

> 📌 下一篇：[第 4 篇：RAGAS 评估体系](./04-ragas-evaluation.md) — 没有评估，优化就是盲人摸象
