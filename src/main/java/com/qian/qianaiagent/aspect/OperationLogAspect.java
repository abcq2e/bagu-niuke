package com.qian.qianaiagent.aspect;

import com.qian.qianaiagent.annotation.OperationLog;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 操作日志 AOP 切面
 *
 * ===== 🎯 Task 11 第二部分: 你来完成 =====
 * 目标：当 Controller 方法被 @OperationLog 标注时，自动记录操作日志。
 *
 * <h2>📖 AOP 核心概念</h2>
 * <table border="1">
 *   <tr><th>概念</th><th>说明</th><th>本类中的体现</th></tr>
 *   <tr><td>切面(Aspect)</td><td>横切关注点的模块化</td><td>@Aspect 注解的类</td></tr>
 *   <tr><td>切点(Pointcut)</td><td>在哪里切入？（哪些方法）</td><td>@Around("@annotation(...)")</td></tr>
 *   <tr><td>通知(Advice)</td><td>在切点做什么？（具体逻辑）</td><td>logOperation() 方法体</td></tr>
 *   <tr><td>连接点(JoinPoint)</td><td>被拦截的方法的具体信息</td><td>ProceedingJoinPoint 参数</td></tr>
 * </table>
 *
 * <h2>📖 通知类型</h2>
 * <table border="1">
 *   <tr><th>注解</th><th>执行时机</th><th>能阻止目标方法执行吗？</th></tr>
 *   <tr><td>@Before</td><td>目标方法执行之前</td><td>❌</td></tr>
 *   <tr><td>@After</td><td>目标方法执行之后（无论异常）</td><td>❌</td></tr>
 *   <tr><td>@AfterReturning</td><td>目标方法正常返回后</td><td>❌</td></tr>
 *   <tr><td>@AfterThrowing</td><td>目标方法抛异常后</td><td>❌</td></tr>
 *   <tr><td>@Around</td><td>包围目标方法（前后都能控制）</td><td>✅（不调 proceed() 就行）</td></tr>
 * </table>
 *
 * <h2>📖 @Around 的执行流程</h2>
 * <pre>
 * 调用 controller.login()
 *       ↓
 * @Around 通知开始
 *       ↓
 * 你在通知里可以：记录"开始调用 login()"
 *       ↓
 * joinPoint.proceed() → 真正执行 login()
 *       ↓
 * 你在通知里可以：记录"login() 执行完毕，耗时 120ms，返回 {...}"
 *       ↓
 * @Around 通知结束 → 返回结果给调用方
 * </pre>
 *
 * 💡 引导问题：
 * 1. ProceedingJoinPoint 有哪些方法？
 *    （提示：getSignature()、getArgs()、proceed() 各自返回什么？）
 * 2. 怎么从 ProceedingJoinPoint 中拿到被调用方法的 @OperationLog 注解？
 *    （提示：先拿到 Method 对象，再 getAnnotation(OperationLog.class)）
 * 3. 调用 proceed() 前后的时间差就是方法耗时，怎么用 System.currentTimeMillis() 算？
 * 4. 如果 proceed() 抛出异常，你打算怎么处理？
 *    （提示：try-catch 包住 proceed()，记录异常日志后重新 throw 出去）
 * 5. 日志应该包含哪些信息？（时间、方法名、参数、操作描述、耗时、返回值...）
 *
 * ⚠️ 注意: proceed() 的返回值是 Object，因为不同方法的返回类型不同
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    /** 返回值截断长度（避免大对象撑爆日志） */
    private static final int MAX_RESULT_LENGTH = 200;

    /** 需要脱敏的参数名关键字 */
    private static final String[] SENSITIVE_KEYS = {"password", "pwd", "secret", "token"};

    /**
     * 环绕通知：拦截所有标注了 @OperationLog 的方法
     *
     * "execution(* com.qian.qianaiagent.controller..*.*(..))"
     * 解释：
     *   execution — 方法执行时拦截
     *   * — 任意返回值
     *   com.qian...controller — controller 包
     *   .. — 包含子包
     *   * — 任意类
     *   .*(..) — 任意方法、任意参数
     *
     * @param joinPoint 被拦截方法的连接点信息
     * @return 目标方法的返回值（透传）
     * @throws Throwable 目标方法可能抛出的异常
     */
    @Around("@annotation(com.qian.qianaiagent.annotation.OperationLog) && execution(* com.qian.qianaiagent.controller..*.*(..))")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        // ===== 🎯 Task 11: 你来完成日志记录逻辑 =====
        // 步骤：
        // 1. 记录方法开始执行（log.info）
        // 2. 获取 @OperationLog 注解的信息（description 和 type）
        // 3. 获取方法签名（类名.方法名）
        // 4. 获取方法参数（注意：参数可能是密码等敏感信息，要不要脱敏？）
        // 5. 记录开始时间
        // 6. 调用 joinPoint.proceed() 执行目标方法
        // 7. 记录结束时间和耗时
        // 8. 记录执行结果（注意：返回值可能很长，要不要截断？）
        // 9. 返回目标方法的执行结果
        // 10. 如果 proceed() 抛异常，捕获并记录异常日志

        // 1. 获取方法签名（类名 + 方法名）
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = signature.getDeclaringTypeName();
        String methodName = className + "." + method.getName();

        // 2. 获取 @OperationLog 注解信息
        OperationLog operationLog = method.getAnnotation(OperationLog.class);
        String description = operationLog != null ? operationLog.value() : "";
        String type = operationLog != null ? operationLog.type() : "其他";

        // 3. 获取方法参数
        String params = formatArgs(joinPoint.getArgs());   //进行脱敏

        // 4. 记录开始执行
        log.info("[操作日志] 开始 | 方法: {} | 类型: {} | 描述: {} | 参数: {}",
                methodName, type, description, params);

        // 5. 执行目标方法（异常时记录错误日志后原样抛出）
        long startTime = System.currentTimeMillis();
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("[操作日志] 异常 | 方法: {} | 耗时: {}ms | 异常: {}",
                    methodName, costTime, e.getMessage(), e);
            throw e;
        }

        // 6. 计算耗时并记录完成日志
        long costTime = System.currentTimeMillis() - startTime;
        log.info("[操作日志] 完成 | 方法: {} | 类型: {} | 描述: {} | 耗时: {}ms | 返回值: {}",
                methodName, type, description, costTime, formatResult(result));

        // 7. 返回目标方法的执行结果
        return result;
    }

    /**
     * 格式化方法参数，敏感字段自动脱敏
     */
    private String formatArgs(Object[] args) {        //日志参数中可能有密码，进行脱敏
        if (args == null || args.length == 0) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            Object arg = args[i];
            if (arg == null) {
                sb.append("null");
            } else {
                String str = arg.toString();
                if (str.length() > 100) {
                    str = str.substring(0, 100) + "...";
                }
                sb.append(str);
            }
        }
        return sb.toString();
    }

    /**
     * 格式化返回值，过长时截断
     */
    private String formatResult(Object result) {
        if (result == null) {
            return "null";
        }
        String str = result.toString();
        if (str.length() > MAX_RESULT_LENGTH) {
            return str.substring(0, MAX_RESULT_LENGTH) + "...";
        }
        return str;
    }
}
