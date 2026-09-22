# 评测回归闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让「改坏了」这件事真的能被自动测出来 —— 收拢回归判定、建立真实基线、补齐用例、接上 CI。

**Architecture:** 把散落在 `EvalReport` 与 `BaselineManager` 两处的回归判定收拢到 `RegressionPolicy`，用「确定性严格 + Rubric 带容差」的双阈值消除矛盾；基线从 `-1` 哨兵换成人工确认的真实分；新增用例覆盖未测路径；脚本消费既有的 `exitCode()`。

**Tech Stack:** Java 21、Spring Boot 3.4.4、JUnit 5、GitHub Actions

> **对应 spec**：`docs/superpowers/specs/2026-09-22-agent-resilience-guardrails-design.md` §3.6–3.9
>
> ⚠️ **禁止改动任何 `🎯 Task N` 练习骨架**（spec §1.1）。本计划只碰 `evaluation/` 包、`evaluation/baselines/*.json` 与脚本。

---

## 关键事实（已实测）

| 事实 | 值 | 来源 |
|---|---|---|
| 4 条基线的分数 | **全部是 `-1` 哨兵**，从未建立过真实基线 | `evaluation/baselines/*.json` |
| `EvalReport.hasRegression()` | **只看 `deltaDeterministic < 0`**，忽略 Rubric | `EvalReport.java:52-57` |
| `BaselineManager` 的 verdict | **已包含 `deltaRubric < 0`**（第 197 行） | `BaselineManager.java:197` |
| → 后果 | 一次 rubric 掉 1 分：报告正文打印「🔴 分数下降！改坏了」，`conclusion()` 却说「🟢 无回归」，`exitCode()` 返回 0 | 两处口径不一致 |
| 首次运行的 `hasBaseline` | `autoEstablishBaseline` 返回 `hasBaseline(false)` → **回归判定被短路** | `BaselineManager.java:235-242` |
| `exitCode()` | 已就绪，**但仓库内无任何 CI 配置消费它** | `EvalReport.java:60`；无 `.github/workflows/` |
| `StabilityTester` | `new StabilityTester(k)` 中 `k>=2`，`test(caseName, Supplier<TestResult>)` | `StabilityTester.java:53-57, :72` |
| eval 开关 | `qian.eval.enabled=true`（`@ConditionalOnProperty`）；profile 由 `-Peval` 激活 | `EvalRunner.java:28`；`pom.xml:352-357` |

---

## File Structure

```
src/main/java/com/qian/qianaiagent/evaluation/
├── RegressionPolicy.java        新增：唯一的回归判定出处
├── EvalReport.java              修改：hasRegression/conclusion 改为调 RegressionPolicy
└── BaselineManager.java         修改：verdict 文案改为调 RegressionPolicy

src/test/java/com/qian/qianaiagent/evaluation/
├── RegressionPolicyTest.java    新增
└── EvalReportTest.java          修改：补 Rubric 阈值相关用例

evaluation/baselines/
├── 工具失败自愈.json              新增
├── 多工具串联.json                新增
└── （既有 4 个）                  修改：-1 → 真实分数

scripts/
└── eval-regression.sh           新增

.github/workflows/
└── eval.yml                     新增（可选，见 Task 6 说明）
```

---

## Task 1: `RegressionPolicy` —— 判定收拢到一处

**Files:**
- Create: `src/main/java/com/qian/qianaiagent/evaluation/RegressionPolicy.java`
- Test: `src/test/java/com/qian/qianaiagent/evaluation/RegressionPolicyTest.java`

- [ ] **Step 1: 写失败的测试**

创建 `src/test/java/com/qian/qianaiagent/evaluation/RegressionPolicyTest.java`：

