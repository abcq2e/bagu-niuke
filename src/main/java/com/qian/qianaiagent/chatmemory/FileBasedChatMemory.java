package com.qian.qianaiagent.chatmemory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 JSON 文件持久化的对话记忆
 * <p>
 * 每个会话以 chatId.json 文件存储在磁盘上，
 * 服务重启后对话历史不丢失。
 * 写入前先备份，读取失败时保留原文件防止数据丢失。
 */
public class FileBasedChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(FileBasedChatMemory.class);

    /** 单个会话文件最大消息数 */
    private static final int MAX_MESSAGES_PER_FILE = 200;
    /** 建议最大会话文件数 */
    private static final int MAX_RECOMMENDED_FILES = 100;

    private final String baseDir;
    private final ObjectMapper objectMapper;

    public FileBasedChatMemory(String baseDir) {
        this.baseDir = baseDir;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        File dir = new File(baseDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        log.info("对话存储目录: {}", dir.getAbsolutePath());
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        List<Message> existing;

        if (file.exists()) {
            existing = readFromFile(file);
            if (existing == null) {
                // 读取失败，保留原文件不覆盖
                log.error("读取对话文件失败，跳过本次写入保护历史数据: {}", file.getPath());
                return;
            }
        } else {
            existing = new ArrayList<>();
        }

        existing.addAll(messages);

        // 🔴 容量保护：超过上限自动截断保留最近 N 条
        if (existing.size() > MAX_MESSAGES_PER_FILE) {
            int removed = existing.size() - MAX_MESSAGES_PER_FILE;
            existing = new ArrayList<>(existing.subList(removed, existing.size()));
            log.warn("对话 {} 超过 {} 条消息，已截断，移除最早 {} 条",
                    conversationId, MAX_MESSAGES_PER_FILE, removed);
        }

        writeToFile(file, existing);
    }

    @Override
    public List<Message> get(String conversationId) {
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            List<Message> messages = readFromFile(file);
            return messages != null ? messages : new ArrayList<>();
        }
        return new ArrayList<>();
    }

    @Override
    public void clear(String conversationId) {
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.delete();
            log.info("已删除对话文件: {}", file.getName());
        }
    }

    /**
     * 删除指定会话（永久删除）
     */
    public boolean deleteConversation(String chatId) {
        File file = getConversationFile(chatId);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                log.info("已删除对话: {}", chatId);
            }
            return deleted;
        }
        return false;
    }

    /**
     * 列出所有会话的概览信息
     */
    public List<ConversationInfo> listConversations() {
        File dir = new File(baseDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return Collections.emptyList();

        // 🔴 文件数量预警
        if (files.length > MAX_RECOMMENDED_FILES) {
            log.warn("对话文件数量 ({}) 超过建议上限 ({})，建议清理旧文件",
                    files.length, MAX_RECOMMENDED_FILES);
        }

        return Arrays.stream(files)
                .map(file -> {
                    String chatId = file.getName().replace(".json", "");
                    List<Message> messages = readFromFile(file);
                    if (messages == null) messages = new ArrayList<>();
                    // 🔴 优先用自定义标题
                    String customTitle = getCustomTitle(chatId);
                    String title = customTitle != null ? customTitle : extractTitle(messages);
                    long lastModified = file.lastModified();
                    int messageCount = messages.size();
                    return new ConversationInfo(chatId, title, lastModified, messageCount);
                })
                .sorted((a, b) -> Long.compare(b.lastModified, a.lastModified))
                .collect(Collectors.toList());
    }

    /**
     * 获取指定会话的完整消息列表
     */
    public List<Message> getConversation(String chatId) {
        return get(chatId);
    }

    // ===== 内部方法 =====

    /** 标题噪音词 —— 这些开头的消息不配当标题 */
    private static final java.util.Set<String> NOISE_WORDS = java.util.Set.of(
            "继续", "下一个", "换一个", "不知道", "不会", "不记得", "忘了",
            "下一个问题", "可以", "好", "行", "嗯", "哦", "是的", "对"
    );

    private String extractTitle(List<Message> messages) {
        if (messages.isEmpty()) return "空对话";
        // 🔴 优先取第一条非噪音的有效用户消息当标题
        for (Message msg : messages) {
            if ("USER".equals(msg.getMessageType().name())) {
                String text = msg.getText();
                if (text != null && !text.isBlank()) {
                    String trimmed = text.trim();
                    // 跳过噪音词
                    if (NOISE_WORDS.contains(trimmed) || trimmed.length() < 4) {
                        continue;
                    }
                    return trimmed.length() > 30 ? trimmed.substring(0, 30) + "…" : trimmed;
                }
            }
        }
        // 全是噪音 → 用第一条用户消息兜底
        for (Message msg : messages) {
            if ("USER".equals(msg.getMessageType().name())) {
                String text = msg.getText();
                if (text != null && !text.isBlank()) {
                    return "对话 " + text.trim();
                }
            }
        }
        return "对话 " + messages.size() + " 条消息";
    }

    /**
     * 更新对话标题（存到旁路元数据文件）
     */
    public void updateTitle(String conversationId, String newTitle) {
        File titleFile = new File(baseDir, conversationId + ".title");
        try {
            java.nio.file.Files.writeString(titleFile.toPath(), newTitle);
            log.info("标题已更新: {} → {}", conversationId, newTitle);
        } catch (IOException e) {
            log.error("保存标题失败: {}", e.getMessage());
        }
    }

    /**
     * 读取自定义标题（优先于 extractTitle 使用）
     */
    public String getCustomTitle(String conversationId) {
        File titleFile = new File(baseDir, conversationId + ".title");
        if (titleFile.exists()) {
            try {
                return java.nio.file.Files.readString(titleFile.toPath()).trim();
            } catch (IOException e) {
                // 读失败，降级
            }
        }
        return null;
    }

    /**
     * 从文件读取消息列表。读取失败返回 null（而非空列表），
     * 让调用方可以区分"文件不存在"和"文件损坏"。
     */
    private List<Message> readFromFile(File file) {
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(
                    file, new TypeReference<List<Map<String, Object>>>() {});
            return raw.stream()
                    .map(this::mapToMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            log.error("读取对话文件失败: {} — {}", file.getPath(), e.getMessage());
            return null;
        }
    }

    /**
     * 写入文件，先写临时文件再原子替换，防止写入中断损坏原文件。
     */
    private void writeToFile(File file, List<Message> messages) {
        File tmpFile = new File(file.getParent(), file.getName() + ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmpFile, messages);
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("保存对话文件失败: {} — {}", file.getPath(), e.getMessage());
            // 清理临时文件
            tmpFile.delete();
        }
    }

    /**
     * 将 Map 还原为 Spring AI Message
     *
     * ===== 🎯 Task 2: 你来完善 =====
     * 当前只处理了 USER 和默认（AssistantMessage）两种情况。
     * 但对话中有工具调用时，JSON 里会有 messageType = "TOOL_RESPONSE" 的消息。
     * 需要增加处理分支。
     *
     * 💡 引导问题：
     * 1. 去 data/chat-memory/ 目录找有工具调用的 JSON 文件，看 TOOL_RESPONSE 的结构是什么样的
     * 2. ToolResponseMessage 类有哪些构造函数？在 IDEA 里 Ctrl+N 搜索
     * 3. ToolResponseMessage.ToolResponse 需要哪些参数来构造？
     * 4. JSON 里的 responses 是个数组，你要怎么遍历它并转成 List<ToolResponse>？
     *
     * 📖 基础知识: 在现有 if-else 之前加 else if 判断 messageType 是否为 "TOOL_RESPONSE"
     * 📖 基础知识: @SuppressWarnings("unchecked") 是因为从 Map 取值需要强制类型转换
     */
    @SuppressWarnings("unchecked")
    private Message mapToMessage(Map<String, Object> map) {
        try {
            String messageType = (String) map.get("messageType");
            String text = (String) map.get("text");
            if (text == null) {
                Object content = map.get("content");
                if (content instanceof String) {
                    text = (String) content;
                } else if (content instanceof List) {
                    List<Map<String, Object>> contentList = (List<Map<String, Object>>) content;
                    for (Map<String, Object> part : contentList) {
                        if ("text".equals(part.get("type")) && part.get("text") instanceof String) {
                            text = (String) part.get("text");
                            break;
                        }
                    }
                }
            }
            if (text == null) text = "";
            if ("USER".equals(messageType)) {
                return new org.springframework.ai.chat.messages.UserMessage(text);
            } else {
                return new org.springframework.ai.chat.messages.AssistantMessage(text);
            }
        } catch (Exception e) {
            log.warn("消息反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private File getConversationFile(String conversationId) {
        return new File(baseDir, conversationId + ".json");
    }

    /**
     * 会话概览信息
     */
    public record ConversationInfo(String chatId, String title, long lastModified, int messageCount) {}
}
