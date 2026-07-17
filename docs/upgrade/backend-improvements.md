# 后端改进文档 —— 对标主流 AI Agent 实践

> 日期：2026-07-01  
> 范围：异常处理、输入校验、容量保护、会话标题、限流、安全加固

---

## 第二轮：安全加固 & 代码质量修复

### 5. CORS 跨域安全修复 🔴

**修改文件**：`src/main/java/com/yupi/yuaiagent/config/CorsConfig.java`

**问题**：`allowedOriginPatterns("*")` + `allowCredentials(true)` 允许任意网站携带 Cookie 发请求，存在 CSRF 攻击风险。

**修复**：限制为本地开发域名白名单：
```java
ALLOWED_ORIGINS = {
    "http://localhost:5173", "http://localhost:8123",
    "http://127.0.0.1:5173", "http://127.0.0.1:8123"
}
```

---

### 6. Actuator 端点安全 🔴

**修改文件**：`src/main/resources/application.yml`

**问题**：`env` 和 `loggers` 端点对外暴露，`/actuator/env` 可泄露所有环境变量（含真实 API Key）。

**修复**：移除 `env`、`loggers`，仅保留 `health,info,metrics`。

---

### 7. 登录防暴力破解 🟡

**修改文件**：`src/main/java/com/yupi/yuaiagent/controller/UserController.java`

**问题**：`/user/login` 无调用次数限制，可被无限暴力尝试。

**修复**：
```java
@RateLimit(maxRequests = 5, timeWindow = 60, message = "登录过于频繁，请 60 秒后再试")
```

---

### 8. 注册参数校验 🟡

**修改文件**：`src/main/java/com/yupi/yuaiagent/controller/UserController.java`

**问题**：`RegisterRequest` 上未启 `@Valid`，`@NotBlank` 等校验注解不生效，空用户名/密码可直通 Service 层。

**修复**：`@Valid @RequestBody RegisterRequest request`

---

### 9. 用户密码哈希泄露 🟡

**修改文件**：`src/main/java/com/yupi/yuaiagent/controller/UserController.java`

**问题**：`getCurrentUser()` 返回完整 `User` 实体，包含 BCrypt 密码哈希。虽不可逆，但哈希仍可用于离线爆破。

**修复**：返回前执行 `user.setPassword(null)`。

---

### 10. 生产代码移除 `e.printStackTrace()` 🟢

**修改文件**：`src/main/java/com/yupi/yuaiagent/agent/ReActAgent.java`

**问题**：`e.printStackTrace()` 写入 stderr，无结构化日志，难追踪。

**修复**：改为 `log.error("ReAct 步骤执行失败", e)`。

---

## 前端同步修复（简要）

| 问题 | 修复 |
|------|------|
| XSS：`v-html` 渲染未净化 AI 输出 | 引入 DOMPurify 净化 `marked()` 输出 |
| 代码复制按钮的 `onclick` 内联 JS（XSS 载体） | 改为 `addEventListener` 事件绑定 |
| 重试消息导致用户消息重复 push | 重写 `retryMessage`，调用原始 `chat()` 而非 `send()` |
| `agentMode` 刷新丢失 | `localStorage` 持久化 |
| 输入框无长度限制 | 加 `maxlength="2000"` |

---

---

---

## 第四轮：响应速度优化 🚀

### 14. 题库启动预加载

**修改文件**：`src/main/java/com/yupi/yuaiagent/app/QuizApp.java`

**问题**：每次请求调用 `loadQuestionBank()` 做 5 次**串行**向量搜索（5 次 DB 往返），累计延迟 200-500ms。

**修复**：
- 用 `@PostConstruct` 在启动时异步预加载题库到 `cachedQuestionBank`
- 请求时直接返回缓存，0 等待
- 内部 5 个关键词改为 `CompletableFuture` 并行搜索

### 15. 联网搜索非阻塞化

**问题**：`allOf(bankFuture, rewriteFuture, webFuture)` 必须等 web 搜索返回才能开始 AI 调用。Tavily API 有时 2-5 秒才响应。

**修复**：
- web 搜索加 `orTimeout(3, SECONDS)`，超时自动放弃
- 不再 `join()` web 结果，改为 `getNow("")`——有结果就用，没有也不阻塞
- 去掉前置 `[STATUS] 🔍 正在查询...`——不等待就不需要提示

### 16. doUnifiedChat 精简

**优化前延迟**：`max(5×向量搜索, LLM改写, web搜索)` + RAG + AI调用  
**优化后延迟**：`max(0, LLM改写)` + RAG + AI调用（题库 0ms、web 非阻塞）

预计减少 **200-3000ms** 首字节延迟。

---

## 第五轮：流式 token 空格丢失修复 🔴

### 17. DeepSeek API 流式 token 无前导空格

