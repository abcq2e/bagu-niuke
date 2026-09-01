package com.qian.qianaiagent.rag.retrieval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 纯逻辑测试：验证 shouldSkipFastPath 的快速跳过规则，不依赖 Spring 容器或 LLM。
 * shouldSkipFastPath 是 static package-private 方法，可直接调用。
 */
class QueryRewriterSkipTest {

    @Test
    void shortMessage_shouldSkip() {
        // 短于 15 字的消息应直接跳过重写
        String msg = "HashMap 怎么工作的";  // 9 字，< 15
        assertEquals(true, QueryRewriter.shouldSkipFastPath(msg));
    }

    @Test
    void nullOrBlank_shouldSkip() {
        assertEquals(true, QueryRewriter.shouldSkipFastPath(null));
        assertEquals(true, QueryRewriter.shouldSkipFastPath(""));
        assertEquals(true, QueryRewriter.shouldSkipFastPath("   "));
    }

    @Test
    void longAnswer_over100Chars_shouldSkip() {
        String longAnswer = "HashMap 的底层是数组加链表加红黑树结构，put 时先通过 key 的 hashCode 计算哈希值，"
                + "再与数组长度取模定位桶位置，如果桶为空直接插入，如果有冲突则遍历链表，"
                + "链表长度超过 8 且数组长度超过 64 时转为红黑树，这样最坏情况时间复杂度从 O(n) 降到 O(logn)。";
        // 超过 100 字，是用户在回答题目，应跳过重写
        assertEquals(true, QueryRewriter.shouldSkipFastPath(longAnswer));
    }

    @Test
    void codeSnippet_shouldSkip() {
        String code = "public class Main { public static void main(String[] args) { return; } }";
        assertEquals(true, QueryRewriter.shouldSkipFastPath(code));
    }

    @Test
    void shortTechnicalQuestion_shouldNotSkip() {
        // 15-100 字的技术提问，不含代码关键词，不应跳过（应该重写以提升检索质量）
        String question = "HashMap 和 Hashtable 的线程安全实现有什么区别，底层原理讲一下";
        assertEquals(false, QueryRewriter.shouldSkipFastPath(question));
    }

    @Test
    void arrowOperator_shouldSkip() {
        String lambda = "list.stream().filter(x -> x > 0).collect(Collectors.toList());";
        assertEquals(true, QueryRewriter.shouldSkipFastPath(lambda));
    }
}
