package com.qian.qianaiagent.advisor.guardrail;

/**
 * 一条护栏规则的命中结果。
 *
 * <h2>为什么用 record</h2>
 * 判定结果是纯粹的「值」——产生后只被读取、不被修改。用 record 一次性拿到
 * 不可变语义与 equals/hashCode，省去样板代码，也让后续规则集的测试可以
 * 直接比较判定结果。
 *
 * @param ruleName 命中的规则名，用于日志与统计（不要放敏感内容）
 * @param reason   给人看的简短原因，会拼进拒绝话术
 */
public record GuardrailVerdict(String ruleName, String reason) {
}
