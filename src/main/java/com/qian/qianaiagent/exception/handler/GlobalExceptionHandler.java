package com.qian.qianaiagent.exception.handler;

import com.qian.qianaiagent.exception.BusinessException;
import com.qian.qianaiagent.model.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * ===== 🎯 Task 4: 这个骨架已经生成好了，你来完善 =====
 *
 * <h2>📖 @RestControllerAdvice 做了什么？</h2>
 * 1. 拦截所有 Controller 抛出的异常
 * 2. 根据异常类型找到对应的 @ExceptionHandler 方法
 * 3. 自动转成 JSON 返回给前端
 *
 * <h2>📖 @ExceptionHandler 方法的选择规则</h2>
 * 抛出的异常类型 → Spring 找参数类型最匹配的 Handler：
 * - 抛出 BusinessException → 匹配 handleBusinessException()
 * - 抛出 NullPointerException → 没有专门 Handler → 匹配 handleException()（兜底）
 *
 * 💡 引导问题：
 * 1. BusinessException 应该返回什么 HTTP 状态码？（提示：用 @ResponseStatus 或在 ApiResponse 设 code）
 * 2. 对于未知异常（Exception），应该返回什么信息给前端？
 *    （提示：不要暴露堆栈信息给前端，但要用 log.error 记录完整堆栈）
 * 3. MethodArgumentNotValidException 是 @Valid 校验失败时抛的，怎么提取具体哪个字段校验失败了？
 * 4. ApiResponse 的静态方法有哪些？（ApiResponse.success()、ApiResponse.error()）
 *
 * 📖 你需要添加 @ExceptionHandler 方法处理：
 *    1. BusinessException → 返回 400（你创建的异常）
 *    2. 你创建的其他自定义异常 → 返回对应状态码
 *    3. Exception → 兜底处理，返回 500（防止堆栈信息泄露给用户）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常 → HTTP 400
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ApiResponse.error(400, e.getMessage());
    }

    /**
     * 参数校验失败 → HTTP 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return ApiResponse.error(400, e.getMessage());
    }

    /**
     * @Valid 校验失败 → HTTP 400，提取第一条错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", msg);
        return ApiResponse.error(400, msg);
    }

    /**
     * 兜底处理 — 未知异常 → HTTP 500，不暴露堆栈给前端
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("未捕获异常", e);
        return ApiResponse.error(500, "服务器内部错误，请稍后重试");
    }
}
