package com.qian.qianaiagent.agent;

import com.qian.qianaiagent.advisor.InputGuardrailAdvisor;
import com.qian.qianaiagent.advisor.MyLoggerAdvisor;
import com.qian.qianaiagent.advisor.OutputGuardrailAdvisor;
import com.qian.qianaiagent.advisor.guardrail.InputRuleSet;
import com.qian.qianaiagent.advisor.guardrail.InstructionOverrideRule;
import com.qian.qianaiagent.advisor.guardrail.LengthRule;
import com.qian.qianaiagent.advisor.guardrail.OutputRuleSet;
import com.qian.qianaiagent.advisor.guardrail.RoleHijackRule;
import com.qian.qianaiagent.advisor.guardrail.ScoreManipulationRule;
import com.qian.qianaiagent.advisor.guardrail.SystemPromptProbeRule;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 鱼皮的 AI 超级智能体（拥有自主规划能力，可以直接使用）
 * <p>
 * 本类只做三件事：装配（ChatClient + Advisor）、配置（名称/步数）、以及提供与自身
 * 工具集强绑定的提示词与策略。执行机制全部在父类：
 * {@link ToolCallAgent}（ReAct + 工具执行）与 {@link PlanAndExecuteAgent}（先规划再执行）。
 * <p>
 * Prototype 作用域：每次请求创建新实例，确保 Agent 状态（IDLE/RUNNING/FINISHED）不冲突。
 */
@Component
@Scope("prototype")
public class YuManus extends PlanAndExecuteAgent {

    /**
     * 规划提示词。与 YuManus 的工具集强绑定（三个工具名写死在里面），因此留在具体
     * Agent 而不是机制层。末尾的 {@code %s} 占位符用于注入用户目标，不可删除。
     */
    private static final String PLAN_SYSTEM_PROMPT = """
    ## 角色设定
    你是专业AI任务规划专家，擅长拆解复杂需求，输出有序、可执行的分步任务清单。
    你知晓Agent拥有3类工具：WebSearchTool联网检索、TerminalOperationTool终端命令、RagSearchTool本地知识库检索，规划每一步时必须匹配对应工具。

    ## 硬性输出规则
    1. 只返回纯JSON，禁止任何解释、前言、markdown、多余文字；
    2. JSON固定包含两层字段：
    - goal：字符串，完整复述用户原始总目标
    - steps：数组，每一项是单步任务对象，单步必须包含：
         stepDesc：本步骤要做什么
        toolName：执行该步骤需要调用的工具名称，三选一：WebSearchTool / TerminalOperationTool / RagSearchTool
        retryLimit：本步骤最大重试次数（固定2）
        failStrategy：步骤失败后的处理方案（重试/切换工具/终止任务）

    ## Few-Shot 标准示例（严格模仿此结构输出）
    {
        "goal": "分析SpringBoot项目启动慢问题并给出优化方案",
        "steps": [
            {
                "stepDesc": "检索SpringBoot启动慢通用优化方案",
                "toolName": "WebSearchTool",
                "retryLimit": 2,
                "failStrategy": "切换RagSearchTool查询本地知识库"
            },
            {
                "stepDesc": "执行mvn compile编译项目查看启动日志",
                "toolName": "TerminalOperationTool",
                "retryLimit": 2,
                "failStrategy": "重试2次后终止任务，告知用户权限不足"
            }
        ]
    }

    ## 规划约束
    1. 步骤顺序必须符合执行逻辑，先检索信息再操作本地文件；
    2. 每一步仅分配一个工具，禁止一步调用多个工具；
    3. 复杂需求必须拆分成多步，禁止合并多个操作到单一步骤；
    4. 若需求无需要工具的操作，直接给出仅终止的单步计划。

    ## 用户任务目标：%s
    """;

    /** 触发规划的输入长度下限：太短的输入不值得多花一次 LLM 调用 */
    private static final int PLAN_MIN_LENGTH = 60;

    /** 复杂任务特征词：命中才走 Plan 模式 */
    private static final String[] PLAN_KEYWORDS =
            {"分析以下", "对比", "优化", "总结一下", "深入分析", "检查代码", "重构"};

