# 第 9 篇：Agent 架构升级 — 从 ReAct 到 Plan-and-Execute

> 难度：⭐⭐⭐⭐ ｜ 学习价值：⭐⭐⭐⭐⭐ ｜ 核心概念：Plan-and-Execute、任务分解、结构化工作流、Multi-Agent 协作

---

## 📖 先读这个：你的 Agent 在"盲飞"

你现在的 `ReActAgent` 用的是 ReAct 范式：

```
思考（Think） → 行动（Act） → 观察 → 思考 → 行动 → 观察 → ...
```

每一步都是"看一步走一步"。这种方式面对简单任务（"帮我搜一下XXX"）没问题，但面对复杂任务（"帮我分析这个项目，给出优化建议，并生成报告"），问题就出来了。

文档《关于agent的初步探索.md》的作者亲身体验：

> "全自主决策"Agent 的阵亡：Agent 在面对一个有多个 docker-compose-xxx.yml 文件的仓库时，会陷入长久的思考："我需要阅读 xxx 文件来明确部署方案""我没有找到 xxx 文件"，这种思考让 AI 陷入了循环困境。

> 阵亡反思：在一个多步骤、长链条的任务中，任何一步的 Thought 出现偏差，都可能导致整个任务链走向一个无法挽回的死胡同。

**核心问题**：ReAct 模式缺少全局规划。Agent 在每一步都在"重新思考接下来做什么"，而不是一开始就看清全局。

---

## 🧠 先想一想

1. 如果你让人去做一件复杂的事（比如"帮我搬家"），你会怎么说？是说"你先去看看卧室"、"你再看看客厅"——还是先说"我们分三步：①打包 ②搬家 ③收拾"？
2. 你现在的 `ToolCallAgent` 面对"分析这个项目并给出优化建议"这种任务时，是怎么执行的？是先规划还是直接开始调用工具？
3. 文档《实现Ai agent框架.md》说 Agent 框架核心 = Agent Loop + Context Engineering。你的 Agent Loop 是 `step()` 方法。如果要把"先规划后执行"的思想加进去，`step()` 需要怎么改？

---

## 📚 基础补充：Plan-and-Execute 范式

### 范式的演进

```
ReAct（你现在用的）:
  思考 → 行动 → 观察 → 思考 → 行动 → 观察 → ...
  优点：灵活，能根据反馈动态调整
  缺点：缺乏全局规划，容易在复杂任务中迷失

Plan-and-Execute:
  规划阶段：一次性生成完整的多步计划
  执行阶段：按计划逐步执行
  重规划阶段：执行失败时调整计划
  优点：全局视角，减少每步的决策负担
  缺点：需要 LLM 有较强的规划能力
```

文档《关于agent的初步探索.md》把 Plan-and-Execute 总结为：

> 先制定多步计划，再逐步执行，属于结构化工作流程。比较适合复杂且任务依赖关系明确的长期任务。

### 文档作者的实践经验

文档作者在"全自主决策"Agent 失败后，转而使用"结构化工作流"并取得了成功：

> 我放弃了让 AI 自由决策的幻想，转而设计了一套固定的、结构化的工作流。在这个范式里，人类负责定义"骨架"（Workflow），AI 负责填充"血肉"（Analysis & Generation）。

他还引入了一个很巧妙的概念——**"部署蓝图"**（中间语言）：

> 让 AI 先把项目的 docker-compose 内容翻译成一份详尽的、结构化的"部署蓝图" JSON。这种解耦，对于调试和迭代至关重要。

**对你的启发**：你可以让 Agent 先输出一个"任务计划 JSON"，再由执行器按计划执行。计划和执行分离后，每一步都可观测、可调试。

---

## 🏗️ 动手升级：你真的理解你现在的架构吗？

在动手改代码之前，先做一个思维练习。

### 类继承关系梳理

```
BaseAgent（抽象类）
    ↑
ReActAgent（抽象类 — 定义了 think() 和 act() 抽象方法）
    ↑
ToolCallAgent（具体类 — 实现了 think() 和 act()）
    ↑
YuManus（具体类 — 配置了 Prompt 和工具）
```

### 引导问题

1. `BaseAgent.run()` 的 for 循环调用了 `step()`，`step()` 在 `ReActAgent` 中实现，调用了 `think()` 和 `act()`。如果你要改成"先规划后执行"，需要改动哪个层级？
   - 只改 `YuManus`（最上层）？
   - 改 `ReActAgent` 或 `ToolCallAgent`（中间层）？
   - 改 `BaseAgent.run()`（底层）？
