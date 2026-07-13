package com.qian.qianaiagent.exception;

/**
 * 业务逻辑异常
 *
 * ===== 🎯 Task 4: 这个骨架已经生成好了 =====
 * 业务异常表示"用户操作不合法"的情况，应该返回 HTTP 400。
 *
 * 💡 引导问题：
 * 1. 这个类应该继承 RuntimeException 还是 Exception？
 *    （提示：RuntimeException 是"非受检异常"，不需要在方法签名上声明 throws）
 * 2. 需要哪些构造函数？至少要有 (String message) 和 (String message, Throwable cause)
 * 3. message 的作用是什么？（提示：最终会显示给前端，告诉用户哪里错了）
 *
 * 📖 继承 RuntimeException 的原因：
 *    - 业务异常通常不需要在调用链中强制处理
 *    - 由全局异常处理器统一捕获并返回给前端
 *    - 代码更简洁，不用到处写 throws
 *
 * ⚠️ 你还需要创建至少一个其他异常类（比如 ResourceNotFoundException 返回 404）
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
