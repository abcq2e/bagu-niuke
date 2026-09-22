package com.qian.qianaiagent.advisor.guardrail;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 评分操纵 —— 本场景特有，直击产品核心功能。
 *
 * <p>措辞需与「询问评分标准」区分开：问「满分是多少」「我能得几分」是正常提问，
 * 只有出现「要求/命令 + 改变分数」的组合才拦截。判断依据是<b>有没有「给我/把…改成」这类
 * 祈使动作</b>，而不是有没有出现「分」字。
 */
@Component
public class ScoreManipulationRule implements InputRule {

    private static final String NAME = "ScoreManipulationRule";

    private static final Pattern[] PATTERNS = {
            Pattern.compile("(给我|直接给|帮我|请给)\\s*(打|判)?\\s*(满分|最高分|高分|100分|一百分)"),
            Pattern.compile("(直接|就)\\s*(判|算)\\s*(我)?\\s*(通过|过|合格|及格)"),
            Pattern.compile("(把|将)\\s*(我?的)?\\s*(分数|评分|得分)\\s*(改成|设为|调成|设置为)"),
            Pattern.compile("(give|award)\\s+me\\s+(full|maximum|perfect)\\s+(marks?|score|points)",
                    Pattern.CASE_INSENSITIVE),
    };

    @Override
    public Optional<GuardrailVerdict> check(String userInput) {
        for (Pattern p : PATTERNS) {
            if (p.matcher(userInput).find()) {
                return Optional.of(new GuardrailVerdict(NAME, "检测到试图操纵评分的输入"));
            }
        }
        return Optional.empty();
    }
}
