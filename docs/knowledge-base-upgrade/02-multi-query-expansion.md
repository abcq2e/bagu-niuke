# 第 2 篇：Multi-Query 查询扩展 — 一个问题，多种问法

> 难度：⭐⭐ ｜ 学习价值：⭐⭐⭐⭐ ｜ 核心概念：查询扩展、结果合并、去重

---

## 📖 先读这个：为什么一个问题需要多种问法？

### 现实类比

你去问三个不同的专家同一个问题：
- 你问专家 A："Java 线程池怎么调优？"
- 你问专家 B："ThreadPoolExecutor 参数怎么配置最合理？"
- 你问专家 C："核心线程数和最大线程数应该怎么设？"

三个专家给的答案角度不同，但合在一起就是全面答案。

**向量检索的局限性**：当你把一个问题转成向量去检索时，不同的表达方式可能召回完全不同的文档。你现在的项目只做了"查询重写"（把问题改写成更规范的形式）
，但只有一个查询变体。

### 文档中的指引

知识库文档在"查询增强"部分提到了 Multi-Query：

> 生成多个查询变体，分别检索后合并去重

---

## 🧠 先想一想

1. 用户输入"线程池满了怎么办"，你觉得有哪些不同的问法可以表达同样的意图？
2. 如果用 3 种不同问法分别检索，返回的 3 组结果中可能有重复的文档，你怎么去重？
3. 去重后你怎么排序？是把 3 组结果简单拼在一起，还是按相似度分数排序？

---

## 📚 基础补充：List 操作是你必须掌握的基本功

Multi-Query 的核心代码逻辑是 **List 的合并、去重、排序**。
作为学了 4 个月 Java 的人，这些操作你应该能熟练写出来。如果还不能，这篇教程正好帮你练习。

### Java 中 List 去重的几种方式

```java
// 方式 1：用 Set（利用 Set 不允许重复元素的特性）
List<String> list = Arrays.asList("A", "B", "A", "C");
Set<String> set = new LinkedHashSet<>(list);  // LinkedHashSet 保留插入顺序
List<String> deduplicated = new ArrayList<>(set);  // 结果：[A, B, C]

// 方式 2：用 Stream API
List<String> deduplicated = list.stream()
        .distinct()
        .collect(Collectors.toList());
```

> **思考**：Document 对象怎么判断"重复"？是内容完全一样才算重复，还是 ID 一样？去重时应该按什么字段？

---

## 🔧 你的项目已经有了 Demo 代码！

打开 `MultiQueryExpanderDemo.java`（位于 `demo/rag/` 包下），
你会发现 Spring AI 已经提供了 `MultiQueryExpander` 组件，可以直接生成多个查询变体。

但这段代码有两个问题：

1. **它只是 Demo**，没有集成到 QuizApp 的实际对话流程中
2. **它接收参数但没用**：`expand(String query)` 方法接收了参数 `query`，
3. 但第 27 行硬编码了 `new Query("谁是程序员鱼皮啊？")`

> **你的任务 1（热身）**：修复 `MultiQueryExpanderDemo.expand()` 
> 方法中的 bug——让它使用传入的参数 `query` 而不是硬编码的字符串。

---

## 🏗️ 动手升级：第 1 步 — 设计 MultiQueryService

你需要在 `rag` 包下新建一个类，负责：
1. 接收一个查询字符串
2. 生成 N 个查询变体
3. 用每个变体分别检索向量库
4. 合并结果并去重
5. 按相似度排序，返回 Top-K

### 引导问题（先设计再动手）

1. 这个类应该叫什么名字？用什么注解标记（`@Component`？`@Service`？）？
2. 它需要依赖哪些已有的 Bean？看看你的 `QuizApp` 中注入了什么。
3. 检索结果去重时，用什么作为"重复"的判断标准？提示：Document 有 `getId()` 方法。
4. 多个变体检索后，同一个 Document 可能被不同 query 以不同分数检索到。
合并时你觉得该保留哪个分数？最高分？平均分？还是第一个？

> **你的任务 2**：在 `rag` 包下新建类，实现上述逻辑。核心方法签名参考：
> ```java
> public List<Document> multiQuerySearch(String userQuery, 
> int numberOfQueries, int topK, double similarityThreshold)
> ```

---

## 🏗️ 动手升级：第 2 步 — 集成到统一对话流程

现在打开 `QuizApp.java`，看看 `doUnifiedChat` 和 `buildContext` 方法。

当前流程：
```
用户问题 → QueryRewriter 重写 → VectorStore 单次检索 → 构建上下文 → LLM
```

升级后流程：
```
用户问题 → MultiQuery 扩展 → 多次检索 → 合并去重 → 构建上下文 → LLM
```

### 引导问题

1. 在 `QuizApp` 中你需要注入什么新的依赖？
2. `buildContext` 方法中，当前是调用 `quizVectorStore.similaritySearch()` 做单次检索。你怎么改成调用你新写的 `multiQuerySearch()`？
3. 查询重写和 Multi-Query 是什么关系？是取代还是组合？文档中是怎么说的？

> **你的任务 3**：修改 `QuizApp.java` 的 `buildContext` 方法，集成 Multi-Query 检索。

---

## 🏗️ 动手升级：第 3 步（挑战）— 性能优化

Multi-Query 最大的代价是 **调用 N 次检索 = N 次 Embedding API 调用**。Spring AI 的 `MultiQueryExpander` 
内部会调用 LLM 生成变体，这也是一次 API 调用。

### 引导问题

1. N 设为多少比较合适？文档建议 `numberOfQueries=3`，为什么不是 10？
2. 3 次检索能不能并行执行？Spring 中怎么实现异步？提示：`@Async` + `CompletableFuture`
3. 如果用户问的是简单问题（比如"什么是 AQS"），还需要 Multi-Query 吗？怎么判断？

> **你的任务 4（挑战）**：使用 `@Async` 让多个查询变体的检索并行执行，减少总耗时。

---

## ✅ 自我检验清单

- [ ] 能用白话解释 Multi-Query 的原理和适用场景
- [ ] 修复了 `MultiQueryExpanderDemo` 中的硬编码 bug
- [ ] 新建了 Multi-Query 检索类，实现了合并去重逻辑
- [ ] 修改了 `QuizApp.buildContext` 集成 Multi-Query
- [ ] 用不同问题测试，对比单次检索和 Multi-Query 的检索结果差异
- [ ] （挑战）实现了并行检索优化

---

## 💡 延伸思考

Multi-Query 和接下来要学的 HyDE 都属于"查询增强"技术。它们的本质区别是什么？一个是从"问题空间"扩展，一个是从"答案空间"生成。理解了这一点，你就理解了 RAG 检索增强的核心思想。

---

> 📌 下一篇：[第 3 篇：HyDE 假设性文档嵌入](./03-hyde-implementation.md) — 先生成答案，再拿答案去检索
