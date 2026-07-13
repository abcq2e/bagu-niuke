package com.qian.qianaiagent.agent.trace;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单步执行记录 —— Agent 每执行一步，就生成一条 TraceStep。
 * <p>
 * 作用：事后调试时，按 stepNumber 排序就能还原整个执行过程。
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li><b>stepNumber</b>：第几步（从 1 开始），用于排序和定位</li>
 *   <li><b>stepType</b>：步骤类型 —— LLM_CALL（大模型推理）/ TOOL_CALL（调用工具）/ TOOL_RESULT（工具返回结果）</li>
 *   <li><b>whatHappened</b>：这一步做了什么？用人话描述，比如 "Agent 决定调用 WebSearchTool 搜索'今天天气'"</li>
 *   <li><b>timestamp</b>：什么时候发生的？用 LocalDateTime 不用 String，方便后续按时间排序/过滤</li>
 *   <li><b>durationMs</b>：这一步花了多少毫秒？如果某步特别慢，靠这个字段发现性能瓶颈</li>
 *   <li><b>resultSummary</b>：结果摘要，截前 200 字符就行，不用存完整结果（太长）</li>
 * </ul>
 *
 * <h3>为什么用 @Builder？</h3>
 * 建造者模式让构建代码更可读：<pre>{@code
 * TraceStep.builder()
 *     .stepNumber(1)
 *     .stepType("LLM_CALL")
 *     .whatHappened("Agent 推理中...")
 *     .timestamp(LocalDateTime.now())
 *     .durationMs(1523L)
 *     .resultSummary("决定调用 WebSearchTool")
 *     .build();
 * }</pre>
 * 而且字段一旦 build() 就不再修改（不可变对象，线程安全）。
 *
 * <h3>🧠 扩展思考（选做）</h3>
 * 如果你以后想让 Trace 更强大，可以考虑加这些字段：
 * <ul>
 *   <li>toolName —— 如果 stepType 是 TOOL_CALL，记录调了哪个工具</li>
 *   <li>toolInput —— 工具调用的输入参数</li>
 *   <li>errorMessage —— 如果这一步失败了，记录错误信息</li>
 * </ul>
 */
@Data
@Builder
public class TraceStep {

    /** 第几步（从 1 开始） */
    private int stepNumber;

    /** 步骤类型：LLM_CALL / TOOL_CALL / TOOL_RESULT */
    private String stepType;

    /** 这一步做了什么（人话描述） */
    private String whatHappened;

    /** 发生时间 */
    private LocalDateTime timestamp;

    /** 耗时（毫秒），-1 表示未记录 */
    @Builder.Default
    private long durationMs = -1;

    /** 结果摘要（截前 200 字符） */
    private String resultSummary;

    /** 调用的工具名称（仅 TOOL_CALL 步骤有值，如 "WebSearchTool"） */
    @Builder.Default
    private String toolName = null;

    /** 工具调用的输入参数（仅 TOOL_CALL 步骤有值，JSON 字符串） */
    @Builder.Default
    private String toolInput = null;
}
