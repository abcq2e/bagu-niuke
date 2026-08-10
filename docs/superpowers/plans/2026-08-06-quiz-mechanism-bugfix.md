# 出题机制正确性修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复严格顺序出题链路中导致「丢题、评错题、误跳过、进度污染、复习删不掉」的缺陷，保持「后端出题、AI 只点评」铁律不变。

**Architecture:** 不改动 16 方向 × N 题/轮的确定性游标模型；只修正推进时机、指令判定、游标恢复策略、复习评题/删题语义，以及前端方向解析与 SSE 完成信号。面试模式继续用 `SequentialRotationService` + 流尾 `【本轮考题】`；复习模式对齐「评上一题、删原题干」语义。

**Tech Stack:** Java 17 / Spring Boot / Reactor (`Flux`) / JUnit 5 / Vue 3 (`ChatView.vue`)

**Related specs:**
- `docs/superpowers/specs/2026-07-23-sequential-quiz-rotation-design.md`
- `docs/superpowers/specs/2026-07-23-quiz-and-ability-profile-fix-design.md`

---

## 文件改动总览

| 文件 | 职责 |
|------|------|
| `src/main/java/com/qian/qianaiagent/app/QuizApp.java` | 指令精确匹配；换方向后推进；流失败回滚游标；会话态 key 对齐 |
| `src/main/java/com/qian/qianaiagent/app/SequentialRotationService.java` | 去掉硬编码紧急恢复；收紧迁移；提供 `snapshot/restore` 或 `rollbackLastAsk` |
| `src/main/java/com/qian/qianaiagent/app/WrongQuestionReviewService.java` | 评 `prevStem`；加锁；重试检测 |
| `src/main/java/com/qian/qianaiagent/app/UserAbilityService.java` | 错题本写入放宽；复习删除按题干匹配 |
| `src/main/java/com/qian/qianaiagent/app/UserAbilityProfile.java` | `removeWrongQuestionByText` |
| `src/main/java/com/qian/qianaiagent/controller/AiController.java` | 面试/复习成功路径追加 `[DONE]` |
| `qian-ai-agent-frontend/src/views/ChatView.vue` | 只认 `【本轮考题】` 更新方向 |
| `src/test/java/com/qian/qianaiagent/app/QuizCommandMatcherTest.java` | 新建：指令匹配单测 |
| `src/test/java/com/qian/qianaiagent/app/SequentialRotationServiceTest.java` | 新建：游标推进/回滚/恢复单测 |
| `src/test/java/com/qian/qianaiagent/app/UserAbilityWrongBookTest.java` | 新建：错题本写入/删除单测 |
| `src/test/java/com/qian/qianaiagent/app/WrongQuestionReviewServiceTest.java` | 新建：复习评题语义单测 |

**Out of scope（本计划不做）：**
- 重写题库加载 / bagu 过滤规则
- 删除死代码 `AskedPointTracker` / `QuestionSelector`（可另开清理 PR）
- 画像雷达图 UI 大改

---

## 修复优先级对照

| # | 问题 | Task |
|---|------|------|
| P0 | `contains("过")` 误触发跳过 | Task 1 |
| P0 | 硬编码紧急恢复污染新会话 | Task 2 |
| P0 | AI 失败后游标已推进 | Task 3 |
| P0 | 换方向首题出示两次 | Task 4 |
| P0 | 复习评错题 + 删不掉 | Task 5–6 |
| P1 | 低分但 `currentWps` 空不进错题本 | Task 6 |
| P1 | 游标 key 与会话态 Map 不一致 | Task 7 |
| P1 | 前端方向解析 / 缺 `[DONE]` | Task 8 |

---

### Task 1: 指令匹配改为整句精确匹配

**Files:**
- Create: `src/main/java/com/qian/qianaiagent/app/QuizCommandMatcher.java`
- Create: `src/test/java/com/qian/qianaiagent/app/QuizCommandMatcherTest.java`
- Modify: `src/main/java/com/qian/qianaiagent/app/QuizApp.java`（`isNextCmd` / `isSkipDir` / `isResetMemory` 调用处）
- Modify: `src/main/java/com/qian/qianaiagent/app/WrongQuestionReviewService.java`（`isNextCmd` 调用处）

