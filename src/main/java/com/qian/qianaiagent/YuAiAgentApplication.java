package com.qian.qianaiagent;

import cn.hutool.json.JSONUtil;
import com.qian.qianaiagent.agent.trace.AgentTrace;
import com.qian.qianaiagent.agent.trace.TraceStep;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

@SpringBootApplication
@ConfigurationPropertiesScan  // 🎯 Task 6: 启用 @ConfigurationProperties 自动扫描
@EnableAsync  // 🎯 Task 12: 启用 Spring 异步任务支持
@EnableScheduling  // 🎯 Task 13: 启用 Spring 定时任务支持
public class YuAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuAiAgentApplication.class, args);
        // 启动后立即跑一次 Trace 演示，验证 Trace 类工作正常
        demoAgentTrace();
    }

    /**
     * 🧠 任务 3+4 演示：模拟一次 Agent 执行，生成完整的 Trace JSON 文件。
     * <p>
     * 运行后去 logs/traces/ 目录下找 demo_{时间戳}.json 文件。
     */
    private static void demoAgentTrace() {
        System.out.println("===== 🧠 Trace 演示开始 =====");

        // —— 任务 3：初始化 AgentTrace ——
        AgentTrace trace = AgentTrace.builder()
                .agentName("demo-agent")
                .startTime(LocalDateTime.now())
                .steps(new ArrayList<>())
                .build();

        // 模拟 3 步执行
        String[] steps = {
                "LLM 推理：用户想搜索'今天天气'，决定调用 WebSearchTool",
                "调用 WebSearchTool.searchWeb('北京今天天气') → 返回 3 条搜索结果",
                "LLM 总结：根据搜索结果，今天北京晴，25°C"
        };

        for (int i = 0; i < steps.length; i++) {
            long start = System.currentTimeMillis();
            // 模拟耗时
            try { Thread.sleep(100 + (long)(Math.random() * 200)); } catch (InterruptedException ignored) {}

            // —— 任务 3：每步构建 TraceStep ——
            TraceStep step = TraceStep.builder()
                    .stepNumber(i + 1)
                    .stepType(i == 1 ? "TOOL_CALL" : "LLM_CALL")
                    .whatHappened(steps[i])
                    .timestamp(LocalDateTime.now())
                    .durationMs(System.currentTimeMillis() - start)
                    .resultSummary(steps[i].length() > 200
                            ? steps[i].substring(0, 200) + "..."
                            : steps[i])
                    .build();
            trace.getSteps().add(step);
            System.out.println("  Step " + (i + 1) + " 完成，耗时 " + step.getDurationMs() + "ms");
        }

        // —— 任务 3：封口 Trace ——
        trace.setEndTime(LocalDateTime.now());
        trace.setFinalState("FINISHED");
        String traceJson = JSONUtil.toJsonPrettyStr(trace);
        System.out.println("\n📋 JSON 预览:\n" + traceJson);

        // —— 任务 4：存到文件 ——
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File traceDir = new File("logs/traces");
        if (!traceDir.exists()) {
            boolean created = traceDir.mkdirs();
            System.out.println("📁 创建目录 " + traceDir.getAbsolutePath() + " → " + (created ? "成功" : "失败"));
        }
        File traceFile = new File(traceDir, "demo_" + timestamp + ".json");
        try (FileWriter writer = new FileWriter(traceFile)) {
            writer.write(traceJson);
            System.out.println("\n✅ Trace 文件已生成：" + traceFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ 保存 Trace 失败：" + e.getMessage());
        }
        System.out.println("===== 🧠 Trace 演示结束 =====");
    }
}
