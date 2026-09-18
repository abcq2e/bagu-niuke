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

class TruncationPreservesMarkerTest {

    @TempDir
    Path tempDir;

    private static final String MARKER = "【方向切换】旧方向考察结束";

    @Test
    void truncationKeepsTopicSwitchMarker() {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        String chatId = "trunc";

        memory.add(chatId, List.of(new SystemMessage(MARKER)));
        List<Message> filler = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            filler.add(new UserMessage("m" + i));
        }
        memory.add(chatId, filler);

        List<Message> back = memory.get(chatId);
        assertEquals(200, back.size(), "应被截断到上限");
        assertTrue(back.stream().anyMatch(m -> m.getText().contains(MARKER)),
                "【方向切换】绝不能被截断丢弃");
    }

    @Test
    void truncationUnchangedWhenNoMarker() {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        String chatId = "plain";

        List<Message> filler = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            filler.add(new UserMessage("m" + i));
        }
        memory.add(chatId, filler);

        List<Message> back = memory.get(chatId);
        assertEquals(200, back.size());
        // 无受保护消息时行为不变：保留最新的 200 条
        assertEquals("m249", back.get(back.size() - 1).getText());
        assertEquals("m50", back.get(0).getText());
    }

    @Test
    void replaceMessagesAlsoPreservesMarker() {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        String chatId = "replace";
        List<Message> input = new ArrayList<>();
        input.add(new SystemMessage(MARKER));
        for (int i = 0; i < 250; i++) {
            input.add(new UserMessage("m" + i));
        }
        memory.replaceMessages(chatId, input);

        List<Message> back = memory.get(chatId);
        assertEquals(200, back.size());
        assertTrue(back.stream().anyMatch(m -> m.getText().contains(MARKER)));
    }
}
