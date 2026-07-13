# 第 7 篇：Agent 工具设计与可观测性 — 从"能用"到"可靠"

> 难度：⭐⭐⭐ ｜ 学习价值：⭐⭐⭐⭐⭐ ｜ 核心概念：Tool 设计原则、错误处理模式、执行追踪、结构化日志

---

## 📖 先读这个：你的 Agent 为什么"时好时坏"？

你已经有了一个能跑通的 Agent（`YuManus` + `ToolCallAgent`），三个工具
（`WebSearchTool`、`TerminalOperationTool`、`TerminateTool`），RAG 评估体系（`RagasEvaluator`），PGVector 持久化。

但你有没有发现一个问题：**你改了一行 Prompt 或者换了一个 Tool 参数，Agent 的行为就变了——变得更好了还是更差了？你其实不知道。**

文档《落地实践》一句话点出了核心：

> Agent 的非确定性：同一 prompt 多次执行结果不同，"跑通一次"不代表"稳定能跑"。

这就像你刚学会骑车，能骑 100 米不出事 ≠ 能骑 10 公里不出事。

本教程基于大厂公众号的几篇核心技术文章（《MCP.md》的工具设计原则、《落地实践.md》的评估框架、《实现Ai agent框架.md》的 Agent Loop 设计）
，帮你的 Agent 从"偶尔能跑"升级到"稳定可靠"。

---

## 🧠 先想一想

1. 你的 `WebSearchTool.searchWeb()` 出错时返回 `"Error searching with Tavily: " + e.getMessage()`。
2. Agent 看到这个错误信息后，知道下一步该怎么做吗？还是只能"算了我不搜了"？
2. `TerminalOperationTool.executeTerminalCommand("dir C:\\")` — 如果 AI 被诱导执行了 `del /f /s /q C:\\important\\*`，
3. 你的 `cmd.exe /c` 会拦住吗？
3. `ToolCallAgent.think()` 抛异常时只打了一行 `log.error`。如果 Agent 执行了 20 步之后失败了，
4. 你能从日志里还原出"第几步、用了哪个工具、传了什么参数"吗？
4. 文档《MCP.md》说"Agent 工具是 Agent 的用户界面"。你现在的工具"界面"（名称 + 描述 + 返回值格式）对 Agent 友好吗？

---

## 📚 基础补充：Agent 工具设计的"南北极"

文档《MCP.md》和《怎么设计好一个AI Agent.md》都在反复讲同一个道理：

```
传统 API 设计：为人类开发者设计 → 假设用户会读文档、会调试、会从上下文推断
Agent 工具设计：为 LLM 设计       →
 LLM 只看到 name + description + 参数 schema
```

### 基础概念 1：LLM 眼中的工具就是一个"三元组"

```
工具 = (名称, 描述, 参数 Schema)

LLM 看不到你的代码实现，看不到注释，看不到文档链接。
它只能通过这三个元素来理解"这个工具能做什么"。
```

举个例子——你的 `TerminalOperationTool` 在 LLM 眼中是这样的：

```
名称: executeTerminalCommand
描述: "Execute a command in the terminal"
参数: command (String) — "Command to execute in the terminal"

LLM 的困惑：
- "我能执行什么命令？有没有限制？"
- "返回什么？执行成功/失败怎么判断？"
- "危险命令（删除文件、关机）能不能执行？"
```

对比如果你的描述是"执行安全的终端命令（dir/echo/type/python），危险命令会被拒绝"，LLM 的选择会更准确。

### 基础概念 2：Agent Loop = 读上下文 → 思考 → 行动 → 观察结果 → 再思考...

文档《实现Ai agent框架.md》把 Agent Loop 讲得很清楚：

```
[agent loop 开始]
    ↓
Agent 读取上下文 → 思考 → 决定调用工具
    ↓
执行工具 → 获得结果
    ↓
结果追加到上下文
    ↓
[循环继续或结束]
```

**核心洞察**：每次工具返回的结果，都会追加到上下文中，影响 LLM 下一步的决策。

如果工具返回 `"Error: NullPointerException"`，Agent 不知道发生了什么，只能"算了"。
如果工具返回 `"搜索失败，API 连接超时。建议：1) 等待 30 秒重试 2) 换用更窄的搜索词"`，Agent 就知道怎么应对。

这就是文档反复强调的：**错误信息不是"终点"，而是给 Agent 的"另一种输入"**。

### 基础概念 3：好的工具设计 = 减少 LLM 的认知负担

```
工具数量：5 个精心设计的工具 > 20 个随意堆砌的工具
参数数量：最少必填参数（Agent 每次填参数都是一次"认知消耗"）
返回内容：Agent 需要的核心信息（不返回 UUID、数据库 ID 等底层细节）
错误信息：告诉 Agent "哪里错了" + "为什么会错" + "怎么办"
```

---

