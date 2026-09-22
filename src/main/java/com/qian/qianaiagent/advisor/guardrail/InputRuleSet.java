package com.qian.qianaiagent.advisor.guardrail;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * 输入规则注册表：按序执行，第一条命中即返回。
 *
 * <h2>为什么单条规则抛异常时跳过而不是整体失败</h2>
 * 两种失败模式都比「跳过」更糟：
 * <ul>
 *   <li><b>向上抛</b> —— 一条正则写错会导致所有请求被拒（护栏把自己变成了故障源）</li>
 *   <li><b>静默放行全部</b> —— 一条正则写错会导致护栏整体失效，且没人发现</li>
 * </ul>
 * 跳过该条、记 warn、继续跑其余规则，是唯一既不影响可用性、又不让其余规则陪葬的选择。
 * 代价是「某条规则长期失效」只能靠 warn 日志发现 —— 因此这里必须用 warn 而非 debug。
 */
@Slf4j
public class InputRuleSet {

    private final List<InputRule> rules;

    public InputRuleSet(List<InputRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("规则列表不能为空");
        }
        this.rules = List.copyOf(rules);
    }

    /**
     * @param userInput 用户输入，可为 null（按空串处理）
     * @return 第一条命中的规则判定，全不命中返回 empty
     */
    public Optional<GuardrailVerdict> check(String userInput) {
        String input = userInput == null ? "" : userInput;
        for (InputRule rule : rules) {
            try {
                Optional<GuardrailVerdict> verdict = rule.check(input);
                if (verdict.isPresent()) {
                    return verdict;
                }
            } catch (Exception e) {
                log.warn("护栏规则执行失败，已跳过: rule={}, err={}",
                        rule.getClass().getSimpleName(), e.toString());
            }
        }
        return Optional.empty();
    }
}
