# 记忆与安全层缺陷加固

- 日期：2026-09-18
- 分支：`feat/eval-harness`
- 状态：设计已确认，待实现

## 1. 背景

一次针对「记忆机制 / 规则约束机制」的代码审查列出了 15 项问题。在动手前核实发现两件事，
它们把范围压缩到了 7 项。

### 1.1 其中 5 项是刻意留白的练习 TODO

代码中存在 30 处 `🎯 Task N`（Task 1–14）标记，是作者自设的练习。

| 曾被列为问题 | 实际身份 |
|---|---|
| `RateLimitAspect` 空转 | Task 13 第二部分 |
| `SemanticCacheService` 未接 Redis | Task 9 |
| `JwtAuthFilter` Token 黑名单 TODO | Task 9 第三部分 |
| `mapToMessage` 的 `TOOL_RESPONSE` | Task 2 |
| `AdminController` 无 RBAC | Task 14 |

**这些一律不改。** 修复它们等于代做作业，摧毁项目的学习价值。

> ⚠️ 运行时后果仍需知晓：`RateLimitAspect.handleRateLimit` 当前直接
> `joinPoint.proceed()`，因此 `UserController:96` 的登录防爆破注解**不生效**。
> 这是「代码看起来有限流、实际没有」的危险形态，属已知取舍。

### 1.2 其中 4 项已由当前工作区的未提交改造解决

工作区存在一处系统性的记忆层加固（`FileBasedChatMemory` 278 → 859 行），已解决：

| 曾被列为问题 | 现状 |
|---|---|
| SYSTEM 消息被读成 `AssistantMessage` | 已修：`mapToMessage` 逐角色还原（含 SYSTEM / TOOL / toolCalls） |
| 文件锁只护单 JVM | 已修：`ChatStoreLockManager` 提供 JVM 监视器 + 跨进程 `FileLock` 双层锁 |
| 会话 ID 可路径穿越 | 已修：`validateConversationId` 覆盖四条拼路径 |
| 会话文件无限增长 | 已修：`enforceRetention` + `ConversationRetentionSweeper` |

本设计**以当前工作区为基线**，不回退上述改造。

## 2. 范围

**做**：

1. `FileOperationTool` 路径穿越防护
2. `JwtAuthFilter` URL 传 token 收窄作用域
3. `FileBasedChatMemory` 截断时保护 `【方向切换】`
4. 记忆窗口引入 token 预算
5. `RubricScorer` 输出 schema 校验 + 重试
6. `ActiveSpecManager` TTL 与孤儿方法接线
7. 死代码清理（零风险小件）

**不做**（明确排除）：

- 全部 `🎯 Task N` 练习 TODO
- 图谱链路（`graph` 包）—— 只标注，不删
- `TopicRotationService` 维度子系统 —— 只标注，不删
- prompt 集中化 / 约束注册表 —— 收益不明而风险高，会动到核心提示词
- 内容审核 / prompt injection 检测 —— 需外部依赖，另议

## 3. 设计

### 3.1 `SafePathResolver`（新增）

`tools/FileOperationTool` 的 `readFile` / `writeFile` 直接拼接
`FILE_DIR + "/" + fileName`，零校验。LLM 可传 `../../data/chat-memory/user_1.json`
读写工作目录外的任意文件。

新增 `util/SafePathResolver`：

```java
public static Path resolveWithin(Path baseDir, String userPath)  // 越界抛 IllegalArgumentException
```

校验顺序（对齐 `ExportController` 已有的三重防护）：

1. 拒绝 `null` / 空白 / 含 `..` / 绝对路径 / Windows 盘符
2. `baseDir.resolve(userPath).normalize()`
3. `startsWith(baseDir.toRealPath())`

**为什么单独成类**：项目刚为此吃过亏。`ChatIdValidator` 的 javadoc 记录了同类问题——
「此前校验逻辑散落成多份 private 副本，有的地方一份都没有」。`WebScrapingTool` /
`ResourceDownloadTool` 有同构缺口，收拢后可直接复用。

`FileOperationTool` 保持不抛异常的既有契约：捕获 `IllegalArgumentException`，
返回 `"Error: 非法路径 — <path>"`。

### 3.2 `JwtAuthFilter` URL token 收窄

现状（`JwtAuthFilter:119`）：

```java
token = request.getParameter("token");
```

URL query 会进 access log、Referer、浏览器历史，JWT 泄漏面大。但 SSE 的 `EventSource`
**确实**无法设置自定义 header，不能直接删。

**采用方案 (a)：限定作用域。** 仅当请求路径命中 SSE 端点白名单时才接受 URL token，
其余路径一律要求 `Authorization: Bearer`。

白名单（当前共 3 个，均为 `GET` + `produces = text/event-stream`）：

- `/ai/agent/chat`
- `/ai/chat`
- `/ai/review/chat`

**判据用路径白名单，不用 `Accept` 头**：`Accept` 由客户端声明、可随意伪造，
用它等于没限制。路径白名单把「可用 URL token」的端点从全站压到 3 个只读流式端点。

> 更彻底的方案（一次性 ticket `POST /auth/sse-ticket`）作为后续独立任务，需前后端同改。

### 3.3 截断保护 `【方向切换】`

`FileBasedChatMemory.add()` 的截断逻辑无差别删除最早 N 条：

```java
existing = new ArrayList<>(existing.subList(removed, existing.size()));
```

