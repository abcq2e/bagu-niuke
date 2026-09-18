package com.qian.qianaiagent.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
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
 *   <li>拒绝 {@code ..} 路径段与绝对路径（按段判定，{@code a..b.txt} 这类合法名不受影响）</li>
 *   <li>纵深防御：{@code normalize()} 之后再做一次前缀比对。含 {@code ..} 的输入在第 1 条
 *       就已拒绝，这一层主要是兜住 normalize 本身可能产生的意外结果（例如空串归一为 {@code .}），
 *       不是为了挡 {@code a/../../x} —— 那个输入根本到不了这里</li>
 *   <li>解析「最深的存在祖先」并对其 {@code toRealPath()}，比对真实路径是否仍在根内 ——
 *       挡住符号链接/junction 逃逸。只解析最终元素是不够的：输入 {@code linkdir/new.txt} 时
 *       最终元素不存在，只看最终元素的检查会被整个跳过，而 {@code linkdir} 本身可能指向目录外。
 *       悬空链接无法解析时保守拒绝，不静默放行</li>
 * </ol>
 */
public final class SafePathResolver {

    private SafePathResolver() {
    }

    /**
     * 把 {@code userPath} 解析到 {@code baseDir} 之内。
     *
     * @return 解析后的绝对路径，保证位于 {@code baseDir} 内
     * @throws IllegalArgumentException 路径为空或全空白、含 {@code ..} 段、为绝对路径、
     *                                  指向根目录本身、含非法字符（如 Windows 的 {@code <>:"|?*}）、
     *                                  无法解析（悬空符号链接）或是经由符号链接越界
     */
    public static Path resolveWithin(Path baseDir, String userPath) {
        if (userPath == null || userPath.isBlank()) {
            throw new IllegalArgumentException("路径不能为空");
        }

        Path candidate;
        try {
            candidate = Path.of(userPath);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("路径无法解析: " + userPath + " — " + e.getMessage());
        }
        if (candidate.isAbsolute()) {
            throw new IllegalArgumentException("不允许绝对路径: " + userPath);
        }
        // 🔴 按「路径段」判定 ..，而非字符串包含 —— 否则合法的 a..b.txt 会被误拒
        for (Path segment : candidate) {
            if ("..".equals(segment.toString())) {
                throw new IllegalArgumentException("路径不能包含 .. 段: " + userPath);
            }
        }

        Path normalizedBase = realBase(baseDir);
        Path resolved = normalizedBase.resolve(candidate).normalize();
        if (!resolved.startsWith(normalizedBase)) {
            throw new IllegalArgumentException("路径越界: " + userPath);
        }
        // 指向根目录本身没有意义，且会让 writeFile 去写一个目录
        if (resolved.equals(normalizedBase)) {
            throw new IllegalArgumentException("路径不能指向根目录本身: " + userPath);
        }

        // 🔴 必须解析「最深的存在祖先」，而不是只看最终元素：
        //    若 base/linkdir 是指向目录外的链接，输入 "linkdir/new.txt" 的最终元素不存在，
        //    只看最终元素的检查会被整个跳过 —— 那是一条真实逃逸路径。
        //    NOFOLLOW_LINKS：悬空链接「本身存在」但跟随判定为 false，必须按链接本身判定，
        //    否则往悬空链接写入会在目录外真实创建文件。
        Path probe = resolved;
        Path existingAncestor = null;
        while (probe != null) {
            if (Files.exists(probe, LinkOption.NOFOLLOW_LINKS)) {
                existingAncestor = probe;
                break;
            }
            if (probe.equals(normalizedBase)) {
                break;      // 连 base 都不存在：没有可解析的祖先
            }
            probe = probe.getParent();
        }
        if (existingAncestor != null) {
            Path real;
            try {
                real = existingAncestor.toRealPath();
            } catch (IOException e) {
                // 悬空链接或无法解析 —— 保守拒绝，不猜
                throw new IllegalArgumentException("路径无法解析（可能是悬空符号链接）: " + userPath);
            }
            if (!real.startsWith(normalizedBase)) {
                throw new IllegalArgumentException("路径经由符号链接越界: " + userPath);
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
