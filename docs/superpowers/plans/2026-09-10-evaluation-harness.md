# 评估机制修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让项目里从未产出过数据的评估机制真正跑起来 —— 离线回归评测一条命令出报告，在线质量监控每次落盘可见、可汇总。

**Architecture:** 两条独立链路共用同一套评分器。离线链路新增 `EvalRunner`（`CommandLineRunner`，由 `qian.eval.enabled` 门控）编排"读基线用例 → 跑 Agent → 双评分 → 对比/建立基线 → 渲染报告"；在线链路给已有的 `EvaluationRecorder` 补齐日志与跨会话汇总。用例定义与基线分数同存于 `evaluation/baselines/*.json`（金文件形态，`-1` 为无基线哨兵）。

**Tech Stack:** Java 21、Spring Boot 3.4.4、Spring AI 1.0、Lombok、Jackson、JUnit 5、Maven（toolchains + profile）

**规格文档:** `docs/superpowers/specs/2026-09-10-evaluation-harness-design.md`

---

## 背景速览（给零上下文的工程师）

这个项目有一套完整的评估代码，但从未产出过任何数据。原因有四个：

1. `evaluation/baselines/*.json` 三个基线文件里的工具名全是错的（`webSearch`/`fileRead`/`ragSearch`），而 Spring AI 以**方法名**为工具名，实际注册的是 `searchWeb`/`readFile`/`searchKnowledgeBase`。所以这三条用例即使跑起来也**全部判失败**。
2. 第二条用例（读代码文件）**根本不可能跑通** —— `FileOperationTool` 的沙箱是 `tmp/file`，读不到项目源码。
3. `DeterministicScorer`、`BaselineManager` 没有 Spring 注解，容器装配不了。
4. 唯一的编排类 `samples/demo/AgentEvaluationDemo.java` 不在 Maven 编译范围（`pom.xml` 没有配 `samples` 的 `sourceDirectory`）。

在线侧（`EvaluationRecorder`）已经接进 Controller，但 `data/evals/` 目录从未被创建过，且成功路径零日志。

**关键约定：**
- 工作目录是仓库根目录，所有相对路径（`evaluation/`、`logs/`、`data/`）都相对它。
- 测试全部不调 LLM、不访问网络。涉及 Agent 的路径只在 `EvalRunner` 里，由手工命令触发。
- 现有测试风格：JUnit 5、类与测试方法均为 package-private、方法名用英文 camelCase。

---

## 文件结构

**新建：**

| 文件 | 职责 |
|---|---|
| `src/main/java/com/qian/qianaiagent/evaluation/EvalRunner.java` | 离线评测唯一入口：编排、报告输出、进程退出码 |
| `src/main/java/com/qian/qianaiagent/evaluation/EvalReport.java` | 报告数据模型 + 控制台渲染 |
| `src/main/resources/application-eval.yml` | eval profile 配置 |
| `src/test/java/com/qian/qianaiagent/evaluation/DeterministicScorerTest.java` | 评分器单测 |
| `src/test/java/com/qian/qianaiagent/evaluation/BaselineManagerTest.java` | 基线管理与自动建基线单测 |
| `src/test/java/com/qian/qianaiagent/evaluation/EvalReportTest.java` | 报告渲染与退出码单测 |
| `src/test/java/com/qian/qianaiagent/evaluation/BaselineToolNameTest.java` | **工具名守卫测试**：基线里的工具名必须是真实注册的 |

**修改：**

| 文件 | 改动 |
|---|---|
| `src/main/java/com/qian/qianaiagent/agent/BaseAgent.java` | 新增 `getCurrentTrace()` |
| `src/main/java/com/qian/qianaiagent/evaluation/DeterministicScorer.java` | 加 `@Component` |
| `src/main/java/com/qian/qianaiagent/evaluation/BaselineManager.java` | 加 `@Component`、自动建基线、`ComparisonReport` 加 `newBaseline` 字段 |
| `src/main/java/com/qian/qianaiagent/evaluation/EvaluationRecorder.java` | 补日志、新增 `summarize()` |
| `src/main/java/com/qian/qianaiagent/controller/ConversationController.java` | 新增 `GET /ai/evals/summary` |
| `pom.xml` | 新增 `eval` profile |
| `evaluation/baselines/*.json`（3 个） | 全部重写 |

**删除：**

| 文件 | 理由 |
|---|---|
| `samples/demo/AgentEvaluationDemo.java` | 不在编译范围、`main` 是空壳、被 `EvalRunner` 取代 |
| `src/test/java/com/qian/qianaiagent/evaluation/RealBaselineBuilderTest.java` | 逻辑并入 `EvalRunner`；它是 `@SpringBootTest`，本就不该是测试 |
| `src/main/java/com/qian/qianaiagent/evaluation/AgentEvalCase.java` | 与 `Baseline` 重复的模型，删除上面两个后无消费者 |

---

