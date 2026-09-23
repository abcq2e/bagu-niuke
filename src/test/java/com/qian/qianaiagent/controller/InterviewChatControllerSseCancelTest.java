package com.qian.qianaiagent.controller;

import com.qian.qianaiagent.ability.UserAbilityService;
import com.qian.qianaiagent.context.UserContext;
import com.qian.qianaiagent.interview.QuizApp;
import com.qian.qianaiagent.interview.review.WrongQuestionReviewService;
import com.qian.qianaiagent.memory.ConversationAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证面试主路径 / 复习路径的 SSE 流在<b>客户端断线（CANCEL）</b>时仍会保存能力画像。
 * <p>
 * 这类行为在浏览器里断线才能触发，本机没有 PostgreSQL/Redis/真实 API Key，
 * 也不便自动化真实的「关标签页」操作，因此用单元测试直接驱动 Flux 的 cancel 信号。
 * <p>
 * 之所以用 doFinally 而不是 doOnComplete：Spring 的响应式适配器在客户端断开时
 * 调用的是 {@code Subscription.cancel()}，信号是 CANCEL 而非 ON_COMPLETE，
 * doOnComplete 根本不会触发 —— 这正是本测试要钉住的行为（去掉 doFinally 会变红）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterviewChatControllerSseCancelTest {

    private static final long USER_ID = 42L;
    private static final String CHAT_ID = "chat_cancel_case";

    @Mock
    private QuizApp quizApp;

    @Mock
    private WrongQuestionReviewService wrongQuestionReviewService;

    @Mock
    private UserAbilityService userAbilityService;

    @Mock
    private ConversationAccess conversationAccess;

    /** 用 inline 执行的 Executor：让 subscribeOn 在当前线程立刻订阅，测试才能拿到 Subscription */
    @Mock
    private Executor taskExecutor;

    @InjectMocks
    private InterviewChatController controller;

    private MockedStatic<UserContext> userContextMock;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        userContextMock = Mockito.mockStatic(UserContext.class);
        userContextMock.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

        when(conversationAccess.startSession(anyString(), anyLong())).thenReturn(true);
        when(conversationAccess.open(anyString(), anyLong())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
    }

    /** 一个先吐一块内容、然后一直挂着的流 —— 模拟「回答还没结束」的 SSE 连接 */
    private static Flux<String> streamingThenHanging() {
        return Flux.concat(Flux.just("第一段回答"), Flux.never());
    }

    @Test
    @DisplayName("doChat：客户端断线（cancel）也要保存画像 —— doOnComplete 不会触发，靠 doFinally 兜住")
    void doChatSavesProfileOnClientDisconnect() {
        when(quizApp.doUnifiedChat(eq("你好"), eq(CHAT_ID), eq(USER_ID)))
                .thenReturn(streamingThenHanging());

        Disposable subscription = controller.doChat("你好", CHAT_ID).subscribe();

        // 流仍在进行中，尚未收到 [DONE]：此时不应保存
        verify(userAbilityService, never()).saveProfile(anyString(), anyLong());

        // 客户端关掉页面 → 适配器取消 Flux
        subscription.dispose();

        // 🔴 关键断言：CANCEL 路径保存了画像，且用的是 finalChatId
        verify(userAbilityService, times(1)).saveProfile(CHAT_ID, USER_ID);
    }

    @Test
    @DisplayName("doChat：流正常跑完仍然保存画像（回归保护，别把正常路径改坏）")
    void doChatSavesProfileOnNormalCompletion() {
        when(quizApp.doUnifiedChat(eq("你好"), eq(CHAT_ID), eq(USER_ID)))
                .thenReturn(Flux.just("第一段回答"));

        controller.doChat("你好", CHAT_ID).blockLast();

        verify(userAbilityService, times(1)).saveProfile(CHAT_ID, USER_ID);
    }

    @Test
    @DisplayName("doChat：出错（ON_ERROR）时也保存画像 —— doFinally 的语义变化，记录为有意为之")
    void doChatSavesProfileOnError() {
        when(quizApp.doUnifiedChat(eq("你好"), eq(CHAT_ID), eq(USER_ID)))
                .thenReturn(Flux.concat(Flux.just("第一段回答"), Flux.error(new RuntimeException("boom"))));

        controller.doChat("你好", CHAT_ID)
                .onErrorResume(e -> Flux.empty())
                .blockLast();

        verify(userAbilityService, times(1)).saveProfile(CHAT_ID, USER_ID);
    }

    @Test
    @DisplayName("doReviewChat：断线时保存的是 finalSourceId（原始面试会话），不是 finalChatId")
    void doReviewChatSavesSourceProfileOnClientDisconnect() {
        when(wrongQuestionReviewService.doReviewChat(eq("复习一下"), anyString(), anyString(), eq(USER_ID)))
                .thenReturn(streamingThenHanging());

        // 不传 sourceChatId → 后端会从 "review_orig_session" 推导出 "orig_session"
        Disposable subscription = controller.doReviewChat("复习一下", "review_orig_session", null).subscribe();

        verify(userAbilityService, never()).saveProfile(anyString(), anyLong());

        subscription.dispose();

        verify(userAbilityService, times(1)).saveProfile("orig_session", USER_ID);
        // 反证：绝不能把复习会话自己的 chatId 当成画像 key
        verify(userAbilityService, never()).saveProfile(eq("review_orig_session"), anyLong());
    }
}
