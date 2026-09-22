package com.qian.qianaiagent.agent;

import com.qian.qianaiagent.agent.model.AgentState;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * BaseAgent / ReActAgent 执行循环的纯单元测试：不启动 Spring、不连 LLM。
 *
 * <p>手法是脚本化子类覆写 {@code think()} / {@code act()}，把"循环控制流"与"模型输出"解耦。
 * 构造用 {@code new ToolCallback[0]} + mock ChatClient —— ToolCallAgent 的构造函数只存引用
 * 不做调用，因此无需任何 stub；mock 是刻意的：一旦真实 {@code think()} 被执行会立刻失败，
 * 而不是静默通过。
 *
 * <p>本文件先于修复落地，用于证明 bug 真实存在（红），修复后转绿。
 */
class AgentLoopTest {

    /**
     * 脚本化 Agent：按剧本返回 think() 的布尔值。
     * 剧本用尽 == AI 直接给出最终答案（无工具调用）。
     */
    static class ScriptedAgent extends ToolCallAgent {

        private final Deque<Boolean> script = new ArrayDeque<>();
        /** 模拟 think() 的"不可恢复异常"分支：置 ERROR 并返回 false */
        boolean errorOnThink = false;
        int thinkCalls = 0;
        int actCalls = 0;

        ScriptedAgent(ChatClient client, boolean... shouldAct) {
            super(new ToolCallback[0], client);
            for (boolean b : shouldAct) {
                script.add(b);
            }
            setName("scripted");
            setMaxSteps(Math.max(shouldAct.length, 1));
        }

        @Override
        public boolean think() {
            thinkCalls++;
            recordTraceStep("LLM_CALL", "第 " + thinkCalls + " 次思考", "答案" + thinkCalls, null, null);
            if (errorOnThink) {
                setState(AgentState.ERROR);
                return false;
            }
            Boolean next = script.pollFirst();
            return next != null && next;
        }

        @Override
        public String act() {
            actCalls++;
            recordTraceStep("TOOL_CALL", "第 " + actCalls + " 次工具调用", "args", "FakeTool", "{}");
            return "✅ FakeTool → ok";
        }
    }

    private ScriptedAgent newAgent(boolean... script) {
        return new ScriptedAgent(mock(ChatClient.class), script);
    }

    /**
     * 回归 bug 1：think() 返回 false（AI 已给出最终答案、无工具可调）必须立刻终止循环。
     * 修复前该 false 只被 step() 用来决定是否 act()，外层循环会继续空转到 maxSteps。
     */
    @Test
    void think返回false时循环立即终止() {
        // 第 1 步就给最终答案，后续剧本不该被消费
        ScriptedAgent agent = newAgent(false, true, true);

        String result = agent.run("测试问题");

        assertEquals(1, agent.thinkCalls, "think()→false 后循环必须立刻退出，不能空转到 maxSteps");
        assertEquals(0, agent.actCalls, "无需行动时不应调用 act()");
        assertEquals(AgentState.FINISHED, agent.getState());
        assertNotNull(agent.getCurrentTrace());
        assertEquals(1, agent.getCurrentTrace().getSteps().size(), "只应产生一步轨迹");
        assertTrue(result.contains("Step 1:"), result);
        assertFalse(result.contains("Step 2:"), "循环不应进入第二步：" + result);
    }

    /**
     * 回归 bug 2：think() 置 ERROR 后循环必须退出，且 ERROR 不能被收尾逻辑覆盖成 FINISHED。
     */
    @Test
    void think置ERROR时循环立即终止且状态不被覆盖() {
        ScriptedAgent agent = newAgent(true, true, true);
        agent.errorOnThink = true;

        String result = agent.run("测试问题");

        assertEquals(1, agent.thinkCalls, "第 1 步 think 已置 ERROR，不应继续循环");
        assertEquals(0, agent.actCalls, "ERROR 之后不应再 act()");
        assertEquals(AgentState.ERROR, agent.getState(), "ERROR 不能被 maxSteps 收尾覆盖成 FINISHED");
        assertEquals("ERROR", agent.getCurrentTrace().getFinalState(),
                "trace 的 finalState 必须反映 ERROR");
        assertFalse(result.contains("Terminated: Reached max steps"),
                "ERROR 退出不应谎报达到步数上限：" + result);
    }

    /**
     * 回归 bug 2 的副产物：达到 maxSteps 时 trace 的 finalState 被记成 "RUNNING"。
     * 根因是 run() 中 setFinalState 早于「步数耗尽 → FINISHED」的翻转。
     */
    @Test
    void 达到maxSteps时以FINISHED收尾并如实记录finalState() {
        ScriptedAgent agent = newAgent(true, true, true);

        String result = agent.run("测试问题");

        assertEquals(3, agent.thinkCalls);
        assertEquals(3, agent.actCalls);
        assertEquals(AgentState.FINISHED, agent.getState());
        assertTrue(result.contains("Terminated: Reached max steps (3)"), result);
        assertEquals("FINISHED", agent.getCurrentTrace().getFinalState(),
                "finalState 不能停在 RUNNING");
    }

    /**
     * 回归 bug 4 的前置：同一实例的 trace 必须恰好封口一次（EvalRunner 依赖 endTime 非空）。
     */
    @Test
    void 每次执行恰好产出一条封口的trace() {
        ScriptedAgent agent = newAgent(false);

        agent.run("测试问题");

        assertNotNull(agent.getCurrentTrace().getEndTime(), "trace 必须封口");
        assertEquals("FINISHED", agent.getCurrentTrace().getFinalState());
    }

    /**
     * finishTrace() 必须幂等：run()/runStream() 的 finally 与可能的异常路径都会调用它，
     * 重复封口会改写 endTime、并重复落盘。
     */
    @Test
    void finishTrace幂等且不重复改写封口时间() {
        ScriptedAgent agent = newAgent(false);
        agent.run("测试问题");

        LocalDateTime sealedAt = agent.getCurrentTrace().getEndTime();
        assertNotNull(sealedAt);

        // 再次封口（模拟 finally 与异常路径重复调用）
        agent.finishTrace();

        assertEquals(sealedAt, agent.getCurrentTrace().getEndTime(), "重复封口不得改写 endTime");
    }

    /** 契约未变：非 IDLE 状态与空提示词仍然抛异常，且异常文案不变。 */
    @Test
    void 非IDLE状态与空提示词仍然抛异常() {
        ScriptedAgent running = newAgent(false);
        running.setState(AgentState.RUNNING);
        RuntimeException stateError = assertThrows(RuntimeException.class, () -> running.run("x"));
        assertTrue(stateError.getMessage().startsWith("Cannot run agent from state:"),
                stateError.getMessage());

        ScriptedAgent blank = newAgent(false);
        RuntimeException promptError = assertThrows(RuntimeException.class, () -> blank.run("   "));
        assertTrue(promptError.getMessage().contains("empty user prompt"), promptError.getMessage());
    }
}