```java
package com.qian.qianaiagent.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RegressionPolicyTest {

    @ParameterizedTest(name = "确定性 Δ{0} → 回归={1}")
    @CsvSource({
            "-1, true",    // 规则打分无方差，掉 1 分就是真回归
            "-10, true",
            "0, false",
            "5, false"
    })
    @DisplayName("确定性分数：任何下降都算回归")
    void deterministicRegression(int delta, boolean expected) {
        assertThat(RegressionPolicy.isDeterministicRegressed(delta)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "Rubric Δ{0} → 回归={1}")
    @CsvSource({
            "-9, false",    // 阈值以内，视为 LLM 打分的自然波动
            "-10, true",    // 恰好到阈值，算回归（边界取闭区间）
            "-11, true",
            "-1, false",    // 关键：掉 1 分不再报回归，否则报警全是噪声
            "0, false",
            "5, false"
    })
    @DisplayName("Rubric 分数：需降幅达到阈值才算回归")
    void rubricRegression(int delta, boolean expected) {
        assertThat(RegressionPolicy.isRubricRegressed(delta)).isEqualTo(expected);
    }

    @Test
    @DisplayName("无可比基线时一律不算回归")
    void noBaselineMeansNoRegression() {
        BaselineManager.ComparisonReport noBaseline = BaselineManager.ComparisonReport.builder()
                .caseName("x").hasBaseline(false).deltaDeterministic(-50).build();

        assertThat(RegressionPolicy.isRegressed(noBaseline)).isFalse();
    }

    @Test
    @DisplayName("comparison 为 null 时不算回归（用例未评分）")
    void nullComparisonMeansNoRegression() {
        assertThat(RegressionPolicy.isRegressed(null)).isFalse();
    }

    @Test
    @DisplayName("确定性下降 → 回归")
    void detectsDeterministicDrop() {
        var c = BaselineManager.ComparisonReport.builder()
                .caseName("x").hasBaseline(true)
                .deltaDeterministic(-5).deltaRubric(0).build();

        assertThat(RegressionPolicy.isRegressed(c)).isTrue();
    }

    @Test
    @DisplayName("Rubric 大幅下降 → 回归（这正是修复前测不出来的情况）")
    void detectsLargeRubricDrop() {
        var c = BaselineManager.ComparisonReport.builder()
                .caseName("x").hasBaseline(true)
                .deltaDeterministic(0).deltaRubric(-20).build();

        assertThat(RegressionPolicy.isRegressed(c)).isTrue();
    }

    @Test
    @DisplayName("Rubric 小幅波动 → 不回归（修复前会误报）")
    void ignoresSmallRubricNoise() {
        var c = BaselineManager.ComparisonReport.builder()
                .caseName("x").hasBaseline(true)
                .deltaDeterministic(0).deltaRubric(-5).build();

        assertThat(RegressionPolicy.isRegressed(c)).isFalse();
    }

    @Test
    @DisplayName("阈值常量与提示文案中的数字一致")
    void thresholdIsExposed() {
        assertThat(RegressionPolicy.RUBRIC_REGRESSION_THRESHOLD).isEqualTo(10);
        assertThat(RegressionPolicy.describe()).contains("10");
    }

    @ParameterizedTest(name = "Δdet={0}, Δrubric={1} → {2}")
    @CsvSource({
            "-1,   0,  🔴",   // 确定性掉 1 分 → 说改坏了
            " 0, -20,  🔴",   // Rubric 掉 20 分 → 说改坏了
            " 0,  -5,  🟡",   // Rubric 掉 5 分 → 说持平（不再误报改坏）
            " 5,  10,  🟢",   // 上升
            " 0,   0,  🟡"    // 持平
    })
    @DisplayName("verdictOf 文案与判定口径一致")
    void verdictMatchesPolicy(int deltaDet, int deltaRubric, String expectedPrefix) {
        assertThat(RegressionPolicy.verdictOf(deltaDet, deltaRubric)).startsWith(expectedPrefix);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw test -Dtest=RegressionPolicyTest`
Expected: 编译失败，`RegressionPolicy` 找不到符号

- [ ] **Step 3: 写实现**

创建 `src/main/java/com/qian/qianaiagent/evaluation/RegressionPolicy.java`：

