package com.qian.qianaiagent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBudgetTest {

    @TempDir
    Path tempDir;

    /** maxMessages 设得很大，确保触发的是 token 裁剪而非条数裁剪。 */
    private SummarizingChatMemory memory(int maxMessages, int maxTokens) {
        return new SummarizingChatMemory(
                new FileBasedChatMemory(tempDir.toString()), null, maxMessages, maxTokens);
    }

    /** 指定单条上限的窗口。 */
    private SummarizingChatMemory memory(int maxMessages, int maxTokens, int maxCharsPerMessage) {
        return new SummarizingChatMemory(
                new FileBasedChatMemory(tempDir.toString()), null,
                maxMessages, maxTokens, maxCharsPerMessage);
    }

    @Test
    void capsSingleOversizedMessageEvenWhenWithinTokenBudget() {
        // 关键：token 预算很大（不会触发裁剪），封顶仍必须生效
        SummarizingChatMemory memory = memory(1000, 100000, 500);
        String chatId = "oversize";
        memory.add(chatId, List.of(new UserMessage("x".repeat(5000))));

        List<Message> window = memory.get(chatId);
        assertEquals(1, window.size());
        String text = window.get(0).getText();
        assertTrue(text.length() < 5000, "超长消息应被截断，实际 " + text.length());
        assertTrue(text.endsWith("…[内容过长，已截断]"), "应带截断标记，实际尾部: "
                + text.substring(Math.max(0, text.length() - 30)));
    }

    @Test
    void doesNotTouchMessageWithinCap() {
        SummarizingChatMemory memory = memory(1000, 100000, 500);
        String chatId = "within-cap";
        memory.add(chatId, List.of(new UserMessage("short")));

        List<Message> window = memory.get(chatId);
        assertEquals("short", window.get(0).getText(), "未超上限的消息应原样返回");
    }

    @Test
    void estimatesTokensWithSafetyMargin() {
        assertEquals(0, SummarizingChatMemory.estimateTokens(null));
        assertEquals(0, SummarizingChatMemory.estimateTokens(""));
        // 150 字 → 150/1.5*1.3 = 130
        assertEquals(130, SummarizingChatMemory.estimateTokens("a".repeat(150)));
    }

    @Test
    void trimsWhenTokenBudgetExceeded() {
        SummarizingChatMemory memory = memory(1000, 300);
        String chatId = "budget";
        List<Message> msgs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            msgs.add(new UserMessage("x".repeat(100)));   // 每条约 87 token
        }
        memory.add(chatId, msgs);

        List<Message> window = memory.get(chatId);
        int total = window.stream().mapToInt(m -> SummarizingChatMemory.estimateTokens(m.getText())).sum();
        assertTrue(total <= 300, "窗口 token 应被裁到预算内，实际 " + total);
        assertTrue(window.size() < 20, "应丢弃了部分消息");
        // 保留的必须是最近的
        assertEquals(msgs.get(19).getText(), window.get(window.size() - 1).getText());
    }

    @Test
    void keepsEverythingWhenWithinBudget() {
        SummarizingChatMemory memory = memory(1000, 100000);
        String chatId = "roomy";
        List<Message> msgs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            msgs.add(new UserMessage("short" + i));
        }
        memory.add(chatId, msgs);
        assertEquals(5, memory.get(chatId).size());
    }

    @Test
    void tokenTrimNeverDropsProtectedMessage() {
        SummarizingChatMemory memory = memory(1000, 200);
        String chatId = "protected";
        List<Message> msgs = new ArrayList<>();
        msgs.add(new SystemMessage("【方向切换】旧方向结束"));
        for (int i = 0; i < 20; i++) {
            msgs.add(new UserMessage("x".repeat(100)));
        }
        memory.add(chatId, msgs);

        List<Message> window = memory.get(chatId);
        assertTrue(window.stream().anyMatch(m -> m.getText().contains("【方向切换】")),
                "token 裁剪不得丢弃锚点");
        // 🔴 必须同时确认「确实裁剪过」，否则裁剪整体失效时本用例会假绿
        assertTrue(window.size() < msgs.size(),
                "应发生了裁剪，实际窗口 " + window.size() + " 条 / 原 " + msgs.size() + " 条");
    }

    @Test
    void keepsNewestMessageEvenWhenItAloneExceedsBudget() {
        // 最新那条自身超预算时也必须保留 —— 否则一次超长检索结果就能让对话尾巴整个消失
        SummarizingChatMemory memory = memory(1000, 300);
        String chatId = "newest-big";
        memory.add(chatId, List.of(
                new UserMessage("old-small"),
                new UserMessage("x".repeat(3000))));

        List<Message> window = memory.get(chatId);
        assertEquals(1, window.size(), "应只保留最新那条，实际: " + window.size());
        assertTrue(window.get(0).getText().startsWith("x"), "保留的必须是最新的那条");
    }

    @Test
    void windowStaysContiguousNoGapInMiddle() {
        // 中间那条超预算时，窗口必须是连续后缀，不能在中间挖空
        SummarizingChatMemory memory = memory(1000, 200);
        String chatId = "gap";
        memory.add(chatId, List.of(
                new UserMessage("f".repeat(120)),     // 约 104 token
                new UserMessage("m".repeat(2000)),    // 约 1734 token，放不下
                new UserMessage("l".repeat(60))));    // 约 52 token

        List<Message> window = memory.get(chatId);
        assertEquals(1, window.size(), "应在遇到放不下的消息时停止，实际: " + window.size());
        assertEquals("l".repeat(60), window.get(0).getText(), "应保留最新的连续后缀");
    }
}
