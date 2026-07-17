# Spring AI @Tool 工具系统架构详解

## 一、整体概览

项目基于 Spring AI 1.0 的 `@Tool` 注解体系，封装了**联网检索、网页抓取、文件读写、资源下载、终端操作、PDF 生成、任务终止**共 7 类工具，采用**单例 Bean + 集中注册**模式统一管理，通过 `ToolCallbacks.from()` 将多个工具对象聚合为 `ToolCallback[]` 数组，供 ChatClient 和自主规划 Agent 使用。

```
┌──────────────────────────────────────────────────────────────────┐
│                        ToolRegistration                         │
│                     (@Configuration @Bean)                       │
│                   单例创建所有工具 + 集中注册                       │
└──────────────┬───────────────────────────────────────────────────┘
               │ ToolCallback[] allTools
               ▼
    ┌──────────────────────┬──────────────────────┐
    │   ChatClient 直调     │   YuManus Agent      │
    │ doChatWithTools()    │  (ReAct 自主规划)      │
    │ .toolCallbacks()     │  think() → act()     │
    └──────────────────────┴──────────────────────┘
               │                      │
               ▼                      ▼
         AI 大模型决策            ToolCallingManager
         调用哪些工具              执行 + 结果回传
```

**源码入口一览：**

| 文件 | 职责 |
|---|---|
| `tools/ToolRegistration.java` | 工具注册中心（单例工厂） |
| `tools/WebSearchTool.java` | Tavily 联网搜索 |
| `tools/WebScrapingTool.java` | Jsoup 网页内容抓取 |
| `tools/PDFGenerationTool.java` | iText PDF 文档导出 |
| `tools/ResourceDownloadTool.java` | HTTP 资源下载 |
| `tools/FileOperationTool.java` | 本地文件读写 |
| `tools/TerminalOperationTool.java` | 终端命令执行 |
| `tools/TerminateTool.java` | 终止 Agent 执行 |
| `agent/ToolCallAgent.java` | ReAct Agent — think/act 循环 |
| `agent/YuManus.java` | 超级智能体（ToolCallAgent 子类） |
| `app/QuizApp.java` | ChatClient 直调工具入口 |

---

## 二、@Tool 注解的工作原理

### 2.1 注解定义

Spring AI 1.0 提供两个核心注解：

- **`@Tool`** — 标记一个方法为可被 AI 调用的工具，必须包含 `description` 属性。AI 模型根据 description 决定何时调用该工具。
- **`@ToolParam`** — 标记方法参数，必须包含 `description` 告诉 AI 这个参数的含义。可选属性 `required` 控制是否必填。

```java
// 典型用法：WebSearchTool.java:29-31
@Tool(description = "Search the web for real-time information using Tavily. " +
    "Use this when you need up-to-date information or facts beyond your knowledge cutoff.")
public String searchWeb(
        @ToolParam(description = "Search query keyword or natural language question") String query) {
    // ...
}
```

### 2.2 运行时机制

`@Tool` 注解本身**不自动生效**。Spring AI 需要通过 `ToolCallbacks.from(object)` 将对象中的 `@Tool` 方法提取为 `ToolCallback` 接口实例。流程如下：

```
@Tool 方法                                              AI 可识别的
(普通 Java 对象)  ──→ ToolCallbacks.from() ──→ ToolCallback[]
                                                        │
                                                        ▼
                                              ChatClient.toolCallbacks()
                                              或 ToolCallingManager
                                                        │
                                                        ▼
                                              AI 模型收到函数定义 JSON
                                              (name + description + parameters)
```

**关键：** `@Tool(description=...)` 的文本直接影响 AI 是否选择调用该工具。description 需要清晰说明"什么情况下应该用这个工具"，这本质是 Prompt Engineering。

### 2.3 description 编写最佳实践

| 要素 | 示例 | 说明 |
|---|---|---|
| 工具用途 | "Search the web for real-time information" | 一句话说清干什么 |
| 使用场景 | "Use this when you need up-to-date information" | 什么时候该用（触发条件） |
| 能力边界 | "Terminate when the request is met OR cannot proceed" | 什么时候不该用 / 何时停 |

---

## 三、工具类逐个详解

### 3.1 WebSearchTool — 联网检索

**来源：** `tools/WebSearchTool.java`

