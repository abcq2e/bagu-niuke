# 第 9 篇动手指南：从 ReAct 到 Plan-and-Execute

> 配合 `09-agent-architecture-upgrade.md` 使用。理论看那篇，动手看这篇。
>
> 面向：Java 学了 ~4 个月，能写 Spring Boot 但不太会自己做设计决策的同学。

---

## 动手之前：你现在的代码长什么样

打开 `ToolCallAgent.java`，你已经有了：

```
BaseAgent.run()                 ← for 循环 + step()
    ↓
ReActAgent.step()               ← think() → act()
    ↓
ToolCallAgent.think() / act()   ← 你刚实现的（自愈循环、失败计数）
    ↓
YuManus                         ← 具体的 Agent 实例
```

你马上要加的：

```
ToolCallAgent.generatePlan()    ← 新方法（你需要实现）
    ↓
在 run() 里加一个判断           ← 新逻辑（你需要设计）
    ├── 简单任务 → ReAct（现有）
    └── 复杂任务 → Plan-and-Execute（新增）
```

---

## 第一步：补全数据模型（~15分钟）

### 先想一想

一个计划包含多个步骤。每个步骤有自己的状态。

问题：如果第 3 步执行失败，你怎么知道"哪些步骤已完成、哪些还没开始"？

### 基础补充：enum 是什么？

你可以用 `String` 表示状态——`"pending"`, `"done"`。但问题是：
- 打错字（`"pendding"`）编译器不会报错，运行时才崩
- 别人不知道状态有哪几种可能的值

`enum` 解决这两个问题：
```java
public enum StepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }
//                       ↑ 编译器会检查，你打错字直接编译不过
```

### 你的任务

1. 打开 `agent/plan/TaskStep.java`，在 TODO 处补全 4 个字段
2. 打开 `agent/plan/TaskPlan.java`，在 TODO 处补全 4 个字段

**验证方式**：写完编译通过（`mvnw compile -q`），说明类型和语法没问题。

---

## 第二步：实现 generatePlan()（~45分钟，核心锻炼）

这是本篇最重要的方法。你需要让 LLM 扮演"规划师"，生成一个 JSON 格式的任务计划。

### 先想一想

1. 你要 LLM 输出 JSON，但 LLM 本质上是"文字接龙"——它不会主动输出 JSON。怎么办？
2. 如果 LLM 输出的 JSON 格式有问题（少括号、多逗号），你的程序会怎样？
3. 你不希望"JSON 解析失败"导致整个 Agent 崩溃。有什么办法优雅处理？

### 基础补充 1：LLM 怎么输出结构化数据？

Spring AI 的 `ChatClient` 提供了一个方法叫 `.entity(Xxx.class)`。它会：
1. 告诉 LLM "请输出 JSON 格式"
2. LLM 返回 JSON 字符串
3. Spring AI 自动把 JSON 转成 Java 对象

一行代码搞定：
```java
Xxx result = chatClient.prompt()
        .system("你是XXX专家，请输出JSON格式...")
        .user("用户的具体需求")
        .call()
        .entity(Xxx.class);  // Spring AI 自动解析 JSON → Java 对象
```

项目中已经有例子：`QuizApp.java` 第 158 行。

### 基础补充 2：你的 LLM 调用工具有哪些？

在你的 `ToolCallAgent` 中，`getChatClient()` 返回的是 `ChatClient` 对象（构造时传入的）。所以你可以直接：

```java
getChatClient().prompt()
    .system(...)    // 你的规划师 Prompt
    .user(...)      // userGoal
    .call()
    .entity(TaskPlan.class)
```

### 基础补充 3：try-catch 为什么要用？

```java
try {
    TaskPlan plan = getChatClient()...entity(TaskPlan.class);
    return plan;  // 成功了
} catch (Exception e) {
    log.warn("规划失败: {}", e.getMessage());
    return null;  // 失败了，返回 null 表示"降级为 ReAct"
}
```

`catch` 让程序不崩溃。`return null` 让调用方知道"这件事没做成"。

### 你的任务

打开 `ToolCallAgent.java`，找到 `generatePlan()` 方法（约第 220 行）。按注释里的 3 个小步实现：

1. **设计 Prompt**：告诉 LLM 它是什么角色、要输出什么格式、给一个示例
2. **调用 LLM**：参考 `QuizApp.doQuizReport()`（158行）的 `.entity()` 模式
3. **容错**：用 try-catch，失败返回 null

**遇到困难时的搜索关键词**：
- "Spring AI ChatClient entity example"
- "Java try catch return null pattern"
- "Few-Shot prompting JSON output"

---

## 第三步：设计混用切换逻辑（~30分钟，进阶锻炼）

`generatePlan()` 返回值：成功 → TaskPlan 对象，失败 → null。

现在你需要一个地方来调用它，并决定"走 Plan 还是走 ReAct"。

### 先想一想

1. 你的 `run()` 方法在哪个类里？（提示：回想继承链——`ToolCallAgent` 
→ `ReActAgent` → `BaseAgent`，`run()` 在最顶层）
2. 你要改 `run()`，是在父类改还是在子类改？各自有什么优缺点？（提示：有三个类可以改）
3. 如果 `generatePlan()` 返回 null，怎么办？

