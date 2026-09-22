package com.qian.qianaiagent.advisor.guardrail;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 角色劫持：试图让模型放弃「面试官」身份。
 *
 * <p>⚠️ 各分支刻意分开写，<b>不要合并成「前缀 + 角色动词」两段式</b>。
 * 合并版（如 {@code (你现在是|从现在起)(扮演|是)}）会漏掉两类常见话术：
 * <ul>
 *   <li>「你现在是…」—— 前缀已含「是」，第二段无处可匹配</li>
 *   <li>「从现在起你扮演…」—— 前两段之间隔着「你」</li>
 * </ul>
 * 这两类都来自实际用例，合并后测试会直接红。
 */
@Component
public class RoleHijackRule implements InputRule {

    private static final String NAME = "RoleHijackRule";

    private static final Pattern[] PATTERNS = {
            // 「你现在是…」——刻意留宽：这是最典型的越狱开场。
            // 代价是「你现在是不是在问我项目经历」这类正常提问会被误拦，
            // 属已知取舍：误拦代价是一句引导话术，漏拦代价是越狱成功。
            Pattern.compile("你现在(是|不再是)"),
            // 「从现在起你扮演…」——「你」可省，故单独成组
            Pattern.compile("(从现在起|接下来)\\s*你?\\s*(扮演|作为|充当|当成|是)"),
            Pattern.compile("(不再|别)(是|当|做)(面试官|考官|面试)"),
            // 英文：pretend/act 后面接 to be / as if / as / you are 都算
            Pattern.compile("(pretend|act)\\s+(to\\s+be|as\\s+if|as|you\\s+are)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(you\\s+are\\s+now|from\\s+now\\s+on\\s+you\\s+are)", Pattern.CASE_INSENSITIVE),
    };

    @Override
    public Optional<GuardrailVerdict> check(String userInput) {
        for (Pattern p : PATTERNS) {
            if (p.matcher(userInput).find()) {
                return Optional.of(new GuardrailVerdict(NAME, "检测到试图改变角色设定的输入"));
            }
        }
        return Optional.empty();
    }
}
