# 面试官全面考察改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 QuizApp 面试官的考察方向全面覆盖 16 个技术方向，由代码层确定性保证轮转，不再偏科

**Architecture:** 
- 新增 `TopicRotationService` 维护每会话方向轮转状态，代码层控制方向覆盖
- 知识库按 `topic` 元数据精确过滤检索，替代关键词向量检索
- AI 通过 `[NEXT_TOPIC]` 标记触发方向推进，4 条消息硬上限兜底

**Tech Stack:** Spring AI 1.0.0, PGVector, Spring Scheduling

---

### Task 1: 完成知识库拆分 — 补齐 4 个方向文件

**Files:**
- Create: `src/main/resources/document/八股-系统设计与场景.md`
- Create: `src/main/resources/document/八股-Docker与运维.md`
- Create: `src/main/resources/document/八股-ES与搜索.md`
- Create: `src/main/resources/document/八股-Agent与AI应用.md`
- Read + verify: `src/main/resources/document/八股-牛客八股.md`（3173行）
- Delete: `src/main/resources/document/八股-牛客八股.md`

- [ ] **Step 1: 创建 `八股-系统设计与场景.md`**

从 `八股-牛客八股.md` 中提取系统设计/场景题/项目深挖/HR/反问类题目（秒杀、短链、高并发架构、幂等、限流、项目经验等）：

```markdown
# 系统设计与场景 面试真题（牛客面经）

秒杀系统怎么设计？怎么应对瞬时高并发流量？
短链系统怎么设计？短链生成算法有哪些方案？
高并发系统架构怎么设计？从哪些维度考虑？
接口幂等性怎么保证？幂等方案有哪些？
限流算法有哪些？令牌桶和漏桶的区别？
设计一个分布式 ID 生成器，有哪些方案？
设计一个延迟队列，怎么实现？
...

（完整提取源文档中所有系统设计/场景/项目/HR/反问类题目）
```

- [ ] **Step 2: 创建 `八股-Docker与运维.md`**

从源文档提取 Docker/k8s/Linux 部署/CI-CD/Nginx/监控类题目。

- [ ] **Step 3: 创建 `八股-ES与搜索.md`**

从源文档提取 Elasticsearch/倒排索引/BM25/分词/ClickHouse 类题目。

- [ ] **Step 4: 创建 `八股-Agent与AI应用.md`**

从源文档提取 Agent/ReAct/RAG/embedding/大模型应用类题目。

- [ ] **Step 5: 完整性校验（扫描源文档，逐段确认所有题目去向）**

```bash
# 统计 16 个方向文件总行数
wc -l src/main/resources/document/八股-*.md | grep -v 牛客 | tail -1
# 确认源文档中每道题都能在各方向文件中找到（通过 grep 逐方向验证）
```

- [ ] **Step 6: 删除源文件**

```bash
git rm src/main/resources/document/八股-牛客八股.md
```

---

### Task 2: QuizDocumentLoader 增加 topic 元数据

**Files:**
- Modify: `src/main/java/com/yupi/yuaiagent/rag/QuizDocumentLoader.java:67-74, 132-135`

- [ ] **Step 1: 新增 `extractTopic()` 方法**

在 `extractCategory` 方法后增加：

```java
/**
 * 从文件名提取二级主题：第一个 "-" 之后、扩展名之前的部分。
 * "八股-Java并发.md" → "Java并发"；无 "-" → "default"
 */
private String extractTopic(String filename) {
    if (filename == null || filename.isEmpty()) {
        return "default";
    }
    int dashIndex = filename.indexOf('-');
    int dotIndex = filename.lastIndexOf('.');
    if (dashIndex <= 0 || dotIndex <= dashIndex) {
        return "default";
    }
    return filename.substring(dashIndex + 1, dotIndex);
}
```

- [ ] **Step 2: loadMarkdowns() 中添加 topic 元数据**

在 `config` builder 中（第72行 `.withAdditionalMetadata("category", category)` 之后）加一行：

```java
.withAdditionalMetadata("topic", extractTopic(filename))
```

- [ ] **Step 3: loadPdfs() 中添加 topic 元数据**

