# 记忆与安全层缺陷加固 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 7 项经核实确认的真缺陷：文件工具路径穿越、JWT URL token 泄漏面、截断丢失方向锚点、无 token 预算、评分输出静默兜底、会话内存泄漏、死代码残留。

**Architecture:** 全部为局部加固，不引入新依赖、不改动架构。新增 3 个单一职责的小类（`SafePathResolver` / `ProtectedMessages` / `MemoryProperties`），沿用项目既有的收拢模式（`ChatIdValidator`）与配置模式（`StorageProperties`）。

**Tech Stack:** Java 17、Spring Boot 3、Spring AI 1.0.0、JUnit 5、Maven。

---

## ⚠️ 执行前必读

### 工作区状态

当前工作区**含另一工作流的未提交改动**（`FileBasedChatMemory` 已从 HEAD 的 278 行重写为 859 行）。
**本计划以当前工作区为基线**，绝不回退或覆盖这些改动。

### 绝不触碰的练习代码

项目含 30 处 `🎯 Task N`（Task 1–14）标记，是作者自设的练习。本计划**不得**修改以下内容：

| 位置 | 标记 |
|---|---|
| `RateLimitAspect` | Task 13 |
| `SemanticCacheService` | Task 9 |
| `JwtAuthFilter` Token 黑名单段（`doFilter` 内 TODO 注释块） | Task 9 第三部分 |
| `FileBasedChatMemory.mapToMessage` 的 `TOOL` 分支 | Task 2（**已完成**） |
| `RubricScorer.callLLMAndParse` 方法体 | 任务 2（**已完成**） |
| `FileOperationTool` / `TerminalOperationTool` 的既有逻辑 | Task 8 |

**Task 5 特别注意**：`RubricScorer` 的 `score()` 上方注释写着"🟢 综合评分入口（框架已写好，你只需要补充上面 3 个 🔴 方法）"，
`callLLMAndParse` 即其中一个 🔴 方法（已完成）。本计划**只在其外部新增校验包装**，
**不修改 `callLLMAndParse` 的方法体**，保留作者的学习成果。

### 环境确认

- [ ] **Step 0: 确认构建可用**

Run: `mvn -v`
Expected: 输出 Maven 版本与 Java 版本（Java 17+）

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS（确认基线可编译）

---

## 文件结构

| 文件 | 动作 | 职责 |
|---|---|---|
| `src/main/java/com/qian/qianaiagent/util/SafePathResolver.java` | 创建 | 路径越界防护，全项目唯一一份 |
| `src/main/java/com/qian/qianaiagent/tools/FileOperationTool.java` | 修改 | 接入路径校验 |
| `src/main/java/com/qian/qianaiagent/filter/JwtAuthFilter.java` | 修改 | URL token 收窄到 SSE 端点 |
| `src/main/java/com/qian/qianaiagent/memory/ProtectedMessages.java` | 创建 | 受保护消息判定与截断，两个记忆类共用 |
| `src/main/java/com/qian/qianaiagent/memory/FileBasedChatMemory.java` | 修改 | 截断时保护 `【方向切换】` |
| `src/main/java/com/qian/qianaiagent/memory/SummarizingChatMemory.java` | 修改 | token 预算裁剪 |
| `src/main/java/com/qian/qianaiagent/config/MemoryProperties.java` | 创建 | `qian.memory.*` 配置绑定 |
| `src/main/java/com/qian/qianaiagent/config/ChatMemoryConfig.java` | 修改 | 装配 `MemoryProperties` |
| `src/main/java/com/qian/qianaiagent/evaluation/RubricResultValidator.java` | 创建 | 评分结果语义不变量校验 |
| `src/main/java/com/qian/qianaiagent/evaluation/RubricScorer.java` | 修改 | 校验失败带原因重试一次 |
| `src/main/java/com/qian/qianaiagent/interview/progress/ActiveSpecManager.java` | 修改 | 加 lastAccess 与过期清理 |
| `src/main/java/com/qian/qianaiagent/controller/ConversationController.java` | 修改 | 删除会话时清理 spec |
| `src/main/java/com/qian/qianaiagent/ability/UserAbilityService.java` | 修改 | 删除 4 个死方法 |
| `src/main/java/com/qian/qianaiagent/interview/progress/AskedPointTracker.java` | 修改 | 删死方法 + 标注 |

---

## Task 1: SafePathResolver 路径越界防护