- [ ] **Step 1: 写失败单测**

```java
package com.qian.qianaiagent.app;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuizCommandMatcherTest {

    @Test
    void exactNextCommandsMatch() {
        assertTrue(QuizCommandMatcher.isNext("过"));
        assertTrue(QuizCommandMatcher.isNext("下一题"));
        assertTrue(QuizCommandMatcher.isNext("SKIP")); // 大小写不敏感
        assertTrue(QuizCommandMatcher.isNext("  继续  "));
    }

    @Test
    void answerContainingSubstringMustNotMatch() {
        assertFalse(QuizCommandMatcher.isNext("线程的创建过程是这样的"));
        assertFalse(QuizCommandMatcher.isNext("可以通过线程池来创建"));
        assertFalse(QuizCommandMatcher.isNext("过滤掉无效请求"));
        assertFalse(QuizCommandMatcher.isNext("I will pass the object"));
        assertFalse(QuizCommandMatcher.isSkipDir("我想换个方向深入理解一下")); // 非整句
    }

    @Test
    void exactSkipAndResetMatch() {
        assertTrue(QuizCommandMatcher.isSkipDir("换个方向"));
        assertTrue(QuizCommandMatcher.isResetMemory("重置记忆"));
        assertFalse(QuizCommandMatcher.isResetMemory("请帮我重置记忆再继续"));
    }
}
```

- [ ] **Step 2: 跑测确认失败**

```bash
mvn -Dtest=QuizCommandMatcherTest test
```

Expected: 编译失败（类不存在）或断言失败。

- [ ] **Step 3: 实现 matcher**

```java
package com.qian.qianaiagent.app;

import java.util.Set;

/** 面试指令整句匹配：禁止 contains，避免「过程/过滤/通过」误触发。 */
public final class QuizCommandMatcher {
    private QuizCommandMatcher() {}

    public static final Set<String> NEXT_CMDS = Set.of(
            "下一题", "下一个", "下一条", "next", "next one",
            "go on", "继续", "继续吧", "下题", "下一道",
            "过", "过吧", "下一问", "跳过", "pass", "skip"
    );

    public static final Set<String> SKIP_DIR_CMDS = Set.of(
            "换个方向", "换个话题", "换方向", "换话题",
            "下一方向", "下一个话题", "next topic"
    );

    public static final Set<String> RESET_MEMORY_CMDS = Set.of(
            "重置记忆", "清空记忆", "清理记忆", "重置会话",
            "reset memory", "clean memory", "clear memory"
    );

    public static boolean isNext(String message) {
        return matches(message, NEXT_CMDS);
    }

    public static boolean isSkipDir(String message) {
        return matches(message, SKIP_DIR_CMDS);
    }

    public static boolean isResetMemory(String message) {
        return matches(message, RESET_MEMORY_CMDS);
    }

    private static boolean matches(String message, Set<String> cmds) {
        if (message == null) return false;
        String n = message.trim().toLowerCase();
        return cmds.contains(n);
    }
}
```

- [ ] **Step 4: 改 QuizApp / WrongQuestionReviewService 调用点**

`QuizApp.java`：
- 删除（或 `@Deprecated` 转发到 matcher）原 `NEXT_CMDS` / `SKIP_DIR_CMDS` / `RESET_MEMORY_CMDS` 字段；对外若有引用，改为：
  `public static final Set<String> NEXT_CMDS = QuizCommandMatcher.NEXT_CMDS;`
- 将：
  ```java
  boolean isNextCmd = NEXT_CMDS.contains(normalized)
          || NEXT_CMDS.stream().anyMatch(normalized::contains);
  ```
  改为：
  ```java
  boolean isNextCmd = QuizCommandMatcher.isNext(message);
  boolean isSkipDir = QuizCommandMatcher.isSkipDir(message);
  boolean isResetMemory = QuizCommandMatcher.isResetMemory(message);
  ```
  （可删除仅用于 contains 的 `normalized` 局部变量，若别处不用。）

