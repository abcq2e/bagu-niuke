package com.qian.qianaiagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域配置
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /** 🔴 安全修复：不允许任意域名的 credentialed 请求，防止 CSRF 攻击 */
    private static final String[] ALLOWED_ORIGINS = {
            "http://localhost:3001",   // Vite dev server (前端项目)
            "http://localhost:5173",   // Vite dev server (默认)
            "http://localhost:8123",   // Spring Boot dev
            "http://127.0.0.1:3001",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:8123",
            "http://*.natappfree.cc",  // NATAPP 内网穿透
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowCredentials(true)
                .allowedOriginPatterns(ALLOWED_ORIGINS)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*");
    }
}
