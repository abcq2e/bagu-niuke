package com.qian.qianaiagent.evaluation;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 期望行为 —— 定义一个用例"怎么做才算对"的所有约束。
 *
 * <p>教程里 YAML 形式的例子：
 * <pre>
 * expected_behavior:
 *   expectedToolCalls:           # 过程检查：应该调用了哪些工具
 *     - toolName: "webSearch"
 *       paramContains: "Spring"
 *   expectedResponseKeywords:    # 结果检查：最终回答应包含哪些关键词
 *     - "Spring AI"
 *     - "教程"
 *   maxToolCalls: 5              # 效率检查：工具调用次数上限
 * </pre>
 */
@Data
@Builder
public class ExpectedBehavior {

    // ============================================================
    // 🔴 字段 1：工具调用期望列表
    // ============================================================
    // 💡 每个元素描述"应该调用哪个工具、参数中要包含什么"。
    //    类型：用内置的 ToolCallExpectation 内部类（见下方）
    //    List 表示可以有多个工具调用期望（如"先调 searchWeb，再调 fileRead"）
    //
    // 🔴 你的代码：
    // private List<___> expectedToolCalls;
    //
    private List<ToolCallExpectation> expectedToolCalls;
    // ============================================================
    // 🔴 字段 2：回答关键词列表
    // ============================================================
    // 💡 Agent 的最终回答里应该包含这些词。全部命中才算通过。
    //    类型：List<String>，简单直白。
    //    示例：["Spring AI", "教程", "1.0"]
    //
    // 🔴 你的代码：
    // private List<String> ___;
    //
    private List<String> expectedResponseKeywords = new ArrayList<>();
    // ============================================================
    // 🔴 字段 3：最大工具调用次数
    // ============================================================
    // 💡 Agent 调用工具的总次数不能超过这个数。超过 → 扣分。
    //    类型：int 或 Integer（用 int 的话 Builder 会给默认值 0，注意处理）
    //
    // 🔴 你的代码：
    // private ___ maxToolCalls;
    //
    private Integer maxToolCalls = 10;
    // ============================================================
    // 🔴 内部类：单条工具调用期望
    // ============================================================
    // 💡 描述"期望调用了某个工具，且参数/结果中包含某个关键词"。
    //    需要的属性：
    //      - toolName: String  —— 期望调用的工具名称（如 "webSearch"）
    //      - paramContains: String —— 工具参数中应包含的关键词（可选，检查参数是否正确传了）
    //      - resultContains: String —— 工具返回结果中应包含的关键词（可选，检查结果对不对）
    //
    //    用 @Data + @Builder，Lombok 帮你搞定 getter/setter/构造器。
    //
    // 🔴 你的代码：在下面声明一个 public static class ToolCallExpectation {...}
    //
    @Data
    @Builder
    //单条工具调用期望
    public static  class ToolCallExpectation {
        private String toolName;
        private String paramContains;
        private String resultContains;
    }

}
