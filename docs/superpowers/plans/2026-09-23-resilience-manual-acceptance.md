# 容错方向 · 端到端验收

**日期**：2026-09-23
**对应计划**：`2026-09-22-resilience-chain.md` 的「完成标准」一节
**验收方式**：真实启动应用 + 真实调用 LLM

---

## 1. 怎么跑

```bash
cd D:/code/qian-ai-agent
JAVA_HOME=/c/Users/huahu/.jdks/openjdk-25.0.2 \
SPRING_AI_OPENAI_BASE_URL=http://127.0.0.1:1 \
./scripts/eval-regression.sh
```

把主模型地址指到一个不可达端口，逼出降级路径；评测跑起来后真实调用 LLM，
日志里就能看到整条韧性链的行为。

> 前置：PostgreSQL / MySQL / Neo4j / Redis 都要在跑（`docker compose up -d`），
> 且 `.env` 里的 API Key 是真实值。

---

## 2. 验收结果（2026-09-23 实测）

| # | 计划要求 | 结果 |
|---|---|---|
| 1 | 日志出现 `⚠️ 主模型调用失败，准备降级` | ✅ 出现 2 次 |
| 2 | 日志出现 `✅ 已降级至备模型并成功返回` | ❌ **未出现** —— 见第 3 节的阻塞问题 |
| 3 | 主备都坏时用户收到 `[ERROR] AI 服务暂时不可用`，日志有 ERROR 级记录 | ✅ `❌ 备模型也失败，返回兜底话术` 出现 3 次 |
| 4 | 重启服务后面试官仍记得项目描述 | ⬜ 未验收（需要前端交互） |
| 5 | 断线时日志出现 `🔌 客户端断线` | ⬜ 未验收（需要真实浏览器断线） |
| 6 | 未改动任何 `🎯 Task N` 骨架文件 | ✅ 全程 `grep "🎯 Task"` 零命中 |

**实测到的日志（原样摘录）**：

```
INFO  ResilientChatModelConfig : ✅ 备选 ChatModel 已就绪: DashScopeChatModel
WARN  ResilientChatModel       : ⚠️ 主模型调用失败，准备降级:
      err=java.util.concurrent.TimeoutException: TimeLimiter 'primary-timeout' recorded a timeout exception.
ERROR ResilientChatModel       : ❌ 备模型也失败，返回兜底话术:
      err=java.util.concurrent.TimeoutException: TimeLimiter 'fallback-timeout' recorded a timeout exception.
ERROR ResilientChatModel       : ❌ 备模型也失败，返回兜底话术:
      err=io.github.resilience4j.circuitbreaker.CallNotPermittedException:
      CircuitBreaker 'fallback-cb' is OPEN and does not permit further calls
```

**这一轮顺带证实了三件事**（都是此前只靠代码推断、没有运行证据的）：

1. **`dashscopeChatModel` 这个 bean 名是真的** —— 交接文档 §5.1 第 2 项标为「未经运行验证」，
   现在 `@Qualifier("dashscopeChatModel")` 解析成功，日志打出「备选 ChatModel 已就绪」。
2. **备模型与主模型的熔断计数确实互相独立** —— 主模型超时的同时，
   备模型走的是自己的 `fallback-timeout` / `fallback-cb`。
3. **四级链真的在跑** —— `TimeLimiter 'primary-timeout'` 说明超时是被韧性链的
   TimeLimiter 掐断的（而不是底层 HTTP 客户端自己超时）；
   `CircuitBreaker 'fallback-cb' is OPEN` 说明熔断器状态机真的会转。

---

## 3. ⚠️ 阻塞问题：备模型当前不可用

**现象**：降级被正确触发了，但备模型自己也是坏的。

**根因**：DashScope 侧返回

```
HTTP 400 - {"code":"InvalidParameter","message":"Model not exist."}
```

直接拿 `application-local.yml` 里的 key 探测 DashScope 的文本生成接口，
`qwen-plus` / `qwen-turbo` / `qwen-max` / `qwen2.5-7b-instruct` **全部**返回：

```
{"code":"Arrearage"}
```

**`Arrearage` = 账户欠费。** 所以：

- 主备降级的**机制**是对的（结构、日志、熔断、兜底全都在工作）
- 但**备模型这条腿现在是断的** —— 不是代码问题，是 DashScope 账户余额问题
- 因此「主模型挂了用户仍能拿到回答」这条**没能验收通过**：
  当前实际行为是主备都失败 → 用户收到 `[ERROR] AI 服务暂时不可用`

**行动项（用户侧）**：
1. 给 DashScope 账户充值，确认 `qwen-plus` 这个模型名在账号下可用
   （日志报的是 `Model not exist`，与批量探测到的 `Arrearage` 不一致，
   充值后建议再跑一次本清单确认模型名也对）
2. 充值后重跑第 1 节那条命令，确认第 2 项能出现 `✅ 已降级至备模型并成功返回`

**注意**：这个配置错误藏了很久，因为 `application.yml` 里 `spring.ai.dashscope.chat.options.model=qwen-plus`
虽然早就配好了，但**这条路径从未被真正使用过** —— 直到这次端到端验收。

---

## 4. 观察到的次生问题（未处理，仅记录）

**双层重试**：日志里出现了 Spring AI 自带的 `SpringAiRetryAutoConfiguration: Retry error. Retry count: 2`，
即 **Spring AI 的 RetryTemplate 与我们的 ResilienceChain 各有一层重试**。
两层叠加意味着一次用户请求可能真的打出去 3×3=9 次下游调用。
当前没问题（两层的重试次数都不大），但要清楚它存在。

---

## 5. 还没做的两条

| 项 | 为什么没做 | 怎么补 |
|---|---|---|
| 重启后项目描述还在 | 需要前端交互确认 | 起应用 → 发一条含项目描述的消息 → 重启 → 再问一次，看面试官是否还记得 |
| SSE 断线被识别为 CANCEL | 需要真实浏览器关标签页 | 起前端 → 发起一次面试对话 → 流未结束时关掉标签页 → 看日志是 `🔌 客户端断线` 还是 `ON_COMPLETE`。<br>**若是 `ON_COMPLETE`，说明 Tomcat 异步链路没有把断线转成 cancel 信号，Task 7 的修复等于没生效** —— 那才是真正的风险点 |