`WrongQuestionReviewService.java` 同样改为 `QuizCommandMatcher.isNext(message)`。

- [ ] **Step 5: 跑测通过并提交**

```bash
mvn -Dtest=QuizCommandMatcherTest test
```

```bash
git add src/main/java/com/qian/qianaiagent/app/QuizCommandMatcher.java \
        src/test/java/com/qian/qianaiagent/app/QuizCommandMatcherTest.java \
        src/main/java/com/qian/qianaiagent/app/QuizApp.java \
        src/main/java/com/qian/qianaiagent/app/WrongQuestionReviewService.java
git commit -m "$(cat <<'EOF'
fix: 面试指令改为整句精确匹配，避免答案子串误跳过

EOF
)"
```

---

### Task 2: 收紧游标恢复 —— 删除硬编码污染路径

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/app/SequentialRotationService.java`（`getOrCreateCursor` / `tryEmergencyRecovery` / `HARDCODED_PROGRESS` / `findBestLegacyCursor` / `tryMigrateCursor`）
- Create: `src/test/java/com/qian/qianaiagent/app/SequentialRotationServiceTest.java`

**目标行为：**
1. 无游标 → **直接新建**从 round=0 / index=0，绝不灌硬编码进度。
2. 迁移仅允许：`fallbackChatId` 精确匹配，或同用户前缀约束（见下）。
3. 删除 `HARDCODED_PROGRESS` 与「扫描失败用硬编码兜底」。
4. `tryEmergencyRecovery` 降级为可选：仅当 `cursorKey` 已存在损坏文件时，从**同一 chatId 备份**恢复；默认关闭全局扫盘。

- [ ] **Step 1: 写失败单测（新会话不继承硬编码）**

在 `SequentialRotationServiceTest` 中用临时目录（通过反射或包内可见测试钩子设置 `cursorDir`）。若当前 `cursorDir` 写死为 `.quiz-cursor`，本测用唯一 `chatId` 前缀，并在 `@AfterEach` 删除对应 json：

```java
@Test
void newSessionStartsAtZeroWithoutHardcodedProgress() {
    String key = "chat_test_fresh_" + System.currentTimeMillis();
    TopicDocumentCache cache = /* @SpringBootTest 注入 或 mock 返回固定 total>0 */;
    service.initSession(key, cache);

    assertEquals(0, service.getCurrentRound(key));
    assertEquals("Java基础与集合", service.currentTopic(key));
    assertEquals(0, service.getTotalAskedThisDirection(key));
    // 不应出现 nextStartIndex=15 / round=4
}
```

若项目更适合纯单元测，可为 `SequentialRotationService` 增加包可见构造或 `Path cursorDir` 注入（仅测试用 `@VisibleForTesting` setter），避免污染真实 `.quiz-cursor`。

- [ ] **Step 2: 跑测确认当前会失败或被硬编码污染**

```bash
mvn -Dtest=SequentialRotationServiceTest#newSessionStartsAtZeroWithoutHardcodedProgress test
```

- [ ] **Step 3: 改 `getOrCreateCursor` 恢复阶梯**

将 Step 4 紧急恢复改为：

```java
// === Step 4: 不再紧急扫盘/硬编码 ===
// 直接落 Step 5 新建游标
```

删除：
- `HARDCODED_PROGRESS` 常量
- `tryEmergencyRecovery` 方法体中的硬编码分支；若整方法仅服务污染路径，可整段删除及其调用。

`tryMigrateCursor` 策略收紧：

```java
// 仅保留策略1：精确匹配 sourceKey
// 删除策略2：findBestLegacyCursor() 全局选最高分
if (sourceKey == null) {
    return null;
}
source = loadCursor(sourceKey);
if (source == null) return null;
// ... 刷新 totalQuestions、改 chatId、save、delete 旧文件
```

`findBestLegacyCursor` 若无其它调用方可删除。

- [ ] **Step 4: 跑测通过**

```bash
mvn -Dtest=SequentialRotationServiceTest test
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/qian/qianaiagent/app/SequentialRotationService.java \
        src/test/java/com/qian/qianaiagent/app/SequentialRotationServiceTest.java
