# 评估机制修复设计

日期：2026-09-10
状态：待实施

## 背景：当前评估机制为什么"感觉不到"

项目的评估代码是完整的（双评分器 + 基线管理 + 稳定性测试 + RAGAS），但**从未产出过一条数据**。四条证据：

1. `data/` 下只有 `chat-memory/`，没有 `evals/` —— 而 `EvaluationRecorder:72` 的 `@PostConstruct` 会无条件创建该目录。目录不存在 = 这段代码从未执行。
2. `samples/demo/AgentEvaluationDemo.java` 不在 Maven 编译范围（`pom.xml` 无 `samples` 的 `sourceDirectory` 配置），其 `main` 只打印一句提示。
3. `evaluation/baselines/*.json` 三个文件全是 `baselineDeterministicScore: -1`（"尚未人工确认"），对比功能从未生效。
4. `DeterministicScorer`、`BaselineManager` 没有 Spring 注解，容器无法装配。

### 三个基线用例全部失效

| 用例 | 问题 | 性质 |
|---|---|---|
| `搜索Spring AI教程` | 期望工具 `webSearch`，实际注册名是 `searchWeb` | 工具名错 |
| `读取代码文件并总结` | 期望工具 `fileRead`；但 `FileOperationTool` 沙箱是 `tmp/file`，**读不到项目源码**；prompt 还写着模板项目的旧路径 `com/yupi/yuaiagent` | **根本不可能跑通** |
| `知识库RAG检索` | 期望工具 `ragSearch`，实际注册名是 `searchKnowledgeBase` | 工具名错 |

> Spring AI 以 **方法名** 作为工具名。参考 `ToolRegistration.allTools()` 的真实注册项：
> `searchWeb` / `scrapeWebPage` / `readFile` / `writeFile` / `downloadResource` /
> `executeTerminalCommand` / `generatePDF` / `doTerminate` / `searchKnowledgeBase`。

### 即使跑起来也"感觉不到"的设计缺陷

| 缺陷 | 位置 | 后果 |
|---|---|---|
| 成功路径零日志 | `EvaluationRecorder.appendRecord` 只在失败时 `log.warn` | 写成功也看不见 |
| 跳过走 debug 级 | `submitAgentEval:94`、`submitRagEval:135` | 默认日志级别下完全无感 |
| 消费端只有一个 HTTP GET | `ConversationController:83` | 不主动调接口就看不到 |
| 无任何汇总/趋势 | 数据是 per-chatId 的裸 JSON 数组 | 只有数字，没有"好还是坏"的判断 |

## 目标

1. 离线回归评测一条命令跑完，控制台输出可读报告。
2. 在线质量监控的每次落盘都可见，且能一键看跨会话汇总。
3. 基线机制真正生效，改坏代码时报告能红出来。

**非目标**：离线 RAG（RAGAS）评测。它需要带 ground-truth 的评测集才能算 `contextRecall`，属于独立的 RAG 评测工作。本次只修复 RAG 在线链路的**可观测性**。

## 架构

```
                        ┌─ DeterministicScorer  （纯代码，不调 LLM，快）
   AgentTrace ──────────┤
                        └─ RubricScorer         （LLM-as-Judge，慢）
                                  │
        ┌─────────────────────────┴─────────────────────────┐
        │                                                   │
   离线 · 回归防线                                     在线 · 质量监控
   mvn spring-boot:run -Peval                        用户对话结束后
        │                                                   │
   EvalRunner 编排                                    EvaluationRecorder 异步
        │                                                   │
   控制台报告 + logs/eval/*.txt                       data/evals/{chatId}.json
                                                      GET /ai/evals/{chatId}
                                                      GET /ai/evals/summary
```

两条链路共用同一套评分器，互相独立：离线跑挂掉不影响线上，线上评分失败只记日志。

## 组件设计

### 1. `BaseAgent.getCurrentTrace()`（改）

新增一个 getter 返回成员 `currentTrace`（`BaseAgent.java:57` 已存在该字段）。

**为什么**：现有两条路径都靠"扫 `logs/traces/` 取最新文件"拿轨迹 —— `AgentEvaluationDemo:196` 和 `RealBaselineBuilderTest:219`。多用例连续跑或并发时会读到**上一次**的轨迹，静默评错。直接取成员变量既准确又便宜。

### 2. `DeterministicScorer`（改）

加 `@Component`。当前无任何 Spring 注解，容器装配不了。

### 3. `BaselineManager`（改）

加 `@Component`。新增自动建基线逻辑：

