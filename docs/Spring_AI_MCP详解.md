# Spring AI MCP 面试详解

> 基于 Spring AI 1.1.0 官方文档，侧重面试高频考点

---

## 一、基础概念（面试必问）

### Q1: 什么是 MCP？

**MCP（Model Context Protocol，模型上下文协议）** 是 Anthropic 推出的开放标准协议，定义了大模型与外部工具/数据源之间的**标准化交互方式**。

一句话：**MCP 就是 AI 世界的 USB-C 接口**——不同厂商的工具和模型，遵循同一协议就能"即插即用"。

### Q2: MCP 解决了什么问题？

| 痛点 | MCP 方案 |
|------|----------|
| **数据孤岛** | 模型通过统一协议访问数据库、API、文件系统等外部数据 |
| **重复开发** | 写一次 MCP Server，所有兼容模型都能调用（不再为每个模型写 Function Calling） |
| **生态碎片化** | 标准协议让不同厂商的工具/模型互操作 |

### Q3: MCP 和传统 Function Calling 的区别？

| 维度 | Function Calling | MCP |
|------|------------------|-----|
| 耦合度 | 工具代码嵌在 AI 应用中 | 工具独立部署为 Server |
| 语言限制 | 只能同语言（如 Java） | 任何语言，通过 JSON-RPC 通信 |
| 复用性 | 一个应用一套工具 | 一个 Server 多个应用共享 |
| 扩展性 | 改主应用代码 | 加新 Server，主应用不改 |
| 标准化 | 各厂商定义不同 | 统一协议规范 |

---

## 二、三层架构（面试高频）

Spring AI MCP 遵循 **三层架构**，这是区别于其他 MCP SDK 的设计亮点：

```
┌──────────────────────────────────────────┐
│  第一层：Client/Server 层                 │
│  McpClient / McpServer                   │
│  职责：连接管理、版本协商、能力协商       │
├──────────────────────────────────────────┤
│  第二层：Session 层                       │
│  DefaultMcpSession                       │
│  职责：通信模式（SYNC/ASYNC）、会话状态   │
├──────────────────────────────────────────┤
│  第三层：Transport 层                     │
│  McpTransport (JSON-RPC)                 │
│  职责：消息序列化、传输协议适配           │
└──────────────────────────────────────────┘
```

**面试话术**：Spring AI MCP 采用分层设计，最上层处理业务语义（工具发现/调用），中间层管理会话生命周期（有状态/无状态），底层负责协议传输（STDIO/HTTP）。这种设计让每层可以独立替换，比如底层从 SSE 切换到 Streamable HTTP，上层代码完全不变。

---

## 三、四种传输方式对比（面试必考）

这是**最高频的面试题**之一。

| 传输方式 | 通信机制 | 端点数量 | 网络范围 | 状态管理 | 适用场景 |
|----------|----------|----------|----------|----------|----------|
| **STDIO** | 进程 stdin/stdout | 无网络端点 | 仅本机 | 进程级 | 本地 IDE 插件、CLI 工具 |
| **SSE** | HTTP SSE + HTTP POST | 2 个 | 远程 | 无会话 | 已弃用，仅兼容旧系统 |
| **Streamable HTTP** | 单端点 POST/GET | 1 个 | 远程 | 有/无状态 | **生产推荐** |
| **Stateless HTTP** | 无状态 POST | 1 个 | 远程 | 完全无状态 | Serverless、弹性伸缩 |

### 选型决策

```
远程访问？
  ├─ 否 → STDIO
  └─ 是 → Streamable HTTP
            ├─ 需要会话状态/断线重连？→ 有状态 + EventStore
            └─ 无状态微服务？→ Stateless 模式
```

### 面试话术

> SSE 是早期方案，需要两个端点（GET /sse + POST /messages），不支持会话管理，MCP 规范在 2025-03-26 起已将其标记为废弃。Streamable HTTP 是替代方案，单端点设计更符合现代 API 网关模型，支持会话恢复、OAuth2 认证、批量请求，高并发下性能提升近 200 倍。

---

## 四、Boot Starter 矩阵（必须能默写）

### Server 端

| Starter | 传输 | 依赖的 Web 框架 |
|---------|------|----------------|
| `spring-ai-starter-mcp-server` | STDIO | 无 |
| `spring-ai-starter-mcp-server-webmvc` | SSE + Streamable HTTP | Spring MVC (Servlet) |
| `spring-ai-starter-mcp-server-webflux` | SSE + Streamable HTTP | WebFlux (Reactive) |

### Client 端

