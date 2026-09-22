# Agent 护栏 / 容错 / 测评三向机制整合

- 日期：2026-09-22
- 分支：`feat/resilience-guardrails`（当前工作区在 `feat/eval-harness`，含未提交的 Agent 层重构）
- 状态：设计已确认，待实现

## 1. 背景

对照一批开源参考项目（`D:\code\agent项目\`）盘点本项目在
「约束与护栏 / 自愈与容错 / Agent 测评」三个方向的机制完备度，落定为 **9 项改动**：

- **约束与护栏** 2 项：输入护栏、输出护栏（均为新增）
- **自愈与容错** 3 项：韧性链（新增）、`ActiveSpecManager` 落盘、SSE 断流补漏
- **Agent 测评** 4 项：建基线、修判定缺陷、扩用例、接 CI

### 1.1 与既有 spec 的决策一致性（重要）

`2026-09-18` spec 第 1.1 节与 `2026-09-19` spec 第 1.1 节已两次确认：

> 代码中存在 30 处 `🎯 Task N`（Task 1–14）标记，是作者自设的练习。
> **这些一律不改。修复它们等于代做作业，摧毁项目的学习价值。**

本次梳理**最初拟填掉其中 4 个骨架**（`RateLimitAspect` / `JwtAuthFilter` Token 黑名单 /
`AdminController` RBAC / `SemanticCacheService` 接 Redis），并据此征询过是否实现。
**该征询在未读到上述两份 spec 的情况下提出，选项把它们描述成「待修缺口」而非「自设练习」，
描述失实。** 补齐背景后确认：**维持原决策，本次一个骨架都不动。**

> ⚠️ 与 `2026-09-19` spec 1.1 节记录的是同一类错误，且是第二次发生。
> 后续凡触及 `🎯 Task N` 的梳理，须**先读这两份 spec** 再决定是否征询。

运行时后果（已知取舍，本次不改变）：`RateLimitAspect.handleRateLimit` 仍直接
`joinPoint.proceed()`，因此 `AgentChatController:69`、`InterviewChatController:60,106`、
`UserController:97` 四处 `@RateLimit` 全部不生效。

### 1.2 参考项目的机制映射

本次每项设计均对应一个已落地的开源参考，避免「凭感觉造机制」：

| 本项目设计 | 参考项目 | 借鉴的具体机制 |
|---|---|---|
| 3.1 Advisor 输入护栏 | `NVIDIA/NeMo-Guardrails` | rails 分层：输入 rail 与输出 rail 分离，命中输入 rail 即拦截、不进入下游 |
| 3.1 规则集组织 | `protectai/llm-guard` | 可组合 scanner 管道：每条规则独立、可单测、按序短路 |
| 3.2 韧性链 | `resilience4j/resilience4j` | `Retry` / `CircuitBreaker` / `TimeLimiter` / `Bulkhead` 的装饰器语义 |
| 3.2 降级链 | `temporalio/temporal` | 「主路径失败不退化为报错，而是退化到次优路径」的可用性优先原则 |
| 3.5 基线回归 | `UKGovernmentBEIS/inspect_ai` | 评测必须可复现、可对比，且有明确的通过/失败出口 |
| 3.6 方差处理 | `sierra-research/tau-bench` | LLM 输出有方差，单次分数差不等于回归，需阈值与重复实验 |

## 2. 范围

**做**：

1. 新增 `InputGuardrailAdvisor` —— 输入侧 prompt 注入拦截（3.1）
2. 新增 `OutputGuardrailAdvisor` —— 输出侧系统提示词泄漏与 PII 拦截（3.2）
3. 新增 `ResilientChatModel` —— resilience4j 四级韧性链 + DeepSeek→Qwen 降级（3.3）
4. `ActiveSpecManager` 落盘持久化（3.4）
5. 面试主路径 SSE 断流中止，对齐 Agent 路径（3.5）
6. 建立真实评测基线，消除 `-1` 哨兵（3.6）
7. 修 `EvalReport.hasRegression()` 漏判 Rubric 的缺陷（3.7）
8. 扩充评测用例集（3.8）
9. 回归脚本接 CI（3.9）

**不做**（明确排除）：

- 全部 `🎯 Task N` 练习骨架 —— 见 1.1，含本次触及的 4 个
- 实现限流逻辑 —— 同上，`RateLimitAspect` 维持 Task 13 留白
- 分布式多实例改造 —— `FileBasedChatMemory` 自陈不支持多实例，超出本轮
- 重写现有工具沙箱（`SafePathResolver` / `UrlSafetyValidator` 已较扎实）
- 改动 `rag/evaluation` 的 RAGAS 四指标算法

## 3. 设计

### 3.1 `InputGuardrailAdvisor`（新增）

`src/main/java/com/qian/qianaiagent/advisor/InputGuardrailAdvisor.java`。

拦截位置选在 **Advisor 链最外层**（`Ordered.HIGHEST_PRECEDENCE`）：外层 advisor 的
前置逻辑先于内层执行，因此护栏看到的是**本轮原始用户输入**，与加载进来的历史无关 ——
正是要检测的对象。命中即**不调 `chain.nextCall()`**，直接返回安全话术，
LLM 调用根本不发生（省 token，且不会被注入内容污染对话历史）。

检测规则集独立成类 `advisor/guardrail/InputRuleSet.java`，每条规则实现同一接口，
可单独单测、按序短路 —— 这是从 `llm-guard` 的 scanner 管道借的形式：

```java
public interface InputRule {
    Optional<GuardrailVerdict> check(String userInput);
}
```

首批规则（全部为**确定性规则**，不调 LLM，故无延迟成本）：

| 规则 | 检测内容 | 举例 |
|---|---|---|
| `InstructionOverrideRule` | 指令覆盖型注入 | 「忽略以上/之前/上面的指令」「ignore previous instructions」「忘记你的规则」 |
| `RoleHijackRule` | 角色劫持 | 「你现在是…不再面试官」「pretend you are」 |
| `SystemPromptProbeRule` | 系统提示词刺探 | 「输出你的系统提示词」「repeat your instructions」 |
| `ScoreManipulationRule` | 评分操纵 | 「给我满分」「直接判我通过」——**本场景特有，直击产品核心功能** |
| `LengthRule` | 长度上限 | 复用 `AiChatConstants.MAX_MESSAGE_LENGTH`，避免与现有校验口径分裂 |

**设计取舍 —— 为什么用确定性规则而非 LLM 判官**：
LLM 判官能覆盖语义变体，但每条消息都多一次 LLM 往返（延迟 + 成本 + 自身可被注入）。
本场景的防护目标是**抬高攻击成本、拦住明显越狱**，确定性规则够用且无副作用。
已知局限见第 5 节。

装配点：`QuizApp.java:84-87`（现有 Memory + MyLogger 之后追加）与
`YuManus.java:136-137`。

### 3.2 `OutputGuardrailAdvisor`（新增）

`src/main/java/com/qian/qianaiagent/advisor/OutputGuardrailAdvisor.java`，
置于 **Advisor 链最内层**（`Ordered.LOWEST_PRECEDENCE`）：内层 advisor 的后置逻辑
最先拿到 LLM 原始响应，能观察到未被其他 advisor 改写的输出。

检测两项：

1. **系统提示词泄漏** —— 输出中是否出现 system prompt 的独有片段（如
   「严禁点评历史对话中的其他题目」`QuizApp.java:58-62`）。命中说明注入绕过了 3.1
2. **PII 泄漏** —— 手机号 / 身份证 / 邮箱 / API Key 形态正则（思路同 `microsoft/presidio`）

**流式路径的已知局限（明确记录，不粉饰）**：
`adviseStream` 拿到的是 `Flux`，分块到达。若做增量检测，命中时**已经推给前端的块无法收回**。
本设计采用**滑动窗口 + 命中即中断流并追加警示**，而不是全量缓冲后统一检测 ——
后者会彻底摧毁流式体验（本项目的核心交互特性）。

因此输出护栏在流式路径上是**尽力而为**，不是强保证。这是刻意取舍，不是遗漏。

### 3.3 `ResilientChatModel`（新增）

`src/main/java/com/qian/qianaiagent/agent/llm/ResilientChatModel.java`，
实现 `ChatModel`，装饰主模型并挂降级链。

**依赖**：`pom.xml` 新增 `io.github.resilience4j:resilience4j-spring-boot3`
（版本随 Spring Boot 3.4.4 的 dependencyManagement，无需指定）。选 Spring Boot 集成版
而非裸库，是为了拿到 Actuator 端点 —— **熔断器状态可观测是本次的演示重点**。

**装饰顺序**（外层 → 内层）：

```
主模型韧性链：  Retry(≤3次, 指数退避+抖动) → CircuitBreaker → Bulkhead → TimeLimiter(30s) → 主模型
备模型韧性链：  同上（独立熔断器 / 独立重试计数）                                        → 备模型