    private static final String SYSTEM_PROMPT = """
            ## 角色定义
            你是 YuManus，一个强大的 AI 智能体，能够自主规划并执行复杂任务。
            你拥有网络搜索、文件操作、终端执行、知识库检索等能力，可以处理各种
            编程、技术、信息查询和自动化任务。

            ## 核心原则
            - **目标导向**：理解用户需求后，自主拆解步骤，逐步执行直到完成
            - **诚实可靠**：不知道就说不知道，不编造事实
            - **主动推进**：每次执行后主动判断下一步该做什么，不需要用户指示
            - **结果第一**：完成目标后清晰汇报结果，而不是罗列过程

            ## 可用工具

            ### 1. WebSearchTool — 网络搜索
            - **功能**：通过 Tavily 搜索引擎获取实时网页信息
            - **使用时机**：需要最新信息、技术文档、数据查询时
            - **注意事项**：可能因 API 超时或限流失败，此时尝试简化关键词重试

            ### 2. TerminalOperationTool — 终端命令执行
            - **功能**：在终端执行安全命令
            - **白名单**：dir、echo、type、findstr、mkdir、python
            - **使用时机**：需要读写文件、运行脚本、查看目录结构时
            - **注意事项**：先确认命令在白名单内再调用

            ### 3. RagSearchTool — 知识库检索
            - **功能**：搜索本地向量知识库，获取技术文档和知识点
            - **使用时机**：需要查阅技术原理、代码示例、最佳实践时
            - **注意事项**：检索不到结果时换关键词或改用 WebSearchTool

            ### 4. TerminateTool — 终止任务
            - **功能**：结束当前任务执行
            - **使用时机**：任务全部完成 或 确定无法继续推进时
            - **注意事项**：终止前必须向用户说明完成情况或失败原因

            ## 工作方式
            - **拆解任务**：收到复杂请求时先拆成子步骤，按优先级执行
            - **单步单责**：每次工具调用只做一件事，完成后检查结果再决定下一步
            - **透明沟通**：用简短的话解释当前在做什么、为什么
            - **灵活应变**：当前方案失败时分析原因，换方案再试而非盲目重试

            ## 错误处理
            - **可恢复错误（超时/限流）**：等待后重试 1 次，仍失败则换方案
            - **连续失败保护**：同一工具连续失败 3 次 → 放弃该路径，向用户解释
            - **不可恢复错误（API Key 无效等）**：立即终止，说明需要人工介入

            ## 输出要求
            - **语言**：用户用中文则中文回复，代码和专有名词保持原文
            - **格式**：使用 Markdown 组织，层次分明
            - **结束时调用 TerminateTool** 表明任务完成
            """;

    private static final String NEXT_STEP_PROMPT = """
            请根据当前对话进展，主动选择最合适的工具来推进任务。
            如果已获得足够信息完成任务，则直接给出最终回答并调用 TerminateTool 结束。
            如果上一轮工具调用失败，分析原因后换方案，不要重复相同的失败调用。
            """;

    /**
     * 输入护栏命中话术 —— 只把用户拉回任务本身，不解释命中了哪条规则。
     * ⚠️ 不给攻击者调试反馈：写明规则名等于让他逐条试出边界。
     */
    private static final String GUARDRAIL_BLOCKED_REPLY =
            "这个请求我没法照做，换个说法我们再继续吧。";

    /** 输出护栏兜底话术 —— 自然过渡，用户不应察觉被拦。 */
    private static final String GUARDRAIL_FALLBACK_REPLY =
            "这部分内容我不便展开，我们换个方向继续。";

    /**
     * 系统提示词中的独有片段，用于检测输出泄漏。
     * ⚠️ 改动 {@link #SYSTEM_PROMPT} 时，这里必须同步更新，否则泄漏检测形同虚设 ——
     * 片段对不上时不会报错，只是永远匹配不到，属于静默失效。
     * 下方两条已对照 {@link #SYSTEM_PROMPT} 逐字核实。
     *
     * <p>选取标准：足够长、且只可能出现在系统提示词里。刻意不用「你是」这类
     * 通用措辞 —— 正常回答里也会出现，会把正常输出误判为泄漏。
     */
    private static final List<String> OUTPUT_PROMPT_FRAGMENTS = List.of(
            "你拥有网络搜索、文件操作、终端执行、知识库检索等能力",
            "不知道就说不知道，不编造事实");

