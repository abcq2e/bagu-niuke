package com.qian.qianaiagent.filter;

import com.qian.qianaiagent.context.UserContext;
import com.qian.qianaiagent.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.Resource;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JWT 认证过滤器 —— 拦截所有请求，验证登录状态
 *
 * <h2>📖 什么是 Filter（过滤器）？</h2>
 * <p>
 * Filter 是 Java Web 的"门卫"。所有 HTTP 请求到达 Controller 之前，先经过 Filter。
 * Filter 可以决定：放行（调用 chain.doFilter）或 拦截（直接返回错误）。
 *
 * <pre>
 * 请求 → Filter → Controller → 响应
 *         ↑ 可以拦截
 * </pre>
 *
 * <h2>📖 Filter 三大方法</h2>
 * <ul>
 *   <li>init() — Filter 创建时调用一次（初始化，一般不用）</li>
 *   <li>doFilter() — 每次请求都调用（核心逻辑在这里）</li>
 *   <li>destroy() — Filter 销毁时调用一次（清理，一般不用）</li>
 * </ul>
 *
 * <h2>📖 整体处理流程（理解这个再写代码！）</h2>
 * <ol>
 *   <li>白名单检查 → 登录/注册接口直接放行</li>
 *   <li>从请求头 Authorization 中取出 Token（格式：Bearer xxx）</li>
 *   <li>用 JwtUtil 解析 Token → 提取 userId → 存入 UserContext</li>
 *   <li>放行到 Controller</li>
 *   <li>不管成功还是异常，finally 中清理 UserContext</li>
 * </ol>
 */
@Component
public class JwtAuthFilter implements Filter {

    // ===== 已生成：JWT 工具类注入 =====
    @Resource
    private JwtUtil jwtUtil;

    // ============================================================
    // 白名单：哪些路径不需要登录就能访问
    //
    // 📖 白名单模式 vs 黑名单模式：
    //   白名单："只有这些路径放行，其他都要检查" → 默认拒绝，更安全
    //   黑名单："只有这些路径要检查，其他都放行" → 默认放行，不安全
    //   安全领域几乎都用白名单
    // ============================================================
    private static final String[] WHITE_LIST = {       //白名单
            "/user/login",
            "/user/register",
            ""                             // 根路径 /api
    };

    /**
     * 路径白名单 —— 这些路径前缀不需要登录就能访问（Swagger、静态资源等）
     */
    private static final String[] WHITE_PATH_PREFIXES = {
            "/swagger-ui",
            "/v3/api-docs",
            "/actuator/health",
            "/knife4j",
            "/doc.html",
            "/webjars",
            "/favicon.ico"
    };

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 白名单检查：精确匹配路径（context-path + whitePath），不放行任何额外路径
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();  // 例如 /api

        // 根路径特殊处理（/api 或 /api/ 都放行）
        if (path.equals(contextPath) || path.equals(contextPath + "/")) {
            chain.doFilter(request, response);
            return;
        }

        for (String whitePath : WHITE_LIST) {
            if (path.equals(contextPath + whitePath)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // 路径前缀白名单：放行 Swagger、Knife4j、静态资源等
        for (String whitePrefix : WHITE_PATH_PREFIXES) {
            if (path.startsWith(contextPath + whitePrefix)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // 从请求头 Authorization 或 URL 参数中提取 Token
        // EventSource API 无法发送自定义请求头，所以 SSE 连接通过 URL 参数传 token
        String token = null;
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            // fallback: 从 URL 查询参数中取 token（用于 EventSource SSE 连接）
            token = request.getParameter("token");
        }
        if (token == null || token.isBlank()) {
            sendError(response, "未登录，请先登录");
            return;
        }

        // ===== 🎯 Task 9 第三部分: Token 黑名单检查 =====
        // 场景：用户退出登录后，Token 应该立即失效（即使还没到 JWT 过期时间）。
        // 实现：退出时把 Token 加入 Redis 黑名单，每次请求检查 Token 是否在黑名单中。
        //
        // 💡 引导问题：
        // 1. 黑名单用什么 Redis 数据结构？（Set vs String，各有什么优缺点？）
        // 2. 黑名单 key 怎么命名？（建议格式：blacklist:token:<token前8位>）
        // 3. 黑名单 TTL 设多长？（提示：Token 本身有 7 天过期，取 claims.getExpiration()）
        // 4. Redis 挂了怎么办？应该放行还是拦截？
        //
        // 你的代码写在这里 ↓



        // 你的代码写在这里 ↑

        // 解析 Token → 提取 userId → 存入 UserContext → 放行
        try {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = claims.get("userId", Long.class);
            UserContext.setCurrentUserId(userId);
            chain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            sendError(response, "Token已过期，请重新登录");
        } catch (JwtException e) {
            sendError(response, "Token无效");
        } catch (Exception e) {
            sendError(response, "认证失败");
        } finally {
            UserContext.remove();  // ⚠️ 线程池复用，必须清理，否则下个请求可能读到上一个用户的 ID
        }
    }

    /**
     * 返回 401 未授权响应（工具方法，直接使用即可）
     *
     * <h2>📖 HTTP 状态码 401 vs 403</h2>
     * <ul>
     *   <li>401 Unauthorized — "你是谁？"（没登录/Token无效）</li>
     *   <li>403 Forbidden — "我知道你是谁，但你不够格"（已登录但权限不够）</li>
     * </ul>
     */
    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\": 401, \"message\": \"" + message + "\", \"data\": null}");
    }
}
