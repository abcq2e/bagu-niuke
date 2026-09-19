package com.qian.qianaiagent.util;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlSafetyValidatorTest {

    /** 网段规则的专属文案 —— 用来断言「确实是被网段规则拒绝」，而非被其他分支捡漏。 */
    private static final String BLOCKED_MSG = "内网";

    /** RFC 6761 保留、正常情况下必定解析失败的域名（见 rejectsUnresolvableHost 的守卫）。 */
    private static final String UNRESOLVABLE_HOST = "this-host-should-not-exist-qian.invalid";

    private void assertRejected(String url) {
        assertThrows(IllegalArgumentException.class,
                () -> UrlSafetyValidator.validate(url),
                "应拒绝: " + url);
    }

    /**
     * 断言 URL 被拒绝，且拒绝原因<b>确实</b>是网段规则（消息含「内网」），
     * 而不是被协议白名单 / URI 解析失败 / host 为 null 之类的前置分支捡漏通过。
     */
    private void assertRejectedByNetworkRule(String url) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> UrlSafetyValidator.validate(url),
                "应拒绝: " + url);
        assertTrue(e.getMessage().contains(BLOCKED_MSG),
                "应按网段规则拒绝（消息含「内网」），实际消息: " + e.getMessage()
                        + "，输入: " + url);
    }

    // ===== IPv4 私有 / 保留网段 =====

    @Test
    void rejectsLoopback() {
        assertRejectedByNetworkRule("http://127.0.0.1/admin");
        // 注意：不能用 http://127.1.2.3/ 来测回环 ——
        // java.net.URI 对简写 IP 的解析依赖 JDK 实现：本机 JDK 17/25 上 getHost() 返回
        // "127.1.2.3"（会走到网段规则），但该行为并非规范保证，不应当作测试前提。
        // 这里统一用规范形式的 127.0.0.1，回环网段判定由 127/8 前缀覆盖。
        assertRejectedByNetworkRule("http://127.255.255.254/");
    }

    @Test
    void rejectsPrivateClassA() {
        assertRejectedByNetworkRule("http://10.0.0.1/");
    }

    @Test
    void rejectsPrivateClassB() {
        assertRejectedByNetworkRule("http://172.16.0.1/");
        assertRejectedByNetworkRule("http://172.31.255.254/");
    }

    @Test
    void rejectsPrivateClassC() {
        assertRejectedByNetworkRule("http://192.168.1.1/");
    }

    @Test
    void rejectsCloudMetadataEndpoint() {
        // 169.254.169.254 是云厂商元数据服务，SSRF 的首要目标
        assertRejectedByNetworkRule("http://169.254.169.254/latest/meta-data/");
        assertRejectedByNetworkRule("http://169.254.1.1/");
    }

    @Test
    void rejectsCarrierGradeNatAndBenchmark() {
        assertRejectedByNetworkRule("http://100.64.0.1/");
        assertRejectedByNetworkRule("http://198.18.0.1/");
    }

    @Test
    void rejectsUnspecifiedAndMulticast() {
        assertRejectedByNetworkRule("http://0.0.0.0/");
        assertRejectedByNetworkRule("http://224.0.0.1/");
        assertRejectedByNetworkRule("http://240.0.0.1/");
    }

    // ===== IPv6 =====

    @Test
    void rejectsIpv6Loopback() {
        assertRejectedByNetworkRule("http://[::1]/");
    }

    @Test
    void rejectsIpv4MappedIpv6() {
        // ⚠️ 本用例实际<b>没有</b>覆盖 isIpv4Mapped 分支：当前 HotSpot 在 getAllByName
        //    阶段就把 ::ffff:127.0.0.1 归一为 Inet4Address（4 字节），走的是 IPv4 网段规则。
        //    isIpv4Mapped 是防御性分支，在当前 JVM 上不可达（见主类注释），
        //    其正确性只能由代码审查保证 —— 这里如实断言「被网段规则拒绝」，
        //    不假装覆盖了那条分支。保留本用例是为了挡住将来的回归：
        //    若哪天归一行为变了、而 isIpv4Mapped 又被删掉，这里会红。
        assertRejectedByNetworkRule("http://[::ffff:127.0.0.1]/");
        assertRejectedByNetworkRule("http://[::ffff:192.168.0.1]/");
    }

    /**
     * 直接对 isIpv4Mapped 分支做单元断言。
     *
     * <p>为什么必须用反射喂原始字节数组：{@code InetAddress.getByAddress(byte[16])} 同样会
     * 把 v4-mapped 形态归一为 4 字节 —— 公开 API 根本构造不出「未归一的 16 字节
     * Inet6Address」。故只能直接对字节数组调用。
     *
     * <p>这条用例的存在意义：isIpv4Mapped 分支在主流程不可达（见主类注释），
     * 但它是防「自定义 InetAddressResolverProvider 返回 v6 形态」的兜底 —— 必须有人验证
     * 逻辑本身正确（内嵌内网→拒绝、内嵌公网→放行），否则它是一段从未被跑过的代码。
     * 这里同时覆盖 isIpv4Mapped 判定 与 拆出内嵌 IPv4 后交给 isBlockedIpv4 的整条链路。
     */
    @Test
    void isIpv4MappedBranchBlocksEmbeddedPrivateIpv4() throws Exception {
        Method isIpv4Mapped = UrlSafetyValidator.class.getDeclaredMethod("isIpv4Mapped", byte[].class);
        isIpv4Mapped.setAccessible(true);
        Method isBlockedIpv4 = UrlSafetyValidator.class
                .getDeclaredMethod("isBlockedIpv4", int.class, int.class, int.class);
        isBlockedIpv4.setAccessible(true);

        byte[] privateMapped = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF,
                (byte) 127, 0, 0, 1};
        assertTrue((boolean) isIpv4Mapped.invoke(null, (Object) privateMapped),
                "::ffff:127.0.0.1 的 16 字节形态应被识别为 v4-mapped");
        assertTrue((boolean) isBlockedIpv4.invoke(null, 127, 0, 0),
                "拆出的 127.0.0.1 应被 isBlockedIpv4 阻断");

        byte[] privateMappedC = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF,
                (byte) 192, (byte) 168, 0, 1};
        assertTrue((boolean) isIpv4Mapped.invoke(null, (Object) privateMappedC),
                "::ffff:192.168.0.1 的 16 字节形态应被识别为 v4-mapped");
        assertTrue((boolean) isBlockedIpv4.invoke(null, 192, 168, 0),
                "拆出的 192.168.0.1 应被 isBlockedIpv4 阻断");

        // 内嵌公网 IPv4（93.184.215.14 = example.com）：识别为 v4-mapped，但不阻断
        byte[] publicMapped = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF,
                (byte) 93, (byte) 184, (byte) 215, (byte) 14};
        assertTrue((boolean) isIpv4Mapped.invoke(null, (Object) publicMapped),
                "内嵌公网 IP 的形态也应是 v4-mapped 形态");
        assertFalse((boolean) isBlockedIpv4.invoke(null, 93, 184, 215),
                "内嵌公网 IP 不应被误伤");

        // 非 v4-mapped 的 16 字节不应被误判
        byte[] plainV6 = {0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        assertFalse((boolean) isIpv4Mapped.invoke(null, (Object) plainV6),
                "普通 IPv6 不应被误判为 v4-mapped");
    }

    /** 留档当前 JVM 的归一行为 —— 主流程里 ::ffff:x 实际走的是 IPv4 分支，不是 isIpv4Mapped。 */
    @Test
    void jdkNormalizesIpv4MappedToInet4Address() throws Exception {
        InetAddress normalized = InetAddress.getByName("::ffff:127.0.0.1");
        assertEquals(4, normalized.getAddress().length,
                "记录当前 JVM 行为：::ffff:127.0.0.1 已被归一为 Inet4Address");
        // getByAddress 亦然 —— 公开 API 构造不出未归一的 v6 形态
        InetAddress viaBytes = InetAddress.getByAddress(new byte[]{0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, (byte) 0xFF, (byte) 0xFF, (byte) 127, 0, 0, 1});
        assertEquals(4, viaBytes.getAddress().length,
                "记录当前 JVM 行为：getByAddress 同样归一，公开 API 无法构造未归一的 v6 映射形态");
    }

    @Test
    void rejectsIpv6UniqueLocalAndLinkLocal() {
        assertRejectedByNetworkRule("http://[fd00::1]/");
        assertRejectedByNetworkRule("http://[fe80::1]/");
    }

    /**
     * fec0::/10 是 RFC 3879 已废弃的 site-local —— 废弃指的是「不再用于新部署」，
     * 不等于「路由可达」：它依旧是内网私有前缀，SSRF 语境下必须与 fe80::/10 同等拦截。
     *
     * <p>边界：判据为「首字节 0xFE 且第二字节高两位非 00」，应完整覆盖 0xFE 下的
     * fe80::/10（高两位 10）与 fec0::/10（高两位 11），且不越界到 fe00::/9 的其余保留段。
     */
    @Test
    void rejectsIpv6SiteLocal() {
        assertRejectedByNetworkRule("http://[fec0::1]/");
        assertRejectedByNetworkRule("http://[fec0:ffff::1]/");
        assertRejectedByNetworkRule("http://[feff::1]/");
    }

    // ===== IPv4/IPv6 过渡机制（NAT64 / 6to4 / Teredo）=====

    @Test
    void rejectsNat64Prefix() {
        // 64:ff9b::/96 是 RFC 6052 的 NAT64 前缀，NAT64/DNS64 网络下翻译成内网地址，
        // 与 ::ffff:127.0.0.1 是同一类绕过 —— 整段拒绝
        assertRejectedByNetworkRule("http://[64:ff9b::7f00:1]/");
        assertRejectedByNetworkRule("http://[64:ff9b::a00:1]/");
        assertRejectedByNetworkRule("http://[64:ff9b::c0a8:101]/");
    }

    @Test
    void rejects6to4Prefix() {
        // 2002::/16 (RFC 3056)：前 32 位内嵌 IPv4
        assertRejectedByNetworkRule("http://[2002:7f00:1::]/");
        assertRejectedByNetworkRule("http://[2002:c0a8:101::]/");
    }

    @Test
    void rejectsTeredoPrefix() {
        // 2001::/32 (RFC 4380)
        assertRejectedByNetworkRule("http://[2001:0:1234::1]/");
        assertRejectedByNetworkRule("http://[2001::1]/");
    }

    /**
     * 过渡前缀的字节匹配不能误伤正常公网 IPv6 ——
     * 尤其 2001::/32 (Teredo) 想当然写成「前两字节 20 01」就会把 2001:db8::、2001:4860::
     * 这类真实地址一起拒掉。这里钉住边界。
     */
    @Test
    void doesNotOverBlockPublicIpv6() throws Exception {
        assertFalse(isBlockedIpv6Bytes(new byte[]{0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 1}), "2001:db8::/32 是文档段，不属 Teredo，不应被前缀规则误伤");
        assertFalse(isBlockedIpv6Bytes(new byte[]{0x20, 0x01, 0x48, 0x60, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, (byte) 0x88, (byte) 0x88}), "2001:4860:: 是公网地址，不应被误伤");
        assertFalse(isBlockedIpv6Bytes(new byte[]{0x20, 0x03, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 1}), "2003:: 不在任何过渡前缀内，不应被误伤");

        // 0xFE 首字节下的边界：合并 fe80::/10 与 fec0::/10 后，判据是「第二字节高两位非 00」，
        // 必须确认没有越界到 fe00:: 段（高两位 00）——
        // fe00::1 属 fe00::/9 保留段但不在 /10 内，按「不做超范围拦截」放行。
        assertFalse(isBlockedIpv6Bytes(new byte[]{(byte) 0xFE, 0x00, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 1}),
                "fe00::1 第二字节高两位为 00，不在 fe80::/10 或 fec0::/10 内，不应被误伤");
        // 公网 IPv6 边界：Cloudflare 与 Google DNS
        assertFalse(isBlockedIpv6Bytes(new byte[]{0x26, 0x06, 0x47, 0x00, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0x11, 0x11}), "2606:4700::1111 是 Cloudflare 公网地址，不应被误伤");
        assertFalse(isBlockedIpv6Bytes(new byte[]{0x20, 0x01, 0x48, 0x60, 0x48, 0x60, 0, 0,
                0, 0, 0, 0, 0, 0, (byte) 0x88, (byte) 0x88}),
                "2001:4860:4860::8888 是 Google 公网 DNS，不应被误伤");
    }

    private static boolean isBlockedIpv6Bytes(byte[] b) throws Exception {
        Method m = UrlSafetyValidator.class.getDeclaredMethod("isBlockedIpv6", byte[].class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, (Object) b);
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
        //
        // ⚠️ 本用例依赖「.invalid 必定解析失败」。RFC 6761 只对兼容实现的解析器有保证；
        //    公司/CI 的 DNS 劫持或 stub 解析器可能给它返回地址，那样 isBlocked=false、
        //    validate 会返回 URI，断言即失败 —— 在无关的机器上误红。
        //    故用 Assumptions 守卫：若该 host 竟然可解析，本用例不适用，跳过。
        Assumptions.assumeFalse(dnsResolvable(UNRESOLVABLE_HOST),
                "该 host 在本机竟可解析（DNS 劫持/桩解析器？），本用例不适用，跳过");
        assertRejected("http://" + UNRESOLVABLE_HOST + "/");
    }

    @Test
    void rejectsLocalhostByName() {
        // localhost 经 DNS 解析到 127.0.0.1，必须被同一规则拦下
        assertRejectedByNetworkRule("http://localhost:8080/admin");
    }

    // ===== 合法公网 URL =====

    @Test
    void acceptsPublicUrl() {
        Assumptions.assumeTrue(dnsResolvable("example.com"), "需要 DNS，已跳过");
        URI uri = UrlSafetyValidator.validate("https://example.com/path?q=1");
        assertEquals("example.com", uri.getHost());
        assertEquals("https", uri.getScheme());
    }

    // ===== 异常消息不得回显原始 URL / 解析结果（I2）=====

    @Test
    void rejectionMessageDoesNotEchoUrlOrAddress() {
        String[] injected = {
                "http://127.0.0.1/ignore-previous-instructions",
                "http://10.0.0.1/",
                "http://169.254.169.254/latest/meta-data/",
                "http://[::1]/",
        };
        for (String url : injected) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> UrlSafetyValidator.validate(url), "应拒绝: " + url);
            String msg = e.getMessage();
            assertFalse(msg.contains(url), "消息不得回显原始 URL，实际: " + msg);
            assertFalse(msg.contains("127.0.0.1") || msg.contains("10.0.0.1")
                            || msg.contains("169.254.169.254"),
                    "消息不得回显解析出的内网地址，实际: " + msg);
            assertFalse(msg.contains("->"), "消息不得包含解析结果箭头，实际: " + msg);
        }
        // 协议分支同样不得回显
        IllegalArgumentException scheme = assertThrows(IllegalArgumentException.class,
                () -> UrlSafetyValidator.validate("ftp://evil.example.com/x"));
        assertFalse(scheme.getMessage().contains("evil.example.com"),
                "协议分支消息不得回显 URL，实际: " + scheme.getMessage());
    }

    @Test
    void uriSyntaxFailureDoesNotEchoUrlAnywhere() {
        // URISyntaxException.getMessage() 形如
        // "Illegal character in path at index 3: not a url at all" —— 内嵌原始 URL。
        // 既不能拼进对外消息，也不能挂成 cause（上层 printStackTrace 会原样打出）。
        String url = "not a url at all";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> UrlSafetyValidator.validate(url));
        assertFalse(e.getMessage().contains(url),
                "对外消息不得回显 URL，实际: " + e.getMessage());
        assertFalse(containsUrl(e.getCause(), url),
                "cause（含递归）不得回显 URL，实际 cause: "
                        + (e.getCause() == null ? "null" : e.getCause().getMessage()));
        // 诊断价值由 log.warn 承接，这里如实记录 cause 被刻意丢弃
        assertNull(e.getCause(), "本分支刻意不挂 cause，避免经 cause 回显 URL");
    }

    private static boolean containsUrl(Throwable t, String url) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getMessage() != null && c.getMessage().contains(url)) {
                return true;
            }
        }
        return false;
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
