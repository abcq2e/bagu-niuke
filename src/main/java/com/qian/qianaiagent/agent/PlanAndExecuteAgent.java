package com.qian.qianaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.qian.qianaiagent.agent.model.AgentState;
import com.qian.qianaiagent.agent.plan.TaskPlan;
import com.qian.qianaiagent.agent.plan.TaskStep;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Plan-and-Execute 模式的 Agent：先让 LLM 生成 {@link TaskPlan}，再逐步交给
 * {@link BaseAgent#executeLoop} 执行。
 *
 * <p><b>分层约定</b>：本类只负责<b>机制</b>（生成计划、按步驱动执行循环、把整个计划
 * 收敛到同一条轨迹）。「规划提示词模板」与「复杂度预判断策略」留给具体子类
 * —— 它们与子类的工具集强相关，放进来会把工具细节泄漏到机制层。
 *
 * <p><b>执行模型</b>：N 轮内层循环<b>串行</b>，不是嵌套递归。每个子步骤 = 一次
 * {@link #executeLoop}（内部自行把 state 置回 RUNNING），因此步骤之间不需要
 * 复位到 IDLE；也正因为不再复用 {@code run()}，轨迹只初始化一次，多步不会互相覆盖。
 *
 * <p><b>预算</b>：{@code maxSteps} 是<b>每个子目标</b>的 ReAct 循环预算（保持原义）。
 * 共享一份预算会让 N 步计划每步只剩几次迭代，低于最小可用循环而必然中途截断，
 * 因此计划长度另有 {@link #MAX_PLAN_STEPS} 硬上限；需要严格成本上限时设
 * {@link #maxTotalSteps}。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class PlanAndExecuteAgent extends ToolCallAgent {

    /** 计划长度硬上限：LLM 给出超长计划时只执行前 N 步 */
    protected static final int MAX_PLAN_STEPS = 5;

    /** 整个计划的累计步数护栏；{@code <=0}（默认）表示只受 maxSteps × MAX_PLAN_STEPS 约束 */
    private int maxTotalSteps = 0;

    /** 最近一次生成的计划（调试/断言可见） */
    private TaskPlan taskPlan;

    protected PlanAndExecuteAgent(ToolCallback[] availableTools, ChatClient chatClient) {
        super(availableTools, chatClient);
    }

    // ==================== 交给子类（与工具集强相关） ====================

    /**
     * 规划用的 system prompt 模板，<b>必须含一个 {@code %s} 占位符</b>用于注入用户目标。
     * <p>用抽象方法而非字段/setter：缺模板会变成编译错误，而不是生产环境第一次
     * 复杂提问时才暴露的 null。
     */
    protected abstract String planSystemPromptTemplate();

    /** 任务是否复杂到需要先规划（关键词/长度策略由子类决定，避免不必要的一次 LLM 调用）。 */
    protected abstract boolean needsPlanning(String userPrompt);

    // ==================== 机制 ====================

    @Override
    protected LoopExit executeStrategy(String userPrompt, Consumer<StepOutcome> onStep) {
        TaskPlan plan = tryGeneratePlan(userPrompt);
        if (plan == null) {
            // 降级：单轮 ReAct，行为与拆类前一致
            return super.executeStrategy(userPrompt, onStep);
        }
        return executePlan(userPrompt, plan, onStep);
    }

    /** 返回 null = 不需要规划或规划失败，由调用方降级 ReAct */
    private TaskPlan tryGeneratePlan(String userPrompt) {
        if (!needsPlanning(userPrompt)) {
            log.info("{} 任务简单，直接走 ReAct 模式", getName());
            return null;
        }
        log.info("{} 任务复杂，尝试 Plan-and-Execute 模式", getName());
        TaskPlan plan = generatePlan(userPrompt);
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            log.info("{} 计划生成失败，降级为 ReAct 模式", getName());
            return null;
        }
        this.taskPlan = plan;
        return plan;
    }

    /**
     * 调 LLM 生成任务计划，失败返回 null。
     *
     * <p>{@code protected} 而非 private：纯单元测试可覆写本方法注入固定计划，完全不碰 LLM。
     *
     * @param userGoal 用户原始目标
     */
    protected TaskPlan generatePlan(String userGoal) {
        try {
            return getChatClient()
                    .prompt()
                    .system(planSystemPromptTemplate().formatted(userGoal))
                    .user(userGoal)
                    .call()
                    .entity(TaskPlan.class);
        } catch (Exception e) {
            log.warn("Plan generate failed", e);
            return null;
        }
    }

    /** 串行执行计划的每一级子步骤（见类注释「执行模型」）。 */
    private LoopExit executePlan(String userPrompt, TaskPlan plan, Consumer<StepOutcome> onStep) {
        List<TaskStep> allSteps = plan.getSteps();
        List<TaskStep> steps = allSteps.size() > MAX_PLAN_STEPS
                ? allSteps.subList(0, MAX_PLAN_STEPS)
                : allSteps;
        if (steps.size() < allSteps.size()) {
            log.warn("{} 计划过长（{} 步），本次只执行前 {} 步",
                    getName(), allSteps.size(), MAX_PLAN_STEPS);
        }
        String goal = StrUtil.isBlank(plan.getGoal()) ? userPrompt : plan.getGoal();

        // ① 原始目标原样入上下文：Plan 模式下「总目标不被子步骤淹没」的关键
        getMessageList().add(new UserMessage(userPrompt));
        // ② 计划本身进轨迹：事后可以对照「计划蓝图 vs 实际执行」
        recordTraceStep("PLAN", "生成执行计划，共 " + steps.size() + " 步",
                describePlan(plan, steps), null, null);
        notify(onStep, "[计划] 已生成 " + steps.size() + " 步："
                + steps.stream().map(TaskStep::getDescription).collect(Collectors.joining(" | ")));

        for (int i = 0; i < steps.size(); i++) {
            // ③ 可选的总预算护栏（默认关闭）
            if (getMaxTotalSteps() > 0 && getCurrentStep() >= getMaxTotalSteps()) {
                notify(onStep, "[计划] 已达总步数上限（" + getMaxTotalSteps()
                        + "），剩余 " + (steps.size() - i) + " 步未执行");
                break;
            }
            TaskStep step = steps.get(i);
            step.setStatus(TaskStep.StepStatus.IN_PROGRESS);
            log.info("Plan 模式执行第 {}/{} 步：{}", i + 1, steps.size(), step.getDescription());
            // ④ 每个子步骤独立的 UserMessage；执行循环本身不碰消息列表
            getMessageList().add(new UserMessage(buildStepPrompt(goal, i + 1, steps.size(), step)));

            LoopExit exit = executeLoop(onStep);
            if (exit == LoopExit.ERROR) {
                step.setStatus(TaskStep.StepStatus.FAILED);
                notify(onStep, "[计划] 第 " + (i + 1) + " 步执行出错，任务中止");
                return LoopExit.ERROR;      // state 仍为 ERROR，后续步骤不再执行
            }
            if (exit == LoopExit.MAX_STEPS) {
                step.setStatus(TaskStep.StepStatus.FAILED);
                notify(onStep, "[计划] 第 " + (i + 1) + " 步达到步数上限（"
                        + getMaxSteps() + "），继续下一步");
            } else {
                step.setStatus(TaskStep.StepStatus.COMPLETED);
            }
        }
        // 全部子步骤跑完（个别步数超限不阻塞收尾）
        setState(AgentState.FINISHED);
        return LoopExit.FINISHED;
    }

    /**
     * 子步骤提示词。
     * <p>明确要求"不要调用 TerminateTool"：否则 {@code ToolCallAgent.act()} 会在每个
     * 子步骤结束时就把 state 置为 FINISHED，子步骤会经由 doTerminate 出口而非
     * "think() 判定无工具可调"出口结束。
     */
    private String buildStepPrompt(String goal, int index, int total, TaskStep step) {
        return """
                总目标：%s
                当前是第 %d/%d 步，请只完成这一步：%s
                完成本步后用一句话汇报结果，不要调用 TerminateTool（后面还有步骤）。
                """.formatted(goal, index, total, stepDescription(step));
    }

    /**
     * 取步骤描述，缺失时给出可继续执行的兜底文案。
     * <p>兜底并非防御性编程洁癖：{@code PLAN_SYSTEM_PROMPT} 里让 LLM 输出的字段名是
     * {@code stepDesc}，而 {@link TaskStep} 的字段是 {@code description}，两者对不上。
     * Spring AI 的 {@code .entity()} 会另行追加按 TaskStep 生成的 schema 说明，
     * LLM 最终按哪一套输出并不确定 —— 这里保证无论如何都不会把 "null" 送进提示词。
     */
    private static String stepDescription(TaskStep step) {
        return StrUtil.isBlank(step.getDescription())
                ? "（计划未提供这一步的描述，请根据总目标自行判断该做什么）"
                : step.getDescription();
    }

    private String describePlan(TaskPlan plan, List<TaskStep> steps) {
        return "goal=" + plan.getGoal() + "; steps=" + steps.stream()
                .map(PlanAndExecuteAgent::stepDescription).collect(Collectors.joining(" -> "));
    }

    /** 回传一条不计入步号的提示信息 */
    private static void notify(Consumer<StepOutcome> onStep, String text) {
        if (onStep != null) {
            onStep.accept(StepOutcome.notice(text));
        }
    }
}
