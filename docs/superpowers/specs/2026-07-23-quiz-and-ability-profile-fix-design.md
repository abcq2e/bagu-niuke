# 知识点考察均匀性 & 能力画像归类修复设计

**日期**：2026-07-23  
**状态**：待实现  
**范围**：方案 B — 中量改造  
**目标读者**：接手实现的开发者（包括其他 AI 模型），可直接按本文档写代码，无需额外沟通

---

## 零、阅读本文档前必须先读的文件

实现前请先完整阅读以下源文件，理解现有逻辑后再动手：

| 文件路径 | 原因 |
|---------|------|
| `src/main/java/com/qian/qianaiagent/app/QuizApp.java` | 核心编排器，改动最多 |
| `src/main/java/com/qian/qianaiagent/app/TopicMemoryTrimmer.java` | 需要新增方法 |
| `src/main/java/com/qian/qianaiagent/app/TopicDimensions.java` | 需要修复两处逻辑 |
| `src/main/java/com/qian/qianaiagent/app/UserAbilityService.java` | 需要修改路由打分 |
| `src/main/java/com/qian/qianaiagent/chatmemory/FileBasedChatMemory.java` | `trimToRecentN` 要调用它的 `replaceMessages` |
| `src/main/java/com/qian/qianaiagent/app/AskedPointTracker.java` | 提供 `getAskedPointIds` |
| `src/main/java/com/qian/qianaiagent/app/KnowledgePointCatalog.java` | 提供 `findById`、`getByTopic` |

---

## 一、问题诊断

### 1.1 知识点考察不均匀 / 题目重复

**P1：ChatMemory 历史无限累积，AI 出题受历史影响绕圈**

`quizChatClient` 带 `MessageChatMemoryAdvisor`，同一方向内历史消息无限增长。`TopicMemoryTrimmer.trimAfterAdvance()` 只在换方向时触发裁剪，方向内没有任何长度限制。LLM 上下文里充满旧问答时，即使 System Prompt 里硬选题干指定了新题干，AI 仍倾向于在熟悉的知识面追问，导致题目集中在少数知识点绕圈。

**P2：已考知识点 ID 的去重没有被显式传达给 AI**

`QuestionSelector` 以 SHA-256 ID 去重，防止同一题干被重复选出。但这个去重对 AI 不可见，AI 看不到"已出过哪些方向的题"，只能靠 System Prompt 里一句泛化禁令，效果很弱。

**P3：`askedQuestionFingerprints` 去重仅用于事后记录，未注入 Prompt**

trigram Jaccard 相似度去重（`filterAskedQuestions`）只在降级模式的题池过滤里用到，正常硬选题路径里 AI 完全看不到这些信息，无法主动规避重复角度。

---

### 1.2 能力画像乱归类

**Q1：`CROSS_TOPIC_CONCEPTS` 有硬冲突条目**

`buildCrossTopicConcepts()` 中 `"零拷贝"` 先写 `Java基础与集合`，后写 `操作系统与Linux`。中间用的是 `HashMap`，后写的覆盖前写的（结果是 `操作系统与Linux`），但这是依赖 HashMap 无序性的实现细节，不可靠。此外 `"epoll"` 只映射了 `计算机网络`，但 `操作系统与Linux` 的维度定义里也包含 epoll 关键词，导致路由时两个方向争抢。

**Q2：`containsForeignTopicKeyword` 反向检测过激，误删本方向弱点**

当前逻辑：只要文本含有任何其他方向的关键词就返回 `true`（判为跨方向）。实际上"事务"在 MySQL 和消息队列都有、"线程池"在 Java并发 和 系统设计都有，大量合理的本方向弱点被误判为跨方向后遭误移或误删，画像数据质量持续劣化。

**Q3：`routeWeakPoints` 平局时不优先当前方向**

`scoreAgainstAllTopics` 返回的 Map 遍历时若两个方向得分相同，取的是 HashMap 遍历顺序第一个，没有"当前方向优先"的保底逻辑，导致本方向弱点被路由到其他方向。

---

## 二、设计目标

| 目标 | 度量标准 |
|------|---------|
| 历史不干扰出题 | 同一方向内 ChatMemory 最多保留最近 8 条消息，超出部分截断 |
| AI 主动规避重复 | 每次出题时将"本方向已出过的知识点"按维度分组注入 System Prompt |
| 弱点正确归类 | 消除 `CROSS_TOPIC_CONCEPTS` 冲突；`containsForeignTopicKeyword` 改为票数制，减少误杀 |
| 路由优先当前方向 | 平局时当前方向得分 +2，确保本方向弱点不被路由走 |