主链失败或熔断器 OPEN ──→ 备链 ──→ 仍失败 ──→ 记录 ERROR 日志 + 返回友好话术
```

**顺序理由（本次最容易做错的一处，逐条核过）**：

- **`Retry` 必须在最外层**。若把 `TimeLimiter` 放在 `Retry` 之外，超时会直接穿透出去、
  根本不会被重试 —— 而「超时」恰恰是最该重试的故障之一
- **`TimeLimiter` 在最内层**，使 30s 超时**按单次尝试**计算，而不是把三次重试的总时长算作一次
- **`CircuitBreaker` 夹在中间**：每次重试尝试单独计入熔断统计，避免「重试成功掩盖了
  底层已经在持续失败」这一情况被熔断器忽略
- 此顺序与 resilience4j 官方推荐的装饰顺序一致

**`TimeLimiter` 的载体问题（实现时必须处理）**：
`ChatModel.call()` 是**阻塞**方法，而 resilience4j 的 `TimeLimiter` 要求被装饰的方法返回
`CompletionStage` / `CompletableFuture`。两条路：

- **方案 A（选此）**：用 Java 21 虚拟线程执行器
  （`Executors.newVirtualThreadPerTaskExecutor()`）把阻塞调用包成 `CompletableFuture`。
  本项目已是 Java 21，虚拟线程代价极低，且不与现有线程池争资源
- **方案 B**：改在 HTTP 客户端层设超时。更彻底（能同时覆盖建连与读超时），
  但 Spring AI 的 `chat.options` 不暴露该配置，需自定义 `RestClient.Builder`，侵入性更大

选 A 的理由：改动局部、可单测，且与韧性链其余部分同构（都在 `ResilientChatModel` 内）。

**各组件职责**：

- **TimeLimiter 30s**：当前 LLM 调用**完全没有超时配置**（`application.yml:44-58` 的
  `chat.options` 只有 `model`），单次卡死最长阻塞 5 分钟（`spring.mvc.async.request-timeout`）。
  这是本次最实在的一处修复
- **Retry**：只对可恢复异常重试 —— 阈值判定**复用 `ToolCallAgent.java:133-156` 已有的
  「timeout / 429 / rate / limit / connection」字符串判定**，不另造一套口径。
  指数退避 + 抖动，上限 3 次
- **CircuitBreaker**：失败率阈值触发熔断，**快速失败**而不是继续打已经挂掉的 DeepSeek
- **Bulkhead**：限制并发 LLM 调用数，防雪崩
- **降级链**：主链失败 → 切 `qwen-plus`（`application.yml` 已配好，此前只用于
  Embedding、未用于对话）。**备链独立**，否则主模型的故障计数会污染备模型
- **全挂时的返回值**：记录 `ERROR` 日志 + 返回友好话术，**不向上抛异常** ——
  对齐 `QuizApp.java:584` 现有的 `[ERROR] AI 服务暂时不可用` 行为，保持前端契约不变。
  代价是调用方无法感知失败，故日志为唯一线索，级别必须为 `ERROR` 而非 `WARN`

**装配**：显式 `@Qualifier` 注入以避免歧义 ——
主模型 `@Qualifier("openAiChatModel")`，备模型 `@Qualifier("dashscopeChatModel")`。

> ⚠️ 实现时需核实：**DashScope starter 注册的 ChatModel bean 实际名称**
> （已确认 `spring.ai.dashscope.chat.options.model=qwen-plus` 已配置，
> 但 bean 名未经运行验证，不能想当然写 `dashscopeChatModel`）。
>
> 关于 `@Primary` 的影响面：已核查全项目测试**无任何 `@MockBean` / `@MockitoBean`**
> （46 个测试类、271 个 `@Test` 方法均不依赖 Spring 容器注入 ChatModel），
> 因此新增 `@Primary` bean 影响现有测试的风险**较低**。但 `QuizApp`、`RubricScorer`
> 等生产代码会受影响 —— 这正是本节的目的（让所有 LLM 路径自动获得保护），
> 需确认无调用点依赖「拿到的必须是原始 DeepSeek 模型」这一假设。

### 3.4 `ActiveSpecManager` 落盘

现状：`ActiveSpecManager.java:34` 为纯内存 `ConcurrentHashMap`，类注释 `:23-24` 明确
「不做持久化」。后果是**服务重启后面试官忘记候选人项目背景**，属用户可直接感知的功能缺陷。

改为文件持久化，**复用现有约定而非另造**：

- 路径 `.active-specs/{safeChatId}.json`，与 `.quiz-cursor/`、`.review-cursor/` 同模式
- 文件名安全化复用 `ChatIdValidator.sanitize()`（`util/ChatIdValidator.java:41`）
- 路径根复用 `StorageProperties`（`config/StorageProperties.java`）
- 写盘时机对齐 `updateSpec()`；**同步写**（该方法不在热路径上，无需异步）
- 加载按 chatId **lazy 读取**，不 `@PostConstruct` 预加载（会话数不可控），
  对齐 `SequentialRotationService.getOrCreateCursor()`（`:192-239`）的四级恢复模式
- 写盘用**临时文件 + `ATOMIC_MOVE`**，对齐 `FileBasedChatMemory` 的做法；
  不沿用 `SequentialRotationService.saveCursor()`（`:587-594`）的直接覆盖写 ——
  那是既有的非原子写，本次不扩散该模式

`IDLE_TTL`（2 小时）语义不变：过期条目同时从内存与磁盘清理。

### 3.5 面试主路径 SSE 断流中止

`BaseAgent.java:350-358` 的 `safeSend()` 在发送失败时返回 `false`，调用方
（`:230-232`）据此抛 `IllegalStateException` 中止执行，类注释说明这是为修复
「在死连接上跑完整个循环」而加。

**面试主路径没有等价逻辑**：`InterviewChatController.java:84-92` 仅在 `doOnError` 打日志。
客户端断线后服务端会继续跑完，白烧 token。

改法：对齐 Agent 路径，在 `doOnNext`/发送处检测连接可用性，断开即中止上游流。
**范围严格限定为「对齐既有做法」，不引入重连 / 续传机制**（`Last-Event-ID` 续传是另一个量级）。

### 3.6 建立真实评测基线

现状：`evaluation/baselines/` 4 个用例的 `baselineDeterministicScore` /
`baselineRubricScore` **全为 `-1` 哨兵**，`BaselineManager.compareWithBaseline()`
遇 `-1` 走 `autoEstablishBaseline()`，即**首次运行自动写入当次结果、未经人工确认**。
结果：回归判定形同虚设。

步骤：

1. 以**当前代码**跑一次 `./mvnw spring-boot:run -Peval`
2. 人工检查 4 条用例的 trace 与评分是否合理（不是照单全收）
3. 将确认后的分数写回基线 JSON，替换 `-1`
4. 复跑一次确认 `hasBaseline=true` 且 `delta == 0`

⚠️ 该步骤需要真实 API Key + PostgreSQL(PGVector) + Redis，非纯离线操作。

### 3.7 修 `EvalReport.hasRegression()` 漏判

现状（`EvalReport.java:52-57`）：

```java
return c != null && c.isHasBaseline() && c.getDeltaDeterministic() < 0;
```

**只看确定性分数**，Rubric（LLM-as-judge）分数下降**完全不算回归**。
而 Rubric 恰恰是评估回答质量的主要手段 —— 意味着「回答质量明显变差」这件事，
现有回归判定**测不出来**。

**不能简单加上 `deltaRubric < 0`**：Rubric 由 LLM 打分，本身有方差，
哪怕代码没动，重跑也可能掉几分。直接用 `< 0` 会产生大量误报，比漏判更糟 ——
噪声大的报警等于没有报警。

改法：引入**阈值**，deterministic 用严格 `< 0`（规则打分无方差），
Rubric 用可配置阈值（默认 10 分，满分 100）：

**写计划时发现的追加事实（本节据此修订）**：`BaselineManager.compareWithBaseline()`
第 197 行的判定是

```java
if (deltaDeterministic < 0 || deltaRubric < 0) {
    verdict = "🔴 分数下降！改坏了，请检查最近的改动。";
}
```

即 **`BaselineManager` 已经把 rubric 纳入判定，而 `EvalReport.hasRegression()` 没有**。
两者的口径不一致，后果比原判断更难看：一次 rubric 掉 1 分的运行，
**报告正文会逐条打印「🔴 分数下降！改坏了」，而 `conclusion()` 说「🟢 无回归」、
退出码为 0** —— 一份报告里自相矛盾。

因此判定规则必须**收拢到一处**，不能在两处各写一份。方案：新增
`evaluation/RegressionPolicy.java` 持有阈值与判定，`EvalReport` 与
`BaselineManager` 都调它。

```java
/** Rubric 回归阈值。规则打分无方差，用严格 < 0；Rubric 由 LLM 打分，
 *  同版本重跑即有自然波动，必须给容差，否则报警全是噪声。 */
