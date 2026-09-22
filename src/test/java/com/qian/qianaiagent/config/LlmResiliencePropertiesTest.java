package com.qian.qianaiagent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LlmResiliencePropertiesTest {

    @Test
    @DisplayName("默认值符合 spec：30s 超时、3 次重试、50% 失败率熔断")
    void hasSpecDefaults() {
        var p = new LlmResilienceProperties();

        assertThat(p.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(p.getMaxAttempts()).isEqualTo(3);
        assertThat(p.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(p.getSlidingWindowSize()).isEqualTo(10);
        assertThat(p.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(p.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(60));
        assertThat(p.getMaxConcurrentCalls()).isEqualTo(8);
    }

    @Test
    @DisplayName("退避基数与倍率可配，默认 500ms / 2.0")
    void hasBackoffDefaults() {
        var p = new LlmResilienceProperties();

        assertThat(p.getBackoffBase()).isEqualTo(Duration.ofMillis(500));
        assertThat(p.getBackoffMultiplier()).isEqualTo(2.0);
    }
}