---

## 三、逐文件改动说明（可直接按此写代码）

### 改动一：`TopicMemoryTrimmer.java` — 新增 `trimToRecentN` 方法

**位置**：`src/main/java/com/qian/qianaiagent/app/TopicMemoryTrimmer.java`

在类的末尾、`trimAfterAdvance` 方法之后新增以下方法，**不要修改或删除 `trimAfterAdvance`**：

```java
/**
 * 方向内历史截断：将当前会话的 ChatMemory 截断为最近 n 条消息。
 * 在 QuizApp 每次对话前调用，防止同一方向内历史无限累积干扰出题。
 * 注意：与 trimAfterAdvance 并存，两者用途不同：
 *   - trimAfterAdvance：换方向时触发，做摘要+保留6条（现有逻辑，不改）
 *   - trimToRecentN：每轮对话前触发，方向内保留最近n条（本次新增）
 */
public void trimToRecentN(String chatId, int n) {
    if (chatId == null || n <= 0) return;
    try {
        List<Message> all = fileBasedChatMemory.get(chatId);
        if (all == null || all.size() <= n) return; // 消息数未超限，无需截断
        List<Message> recent = new ArrayList<>(all.subList(all.size() - n, all.size()));
        fileBasedChatMemory.replaceMessages(chatId, recent);
        log.info("✂️ 方向内历史截断: chatId={}, 原{}条→保留{}条", chatId, all.size(), recent.size());
    } catch (Exception e) {
        log.warn("方向内历史截断失败（不影响主流程）: {}", e.getMessage());
    }
}
```

---

### 改动二：`QuizApp.java` — 两处改动

**位置**：`src/main/java/com/qian/qianaiagent/app/QuizApp.java`

#### 改动 2-A：每轮对话前截断历史

在 `doUnifiedChat(message, chatId, userId)` 方法内，找到 `ensureAskedHydrated(chatId, userId);` 这一行，在它**之后**、`topicRotationService.currentTopic(chatId)` **之前**插入：

```java
// 方向内历史截断：每轮无条件截断，防止历史积累干扰出题
// 8条 = 4轮问答，足够AI点评上一轮回答，超出则干扰大于帮助
topicMemoryTrimmer.trimToRecentN(chatId, 8);
```

> **注意**：这行必须在 `ensureAskedHydrated` 之后（已考题目需要先恢复），在 `currentTopic` 之前（截断不影响方向状态）。换方向时 `trimAfterAdvance` 仍然会被调用，两者不冲突——换方向时先执行 `trimAfterAdvance`（做摘要），下一轮再执行 `trimToRecentN`（保持8条上限）。

#### 改动 2-B：在 context 里注入已出知识点摘要

在 `doUnifiedChat` 里找到构建 `context` 的段落，找到以下注入薄弱点提示的代码：

```java
// 🔴 [P4] 薄弱点提示（个性化）
String weakHint = userAbilityService.buildWeakPointHint(chatId, topic);
if (!weakHint.isEmpty()) {
    context.append(weakHint);
}
```

在这段代码**之前**插入已出知识点摘要的注入逻辑：

```java
// 注入本方向已出知识点摘要，让 AI 主动规避重复角度
Set<String> askedIds = askedPointTracker.getAskedPointIds(chatId);
if (!askedIds.isEmpty()) {
    // 只取当前方向的已考知识点，按维度分组，最多展示10条
    List<KnowledgePoint> askedInTopic = askedIds.stream()
            .map(id -> knowledgePointCatalog.findById(topic, id))
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .limit(10)
            .collect(java.util.stream.Collectors.toList());
    if (!askedInTopic.isEmpty()) {
        context.append("📌 本方向已考察知识点（禁止以相同角度出题，必须覆盖新角度）：\n");
        // 按维度分组展示
        java.util.Map<String, List<KnowledgePoint>> byDim = askedInTopic.stream()
                .collect(java.util.stream.Collectors.groupingBy(KnowledgePoint::dimension));
        byDim.forEach((dim, points) -> {
            String dimLabel = com.qian.qianaiagent.app.KnowledgePoint.UNCLASSIFIED.equals(dim)
                    ? "综合" : TopicDimensions.dimensionSubject(dim);
            context.append("- 【").append(dimLabel).append("】：");
            String stems = points.stream()
                    .map(p -> p.stem().length() > 40 ? p.stem().substring(0, 40) + "…" : p.stem())
                    .collect(java.util.stream.Collectors.joining("、"));
            context.append(stems).append("\n");
        });
    }
}
```

