package com.qian.qianaiagent.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 限流注解
 *
 * ===== 🎯 Task 13 第一部分: 这个文件已生成，你只需要理解它 =====
 * 在需要限流的方法上加 @RateLimit，AOP 切面会自动拦截并计数。
 * <p>
 * 示例用法：
 * <pre>
 *   @RateLimit(maxRequests = 5, timeWindow = 60, timeUnit = TimeUnit.SECONDS)
 *   // 代表：60 秒内最多调用 5 次
 * </pre>
 *
 * <h2>📖 元注解回顾</h2>
 * @Target(ElementType.METHOD) — 只能加在方法上
 * @Retention(RetentionPolicy.RUNTIME) — JVM 运行时可通过反射读取（AOP 必须）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 时间窗口内最大允许请求次数 */
    int maxRequests() default 10;

    /** 时间窗口大小 */
    long timeWindow() default 60;

    /** 时间单位（默认秒） */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /** 限流提示信息 */
    String message() default "请求过于频繁，请稍后再试";
}
