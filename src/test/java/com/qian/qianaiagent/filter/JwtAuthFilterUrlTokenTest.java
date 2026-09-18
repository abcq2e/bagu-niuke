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

        // token 不再从 URL 取 → 视为未登录
        assertEquals(401, response.getStatus());
    }
}
