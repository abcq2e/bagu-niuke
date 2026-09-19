# 执行链路与工具层缺陷加固

- 日期：2026-09-19
- 分支：`feat/eval-harness`
- 状态：设计已确认，待实现

## 1. 背景

一次针对「执行管控与自愈机制」的梳理列出了 6 项可疑点。在动手前核实发现：
2 项并非缺陷（1.1、1.2），1 项需先定处置策略才能落地（1.3）。
最终落定为 **4 项实现改动 + 1 项风险标注**。

### 1.1 与 2026-09-18 spec 的决策冲突及结论

`docs/superpowers/specs/2026-09-18-memory-security-hardening-design.md` 第 1.1 节已明确：

> 代码中存在 30 处 `🎯 Task N`（Task 1–14）标记，是作者自设的练习。
> **这些一律不改。修复它们等于代做作业，摧毁项目的学习价值。**

`RateLimitAspect` 正是其中的 Task 13 第二部分。

本次梳理最初把它列为「@RateLimit 注解全不生效」的缺陷，并据此征询过是否实现。
**该征询在未读到上述 spec 的情况下提出，选项描述低估了后果。** 补齐背景后确认：
维持 2026-09-18 的决策，**不实现限流逻辑**。改以 3.6 节的方式消除「看起来有防护」的假象。

> 运行时后果（已知取舍，本次不改变）：`RateLimitAspect.handleRateLimit` 直接
> `joinPoint.proceed()`，因此 `AgentChatController:68`、`InterviewChatController:59,106`、
> `UserController:96` 四处 `@RateLimit` 全部不生效。

### 1.2 另一项非缺陷

`AskedPointTracker`（追问配额）无生产调用者。类注释已自陈，
且已在提交 `6218b36`（「清理零调用死代码，标注未接线实现」）中做过决策 ——
**保留并标注，等待接入出题链路**。本次不动。

### 1.3 一个改变问题性质的发现

`RubricScorer.parseFailed`（提交 `0af4915` 引入）**当前没有任何消费方** ——
全项目检索只有定义处 `RubricScorer.java:432`、设置处 `:293` 与两个测试，
无任何业务代码读取它。

这使「给 `parseScoreResult` 补一个标记」不再是加个字段，而需要先回答
「不可信评分由谁处置」。若处置缺位，照搬只会造出第二个死标记。见 3.4 节。

## 2. 范围

**做**：

1. 新增 `UrlSafetyValidator`，收拢 URL 安全校验（3.1）
2. `WebScrapingTool` 加固：SSRF 防护 + 超时 + 体积上限 + 禁跳转（3.2）
3. `ResourceDownloadTool` 加固：路径穿越 + SSRF + 体积上限 + 超时（3.3）
4. 评分不可信标记统一并接入告警（3.4）
5. `maxSteps` 文档与代码同步（3.5）
6. `RateLimitAspect` 风险可见化，不改逻辑（3.6）

**不做**（明确排除）：

- 全部 `🎯 Task N` 练习 TODO（与 2026-09-18 spec 一致）—— 含本次触及的 `RateLimitAspect`
- 实现限流逻辑（见 1.1）
- `AskedPointTracker` 的接入或删除（见 1.2）
- 内容审核 / prompt injection 检测 —— 需外部依赖，另议

## 3. 设计

### 3.1 `UrlSafetyValidator`（新增）

`src/main/java/com/qian/qianaiagent/util/UrlSafetyValidator.java`，与 `SafePathResolver` 并列。

收拢理由同 `SafePathResolver` 类注释：校验逻辑一旦散落成多份 private 副本，
必然出现「有的地方有、有的地方没有」。2026-09-18 spec 第 84–85 行已预告
`WebScrapingTool` / `ResourceDownloadTool` 有同构缺口，本节即其兑现。

```java
public static URI validate(String url)   // 不合法则抛 IllegalArgumentException
```

校验规则：

1. 非空、可解析为 `URI`
2. 协议白名单：`http` / `https`（挡 `file:` `ftp:` `jar:` 等）
3. 主机名经 `InetAddress.getAllByName()` 解析后，**任一**结果命中下列网段即拒绝：
   - IPv4：`0.0.0.0/8`、`10.0.0.0/8`、`100.64.0.0/10`、`127.0.0.0/8`、`169.254.0.0/16`（含云元数据 `169.254.169.254`）、`172.16.0.0/12`、`192.0.0.0/24`、`192.168.0.0/16`、`198.18.0.0/15`、`224.0.0.0/4`、`240.0.0.0/4`
   - IPv6：`::1`、`::`、`fc00::/7`、`fe80::/10`
