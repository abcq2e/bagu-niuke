package com.qian.qianaiagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 * <p>
 * 参考 Spring AI 官方示例和 LangChain4j 的线程池设计模式：
 * - 主池（taskExecutor）：RAG 检索、异步处理等 I/O 密集型任务
 * - 评分池（scoringExecutor）：LLM 评分调用（隔离，防止评分阻塞主流程）
 * <p>
 * I/O 密集型场景线程数 = CPU 核心数 × (1 + 等待时间/计算时间)，
 * LLM 调用等待时间远大于计算时间，故线程数可设为 CPU 的 2-4 倍。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 主异步任务线程池：RAG 检索、异步业务处理
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cpus = Runtime.getRuntime().availableProcessors();

        executor.setCorePoolSize(Math.max(cpus * 2, 8));
        executor.setMaxPoolSize(Math.max(cpus * 4, 16));
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("async-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 评分专用线程池：与主池隔离，防止 LLM 评分阻塞 RAG 检索
     * <p>
     * 评分是异步非实时需求，小池子足够。
     */
    @Bean(name = "scoringExecutor")
    public Executor scoringExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("async-score-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