2. "规划"是一个新的步骤类型。它调 LLM 但不调工具。它应该放在 `think()` 里？还是新加一个 `plan()` 方法？
3. 规划的结果（比如一个 JSON 格式的任务列表）存在哪里？作为 Agent 的一个新字段？

> 💡 给你的思考框架：**在继承体系中选择正确的改动层级，是面向对象设计的核心能力。**
> 原则是——改动应该发生在"最具体的受影响层级"。

---

## 🏗️ 动手升级：设计任务计划的数据模型

在写执行逻辑之前，你需要先定义"计划"长什么样。

文档作者把计划定义为 "部署蓝图" JSON。你的 Agent 的计划可以更简单：

```json
{
  "goal": "分析项目并给出优化建议",
  "steps": [
    {
      "stepNumber": 1,
      "description": "搜索项目的技术栈信息",
      "toolToUse": "webSearch",
      "expectedOutput": "了解项目使用的主要技术"
    },
    {
      "stepNumber": 2,
      "description": "读取项目的构建文件",
      "toolToUse": "terminal",
      "expectedOutput": "获取 pom.xml 或 build.gradle 的内容"
    }
  ]
}
```

### 引导问题

1. 这个 JSON 结构在 Java 中用什么类表示？需要几个类？
2. `steps` 字段用什么类型？`List<Step>`？
3. 每个 step 包含 `stepNumber`、`description`、`toolToUse`、
`expectedOutput`——这些字段分别用什么 Java 类型？`int`？`String`？
4. 如果计划执行到一半失败了，你需要标记"哪些步骤已完成、哪些未完成"。怎么在数据模型中表达？加一个 `status` 字段吗？
5. 这个 JSON 是 LLM 生成的——LLM 可能生成格式不正确的 JSON。你怎么处理这种情况？

> **你的任务 1**：创建任务计划的数据模型类。
>
> 🔴 在 `agent/plan/` 包下创建：
> - `TaskPlan.java` — 整体计划（goal + List<TaskStep> + 时间戳 + 状态）
> - `TaskStep.java` — 单步计划（stepNumber + description + expectedOutput + status）
>
> 💡 用 Lombok（`@Data`、`@Builder`），用 `enum` 表示步骤状态（PENDING / IN_PROGRESS / COMPLETED / FAILED）。

---

## 🏗️ 动手升级：实现 Planner — 让 LLM 生成计划

有了数据模型，下一步是让 LLM 生成计划。这本质上是 Prompt 工程的延续。

### 引导问题

1. 你要写一个 Prompt，让 LLM 输出一个 JSON 格式的任务计划。Prompt 需要包含什么？角色设定？输出格式？示例？
2. 文档《怎么设计好一个AI Agent.md》强调了 "Few-Shot 示例" 的重要性。你要不要给 LLM 一个示例计划？
3. LLM 返回的 JSON 可能不合法（比如多了一个逗号）。你怎么解析？直接 `JSONUtil.parseObj()` 吗？如果解析失败怎么办？
4. 计划生成后，Agent 需要把计划"记住"以便后续执行。存在哪里？Agent 的一个字段？还是消息上下文中？

> **你的任务 2（核心）**：在 `ToolCallAgent` 中添加"先规划"的能力。
>
> 🔴 你需要做：
> - ① 添加 `TaskPlan currentPlan` 字段
> - ② 写一个 `generatePlan(String userGoal)` 方法：调 LLM，让 LLM 返回 JSON 格式的任务计划
> - ③ 解析 LLM 返回的 JSON 为 `TaskPlan` 对象
> - ④ 如果解析失败，降级为传统的 ReAct 模式（边想边做）
>
> 💡 引导提示：
> - LLM 调用模式参考你已有的 `extractClaims()` 方法（在 `RagasEvaluator` 中）
> - Prompt 设计参考《怎么设计好一个AI Agent.md》的"输出格式化"示例
> - JSON 解析失败的容错：先用 `JSONUtil.parseObj()` 试试，不行就降级

---

## 🏗️ 动手升级：实现 Executor — 按计划逐步执行

有了计划，执行就简单了——遍历 `steps` 列表，逐个执行。

但有一个关键设计决策：**执行器是逐步骤调 LLM，还是把计划交给 LLM 让它自己执行？**

