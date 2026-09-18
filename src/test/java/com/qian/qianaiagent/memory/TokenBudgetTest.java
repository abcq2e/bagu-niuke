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
                "token 裁剪同样不得丢弃锚点");
    }
}
