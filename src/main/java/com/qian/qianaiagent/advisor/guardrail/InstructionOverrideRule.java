package com.qian.qianaiagent.advisor.guardrail;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 指令覆盖型注入：试图让模型丢弃既有设定。
 *
 * <h2>为什么留窄</h2>
 * 规则刻意留窄 —— 只匹配「动词 + 指代既有设定」的组合，避免误伤
 * 「JVM 字节码指令」「CPU 指令流水线」这类正常技术讨论。关键在于
 * <b>动词是必需的</b>：光出现「指令」「提示」这类名词不会命中。
 */
@Component
public class InstructionOverrideRule implements InputRule {

    private static final String NAME = "InstructionOverrideRule";

    /**
     * 中文规则的指代词与「的」拆成两个可选组，而不是写成 {@code 你?的}。
     *
     * <h2>为什么要拆</h2>
     * 若把「的」绑在「你」上（{@code 你?的}），则 {@code 忽略以上的指令} 中的
     * 「的」没有任何组能消费：引擎匹配完「以上」后直接期望「指令」，遇到「的」
     * 就失败；回溯让指代组取空后，后面同样没人吃「的」，于是整条漏检。
     * 「之前」+「的」、「上面」+「的」同理。把「的」独立成可选组即可覆盖
     * 所有「指代词 + 的 + 名词」的组合。
     *
     * <h2>为什么指代词必填（不能写成 {@code (…)?}）</h2>
     * 指代组必须匹配，不能可选。一旦加回 {@code ?}，裸「动词 + 名词」也会命中，
     * 于是「请忽略指令流水线的细节」「忽略指令」「忘记规则」这类正常技术讨论
     * 会被误判 —— 恰恰是本规则要避免的场景。英文分支本来就要求
     * {@code previous|prior|above|earlier|preceding}，中文这条与它保持一致。
     */
    private static final Pattern[] PATTERNS = {
            // 忽略/忘记 + 指代既有设定的词（指代词必需，见上方说明）
            Pattern.compile("(忽略|无视|忘掉|忘记|抛弃|丢弃)\\s*(以上|上面|之前|前面|先前|你)\\s*的?\\s*(所有|全部|一切)?\\s*(指令|指示|要求|规则|设定|人设|提示)"),
            // 英文：ignore/disregard/forget + previous/prior/above + instructions
            Pattern.compile("(ignore|disregard|forget)\\s+(all\\s+)?(the\\s+)?(previous|prior|above|earlier|preceding)\\s+(instructions?|prompts?|rules?)",
                    Pattern.CASE_INSENSITIVE),
            // 伪造新指令块
            Pattern.compile("(new|updated|real)\\s+instructions?\\s*[:：]", Pattern.CASE_INSENSITIVE),
    };

    @Override
    public Optional<GuardrailVerdict> check(String userInput) {
        for (Pattern p : PATTERNS) {
            if (p.matcher(userInput).find()) {
                return Optional.of(new GuardrailVerdict(NAME, "检测到试图覆盖既有指令的输入"));
            }
        }
        return Optional.empty();
    }
}
