package com.qian.qianaiagent.advisor.guardrail;

import java.util.Optional;

/**
 * 输入侧护栏规则。
 *
 * <h2>为什么要求确定性</h2>
 * 实现必须是<b>确定性</b>的：不得调用 LLM、不得访问网络、不得抛异常。
 * 这是刻意的取舍——确定性规则没有额外延迟、也不会被注入绕过，代价是
 * 语义变体可能漏网。审核型（调 LLM 判断）的护栏可以另开接口，不要污染这里。
 *
 * <h2>执行顺序的约定</h2>
 * 规则按注册顺序执行，第一条命中即短路，因此「更严格的规则」应排在前面。
 */
public interface InputRule {

    /**
     * @param userInput 本轮用户原始输入（非 null，调用方保证）
     * @return 命中则返回判定结果，未命中返回 {@link Optional#empty()}
     */
    Optional<GuardrailVerdict> check(String userInput);
}
