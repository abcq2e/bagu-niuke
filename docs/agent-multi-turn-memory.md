# Agent 多轮对话记忆改造文档

## 概述

将 `YuManus` Agent 从**单次调用模式**升级为**多轮对话模式**，采用 OpenAI Threads 架构风格，让 Agent 在多次 HTTP 请求之间保持对话上下文。

## 改造前 vs 改造后

### 改造前（单次调用）

```
请求1: "分析 Spring Boot 启动慢" → new Agent → messageList=[]
       → ReAct 循环 → 返回结果 → Agent 销毁 🗑️

请求2: "那数据库连接池呢？" → new Agent → messageList=[] 
       → Agent: "什么数据库？" 🤷 → 完全失忆
```

### 改造后（多轮对话）

```
请求1: "分析 Spring Boot 启动慢"  (chatId=abc123)
       → new Agent → 加载历史 (空) → messageList=[]
       → ReAct 循环 → 返回结果 → 保存到 chatMemory 💾

请求2: "那数据库连接池呢？"  (chatId=abc123)
       → new Agent → 加载历史 → messageList=[请求1的完整上下文]
       → ReAct 循环 → Agent 知道上下文 → 正确回答 → 保存 💾
```

## 架构设计

### 三层记忆架构（OpenAI Threads 模式）

```
┌──────────────────────────────────────────────────────┐
│ 第1层：SummarizingChatMemory（滑动窗口 + 摘要压缩）    │
│ - 保留最近 40 条消息原文                               │
│ - 超出部分调用 LLM 压缩为结构化摘要                     │
│ - 存储：JSON 文件（data/chat-memory/）                 │
├──────────────────────────────────────────────────────┤
│ 第2层：Agent messageList（单次推理的工作记忆）          │
│ - 从 ChatMemory 预加载历史                             │
│ - 在 runStreamAsFlux() 内部维护 ReAct 循环上下文        │
│ - 存储：内存，用完即弃                                 │
├──────────────────────────────────────────────────────┤
│ 第3层：FileBasedChatMemory（完整历史备份）              │
│ - 持久化所有原始消息，不压缩不截断                      │
│ - 供管理接口使用（列表/导出/删除/重命名）               │
│ - 存储：JSON 文件（data/chat-memory/）                 │
└──────────────────────────────────────────────────────┘
```

### 消息流转

```
用户请求(message, chatId)
    │
    ▼
agentChatMemory.get(chatId)                    ← SummarizingChatMemory
    │  ┌─ 消息数 ≤ 40 → 返回全部原文
    │  └─ 消息数 > 40  → 返回 [摘要] + [最近40条]
    ▼
agent.getMessageList().addAll(history)          ← 预加载到 Agent
    │
    ▼
agent.runStreamAsFlux(message)                  ← ReAct 循环
    │  messageList: [...历史, 用户消息, AI思考, 工具结果, ...]
    ▼
doFinally → agentChatMemory.add(chatId, newMsgs) ← 持久化本轮新增消息
```

## 修改的文件

### 1. `AiController.java`

| 变更 | 说明 |
|------|------|
| 新增字段 `agentChatMemory` | 注入 `ChatMemory`（SummarizingChatMemory），用于 Agent 多轮记忆 |
| 重命名字段 `chatMemory` → `fileBasedChatMemory` | 区分两个 Bean，管理接口继续使用完整历史 |
| `doAgentChat()` 新增 `chatId` 参数 | 可选，不传自动生成 `agent_` 前缀的新会话 |
| `doAgentChat()` 新增预加载逻辑 | 运行前从 ChatMemory 加载历史到 Agent 的 messageList |
| `doAgentChat()` 新增持久化逻辑 | 运行后通过 `doFinally` 保存本轮新消息 |

#### 关键代码

```java
// 预加载历史
List<Message> history = agentChatMemory.get(finalChatId);
int historySize = 0;
if (!history.isEmpty()) {
    agent.getMessageList().addAll(history);
    historySize = history.size();
}

// 运行 Agent
return agent.runStreamAsFlux(message)
    .doFinally(signalType -> {
        // 只保存本轮新增的消息，避免重复存储
        List<Message> fullList = agent.getMessageList();
        if (fullList.size() > savedHistorySize) {
            List<Message> newMessages = new ArrayList<>(
                    fullList.subList(savedHistorySize, fullList.size()));
            agentChatMemory.add(finalChatId, newMessages);
        }
    });
```

### 依赖的已有组件（未修改）

| 组件 | 文件 | 作用 |
|------|------|------|
| `SummarizingChatMemory` | `chatmemory/SummarizingChatMemory.java` | 滑动窗口 + 摘要压缩 |
| `FileBasedChatMemory` | `chatmemory/FileBasedChatMemory.java` | JSON 文件持久化 |
| `ConversationSummarizer` | `chatmemory/ConversationSummarizer.java` | LLM 对话摘要 |
| `ChatMemoryConfig` | `config/ChatMemoryConfig.java` | 双层架构 Bean 配置 |

## API 变更

### Agent 对话接口

```
GET /ai/agent/chat?message=xxx&chatId=xxx
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `message` | String | ✅ | 用户消息，最长 2000 字 |
| `chatId` | String | ❌ | 会话 ID，不传自动生成 |

**向后兼容**：`chatId` 为可选参数，不传时行为与改造前一致（每次生成新会话）。

### 前端集成示例

```javascript
// 第1次调用
const chatId = crypto.randomUUID();
fetch(`/ai/agent/chat?message=分析启动慢&chatId=${chatId}`);

// 第2次调用（同一 chatId，Agent 有记忆）
fetch(`/ai/agent/chat?message=那数据库连接池呢？&chatId=${chatId}`);
```

## 容量与策略

### 滑动窗口

| 参数 | 值 | 位置 |
|------|-----|------|
| 最大消息数（窗口大小） | 40 条 | `SummarizingChatMemory.DEFAULT_MAX_MESSAGES` |
| 单文件最大消息数 | 200 条 | `FileBasedChatMemory.MAX_MESSAGES_PER_FILE` |
| 建议最大文件数 | 100 个 | `FileBasedChatMemory.MAX_RECOMMENDED_FILES` |

### 摘要触发条件

当 `chatMemory.get(chatId)` 发现历史消息超过 40 条时：
1. 前 (N - 40) 条消息送给 `ConversationSummarizer` 压缩
2. 后 40 条消息保留原文
3. 返回 `[SystemMessage(摘要) + 最近40条原文]`
4. 摘要缓存在内存中，新消息到达时作废

### Token 控制

```
单轮最多消耗 ≈ systemPrompt(~500) + 摘要(~100) + 40条原文(~4000) + ReAct步骤(~2000)
              ≈ 6600 token/次 LLM 调用
```

## 后续优化方向

1. **Agent 专属 ChatMemory**：当前 Agent 和 QuizApp 共用同一套 ChatMemory 文件，可拆分为独立存储
2. **摘要 Prompt 定制**：当前摘要 Prompt 面向面试场景，可改为通用 Agent 摘要
3. **流式 Plan-and-Execute**：`ToolCallAgent.run()` 已支持 Plan 模式，但 `runStreamAsFlux()` 未覆盖
4. **对话列表区分**：管理接口 `/ai/conversations` 返回所有对话（Agent + QuizApp 混在一起），可加 type 过滤