在第134行（`.put("category", category)`）之后加：

```java
doc.getMetadata().put("topic", extractTopic(filename));
```

---

### Task 3: 孤儿向量自动清理

**Files:**
- Modify: `src/main/java/com/yupi/yuaiagent/rag/QuizVectorStoreConfig.java:92-141`

- [ ] **Step 1: 在增量 ETL 之前插入孤儿向量清理**

在 `allDocuments` 加载之后（第104行后），分组循环之前（第107行前）插入：

```java
// 孤儿清理：删除源目录中已不存在的文件对应的向量
List<String> sourceFilenames = allDocuments.stream()
        .map(doc -> (String) doc.getMetadata().get("filename"))
        .distinct().toList();

for (String existing : existingFilenames) {
    if (!sourceFilenames.contains(existing)) {
        int deleted = pgVectorJdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'filename' = ?", existing);
        log.info("🗑️ 源文件已删除，清理孤儿向量: {} ({} 条)", existing, deleted);
    }
}
```

---

### Task 4: 新建 TopicRotationService

**Files:**
- Create: `src/main/java/com/yupi/yuaiagent/app/TopicRotationService.java`
- Modify: `src/main/java/com/yupi/yuaiagent/YuAiAgentApplication.java`（加 `@EnableScheduling`）

- [ ] **Step 1: 创建 TopicRotationService**

```java
package com.qian.qianaiagent.app;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方向轮转服务 — 维护每个会话的方向覆盖状态
 *
 * <p>保证一轮内 16 个方向不重不漏，轮完重新洗牌进入下一轮。
 * 方向推进由两个信号驱动：
 * <ol>
 *   <li>AI 主动信号：System Prompt 要求 AI 在方向考察完毕时输出 [NEXT_TOPIC]</li>
 *   <li>硬上限兜底：同一方向连续 4 条用户消息后强制推进</li>
 * </ol>
 */
@Component
@Slf4j
public class TopicRotationService {

    /** 必须与 document 目录下 八股-*.md 的方向名逐字一致 */
    public static final List<String> TOPICS = List.of(
            "Java基础与集合", "Java并发", "JVM", "Spring框架",
            "MySQL", "Redis", "消息队列", "计算机网络",
            "操作系统与Linux", "分布式与微服务", "算法与数据结构", "设计模式",
            "系统设计与场景", "Docker与运维", "ES与搜索", "Agent与AI应用");

    /** 同一方向最多停留的用户消息数（追问预算），超过则强制换方向 */
    private static final int MAX_EXCHANGES_PER_TOPIC = 4;

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    private static class SessionState {
        final Deque<String> remaining;
        final List<String> covered;
        String currentTopic;
        int exchangesOnCurrent;
        long lastAccess;

        SessionState() {
            List<String> shuffled = new ArrayList<>(TOPICS);
            Collections.shuffle(shuffled);
            remaining = new ArrayDeque<>(shuffled);
            covered = new ArrayList<>();
            currentTopic = remaining.pollFirst();
            exchangesOnCurrent = 0;
            lastAccess = System.currentTimeMillis();
        }
    }

    public String currentTopic(String chatId) {
        SessionState state = sessions.computeIfAbsent(chatId, k -> new SessionState());
        synchronized (state) {
            state.lastAccess = System.currentTimeMillis();
            // 硬上限兜底：超限则自动推进
            if (state.exchangesOnCurrent >= MAX_EXCHANGES_PER_TOPIC) {
                advanceInternal(state);
            }
            state.exchangesOnCurrent++;
            return state.currentTopic;
        }
    }

    public void advance(String chatId) {
        SessionState state = sessions.get(chatId);
        if (state == null) return;
        synchronized (state) {
            advanceInternal(state);
        }
    }

    private void advanceInternal(SessionState state) {
        if (state.currentTopic != null) {
            state.covered.add(state.currentTopic);
        }
        if (state.remaining.isEmpty()) {
            // 一轮完成，重新洗牌
            List<String> shuffled = new ArrayList<>(TOPICS);
            Collections.shuffle(shuffled);
            state.remaining.addAll(shuffled);
            state.covered.clear();
            log.info("🔄 所有方向已考察完毕，重新洗牌开始新一轮");
        }
        state.currentTopic = state.remaining.pollFirst();
        state.exchangesOnCurrent = 0;
    }

    public List<String> coveredTopics(String chatId) {
        SessionState state = sessions.get(chatId);
        if (state == null) return List.of();
        synchronized (state) {
            return new ArrayList<>(state.covered);
        }
    }

    /** 定时清理过期会话（每 30 分钟） */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void evictExpiredSessions() {
        long now = System.currentTimeMillis();
        long timeout = 2 * 60 * 60 * 1000; // 2 小时
        sessions.entrySet().removeIf(entry -> {
            SessionState state = entry.getValue();
            synchronized (state) {
                return (now - state.lastAccess) > timeout;
            }
        });
        log.info("🧹 过期会话清理完成，剩余活跃会话数: {}", sessions.size());
    }
}
```

