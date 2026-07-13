package com.qian.qianaiagent.agent.plan;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LLM 生成的任务计划
 *
 * <p>Plan-and-Execute 模式的核心数据结构：
 * Agent 先让 LLM 生成一个 TaskPlan，然后按步骤逐个执行。
 *
 * <p>与 AgentTrace 的关系：
 * <ul>
 *   <li>TaskPlan 是"执行前的蓝图"（应该做什么）</li>
 *   <li>AgentTrace 是"执行后的记录"（实际做了什么）</li>
 *   <li>两者对比可以判断"计划是否被正确执行"</li>
 * </ul>
 */
@Data
@Builder
public class TaskPlan {

    // ===== 🧠 你来补全字段 =====
    // 💡 思考：一个"计划"需要包含哪些信息？
    //
    //   1. 这个计划要达成什么目标？
    //      提示：用户原始问题可能就是目标，但 LLM 可能会改写得更清晰
    //
    //   2. 分几步执行？
    //      提示：看看同包下的 TaskStep 类，用什么类型来存"一组步骤"？
    //      List 还是数组？它们有什么区别？（提示：数组长度固定，List 可以动态扩展）
    //
    //   3. 计划是什么时候生成的？
    //      提示：java.time.LocalDateTime，用于和 AgentTrace 的 startTime 对比
    //
    //   4. 计划整体是什么状态？
    //      提示：可以用 TaskStep.StepStatus 枚举，也可以用单独的字符串
    //      思考：计划和步骤的状态需要独立管理吗？如果所有步骤都完成了，计划状态应该是什么？
    //
    // 📖 基础补充：List vs 数组
    //   - TaskStep[] steps → 长度不可变，需要提前知道步骤数
    //   - List<TaskStep> steps → 长度可变，LLM 生成计划时步骤数不确定
    //   显然这里应该用 List，因为 LLM 可能生成 3 步也可能生成 10 步
    //
    // 🔴 你的任务：在下面声明 4 个 private 字段，建议用 @Builder.Default 给 List 设默认值
    // ============================================
    // TODO: private ___ goal;
    // TODO: @Builder.Default
    //       private ___ steps = ___;
    // TODO: private ___ createdAt;
    // TODO: private ___ status;
    // ============================================
    private String goal;
    @Builder.Default
    private List<TaskStep> steps = List.of();
    private LocalDateTime createdAt;
    private Integer status;
}
