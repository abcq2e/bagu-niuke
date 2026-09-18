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
