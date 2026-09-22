package com.qian.qianaiagent.advisor.guardrail;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 输出侧规则集：系统提示词泄漏 + PII。
 *
 * <p>与输入侧共用 {@link GuardrailVerdict}，但按输出场景单独组织 ——
 * 输出规则关心的是「不该说出去的」，输入规则关心的是「不该问进来的」，
 * 两者的误报代价与调优方向都不同。
 *
 * <h2>为什么不做成 InputRule 的复用</h2>
 * 输入侧 {@link InputRuleSet} 的核心动作是「按序执行多条 {@code InputRule} 并短路」，
 * 而输出侧当前只有两类判定，且 PII 必须合并成一条正则一次性扫过
 * （拆成四条规则会让同一段文本被重复扫描，还得靠短路顺序决定谁胜出，没必要）。
 * 保持独立也让两侧可以各自演化误报阈值，不必为对方让步。
 *
 * <h2>已知局限：检测是「单段文本」的，不是「对话级」的</h2>
 * 本类只检查传入的这一段文本。若模型把提示词片段拆到多轮回答里（每轮说一点），
 * 或对片段做同义改写、逐字插入零宽字符，这里的字面量匹配都会漏检。
 * 这是刻意的边界 —— 本类的目标是拦住「提示词被整段复述」这一最常见形态，
 * 而不是对抗有意的规避式提取。
 *
 * <h2>为什么传空片段列表不报错</h2>
 * 装配侧可能从配置读不到片段（配置缺失、环境变量未注入）。此时正确的行为是
 * 「该检测关闭、其余照常」，而不是让应用启动失败 —— 护栏不该成为启动故障源。
 * 但这也意味着配置写错时泄漏检测会静默失效，所以 {@link #check} 里对空白片段
 * 逐个跳过，避免 {@code contains("")} 恒为真而把一切都判成泄漏。
 */
@Slf4j
public class OutputRuleSet {

    /** 系统提示词泄漏。 */
    private static final String LEAK_RULE = "SystemPromptLeakRule";

    /** 敏感个人信息。 */
    private static final String PII_RULE = "PiiRule";

    /**
     * 手机号（中国大陆）、身份证（18 位）、常见 API Key 前缀。
     *
     * <h2>为什么每条都加前后向断言</h2>
     * {@code (?<!\d)} / {@code (?!\d)} 保证匹配的是「完整的一串数字」而非某个长数字的
     * 中间片段。缺少它时，「订单号 20240903138123456789」里的 11 位子串会被当成手机号，
     * 正常业务数据大量误报。
     *
     * <h2>为什么身份证放手机号之后</h2>
     * 18 位数字里可能嵌着 11 位手机号形态的子串。交替分支按书写顺序尝试，
     * 手机号在前时会把这种子串先认成手机号 —— 两者同属 {@code PII_RULE}，
     * 判定结果一致，故顺序不影响结论，此处仅保持可读性。
     */
    private static final Pattern PII_PATTERN = Pattern.compile(
            "(?<!\\d)1[3-9]\\d{9}(?!\\d)"                                  // 手机号
            + "|(?<!\\d)\\d{17}[\\dXx](?!\\d)"                            // 身份证 18 位
            + "|sk-[A-Za-z0-9]{16,}"                                      // OpenAI/DeepSeek 风格 key
            + "|AKIA[0-9A-Z]{16}"                                         // AWS Access Key
    );

    private final List<String> promptFragments;

    /**
     * @param promptFragments 系统提示词中的独有片段，命中任一即判为泄漏。
     *                        传空列表则该检测关闭（不报错）。
     */
    public OutputRuleSet(List<String> promptFragments) {
        this.promptFragments = promptFragments == null ? List.of() : List.copyOf(promptFragments);
    }

    /**
     * @param output 模型输出文本，可为 null/空白
     * @return 命中判定；全不命中返回 empty
     */
    public Optional<GuardrailVerdict> check(String output) {
        if (output == null || output.isBlank()) {
            return Optional.empty();
        }
        for (String fragment : promptFragments) {
            // 跳过空白片段：contains("") 恒为真，会把所有输出都判成泄漏
            if (!fragment.isBlank() && output.contains(fragment)) {
                return Optional.of(new GuardrailVerdict(LEAK_RULE, "输出中疑似包含系统提示词内容"));
            }
        }
        if (PII_PATTERN.matcher(output).find()) {
            return Optional.of(new GuardrailVerdict(PII_RULE, "输出中疑似包含敏感个人信息"));
        }
        return Optional.empty();
    }
}
