package com.qian.qianaiagent.app;

import java.util.Set;

/**
 * 面试指令整句匹配：禁止 contains，避免「过程/过滤/通过」误触发跳过。
 */
public final class QuizCommandMatcher {

    private QuizCommandMatcher() {}

    public static final Set<String> NEXT_CMDS = Set.of(
            "下一题", "下一个", "下一条", "next", "next one",
            "go on", "继续", "继续吧", "下题", "下一道",
            "过", "过吧", "下一问", "跳过", "pass", "skip"
    );

    public static final Set<String> SKIP_DIR_CMDS = Set.of(
            "换个方向", "换个话题", "换方向", "换话题",
            "下一方向", "下一个话题", "next topic"
    );

    public static final Set<String> RESET_MEMORY_CMDS = Set.of(
            "重置记忆", "清空记忆", "清理记忆", "重置会话",
            "reset memory", "clean memory", "clear memory"
    );

    public static boolean isNext(String message) {
        return matches(message, NEXT_CMDS);
    }

    public static boolean isSkipDir(String message) {
        return matches(message, SKIP_DIR_CMDS);
    }

    public static boolean isResetMemory(String message) {
        return matches(message, RESET_MEMORY_CMDS);
    }

    private static boolean matches(String message, Set<String> cmds) {
        if (message == null) return false;
        String n = message.trim().toLowerCase();
        return cmds.contains(n);
    }
}
