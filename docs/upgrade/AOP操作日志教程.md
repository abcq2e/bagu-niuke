# 🔵 AOP 全局操作日志教程

> Task 11 | 难度 ⭐⭐⭐ | 涉及文件: `annotation/OperationLog.java`、`aspect/OperationLogAspect.java`、`UserController.java`

---

## 一、为什么要学 AOP？

### 面试场景

面试官："AOP 是什么？你在项目里用过吗？"
你："用过。用自定义注解 + @Around 切面实现了全局操作日志，自动记录每个 API 的调用参数、返回值和耗时。"

**AOP 是 Spring 两大核心之一（另一个是 IoC），面试必问。**

### 你到底要做什么

当前项目没有任何操作日志。你需要：
1. 完成 `OperationLogAspect` 的 `@Around` 方法体（已生成骨架）
2. 在 `UserController` 的方法上加 `@OperationLog` 注解测试效果

---

## 二、AOP 核心概念（用大白话理解）

### 什么场景需要 AOP？

假设项目有 20 个 Controller 方法，需求：记录每个方法的调用日志。

- ❌ **不用 AOP**: 在 20 个方法里手动加 `log.info(...)` → 代码重复、容易漏、难维护
- ✅ **用 AOP**: 写一个切面类 → 20 个方法自动被拦截 → 日志自动记录

### 核心术语

```
调用 controller.register()
      ↓
  ┌─── @Before ───┐     ← 方法执行前
  │               │
  │  proceed() →  │ 真正执行 register() 方法
  │               │
  └─── @After ────┘     ← 方法执行后
      ↓
  返回结果给调用方
```

| 术语 | 通俗理解 | 代码体现 |
|------|----------|----------|
| 切面(Aspect) | 横切逻辑的"类" | `@Aspect` 注解的类 |
| 切点(Pointcut) | "哪些方法要被拦截？" | `@Around("@annotation(...)")` |
| 通知(Advice) | "拦截后做什么？" | 切面类里的方法体 |
| 连接点(JoinPoint) | 被拦截的方法的信息 | `ProceedingJoinPoint` 参数 |

### 5 种通知类型

| 通知 | 执行时机 | 能拿返回值吗 | 能阻止执行吗 |
|------|----------|-------------|-------------|
| `@Before` | 方法执行前 | ❌ | ❌ |
| `@After` | 方法执行后（异常也执行） | ❌ | ❌ |
| `@AfterReturning` | 方法正常返回后 | ✅ | ❌ |
| `@AfterThrowing` | 方法抛异常后 | ❌（只能拿异常） | ❌ |
| `@Around` | 围绕方法（全能） | ✅ | ✅ |

**你用 @Around，因为它最灵活。**

---

## 三、你的任务

### 子任务 1: 完成 OperationLogAspect 的方法体

**文件**: `aspect/OperationLogAspect.java`（骨架已生成）

**目标**: 当有 `@OperationLog` 注解的方法被调用时，自动记录：类名+方法名、操作描述和类型、参数、耗时、返回值（可截断），异常时记录异常信息。

> 🧠 **引导**（按顺序思考，而不是一次性全想完）：

**第 1 步：获取方法签名**
- `joinPoint.getSignature()` 返回什么类型？怎么安全地转成 `MethodSignature`？
- 从 `MethodSignature` 怎么拿到方法所在的类名和方法名？

**第 2 步：获取 @OperationLog 注解**
- `method.getAnnotation(OperationLog.class)` 返回什么？如果是 null 说明什么？
- 从注解对象怎么取 `value()` 和 `type()`？

**第 3 步：计时**
- 用什么 API 获取当前时间？`System.currentTimeMillis()` 返回什么类型？
- 在 `proceed()` 前后各取一次，差值就是耗时

**第 4 步：调用目标方法**
- `joinPoint.proceed()` —— 这是最重要的调用，真正执行目标方法
- 返回值类型是什么？为什么是 `Object`？
- 如果 proceed() 抛异常怎么办？try-catch 里做什么？

**第 5 步：记录日志**
- 正常执行用什么日志级别？异常用什么？
- 参数里可能有密码，要不要脱敏？（学习阶段先不脱敏，知道这是面试加分点）

> 📚 **敏感信息脱敏（面试加分）**：你可以在切面中检查参数名是否包含 "password"，如果是替换为 `"******"`。

---

### 子任务 2: 在 Controller 上加注解测试

**文件**: `controller/UserController.java`（已有引导注释）

> ✍️ **动手**: 在 `register()` 方法上加 `@OperationLog(value = "用户注册", type = "注册")`

> 🧠 **思考**：
> 1. 加了注解后，启动项目，调用注册接口，日志会多一行什么？
> 2. 同一个 `@OperationLog` 可以加在多个方法上吗？
> 3. 如果加在 Service 层的方法上，AOP 还会拦截吗？
>    （提示：看切点表达式 `execution(* ...controller..*.*(..))` —— 只拦截 controller 包下的）

---

## 四、AOP 底层原理（面试重点）

### 动态代理

Spring AOP 的底层是**动态代理**。当你注入一个 Bean 时，Spring 给你的不是原对象，而是**代理对象**：

```
你调用: controller.register()
    ↓
代理对象 intercept:
    1. 执行 @Before 通知
    2. 调用 target.register()  ← 真正的 register
    3. 执行 @After 通知
    ↓
返回结果
```

### 两种代理方式

| 方式 | 条件 | 原理 |
|------|------|------|
| JDK 动态代理 | 目标类实现了接口 | 生成接口的实现类作为代理 |
| CGLIB 代理 | 目标类没有接口 | 生成目标类的子类作为代理 |

Spring Boot 默认用 CGLIB。

> 🧠 **面试经典问题**："为什么同类方法调用 `@Async` 或 `@Transactional` 不生效？"
>
> 答案：因为调用绕过了代理！`this.methodB()` 直接调的是原始对象，不是代理对象。

---

## 五、验证方法

1. 启动项目
2. 调用注册接口（POST `/api/user/register`）
3. 看控制台日志，应该出现操作日志信息

---

## 六、面试追问

- AOP 的应用场景有哪些？（日志、事务、权限、限流、缓存）
- 动态代理和静态代理的区别？
- Spring AOP 和 AspectJ 的区别？

---

## 🧠 自检清单

- [ ] 能用大白话解释 AOP 解决什么问题
- [ ] 知道 5 种通知类型的区别和使用场景
- [ ] 能写出一个 @Around 切面方法
- [ ] 理解 @Around 中 proceed() 的作用
- [ ] 能解释 JDK 动态代理 vs CGLIB 的区别
- [ ] 知道为什么同类方法调用 @Transactional 不生效