### 两种方案对比

```
方案 A（工程控制）：
  遍历 steps → 每个 step 调一次 LLM → LLM 执行该 step → 记录结果

方案 B（LLM 自主）：
  把完整计划注入消息上下文 → LLM 自己按计划逐步执行
```

| 对比维度 | 方案 A（工程控制） | 方案 B（LLM 自主） |
|---------|------------------|------------------|
| 可靠性 | 高（工程控制节奏） | 低（LLM 可能偏离计划） |
| 灵活性 | 低（无法动态调整） | 高（LLM 可以随机应变） |
| 成本 | 每步一次 LLM 调用 | 不确定 |
| 实现复杂度 | 简单 | 中等 |

文档《关于agent的初步探索.md》的作者选择了方案 A：

> 结构化工作流：人类负责定义"骨架"（Workflow），AI 负责填充"血肉"（Analysis & Generation）。

### 引导问题

1. 你选择方案 A 还是方案 B？为什么？
2. 在遍历 steps 的过程中，如果第 3 步失败了，第 4 步还继续执行吗？还是整个计划作废？
3. 你怎么把当前正在执行的步骤信息（"正在执行第 2/5 步：搜索技术栈信息..."）传达给用户？

> **你的任务 3（进阶）**：在 `ReActAgent` 或 `ToolCallAgent` 中实现 Plan-and-Execute 模式。
>
> 🔴 🔴 这是整篇教程最大的挑战。你要决定：
> - 要不要新建一个 `PlanAndExecuteAgent` 类？（继承 `ReActAgent`？
> - 还是直接在 `ToolCallAgent` 中加一个判断：简单任务用 ReAct，复杂任务先规划后执行？
> - `step()` 方法要重写吗？还是改 `run()` 的逻辑？
>
> 没有标准答案。选一种方案，写出来，跑起来，看效果。

---

## 📊 实践建议：记录改进前后的差异

完成 Plan-and-Execute 后，用一个复杂任务测试两轮：

```
任务：分析 /src/main/java/com/yupi/yuaiagent 目录下所有代码，
      总结项目的架构设计，给出 3 条改进建议。

ReAct 模式（改进前）：
  - 执行了多少步？
  - 有没有重复调用同一个工具？
  - 结果有逻辑漏洞吗？

Plan-and-Execute 模式（改进后）：
  - 计划质量如何？
  - 执行效率和结果质量有无提升？
  - Token 消耗多了还是少了？
```

---

## ✅ 自我检验清单

- [ ] 能用自己的话解释 ReAct 和 Plan-and-Execute 的区别和适用场景
- [ ] 创建了 `TaskPlan` + `TaskStep` 数据模型
- [ ] 实现了 `generatePlan()` 方法（LLM 生成 JSON 计划）
- [ ] 处理了 JSON 解析失败的容错
- [ ] 实现了按计划逐步执行（方案 A 或方案 B 任选）
- [ ] 用复杂任务测试，Plan-and-Execute 模式下步骤更清晰、结果更稳定

---

## 💡 延伸思考

1. 文档《怎么设计好一个AI Agent.md》提到 Multi-Agent 模式：一个 Orchestrator（总指挥）负责规划，多个专家 Agent 负责执行。你现在的 Plan-and-Execute 是"单 Agent 内部的分阶段"。如果以后要升级到 Multi-Agent，你的 `TaskPlan` 数据结构需要怎么改？

2. 文档作者提到的"部署蓝图"有一个关键优势：**它是可调试的中间状态**。如果你的 Agent 执行出错了，你只需要检查"是计划错了还是执行错了"。你的 `TaskPlan` 能用 JSON 格式存到文件吗？这跟你第 7 篇做的 AgentTrace 怎么联动？

3. 文档《agent工具化.md》强调：**Agent 应该只做工程做不了的事，工程应该做它擅长的事**。Plan-and-Execute 中，"生成计划"是 LLM 做的（因为需要语义理解），"按计划执行"是工程代码做的（因为需要可靠性）。这个"各取所长"的思想，你在其他地方能应用吗？

---

> 📌 上一篇：[第 8 篇：结构化 Prompt 工程与自愈循环](./08-prompt-engineering-self-healing.md)
> 📌 下一篇：[第 10 篇：Agent 端到端评估体系](./10-agent-evaluation-system.md) — 从"感觉变好了"到"数据证明变好了"