**Files:**
- Create: `src/main/java/com/qian/qianaiagent/util/SafePathResolver.java`
- Modify: `src/main/java/com/qian/qianaiagent/tools/FileOperationTool.java`
- Test: `src/test/java/com/qian/qianaiagent/util/SafePathResolverTest.java`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/qian/qianaiagent/util/SafePathResolverTest.java`：

```java
package com.qian.qianaiagent.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafePathResolverTest {

    @TempDir
    Path base;

    @Test
    void rejectsParentTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> SafePathResolver.resolveWithin(base, "../evil.txt"));
    }

    @Test
    void rejectsNestedTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> SafePathResolver.resolveWithin(base, "a/../../evil.txt"));
    }

    @Test
    void rejectsAbsolutePath() {
        assertThrows(IllegalArgumentException.class,
                () -> SafePathResolver.resolveWithin(base, base.resolve("x.txt").toAbsolutePath().toString()));
    }

    @Test
    void rejectsBlankAndNull() {
        assertThrows(IllegalArgumentException.class,
                () -> SafePathResolver.resolveWithin(base, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> SafePathResolver.resolveWithin(base, null));
    }

    @Test
    void acceptsLegalRelativePath() {
        Path resolved = SafePathResolver.resolveWithin(base, "notes.md");
        assertEquals("notes.md", resolved.getFileName().toString());
        assertTrue(resolved.startsWith(base.toRealPath()));
    }

    @Test
    void acceptsNestedLegalPath() {
        Path resolved = SafePathResolver.resolveWithin(base, "sub/dir/notes.md");
        assertTrue(resolved.startsWith(base.toRealPath()));
    }

    @Test
    void rejectsSymlinkEscapingBase() throws Exception {
        // 符号链接指向目录外 —— 规范化的字符串前缀检查挡不住，必须靠 toRealPath
        Path outside = Files.createTempDirectory("outside");
        Path link = base.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, outside.resolve("victim.txt"));
        } catch (UnsupportedOperationException | java.io.IOException e) {
            return;     // Windows 无权限时跳过，不误报失败
        }
        assertThrows(IllegalArgumentException.class,
                () -> SafePathResolver.resolveWithin(base, "link.txt"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -Dtest=SafePathResolverTest test`
Expected: 编译失败 — `SafePathResolver` 不存在

- [ ] **Step 3: 实现 SafePathResolver**

创建 `src/main/java/com/qian/qianaiagent/util/SafePathResolver.java`：

```java
package com.qian.qianaiagent.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 把用户/LLM 提供的相对路径安全地解析到指定根目录之内 —— 全项目唯一一份。
 *
 * <h2>为什么必须收拢</h2>
 * 与 {@link ChatIdValidator} 同源的问题：路径校验一旦散落成多份 local 副本，
 * 必然出现「有的地方有、有的地方没有」。{@code FileOperationTool} 此前是完全裸奔的
 * —— 它暴露给 LLM，可以直接传 {@code ../../data/chat-memory/user_1.json}。
 *
 * <h2>三层防护</h2>
 * <ol>
 *   <li>拒绝 {@code ..} 与绝对路径（字符层面，挡住绝大多数尝试）</li>
 *   <li>{@code normalize()} 后做前缀检查（挡住 {@code a/../../x} 这类嵌套）</li>
 *   <li>目标已存在时用 {@code toRealPath()} 再查一次（挡住符号链接逃逸）</li>
 * </ol>
 */
public final class SafePathResolver {

    private SafePathResolver() {
    }

    /**
     * 把 {@code userPath} 解析到 {@code baseDir} 之内。
     *
     * @return 解析后的绝对路径，保证位于 {@code baseDir} 内
     * @throws IllegalArgumentException 路径为空、越界、或是绝对路径
     */
    public static Path resolveWithin(Path baseDir, String userPath) {
        if (userPath == null || userPath.isBlank()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        if (userPath.contains("..")) {
            throw new IllegalArgumentException("路径不能包含 ..: " + userPath);
        }
        if (Path.of(userPath).isAbsolute()) {
            throw new IllegalArgumentException("不允许绝对路径: " + userPath);
        }

        Path normalizedBase = realBase(baseDir);
        Path resolved = normalizedBase.resolve(userPath).normalize();
        if (!resolved.startsWith(normalizedBase)) {
            throw new IllegalArgumentException("路径越界: " + userPath);
        }

        // 目标已存在时再做一次符号链接解析 —— 字符串前缀检查对 link -> 目录外 无效
        if (Files.exists(resolved)) {
            try {
                if (!resolved.toRealPath().startsWith(normalizedBase)) {
                    throw new IllegalArgumentException("路径经由符号链接越界: " + userPath);
                }
            } catch (IOException e) {
                // 解析失败：退回上面的规范化结果，它在字符层面已确认安全
            }
        }
        return resolved;
    }

    /** 根目录可能存在（首次写入前不存在），拿不到 realPath 时退化为规范化绝对路径。 */
    private static Path realBase(Path baseDir) {
        try {
            return baseDir.toRealPath();
        } catch (IOException e) {
            return baseDir.toAbsolutePath().normalize();
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -Dtest=SafePathResolverTest test`
Expected: PASS（6 个用例；符号链接用例在无权限环境下静默跳过）

- [ ] **Step 5: 接入 FileOperationTool**

修改 `src/main/java/com/qian/qianaiagent/tools/FileOperationTool.java` 为：

```java
package com.qian.qianaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.qian.qianaiagent.constant.FileConstant;
import com.qian.qianaiagent.util.SafePathResolver;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Path;

/**
 * 文件操作工具类（提供文件读写功能）
 * <p>
 * 🔴 对外暴露给 LLM，入参不可信 —— 所有路径都必须经 {@link SafePathResolver} 收进 FILE_ROOT。
 */
public class FileOperationTool {

    private static final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";
    private static final Path FILE_ROOT = Path.of(FILE_DIR);

    @Tool(description = "读取指定文件的内容。使用时机：需要查看之前保存的文件内容时。")
    public String readFile(@ToolParam(description = "要读取的文件名，例如：'output.txt'、'notes.md'") String fileName) {
        try {
            Path filePath = SafePathResolver.resolveWithin(FILE_ROOT, fileName);
            return FileUtil.readUtf8String(filePath.toString());
        } catch (IllegalArgumentException e) {
            return "Error: 非法路径 — " + e.getMessage();
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "将内容写入文件。使用时机：需要保存分析结果、日志、或给用户生成文件时。")
    public String writeFile(@ToolParam(description = "要写入的文件名，例如：'result.txt'、'analysis.md'") String fileName,
                            @ToolParam(description = "要写入的文件内容（支持 Markdown/文本）") String content
    ) {
        try {
            Path filePath = SafePathResolver.resolveWithin(FILE_ROOT, fileName);
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(content, filePath.toString());
            return "File written successfully to: " + filePath;
        } catch (IllegalArgumentException e) {
            return "Error: 非法路径 — " + e.getMessage();
        } catch (Exception e) {
            return "Error writing to file: " + e.getMessage();
        }
    }
}
```

- [ ] **Step 6: 加工具级测试**

修改 `src/test/java/com/qian/qianaiagent/tools/FileOperationToolTest.java`，追加两个用例（保留原有的两个）：

```java
    @Test
    void rejectsTraversalOnRead() {
        FileOperationTool tool = new FileOperationTool();
        String result = tool.readFile("../../pom.xml");
        Assertions.assertTrue(result.startsWith("Error: 非法路径"), "实际: " + result);
    }

    @Test
    void rejectsTraversalOnWrite() {
        FileOperationTool tool = new FileOperationTool();
        String result = tool.writeFile("../escaped.txt", "x");
        Assertions.assertTrue(result.startsWith("Error: 非法路径"), "实际: " + result);
    }
```

- [ ] **Step 7: 运行全部工具测试**

Run: `mvn -q -Dtest='SafePathResolverTest,FileOperationToolTest' test`
Expected: PASS

- [ ] **Step 8: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/util/SafePathResolver.java \
        src/main/java/com/qian/qianaiagent/tools/FileOperationTool.java \
        src/test/java/com/qian/qianaiagent/util/SafePathResolverTest.java \
        src/test/java/com/qian/qianaiagent/tools/FileOperationToolTest.java
git commit -m "fix: FileOperationTool 接入路径越界防护"
```

---

## Task 2: JWT URL token 收窄到 SSE 端点

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/filter/JwtAuthFilter.java:111-124`
- Test: `src/test/java/com/qian/qianaiagent/filter/JwtAuthFilterUrlTokenTest.java`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/qian/qianaiagent/filter/JwtAuthFilterUrlTokenTest.java`：

```java
package com.qian.qianaiagent.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthFilterUrlTokenTest {

    @Test
    void urlTokenAllowedOnlyOnSseEndpoints() {
        assertTrue(JwtAuthFilter.allowsUrlToken("/api/ai/chat", "/api"));
        assertTrue(JwtAuthFilter.allowsUrlToken("/api/ai/review/chat", "/api"));
        assertTrue(JwtAuthFilter.allowsUrlToken("/api/ai/agent/chat", "/api"));

        assertFalse(JwtAuthFilter.allowsUrlToken("/api/conversations", "/api"));
        assertFalse(JwtAuthFilter.allowsUrlToken("/api/user/profile", "/api"));
        assertFalse(JwtAuthFilter.allowsUrlToken("/api/ai/review/pool/abc", "/api"));
    }

    @Test
    void rejectsUrlTokenOnNonSseEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/conversations");
        request.setRequestURI("/api/conversations");
        request.setContextPath("/api");
        request.addParameter("token", "some.jwt.value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthFilter().doFilter(request, response, new MockFilterChain());

        // token 不再从 URL 取 → 视为未登录
        assertEquals(401, response.getStatus());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -Dtest=JwtAuthFilterUrlTokenTest test`
Expected: 编译失败 — `allowsUrlToken` 不存在

- [ ] **Step 3: 实现收窄**

修改 `src/main/java/com/qian/qianaiagent/filter/JwtAuthFilter.java`。

在 `WHITE_PATH_PREFIXES` 之后新增常量与方法：

```java
    /**
     * 允许通过 URL 参数传 token 的端点 —— 仅限 SSE。
     * <p>
     * {@code EventSource} 无法设置自定义请求头，所以这几个端点必须能从 query 取 token。
     * 但 URL 会进 access log、Referer 与浏览器历史，因此<b>不能全站开放</b>：
     * 判据用路径白名单而非 {@code Accept} 头 —— 后者由客户端声明、可随意伪造。
     */
    private static final String[] URL_TOKEN_PATHS = {
            "/ai/chat",             // InterviewChatController.doChat
            "/ai/review/chat",      // InterviewChatController.doReviewChat
            "/ai/agent/chat"        // AgentChatController.doAgentChat
    };

    /** 该路径是否允许从 URL 查询参数取 token。包级可见以便单测。 */
    static boolean allowsUrlToken(String path, String contextPath) {
        for (String allowed : URL_TOKEN_PATHS) {
            if (path.equals(contextPath + allowed)) {
                return true;
            }
        }
        return false;
    }
```

把 `doFilter` 中的 token 提取段（原 `:111-120`）替换为：

```java
        // 从请求头 Authorization 提取 Token；
        // 仅 SSE 端点允许回退到 URL 参数（EventSource 无法发送自定义请求头）
        String token = null;
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (allowsUrlToken(path, contextPath)) {
            token = request.getParameter("token");
        }
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -Dtest=JwtAuthFilterUrlTokenTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/filter/JwtAuthFilter.java \
        src/test/java/com/qian/qianaiagent/filter/JwtAuthFilterUrlTokenTest.java
git commit -m "fix: URL token 仅限 SSE 端点，其余路径要求 Authorization 头"
```

---

## Task 3: 截断保护【方向切换】

**Files:**
- Create: `src/main/java/com/qian/qianaiagent/memory/ProtectedMessages.java`
- Modify: `src/main/java/com/qian/qianaiagent/memory/FileBasedChatMemory.java`（`add` 与 `replaceMessages` 的截断段）
- Modify: `src/main/java/com/qian/qianaiagent/memory/SummarizingChatMemory.java:70-82`
- Test: `src/test/java/com/qian/qianaiagent/memory/TruncationPreservesMarkerTest.java`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/qian/qianaiagent/memory/TruncationPreservesMarkerTest.java`：

```java
package com.qian.qianaiagent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TruncationPreservesMarkerTest {

    @TempDir
    Path tempDir;

    private static final String MARKER = "【方向切换】旧方向考察结束";

    @Test
    void truncationKeepsTopicSwitchMarker() {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        String chatId = "trunc";

        memory.add(chatId, List.of(new SystemMessage(MARKER)));
        List<Message> filler = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            filler.add(new UserMessage("m" + i));
        }
        memory.add(chatId, filler);

        List<Message> back = memory.get(chatId);
        assertEquals(200, back.size(), "应被截断到上限");
        assertTrue(back.stream().anyMatch(m -> m.getText().contains(MARKER)),
                "【方向切换】绝不能被截断丢弃");
    }

    @Test
    void truncationUnchangedWhenNoMarker() {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        String chatId = "plain";

        List<Message> filler = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            filler.add(new UserMessage("m" + i));
        }
        memory.add(chatId, filler);

        List<Message> back = memory.get(chatId);
        assertEquals(200, back.size());
        // 无受保护消息时行为不变：保留最新的 200 条
        assertEquals("m249", back.get(back.size() - 1).getText());
        assertEquals("m50", back.get(0).getText());
    }

    @Test
    void replaceMessagesAlsoPreservesMarker() {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        String chatId = "replace";
        List<Message> input = new ArrayList<>();
        input.add(new SystemMessage(MARKER));
        for (int i = 0; i < 250; i++) {
            input.add(new UserMessage("m" + i));
        }
        memory.replaceMessages(chatId, input);

        List<Message> back = memory.get(chatId);
        assertEquals(200, back.size());
        assertTrue(back.stream().anyMatch(m -> m.getText().contains(MARKER)));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -Dtest=TruncationPreservesMarkerTest test`
Expected: FAIL — `truncationKeepsTopicSwitchMarker` 断言失败（marker 已被丢弃）

- [ ] **Step 3: 实现 ProtectedMessages**

创建 `src/main/java/com/qian/qianaiagent/memory/ProtectedMessages.java`：

```java
package com.qian.qianaiagent.memory;

import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 受保护消息的判定与截断 —— {@link FileBasedChatMemory} 与 {@link SummarizingChatMemory} 共用。
 *
 * <h2>为什么必须共用一份</h2>
 * 「哪些消息不能丢」这条语义此前只写在 {@code SummarizingChatMemory} 里（按文本匹配
 * {@code 【方向切换】}）。{@code FileBasedChatMemory} 的截断完全不知道它，
 * 于是同一份数据在两层有<b>两套互相矛盾的保留规则</b> —— 摘要层小心翼翼保住的锚点，
 * 会被存储层按条数无差别删掉。判定逻辑收到这里，两层用同一份。
 *
 * <h2>锚点为什么丢不得</h2>
 * {@code 【方向切换】} 一旦丢失，AI 会拿旧方向的题目点评新方向的回答 ——
 * 这是 {@code TopicMemoryTrimmer} 整个类存在的原因。
 */
final class ProtectedMessages {

    /** 方向切换标记。改这里等于改所有保留语义，务必同步 {@code TopicMemoryTrimmer}。 */
    static final String TOPIC_SWITCH_MARKER = "【方向切换】";

    private ProtectedMessages() {
    }

    /** 该消息是否受保护（任何截断都不得丢弃）。 */
    static boolean isProtected(Message message) {
        String text = message != null ? message.getText() : null;
        return text != null && text.contains(TOPIC_SWITCH_MARKER);
    }

    /**
     * 截断到 {@code max} 条，但受保护消息永不丢弃。
     * <p>
     * 受保护消息排在结果最前（它们语义上就属于会话开头），其余配额按「从新到旧」填充。
     * 若受保护消息自身已超过 {@code max}，只保留最新的 {@code max} 条。
     */
    static List<Message> truncate(List<Message> messages, int max) {
        if (messages.size() <= max) {
            return new ArrayList<>(messages);
        }

        List<Message> protectedMsgs = new ArrayList<>();
        List<Message> normal = new ArrayList<>();
        for (Message m : messages) {
            if (isProtected(m)) {
                protectedMsgs.add(m);
            } else {
                normal.add(m);
            }
        }

        if (protectedMsgs.size() >= max) {
            return new ArrayList<>(protectedMsgs.subList(protectedMsgs.size() - max, protectedMsgs.size()));
        }

        int quota = max - protectedMsgs.size();
        List<Message> result = new ArrayList<>(protectedMsgs);
        result.addAll(normal.subList(normal.size() - quota, normal.size()));
        return result;
    }
}
```

- [ ] **Step 4: 让 FileBasedChatMemory 使用它**

修改 `src/main/java/com/qian/qianaiagent/memory/FileBasedChatMemory.java`。

`add()` 中原来的截断块：

```java
            existing.addAll(messages);
            if (existing.size() > MAX_MESSAGES_PER_FILE) {
                int removed = existing.size() - MAX_MESSAGES_PER_FILE;
                existing = new ArrayList<>(existing.subList(removed, existing.size()));
                log.warn("对话 {} 超过 {} 条消息，已截断，移除最早 {} 条",
                        conversationId, MAX_MESSAGES_PER_FILE, removed);
            }
```

替换为：

```java
            existing.addAll(messages);
            if (existing.size() > MAX_MESSAGES_PER_FILE) {
                int before = existing.size();
                existing = ProtectedMessages.truncate(existing, MAX_MESSAGES_PER_FILE);
                log.warn("对话 {} 超过 {} 条消息，已截断 {} → {} 条（受保护消息保留）",
                        conversationId, MAX_MESSAGES_PER_FILE, before, existing.size());
            }
```

`replaceMessages()` 中同样的截断块：

```java
            List<Message> copy = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
            if (copy.size() > MAX_MESSAGES_PER_FILE) {
                int removed = copy.size() - MAX_MESSAGES_PER_FILE;
                copy = new ArrayList<>(copy.subList(removed, copy.size()));
            }
```

替换为：

```java
            List<Message> copy = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
            if (copy.size() > MAX_MESSAGES_PER_FILE) {
                copy = ProtectedMessages.truncate(copy, MAX_MESSAGES_PER_FILE);
            }
```

- [ ] **Step 5: 让 SummarizingChatMemory 复用同一判定**

修改 `src/main/java/com/qian/qianaiagent/memory/SummarizingChatMemory.java`，
把 `get()` 中内联的保护扫描（原 `:70-82`）替换为复用：

```java
        // 🔴 保护【方向切换】消息不被摘要（判定与 FileBasedChatMemory 共用同一份）
        List<Message> protectedMsgs = new ArrayList<>();
        int firstUnprotected = 0;
        for (int i = 0; i < all.size(); i++) {
            if (ProtectedMessages.isProtected(all.get(i))) {
                protectedMsgs.add(all.get(i));
                firstUnprotected = i + 1;
            } else {
                break; // 只在开头连续查找，遇到非保护消息就停止
            }
        }
```

- [ ] **Step 6: 运行测试确认通过**

Run: `mvn -q -Dtest='TruncationPreservesMarkerTest,FileBasedChatMemoryTest,ConversationRetentionTest' test`
Expected: PASS（含既有回归）

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/memory/ProtectedMessages.java \
        src/main/java/com/qian/qianaiagent/memory/FileBasedChatMemory.java \
        src/main/java/com/qian/qianaiagent/memory/SummarizingChatMemory.java \
        src/test/java/com/qian/qianaiagent/memory/TruncationPreservesMarkerTest.java
git commit -m "fix: 截断时保护【方向切换】锚点，两层记忆共用同一判定"
```

---

## Task 4: 记忆窗口 token 预算

**Files:**
- Create: `src/main/java/com/qian/qianaiagent/config/MemoryProperties.java`
- Modify: `src/main/java/com/qian/qianaiagent/memory/SummarizingChatMemory.java`
- Modify: `src/main/java/com/qian/qianaiagent/config/ChatMemoryConfig.java`
- Test: `src/test/java/com/qian/qianaiagent/memory/TokenBudgetTest.java`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/qian/qianaiagent/memory/TokenBudgetTest.java`：

```java
package com.qian.qianaiagent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBudgetTest {

    @TempDir
    Path tempDir;

    /** maxMessages 设得很大，确保触发的是 token 裁剪而非条数裁剪。 */
    private SummarizingChatMemory memory(int maxMessages, int maxTokens) {
        return new SummarizingChatMemory(
                new FileBasedChatMemory(tempDir.toString()), null, maxMessages, maxTokens);
    }

    @Test
    void estimatesTokensWithSafetyMargin() {
        assertEquals(0, SummarizingChatMemory.estimateTokens(null));
        assertEquals(0, SummarizingChatMemory.estimateTokens(""));
        // 150 字 → 150/1.5*1.3 = 130
        assertEquals(130, SummarizingChatMemory.estimateTokens("a".repeat(150)));
    }

    @Test
    void trimsWhenTokenBudgetExceeded() {
        SummarizingChatMemory memory = memory(1000, 300);
        String chatId = "budget";
        List<Message> msgs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            msgs.add(new UserMessage("x".repeat(100)));   // 每条约 87 token
        }
        memory.add(chatId, msgs);

        List<Message> window = memory.get(chatId);
        int total = window.stream().mapToInt(m -> SummarizingChatMemory.estimateTokens(m.getText())).sum();
        assertTrue(total <= 300, "窗口 token 应被裁到预算内，实际 " + total);
        assertTrue(window.size() < 20, "应丢弃了部分消息");
        // 保留的必须是最近的
        assertEquals(msgs.get(19).getText(), window.get(window.size() - 1).getText());
    }

    @Test
    void keepsEverythingWhenWithinBudget() {
        SummarizingChatMemory memory = memory(1000, 100000);
        String chatId = "roomy";
        List<Message> msgs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            msgs.add(new UserMessage("short" + i));
        }
        memory.add(chatId, msgs);
        assertEquals(5, memory.get(chatId).size());
    }

    @Test
    void tokenTrimNeverDropsProtectedMessage() {
        SummarizingChatMemory memory = memory(1000, 200);
        String chatId = "protected";
        List<Message> msgs = new ArrayList<>();
        msgs.add(new SystemMessage("【方向切换】旧方向结束"));
        for (int i = 0; i < 20; i++) {
            msgs.add(new UserMessage("x".repeat(100)));
        }
        memory.add(chatId, msgs);

        List<Message> window = memory.get(chatId);
        assertTrue(window.stream().anyMatch(m -> m.getText().contains("【方向切换】")),
                "token 裁剪同样不得丢弃锚点");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -Dtest=TokenBudgetTest test`
Expected: 编译失败 — 构造器签名不匹配、`estimateTokens` 不存在

- [ ] **Step 3: 实现 MemoryProperties**

创建 `src/main/java/com/qian/qianaiagent/config/MemoryProperties.java`：

```java
package com.qian.qianaiagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对话记忆窗口配置。
 *
 * <p>默认值与改造前逐字节一致（20 条），因此不配置任何属性时行为不变。
 * token 预算默认 8000，`<= 0` 表示关闭该维度、退回按条数裁剪。
 */
@ConfigurationProperties(prefix = "qian.memory")
public class MemoryProperties {

    private final Window window = new Window();

    public Window getWindow() {
        return window;
    }

    /** 记忆窗口。 */
    public static class Window {

        /** 最大保留消息条数。 */
        private int maxMessages = 20;

        /** 最大估算 token 数，<= 0 表示不限制。 */
        private int maxTokens = 8000;

        public int getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }
}
```

- [ ] **Step 4: 实现 token 裁剪**

修改 `src/main/java/com/qian/qianaiagent/memory/SummarizingChatMemory.java`。

新增常量与静态估算方法：

```java
    /** 中文约 1.5 字/token 的折中估计；不引入 tokenizer 库，避免虚假的精确感。 */
    private static final double CHARS_PER_TOKEN = 1.5;
    /** 安全余量：宁可估多，不可估少。 */
    private static final double SAFETY_FACTOR = 1.3;

    /**
     * 估算一段文本占用的 token 数。
     * <p>
     * 用字符启发式而非真实 tokenizer：DeepSeek 的 tokenizer 非公开稳定，
     * 引入 jtokkit 之类只会给出一个看似精确、实则同样偏差的数字。
     */
    static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN * SAFETY_FACTOR);
    }
```

构造函数改为同时接受两个阈值（保留旧签名委托，避免破坏既有调用）：

```java
    private final int maxTokens;

    public SummarizingChatMemory(FileBasedChatMemory delegate, ConversationSummarizer summarizer) {
        this(delegate, summarizer, DEFAULT_MAX_MESSAGES, DEFAULT_MAX_TOKENS);
    }

    public SummarizingChatMemory(FileBasedChatMemory delegate, ConversationSummarizer summarizer,
                                  int maxMessages) {
        this(delegate, summarizer, maxMessages, DEFAULT_MAX_TOKENS);
    }

    public SummarizingChatMemory(FileBasedChatMemory delegate, ConversationSummarizer summarizer,
                                  int maxMessages, int maxTokens) {
        this.delegate = delegate;
        this.summarizer = summarizer;
        this.maxMessages = maxMessages;
        this.maxTokens = maxTokens;
        log.info("SummarizingChatMemory 初始化完成，窗口: {} 条消息 / {} token",
                maxMessages, maxTokens > 0 ? maxTokens : "不限");
    }
```

新增默认常量：

```java
    private static final int DEFAULT_MAX_TOKENS = 8000;
```

把 `get()` 的开头改为**先按条数取窗口，再按 token 裁剪**（关键：原实现在条数未超时直接
`return all`，会绕过 token 检查）：

```java
    @Override
    public List<Message> get(String conversationId) {
        List<Message> all = delegate.get(conversationId);
        if (all.isEmpty()) {
            return all;
        }
        List<Message> windowed = all.size() <= maxMessages
                ? new ArrayList<>(all)
                : windowWithSummary(conversationId, all);
        return trimToTokenBudget(windowed);
    }
```

把摘要窗口逻辑整体搬进 `windowWithSummary`——方法体即 **Task 3 完成后** `get()` 的中段，
**逻辑一行不改**，只是把 `all` 换成参数、去掉最外层 `if`。仔细对照下面代码里的
`ProtectedMessages.isProtected`：那是 Task 3 的产物，此处一并搬移，不要还原成内联判定。

```java
    /** 按条数取窗口：超阈值时 [受保护消息] + [摘要] + [最近 N 条原文]。 */
    private List<Message> windowWithSummary(String conversationId, List<Message> all) {
        CachedSummary cached = summaryCache.get(conversationId);
        if (cached != null && cached.totalMessageCount == all.size()) {
            log.debug("使用缓存的对话摘要: chatId={}, summaryLen={}",
                    conversationId, cached.summary.length());
            return buildResult(cached.summary, cached.recentMessages);
        }

        // 🔴 保护【方向切换】消息不被摘要（判定与 FileBasedChatMemory 共用同一份）
        List<Message> protectedMsgs = new ArrayList<>();
        int firstUnprotected = 0;
        for (int i = 0; i < all.size(); i++) {
            if (ProtectedMessages.isProtected(all.get(i))) {
                protectedMsgs.add(all.get(i));
                firstUnprotected = i + 1;
            } else {
                break; // 只在开头连续查找，遇到非保护消息就停止
            }
        }
        int effectiveMax = maxMessages - protectedMsgs.size();
        List<Message> rest = all.subList(firstUnprotected, all.size());
        if (rest.size() <= effectiveMax) {
            List<Message> result = new ArrayList<>(protectedMsgs);
            result.addAll(rest);
            return result;
        }

        int summaryCount = rest.size() - effectiveMax;
        List<Message> toSummarize = rest.subList(0, summaryCount);
        List<Message> recent = new ArrayList<>(rest.subList(summaryCount, rest.size()));
        log.info("触发对话摘要: chatId={}, 总消息={}, 受保护={}, 需摘要={}, 保留={}",
                conversationId, all.size(), protectedMsgs.size(), summaryCount, recent.size());
        String summary = summarizer.summarize(toSummarize);
        summaryCache.put(conversationId, new CachedSummary(summary, recent, all.size()));

        List<Message> result = new ArrayList<>(protectedMsgs);
        result.addAll(buildResult(summary, recent));
        return result;
    }

    /**
     * 按 token 预算裁剪：从最新往旧累加，超预算的普通消息丢弃。
     * <p>
     * 🔴 受保护消息不计入丢弃 —— 但也不 break，因为锚点通常在最早处，
     * break 会让它第一个被丢掉，正好与保护意图相反。
     */
    private List<Message> trimToTokenBudget(List<Message> messages) {
        if (maxTokens <= 0) {
            return messages;
        }
        int total = messages.stream().mapToInt(m -> estimateTokens(m.getText())).sum();
        if (total <= maxTokens) {
            return messages;
        }

        List<Message> kept = new ArrayList<>();
        int used = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (!ProtectedMessages.isProtected(m) && used + estimateTokens(m.getText()) > maxTokens) {
                continue;
            }
            kept.add(m);
            used += estimateTokens(m.getText());
        }
        Collections.reverse(kept);
        log.info("token 预算裁剪: {} 条 → {} 条（预算 {} token，实际约 {}）",
                messages.size(), kept.size(), maxTokens, used);
        return kept;
    }
```

补充 import：`java.util.Collections`。

- [ ] **Step 5: 装配配置**

修改 `src/main/java/com/qian/qianaiagent/config/ChatMemoryConfig.java`：

```java
    @Bean
    public ChatMemory chatMemory(FileBasedChatMemory fileBasedChatMemory,
                                  ConversationSummarizer conversationSummarizer,
                                  MemoryProperties memoryProperties) {
        MemoryProperties.Window window = memoryProperties.getWindow();
        return new SummarizingChatMemory(fileBasedChatMemory, conversationSummarizer,
                window.getMaxMessages(), window.getMaxTokens());
    }
```

> `MemoryProperties` 通过 `@ConfigurationPropertiesScan`（已在 `QianAiAgentApplication:10` 启用）自动注册，无需额外注解。

- [ ] **Step 6: 运行测试确认通过**

Run: `mvn -q -Dtest='TokenBudgetTest,FileBasedChatMemoryTest,StoragePropertiesTest' test`
Expected: PASS

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/config/MemoryProperties.java \
        src/main/java/com/qian/qianaiagent/config/ChatMemoryConfig.java \
        src/main/java/com/qian/qianaiagent/memory/SummarizingChatMemory.java \
        src/test/java/com/qian/qianaiagent/memory/TokenBudgetTest.java
git commit -m "feat: 记忆窗口引入 token 预算，配置化 qian.memory.window"
```

---

## Task 5: 评分结果校验 + 重试

> ⚠️ **不修改 `RubricScorer.callLLMAndParse` 的方法体** —— 它是作者已完成的 Task 2 练习成果。
> 本任务只在它外部新增校验包装。

**Files:**
- Create: `src/main/java/com/qian/qianaiagent/evaluation/RubricResultValidator.java`
- Modify: `src/main/java/com/qian/qianaiagent/evaluation/RubricScorer.java`（仅 `score()` 内两行）
- Test: `src/test/java/com/qian/qianaiagent/evaluation/RubricResultValidatorTest.java`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/qian/qianaiagent/evaluation/RubricResultValidatorTest.java`：

```java
package com.qian.qianaiagent.evaluation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RubricResultValidatorTest {

    private static RubricScorer.RubricResult valid() {
        return RubricScorer.RubricResult.builder()
                .reasoningQuality(20).faithfulness(20).completeness(20).toolUsage(20)
                .totalScore(80)
                .overallComment("ok")
                .build();
    }

    @Test
    void acceptsWellFormedResult() {
        assertFalse(RubricResultValidator.validate(valid()).isPresent());
    }

    @Test
    void rejectsNull() {
        assertTrue(RubricResultValidator.validate(null).isPresent());
    }

    @Test
    void rejectsParseFailureComment() {
        RubricScorer.RubricResult r = valid();
        r.setOverallComment("JSON 解析失败: unexpected token");
        assertTrue(RubricResultValidator.validate(r).isPresent());
    }

    @Test
    void rejectsEmptyResponseComment() {
        RubricScorer.RubricResult r = valid();
        r.setOverallComment("LLM 返回空响应，评分失败");
        assertTrue(RubricResultValidator.validate(r).isPresent());
    }

    @Test
    void rejectsTotalScoreNotEqualToSum() {
        RubricScorer.RubricResult r = valid();
        r.setTotalScore(99);
        assertTrue(RubricResultValidator.validate(r).isPresent());
    }

    @Test
    void rejectsDimensionOutOfRange() {
        RubricScorer.RubricResult r = RubricScorer.RubricResult.builder()
                .reasoningQuality(150).faithfulness(10).completeness(10).toolUsage(10)
                .totalScore(180)
                .overallComment("ok")
                .build();
        assertTrue(RubricResultValidator.validate(r).isPresent());
    }

    @Test
    void rejectsBlankOverallComment() {
        RubricScorer.RubricResult r = valid();
        r.setOverallComment("  ");
        assertTrue(RubricResultValidator.validate(r).isPresent());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -Dtest=RubricResultValidatorTest test`
Expected: 编译失败 — `RubricResultValidator` 不存在

- [ ] **Step 3: 实现校验器**

创建 `src/main/java/com/qian/qianaiagent/evaluation/RubricResultValidator.java`：

```java
package com.qian.qianaiagent.evaluation;

import java.util.Optional;

/**
 * 评分结果的语义不变量校验。
 *
 * <h2>为什么不是 JSON Schema 校验</h2>
 * 结构问题（字段缺失、类型不符）Jackson 的 {@code readValue} 已经会抛异常了，
 * 再套一层 JSON Schema 是重复劳动。真正会静默出错的是<b>结构合法但语义违规</b> ——
 * 例如 LLM 返回 {@code totalScore: 120} 而四个维度加起来只有 60，
 * 这种数据会被原样写进 {@code data/evals/}，污染所有下游统计。
 *
 * <h2>为什么要有这个类</h2>
 * 改造前解析失败会静默兜底成 {@code totalScore=0}，把「解析问题」伪装成「评分为 0」。
 * 有了校验器，调用方才能区分「真的 0 分」和「没解析出来」，进而决定是否重试。
 */
final class RubricResultValidator {

    private static final int DIMENSION_MAX = 25;

    private RubricResultValidator() {
    }

    /**
     * @return 空表示合规；非空是<b>可直接回灌给 LLM 的失败原因</b>
     */
    static Optional<String> validate(RubricScorer.RubricResult result) {
        if (result == null) {
            return Optional.of("评分结果为 null");
        }
        String comment = result.getOverallComment();
        if (comment == null || comment.isBlank()) {
            return Optional.of("缺少 overallComment 字段");
        }
        // callLLMAndParse 的兜底路径会写入这两种前缀，可据此识别「其实没解析成功」
        if (comment.startsWith("JSON 解析失败")) {
            return Optional.of(comment);
        }
        if (comment.startsWith("LLM 返回空响应")) {
            return Optional.of(comment);
        }

        int sum = result.getReasoningQuality() + result.getFaithfulness()
                + result.getCompleteness() + result.getToolUsage();
        if (result.getTotalScore() != sum) {
            return Optional.of("totalScore=%d 与四维度之和 %d 不一致"
                    .formatted(result.getTotalScore(), sum));
        }

        if (result.getReasoningQuality() < 0 || result.getReasoningQuality() > DIMENSION_MAX) {
            return Optional.of("reasoningQuality 超出 0-%d：%d".formatted(DIMENSION_MAX, result.getReasoningQuality()));
        }
        if (result.getFaithfulness() < 0 || result.getFaithfulness() > DIMENSION_MAX) {
            return Optional.of("faithfulness 超出 0-%d：%d".formatted(DIMENSION_MAX, result.getFaithfulness()));
        }
        if (result.getCompleteness() < 0 || result.getCompleteness() > DIMENSION_MAX) {
            return Optional.of("completeness 超出 0-%d：%d".formatted(DIMENSION_MAX, result.getCompleteness()));
        }
        if (result.getToolUsage() < 0 || result.getToolUsage() > DIMENSION_MAX) {
            return Optional.of("toolUsage 超出 0-%d：%d".formatted(DIMENSION_MAX, result.getToolUsage()));
        }
        return Optional.empty();
    }
}
```

> `RubricResult` 的注解是 `@Data @Builder @NoArgsConstructor @AllArgsConstructor`（**无** `toBuilder`），
> 所以测试里改单个字段走 Lombok 生成的 setter，构造新对象才用 builder。

- [ ] **Step 4: 运行校验器测试**

Run: `mvn -q -Dtest=RubricResultValidatorTest test`
Expected: PASS

- [ ] **Step 5: 在 score() 中接入重试**

修改 `src/main/java/com/qian/qianaiagent/evaluation/RubricScorer.java` 的 `score()`，
把原来的第 2 步（`RubricResult result = callLLMAndParse(prompt);`）替换为：

```java
        // 第 2 步：调 LLM 打分；结果不合规时带失败原因重试一次
        RubricResult result = callLLMAndParse(prompt);
        Optional<String> problem = RubricResultValidator.validate(result);
        if (problem.isPresent()) {
            log.warn("评分结果不合规（{}），重试一次", problem.get());
            String retryPrompt = prompt
                    + "\n\n⚠️ 上一次输出不合规：" + problem.get()
                    + "\n请重新输出严格符合上述字段与约束的 JSON。";
            RubricResult retried = callLLMAndParse(retryPrompt);
            if (RubricResultValidator.validate(retried).isEmpty()) {
                result = retried;
            } else {
                log.error("重试后仍不合规，保留首次结果");
            }
        }
```

补充 `import java.util.Optional;`。

- [ ] **Step 6: 编译并跑评估包全部测试**

Run: `mvn -q -Dtest='RubricResultValidatorTest,DeterministicScorerTest,EvalReportTest,BaselineManagerTest' test`
Expected: PASS

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/evaluation/RubricResultValidator.java \
        src/main/java/com/qian/qianaiagent/evaluation/RubricScorer.java \
        src/test/java/com/qian/qianaiagent/evaluation/RubricResultValidatorTest.java
git commit -m "fix: 评分结果加语义校验，失败带原因重试一次"
```

---

## Task 6: ActiveSpecManager TTL 与接线

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/interview/progress/ActiveSpecManager.java`
- Modify: `src/main/java/com/qian/qianaiagent/controller/ConversationController.java:118-130`
- Test: `src/test/java/com/qian/qianaiagent/interview/progress/ActiveSpecManagerEvictionTest.java`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/qian/qianaiagent/interview/progress/ActiveSpecManagerEvictionTest.java`：

```java
package com.qian.qianaiagent.interview.progress;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveSpecManagerEvictionTest {

    @Test
    void evictsEntriesIdleBeyondTtl() {
        ActiveSpecManager manager = new ActiveSpecManager();
        manager.updateSpec("stale", "旧项目描述");
        manager.updateSpec("fresh", "新项目描述");

        // 让 fresh 重新活跃一次
        manager.getSpec("fresh");

        long future = System.currentTimeMillis() + Duration.ofHours(3).toMillis();
        // stale 与 fresh 都在 3 小时前更新过，但 fresh 刚被访问过 —— 两者都会过期。
        // 这里只验证「超过 TTL 必被清理」这一条。
        int evicted = manager.evictExpired(future, Duration.ofHours(2));

        assertEquals(2, evicted);
        assertFalse(manager.hasSpec("stale"));
        assertFalse(manager.hasSpec("fresh"));
    }

    @Test
    void keepsEntriesWithinTtl() {
        ActiveSpecManager manager = new ActiveSpecManager();
        manager.updateSpec("recent", "项目描述");

        int evicted = manager.evictExpired(System.currentTimeMillis(), Duration.ofHours(2));

        assertEquals(0, evicted);
        assertTrue(manager.hasSpec("recent"));
    }

    @Test
    void removeClearsSingleEntry() {
        ActiveSpecManager manager = new ActiveSpecManager();
        manager.updateSpec("c1", "描述");
        manager.remove("c1");
        assertFalse(manager.hasSpec("c1"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -Dtest=ActiveSpecManagerEvictionTest test`
Expected: 编译失败 — `evictExpired` 不存在

- [ ] **Step 3: 实现 lastAccess 与清理**

修改 `src/main/java/com/qian/qianaiagent/interview/progress/ActiveSpecManager.java`。

替换字段声明与相关方法：

```java
    /** 每个会话 -> 当前生效的项目描述 + 最后访问时间 */
    private final Map<String, Entry> activeSpecs = new ConcurrentHashMap<>();

    /** 空闲多久后清理。对齐 QuizApp.evictExpiredEntries 的既有口径。 */
    private static final Duration IDLE_TTL = Duration.ofHours(2);

    private record Entry(String spec, long lastAccess) {
    }

    /**
     * 更新或覆盖当前项目描述（天然覆盖语义）
     */
    public void updateSpec(String chatId, String spec) {
        if (chatId == null || spec == null || spec.isBlank()) {
            return;
        }
        Entry old = activeSpecs.put(chatId, new Entry(spec.trim(), System.currentTimeMillis()));
        if (old != null) {
            log.info("🔄 项目描述已覆盖: chatId={}, oldLen={}, newLen={}",
                    chatId, old.spec().length(), spec.length());
        } else {
            log.info("📋 新项目描述已设置: chatId={}, specLen={}", chatId, spec.length());
        }
    }

    /**
     * 获取当前项目描述，用于场景不关心是否有描述时
     */
    public String getSpec(String chatId) {
        Entry entry = activeSpecs.get(chatId);
        if (entry == null) {
            return null;
        }
        touch(chatId, entry);
        return entry.spec();
    }

    /** 续期 —— 访问过就不算空闲。 */
    private void touch(String chatId, Entry entry) {
        activeSpecs.replace(chatId, entry, new Entry(entry.spec(), System.currentTimeMillis()));
    }

    /**
     * 清理空闲超过 {@code ttl} 的条目。
     * <p>
     * 抽成纯方法（注入 {@code now}）以便单测，<b>不</b>把 {@code System.currentTimeMillis()}
     * 写死在内部 —— 那会让 2 小时的等待变成测试不可承受的成本。
     *
     * @return 实际清理的条目数
     */
    public int evictExpired(long now, Duration ttl) {
        if (ttl == null || ttl.isNegative()) {
            return 0;
        }
        long cutoff = now - ttl.toMillis();
        List<String> expired = activeSpecs.entrySet().stream()
                .filter(e -> e.getValue().lastAccess() < cutoff)
                .map(Map.Entry::getKey)
                .toList();
        expired.forEach(activeSpecs::remove);
        if (!expired.isEmpty()) {
            log.info("🧹 清理空闲项目描述 {} 个", expired.size());
        }
        return expired.size();
    }

    /**
     * 定时清理空闲条目。
     */
    @Scheduled(fixedDelayString = "${qian.memory.active-spec.evict-interval-ms:1800000}")
    public void evictExpiredScheduled() {
        evictExpired(System.currentTimeMillis(), IDLE_TTL);
    }
```

`buildSpecPrompt` 与 `hasSpec` 改为基于 `Entry`：

```java
    public String buildSpecPrompt(String chatId) {
        String spec = getSpec(chatId);
        if (spec == null || spec.isBlank()) {
            return "";
        }
        return """

                📋 【当前项目描述】（唯一有效版本）
                以下为用户最新的项目描述，对话历史中的旧项目描述已过时，请完全忽略。
                但用户之前回答过的知识点仍视为已掌握，禁止重复出题。

                %s
                """.formatted(spec);
    }

    public boolean hasSpec(String chatId) {
        Entry entry = activeSpecs.get(chatId);
        return entry != null && !entry.spec().isBlank();
    }
```

`remove` 保持不变。

补充 import：

```java
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Duration;
import java.util.List;
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -Dtest=ActiveSpecManagerEvictionTest test`
Expected: PASS

- [ ] **Step 5: 接到会话删除路径**

修改 `src/main/java/com/qian/qianaiagent/controller/ConversationController.java`。

注入 `ActiveSpecManager`（与既有注入风格一致）：

```java
    @Resource
    private ActiveSpecManager activeSpecManager;
```

在 `deleteConversation` 的成功分支中，删除外层记忆后一并清理 spec：

```java
        boolean deleted = fileBasedChatMemory.deleteConversation(chatId);
        if (deleted) {
            // 🔴 项目描述是独立的会话级状态，不清理会在内存里永久驻留
            activeSpecManager.remove(chatId);
        }
        return ResponseEntity.ok(Map.of("success", deleted, "chatId", chatId));
```

补充 import：`import com.qian.qianaiagent.interview.progress.ActiveSpecManager;`

- [ ] **Step 6: 编译并跑相关测试**

Run: `mvn -q -Dtest='ActiveSpecManagerEvictionTest,ConversationOwnershipTest' test`
Expected: PASS

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/interview/progress/ActiveSpecManager.java \
        src/main/java/com/qian/qianaiagent/controller/ConversationController.java \
        src/test/java/com/qian/qianaiagent/interview/progress/ActiveSpecManagerEvictionTest.java
git commit -m "fix: ActiveSpecManager 加空闲清理并接到会话删除路径"
```

---

## Task 7: 死代码清理

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/ability/UserAbilityService.java`
- Modify: `src/main/java/com/qian/qianaiagent/interview/progress/AskedPointTracker.java`
- Modify: `src/main/java/com/qian/qianaiagent/graph/service/ConversationAnalysisService.java:271-276`

- [ ] **Step 1: 再次确认零引用（不得跳过）**

Run:
```bash
grep -rn "saveToUserFile\|saveToUserRedis\|deleteUserProfileFile\|deleteFromUserRedis" src/main src/test --include=*.java
```
Expected: **只**匹配到 `UserAbilityService.java` 中的方法定义本身。
若出现任何调用点，**停止本任务**并报告 —— 说明它并非死代码。

Run:
```bash
grep -rn "hydrateAsked" src/main src/test --include=*.java
```
Expected: **只**匹配到 `AskedPointTracker.java:56` 的定义。
测试文件若引用它，改为一并删除该测试方法（保留测试类中其余用例）。

- [ ] **Step 2: 检查连带孤儿**

Run:
```bash
grep -rn "userProfilePath" src/main --include=*.java
```
Expected: 记下所有调用点。若删除上面 4 个方法后 `userProfilePath` 再无调用者，在 Step 3 中一并删除。

- [ ] **Step 3: 删除 UserAbilityService 的 4 个死方法**

删除 `saveToUserFile`、`saveToUserRedis`、`deleteUserProfileFile`、`deleteFromUserRedis`
四个 `private` 方法整体（含各自的 `try/catch` 块）。
若 Step 2 确认 `userProfilePath` 成为孤儿，一并删除。

- [ ] **Step 4: 删除 hydrateAsked 并标注 AskedPointTracker**

删除 `AskedPointTracker.hydrateAsked` 方法整体，并在类 javadoc 追加：

```java
/**
 * ...
 *
 * <p>⚠️ <b>当前无生产调用者</b>（仅 {@code AskedPointTrackerTest} 引用）。
 * 保留原因：追问配额是完整实现的功能，等待接入出题链路；
 * 若确定不再需要，可连同测试一并删除。
 */
```

- [ ] **Step 5: 标注 findPreviousAssistant**

在 `ConversationAnalysisService.findPreviousAssistant` 的 javadoc 上追加说明，
**不删除**（属图谱链路大件，按约定只标注）：

```java
    /**
     * 找上一条 AI 回复（作为 REPLIES_TO 的目标）。
     *
     * <p>⚠️ <b>当前恒返回 null</b> —— 是占位实现。因此 {@code REPLIES_TO} 关系实际
     * 从未建立，依赖它的 {@code analyzeFollowUpDepth} 查询会返回空结果。
     * 保留原因：图谱链路整体尚未接入对话主流程（见 {@code ConversationGraphAdvisor}），
     * 待接入时一并实现。
     */
```

- [ ] **Step 6: 编译与全量测试**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS

Run: `mvn -q test`
Expected: BUILD SUCCESS，无新增失败

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/ability/UserAbilityService.java \
        src/main/java/com/qian/qianaiagent/interview/progress/AskedPointTracker.java \
        src/main/java/com/qian/qianaiagent/graph/service/ConversationAnalysisService.java
git commit -m "chore: 清理零调用死代码，标注未接线实现"
```

---

## 收尾验证

- [ ] **全量测试**

Run: `mvn -q test`
Expected: BUILD SUCCESS

- [ ] **确认练习代码未被触碰**

Run:
```bash
git diff --stat HEAD~7 -- src/main/java/com/qian/qianaiagent/aspect/ \
    src/main/java/com/qian/qianaiagent/cache/SemanticCacheService.java
```
Expected: **无输出** —— `RateLimitAspect`、`OperationLogAspect`、`SemanticCacheService` 一行未动

- [ ] **确认 RubricScorer 的练习方法体未变**

Run:
```bash
git log -p --follow -- src/main/java/com/qian/qianaiagent/evaluation/RubricScorer.java | grep -c "callLLMAndParse"
```
人工确认：`callLLMAndParse` 的**方法体**（调 LLM → 清理 markdown → Jackson 解析）
在本次改动中保持原样，只在 `score()` 的调用处新增了包装。

---

## 已知残留（本次不处理）

1. **写侧越权 TOCTOU** —— 见 `FileBasedChatMemory` 类注释。需让 chatId 不可预测，属独立课题。
2. **跨实例状态不一致** —— 各 Service 的 per-chatId 内存状态仍是 JVM 内的。定位是「单机 + 跨进程防损坏」。
3. **记忆只能单机存储** —— 作者 `disadvantage` 笔记所记，需引入 Redis/DB 持久化的独立规划。
4. **`RateLimitAspect` 空转** —— Task 13 练习，`UserController` 登录防爆破当前**不生效**。
   若决定保留为练习，建议在生产部署前单独处理。
