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
 *   <li>{@code normalize()} 后做前缀检查（挡住 {@code a/../../x} 这类嵌套）</li>
 *   <li>用 {@code NOFOLLOW_LINKS} 检测链接「本身」是否存在，存在则 {@code toRealPath()} 再查一次
 *       （挡住符号链接逃逸）；注意悬空链接本身存在但跟随判定为 false，必须按链接本身判定，
 *       且 {@code toRealPath()} 无法解析时保守拒绝，不静默放行</li>
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

        Path candidate;
        try {
            candidate = Path.of(userPath);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("路径无法解析: " + userPath);
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

        // 🔴 NOFOLLOW_LINKS：悬空符号链接「本身存在」但跟随判定为 false，
        //    若跟随判定就会漏过它 —— 而往悬空链接写入会在目录外真实创建文件
        if (Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
            Path real;
            try {
                real = resolved.toRealPath();
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
