package com.qian.qianaiagent.advisor.guardrail;

import com.qian.qianaiagent.controller.AiChatConstants;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 长度与空值约束。
 *
 * <p>阈值直接复用 {@link AiChatConstants#MAX_MESSAGE_LENGTH} —— 不另设常量，
 * 否则两处口径迟早分叉（控制器放行了、护栏拦截了，或反之）。
 * 为此 {@code AiChatConstants} 已从包私有改为 public（commit fc217c2）。
 *
 * <p>注意：控制器层已有同样的校验。这里是纵深防御的第二道，
 * 覆盖未来新增的、绕过控制器的调用路径。
 */
@Component
public class LengthRule implements InputRule {

    private static final String NAME = "LengthRule";

    @Override
    public Optional<GuardrailVerdict> check(String userInput) {
        if (userInput.isBlank()) {
            return Optional.of(new GuardrailVerdict(NAME, "输入为空"));
        }
        if (userInput.length() > AiChatConstants.MAX_MESSAGE_LENGTH) {
            return Optional.of(new GuardrailVerdict(NAME,
                    "输入长度超过上限 " + AiChatConstants.MAX_MESSAGE_LENGTH));
        }
        return Optional.empty();
    }
}