> **说明**：`knowledgePointCatalog` 已经是 `QuizApp` 的 `@Resource` 字段，直接可用。`askedPointTracker` 同理。每条题干截断到 40 字，最多 10 条，整个块约 400 字，不会撑爆 prompt。

---

### 改动三：`TopicDimensions.java` — 两处改动

**位置**：`src/main/java/com/qian/qianaiagent/app/TopicDimensions.java`

#### 改动 3-A：修复 `buildCrossTopicConcepts()` 的冲突条目

在 `buildCrossTopicConcepts()` 方法里，找到以下两行并做对应修改：

**找到**（在 Java基础与集合 部分）：
```java
m.put("零拷贝", "Java基础与集合");
```
**删除这一行**（零拷贝属于操作系统，不属于Java基础，下方操作系统部分已有正确的写入）。

**找到**（在 计算机网络 部分）：
```java
m.put("epoll", "计算机网络");
```
**删除这一行**（epoll 同时属于计算机网络和操作系统与Linux，单独映射会导致两个方向争抢，改由 3-B 的票数制正向匹配决定归属，不在映射表里硬编码）。

修改完成后，在 `buildCrossTopicConcepts()` 返回前（`return Map.copyOf(result);` 之前）加断言，用于开发阶段验证无重复 key：

```java
// 验证无重复 key（assert 在生产环境默认关闭，不影响性能）
assert result.size() == m.size()
    : "CROSS_TOPIC_CONCEPTS 存在重复 key，实际 key 数=" + m.size() + "，去重后=" + result.size();
```

#### 改动 3-B：`containsForeignTopicKeyword` 改为票数制

**完整替换** `containsForeignTopicKeyword` 方法体（保留方法签名不变），用以下实现替代：

```java
public static boolean containsForeignTopicKeyword(String topic, String text) {
    if (topic == null || topic.isBlank() || text == null || text.isBlank()) return false;
    String lowerText = text.toLowerCase(java.util.Locale.ROOT).trim();

    // === 第一优先级：CROSS_TOPIC_CONCEPTS 精确/子串匹配 ===
    // 该表是硬编码的强信号，命中后直接判定
    String exactMapped = CROSS_TOPIC_CONCEPTS.get(lowerText);
    if (exactMapped != null) {
        return !exactMapped.equals(topic);
    }
    if (lowerText.length() > 4) {
        for (Map.Entry<String, String> entry : CROSS_TOPIC_CONCEPTS.entrySet()) {
            String concept = entry.getKey();
            if (concept.length() >= 2 && lowerText.contains(concept)) {
                return !entry.getValue().equals(topic);
            }
        }
    }

    // === 第二优先级：票数制 — 弱信号词用票数差决定 ===
    // 规则：外来方向得票 > 当前方向得票 + 1 才判为跨方向
    // 得票相等或差距 ≤ 1 → 返回 false（保留，宁可漏过不误杀）
    int currentScore = countDimensionKeywordHits(topic, lowerText);
    int maxForeignScore = 0;
    for (Map.Entry<String, List<String>> entry : DIMENSIONS.entrySet()) {
        if (entry.getKey().equals(topic)) continue;
        int score = countDimensionKeywordHits(entry.getKey(), lowerText);
        if (score > maxForeignScore) maxForeignScore = score;
    }

    // 仅当外来信号明显强于当前方向信号时才判为跨方向
    return maxForeignScore > currentScore + 1;
}

/**
 * 统计文本命中指定方向的维度关键词数量（主体名命中得3分，关键词命中得1分）。
 * 供 containsForeignTopicKeyword 票数制使用。
 */
private static int countDimensionKeywordHits(String topic, String lowerText) {
    List<String> dims = getDimensions(topic);
    if (dims == null || dims.isEmpty()) return 0;
    int score = 0;
    for (String dim : dims) {
        String subject = dimensionSubject(dim);
        if (!subject.isEmpty()
                && lowerText.contains(subject.toLowerCase(java.util.Locale.ROOT))) {
            score += 3; // 主体名命中权重更高
        }
        for (String kw : getSubDimensionKeywords(dim)) {
            if (kw.length() >= 2
                    && lowerText.contains(kw.toLowerCase(java.util.Locale.ROOT))) {
                score += 1;
            }
        }
    }
    return score;
}
```