4. 特殊处理 **IPv4-mapped IPv6**（`::ffff:127.0.0.1`）—— 必须拆出内嵌 IPv4 后按上表判定，
   否则是绕过路径
5. 主机无法解析（`UnknownHostException`）→ 拒绝，不静默放行（与 `SafePathResolver`
   对悬空符号链接的处理同源：不确定即拒绝）

字面 IP 与域名统一走 `getAllByName`，无需分支。

**已知局限（明确记录，不粉饰）**：本校验挡不住 DNS rebinding ——
校验时刻解析到的 IP 与真正建连时刻解析到的 IP 之间存在时间窗，
攻击者可让域名先解析为公网地址、建连时再指向内网。彻底防住需在 socket 层
校验对端 IP（自定义 `SocketFactory` 或连接后比对 `getRemoteAddress`），成本显著。

本层挡的是「LLM 直接把内网 URL 当作参数传进来」这类现实攻击路径 ——
考虑到两个 Tool 的入参均来自 LLM 输出（不可信），这一层是必要且足够的。

### 3.2 `WebScrapingTool` 加固

```java
URI safe = UrlSafetyValidator.validate(url);
Document document = Jsoup.connect(safe.toString())
        .timeout(10_000)
        .maxBodySize(2 * 1024 * 1024)
        .followRedirects(false)
        .get();
```

- **`followRedirects(false)` 不可省**：校验发生在「发出请求之前」，跳转发生在
  「拿到响应之后」。若允许跟随，外部 URL 返回 302 指向 `127.0.0.1` 即可绕过 3.1。
  禁用后请求不会到达内网目标；此时拿到的是重定向响应本身而非目标页面，
  具体形态（空文档或 `HttpStatusException`）以实现时实测为准 —— 两种都不构成安全风险
- 体积上限防「抓一个无限流页面把内存吃光」
- **`"Error scraping web page: "` 前缀必须保留**：`ToolCallAgent.isFailureResponse()`
  （`ToolCallAgent.java:204-212`）依前缀 `Error` 判定工具失败，进而驱动失败计数与自愈
  循环。改了前缀会**静默破坏自愈机制**

### 3.3 `ResourceDownloadTool` 加固

这是本次**最直接的漏洞**：`fileName` 未做任何校验即拼进路径
（`fileDir + "/" + fileName`），而入参由 LLM 生成 —— 传入
`../../data/chat-memory/user_1.json` 即可写到任意位置。除此之外还缺
URL 校验、体积上限与超时。

```java
Path target = SafePathResolver.resolveWithin(
        Path.of(FileConstant.FILE_SAVE_DIR, "download"), fileName);
URI safe = UrlSafetyValidator.validate(url);
// 后续建连一律使用 safe，不再引用原始 url 字符串
```

- 路径：复用现成的 `SafePathResolver.resolveWithin`（三层防护：`..` 段拒绝、
  normalize 后前缀比对、最深存在祖先 `toRealPath()` 挡符号链接逃逸）
- URL：走 3.1
- 体积上限 100MB：`HttpUtil.downloadFile` 无法限制，改为
  `HttpUtil.createGet(url).timeout(60_000).execute()` 取 `HttpResponse` 后流式读取、
  累计字节数，超限即中止并删除半成品文件
- `HttpResponse` 必须关闭（try-with-resources 或 finally），否则连接泄漏
- **`"Error downloading resource: "` 前缀必须保留**，理由同 3.2

### 3.4 评分不可信标记统一

**问题**：`RubricScorer.parseFailed` 是死标记（见 1.3）。需要先定处置策略，再加字段。

**决策**：标记 + 告警，**不改变落盘行为**。

理由：评分不可信时拒绝落盘会导致用户答题数据丢失，代价高于收益；
而静默落盘又会让不可信数据混入画像且事后无法辨识。
告警 + 可筛选的标记在两者间取平衡。

实现：

1. `UserAbilityProfile.ScoreResult`（`UserAbilityProfile.java:485`）增
   `private boolean parseFailed = false;`。该类 `implements Serializable`，
   新增基本类型字段有默认值，反序列化既有数据安全，无需变更 `serialVersionUID`

   ⚠️ 需与 `RubricScorer.RubricResult.parseFailed`（`RubricScorer.java:432`）区分：
   两者同名但分属不同类、不同评分链路。本次只统一语义，**不做合并**

