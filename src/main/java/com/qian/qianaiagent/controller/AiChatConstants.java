package com.qian.qianaiagent.controller;

/**
 * AI 对话共享常量。
 *
 * <h2>为什么是 public</h2>
 * 原本是包私有，注释写着「不暴露为公共 API」—— 那个判断的前提是「只有两个 controller 用」。
 * 现在输入护栏的 {@code LengthRule}（{@code advisor.guardrail} 包）也要用同一个上限：
 * 护栏若自己另设一个常量，两处口径迟早分叉（控制器放行了、护栏拦截了，或反之），
 * 而「长度上限」本就是产品级约束，不是 controller 的实现细节。
 *
 * <p>因此改为 public，保持单一事实来源。代价是引入了
 * {@code advisor.guardrail → controller} 这条反向依赖 —— 目前没有 ArchUnit 规则禁止它，
 * 但若日后要给 {@code advisor} 包加依赖约束，应优先把本类迁到
 * {@code com.qian.qianaiagent.constant}（那里已有 {@code FileConstant}），而不是在此放宽规则。
 */
public final class AiChatConstants {

    /** 消息最大长度 */
    public static final int MAX_MESSAGE_LENGTH = 2000;

    private AiChatConstants() {}
}
