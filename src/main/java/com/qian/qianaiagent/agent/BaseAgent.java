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
import java.util.function.Consumer;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 * <p>
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 *
 * <p><b>执行循环的结构</b>：{@link #run(String)} 与 {@link #runStream(String)}
 * 只负责"结果怎么输出"，两者共用 {@link #executeStrategy} 这一个多态点；
 * 后者默认转调 {@link #executeLoop} 跑单目标的 ReAct 循环。子类重写
 * {@code executeStrategy} 即可改变执行策略（如 Plan-and-Execute），
 * 且同步/流式两条路径同时生效。
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

    // 当前这次执行的轨迹（run()/runStream() 赋值）。提升为成员而非局部变量，
    // 是为了让子类在 step() 内部能够追加"细粒度"轨迹步骤（LLM_CALL/TOOL_CALL/TOOL_RESULT）。
    private AgentTrace currentTrace;

    // ============================================================
    // 执行循环的类型与钩子
    // ============================================================

    /** 执行循环的退出原因。 */
    protected enum LoopExit {
        /** 任务自行结束（think() 判定无工具可调，或子类主动收尾） */
        FINISHED,
        /** 步数预算耗尽，被迫中断 */
        MAX_STEPS,
        /** 不可恢复异常 */
        ERROR
    }

    /**
     * 单步执行结果，供 {@link #executeStrategy} 回传给输出层。
     *
     * @param stepNumber 累计步号；{@code <= 0} 表示"非 ReAct 步的提示信息"（如计划横幅），
     *                   {@link #run(String)} 不会给它加 "Step N: " 前缀
     * @param text       该步的原始文本（无前缀）
     * @param durationMs 耗时毫秒；{@code -1} 表示未计时
     */
    protected record StepOutcome(int stepNumber, String text, long durationMs) {

        public static StepOutcome of(int stepNumber, String text, long durationMs) {
            return new StepOutcome(stepNumber, text, durationMs);
        }

        /** 构造一条提示信息（计划横幅、中止说明等），不计入步号。 */
        public static StepOutcome notice(String text) {
            return new StepOutcome(0, text, -1);
        }

        public boolean isNotice() {
            return stepNumber <= 0;
        }
    }

    /**
     * 执行策略钩子：{@link #run(String)} 与 {@link #runStream(String)} 的唯一公共执行体，
     * 两者只差"结果怎么输出"。
     *
     * <p><b>子类重写契约</b>：
     * <ol>
     *   <li>自行把本轮目标的 {@link UserMessage} 加入 messageList —— 执行循环不碰消息列表；</li>
     *   <li>通过 {@code onStep} 回传每一步的原始文本。这是流式路径唯一的前端输出通道，
     *       漏调等于前端空屏；</li>
     *   <li>可以多次调用 {@link #executeLoop}（如 Plan-and-Execute 逐个子步骤驱动），
     *       但<b>不得重建 currentTrace</b> —— 一次执行只有一条轨迹；</li>
     *   <li>返回时 state 必须是 {@code FINISHED} 或 {@code ERROR}。</li>
     * </ol>
     *
     * <p>默认实现 = 单目标 ReAct：加入一条 UserMessage，跑一次执行循环。
     *
     * @param userPrompt 用户提示词
     * @param onStep     每步回调（可为 null）
     * @return 循环退出原因
     */
    protected LoopExit executeStrategy(String userPrompt, Consumer<StepOutcome> onStep) {
        messageList.add(new UserMessage(userPrompt));
        return executeLoop(onStep);
    }

    /**
     * 执行"一个目标"的 ReAct / 工具循环。
     *
     * <p><b>前提（调用方保证）</b>：
     * <ul>
     *   <li>本轮目标的 UserMessage 已加入 messageList —— 本方法不碰消息列表；</li>
     *   <li>currentTrace 已初始化 —— 本方法不负责轨迹的生命周期。</li>
     * </ul>
     *
     * <p><b>退出保证</b>：state 一定是 FINISHED 或 ERROR。可以安全地连续调用多次
     * （内部会自行把 state 置回 RUNNING），因此 Plan-and-Execute 的多个子步骤之间
     * 不需要把 state 复位成 IDLE。
     *
     * @param onStep 每步回调（可为 null）
     * @return 循环退出原因
     */
    protected LoopExit executeLoop(Consumer<StepOutcome> onStep) {
        this.state = AgentState.RUNNING;
        int iterations = 0;
        while (this.state == AgentState.RUNNING && iterations < maxSteps) {
            currentStep++;      // 累计步号：跨 Plan 子步骤单调递增
            iterations++;
            log.info("Executing step {}/{}", iterations, maxSteps);
            long stepStart = System.currentTimeMillis();
            String stepResult = step();
            long durationMs = System.currentTimeMillis() - stepStart;
            if (!shouldRecordFineGrainedTrace()) {
                recordCoarseStepTrace(stepResult, durationMs);
            }
            if (onStep != null) {
                onStep.accept(StepOutcome.of(currentStep, stepResult, durationMs));
            }
        }
        if (this.state == AgentState.ERROR) {
            return LoopExit.ERROR;
        }
        if (this.state == AgentState.RUNNING) {
            // while 的另一项条件（iterations < maxSteps）已不成立 = 预算被耗尽
            this.state = AgentState.FINISHED;
            return LoopExit.MAX_STEPS;
        }
        // 循环内部主动置了 FINISHED（think() 判定无工具可调，或子类收尾）
        return LoopExit.FINISHED;
    }

    /**
     * 运行代理
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public String run(String userPrompt) {
        validateRunnable(userPrompt);
        List<String> results = new ArrayList<>();
        try {
            this.state = AgentState.RUNNING;
            initTrace();
            LoopExit exit = executeStrategy(userPrompt, outcome ->
                    results.add(outcome.isNotice()
                            ? outcome.text()
                            : "Step " + outcome.stepNumber() + ": " + outcome.text()));
            if (exit == LoopExit.MAX_STEPS) {
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            finishTrace();
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
            boolean[] sentAny = {false};
            try {
                // 1、基础校验（文案是用户可见内容，逐字保留）
                if (this.state != AgentState.IDLE) {
                    safeSend(sseEmitter, "错误：无法从状态运行代理：" + this.state);
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    safeSend(sseEmitter, "错误：不能使用空提示词运行代理");
                    return;
                }
                // 2、执行，更改状态
                this.state = AgentState.RUNNING;
                initTrace();
                LoopExit exit = executeStrategy(userPrompt, outcome -> {
                    if (!safeSend(sseEmitter, outcome.text())) {
                        throw new IllegalStateException("SSE 连接已断开");
                    }
                    sentAny[0] = true;
                });
                if (exit == LoopExit.MAX_STEPS) {
                    sentAny[0] |= safeSend(sseEmitter, "执行结束：达到最大步骤（" + maxSteps + "）");
                }
                // 前端收到 [DONE] 会无条件 flush 当前 AI 消息；若一条内容事件都没发过，
                // 前端的 aiIdx 仍为 -1，flush 时会抛 TypeError（其下的兜底来不及执行）。
                // 因此保证每个流至少有一条非 [DONE] 事件。
                if (!sentAny[0]) {
                    safeSend(sseEmitter, "（本次执行没有产生可展示的内容）");
                }
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                safeSend(sseEmitter, "执行错误：" + e.getMessage());
            } finally {
                // 🔴 finishTrace() 必须早于 complete()：AgentChatController 的 onCompletion
                // 回调会读 getCurrentTrace() 交给评估，未封口的轨迹会被静默丢弃。
                finishTrace();
                this.cleanup();
                // 发送结束标记
                safeSend(sseEmitter, "[DONE]");
                // 正常完成
                sseEmitter.complete();
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

    protected boolean shouldRecordFineGrainedTrace() {
        return false;
    }

    /**
     * 前置校验：状态必须是 IDLE（防止同一实例重复调用导致 messageList/currentStep 混乱），
     * 提示词不能为空。异常文案是既有的对外契约，勿改。
     */
    protected void validateRunnable(String userPrompt) {
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
    }

    /**
     * 初始化本次执行的轨迹。每次执行恰好调用一次 —— Plan-and-Execute 的多个子步骤
     * 共用同一条轨迹，由各子步骤追加记录，不得重建。
     */
    protected void initTrace() {
        this.currentTrace = AgentTrace.builder()
                .agentName(this.name)
                .startTime(LocalDateTime.now())
                .steps(new ArrayList<>())
                .build();
    }

    /**
     * 封口并落盘本次执行的轨迹。幂等（已封口则直接返回）。
     * <p>调用时机必须早于 SseEmitter.complete()。
     */
    protected void finishTrace() {
        if (this.currentTrace == null || this.currentTrace.getEndTime() != null) {
            return;
        }
        this.currentTrace.setEndTime(LocalDateTime.now());
        this.currentTrace.setFinalState(this.state.name());
        String traceJson = JSONUtil.toJsonPrettyStr(this.currentTrace);
        log.info("Agent Trace:\n{}", traceJson);
        saveTraceToFile(traceJson);
    }

    /** 粗粒度轨迹：步号按"追加序"取，与 {@link #recordTraceStep} 同约定，杜绝重号。 */
    private void recordCoarseStepTrace(String stepResult, long durationMs) {
        if (this.currentTrace == null) {
            return;
        }
        TraceStep traceStep = TraceStep.builder()
                .stepNumber(this.currentTrace.getSteps().size() + 1)
                .stepType("STEP")
                .whatHappened(stepResult)
                .timestamp(LocalDateTime.now())
                .durationMs(durationMs)
                .resultSummary(stepResult.length() > 200
                        ? stepResult.substring(0, 200) + "..."
                        : stepResult)
                .build();
        this.currentTrace.getSteps().add(traceStep);
    }

    /**
     * 发送一条 SSE 事件。失败不抛异常（客户端可能已断开），返回是否发送成功。
     * <p>旧实现遇到 IOException 会走 completeWithError 但<b>不中断执行</b>，
     * 导致在一个死连接上继续跑完整个循环；改由调用方根据返回值决定是否中止。
     */
    private boolean safeSend(SseEmitter emitter, String text) {
        try {
            emitter.send(text);
            return true;
        } catch (Exception e) {
            log.warn("SSE 发送失败，判定客户端已断开: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 往当前轨迹里追加一条步骤（供子类在 step() 内记录细粒度事件）。
     *
     * @param stepType     步骤类型：LLM_CALL / TOOL_CALL / TOOL_RESULT
     * @param whatHappened 这步做了什么（人话描述）
     * @param resultSummary 结果摘要（自动截断到 200 字符）
     * @param toolName     工具名（仅 TOOL_CALL/TOOL_RESULT 有值）
     * @param toolInput    工具入参 JSON（仅 TOOL_CALL 有值）
     */
    //Trace步骤的链路追踪，追加步骤
    protected void recordTraceStep(String stepType, String whatHappened, String resultSummary,
                                   String toolName, String toolInput) {
        if (currentTrace == null) {
            return;
        }
        String safeSummary = resultSummary == null ? null
                : (resultSummary.length() > 200 ? resultSummary.substring(0, 200) + "..." : resultSummary);
        TraceStep traceStep = TraceStep.builder()
                .stepNumber(currentTrace.getSteps().size() + 1)
                .stepType(stepType)
                .whatHappened(whatHappened)
                .timestamp(LocalDateTime.now())
                .resultSummary(safeSummary)
                .toolName(toolName)
                .toolInput(toolInput)
                .build();
        currentTrace.getSteps().add(traceStep);
    }

    //保存文件
    /** 保存 Trace 到文件（run() 和 runStream() 共用） */
    private void saveTraceToFile(String traceJson) {
        try {
            // 毫秒精度：同一秒内的两次执行（快速降级运行、稳定性连跑）否则会互相覆盖
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
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
     * 当前这次执行的轨迹（{@link #run(String)} / {@link #runStream(String)} 执行期间赋值）。
     * <p>评估链路直接取此引用，避免扫 logs/traces 目录取"最新文件"——
     * 多用例连续跑或并发时会读到上一次的轨迹。
     */
    public AgentTrace getCurrentTrace() {
        return currentTrace;
    }

    /**
     * 清理资源
     */
    protected void cleanup() {
        // 子类可以重写此方法来清理资源
    }
}