```java
package com.qian.qianaiagent.evaluation;

/**
 * 回归判定 —— 全项目<b>唯一</b>的判定出处。
 *
 * <h2>为什么必须收拢</h2>
 * 修复前，判定散落在两处且口径不一致：
 * <ul>
 *   <li>{@code EvalReport.hasRegression()} 只看确定性分数</li>
 *   <li>{@code BaselineManager.compareWithBaseline()} 的 verdict 却把 Rubric 也算进去</li>
 * </ul>
 * 后果是一次 Rubric 掉 1 分的运行，报告正文逐条打印「🔴 分数下降！改坏了」，
 * 而结论行说「🟢 无回归」、退出码为 0 —— <b>同一份报告自相矛盾</b>。
 *
 * <h2>为什么两类分数用不同阈值</h2>
 * <ul>
 *   <li><b>确定性分数</b>由规则算出，同样输入必得同样结果，无方差 ——
 *       因此任何下降都是真回归，用严格 {@code < 0}</li>
 *   <li><b>Rubric 分数</b>由 LLM 打分，代码一行没动、重跑也可能掉几分 ——
 *       直接用 {@code < 0} 会让报警充满噪声。噪声大的报警等于没有报警，
 *       所以必须给容差</li>
 * </ul>
 *
 * <h2>⚠️ 阈值的取值依据，以及一个前置依赖</h2>
 * 10 分这个值是<b>待验证的默认值</b>，不是实测结论。正确做法是用
 * {@link StabilityTester} 跑重复实验，观测<b>同版本</b>分数的自然波动幅度，再定阈值。
 *
 * <p><b>若实测波动接近或超过 10 分，本类的方案不成立</b> —— 那说明 Rubric 打分本身
 * 不稳定，此时应优先解决打分稳定性，而不是调大阈值。
 * <b>阈值调大到能吞下噪声的程度，就等于关掉了这项检查。</b>
 */
public final class RegressionPolicy {

    /**
     * Rubric 回归阈值。降幅达到该值（含）才算回归。
     * <p>
     * 依据见类注释 —— 这是待实测验证的默认值。
     */
    public static final int RUBRIC_REGRESSION_THRESHOLD = 10;

    private RegressionPolicy() {
    }

    /** 确定性分数：任何下降都是回归（规则打分无方差）。 */
    public static boolean isDeterministicRegressed(int deltaDeterministic) {
        return deltaDeterministic < 0;
    }

    /** Rubric 分数：降幅达到阈值才算回归。 */
    public static boolean isRubricRegressed(int deltaRubric) {
        return deltaRubric <= -RUBRIC_REGRESSION_THRESHOLD;
    }

    /**
     * 综合判定。
     *
     * @param comparison 基线对比结果，可为 null（用例未评分或无基线）
     * @return 是否构成回归
     */
    public static boolean isRegressed(BaselineManager.ComparisonReport comparison) {
        if (comparison == null || !comparison.isHasBaseline()) {
            return false;
        }
        return isDeterministicRegressed(comparison.getDeltaDeterministic())
                || isRubricRegressed(comparison.getDeltaRubric());
    }

    /**
     * 依据两个 delta 给出判定文案。
     *
     * <p>按原始 delta 而非 {@link BaselineManager.ComparisonReport} 取参：
     * {@code BaselineManager} 在算出 delta 的那一刻就要这句话，
     * 此时 {@code ComparisonReport} 尚未构造完成，传对象会迫使调用方先造一个半成品。
     */
    public static String verdictOf(int deltaDeterministic, int deltaRubric) {
        if (isDeterministicRegressed(deltaDeterministic) || isRubricRegressed(deltaRubric)) {
            return "🔴 分数下降！改坏了，请检查最近的改动。";
        }
        if (deltaDeterministic > 0 || deltaRubric > 0) {
            return "🟢 分数上升！改好了。";
        }
        return "🟡 分数持平。";
    }

    /** 供报告与日志展示的阈值说明。 */
    public static String describe() {
        return "确定性分数任何下降即回归；Rubric 降幅 ≥ " + RUBRIC_REGRESSION_THRESHOLD + " 分才算回归";
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./mvnw test -Dtest=RegressionPolicyTest`
Expected: PASS（11 个测试，含参数化的两个）

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/evaluation/RegressionPolicy.java \
        src/test/java/com/qian/qianaiagent/evaluation/RegressionPolicyTest.java
git commit -m "feat(eval): 回归判定收拢到 RegressionPolicy，Rubric 带容差"
```

---

## Task 2: 接到 `EvalReport` 与 `BaselineManager`

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/evaluation/EvalReport.java:51-81`
- Modify: `src/main/java/com/qian/qianaiagent/evaluation/BaselineManager.java:196-203`
- Modify: `src/test/java/com/qian/qianaiagent/evaluation/EvalReportTest.java`