### 基础补充：方法重写（Override）

子类可以声明一个和父类**完全相同**的方法签名，覆盖父类的实现：

```java
// 顶层父类：ReAct 循环在这里
public class BaseAgent {
    public String run(String userPrompt) {
        // for 循环 + step() → ReAct 模式
    }
    public abstract String step();
}

// 中间层：定义 think/act 骨架
public abstract class ReActAgent extends BaseAgent {
    @Override
    public String step() {
        // think() → act()
    }
    public abstract boolean think();
    public abstract String act();
}

// 具体子类：你当前所在的类
public class ToolCallAgent extends ReActAgent {
    // think() 和 act() 已实现（自愈循环、工具调用）

    @Override
    public String run(String userPrompt) {
        // 先尝试 Plan-and-Execute
        // 不行就调用 super.run(userPrompt) 回到 BaseAgent 的 ReAct 循环
    }
}
```

> 💡 虽然中间隔了一个 `ReActAgent`，但 `ReActAgent` 没有重写 `run()`，所以
> `super.run()` 最终调用的还是 `BaseAgent.run()`——原来的 ReAct 循环。

`super.run()` 是调用父类的 run() 方法。这意味着：**Plan 失败后，可以无缝回退到原来的
ReAct 逻辑。**

### 基础补充：遍历 List

```java
List<TaskStep> steps = plan.getSteps();
for (TaskStep step : steps) {
    // step 是当前步骤
    // step.getDescription() 获取描述
    // step.setStatus(...) 修改状态
}
```

### 你的任务

在 `ToolCallAgent` 中重写 `run()` 方法（代码里已经给你留了注释入口）。逻辑很简单：

```
1. 调用 generatePlan(userPrompt)
2. 如果计划生成成功 → 遍历 steps，每个 step 作为一个"子任务"执行
3. 如果计划生成失败 → super.run(userPrompt)，走原来的 ReAct
```

**设计问题（不用写代码，想一想就好）**：

1. 遍历步骤时，怎样把"当前这一步的描述"告诉 Agent 让它执行？
   提示：`BaseAgent` 有个 `run()` 方法可以传入任意文本
2. 如果第 3 步失败，第 4 步还执行吗？
3. 你怎么记录"Plan 模式的执行结果"和"ReAct 模式的执行结果"？
   提示：AgentTrace

---

## 第四步：测试你的实现（~20分钟）

### 找一个复杂任务来测

简单任务："你好"、"Java 是什么" → 应该走 ReAct

复杂任务："分析 /src/main/java/com/yupi/yuaiagent 目录下所有代码，
总结项目架构，给出 3 条改进建议" → 应该走 Plan-and-Execute

### 验证清单

找对比例子：
- [ ] 同一个复杂任务，ReAct 模式下执行了多少步？
- [ ] 同一个复杂任务，Plan-and-Execute 模式下执行了多少步？
- [ ] 哪个模式结果更清晰、更有条理？
- [ ] Token 消耗是多了还是少了？（看日志里的请求次数）

### 调 Bug 思路

如果 Plan-and-Execute 模式不工作：

| 现象 | 可能原因 | 检查方法 |
|------|---------|---------|
| 计划总是返回 null | LLM 返回的 JSON 解析失败 | 在 catch 里打印 LLM 的原始返回 |
| 计划生成成功但不执行 | 没有正确遍历 steps | 加 `log.info` 打印每一步 |
| 执行后结果混乱 | 每一步的上下文串了 | 检查消息列表是否在步间被污染 |
| 编译报错 | 类型不匹配或 import 缺失 | 看错误信息，逐个 import |

---

## 做完后的自我检验

- [ ] 能解释 Plan-and-Execute 和 ReAct 的核心区别（不是背定义，是用自己的例子）
- [ ] 知道 `try-catch` + `return null` 是一种什么设计模式
- [ ] 知道 `enum` 相比 `String` 作为状态标记好在哪
- [ ] 知道 `.entity()` 方法做了什么
- [ ] 能在复杂任务上看到 Plan 模式比 ReAct 步骤更清晰
- [ ] 知道方法重写（Override）和 `super.xxx()` 的作用

---

## 进阶思考（做完再看）

1. 你现在是让 LLM 一次性生成全部计划。但如果任务执行到一半，发现计划需要调整怎么办？这叫做"重规划"（Replanning）。
2. 你现在的切换判断是"Plan 成功就用 Plan，失败就 ReAct"。有没有更智能的判断方式？比如分析用户问题的复杂度？
3. 如果以后你要升级到 Multi-Agent（多个 Agent 各司其职），这个 TaskPlan 数据结构需要怎么改？

---

> 📌 返回理论篇：[第 9 篇：Agent 架构升级](./09-agent-architecture-upgrade.md)
> 📌 上一篇：[第 8 篇：结构化 Prompt 工程与自愈循环](./08-prompt-engineering-self-healing.md)
