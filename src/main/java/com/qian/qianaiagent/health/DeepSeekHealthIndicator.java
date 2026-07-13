package com.qian.qianaiagent.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * DeepSeek API 健康检查指标
 */
@Component
public class DeepSeekHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate;

    public DeepSeekHealthIndicator() {
        this.restTemplate = new RestTemplate();
    }
    //健康检查看看是不是连通的
    @Override
    public Health health() {
        try {
            String result = restTemplate.getForObject(
                    "https://api.deepseek.com/v1/models", String.class);
            return Health.up()
                    .withDetail("message", "DeepSeek API is reachable")
                    .withDetail("endpoint", "https://api.deepseek.com")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("endpoint", "https://api.deepseek.com")
                    .build();
        }
    }
}