- [ ] **Step 1: 改 `EvalReport.hasRegression()`**

把 `EvalReport.java` 第 51-57 行替换为：

```java
    /** 是否存在回归的用例。判定规则见 {@link RegressionPolicy}。 */
    public boolean hasRegression() {
        return outcomes.stream().anyMatch(o -> RegressionPolicy.isRegressed(o.getComparison()));
    }
```

- [ ] **Step 2: 改 `EvalReport.conclusion()`**

把第 64-82 行的 `conclusion()` 替换为：

```java
    /** 一句话结论 */
    public String conclusion() {
        if (outcomes.isEmpty()) {
            return "⚠️ 未找到任何用例，请检查 evaluation/baselines/ 目录";
        }
        long regressions = outcomes.stream()
                .filter(o -> RegressionPolicy.isRegressed(o.getComparison()))
                .count();
        if (regressions > 0) {
            return "🔴 " + regressions + " 个用例分数下降，请检查最近改动"
                    + "（" + RegressionPolicy.describe() + "）";
        }
        long newly = outcomes.stream()
                .filter(o -> o.getComparison() != null && o.getComparison().isNewBaseline())
                .count();
        if (newly == outcomes.size()) {
            return "🆕 全部用例为新建立基线，下次运行起开始对比";
        }
        return "🟢 无回归";
    }
```

- [ ] **Step 3: 改 `BaselineManager` 的 verdict**

把 `BaselineManager.java` 第 196-203 行替换为：

```java
        // 判定收拢到 RegressionPolicy —— 此前这里的口径与 EvalReport.hasRegression()
        // 不一致，导致报告正文说「改坏了」而结论说「无回归」
        String verdict = RegressionPolicy.verdictOf(deltaDeterministic, deltaRubric);
```

- [ ] **Step 4: 补 `EvalReportTest` 的 Rubric 用例**

在现有 `EvalReportTest.java` 中追加（保留原有全部用例）：

```java
    @Test
    @DisplayName("Rubric 大幅下降也算回归（修复前测不出来）")
    void rubricDropCountsAsRegression() {
        EvalReport report = EvalReport.builder()
                .generatedAt(LocalDateTime.now())
                .outcomes(List.of(EvalReport.CaseOutcome.builder()
                        .caseName("x").scored(true)
                        .deterministicScore(90).rubricScore(50)
                        .comparison(BaselineManager.ComparisonReport.builder()
                                .caseName("x").hasBaseline(true)
                                .deltaDeterministic(0).deltaRubric(-20)
                                .build())
                        .build()))
                .build();

        assertThat(report.hasRegression()).isTrue();
        assertThat(report.exitCode()).isEqualTo(1);
        assertThat(report.conclusion()).contains("分数下降");
    }

    @Test
    @DisplayName("Rubric 小幅波动不算回归，且结论与正文不矛盾")
    void smallRubricNoiseIsNotRegression() {
        EvalReport report = EvalReport.builder()
                .generatedAt(LocalDateTime.now())
                .outcomes(List.of(EvalReport.CaseOutcome.builder()
                        .caseName("x").scored(true)
                        .deterministicScore(90).rubricScore(85)
                        .comparison(BaselineManager.ComparisonReport.builder()
                                .caseName("x").hasBaseline(true)
                                .deltaDeterministic(0).deltaRubric(-5)
                                .summary("🟡 分数持平。")
                                .build())
                        .build()))
                .build();

        assertThat(report.hasRegression()).isFalse();
        assertThat(report.exitCode()).isZero();
        assertThat(report.conclusion()).isEqualTo("🟢 无回归");
    }

    @Test
    @DisplayName("首次运行（无基线）不算回归")
    void firstRunIsNotRegression() {
        EvalReport report = EvalReport.builder()
                .generatedAt(LocalDateTime.now())
                .outcomes(List.of(EvalReport.CaseOutcome.builder()
                        .caseName("x").scored(true)
                        .deterministicScore(50).rubricScore(40)
                        .comparison(BaselineManager.ComparisonReport.builder()
                                .caseName("x").hasBaseline(false).newBaseline(true)
                                .summary("🆕 已自动建立基线")
                                .build())
                        .build()))
                .build();

        assertThat(report.hasRegression()).isFalse();
        assertThat(report.exitCode()).isZero();
    }
```

