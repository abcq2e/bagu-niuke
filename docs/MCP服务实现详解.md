# MCP 服务实现详解

> 基于 qian-ai-agent 项目实际代码，梳理 MCP（Model Context Protocol）的完整实现。

---

## 一、MCP 基础概念

**MCP（Model Context Protocol，模型上下文协议）** 是 Anthropic 推出的开放标准协议，定义了大模型与外部工具/数据源之间的**标准化交互方式**。

一句话：**MCP 就是 AI 世界的 USB-C 接口**——不同厂商的工具和模型，遵循同一协议就能"即插即用"。

### MCP 解决了什么问题

| 痛点 | MCP 方案 |
|------|----------|
| **数据孤岛** | 模型通过统一协议访问数据库、API、文件系统等外部数据 |
| **重复开发** | 写一次 MCP Server，所有兼容模型都能调用（不再为每个模型写 Function Calling） |
| **生态碎片化** | 标准协议让不同厂商的工具/模型互操作 |

### MCP vs 传统 Function Calling

| 维度 | Function Calling | MCP |
|------|------------------|-----|
| 耦合度 | 工具代码嵌在 AI 应用中 | 工具独立部署为 Server |
| 语言限制 | 只能同语言（如 Java） | 任何语言，通过 JSON-RPC 通信 |
| 复用性 | 一个应用一套工具 | 一个 Server 多个应用共享 |
| 扩展性 | 改主应用代码 | 加新 Server，主应用不改 |
| 标准化 | 各厂商定义不同 | 统一协议规范 |

---

## 二、项目 MCP 架构总览

```
┌──────────────────────────────────────────────────┐
│                   qian-ai-agent (Client)            │
│  LoveApp.doChatWithMcp()                         │
│    └─ ChatClient.toolCallbacks(toolCallbackProvider)│
│         └─ 聚合所有 MCP Server 的工具              │
│              ├─ 本地 STDIO: ImageSearchTool        │
│              └─ 远程(已注释): 高德地图 MCP Server   │
├──────────────────────────────────────────────────┤
│              JSON-RPC (跨进程/跨网络)              │
├──────────────────────────────────────────────────┤
│  qian-image-search-mcp-server (独立子模块)          │
│    ImageSearchTool.searchImage()                  │
│      └─ Pexels API 搜图                           │
└──────────────────────────────────────────────────┘
```

项目包含两部分：
- **MCP Server**：`qian-image-search-mcp-server/` 子模块，独立部署，提供图片搜索能力
- **MCP Client**：主项目 `qian-ai-agent`，连接多个 MCP Server，在 AI 对话中调用其工具

---

## 三、MCP Server 端实现

### 3.1 模块位置

```
qian-image-search-mcp-server/
├── pom.xml                          # Maven 依赖
├── src/main/java/com/yupi/yuimagesearchmcpserver/
│   ├── YuImageSearchMcpServerApplication.java  # 启动类 + 工具注册
│   └── tools/
│       └── ImageSearchTool.java                # 工具实现
└── src/main/resources/
    ├── application.yml              # 默认配置
    ├── application-sse.yml          # SSE 模式配置
    └── application-stdio.yml        # STDIO 模式配置
```

### 3.2 依赖配置

文件：`qian-image-search-mcp-server/pom.xml`

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

选择了 **WebMVC 版 Starter**，支持 SSE 和 Streamable HTTP 两种远程传输方式（也可以通过命令行参数切换为 STDIO 模式）。

### 3.3 工具定义 — `@Tool` 注解

文件：`qian-image-search-mcp-server/src/main/java/.../tools/ImageSearchTool.java`

```java
@Service
public class ImageSearchTool {

    private static final String API_KEY = "your-pexels-api-key";
    private static final String API_URL = "https://api.pexels.com/v1/search";

    @Tool(description = "search image from web")
    public String searchImage(
            @ToolParam(description = "Search query keyword") String query) {
        try {
            return String.join(",", searchMediumImages(query));
        } catch (Exception e) {
            return "Error search image: " + e.getMessage();
        }
    }

    public List<String> searchMediumImages(String query) {
        // 设置请求头（包含API密钥）
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", API_KEY);

        // 设置请求参数
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);

        // 发送 GET 请求到 Pexels API
        String response = HttpUtil.createGet(API_URL)
                .addHeaders(headers)
                .form(params)
                .execute()
                .body();

        // 解析响应，提取图片 URL
        return JSONUtil.parseObj(response)
                .getJSONArray("photos")
                .stream()
                .map(photoObj -> (JSONObject) photoObj)
                .map(photoObj -> photoObj.getJSONObject("src"))
                .map(photo -> photo.getStr("medium"))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }
}
```