- 加载基线，若 `baselineDeterministicScore < 0` → 视为**无基线**，用本次结果落盘为基线，报告标注"🆕 已自动建立基线"
- 否则 → 对比，计算 delta

**为什么**：`-1` 这个哨兵值天然就是"尚未建立基线"，直接复用它，无需新增状态。金文件测试（golden file testing）的通行做法——首次跑建立基线，之后跑对比。

`saveBaseline` 时保留 `caseName`/`query`/`expectedBehavior` 原样，只更新分数和 `notes`（附上建立时间与模型标识）。

### 4. `EvalRunner`（新）

离线评测的唯一入口。`@Component` + `@ConditionalOnProperty(name = "qian.eval.enabled", havingValue = "true")` + `implements CommandLineRunner`。

编排流程：

```
1. baselineManager.loadAllBaselines()          读取 evaluation/baselines/*.json 作为用例集
2. for each case:
     agent = yuManusProvider.getObject()        每用例取全新实例（BaseAgent 要求从 IDLE 启动）
     agent.run(case.query)
     trace = agent.getCurrentTrace()
     det    = deterministicScorer.score(trace, case.expectedBehavior)
     rubric = rubricScorer.score(case.query, trace)
     基线对比 / 自动建立
3. 可选稳定性测试：--eval.stability=N（默认 0 = 不跑）
4. EvalReport 渲染 → 控制台 + logs/eval/报告_{timestamp}.txt
5. 退出进程
```

**触发方式**：`pom.xml` 新增 `eval` profile，设置 `spring-boot.run.profiles=eval`；`application-eval.yml` 设 `qian.eval.enabled: true`。于是 `mvn spring-boot:run -Peval` 即一键跑。

大跑（含稳定性测试）默认关闭，避免误触发大量 LLM 调用：`mvn spring-boot:run -Peval -Dspring-boot.run.arguments=--eval.stability=5`。

### 5. `EvalReport`（新）

报告模型与渲染。这是"看得见反馈"的核心交付物。

```
╔══════════════════════════════════════════════════════════════════╗
║              Agent 评估报告   2026-09-10 15:04:22                 ║
╠══════════════════════════════════════════════════════════════════╣
║ 总览  3 个用例 | 通过 2 | 未通过 1 | 平均 78.3 | 耗时 4m12s        ║
╠══════════════════════════════════════════════════════════════════╣
║ [1] 搜索Spring AI教程                                  ✅ 90/100  ║
║     确定性 90  │ Rubric 85  │ 基线 88  │ Δ +2 🟢 分数上升          ║
║     工具 searchWeb ✅  关键词 2/2 ✅  调用 1/5 ✅                 ║
╠══════════════════════════════════════════════════════════════════╣
║ [2] 文件读写往返                                       ❌ 40/100  ║
║     确定性 40  │ Rubric 0   │ 基线 80  │ Δ -40 🔴 分数下降！       ║
║     工具 writeFile ✅  readFile ❌ 缺少工具调用                   ║
║     关键词 0/1 ❌ 缺少关键词: 测试内容                            ║
╠══════════════════════════════════════════════════════════════════╣
║ [3] 知识库RAG检索                             🆕 已自动建立基线   ║
║     确定性 80  │ Rubric 78  │ 基线 (新建)                        ║
╠══════════════════════════════════════════════════════════════════╣
║ 稳定性  pass^5 = 4/5 (80%)  ❌ 不稳定（失败率 20% > 10%）         ║
╠══════════════════════════════════════════════════════════════════╣
║ 结论  🔴 1 个用例分数下降，请检查最近改动                          ║
╚══════════════════════════════════════════════════════════════════╝
```

同时写 `logs/eval/报告_{timestamp}.txt`，避免 Windows 控制台中文乱码（沿用
`RealBaselineBuilderTest:184` 已验证过的做法）。进程退出码：有任一用例下降 → `1`，否则 `0`，便于接 CI。

### 6. 重写 `evaluation/baselines/*.json`（改）

三个文件全部重写。`Baseline` 模型已包含 `caseName` + `query` + `expectedBehavior`，**用例定义与基线分数同文件**（方案 A），`AgentEvalCase` 因此成为重复模型，一并删除。

| 用例 | query | 期望工具 | 说明 |
|---|---|---|---|
| `搜索Spring AI教程` | 帮我搜一下 Spring AI 最新教程，要有代码示例的那种 | `searchWeb` (paramContains: Spring AI) | |
| `文件读写往返` | 请把"评估测试内容"写入 eval-test.txt，然后读回来确认 | `writeFile` + `readFile` | **重新设计**：原用例读项目源码在沙箱下不可能，改为沙箱内读写往返 |
| `知识库RAG检索` | Spring AI 1.0 版本有哪些新特性？ | `searchKnowledgeBase` (paramContains: Spring AI) | 依赖知识库已导入 Spring AI 文档 |