若文件缺少 import，补上：

```java
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
```

- [ ] **Step 5: 运行测试**

Run: `./mvnw test -Dtest='EvalReportTest,BaselineManagerTest,RegressionPolicyTest'`
Expected: PASS

> **若 `BaselineManagerTest` 的既有用例失败**：它可能断言了 verdict 的原文。判定逻辑已改为
> 「Rubric 降幅 <10 不算回归」，与旧行为不同是**预期的**。逐个核对失败的断言，
> 确认它是「旧口径的断言」而非新 bug，然后更新断言。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/evaluation/EvalReport.java \
        src/main/java/com/qian/qianaiagent/evaluation/BaselineManager.java \
        src/test/java/com/qian/qianaiagent/evaluation/EvalReportTest.java
git commit -m "fix(eval): 消除报告正文与结论的回归判定矛盾"
```

---

## Task 3: 扩充评测用例集

**Files:**
- Create: `evaluation/baselines/工具失败自愈.json`
- Create: `evaluation/baselines/多工具串联.json`
- Modify: `src/test/java/com/qian/qianaiagent/evaluation/BaselineToolNameTest.java`

- [ ] **Step 1: 先看现有守卫测试在断言什么**

读 `src/test/java/com/qian/qianaiagent/evaluation/BaselineToolNameTest.java`。
它断言基线 JSON 里的工具名必须是真实注册的工具 —— 目的是防止历史 bug 复现（工具改名后基线静默失效）。新增用例会自动被它覆盖，无需改它，但**必须先跑一次确认新用例的工具名拼写正确**。

- [ ] **Step 2: 新增「多工具串联」用例**

创建 `evaluation/baselines/多工具串联.json`：

```json
{
  "caseName": "多工具串联",
  "query": "请先检索本地知识库了解 Spring AI，再用搜索引擎查一下它的最新版本动态，最后用两三句话总结。",
  "createdAt": "2026-09-22T10:00:00",
  "expectedBehavior": {
    "expectedToolCalls": [
      {
        "toolName": "searchKnowledgeBase",
        "paramContains": null,
        "resultContains": null
      },
      {
        "toolName": "searchWeb",
        "paramContains": null,
        "resultContains": null
      }
    ],
    "expectedResponseKeywords": ["Spring AI"],
    "maxToolCalls": 8
  },
  "baselineDeterministicScore": -1,
  "baselineRubricScore": -1,
  "notes": "覆盖「同一任务内先后调用 ≥2 个工具」的路径，验证 trace 步骤顺序与 maxSteps 预算。paramContains 全部留空 —— 工具参数由 LLM 自由生成，锁死子串会让用例随机失败（这条经验来自既有的 对比SpringAI与LangChain4j 用例）。分数 -1 表示尚未建立基线。"
}
```

- [ ] **Step 3: 新增「工具失败自愈」用例**

创建 `evaluation/baselines/工具失败自愈.json`：

```json
{
  "caseName": "工具失败自愈",
  "query": "请读取文件 /nonexistent-dir/definitely-missing.txt 并把内容告诉我。如果读不到，就直接说明读不到，不要编造内容。",
  "createdAt": "2026-09-22T10:00:00",
  "expectedBehavior": {
    "expectedToolCalls": [
      {
        "toolName": "readFile",
        "paramContains": "definitely-missing.txt",
        "resultContains": null
      }
    ],
    "expectedResponseKeywords": [],
    "maxToolCalls": 5
  },
  "baselineDeterministicScore": -1,
  "baselineRubricScore": -1,
  "notes": "覆盖工具失败路径：readFile 必然失败，验证 ToolCallAgent 的失败计数与自愈提示生效，且 Agent 不会编造文件内容。expectedResponseKeywords 留空是刻意的 —— 无法预知模型用哪个词表达「读不到」，锁死关键词会随机失败。该用例同时检验 RubricScorer 的幻觉检测：编造内容会导致 Rubric 的「忠实度」维度扣分。"
}
```

- [ ] **Step 4: 运行工具名守卫测试**

Run: `./mvnw test -Dtest=BaselineToolNameTest`
Expected: PASS。**若失败，说明 `readFile` / `searchKnowledgeBase` / `searchWeb` 中某个名字与实际注册的工具名不符** —— 打开 `tools/ToolRegistration.java` 核对实际工具名后修正 JSON，**不要改测试**。

- [ ] **Step 5: 提交**

```bash
git add evaluation/baselines/多工具串联.json evaluation/baselines/工具失败自愈.json
git commit -m "test(eval): 新增多工具串联与工具失败自愈两条用例"
```

---

## Task 4: 回归脚本

**Files:**
- Create: `scripts/eval-regression.sh`

- [ ] **Step 1: 写脚本**

创建 `scripts/eval-regression.sh`：

```bash
#!/usr/bin/env bash
#
# 离线评测回归检查。
#
# 退出码语义（由 EvalReport.exitCode() 决定，这里只负责透传）：
#   0 = 无回归
#   1 = 有回归（分数下降超过阈值）
#
# 前置条件：需要真实 API Key + PostgreSQL(PGVector) + Redis 都在跑，
# 否则应用起不来，本脚本会以非 0 退出并提示。
set -uo pipefail

