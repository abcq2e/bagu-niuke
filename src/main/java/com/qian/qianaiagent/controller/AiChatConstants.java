package com.qian.qianaiagent.controller;

/**
 * AI 对话控制器共享常量（InterviewChatController / AgentChatController 共用）。
 * <p>
 * 包私有 final 类，避免在多 controller 间复制漂移；不暴露为公共 API。
 */
final class AiChatConstants {

    /** 消息最大长度 */
    static final int MAX_MESSAGE_LENGTH = 2000;

    private AiChatConstants() {}
}
