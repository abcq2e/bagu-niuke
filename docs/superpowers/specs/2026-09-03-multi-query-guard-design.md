# MultiQuerySearchService 多查询扩展守卫设计

日期：2026-09-03

## 背景

`MultiQuerySearchService.multiQuerySearch()` 目前对**任何**查询都无条件执行多查询扩展：生成 N 个变体 → N 次向量检索 → 合并去重。调用方 `RagSearchTool` 硬编码 `numberOfQueries=3`，因此只要 Agent 调用一次知识库检索，就固定触发 3 次 LLM 变体生成 + 3 次向量查询。

问题在于：对话指令（「继续」「换一个」「下一题」）或过短/过长内容也会走这套变体逻辑，造成无效的 LLM 调用和向量查询浪费。同时，面试主链路 `QuizApp.java:448` 已经主动放弃多查询扩展（注释明确「性价比低」，直接单次检索），但这条旁支仍保留无条件多查询，存在行为不一致。

## 目标

为多查询扩展增加**严格且准确**的确定性守卫：只有当查询确实值得多角度检索时，才生成变体；否则退化为单次向量检索。

## 方案（已确定）

- 判断方式：**纯确定性规则**（零 LLM 成本、可单元测试、行为可预测）。
- 降级行为：**退化为单次检索**（直接用原 query 走 `similaritySearch`，与主链路一致，保证始终有召回）。

## 改动位置

`src/main/java/com/qian/qianaiagent/rag/retrieval/MultiQuerySearchService.java`

- 守卫逻辑放在 `multiQuerySearch()` 方法**开头**。判断不通过则调用单次检索降级，不生成变体。
- 调用方 `RagSearchTool` 无需任何改动，自动受益。

## 守卫规则 `shouldUseMultiQuery(String)`

满足以下任一条件返回 `false`（退化为单次检索），按顺序短路：

1. query 为 `null` 或空白
2. `trim()` 后**精确命中**对话指令黑名单（语义层：明确是对话指令而非技术提问）
3. `trim()` 后长度 `< minQueryLength`（默认 15）或 `> maxQueryLength`（默认 100）（形式层：过短=指令/关键词，过长=回答/粘贴）
   - 复用配置 key `rag.query-rewrite.min-length` / `rag.query-rewrite.max-length`，与 `QueryRewriter` 同一组语义，避免配置蔓延。

### 指令词黑名单（精确匹配）

```
继续、换一个、换一题、换一道、下一题、下一道、下一个、跳过、
好的、嗯、嗯嗯、好、可以、行、对、是、不知道、不会、再来、再问、
谢谢、你好、在吗、退出、结束、pass、ok、okay、yes、no
```

**关键决定**：黑名单用精确匹配而非包含匹配。因为「继续」「换一个」等词会合法地出现在技术问题里（如「请继续讲解线程池原理」），包含匹配会误伤，违背「准确」要求。

**边界说明**：黑名单检查放在长度守卫之前——语义上先识别「这是对话指令」，长度守卫再兜底「过短/过长」的形式边界。两者职责不同：黑名单负责「准确」（语义识别），长度守卫负责「严格」（形式边界），叠加保证既不漏挡也不误伤。

## 降级逻辑 `singleQuerySearch(query, topK, threshold)`

- 直接用 `quizVectorStore.similaritySearch(SearchRequest.query(query).topK(topK).similarityThreshold(threshold))`。
- 与 `QuizApp.java:448` 的单次检索行为完全一致。
- 抽为私有方法，供降级分支复用。

## 测试

新增 `src/test/java/com/qian/qianaiagent/rag/retrieval/MultiQuerySearchServiceTest.java`（JUnit 5 + Mockito，纯单元测试）：

1. `shouldUseMultiQuery` 纯函数各分支：
   - null / 空白 → false
   - 过短（「继续」）→ false
   - 过长（>100 字）→ false
   - 精确命中黑名单 → false
   - 正常技术问题（「线程池有哪些核心参数」）→ true
2. 降级行为：mock `VectorStore` + `QueryRewriter`，验证「不值得多查询」的查询只调用一次 `similaritySearch`，且不调用 `doMultiQueryExpand`。

## 影响范围

- 仅 `MultiQuerySearchService` 一个生产类 + 一个新增测试类。
- 不影响 `RagSearchTool`、`QuizApp` 及其他调用方。
- 配置项复用现有 key，无新增配置。
