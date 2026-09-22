package com.qian.qianaiagent.advisor.guardrail;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 系统提示词刺探：试图套出 system prompt 原文。
 *
 * <p>中文分支刻意要求「动作 + 提示词类名词」同时出现，
 * 避免「提示一下思路」这类正常讨提示的请求被误拦 ——
 * 「提示」在面试场景里是高频词，是误报风险最高的地方。
 */
@Component
public class SystemPromptProbeRule implements InputRule {

    private static final String NAME = "SystemPromptProbeRule";

    private static final Pattern[] PATTERNS = {
            Pattern.compile("(输出|打印|展示|显示|重复|泄露|告诉我|复述)\\s*(一下)?\\s*(你?的)?\\s*(系统)?\\s*(提示词|提示语|prompt|设定|人设|系统消息|初始指令)"),
            Pattern.compile("(repeat|print|show|reveal|output|leak)\\s+(your\\s+)?(system\\s+)?(prompt|instructions?|rules?|configuration)",
                    Pattern.CASE_INSENSITIVE),
    };

    @Override
    public Optional<GuardrailVerdict> check(String userInput) {
        for (Pattern p : PATTERNS) {
            if (p.matcher(userInput).find()) {
                return Optional.of(new GuardrailVerdict(NAME, "检测到试图获取系统提示词的输入"));
            }
        }
        return Optional.empty();
    }
}
