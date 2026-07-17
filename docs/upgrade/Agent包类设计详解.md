# Agent 包类设计详解

## 概述

`agent` 包实现了一套 **AI 智能体（Agent）** 的类继承体系，核心理念是：**让 LLM 自己决定用哪些工具、按什么顺序调用，在多轮循环中自主完成任务**。

**包路径**：`com.qian.qianaiagent.agent`

## 类层级结构

```
BaseAgent（抽象基类 — 智能体大脑）
  └── ReActAgent（抽象类 — 思考-行动模式）
        └── ToolCallAgent（具体类 — 工具调用智能体）
              └── YuManus（开箱即用的 Spring Bean）
```

辅助类：
- `model/AgentState` — 智能体状态枚举

---

## 1. BaseAgent — 智能体大脑

**类型**：抽象基类  
**职责**：定义智能体的通用框架，管理状态和执行流程

### 核心属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `name` | String | 智能体名称 |
| `systemPrompt` | String | 系统提示词，定义智能体的角色和行为 |
| `nextStepPrompt` | String | 下一步提示词，引导 LLM 规划下一步操作 |
| `state` | AgentState | 当前状态（IDLE / RUNNING / FINISHED / ERROR） |
| `currentStep` | int | 当前执行步骤数 |
| `maxSteps` | int | 最大执行步骤（默认 10） |
| `chatClient` | ChatClient | LLM 对话客户端，作为决策引擎 |
| `messageList` | List\<Message\> | 会话消息上下文（记忆系统） |

### 核心方法

#### `run(String userPrompt)` — 同步执行

1. 校验状态是否为 `IDLE`
2. 状态切换为 `RUNNING`
3. 将用户提示词加入消息上下文
4. 进入循环：最多执行 `maxSteps` 步，每步调用 `step()`，直到状态变为 `FINISHED`
5. 若超出步骤限制，自动终止
6. 异常处理：状态切换为 `ERROR`
7. `finally` 中调用 `cleanup()` 清理资源
8. 返回所有步骤结果的拼接字符串

#### `runStream(String userPrompt)` — 流式执行（SSE）

- 与 `run()` 逻辑相同，但通过 `SseEmitter` 实时推送每一步的结果
- 在独立线程中异步执行，避免阻塞主线程
- 5 分钟超时，支持超时回调和完成回调
- 适合前端需要实时展示智能体思考过程的场景

#### `step()` — 抽象方法

子类必须实现，定义单步执行的逻辑。

#### `cleanup()` — 资源清理

子类可重写，用于清理资源（如关闭连接、释放工具等）。

---

## 2. ReActAgent — 思考-行动循环模式

**类型**：抽象类（继承 `BaseAgent`）  
**职责**：实现 ReAct（Reasoning + Acting）模式，将单步拆分为"先思考、后行动"

### 核心方法

#### `think()` — 思考阶段（抽象方法）

- 分析当前状态，判断是否需要执行行动
- 返回 `true` → 需要行动，进入 `act()`
- 返回 `false` → 不需要行动，跳过

#### `act()` — 行动阶段（抽象方法）

- 执行具体操作
- 返回行动结果字符串

#### `step()` — 已实现

```java
public String step() {
    boolean shouldAct = think();   // 先思考
    if (!shouldAct) {
        return "思考完成 - 无需行动";
    }
    return act();                  // 再行动
}
```

**设计思想**：将 LLM 的决策（思考）和执行（行动）解耦，符合 ReAct 论文的核心范式。

---

## 3. ToolCallAgent — 工具调用智能体

**类型**：具体类（继承 `ReActAgent`）  
**职责**：具体实现 `think()` 和 `act()`，可以实例化使用

### 核心属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `availableTools` | ToolCallback[] | 可用的工具列表 |
| `toolCallChatResponse` | ChatResponse | LLM 返回的工具调用响应（暂存） |
| `toolCallingManager` | ToolCallingManager | 工具调用管理器 |
| `chatOptions` | ChatOptions | 对话选项（禁用 Spring AI 内置工具调用） |

### 关键设计

构造函数中设置了：
```java
this.chatOptions = OpenAiChatOptions.builder()
    .internalToolExecutionEnabled(false)  // 禁用 Spring AI 内置工具调用机制
    .build();
```

**为什么禁用**：改为手动维护消息上下文和工具调用流程，以获得更精细的控制。

### `think()` — 思考阶段实现

