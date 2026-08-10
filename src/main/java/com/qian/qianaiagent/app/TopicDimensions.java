package com.qian.qianaiagent.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🔴 [终版-双链路] 各方向必考知识点维度注册表
 *
 * <p>职责：
 * <ul>
 *   <li>维护 16 方向共 128 个维度的枚举定义（DIMENSIONS）</li>
 *   <li>维护维度权重（SUB_DIMENSION_WEIGHTS）</li>
 *   <li>提供维度主体名提取、权重查询等纯工具方法</li>
 * </ul>
 * 不再承担任何关键词匹配、事后分类职责。<br>
 * 维度验证、DIM 标记提取统一由 {@link DimensionValidator} 负责。</p>
 */
public final class TopicDimensions {

    private static final Logger log = LoggerFactory.getLogger(TopicDimensions.class);

    private TopicDimensions() {}

    /** 🔴 [Hotfix-热力图] 权重等级标签 */
    public static final String WEIGHT_HIGH = "高";
    public static final String WEIGHT_MEDIUM = "中";
    public static final String WEIGHT_LOW = "低";

    /** 🔴 [Hotfix-热力图] 方向 → 二级子领域 → 权重值（3/2/1，用于排序决策） */
    public static final Map<String, Map<String, Integer>> SUB_DIMENSION_WEIGHTS = Map.ofEntries(
            Map.entry("Java基础与集合", Map.of(
                    "Java基础语法（关键字/数据类型/运算符）", 1,
                    "面向对象（封装/继承/多态/接口/抽象类）", 2,
                    "异常机制（try-catch/throws/自定义异常）", 1,
                    "泛型（类型擦除/通配符/泛型方法）", 2,
                    "集合框架（HashMap/CopyOnWriteArrayList/TreeSet）", 3,
                    "Stream & Lambda 函数式编程", 2,
                    "IO/NIO（BIO/NIO/AIO/零拷贝）", 2,
                    "反射与注解（动态代理/APT/SPI）", 2
            )),
            Map.entry("Java并发", Map.of(
                    "线程基础（状态/通信/ThreadLocal）", 2,
                    "锁机制（synchronized/ReentrantLock/读写锁）", 3,
                    "JMM（可见性/有序性/原子性/happens-before）", 3,
                    "AQS 原理（CLH队列/独占共享/条件等待）", 3,
                    "线程池（参数/提交/拒绝策略/动态调整）", 3,
                    "并发容器（ConcurrentHashMap/CopyOnWriteArrayList/BlockingQueue）", 2,
                    "CAS 与原子类（ABA/自旋/性能）", 2,
                    "并发工具类（CountDownLatch/CyclicBarrier/Semaphore）", 2
            )),
            Map.entry("JVM", Map.of(
                    "内存结构（堆/栈/方法区/元空间/直接内存）", 2,
                    "GC 算法（标记清除/标记整理/复制/三色标记）", 3,
                    "垃圾收集器（CMS/G1/ZGC/Shenandoah）", 3,
                    "类加载机制（双亲委派/破坏双亲委派/模块化）", 2,
                    "JVM 参数调优（堆配置/OOM 诊断/工具）", 3,
                    "内存泄露排查（MAT/JProfiler/堆转储）", 2,
                    "GC 日志分析（GCViewer/日志格式/调优案例）", 2,
                    "字节码与 JIT 编译（ASM/内联/逃逸分析）", 1
            )),
            Map.entry("Spring框架", Map.of(
                    "IoC 与 DI（BeanFactory/ApplicationContext/自动装配）", 3,
                    "Bean 生命周期（实例化/初始化/销毁/后处理器）", 3,
                    "AOP 原理（JDK 动态代理/CGLIB/切面优先级）", 3,
                    "事务管理（@Transactional/传播机制/隔离级别/失效场景）", 3,
                    "Spring MVC（DispatcherServlet/HandlerInterceptor/统一异常）", 2,
                    "Spring Boot（自动配置/条件装配/起步依赖）", 2,
                    "循环依赖（三级缓存/构造器注入限制）", 3,
                    "Spring Security（认证授权流程/OAuth2/JWT）", 1
            )),
            Map.entry("MySQL", Map.of(
                    "索引（B+树/聚簇索引/最左前缀/索引下推/覆盖索引）", 3,
                    "SQL 优化（explain/慢查询/深分页/索引失效场景）", 3,
                    "事务与隔离级别（MVCC/ReadView/幻读）", 3,
                    "锁机制（行锁/间隙锁/临键锁/死锁分析）", 3,
                    "主从复制与分库分表（binlog/ShardingSphere/mycat）", 2,
                    "日志（redolog/binlog/undolog/两阶段提交）", 2,
                    "存储引擎（InnoDB/MyISAM/行格式/页结构）", 2,
                    "SQL 调优实战（大表DDL/批量操作/连接池）", 2
            )),
            Map.entry("Redis", Map.of(
                    "核心数据结构（String/List/Hash/Set/ZSet/Stream/Geo）", 2,
                    "持久化（RDB/AOF/混合持久化/重写机制）", 2,
                    "集群（主从/Sentinel/Cluster/哈希槽/重定向）", 3,
                    "过期淘汰策略（LRU/LFU/TTL/内存淘汰流程）", 2,
                    "缓存场景（穿透/雪崩/击穿/双删一致性/热key）", 3,
                    "分布式锁（Redlock/Redisson/可重入/续期）", 3,
                    "事务与 Lua 脚本（原子性/EVAL/EVALSHA）", 1,
                    "高性能设计（IO多路复用/单线程模型/管道）", 2
            )),
            Map.entry("消息队列", Map.of(
                    "消息模型（点对点/发布订阅/消费者组）", 1,
                    "可靠性保证（ACK/重试/死信/消息轨迹）", 3,
                    "顺序消息（全局有序/分区有序/ID定义）", 2,
                    "事务消息（半消息/回查/最终一致性）", 2,
                    "死信队列（产生原因/处理策略/重试间隔）", 2,
                    "消息堆积处理（扩容/分流/TTL/降级）", 2,
                    "幂等性设计（唯一ID/去重表/业务幂等）", 2,
                    "主流 MQ 对比（RocketMQ/Kafka/Pulsar/RabbitMQ）", 3
            )),
            Map.entry("计算机网络", Map.of(
                    "TCP/IP（三次握手/四次挥手/拥塞控制/流量控制）", 3,
                    "HTTP/HTTPS（状态码/缓存/版本演进/SSL握手）", 3,
                    "DNS（递归/迭代/域名缓存/CDN 回源）", 2,
                    "负载均衡（四层/七层/一致性Hash/健康检查）", 2,
                    "网络安全（CSRF/XSS/SQL注入/DDOS/HTTPS中间人）", 2,
                    "WebSocket（握手/心跳/二进制帧/网关穿透）", 1,
                    "HTTP2/3（多路复用/头部压缩/QUIC/0-RTT）", 2,
                    "网络模型（IO多路复用/select/poll/epoll/Reactor）", 3
            )),
            Map.entry("操作系统与Linux", Map.of(
                    "进程线程调度（PCB/上下文切换/调度算法/协程）", 3,
                    "内存管理（虚拟内存/分页分段/缺页中断/TLB）", 3,
                    "文件系统（inode/硬软链接/挂载/磁盘IO）", 2,
                    "IO 模型（BIO/NIO/AIO/零拷贝/mmap）", 3,
                    "Linux 命令（top/ps/strace/awk/netstat/iperf）", 2,
                    "网络编程（socket/epoll/Reactor/Netty 线程模型）", 2,
                    "内核优化（sysctl/ulimit/cgroup/swap 调优）", 1,
                    "Shell 脚本（变量/管道/重定向/定时任务/sed）", 1
            )),
            Map.entry("分布式与微服务", Map.of(
                    "CAP/BASE 理论（AP/CP取舍/最终一致性）", 3,
                    "服务发现与注册（Nacos/Eureka/Consul/选主）", 3,
                    "配置中心（Nacos/Apollo/配置热更新/版本回滚）", 2,
                    "限流熔断降级（Sentinel/Hystrix/滑动窗口/漏桶令牌桶）", 3,
                    "分布式事务（Seata TCC/AT/Saga/可靠消息）", 3,
                    "分布式 ID（雪花算法/美团Leaf/uid-generator）", 2,
                    "RPC 原理（Dubbo/gRPC/序列化/服务暴露/负载均衡）", 3,
                    "链路追踪（Skywalking/Zipkin/OpenTelemetry/采样策略）", 1
            )),
            Map.entry("算法与数据结构", Map.of(
                    "排序（快排/归并/堆排/计数排序/排序稳定性）", 3,
                    "查找（二分查找/跳表/布隆过滤器/倒排索引）", 3,
                    "链表（反转/判环/合并/相交/快慢指针）", 3,
                    "树（二叉树遍历/LCA/红黑树/B+树/Trie树）", 3,
                    "图（BFS/DFS/拓扑排序/最短路/最小生成树）", 2,
                    "动态规划（背包/LIS/LCS/区间DP/状态压缩）", 3,
                    "哈希（HashMap 原理/一致性哈希/哈希冲突解决）", 2,
                    "字符串（KMP/Rabin-Karp/AC自动机/字典序）", 2
            )),
            Map.entry("设计模式", Map.of(
                    "创建型（单例/工厂/建造者/原型——适用场景与变体）", 3,
                    "结构型（代理/适配器/装饰器/组合/外观——对比选择）", 3,
                    "行为型（策略/模板/观察者/责任链/状态——源码分析）", 3,
                    "面向对象设计原则（SOLID/迪米特/合成复用）", 2,
                    "源码中的应用（JDK Collections/Spring 中的模式）", 2,
                    "重构与坏代码味道（长方法/过度耦合/重复代码）", 1,
                    "UML（类图/时序图/用例图/领域模型设计）", 1,
                    "反模式（God Object/面条代码/复制粘贴/黄金锤）", 1
            )),
            Map.entry("系统设计与场景", Map.of(
                    "高并发设计（读写分离/CQRS/缓存多级/SLA 设定）", 3,
                    "高可用设计（故障转移/降级/重试-退避/多活架构）", 3,
                    "微服务架构（服务拆分/BFF/API网关/容器化）", 3,
                    "数据库设计（分库分表/ER图/索引覆盖/读写隔离）", 2,
                    "缓存设计（多级缓存/缓存策略/更新一致性/热点处理）", 3,
                    "异步架构（事件驱动/消息解耦/异步编排/回调）", 2,
                    "容灾备份（异地多活/数据复制/备份恢复/混沌工程）", 1,
                    "监控告警（Metrics/Tracing/Logging/Grafana 面板）", 1
            )),
            Map.entry("Docker与运维", Map.of(
                    "容器原理（Namespace/Cgroup/UnionFS/Docker 架构）", 3,
                    "Dockerfile（多阶段构建/层缓存/最佳实践/dockerignore）", 2,
                    "Docker Compose（服务编排/网络/卷/环境变量管理）", 2,
                    "Kubernetes 基础（Pod/Deployment/Service/ConfigMap/Ingress）", 3,
                    "CI/CD（GitLab CI/GitHub Actions/Jenkins 流水线/自动化测试）", 2,
                    "监控与告警（Prometheus/Grafana/Node Exporter/告警规则）", 2,
                    "日志收集（ELK/Loki/Filebeat/Fluentd/日志采集方案）", 1,
                    "镜像优化（基础镜像选择/瘦身/安全扫描/多架构构建）", 1
            )),
            Map.entry("ES与搜索", Map.of(
                    "倒排索引原理（TF-IDF/BM25/段合并/索引刷新）", 3,
                    "分词器（IK/pinyin/自定义词典/同义词/停用词）", 2,
                    "DSL 查询（term/match/bool/range/聚合查询/嵌套查询）", 3,
                    "聚合分析（Bucket/Metrics/Pipeline/聚合排序/性能）", 2,
                    "集群管理（分片/副本/路由/脑裂/滚动升级）", 3,
                    "映射与分析器（dynamic/explicit/字段类型/多字段设计）", 2,
                    "优化策略（查询缓存/写入优化/冷热分离/forcemerge）", 2,
                    "搜索引擎对比（ES/Solr/Manticore/Zinc/选型依据）", 1
            )),
            Map.entry("Agent与AI应用", Map.of(
                    "LLM 原理（Transformer/注意力机制/预训练/微调/RLHF）", 3,
                    "Prompt 工程（思维链/少样本/结构化提示/上下文窗口）", 2,
                    "RAG（向量检索/分块策略/重排序/HyDE/多模态RAG）", 3,
                    "Agent 设计（规划/记忆/工具调用/多Agent协作）", 3,
                    "MCP 与工具（SSE/工具定义/函数调用/结果处理）", 3,
                    "模型部署（VLLM/TGI/Ollama/量化/推理加速）", 1,
                    "AI 应用架构（流式响应/安全护栏/缓存/可观测性）", 2,
                    "安全与对齐（幻觉缓解/越狱防护/PII脱敏/红队测试）", 1
            ))
    );

