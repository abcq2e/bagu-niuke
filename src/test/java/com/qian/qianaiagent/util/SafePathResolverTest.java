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
    void acceptsLegalRelativePath() throws Exception {
        Path resolved = SafePathResolver.resolveWithin(base, "notes.md");
        assertEquals("notes.md", resolved.getFileName().toString());
        assertTrue(resolved.startsWith(base.toRealPath()));
    }

    @Test
    void acceptsNestedLegalPath() throws Exception {
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