- [ ] **Step 2: 确认启动类有 @EnableScheduling**

检查 `YuAiAgentApplication.java`，若没有则添加：

```java
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling  // 新增
@EnableAsync
public class YuAiAgentApplication {
```

（当前已有 `@EnableAsync`，在它旁边加 `@EnableScheduling`）

---

### Task 5: QuizApp 检索链路改造

**Files:**
- Modify: `src/main/java/com/yupi/yuaiagent/app/QuizApp.java`

- [ ] **Step 1: 注入 TopicRotationService + 删除旧代码**

```java
@Resource
private TopicRotationService topicRotationService;
```

删除：
- `KB_ROTATION_QUERIES`（第81-94行）
- `rotationIndex`（第97-98行）
- `cachedQuestionBank`（第103-104行）
- `initQuestionBank()`（第106-112行）
- `loadQuestionBank()`（第338-340行）
- `loadQuestionBankInternal()`（第345-417行）

删除 `doUnifiedChat` 中：
- 第139行：`String questionBank = loadQuestionBank();`
- 第141-143行：轮转索引逻辑
- 第172-182行：`rotationDocsFuture`
- 第207-228行：`2️⃣ 题库参考` + `3️⃣ 用户相关 RAG` 块

- [ ] **Step 2: 新增方向检索 + 上下文构建**

替换第141-143行 + 第172-182行的轮转逻辑：

```java
// 🔴 方向轮转 + 精确过滤检索
String topic = topicRotationService.currentTopic(chatId);
List<String> covered = topicRotationService.coveredTopics(chatId);

CompletableFuture<List<Document>> topicDocsFuture = CompletableFuture.supplyAsync(() -> {
    try {
        List<Document> docs = quizVectorStore.similaritySearch(SearchRequest.builder()
                .query(topic + " 面试题")
                .topK(10)
                .similarityThreshold(0.0)
                .filterExpression(new FilterExpressionBuilder()
                        .eq("topic", topic).build())
                .build());
        Collections.shuffle(docs);
        return docs.stream().limit(4).toList();
    } catch (Exception e) {
        log.warn("方向检索失败 [{}]: {}", topic, e.getMessage());
        return List.<Document>of();
    }
});
```

替换第188-228行的上下文构建：

```java
// ===== 构建上下文 =====
StringBuilder context = new StringBuilder();

context.append("🔴 本轮必考方向：【").append(topic).append("】\n");
if (!covered.isEmpty()) {
    context.append("本轮已考察过：").append(String.join("、", covered))
           .append("（除追问外禁止再出这些方向的新题）\n");
}

List<Document> topicDocs = topicDocsFuture.join();
if (!topicDocs.isEmpty()) {
    context.append("\n以下是该方向的牛客真实面经题目，从中选题：\n\n");
    for (Document doc : topicDocs) {
        String text = doc.getText();
        if (text.length() > 500) {
            text = text.substring(0, 500) + "…";
        }
        context.append("📌 ").append(text).append("\n\n");
    }
} else {
    context.append("\n（方向文档暂未加载，请按该方向自行出题）\n");
}

// 用户相关 RAG（降级参数，切断正反馈）
try {
    List<Document> baguDocs = multiQuerySearchService
            .multiQuerySearchWithCategory(rewrittenForRag, 2, 2, 0.5, "八股");
    if (!baguDocs.isEmpty()) {
        context.append("\n【📖 讲解参考（仅用于点评用户回答，不作为出题依据）】\n");
        for (Document doc : baguDocs) {
            String text = doc.getText();
            if (text.length() > 400) text = text.substring(0, 400) + "…";
            context.append("---\n").append(text).append("\n");
        }
    }
} catch (Exception e) {
    log.warn("RAG 检索失败: {}", e.getMessage());
}
```