git commit -m "$(cat <<'EOF'
fix: 禁用硬编码游标恢复与全局 chat_* 偷进度迁移

EOF
)"
```

**运维备注（写入 commit/PR 描述即可）：** 已有被污染的 `user_*.json`（round=4 等）需用户手动删文件或提供「重置进度」入口；本 Task 不自动改写现网游标。

---

### Task 3: AI 流失败时回滚游标推进

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/app/SequentialRotationService.java`
- Modify: `src/main/java/com/qian/qianaiagent/app/QuizApp.java`
- Modify: `src/test/java/com/qian/qianaiagent/app/SequentialRotationServiceTest.java`

**设计选择（两阶段中的可回滚快照）：**  
保持「锁内先 `markQuestionAsked`」以防止并发双读同一题；在推进前深拷贝游标快照，流失败时 `restoreCursor(snapshot)`。

- [ ] **Step 1: 写回滚单测**

```java
@Test
void rollbackRestoresAskedIndexAfterMark() {
    String key = "chat_test_rollback_" + System.currentTimeMillis();
    service.initSession(key, cache);
    int before = service.getTotalAskedThisDirection(key);
    SequentialRotationService.SequentialCursor snap = service.snapshotCursor(key);

    service.markQuestionAsked(key);
    assertEquals(before + 1, service.getTotalAskedThisDirection(key));

    service.restoreCursor(snap);
    assertEquals(before, service.getTotalAskedThisDirection(key));
    assertEquals("Java基础与集合", service.currentTopic(key));
}
```

- [ ] **Step 2: 实现 snapshot / restore**

在 `SequentialRotationService`：

```java
/** 深拷贝当前游标（Jackson round-trip 或手动拷贝 activeDirections）。 */
public SequentialCursor snapshotCursor(String chatId) {
    SequentialCursor c = sessions.get(chatId);
    if (c == null) return null;
    try {
        return mapper.readValue(mapper.writeValueAsBytes(c), SequentialCursor.class);
    } catch (IOException e) {
        throw new IllegalStateException("snapshot failed", e);
    }
}

public void restoreCursor(SequentialCursor snapshot) {
    if (snapshot == null || snapshot.chatId == null) return;
    sessions.put(snapshot.chatId, snapshot);
    saveCursor(snapshot);
}
```

- [ ] **Step 3: QuizApp 在锁内推进前快照，错误路径恢复**

在 `synchronized` 块内、`markQuestionAsked` 之前：

```java
final SequentialRotationService.SequentialCursor cursorSnapshot =
        shouldAdvance ? sequentialRotationService.snapshotCursor(ck) : null;
```

将 `cursorSnapshot` 传到流链路（effectively final）。修改返回的 Flux：

```java
return quizChatClient.prompt()
        // ...
        .stream().content()
        .doOnNext(chunk -> fullTextBuilder.append(chunk))
        .doOnComplete(() -> { /* 现有评分逻辑 */ })
        .concatWith(Flux.just(questionBlock))
        .doOnError(e -> {
            log.error("❌ 流式对话异常，回滚游标: {}", e.getMessage(), e);
            if (cursorSnapshot != null) {
                synchronized (chatLocks.computeIfAbsent(ck, k -> new Object())) {
                    sequentialRotationService.restoreCursor(cursorSnapshot);
                    // 恢复 lastShownStems 为评测题，避免下一轮评错
                    if (effectiveEvalStem != null) {
                        lastShownStems.put(chatId, effectiveEvalStem);
                    }
                }
            }
        })
        .onErrorResume(e -> Flux.just("[ERROR] AI 服务暂时不可用：" + e.getMessage()));
```

注意：外层 `Flux.defer` 的 `.onErrorResume` 若在锁内同步失败路径触发，同样需要能访问 snapshot；优先保证 LLM 流错误走内层 `doOnError`。

- [ ] **Step 4: 跑测**

