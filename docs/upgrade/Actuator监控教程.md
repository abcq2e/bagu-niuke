# 🟢 Spring Actuator 监控教程

> Task 10 | 难度 ⭐ | 涉及文件: `application.yml`、新建 `health/` 和 `info/` 配置类

---

## 一、为什么要学 Actuator？

### 面试场景

面试官："你们项目上线后怎么监控？怎么知道服务还活着？"
你："用了 Spring Actuator，有健康检查端点，还自定义了 AI 服务的健康指标。"
面试官："如果健康检查失败怎么办？"
你："Kubernetes 会根据健康检查自动重启不健康的 Pod，或者触发告警。"

**这叫生产环境意识——面试中最大的加分项之一。**

### 你到底要做什么

Spring Actuator 已经加好依赖和配置了。你需要：
1. 自定义一个 HealthIndicator（检查 DeepSeek API 能不能连通）
2. 自定义一个 InfoContributor（在 /actuator/info 里显示项目信息）

---

## 二、Actuator 基础概念

### Actuator 是什么？

Spring Boot 内置的监控工具，自动暴露 HTTP 端点：

| 端点 | 作用 |
|------|------|
| `/actuator/health` | 健康检查（服务是否正常） |
| `/actuator/info` | 应用信息（版本、描述） |
| `/actuator/metrics` | 指标（内存、CPU、请求数） |
| `/actuator/env` | 环境变量（密码会脱敏） |
| `/actuator/loggers` | 动态改日志级别 |

### 健康检查原理

Actuator 自动收集所有 `HealthIndicator` Bean 的健康状态，汇总成整体状态：

```
/actuator/health
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},          ← 数据库连接（自带）
    "redis": {"status": "UP"},       ← Redis 连接（自带）
    "deepseek": {"status": "UP"}     ← 你需要自定义的！
  }
}
```

**Kubernetes 就是靠这个端点判断 Pod 是否健康，不健康就自动重启。**

---

## 三、你要做的两个自定义组件

### 子任务 1: 自定义 HealthIndicator

**新建文件**: 在 `com.qian.qianaiagent.health` 包下创建

**目标**: 检查 DeepSeek API 是否能连通

> 🧠 **引导**：
> 1. `HealthIndicator` 接口只有一个方法 `health()`，返回 `Health` 对象
> 2. 怎么判断 DeepSeek 是否连通？
>    - 提示：用 `RestTemplate` 发 GET 请求到 `https://api.deepseek.com`
>    - 你需要 new 一个 `RestTemplate`，调用它的什么方法？
> 3. 如果连通 → `Health.up().withDetail("message", "...").build()`
> 4. 如果断开 → `Health.down().withDetail("error", e.getMessage()).build()`
> 5. 健康检查应该很快！因为 K8s 每次请求都执行，慢了会超时
>
> 📚 **基础补充 — RestTemplate**：Spring 提供的 HTTP 客户端，核心方法是 `getForObject(url, 返回类型.class)`。如果请求失败会抛异常（比如 `ResourceAccessException`）。

<details>
<summary>🆘 提示——思考方向</summary>

```
1. 新建包 com.qian.qianaiagent.health
2. 创建类，实现 HealthIndicator 接口
3. 加上 @Component，让 Spring 管理它
4. 实现 health() 方法：
   - new RestTemplate()
   - try { restTemplate.getForObject("https://api.deepseek.com/v1/models", String.class) }
   - 没抛异常 → return Health.up()...
   - 抛异常 → return Health.down()...
```

</details>

---

### 子任务 2: 自定义 InfoContributor

**新建文件**: 和上面同包，或放在 `com.qian.qianaiagent.info` 包下

**目标**: 在 `/actuator/info` 里显示项目名称、版本号、启动时间

> 🧠 **引导**：
> 1. `InfoContributor` 接口只有一个方法 `contribute(Info.Builder builder)`
> 2. 版本号从哪来？（提示：可以在构造函数里读取 `application.yml` 中的 `info.app.version`）
> 3. 启动时间怎么获取？（构造函数里 `this.startTime = LocalDateTime.now()`）
> 4. 你还能加什么信息？（Java 版本、操作系统、激活的 Profile）
>
> 📚 **基础补充**：`Info.Builder` 用 `.withDetail("key", value)` 添加信息。

---

## 四、验证方法

1. 启动项目
2. 访问 `http://localhost:8123/api/actuator/health` → 你的 DeepSeek 检查应出现在 components 里
3. 访问 `http://localhost:8123/api/actuator/info` → 看到自定义信息
4. 访问 `http://localhost:8123/api/actuator/metrics` → 看到 JVM 内存、CPU 等指标

---

## 五、面试追问

- 如果健康检查太慢会怎样？（K8s 超时会把 Pod 杀掉）
- 健康检查（Liveness）和就绪检查（Readiness）有什么区别？
- 怎么把 Actuator 的 metrics 对接 Prometheus + Grafana？

---

## 🧠 自检清单

- [ ] 知道 Actuator 5 个常用端点分别做什么
- [ ] 能实现自定义 HealthIndicator
- [ ] 能实现自定义 InfoContributor
- [ ] 理解健康检查对 Kubernetes 的意义
- [ ] 知道 Liveness 和 Readiness 的区别