    /**
     * 🔴 [Hotfix-热力图] 获取指定方向中子领域的权重显示标签。
     */
    public static String getSubDimensionWeight(String topic, String dim) {
        int w = getSubDimensionWeightValue(topic, dim);
        return w >= 3 ? WEIGHT_HIGH : (w >= 2 ? WEIGHT_MEDIUM : WEIGHT_LOW);
    }

    /**
     * 🔴 [Hotfix-热力图] 获取指定方向中子领域的权重数值（3/2/1）。
     */
    public static int getSubDimensionWeightValue(String topic, String dim) {
        Map<String, Integer> topicWeights = SUB_DIMENSION_WEIGHTS.get(topic);
        if (topicWeights == null) return 2; // 默认中权重
        return topicWeights.getOrDefault(dim, 2);
    }

    /**
     * 🔴 [热力图-增强] 从二级子领域名称中提取候选细分考点关键词。
     * <p>
     * 例如："锁机制（synchronized/ReentrantLock/读写锁）" →
     * ["synchronized", "ReentrantLock", "读写锁"]
     * 用于在热力图中提示 AI 该子领域下还有哪些未覆盖的细节点可出题。
     *
     * @param dim 二级子领域全名（含括号内容）
     * @return 候选考点关键词列表（去重，长度≥2）
     */
    public static List<String> getSubDimensionKeywords(String dim) {
        if (dim == null || dim.isBlank()) return List.of();
        List<String> result = new ArrayList<>();

        // 提取括号内的内容
        int parenStart = dim.indexOf('（');
        int parenEnd = dim.indexOf('）');
        if (parenStart < 0 || parenEnd < 0) {
            // 无括号时，用斜杠拆分维度名本身
            String[] parts = dim.split("[/,，]");
            for (String p : parts) {
                String t = p.trim();
                if (t.length() >= 2) result.add(t);
            }
            return result;
        }

        String content = dim.substring(parenStart + 1, parenEnd);
        String[] keywords = content.split("[/、,，]");
        for (String kw : keywords) {
            String trimmed = kw.trim();
            if (trimmed.length() >= 2) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** 方向 → 知识点维度列表 */
    public static final Map<String, List<String>> DIMENSIONS = Map.ofEntries(
            Map.entry("Java基础与集合", List.of(
                    "Java基础语法（关键字/数据类型/运算符）",
                    "面向对象（封装/继承/多态/接口/抽象类）",
                    "异常机制（try-catch/throws/自定义异常）",
                    "泛型（类型擦除/通配符/泛型方法）",
                    "集合框架（HashMap/CopyOnWriteArrayList/TreeSet）",
                    "Stream & Lambda 函数式编程",
                    "IO/NIO（BIO/NIO/AIO/零拷贝）",
                    "反射与注解（动态代理/APT/SPI）"
            )),
            Map.entry("Java并发", List.of(
                    "线程基础（状态/通信/ThreadLocal）",
                    "锁机制（synchronized/ReentrantLock/读写锁）",
                    "JMM（可见性/有序性/原子性/happens-before）",
                    "AQS 原理（CLH队列/独占共享/条件等待）",
                    "线程池（参数/提交/拒绝策略/动态调整）",
                    "并发容器（ConcurrentHashMap/CopyOnWriteArrayList/BlockingQueue）",
                    "CAS 与原子类（ABA/自旋/性能）",
                    "并发工具类（CountDownLatch/CyclicBarrier/Semaphore）"
            )),
            Map.entry("JVM", List.of(
                    "内存结构（堆/栈/方法区/元空间/直接内存）",
                    "GC 算法（标记清除/标记整理/复制/三色标记）",
                    "垃圾收集器（CMS/G1/ZGC/Shenandoah）",
                    "类加载机制（双亲委派/破坏双亲委派/模块化）",
                    "JVM 参数调优（堆配置/OOM 诊断/工具）",
                    "内存泄露排查（MAT/JProfiler/堆转储）",
                    "GC 日志分析（GCViewer/日志格式/调优案例）",
                    "字节码与 JIT 编译（ASM/内联/逃逸分析）"
            )),
            Map.entry("Spring框架", List.of(
                    "IoC 与 DI（BeanFactory/ApplicationContext/自动装配）",
                    "Bean 生命周期（实例化/初始化/销毁/后处理器）",
                    "AOP 原理（JDK 动态代理/CGLIB/切面优先级）",
                    "事务管理（@Transactional/传播机制/隔离级别/失效场景）",
                    "Spring MVC（DispatcherServlet/HandlerInterceptor/统一异常）",
                    "Spring Boot（自动配置/条件装配/起步依赖）",
                    "循环依赖（三级缓存/构造器注入限制）",
                    "Spring Security（认证授权流程/OAuth2/JWT）"
            )),
            Map.entry("MySQL", List.of(
                    "索引（B+树/聚簇索引/最左前缀/索引下推/覆盖索引）",
                    "SQL 优化（explain/慢查询/深分页/索引失效场景）",
                    "事务与隔离级别（MVCC/ReadView/幻读）",
                    "锁机制（行锁/间隙锁/临键锁/死锁分析）",
                    "主从复制与分库分表（binlog/ShardingSphere/mycat）",
                    "日志（redolog/binlog/undolog/两阶段提交）",
                    "存储引擎（InnoDB/MyISAM/行格式/页结构）",
                    "SQL 调优实战（大表DDL/批量操作/连接池）"
            )),
            Map.entry("Redis", List.of(
                    "核心数据结构（String/List/Hash/Set/ZSet/Stream/Geo）",
                    "持久化（RDB/AOF/混合持久化/重写机制）",
                    "集群（主从/Sentinel/Cluster/哈希槽/重定向）",
                    "过期淘汰策略（LRU/LFU/TTL/内存淘汰流程）",
                    "缓存场景（穿透/雪崩/击穿/双删一致性/热key）",
                    "分布式锁（Redlock/Redisson/可重入/续期）",
                    "事务与 Lua 脚本（原子性/EVAL/EVALSHA）",
                    "高性能设计（IO多路复用/单线程模型/管道）"
            )),
            Map.entry("消息队列", List.of(
                    "消息模型（点对点/发布订阅/消费者组）",
                    "可靠性保证（ACK/重试/死信/消息轨迹）",
                    "顺序消息（全局有序/分区有序/ID定义）",
                    "事务消息（半消息/回查/最终一致性）",
                    "死信队列（产生原因/处理策略/重试间隔）",
                    "消息堆积处理（扩容/分流/TTL/降级）",
                    "幂等性设计（唯一ID/去重表/业务幂等）",
                    "主流 MQ 对比（RocketMQ/Kafka/Pulsar/RabbitMQ）"
            )),
            Map.entry("计算机网络", List.of(
                    "TCP/IP（三次握手/四次挥手/拥塞控制/流量控制）",
                    "HTTP/HTTPS（状态码/缓存/版本演进/SSL握手）",
                    "DNS（递归/迭代/域名缓存/CDN 回源）",
                    "负载均衡（四层/七层/一致性Hash/健康检查）",
                    "网络安全（CSRF/XSS/SQL注入/DDOS/HTTPS中间人）",
                    "WebSocket（握手/心跳/二进制帧/网关穿透）",
                    "HTTP2/3（多路复用/头部压缩/QUIC/0-RTT）",
                    "网络模型（IO多路复用/select/poll/epoll/Reactor）"
            )),
            Map.entry("操作系统与Linux", List.of(
                    "进程线程调度（PCB/上下文切换/调度算法/协程）",
                    "内存管理（虚拟内存/分页分段/缺页中断/TLB）",
                    "文件系统（inode/硬软链接/挂载/磁盘IO）",
                    "IO 模型（BIO/NIO/AIO/零拷贝/mmap）",
                    "Linux 命令（top/ps/strace/awk/netstat/iperf）",
                    "网络编程（socket/epoll/Reactor/Netty 线程模型）",
                    "内核优化（sysctl/ulimit/cgroup/swap 调优）",
                    "Shell 脚本（变量/管道/重定向/定时任务/sed）"
            )),
            Map.entry("分布式与微服务", List.of(
                    "CAP/BASE 理论（AP/CP取舍/最终一致性）",
                    "服务发现与注册（Nacos/Eureka/Consul/选主）",
                    "配置中心（Nacos/Apollo/配置热更新/版本回滚）",
                    "限流熔断降级（Sentinel/Hystrix/滑动窗口/漏桶令牌桶）",
                    "分布式事务（Seata TCC/AT/Saga/可靠消息）",
                    "分布式 ID（雪花算法/美团Leaf/uid-generator）",
                    "RPC 原理（Dubbo/gRPC/序列化/服务暴露/负载均衡）",
                    "链路追踪（Skywalking/Zipkin/OpenTelemetry/采样策略）"
            )),
            Map.entry("算法与数据结构", List.of(
                    "排序（快排/归并/堆排/计数排序/排序稳定性）",
                    "查找（二分查找/跳表/布隆过滤器/倒排索引）",
                    "链表（反转/判环/合并/相交/快慢指针）",
                    "树（二叉树遍历/LCA/红黑树/B+树/Trie树）",
                    "图（BFS/DFS/拓扑排序/最短路/最小生成树）",
                    "动态规划（背包/LIS/LCS/区间DP/状态压缩）",
                    "哈希（HashMap 原理/一致性哈希/哈希冲突解决）",
                    "字符串（KMP/Rabin-Karp/AC自动机/字典序）"
            )),
            Map.entry("设计模式", List.of(
                    "创建型（单例/工厂/建造者/原型——适用场景与变体）",
                    "结构型（代理/适配器/装饰器/组合/外观——对比选择）",
                    "行为型（策略/模板/观察者/责任链/状态——源码分析）",
                    "面向对象设计原则（SOLID/迪米特/合成复用）",
                    "源码中的应用（JDK Collections/Spring 中的模式）",
                    "重构与坏代码味道（长方法/过度耦合/重复代码）",
                    "UML（类图/时序图/用例图/领域模型设计）",
                    "反模式（God Object/面条代码/复制粘贴/黄金锤）"
            )),
            Map.entry("系统设计与场景", List.of(
                    "高并发设计（读写分离/CQRS/缓存多级/SLA 设定）",
                    "高可用设计（故障转移/降级/重试-退避/多活架构）",
                    "微服务架构（服务拆分/BFF/API网关/容器化）",
                    "数据库设计（分库分表/ER图/索引覆盖/读写隔离）",
                    "缓存设计（多级缓存/缓存策略/更新一致性/热点处理）",
                    "异步架构（事件驱动/消息解耦/异步编排/回调）",
                    "容灾备份（异地多活/数据复制/备份恢复/混沌工程）",
                    "监控告警（Metrics/Tracing/Logging/Grafana 面板）"
            )),
            Map.entry("Docker与运维", List.of(
                    "容器原理（Namespace/Cgroup/UnionFS/Docker 架构）",
                    "Dockerfile（多阶段构建/层缓存/最佳实践/dockerignore）",
                    "Docker Compose（服务编排/网络/卷/环境变量管理）",
                    "Kubernetes 基础（Pod/Deployment/Service/ConfigMap/Ingress）",
                    "CI/CD（GitLab CI/GitHub Actions/Jenkins 流水线/自动化测试）",
                    "监控与告警（Prometheus/Grafana/Node Exporter/告警规则）",
                    "日志收集（ELK/Loki/Filebeat/Fluentd/日志采集方案）",
                    "镜像优化（基础镜像选择/瘦身/安全扫描/多架构构建）"
            )),
            Map.entry("ES与搜索", List.of(
                    "倒排索引原理（TF-IDF/BM25/段合并/索引刷新）",
                    "分词器（IK/pinyin/自定义词典/同义词/停用词）",
                    "DSL 查询（term/match/bool/range/聚合查询/嵌套查询）",
                    "聚合分析（Bucket/Metrics/Pipeline/聚合排序/性能）",
                    "集群管理（分片/副本/路由/脑裂/滚动升级）",
                    "映射与分析器（dynamic/explicit/字段类型/多字段设计）",
                    "优化策略（查询缓存/写入优化/冷热分离/forcemerge）",
                    "搜索引擎对比（ES/Solr/Manticore/Zinc/选型依据）"
            )),
            Map.entry("Agent与AI应用", List.of(
                    "LLM 原理（Transformer/注意力机制/预训练/微调/RLHF）",
                    "Prompt 工程（思维链/少样本/结构化提示/上下文窗口）",
                    "RAG（向量检索/分块策略/重排序/HyDE/多模态RAG）",
                    "Agent 设计（规划/记忆/工具调用/多Agent协作）",
                    "MCP 与工具（SSE/工具定义/函数调用/结果处理）",
                    "模型部署（VLLM/TGI/Ollama/量化/推理加速）",
                    "AI 应用架构（流式响应/安全护栏/缓存/可观测性）",
                    "安全与对齐（幻觉缓解/越狱防护/PII脱敏/红队测试）"
            ))
    );

    /**
     * 获取指定方向的知识点维度列表
     */
    public static List<String> getDimensions(String topic) {
        return DIMENSIONS.getOrDefault(topic, List.of());
    }

    /**
     * 🔴 [废弃] 关键词维度推断方法，已被 {@link DimensionValidator} 取代。
     * <p>
     * 保留仅用于向后兼容，新代码请使用 {@link DimensionValidator#extractDimTags(String)}。
     *
     * @deprecated 不再使用关键词匹配。维度追踪走预选+DIM确认链路，
     * 弱点评维度由评分AI源头标注。请迁移到 {@link DimensionValidator}。
     */
    @Deprecated(since = "1.0")
    public static String inferDimensionFromMarker(String aiResponse, String topic) {
        if (aiResponse == null || topic == null) return null;
        List<String> tags = com.qian.qianaiagent.app.DimensionValidator.extractDimTags(aiResponse);
        if (tags.isEmpty()) return null;
        String markerName = tags.get(tags.size() - 1);
        List<String> dims = getDimensions(topic);
        if (dims.isEmpty()) return markerName;
        for (String dim : dims) {
            if (dim.startsWith(markerName) || dim.contains(markerName)) {
                return dim;
            }
        }
        return null;
    }


    /**
     * 🔴 [全覆盖] 获取指定维度下的知识点列表。
     * <p>
     * 从维度括号内容中提取（如"锁机制（synchronized/ReentrantLock/读写锁）"
     * → ["synchronized", "ReentrantLock", "读写锁"]）。
     * 与 {@link #getSubDimensionKeywords} 逻辑相同，语义更清晰。
     *
     * @param dim 维度全名（含括号内容）
     * @return 知识点名称列表
     */
    public static List<String> getKnowledgePoints(String dim) {
        return getSubDimensionKeywords(dim);
    }

    /**
     * 🔴 [全覆盖] 获取指定维度期望最低出题数。
     * <p>
     * 规则：每个知识点至少出 1 题，下限 2 题，上限 5 题。
     * 该值决定维度何时达到"饱和度"——所有知识点都考核过后才算覆盖完成。
     *
     * @param topic 方向名（当前未使用，保留扩展）
     * @param dim   维度全名
     * @return 期望最低出题数
     */
    public static int getExpectedMinQuestions(String topic, String dim) {
        List<String> kps = getKnowledgePoints(dim);
        if (kps.isEmpty()) return 2; // 无知识点信息时默认 2 题
        // 每个知识点至少 1 题，下限 2 上限 5
        return Math.max(2, Math.min(5, kps.size()));
    }

    /** 返回维度在全角左括号前的主体名称。 */
    public static String dimensionSubject(String dimension) {
        if (dimension == null || dimension.isBlank()) return "";
        int parenthesisIndex = dimension.indexOf('（');
        return (parenthesisIndex >= 0 ? dimension.substring(0, parenthesisIndex) : dimension).trim();
    }

    /**
     * 🔴 [简化版] 检查文本是否包含维度主体名。
     * <p>
     * 仅检查维度括号前的主体名是否出现在文本中（如"锁机制"），
     * 不做括号内关键词匹配，不维护关键词表。
     * 用于 {@link WeakPointNormalizer} 等降级链路的简单过滤。
     */
    public static boolean matchesDimension(String dimension, String text) {
        if (dimension == null || dimension.isBlank()) return true;
        if (text == null || text.isBlank()) return false;
        String subject = dimensionSubject(dimension);
        return subject.isEmpty()
                || text.toLowerCase(java.util.Locale.ROOT)
                       .contains(subject.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * 🔴 [Bug修复] 短概念名 → 归属方向映射表。
     * <p>
     * 覆盖关键字系统无法匹配的简短技术概念（如"值传递"、"AQS"等）。
     * 主要用于 {@link #containsForeignTopicKeyword} 的跨方向检测。
     * 新概念按需添加，格式：全小写概念名 → 归属方向名。
     */
    public static final Map<String, String> CROSS_TOPIC_CONCEPTS = buildCrossTopicConcepts();

    private static Map<String, String> buildCrossTopicConcepts() {
        Map<String, String> m = new HashMap<>();
        // Java基础与集合
        m.put("值传递", "Java基础与集合");
        m.put("传引用", "Java基础与集合");
        m.put("值类型", "Java基础与集合");
        m.put("引用类型", "Java基础与集合");
        m.put("泛型方法", "Java基础与集合");
        m.put("类型擦除", "Java基础与集合");
        m.put("SPI", "Java基础与集合");
        m.put("Netty", "Java基础与集合");
        // Java并发
        m.put("AQS", "Java并发");
        m.put("CAS", "Java并发");
        m.put("ABA", "Java并发");
        m.put("JMM", "Java并发");
        m.put("ThreadLocal", "Java并发");
        m.put("死锁", "Java并发");
        // JVM
        m.put("OOM", "JVM");
        m.put("StackOverflow", "JVM");
        m.put("TLAB", "JVM");
        m.put("双亲委派", "JVM");
        m.put("GC Roots", "JVM");
        m.put("三色标记", "JVM");
        m.put("STW", "JVM");
        m.put("CMS", "JVM");
        m.put("G1", "JVM");
        m.put("ZGC", "JVM");
        m.put("元空间", "JVM");
        m.put("直接内存", "JVM");
        // 🔴 以下概念跨方向共享率高，已移除由票数制投票决定：
        //   "内存模型" — 可能指JVM内存结构或Java内存模型(JMM)；"内存泄漏" — 任何语言都可能
        //   JMM已在Java并发维度明确定义，JVM内存概念由"堆内存/栈内存/方法区"等精确词覆盖
        // MySQL
        m.put("MVCC", "MySQL");
        m.put("B+树", "MySQL");
        m.put("redolog", "MySQL");
        m.put("binlog", "MySQL");
        m.put("undolog", "MySQL");
        // Redis
        m.put("热key", "Redis");
        m.put("大key", "Redis");
        m.put("Redlock", "Redis");
        m.put("Redisson", "Redis");
        m.put("缓存穿透", "Redis");
        m.put("缓存雪崩", "Redis");
        m.put("缓存击穿", "Redis");
        // 消息队列
        m.put("死信", "消息队列");
        m.put("消息堆积", "消息队列");
        m.put("幂等", "消息队列");
        m.put("RocketMQ", "消息队列");
        m.put("Kafka", "消息队列");
        // 计算机网络
        m.put("三次握手", "计算机网络");
        m.put("四次挥手", "计算机网络");
        m.put("拥塞控制", "计算机网络");
        m.put("流量控制", "计算机网络");
        // 操作系统
        m.put("零拷贝", "操作系统与Linux");
        m.put("mmap", "操作系统与Linux");
        m.put("cgroup", "操作系统与Linux");
        m.put("Namespace", "操作系统与Linux");
        m.put("虚拟内存", "操作系统与Linux");
        // 分布式与微服务
        m.put("CAP", "分布式与微服务");
        m.put("BASE", "分布式与微服务");
        m.put("TCC", "分布式与微服务");
        m.put("Saga", "分布式与微服务");
        m.put("Nacos", "分布式与微服务");
        m.put("Dubbo", "分布式与微服务");
        m.put("Sentinel", "分布式与微服务");
        m.put("Seata", "分布式与微服务");
        m.put("服务雪崩", "分布式与微服务");
        m.put("链路追踪", "分布式与微服务");
        m.put("Skywalking", "分布式与微服务");
        // 算法
        m.put("KMP", "算法与数据结构");
        m.put("LRU", "算法与数据结构");
        m.put("LFU", "算法与数据结构");
        m.put("布隆过滤器", "算法与数据结构");
        m.put("红黑树", "算法与数据结构");
        m.put("快速排序", "算法与数据结构");
        m.put("快排", "算法与数据结构");
        m.put("归并排序", "算法与数据结构");
        m.put("堆排序", "算法与数据结构");
        m.put("冒泡排序", "算法与数据结构");
        m.put("选择排序", "算法与数据结构");
        m.put("插入排序", "算法与数据结构");
        m.put("希尔排序", "算法与数据结构");
        m.put("计数排序", "算法与数据结构");
        m.put("基数排序", "算法与数据结构");
        m.put("桶排序", "算法与数据结构");
        m.put("二分查找", "算法与数据结构");
        m.put("二分搜索", "算法与数据结构");
        m.put("动态规划", "算法与数据结构");
        m.put("贪心算法", "算法与数据结构");
        m.put("回溯算法", "算法与数据结构");
        m.put("BFS", "算法与数据结构");
        m.put("DFS", "算法与数据结构");
        m.put("广度优先", "算法与数据结构");
        m.put("深度优先", "算法与数据结构");
        m.put("链表反转", "算法与数据结构");
        m.put("单链表", "算法与数据结构");
        m.put("双链表", "算法与数据结构");
        m.put("二叉树", "算法与数据结构");
        m.put("平衡二叉树", "算法与数据结构");
        m.put("AVL树", "算法与数据结构");
        m.put("哈希表", "算法与数据结构");
        m.put("栈和队列", "算法与数据结构");
        m.put("滑动窗口", "算法与数据结构");
        m.put("双指针", "算法与数据结构");
        // Java基础与集合
        m.put("HashMap", "Java基础与集合");
        m.put("ConcurrentHashMap", "Java基础与集合");
        m.put("ArrayList", "Java基础与集合");
        m.put("LinkedList", "Java基础与集合");
        m.put("HashSet", "Java基础与集合");
        m.put("TreeMap", "Java基础与集合");
        m.put("equals和hashCode", "Java基础与集合");
        m.put("final关键字", "Java基础与集合");
        m.put("static关键字", "Java基础与集合");
        m.put("抽象类", "Java基础与集合");
        m.put("接口", "Java基础与集合");
        m.put("多态", "Java基础与集合");
        m.put("封装", "Java基础与集合");
        m.put("继承", "Java基础与集合");
        m.put("异常处理", "Java基础与集合");
        m.put("反射", "Java基础与集合");
        m.put("注解", "Java基础与集合");
        m.put("泛型", "Java基础与集合");
        // JVM
        m.put("GC算法", "JVM");
        m.put("垃圾回收", "JVM");
        m.put("堆内存", "JVM");
        m.put("栈内存", "JVM");
        m.put("方法区", "JVM");
        m.put("类加载", "JVM");
        m.put("JVM调优", "JVM");
        m.put("内存溢出", "JVM");
        // Spring框架
        m.put("Bean生命周期", "Spring框架");
        m.put("Bean的生命周期", "Spring框架");
        m.put("IoC容器", "Spring框架");
        m.put("控制反转", "Spring框架");
        m.put("依赖注入", "Spring框架");
        m.put("DI", "Spring框架");
        m.put("AOP", "Spring框架");
        m.put("面向切面", "Spring框架");
        m.put("Spring事务", "Spring框架");
        m.put("事务传播", "Spring框架");
        m.put("循环依赖", "Spring框架");
        m.put("SpringMVC", "Spring框架");
        m.put("SpringBoot", "Spring框架");
        m.put("自动配置", "Spring框架");
        m.put("starter机制", "Spring框架");
        // MySQL
        m.put("索引结构", "MySQL");
        m.put("聚簇索引", "MySQL");
        m.put("非聚簇索引", "MySQL");
        m.put("覆盖索引", "MySQL");
        m.put("最左前缀", "MySQL");
        m.put("索引优化", "MySQL");
        m.put("事务隔离级别", "MySQL");
        m.put("幻读", "MySQL");
        m.put("不可重复读", "MySQL");
        m.put("脏读", "MySQL");
        m.put("行锁", "MySQL");
        m.put("表锁", "MySQL");
        m.put("间隙锁", "MySQL");
        m.put("Next-Key Lock", "MySQL");
        m.put("主从复制", "MySQL");
        // Redis
        m.put("数据类型", "Redis");
        m.put("String类型", "Redis");
        m.put("Hash类型", "Redis");
        m.put("List类型", "Redis");
        m.put("Set类型", "Redis");
        m.put("ZSet类型", "Redis");
        m.put("持久化", "Redis");
        m.put("RDB", "Redis");
        m.put("AOF", "Redis");
        m.put("主从同步", "Redis");
        m.put("哨兵模式", "Redis");
        m.put("集群模式", "Redis");
        m.put("RedisCluster", "Redis");
        m.put("哈希槽", "Redis");
        m.put("过期策略", "Redis");
        m.put("内存淘汰", "Redis");
        // 设计模式
        m.put("SOLID", "设计模式");
        m.put("单例", "设计模式");
        m.put("动态代理", "设计模式");
        // Spring框架
        m.put("三级缓存", "Spring框架");
        // Docker
        m.put("Dockerfile", "Docker与运维");
        m.put("K8s", "Docker与运维");
        m.put("Kubernetes", "Docker与运维");
        m.put("Pod", "Docker与运维");
        m.put("Service", "Docker与运维");
        // Agent与AI
        m.put("RAG", "Agent与AI应用");
        m.put("HyDE", "Agent与AI应用");
        m.put("MCP", "Agent与AI应用");
        m.put("SSE", "Agent与AI应用");
        m.put("Agent", "Agent与AI应用");
        // 🔴 统一转为小写 key，保证所有查询大小写不敏感
        Map<String, String> result = new HashMap<>();
        for (java.util.Map.Entry<String, String> e : m.entrySet()) {
            result.put(e.getKey().toLowerCase(java.util.Locale.ROOT), e.getValue());
        }
        // 验证无重复 key（assert 在生产环境默认关闭，不影响性能）
        assert result.size() == m.size()
            : "CROSS_TOPIC_CONCEPTS 存在重复 key，实际 key 数=" + m.size() + "，去重后=" + result.size();
        return Map.copyOf(result);
    }

    /**
     * 🔴 跨方向脏数据检测（票数制，避免误判）。
     * <p>
     * 两步判定：
     * <ol>
     *   <li>CROSS_TOPIC_CONCEPTS 硬映射：精确/子串命中 → 立即判定</li>
     *   <li>票数制：统计当前方向 vs 其他方向的维度关键词命中数，
     *       仅当外来方向得分严格高于当前方向时判为跨方向</li>
     * </ol>
     * <p>
     * 权重：主体名命中 +1（通用性强），关键词命中 +3（区分度高）。
     * 容错原则：得票相等 → 保留在当前方向，宁可漏过不误杀。
     */
    public static boolean containsForeignTopicKeyword(String topic, String text) {
        if (topic == null || topic.isBlank() || text == null || text.isBlank()) return false;
        String lowerText = text.toLowerCase(java.util.Locale.ROOT).trim();

        // === 第一优先级：CROSS_TOPIC_CONCEPTS 精确/子串匹配 ===
        // 该表是硬编码的强信号，命中后直接判定
        String exactMapped = CROSS_TOPIC_CONCEPTS.get(lowerText);
        if (exactMapped != null) {
            return !exactMapped.equals(topic);
        }
        if (lowerText.length() > 4) {
            // 🔴 [Bug修复] 取最长匹配（最具体的概念名），消除HashMap遍历非确定性
            String bestMatch = null;
            String bestMappedTopic = null;
            for (Map.Entry<String, String> entry : CROSS_TOPIC_CONCEPTS.entrySet()) {
                String concept = entry.getKey();
                if (concept.length() >= 2 && lowerText.contains(concept)) {
                    if (bestMatch == null || concept.length() > bestMatch.length()) {
                        bestMatch = concept;
                        bestMappedTopic = entry.getValue();
                    }
                }
            }
            if (bestMappedTopic != null) {
                return !bestMappedTopic.equals(topic);
            }
        }

        // === 第二优先级：票数制 — 统计各方向关键词命中数比较 ===
        // 仅当外来方向得分 > 当前方向得分时才判为跨方向
        // 得票相等 → 返回 false（保留在当前方向，宁可漏过不误杀）
        int currentScore = countDimensionKeywordHits(topic, lowerText);
        int maxForeignScore = 0;
        for (Map.Entry<String, List<String>> entry : DIMENSIONS.entrySet()) {
            if (entry.getKey().equals(topic)) continue;
            int score = countDimensionKeywordHits(entry.getKey(), lowerText);
            if (score > maxForeignScore) maxForeignScore = score;
        }

        // 仅当外来信号强于当前方向信号时才判为跨方向
        // 🔴 [阈值优化] 改为严格大于：只要外来得分更高就判跨方向（原 +1 阈值过于保守，
        // 配合权重反转后区分度已足够，无需额外缓冲）
        return maxForeignScore > currentScore;
    }

    /**
     * 统计文本命中指定方向的维度关键词数量。
     * 🔴 [权重优化] 主体名命中得1分（通用性太强），关键词命中得3分（区分度高）。
     * 供 containsForeignTopicKeyword 票数制使用。
     */
    private static int countDimensionKeywordHits(String topic, String lowerText) {
        List<String> dims = getDimensions(topic);
        if (dims == null || dims.isEmpty()) return 0;
        int score = 0;
        for (String dim : dims) {
            String subject = dimensionSubject(dim);
            if (!subject.isEmpty()
                    && lowerText.contains(subject.toLowerCase(java.util.Locale.ROOT))) {
                score += 1; // 主体名命中权重低：通用性太强
            }
            for (String kw : getSubDimensionKeywords(dim)) {
                if (kw.length() >= 2
                        && lowerText.contains(kw.toLowerCase(java.util.Locale.ROOT))) {
                    score += 3; // 关键词命中权重高：区分度强
                }
            }
        }
        return score;
    }
}
