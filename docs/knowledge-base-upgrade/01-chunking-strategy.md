# 第 1 篇：启用文档切分 — 知识库最重要的第一步

> 难度：⭐ ｜ 学习价值：⭐⭐⭐ ｜ 核心概念：Chunking、Token、信息粒度

---

## 📖 先读这个：为什么切分是"影响最大但最被忽视"的环节？

### 现实类比

想象你去图书馆找一本书。图书管理员告诉你：

- **不切分**（你现在的做法）：把整本书的内容贴在墙上，你问什么都在整面墙上找 → 找到的信息不精确，而且整面墙太长，塞不进 LLM 的"脑子"
- **切分后**：每章、每节分开贴标签 → 你问"Java 线程池原理"，管理员直接给你第 3 章第 2 节

### 核心矛盾

知识库文档指出切分存在一个经典的 **两难困境**：

```
切得太细 ──────────────── 切得太粗
语义碎片化              信息冗余
丢失上下文              引入噪声
检索精准但回答不完整    回答完整但检索不精准

目标：在粒度和完整性之间找到平衡点
```

### 你当前项目的问题

打开 `QuizVectorStoreConfig.java` 第 37 行：

```java
// 自主切分文档（可选，按需启用）
// List<Document> splitDocuments = myTokenTextSplitter.splitCustomized(documentList);
```

**这行代码被注释掉了！** 这意味着你加载的 Markdown 和 PDF 文档是 **整篇入库** 的。一篇 2 万字的 "知识库" 文档，被当作一个向量存入数据库。检索时，你要么召回整篇（噪声巨大），要么召回不到（语义稀释）。

---

## 🧠 先想一想（不要跳过！）

在动手之前，请用纸笔或大脑回答以下问题：

1. 你的 `document/` 目录下有 4 篇文档，最长的那篇有多少内容？检索时如果返回整篇文档，LLM 能处理吗？
2. 如果一个用户问"Java 的 synchronized 关键字底层原理"，你期望检索返回多大的文本块？是一整章？还是一个段落？还是 3-5 句话？
3-5句话
3. 如果切得太细，比如每句话一个块，"synchronized 是基于对象监视器实现的" 这句话脱离了上下文，LLM 能理解吗？

---

## 📚 基础补充：Token 是什么？

作为学了 4 个月 Java 的小白，你可能常听到 "token" 但不理解。简单说：

```
Token ≈ 一个"有意义的最小单位"

中文：通常 1 个汉字 ≈ 1-2 个 token
英文：通常 1 个单词 ≈ 1-2 个 token
代码：关键字、符号各算 token

举例：
"你好世界" → 约 4-6 个 token
"public static void main" → 4 个 token
```

**为什么切分用 token 而不是字符数？**
因为 LLM 按 token 计费和限制上下文窗口。DeepSeek-Chat 上下文窗口是 128K tokens，
但你不可能把 128K tokens 全用来做检索上下文——那太贵太慢了。通常检索注入的上下文控制在 2K-4K tokens 以内。

---

## 🔧 动手前的知识准备

打开你的 `MyTokenTextSplitter.java`，看看当前代码：

```java
public List<Document> splitCustomized(List<Document> documents) {
    TokenTextSplitter splitter = new TokenTextSplitter(200, 100, 10, 5000, true);
    return splitter.apply(documents);
}
```

这 5 个参数是什么含义？请去 Spring AI 源码或文档中找到答案。这里我给你第一个参数的解释作为示范：

| 参数位置 | 参数名                   | 当前值 | 含义                  |
|---------|-----------------------|--------|---------------------|
| 第 1 个 | `defaultChunkSize`    | 200 | 每个文本块的目标大小（tokens）  |
| 第 2 个 | chunkOverlap          | 100 | 相邻分块重叠token         |
| 第 3 个 | minChunkLengthToEmbed | 10 | 低于token的水片不生成向量入库   |
| 第 4 个 | maxNumTokens          | 5000 | 单次处理文档总token上限，超长截断 |
| 第 5 个 | stripWhitespace       | true | 自动清除分块首尾多余空白、换行     |