**问题**：DeepSeek API 流式返回的 token 不带前导空格（如 `"SELECT"` / `"FROM"` 而非 `" SELECT"` / `" FROM"`）。  
Spring AI 内部 `ChatClientMessageAggregator` 会自动补齐空格 → 存入 Memory 的文本有空格。  
但我们代码绕过了 Aggregator，直接用 `.content()` 流 → 丢失空格 → 用户看到 `SELECT*FROMt`。

**修复**：用 `.reduce()` 累积全文，检测 token 边界（前一字符字母数字 + 当前字符字母数字）自动插入空格。

```java
.reduce("", (acc, chunk) -> {
    if (!acc.isEmpty() && !chunk.isEmpty()) {
        char prev = acc.charAt(acc.length() - 1);
        char curr = chunk.charAt(0);
        if (Character.isLetterOrDigit(prev) && Character.isLetterOrDigit(curr)) {
            return acc + " " + chunk;
        }
    }
    return acc + chunk;
})
```

**代价**：流式效果变为整段输出（收集完才显示），但内容正确性远大于逐字动画。

---

## 第三轮：全量安全加固 & 输入校验补全

### 11. DTO 参数校验注解（LoginRequest + RegisterRequest）🟡

**修改文件**：
- `src/main/java/com/yupi/yuaiagent/model/dto/LoginRequest.java`
- `src/main/java/com/yupi/yuaiagent/model/dto/RegisterRequest.java`

**问题**：两个 DTO 的 `@NotBlank`、`@Size` 注解全部是 TODO，空用户名/短密码可直通 Service 层。

**修复**：
```java
// LoginRequest
@NotBlank(message = "用户名不能为空")
@Size(min = 4, max = 20, message = "用户名长度需在 4-20 之间")
private String username;

@NotBlank(message = "密码不能为空")
@Size(min = 6, max = 100, message = "密码长度至少 6 位")
private String password;

// RegisterRequest 同上 + nickname: @Size(max = 30)
```

### 12. @Valid 校验失败异常处理 🟡

**修改文件**：`src/main/java/com/yupi/yuaiagent/exception/handler/GlobalExceptionHandler.java`

**问题**：加 `@Valid` 后校验失败抛 `MethodArgumentNotValidException`，无处理会导致 500。

**修复**：新增 handler，提取第一条字段错误信息返回 400。

### 13. Agent 端点安全加固 🟡

**修改文件**：`src/main/java/com/yupi/yuaiagent/controller/AiController.java`

**问题**：
- `doAgentChat` 无 `@RequestParam`、无 null 检查、无长度校验、无限流
- `deleteConversation` / `renameConversation` 的 chatId 未校验，存在路径遍历风险

**修复**：
- `doAgentChat` 加 `@RequestParam`、空白/超长校验、`@RateLimit`
- 删除/改名接口加 chatId 黑名单字符检测（`..`、`/`、`\`）

---

## 前端安全加固（简要）

| 问题 | 修复 |
|------|------|
| SSE connectSSE 修改调用者 params | 改用 `{ ...params }` 浅拷贝 |
| API default export 缺失 renameConversation | 补全 |
| Router 无 404 兜底 | `/:pathMatch(.*)*` → redirect `/` |
| ChatView 消息缓存无限增长 | LRU 上限 50 个会话 |
| ChatView chatId 用 Date.now() 碰撞风险 | 改用 `crypto.randomUUID()` |
| ChatView 建议按钮 streaming 时可点击 | 加 `:disabled="connecting"` |
| Login/Register 所有错误显示"网络错误" | 区分 429/500/网络断开 |
| Register 用 `alert()` 提示成功 | 改为页面内 `successMsg` |
| Register confirmPassword autocomplete 错误 | 改为 `autocomplete="off"` |
| App.vue 无路由过渡、无键盘焦点样式 | 加 `<transition>` + `:focus-visible` |
| main.js 无全局错误处理 | 加 `app.config.errorHandler` |
| index.html 无 CSP、无 theme-color、假 favicon | 加 CSP meta + theme-color + 移除无效 favicon |
| SSE connectSSE API | 加 `{...params}` 防外部修改 |
| API exports | 补全缺失导出 |

---

## 更新验证清单

| # | 测试场景 | 预期结果 |
|---|---------|---------|
| 10 | `/actuator/env` 访问 | 404 禁止访问 |
| 11 | `curl -H "Origin: https://evil.com" localhost:8123/api/ai/chat` | CORS 拒绝，无 `Access-Control-Allow-Origin` |
| 12 | 1 分钟内登录 6 次 | 第 6 次返回限流错误 |
| 13 | 注册时 username 为空 | 返回 400 校验失败（需配合 DTO 上 @NotBlank） |
| 14 | 登录后调 `/user/current` | 返回 user 不含 password 字段 |
| 15 | AI 回复含 `<script>alert(1)</script>` | 前端渲染为纯文本，不弹窗 |

---

## 1. GlobalExceptionHandler 全局异常处理

**修改文件**：`src/main/java/com/yupi/yuaiagent/exception/handler/GlobalExceptionHandler.java`

**问题**：所有 `@ExceptionHandler` 方法为空白 TODO。未捕获异常直接暴给前端（Spring Boot 默认错误页），用户看到原始堆栈或 undefined。

**修复**：

| 异常类型 | HTTP 状态码 | 前端返回 |
|---------|-------------|---------|
| `BusinessException` | 400 | `ApiResponse.error(400, e.getMessage())` |
| `IllegalArgumentException` | 400 | `ApiResponse.error(400, e.getMessage())` |
| `Exception` (兜底) | 500 | `ApiResponse.error(500, "服务器内部错误，请稍后重试")` + `log.error` 记录完整堆栈 |

**同修**：`BusinessException.java` 补全构造函数 `(String message)` 和 `(String message, Throwable cause)`。

**设计原则**：永远不向前端暴露堆栈信息。

---

## 2. AiController 输入校验 + 限流

**修改文件**：`src/main/java/com/yupi/yuaiagent/controller/AiController.java`

**问题**：
- 聊天接口无输入校验，超长消息直接打给 LLM 浪费 token
- `@RateLimit` 注解已存在但未挂到 chat 接口
- GET 请求参数未加 `@RequestParam` 注解

**修复**：

```java
// 校验
if (message == null || message.isBlank()) → "[ERROR] 消息不能为空"
if (message.length() > 2000) → "[ERROR] 消息过长，最多 2000 字"

