package com.qian.qianaiagent.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public String healthCheck() {
        return "ok";
    }

    @GetMapping("/")
    public void welcome(HttpServletResponse response) throws Exception {
        response.sendRedirect("/api/swagger-ui/index.html");
    }
}
