package com.qian.qianaiagent.agent;

import com.qian.qianaiagent.agent.model.AgentState;
import com.qian.qianaiagent.agent.plan.TaskPlan;
import com.qian.qianaiagent.agent.plan.TaskStep;
import com.qian.qianaiagent.agent.trace.AgentTrace;
import com.qian.qianaiagent.agent.trace.TraceStep;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Plan-and-Execute 的纯单元测试：覆写 {@code generatePlan()} 注入固定计划，完全不调 LLM。
 *
 * <p>覆盖：多步共用一条轨迹（bug 4）、流式路径同样触发 Plan（bug 3）、
 * 不需要规划/规划失败时的降级、子步骤出错时中止计划。
 */
class PlanModeTest {

    static class ScriptedPlanAgent extends PlanAndExecuteAgent {

        private final int planSteps;
        private final boolean planningEnabled;
        private final boolean planGenerationFails;
        /** 模拟某个子步骤内部发生不可恢复异常 */
        boolean errorOnFirstThink = false;
        int thinkCalls = 0;
        int generatePlanCalls = 0;

        ScriptedPlanAgent(int planSteps, boolean planningEnabled, boolean planGenerationFails) {
            super(new ToolCallback[0], mock(ChatClient.class));
            this.planSteps = planSteps;
            this.planningEnabled = planningEnabled;
            this.planGenerationFails = planGenerationFails;
            setName("scriptedPlan");
            setMaxSteps(3);
        }

        @Override
        protected String planSystemPromptTemplate() {
            return "目标=%s";      // 只需覆盖抽象方法，无需真实提示词
        }

        @Override
        protected boolean needsPlanning(String userPrompt) {
            return planningEnabled;
        }

        @Override
        protected TaskPlan generatePlan(String userGoal) {
            generatePlanCalls++;
            if (planGenerationFails) {
                return null;       // 模拟"规划失败 → 降级 ReAct"
            }
            return TaskPlan.builder()
                    .goal(userGoal)
                    .steps(IntStream.rangeClosed(1, planSteps)
                            .mapToObj(i -> TaskStep.builder()
                                    .stepNumber(i)
                                    .description("子步骤" + i)
                                    .build())
                            .toList())
                    .build();
        }

        @Override
        public boolean think() {
            thinkCalls++;
            recordTraceStep("LLM_CALL", "think" + thinkCalls, "答案" + thinkCalls, null, null);
            if (errorOnFirstThink && thinkCalls == 1) {
                setState(AgentState.ERROR);
            }
            return false;          // 每步都直接给答案 → 内层循环各跑 1 次迭代
        }

        @Override
        protected String noActionText() {
            return "💬 子步骤完成";
        }
    }

