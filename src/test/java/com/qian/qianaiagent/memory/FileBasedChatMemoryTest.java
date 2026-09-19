package com.qian.qianaiagent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileBasedChatMemory 的角色还原与文件生命周期测试。
 */
class FileBasedChatMemoryTest {

    @TempDir
    Path tempDir;

    private FileBasedChatMemory memory() {
        return new FileBasedChatMemory(tempDir.toString());
    }

    // ===== 角色还原（落盘再读回，角色不能丢）=====

    @Test
    void restoresAllFourMessageRoles() {
        FileBasedChatMemory memory = memory();
        String chatId = "roles";
        memory.add(chatId, List.of(
                new UserMessage("u"),
                new SystemMessage("s"),
                new AssistantMessage("a")));

        List<Message> back = memory.get(chatId);
        assertEquals(MessageType.USER, back.get(0).getMessageType());
        assertEquals(MessageType.SYSTEM, back.get(1).getMessageType(),
                "SystemMessage 被读成 Assistant 会让【方向切换】指令降级成 AI 发言");
        assertEquals(MessageType.ASSISTANT, back.get(2).getMessageType());
    }

    @Test
    void restoresToolResponseWithResponses() {
        FileBasedChatMemory memory = memory();
        String chatId = "tool-resp";
        memory.add(chatId, List.of(new ToolResponseMessage(List.of(
                new ToolResponseMessage.ToolResponse("call_1", "getWeather", "{\"temp\":25}")))));

        List<Message> back = memory.get(chatId);
        assertEquals(1, back.size(), "TOOL 消息不应在还原时丢失");
        ToolResponseMessage restored = assertInstanceOf(ToolResponseMessage.class, back.get(0));
        assertEquals(1, restored.getResponses().size());
        assertEquals("call_1", restored.getResponses().get(0).id());
        assertEquals("getWeather", restored.getResponses().get(0).name());
        assertEquals("{\"temp\":25}", restored.getResponses().get(0).responseData());
    }

    @Test
    void restoresAssistantToolCalls() {
        FileBasedChatMemory memory = memory();
        String chatId = "asst-toolcalls";
        memory.add(chatId, List.of(new AssistantMessage("", java.util.Map.of(), List.of(
                new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":\"SH\"}")),
                List.of())));

