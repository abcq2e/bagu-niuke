# 执行链路与工具层缺陷加固 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `WebScrapingTool` / `ResourceDownloadTool` 补上 SSRF 与路径穿越防护，统一评分不可信标记，并同步文档、标注失效注解。

**Architecture:** 新增 `util/UrlSafetyValidator`（与既有 `SafePathResolver` 并列，收拢 URL 安全校验的单一实现）供两个工具复用；`ResourceDownloadTool` 的 `fileName` 复用现成的 `SafePathResolver.resolveWithin`。评分标记走「加字段 + 消费点告警、不改落盘」的最小闭环。全部改动 TDD 驱动，逐任务提交。

**Tech Stack:** Java 17 / Spring Boot / Spring AI / Jsoup 1.19.1 / Hutool 5.8.37 / JUnit 5 / Mockito / Lombok

**设计依据:** `docs/superpowers/specs/2026-09-19-execution-control-hardening-design.md`

**重要前提（贯穿全程）：**

- 本项目有 30 处 `🎯 Task N` 是作者自设练习，**一律不改**。本计划**不触碰** `RateLimitAspect` 的任何逻辑行（Task 6 只加注释）。
- 两个 Tool 的错误返回前缀 `"Error scraping web page: "` / `"Error downloading resource: "` 被
  `ToolCallAgent.isFailureResponse()`（`ToolCallAgent.java:204-212`）依赖，用于驱动失败计数与自愈循环。
  **任何情况下不得修改这两个前缀。**
- 测试命令在 Windows + Git Bash 下执行。若 `./mvnw` 不可用，改用 `mvn`（等价）。

---

### Task 1: 新增 `UrlSafetyValidator`

