package com.qian.qianaiagent.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthFilterUrlTokenTest {

    @Test
    void urlTokenAllowedOnlyOnSseEndpoints() {
        assertTrue(JwtAuthFilter.allowsUrlToken("/api/ai/chat", "/api"));
        assertTrue(JwtAuthFilter.allowsUrlToken("/api/ai/review/chat", "/api"));
        assertTrue(JwtAuthFilter.allowsUrlToken("/api/ai/agent/chat", "/api"));

        assertFalse(JwtAuthFilter.allowsUrlToken("/api/conversations", "/api"));
        assertFalse(JwtAuthFilter.allowsUrlToken("/api/user/profile", "/api"));
        assertFalse(JwtAuthFilter.allowsUrlToken("/api/ai/review/pool/abc", "/api"));
    }

    @Test
    void rejectsUrlTokenOnNonSseEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/conversations");
        request.setRequestURI("/api/conversations");
        request.setContextPath("/api");
        request.addParameter("token", "some.jwt.value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthFilter().doFilter(request, response, new MockFilterChain());

        // 🔴 只断言 401 是不够的：旧代码在 URL token 被取到后会让 jwtUtil 抛 NPE，
        //    被 catch (Exception) 吞掉同样返回 401。必须断言落在「未登录」分支，
        //    否则这个测试在收窄被回退后依然会绿。
        assertEquals(401, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("未登录"), "应走未登录分支，实际响应体: " + body);
        assertFalse(body.contains("认证失败"), "不应触达 token 解析，实际响应体: " + body);
    }
}
