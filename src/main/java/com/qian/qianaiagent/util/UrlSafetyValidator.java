package com.qian.qianaiagent.util;

import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public final class UrlSafetyValidator {

    private UrlSafetyValidator() {
    }

    /**
     * 校验 URL 并返回解析后的 {@link URI}，调用方应使用返回值建连，不要再用原始字符串。
     *
     * <p><b>异常消息刻意不含原始 URL 与解析结果</b>：本方法的消息会被上层原样回传给 LLM
     * 供其自愈换方案，而 URL 完全由 LLM / 用户控制 —— 拼回去等于把攻击者可控文本重新注入
     * 提示词，并顺带回显内网地址。完整细节（URL、主机、解析结果）改为走 {@code log.warn}。
     *
     * @throws IllegalArgumentException URL 为空、协议非 http/https、缺主机名、
     *                                  DNS 无法解析、或解析结果落在内网 / 保留网段
     */
    public static URI validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        String trimmed = url.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            // 不挂 cause：URISyntaxException.getMessage() 形如
            // "Illegal character in path at index 3: <被拒的原始 URL>"，把它挂成 cause
            // 等于绕一圈把 URL 又带回调用栈（上层 printStackTrace / 日志会原样打出）。
            // 该异常的诊断价值已由下面这条 log（含完整 URL 与栈）承接。
            log.warn("URL 校验失败：无法解析为 URI，url={}", sanitize(trimmed), e);
            throw new IllegalArgumentException("URL 无法解析");
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            log.warn("URL 校验失败：协议非 http/https，scheme={}, url={}", scheme, sanitize(trimmed));
            throw new IllegalArgumentException("只允许 http/https 协议");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            log.warn("URL 校验失败：缺少主机名，url={}", sanitize(trimmed));
            throw new IllegalArgumentException("URL 缺少主机名");
        }
        // IPv6 字面量在 URI 中带方括号，交给 InetAddress 前必须去掉
        String bare = stripBrackets(host);
        InetAddress[] addresses;
        try {
            // 字面 IP 与域名统一走这里，无需分支
            addresses = InetAddress.getAllByName(bare);
        } catch (UnknownHostException e) {
            // 无法解析 → 保守拒绝，不猜
            log.warn("URL 校验失败：主机无法解析，host={}, url={}", host, sanitize(trimmed), e);
            throw new IllegalArgumentException("URL 主机无法解析");
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                // 安全事件落日志：含完整 URL 与解析到的地址，供排查
                log.warn("URL 校验失败：指向内网或保留地址，host={} -> {}, url={}",
                        host, address.getHostAddress(), sanitize(trimmed));
                throw new IllegalArgumentException("URL 指向内网或保留地址，已拒绝");
            }
        }
        return uri;
    }

    /**
     * 去掉控制字符与行分隔符再落日志：原始 URL 可含 {@code \n}、{@code \r} 等，
     * 直接写入日志可被用来伪造日志行（log injection）。
     *
     * <p>判据用 {@link Character#isISOControl}（覆盖 C0 {@code < 0x20} 与 DEL {@code 0x7F}）
     * 之外，另需显式拦下三个 <b>非 ISO 控制字符</b>却会被部分日志收集器 / 解析器当作换行的码点：
     * {@code U+0085} NEL、{@code U+2028} LINE SEPARATOR、{@code U+2029} PARAGRAPH SEPARATOR ——
     * 它们不在 {@code isISOControl} 的范围内，是典型漏网点。
     */
    private static String sanitize(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean lineBreak = c == '\u0085' || c == '\u2028' || c == '\u2029';
            sb.append(Character.isISOControl(c) || lineBreak ? '?' : c);
        }
        return sb.toString();
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
            return isBlockedIpv4(bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF);
        }
        if (bytes.length == 16) {
            // IPv4-mapped（::ffff:a.b.c.d）：当前 HotSpot 实现会在 getAllByName 阶段就把它
            // 归一为 Inet4Address（走上面的 length==4 分支），所以本分支在 HotSpot 上不可达。
            // 「归一」是 JDK 实现细节而非规范保证：自定义 InetAddressResolverProvider
            // 完全可以返回 v6 形态的 Inet6Address —— 此处正是兜住这类实现，否则它是一条
            // 绕过路径（内嵌 IPv4 直接跳过全部网段判定）。请勿当作死代码删除。
            //
            // ⚠️ 上面的「主流程」在当前 JVM 上不可达（测试里 ::ffff:127.0.0.1 经 JDK 归一后
            //    实际命中的是 IPv4 分支）。但本分支的逻辑本身<b>并非</b>无人验证：
            //    测试文件里的反射用例直接对 isIpv4Mapped 喂原始字节数组，覆盖了判定逻辑与
            //    拆出内嵌 IPv4 的整条链路（改坏它会红）。
            if (isIpv4Mapped(bytes)) {
                return isBlockedIpv4(bytes[12] & 0xFF, bytes[13] & 0xFF, bytes[14] & 0xFF);
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

    private static boolean isBlockedIpv4(int a, int b, int c) {
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
        // IPv4/IPv6 过渡机制：单播前缀内嵌着 IPv4，在对应网络环境下会被翻译/隧道到内网。
        // 正常业务用不到，整段拒绝，不拆内嵌 IPv4（避免与 NAT64 拼接形式纠缠）。
        if ((b[0] & 0xFF) == 0x00 && (b[1] & 0xFF) == 0x64
                && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B) {
            return true;                                            // 64:ff9b::/96 NAT64 (RFC 6052)
        }
        if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x02) {
            return true;                                            // 2002::/16 6to4 (RFC 3056)
        }
        if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x01
                && (b[2] & 0xFF) == 0x00 && (b[3] & 0xFF) == 0x00) {
            return true;                                            // 2001::/32 Teredo (RFC 4380)
        }
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
        // 0xFE 首字节下的两段：fe80::/10 链路本地（b[1] 高两位 10），
        // 以及 fec0::/10 site-local（b[1] 高两位 11）——后者已被 RFC 3879 废弃，
        // 但废弃不等于可路由：它仍是内网私有前缀，SSRF 语境下必须一并拦截。
        // 合并判据即「b[1] 高两位非 00」，同时排除 fe00::/9 的其余段（fe00::/10 等保留段不在此列）。
        return first == 0xFE && (b[1] & 0xC0) != 0x00;
    }
}
