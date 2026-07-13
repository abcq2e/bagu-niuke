package com.qian.qianaiagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 *
 * ===== 🎯 Task 12 第一部分: 你来完成 =====
 * 目标：配置一个线程池，让 @Async 标注的方法在独立线程中执行。
 *
 * <h2>📖 为什么需要自定义线程池？</h2>
 * Spring Boot 默认的 @Async 线程池（SimpleAsyncTaskExecutor）会为每个任务创建新线程，
 * 没有上限！高并发时会创建大量线程导致 OOM。
 * 所以生产环境必须自定义线程池。
 *
 * <h2>📖 线程池核心参数</h2>
 * <table border="1">
 *   <tr><th>参数</th><th>含义</th><th>如何设置</th></tr>
 *   <tr><td>corePoolSize</td><td>核心线程数（一直存活，即使空闲）</td><td>CPU 核心数 或 CPU*2</td></tr>
 *   <tr><td>maxPoolSize</td><td>最大线程数（繁忙时最多开几个线程）</td><td>corePoolSize * 2 或更大</td></tr>
 *   <tr><td>queueCapacity</td><td>任务队列容量（核心线程都忙时，任务排队）</td><td>根据内存和响应时间权衡</td></tr>
 *   <tr><td>keepAliveSeconds</td><td>超过核心数的线程空闲多久后销毁</td><td>30-60 秒</td></tr>
 * </table>
 *
 * <h2>📖 线程池工作流程</h2>
 * 1. 任务来了 → 先分配给核心线程
 * 2. 核心线程都忙 → 任务进队列排队
 * 3. 队列也满了 → 创建新线程（最多到 maxPoolSize）
 * 4. 线程数到上限 + 队列满 → 执行拒绝策略
 *
 * <h2>📖 拒绝策略（RejectedExecutionHandler）</h2>
 * - CallerRunsPolicy：交给调用线程执行（推荐，不会丢任务）
 * - AbortPolicy：直接抛异常（默认）
 * - DiscardPolicy：静默丢弃
 * - DiscardOldestPolicy：丢弃队列中最老的任务
 *
 * 💡 引导问题：
 * 1. corePoolSize 设多大合适？和 CPU 核心数有什么关系？
 *    （提示：Runtime.getRuntime().availableProcessors() 获取 CPU 核心数）
 * 2. queueCapacity 设多大？设太小 → 频繁开新线程。设太大 → 任务积压、OOM
 * 3. 线程名前缀有什么用？（提示：出现问题时方便在日志中定位是哪个线程池）
 * 4. CallerRunsPolicy 和 AbortPolicy 的区别是什么？各适合什么场景？
 *    （提示：CallerRunsPolicy -> 调用方自己执行，相当于自动降级）
 *
 * ⚠️ 注意：这个 Bean 的 name 是 "taskExecutor"，@Async 注解默认就找这个名字的线程池
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 创建异步任务线程池
     *
     * @return 线程池执行器
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // ===== 🎯 Task 12: 你来配置线程池参数 =====
        // 这个 Agent 项目的特点是：AI 调用是网络 I/O 密集型（等待 LLM 响应）
        // I/O 密集型场景线程数通常比 CPU 密集型多

        // 你的代码写在这里 ↓

        // 核心线程数（提示：CPU核心数 或 CPU*2，I/O密集型可以更大）
        // executor.setCorePoolSize(?);

        // 最大线程数（提示：核心数的 2-4 倍）
        // executor.setMaxPoolSize(?);

        // 任务队列容量（提示：100-500 之间，太大了内存扛不住）
        // executor.setQueueCapacity(?);

        // 空闲线程存活时间（秒）
        // executor.setKeepAliveSeconds(?);

        // 线程名前缀（方便排查问题）
        // executor.setThreadNamePrefix(?);

        // 拒绝策略（提示：CallerRunsPolicy 最安全，不会丢任务）
        // executor.setRejectedExecutionHandler(?);

        // 你的代码写在这里 ↑

        // 初始化线程池
        executor.initialize();
        return executor;
    }
}
