package com.qian.qianaiagent.annotation;

import java.lang.annotation.*;

/**
 * 自定义操作日志注解
 *
 * ===== 🎯 Task 11 第一部分: 这个文件已生成，你只需要理解它 =====
 * 在 Controller 方法上加 @OperationLog，AOP 切面会自动记录日志。
 *
 * <h2>📖 元注解速查表</h2>
 * <table border="1">
 *   <tr><th>元注解</th><th>作用</th></tr>
 *   <tr><td>@Target</td><td>这个注解可以加在什么地方？（方法、类、字段...）</td></tr>
 *   <tr><td>@Retention</td><td>注解保留到什么时候？（源码、编译期、运行时）</td></tr>
 *   <tr><td>@Documented</td><td>生成 JavaDoc 时是否包含此注解</td></tr>
 * </table>
 *
 * <h2>📖 @Retention 的三个级别</h2>
 * - SOURCE：编译时丢弃（如 @Override）
 * - CLASS：保留到 .class 文件，但 JVM 不加载（默认）
 * - RUNTIME：JVM 运行时可用，可以通过反射读取（AOP 切面必须用这个！）
 *
 * 💡 思考：value() 方法定义了注解参数，使用方式是 @OperationLog("描述文字")，
 *    如果不填 value，默认值是空字符串。
 *    你能让 value 成为可选参数吗？（提示：default 关键字）
 */
//贴标签+挂配件
@Target(ElementType.METHOD)   // 只能用在方法上
@Retention(RetentionPolicy.RUNTIME)  // 运行时可反射获取（AOP 需要）
@Documented
public @interface OperationLog {

    /** 操作描述，不填默认为空 */
    String value() default "";

    /** 操作类型（如：登录、注册、查询...），不填默认为"其他" */
    String type() default "其他";
}