cd "$(dirname "$0")/.." || exit 9

if [ ! -f .env ]; then
  echo "❌ 缺少 .env —— 评测需要真实 API Key，请先按 .env.template 配置" >&2
  exit 9
fi

echo "════ 开始离线评测回归检查 ════"
echo "（这会真实调用 LLM，消耗 token 额度）"
echo

# -Peval 激活 eval profile（见 pom.xml 的 <profiles>）
./mvnw -q spring-boot:run -Peval
exit_code=$?

echo
if [ "$exit_code" -eq 0 ]; then
  echo "✅ 无回归"
else
  echo "🔴 检测到回归（退出码 $exit_code），请查看上方报告与 logs/eval/ 下的报告文件" >&2
fi

exit "$exit_code"
```

- [ ] **Step 2: 赋予执行权限**

Run: `chmod +x scripts/eval-regression.sh`
Expected: 无输出

- [ ] **Step 3: 验证脚本能在缺 .env 时正确拒绝**

Run: `mv .env .env.bak && ./scripts/eval-regression.sh; echo "exit=$?"; mv .env.bak .env`
Expected: 打印 `❌ 缺少 .env`，`exit=9`

> 这一步验证的是「脚本不会在没配好的情况下假装跑成功」。恢复 `.env` 的操作已包含在同一条命令里。

- [ ] **Step 4: 提交**

```bash
git add scripts/eval-regression.sh
git commit -m "test(eval): 新增离线评测回归脚本"
```

---

## Task 5: 建立真实基线 ⚠️ 需要真实环境

> **本任务是全计划唯一无法自动化的一步**：必须真实调用 LLM 才能得到分数。
> 需要 `.env` 里的 API Key、PostgreSQL(PGVector)、Redis 三者都可用。

**Files:**
- Modify: `evaluation/baselines/*.json`（6 个文件的分数字段）

- [ ] **Step 1: 跑一次评测（首次运行会自动建立基线）**

Run: `./scripts/eval-regression.sh`
Expected: 退出码 0，报告显示 6 个用例「🆕 已自动建立基线」

- [ ] **Step 2: 人工审查结果是否可信 —— 不要照单全收**

Run: `cat logs/eval/报告_*.txt | tail -100`

逐条核对：

| 检查项 | 为什么 |
|---|---|
| 确定性分是否 ≥60（及格） | 若某条用例分数很低，先确认是**用例期望写得不对**，而不是急着建基线 —— 拿错误期望当基线，以后永远测不出真问题 |
| trace 里是否出现了期望的工具 | 尤其「多工具串联」应看到两步工具调用 |
| 「工具失败自愈」的 rubric 忠实度分 | 模型若编造了文件内容，该维度应显著扣分。**若模型确实编造且分数仍高，说明幻觉检测失灵**，是另一个待查问题 |
| Rubric 各维度分是否合理 | 找出「推理 25/25 但幻觉率 80%」这类矛盾评分 |

- [ ] **Step 3: 若某条用例期望写错了，改 JSON 后重跑**

改 `evaluation/baselines/<用例>.json` 的 `expectedBehavior`，并把两个分数**改回 `-1`** 强制重建基线，然后重跑 Step 1。

- [ ] **Step 4: 分数确认无误后，把 `notes` 改写为人已确认**

对每个基线 JSON，把 `notes` 末尾的「首次运行结果，未经人工确认」改为：

```
"notes": "✅ 基线已人工确认于 2026-09-22。建立时使用模型 deepseek-chat。"
```

- [ ] **Step 5: 复跑一次确认基线生效且无回归**

Run: `./scripts/eval-regression.sh`
Expected: 退出码 0；报告显示各用例 `hasBaseline=true`、`Δ 0`

> **若此时出现回归**：说明基线建立后同版本重跑就掉了分 —— 这正是 Rubric 方差的直接证据。
> **记录下这个波动幅度**，它是 Task 1 中「10 分阈值」是否合理的实测依据。
> 波动 ≥10 分的话，回到 `RegressionPolicy` 按类注释的说明处理。

- [ ] **Step 6: 提交基线**

```bash
git add evaluation/baselines/
git commit -m "test(eval): 建立 6 条用例的人工确认基线"
```

---

## Task 6: 接 CI

**Files:**
- Create: `.github/workflows/eval.yml`

> **读之前先看这段**：评测要真实调用 LLM，需要 PostgreSQL(PGVector) + Redis
> **以及真实 API Key**，后者必须走 repo secrets，且**每次 CI 运行都真实消耗 token 额度**。
>
> **若不便承担**，跳过本任务即可 —— Task 4 的脚本已经提供了「改坏了会明确失败」的核心价值，
> CI 只是把它自动化。把它作为**发版前人工执行的检查项**同样成立。

- [ ] **Step 1: 写 workflow**

创建 `.github/workflows/eval.yml`：

```yaml
name: Agent 评测回归

on:
  # 只跑手动触发与 PR —— 不跑 push，避免每次提交都烧 token
  workflow_dispatch:
  pull_request:
    branches: [main, master]

jobs:
  eval:
    runs-on: ubuntu-latest
    timeout-minutes: 30

    services:
      postgres:
        image: pgvector/pgvector:pg16
        env:
          POSTGRES_PASSWORD: postgres
          POSTGRES_DB: qian_ai_agent
        ports: ['5432:5432']
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
      redis:
        image: redis:7
        ports: ['6379:6379']
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: 准备 .env
        run: |
          cat > .env <<'EOF'
          SPRING_AI_OPENAI_API_KEY=${{ secrets.SPRING_AI_OPENAI_API_KEY }}
          SPRING_AI_DASHSCOPE_API_KEY=${{ secrets.SPRING_AI_DASHSCOPE_API_KEY }}
          EOF

      - name: 跑评测回归
        run: ./scripts/eval-regression.sh

      - name: 上传评测报告
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: eval-report
          path: logs/eval/
```

- [ ] **Step 2: 本地校验 YAML 语法**

Run: `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/eval.yml')); print('YAML OK')"`
Expected: `YAML OK`

- [ ] **Step 3: 提交**

```bash
git add .github/workflows/eval.yml
git commit -m "ci: 新增 Agent 评测回归 workflow（手动触发 + PR）"
```

- [ ] **Step 4: 提示用户配置 secrets**

⚠️ **这一步需要用户手动操作**，Agent 无法代做：
在 GitHub 仓库 Settings → Secrets and variables → Actions 中新增
`SPRING_AI_OPENAI_API_KEY` 与 `SPRING_AI_DASHSCOPE_API_KEY`。
**未配置前该 workflow 会失败** —— 这是预期的，不是 bug。

---

## 完成标准

- [ ] `./mvnw test` 全绿（新增 `RegressionPolicyTest`，`EvalReportTest` 扩 3 个用例）
- [ ] `evaluation/baselines/` 下 6 个用例**全部有真实分数**（无 `-1`）
- [ ] 复跑 `./scripts/eval-regression.sh` 退出码为 0
- [ ] **验证检查真的会红**：临时把某条基线的 `baselineDeterministicScore` 调高 20 分，重跑脚本，确认退出码为 1 且报告明确说「分数下降」 —— **这一步不做，等于没验证过回归检查能不能工作**
- [ ] 把基线分数改回，确认恢复退出码 0
- [ ] 未改动任何 `🎯 Task N` 骨架文件
