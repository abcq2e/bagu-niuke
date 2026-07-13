package com.qian.qianaiagent.learn;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 从零理解 ReAct 循环的渐进式练习
 *
 * 目标：在一个类里把 ReAct 循环写出来，再回头理解 BaseAgent/ReActAgent/ToolCallAgent 为什么那样设计
 */
@SpringBootTest
@Slf4j
public class StepByStepReActTest {

    @Qualifier("chatModel")
    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ToolCallback[] allTools;

    // ==================== 第一关 ====================
    // 目标：调用一次 AI，拿到文本回复

    @Test
    public void level1_simpleChat() {
        // Step 1: 创建一个 ChatClient，它是对 AI 大模型的封装
        //          chatModel 是 Spring 自动注入的，代表可以调哪个模型（通义千问/Ollama等）
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        // Step 2: 构造用户消息
        UserMessage userMessage = new UserMessage("你好");

        // Step 3: 发起调用
        //   .prompt(new Prompt(userMessage)) → 把消息包装成 Prompt
        //   .call()                          → 同步调用 AI
        //   .chatResponse()                  → 拿到 ChatResponse 对象
        ChatResponse response = chatClient.prompt(new Prompt(userMessage))
                .call()
                .chatResponse();

        // Step 4: 从响应里取出 AI 回复的文本
        //   response.getResult()           → 拿到单个结果
        //           .getOutput()           → 拿到 AssistantMessage（AI 说的话）
        //           .getText()             → 提取纯文本
        String aiReply = response.getResult().getOutput().getText();
        System.out.println("AI 回复：" + aiReply);
    }

    // ==================== 第二关 ====================
    // 目标：让 AI 知道有哪些工具可以用，并拿到 AI 的工具调用决策
    //
    // 💡 引导问题（先想清楚再写）：
    // 1. 怎么把工具列表传给 ChatClient？（方法链：.prompt().user().???）
    // 2. ChatResponse 的调用链是什么？→ .getResult() → .getOutput() → ???
    // 3. getToolCalls() 返回什么类型？如果 AI 不需要调工具，列表是什么样的？
    // 4. 遍历 ToolCall 时，.name() 和 .arguments() 分别返回什么？
    //
    // 📖 基础知识: 方法链 .prompt(new Prompt(msg)).call().chatResponse()
    //            每个 .xxx() 返回一个新对象，你可以继续调用它的方法

    @Test
    public void level2_getToolCallDecision() {
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        // 你的代码写在这里 ↓






        // 你写的代码 ↑
    }

    // ==================== 第三关 ====================
    // 目标：真正执行 AI 决定的工具，拿到工具返回结果
    //
    // 💡 引导问题:
    // 1. ToolCallingManager.executeToolCalls() 需要哪两个参数？
    // 2. ToolExecutionResult.conversationHistory() 返回什么？包含哪些消息？
    //    （提示：是全新的列表还是追加？包含 UserMessage + AssistantMessage + ToolResponseMessage？）
    // 3. 旧的消息列表还需要吗？用新的替换旧的，还是追加？
    // 4. 如何从 conversationHistory() 的最后一条消息中提取工具返回数据？

    @Test
    public void level3_executeTool() {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

        // 你的代码写在这里 ↓






        // 你写的代码 ↑
    }

    // ==================== 第四关 ====================
    // 目标：把手动一轮变成循环，AI 看到工具结果后继续决策，直到它说"结束了"
    //
    // 💡 引导问题:
    // 1. 循环的终止条件有几个？（提示：至少两个 — 没有 ToolCall 了 / 调用了 terminate）
    // 2. 如何判断 AI 调用了 doTerminate 工具？
    //    （提示：遍历 ToolResponseMessage.getResponses()，检查 .name() 是否等于 "doTerminate"）
    // 3. 循环最多执行几轮？不加限制会发生什么？
    // 4. 每轮结束后消息列表如何维护？
    //    ⚠️ executeToolCalls 返回的 conversationHistory() 已经是完整的新列表了
    // 5. 每条消息是什么类型？ToolResponseMessage 在第几条？

    @Test
    public void level4_reactLoop() {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

        // 你的代码写在这里 ↓






        // 你写的代码 ↑
    }

    // ==================== 第五关（挑战） ====================
    // 目标：给 AI 加 SystemPrompt + NextStepPrompt，并抽取成可复用的方法
    //
    // 💡 引导问题:
    // 1. SystemPrompt 应该加在哪里？.prompt().system(...) 还是加到消息列表里？
    //    （提示：两者的语义不同，.system() 是给模型的指令，消息列表是对话历史）
    // 2. NextStepPrompt 应该每轮都加还是只加一次？
    //    （提示：想想 ToolCallAgent.think() 第62行，每轮都加了）
    // 3. 抽取方法时，方法签名怎么设计？reactLoop(String userPrompt) 还是更复杂？
    // 4. 这个方法和 ToolCallAgent 的 think()/act() 有什么区别？
    //    （完成这关后去读 ToolCallAgent.java，你会恍然大悟）
    // 5. 变量作用域：哪些变量应该是方法参数？哪些可以留在方法体内？

    @Test
    public void level5_withPrompts() {
        // 你的代码写在这里 ↓






        // 你写的代码 ↑
    }
}