## Task 1: BaseAgent 暴露当前轨迹

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/agent/BaseAgent.java`

**为什么：** `currentTrace`（`BaseAgent.java:57`）已是成员字段，但外部拿不到。现有代码只能靠"扫 `logs/traces/` 取最新文件"绕过，多用例连续跑时会读到上一次的轨迹，静默评错。

- [ ] **Step 1: 确认字段位置**

运行：
```bash
grep -n "private AgentTrace currentTrace" src/main/java/com/qian/qianaiagent/agent/BaseAgent.java
```
预期输出：`57:    private AgentTrace currentTrace;`

- [ ] **Step 2: 在 `cleanup()` 方法之前插入 getter**

在 `BaseAgent.java` 中找到 `protected void cleanup() {` 这一行，在它**上方**插入：

```java
    /**
     * 当前这次执行的轨迹（{@link #run(String)} / {@link #runStream(String)} 执行期间赋值）。
     * <p>评估链路直接取此引用，避免扫 logs/traces 目录取"最新文件"——
     * 多用例连续跑或并发时会读到上一次的轨迹。
     */
    public AgentTrace getCurrentTrace() {
        return currentTrace;
    }

```

同时在文件顶部确认已 import `com.qian.qianaiagent.agent.trace.AgentTrace`（若已存在则跳过）。

- [ ] **Step 3: 编译验证**

运行：
```bash
mvn -q compile
```
预期：BUILD SUCCESS，无报错。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/agent/BaseAgent.java
git commit -m "feat: BaseAgent 暴露 getCurrentTrace() 供评估链路取轨迹"
```

---

## Task 2: DeterministicScorer 装配与测试

**Files:**
- Create: `src/test/java/com/qian/qianaiagent/evaluation/DeterministicScorerTest.java`
- Modify: `src/main/java/com/qian/qianaiagent/evaluation/DeterministicScorer.java`

**背景：** `DeterministicScorer` 是扣分制评分器，满分 100。扣分常量：缺工具调用 -20、参数不匹配 -10、缺关键词 -20、调用次数超标 -10。注意 `checkToolCalls` 目前**把参数不匹配和缺调用合并**成一条 `缺少工具调用` 扣 20（`PENALTY_PARAM_MISMATCH` 未被使用）—— 本次不改变这个既有行为，测试按现状断言。

- [ ] **Step 1: 写失败的测试**

创建 `src/test/java/com/qian/qianaiagent/evaluation/DeterministicScorerTest.java`：

```java
package com.qian.qianaiagent.evaluation;

import com.qian.qianaiagent.agent.trace.AgentTrace;
import com.qian.qianaiagent.agent.trace.TraceStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 确定性评分器单测 —— 纯代码逻辑，不调 LLM、不访问网络。
 * 扣分规则：缺工具调用 -20、缺关键词 -20、调用次数超标 -10，下限 0。
 */
class DeterministicScorerTest {

    private final DeterministicScorer scorer = new DeterministicScorer();

    private AgentTrace traceOf(TraceStep... steps) {
        return AgentTrace.builder().steps(new java.util.ArrayList<>(List.of(steps))).build();
    }

    private TraceStep toolCall(int n, String toolName, String input, String result) {
        return TraceStep.builder()
                .stepNumber(n).stepType("TOOL_CALL")
                .toolName(toolName).toolInput(input).resultSummary(result)
                .build();
    }

    private TraceStep llmCall(int n, String answer) {
        return TraceStep.builder()
                .stepNumber(n).stepType("LLM_CALL").resultSummary(answer)
                .build();
    }

    private ExpectedBehavior expectedOneTool(String toolName, String param, List<String> keywords, int maxCalls) {
        return ExpectedBehavior.builder()
                .expectedToolCalls(List.of(ExpectedBehavior.ToolCallExpectation.builder()
                        .toolName(toolName).paramContains(param).build()))
                .expectedResponseKeywords(keywords)
                .maxToolCalls(maxCalls)
                .build();
    }

    /**
     * 只校验关键词、不校验工具的期望。
     * <p>注意 expectedToolCalls 必须为 null —— 传空 List 会让 checkToolCalls 进入循环，
     * 而轨迹里没有 TOOL_CALL 步骤时任何期望都匹配不上，会平白扣 20 分。
     */
    private ExpectedBehavior expectedKeywordsOnly(List<String> keywords, int maxCalls) {
        return ExpectedBehavior.builder()
                .expectedResponseKeywords(keywords)
                .maxToolCalls(maxCalls)
                .build();
    }

    @Test
    void fullMatchScores100() {
        AgentTrace trace = traceOf(
                toolCall(1, "searchWeb", "Spring AI 教程", "搜索结果"),
                llmCall(2, "Spring AI 的教程包含代码示例"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedOneTool("searchWeb", "Spring AI", List.of("Spring AI", "教程"), 5));

        assertEquals(100, r.getScore());
        assertTrue(r.isPassed());
        assertTrue(r.getDeductions().isEmpty());
    }

    @Test
    void missingToolCallDeducts20() {
        AgentTrace trace = traceOf(llmCall(1, "Spring AI 的教程"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedOneTool("searchWeb", "Spring AI", List.of(), 5));

        assertEquals(80, r.getScore());
        assertTrue(r.getDeductions().stream().anyMatch(d -> d.contains("缺少工具调用: searchWeb")));
    }

    @Test
    void wrongToolNameDeducts20() {
        // 这正是本次发现的历史 bug：基线写 webSearch，实际工具是 searchWeb
        AgentTrace trace = traceOf(
                toolCall(1, "searchWeb", "Spring AI", "结果"),
                llmCall(2, "回答"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedOneTool("webSearch", "Spring AI", List.of(), 5));

        assertEquals(80, r.getScore());
    }

    @Test
    void toolNameMatchIsCaseInsensitive() {
        AgentTrace trace = traceOf(toolCall(1, "SearchWeb", "Spring AI", "结果"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedOneTool("searchweb", "Spring AI", List.of(), 5));

        assertEquals(100, r.getScore());
    }

    @Test
    void paramMustContainKeyword() {
        AgentTrace trace = traceOf(toolCall(1, "searchWeb", "今天天气", "结果"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedOneTool("searchWeb", "Spring AI", List.of(), 5));

        assertEquals(80, r.getScore());
    }

    @Test
    void missingKeywordDeducts20() {
        AgentTrace trace = traceOf(llmCall(1, "这是一段无关的回答"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedKeywordsOnly(List.of("Spring AI", "教程"), 10));

        // 两个关键词都缺 → 扣 40
        assertEquals(60, r.getScore());
        assertEquals(2, r.getDeductions().size());
        assertTrue(r.getDeductions().get(0).contains("缺少关键词: Spring AI"));
    }

    @Test
    void keywordMatchIsCaseInsensitive() {
        AgentTrace trace = traceOf(llmCall(1, "spring ai 很好用"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedKeywordsOnly(List.of("Spring AI"), 10));

        assertEquals(100, r.getScore());
    }

    @Test
    void finalAnswerComesFromLastLlmCallNotToolResult() {
        // 尾部的 doTerminate 工具返回不应被当成最终回答
        AgentTrace trace = traceOf(
                llmCall(1, "Spring AI 教程在这"),
                toolCall(2, "doTerminate", "{}", "任务终止"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedKeywordsOnly(List.of("Spring AI"), 10));

        assertEquals(100, r.getScore());
    }

    @Test
    void exceedingMaxToolCallsDeducts10() {
        AgentTrace trace = traceOf(
                toolCall(1, "searchWeb", "Spring AI", "r"),
                toolCall(2, "searchWeb", "Spring AI", "r"),
                toolCall(3, "searchWeb", "Spring AI", "r"),
                toolCall(4, "searchWeb", "Spring AI", "r"),
                llmCall(5, "Spring AI 回答"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedOneTool("searchWeb", "Spring AI", List.of("Spring AI"), 3));

        assertEquals(90, r.getScore());
        assertTrue(r.getDeductions().stream().anyMatch(d -> d.contains("工具调用次数超标: 实际 4 次, 上限 3 次")));
    }

    @Test
    void scoreNeverGoesBelowZero() {
        AgentTrace trace = traceOf(llmCall(1, "无关回答"));

        DeterministicScorer.ScoreResult r = scorer.score(
                trace, expectedOneTool("searchWeb", null, List.of("A", "B", "C", "D", "E", "F"), 0));

        assertEquals(0, r.getScore());
        assertFalse(r.isPassed());
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

运行：
```bash
mvn -q test -Dtest=DeterministicScorerTest
```
预期：编译失败或测试报错，因为 `DeterministicScorer` 目前没有 `@Component` —— **实际不会因此失败**（测试直接 `new`，不需要容器）。此时应看到测试**全部通过**，因为评分逻辑本就存在。

这符合预期：本次的评分器改动的价值在于**把既有行为固化成测试**，防止后续回归。若出现失败，说明你对现有行为理解有误，先修正测试断言。

- [ ] **Step 3: 给 DeterministicScorer 加 @Component**

在 `DeterministicScorer.java` 中，把：

```java
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
```

改为：

```java
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
```

并把类声明：

```java
@Slf4j
public class DeterministicScorer {
```

改为：

```java
@Slf4j
@Component
public class DeterministicScorer {
```

- [ ] **Step 4: 运行测试，确认通过**

运行：
```bash
mvn -q test -Dtest=DeterministicScorerTest
```
预期：`Tests run: 10, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add src/test/java/com/qian/qianaiagent/evaluation/DeterministicScorerTest.java \
        src/main/java/com/qian/qianaiagent/evaluation/DeterministicScorer.java
git commit -m "feat: DeterministicScorer 加 @Component 并补齐行为测试"
```

---

## Task 3: BaselineManager 自动建基线

**Files:**
- Create: `src/test/java/com/qian/qianaiagent/evaluation/BaselineManagerTest.java`
- Modify: `src/main/java/com/qian/qianaiagent/evaluation/BaselineManager.java`

**背景：** 三个基线文件的分数都是 `-1`（"尚未人工确认"哨兵），导致对比功能从未生效。改为：`-1` → 用本次结果自动建立基线；`>= 0` → 正常对比。用例文件**必须已存在**才自动建基线 —— 离线跑分的用例集就是从 `evaluation/baselines/` 读的，文件不存在说明用例名写错了，应报错而非凭空造一个丢掉 `query`/`expectedBehavior` 的空壳。

- [ ] **Step 1: 写失败的测试**

创建 `src/test/java/com/qian/qianaiagent/evaluation/BaselineManagerTest.java`：

```java
package com.qian.qianaiagent.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基线管理单测 —— 覆盖 "-1 哨兵 → 自动建基线" 与 "已有基线 → 对比" 两条路径。
 * 全程使用 @TempDir 隔离文件系统，不触碰仓库的真实 evaluation/baselines/。
 */
class BaselineManagerTest {

    private BaselineManager.Baseline caseWithScore(String name, int det, int rubric) {
        return BaselineManager.Baseline.builder()
                .caseName(name)
                .query("测试问题")
                .expectedBehavior(ExpectedBehavior.builder()
                        .expectedToolCalls(List.of(ExpectedBehavior.ToolCallExpectation.builder()
                                .toolName("searchWeb").paramContains("Spring AI").build()))
                        .expectedResponseKeywords(List.of("Spring AI"))
                        .maxToolCalls(5)
                        .build())
                .baselineDeterministicScore(det)
                .baselineRubricScore(rubric)
                .build();
    }

    private BaselineManager.EvaluationResult result(int det, int rubric) {
        return BaselineManager.EvaluationResult.builder()
                .deterministicScore(det).rubricScore(rubric).build();
    }

    @Test
    void missingFileReturnsNullBaseline(@TempDir Path dir) {
        BaselineManager manager = new BaselineManager(dir.toString());

        assertNull(manager.loadBaseline("不存在"));
    }

    @Test
    void missingFileComparisonReportsNoBaseline(@TempDir Path dir) {
        BaselineManager manager = new BaselineManager(dir.toString());

        BaselineManager.ComparisonReport report = manager.compareWithBaseline("不存在", result(80, 70));

        assertFalse(report.isHasBaseline());
        assertFalse(report.isNewBaseline());
        assertTrue(report.getSummary().contains("用例文件不存在"));
    }

    @Test
    void sentinelMinusOneAutoEstablishesBaseline(@TempDir Path dir) throws Exception {
        BaselineManager manager = new BaselineManager(dir.toString());
        manager.saveBaseline(caseWithScore("用例A", -1, -1));

        BaselineManager.ComparisonReport report = manager.compareWithBaseline("用例A", result(85, 77));

        assertTrue(report.isNewBaseline());
        assertFalse(report.isHasBaseline());
        assertEquals(85, report.getNewDeterministicScore());

        // 基线已落盘为本次分数
        BaselineManager.Baseline saved = manager.loadBaseline("用例A");
        assertEquals(85, saved.getBaselineDeterministicScore());
        assertEquals(77, saved.getBaselineRubricScore());
    }

    @Test
    void autoEstablishPreservesCaseDefinition(@TempDir Path dir) {
        BaselineManager manager = new BaselineManager(dir.toString());
        manager.saveBaseline(caseWithScore("用例A", -1, -1));

        manager.compareWithBaseline("用例A", result(85, 77));

        BaselineManager.Baseline saved = manager.loadBaseline("用例A");
        assertEquals("测试问题", saved.getQuery());
        assertNotNull(saved.getExpectedBehavior());
        assertEquals("searchWeb", saved.getExpectedBehavior().getExpectedToolCalls().get(0).getToolName());
    }

    @Test
    void existingBaselineComparesAndComputesDelta(@TempDir Path dir) {
        BaselineManager manager = new BaselineManager(dir.toString());
        manager.saveBaseline(caseWithScore("用例B", 90, 80));

        BaselineManager.ComparisonReport report = manager.compareWithBaseline("用例B", result(70, 60));

        assertTrue(report.isHasBaseline());
        assertFalse(report.isNewBaseline());
        assertEquals(-20, report.getDeltaDeterministic());
        assertEquals(-20, report.getDeltaRubric());
        assertEquals(90, report.getBaselineDeterministicScore());
        assertEquals(70, report.getNewDeterministicScore());
        assertTrue(report.getSummary().contains("分数下降"));
    }

    @Test
    void improvedScoreIsReportedAsRise(@TempDir Path dir) {
        BaselineManager manager = new BaselineManager(dir.toString());
        manager.saveBaseline(caseWithScore("用例C", 70, 60));

        BaselineManager.ComparisonReport report = manager.compareWithBaseline("用例C", result(90, 85));

        assertTrue(report.getSummary().contains("分数上升"));
        assertEquals(20, report.getDeltaDeterministic());
    }

    @Test
    void equalScoreIsReportedAsFlat(@TempDir Path dir) {
        BaselineManager manager = new BaselineManager(dir.toString());
        manager.saveBaseline(caseWithScore("用例D", 80, 70));

        BaselineManager.ComparisonReport report = manager.compareWithBaseline("用例D", result(80, 70));

        assertTrue(report.getSummary().contains("持平"));
    }

    @Test
    void loadAllBaselinesReadsEveryJsonFile(@TempDir Path dir) throws Exception {
        BaselineManager manager = new BaselineManager(dir.toString());
        manager.saveBaseline(caseWithScore("用例甲", 80, 70));
        manager.saveBaseline(caseWithScore("用例乙", 60, 50));
        // 非 json 文件应被忽略
        Files.writeString(dir.resolve("readme.txt"), "忽略我");

        List<BaselineManager.Baseline> all = manager.loadAllBaselines();

        assertEquals(2, all.size());
    }

    @Test
    void caseNameIsSanitizedInFileName(@TempDir Path dir) {
        BaselineManager manager = new BaselineManager(dir.toString());
        manager.saveBaseline(caseWithScore("搜索/Spring AI 教程", 80, 70));

        // 路径分隔符被替换，不会穿越目录
        assertNotNull(manager.loadBaseline("搜索/Spring AI 教程"));
        assertEquals(1, dir.toFile().listFiles().length);
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

运行：
```bash
mvn -q test -Dtest=BaselineManagerTest
```
预期：编译失败 —— `ComparisonReport` 没有 `isNewBaseline()` / builder 的 `newBaseline(...)`。

- [ ] **Step 3: 给 ComparisonReport 加 newBaseline 字段**

在 `BaselineManager.java` 的 `ComparisonReport` 内部类中，把：

```java
    @Data
    @Builder
    public static class ComparisonReport {
        private String caseName;
        private boolean hasBaseline;
        private int deltaDeterministic;
```

改为：

```java
    @Data
    @Builder
    public static class ComparisonReport {
        private String caseName;
        private boolean hasBaseline;
        /** true = 本次运行刚建立基线（此前是 -1 哨兵），尚未与任何历史对比 */
        private boolean newBaseline;
        private int deltaDeterministic;
```

- [ ] **Step 4: 实现自动建基线**

在 `BaselineManager.java` 中，把 `compareWithBaseline` 开头的这段：

```java
        Baseline baseline = loadBaseline(caseName);
        if (baseline == null) {
            return ComparisonReport.builder()
                    .caseName(caseName)
                    .hasBaseline(false)
                    .summary("⚠️ 没有基线数据，无法对比。请先建立基线。")
                    .build();
        }
```

替换为：

```java
        Baseline baseline = loadBaseline(caseName);
        if (baseline == null) {
            // 用例文件不存在 —— 离线跑分的用例集就来自 baselines 目录，
            // 文件缺失说明用例名写错了，报错而非凭空造一个丢掉 query/expectedBehavior 的空壳
            return ComparisonReport.builder()
                    .caseName(caseName)
                    .hasBaseline(false)
                    .summary("⚠️ 用例文件不存在: " + caseName)
                    .build();
        }
        if (baseline.getBaselineDeterministicScore() < 0) {
            // -1 哨兵 = 尚未建立基线 → 用本次结果自动建立（金文件测试的通行做法）
            return autoEstablishBaseline(baseline, newResult);
        }
```

然后在 `compareWithBaseline` 方法**之后**插入新方法：

```java
    /**
     * 用本次评分结果自动建立基线。
     *
     * <p>仅在用例文件已存在、且分数为 -1 哨兵时调用。原地更新分数与备注，
     * 保留文件里的 query / expectedBehavior 等用例定义不动。
     */
    private ComparisonReport autoEstablishBaseline(Baseline baseline, EvaluationResult newResult) {
        baseline.setBaselineDeterministicScore(newResult.getDeterministicScore());
        baseline.setBaselineRubricScore(newResult.getRubricScore());
        baseline.setCreatedAt(LocalDateTime.now());
        baseline.setNotes("🆕 自动建立于 " + LocalDateTime.now()
                + "（首次运行结果，未经人工确认；下次运行起开始对比）");
        saveBaseline(baseline);

        log.info("🆕 用例 [{}] 无基线，已用本次结果自动建立: 确定性={}, Rubric={}",
                baseline.getCaseName(), newResult.getDeterministicScore(), newResult.getRubricScore());

        return ComparisonReport.builder()
                .caseName(baseline.getCaseName())
                .hasBaseline(false)
                .newBaseline(true)
                .newDeterministicScore(newResult.getDeterministicScore())
                .newRubricScore(newResult.getRubricScore())
                .summary("🆕 已自动建立基线")
                .build();
    }
```

- [ ] **Step 5: 降低"文件不存在"的日志噪声**

`loadBaseline` 里的 `log.warn("基线文件不存在: {}", filePath);` 改为 `log.debug(...)` —— 探测不存在的基线现在是正常流程的一部分，不该刷 warn。

- [ ] **Step 6: 加 @Component**

在 `BaselineManager.java` 顶部 import 区加入：

```java
import org.springframework.stereotype.Component;
```

并把类声明：

```java
@Slf4j
public class BaselineManager {
```

改为：

```java
@Slf4j
@Component
public class BaselineManager {
```

> 注意：`@Component` 走无参构造器 → 使用 `DEFAULT_BASELINES_DIR`（`evaluation/baselines/`），正是离线跑分需要的目录。测试用 `@TempDir` 走有参构造器，两不相干。

- [ ] **Step 7: 运行测试，确认通过**

运行：
```bash
mvn -q test -Dtest=BaselineManagerTest
```
预期：`Tests run: 9, Failures: 0, Errors: 0` — BUILD SUCCESS

- [ ] **Step 8: 提交**

```bash
git add src/test/java/com/qian/qianaiagent/evaluation/BaselineManagerTest.java \
        src/main/java/com/qian/qianaiagent/evaluation/BaselineManager.java
git commit -m "feat: BaselineManager 支持 -1 哨兵自动建立基线并加 @Component"
```

---

## Task 4: 工具名守卫测试 + 重写基线用例

**Files:**
- Create: `src/test/java/com/qian/qianaiagent/evaluation/BaselineToolNameTest.java`
- Modify: `evaluation/baselines/搜索Spring_AI教程.json`
- Modify: `evaluation/baselines/读取代码文件并总结.json` → 重命名为 `evaluation/baselines/文件读写往返.json`
- Modify: `evaluation/baselines/知识库RAG检索.json`

**为什么这最重要：** 本次发现的 bug 正是"基线里写了不存在的工具名"，而这会**静默地**让用例永久失败 —— 分数一直是 0，看不出哪里错。守卫测试把 `ToolRegistration` 的真实注册集作为断言源，任何工具改名都会立刻红。

- [ ] **Step 1: 写守卫测试**

创建 `src/test/java/com/qian/qianaiagent/evaluation/BaselineToolNameTest.java`：

```java
package com.qian.qianaiagent.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qian.qianaiagent.tools.FileOperationTool;
import com.qian.qianaiagent.tools.PDFGenerationTool;
import com.qian.qianaiagent.tools.RagSearchTool;
import com.qian.qianaiagent.tools.ResourceDownloadTool;
import com.qian.qianaiagent.tools.TerminateTool;
import com.qian.qianaiagent.tools.TerminalOperationTool;
import com.qian.qianaiagent.tools.WebScrapingTool;
import com.qian.qianaiagent.tools.WebSearchTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基线用例工具名守卫 —— 防止"基线里写了不存在的工具名"这类静默失败。
 *
 * <p>Spring AI 以 <b>方法名</b> 作为工具名。历史上基线写的是 webSearch/fileRead/ragSearch，
 * 而实际注册的是 searchWeb/readFile/searchKnowledgeBase，导致三个用例永久失败且毫无提示。
 *
 * <p>断言源是 {@link ToolCallbacks#from} 的真实注册结果（与 {@code ToolRegistration.allTools()} 同一路径），
 * 不是手抄的字符串列表 —— 任何工具改名都会让本测试立刻变红。
 */
class BaselineToolNameTest {

    private static final Path BASELINES_DIR = Paths.get("evaluation", "baselines");

    /** 与 ToolRegistration.allTools() 完全一致的装配；无参构造器可安全传 null/占位值 */
    private static Set<String> registeredToolNames() {
        ToolCallback[] tools = ToolCallbacks.from(
                new FileOperationTool(),
                new WebSearchTool("dummy-key-for-name-scan"),
                new WebScrapingTool(),
                new ResourceDownloadTool(),
                new TerminalOperationTool(),
                new PDFGenerationTool(),
                new TerminateTool(),
                new RagSearchTool(null));
        return Arrays.stream(tools)
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toSet());
    }

    private static List<BaselineManager.Baseline> loadBaselineFiles() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        try (Stream<Path> files = Files.list(BASELINES_DIR)) {
            return files
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        try {
                            return mapper.readValue(p.toFile(), BaselineManager.Baseline.class);
                        } catch (Exception e) {
                            throw new RuntimeException("解析基线文件失败: " + p, e);
                        }
                    })
                    .toList();
        }
    }

    @Test
    void everyExpectedToolNameIsActuallyRegistered() throws Exception {
        Set<String> registered = registeredToolNames();
        List<BaselineManager.Baseline> cases = loadBaselineFiles();

        assertFalse(cases.isEmpty(), "evaluation/baselines/ 下没有找到任何用例文件");

        for (BaselineManager.Baseline c : cases) {
            ExpectedBehavior expected = c.getExpectedBehavior();
            if (expected == null || expected.getExpectedToolCalls() == null) {
                continue;
            }
            for (ExpectedBehavior.ToolCallExpectation call : expected.getExpectedToolCalls()) {
                if (call.getToolName() == null) {
                    continue;
                }
                assertTrue(registered.contains(call.getToolName()),
                        "用例 [" + c.getCaseName() + "] 期望的工具 \"" + call.getToolName()
                                + "\" 未注册。实际注册的工具名: " + registered);
            }
        }
    }

    @Test
    void expectedToolCallsAreNotEmpty() throws Exception {
        List<BaselineManager.Baseline> cases = loadBaselineFiles();

        for (BaselineManager.Baseline c : cases) {
            assertTrue(c.getExpectedBehavior() != null
                            && c.getExpectedBehavior().getExpectedToolCalls() != null
                            && !c.getExpectedBehavior().getExpectedToolCalls().isEmpty(),
                    "用例 [" + c.getCaseName() + "] 没有声明期望的工具调用");
        }
    }

    @Test
    void expectedResponseKeywordsAreNotEmpty() throws Exception {
        List<BaselineManager.Baseline> cases = loadBaselineFiles();

        for (BaselineManager.Baseline c : cases) {
            assertTrue(c.getExpectedBehavior() != null
                            && c.getExpectedBehavior().getExpectedResponseKeywords() != null
                            && !c.getExpectedBehavior().getExpectedResponseKeywords().isEmpty(),
                    "用例 [" + c.getCaseName() + "] 没有声明期望的回答关键词");
        }
    }
}
```

- [ ] **Step 2: 运行守卫测试，确认它抓到 bug**

运行：
```bash
mvn -q test -Dtest=BaselineToolNameTest
```
预期：**FAIL**，报错形如：
```
用例 [搜索Spring AI教程] 期望的工具 "webSearch" 未注册。实际注册的工具名: [searchWeb, scrapeWebPage, readFile, ...]
```
这正是要修的历史 bug。

- [ ] **Step 3: 重写三个基线文件**

删除三个旧用例文件（文件名含空格与旧工具名，逐一重写）：
```bash
rm -f "evaluation/baselines/读取代码文件并总结.json" \
      "evaluation/baselines/搜索Spring_AI教程.json" \
      "evaluation/baselines/知识库RAG检索.json"
```

创建 `evaluation/baselines/搜索Spring AI教程.json`：

```json
{
  "caseName": "搜索Spring AI教程",
  "query": "请使用搜索引擎（searchWeb 工具）查找 Spring AI 的官方教程，用两三句话概括它大致包含哪些内容模块。",
  "createdAt": "2026-09-10T10:00:00",
  "expectedBehavior": {
    "expectedToolCalls": [
      {
        "toolName": "searchWeb",
        "paramContains": "Spring AI",
        "resultContains": null
      }
    ],
    "expectedResponseKeywords": ["Spring AI"],
    "maxToolCalls": 5
  },
  "baselineDeterministicScore": -1,
  "baselineRubricScore": -1,
  "notes": "⚠️ 分数为 -1 表示尚未建立基线。首次运行 eval 时会自动建立。"
}
```

创建 `evaluation/baselines/文件读写往返.json`：

```json
{
  "caseName": "文件读写往返",
  "query": "请把「评估测试内容」这行字写入文件 eval-test.txt，然后把文件读回来确认内容。",
  "createdAt": "2026-09-10T10:00:00",
  "expectedBehavior": {
    "expectedToolCalls": [
      {
        "toolName": "writeFile",
        "paramContains": "eval-test.txt",
        "resultContains": null
      },
      {
        "toolName": "readFile",
        "paramContains": "eval-test.txt",
        "resultContains": null
      }
    ],
    "expectedResponseKeywords": ["评估测试内容"],
    "maxToolCalls": 5
  },
  "baselineDeterministicScore": -1,
  "baselineRubricScore": -1,
  "notes": "⚠️ 分数为 -1 表示尚未建立基线。首次运行 eval 时会自动建立。此用例不依赖网络与知识库，适合做稳定性测试。"
}
```

创建 `evaluation/baselines/知识库RAG检索.json`：

```json
{
  "caseName": "知识库RAG检索",
  "query": "请搜索本地知识库，告诉我 Spring AI 1.0 版本有哪些新特性。",
  "createdAt": "2026-09-10T10:00:00",
  "expectedBehavior": {
    "expectedToolCalls": [
      {
        "toolName": "searchKnowledgeBase",
        "paramContains": "Spring AI",
        "resultContains": null
      }
    ],
    "expectedResponseKeywords": ["Spring AI"],
    "maxToolCalls": 5
  },
  "baselineDeterministicScore": -1,
  "baselineRubricScore": -1,
  "notes": "⚠️ 分数为 -1 表示尚未建立基线。首次运行 eval 时会自动建立。依赖知识库已导入 Spring AI 相关文档。"
}
```

> **为什么第二条用例要重新设计：** 原来它让 Agent 读 `src/main/java/com/yupi/yuaiagent/tools/WebSearchTool.java`，但 `FileOperationTool.readFile` 的沙箱是 `FILE_SAVE_DIR + "/file"`（即 `tmp/file`），Agent 读不到项目源码，且路径还残留着模板项目的 `com/yupi` 包名。改成沙箱内的读写往返后，这条用例**不依赖网络也不依赖知识库**，是最稳定的那条，适合当稳定性测试的样本。

- [ ] **Step 4: 运行守卫测试，确认通过**

运行：
```bash
mvn -q test -Dtest=BaselineToolNameTest
```
预期：`Tests run: 3, Failures: 0, Errors: 0` — BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add evaluation/baselines src/test/java/com/qian/qianaiagent/evaluation/BaselineToolNameTest.java
git commit -m "fix: 重写基线用例修正工具名，并加工具名守卫测试

原基线写的 webSearch/fileRead/ragSearch 均未注册（实际是
searchWeb/readFile/searchKnowledgeBase），三个用例永久失败且无提示。
第二条用例改为沙箱内文件读写往返——原场景读项目源码在沙箱下不可能实现。"
```

---

## Task 5: EvalReport 报告模型与渲染

**Files:**
- Create: `src/main/java/com/qian/qianaiagent/evaluation/EvalReport.java`
- Create: `src/test/java/com/qian/qianaiagent/evaluation/EvalReportTest.java`

**为什么：** 用户的核心痛点原话是"一点反馈都没有"。这个类就是反馈本身。

- [ ] **Step 1: 写失败的测试**

创建 `src/test/java/com/qian/qianaiagent/evaluation/EvalReportTest.java`：

```java
package com.qian.qianaiagent.evaluation;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评估报告渲染单测 —— 结论文案与退出码推导，不涉及 LLM。
 */
class EvalReportTest {

    private EvalReport.CaseOutcome scored(String name, int det, int rubric,
                                          BaselineManager.ComparisonReport comparison) {
        return EvalReport.CaseOutcome.builder()
                .caseName(name).query("问题")
                .scored(true)
                .deterministicScore(det).rubricScore(rubric)
                .passed(det >= 60)
                .comparison(comparison)
                .build();
    }

    private BaselineManager.ComparisonReport compared(int baseline, int now, int delta, String summary) {
        return BaselineManager.ComparisonReport.builder()
                .caseName("x").hasBaseline(true).newBaseline(false)
                .baselineDeterministicScore(baseline).newDeterministicScore(now)
                .deltaDeterministic(delta)
                .baselineRubricScore(baseline).newRubricScore(now).deltaRubric(delta)
                .summary(summary)
                .build();
    }

    private BaselineManager.ComparisonReport newlyEstablished(int now) {
        return BaselineManager.ComparisonReport.builder()
                .caseName("x").hasBaseline(false).newBaseline(true)
                .newDeterministicScore(now).newRubricScore(now)
                .summary("🆕 已自动建立基线")
                .build();
    }

    private EvalReport reportOf(EvalReport.CaseOutcome... outcomes) {
        return EvalReport.builder()
                .generatedAt(LocalDateTime.of(2026, 9, 10, 15, 4, 22))
                .outcomes(List.of(outcomes))
                .elapsedMs(252_000)
                .build();
    }

    @Test
    void regressionYieldsExitCode1() {
        EvalReport report = reportOf(scored("用例A", 40, 30,
                compared(80, 40, -40, "🔴 分数下降！改坏了，请检查最近的改动。")));

        assertEquals(1, report.exitCode());
        assertTrue(report.conclusion().contains("分数下降"));
    }

    @Test
    void noRegressionYieldsExitCode0() {
        EvalReport report = reportOf(scored("用例A", 90, 85,
                compared(80, 90, 10, "🟢 分数上升！改好了。")));

        assertEquals(0, report.exitCode());
        assertTrue(report.conclusion().contains("无回归"));
    }

    @Test
    void allNewBaselinesYieldsExitCode0WithHint() {
        EvalReport report = reportOf(scored("用例A", 90, 85, newlyEstablished(90)));

        assertEquals(0, report.exitCode());
        assertTrue(report.conclusion().contains("新建立基线"));
    }

    @Test
    void emptyReportIsNotAnError() {
        EvalReport report = reportOf();

        assertEquals(0, report.exitCode());
        assertTrue(report.conclusion().contains("未找到任何用例"));
    }

    @Test
    void passCountAndAverageOnlyCountScoredOutcomes() {
        EvalReport report = reportOf(
                scored("A", 100, 90, null),
                scored("B", 60, 50, null),
                EvalReport.CaseOutcome.builder()
                        .caseName("C").scored(false).skipReason("轨迹不完整").build());

        // A、B 均 >= 60 及格线；C 未评分不计入
        assertEquals(2, report.passCount());
        assertEquals(80.0, report.avgScore(), 0.01);
    }

    @Test
    void renderIncludesCaseNamesScoresAndElapsed() {
        EvalReport report = reportOf(scored("搜索Spring AI教程", 90, 85,
                compared(88, 90, 2, "🟢 分数上升！改好了。")));

        String text = report.render();

        assertTrue(text.contains("搜索Spring AI教程"));
        assertTrue(text.contains("确定性 90/100"));
        assertTrue(text.contains("Rubric 85/100"));
        assertTrue(text.contains("基线 确定性 88 → 90"));
        assertTrue(text.contains("4m12s"), "耗时应渲染为 4m12s，实际:\n" + text);
    }

    @Test
    void renderIncludesDeductions() {
        EvalReport.CaseOutcome outcome = EvalReport.CaseOutcome.builder()
                .caseName("A").query("问题").scored(true)
                .deterministicScore(80).rubricScore(70).passed(true)
                .deductions(List.of("[-20] 缺少关键词: 教程"))
                .build();

        String text = reportOf(outcome).render();

        assertTrue(text.contains("[-20] 缺少关键词: 教程"));
    }

    @Test
    void renderMarksSkippedAndErroredCases() {
        EvalReport report = reportOf(
                EvalReport.CaseOutcome.builder()
                        .caseName("跳过用例").scored(false).skipReason("轨迹不完整（endTime 为空或无步骤）").build(),
                EvalReport.CaseOutcome.builder()
                        .caseName("异常用例").scored(false).error("连接超时").build());

        String text = report.render();

        assertTrue(text.contains("跳过评分: 轨迹不完整"));
        assertTrue(text.contains("执行异常: 连接超时"));
    }

    @Test
    void renderIncludesStabilityWhenPresent() {
        StabilityTester.StabilityReport stability = StabilityTester.StabilityReport.builder()
                .caseName("文件读写往返").runCount(5).passCount(4)
                .passK(0.8).avgScore(88.0).stdDev(5.0).isStable(false)
                .build();
        EvalReport report = EvalReport.builder()
                .generatedAt(LocalDateTime.of(2026, 9, 10, 15, 4, 22))
                .outcomes(List.of(scored("A", 90, 85, null)))
                .stability(stability)
                .elapsedMs(1000)
                .build();

        String text = report.render();

        assertTrue(text.contains("pass^5 = 4/5 (80%)"));
        assertTrue(text.contains("不稳定"));
    }

    @Test
    void elapsedFormatsSubMinuteAsSeconds() {
        EvalReport report = EvalReport.builder()
                .generatedAt(LocalDateTime.now())
                .outcomes(List.of())
                .elapsedMs(12_400)
                .build();

        assertTrue(report.render().contains("12s"), "预期 12s，实际:\n" + report.render());
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

运行：
```bash
mvn -q test -Dtest=EvalReportTest
```
预期：编译失败 —— `EvalReport` 不存在。

- [ ] **Step 3: 实现 EvalReport**

创建 `src/main/java/com/qian/qianaiagent/evaluation/EvalReport.java`：

```java
package com.qian.qianaiagent.evaluation;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 离线评估报告 —— 一次 {@link EvalRunner} 运行的完整结果，负责渲染成可读文本。
 *
 * <p>渲染刻意不追求边框对齐：中文在终端占两个字符宽，按 {@code String.length()} 补空格会错位。
 * 改用整行分隔 + 缩进层级，任何终端下都整齐。
 */
@Data
@Builder
public class EvalReport {

    /** 报告生成时间 */
    private LocalDateTime generatedAt;

    /** 各用例结果，顺序与用例文件加载顺序一致 */
    @Builder.Default
    private List<CaseOutcome> outcomes = new ArrayList<>();

    /** 稳定性测试结果；未跑则为 null */
    private StabilityTester.StabilityReport stability;

    /** 总耗时（毫秒） */
    private long elapsedMs;

    // ============================================================
    // 统计
    // ============================================================

    /** 通过数（仅统计实际评分的用例） */
    public int passCount() {
        return (int) outcomes.stream().filter(o -> o.isScored() && o.isPassed()).count();
    }

    /** 已评分用例的平均确定性分数；无已评分用例返回 0 */
    public double avgScore() {
        return outcomes.stream()
                .filter(CaseOutcome::isScored)
                .mapToInt(CaseOutcome::getDeterministicScore)
                .average()
                .orElse(0.0);
    }

    /** 是否存在分数下降的用例 */
    public boolean hasRegression() {
        return outcomes.stream().anyMatch(o -> {
            BaselineManager.ComparisonReport c = o.getComparison();
            return c != null && c.isHasBaseline() && c.getDeltaDeterministic() < 0;
        });
    }

    /** 进程退出码：有回归 → 1，否则 → 0（便于接 CI） */
    public int exitCode() {
        return hasRegression() ? 1 : 0;
    }

    /** 一句话结论 */
    public String conclusion() {
        if (outcomes.isEmpty()) {
            return "⚠️ 未找到任何用例，请检查 evaluation/baselines/ 目录";
        }
        long regressions = outcomes.stream().filter(o -> {
            BaselineManager.ComparisonReport c = o.getComparison();
            return c != null && c.isHasBaseline() && c.getDeltaDeterministic() < 0;
        }).count();
        if (regressions > 0) {
            return "🔴 " + regressions + " 个用例分数下降，请检查最近改动";
        }
        long newly = outcomes.stream()
                .filter(o -> o.getComparison() != null && o.getComparison().isNewBaseline())
                .count();
        if (newly == outcomes.size()) {
            return "🆕 全部用例为新建立基线，下次运行起开始对比";
        }
        return "🟢 无回归";
    }

    // ============================================================
    // 渲染
    // ============================================================

    public String render() {
        String rule = "══════════════════════════════════════════════════════════\n";
        String thin = "──────────────────────────────────────────────────────────\n";

        StringBuilder sb = new StringBuilder();
        sb.append(rule);
        sb.append("  Agent 评估报告   ").append(generatedAt).append("\n");
        sb.append(rule);
        sb.append(String.format("  总览  %d 个用例 | 通过 %d | 未通过 %d | 平均 %.1f | 耗时 %s%n",
                outcomes.size(), passCount(), outcomes.size() - passCount(),
                avgScore(), humanElapsed()));

        for (int i = 0; i < outcomes.size(); i++) {
            appendOutcome(sb, thin, i + 1, outcomes.get(i));
        }

        if (stability != null) {
            sb.append(thin);
            sb.append(String.format("  稳定性  pass^%d = %d/%d (%.0f%%)  %s%n",
                    stability.getRunCount(), stability.getPassCount(), stability.getRunCount(),
                    stability.getPassK() * 100,
                    stability.isStable() ? "✅ 稳定（失败率 ≤ 10%）" : "❌ 不稳定（失败率 > 10%）"));
        }

        sb.append(rule);
        sb.append("  结论  ").append(conclusion()).append("\n");
        sb.append(rule);
        return sb.toString();
    }

    private void appendOutcome(StringBuilder sb, String thin, int index, CaseOutcome o) {
        sb.append(thin);
        sb.append("  [").append(index).append("] ").append(o.getCaseName()).append("\n");

        if (o.getError() != null && !o.getError().isBlank()) {
            sb.append("      ❌ 执行异常: ").append(o.getError()).append("\n");
            return;
        }
        if (!o.isScored()) {
            sb.append("      ⚠️ 跳过评分: ").append(o.getSkipReason()).append("\n");
            return;
        }

        sb.append(String.format("      %s 确定性 %d/100 | Rubric %d/100%n",
                o.isPassed() ? "✅" : "❌", o.getDeterministicScore(), o.getRubricScore()));

        BaselineManager.ComparisonReport c = o.getComparison();
        if (c != null && c.isNewBaseline()) {
            sb.append("      │ 🆕 已自动建立基线\n");
        } else if (c != null && c.isHasBaseline()) {
            sb.append(String.format("      │ 基线 确定性 %d → %d (Δ %+d) | Rubric %d → %d (Δ %+d)%n",
                    c.getBaselineDeterministicScore(), c.getNewDeterministicScore(), c.getDeltaDeterministic(),
                    c.getBaselineRubricScore(), c.getNewRubricScore(), c.getDeltaRubric()));
            sb.append("      │ ").append(c.getSummary()).append("\n");
        }

        for (String d : o.getDeductions()) {
            sb.append("      │ ").append(d).append("\n");
        }
    }

    private String humanElapsed() {
        long totalSeconds = elapsedMs / 1000;
        if (totalSeconds < 60) {
            return totalSeconds + "s";
        }
        return (totalSeconds / 60) + "m" + (totalSeconds % 60) + "s";
    }

    // ============================================================
    // 数据模型
    // ============================================================

    /** 单个用例的执行与评分结果 */
    @Data
    @Builder
    public static class CaseOutcome {
        private String caseName;
        private String query;

        /** 是否完成了评分（轨迹不完整或执行异常时为 false） */
        private boolean scored;

        /** scored=false 的原因 */
        private String skipReason;

        private int deterministicScore;
        private int rubricScore;
        private boolean passed;

        /** 确定性评分的扣分明细 */
        @Builder.Default
        private List<String> deductions = new ArrayList<>();

        /** 基线对比结果；无基线且文件不存在时为 null */
        private BaselineManager.ComparisonReport comparison;

        /** 执行异常信息 */
        private String error;
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

运行：
```bash
mvn -q test -Dtest=EvalReportTest
```
预期：`Tests run: 10, Failures: 0, Errors: 0` — BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/evaluation/EvalReport.java \
        src/test/java/com/qian/qianaiagent/evaluation/EvalReportTest.java
git commit -m "feat: 新增 EvalReport 评估报告模型与控制台渲染"
```

---

## Task 6: EvalRunner 离线入口与 eval profile

**Files:**
- Create: `src/main/java/com/qian/qianaiagent/evaluation/EvalRunner.java`
- Create: `src/main/resources/application-eval.yml`
- Modify: `pom.xml`

**为什么：** 这是"一键跑"的落点。用 `@ConditionalOnProperty` 门控，正常启动应用时它完全不存在。

- [ ] **Step 1: 创建 application-eval.yml**

创建 `src/main/resources/application-eval.yml`：

```yaml
# eval profile：离线回归评测专用
# 用法： mvn spring-boot:run -Peval
# 大跑： mvn spring-boot:run -Peval -Dspring-boot.run.arguments=--qian.eval.stability=5
qian:
  eval:
    # 打开后 EvalRunner 才会作为 CommandLineRunner 执行；正常启动应用时保持 false
    enabled: true
    # 稳定性重复次数，0 = 不跑。>=2 才会触发
    stability: 0
```

- [ ] **Step 2: 实现 EvalRunner**

创建 `src/main/java/com/qian/qianaiagent/evaluation/EvalRunner.java`：

```java
package com.qian.qianaiagent.evaluation;

import com.qian.qianaiagent.agent.YuManus;
import com.qian.qianaiagent.agent.trace.AgentTrace;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 离线回归评测入口 —— 项目里唯一的"跑一次评测集"的地方。
 *
 * <p><b>怎么触发：</b>
 * <pre>
 *   全量跑（3 个用例）：        mvn spring-boot:run -Peval
 *   含稳定性测试（连跑 5 次）：  mvn spring-boot:run -Peval -Dspring-boot.run.arguments=--qian.eval.stability=5
 * </pre>
 *
 * <p><b>做什么：</b>从 {@code evaluation/baselines/} 读出用例（用例定义与基线分数同文件），
 * 逐个跑真实 Agent → 确定性评分 + Rubric 评分 → 与基线对比（无基线则自动建立）→ 渲染报告。
 *
 * <p><b>为什么用 {@code @ConditionalOnProperty}：</b>正常启动应用时这个 Bean 根本不存在，
 * 不会因为误触发而消耗 LLM 调用。只有 eval profile 才把它打开。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "qian.eval.enabled", havingValue = "true")
public class EvalRunner implements CommandLineRunner {

    /** 报告落盘目录（同时打印到控制台，避免 Windows 控制台中文乱码） */
    private static final String REPORT_DIR = "logs/eval";

    @Resource
    private ObjectProvider<YuManus> yuManusProvider;

    @Resource
    private DeterministicScorer deterministicScorer;

    @Resource
    private RubricScorer rubricScorer;

    @Resource
    private BaselineManager baselineManager;

    private final ApplicationContext applicationContext;

    /** 稳定性测试重复次数，0 = 不跑 */
    @Value("${qian.eval.stability:0}")
    private int stabilityRuns;

    public EvalRunner(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) {
        log.info("════ 离线评估开始 ════");
        EvalReport report = evaluate();
        String rendered = report.render();

        System.out.println(rendered);
        writeReportFile(rendered);

        int exitCode = report.exitCode();
        log.info("════ 离线评估结束，退出码 {} ════", exitCode);

        // 评测跑完就该退出，不能让 Web 服务挂着
        SpringApplication.exit(applicationContext, () -> exitCode);
        System.exit(exitCode);
    }

    // ============================================================
    // 编排
    // ============================================================

    private EvalReport evaluate() {
        long start = System.currentTimeMillis();

        List<BaselineManager.Baseline> cases = baselineManager.loadAllBaselines();
        log.info("加载了 {} 个评估用例", cases.size());

        List<EvalReport.CaseOutcome> outcomes = new ArrayList<>();
        for (BaselineManager.Baseline evalCase : cases) {
            outcomes.add(runOneCase(evalCase));
        }

        StabilityTester.StabilityReport stability = null;
        if (stabilityRuns >= 2 && !cases.isEmpty()) {
            // 用第一条用例做稳定性样本；文件读写往返这类不依赖外部的用例最适合，
            // 但顺序由文件名决定，不额外挑拣
            stability = runStabilityTest(cases.get(0));
        }

        return EvalReport.builder()
                .generatedAt(LocalDateTime.now())
                .outcomes(outcomes)
                .stability(stability)
                .elapsedMs(System.currentTimeMillis() - start)
                .build();
    }

    /** 跑单个用例。任何异常都被收敛成"该用例失败"，绝不让整轮评测中断。 */
    private EvalReport.CaseOutcome runOneCase(BaselineManager.Baseline evalCase) {
        String caseName = evalCase.getCaseName();
        log.info("▶ 用例 [{}]：{}", caseName, evalCase.getQuery());

        try {
            // 每个用例取全新实例（BaseAgent 要求从 IDLE 状态启动）
            YuManus agent = yuManusProvider.getObject();
            agent.run(evalCase.getQuery());
            AgentTrace trace = agent.getCurrentTrace();

            if (trace == null || trace.getEndTime() == null
                    || trace.getSteps() == null || trace.getSteps().isEmpty()) {
                log.warn("⚠️ 用例 [{}] 轨迹不完整，跳过评分", caseName);
                return EvalReport.CaseOutcome.builder()
                        .caseName(caseName).query(evalCase.getQuery())
                        .scored(false).skipReason("轨迹不完整（endTime 为空或无步骤）")
                        .build();
            }

            DeterministicScorer.ScoreResult det =
                    deterministicScorer.score(trace, evalCase.getExpectedBehavior());
            log.info("  确定性评分 {}/100", det.getScore());

            int rubricScore = 0;
            boolean rubricFailed = false;
            try {
                rubricScore = rubricScorer.score(evalCase.getQuery(), trace).getTotalScore();
                log.info("  Rubric 评分 {}/100", rubricScore);
            } catch (Exception e) {
                rubricFailed = true;
                log.warn("  Rubric 评分失败（保留确定性分）: {}", e.getMessage());
            }

            BaselineManager.EvaluationResult result = BaselineManager.EvaluationResult.builder()
                    .deterministicScore(det.getScore())
                    .rubricScore(rubricScore)
                    .build();
            BaselineManager.ComparisonReport comparison =
                    baselineManager.compareWithBaseline(caseName, result);

            return EvalReport.CaseOutcome.builder()
                    .caseName(caseName).query(evalCase.getQuery())
                    .scored(true)
                    .deterministicScore(det.getScore())
                    .rubricScore(rubricScore)
                    .passed(det.isPassed() && !rubricFailed)
                    .deductions(det.getDeductions())
                    .comparison(comparison)
                    .build();

        } catch (Exception e) {
            log.error("❌ 用例 [{}] 执行异常: {}", caseName, e.getMessage(), e);
            return EvalReport.CaseOutcome.builder()
                    .caseName(caseName).query(evalCase.getQuery())
                    .scored(false).skipReason("执行异常")
                    .error(e.getMessage())
                    .build();
        }
    }

    private StabilityTester.StabilityReport runStabilityTest(BaselineManager.Baseline evalCase) {
        log.info("▶ 稳定性测试 [{}]：连跑 {} 次", evalCase.getCaseName(), stabilityRuns);

        StabilityTester tester = new StabilityTester(stabilityRuns);
        return tester.test(evalCase.getCaseName(), () -> {
            try {
                YuManus agent = yuManusProvider.getObject();
                agent.run(evalCase.getQuery());
                AgentTrace trace = agent.getCurrentTrace();

                if (trace == null || trace.getSteps() == null || trace.getSteps().isEmpty()) {
                    return StabilityTester.TestResult.builder()
                            .totalScore(0).passed(false).errorMessage("轨迹为空").build();
                }

                DeterministicScorer.ScoreResult det =
                        deterministicScorer.score(trace, evalCase.getExpectedBehavior());
                return StabilityTester.TestResult.builder()
                        .totalScore(det.getScore()).passed(det.isPassed()).build();
            } catch (Exception e) {
                return StabilityTester.TestResult.builder()
                        .totalScore(0).passed(false).errorMessage(e.getMessage()).build();
            }
        });
    }

    // ============================================================
    // 报告落盘
    // ============================================================

    /** 报告写文件，避免 Windows 控制台编码问题导致中文乱码 */
    private void writeReportFile(String rendered) {
        try {
            Path dir = Paths.get(REPORT_DIR);
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path out = dir.resolve("报告_" + stamp + ".txt");
            Files.writeString(out, rendered);
            log.info("📄 评估报告已写入：{}", out.toAbsolutePath());
        } catch (IOException e) {
            log.warn("写入评估报告失败：{}", e.getMessage());
        }
    }
}
```

- [ ] **Step 3: 给 pom.xml 加 eval profile**

在 `pom.xml` 中，把 `</repositories>` 之前的位置插入 profile 段 —— 即把：

```xml
    </repositories>
</project>
```

改为：

```xml
    </repositories>

    <profiles>
        <!--
            离线评估 profile。
            用法： mvn spring-boot:run -Peval
            大跑： mvn spring-boot:run -Peval -Dspring-boot.run.arguments=--qian.eval.stability=5
        -->
        <profile>
            <id>eval</id>
            <properties>
                <spring-boot.run.profiles>eval</spring-boot.run.profiles>
            </properties>
        </profile>
    </profiles>
</project>
```

- [ ] **Step 4: 编译验证**

运行：
```bash
mvn -q compile
```
预期：BUILD SUCCESS。若报 `ApplicationContext` 未找到，确认 import 是 `org.springframework.context.ApplicationContext`。

- [ ] **Step 5: 验证条件装配不会误触发**

运行：
```bash
grep -n "qian" src/main/resources/application.yml || echo "application.yml 中没有 qian.eval，EvalRunner 默认不装配 ✅"
```
预期：输出 `application.yml 中没有 qian.eval，EvalRunner 默认不装配 ✅`
（`@ConditionalOnProperty` 默认 `matchIfMissing=false`，未配置即为 false。）

- [ ] **Step 6: 全量单测回归**

运行：
```bash
mvn -q test
```
预期：所有测试通过。特别确认 `PackageDependencyTest` 仍绿 —— 新增的 `EvalRunner` 在 `evaluation` 包，依赖 `agent` 包（`YuManus`），而 archunit 规则只约束 `rag`/`ability`/`knowledge` 不得反向依赖，`evaluation` 依赖 `agent` 是允许方向。

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/evaluation/EvalRunner.java \
        src/main/resources/application-eval.yml pom.xml
git commit -m "feat: 新增 EvalRunner 离线评估入口与 eval profile"
```

---

## Task 7: EvaluationRecorder 可观测性

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/evaluation/EvaluationRecorder.java`

**为什么：** 成功路径现在零日志，跳过走 debug 级 —— 这就是用户说"一点反馈都没有"的直接原因。

- [ ] **Step 1: 让落盘可见**

在 `EvaluationRecorder.java` 的 `appendRecord` 方法中，把：

```java
                Path tmp = evalDir.resolve(name + ".json.tmp");
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), all);
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
```

改为：

```java
                Path tmp = evalDir.resolve(name + ".json.tmp");
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), all);
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                log.info("📊 评估已落盘: chatId={}, type={}, 该会话累计 {} 条 → {}",
                        chatId, record.getType(), all.size(), path.getFileName());
```

- [ ] **Step 2: 让"为什么没评"可见**

把 `submitAgentEval` 中的：

```java
        if (trace == null || trace.getEndTime() == null
                || trace.getSteps() == null || trace.getSteps().isEmpty()) {
            log.debug("跳过 Agent 评估（trace 未封口或为空）: chatId={}", chatId);
            return CompletableFuture.completedFuture(null);
        }
```

改为：

```java
        if (trace == null || trace.getEndTime() == null
                || trace.getSteps() == null || trace.getSteps().isEmpty()) {
            log.info("⏭ 跳过 Agent 评估（trace 未封口或为空，超时/异常路径）: chatId={}", chatId);
            return CompletableFuture.completedFuture(null);
        }
```

把 `submitRagEval` 中的：

```java
            log.debug("跳过 RAG 评估（题干/回答/检索文档缺失）: chatId={}", chatId);
```

改为：

```java
            log.info("⏭ 跳过 RAG 评估（题干/回答/检索文档缺失）: chatId={}", chatId);
```

- [ ] **Step 3: 新增跨会话汇总**

在 `EvaluationRecorder.java` 的"读取"区块（`listRecent` 方法之后）插入：

```java
    // ============================================================
    // 汇总（跨会话）
    // ============================================================

    /**
     * 扫描 data/evals/ 下所有会话文件，产出跨会话汇总。
     *
     * <p>单会话的 {@link #listRecent} 只能看一个 chatId；这个方法是"整体情况怎么样"的答案。
     * 损坏的文件被跳过而不是让整个汇总失败。
     */
    public EvalSummary summarize() {
        List<EvalRecord> all = readAllRecords();
        List<EvalRecord> agentRecords = all.stream().filter(r -> "agent".equals(r.getType())).toList();
        List<EvalRecord> ragRecords = all.stream().filter(r -> "rag".equals(r.getType())).toList();

        TypeSummary agent = TypeSummary.builder()
                .count(agentRecords.size())
                .avgRubricTotal(avg(agentRecords,
                        r -> r.getRubric() == null ? null : (double) r.getRubric().getTotalScore()))
                .build();

        TypeSummary rag = TypeSummary.builder()
                .count(ragRecords.size())
                .avgContextPrecision(avg(ragRecords,
                        r -> r.getRagas() == null ? null : r.getRagas().getContextPrecision()))
                .avgFaithfulness(avg(ragRecords,
                        r -> r.getRagas() == null ? null : r.getRagas().getFaithfulness()))
                .avgAnswerRelevance(avg(ragRecords,
                        r -> r.getRagas() == null ? null : r.getRagas().getAnswerRelevance()))
                .build();

        return EvalSummary.builder()
                .totalRecords(all.size())
                .sessions((int) all.stream()
                        .map(EvalRecord::getChatId)
                        .filter(Objects::nonNull)
                        .distinct().count())
                .earliest(all.stream().map(EvalRecord::getTimestamp)
                        .filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(null))
                .latest(all.stream().map(EvalRecord::getTimestamp)
                        .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null))
                .agent(agent)
                .rag(rag)
                .build();
    }

    /** 扫描 evalDir 下所有 .json，解析并展平。损坏文件跳过。 */
    private List<EvalRecord> readAllRecords() {
        List<EvalRecord> all = new ArrayList<>();
        if (!Files.isDirectory(evalDir)) {
            return all;
        }
        try (var stream = Files.list(evalDir)) {
            for (Path p : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                try {
                    List<EvalRecord> records = objectMapper.readValue(
                            p.toFile(), new TypeReference<List<EvalRecord>>() {});
                    if (records != null) {
                        all.addAll(records);
                    }
                } catch (Exception e) {
                    log.warn("跳过损坏的评估文件: {}, err={}", p.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("扫描评估目录失败: {}", e.getMessage());
        }
        return all;
    }

    /** 对非空的取值求平均；全为 null 时返回 null（而非 0，避免误导） */
    private Double avg(List<EvalRecord> records, java.util.function.Function<EvalRecord, Double> extractor) {
        List<Double> values = records.stream().map(extractor).filter(Objects::nonNull).toList();
        return values.isEmpty() ? null
                : values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
```

- [ ] **Step 4: 新增汇总数据模型**

在 `EvaluationRecorder.java` 类的**末尾**（最后一个 `}` 之前）插入两个内部类：

```java
    // ============================================================
    // 汇总数据模型
    // ============================================================

    /** 跨会话评估汇总 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvalSummary {
        /** 全部记录数 */
        private int totalRecords;

        /** 涉及的会话数 */
        private int sessions;

        /** 最早/最近的评估时间 */
        private LocalDateTime earliest;
        private LocalDateTime latest;

        /** Agent 链路汇总 */
        private TypeSummary agent;

        /** RAG 链路汇总 */
        private TypeSummary rag;
    }

    /** 单一链路的汇总。未产出的指标为 null（而非 0），避免"没数据"被读成"分数很烂" */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypeSummary {
        private int count;

        /** 仅 agent：Rubric 总分均值（0-100） */
        private Double avgRubricTotal;

        /** 仅 rag：以下三项均为 [0,1] */
        private Double avgContextPrecision;
        private Double avgFaithfulness;
        private Double avgAnswerRelevance;
    }
```

- [ ] **Step 5: 补齐 import**

在 `EvaluationRecorder.java` 的 import 区确认存在（缺失则添加）：

```java
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;
```

`java.util.List`、`java.util.ArrayList`、`java.io.IOException`、`java.nio.file.Files`、`java.nio.file.Path` 已在原文件中。

- [ ] **Step 6: 编译验证**

运行：
```bash
mvn -q compile
```
预期：BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/evaluation/EvaluationRecorder.java
git commit -m "feat: EvaluationRecorder 补齐落盘日志并新增跨会话汇总"
```

---

## Task 8: 汇总查询接口

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/controller/ConversationController.java`

- [ ] **Step 1: 新增端点**

在 `ConversationController.java` 中，把：

```java
    /**
     * 删除指定会话（永久删除）
     */
    @DeleteMapping("/conversations/{chatId}")
```

改为：

```java
    /**
     * 获取跨会话的评估汇总（全部会话的评估次数、平均分、时间跨度）
     * <p>
     * 区别于 {@code /ai/evals/{chatId}} 的单会话明细，这个接口回答"整体情况怎么样"。
     */
    @GetMapping("/evals/summary")
    public EvaluationRecorder.EvalSummary getEvalSummary() {
        return evaluationRecorder.summarize();
    }

    /**
     * 删除指定会话（永久删除）
     */
    @DeleteMapping("/conversations/{chatId}")
```

> **路由优先级说明：** `/ai/evals/summary` 与 `/ai/evals/{chatId}` 不冲突 —— Spring 的 `PathPattern` 匹配中，字面量段优先于变量段。即使不放心，`summary` 也只会是个不存在的 chatId，返回空列表，不会 500。

- [ ] **Step 2: 编译验证**

运行：
```bash
mvn -q compile
```
预期：BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/controller/ConversationController.java
git commit -m "feat: 新增 GET /ai/evals/summary 跨会话评估汇总接口"
```

---

## Task 9: 清理死代码

**Files:**
- Delete: `samples/demo/AgentEvaluationDemo.java`
- Delete: `src/test/java/com/qian/qianaiagent/evaluation/RealBaselineBuilderTest.java`
- Delete: `src/main/java/com/qian/qianaiagent/evaluation/AgentEvalCase.java`

**为什么：** 三个文件都被前面的任务取代。留着就是"两个入口做同一件事" —— 正是这次混乱的成因。

- [ ] **Step 1: 确认无残余引用**

运行：
```bash
grep -rn "AgentEvaluationDemo\|RealBaselineBuilderTest\|AgentEvalCase" --include=*.java src/ samples/ 2>/dev/null
```
预期：只输出这三个文件自身的匹配行。若出现其它文件的引用，**停下来**，先处理那些引用再删。

- [ ] **Step 2: 删除**

```bash
git rm samples/demo/AgentEvaluationDemo.java
git rm src/main/java/com/qian/qianaiagent/evaluation/AgentEvalCase.java
git rm src/test/java/com/qian/qianaiagent/evaluation/RealBaselineBuilderTest.java
```

- [ ] **Step 3: 确认 samples 目录已空则一并删除**

运行：
```bash
ls -R samples/ 2>/dev/null || echo "samples/ 已不存在"
```
若 `samples/` 下已无文件，执行：
```bash
rm -rf samples/
```

- [ ] **Step 4: 全量测试**

运行：
```bash
mvn -q test
```
预期：全部通过。若 `RealBaselineBuilderTest` 的删除导致某处 import 报错，说明 Step 1 的检查被跳过了。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "refactor: 删除被 EvalRunner 取代的三个死代码文件

AgentEvaluationDemo 不在 Maven 编译范围且 main 是空壳；
RealBaselineBuilderTest 是 @SpringBootTest 却会消耗真实 LLM 调用；
AgentEvalCase 与 Baseline 重复，删除前两者后无消费者。"
```

---

## 验收

三个任务都完成后，按以下顺序验证。**全部要有实际输出，不能只看代码。**

- [ ] **A. 单测全绿**

```bash
mvn -q test
```
预期：BUILD SUCCESS。新增测试数：`DeterministicScorerTest` 10 + `BaselineManagerTest` 9 + `EvalReportTest` 10 + `BaselineToolNameTest` 3。

- [ ] **B. 一键跑出报告（核心验收）**

```bash
mvn spring-boot:run -Peval
```
预期：
1. 控制台出现以 `══════` 分隔的「Agent 评估报告」，含总览、三个用例的逐条明细、结论
2. `logs/eval/报告_<时间戳>.txt` 被创建
3. 三个 `evaluation/baselines/*.json` 的 `baselineDeterministicScore` 从 `-1` 变成真实分数
4. 进程自动退出

> 前置条件：应用能正常启动（MySQL / PostgreSQL+PGVector / Redis / Neo4j 等依赖服务需在运行）。这与平时启动应用的要求一致。

- [ ] **C. 基线机制真的能红出来（判据）**

先跑一次 B 建立基线，然后人为改坏评分器 —— 把 `DeterministicScorer.PENALTY_KEYWORD_MISSING` 从 `20` 改成 `60`，再跑：

```bash
mvn spring-boot:run -Peval
```
预期：报告显示 `Δ -80` 之类的下降、结论行出现 `🔴 N 个用例分数下降`，退出码为 1：

```bash
echo $?
```
预期输出：`1`

验证后**务必改回 20**：
```bash
git checkout src/main/java/com/qian/qianaiagent/evaluation/DeterministicScorer.java
```

- [ ] **D. 工具名守卫能抓到改名**

临时把 `evaluation/baselines/知识库RAG检索.json` 里的 `searchKnowledgeBase` 改回 `ragSearch`，然后：

```bash
mvn -q test -Dtest=BaselineToolNameTest
```
预期：FAIL，报错指出 `"ragSearch" 未注册`。验证后改回来。

- [ ] **E. 在线链路有反馈**

启动应用（普通模式，不带 eval profile），真实对话一轮，然后：
1. 控制台应出现 `📊 评估已落盘: chatId=..., type=agent, 该会话累计 N 条`
2. `data/evals/<chatId>.json` 被创建
3. `curl http://localhost:8123/ai/evals/summary` 返回含 `totalRecords` / `agent` / `rag` 的 JSON

> 端口以 `application.yml` 里的 `server.port` 为准，先确认实际值。

---

## 已知边界

- **离线评测只覆盖 Agent 链路。** RAGAS 离线评测需要带 ground-truth 的评测集才能算 `contextRecall`，属于独立的 RAG 评测工作，不在本次范围。RAG 侧本次只做在线可观测性。
- **`EvalRunner` 需要完整应用上下文。** 它跑的是真实 `YuManus`，因此数据库、向量库等依赖服务必须可用。
- **工具名守卫测试覆盖 `ToolRegistration` 里当前注册的 8 个工具。** 若将来新增工具但忘了加进 `BaselineToolNameTest.registeredToolNames()`，新工具不在断言范围内。届时同步更新该方法的装配列表即可。
- **`PENALTY_PARAM_MISMATCH`（10 分）目前在 `checkToolCalls` 中未被使用** —— 参数不匹配和缺调用被合并成一条 -20。这是既有的行为，本次不改变，测试按现状固化。