```bash
mvn -Dtest=SequentialRotationServiceTest,QuizCommandMatcherTest test
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/qian/qianaiagent/app/SequentialRotationService.java \
        src/main/java/com/qian/qianaiagent/app/QuizApp.java \
        src/test/java/com/qian/qianaiagent/app/SequentialRotationServiceTest.java
git commit -m "$(cat <<'EOF'
fix: AI 流失败时回滚游标，避免丢题与评题错位

EOF
)"
```

---

### Task 4: 换方向后首题只出示一次

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/app/QuizApp.java`（`shouldAdvance` 逻辑）
- Modify: `src/test/java/com/qian/qianaiagent/app/SequentialRotationServiceTest.java`（可选：集成式断言）

**根因：** `isSkipDir` 时 `shouldAdvance=false`，已 `skipCurrentDirection` 并展示新方向第 1 题，但未 `markQuestionAsked`。

- [ ] **Step 1: 改推进条件**

```java
// 重连不推进；换方向后仍要消费「刚出示的新方向首题」
boolean shouldAdvance = !isRetry;
```

删除对 `isSkipDir` 的排除。保留锁内 `skipCurrentDirection` 调用顺序：先 skip → 再读新方向 stem → 再 `markQuestionAsked`。

- [ ] **Step 2: 手工/单测验证语义**

单测可只测 service 层：

```java
@Test
void skipThenMarkConsumesFirstQuestionOfNewDirection() {
    String key = "chat_test_skip_" + System.currentTimeMillis();
    service.initSession(key, cache);
    assertEquals("Java基础与集合", service.currentTopic(key));

    service.skipCurrentDirection(key);
    assertEquals("JVM", service.currentTopic(key));
    int[] range = service.getCurrentQuestionRange(key);
    assertNotNull(range);
    assertEquals(0, range[0]); // 新方向第 0 题

    service.markQuestionAsked(key);
    int[] after = service.getCurrentQuestionRange(key);
    assertEquals(1, after[0]); // 下次应是第 1 题
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/qian/qianaiagent/app/QuizApp.java \
        src/test/java/com/qian/qianaiagent/app/SequentialRotationServiceTest.java
git commit -m "$(cat <<'EOF'
fix: 换方向后推进游标，避免新方向首题重复出示

EOF
)"
```

---

### Task 5: 复习模式 —— 评上一题 + 会话锁 + 重试

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/app/WrongQuestionReviewService.java`
- Create: `src/test/java/com/qian/qianaiagent/app/WrongQuestionReviewServiceTest.java`

**目标语义（对齐面试模式）：**
- Turn N：用户回答的是 Turn N-1 出示的题（`previousStem` / `previousEntry`）。
- 评分目标 = 上一题；本轮上下文仍告知 AI「下一题要出什么」（若仍由 AI 出题）或改为后端拼题（本 Task 保持 AI 出题 prompt，只修评分参数）。
- `doOnComplete`：先用 `prevEntry` 评分，再 `advanceCursor`，再更新 `previous*`。
- 同文重试不推进。

- [ ] **Step 1: 写语义单测（可测 package-private 辅助方法）**

若 `doReviewChat` 难单测，先抽出：

```java
record EvalTarget(String topic, String knowledgePoint, String questionText) {}

static EvalTarget resolveEvalTarget(QEntry previous, QEntry current, boolean isFirst) {
    if (isFirst || previous == null) return null; // 首条不评分
    return new EvalTarget(previous.topicName, previous.knowledgePoint, previous.questionText);
}
```

```java
@Test
void reviewScoresPreviousNotCurrent() {
    QEntry prev = new QEntry("JVM", "GC", "什么是GC？");
    QEntry curr = new QEntry("JVM", "CMS", "CMS有什么问题？");
    var target = WrongQuestionReviewService.resolveEvalTarget(prev, curr, false);
    assertEquals("什么是GC？", target.questionText());
    assertNotEquals(curr.questionText, target.questionText());
}
```

- [ ] **Step 2: 改 `doReviewChat`**

关键改动要点：

