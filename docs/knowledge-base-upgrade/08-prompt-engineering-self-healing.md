# 第 8 篇：结构化 Prompt 工程与 Agent 自愈循环 — 给 Agent 装上"大脑"和"免疫系统"

> 难度：⭐⭐⭐ ｜ 学习价值：⭐⭐⭐⭐⭐ ｜ 核心概念：结构化 Prompt、自我反思、错误恢复、上下文工程

---

## 📖 先读这个：你的 Prompt 在"裸奔"

打开 `YuManus.java`，看看你的 System Prompt：

```java
"You are YuManus, an all-capable AI assistant, aimed at solving any task presented by the user. " +
"You have various tools at your disposal that you can call upon to efficiently complete complex requests."
```

文档《怎么设计好一个AI Agent.md》（大厂资损防控团队）把 Prompt 工程定位为"Agent 的底层协议"：

> 提示词工程是 Agent 的"底层协议"。它不是自然语言对话，而是通过结构化的文本指令，将非确定性的 LLM 输出约束为确定性的软件系统接口。

你的 System Prompt 只有一句话，没有告诉 Agent：
- **角色**：你是谁？在什么领域中工作？
- **规则**：什么能做、什么不能做？
- **输出格式**：回答应该长什么样？
- **错误应对**：工具调用失败怎么办？

文档《关于agent的初步探索.md》的作者踩过的坑，你现在也在踩：

> 我最初也曾低估了 Prompt 工程的复杂性，认为它不过是"提需求"的简单环节。
> 但当一个初步可行的框架和流水线搭建起来后，所有后续的质量优化、效果对齐和稳定性问题，几乎都聚焦到了与 Prompt 的持续"搏斗"上。

这篇文章还指出：**Agent 在面对一次工具调用失败后，
往往会陷入无限重试循环**——因为它的 Prompt 没有告诉它"什么时候该放弃、什么时候该换方案"。

---

## 🧠 先想一想

1. 你现在的 `SystemPrompt` 和 `NextStepPrompt` 是两段扁平文本。如果 Agent 行为出问题，你怎么快速定位是哪句话引导错了？
2. `ToolCallAgent.think()` 的 catch 块（第 113-127 行）捕获所有异常后只做一件事：`log.error` + 返回 `false`。
3. Agent 知道"工具为什么失败"吗？它会重试还是直接放弃？
3. 如果你的 Agent 连续 3 次调用了同一个工具，每次参数都一样，每次结果都是失败——它什么时候会意识到"这条路走不通，换条路"？
4. 文档说 Prompt 的模块化结构（角色/工具/规则/输出格式/示例）能显著提高稳定性。你现在的 Prompt 有哪几个模块？

---

## 📚 基础补充：Prompt 工程的三个层次

文档《怎么设计好一个AI Agent.md》把 Prompt 工程拆成了几个核心技巧：

### 层次 1：角色设定（Role Prompting）

不是"你是一个助手"，而是"你是一个具有 X 领域知识的 Y 角色，负责 Z 任务"。

```
❌ 扁平： "You are a helpful assistant."
✅ 结构化：
   【角色】你是电商平台的资深内容安全审核专家
   【领域知识】精通广告法和平台营销规范
   【任务】审查商家提交的促销文案，识别绝对化用语和虚假比价
```

**为什么有效？** 模型在预训练时见过大量专业领域文本，设定角色可以激活模型内部相关的知识分布。

### 层次 2：模块化指令（Structured Instructions）

文档《关于agent的初步探索.md》的作者踩坑后总结：

> 结构化是提升沟通效率的关键。通过类似 README 的格式，将 Prompt 拆解为角色、工具、注意事项、输出格式、执行逻辑、具体要求等模块化指令。

一个结构化的 Agent Prompt 模板：

```
## 角色
你是谁，负责什么

## 可用工具
每个工具的名称、用途、使用时机

## 工作流程
遇到任务时的思考和执行步骤

## 输出要求
回答的格式、语言风格、包含的信息

## 约束与边界
什么绝对不能做、什么时候必须停止

## 错误处理
工具失败时的应对策略、最大重试次数
```

### 层次 3：自我反思（Self-Reflection）

文档《怎么设计好一个AI Agent.md》介绍了 Self-Reflection 模式：

> 要求 Agent 在生成初步结果或采取行动后，暂时跳出当前任务视角，扮演一个独立的"批评者"角色。它会审视自己的输出是否符合要求、推理是否存在逻辑漏洞。

