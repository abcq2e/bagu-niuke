# 登录注册功能 — 完整教程

> 🎯 目标读者：学 Java 不到 4 个月的小白
> ⏱️ 预计时间：7 天（每天 1-2 小时）
> 🧠 核心理念：**先理解原理，自己想办法写，实在卡住再看提示**

---

## 目录

1. [前置知识补充](#1-前置知识补充)
2. [第 1 天：实体类和 DTO](#2-第-1-天实体类和-dto)
3. [第 2 天：UserServiceImpl 业务逻辑](#3-第-2-天userserviceimpl-业务逻辑)
4. [第 3 天：JwtUtil Token 工具类](#4-第-3-天jwtutil-token-工具类)
5. [第 4 天：UserController 接口层](#5-第-4-天usercontroller-接口层)
6. [第 5 天：JwtAuthFilter 过滤器](#6-第-5-天jwtauthfilter-过滤器)
7. [第 6 天：前端登录注册页面](#7-第-6-天前端登录注册页面)
8. [第 7 天：前端拦截器 + 路由守卫](#8-第-7-天前端拦截器--路由守卫)
9. [调试排错指南](#9-调试排错指南)

---

## 1. 前置知识补充

### 1.1 什么是分层架构？

```
 ┌──────────────────────────────────────┐
 │  Controller    ← HTTP 请求/响应处理   │   "接电话的人"
 │  Service       ← 业务逻辑/规则判断    │   "做决定的人"
 │  Mapper        ← 数据库增删改查       │   "翻抽屉的人"
 │  Entity        ← 数据库表的 Java 镜像  │   "抽屉里的文件夹"
 └──────────────────────────────────────┘
```

**为什么分层？** 想象你在餐厅：服务员（Controller）只管接待和传菜，厨师（Service）只管做菜，采购（Mapper）只管从仓库取食材。如果服务员还要自己炒菜，不乱套了？

### 1.2 登录注册的全链路（先看懂这个再写代码！）

```
用户点击"登录"
  → 前端 Login.vue 收集用户名密码
    → Axios POST /api/user/login
      → 后端 JwtAuthFilter 检查：这是登录接口，白名单放行
        → UserController.login() 接收参数
          → UserServiceImpl.login() 查数据库、验密码
            → JwtUtil.generateToken() 生成 JWT
          ← 返回 Token
        ← 返回 {code: 0, data: {token: "eyJ..."}}
      ← HTTP 200
    ← 响应回来了
  → 前端存 Token 到 localStorage，跳转首页
用户看到主页，登录完成！
```

> ✍️ **动手**：把这个流程画在纸上，每一步理解后再开始写代码。不理解的地方直接问我。

### 1.3 MyBatis-Plus 快速入门

MyBatis-Plus 让你操作数据库不用写 SQL。核心就一个接口 `BaseMapper<T>`：

```java
// 概念演示（不是让你抄的代码，理解思路即可）

// 查一条：根据条件
// 你需要一个 QueryWrapper 来构建 WHERE 条件
// 思考：用什么方法设置 username = "zhangsan" 这个条件？
// 提示：.eq("字段名", "值")

// 插一条
// 思考：BaseMapper 提供了什么方法？你 new 一个 User 对象后怎么插入？

// 根据 ID 查一条
// 思考：BaseMapper 提供了什么方法？它需要什么参数？
```

> 🧠 **思考**：`UserMapper` 是一个接口，继承了 `BaseMapper<User>`。它没有实现类，但 Spring 能注入它——这是怎么做到的？提示：MyBatis-Plus 在启动时做了什么？

### 1.4 BCrypt 密码加密原理

```
注册时：
  用户输入密码 "123456"
  → BCrypt 加密 → "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
  → 存到数据库（存的是密文，不是 "123456"）

登录时：
  用户输入密码 "123456"
  → passwordEncoder.matches("123456", "$2a$10$N9q...")
  → BCrypt 内部：把 "123456" 用同样算法加密 → 和数据库里的密文对比
  → 匹配成功！返回 true
```

**关键点：BCrypt 是单向的**——能加密不能解密。`matches` 方法内部再做一次加密和密文比较。

### 1.5 JWT 结构（用图解理解）

```
eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.0LqK1xS0p_K5v8XQQ1yfRqZzuB-JqnYKtPqLtX9fOqg
│                                            │                                         │
└────────── Header ──────────┘  └────── Payload ──────┘  └────────── Signature ──────────┘
   {"alg":"HS256"}               {"userId":1,...}         对(Header+Payload+密钥)做哈希
```

> ⚠️ **重要**：Payload 只是 Base64 编码，不是加密！任何人都能解码看到内容。**绝对不要在这里放敏感信息（密码等）！**

---

## 2. 第 1 天：实体类和 DTO

### 目标
- 理解 Java 类和数据库表的映射关系
- 掌握 Lombok 常用注解
- 完成 4 个简单文件

### 文件清单

| 文件 | 位置 | 难度 |
|------|------|:--:|
| User.java | `model/entity/User.java` | ⭐ |
| UserMapper.java | `mapper/UserMapper.java` | ⭐ |
| LoginRequest.java | `model/dto/LoginRequest.java` | ⭐ |
| RegisterRequest.java | `model/dto/RegisterRequest.java` | ⭐ |

### 2.1 写 User.java

打开 `model/entity/User.java`，找到 `TODO 1~5`。

对照 `init-user-table.sql`：
```
id         BIGINT       → Java 的什么类型？（主键，已有 @TableId）
username   VARCHAR(50)  → Java 的什么类型？
password   VARCHAR(255) → Java 的什么类型？
nickname   VARCHAR(50)  → Java 的什么类型？
created_at DATETIME     → Java 的什么类型？⚠️ 下划线→驼峰
updated_at DATETIME     → Java 的什么类型？⚠️ 下划线→驼峰
```

> 🧠 **思考**：列名 `created_at` 和字段名 `createdAt` 不一致时，需要什么注解？这个注解的作用是什么？

### 2.2 读 UserMapper.java

这个文件已经是完整的，**只需要读懂**。继承 `BaseMapper<User>` 后自动获得增删改查方法。

> 🧠 **思考**：
> - 为什么接口可以继承另一个接口？
> - `@Mapper` 注解是干嘛的？

### 2.3 写 LoginRequest.java 和 RegisterRequest.java

两个文件结构几乎一样。

> 🧠 **思考**：
> - 为什么要把登录和注册分成两个类？用一个类不行吗？
> - Spring 是怎么把前端 JSON 自动转成 Java 对象的？（提示：`@RequestBody` + Jackson）
> - 用 Lombok 的什么注解来自动生成 getter/setter？

### ✅ 第 1 天完成标准
- [ ] 4 个文件编译通过（IDE 没红线）
- [ ] 能用自己的话解释：实体类、Mapper、DTO 分别是什么
- [ ] 能用自己的话解释 Lombok `@Data` 做了什么

---

## 3. 第 2 天：UserServiceImpl 业务逻辑

### 目标
- 掌握 `@Service`、`@Resource` 的使用
- 理解业务逻辑层的位置和职责
- 学会用 `QueryWrapper` 做条件查询

### 文件清单

| 文件 | 位置 | 难度 |
|------|------|:--:|
| UserService.java | `service/UserService.java` | ⭐⭐ |
| UserServiceImpl.java | `service/impl/UserServiceImpl.java` | ⭐⭐⭐ |

### 3.1 先读 UserService.java（接口）

接口 = 契约。三个方法：`register`、`login`、`getById`。

> 🧠 **思考**：
> - 为什么返回 `ApiResponse` 而不是直接返回 `String`？
> - 为什么 login 返回 `ApiResponse<Map<String, Object>>` 而不是 `ApiResponse<String>`？

### 3.2 写 UserServiceImpl.java（核心！）

打开文件，按 `TODO 1 → TODO 2 → TODO 3` 的顺序完成。

#### TODO 1：注入依赖

> 🧠 **引导**：你需要注入三个东西——`UserMapper`、`PasswordEncoder`、`JwtUtil`。Spring 的依赖注入怎么写？用什么注解？
>
> 📚 **基础补充**：`@Resource` 和 `@Autowired` 的区别——
> - `@Resource` 是 Java 标准的（javax 包），默认按名称匹配
> - `@Autowired` 是 Spring 的，默认按类型匹配
> - 日常使用差别不大，但面试常问

#### TODO 2：实现 register()

**用你自己的思路设计流程**，然后和下面的步骤对比：

<details>
<summary>🆘 思路框架（不是代码！）</summary>

```
1. 查用户名是否已存在（用 QueryWrapper）
   → 思考：selectOne 和 selectList 的区别？这里用哪个？
2. 如果已存在 → return ApiResponse.error("用户名已存在")
3. 加密密码 → 思考：passwordEncoder 的什么方法？
4. 构建 User 对象 → 设置字段 → 插入数据库
   → 思考：insert 后 userId 会自动回填吗？
5. return ApiResponse.success(...)
```

</details>

**⚠️ 常见踩坑**：
- `QueryWrapper` 是 `com.baomidou.mybatisplus.core.conditions.query.QueryWrapper`，别导错包！
- 密码必须存**加密后的**，不是明文

#### TODO 3：实现 login()

> 🧠 **引导**：流程和 register 类似，但有两个关键不同：
> 1. 密码不需要加密——你需要用 `PasswordEncoder` 的另一个方法来**验证**密码
> 2. 验证通过后需要生成 JWT Token
>
> 📚 **安全知识**：为什么"用户名不存在"和"密码错误"要返回**相同的错误信息**？
>
> 因为如果分别返回，攻击者就能用穷举试出哪些用户名已注册（他输入一堆用户名，看到"密码错误"就知道这个用户存在）。这叫**用户枚举漏洞**。

### ✅ 第 2 天完成标准
- [ ] 能用笔画出注册和登录的完整流程图
- [ ] 理解为什么返回错误信息要模糊化
- [ ] 理解 QueryWrapper 的作用——不写 SQL 就能查数据库

---

## 4. 第 3 天：JwtUtil Token 工具类

### 目标
- 理解 JWT 的生成和校验流程
- 掌握 jjwt 库的基本 API
- 理解 `@Value` 注解从配置文件注入值

### 文件清单

| 文件 | 位置 | 难度 |
|------|------|:--:|
| JwtUtil.java | `util/JwtUtil.java` | ⭐⭐⭐ |

### 4.1 理解 JWT 密钥

打开 `application.yml`，找到 `jwt.secret` 和 `jwt.expiration`。

这个 secret 就是"签名钥匙"。只要钥匙不泄露，Token 就无法被伪造。

> 🧠 **思考**：你怎么在 JwtUtil 中读取 `application.yml` 中的这两个值？用什么注解？

### 4.2 写 generateToken()

> 🧠 **引导**（按你的思路设计，然后对比）：
>
> 你需要生成一个 JWT Token 字符串，包括以下信息：
> - 用户名（主题 subject）
> - 用户 ID（自定义字段 claim）
> - 签发时间
> - 过期时间（现在 + expiration）
> - 用密钥签名
>
> 📚 **基础补充**：Builder 模式——
> `Jwts.builder()` 返回一个 Builder，每个 `.xxx()` 返回 Builder 自己，可以链式调用。最后的 `.compact()` 生成最终字符串。这就是 Builder 模式。

<details>
<summary>🆘 提示——不是代码，是思考方向</summary>

```
1. 把 secret 字符串转成 SecretKey 对象
   → 找 Keys 类里的静态方法

2. 计算过期时间
   → Date now = ?;  Date expireDate = now + expiration

3. 用 Jwts.builder() 链式构建
   → .subject(?)     — 主题
   → .claim(?, ?)    — 自定义数据（userId）
   → .issuedAt(?)    — 签发时间
   → .expiration(?)  — 过期时间
   → .signWith(?)    — 用密钥签名
   → .compact()      — 生成最终字符串
```

</details>

### 4.3 写 parseToken()

> 🧠 **引导**：
>
> 你需要解析 Token 字符串，拿回里面的数据。
> 1. 先把 secret 转成 SecretKey
> 2. 创建一个 JwtParser（用 `Jwts.parser()` 构建）
> 3. 解析 Token → 拿到 Claims（就像 Map，key-value 存储）
> 4. 从 Claims 里取 `userId` 和 `subject`（用户名）
>
> ⚠️ **异常处理**：如果 Token 过期或签名不对，`parseSignedClaims()` 会抛异常——调用方需要处理。

<details>
<summary>🆘 提示——思考方向</summary>

```
1. SecretKey key = Keys.hmacShaKeyFor(?)

2. JwtParser parser = Jwts.parser()
       .verifyWith(?)    — 设置验证密钥
       .build()

3. parser.parseSignedClaims(token)  — 解析并验证
   → 返回什么类型？

4. 从结果中取 getPayload() → Claims
   → claims.get(?, ?.class)  — 取自定义字段
   → claims.getSubject()     — 取用户名

5. 异常怎么处理？
   → 签名不对 → ?
   → 过期了 → ?
```

</details>

### ✅ 第 3 天完成标准
- [ ] 能用自己的话说出 JWT 三部分分别是什么
- [ ] 知道 Payload 只是编码不是加密（敏感信息不能放）
- [ ] 能解释为什么 JWT 是安全的（篡改会被签名检测）

---

## 5. 第 4 天：UserController 接口层

### 目标
- 掌握 RESTful API 注解
- 理解 `@RequestBody` 的自动转换
- 理解 `UserContext` 的作用

### 文件清单

| 文件 | 位置 | 难度 |
|------|------|:--:|
| UserController.java | `controller/UserController.java` | ⭐⭐ |

### 写注册、登录和获取当前用户接口

> 🧠 **引导**：
>
> Controller 层**不写任何业务逻辑**，纯做"转交"——这就是分层的意义。
>
> 1. **注册接口**：用什么 HTTP 方法？参数怎么接收？调用哪个 Service 方法？
> 2. **登录接口**：同上
> 3. **获取当前用户**：
>    - 从 `UserContext.getCurrentUserId()` 拿到当前用户 ID
>    - 调用 `userService.getById(userId)` 获取用户信息
>    - 为什么这里不需要传用户名密码？因为 Token 已经确认了身份！
>
> 📚 **基础补充**：RESTful 方法——
> - `@PostMapping` — 创建资源（注册、登录都是创建会话）
> - `@GetMapping` — 读取资源（获取用户信息）
> - `@PutMapping` — 修改资源
> - `@DeleteMapping` — 删除资源
>
> 📚 **基础补充**：
> - `@RequestBody` — 把 HTTP 请求体中的 JSON 自动转成 Java 对象
> - `@PathVariable` — 从 URL 路径中取值（如 `/users/{id}`）
> - `@RequestParam` — 从 URL 查询参数中取值（如 `?name=张三`）

### ✅ 第 4 天完成标准
- [ ] 知道 GET/POST/PUT/DELETE 分别用于什么场景
- [ ] 理解 `@RequestBody` 和 `@PathVariable` 的区别
- [ ] 理解 Controller 为什么这么"薄"（不写业务逻辑）

---

## 6. 第 5 天：JwtAuthFilter 过滤器

> ⚠️ 这是最难的一个文件，也是最关键的。花 1-2 小时别急。

### 目标
- 理解 Servlet Filter 机制
- 掌握白名单模式
- 理解 ThreadLocal 和内存泄漏

### 文件清单

| 文件 | 位置 | 难度 |
|------|------|:--:|
| JwtAuthFilter.java | `filter/JwtAuthFilter.java` | ⭐⭐⭐⭐ |

### 6.1 理解 Filter 的执行流程

```
请求 → Filter.doFilter() → 白名单检查 → 取 Token → 解析 → 存 UserContext → chain.doFilter() → Controller
                                                    ↓解析失败
                                                  返回 401
```

每一个请求都要经过这个流程。你需要写 4 个 TODO。

### 6.2 TODO 1：白名单检查

> 🧠 **引导**：
>
> 为什么需要白名单？登录和注册接口不需要 Token（还没登录呢），必须绕过 JWT 检查。
>
> 你需要：
> 1. 遍历白名单路径列表
> 2. 如果当前请求路径包含白名单路径 → `chain.doFilter()` 直接放行，**然后必须 return**
> 3. 不在白名单 → 继续往下执行
>
> ⚠️ **关键细节**：放行后为什么要 `return`？如果不 return，代码会继续执行后续的 Token 校验逻辑！

### 6.3 TODO 2：取 Token

> 🧠 **引导**：
>
> 请求头格式：
> ```
> Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
> ```
>
> 你需要：
> 1. 从 request 中取 `Authorization` 请求头 —— 用什么方法？
> 2. 检查是否以 `"Bearer "` 开头
> 3. 截取 `"Bearer "` 后面的部分（从第 7 个字符开始）—— 用什么方法？
> 4. 如果请求头不存在或格式不对 → 返回 401 错误
>
> 📚 **基础补充**：`String.substring(beginIndex)` — 从 beginIndex 开始截取到末尾。
> `"Bearer xxx".substring(7)` → `"xxx"`

### 6.4 TODO 3：解析 Token

> 🧠 **引导**：
>
> 1. 调用 `jwtUtil.parseToken(token)` 拿到 Claims
> 2. 从 Claims 中取 `userId` —— 注意类型，存的时候是 Long，取的也是 Long
> 3. 把 userId 存入 `UserContext.setCurrentUserId(userId)`
> 4. `chain.doFilter(request, response)` 放行
> 5. **异常处理**：
>    - Token 过期 → `ExpiredJwtException` → 返回 "Token已过期"
>    - Token 无效 → `JwtException` → 返回 "Token无效"
>    - ⚠️ **注意 catch 顺序**：`ExpiredJwtException` 是 `JwtException` 的子类。应该先 catch 哪个？为什么？

### 6.5 TODO 4：finally 清除

> 🧠 **引导**：
>
> 在 finally 块中调用 `UserContext.remove()`。
>
> 📚 **基础补充**：**为什么必须清除 ThreadLocal？**
>
> Tomcat 用线程池。线程会复用！如果不清除，下一次请求复用到同一个线程时，可能读到上一个用户的 ID。这就是**线程安全问题 + 内存泄漏**。
>
> 面试高频问题：**"ThreadLocal 为什么会导致内存泄漏？"** → 因为线程池复用，ThreadLocal 里的值不会被 GC，越积越多。

### ✅ 第 5 天完成标准
- [ ] 能用笔画出 Filter 处理请求的完整流程
- [ ] 理解白名单为什么安全
- [ ] 能解释 ThreadLocal 是什么，为什么要 remove

---

## 7. 第 6 天：前端登录注册页面

### 目标
- 掌握 Vue 3 Composition API 基础
- 学会用 axios 调用后端 API
- 完成表单交互

### 文件清单

| 文件 | 位置 | 难度 |
|------|------|:--:|
| api/index.js | `api/index.js` | ⭐⭐ |
| Login.vue | `views/Login.vue` | ⭐⭐ |
| Register.vue | `views/Register.vue` | ⭐⭐ |

### 7.1 写 api/index.js（添加 login/register 函数）

> 🧠 **引导**：
>
> 你需要添加两个 API 函数：
> 1. `login(username, password)` → POST 到 `/user/login`
> 2. `register(username, password, nickname)` → POST 到 `/user/register`
>
> 参考文件中已有的 API 函数写法，模式是一样的：`request.post(url, body)`
>
> 📚 **基础补充**：`request.post(url, body)` — 第一个参数是 URL，第二个参数是请求体对象（axios 会自动转成 JSON）。

### 7.2 写 Login.vue

按 TODO 1→2→3→4 顺序完成：

> 🧠 **TODO 1 — 响应式变量**：
> Vue 3 用什么函数创建响应式变量？声明 `username` 和 `password` 两个变量。

> 🧠 **TODO 2 — 登录函数**：
> 1. 校验：用户名和密码不能为空
> 2. 调用 `loginApi(username.value, password.value)`
> 3. 成功（`res.data.code === 0`）→ 存 Token + 跳转首页
> 4. 失败 → 显示错误信息
> 5. 异常（网络错误等）→ try/catch
>
> 📚 **基础补充**：
> - `async/await`：让异步代码读起来像同步代码。`await` 会等待 Promise 完成。
> - `.value`：Vue 3 的 `ref()` 在 `<script>` 中需要用 `.value` 取值，在 `<template>` 中自动解包。
> - `router.push('/')`：Vue Router 的页面跳转。

> 🧠 **TODO 3 — 模板**：用 `v-model` 双向绑定 + `@click` 事件处理。

> 🧠 **TODO 4 — 样式**：用 Flexbox 让卡片居中。

### 7.3 写 Register.vue

参照 Login.vue，多一个 `nickname` 字段。

> ✍️ **加分挑战**：加一个"确认密码"输入框，校验两次输入是否一致。如果两次输入不同，怎么阻止提交？

### ✅ 第 6 天完成标准
- [ ] 登录页能发送请求到后端
- [ ] 登录成功能跳转首页
- [ ] 注册成功能跳转登录页

---

## 8. 第 7 天：前端拦截器 + 路由守卫

### 目标
- 理解 Axios 拦截器（请求/响应）
- 理解 Vue Router 导航守卫
- 形成完整的前后端认证闭环

### 文件清单

| 文件 | 位置 | 难度 |
|------|------|:--:|
| api/index.js（拦截器部分）| `api/index.js` | ⭐⭐⭐ |
| router/index.js（路由守卫）| `router/index.js` | ⭐⭐⭐ |

### 8.1 Axios 请求拦截器

> 🧠 **引导**：
>
> 作用：**每次请求自动带 Token**。前端调 API 时不用手动传 Token。
>
> 你需要：
> 1. 用 `request.interceptors.request.use()` 注册拦截器
> 2. 从 localStorage 取 Token（项目已有 `getToken()` 工具函数）
> 3. 如果有 Token，加到请求头 `config.headers.Authorization`
> 4. 格式：`` `Bearer ${token}` ``
>
> 📚 **基础补充**：拦截器接收一个回调函数 `(config) => { ...; return config; }`。`config` 是请求配置对象，`config.headers` 是请求头。

### 8.2 Axios 响应拦截器

> 🧠 **引导**：
>
> 作用：**收到 401 自动踢回登录页**。
>
> 你需要：
> 1. 用 `request.interceptors.response.use()` 注册拦截器
> 2. 响应拦截器有两个回调：成功回调（直接返回 response）和错误回调（处理 401）
> 3. 在错误回调中判断 `error.response?.status === 401`
> 4. 如果是 401 → 清除 Token（项目已有 `clearAuth()`）→ 跳转到 `/login`

### 8.3 Vue Router 路由守卫

> 🧠 **引导**：
>
> 用 `router.beforeEach((to, from, next) => {...})` 实现。
>
> 三种场景：
> 1. **没登录想访问需要登录的页面** → `next('/login')`
>    - 怎么判断"需要登录"？看 `to.meta.requiresAuth`
>    - 怎么判断"已登录"？项目已有 `isLoggedIn()` 工具函数
> 2. **已登录还想访问登录/注册页** → `next('/')`
> 3. **其他正常情况** → `next()` 放行

### ✅ 第 7 天完成标准
- [ ] 未登录时访问首页 → 自动跳转到 `/login`
- [ ] 登录后访问 `/login` → 自动跳转到 `/`
- [ ] 不带 Token 请求需要认证的接口 → 后端返回 401 → 前端自动跳登录
- [ ] 清除 localStorage 中的 Token → 刷新页面 → 自动跳登录

---

## 9. 调试排错指南

### 后端常见问题

| 症状 | 可能原因 | 排查方法 |
|------|---------|---------|
| MyBatis 扫描不到 Mapper | `@MapperScan` 路径不对 | 检查 `MyBatisPlusConfig` 中的 `basePackages` |
| `@Resource` 注入为 null | 类没加 @Service/@Component | 检查有没有漏注解 |
| QueryWrapper 导错包 | 导成了 JPA 的 | 确认包是 `com.baomidou.mybatisplus.core.conditions.query` |
| BCrypt matches 一直 false | 存的时候没加密 | 检查 register 里是否调用了 encode |
| Token 解析报错 | secret 配置不匹配 | 检查 application.yml 的 jwt.secret |

### 前端常见问题

| 症状 | 可能原因 | 排查方法 |
|------|---------|---------|
| 请求 404 | URL 路径不对 | F12 → Network，看实际请求 URL |
| CORS 跨域报错 | 跨域配置问题 | 已有 CorsConfig，检查端口是否写错 |
| Token 没带过去 | 请求拦截器没写 | F12 → Network → 看请求头有没有 Authorization |
| ref 变量在模板中不更新 | 忘了 .value 或忘了 ref | 检查变量是否用 ref() 包裹 |

### 通用调试技巧

1. **看日志**：后端 `logging.level.com.qian.qianaiagent.mapper: DEBUG` 会打印 SQL
2. **用 Postman/Apifox** 先调通后端接口，再写前端
3. **F12 开发者工具**：Network 看请求，Application 看 localStorage
4. **console.log**：前端在关键位置打日志
5. **断点调试**：IDE 里打断点，一行一行走

---

## 附录：完整文件清单

### 后端（12 个文件）

```
src/main/java/com/yupi/yuaiagent/
├── config/
│   ├── MyBatisPlusConfig.java      ✅ 已生成（配置，不用改）
│   └── PasswordEncoderConfig.java  ✅ 已生成（配置，不用改）
├── context/
│   └── UserContext.java            ✅ 已生成（ThreadLocal 工具）
├── controller/
│   └── UserController.java         ⚠️ TODO × 3
├── filter/
│   └── JwtAuthFilter.java           ⚠️ TODO × 4
├── mapper/
│   └── UserMapper.java             ✅ 已生成（纯阅读）
├── model/
│   ├── entity/
│   │   └── User.java               ⚠️ TODO × 5
│   └── dto/
│       ├── ApiResponse.java        ✅ 已生成
│       ├── LoginRequest.java       ⚠️ TODO × 2
│       └── RegisterRequest.java    ⚠️ TODO × 3
├── service/
│   ├── UserService.java            ✅ 已生成（接口，纯阅读）
│   └── impl/
│       └── UserServiceImpl.java     ⚠️ TODO × 3
└── util/
    └── JwtUtil.java                ⚠️ TODO × 2
```

### 前端（4 个文件）

```
qian-ai-agent-frontend/src/
├── api/
│   └── index.js                    ⚠️ TODO × 4（拦截器 × 2 + API × 2）
├── router/
│   └── index.js                    ⚠️ TODO × 1（路由守卫）
├── utils/
│   └── auth.js                     ✅ 已生成（Token 存取工具）
└── views/
    ├── Login.vue                   ⚠️ TODO × 4
    └── Register.vue                ⚠️ TODO × 4
```

---

> 💬 **遇到问题？** 先自己思考 10-15 分钟，实在卡住了再问我。问的时候告诉我你卡在哪一步、你尝试了什么方案——这样我给你的引导才最有价值。