而 `SummarizingChatMemory.get():76` 的保护语义依赖 `【方向切换】` 消息存在。
普通 Agent 对话链路（`AgentChatController`）没有 `TopicMemoryTrimmer` 的定期裁剪，
会话超 200 条时最早的 `【方向切换】` 会被静默删除，保护逻辑随之失效。

**改法**：截断时先摘出受保护消息，再按剩余配额从新到旧填充。

受保护判定提取为共享方法（当前只写在 `SummarizingChatMemory` 里），
两处共用避免语义漂移。`replaceMessages` 的同款截断一并处理。

### 3.4 token 预算

`SummarizingChatMemory` 目前只按条数裁剪（`DEFAULT_MAX_MESSAGES = 20`）。
一条超长 RAG 结果即可打爆上下文。

**改法**：`get()` 改为**条数与 token 双阈值**——先按现有条数逻辑取窗口，
若窗口估算 token 超预算，再从最早开始丢弃至低于预算。

估算用字符启发式（中文约 1.5 字/token），保留 30% 余量。

配置项（默认值即当前行为，不配则不变）：

| key | 默认 | 说明 |
|---|---|---|
| `qian.memory.window.max-messages` | `20` | 沿用现值 `DEFAULT_MAX_MESSAGES` |
| `qian.memory.window.max-tokens` | `8000` | `<= 0` 表示不按 token 限制 |

**不引入 `jtokkit`**：DeepSeek 的 tokenizer 非公开稳定，引库只换来虚假的精确感；
字符启发式 + 余量更诚实。配置沿用项目已有的 `@ConfigurationProperties` 模式
（见 `StorageProperties`）。

### 3.5 `RubricScorer` schema 校验 + 重试

现状（`RubricScorer:168-176`）：手工剥 ` ```json ` 后 `readValue`，
失败即兜底 `totalScore = 0`——**把解析问题静默成了数据**。

`pom.xml:197` 已有 `jsonschema-generator` 依赖，从未启用。

**改法**：

1. 启动时从 `RubricResult` 反射生成一次 JSON Schema（缓存）
2. LLM 返回后先 schema 校验，失败则**带错误信息重试一次**
3. 仍失败才走兜底 0 分，并在结果里标记 `parseFailed`

### 3.6 `ActiveSpecManager` TTL 与接线

`activeSpecs`（`Map<String,String>`）无 TTL、无清理任务；`remove()` 是孤儿方法，
会话删除后项目描述永久驻留内存。

**改法**：

- 增加 `lastAccess` 时间戳，`@Scheduled` 清理超时条目（复用
  `ConversationRetentionSweeper` 的模式：`fixedDelayString` 可配 + 纯方法可单测）
- TTL 默认 **2 小时**，清理间隔默认 **30 分钟**——对齐 `QuizApp.evictExpiredEntries`
  的既有口径，避免同一项目内两套过期语义
- 把 `remove()` 接到 `ConversationController` 的会话删除路径

### 3.7 死代码清理

经当前工作区重新核实：

| 目标 | 判定 | 处置 |
|---|---|---|
| `UserAbilityService.saveToUserFile` / `saveToUserRedis` / `deleteUserProfileFile` / `deleteFromUserRedis` | 4 个 private 方法零调用 | **删除** |
| `AskedPointTracker.hydrateAsked` | 零调用 | **删除** |
| `AskedPointTracker` 类整体 | 生产零调用，但功能完整（追问配额） | **标注** `@Deprecated` + 注明无生产调用者 |
| `ConversationAnalysisService.findPreviousAssistant` | 有调用者，但硬编码 `return null` | **标注**（属图谱大件，按约定不删） |

## 4. 测试策略

每项配单测，沿用现有风格（`@TempDir`、显式注入 `Clock`、反向断言）。

| 项 | 关键用例 |
|---|---|
| 3.1 | `../` 逃逸、绝对路径、符号链接逃逸**被拒**；合法相对路径**通过** |
| 3.2 | 非 SSE 请求带 URL token **被拒**；`Accept: text/event-stream` **放行** |
| 3.3 | 超 200 条时 `【方向切换】` **仍在**；无保护消息时行为不变 |
| 3.4 | 超 token 预算的窗口被裁到预算内；未超预算时不裁剪 |
| 3.5 | 非法 JSON 触发重试；重试成功则数据正确；两次都失败才兜底 |
| 3.6 | 超时条目被清理；未超时**不**被清；删除会话时 `remove()` 被调用 |

## 5. 已知残留（不在本次范围）

来自工作区 `FileBasedChatMemory` 类注释的自我声明，本设计不处理：

1. **写侧越权防不住** —— 真正写入口是 `ChatMemoryAdvisor`，跑在响应式线程上，
   此时 `UserContext` 的 ThreadLocal 已被 `JwtAuthFilter` 清理。归属校验只保证「读不越权」，
   Controller 门卫与写入之间的 TOCTOU 窗口无法消除。
2. **跨进程锁只保证「不写坏文件」** —— 各 Service 的 per-chatId 内存状态仍是 JVM 内的。
   定位是「单机 + 跨进程防损坏」，不是「已支持多实例」。

另：`disadvantage` 笔记记录的「记忆文件本地存储，只能单机使用」与第 2 条同源，
是架构级取舍，需独立规划（如引入 Redis/DB 持久化），不在本次范围。