这就是**自愈循环**的基础：Agent 不只是"执行→失败→放弃"，而是"执行→失败→分析原因→换方案→重试"。

---

## 🏗️ 动手升级：第 1 步 — 重构 System Prompt 为模块化结构

你现在有一个很好的学习机会——你的 Prompt 足够简单，重构成本很低。

### 引导问题

1. 你的 Agent 叫 `YuManus`，它的定位是什么？一个"全能的超级 AI"还是某个领域的"专家助手"？
2. 你的 Agent 有三个工具：`WebSearchTool`（搜索）、`TerminalOperationTool`（终端）、
`TerminateTool`（结束任务）。在 Prompt 中，你需要告诉 Agent 每个工具**什么时候用**、**什么时候不用**。
比如搜索工具应该在什么场景用？终端工具呢？
3. 文档强调"约束与边界"——你的 Agent 绝对不能做什么？如果用户要求执行危险命令怎么办？
4. 你怎么组织 Prompt 的模块？用 Markdown 的 `##` 标题分块？还是用纯文本段落？

> **你的任务 1**：在 `YuManus.java` 中，把原来两行 `SYSTEM_PROMPT` + `NEXT_STEP_PROMPT`
> 重构为一个结构化 Prompt 模板。
>
> 🔴 你自己设计 Prompt 的模块结构，至少包含以下维度：
> - 角色定义
> - 可用工具说明（每个工具的名称、用途、使用时机、注意事项）
> - 工作原则
> - 约束（安全边界）
> - 错误处理策略
>
> 💡 建议用 Java 15+ 的文本块（`"""..."""`），比 `"..." + "..."` 拼接更易读。
>
> 📖 参考：《怎么设计好一个AI Agent.md》的"提示词工程"章节有大量 Prompt 模板示例。

---

## 🏗️ 动手升级：第 2 步 — 给 Tool 定义加上"使用说明书"

文档《MCP.md》强调：

> Agent 只能通过工具的名称、描述和参数 schema 来"理解"这个工具能做什么。它看不到你的代码实现，不知道函数内部做了什么。

你现在的 Tool 的 `@Tool(description = ...)` 和 `@ToolParam(description = ...)` 就是 LLM 看到的全部信息。

### 引导问题

1. `TerminalOperationTool` 的描述是 `"Execute a command in the terminal"`。Agent 看到这个描述，会认为它能执行任何命令吗？你需要在描述中说明什么？
2. `WebSearchTool.searchWeb()` 的描述说 `"Search the web for real-time information"`——这已经很好了！但它没说"什么时候不应该用"。
如果 Agent 已经在检索本地知识库了，还需要搜索吗？
3. `TerminateTool.doTerminate()` 的描述很长，但包含了关键信息：什么时候应该调用它。你能从这段描述中提取出"终止的判定标准"吗？
4. 文档说参数 description 中可以加"示例"——比如
`@ToolParam(description = "搜索关键词，例如：'人工智能最新进展'、'Spring AI 教程'")`。示例的作用是什么？

> **你的任务 2**：审查并优化三个 Tool 的 `@Tool(description)` 和 `@ToolParam(description)`。
>
> 🔴 优化标准：
> - 描述不仅说"做什么"，还要说"什么时候用、什么时候不用"
> - 参数描述加一个真实示例
> - `TerminalOperationTool` 的描述要体现安全限制（白名单）
>
> 这是"零代码改动"的优化——只改注解里的字符串，但效果可能比你想的大得多。

---

## 🏗️ 动手升级：第 3 步 — 实现 Agent 自愈循环（核心挑战）

这是本篇最核心的任务。看 `ToolCallAgent.think()` 
和 `ReActAgent.step()` 的异常处理：

```java
// ToolCallAgent.think() — 第 113-127 行
catch (Exception e) {
    log.error(getName() + "的思考过程遇到了问题：" + e.getMessage());
    getMessageList().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
    return false;
}

// ReActAgent.step() — 第 45-49 行
catch (Exception e) {
    e.printStackTrace();
    return "步骤执行失败：" + e.getMessage();
}
```

**问题**：Agent 遇到错误后，不知道"为什么错"、"可以怎么修"。

文档《关于agent的初步探索.md》描述了一个很好的模式——**自愈循环（Self-Healing Loop）**：