```java
final Object lock = reviewLocks.computeIfAbsent(chatId, k -> new Object());
synchronized (lock) {
    // isRetry = message.equals(lastRetryKeys.get(chatId))
    // 读取 currentQ、prevEntry（新增 previousEntryMap）
    // eval = resolveEvalTarget(prevEntry, currentQ, isFirstMessage)
}

// prompt 仍可带「上一题/本轮必考」

return reviewChatClient...stream().content()
    .doOnComplete(() -> {
        synchronized (lock) {
            if (!isFirstMessage && !isNextCmd && !isRetry && eval != null) {
                userAbilityService.scoreAnswerReviewAsync(
                        sourceChatId, eval.topic(), eval.questionText(), message,
                        eval.knowledgePoint()); // 见 Task 6 扩签名
            }
            if (!isRetry) {
                previousStemMap.put(chatId, stemSnapshot);
                previousEntryMap.put(chatId, currentQ);
                advanceCursor(session);
                saveSession(session);
            }
            lastRetryKeys.put(chatId, message);
        }
    })
    .concatWith(Flux.just("[DONE]")); // 与 Task 8 一致，可在此先加
```

- [ ] **Step 3: 跑测**

```bash
mvn -Dtest=WrongQuestionReviewServiceTest test
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/qian/qianaiagent/app/WrongQuestionReviewService.java \
        src/test/java/com/qian/qianaiagent/app/WrongQuestionReviewServiceTest.java
git commit -m "$(cat <<'EOF'
fix: 复习模式按上一题评分，并加重试与会话锁

EOF
)"
```

---

### Task 6: 错题本写入放宽 + 复习按题干/知识点删除

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/app/UserAbilityProfile.java`
- Modify: `src/main/java/com/qian/qianaiagent/app/UserAbilityService.java`
- Create: `src/test/java/com/qian/qianaiagent/app/UserAbilityWrongBookTest.java`

- [ ] **Step 1: 写失败单测**

```java
@Test
void lowScoreWithoutCurrentWpsStillRecordsWrongQuestion() {
    UserAbilityProfile.TopicScore ts = new UserAbilityProfile.TopicScore();
    // 模拟：score=2，弱点被路由走，currentWps 空 —— 业务层应仍用题干记一条
    // 本测直接验证新 API：
    ts.recordWrongQuestion("未归类弱点", "HashMap 为什么线程不安全？");
    assertTrue(ts.getWrongQuestions().containsValue("HashMap 为什么线程不安全？"));
}