    /** 轮询等待异步的 runStream 收尾（finishTrace 在 finally 中执行） */
    private static void awaitTrace(PlanAndExecuteAgent agent) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (agent.getCurrentTrace() == null || agent.getCurrentTrace().getEndTime() == null) {
            assertTrue(System.currentTimeMillis() < deadline, "runStream 未在 10s 内完成");
            Thread.sleep(20);
        }
    }

    /**
     * 回归 bug 4：多步计划必须共用同一条轨迹，而不是每步重建、互相覆盖。
     * 这直接决定 EvalRunner 能否看到完整计划（它取的就是 getCurrentTrace()）。
     */
    @Test
    void Plan模式多步共用一条trace且步号不重号() {
        ScriptedPlanAgent agent = new ScriptedPlanAgent(3, true, false);

        String out = agent.run("一个足够复杂、需要规划的任务");

        AgentTrace trace = agent.getCurrentTrace();
        assertNotNull(trace);
        assertNotNull(trace.getEndTime(), "trace 必须封口（EvalRunner 依赖 endTime 非空）");
        assertEquals("FINISHED", trace.getFinalState());

        List<String> thinks = trace.getSteps().stream()
                .filter(s -> "LLM_CALL".equals(s.getStepType()))
                .map(TraceStep::getWhatHappened)
                .toList();
        assertEquals(3, thinks.size(), "3 个子步骤的 LLM_CALL 必须落在同一条 trace 上，不能被覆盖");
        assertTrue(thinks.containsAll(List.of("think1", "think2", "think3")), thinks.toString());

        List<Integer> numbers = trace.getSteps().stream().map(TraceStep::getStepNumber).toList();
        assertEquals(numbers.size(), Set.copyOf(numbers).size(), "trace 步号不能重号: " + numbers);
        assertTrue(IntStream.range(1, numbers.size()).allMatch(i -> numbers.get(i) > numbers.get(i - 1)),
                "trace 步号必须单调递增: " + numbers);

        assertEquals(3, agent.thinkCalls, "三个子步骤都要执行");
        assertEquals(1, agent.generatePlanCalls);
        assertEquals(3, agent.getCurrentStep(), "currentStep 语义 = 跨子步骤的累计步数");
        assertEquals(AgentState.FINISHED, agent.getState());
        assertTrue(out.contains("子步骤完成"), out);
    }

    /**
     * 回归 bug 3：生产走的是 runStream()，Plan 必须在流式路径同样生效。
     * 修复前 ToolCallAgent 只重写了 run()，流式路径完全绕开规划。
     */
    @Test
    void 流式路径同样触发Plan模式() throws Exception {
        ScriptedPlanAgent agent = new ScriptedPlanAgent(2, true, false);

        SseEmitter emitter = agent.runStream("一个足够复杂、需要规划的任务");
        awaitTrace(agent);

        assertEquals(1, agent.generatePlanCalls, "流式路径必须走 executeStrategy → Plan 生效（bug 3 回归断言）");
        assertEquals(2, agent.thinkCalls, "两个子步骤都必须执行");
        AgentTrace trace = agent.getCurrentTrace();
        assertTrue(trace.getSteps().stream().anyMatch(s -> "PLAN".equals(s.getStepType())),
                "轨迹里应有 PLAN 记录：" + trace.getSteps());
        assertNotNull(emitter);
    }

    /** 前端硬契约：每个流至少一条内容事件，且以 [DONE] 收尾（否则前端 connecting 永不复位）。 */
    @Test
    void 流式路径推送内容事件并以DONE收尾() throws Exception {
        ScriptedPlanAgent agent = new ScriptedPlanAgent(2, true, false);

        SseEmitter emitter = agent.runStream("一个足够复杂、需要规划的任务");
        awaitTrace(agent);

        List<String> payloads = sentPayloads(emitter);
        assertTrue(payloads.stream().anyMatch("[DONE]"::equals), "必须发送 [DONE]: " + payloads);
        assertTrue(payloads.stream().anyMatch(p -> p.contains("子步骤完成")),
                "前端必须收到子步骤内容: " + payloads);
        assertTrue(payloads.stream().anyMatch(p -> p.contains("[计划]")),
                "前端必须收到计划横幅: " + payloads);
        assertEquals(1, payloads.stream().filter("[DONE]"::equals).count(),
                "每个流只能有一个 [DONE]: " + payloads);
    }

    /** 不需要规划时降级为单轮 ReAct，且不浪费一次规划 LLM 调用。 */
    @Test
    void 不需要规划时降级为单轮ReAct且不调用规划LLM() {
        ScriptedPlanAgent agent = new ScriptedPlanAgent(3, false, false);

        agent.run("短任务");

        assertEquals(0, agent.generatePlanCalls, "needsPlanning=false 时不得调用 generatePlan");
        assertEquals(1, agent.thinkCalls, "降级路径只跑一轮 ReAct");
        assertNull(agent.getTaskPlan());
    }

    /** 规划失败时降级为单轮 ReAct，而不是整个任务失败。 */
    @Test
    void 规划失败时降级为单轮ReAct() {
        ScriptedPlanAgent agent = new ScriptedPlanAgent(3, true, true);

        String out = agent.run("需要规划但规划失败的任务");

        assertEquals(1, agent.generatePlanCalls);
        assertEquals(1, agent.thinkCalls);
        assertEquals("FINISHED", agent.getCurrentTrace().getFinalState());
        assertTrue(out.contains("子步骤完成"), out);
    }

    /** 某个子步骤发生不可恢复异常时，中止剩余计划步骤，state 保持 ERROR。 */
    @Test
    void 子步骤ERROR时中止后续计划步骤() {
        ScriptedPlanAgent agent = new ScriptedPlanAgent(3, true, false);
        agent.errorOnFirstThink = true;

        agent.run("需要规划的任务");

        assertEquals(1, agent.thinkCalls, "第 1 步 ERROR 后不得继续第 2、3 步");
        assertEquals(AgentState.ERROR, agent.getState());
        assertEquals("ERROR", agent.getCurrentTrace().getFinalState());
    }

    /**
     * 读取 SseEmitter 在未初始化 handler 时缓冲的事件载荷。
     * <p>无 handler 时 {@code ResponseBodyEmitter.send} 会把数据存进私有的
     * {@code earlySendAttempts}，因此无需起 HTTP 服务即可断言推送内容。
     */
    @SuppressWarnings("unchecked")
    private static List<String> sentPayloads(SseEmitter emitter) {
        Object attempts = ReflectionTestUtils.getField(emitter, "earlySendAttempts");
        assertNotNull(attempts, "无法读取 SseEmitter.earlySendAttempts（Spring 内部结构可能已变）");
        return ((Set<Object>) attempts).stream()
                .map(data -> {
                    // 先落到 Object：直接写进 String.valueOf() 会让编译器把泛型
                    // invokeMethod 的返回类型推断成 char[]，从而选中 valueOf(char[]) 重载
                    Object payload = ReflectionTestUtils.invokeMethod(data, "getData");
                    return String.valueOf(payload);
                })
                .toList();
    }
}
