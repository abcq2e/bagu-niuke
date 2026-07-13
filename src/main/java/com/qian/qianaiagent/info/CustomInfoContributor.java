package com.qian.qianaiagent.info;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
/**
 * 自定义应用信息贡献者，向 /actuator/info 端点提供项目信息
 */
@Component
public class CustomInfoContributor implements InfoContributor {

    @Value("${info.app.name:qian-ai-agent}")
    private String appName;

    @Value("${info.app.version:0.0.1}")
    private String version;

    @Value("${info.app.description:AI 智能体面试官}")
    private String description;

    private final String startTime;

    public CustomInfoContributor() {
        this.startTime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("appName", appName);
        builder.withDetail("version", version);
        builder.withDetail("description", description);
        builder.withDetail("startTime", startTime);
        builder.withDetail("javaVersion", System.getProperty("java.version"));
        builder.withDetail("osName", System.getProperty("os.name"));
        builder.withDetail("osArch", System.getProperty("os.arch"));
    }
}
