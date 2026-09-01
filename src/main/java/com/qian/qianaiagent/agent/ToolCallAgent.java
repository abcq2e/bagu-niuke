package com.qian.qianaiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.qian.qianaiagent.agent.model.AgentState;
import com.qian.qianaiagent.agent.plan.TaskPlan;
import com.qian.qianaiagent.agent.plan.TaskStep;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.qian.qianaiagent.interview.QuizApp;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 记录每个工具的连续失败次数（工具名 → 连续失败次数）
    private final Map<String, Integer> toolFailCountMap;
    // 连续失败阈值：超过此次数后 Agent 应换方案
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    // Plan-and-Execute 模式下的任务计划（第 9 篇教程）
    private TaskPlan taskPlan;
    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    /** 最近一次 think() 中 AI 的文本回复（推理过程或最终答案） */
    private String lastThinkText = "";

    /** 工具返回数据在流式输出中的最大展示长度 */
    private static final int MAX_DISPLAY_LENGTH = 150;

    public ToolCallAgent(ToolCallback[] availableTools, ChatClient chatClient) {
        super();
        this.availableTools = availableTools;
        // this.chatClient = chatClient;  // ❌ 父类 BaseAgent 已有 chatClient 字段，这里会 shadowing
        super.setChatClient(chatClient);   // ✅ 用父类 setter，避免字段重复声明
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.toolFailCountMap = new HashMap<>();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动（含自愈逻辑）
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        // 1、注入 NextStepPrompt（每轮都加，驱动 ReAct 循环）
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        // 2、自愈检查：检测是否有工具连续失败超过阈值
        injectSelfHealingMessageIfNeeded();
        // 3、调用 AI 大模型，获取工具调用结果
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            // 记录响应，用于等下 Act
            this.toolCallChatResponse = chatResponse;
            // 4、解析工具调用结果，获取要调用的工具
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // 保存 AI 的文本回复（推理/最终答案），供流式输出展示
            this.lastThinkText = assistantMessage.getText() != null
                    ? assistantMessage.getText() : "";
            // 输出提示信息
            String result = this.lastThinkText;
            log.info(getName() + "的思考：" + result);
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            // 如果不需要调用工具，返回 false
            if (toolCallList.isEmpty()) {
                getMessageList().add(assistantMessage);
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            // 区分可恢复 vs 不可恢复异常
            String errorMsg = e.getMessage();
            boolean isRecoverable = errorMsg != null && (
                    errorMsg.contains("timeout") || errorMsg.contains("timed out")
                    || errorMsg.contains("429") || errorMsg.contains("rate")
                    || errorMsg.contains("limit") || errorMsg.contains("connection")
                    || errorMsg.contains("refused") || errorMsg.contains("reset"));
            if (isRecoverable) {
                // 可恢复异常（超时、限流、连接问题）—— Agent 可以重试或换方案
                log.warn("{} think() 可恢复异常: {}", getName(), errorMsg);
                getMessageList().add(new AssistantMessage(
                        "⚠️ API 调用异常（可恢复）：" + errorMsg
                        + "。建议：① 等待 3 秒后重试 1 次 ② 缩小查询范围 ③ 若持续失败则用已有知识回答用户。"));
            } else {
                // 不可恢复异常（API Key 错误、代码 Bug）—— 记录完整堆栈，停止 Agent
                log.error("{} think() 不可恢复异常", e);
                setState(AgentState.ERROR);
                getMessageList().add(new AssistantMessage(
                        "❌ 系统内部错误（不可恢复）：" + errorMsg
                        + "。无法继续执行，请向用户说明原因并建议检查配置或日志。"));
            }
            return false;
        }
    }

    /**
     * 重写 step()，让 AI 的文本回复在流式输出中可见。
     * <p>
     * 父类只在 think()→false 时返回固定字符串"思考完成 - 无需行动"，
     * 但 AI 的真正回复文本（lastThinkText）被丢弃了。
     * 这里把它拼进返回值，用户能看到 AI 说了什么。
     */
    @Override
    public String step() {
        try {
            boolean shouldAct = think();
            if (!shouldAct) {
                // 无需工具 → 直接展示 AI 的文本回复（如最终答案）
                if (!lastThinkText.isBlank()) {
                    return "💬 " + lastThinkText;
                }
                return "思考完成，无需行动";
            }
            return act();
        } catch (Exception e) {
            log.error("ReAct 步骤执行失败", e);
            return "步骤执行失败：" + e.getMessage();
        }
    }

    /** 自愈检查：工具连续失败超阈值时，向消息列表注入警告提示 */
    private void injectSelfHealingMessageIfNeeded() {
        for (Map.Entry<String, Integer> entry : toolFailCountMap.entrySet()) {
            int failCount = entry.getValue();
            if (failCount >= MAX_CONSECUTIVE_FAILURES) {
                String toolName = entry.getKey();
                String warning = String.format(
                        "⚠️ 工具 '%s' 已连续失败 %d 次（达到上限 %d 次）。"
                                + "请立即更换方案，改用其他工具，或向用户说明失败原因。不要再调用此工具。",
                        toolName, failCount, MAX_CONSECUTIVE_FAILURES);
                log.warn("{}{}", getName(), warning);
                getMessageList().add(new UserMessage(warning));
                // 重置计数，避免重复注入同一条警告
                toolFailCountMap.put(toolName, 0);
            }
        }
    }

    /** 判断工具返回结果是否为失败 */
    private boolean isFailureResponse(String response) {
        if (response == null || response.isEmpty()) {
            return false;
        }
        return response.startsWith("❌")
                || response.startsWith("⛔")
                || response.startsWith("Error")
                || (response.startsWith("⚠️") && response.contains("失败"));
    }

    /** 截断工具返回数据，提取关键信息用于流式展示 */
    private String truncateForDisplay(String responseData) {
        if (responseData == null || responseData.isEmpty()) {
            return "无返回数据";
        }
        if (responseData.length() <= MAX_DISPLAY_LENGTH) {
            return responseData;
        }
        // 优先取前面的摘要（通常以 "Answer:" 开头）
        int answerIdx = responseData.indexOf("Answer:");
        if (answerIdx >= 0) {
            int end = Math.min(answerIdx + MAX_DISPLAY_LENGTH, responseData.length());
            String snippet = responseData.substring(answerIdx, end).replace("\n", " ");
            return (end < responseData.length() ? snippet + "…" : snippet);
        }
        // 否则取前 N 个字符
        return responseData.substring(0, MAX_DISPLAY_LENGTH) + "…";
    }

    /**
     * 执行工具调用并处理结果（含失败计数 + 自愈逻辑）
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        // 调用工具
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult;
        try {
            toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        } catch (Exception e) {
            // 工具执行本身抛出异常（如 ToolCallingManager 内部错误）
            log.error("{} 工具执行异常", getName(), e);
            // 对所有尝试调用的工具累加失败次数
            //计数器进行初始化
            toolCallChatResponse.getResult().getOutput().getToolCalls().forEach(tc -> {
                toolFailCountMap.merge(tc.name(), 1, Integer::sum);
                log.warn("{} 工具失败计数: {}={}", getName(), tc.name(), toolFailCountMap.get(tc.name()));
            });
            return "工具执行异常：" + e.getMessage();
        }
        // 记录消息上下文
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
        }
        // 处理每个工具的返回结果：成功则清零失败计数，失败则累加
        // 🔴 流式展示优化：截断原始数据，避免几千字的搜索 JSON 直接吐给用户
        StringBuilder displayBuilder = new StringBuilder();
        StringBuilder logBuilder = new StringBuilder();
        for (var response : toolResponseMessage.getResponses()) {
            String toolName = response.name();
            String responseData = response.responseData();
            if (isFailureResponse(responseData)) {
                int newCount = toolFailCountMap.merge(toolName, 1, Integer::sum);
                log.warn("{} 工具 {} 执行失败（第 {} 次）: {}",
                        getName(), toolName, newCount,
                        responseData.length() > 100 ? responseData.substring(0, 100) + "..." : responseData);
                displayBuilder.append("❌ ").append(toolName).append(" 执行失败\n");
            } else {
                if (toolFailCountMap.getOrDefault(toolName, 0) > 0) {
                    log.info("{} 工具 {} 执行成功，清零失败计数", getName(), toolName);
                }
                toolFailCountMap.put(toolName, 0);
                // 截断：只展示摘要，完整数据在 messageList 里 LLM 能读到
                String summary = truncateForDisplay(responseData);
                displayBuilder.append("✅ ").append(toolName).append(" → ").append(summary).append("\n");
            }
            logBuilder.append("工具 ").append(toolName).append(" 返回的结果：").append(responseData);
        }
        String displayResult = displayBuilder.toString().trim();
        // 如果是终止工具，把 AI 的最终回答拼在前面
        if (terminateToolCalled && !lastThinkText.isBlank()) {
            displayResult = "💬 " + lastThinkText + "\n" + displayResult;
        }
        log.info(logBuilder.toString());
        return displayResult;
    }


    private static final String PLAN_SYSTEM_PROMPT = """
    ## 角色设定
    你是专业AI任务规划专家，擅长拆解复杂需求，输出有序、可执行的分步任务清单。
    你知晓Agent拥有3类工具：WebSearchTool联网检索、TerminalOperationTool终端命令、RagSearchTool本地知识库检索，规划每一步时必须匹配对应工具。

    ## 硬性输出规则
    1. 只返回纯JSON，禁止任何解释、前言、markdown、多余文字；
    2. JSON固定包含两层字段：
    - goal：字符串，完整复述用户原始总目标
    - steps：数组，每一项是单步任务对象，单步必须包含：
         stepDesc：本步骤要做什么
        toolName：执行该步骤需要调用的工具名称，三选一：WebSearchTool / TerminalOperationTool / RagSearchTool
        retryLimit：本步骤最大重试次数（固定2）
        failStrategy：步骤失败后的处理方案（重试/切换工具/终止任务）

    ## Few-Shot 标准示例（严格模仿此结构输出）
    {
        "goal": "分析SpringBoot项目启动慢问题并给出优化方案",
        "steps": [
            {
                "stepDesc": "检索SpringBoot启动慢通用优化方案",
                "toolName": "WebSearchTool",
                "retryLimit": 2,
                "failStrategy": "切换RagSearchTool查询本地知识库"
            },
            {
                "stepDesc": "执行mvn compile编译项目查看启动日志",
                "toolName": "TerminalOperationTool",
                "retryLimit": 2,
                "failStrategy": "重试2次后终止任务，告知用户权限不足"
            }
        ]
    }

    ## 规划约束
    1. 步骤顺序必须符合执行逻辑，先检索信息再操作本地文件；
    2. 每一步仅分配一个工具，禁止一步调用多个工具；
    3. 复杂需求必须拆分成多步，禁止合并多个操作到单一步骤；
    4. 若需求无需要工具的操作，直接给出仅终止的单步计划。

    ## 用户任务目标：%s
    """;


    // @formatter:off
    public TaskPlan generatePlan(String userGoal) {
        // ============================================
        // 🔴 你的代码写在这里
        // ============================================
        // 第 1 步：写规划 Prompt
        // 第 2 步：调 LLM 获取 TaskPlan（提示：看 QuizApp 第158行）
        try {
            TaskPlan taskPlan = getChatClient()        // ✅ 用 getter，不用子类字段
                    .prompt()
                    .system(PLAN_SYSTEM_PROMPT.formatted(userGoal))  // ✅ 格式化 %s 注入目标
                    .user(userGoal)
                    .call()
                    .entity(TaskPlan.class);
            return taskPlan;
        } catch (Exception e) {
            log.warn("Plan generate failed", e);

            return null;
        }
        // 第 3 步：try-catch 包裹，失败时 log.warn + return null
        // ============================================
     //   return null; // 先返回 null，等你实现后删除这行

    }


    // ✅ ========== 混用模式：判断 + 切换 ==========
    @Override
    public String run(String userPrompt) {
        // ===== 第 1 层：预判断 =====
        if (needsPlanning(userPrompt)) {
            log.info("{} 任务复杂，尝试 Plan-and-Execute 模式", getName());

            // ===== 第 2 层：调 LLM 生成计划 =====
            TaskPlan plan = generatePlan(userPrompt);
            this.taskPlan = plan;

            // ===== 第 3 层：遍历执行 =====
            if (plan != null && plan.getSteps() != null && !plan.getSteps().isEmpty()) {
                log.info("{} 计划生成成功，共 {} 步", getName(), plan.getSteps().size());
                List<String> stepResults = new ArrayList<>();
                for (int i = 0; i < plan.getSteps().size(); i++) {
                    TaskStep step = plan.getSteps().get(i);
                    step.setStatus(TaskStep.StepStatus.IN_PROGRESS);
                    log.info("Plan 模式执行第 {}/{} 步：{}", i + 1, plan.getSteps().size(), step.getDescription());
                    String stepResult = super.run(step.getDescription());
                    stepResults.add("Step " + (i + 1) + "：" + stepResult);
                    step.setStatus(TaskStep.StepStatus.COMPLETED);
                    setState(AgentState.IDLE);
                }
                return "Plan-and-Execute 完成（共 " + plan.getSteps().size() + " 步）：\n" + String.join("\n", stepResults);
            }
            // 计划失败 → 兜底 ReAct
            log.info("{} 计划生成失败，降级为 ReAct 模式", getName());
        } else {
            log.info("{} 任务简单，直接走 ReAct 模式", getName());
        }

        // ===== 兜底：ReAct 模式 =====
        return super.run(userPrompt);
    }

    // ============================================================
    // 🔴 你的任务：实现预判断方法 needsPlanning()
    // ============================================================
    // 3 关，从简单到进阶：
    //   第 1 关：太短的输入不规划 → userPrompt.length() < 阈值 → return false
    //   第 2 关：包含复杂任务特征词 → "分析""对比""优化""总结""报告""检查""重构" → return true
    //   第 3 关（可选）：提到文件路径 → ".java" ".xml" "src/" → return true
    //
    // 🔴 你的代码写在这里 ↓
    private boolean needsPlanning(String userPrompt) {
        // 太短的输入不规划
        if(userPrompt.length() < 60){
            return false;
        }
        // 必须包含复杂任务特征词才走 Plan（避免大部分日常问答触发多余的 LLM 调用）
        String[] keywords = {"分析以下", "对比", "优化", "总结一下", "深入分析", "检查代码", "重构"};
        for (String kw : keywords) {
            if(userPrompt.contains(kw)){
                return true;
            }
        }

        return false;
    }

}