> **你的任务 1**：补全上面表格中剩余 4 个参数的含义。去 Spring AI 的 `TokenTextSplitter` 构造函数源码中找答案。

---

## 🏗️ 动手升级：第 1 步 — 启用切分

### 你要做的事情

在 `QuizVectorStoreConfig.java` 中，取消第 37 行的注释，让 ETL 流水线变成：

```
Load → Split → Enrich → Store
```

而不是现在的：

```
Load → Skip Split → Enrich → Store
```

### 引导问题（先想再做）

1. 取消注释后，`splitDocuments` 这个变量应该传给下面的哪一步？看看第 39 行 `myKeywordEnricher.enrichDocuments()` 接收的是什么参数？
2. 你想要用 `splitCustomized()` 还是 `splitDocuments()`？两个方法的参数有什么区别？

> **你的任务 2**：修改 `QuizVectorStoreConfig.java`，启用文档切分，让切分后的文档流入关键词增强和向量存储。

---

## 🏗️ 动手升级：第 2 步 — 选择合适的切分参数

文档中提到了多种切分策略：

| 策略 | 原理 | 适用场景 |
|------|------|---------|
| 固定长度切分 | 按固定 token 数切分 + 重叠 | 快速原型 |
| 递归切分 | 段落 → 句子 → 字符 逐级切分 | 通用文档 |
| 语义切分 | 相邻句子相似度低于阈值时切分 | 高质量要求 |
| Small-to-Big | 检索用小粒度，生成时扩展为大粒度 | 精准检索 |

文档给了一个核心建议：

> **"从简单开始：先用递归切分 + 10% 重叠建立 baseline"**

### 引导问题

1. 你当前的 `splitCustomized(200, 100, ...)` 是什么策略？200 tokens 大概是多少个中文字？（提示：中文 1 个字 ≈ 1-2 tokens）
2. 文档建议 chunk_overlap 为 10%-20%。你当前的 overlap 是 100 tokens，如果是 200 tokens 的 chunk，overlap 比例是多少？合理吗？
3. 你的知识库文档是中文技术文章，每个"知识点"大概多长？是 50 字能说清，还是需要 500 字？

> **你的任务 3**：根据你的知识库文档特点，调整 `splitCustomized()` 的参数。在 `MyTokenTextSplitter` 中新增一个 `splitForKnowledgeBase()` 方法，使用你认为合适的参数。

---

## 🏗️ 动手升级：第 3 步（可选挑战）— 理解递归切分

`TokenTextSplitter` 底层使用的就是递归切分策略。去 Spring AI 源码中找 `TokenTextSplitter` 的 `splitText` 方法，理解它的切分逻辑。

> **你的任务 4（挑战）**：在 `MyTokenTextSplitter` 中用注释写出递归切分的伪代码流程，验证你是否真正理解了。

---

## ✅ 自我检验清单

完成以下所有项才算通过：

- [ ] 能用自己的话解释为什么要切分文档（给完全不懂的人讲）
- [ ] 补全了 5 个参数的含义表格
- [ ] `QuizVectorStoreConfig` 中启用了文档切分
- [ ] 创建了 `splitForKnowledgeBase()` 方法并选择了合适的参数
- [ ] 运行项目，观察日志中切分前后的文档数量变化
- [ ] 用同一个问题分别测试切分前和切分后的检索结果，对比差异

---

## 💡 延伸思考

等完成第 4 篇（RAGAS 评估）后，你可以回过头来用 Context Precision 和 Context Recall 指标来 **量化** 不同切分参数的效果。这就是文档说的"评估驱动迭代"。

---

> 📌 下一篇：[第 2 篇：Multi-Query 查询扩展](./02-multi-query-expansion.md) — 一个问题，多个角度检索