三条用例的难度分层：`文件读写往返` 完全确定性（无网络、无 KB）→ 用于稳定性测试；另两条分别覆盖联网搜索与本地 RAG。

### 7. `EvaluationRecorder`（改）

- `appendRecord` 成功后补 `log.info("📊 评估已落盘: chatId={}, type={}, 分数={}", ...)`
- 两处 `log.debug("跳过…")` 提升为 `log.info`，让"为什么没评"可见
- 新增 `summarize()`：扫描 `data/evals/*.json`，跨会话汇总各类型的评估次数、平均分、时间跨度

### 8. 在线汇总接口 `GET /ai/evals/summary`（新）

挂在 `ConversationController`（已有 `/ai/evals/{chatId}`）。返回跨会话汇总，让在线数据不必逐个 chatId 翻。

```json
{ "totalRecords": 42, "agent": { "count": 30, "avgRubric": 81.2 },
  "rag": { "count": 12, "avgContextPrecision": 0.73 }, "earliest": "...", "latest": "..." }
```

### 9. 删除冗余入口

| 文件 | 理由 |
|---|---|
| `samples/demo/AgentEvaluationDemo.java` | 不在编译范围、`main` 是空壳、与 `EvalRunner` 职责完全重复 |
| `src/test/.../RealBaselineBuilderTest.java` | 逻辑（真实 Agent 跑分 + 证据渲染）并入 `EvalRunner`；它是 `@SpringBootTest`，改文件就跟着跑、消耗真实 LLM 调用，本就该是显式触发的入口而非测试 |
| `evaluation/AgentEvalCase.java` | 与 `Baseline` 重复的模型，方案 A 下无消费者 |

## 错误处理

| 场景 | 行为 |
|---|---|
| 单个用例抛异常 | 记录该用例为失败（0 分 + 错误信息），**继续跑后续用例**，报告中标红 |
| `getCurrentTrace()` 返回 null 或未封口（`endTime == null`） | 该用例标记"轨迹不完整"，不给分，报告标注 |
| RubricScorer 调 LLM 失败 | 重试耗尽后 rubric 记 0，确定性分照常保留，报告标注"Rubric 评分失败" |
| 无任何基线文件 | 报告提示"未找到用例"，正常退出（非崩溃） |
| 在线评估失败 | 只 `log.warn`，绝不向上抛 —— 沿用现有的"不阻塞、不改变用户请求"定位 |

## 测试策略

**核心约束：所有单测不调用 LLM、不访问网络。** 涉及 LLM 的路径用假的 `AgentTrace` 和 stub 评分器驱动。

| 测试 | 覆盖 |
|---|---|
| `DeterministicScorerTest` | 工具命中/未命中、大小写不敏感、参数包含、关键词缺失逐条扣分、调用次数超标、满分与 0 分下限 |
| `BaselineManagerTest` | `-1` 哨兵 → 自动建基线并落盘；已有基线 → 正确算 delta 与判定语；文件不存在 → 返回 null 不抛 |
| `EvalReportTest` | 上升/下降/持平的结论渲染；通过率统计；退出码推导 |
| `BaselineToolNameTest` | **基线 JSON 里每个 `toolName` 都必须是 `ToolRegistration` 实际注册的工具** |

最后一条是防回归的关键：本次发现的 bug 正是"基线里写了不存在的工具名"，而这会静默地让所有用例永久失败。该测试把工具名集合作为断言源，任何新增/改名都会立刻红。

测试先行（TDD）：先写失败的测试，再实现到通过。

## 验证方式

1. `mvn test` —— 上述单测全绿，且 `PackageDependencyTest` 不被破坏
2. `mvn spring-boot:run -Peval` —— 控制台出现报告，`logs/eval/` 有报告文件，`evaluation/baselines/*.json` 的 `-1` 被真实分数替换
3. 改动 `DeterministicScorer` 的任一扣分常量后重跑 —— 报告应显示分数下降且退出码为 1
4. 启动应用真实对话一轮 —— 控制台出现 `📊 评估已落盘` 日志，`data/evals/` 出现文件，`GET /ai/evals/summary` 返回汇总

第 3 条是这次修复是否真正生效的判据：**基线机制能红出来，才算修好。**
