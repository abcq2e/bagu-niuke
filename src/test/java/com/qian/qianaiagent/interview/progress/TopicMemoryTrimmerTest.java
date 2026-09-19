package com.qian.qianaiagent.interview.progress;

import com.qian.qianaiagent.memory.FileBasedChatMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Path;
import java.util.ArrayList;
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

    @Test
    void trimToRecentNKeepsTopicSwitchMarker() {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        String chatId = "trim-marker";
        memory.add(chatId, List.of(
                new SystemMessage("【方向切换】旧方向结束。当前方向：【JVM】"),
                new SystemMessage("【本轮考题】\nJVM\nGC 算法有哪些？"),
                new UserMessage("u1"),
                new AssistantMessage("a1"),
                new UserMessage("u2"),
                new AssistantMessage("a2")));

        TopicMemoryTrimmer trimmer = new TopicMemoryTrimmer(memory);
        trimmer.trimToRecentN(chatId, 4);

        List<Message> after = memory.get(chatId);
        assertEquals(4, after.size(), "总数仍应为 n，实际: " + after.size());
        assertTrue(after.stream().anyMatch(m -> m.getText().contains("【方向切换】")),
                "【方向切换】锚点不得被 trimToRecentN 丢弃");
    }

    @Test
    void trimToRecentNUnchangedWhenNoMarker() {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        String chatId = "trim-plain";
        List<Message> msgs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            msgs.add(new UserMessage("m" + i));
        }
        memory.add(chatId, msgs);

        TopicMemoryTrimmer trimmer = new TopicMemoryTrimmer(memory);
        trimmer.trimToRecentN(chatId, 4);

        List<Message> after = memory.get(chatId);
        assertEquals(4, after.size());
        // 无受保护消息时行为不变：保留最新 4 条
        assertEquals("m4", after.get(0).getText());
        assertEquals("m7", after.get(3).getText());
    }
}