```
┌──────────────────────────────────────────┐
│  1. 拼接 nextStepPrompt 到消息上下文     │
├──────────────────────────────────────────┤
│  2. 调用 ChatClient + tools             │
│     → LLM 决定要调用哪些工具             │
├──────────────────────────────────────────┤
│  3. 解析返回的工具调用列表（ToolCall）   │
│     - 获取工具名称和参数                 │
│     - 记录日志                           │
├──────────────────────────────────────────┤
│  4. 判断：                               │
│     - 无工具调用 → 记录助手消息，返回 false│
│     - 有工具调用 → 暂存响应，返回 true    │
└──────────────────────────────────────────┘
```

### `act()` — 行动阶段实现

```
┌──────────────────────────────────────────┐
│  1. 通过 ToolCallingManager 执行工具     │
├──────────────────────────────────────────┤
│  2. 将执行结果记录到消息上下文           │
│     （conversationHistory 自动管理）      │
├──────────────────────────────────────────┤
│  3. 检查是否调用了 doTerminate 终止工具  │
│     - 是 → 状态切换为 FINISHED           │
├──────────────────────────────────────────┤
│  4. 返回所有工具结果                     │
└──────────────────────────────────────────┘
```

### 完整执行流程

```
用户输入 "今天天气怎么样？"
  │
  ▼
┌─────────────────────────────────────────┐
│  Step 1: think()                        │
│  LLM 分析 → 需要调用 searchWeb 工具     │
│  参数: query="今天天气"                  │
│  → 返回 true                            │
├─────────────────────────────────────────┤
│  Step 1: act()                          │
│  执行 searchWeb("今天天气")              │
│  → 返回搜索结果                         │
└─────────────────────────────────────────┘
  │
  ▼
┌─────────────────────────────────────────┐
│  Step 2: think()                        │
│  LLM 分析结果 → 信息充足，可回答用户     │
│  → 返回 false（无需再调用工具）          │
└─────────────────────────────────────────┘
  │
  ▼
  返回最终回答
```

这就是 **"自主规划 → 调用工具 → 观察结果 → 再规划"** 的核心循环。

---

## 4. YuManus — 鱼皮的超级智能体

**类型**：具体类（继承 `ToolCallAgent`），标注 `@Component`  
**职责**：封装好配置的、可直接注入使用的超级智能体

### 预置配置

```java
// 角色定义
systemPrompt: "You are YuManus, an all-capable AI assistant..."

// 行动指南
nextStepPrompt: """
    Based on user needs, proactively select the most appropriate tool...
    For complex tasks, you can break down the problem...
    If you want to stop, use the `terminate` tool.
    """

// 参数配置
maxSteps: 20  // 比默认 10 步更宽容

// 日志增强
defaultAdvisors: MyLoggerAdvisor  // 记录每次 LLM 交互的详细日志
```

### 依赖注入

```java
public YuManus(ToolCallback[] allTools, ChatModel openAiChatModel) {
    // 注入所有 Spring 容器中的 ToolCallback Bean
    // 注入 OpenAI ChatModel
    // 自动构建 ChatClient
}
```

### 使用方式

```java
@Autowired
private YuManus yuManus;

// 同步调用
String result = yuManus.run("帮我查一下北京的天气");

// 流式调用
SseEmitter emitter = yuManus.runStream("帮我分析这份数据");
```

---

## 5. AgentState — 状态枚举

**路径**：`agent/model/AgentState.java`

```java
public enum AgentState {
    IDLE,      // 空闲，等待任务
    RUNNING,   // 正在执行
    FINISHED,  // 执行完成
    ERROR      // 执行出错
}
```

### 状态流转图

```
IDLE ──run()──▶ RUNNING ──正常结束──▶ FINISHED
                    │
                    └──异常──▶ ERROR
```

---

## 设计模式总结

| 模式 | 应用 |
|------|------|
| **模板方法模式** | `BaseAgent.run()` 定义骨架，`step()` 由子类实现 |
| **策略模式** | `ReActAgent` 将执行策略拆分为 `think()` + `act()` |
| **组合模式** | `ToolCallAgent` 组合了 `ToolCallingManager`、`ChatClient`、多个 `ToolCallback` |
| **依赖注入** | `YuManus` 通过 Spring 容器注入所有依赖 |

## 扩展指南

如需自定义智能体：

1. **简单场景**：直接使用 `ToolCallAgent` 实例，传入工具和配置
2. **定制行为**：继承 `ToolCallAgent`，重写 `think()` 或 `act()`
3. **完全自定义**：继承 `BaseAgent` 或 `ReActAgent`，实现自己的决策逻辑
4. **固定流程**：不需要 ReAct 循环时，直接继承 `BaseAgent` 实现 `step()` 即可
