package com.qian.qianaiagent.util;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
        Path outside = Files.createTempDirectory("outside");
        // 🔴 必须让目标真实存在：悬空链接会被 Files.exists 跟随判定为 false，
        //    防护被跳过，测试就变成「断言从未执行」的假绿
        Path victim = Files.writeString(outside.resolve("victim.txt"), "secret");
        Path link = base.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, victim);
        } catch (UnsupportedOperationException | IOException e) {
            // 本机无权限（Windows 未开开发者模式）—— 显式 skip，不伪装成通过
            Assumptions.assumeTrue(false, "本机无法创建符号链接，跳过: " + e.getMessage());
        }
        assertThrows(IllegalArgumentException.class,
                () -> SafePathResolver.resolveWithin(base, "link.txt"));
    }

    @Test
    void rejectsDanglingSymlink() throws Exception {
        // 悬空链接：往它写入会在目录外创建文件，必须拒绝
        Path outside = Files.createTempDirectory("outside");
        Path link = base.resolve("dangling.txt");
        try {
            Files.createSymbolicLink(link, outside.resolve("never-created.txt"));
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.assumeTrue(false, "本机无法创建符号链接，跳过: " + e.getMessage());
        }
        assertThrows(IllegalArgumentException.class,
                () -> SafePathResolver.resolveWithin(base, "dangling.txt"));
    }

    @Test
    void rejectsEscapeViaSymlinkedDirectory() throws Exception {
        // base/linkdir -> 目录外，再写 linkdir/new.txt。
        // 最终元素 new.txt 不存在，只看最终元素的检查会漏过这条逃逸。
        Path outside = Files.createTempDirectory("outside");
        Path linkDir = base.resolve("linkdir");
        try {
            Files.createSymbolicLink(linkDir, outside);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.assumeTrue(false, "本机无法创建目录符号链接，跳过: " + e.getMessage());
        }
        assertThrows(IllegalArgumentException.class,
                () -> SafePathResolver.resolveWithin(base, "linkdir/new.txt"));
    }

    @Test
    void rejectsBaseItself() {
        assertThrows(IllegalArgumentException.class,
                () -> SafePathResolver.resolveWithin(base, "."));
    }

    @Test
    void acceptsDotsInsideFileName() {
        // a..b.txt 是合法文件名，不能因为字符串含 .. 就误拒
        Path resolved = SafePathResolver.resolveWithin(base, "a..b.txt");
        assertEquals("a..b.txt", resolved.getFileName().toString());
    }
}
