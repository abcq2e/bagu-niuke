package com.qian.qianaiagent.agent.trace;

import com.qian.qianaiagent.agent.model.AgentState;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次完整 Agent 执行的 Trace 记录 —— 包含所有步骤 + 元信息。
 * <p>
 * 作用：
 * <ul>
 *   <li>把 steps 按时间排序 → 还原 Agent 整个"思考-行动"链条</li>
 *   <li>endTime - startTime → 算出任务总耗时</li>
 *   <li>JSON 序列化后存文件 → 事后分析 Agent 行为</li>
 * </ul>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li><b>agentName</b>：哪个 Agent 产生的？以后多 Agent 协作时能区分</li>
 *   <li><b>startTime</b>：任务开始时间</li>
 *   <li><b>endTime</b>：任务结束时间（结束后再设置）</li>
 *   <li><b>finalState</b>：任务最终状态（FINISHED / ERROR），用于判断是否成功</li>
 *   <li><b>steps</b>：所有步骤，按 stepNumber 排序</li>
 * </ul>
 *
 * <h3>@Builder.Default 的作用</h3>
 * 如果不用 @Builder.Default，当你写 {@code AgentTrace.builder().agentName("test").build()} 时，
 * steps 字段会是 null 而不是空列表。加了 @Builder.Default 后，build() 出来的对象 steps = new ArrayList<>()。
 *
 * <h3>🧠 扩展思考（选做）</h3>
 * <ul>
 *   <li>加 totalSteps 字段：steps.size() 就行了，但如果 steps 很大，缓存这个值省遍历</li>
 *   <li>加 totalDurationMs 字段：endTime - startTime，缓存避免重复计算</li>
 *   <li>加 errorMessage 字段：如果 finalState 是 ERROR，记录具体错误</li>
 * </ul>
 */
@Data
@Builder
public class AgentTrace {

    /** 哪个 Agent 产生的 */
    private String agentName;

    /** 任务开始时间 */
    private LocalDateTime startTime;

    /** 任务结束时间（任务完成后设置） */
    private LocalDateTime endTime;

    /** 任务最终状态：FINISHED（成功完成）/ ERROR（异常终止） */
    private String finalState;

    /** 所有执行步骤，按 stepNumber 升序 */
    @Builder.Default
    private List<TraceStep> steps = new ArrayList<>();
}
