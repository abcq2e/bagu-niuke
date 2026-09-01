package com.qian.qianaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan  // 🎯 Task 6: 启用 @ConfigurationProperties 自动扫描
@EnableAsync  // 🎯 Task 12: 启用 Spring 异步任务支持
@EnableScheduling  // 🎯 Task 13: 启用 Spring 定时任务支持
public class QianAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(QianAiAgentApplication.class, args);
    }
}