```
工具调用失败
    ↓
错误信息返回给 LLM（包含失败原因 + 建议的修正方向）
    ↓
LLM 分析错误 → 调整策略 → 重新选择工具/参数
    ↓
重试（但不是无限循环！有最大重试次数）
    ↓
超过重试上限 → 输出"无法完成" + 原因，不继续浪费 Token
```

### 引导问题

1. 你需要在 `ToolCallAgent` 中记录"同一个工具连续失败的次数"。用什么数据结构？
Map<String, Integer>（工具名 → 失败次数）？
2. 重试上限设多少合适？3 次？5 次？
3. 如果 Agent 换了一个工具但还是失败，算不算"连续失败"？还是只统计同一个工具？
4. 自愈循环需要"失败原因分析"——你怎么让 LLM 分析失败原因？
是在 Prompt 中加一条规则？还是写一段代码来包装错误信息？
5. 文档提到"越修越错"的风险：LLM 的修复尝试可能让问题更糟。你怎么避免？

> **你的任务 3（核心）**：在 `ToolCallAgent` 中添加失败计数和自愈逻辑。
>
> 🔴 你需要做：
> - ① 添加 `Map<String, Integer> toolFailureCount` 字段，记录每个工具连续失败的次数
> - ② 在 `act()` 方法中，工具调用失败时累加计数；成功后清零
> - ③ 在 `think()` 方法中，检测到某工具连续失败超过阈值（如 3 次）时，
> 在 Prompt 中追加一条提示："工具 X 已连续失败 3 次，请换用其他方案或向用户说明原因"
> - ④ 修改 `catch` 块的日志级别：可恢复的用 `log.warn`，不可恢复的用 `log.error`
>
> 💡 这涉及到字段管理、Map 操作、状态维护——都是 Java 基础功的实战。

---

## 🏗️ 动手升级：第 4 步 — 输出格式约束

文档《怎么设计好一个AI Agent.md》说：

> 结合显式的格式约束指令（如要求输出特定 Schema 的 JSON、XML、Markdown 等），
> 可以强制模型生成可被下游系统解析的结构化数据，而非自由文本。

你的 Agent 输出是纯文本。但如果有一天你想让前端解析 Agent 的回答来展示（比如标题、正文、建议分开显示），纯文本很难解析。

### 引导问题

1. 你能在 Prompt 中要求 Agent 用 Markdown 格式输出吗？是不是只加一句话就够了？
2. 如果要更严格——强制 JSON 格式——Prompt 怎么写？文档中给了一个 Schema 示例。
3. 格式约束的代价是什么？（提示：LLM 可能生成语法错误的 JSON，你需要容错解析）

> **你的任务 4（可选的进阶）**：在结构化 Prompt 中加入输出格式约束，
> 要求 Agent 用 Markdown 格式组织回答（标题 + 正文 + 建议），
> 并在 `AiController` 中用 Markdown 渲染返回给前端。
>
> 这个任务不强制，但如果你以后要做前端展示，这是必备的。

---

## ✅ 自我检验清单

- [ ] 能用自己的话解释"为什么结构化 Prompt 比扁平 Prompt 更稳定"
- [ ] 重构了 `YuManus` 的 Prompt 为模块化结构（至少含角色/工具/规则/约束）
- [ ] 优化了三个 Tool 的 description，LLM 能更准确理解"什么时候用"
- [ ] 在 `ToolCallAgent` 中实现了失败计数
- [ ] Agent 在工具连续失败后能自行调整策略（换工具或告知用户）
- [ ] 跑同一个任务 3 次，对比重构前后的行为稳定性

---

## 💡 延伸思考

1. 文档《关于agent的初步探索.md》的作者花大量时间调 Prompt，最后得出一个结论："Prompt 调试容易陷入调试地狱"。你怎么避免？有没有办法自动化测试 Prompt 的效果？

2. 文档《MCP.md》提到一个有趣的技巧叫做"Reminder Pattern"——在工具参数中加一个静态参数作为"行为提醒"，让 LLM 在执行前被迫重新确认。你能想出一个适用场景吗？

3. 文档中多次提到"上下文窗口是稀缺资源"。你的结构化 Prompt 模块中，哪些部分是**每次对话都必须带的**（如角色、约束），哪些是**可以按需加载的**（如工具详细说明）？

---

> 📌 上一篇：[第 7 篇：Agent 工具设计与可观测性](./07-agent-tool-observability.md) — 从"能用"到"可靠"
> 📌 下一篇：[第 9 篇：Agent 架构升级](./09-agent-architecture-upgrade.md) — Plan-and-Execute 范式