**Files:**
- Create: `src/main/java/com/qian/qianaiagent/util/UrlSafetyValidator.java`
- Test: `src/test/java/com/qian/qianaiagent/util/UrlSafetyValidatorTest.java`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/qian/qianaiagent/util/UrlSafetyValidatorTest.java`：

```java
package com.qian.qianaiagent.util;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlSafetyValidatorTest {

    private void assertRejected(String url) {
        assertThrows(IllegalArgumentException.class,
                () -> UrlSafetyValidator.validate(url),
                "应拒绝: " + url);
    }

    // ===== IPv4 私有 / 保留网段 =====

    @Test
    void rejectsLoopback() {
        assertRejected("http://127.0.0.1/admin");
        assertRejected("http://127.1.2.3/");
    }

    @Test
    void rejectsPrivateClassA() {
        assertRejected("http://10.0.0.1/");
    }

    @Test
    void rejectsPrivateClassB() {
        assertRejected("http://172.16.0.1/");
        assertRejected("http://172.31.255.254/");
    }

    @Test
    void rejectsPrivateClassC() {
        assertRejected("http://192.168.1.1/");
    }

    @Test
    void rejectsCloudMetadataEndpoint() {
        // 169.254.169.254 是云厂商元数据服务，SSRF 的首要目标
        assertRejected("http://169.254.169.254/latest/meta-data/");
        assertRejected("http://169.254.1.1/");
    }

    @Test
    void rejectsCarrierGradeNatAndBenchmark() {
        assertRejected("http://100.64.0.1/");
        assertRejected("http://198.18.0.1/");
    }

    @Test
    void rejectsUnspecifiedAndMulticast() {
        assertRejected("http://0.0.0.0/");
        assertRejected("http://224.0.0.1/");
        assertRejected("http://240.0.0.1/");
    }

    // ===== IPv6 =====

    @Test
    void rejectsIpv6Loopback() {
        assertRejected("http://[::1]/");
    }

    @Test
    void rejectsIpv4MappedIpv6() {
        // ::ffff:127.0.0.1 必须拆出内嵌 IPv4 后判定，否则是绕过路径
        assertRejected("http://[::ffff:127.0.0.1]/");
        assertRejected("http://[::ffff:192.168.0.1]/");
    }

    @Test
    void rejectsIpv6UniqueLocalAndLinkLocal() {
        assertRejected("http://[fd00::1]/");
        assertRejected("http://[fe80::1]/");
    }

    // ===== 协议与形态 =====

    @Test
    void rejectsNonHttpSchemes() {
        assertRejected("file:///etc/passwd");
        assertRejected("ftp://example.com/x");
        assertRejected("jar:file:/tmp/a.jar!/x");
    }

    @Test
    void rejectsBlankNullAndMalformed() {
        assertRejected(null);
        assertRejected("   ");
        assertRejected("http://");
        assertRejected("not a url at all");
    }

    @Test
    void rejectsUnresolvableHost() {
        // 无法解析 → 保守拒绝，不静默放行（与 SafePathResolver 对悬空链接的处理同源）
        assertRejected("http://this-host-should-not-exist-qian.invalid/");
    }

    @Test
    void rejectsLocalhostByName() {
        // localhost 经 DNS 解析到 127.0.0.1，必须被同一规则拦下
        assertRejected("http://localhost:8080/admin");
    }

    // ===== 合法公网 URL =====

    @Test
    void acceptsPublicUrl() {
        Assumptions.assumeTrue(dnsResolvable("example.com"), "需要 DNS，已跳过");
        URI uri = UrlSafetyValidator.validate("https://example.com/path?q=1");
        assertEquals("example.com", uri.getHost());
        assertEquals("https", uri.getScheme());
    }

    private static boolean dnsResolvable(String host) {
        try {
            InetAddress.getAllByName(host);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `./mvnw test -Dtest=UrlSafetyValidatorTest -DfailIfNoTests=false`

Expected: 编译失败，报 `找不到符号: 类 UrlSafetyValidator`

- [ ] **Step 3: 实现**

创建 `src/main/java/com/qian/qianaiagent/util/UrlSafetyValidator.java`：

```java
package com.qian.qianaiagent.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * 把 LLM / 用户提供的 URL 校验为「可安全发起外发请求」—— 全项目唯一一份。
 *
 * <h2>为什么必须收拢</h2>
 * 与 {@link SafePathResolver} 同源的问题：校验一旦散落成多份 local 副本，
 * 必然出现「有的地方有、有的地方没有」。
 * {@code WebScrapingTool} 与 {@code ResourceDownloadTool} 的入参均来自 LLM 输出（不可信），
 * 此前两者都未做任何 URL 校验 —— LLM 传入 {@code http://169.254.169.254/latest/meta-data/}
 * 即可让服务端去读取云元数据。
 *
 * <h2>已知局限（不粉饰）</h2>
 * 本校验<b>挡不住 DNS rebinding</b>：校验时刻解析到的 IP 与真正建连时刻解析到的 IP
 * 之间存在时间窗，攻击者可让域名先解析为公网地址、建连时再指向内网。
 * 彻底防住需在 socket 层校验对端 IP（自定义 {@code SocketFactory}），成本显著。
 * 本层挡的是「直接传入内网 URL」这类现实攻击路径。
 *
 * <p>注意：校验通过<b>不等于</b>可以跟随重定向 —— 见 {@code WebScrapingTool} 的
 * {@code followRedirects(false)}，跳转发生在校验之后。
 */
public final class UrlSafetyValidator {

    private UrlSafetyValidator() {
    }

    /**
     * 校验 URL 并返回解析后的 {@link URI}，调用方应使用返回值建连，不要再用原始字符串。
     *
     * @throws IllegalArgumentException URL 为空、协议非 http/https、缺主机名、
     *                                  DNS 无法解析、或解析结果落在内网 / 保留网段
     */
    public static URI validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URL 无法解析: " + url, e);
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("只允许 http/https 协议: " + url);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL 缺少主机名: " + url);
        }
        // IPv6 字面量在 URI 中带方括号，交给 InetAddress 前必须去掉
        String bare = stripBrackets(host);
        InetAddress[] addresses;
        try {
            // 字面 IP 与域名统一走这里，无需分支
            addresses = InetAddress.getAllByName(bare);
        } catch (UnknownHostException e) {
            // 无法解析 → 保守拒绝，不猜
            throw new IllegalArgumentException("URL 主机无法解析: " + host);
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                throw new IllegalArgumentException(
                        "URL 指向内网或保留地址，已拒绝: " + host + " -> " + address.getHostAddress());
            }
        }
        return uri;
    }

    private static String stripBrackets(String host) {
        if (host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static boolean isBlocked(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isBlockedIpv4(bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF, bytes[3] & 0xFF);
        }
        if (bytes.length == 16) {
            // IPv4-mapped（::ffff:a.b.c.d）：JVM 通常已归一为 Inet4Address，
            // 这里兜住未归一的情形 —— 否则是一条绕过路径
            if (isIpv4Mapped(bytes)) {
                return isBlockedIpv4(bytes[12] & 0xFF, bytes[13] & 0xFF, bytes[14] & 0xFF, bytes[15] & 0xFF);
            }
            return isBlockedIpv6(bytes);
        }
        return true;    // 未知形态，保守拒绝
    }

    private static boolean isIpv4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
    }

    private static boolean isBlockedIpv4(int a, int b, int c, int d) {
        if (a == 0) return true;                                    // 0.0.0.0/8
        if (a == 10) return true;                                   // 10.0.0.0/8
        if (a == 127) return true;                                  // 127.0.0.0/8 回环
        if (a == 169 && b == 254) return true;                      // 169.254.0.0/16 链路本地（含云元数据）
        if (a == 172 && b >= 16 && b <= 31) return true;            // 172.16.0.0/12
        if (a == 192 && b == 168) return true;                      // 192.168.0.0/16
        if (a == 192 && b == 0 && c == 0) return true;              // 192.0.0.0/24
        if (a == 100 && b >= 64 && b <= 127) return true;           // 100.64.0.0/10 运营商级 NAT
        if (a == 198 && (b == 18 || b == 19)) return true;          // 198.18.0.0/15 基准测试
        return a >= 224;                                            // 224.0.0.0/4 组播 + 240.0.0.0/4 保留
    }

    private static boolean isBlockedIpv6(byte[] b) {
        boolean allZeroExceptLast = true;
        for (int i = 0; i < 15; i++) {
            if (b[i] != 0) {
                allZeroExceptLast = false;
                break;
            }
        }
        if (allZeroExceptLast && b[15] == 1) return true;           // ::1 回环
        boolean allZero = true;
        for (byte v : b) {
            if (v != 0) {
                allZero = false;
                break;
            }
        }
        if (allZero) return true;                                   // :: 未指定
        int first = b[0] & 0xFF;
        if ((first & 0xFE) == 0xFC) return true;                    // fc00::/7 唯一本地
        return first == 0xFE && (b[1] & 0xC0) == 0x80;              // fe80::/10 链路本地
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `./mvnw test -Dtest=UrlSafetyValidatorTest -DfailIfNoTests=false`

Expected: `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`
（无外网时 `acceptsPublicUrl` 计为 Skipped，属预期）

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/util/UrlSafetyValidator.java \
        src/test/java/com/qian/qianaiagent/util/UrlSafetyValidatorTest.java
git commit -m "$(cat <<'EOF'
feat: 新增 UrlSafetyValidator，收拢 URL 安全校验

WebScrapingTool 与 ResourceDownloadTool 的入参均来自 LLM（不可信）却
未做任何 URL 校验，LLM 传入 http://169.254.169.254/latest/meta-data/
即可让服务端读取云元数据。

与 SafePathResolver 同源的做法：收拢成全项目唯一一份，避免校验散落
成多份后出现「有的地方有、有的地方没有」。覆盖 IPv4/IPv6 私有与保留
网段，含 ::ffff:127.0.0.1 这类 IPv4-mapped 绕过路径。

已知局限：挡不住 DNS rebinding（校验与建连之间存在时间窗），已在类
注释中明确记录。

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: `WebScrapingTool` 加固

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/tools/WebScrapingTool.java`
- Test: `src/test/java/com/qian/qianaiagent/tools/WebScrapingToolTest.java`

- [ ] **Step 1: 写失败测试**

把 `src/test/java/com/qian/qianaiagent/tools/WebScrapingToolTest.java` **整体替换**为：

```java
package com.qian.qianaiagent.tools;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebScrapingToolTest {

    // ===== 新增：SSRF 防护（纯本地，不发起任何网络请求）=====

    @Test
    void rejectsLoopbackUrlBeforeAnyRequest() {
        String result = new WebScrapingTool().scrapeWebPage("http://127.0.0.1:8080/admin");
        // 前缀必须保留：ToolCallAgent.isFailureResponse() 依赖 "Error" 前缀驱动自愈计数
        assertTrue(result.startsWith("Error scraping web page:"), result);
        assertTrue(result.contains("内网"), result);
    }

    @Test
    void rejectsCloudMetadataUrl() {
        String result = new WebScrapingTool()
                .scrapeWebPage("http://169.254.169.254/latest/meta-data/");
        assertTrue(result.startsWith("Error scraping web page:"), result);
    }

    @Test
    void rejectsFileScheme() {
        String result = new WebScrapingTool().scrapeWebPage("file:///etc/passwd");
        assertTrue(result.startsWith("Error scraping web page:"), result);
    }

    // ===== 改造：原测试只断言 assertNotNull，"Error..." 也算通过（假绿）=====

    @Test
    void scrapePublicPage() {
        Assumptions.assumeTrue(dnsResolvable("www.codefather.cn"), "需要外网，已跳过");
        String result = new WebScrapingTool().scrapeWebPage("https://www.codefather.cn");
        assertNotNull(result);
        // 断言正常站点未被内网规则误伤 —— 这是加固后最可能的回归
        assertFalse(result.contains("内网"), result);
        assertFalse(result.startsWith("Error scraping web page:"), result);
    }

    private static boolean dnsResolvable(String host) {
        try {
            InetAddress.getAllByName(host);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `./mvnw test -Dtest=WebScrapingToolTest -DfailIfNoTests=false`

Expected: `rejectsLoopbackUrlBeforeAnyRequest` 等 3 个用例 FAIL —— 当前实现无校验，
会真的去连 `127.0.0.1:8080` 并返回 `Error scraping web page: Connection refused`，
其中 `contains("内网")` 断言失败

- [ ] **Step 3: 实现**

把 `src/main/java/com/qian/qianaiagent/tools/WebScrapingTool.java` **整体替换**为：

```java
package com.qian.qianaiagent.tools;

import com.qian.qianaiagent.util.UrlSafetyValidator;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.URI;

/**
 * 网页抓取工具
 *
 * <p>⚠️ 错误返回前缀 {@code "Error scraping web page: "} 与
 * {@code ToolCallAgent.isFailureResponse()} 耦合，不得改动。
 */
public class WebScrapingTool {

    /** 单次抓取超时（毫秒） */
    private static final int TIMEOUT_MS = 10_000;

    /** 响应体上限 2MB，防止一个无限流页面把内存吃光 */
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    @Tool(description = "抓取指定网页的 HTML 内容。使用时机：WebSearchTool 找到相关网页后，需要深入阅读具体内容时。不使用时机：URL 不可达或网页需要登录认证时。")
    public String scrapeWebPage(@ToolParam(description = "要抓取的网页 URL，例如：'https://docs.spring.io/spring-ai/reference/'") String url) {
        try {
            // 校验必须在发起请求之前；后续一律使用返回值建连
            URI safe = UrlSafetyValidator.validate(url);
            Document document = Jsoup.connect(safe.toString())
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    // 🔴 不可省：校验发生在发请求之前，跳转发生在拿到响应之后。
                    //    若允许跟随，外部 URL 返回 302 指向 127.0.0.1 即可绕过校验
                    .followRedirects(false)
                    .get();
            return document.html();
        } catch (Exception e) {
            return "Error scraping web page: " + e.getMessage();
        }
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `./mvnw test -Dtest=WebScrapingToolTest -DfailIfNoTests=false`

Expected: `Tests run: 4, Failures: 0, Errors: 0`，`BUILD SUCCESS`
（无外网时 `scrapePublicPage` 计为 Skipped）

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/tools/WebScrapingTool.java \
        src/test/java/com/qian/qianaiagent/tools/WebScrapingToolTest.java
git commit -m "$(cat <<'EOF'
fix: WebScrapingTool 加 SSRF 防护与资源上限

入参来自 LLM 输出（不可信），此前直接交给 Jsoup，可被诱导去请求
127.0.0.1 或 169.254.169.254 等内网地址。

- URL 走 UrlSafetyValidator 校验
- 10s 超时 + 2MB 响应体上限
- followRedirects(false)：校验在发请求前、跳转在拿响应后，
  允许跟随则 302 指向内网即可绕过校验

顺带把原测试的 assertNotNull 断言收紧 —— 它对 "Error..." 返回值同样
通过，属假绿，抓不住「正常站点被内网规则误伤」这一回归。

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: `ResourceDownloadTool` 加固

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/tools/ResourceDownloadTool.java`
- Test: `src/test/java/com/qian/qianaiagent/tools/ResourceDownloadToolTest.java`

- [ ] **Step 1: 写失败测试**

把 `src/test/java/com/qian/qianaiagent/tools/ResourceDownloadToolTest.java` **整体替换**为：

```java
package com.qian.qianaiagent.tools;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceDownloadToolTest {

    private static final String PUBLIC_URL = "https://www.codefather.cn/logo.png";

    // ===== 新增：路径穿越（纯本地，不发起任何网络请求）=====

    @Test
    void rejectsParentTraversalFileName() {
        String result = new ResourceDownloadTool()
                .downloadResource(PUBLIC_URL, "../../evil.png");
        assertTrue(result.startsWith("Error downloading resource:"), result);
    }

    @Test
    void rejectsAbsolutePathFileName() {
        String result = new ResourceDownloadTool()
                .downloadResource(PUBLIC_URL, "C:/Windows/evil.png");
        assertTrue(result.startsWith("Error downloading resource:"), result);
    }

    @Test
    void rejectsNestedTraversalFileName() {
        String result = new ResourceDownloadTool()
                .downloadResource(PUBLIC_URL, "sub/../../evil.png");
        assertTrue(result.startsWith("Error downloading resource:"), result);
    }

    // ===== 新增：SSRF（纯本地）=====

    @Test
    void rejectsLoopbackUrl() {
        String result = new ResourceDownloadTool()
                .downloadResource("http://127.0.0.1:8080/x.png", "x.png");
        assertTrue(result.startsWith("Error downloading resource:"), result);
        assertTrue(result.contains("内网"), result);
    }

    @Test
    void rejectsCloudMetadataUrl() {
        String result = new ResourceDownloadTool()
                .downloadResource("http://169.254.169.254/latest/meta-data/", "x.png");
        assertTrue(result.startsWith("Error downloading resource:"), result);
    }

    // ===== 改造：原测试只断言 assertNotNull，"Error..." 也算通过（假绿）=====

    @Test
    public void testDownloadResource() {
        Assumptions.assumeTrue(dnsResolvable("www.codefather.cn"), "需要外网，已跳过");
        String result = new ResourceDownloadTool().downloadResource(PUBLIC_URL, "logo.png");
        assertNotNull(result);
        assertFalse(result.startsWith("Error downloading resource:"), result);
        assertTrue(result.startsWith("Resource downloaded successfully"), result);
    }

    private static boolean dnsResolvable(String host) {
        try {
            InetAddress.getAllByName(host);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

> 说明：三个路径穿越用例传入的是合法公网 URL，而实现中 `fileName` 的校验
> 排在 URL 校验**之前**（见 Step 3）—— 因此它们在无外网环境下同样能真实覆盖
> 路径穿越防护，不会因 DNS 失败而假绿。

- [ ] **Step 2: 运行测试，确认失败**

Run: `./mvnw test -Dtest=ResourceDownloadToolTest -DfailIfNoTests=false`

Expected: `rejectsParentTraversalFileName` 与 `rejectsLoopbackUrl` FAIL ——
当前实现无任何校验，路径穿越用例会真的尝试下载并返回成功信息

- [ ] **Step 3: 实现**

把 `src/main/java/com/qian/qianaiagent/tools/ResourceDownloadTool.java` **整体替换**为：

```java
package com.qian.qianaiagent.tools;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.qian.qianaiagent.constant.FileConstant;
import com.qian.qianaiagent.util.SafePathResolver;
import com.qian.qianaiagent.util.UrlSafetyValidator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 资源下载工具
 *
 * <p>⚠️ 错误返回前缀 {@code "Error downloading resource: "} 与
 * {@code ToolCallAgent.isFailureResponse()} 耦合，不得改动。
 */
public class ResourceDownloadTool {

    /** 单次下载超时（毫秒） */
    private static final int TIMEOUT_MS = 60_000;

    /** 单文件上限 100MB —— 与工具描述中声明的上限保持一致 */
    private static final long MAX_DOWNLOAD_BYTES = 100L * 1024 * 1024;

    private static final int BUFFER_SIZE = 8192;

    @Tool(description = "从指定 URL 下载资源文件。使用时机：用户需要下载图片、文档、或其他静态资源时。不使用时机：URL 需要认证、资源过大（>100MB）时。")
    public String downloadResource(@ToolParam(description = "资源 URL，例如：'https://example.com/chart.png'") String url, @ToolParam(description = "保存的文件名，例如：'chart.png'、'report.pdf'") String fileName) {
        try {
            // 🔴 两个入参都不可信：URL 来自 LLM，fileName 同样来自 LLM。
            //    此前 fileName 未校验即拼进路径，传入 ../../ 可写到任意位置。
            //    先校验 fileName：它是纯本地判断、不依赖网络，能更早拒绝路径穿越，
            //    也让该层防护可被「无外网环境」下的测试真实覆盖 —— 若先校验 URL，
            //    无网时 DNS 解析失败会提前抛错，路径穿越用例会因错误的原因通过
            Path target = SafePathResolver.resolveWithin(
                    Path.of(FileConstant.FILE_SAVE_DIR, "download"), fileName);
            URI safe = UrlSafetyValidator.validate(url);

            Files.createDirectories(target.getParent());

            long written = 0;
            boolean tooLarge = false;
            // HttpResponse 必须关闭，否则连接泄漏
            try (HttpResponse response = HttpUtil.createGet(safe.toString())
                    .timeout(TIMEOUT_MS)
                    .execute();
                 InputStream in = response.bodyStream();
                 OutputStream out = Files.newOutputStream(target)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    written += read;
                    if (written > MAX_DOWNLOAD_BYTES) {
                        tooLarge = true;
                        break;
                    }
                    out.write(buffer, 0, read);
                }
            }

            if (tooLarge) {
                // 流已随 try-with-resources 关闭，此处可安全删除半成品
                Files.deleteIfExists(target);
                return "Error downloading resource: 文件超过 "
                        + (MAX_DOWNLOAD_BYTES / 1024 / 1024) + "MB 上限，已中止";
            }
            return "Resource downloaded successfully to: " + target;
        } catch (Exception e) {
            return "Error downloading resource: " + e.getMessage();
        }
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `./mvnw test -Dtest=ResourceDownloadToolTest -DfailIfNoTests=false`

Expected: `Tests run: 6, Failures: 0, Errors: 0`，`BUILD SUCCESS`
（无外网时 `testDownloadResource` 计为 Skipped）

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/tools/ResourceDownloadTool.java \
        src/test/java/com/qian/qianaiagent/tools/ResourceDownloadToolTest.java
git commit -m "$(cat <<'EOF'
fix: ResourceDownloadTool 修复路径穿越并加 SSRF 防护

fileName 此前未做任何校验即拼进路径（fileDir + "/" + fileName），
而入参由 LLM 生成 —— 传入 ../../ 可写到任意位置，是本轮最直接的漏洞。

- fileName 复用 SafePathResolver.resolveWithin（三层防护）
- url 走 UrlSafetyValidator
- 改流式下载 + 累计字节计数，超 100MB 中止并删除半成品
  （HttpUtil.downloadFile 无法限制体积）
- 60s 超时；HttpResponse 用 try-with-resources 关闭防连接泄漏

顺带把原测试的 assertNotNull 断言收紧，理由同 WebScrapingToolTest。

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: 评分不可信标记 `parseFailed`

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/ability/UserAbilityProfile.java:485-501`
- Modify: `src/main/java/com/qian/qianaiagent/ability/UserAbilityService.java:1062-1079`（`parseScoreResult`）
- Modify: `src/main/java/com/qian/qianaiagent/ability/UserAbilityService.java:974`（消费点 1）
- Modify: `src/main/java/com/qian/qianaiagent/ability/UserAbilityService.java:1128`（消费点 2）
- Test: `src/test/java/com/qian/qianaiagent/ability/ScoreResultParseFailedTest.java`（新建）

> **⚠️ 测试陷阱（必读）**：`objectMapper` 在 `UserAbilityService` 中是 `@Resource` 字段注入
> （`UserAbilityService.java:68-69`），构造函数只设 `chatClient`。
> 因此 `new UserAbilityService(mock(ChatModel.class))` 之后 `objectMapper` 为 **null**，
> 直接调 `parseScoreResult` 会抛 NPE 并被 catch 吞掉 → 走 fallback →
> **"解析失败"的用例会因 NPE 而非 JSON 解析失败通过（假绿）**。
> 必须用 `ReflectionTestUtils.setField` 显式注入一个真实 `ObjectMapper`。

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/qian/qianaiagent/ability/ScoreResultParseFailedTest.java`：

```java
package com.qian.qianaiagent.ability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ScoreResultParseFailedTest {

    private UserAbilityService service;

    @BeforeEach
    void setUp() {
        service = new UserAbilityService(mock(ChatModel.class));
        // 🔴 必须显式注入：构造函数不设 objectMapper，不注入则解析路径会因 NPE
        //    而非 JSON 解析失败走到 fallback，测试会因错误的原因通过
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void parseFailedDefaultsToFalse() {
        assertFalse(new UserAbilityProfile.ScoreResult().isParseFailed());
    }

    @Test
    void unparsableJsonMarksParseFailed() {
        UserAbilityProfile.ScoreResult sr = ReflectionTestUtils.invokeMethod(
                service, "parseScoreResult", "这根本不是 JSON", "Java基础与集合");
        assertNotNull(sr);
        assertTrue(sr.isParseFailed(), "解析失败必须标记为不可信");
    }

    @Test
    void parsableJsonDoesNotMarkParseFailed() {
        UserAbilityProfile.ScoreResult sr = ReflectionTestUtils.invokeMethod(
                service, "parseScoreResult",
                "{\"score\":4,\"weakPoints\":[\"值传递\"]}", "Java基础与集合");
        assertNotNull(sr);
        assertFalse(sr.isParseFailed(), "解析成功不应标记为不可信");
    }

    @Test
    void emptyJsonObjectIsParsableButHasNoScore() {
        // 兜底默认 score=3（UserAbilityProfile.java:488），用于确认默认值与标记正交
        UserAbilityProfile.ScoreResult sr = ReflectionTestUtils.invokeMethod(
                service, "parseScoreResult", "{}", "JVM");
        assertNotNull(sr);
        assertFalse(sr.isParseFailed());
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `./mvnw test -Dtest=ScoreResultParseFailedTest -DfailIfNoTests=false`

Expected: 编译失败，报 `找不到符号: 方法 isParseFailed()`

- [ ] **Step 3: 加字段**

在 `src/main/java/com/qian/qianaiagent/ability/UserAbilityProfile.java` 的
`ScoreResult` 内、`originalTopic` 字段之后（约第 501 行）插入：

```java
        /**
         * LLM 评分输出不可信：JSON 无法解析，当前值来自兜底默认值。
         * <p>
         * ⚠️ 与 {@code RubricScorer.RubricResult.parseFailed}（另一条评分链路）同名但不同类，
         * 两者语义一致：LLM 原始输出不可信，当前值为兜底或首次结果。
         */
        private boolean parseFailed = false;
```

- [ ] **Step 4: 标记 fallback 分支**

修改 `src/main/java/com/qian/qianaiagent/ability/UserAbilityService.java` 的
`parseScoreResult`（第 1062-1079 行）。把 catch 块与 fallback 段替换为：

```java
        } catch (Exception e) {
            log.warn("评分 JSON 解析失败，使用默认值并标记为不可信: {}", e.getMessage());
        }
        UserAbilityProfile.ScoreResult fallback = new UserAbilityProfile.ScoreResult();
        fallback.setTopic(defaultTopic);
        fallback.setParseFailed(true);
        return fallback;
```

- [ ] **Step 5: 运行测试，确认通过**

Run: `./mvnw test -Dtest=ScoreResultParseFailedTest -DfailIfNoTests=false`

Expected: `Tests run: 4, Failures: 0, Errors: 0`，`BUILD SUCCESS`

- [ ] **Step 6: 在消费点 1 加告警（不改变行为）**

在 `src/main/java/com/qian/qianaiagent/ability/UserAbilityService.java` 第 974 行
`UserAbilityProfile.ScoreResult sr = parseScoreResult(result, topic);` 之后，
紧接着的 `if (sr != null) {` 之内、`getOrCreateProfile` 调用之前插入：

```java
                        if (sr.isParseFailed()) {
                            // 不可信评分照常落盘（避免用户答题数据丢失），但必须留下可检索的痕迹。
                            // 兜底 score=3 会让本应「答得好」的回答按非优秀处理（:974 要求 score>=4）
                            log.warn("⚠️ [不可信评分] 解析失败已用兜底值，画像计入可能偏低: chatId={}, topic={}",
                                    chatId, topic);
                        }
```

- [ ] **Step 7: 在消费点 2 加告警（不改变行为）**

在同一个文件第 1128 行
`UserAbilityProfile.ScoreResult sr = parseScoreResult(result, topic);` 之后，
紧接着的 `if (sr != null && sr.getScore() >= 4) {` 之内插入：

```java
                        if (sr.isParseFailed()) {
                            // 此处兜底 score=3 < 4，解析失败时不会误移除错题，落在安全侧
                            log.warn("⚠️ [不可信评分] 解析失败已用兜底值，本次不会移除错题: chatId={}, topic={}",
                                    chatId, topic);
                        }
```

- [ ] **Step 8: 运行相关测试与全量测试**

Run: `./mvnw test -Dtest='ScoreResultParseFailedTest,WeakPointRoutingTest,UserAbilityWrongBookTest' -DfailIfNoTests=false`

Expected: `BUILD SUCCESS`，无 Failures

Run: `./mvnw test`

Expected: `BUILD SUCCESS`（无外网时若干网络用例为 Skipped）

> ⚠️ 全量测试含 8 个 `@SpringBootTest`（`QianAiAgentApplicationTests` 等），会启动完整
> Spring 上下文，依赖 Redis、数据库与模型 key。**本机环境不完备时会出现与本次改动无关的失败。**
> 遇到这类失败：先 `git stash` 在干净工作区跑一次同一命令作为基线比对，
> 基线同样失败的即非本次引入。**不要为了让全量测试变绿而改动无关代码。**

- [ ] **Step 9: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/ability/UserAbilityProfile.java \
        src/main/java/com/qian/qianaiagent/ability/UserAbilityService.java \
        src/test/java/com/qian/qianaiagent/ability/ScoreResultParseFailedTest.java
git commit -m "$(cat <<'EOF'
fix: 评分解析失败标记 parseFailed 并接入消费点告警

parseScoreResult 此前解析失败时静默返回兜底对象（score=3），调用方无法
区分「真打了 3 分」与「解析失败」。对照另加的 RubricScorer.parseFailed
（0af4915），同样是写了没人读的标记。

本次取最小闭环：加标记 + 两处消费点告警，不改变落盘行为 ——
不可信时拒绝落盘会导致用户答题数据丢失，代价高于收益。

两处后果不同，告警文案据实区分：
- :1128 复习评分要求 score>=4，兜底 3 分不会误移除错题（安全侧）
- :974 常规评分的「答得好」判定同样要求 score>=4，兜底会让本应正向
  计入的回答按非优秀处理

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: `maxSteps` 文档与代码同步

**Files:**
- Modify: `docs/upgrade/tool-system-architecture.md:359`
- Modify: `docs/upgrade/tool-system-architecture.md:407`
- Modify: `docs/upgrade/面试考察点分析.md:921`
- Modify: `docs/upgrade/Agent包类设计详解.md:216`

代码实际值：`BaseAgent.java:47` 默认 `10`，`YuManus.java:82` 覆写为 `8`
（该行注释记录了 `20→8` 的调优历史）。

- [ ] **Step 1: 改 `tool-system-architecture.md:359`**

把：

```
│   maxSteps=20, 直到 FINISHED 或超限       │
```

改为：

```
│   maxSteps=8, 直到 FINISHED 或超限        │
```

- [ ] **Step 2: 改 `tool-system-architecture.md:407`**

把：

```
- **步数限制：** `maxSteps=20`，防止无限循环耗尽 token
```

改为：

```
- **步数限制：** `maxSteps=8`（`BaseAgent` 默认 10，`YuManus` 覆写为 8），防止无限循环耗尽 token
```

- [ ] **Step 3: 改 `面试考察点分析.md:921`**

把：

```
├── 设置 maxSteps = 20
```

改为：

```
├── 设置 maxSteps = 8
```

- [ ] **Step 4: 改 `Agent包类设计详解.md:216`**

把：

```
maxSteps: 20  // 比默认 10 步更宽容
```

改为：

```
maxSteps: 8  // 比默认 10 步更收敛
```

- [ ] **Step 5: 确认无遗漏**

Run: `grep -rn "maxSteps *= *20\|maxSteps=20\|maxSteps: 20" docs/`

Expected: 无输出（无残留）

- [ ] **Step 6: 提交**

```bash
git add docs/upgrade/tool-system-architecture.md docs/upgrade/面试考察点分析.md docs/upgrade/Agent包类设计详解.md
git commit -m "$(cat <<'EOF'
docs: 同步 maxSteps 文档与代码实际值

四处文档写 maxSteps=20，实际 BaseAgent 默认 10、YuManus 覆写为 8
（见 YuManus.java:82 注释记录的 20→8 调优历史）。

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: 标注 `@RateLimit` 当前不生效

**Files:**
- Modify: `src/main/java/com/qian/qianaiagent/annotation/RateLimit.java`（javadoc）
- Modify: `src/main/java/com/qian/qianaiagent/controller/AgentChatController.java:68`
- Modify: `src/main/java/com/qian/qianaiagent/controller/InterviewChatController.java:59,104`
- Modify: `src/main/java/com/qian/qianaiagent/controller/UserController.java:92-96`

> 🔴 **本任务只加注释，不得修改 `RateLimitAspect` 的任何代码行。**
> 该切面是作者自设的 Task 13 练习（见 2026-09-18 spec 第 1.1 节「这些一律不改」）。

- [ ] **Step 1: 在注解 javadoc 顶部加提示**

在 `src/main/java/com/qian/qianaiagent/annotation/RateLimit.java` 的类 javadoc
（第 6-21 行）中，把标题行：

```java
/**
 * 限流注解
```

改为：

```java
/**
 * 限流注解
 *
 * <p>🔴 <b>当前标注了也不生效</b>：切面 {@code RateLimitAspect} 是 Task 13 的练习骨架，
 * {@code handleRateLimit} 直接 {@code joinPoint.proceed()} 放行。全项目 4 处使用点
 * （{@code AgentChatController}、{@code InterviewChatController} 两处、{@code UserController}）
 * 均<b>没有任何限流保护</b>，其中包含登录防爆破。切勿据此认为已有防护。
```

- [ ] **Step 2: 标注 4 处使用点**

在 `AgentChatController.java` 第 68 行 `@RateLimit(maxRequests = 10, timeWindow = 60)` 上方插入：

```java
    // ⚠️ 该注解当前不生效，见 RateLimit javadoc（Task 13 练习未完成）
```

在 `InterviewChatController.java` 第 59 行与第 104 行的
`@RateLimit(maxRequests = 10, timeWindow = 60)` 上方各插入同样一行。

在 `UserController.java` 第 92-96 行那段注释中，把：

```java
    // ===== 🎯 Task 13: 加 @RateLimit 防暴力破解！=====
    // 60 秒内最多登录 5 次
    // 💡 想想 maxRequests 设多少合适？为什么不是 3 也不是 10？
```

改为：

```java
    // ===== 🎯 Task 13: 加 @RateLimit 防暴力破解！=====
    // 60 秒内最多登录 5 次
    // 💡 想想 maxRequests 设多少合适？为什么不是 3 也不是 10？
    // ⚠️ 提醒：切面未实现前该注解不生效，登录当前没有防爆破保护
```

- [ ] **Step 3: 确认未触碰切面**

Run: `git diff --stat src/main/java/com/qian/qianaiagent/aspect/RateLimitAspect.java`

Expected: 无输出（该文件零改动）

- [ ] **Step 4: 确认编译通过**

Run: `./mvnw -q compile`

Expected: `BUILD SUCCESS`（注释改动不影响编译）

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/qian/qianaiagent/annotation/RateLimit.java \
        src/main/java/com/qian/qianaiagent/controller/AgentChatController.java \
        src/main/java/com/qian/qianaiagent/controller/InterviewChatController.java \
        src/main/java/com/qian/qianaiagent/controller/UserController.java
git commit -m "$(cat <<'EOF'
docs: 标注 @RateLimit 当前不生效，消除假防护

RateLimitAspect 是 Task 13 练习骨架，handleRateLimit 直接放行，
4 处 @RateLimit 全部不生效 —— 其中 UserController:96 是登录防爆破。
这是「代码看起来有限流、实际没有」的危险形态。

维持 2026-09-18 spec「全部 🎯 Task N 一律不改」的决策，
只做可见化标注，不碰切面逻辑。

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## 完成标准

- [ ] 四个新增 / 改造的测试类全绿：`UrlSafetyValidatorTest`、`WebScrapingToolTest`、
      `ResourceDownloadToolTest`、`ScoreResultParseFailedTest`
- [ ] `./mvnw test` 无**本次引入**的失败。含 8 个 `@SpringBootTest`，环境不完备时
      会出现与本次改动无关的失败 —— 用 `git stash` 后的基线比对判定，不要去修它们
- [ ] `git diff --stat src/main/java/com/qian/qianaiagent/aspect/RateLimitAspect.java` 无输出
- [ ] `grep -rn "maxSteps *= *20" docs/` 无输出
- [ ] 两个 Tool 的错误前缀保持为 `"Error scraping web page: "` / `"Error downloading resource: "`

## 已知残留（不在本计划范围）

1. **DNS rebinding 未防住** —— 见 `UrlSafetyValidator` 类注释。
2. **`RateLimitAspect` 仍未生效** —— Task 6 只做标注，登录防爆破依旧无保护。
3. **`RubricScorer.parseFailed` 与 `ScoreResult.parseFailed` 未合并** —— 仅统一语义。
4. **`AskedPointTracker` 未接线** —— 维持既有决策。