@Test
void removeByQuestionTextDeletesEntry() {
    UserAbilityProfile.TopicScore ts = new UserAbilityProfile.TopicScore();
    ts.recordWrongQuestion("AQS", "什么是AQS？");
    assertTrue(ts.removeWrongQuestionByText("什么是AQS？"));
    assertTrue(ts.getWrongQuestions().isEmpty());
}
```

- [ ] **Step 2: Profile 增加按题干删除**

```java
/** 按原题文本删除（复习答对时优先用此方法）。 */
public boolean removeWrongQuestionByText(String questionText) {
    if (wrongQuestions == null || questionText == null || questionText.isBlank()) {
        return false;
    }
    String target = questionText.trim();
    String keyToRemove = null;
    for (Map.Entry<String, String> e : wrongQuestions.entrySet()) {
        if (target.equals(e.getValue()) || e.getValue() != null && e.getValue().startsWith(target)) {
            keyToRemove = e.getKey();
            break;
        }
    }
    if (keyToRemove == null) return false;
    wrongQuestions.remove(keyToRemove);
    return true;
}
```

（`startsWith` 兼容入库时截断到 200 字的 `wqText`。）

- [ ] **Step 3: 改 `scoreAnswerAsync` 错题写入条件**

原：

```java
if (sr.getScore() < 4 && !currentWps.isEmpty()) {
```

改为：

```java
if (sr.getScore() < 4) {
    UserAbilityProfile.TopicScore ts = profile.getTopicScores().get(topic);
    if (ts != null) {
        List<String> keys = !currentWps.isEmpty()
                ? currentWps
                : List.of("综合薄弱"); // 无弱点标签时仍保留题干
        for (String wp : keys) {
            ts.recordWrongQuestion(wp, wqText != null ? wqText : question);
        }
    }
}
```

`recordWrongQuestion` 已有 `containsValue(question)` 去重，不会因多 wp 重复插同一题。

- [ ] **Step 4: 改 `scoreAnswerReviewAsync`**

扩签名（保留旧重载转发）：

```java
public CompletableFuture<Void> scoreAnswerReviewAsync(
        String chatId, String topic, String question, String answer, String knowledgePoint) {
    return CompletableFuture.runAsync(() -> {
        // ... 调 LLM 评分 ...
        if (sr != null && sr.getScore() >= 4) {
            UserAbilityProfile profile = getOrCreateProfile(chatId);
            UserAbilityProfile.TopicScore ts = profile.getTopicScores().get(topic);
            if (ts != null) {
                boolean removed = false;
                if (knowledgePoint != null && !knowledgePoint.isBlank()) {
                    ts.removeWrongQuestion(knowledgePoint);
                    removed = true;
                }
                removed = ts.removeWrongQuestionByText(question) || removed;
                if (removed) {
                    saveProfile(chatId);
                    log.info("✅ 复习答对，移除错题: chatId={}, topic={}, score={}/5, q={}",
                            chatId, topic, sr.getScore(),
                            question != null && question.length() > 40 ? question.substring(0, 40) : question);
                }
            }
        }
    }, scoringExecutor);
}
```

**禁止**再遍历 `sr.getWeakPoints()` 作为删除 key（答对时通常为空）。

- [ ] **Step 5: 跑测 + Commit**

```bash
mvn -Dtest=UserAbilityWrongBookTest,WrongQuestionReviewServiceTest test
```

```bash
git commit -m "$(cat <<'EOF'
fix: 低分必记错题本，复习答对按题干/知识点删除

EOF
)"
```

---

### Task 7: 会话态 Map 与游标 key 对齐

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/app/QuizApp.java`

**问题：** 游标用 `ck=user_X`，但 `lastShownStems` / `lastRetryKeys` / `previousStemMap` / `pendingEval*` / `lastQuestions` / `messageCounts` 用 `chatId`。同用户多对话框会串评题状态。

- [ ] **Step 1: 统一 stateKey**

在 `doUnifiedChat` 开头：

```java
final String ck = cursorKey(chatId, userId);
final String stateKey = ck; // 登录后与游标同源；未登录仍等于 chatId
```

将上述 Map 的所有 `chatId` 读写改为 `stateKey`。  
**例外：** `ChatMemory.CONVERSATION_ID` 与 `topicMemoryTrimmer` 仍用原始 `chatId`（对话历史按对话框隔离是合理的）。

- [ ] **Step 2: 自检清单（代码搜索）**

在 `QuizApp.java` 内确认面试路径不再出现：

```text
lastShownStems.get(chatId)
lastRetryKeys.put(chatId
previousStemMap.put(chatId
pendingEvalStemMap
lastQuestions.put
messageCounts.
```

应全部变为 `stateKey`（trimmer/memory 除外）。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/qian/qianaiagent/app/QuizApp.java
git commit -m "$(cat <<'EOF'
fix: 面试会话态与游标 key 对齐，避免多对话框评题串台

EOF
)"
```

---

### Task 8: 前端方向解析 + 后端补发 `[DONE]`

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/app/QuizApp.java`（流尾在 `questionBlock` 后再发 `[DONE]`，或由 Controller 统一 `concatWith`）
- Modify: `src/main/java/com/qian/qianaiagent/app/WrongQuestionReviewService.java`
- Modify: `src/main/java/com/qian/qianaiagent/controller/AiController.java`（可选统一层）
- Modify: `qian-ai-agent-frontend/src/views/ChatView.vue`

- [ ] **Step 1: 后端成功路径追加 `[DONE]`**

推荐在 Controller 统一，避免漏：

```java
// AiController 面试与复习：
return quizApp.doUnifiedChat(message, finalChatId, userId)
        .concatWith(Flux.just("[DONE]"))
        .subscribeOn(...)
        .doOnComplete(() -> userAbilityService.saveProfile(finalChatId, userId));
```

