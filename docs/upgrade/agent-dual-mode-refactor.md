# 项目架构改造说明文档

## 改造日期

2026-06-21

## 改造背景

原项目存在两个独立开发的模块但未整合：
- **QuizApp**（智能面试官）：直接通过 `ChatClient` 调用 LLM，配合 RAG 检索做知识考察
- **YuManus Agent**（自主规划智能体）：自研 ReAct 循环框架，但无任何生产端点使用

结果是：agent 框架写得完整但完全没被用上，名叫 `qian-ai-agent` 的项目实际跑起来只是一个 RAG 聊天机器人。

## 改造目标

参考 GitHub 热门 Agent 项目（Dify/Coze/LangGraph）的架构，采用 **"双模式并存"** 设计：

| 模式 | 端点 | 适用场景 |
|------|------|---------|
| 💬 智能对话 | `/api/ai/chat` | 快速问答、面试官知识考察（低延迟） |
| 🧠 Agent 自主规划 | `/api/ai/agent/chat` | 复杂多步任务、需要自主调用工具的场景 |

## 架构对比

### 改造前
```
AiController → QuizApp（RAG 聊天机器人）
YuManus（无人使用的 Agent 框架）
```

### 改造后
```
AiController
    ├── /ai/chat        → QuizApp（保留，快速问答）
    └── /ai/agent/chat  → YuManus（新增，Agent 自主规划）
            ├── RagSearchTool      ← 新增（知识库检索）
            ├── WebSearchTool      （已有）
            ├── TerminalOperationTool（已有）
            ├── TerminateTool      （已有）
            └── ...共 8 个工具
```

---

## 后端变更清单

### 1. 新增文件

| 文件 | 说明 |
|------|------|
| `tools/RagSearchTool.java` | 将 RAG 知识库检索封装为 Spring AI Tool，Agent 可自主调用。内部复用 `MultiQuerySearchService`，与 QuizApp 共享同一套检索逻辑（多查询变体 → 并行检索 → 去重排序） |

### 2. 修改文件

| 文件 | 改动内容 | 改变原因 |
|------|---------|---------|
| **`agent/BaseAgent.java`** | 新增 `runStreamAsFlux()` 方法 + `saveTraceToFile()` 方法 | 提供 Flux 流式输出，与 QuizApp 的 API 风格一致；Trace 保存逻辑从 run() 提取为独立方法，避免重复 |
| **`agent/YuManus.java`** | 改 `@Scope("prototype")`；系统提示词新增 RagSearchTool 描述 + "知识优先"原则 | Prototype 确保每次请求创建新 Agent 实例，避免多请求状态冲突；让 Agent 知道知识库检索的存在 |
| **`controller/AiController.java`** | 注入 `ObjectProvider<YuManus>`；新增 `/ai/agent/chat` 端点 | 每次请求获取新 Agent 实例；为 Agent 模式提供 HTTP 接口 |
| **`tools/ToolRegistration.java`** | 注入 `MultiQuerySearchService`；新增 `RagSearchTool` 注册 | 让 RAG 检索成为 Agent 工具箱的一部分 |
| **`agent/ToolCallAgent.java`** | 修复 `TaskPlan` 缺失 import | 已有 bug 修复（TaskPlan 类存在但未 import） |

### 3. 关键设计决策

#### 3.1 为什么 YuManus 改成 Prototype Scope？

YuManus 内部维护状态机（IDLE → RUNNING → FINISHED），单例模式下：
- 第一次请求完成后状态变为 FINISHED
- 第二次请求直接失败（状态校验不通过）
- 并发请求会互相踩踏状态

改为 Prototype 后，每次 `yuManusProvider.getObject()` 获取全新实例，状态隔离。

#### 3.2 为什么新增 RagSearchTool 而不是修改 QuizApp？

遵循 Agent 架构的经典模式：**RAG 是 Agent 的工具，不是平级模块**。将 RAG 封装为 Tool：
- Agent 在 think 阶段自主决定"是否需要检索知识库"
- 与 WebSearchTool 形成互补：本地知识库优先，互联网兜底
- 复用了现有 `MultiQuerySearchService`，零重复代码

#### 3.3 为什么 BaseAgent 新增 Flux 方法而不是改造 SseEmitter？

- QuizApp 的 API 风格是 `Flux<String>`，保持一致性
- `Flux.create()` 是 Reactor 原生的异步流构建方式
- 同时保留了原有 `run()` 和 `runStream()`（SseEmitter）方法，不影响现有测试

---

## 前端变更清单

| 文件 | 改动内容 |
|------|---------|
| `src/api/index.js` | 新增 `agentChat()` 函数，连接 `/ai/agent/chat` |
| `src/views/ChatView.vue` | 新增 `agentMode` 状态 + 侧边栏模式切换开关 + 欢迎页/提示文字随模式变化 + send() 根据模式选择 API |

**UI 变化**：侧边栏底部增加模式切换开关（💬 智能对话 ⇄ 🧠 Agent 规划），点击即可切换。

---

## 新增 API 接口

### GET `/api/ai/agent/chat`

**描述**：Agent 自主规划对话（SSE 流式）

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| message | String | 是 | 用户问题 |

**SSE 事件格式**：
```
Step 1: 思考完成 - 需要调用 RagSearchTool 搜索知识库
Step 2: 工具 RagSearchTool 返回的结果：...
Step 3: 思考完成 - 信息已充足，输出最终答案
[DONE]
```

**与 `/api/ai/chat` 的区别**：

| 维度 | /ai/chat（智能对话） | /ai/agent/chat（Agent 规划） |
|------|---------------------|---------------------------|
| 响应模式 | LLM Token 逐字流式输出 | 每步执行结果作为独立事件 |
| 推理方式 | 单次 LLM 调用 | ReAct 循环（think → act → think → ...） |
| 最大步数 | N/A | 20 步 |
| 会话记忆 | chatId 持久化（文件） | 单次请求内记忆 |
| 知识库 | 自动 RAG + 联网搜索 | Agent 自主决定何时检索 |
| 延迟 | 低（毫秒级） | 较高（秒级，多步执行） |

---

## 兼容性说明

- ✅ 原有 `/api/ai/chat` 端点保持不变，前端默认仍是智能对话模式
- ✅ 原有 `QuizApp` 所有方法未改动
- ✅ 原有 `YuManus.run()` / `YuManus.runStream()` 方法未改动
- ✅ 原有单元测试兼容（Compile 通过）

---

## 后续优化建议

1. **Agent 会话记忆持久化**：目前 Agent 模式无跨请求记忆，可参考 QuizApp 的 FileBasedChatMemory 实现
2. **YuManus 的 Plan-and-Execute 模式**：ToolCallAgent 中已预留 `TaskPlan` 字段，可在 `agent/plan/` 包下实现
3. **流式 Token 输出**：目前 Agent 按步骤输出结果，未来可在 think/act 内部集成 LLM 的 Token 级流式输出
