package com.qian.qianaiagent.aspect;

import com.qian.qianaiagent.annotation.RateLimit;
import com.qian.qianaiagent.model.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 限流 AOP 切面（基于 Redis 计数器）
 *
 * ===== 🎯 Task 13 第二部分: 你来完成 =====
 * 目标：用 Redis 的 INCR + EXPIRE 命令实现简单的计数器限流。
 *
 * <h2>📖 核心算法（滑动窗口计数器）</h2>
 * 1. 每个请求到来时，生成一个 Redis key：rate_limit:<方法名>:<IP或用户ID>
 * 2. 用 Redis INCR 命令对该 key 自增 1
 * 3. 如果自增后值为 1（首次访问），给该 key 设 EXPIRE = timeWindow
 * 4. 如果自增后值 > maxRequests，拦截请求，返回 429
 * 5. 时间窗口到期后 key 自动过期，计数器归零 → 新窗口开始
 *
 * <h2>📖 Redis 命令对照</h2>
 * <table border="1">
 *   <tr><th>Redis 命令</th><th>Java 代码</th><th>含义</th></tr>
 *   <tr><td>INCR key</td><td>redisTemplate.opsForValue().increment(key)</td><td>key 自增 1，返回新值</td></tr>
 *   <tr><td>EXPIRE key seconds</td><td>redisTemplate.expire(key, ttl, TimeUnit)</td><td>设过期时间</td></tr>
 *   <tr><td>TTL key</td><td>redisTemplate.getExpire(key)</td><td>查剩余时间</td></tr>
 * </table>
 *
 * <h2>📖 关键细节：为什么先 INCR 再 EXPIRE？</h2>
 * 因为 Redis 的 INCR 和 EXPIRE 不是原子操作。如果在 INCR 之后、EXPIRE 之前服务崩溃，
 * key 永远不会过期 → 所有请求被永久拦截。解决：
 *   - 方案 A: 在 INCR 之前检查 TTL==-1 → 设 EXPIRE（推荐，简单）
 *   - 方案 B: Lua 脚本（原子操作，更难但更安全）
 *
 * 💡 引导问题：
 * 1. Redis key 怎么命名？建议格式：rate_limit:<类名>.<方法名>:<客户端标识>
 *    （提示：IP 地址从 HttpServletRequest 拿，或者从 UserContext 拿当前用户 ID）
 * 2. 怎么拿到当前请求的 IP？（提示：RequestContextHolder + HttpServletRequest）
 * 3. INCR 返回的是什么类型？Long 还是 Integer？
 * 4. 如果 key 已经存在（TTL > 0），还要重新设 EXPIRE 吗？（不需要）
 * 5. 被限流时，应该返回什么？HTTP 状态码 429（Too Many Requests）+ 提示信息
 * 6. 如果 Redis 挂了，限流应该放行还是拦截？
 *    （提示：try-catch Redis 异常，异常时放行——保证可用性优先）
 *
 * ⚠️ 注意: 这个切面和 OperationLogAspect 是独立的，两个都会拦截 Controller 方法
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    // ===== 🎯 Task 13: 注入 RedisTemplate =====
    // 💡 思考：应该用 @Resource 还是构造函数注入？
    // 你的代码写在这里 ↓


    // 你的代码写在这里 ↑

    /**
     * 环绕通知：拦截标注了 @RateLimit 的方法
     *
     * @param joinPoint 连接点
     * @return 如果不超限 → 正常返回；如果超限 → 返回 ApiResponse.error("请求过于频繁")
     */
    @Around("@annotation(com.qian.qianaiagent.annotation.RateLimit)")
    public Object handleRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        // ===== 🎯 Task 13: 你来完成限流逻辑 =====
        // 步骤：
        // 1. 获取 @RateLimit 注解（拿 maxRequests / timeWindow / timeUnit）
        // 2. 获取方法签名（类名.方法名）
        // 3. 获取客户端标识（IP 或用 UserContext.getCurrentUserId()）
        // 4. 拼 Redis key: "rate_limit:" + 类名.方法名 + ":" + 客户端标识
        // 5. 用 redisTemplate.opsForValue().increment(key) 自增
        // 6. 如果自增后为 1（首次访问），设 EXPIRE = timeWindow
        // 7. 如果自增后 > maxRequests → 拦截！返回 ApiResponse.error()
        // 8. 如果未超限 → joinPoint.proceed() 放行
        // 9. 用 try-catch 包住 Redis 操作，Redis 异常时直接放行

        // 你的代码写在这里 ↓
        // 1. 获取注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        // 2. 获取方法信息



        // 3. 获取客户端标识（从请求中取 IP 或用 userId）
        // 提示: ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        //       String ip = attrs.getRequest().getRemoteAddr();

        // 4. 拼 Redis key



        // 5. Redis INCR 自增



        // 6. 首次访问设 EXPIRE



        // 7. 超过限制 → 拦截



        // 8. 未超限 → 放行



        // 9. Redis 异常时的降级处理



        // 你的代码写在这里 ↑

        // 🔴 临时：骨架代码放行（后续完成 Task 13 时删除此行）
        try {
            return joinPoint.proceed();
        } catch (Exception e) {
            throw e;
        }
    }
}