- [ ] **Step 3: 添加 [NEXT_TOPIC] 检测**

替换第264行的 `.map(...)`：

```java
.map(full -> {
    if (full.contains("[NEXT_TOPIC]")) {
        topicRotationService.advance(chatId);
        full = full.replace("[NEXT_TOPIC]", "");
    }
    return full.replace("\r\n", "\n").replaceAll("\\n{2,}", "\n");
})
```

- [ ] **Step 4: 修改日志**

第244行改为：

```java
log.info("🚀 AI 调用: chatId={}, topic={}, 已覆盖={}/16, ctxLen={}",
        chatId, topic, covered.size(), contextStr.length());
```

---

### Task 6: System Prompt 瘦身

**Files:**
- Modify: `src/main/java/com/yupi/yuaiagent/app/QuizApp.java:48-72`

- [ ] **Step 1: 替换 SYSTEM_PROMPT**

```java
private static final String SYSTEM_PROMPT = """
        你是大厂技术面试官。每轮对话下方都会注入【本轮必考方向】和该方向的牛客真实面经题目。
        
        🔴 出题规则
        1. 新题必须出自【本轮必考方向】注入的题目，禁止自选方向。
        2. 深度追问：L1 表面 → L2 原理 → L3 源码/实战。同一道题最多追问到 L3。
        3. 当前方向考察完毕的判定：追问触达候选人边界（答不上来或答得很浅），或已问过 2 道该方向的题。
           判定成立时，在回复的最末尾输出标记 [NEXT_TOPIC]（原样输出这 12 个字符，系统会处理，候选人看不到）。
           输出标记的那一轮不要出新题，等下一轮注入。
        4. 已考察过的方向禁止再出新题（追问除外）。
        
        🔴 行为准则
        禁止：空洞夸赞 / 给选择题 / 一次性问多个问题
        每次回复：
        ① 简短评价（答得怎么样、漏了什么）
        ② 答不上来 → 150 字讲解（①②③分条）；答对了 → 直接追问或出新题
        ③ 出题（需要换方向时先输出 [NEXT_TOPIC]，下一轮系统会注入新方向）
        输出风格：自然对话。代码用```java```或```sql```，重要概念**粗体**。
        """;
```

---

### Task 7: 验证

- [ ] **Step 1: mvn compile**

```bash
cd D:/code/qian-ai-agent && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 2: 启动应用**

```bash
mvn spring-boot:run
```

观察日志：
- 孤儿向量清理条数
- 16 个新文件增量 ETL 入库（旧向量 + 新方向文件向量）
- 无启动错误

- [ ] **Step 3: 方向覆盖测试**

用一个脚本连续发送消息测试轮转：

```bash
# 测试方向轮转
for i in $(seq 1 20); do
  curl -s "http://localhost:8080/ai/chat?message=不会，下一题&chatId=test_simple_1"
  echo "--- round $i ---"
done
```

从日志中确认：
- `topic` 在 [NEXT_TOPIC] 或满 4 条消息后才变化
- 一轮内方向不重复，直到 16 个方向全部覆盖
- 覆盖完毕后重新洗牌
- 前端收到响应不含 `[NEXT_TOPIC]`

- [ ] **Step 4: 追问不烧方向测试**

认真回答一道题（触发 AI 追问），确认追问期间 topic 保持不变、covered 不增长。

- [ ] **Step 5: 多会话独立测试**

用另一个 chatId 测试，确认轮转状态独立。