        List<Message> back = memory.get(chatId);
        AssistantMessage restored = assertInstanceOf(AssistantMessage.class, back.get(0));
        assertTrue(restored.hasToolCalls(), "工具调用轮次丢失会让后续 TOOL 消息失去配对的 callId");
        assertEquals("call_1", restored.getToolCalls().get(0).id());
        assertEquals("getWeather", restored.getToolCalls().get(0).name());
    }

    @Test
    void roundTripsAToolCallingTurn() {
        FileBasedChatMemory memory = memory();
        String chatId = "full-turn";
        memory.add(chatId, List.of(
                new UserMessage("上海天气?"),
                new AssistantMessage("", java.util.Map.of(), List.of(
                        new AssistantMessage.ToolCall("c1", "function", "getWeather", "{\"city\":\"SH\"}")),
                        List.of()),
                new ToolResponseMessage(List.of(
                        new ToolResponseMessage.ToolResponse("c1", "getWeather", "晴 25 度"))),
                new AssistantMessage("上海今天晴，25 度。")));

        List<Message> back = memory.get(chatId);
        assertEquals(4, back.size());
        assertEquals(
                List.of(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL, MessageType.ASSISTANT),
                back.stream().map(Message::getMessageType).toList());
    }

    // ===== 会话 ID 校验（路径遍历防护）=====

    @Test
    void rejectsPathTraversalConversationIds() {
        FileBasedChatMemory memory = memory();
        for (String bad : List.of("../secret", "..\\secret", "a/b", "a\\b", "..", "")) {
            assertThrows(IllegalArgumentException.class,
                    () -> memory.get(bad), "应拒绝非法会话 ID: " + bad);
            assertThrows(IllegalArgumentException.class,
                    () -> memory.add(bad, List.of(new UserMessage("x"))), "应拒绝非法会话 ID: " + bad);
        }
    }

    @Test
    void rejectsUnsafeTitleUpdate() {
        FileBasedChatMemory memory = memory();
        assertThrows(IllegalArgumentException.class, () -> memory.updateTitle("../evil", "x"));
    }

    // ===== 标题生命周期 =====

    @Test
    void clearAlsoRemovesSidecarTitle() throws Exception {
        FileBasedChatMemory memory = memory();
        String chatId = "titled";
        memory.add(chatId, List.of(new UserMessage("hi")));
        memory.updateTitle(chatId, "我的标题");
        assertEquals("我的标题", memory.getCustomTitle(chatId));

        memory.clear(chatId);

        assertFalse(Files.exists(tempDir.resolve(chatId + ".json")), "消息文件应被删除");
        assertFalse(Files.exists(tempDir.resolve(chatId + ".title")),
                "标题旁路文件残留会让重建的同名会话继承旧标题");
    }

    @Test
    void deleteConversationAlsoRemovesSidecarTitle() throws Exception {
        FileBasedChatMemory memory = memory();
        String chatId = "titled-del";
        memory.add(chatId, List.of(new UserMessage("hi")));
        memory.updateTitle(chatId, "待删标题");

        assertTrue(memory.deleteConversation(chatId));

        assertFalse(Files.exists(tempDir.resolve(chatId + ".json")));
        assertFalse(Files.exists(tempDir.resolve(chatId + ".title")));
        assertFalse(memory.deleteConversation(chatId), "重复删除应返回 false");
    }

    // ===== 列表与计数 =====

    @Test
    void listConversationsReportsCountsAndCustomTitles() {
        FileBasedChatMemory memory = memory();
        memory.add("c1", List.of(new UserMessage("a"), new AssistantMessage("b")));
        memory.add("c2", List.of(new UserMessage("x")));
        memory.updateTitle("c2", "第二个会话");

        List<FileBasedChatMemory.ConversationInfo> list = memory.listConversations(1L);
        assertEquals(2, list.size());
        assertEquals(2, list.stream().filter(c -> c.chatId().equals("c1")).findFirst().orElseThrow().messageCount());
        FileBasedChatMemory.ConversationInfo c2 = list.stream()
                .filter(c -> c.chatId().equals("c2")).findFirst().orElseThrow();
        assertEquals(1, c2.messageCount());
        assertEquals("第二个会话", c2.title());
        // 无自定义标题时降级为「对话」
        assertEquals("对话", list.stream()
                .filter(c -> c.chatId().equals("c1")).findFirst().orElseThrow().title());
    }

    @Test
    void listConversationsCountStaysCorrectAfterReplaceAndClear() {
        FileBasedChatMemory memory = memory();
        memory.add("c1", List.of(new UserMessage("a"), new AssistantMessage("b"), new UserMessage("c")));
        assertEquals(3, countOf(memory, "c1"));

        memory.replaceMessages("c1", List.of(new UserMessage("only")));
        assertEquals(1, countOf(memory, "c1"), "replaceMessages 后计数必须跟着变");

        memory.clear("c1");
        assertTrue(memory.listConversations(1L).isEmpty(), "clear 后不应再出现在列表里");
    }

    /** 计数缓存必须与真实文件内容一致，不能被缓存带偏。 */
    @Test
    void countCacheAgreesWithFileContent() {
        FileBasedChatMemory memory = memory();
        memory.add("c1", List.of(new UserMessage("a")));
        memory.add("c1", List.of(new AssistantMessage("b")));
        assertEquals(2, countOf(memory, "c1"));
        assertEquals(2, memory.get("c1").size(), "文件里的真实条数应与列表计数一致");
    }

    private int countOf(FileBasedChatMemory memory, String chatId) {
        return memory.listConversations(1L).stream()
                .filter(c -> c.chatId().equals(chatId))
                .findFirst().orElseThrow()
                .messageCount();
    }

    // ===== 截断 =====

    @Test
    void addTruncatesToMaxMessages() {
        FileBasedChatMemory memory = memory();
        String chatId = "long";
        for (int i = 0; i < 250; i++) {
            memory.add(chatId, List.of(new UserMessage("m" + i)));
        }
        List<Message> back = memory.get(chatId);
        assertEquals(200, back.size());
        assertEquals("m50", back.get(0).getText(), "应保留最新的 200 条，丢弃最早的");
        assertEquals(200, countOf(memory, chatId));
    }
}
