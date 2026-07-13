# SSE 流式 AI 接口架构设计文档

> **核心技术栈**: SseEmitter / Flux + CompletableFuture + Spring AI + EventSource  
> **设计目标**: 异步解耦大模型推理任务，实时推送智能体推理过程，优化用户等待体感

---

## 目录

1. [整体架构概览](#1-整体架构概览)
2. [两条 SSE 流式路径对比](#2-两条-sse-流式路径对比)
3. [路径一：Flux\<String\> 响应式流（统一对话）](#3-路径一fluxstring-响应式流统一对话)
4. [路径二：SseEmitter + CompletableFuture（智能体推理）](#4-路径二sseemitter--completablefuture智能体推理)
5. [前端 EventSource 消费](#5-前端-eventsource-消费)
6. [完整调用链路时序](#6-完整调用链路时序)
7. [关键设计决策与优化](#7-关键设计决策与优化)
8. [文件索引](#8-文件索引)

---

## 1. 整体架构概览

```
┌──────────────────────────────────────────────────────────────────────┐
│                          前端 (Vue 3)                                 │
│  ChatView.vue  ──EventSource──>  /api/ai/chat?message=xxx&chatId=xxx │
└──────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ SSE (text/event-stream)
                                    ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     Controller 层 (AiController)                      │
│  @GetMapping(value="/chat", produces=TEXT_EVENT_STREAM_VALUE)         │
│  返回 Flux<String>  →  Spring MVC 自动转为 SSE 格式发给前端             │
└──────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────┐
│                       App 层 (QuizApp)                                │
│  doUnifiedChat(): 查询重写 → RAG检索 → 联网搜索 → 拼接上下文 → 流式调用 │
│  chatClient.prompt()...stream().content()  →  Flux<String>            │
└──────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    Spring AI ChatClient (框架层)                       │
│  .stream().content() 自动将模型 token 流包装为 Flux<String>             │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  Advisor 链 (AOP 拦截器链)                                     │    │
│  │  MessageChatMemoryAdvisor  ← MongoDB 持久化多轮对话记忆         │    │
│  │  MyLoggerAdvisor           ← 流式日志（聚合后输出）              │    │
│  │  ConversationGraphAdvisor  ← Neo4j 对话图谱同步（可选）         │    │
│  └──────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────┐
│                   大模型 API (DeepSeek / DashScope)                    │
│  返回 SSE 流 → token 级别的增量推送 → Spring AI 解析为 Flux<String>    │
└──────────────────────────────────────────────────────────────────────┘
```

**项目中有两条独立的 SSE 流式路径**，分别服务于不同场景：

| 特性 | 路径一：Flux\<String\> | 路径二：SseEmitter + CompletableFuture |
|------|----------------------|--------------------------------------|
| **使用场景** | 统一对话（RAG + 搜索 + 记忆） | 智能体自主推理（Think-Act 循环） |
| **核心技术** | Spring WebFlux Reactive Streams | Spring MVC SseEmitter + 线程池 |
| **入口方法** | `AiController.doChat()` | `BaseAgent.runStream()` |
| **流式粒度** | LLM token 级别（逐 token） | Agent step 级别（逐步骤） |
| **异步方式** | Reactor 响应式（非阻塞 I/O） | CompletableFuture.runAsync（线程池） |
| **超时控制** | Flux 自带背压 | SseEmitter(300000L) 5分钟超时 |

---

## 2. 两条 SSE 流式路径对比

### 路径一：Flux\<String\>（当前主力）

```
用户发消息 → Controller(Flux) → QuizApp → Spring AI stream() → DeepSeek API
                                                                    │
                                            token-1, token-2, ..., token-N
                                                                    │
前端 EventSource.onmessage ◄── SSE ◄── Flux<String> ◄──────────────┘
```

- **粒度**：LLM 每生成一个 token，立即推给前端（打字机效果）
- **优点**：用户体验最好，看到 AI 逐字"思考输出"
- **缺点**：只是单次 LLM 调用，没有 Agent 多步推理的中间过程

### 路径二：SseEmitter + CompletableFuture（智能体专属）

```
用户发消息 → Controller → BaseAgent.runStream()
                              │
                              ├── CompletableFuture.runAsync() → 后台线程
                              │       │
                              │       ├── Step 1: think() → "思考：需要使用搜索工具"
                              │       │   sseEmitter.send("Step 1: ...") ──────┐
                              │       ├── Step 2: act()  → "工具返回：搜索结果"   │
                              │       │   sseEmitter.send("Step 2: ...") ──────┤
                              │       ├── Step 3: think() → "思考：无需更多工具"  │
                              │       │   sseEmitter.send("Step 3: ...") ──────┤
                              │       └── sseEmitter.complete() ───────────────┤
                              │                                                 │
前端 EventSource ◄────────────┴─────────────────────────────────────────────────┘
   每收到一个 send() → 屏幕追加一行推理过程
```

- **粒度**：Agent 每一步推理和工具调用完成后，推送给前端
- **优点**：用户看到 AI 的"思考过程"——它选择了什么工具、工具返回了什么、下一步做什么
- **缺点**：实现更复杂，需要手动管理线程和 SSE 连接生命周期

---

## 3. 路径一：Flux\<String\> 响应式流（统一对话）

### 3.1 Controller 入口

```java
// AiController.java (第 44-49 行)
@GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> doChat(String message, String chatId) {
    if (chatId == null || chatId.isBlank()) {
        chatId = "chat_" + System.currentTimeMillis();
    }
    return quizApp.doUnifiedChat(message, chatId);
}
```

**关键点**：
- `produces = MediaType.TEXT_EVENT_STREAM_VALUE` 告诉 Spring MVC：这个接口返回的是 SSE 流
- 返回类型 `Flux<String>` 是 Reactor 的响应式类型，Spring 会自动将每个 `String` 元素包装为 `data: xxx\n\n` 的 SSE 格式
- Spring MVC 检测到 `Flux` 返回类型 → 自动启用异步处理 → 不阻塞 Tomcat 线程

### 3.2 QuizApp 核心流式方法

```java
// QuizApp.java (第 219-246 行)
public Flux<String> doUnifiedChat(String message, String chatId) {
    // 1. 查询重写（优化检索效果）
    String rewrittenMessage;
    try {
        rewrittenMessage = queryRewriter.doQueryRewrite(message);
    } catch (Exception e) {
        log.warn("查询重写失败，使用原始消息: {}", e.getMessage());
        rewrittenMessage = message;
    }

    // 2. 构建上下文：RAG 向量检索 + 联网搜索
    String context = buildContext(rewrittenMessage);

    // 3. 拼接最终消息
    String userMessage;
    if (!context.isEmpty()) {
        userMessage = context + "\n\n请基于以上参考信息回答用户问题：" + rewrittenMessage;
    } else {
        userMessage = rewrittenMessage;
    }

    // 4. 流式调用大模型
    return chatClient
            .prompt()
            .user(userMessage)
            .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
            .stream()      // ← 关键：调用 stream() 而非 call()
            .content();    // ← 返回 Flux<String>，每个元素是一个 token
}
```

**执行流程详解**：

```
doUnifiedChat() 执行
    │
    ├── 1. queryRewriter.doQueryRewrite(message)
    │      调用大模型将口语化查询改写为更精准的检索查询
    │      如 "java里那个多线程的锁是咋回事" → "Java 多线程 synchronized 锁机制原理"
    │      失败时降级：直接用原始消息（try-catch 保护）
    │
    ├── 2. buildContext(rewrittenMessage)
    │      │
    │      ├── RAG 向量检索
    │      │   quizVectorStore.similaritySearch(query, topK=5, threshold=0.3)
    │      │   从向量数据库检索相关知识点作为上下文
    │      │
    │      └── 联网搜索（按需触发）
    │          needWebSearch(query) 检测关键词: "搜索/最新/今天/新闻/价格/天气..."
    │          → WebSearchTool.searchWeb(query) 调用搜索 API
    │
    ├── 3. 拼接 userMessage = context + 用户原始问题
    │
    └── 4. chatClient.prompt()
            .user(userMessage)
            .advisors(chatId绑定)   ← 多轮对话记忆
            .stream()               ← 流式调用
            .content()              ← 返回 Flux<String>
```

### 3.3 Spring AI stream() 底层原理

```
chatClient.stream().content()
    │
    ▼
Spring AI ChatClient
    │  创建 StreamingChatModel 请求
    │  底层调用 DeepSeek/DashScope API (stream=true)
    ▼
大模型 API (DeepSeek)
    │  返回 SSE 流:
    │  data: {"choices":[{"delta":{"content":"Java"}}]}
    │  data: {"choices":[{"delta":{"content":"的"}}]}
    │  data: {"choices":[{"delta":{"content":"synchronized"}}]}
    │  ...
    │  data: [DONE]
    ▼
Spring AI 解析每个 SSE chunk
    │  提取 delta.content → 逐个发射到 Flux<String>
    ▼
Flux<String>
    │  "Java" → "的" → "synchronized" → "关键字" → ...
    ▼
Spring MVC 自动序列化为 SSE 格式
    │  data: Java\n\n
    │  data: 的\n\n
    │  data: synchronized\n\n
    ▼
前端 EventSource.onmessage = (e) => { msg.content += e.data }
```

### 3.4 Advisor 链（AOP 拦截器）                                                 

```java
// QuizApp 构造函数 (第 74-98 行)
List<Advisor> advisors = new ArrayList<>();
advisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build());  // ①
advisors.add(new MyLoggerAdvisor());                                  // ②
graphAdvisorProvider.ifAvailable(advisor -> advisors.add(advisor));   // ③
```

每个 Advisor 可以在请求前/响应后织入逻辑：

| Advisor | 作用 | 流式模式 |
|---------|------|---------|
| `MessageChatMemoryAdvisor` | 自动从 MongoDB 加载历史消息，注入到 Prompt 上下文 | 请求前加入历史 |
| `MyLoggerAdvisor` | 日志记录：请求前打印 prompt，流式结束后聚合打印完整响应 | 聚合后输出（避免日志刷屏） |
| `ConversationGraphAdvisor` | 将对话同步到 Neo4j 图数据库 | 可选，Neo4j 不可用时自动降级 |

**MyLoggerAdvisor 的关键设计**（对流式日志的特殊处理）：

```java
// MyLoggerAdvisor.java (第 47-52 行)
@Override
public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
    request = before(request);  // 打印请求日志
    Flux<ChatClientResponse> flux = chain.nextStream(request);
    // ⭐ 关键：用 ChatClientMessageAggregator 聚合所有 token 后，再打印完整响应
    return new ChatClientMessageAggregator()
            .aggregateChatClientResponse(flux, this::observeAfter);
    // 如果不聚合，每个 token 都会触发一次日志 → 日志爆炸
}
```

---

## 4. 路径二：SseEmitter + CompletableFuture（智能体推理）

> 这是本项目真正的"异步解耦大模型推理任务"核心实现，位于 `BaseAgent.runStream()`。

### 4.1 类继承体系

```
BaseAgent (抽象类)                    ← runStream() 定义在这里
  ├── 状态管理: IDLE → RUNNING → FINISHED / ERROR
  ├── 消息上下文: List<Message> messageList
  ├── 执行循环: for (step 1..maxSteps)
  └── runStream(): SseEmitter + CompletableFuture
       │
       └── ReActAgent (抽象类)        ← Think-Act 模式
             └── step() = think() + act()
                  │
                  └── ToolCallAgent   ← 具体实现
                        ├── think(): 调用 LLM 决定用哪些工具
                        └── act():   执行工具调用，处理结果
                             │
                             └── YuManus  ← 最终智能体实例
```

### 4.2 runStream() 核心实现

```java
// BaseAgent.java (第 100-177 行)
public SseEmitter runStream(String userPrompt) {
    // ════════════════════════════════════════════════════════
    // 第 1 层：创建 SseEmitter（Spring MVC 异步端点）
    // ════════════════════════════════════════════════════════
    SseEmitter sseEmitter = new SseEmitter(300000L); // 5 分钟超时
    //   ↑ SseEmitter 是 Spring MVC 提供的 SSE 工具类
    //   调用 sseEmitter.send(data) → 自动序列化为 SSE 格式 → 推送给前端

    // ════════════════════════════════════════════════════════
    // 第 2 层：CompletableFuture 异步解耦
    // ════════════════════════════════════════════════════════
    CompletableFuture.runAsync(() -> {
        // ┌─────────────────────────────────────────────────┐
        // │  这个 lambda 跑在 ForkJoinPool.commonPool() 中   │
        // │  主线程立即返回 sseEmitter，不阻塞 Tomcat 线程    │
        // └─────────────────────────────────────────────────┘

        // ① 基础校验（通过 SSE 发送错误信息）
        try {
            if (this.state != AgentState.IDLE) {
                sseEmitter.send("错误：无法从状态运行代理：" + this.state);
                sseEmitter.complete();
                return;
            }
            if (StrUtil.isBlank(userPrompt)) {
                sseEmitter.send("错误：不能使用空提示词运行代理");
                sseEmitter.complete();
                return;
            }
        } catch (Exception e) {
            sseEmitter.completeWithError(e);
        }

        // ② 状态转换：IDLE → RUNNING
        this.state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));
        List<String> results = new ArrayList<>();

        try {
            // ═══════════════════════════════════════════════
            // 第 3 层：ReAct 执行循环（Think → Act）
            // ═══════════════════════════════════════════════
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);

                // 单步执行：think() + act()
                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);

                // ⭐ 关键：每一步的结果实时推送给前端
                sseEmitter.send(result);
            }

            // 达到最大步数限制
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                sseEmitter.send("执行结束：达到最大步骤（" + maxSteps + "）");
            }

            // 正常完成
            sseEmitter.complete();  // → 前端 EventSource 收到 close 事件

        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            try {
                sseEmitter.send("执行错误：" + e.getMessage());
                sseEmitter.complete();
            } catch (IOException ex) {
                sseEmitter.completeWithError(ex);
            }
        } finally {
            this.cleanup();  // 子类可重写，清理资源
        }
    });  // ← CompletableFuture.runAsync 结束

    // ════════════════════════════════════════════════════════
    // 第 4 层：生命周期回调
    // ════════════════════════════════════════════════════════
    sseEmitter.onTimeout(() -> {
        this.state = AgentState.ERROR;
        this.cleanup();
        log.warn("SSE connection timeout");
    });

    sseEmitter.onCompletion(() -> {
        if (this.state == AgentState.RUNNING) {
            this.state = AgentState.FINISHED;
        }
        this.cleanup();
        log.info("SSE connection completed");
    });

    return sseEmitter;  // ← 立即返回，不等待 Agent 执行完毕
}
```

### 4.3 Think-Act 循环详解

每个 `step()` 包含两个阶段：

```
step() {
    // --- Think 阶段 ---
    boolean shouldAct = think();
    //  1. 将下一步提示词加入消息列表
    //  2. 调用 chatClient.prompt().toolCallbacks(allTools).call()
    //  3. LLM 返回：决定调用哪些工具（或不调用）
    //  4. 日志输出：选择了 N 个工具：工具名 + 参数
    //  5. return true=需要执行工具 / false=不需要

    if (!shouldAct) return "思考完成 - 无需行动";

    // --- Act 阶段 ---
    return act();
    //  1. toolCallingManager.executeToolCalls() 执行工具
    //  2. 将工具返回结果加入消息列表
    //  3. 判断是否调用了 terminate 工具 → 设状态为 FINISHED
    //  4. 返回工具执行结果摘要
}
```

**具体例子——用户问"帮我搜索 Spring AI 最新文档"**：

```
Step 1: think() → LLM 决定调用 searchWeb 工具 → shouldAct=true
         act() → 执行搜索 → 返回搜索结果
         sseEmitter.send("Step 1: 工具 searchWeb 返回: Spring AI 1.0.0 发布...")

Step 2: think() → LLM 综合分析，给出回答 → shouldAct=false
         sseEmitter.send("Step 2: 思考完成 - 无需行动")

         → 循环结束
         → sseEmitter.complete()
```

### 4.4 线程模型

```
Tomcat 请求线程 (http-nio-8123-exec-1)
    │
    ├── 进入 Controller
    ├── 调用 agent.runStream(userPrompt)
    │       │
    │       ├── new SseEmitter(300000L)          ← 创建 SSE 发射器
    │       ├── CompletableFuture.runAsync(...)   ← 提交异步任务
    │       └── return sseEmitter                ← 立即返回
    │
    └── Spring MVC 持有 sseEmitter，Tomcat 线程释放
         │
         ▼
ForkJoinPool.commonPool() 工作线程 (ForkJoinPool.commonPool-worker-1)
    │
    ├── for step 1..20:
    │   ├── think() → 调用 LLM API（阻塞等待，但这是工作线程）
    │   ├── act()   → 执行工具调用
    │   └── sseEmitter.send(result)  → 写入响应缓冲区
    │
    └── sseEmitter.complete()  → 关闭连接

前端 EventSource
    │  每收到 send() → 触发 onmessage → 追加到聊天界面
    └── 收到 complete() → 触发 onclose（或 [DONE] 信号）
```

**关键收益**：
```
没有异步解耦：
  用户等待 = Agent完整执行时间（可能 30秒~数分钟）
  浏览器一直转圈，不知道 AI 在干什么

有异步解耦 + 实时推送：
  用户等待体感 = 第一个 step 出现的时间（通常 2~5 秒）
  能看到 AI 一步步"思考" → 搜索 → 分析 → 总结
  等待体验从"黑盒焦虑"变成"透明可控"
```

---

## 5. 前端 EventSource 消费

### 5.1 API 封装

```javascript
// api/index.js (第 42-47 行)
export const connectSSE = (url, params) => {
  const queryString = Object.keys(params)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
  return new EventSource(`${API_BASE_URL}${url}?${queryString}`)
}
```

### 5.2 ChatView 消费流式数据

```javascript
// ChatView.vue (第 314-347 行)
const send = () => {
  // ... 发送前准备 ...

  // ① 创建 SSE 连接
  eventSource = chat(text, chatId.value)  // → new EventSource('/api/ai/chat?...')

  // ② 接收流式消息（每个 token / step 触发一次）
  eventSource.onmessage = (e) => {
    if (e.data && e.data !== '[DONE]') {
      if (aiIdx === -1) {
        // 第一个 token → 创建新的 AI 消息气泡
        messages.value.push({ content: '', isUser: false, time: Date.now() })
        aiIdx = messages.value.length - 1
      }
      // 追加内容（打字机效果）
      messages.value[aiIdx].content += e.data
      scrollDown()  // 自动滚动到底部
    }
    if (e.data === '[DONE]') {
      // 流结束
      connecting.value = false
      eventSource.close()
    }
  }

  // ③ 错误处理
  eventSource.onerror = () => {
    connecting.value = false
    eventSource.close()
    messages.value[aiIdx].content = '(连接中断，请重试)'
  }
}
```

**流式体验的关键细节**：

```
时间线（以 "打字机效果" Fluxe 模式为例）:

t=0ms   用户按 Enter → 立刻看到自己的消息气泡
t=100ms 前端侧出现 "AI 正在输入..." 动画（三点跳动）
t=800ms 第一个 token 到达 → 输入动画消失 → AI 气泡出现，内容="Java"
t=830ms 第二个 token → 内容="Java的"
t=860ms 第三个 token → 内容="Java的synchronized"
...
t=15s   最后一个 token → 内容完整显示
        [DONE] 信号到达 → 连接关闭

用户体感：像在看真人打字，而非等待一个"黑盒"响应
```

---

## 6. 完整调用链路时序

### 路径一（Flux 统一对话）完整时序：

```
时间 │ 前端(ChatView)      │ Controller          │ QuizApp               │ Spring AI            │ DeepSeek API
─────┼────────────────────┼────────────────────┼──────────────────────┼─────────────────────┼──────────────
  0ms│ send()              │                     │                       │                     │
     │ new EventSource(...)│                     │                       │                     │
     │────────────────────>│ GET /api/ai/chat    │                       │                     │
     │                     │ doChat(msg, chatId) │                       │                     │
     │                     │────────────────────>│ doUnifiedChat()       │                     │
     │                     │                     │                       │                     │
 50ms│                     │                     │ 1. 查询重写            │                     │
     │                     │                     │ 2. RAG 向量检索        │                     │
     │                     │                     │ 3. 联网搜索(可选)      │                     │
     │                     │                     │ 4. 拼接上下文          │                     │
     │                     │                     │                       │                     │
100ms│                     │                     │ .stream().content()   │                     │
     │                     │                     │──────────────────────>│ POST chat/completions│
     │                     │                     │                       │ stream=true         │
     │                     │                     │                       │─────────────────────>│
     │                     │                     │                       │                     │
800ms│   ◄── SSE chunk ─────────────────────────────────────────── token1 ──────────────────│
     │  msg += "Java"     │                     │                       │                     │
830ms│   ◄── SSE chunk ─────────────────────────────────────────── token2 ──────────────────│
     │  msg += "的"       │                     │                       │                     │
 ... │       ...          │                     │                       │       ...           │
 10s │   ◄── SSE chunk ─────────────────────────────────────────── token-N ─────────────────│
     │   ◄── [DONE]       │                     │                       │                     │
     │  eventSource.close │                     │                       │                     │
```

### 路径二（SseEmitter Agent）完整时序：

```
时间 │ 前端               │ Controller        │ Tomcat线程            │ ForkJoinPool线程       │ LLM API
─────┼───────────────────┼──────────────────┼─────────────────────┼──────────────────────┼──────────
  0ms│ send()             │                   │                      │                       │
     │───────────────────>│ GET /ai/agent/run │                      │                       │
     │                    │ runStream(msg)    │                      │                       │
     │                    │───────────────>   │ new SseEmitter(5min) │                       │
     │                    │                   │ CompletableFuture ──>│                       │
     │                    │                   │ .runAsync(task)      │                       │
     │                    │ return sseEmitter │  (线程释放)           │                       │
     │ ◄── SSE 连接建立 ──────────────────────│                      │                       │
     │                    │                   │                      │                       │
 2s  │                    │                   │                      │ Step1: think()        │
     │                    │                   │                      │──────────────────────>│
     │                    │                   │                      │    ◄── 工具调用建议 ──│
     │                    │                   │                      │ Step1: act()          │
     │                    │                   │                      │   执行工具...          │
     │ ◄── SSE ─────────────────────────────────────────── "Step 1: searchWeb 返回..." ─────│
     │                    │                   │                      │                       │
 5s  │                    │                   │                      │ Step2: think()        │
     │                    │                   │                      │──────────────────────>│
     │                    │                   │                      │    ◄── 综合回答 ───────│
     │                    │                   │                      │ shouldAct = false     │
     │ ◄── SSE ─────────────────────────────────────────── "Step 2: 思考完成 - 无需行动" ───│
     │                    │                   │                      │                       │
     │ ◄── SSE complete ───────────────────────────────── sseEmitter.complete() ───────────│
     │ eventSource.close │                   │                      │  cleanup()            │
```

---

## 7. 关键设计决策与优化

### 7.1 为什么有两条 SSE 路径？

| 考量 | Flux\<String\> | SseEmitter + CompletableFuture |
|------|---------------|-------------------------------|
| **适合** | LLM 直接回复（一问一答） | Agent 多步推理（思考→工具→再思考） |
| **粒度** | Token 级（细粒度，打字机效果） | Step 级（粗粒度，过程可见） |
| **实现复杂度** | 低（Spring AI 内置，一行 `.stream().content()`） | 中（手动管理线程、连接、异常） |
| **前端适配** | 相同（都是 EventSource SSE） | 相同 |

**当前项目主力是路径一**（`/ai/chat` 接口使用 `Flux<String>`），路径二（`BaseAgent.runStream()`）是预留给智能体推理场景的高级能力。

### 7.2 异步解耦的价值

```
┌─────────────────────────────────────────────────────────┐
│              没有 CompletableFuture（同步阻塞）            │
│                                                          │
│  Tomcat 线程 ────────────────────────────────────────    │
│  (被占用 30s)  │ think() 10s │ act() 5s │ think() 10s.. │
│                                                          │
│  问题：Tomcat 线程池被耗尽 → 其他请求排队 → 服务不可用      │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│            有 CompletableFuture（异步解耦）                │
│                                                          │
│  Tomcat 线程 ──→ 提交任务 → 立即释放                       │
│  (占用 <1ms)                                              │
│                                                          │
│  工作线程 ────────────────────────────────────────────    │
│  (ForkJoinPool)│ think() 10s │ act() 5s │ think() 10s.. │
│                                                          │
│  收益：Tomcat 线程不阻塞 → 高并发能力 → 服务稳定            │
└─────────────────────────────────────────────────────────┘
```

### 7.3 超时与容错

| 机制 | 位置 | 策略 |
|------|------|------|
| SseEmitter 超时 | `BaseAgent.runStream()` | `new SseEmitter(300000L)` → 5 分钟后触发 `onTimeout` → 清理状态 |
| 查询重写降级 | `QuizApp.doUnifiedChat()` | `try-catch` → 失败用原始消息 |
| RAG 检索降级 | `QuizApp.buildContext()` | `try-catch` → 检索失败返回空上下文 |
| 联网搜索降级 | `QuizApp.buildContext()` | `try-catch` → 搜索失败跳过 |
| 前端断连处理 | `ChatView.vue` | `onerror` → 显示"(连接中断，请重试)" |

### 7.4 优化用户等待体感的核心技巧

```
❌ 传统做法（黑盒等待）：
   用户发送 → [转圈 30 秒...] → 完整回复出现
   用户焦虑：AI 卡住了？在干嘛？我该不该刷新？

✅ 本项目的做法（流式透明）：
   用户发送 → [2秒] → "Java" → "的" → "synchronized" → ...
   → 看到内容在"生长"，知道 AI 在工作
   → 不等完整回复就能开始阅读
   → 如果方向不对，可以提前中断
```

---

## 8. 文件索引

| 文件 | 角色 |
|------|------|
| `src/main/java/.../controller/AiController.java` | SSE 接口入口，返回 `Flux<String>` |
| `src/main/java/.../app/QuizApp.java` | 核心业务：查询重写、RAG、搜索、流式调用 |
| `src/main/java/.../agent/BaseAgent.java` | `runStream()`：SseEmitter + CompletableFuture 核心实现 |
| `src/main/java/.../agent/ReActAgent.java` | Think-Act 模式抽象：`step() = think() + act()` |
| `src/main/java/.../agent/ToolCallAgent.java` | `think()` 调用 LLM 选工具，`act()` 执行工具 |
| `src/main/java/.../agent/YuManus.java` | 具体智能体实例 |
| `src/main/java/.../agent/model/AgentState.java` | 状态机：IDLE → RUNNING → FINISHED / ERROR |
| `src/main/java/.../advisor/MyLoggerAdvisor.java` | 流式日志聚合（避免 token 级别日志刷屏） |
| `qian-ai-agent-frontend/src/api/index.js` | 前端 SSE 连接封装 `EventSource` |
| `qian-ai-agent-frontend/src/views/ChatView.vue` | 前端消费流式数据，打字机效果渲染 |
| `src/main/resources/application.yml` | 模型配置（DeepSeek/DashScope） |
