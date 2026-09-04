package com.qian.qianaiagent.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qian.qianaiagent.agent.model.AgentState;
import com.qian.qianaiagent.agent.trace.AgentTrace;
import com.qian.qianaiagent.agent.trace.TraceStep;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.qian.qianaiagent.interview.QuizApp;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 * <p>
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    // 核心属性
    private String name;

    // 提示词
    private String systemPrompt;
    private String nextStepPrompt;

    // 代理状态
    private AgentState state = AgentState.IDLE;

    // 执行步骤控制
    private int currentStep = 0;

    private int maxSteps = 10;

    // LLM 大模型
    private ChatClient chatClient;

    // Memory 记忆（需要自主维护会话上下文）
    private List<Message> messageList = new ArrayList<>();

    /**
     * 运行代理
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public String run(String userPrompt) {
        // 1、基础校验
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        // 2、执行，更改状态
        this.state = AgentState.RUNNING;
        // 记录消息上下文
        messageList.add(new UserMessage(userPrompt));
        // 保存结果列表
        List<String> results = new ArrayList<>();
        try {
            // ============================================================
            // 🧠 任务 3：初始化 AgentTrace —— 在 try 块开始处创建
            // ============================================================
            AgentTrace trace = AgentTrace.builder()
                    .agentName(this.name)
                    .startTime(LocalDateTime.now())
                    .steps(new ArrayList<>())
                    .build();
            // 执行循环
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                // 🧠 任务 3：每一步执行前记录开始时间，执行后构建 TraceStep 加入 trace
                long stepStart = System.currentTimeMillis();
                // 单步执行
                String stepResult = step();
                long stepDuration = System.currentTimeMillis() - stepStart;
                // 构建这一步的 TraceStep
                TraceStep traceStep = TraceStep.builder()
                        .stepNumber(stepNumber)
                        .stepType("STEP")
                        .whatHappened(stepResult)
                        .timestamp(LocalDateTime.now())
                        .durationMs(stepDuration)
                        .resultSummary(stepResult.length() > 200
                                ? stepResult.substring(0, 200) + "..."
                                : stepResult)
                        .build();
                trace.getSteps().add(traceStep);

                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }
            // ============================================================
            // 🧠 任务 3：循环结束后，封口 AgentTrace 并打印 JSON 日志
            // ============================================================
            trace.setEndTime(LocalDateTime.now());
            trace.setFinalState(state.name());
            String traceJson = JSONUtil.toJsonPrettyStr(trace);
            log.info("Agent Trace:\n{}", traceJson);

            // ============================================================
            // 🧠 任务 4：把 Trace 存到文件 logs/traces/{agentName}_{时间戳}.json
            // ============================================================
            // 时间戳格式：20250621_143025（年月日_时分秒）
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File traceDir = new File("logs/traces");
            if (!traceDir.exists()) {
                traceDir.mkdirs();
            }
            File traceFile = new File(traceDir, this.name + "_" + timestamp + ".json");
            try (FileWriter writer = new FileWriter(traceFile)) {
                writer.write(traceJson);
                log.info("Trace 已保存到文件：{}", traceFile.getAbsolutePath());
            } catch (IOException e) {
                log.error("保存 Trace 文件失败", e);
            }
            // ============================================================
            // 检查是否超出步骤限制
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            // 3、清理资源
            this.cleanup();
        }
    }

    /**
     * 运行代理（流式输出，MVC 原生 SSE）
     *
     * @param userPrompt 用户提示词
     * @return SseEmitter 流式响应，每步结果独立推送，结束后发送 "[DONE]"
     */
    public SseEmitter runStream(String userPrompt) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(300000L); // 5 分钟超时
        // 使用线程异步处理，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            // 1、基础校验
            try {
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误：无法从状态运行代理：" + this.state);
                    sseEmitter.send("[DONE]");
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：不能使用空提示词运行代理");
                    sseEmitter.send("[DONE]");
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);   //出现异常关闭
            }
            // 2、执行，更改状态
            this.state = AgentState.RUNNING;
            // 记录消息上下文
            messageList.add(new UserMessage(userPrompt));
            try {
                // AgentTrace 初始化
                AgentTrace trace = AgentTrace.builder()
                        .agentName(this.name)
                        .startTime(LocalDateTime.now())
                        .steps(new ArrayList<>())
                        .build();
                // 执行循环
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);
                    // 单步执行
                    long stepStart = System.currentTimeMillis();
                    String stepResult = step();
                    long stepDuration = System.currentTimeMillis() - stepStart;
                    // 记录 TraceStep
                    TraceStep traceStep = TraceStep.builder()
                            .stepNumber(stepNumber)
                            .stepType("STEP")
                            .whatHappened(stepResult)
                            .timestamp(LocalDateTime.now())
                            .durationMs(stepDuration)
                            .resultSummary(stepResult.length() > 200
                                    ? stepResult.substring(0, 200) + "..."
                                    : stepResult)
                            .build();
                    trace.getSteps().add(traceStep);
                    // 输出当前每一步的结果到 SSE（stepResult 自带 💬/✅ 等标识，无需再加 "Step N:" 前缀）
                    sseEmitter.send(stepResult);
                }
                // 检查是否超出步骤限制
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    sseEmitter.send("执行结束：达到最大步骤（" + maxSteps + "）");
                }
                // 封口 Trace
                trace.setEndTime(LocalDateTime.now());
                trace.setFinalState(state.name());
                String traceJson = JSONUtil.toJsonPrettyStr(trace);
                log.info("Agent Trace:\n{}", traceJson);
                // 保存 Trace 文件
                saveTraceToFile(traceJson);
                // 发送结束标记
                sseEmitter.send("[DONE]");
                // 正常完成
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                try {
                    sseEmitter.send("执行错误：" + e.getMessage());
                    sseEmitter.send("[DONE]");
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                // 3、清理资源
                this.cleanup();
            }
        });
        // 设置超时回调
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timeout");
        });
        // 设置完成回调
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    /**
     * 定义单个步骤
     *
     * @return
     */
    public abstract String step();

    /** 保存 Trace 到文件（run() 和 runStream() 共用） */
    private void saveTraceToFile(String traceJson) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File traceDir = new File("logs/traces");
            if (!traceDir.exists()) {
                traceDir.mkdirs();
            }
            File traceFile = new File(traceDir, this.name + "_" + timestamp + ".json");
            try (FileWriter writer = new FileWriter(traceFile)) {
                writer.write(traceJson);
                log.info("Trace 已保存到文件：{}", traceFile.getAbsolutePath());
            }
        } catch (IOException e) {
            log.error("保存 Trace 文件失败", e);
        }
    }

    /**
     * 清理资源
     */
    protected void cleanup() {
        // 子类可以重写此方法来清理资源
    }
}
