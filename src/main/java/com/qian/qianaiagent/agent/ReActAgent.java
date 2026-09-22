package com.qian.qianaiagent.agent;

import com.qian.qianaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 实现了思考-行动的循环模式
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    public abstract boolean think();

    /**
     * 执行决定的行动
     *
     * @return 行动执行结果
     */
    public abstract String act();


    /**
     * 执行单个步骤：思考和行动
     *
     * @return 步骤执行结果
     */
    @Override
    public String step() {
        try {
            // 先思考
            boolean shouldAct = think();
            if (!shouldAct) {
                // think() 返回 false = AI 已给出最终答案、没有工具要调用 = 本次目标结束。
                // 必须把状态置为 FINISHED，外层 executeLoop（循环条件 state == RUNNING）
                // 才会退出；否则循环会空转到 maxSteps，白白多跑若干轮 LLM 调用。
                //
                // 但 think() 内部可能已经置了 ERROR（不可恢复异常分支），不能覆盖它，
                // 否则 LoopExit.ERROR 不可达、轨迹会对失败运行谎报 FINISHED。
                if (getState() == AgentState.RUNNING) {
                    setState(AgentState.FINISHED);
                }
                return noActionText();
            }
            // 再行动
            return act();
        } catch (Exception e) {
            log.error("ReAct 步骤执行失败", e);
            return "步骤执行失败：" + e.getMessage();
        }
    }

    /**
     * "无需行动"时的展示文案。流式路径会把它直接推给前端，因此子类可重写以展示
     * 更有价值的内容（如 AI 的最终回答）。
     */
    protected String noActionText() {
        return "思考完成 - 无需行动";
    }

}
