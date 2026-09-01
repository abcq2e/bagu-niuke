package com.qian.qianaiagent.interview.progress;

import com.qian.qianaiagent.memory.FileBasedChatMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicMemoryTrimmerTest {

    @TempDir
    Path tempDir;

    @Test
    void trimKeepsSummaryAndPendingExamQuestion() {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        String chatId = "trim-1";
        for (int i = 0; i < 10; i++) {
            memory.add(chatId, List.of(new UserMessage("u" + i), new AssistantMessage("a" + i)));
        }
        // 换方向前刚出示的新题（必须保留，否则用户作答时 AI 失锚）
        memory.add(chatId, List.of(new AssistantMessage(
                "━━━━━━━━━━━━━━━━━━━━━━━━━\n【本轮考题】MySQL\n讲下覆盖索引\n━━━━━━━━━━━━━━━━━━━━━━━━━")));
        assertEquals(21, memory.get(chatId).size());

        TopicMemoryTrimmer trimmer = new TopicMemoryTrimmer(memory);
        trimmer.trimAfterAdvance(chatId, "计算机网络", "MySQL");
        List<Message> after = memory.get(chatId);
        assertTrue(after.get(0).getText().contains("计算机网络"));
        assertTrue(after.stream().anyMatch(m -> m.getText() != null && m.getText().contains("【本轮考题】MySQL")),
                "换方向裁剪必须保留待回答的【本轮考题】，否则点评会串到下一题");
        assertTrue(after.stream().anyMatch(m -> m.getText() != null && m.getText().contains("讲下覆盖索引")));
    }

    @Test
    void extractLastExamStemPrefersLatestQuestionBlock() {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        String chatId = "trim-extract";
        memory.add(chatId, List.of(new AssistantMessage(
                "【本轮考题】计算机网络\n除了 ARP 协议还有什么地址转换手段？")));
        memory.add(chatId, List.of(new UserMessage("不知道")));
        memory.add(chatId, List.of(new AssistantMessage(
                "点评...\n【本轮考题】MySQL\n讲下覆盖索引，覆盖索引是怎么用的？")));

        TopicMemoryTrimmer trimmer = new TopicMemoryTrimmer(memory);
        String stem = TopicMemoryTrimmer.extractLastExamStem(memory.get(chatId));
        assertEquals("讲下覆盖索引，覆盖索引是怎么用的？", stem);
    }
}
