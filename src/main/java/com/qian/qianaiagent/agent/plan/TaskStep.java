package com.qian.qianaiagent.agent.plan;

import lombok.Builder;
import lombok.Data;

/**
 * 计划中的单步任务
 *
 * <p>每个 TaskStep 代表计划中的一个执行步骤，由 LLM 在规划阶段生成。
 *
 * <p>状态流转：
 * <pre>
 *   PENDING → IN_PROGRESS → COMPLETED
 *                       ↘ FAILED → （触发重规划）
 * </pre>
 */
@Data
@Builder
public class TaskStep {

    /** 步骤状态枚举 */
    public enum StepStatus {
        PENDING,      // 等待执行
        IN_PROGRESS,  // 正在执行
        COMPLETED,    // 已完成
        FAILED        // 执行失败
    }

    // ===== 🧠 你来补全字段 =====
    // 💡 思考：一个"步骤"需要包含哪些信息？
    //   1. 这是第几步？（提示：给个序号，人和 AI 都能看懂当前进度）
    //   2. 这一步要做什么？（提示：一段自然语言描述，告诉执行者具体任务）
    //   3. 预期产出是什么？（提示：怎么判断这一步"做完了"？）
    //   4. 当前状态是什么？（提示：看看上面 Javadoc 里的状态流转图）
    //
    // 📖 提示：
    //   - 序号用 int 还是 Integer？想想"没赋值时应该是多少"
    //   - 描述类字段用 String 就够了
    //   - 状态用上面定义的 StepStatus 枚举
    //   - Lombok 会帮你生成 getter/setter，不用手写
    //
    // 🔴 你的任务：在下面声明 4 个 private 字段
    // ============================================
    // TODO: private ___ stepNumber;
    // TODO: private ___ description;
    // TODO: private ___ expectedOutput;
    // TODO: private ___ status;
    // ============================================
    private Integer stepNumber;
    private String  description;
    private String  expectedOutput;
    // private Integer status;  // ❌ 用 Integer 丢失了枚举的类型安全
    private StepStatus status;   // ✅ 用 enum 做状态标记

}
