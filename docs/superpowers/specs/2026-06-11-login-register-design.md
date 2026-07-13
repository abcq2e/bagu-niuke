# 登录注册功能 — 设计文档

> 创建日期：2026-06-11 | 用户：小白（Java 学习 ~4 个月）

## 1. 目标

为 qian-ai-agent 项目增加用户名+密码的登录注册功能。未登录用户不能访问任何功能页面，必须跳转到登录页。

## 2. 技术选型

| 层 | 技术 | 选型理由 |
|---|------|---------|
| 认证方式 | JWT（手写 Filter） | 不用 Spring Security，每一行核心代码用户自己写，彻底理解认证流程 |
| 数据库 | MySQL 8.0 | 用户 Docker 已有，宿主机 `localhost:13306` |
| ORM | MyBatis-Plus 3.5+ | 国内最主流，比 JPA 更直观，适合新手 |
| 密码加密 | BCrypt | 单向哈希，工业标准 |
| 前端 | Vue 3 + Vue Router + Axios | 项目已有，无需新增依赖 |

## 3. 核心学习目标（用户手写代码）

按学习价值从高到低排列：

### 3.1 后端部分

| 文件 | 学习内容 | 难度 |
|------|---------|:--:|
| `JwtUtil.java` | 加密签名原理、HMAC-SHA256、Token 签发与校验 | ⭐⭐⭐ |
| `JwtAuthFilter.java` | Servlet Filter 机制、请求拦截、ThreadLocal 传递用户信息 | ⭐⭐⭐⭐ |
| `UserService.java`（实现类） | 业务逻辑层设计、密码比对、异常处理、事务 | ⭐⭐⭐ |
| `UserController.java` | RESTful API 设计、请求/响应 DTO、参数校验 | ⭐⭐ |
| `User.java`（实体类） | MyBatis-Plus 注解、数据库字段映射 | ⭐ |
| `UserMapper.java` | MyBatis-Plus BaseMapper 接口 | ⭐ |

### 3.2 前端部分

| 文件 | 学习内容 | 难度 |
|------|---------|:--:|
| `router/index.js`（路由守卫） | Vue Router 导航守卫、`beforeEach` 钩子 | ⭐⭐⭐ |
| `api/index.js`（请求拦截） | Axios 拦截器、自动带 Token、401 处理 | ⭐⭐⭐ |
| `Login.vue` | 表单绑定、axios 调用、localStorage | ⭐⭐ |
| `Register.vue` | 同上，加表单校验 | ⭐⭐ |

### 3.3 由我直接生成的部分（无学习价值的配置/模板代码）

| 文件 | 原因 |
|------|------|
| `pom.xml` 新增依赖 | 纯坐标复制，不用动脑 |
| `application.yml` 新增数据库连接 | 纯配置 |
| MyBatis-Plus 配置类 | Spring Boot 标准套路 |
| BCrypt Bean 配置 | 一行代码 |
| 数据库建表 SQL 脚本 | DDL 模板 |
| 前端路由结构新增 | 加几行配置 |

## 4. 数据库

```sql
CREATE TABLE `user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`   VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`   VARCHAR(255) NOT NULL COMMENT 'BCrypt加密后的密码',
    `nickname`   VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

## 5. API 设计

### POST `/api/user/register`

```
请求体: { "username": "zhangsan", "password": "123456", "nickname": "张三" }
成功响应 (200): { "code": 0, "message": "注册成功", "data": null }
失败响应 (200): { "code": 1, "message": "用户名已存在", "data": null }
```

### POST `/api/user/login`

```
请求体: { "username": "zhangsan", "password": "123456" }
成功响应 (200): { "code": 0, "message": "登录成功", "data": { "token": "eyJhbG...", "nickname": "张三" } }
失败响应 (200): { "code": 1, "message": "用户名或密码错误", "data": null }
```

### GET `/api/user/me`

```
请求头: Authorization: Bearer eyJhbG...
成功响应 (200): { "code": 0, "data": { "id": 1, "username": "zhangsan", "nickname": "张三" } }
失败响应 (200): { "code": 1, "message": "未登录", "data": null }
```

## 6. 认证流程

```
1. 用户登录 → 后端校验用户名密码 → 生成 JWT → 返回给前端
2. 前端存 JWT 到 localStorage → Axios 拦截器每次请求自动带 Authorization 头
3. 后端 JwtAuthFilter 拦截请求 → 解析 Token → 校验是否有效 → 放行/拒绝
4. 前端路由守卫 → 检查 localStorage 是否有 Token → 没 Token 就跳登录页
```

## 7. JWT 设计

- **算法**：HMAC-SHA256（对称加密）
- **有效载荷**（Payload）：`{ "userId": 1, "username": "zhangsan" }`
- **过期时间**：7 天
- **密钥**：存放在 `application.yml` 中

## 8. 目录结构

```
后端新增文件：
src/main/java/com/yupi/yuaiagent/
├── config/
│   ├── MyBatisPlusConfig.java       [生成] MyBatis-Plus 分页插件配置
│   └── PasswordEncoderConfig.java   [生成] BCrypt Bean 注册
├── controller/
│   └── UserController.java          [用户写] 注册/登录/查自己的接口
├── model/
│   ├── entity/
│   │   └── User.java                [用户写] 用户实体类
│   └── dto/
│       ├── LoginRequest.java        [用户写] 登录请求体
│       ├── RegisterRequest.java     [用户写] 注册请求体
│       └── ApiResponse.java         [生成] 统一响应格式
├── mapper/
│   └── UserMapper.java              [用户写] MyBatis-Plus Mapper
├── service/
│   ├── UserService.java             [用户写] 接口
│   └── impl/
│       └── UserServiceImpl.java     [用户写] 实现类
├── util/
│   └── JwtUtil.java                 [用户写] JWT 工具类
├── filter/
│   └── JwtAuthFilter.java           [用户写] JWT 认证过滤器
└── context/
    └── UserContext.java             [生成] ThreadLocal 保存当前用户

前端新增/修改文件：
qian-ai-agent-frontend/src/
├── router/index.js                  [生成结构，用户补守卫]
├── api/index.js                     [生成结构，用户补拦截器]
├── views/
│   ├── Login.vue                    [用户写] 登录页
│   └── Register.vue                 [用户写] 注册页
└── utils/
    └── auth.js                      [生成] Token 存取工具函数
```

## 9. 前后"你需要写"和"我直接生成"的边界

这个设计文档确认后，我会按以下方式执行：

1. **先发教程**：按模块顺序发详细教程，引导你写核心代码
2. **在需要写代码的地方加注释**：先加好 `// TODO: 你需要在这里完成...` 注释
3. **生成纯配置/模板代码**：我会把真正没有学习价值的文件直接写好
4. **每个模块你自己动手**：看完教程 → 自己写 → 遇到问题问我