## 🏗️ 动手升级：第 1 步 — 改造现有 Tool 的错误处理

文档《MCP.md》说：

> 对于 Agent 工具，错误不是"终点"，而是"输入"，是给 Agent 的另一种反馈，帮助它调整策略继续前进。

看你的 `WebSearchTool.searchWeb()`（第 68-70 行）：

```java
} catch (Exception e) {
    return "Error searching with Tavily: " + e.getMessage();
}
```

### 引导问题

1. 如果 API 连接超时（`SocketTimeoutException`），Agent 看到这个错误后能做什么？它能判断"应该重试"还是"换个搜索词"吗？
2. 如果是 API Key 错误（`401 Unauthorized`），重试有用吗？
3. 你能区分**可恢复的错误**（网络超时、限流）和**不可恢复的错误**（API Key 错误、配置错误）吗？
4. 文档说好的错误信息要回答三个问题：**出了什么问题？为什么会出问题？应该怎么修正？** 你的错误信息回答了哪几个？

> **你的任务 1**：改造 `WebSearchTool.searchWeb()` 的异常处理，让 Agent 能看到"有操作性的错误信息"。
>
> 💡 提示：
> - 先了解 `Exception` 有哪些子类：`SocketTimeoutException`（超时）、`IOException`（IO 错误）、`RuntimeException`（运行时异常）
> - 用多个 `catch` 块分别处理不同类型的异常
> - 每种异常返回不同的错误描述，告诉 Agent 下一步可以怎么做
> - 不可恢复的错误（如 API Key 错误）要明确说"需要人工介入"

---

## 🏗️ 动手升级：第 2 步 — 完善 TerminalOperationTool 的安全性

你的 `TerminalOperationTool` 已经有一个注释框架（第 36-42 行），要求实现命令白名单机制。

### 引导问题

1. 为什么用白名单（只允许特定命令）而不是黑名单（禁止特定命令）？
   - 💡 提示：你能列举出所有危险命令吗？`del /f`、`format`、`shutdown`、`reg delete`、`net user`... 这个列表写得完吗？
2. 怎么从 `"dir C:\\Users"` 中提取出命令名 `"dir"`？
   - 💡 提示：`String.split(" ")` 会返回什么数组？
3. 用什么数据结构存白名单？为什么用 `Set<String>` 而不是 `List<String>`？
   - 💡 提示：查一下 `HashSet.contains()` 和 `ArrayList.contains()` 的时间复杂度有什么区别
4. 如果命令在白名单内，但参数很危险（比如 `python -c "import os; os.system('del /f C:\\*')"`），怎么办？
5. 当命令不在白名单时，返回什么信息给 Agent？文档强调错误要有"可操作性"。

> **你的任务 2**：实现 `TerminalOperationTool.executeTerminalCommand()` 的白名单校验（在第 42 行"你的代码写在这里 ↓"的位置）。
>
> 💡 建议步骤：
> - 先建一个 `private static final Set<String> ALLOWED_COMMANDS =
> - Set.of("dir", "echo", "type", "findstr", "mkdir", "python");`
> - 用 `command.trim().split("\\s+")` 取第一个元素作为命令名
> - 用 `ALLOWED_COMMANDS.contains(命令名.toLowerCase())` 判断
> - 不在白名单 → return 一段"Agent 能看懂"的错误信息（包含：什么命令被拒绝了、允许哪些命令、如果确实需要执行该怎么办）

---

## 🏗️ 动手升级：第 3 步 — 为 Agent 添加简单的执行追踪

文档《落地实践.md》把 Trace（执行轨迹）列为测评的前提条件：

> Trace（执行轨迹）是 Agent 执行过程中产生的结构化日志，
> 记录了每一步的工具调用、参数、返回值和思考过程，类似于程序调试中的"调用栈记录"。

你现在的 `ToolCallAgent.think()` 和 `act()` 里有 `log.info` 打印，但不是结构化的。
当 Agent 执行失败时，
很难回溯"第几步、调了什么工具、传了什么参数、返回了什么"。

### 引导问题

1. 如果你要把 Agent 每一步的执行信息存下来（步骤编号、时间戳、做了什么、结果是什么），
你会用什么 Java 类来表示一条执行记录？
2. 一个 Agent 执行一次任务，会有多条执行记录。用什么数据结构存？`List<???>` 吗？
3. 这个数据结构应该放在哪里？Agent 内部（`BaseAgent` 里）还是外部？
4. 文档说 Trace 的格式应该"结构化、可解析"。JSON 格式怎么样？
你用过 `JSONUtil.toJsonStr()`（Hutool 的工具）吗？
5. Agent 执行完后，怎么把这些记录输出？打日志？存文件？还是都存在 `List` 里最后一起返回？