| Starter | HTTP 库 | 支持的传输 |
|---------|---------|-----------|
| `spring-ai-starter-mcp-client` | JDK HttpClient | STDIO + SSE + Streamable HTTP |
| `spring-ai-starter-mcp-client-webflux` | WebClient (Reactive) | STDIO + SSE + Streamable HTTP |

---

## 五、注解体系（Server 端 vs Client 端）

### Server 端：暴露能力

| 注解 | 作用 | 打在 |
|------|------|------|
| `@Tool` | 标记方法为 AI 可调用工具 | 方法 |
| `@ToolParam` | 描述工具方法的参数 | 参数 |
| `@McpTool` | MCP 原生工具注解（比 @Tool 多 MCP 元数据） | 方法 |
| `@McpResource` | 通过 URI 模板暴露数据资源 | 方法 |
| `@McpPrompt` | 暴露可复用的提示词模板 | 方法 |
| `@McpComplete` | 提示词/资源的参数自动补全 | 方法 |

### Client 端：消费能力

| 注解 | 作用 |
|------|------|
| `@McpProgress` | 处理 Server 发来的长任务进度通知 |
| `@McpLogging` | 接收 Server 的结构化日志 |
| `@McpSampling` | 处理 Server 发来的 LLM 采样请求（Server 让 Client 帮忙调 LLM） |
| `@McpElicitation` | 处理 Server 发来的用户交互请求 |

### 面试话术

> Spring AI MCP 的注解体系实现了**声明式编程**。Server 端用 `@Tool`/`@McpTool` 暴露能力，Client 端用 `@McpProgress`/`@McpLogging` 等消费通知。自动配置类 `McpServerAnnotationScannerAutoConfiguration` 会扫描这些注解，自动生成 JSON Schema 并注册到 MCP 协议层，开发者不用手写任何 JSON-RPC 代码。

---

## 六、核心配置（Server 端）

```yaml
spring:
  ai:
    mcp:
      server:
        name: my-mcp-server              # Server 名称
        version: "1.0.0"                 # 版本号
        type: SYNC                       # SYNC 或 ASYNC
        protocol: STREAMABLE             # STREAMABLE | STATELESS | SSE

        # 注解扫描（自动发现 @McpTool/@McpResource）
        annotation-scanner:
          enabled: true

        # 能力开关
        capabilities:
          tool: true
          resources: true
          prompts: true
          completions: true

        # 变更通知（工具/资源/提示词变化时通知 Client）
        tool-change-notification: true
        resource-change-notification: true
        prompt-change-notification: true

        # Streamable HTTP 端点
        streamable-http:
          mcp-endpoint: /mcp

        # 工具响应 MIME 类型（按工具名配置）
        tool-response-mime-type:
          generateImage: image/png
          exportReport: application/pdf
```

### 核心配置（Client 端）

```yaml
spring:
  ai:
    mcp:
      client:
        # STDIO 连接（本地进程）
        stdio:
          connections:
            local-server:
              command: java
              args:
                - "-jar"
                - "/path/to/mcp-server.jar"

        # SSE 连接（远程，已弃用）
        sse:
          connections:
            remote-server:
              url: http://localhost:8080

        # Streamable HTTP 连接（远程，推荐）
        streamable-http:
          connections:
            cloud-server:
              url: http://my-server:8080/mcp
```

---

## 七、自动配置机制（面试加分项）

Spring AI 通过以下自动配置类完成 MCP 集成，面试时说出这些类名很加分：

| 自动配置类 | 职责 |
|-----------|------|
| `McpServerAutoConfiguration` | Server 生命周期管理（启动/停止） |
| `McpServerAnnotationScannerAutoConfiguration` | 扫描 `@McpTool`、`@McpResource`、`@McpPrompt`、`@McpComplete` |
| `McpServerSpecificationFactoryAutoConfiguration` | 将注解方法转换为 MCP 协议规范 |
| `ToolCallbackConverterAutoConfiguration` | `@Tool` 注解方法 → MCP 工具规范的双向桥接 |
| `McpServerObjectMapperAutoConfiguration` | Jackson 序列化定制 |

---

## 八、MCP 完整调用流程（6 步）

```
① 用户提问 → ② AI 模型判断需要工具
  → ③ 模型通过 MCP Client 发 JSON-RPC 请求
  → ④ MCP Server 接收请求，执行对应方法
  → ⑤ 结果通过 JSON-RPC 返回
  → ⑥ 模型整合结果，生成最终回复
```

**和传统 Function Calling 的核心区别**：步骤 ③④ 走的是 JSON-RPC 协议，跨进程/跨网络通信，而不是同 JVM 内的方法调用。