关键点：
- `@Tool(description = "search image from web")` — 声明该方法为 AI 可调用的工具，`description` 帮助模型判断何时调用
- `@ToolParam(description = "Search query keyword")` — 描述参数语义，框架自动生成 JSON Schema 给 AI 模型
- 方法内部实现（调 Pexels API）对 AI 模型完全透明

### 3.4 工具注册

文件：`qian-image-search-mcp-server/src/main/java/.../YuImageSearchMcpServerApplication.java`

```java
@SpringBootApplication
public class YuImageSearchMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuImageSearchMcpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider imageSearchTools(ImageSearchTool imageSearchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(imageSearchTool)
                .build();
    }
}
```

`MethodToolCallbackProvider` 会扫描 `imageSearchTool` 对象上所有带 `@Tool` 注解的方法，自动转换为标准的 `ToolCallback` 并注册到 MCP 协议层。

### 3.5 双模式运行

Server 支持两种传输模式，通过 Spring Profile 切换：

**STDIO 模式**（`application-stdio.yml`）：

```yaml
spring:
  ai:
    mcp:
      server:
        name: qian-image-search-mcp-server
        version: 0.0.1
        type: SYNC
        stdio: true
  main:
    web-application-type: none    # 不启动 Web 容器
    banner-mode: off              # 关闭 banner，stdout 只能输出 JSON-RPC
```

**SSE 模式**（`application-sse.yml`）：

```yaml
spring:
  ai:
    mcp:
      server:
        name: qian-image-search-mcp-server
        version: 0.0.1
        type: SYNC
        stdio: false              # HTTP 模式，启动 Web 容器暴露 SSE 端点
```

默认使用 SSE 模式（`application.yml` 中 `spring.profiles.active: sse`），Server 端口为 `8127`。

> ⚠️ **STDIO 模式坑点**：Server 通过 `stdin/stdout` 通信，任何输出到 stdout 的内容（Spring Banner、日志等）都会破坏 JSON-RPC 消息格式。必须设置 `banner-mode: off`，并将日志输出定向到 stderr。

---

## 四、MCP Client 端实现

### 4.1 依赖配置

文件：`pom.xml`

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

使用 JDK HttpClient 版本的 Client Starter，支持 STDIO + SSE + Streamable HTTP 三种连接方式。

### 4.2 MCP Server 连接配置

文件：`src/main/resources/mcp-servers.json`

```json
{
  "mcpServers": {
    "amap-maps": {
      "command": "npx.cmd",
      "args": [
        "-y",
        "@amap/amap-maps-mcp-server"
      ],
      "env": {
        "AMAP_MAPS_API_KEY": "f4058a41ed399f93cd798e334f76072d"
      }
    },
    "qian-image-search-mcp-server": {
      "command": "java",
      "args": [
        "-Dspring.ai.mcp.server.stdio=true",
        "-Dspring.main.web-application-type=none",
        "-Dlogging.pattern.console=",
        "-jar",
        "qian-image-search-mcp-server/target/qian-image-search-mcp-server-0.0.1-SNAPSHOT.jar"
      ],
      "env": {}
    }
  }
}
```

| 字段 | 说明 |
|------|------|
| `command` | 启动 MCP Server 子进程的命令 |
| `args` | 命令行参数（含 JVM 参数切换为 STDIO 模式） |
| `env` | 环境变量（如 API Key） |

启动时，Spring AI 会为每个配置项**启动一个子进程**，通过 STDIO 建立 JSON-RPC 通道，自动完成**能力协商**和**工具发现**。

### 4.3 application.yml 中的 MCP 配置

文件：`src/main/resources/application.yml`

```yaml
# 临时注释掉，便于大家开发调试和部署（实际需要启动 MCP 服务）
#    mcp:
#      client:
#        sse:
#          connections:
#            server1:
#              url: http://localhost:8127
#        stdio:
#          servers-configuration: classpath:mcp-servers.json
```

当前 MCP Client 配置被注释掉——开发阶段不需要启动 MCP Server，减少依赖。需要使用 MCP 功能时取消注释即可。

### 4.4 在 ChatClient 中使用 MCP 工具

文件：`src/main/java/com/yupi/yuaiagent/app/LoveApp.java`