private static final int RUBRIC_REGRESSION_THRESHOLD = 10;

/** 确定性分数任何下降都算回归（规则打分无方差） */
public static boolean isDeterministicRegressed(int deltaDeterministic) { ... }

/** Rubric 需降幅超过阈值才算回归 */
public static boolean isRubricRegressed(int deltaRubric) { ... }
```

**本轮不做外部配置**：只在 `RegressionPolicy` 内定义常量，不引入配置层 ——
为一个数值加一层配置的收益低于其维护成本。若后续确需按环境调参，再迁到
`application.yml` 的 `qian.eval.*` 下。

同时 `EvalReport.conclusion()`（`:65-75`）与 `BaselineManager` 的 verdict 文案
**都改为调用 `RegressionPolicy`**，消除「报告正文说改坏了、结论说无回归」的矛盾。

> **10 分的取值依据，以及一个前置依赖**：该阈值须在 3.6 建立基线后，
> 用 `StabilityTester`（已实现，`pass^k`）跑重复实验观测**同版本**分数的自然波动幅度，
> 用实测波动定阈值，而非拍脑袋。
>
> ⚠️ **若实测波动接近或超过 10 分，则本节方案不成立** —— 那说明 Rubric 打分本身
> 不稳定，此时正确做法是先解决打分稳定性（如提高 `StabilityTester` 重复次数取中位数），
> 而不是调大阈值掩盖噪声。**阈值调大到能吞下噪声的程度，就等于关掉了这项检查。**

### 3.8 扩充评测用例集

现状 4 条（`evaluation/baselines/`），其中仅「对比 SpringAI 与 LangChain4j」一条
覆盖 Plan-and-Execute 分支（触发条件见 `YuManus.needsPlanning()`）。

新增用例（覆盖未覆盖的路径与边界）：

| 用例 | 覆盖点 |
|---|---|
| 多工具串联 | 一次任务中先后调用 ≥2 个工具，验证 trace 顺序与 `maxSteps` 预算 |
| 工具失败自愈 | 构造必然失败的工具入参，验证 `ToolCallAgent` 失败计数与自愈提示生效 |
| 边界：空/超长输入 | 空串、超 `MAX_MESSAGE_LENGTH` 入参，验证 3.1 的 `LengthRule` |
| 注入拦截 | 构造注入话术，验证被 3.1 拦截且**未产生 LLM 调用**（trace 无 `LLM_CALL`） |

最后一条尤其重要：**它让护栏机制本身成为可回归验证的对象** ——
护栏失效会被评测测出来，而不是靠人肉发现。

### 3.9 回归脚本接 CI

- 新增 `scripts/eval-regression.sh`：调 `-Peval`，消费 `EvalReport.exitCode()`
  （`EvalReport.java:60`，有回归→1，已就绪但**无人消费**），非 0 即失败退出
- 新增 `.github/workflows/eval.yml`（仓库当前无任何 CI 配置）

**诚实说明 CI 的代价**：评测要跑真实 LLM，需要
PostgreSQL(PGVector) + Redis service container **以及真实 API Key**。
后者必须走 repo secrets，且**每次 CI 运行都会真实消耗 token 额度**。
若不便承担，可只接 `scripts/eval-regression.sh` 作为**发版前人工执行的检查项** ——
脚本本身已能提供「改坏了会明确失败」的价值，CI 只是自动化外壳。

## 4. 测试策略

全部测试**不调真实 LLM、不访问网络**（与现有 46 个测试类 / 271 个 `@Test` 方法的约定一致；
`AgentLoopTest` 即为「mock ChatClient 跑控制流」的范例，3.3 的测试照此办理）。

| 项 | 测试 |
|---|---|
| 3.1 | 新增 `InputGuardrailAdvisorTest`：每条规则各一组正/反例；命中时断言**未调用下游 chain**；`LengthRule` 与 `AiChatConstants` 口径一致 |
| 3.2 | 新增 `OutputGuardrailAdvisorTest`：系统提示词片段泄漏被检出；各 PII 形态正/反例 |
| 3.1+3.2 | 新增 Advisor 链**顺序断言测试**：验证输入护栏最外、输出护栏最内（顺序错了护栏形同虚设，必须有测试锁住） |
| 3.3 | 新增 `ResilientChatModelTest`：用 stub `ChatModel` 注入超时/429/5xx，断言重试次数、熔断打开、降级切到备模型、全挂时返回友好话术 |
| 3.4 | 扩充 `ActiveSpecManager` 测试：写入后重建实例仍能读到；`IDLE_TTL` 过期清理；文件名安全化 |
| 3.5 | 无（需真实断连，靠人工验证） |
| 3.6 | 无（人工确认基线） |
| 3.7 | 扩充 `EvalReportTest`：deterministic 降 1 分 → 回归；rubric 降 5 分 → 不回归；rubric 降 15 分 → 回归 |
| 3.8 | 新增用例 JSON + 扩充 `BaselineToolNameTest` 的工具名守卫 |
| 3.9 | 无（脚本） |

## 5. 已知残留（不在本次范围）

1. **限流仍未生效** —— `RateLimitAspect` 是 Task 13 练习骨架，本次维持不改（见 1.1）。
   4 处 `@RateLimit` 仍是「注解已挂、运行时无防护」，仅靠 `2026-09-19` spec 3.6 节的
   注解文档警示。
2. **无 RBAC** —— `AdminController` 是 Task 14 练习骨架。当前任何已登录用户
   可访问 `/admin/*`（接口本身是空壳，无实际危害，但实现时即构成越权）。
3. **JWT 无黑名单** —— `JwtAuthFilter:148` 是 Task 9 第三部分。登出后旧 token
   在过期前仍然有效。
4. **输入护栏是确定性规则** —— 3.1 明确取舍。语义变体注入可绕过；
   要覆盖需上 LLM 判官，代价见该节。**不要把它当成完备的注入防护。**
5. **输出护栏在流式路径是尽力而为** —— 见 3.2，已推给前端的块无法收回。
6. **`SemanticCacheService` 未接 Redis** —— Task 9 骨架，不改。
7. **多实例部署不支持** —— `FileBasedChatMemory.java:50-56` 自陈。本次新增的
   `ResilientChatModel` 熔断状态与 `ActiveSpecManager` 缓存**均为 JVM 内**，
   多实例下各算各的。
8. **DNS rebinding 未防住** —— 承接 `2026-09-19` spec 3.1 节的既有残留，本次未触及。