2. `UserAbilityService.parseScoreResult`（`:1062`）落进 fallback 分支时
   `setParseFailed(true)`，并给现有 `log.warn("评分 JSON 解析失败，使用默认值")`
   补上标记语义

3. 两处消费点检查标记并 `log.warn` 告警，**不改变行为**：
   - `UserAbilityService.java:974`（常规评分）—— 画像更新前
   - `UserAbilityService.java:1128`（复习评分）—— 错题移除前

   更正：`EvaluationRecorder` 不涉及 `ScoreResult`，不在改动范围内

4. fallback 的默认值是 `score=3`（`UserAbilityProfile.java:488`）。
   该默认值在两处消费点的后果不同，告警文案需据实描述、避免误导：
   - `:1128` 要求 `score >= 4` 才移除错题 → 解析失败时**不会误移除**，落在安全侧
   - `:974` 以 `score >= 4 && 无弱点评` 判定「答得好」→ 解析失败会按「非优秀」处理，
     即用户答得好却得不到正向计入

5. 两处 `parseFailed` 语义统一为「LLM 原始输出不可信，当前值为兜底或首次结果」

### 3.5 `maxSteps` 文档同步

代码实际值：`BaseAgent.java:47` 默认 `10`，`YuManus.java:82` 覆写为 `8`
（注释记录调优历史 `20→8`）。四处文档仍写 20：

| 文件:行 | 现内容 | 改为 |
|---|---|---|
| `docs/upgrade/tool-system-architecture.md:359` | `maxSteps=20, 直到 FINISHED 或超限` | `maxSteps=8` |
| `docs/upgrade/tool-system-architecture.md:407` | `**步数限制：** maxSteps=20` | `maxSteps=8`，补注「BaseAgent 默认 10，YuManus 覆写为 8」 |
| `docs/upgrade/面试考察点分析.md:921` | `├── 设置 maxSteps = 20` | `maxSteps = 8` |
| `docs/upgrade/Agent包类设计详解.md:216` | `maxSteps: 20  // 比默认 10 步更宽容` | `maxSteps: 8  // 比默认 10 步更收敛` |

### 3.6 `RateLimitAspect` 风险可见化（不改逻辑）

维持 Task 13 留白，但消除「代码看起来有限流、实际没有」的危险形态：

- `RateLimit.java`（注解类型）javadoc 顶部补一段醒目提示：
  切面为 Task 13 练习骨架，当前直接放行，**标注了也不生效，勿据此认为已有防护**
- 4 处使用点（`AgentChatController:68`、`InterviewChatController:59`、`:104`、
  `UserController:96`）各补一行注释指向上述提示
- 不动 `RateLimitAspect` 的任何代码行

## 4. 测试策略

| 项 | 测试 |
|---|---|
| 3.1 | 新增 `UrlSafetyValidatorTest`：各私有/保留网段逐条断言、协议白名单、`::ffff:127.0.0.1` 绕过、无法解析的主机名、合法公网 URL 放行 |
| 3.2 | 扩充现有 `WebScrapingToolTest`：内网 URL 被拒、302 跳转不跟随 |
| 3.3 | 扩充现有 `ResourceDownloadToolTest`：`../` 与绝对路径被拒、内网 URL 被拒、超限中止 |
| 3.4 | 扩充 `RubricResultValidatorTest` 与 `UserAbilityService` 相关测试：fallback 路径 `parseFailed == true`、正常路径为 `false` |
| 3.5 | 无（文档） |
| 3.6 | 无（注释） |

## 5. 已知残留（不在本次范围）

1. **DNS rebinding 未防住** —— 见 3.1 局限说明。需 socket 层校验对端 IP 才能彻底解决。
2. **`RateLimitAspect` 仍未生效** —— 见 1.1。4 处 `@RateLimit` 是「注解已挂、
   运行时无防护」状态，本次仅做可见化标注，不改行为。
3. **`SemanticCacheService` 未接 Redis**（Task 9）—— 同属练习 TODO，
   与 2026-09-18 spec 决策一致，不动。
4. **`AdminController` 无 RBAC**（Task 14）—— 同上。
5. **`JwtAuthFilter` Token 黑名单 TODO**（Task 9 第三部分）—— 同上，
   意味着**登出后旧 token 在过期前仍然有效**。
6. **`AskedPointTracker` 未接线** —— 见 1.2。