---

## 九、面试模拟问答

### Q: `@Tool` 和 `@McpTool` 有什么区别？

- `@Tool` 是 Spring AI 通用工具注解，本地工具和 MCP Server 都能用
- `@McpTool` 是 MCP 协议专用注解，携带更丰富的 MCP 元数据
- **建议**：优先用 `@Tool`，简单通用；需要 MCP 高级特性时用 `@McpTool`

### Q: ToolCallbackProvider 和 ToolCallback[] 的区别？

- `ToolCallback[]`：固定的工具列表（一次性注册）
- `ToolCallbackProvider`：工具提供者接口（延迟解析，支持动态工具列表）
- MCP Client 的工具数量是动态的（取决于连接了哪些 Server），所以 MCP 工具用 `ToolCallbackProvider`

### Q: Streamable HTTP 的有状态和无状态模式区别？

| 维度 | 有状态 (Stateful) | 无状态 (Stateless) |
|------|-------------------|-------------------|
| 会话 ID | 有 `Mcp-Session-Id` | 无 |
| 断线重连 | 支持事件恢复 | 不支持 |
| 内存占用 | 较高 | 极低（≈5KB/请求） |
| 水平扩展 | 需会话亲和性 | 随意扩展 |
| 适用 | 长对话/流式任务 | Serverless/微服务 |

### Q: 如果面试官问"你是怎么把多个 MCP Server 的工具统一管理起来的？"

> Spring AI 的 `ToolCallbackProvider` 会自动聚合所有 MCP Client 连接的工具。我在 application.yaml 中配置多个 MCP Server 连接（STDIO 的本地 Server + Streamable HTTP 的远程 Server），Spring Boot 启动时自动建立连接、完成能力协商、发现所有工具。在代码里只需注入一个 `ToolCallbackProvider`，传给 `ChatClient.toolCallbacks()`，AI 模型就能调用所有 MCP Server 的工具。

### Q: STDIO Server 有什么坑？

- Server 必须**只通过 stdout 输出 JSON-RPC 消息**，任何日志/banner 输出到 stdout 都会导致 JSON 解析失败
- 解决方案：日志输出到 stderr，或使用 `spring.ai.mcp.server.stdio=true` 让框架自动处理
- 进程生命周期由 Client 管理，Client 退出则 Server 销毁

### Q: MCP 如何做安全认证？

- Streamable HTTP 原生支持 **OAuth 2.0**（含 PKCE、refresh token）
- 通过 `HttpRequestCustomizer` 注入 API Key 或 Bearer Token
- 支持 `allowedHosts`/`allowedOrigins` DNS 白名单防护
- Transport Context 统一传递认证 token 和关联 ID

---

## 十、版本变迁（时间线）

| 时间 | 版本 | 关键变化 |
|------|------|----------|
| 2024-11-05 | MCP 规范初版 | SSE 为推荐传输 |
| 2025-03-26 | MCP 规范更新 | **SSE 被废弃**，Streamable HTTP 成为新标准 |
| 2025-05 | Spring AI 1.0.x | 仅支持 STDIO + SSE |
| 2025-09 | Spring AI 1.1.0-M1 | 新增 Streamable HTTP + Stateless |
| 2025-11 | Spring AI 1.1.0 GA | MCP 功能生产就绪 |

---

## 十一、总结公式

```
创建 MCP Server:
  pom.xml → 选 Starter（STDIO/WebMVC/WebFlux）
     ↓
  写 @Tool 方法 + @ToolParam 描述参数
     ↓
  注册 ToolCallbackProvider Bean
     ↓
  application.yaml 配置协议和能力
     ↓
  启动 → 自动暴露为 MCP 工具

Host 连接 MCP Server:
  pom.xml → spring-ai-starter-mcp-client
     ↓
  application.yaml → 配置连接（STDIO 或 HTTP URL）
     ↓
  注入 ToolCallbackProvider
     ↓
  ChatClient.toolCallbacks(provider).call()
```

| 面试核心词 | 一句话 |
|-----------|--------|
| MCP | AI 调用外部工具的标准化协议 |
| 三层架构 | Client/Server → Session → Transport |
| Streamable HTTP | 生产推荐，单端点，替代 SSE |
| `@Tool` | 声明式工具定义 |
| `ToolCallbackProvider` | 工具统一入口（本地 + MCP 自动聚合） |
| `McpServerAnnotationScannerAutoConfiguration` | 注解自动扫描注册 |
| OAuth 2.0 | MCP 原生安全认证 |
