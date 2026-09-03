package com.qian.qianaiagent.rag.retrieval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MultiQuerySearchService 守卫逻辑单元测试。
 * <p>
 * 覆盖 {@link MultiQuerySearchService#shouldUseMultiQuery(String)} 纯函数的各分支，
 * 以及 {@code multiQuerySearch} 在「不值得多查询」时的降级行为。
 */
@ExtendWith(MockitoExtension.class)
class MultiQuerySearchServiceTest {

    @Mock
    private VectorStore quizVectorStore;

    @Mock
    private QueryRewriter queryRewriter;

    @InjectMocks
    private MultiQuerySearchService service;

    @BeforeEach
    void setUp() {
        // @Value 注入的字段在纯单测中不会被 Spring 填充，手动设置与生产默认值一致的阈值
        ReflectionTestUtils.setField(service, "minQueryLength", 15);
        ReflectionTestUtils.setField(service, "maxQueryLength", 100);
    }

    // ============ shouldUseMultiQuery 纯函数 ============

    @Test
    void 空白查询不值得多查询() {
        assertFalse(service.shouldUseMultiQuery(null));
        assertFalse(service.shouldUseMultiQuery(""));
        assertFalse(service.shouldUseMultiQuery("   "));
    }

    @Test
    void 命中指令黑名单不值得多查询() {
        assertFalse(service.shouldUseMultiQuery("继续"));
        assertFalse(service.shouldUseMultiQuery("换一个"));
        assertFalse(service.shouldUseMultiQuery("下一题"));
        assertFalse(service.shouldUseMultiQuery("ok"));
    }

    @Test
    void 过短查询不值得多查询() {
        // 非黑名单、但长度 < minQueryLength(15)
        assertFalse(service.shouldUseMultiQuery("ab"));
        assertFalse(service.shouldUseMultiQuery("线程池参数"));
    }

    @Test
    void 过长查询不值得多查询() {
        // 非黑名单、长度 > maxQueryLength(100)
        assertFalse(service.shouldUseMultiQuery("a".repeat(101)));
    }

    @Test
    void 正常技术问题值得多查询() {
        assertTrue(service.shouldUseMultiQuery("请详细介绍一下 Java 线程池的核心参数"));
    }

    // ============ multiQuerySearch 降级行为 ============

    @Test
    void 指令查询退化为单次检索且不生成变体() {
        when(quizVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        List<Document> result = service.multiQuerySearch("继续", 3, 5, 0.3);

        // 不生成变体，只做一次单次检索
        verify(queryRewriter, never()).doMultiQueryExpand(anyString(), anyInt());
        verify(quizVectorStore, times(1)).similaritySearch(any(SearchRequest.class));
        assertTrue(result.isEmpty());
    }

    @Test
    void 技术问题走多查询扩展路径() {
        when(queryRewriter.doMultiQueryExpand(anyString(), anyInt()))
                .thenReturn(List.of("变体1", "变体2", "变体3"));
        when(quizVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.multiQuerySearch("请详细介绍一下 Java 线程池的核心参数", 3, 5, 0.3);

        // 生成 3 个变体，执行 3 次检索
        verify(queryRewriter, times(1)).doMultiQueryExpand(anyString(), eq(3));
        verify(quizVectorStore, times(3)).similaritySearch(any(SearchRequest.class));
    }
}