**底层 API：** [Tavily](https://tavily.com) — 专为 AI Agent 设计的搜索引擎，返回结果直接适配 LLM 消费。

**技术要点：**
- 接收 API Key 构造注入（不由 `@Value` 直接注入字段，保持可测试性）
- 发送 POST JSON 请求，参数控制 `search_depth`、`max_results`、`include_answer`
- Tavily 的 `answer` 字段是 AI 生成的摘要（区别于传统搜索的 snippet），优先输出
- 返回格式：`Answer: ...\nSearch Results:\n1. title\nURL: ...\ncontent...`

```java
// 构造时注入 API Key，而非依赖 @Value 字段注入
// WebSearchTool.java:25-27
public WebSearchTool(String apiKey) {
    this.apiKey = apiKey;
}
```

**设计考量：** 构造注入使得该工具可独立于 Spring 容器进行单元测试。ToolRegistration 从 `@Value` 读取配置再传入，实现配置与逻辑解耦。

### 3.2 WebScrapingTool — 网页内容抓取

**来源：** `tools/WebScrapingTool.java`

**底层库：** Jsoup — Java HTML 解析器。

**极简设计：** 仅 22 行代码。接收 URL，返回完整 HTML。无状态、无依赖，不需要构造注入。比 WebSearchTool 更简单，因为它不需要外部 API Key。

```java
// WebScrapingTool.java:13-21
@Tool(description = "Scrape the content of a web page")
public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
    Document document = Jsoup.connect(url).get();
    return document.html();
}
```

### 3.3 PDFGenerationTool — PDF 文档导出

**来源：** `tools/PDFGenerationTool.java`

**底层库：** iText 7 (Community Edition) — Java PDF 生成标准库。

**技术要点：**
- `returnDirect = false` — 结果不直接返回给用户，而是由 AI 解释后返回
- 中文字体处理：使用内置 `STSongStd-Light` + `UniGB-UCS2-H` 编码，无需额外下载字体文件
- 文件输出到 `{user.dir}/tmp/pdf/` 目录
- try-with-resources 确保 PdfWriter/PdfDocument/Document 正确关闭

```java
// PDFGenerationTool.java:40
PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
```

### 3.4 ResourceDownloadTool — 资源下载

**来源：** `tools/ResourceDownloadTool.java`

**底层库：** Hutool `HttpUtil.downloadFile()`。

**设计：** 两个参数 — `url`（资源地址）和 `fileName`（保存文件名）。文件保存到 `{user.dir}/tmp/download/`。`@ToolParam` 的 description 让 AI 懂得何时填这两个参数。

### 3.5 FileOperationTool — 文件读写

**来源：** `tools/FileOperationTool.java`

**两个 @Tool 方法：** `readFile` 和 `writeFile`，底层用 Hutool `FileUtil`。

**设计要点：** 这是项目中唯一一个类同时暴露多个 `@Tool` 方法的例子。Spring AI 会分别提取为独立的 `ToolCallback`，每个方法对应一个 AI 可调用的 function。

### 3.6 TerminalOperationTool — 终端命令执行

**来源：** `tools/TerminalOperationTool.java`

**当前状态：** ⚠️ 教学项目中的**安全加固练习**。白名单检查逻辑留空（引导学习者实现）。

**安全原则：**
- 采用"默认拒绝"策略：只有白名单中的命令才允许执行
- 白名单设计：用 `Set<String>` 存储允许的命令名（python、dir、echo、type、findstr、mkdir）
- 命令名提取：`command.split(" ")[0]` 取第一个 token
- 黑名单的缺陷：攻击者总能想出新命令，无法穷举

### 3.7 TerminateTool — 终止执行

**来源：** `tools/TerminateTool.java`

**唯一作用：** 让 AI Agent 在任务完成或无法继续时主动结束循环。ToolCallAgent.act() 中检测到此工具被调用则设置 `AgentState.FINISHED`。

---

## 四、单例统一注册机制

### 4.1 ToolRegistration 设计

**来源：** `tools/ToolRegistration.java`

```java
@Configuration                    // Spring 配置类
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;   // 从配置文件读取 API Key

    @Bean                          // 声明为 Spring Bean
    public ToolCallback[] allTools() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TerminateTool terminateTool = new TerminateTool();
        return ToolCallbacks.from(    // 核心：将多个对象转换为 ToolCallback[]
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool,
                terminateTool
        );
    }
}
```

### 4.2 设计要点分析

**1. 为什么用 `@Configuration` + `@Bean` 而不是 `@Component`？**

- `@Configuration` 表示这是一个**工厂类**而非业务类，语义更清晰
- `ToolCallback[]` 是 Spring AI 框架类型，不能直接加 `@Component`
- 集中管理工具依赖（如 `searchApiKey`），一处注入、到处使用
- **单例保证：** `@Bean` 方法默认单例，`allTools()` 只执行一次，整个应用共享同一批工具实例

**2. `ToolCallbacks.from()` 内部做了什么？**

Spring AI 扫描每个传入对象的 public 方法，找到带 `@Tool` 注解的方法，为每个方法创建 `DefaultToolCallback` 实例。每个 callback 包含：
- 方法引用（Method handle）
- 工具名称（默认取方法名，或 `@Tool(name=...)` 覆盖）
- 工具描述（`@Tool(description=...)`）
- 参数 Schema（从 `@ToolParam` 提取，转换为 JSON Schema）

**3. 为什么每个工具 new 一个实例？**

虽然这些工具大多是无状态的（除了 WebSearchTool 持有 apiKey），但各自独立创建遵循了**单一职责原则**和**开闭原则**：新增工具只需新增一行 `new XxxTool()` + 加入 `ToolCallbacks.from()`，不会影响现有工具。

---

## 五、ToolContext 与用户身份透传

### 5.1 当前状态

项目中**尚未使用** Spring AI 的 `ToolContext` API。`ToolContext` 是 Spring AI 1.0 提供的一种机制，允许在工具执行时传递调用上下文（如用户 ID、会话 ID、租户信息等），而无需修改每个工具的方法签名。

### 5.2 设计意图与规划

你提到的"通过 ToolContext 透传用户身份上下文"是典型的**多租户安全场景**：

```
用户请求 → Controller(chatId) → Agent → AI 决策调用工具
                                           │
                                           ▼
                                    ToolContext 携带:
                                    - userId / chatId
                                    - 权限范围（允许执行哪些操作）
                                    - 请求追踪 ID
```

### 5.3 推荐实现方式

当后续实现该能力时，有两种路径：

**路径 A：Spring AI ToolContext（推荐）**

```java
// 工具方法签名增加 ToolContext 参数
@Tool(description = "...")
public String searchWeb(
    @ToolParam(description = "Search query") String query,
    ToolContext context           // Spring AI 自动注入
) {
    String chatId = context.getContext().get("chatId");
    // 根据 chatId 做权限控制、日志追踪等
}
```

ToolContext 在调用链中的传递：
- `ChatClient.prompt().toolContext(Map.of("chatId", "..."))` 设置
- Spring AI 框架自动透传到被调用的工具

**路径 B：RequestContextHolder（当前可行）**

```java
// 不修改工具签名，从 Servlet 请求上下文获取
HttpServletRequest request = 
    ((ServletRequestAttributes) RequestContextHolder
        .currentRequestAttributes()).getRequest();
String chatId = request.getParameter("chatId");
```

| 方案 | 侵入性 | 解耦度 | 适合场景 |
|---|---|---|---|
| ToolContext | 低（加一个参数） | 高 | 长期演进，多工具需统一上下文 |
| RequestContextHolder | 无 | 低 | 快速实现，工具仅 Web 调用 |

---

## 六、工具调用的两条路径

项目中有**两种使用工具的方式**，适用于不同复杂度场景：

### 6.1 路径一：ChatClient 直调（简单场景）

**入口：** `QuizApp.doChatWithTools()` → `QuizApp.doUnifiedChat()`

```java
// QuizApp.java:307-318
public String doChatWithTools(String message, String chatId) {
    ChatResponse chatResponse = chatClient
            .prompt()
            .user(message)
            .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
            .advisors(new MyLoggerAdvisor())
            .toolCallbacks(allTools)   // 注入工具列表
            .call()
            .chatResponse();
    return chatResponse.getResult().getOutput().getText();
}
```

**流程：**
```
用户消息 → ChatClient.prompt()
   ├─ Spring AI 自动构建 function calling 请求
   ├─ AI 模型判断是否需要调用工具
   ├─ 如需调用 → 框架自动执行 → 结果回填 prompt
   └─ 最终返回文本回复
```

**特点：**
- Spring AI 框架**内部自动管理**工具调用的完整循环
- 开发者只需 `.toolCallbacks(allTools)` 一行
- 适合：单轮对话、少量工具调用、不需要复杂决策逻辑

### 6.2 路径二：ReAct Agent 自主规划（复杂场景）

**入口：** `YuManus` → `ToolCallAgent` → `ReActAgent` → `BaseAgent`

这是项目的**核心亮点**——手写实现了一个 ReAct（Reasoning + Acting）智能体循环。

```
┌──────────────────────────────────────────┐
│              ReActAgent.step()            │
│                                           │
│   ┌──────────┐     ┌──────────┐          │
│   │ think()  │ ──→ │  act()   │          │
│   │ 调用 LLM  │     │ 执行工具  │          │
│   │ 决定工具  │     │ 记录结果  │          │
│   └──────────┘     └──────────┘          │
│        ↑                 │               │
│        │                 ↓               │
│        └──── 结果回填 ────┘               │
│                                           │
│   maxSteps=20, 直到 FINISHED 或超限       │
└──────────────────────────────────────────┘
```

**think() 核心逻辑：** `ToolCallAgent.java:74-127`

```java
public boolean think() {
    // 1. 拼接用户提示词到消息上下文
    if (StrUtil.isNotBlank(getNextStepPrompt())) {
        getMessageList().add(new UserMessage(getNextStepPrompt()));
    }
    // 2. 构建 Prompt → 调用 LLM → 获取 toolCalls
    ChatResponse chatResponse = getChatClient().prompt(prompt)
            .system(getSystemPrompt())
            .toolCallbacks(availableTools)  // ← 工具在此注入
            .call()
            .chatResponse();
    // 3. 解析响应：有没有工具要调用？
    List<ToolCall> toolCallList = assistantMessage.getToolCalls();
    if (toolCallList.isEmpty()) {
        return false;  // 不需要调用工具，循环结束
    } else {
        return true;   // 需要调用工具，进入 act()
    }
}
```

**act() 核心逻辑：** `ToolCallAgent.java:135-157`

```java
public String act() {
    // 1. 通过 ToolCallingManager 批量执行工具
    ToolExecutionResult toolExecutionResult = 
        toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
    // 2. 更新消息上下文（含 AI 的工具调用请求 + 工具返回结果）
    setMessageList(toolExecutionResult.conversationHistory());
    // 3. 判断是否调用了 TerminateTool → 设置 FINISHED 状态
    if (terminateToolCalled) {
        setState(AgentState.FINISHED);
    }
    return results;
}
```

**关键设计：**
- **禁用 Spring AI 内置工具循环：** `withInternalToolExecutionEnabled(false)` → 自己控制 think/act 节奏
- **自主维护消息上下文：** `messageList` 贯穿多步循环，每步追加 tool call 和 tool result
- **步数限制：** `maxSteps=20`，防止无限循环耗尽 token
- **多模型支持：** YuManus 同时支持 DashScope（便宜）和 DeepSeek（效果好），通过 `ChatOptions` 切换

### 6.3 两条路径对比

| 维度 | ChatClient 直调 | ReAct Agent |
|---|---|---|
| 控制粒度 | 框架自动 | 每步可控 |
| 多步推理 | 支持但不可控 | 完全可控（maxSteps） |
| 消息上下文 | 框架管理 | 自己维护 messageList |
| 终止条件 | AI 自然结束 | TerminateTool + 步数上限 |
| 流式输出 | 原生支持 | BaseAgent.runStream() 手动 SSE |
| 适用场景 | 单轮问答、RAG | 多步复杂任务自主规划 |
| 代码量 | ~20 行 | ~300 行（含基类） |

---

## 七、消息上下文管理与 Agent 状态机

### 7.1 消息类型流转

ReAct Agent 在每个 `step()` 中维护完整的对话历史：

```
[SystemMessage]         系统提示词（面试官人设 / Agent 能力声明）
[UserMessage]           用户原始问题 + nextStepPrompt
[AssistantMessage]      AI 返回的 toolCalls（包含工具名 + 参数 JSON）
[ToolResponseMessage]   工具执行结果（每个工具调用一条）
[AssistantMessage]      新一轮 think → 又有 toolCalls 或最终文本回复
...
```

### 7.2 Agent 状态机

**来源：** `agent/model/AgentState.java`

```
IDLE ──→ RUNNING ──→ FINISHED（正常完成或 TerminateTool 触发）
  │                    │
  └──→ ERROR（异常）   └──→ 超 maxSteps → FINISHED
```

关键转换点：
- `BaseAgent.run()` 中 `state != IDLE` 直接抛异常，防止重入
- `ToolCallAgent.act()` 检测 TerminateTool 调用后置 `FINISHED`
- `finally` 块中 `cleanup()` 保证资源释放

---

## 八、工具系统的整体数据流

以一次完整的 Agent 调用为例（用户问："搜索 Java 21 新特性并生成 PDF 报告"）：

```
1. Controller 接收请求
   AiController.doChat("搜索 Java 21 新特性并生成 PDF 报告", "chat_123")

2. QuizApp 构建上下文
   - RAG 检索本地知识库（可能没有 Java 21 内容）
   - needWebSearch() 检测"搜索"关键词 → 调用 WebSearchTool

3. 上下文 + 原始问题 → YuManus.run()

4. Step 1: think()
   AI 收到：系统提示词 + 用户问题 + 可用工具列表
   AI 决策：需要先调用 WebSearchTool("Java 21 新特性")
   → 返回 AssistantMessage[toolCalls=[{name:"searchWeb", args:{query:"Java 21 新特性"}}]]
   → shouldAct = true

5. Step 1: act()
   ToolCallingManager 执行 searchWeb("Java 21 新特性")
   → 返回 Tavily 搜索结果
   → 结果回填 messageList

6. Step 2: think()
   AI 收到搜索结果 + 上下文
   AI 决策：已获取足够信息，调用 PDFGenerationTool 生成报告
   → toolCalls=[{name:"generatePDF", args:{fileName:"java21-report.pdf", content:"..."}}]

7. Step 2: act()
   执行 PDF 生成 → 成功 → 结果回填

8. Step 3: think()
   AI 判断任务完成 → 调用 doTerminate()

9. Step 3: act()
   检测到 doTerminate → state = FINISHED → 循环退出

10. 结果通过 SSE 流式返回前端
```

---

## 九、配置与扩展点

### 9.1 搜索 API 配置

```yaml
# application.yml:99-100
search-api:
  api-key: ${TAVILY_API_KEY:your-tavily-api-key}
```

通过环境变量注入，避免将密钥硬编码到配置文件。

### 9.2 模型切换

```yaml
# 当前使用 DeepSeek（OpenAI 兼容接口）
spring.ai.openai:
  api-key: ${SPRING_AI_OPENAI_API_KEY:...}
  base-url: https://api.deepseek.com

# 备选：阿里云 DashScope（百炼）
spring.ai.dashscope:
  api-key: ${SPRING_AI_DASHSCOPE_API_KEY:...}
```

### 9.3 新增工具步骤

1. 创建工具类，用 `@Tool` + `@ToolParam` 标记方法和参数
2. 在 `ToolRegistration.allTools()` 中 `new` 并加入 `ToolCallbacks.from()`
3. 如需 API Key 等配置，通过构造注入，由 ToolRegistration 读取 `@Value` 传入
4. Agent/YuManus 自动获得该工具（因为 `ToolCallback[]` 是共享 Bean）

---

## 十、总结

| 设计维度 | 实现方式 |
|---|---|
| 工具定义 | Spring AI `@Tool` + `@ToolParam` 注解 |
| 工具注册 | 单例模式：`@Configuration` + `@Bean` 集中管理 |
| 工具发现 | `ToolCallbacks.from(objects...)` 反射提取 |
| 身份透传 | 规划中使用 `ToolContext`，当前通过 `chatId` 参数传递 |
| 简单调用 | `ChatClient.toolCallbacks(allTools)` 框架自动循环 |
| 自主规划 | ReAct Agent（think → act 循环，手动控制每一步） |
| 消息上下文 | 手动维护 `List<Message>`，每步追加 |
| 循环终止 | `maxSteps` 上限 + `TerminateTool` + `AgentState.FINISHED` |
| 配置解耦 | API Key 等通过构造注入 + `@Value` 读取，与工具类分离 |