```java
@Resource
private ToolCallbackProvider toolCallbackProvider;

// AI 调用 MCP 服务
public String doChatWithMcp(String message, String chatId) {
    ChatResponse chatResponse = chatClient
            .prompt()
            .user(message)
            .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
            .advisors(new MyLoggerAdvisor())                       // 日志观察
            .toolCallbacks(toolCallbackProvider)                   // ← MCP 工具绑定
            .call()
            .chatResponse();
    return chatResponse.getResult().getOutput().getText();
}
```

**两种工具注入方式的对比**：

| 方式 | 注入类型 | 用途 |
|------|---------|------|
| `ToolCallback[] allTools` | 固定数组 | 本地工具（一次性注册） |
| `ToolCallbackProvider` | 提供者接口 | MCP 工具（动态发现，延迟解析） |

MCP 工具数量随连接的 Server **动态变化**（加一个 Server 就多一批工具），所以必须用 `ToolCallbackProvider` 而非固定数组。

---

## 五、MCP 调用完整流程

```
① 用户问："帮我找一张猫的图片"
    ↓
② DeepSeek 模型判断需要调用 searchImage 工具
    ↓
③ ChatClient 通过 MCP Client 向 qian-image-search-mcp-server 发 JSON-RPC 请求
    → tools/call 方法，参数 {"query": "猫"}
    ↓
④ MCP Server 接收请求，执行 ImageSearchTool.searchImage("猫")
    → 调用 Pexels API → 返回图片 URL 列表
    ↓
⑤ 结果通过 JSON-RPC 返回给 ChatClient
    ↓
⑥ 模型整合结果，生成最终回复：
   "我为你找到了以下猫咪图片：[url1, url2, url3, ...]"
```

和传统 Function Calling 的核心区别：**步骤 ③④ 走的是 JSON-RPC 跨进程/跨网络通信**，而不是同 JVM 内的直接方法调用。

---

## 六、Spring AI MCP 三层架构

Spring AI MCP 采用分层设计：

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

好处：每层可以独立替换。比如底层从 SSE 切换到 Streamable HTTP，上层代码完全不变。

---

## 七、四种传输方式对比

| 传输方式 | 通信机制 | 网络范围 | 适用场景 |
|----------|----------|----------|----------|
| **STDIO** | 进程 stdin/stdout | 仅本机 | 本地 IDE 插件、CLI 工具 |
| **SSE** | HTTP SSE + HTTP POST（2 端点） | 远程 | 已弃用，仅兼容旧系统 |
| **Streamable HTTP** | 单端点 POST/GET | 远程 | **生产推荐** |
| **Stateless HTTP** | 无状态 POST | 远程 | Serverless、弹性伸缩 |

> SSE 在 MCP 规范 2025-03-26 版本中被标记为废弃，Streamable HTTP 是推荐替代方案。

---

## 八、总结公式

### 创建自己的 MCP Server

```
pom.xml → 选 Starter（STDIO/WebMVC/WebFlux）
    ↓
写 @Tool 方法 + @ToolParam 描述参数
    ↓
注册 ToolCallbackProvider Bean
    ↓
application.yaml 配置协议和能力
    ↓
启动 → 自动暴露为 MCP 工具
```

### Host 项目消费 MCP 工具

```
pom.xml → 加 spring-ai-starter-mcp-client
    ↓
mcp-servers.json → 配置要连接的 MCP Server
    ↓
application.yml → 开启 mcp.client 配置
    ↓
注入 ToolCallbackProvider
    ↓
ChatClient.toolCallbacks(provider).call()
```

### 核心注解速查

| 注解 | 位置 | 作用 |
|------|------|------|
| `@Tool` | Server 方法 | 标记方法为 AI 可调用工具 |
| `@ToolParam` | 方法参数 | 描述工具参数语义 |
| `@McpTool` | Server 方法 | MCP 原生工具注解（更丰富元数据） |
| `@McpResource` | Server 方法 | 通过 URI 暴露数据资源 |
| `@McpPrompt` | Server 方法 | 暴露可复用提示词模板 |

### 关键类速查

| 类 | 作用 |
|------|------|
| `MethodToolCallbackProvider` | 扫描 `@Tool` 注解，生成 ToolCallback |
| `McpServerAutoConfiguration` | Server 生命周期管理 |
| `McpServerAnnotationScannerAutoConfiguration` | 注解自动扫描注册 |
| `ToolCallbackConverterAutoConfiguration` | `@Tool` 与 MCP 规范的桥接 |