> **注意**：`countDimensionKeywordHits` 是新增的 `private static` 方法，加在 `containsForeignTopicKeyword` 下方即可。原方法中调用的 `log.debug(...)` 可以不保留（票数制逻辑更简洁）。

---

### 改动四：`UserAbilityService.java` — `routeWeakPoints` 加当前方向权重

**位置**：`src/main/java/com/qian/qianaiagent/app/UserAbilityService.java`

找到 `routeWeakPoints` 方法，在"步骤 2：正向匹配打分"的代码里，找到以下计算 `bestTopic` 的循环：

```java
// 步骤 2: 正向匹配打分 — 给每个方向计算匹配度，选最高分的方向
java.util.Map<String, Integer> scores = scoreAgainstAllTopics(trimmed);
String bestTopic = null;
int bestScore = 0;
for (java.util.Map.Entry<String, Integer> entry : scores.entrySet()) {
    if (entry.getValue() > bestScore) {
        bestScore = entry.getValue();
        bestTopic = entry.getKey();
    }
}
```

在 `scoreAgainstAllTopics(trimmed)` 调用**之后**、遍历 `scores` 循环**之前**，插入一行当前方向加权：

```java
java.util.Map<String, Integer> scores = scoreAgainstAllTopics(trimmed);
// 当前方向加权 +2：平局时优先归属当前方向，避免通用词（如"事务"）被路由到其他方向
// 只有其他方向得分超出当前方向 2 分以上，才会路由走
scores.merge(currentTopic, 2, Integer::sum);
String bestTopic = null;
int bestScore = 0;
for (java.util.Map.Entry<String, Integer> entry : scores.entrySet()) {
    if (entry.getValue() > bestScore) {
        bestScore = entry.getValue();
        bestTopic = entry.getKey();
    }
}
```

---

## 四、改动边界（严格遵守）

**只改这 4 个文件**，其余文件一律不动：

| 文件 | 改动类型 |
|------|---------|
| `TopicMemoryTrimmer.java` | 新增方法 `trimToRecentN` |
| `QuizApp.java` | 插入 2 段代码（截断调用 + 摘要注入） |
| `TopicDimensions.java` | 删除 2 个冲突条目；替换 `containsForeignTopicKeyword` 方法体；新增 `countDimensionKeywordHits` 私有方法 |
| `UserAbilityService.java` | 插入 1 行 `scores.merge(currentTopic, 2, Integer::sum)` |

**明确不改的文件**（即使看起来有关联）：

- `TopicRotationService.java` — 方向轮转逻辑不动
- `QuestionSelector.java` — 选题算法不动
- `KnowledgePointCatalog.java` — 知识点目录不动
- `FileBasedChatMemory.java` — 直接调用其 `replaceMessages` 即可，无需改它
- `AskedPointTracker.java` — 直接调用其 `getAskedPointIds` 即可，无需改它
- 所有前端文件 — 不动

---

## 五、风险与边界情况

| 风险 | 说明 | 应对 |
|------|------|------|
| 截断后 AI 找不到上一轮回答内容 | 截断保留最近 8 条（4轮问答），上一轮的 AI问+用户答必然在最近 2 条内，不受影响 | 不需要额外处理 |
| `trimToRecentN` 与 `trimAfterAdvance` 冲突 | 两者触发时机不同，不会同一轮同时执行。换方向时先执行 `trimAfterAdvance`（保留6条），下一轮再执行 `trimToRecentN`（上限8条），6<8，不会再次截断 | 不需要额外处理 |
| 已出知识点摘要过长撑爆 prompt | 每条 40 字 × 最多 10 条 = 400 字，QuizApp 的 context 通常在 2000 字以内，占比极小 | 已通过 `limit(10)` 硬限制 |
| 票数制太保守，真正的跨方向弱点漏过 | `CROSS_TOPIC_CONCEPTS` 作为最高优先级兜底，明确的技术词（AQS、MVCC、Redlock 等）都在映射表里，不会漏过 | 必要时扩充 `CROSS_TOPIC_CONCEPTS` 而非修改票数阈值 |
| `routeWeakPoints` +2 把跨方向弱点错归当前方向 | +2 是固定偏置，其他方向得分只要 ≥ 3 分就能超过当前方向（0+2=2），跨方向强信号不受影响 | 不需要额外处理 |
| `knowledgePointCatalog.findById(topic, id)` 找不到（id 属于其他方向） | `filter(Optional::isPresent)` 已过滤掉 empty，只展示当前方向内能找到的 | 已处理 |