> **你的任务 3**：创建一个 `AgentTrace` 数据模型 + `TraceStep` 步骤模型，在 `BaseAgent.run()` 
> 中记录每一步的执行轨迹。
>
> 💡 你需要做的：
> - ① 新建 `agent/trace/TraceStep.java` 
> — 表示单步执行记录（stepNumber, whatHappened, timestamp, resultSummary）
> - ② 新建 `agent/trace/AgentTrace.java` —
> 表示一次完整执行的所有步骤（List<TraceStep> + agentName + startTime + endTime）
> - ③ 在 `BaseAgent.run()` 的 for 循环中，每执行一步就追加一条 TraceStep
> - ④ 任务结束后，用 `JSONUtil.toJsonPrettyStr(agentTrace)` 把整条 Trace 打印到日志
>
> 🔴 这是核心任务，类设计交给你自己完成。想想：
> - `TraceStep` 需要哪些字段？除了步骤号、描述、时间戳、结果摘要，还需要什么？
> - 字段用什么类型？`int` 还是 `Integer`？`String` 还是 `LocalDateTime`？
> - 要不要用 Lombok 的 `@Builder` 来方便构建对象？

---

## 🏗️ 动手升级：第 4 步 — 用 Trace 建立简单的回归验证

有了 Trace，你就可以做回归验证了——改了一行 Prompt 之后，跑同一个问题，对比改之前和改之后的 Trace，看 Agent 的行为是否退化。

### 引导问题

1. 如果你手动跑一次 Agent，把 Trace 保存为 JSON 文件，这叫做"基线（Baseline）"。
2. 下次再跑同一个问题，把新的 Trace 和基线对比，你觉得哪些维度可以自动对比？
   - 💡 提示：工具调用次数是否变多了？是否调用了不该调用的工具？最终状态是否一致？
2. 文档《落地实践.md》用 `pass^k`（k 次试验中每次都成功的概率）来衡量稳定性。
同一个问题跑 5 次，5 次都成功才算稳定。你的 Agent 跑同一个问题 5 次，行为一致吗？

> **你的任务 4**：在 `BaseAgent.run()` 的最后，把 `AgentTrace`
>保存到文件（JSON 格式，放在 `logs/traces/` 目录下），文件名用 `{agentName}_{时间戳}.json`。这为后续的回归对比打基础。
>
> 💡 这是偏样板的任务，但你也需要思考：
> - 文件放在哪里？项目根目录的 `logs/traces/` 还是 `target/` 下？
> - 文件名怎么保证不重复？
> - 如果目录不存在怎么办？

---

## 📊 实践建议：建立改进前后的量化对比

在做上面的任何改进之前，先记下当前的基线数据：

| 指标 | 改进前 | 你的记录 |
|------|--------|---------|
| WebSearch 错误时 Agent 能否自行恢复 | 不知道 | ? |
| TerminalOperation 是否有安全防护 | 无白名单 | ? |
| 执行过程是否可追溯 | 只有零散 log | ? |
| 同一个问题跑 3 次结果是否一致 | 未验证 | ? |

每完成一个改进，重新测试，记录数据。这样你才能说"我的改进是有效的"，不是"我感觉好了一点"。

---

## ✅ 自我检验清单

- [ ] 能用自己的话解释"为什么 Agent 工具设计 ≠ 传统 API 设计"
- [ ] 能说出好的工具错误信息需要包含哪三个要素
- [ ] 改造了 `WebSearchTool` 的错误处理，Agent 能看到"可操作的错误信息"
- [ ] 实现了 `TerminalOperationTool` 的命令白名单
- [ ] 创建了 `AgentTrace` + `TraceStep` 数据模型
- [ ] 在 `BaseAgent.run()` 中记录了结构化执行轨迹
- [ ] Agent 执行完后 Trace 被保存为 JSON 文件
- [ ] 跑同一个问题 3 次，对比 3 次 Trace 看行为是否一致

---

## 💡 延伸思考

1. 文档《关于agent的初步探索.md》中提到了三种 Agent 行为范式：ReAct、Plan-and-Execute、ReWOO。你现在的 `ToolCallAgent` 用的是哪种？如果换成 Plan-and-Execute（先规划所有步骤，再逐一执行），你的 Agent 能更稳定吗？

2. 文档《agent工具化.md》用了一个很有意思的做法：把 MCP server 暴露为 CLI 工具，Agent 通过"写脚本→执行脚本→拿到结果"来操作工具，而不是一次一调。这种模式的好处是什么？（提示：上下文占用、执行可靠性）

3. 文档《怎么设计好一个AI Agent.md》反复强调"评估先行"、"无评估不迭代"。你现在有了 RAGAS 评估检索质量，有了 Trace 追踪执行过程。这两者互补：RAGAS 评"结果对不对"，Trace 评"过程对不对"。你能把这两者组合起来，对一个完整的问题（检索→生成）做端到端评估吗？

---

> 📌 上一篇：[第 6 篇：PGVector 向量存储持久化](./06-pgvector-persistence.md) — 从内存到数据库，数据不丢失