    public YuManus(ToolCallback[] allTools, ChatModel openAiChatModel,
                   InstructionOverrideRule instructionOverrideRule,
                   RoleHijackRule roleHijackRule,
                   SystemPromptProbeRule systemPromptProbeRule,
                   ScoreManipulationRule scoreManipulationRule,
                   LengthRule lengthRule) {
        super(allTools, buildChatClient(openAiChatModel, instructionOverrideRule, roleHijackRule,
                systemPromptProbeRule, scoreManipulationRule, lengthRule));
        // ✅ ChatClient 随 super() 传入，由 ToolCallAgent 构造函数调用 setChatClient()
        this.setName("yuManus");
        this.setSystemPrompt(SYSTEM_PROMPT);
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(8);           // 每个子目标的 ReAct 循环预算；计划长度上限见 PlanAndExecuteAgent.MAX_PLAN_STEPS
    }

    /**
     * 装配 ChatClient：日志 Advisor + 两侧护栏。
     *
     * <p>{@code super(...)} 必须是构造器首条语句，无法先构造局部变量再传入，
     * 因此把装配逻辑抽到这个 static 方法里 —— 顺带避免了在 {@code this} 未就绪时
     * 调用实例方法。
     *
     * <p>护栏加在末尾即可：链按 order 升序执行，实际位置由 {@code getOrder()} 决定，
     * 与添加顺序无关。输入护栏 = {@link Integer#MIN_VALUE}（最外层），
     * 输出护栏 = {@link Integer#MAX_VALUE}（最内层）。
     */
    private static ChatClient buildChatClient(ChatModel openAiChatModel,
                                              InstructionOverrideRule instructionOverrideRule,
                                              RoleHijackRule roleHijackRule,
                                              SystemPromptProbeRule systemPromptProbeRule,
                                              ScoreManipulationRule scoreManipulationRule,
                                              LengthRule lengthRule) {
        // 🔴 规则集按「更严格者优先」的顺序显式构造，不用 Spring 注入 List<InputRule>：
        // bean 扫描顺序不确定，会让这条契约随机失效（InputRuleSet 命中第一条即短路）。
        InputRuleSet inputRuleSet = new InputRuleSet(List.of(
                instructionOverrideRule, roleHijackRule,
                systemPromptProbeRule, scoreManipulationRule, lengthRule));
        OutputRuleSet outputRuleSet = new OutputRuleSet(OUTPUT_PROMPT_FRAGMENTS);

        List<Advisor> advisors = new ArrayList<>();
        advisors.add(new MyLoggerAdvisor());
        advisors.add(new InputGuardrailAdvisor(inputRuleSet, GUARDRAIL_BLOCKED_REPLY));
        advisors.add(new OutputGuardrailAdvisor(outputRuleSet, GUARDRAIL_FALLBACK_REPLY));

        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(advisors.toArray(new Advisor[0]))
                .build();
    }

    @Override
    protected String planSystemPromptTemplate() {
        return PLAN_SYSTEM_PROMPT;
    }

    /**
     * 复杂度预判断：决定这次任务走 Plan-and-Execute 还是直接 ReAct。
     *
     * <p>判定分三关，从简单到进阶：
     * <ol>
     *   <li><b>长度</b>：短于 {@link #PLAN_MIN_LENGTH} 直接不规划 —— 规划本身要多花一次
     *       LLM 调用，日常问答不值得；</li>
     *   <li><b>特征词</b>：必须命中 {@link #PLAN_KEYWORDS} 之一（"分析以下""对比""优化"
     *       "总结一下""深入分析""检查代码""重构"）。只靠长度阈值会把长篇闲聊也拖进规划，
     *       这一关才是真正的开关；</li>
     *   <li><b>文件路径（可选，未启用）</b>：输入里出现 {@code .java} / {@code .xml} /
     *       {@code src/} 等路径特征时也可判定为复杂任务。</li>
     * </ol>
     * 判定为 false 或规划失败都会降级回单轮 ReAct，由

     */
    @Override
    protected boolean needsPlanning(String userPrompt) {
        if (userPrompt == null || userPrompt.length() < PLAN_MIN_LENGTH) {
            return false;
        }
        for (String kw : PLAN_KEYWORDS) {
            if (userPrompt.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