// 限流
@RateLimit(maxRequests = 10, timeWindow = 60)  // 每分钟最多 10 次
```

参数声明显式化为 `@RequestParam`，避免 Spring 推断歧义。

---

## 3. FileBasedChatMemory 容量保护

**修改文件**：`src/main/java/com/yupi/yuaiagent/chatmemory/FileBasedChatMemory.java`

**问题**：
- 单文件无条件追加，50+ 条对话后 JSON 文件持续膨胀
- 无文件数量预警
- 缺少标题编辑能力

**修复**：

### 3.1 消息数上限
```java
MAX_MESSAGES_PER_FILE = 200  // 单文件消息数上限
```
`add()` 方法执行后检查：若 `size > 200`，截断保留最近 200 条，`log.warn` 记录。

### 3.2 文件数量预警
```java
MAX_RECOMMENDED_FILES = 100  // 建议最大会话数
```
`listConversations()` 检查：超过 100 个文件时 `log.warn` 输出预警。

### 3.3 标题自定义
- `updateTitle(chatId, title)` — 写入 `.title` 旁路文件
- `getCustomTitle(chatId)` — 读取自定义标题
- `listConversations()` 优先使用自定义标题，其次 `extractTitle()`

### 3.4 标题提取优化
`extractTitle()` 增加噪音词过滤：
```java
NOISE_WORDS = {"继续", "下一个", "换一个", "不知道", "不会", "忘了", ...}
```
跳过噪音词取第一条有效用户消息，长度 < 4 字的也跳过。

---

## 4. 对话标题 API

**修改文件**：`src/main/java/com/yupi/yuaiagent/controller/AiController.java`

新增端点：

```
PUT /api/ai/conversations/{chatId}/title?title=新标题
```

校验：标题 1-30 字，空白/超长返回 400。

前端调用 `renameConversation(chatId, title)` 实现双击改名。

---

## 验证清单

| # | 测试场景 | 预期结果 |
|---|---------|---------|
| 1 | 发空消息 | 返回 `[ERROR] 消息不能为空` |
| 2 | 发 >2000 字消息 | 返回 `[ERROR] 消息过长` |
| 3 | 1 分钟内发 11 次请求 | 第 11 次返回限流 429 |
| 4 | 构造 250 条消息的 JSON 文件，再发一条 | 文件截断到 200 条 |
| 5 | 创建 101 个会话文件 | 日志输出文件数预警 |
| 6 | PUT `/conversations/xxx/title?title=测试` | 返回 success，标题变更 |
| 7 | 首条消息为"继续" | 标题不再是"继续"，跳过噪音词 |
| 8 | 抛 BusinessException | 返回 `{"code":400,"message":"..."}` |
| 9 | 抛 NullPointerException | 返回 `{"code":500,"message":"服务器内部错误，请稍后重试"}` |

---

## 前后端改动对照

| 后端 | 前端配套 |
|------|---------|
| `PUT /conversations/{chatId}/title` | 双击标题 → 就地编辑 → 回车保存 |
| `@RateLimit` | 无需配合，后端自动拦截 |
| 输入校验 2000 字上限 | 前端输入框无硬限制（由后端返回错误） |
| 容量保护 200 条截断 | 无需配合，后端自动执行 |
| GlobalExceptionHandler | 错误信息正常显示，不再白屏 |