复习同理。若 `WrongQuestionReviewService` 错误路径已有 `[DONE]`，注意不要重复两次；成功路径只在一处拼接。

- [ ] **Step 2: 前端只认本轮考题方向**

```javascript
const extractDirection = (content) => {
  const match = content.match(/【本轮考题】([^\n]+)/)
  if (match) {
    currentDirection.value = match[1].trim()
  }
}
```

复习模式若标记不同，增加：

```javascript
const m2 = content.match(/【本轮必考题干】|（([^）]+)）/)
// 或与复习 prompt 实际标记对齐；优先解析明确的方向行
```

面试 `onmessage` 中保留 `extractDirection`，但因正则已锚定 `【本轮考题】`，流式中途的 `【点评】` 不再污染。

- [ ] **Step 3: 手动验证清单**

1. 正常答一题 → 流结束收到 `[DONE]` → 前端 `flushStreamRender` 执行、画像刷新。
2. UI 方向标签显示 `JVM` 等，而不是 `点评`。
3. 答案含「过程」→ 正常评分，不跳题。

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/qian/qianaiagent/controller/AiController.java \
        src/main/java/com/qian/qianaiagent/app/QuizApp.java \
        src/main/java/com/qian/qianaiagent/app/WrongQuestionReviewService.java \
        qian-ai-agent-frontend/src/views/ChatView.vue
git commit -m "$(cat <<'EOF'
fix: SSE 补发 [DONE]，前端仅从【本轮考题】解析方向

EOF
)"
```

---

### Task 9: 回归验证（全量相关测试 + 手工冒烟）

- [ ] **Step 1: 跑全部本计划测试**

```bash
mvn -Dtest=QuizCommandMatcherTest,SequentialRotationServiceTest,WrongQuestionReviewServiceTest,UserAbilityWrongBookTest test
```

Expected: BUILD SUCCESS。

- [ ] **Step 2: 手工冒烟（本地启动后端 + 前端）**

| # | 步骤 | 期望 |
|---|------|------|
| 1 | 新用户/删游标后开聊 | 从 Java 基础第 1 题开始，round=0 |
| 2 | 回答含「创建过程」 | 正常点评+出下一题，不跳过 |
| 3 | 整句发送「过」 | 跳过当前题 |
| 4 | 「换个方向」 | 进入 JVM，下一轮不再重复 JVM 第 1 题 |
| 5 | 模拟 AI 超时/断开后「继续」 | 不丢题；评的仍是未成功那题或可重答 |
| 6 | 低分且弱点路由走 | 错题本仍有该题干 |
| 7 | 复习答对一题 | 画像错题本条目减少 |
| 8 | 流结束 | 网络面板可见 `[DONE]`，方向 UI 正确 |

- [ ] **Step 3: 最终 Commit（若有测试/文档微调）或直接开 PR**

---

## Self-Review

**Spec coverage**
- P0 误跳过 → Task 1  
- P0 硬编码污染 → Task 2  
- P0 失败丢题 → Task 3  
- P0 换方向双题 → Task 4  
- P0 复习评错/删不掉 → Task 5–6  
- P1 错题写入过严 → Task 6  
- P1 多对话框串态 → Task 7  
- P1 方向 UI / DONE → Task 8  
- 回归 → Task 9  

**Placeholder scan:** 无 TBD；关键步骤含具体代码与命令。

**Type consistency:** `QuizCommandMatcher` / `snapshotCursor` / `removeWrongQuestionByText` / `resolveEvalTarget` / `scoreAnswerReviewAsync(..., knowledgePoint)` 在后续 Task 引用一致。

---

## 执行交接

Plan complete and saved to `docs/superpowers/plans/2026-08-06-quiz-mechanism-bugfix.md`.

**两种执行方式：**

1. **Subagent-Driven（推荐）** — 每个 Task 开新子代理，Task 间审查  
2. **Inline Execution** — 本会话按 executing-plans 连续做，设检查点  

你更想用哪一种？
