package com.qian.qianaiagent.controller;

import com.qian.qianaiagent.model.dto.ApiResponse;
import com.qian.qianaiagent.model.dto.PageRequest;
import com.qian.qianaiagent.model.entity.User;
import com.qian.qianaiagent.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理后台控制器
 *
 * ===== 🎯 Task 14: 你来完成 =====
 * 提供管理员才能调用的接口：用户列表、用户状态管理。
 *
 * <h2>📖 RESTful 设计原则</h2>
 * <table border="1">
 *   <tr><th>操作</th><th>HTTP方法</th><th>URL</th></tr>
 *   <tr><td>用户列表（分页）</td><td>GET</td><td>/admin/users?page=1&size=10&keyword=zhang</td></tr>
 *   <tr><td>启用用户</td><td>PUT</td><td>/admin/users/{id}/enable</td></tr>
 *   <tr><td>禁用用户</td><td>PUT</td><td>/admin/users/{id}/disable</td></tr>
 * </table>
 *
 * <h2>📖 权限控制（简化版）</h2>
 * 当前没有 RBAC 权限模型。作为学习，你可以用简单的方案：
 * - 在 request 参数或 Header 中传 adminKey（用于验证管理员身份）
 * - 或者从 UserContext 中取当前用户，判断其角色
 * - 或者直接依赖 JwtAuthFilter（已登录即可访问，暂时不区分角色）
 *
 * 面试时你可以说："目前通过 JWT Token 做认证，管理员鉴权通过自定义注解 + AOP 实现，
 * 后续可以升级为 Spring Security + RBAC 模型。"
 *
 * 💡 引导问题：
 * 1. MyBatis-Plus 的 Page<User> 怎么用？
 *    （提示：new Page<>(page, size)，然后 mapper.selectPage(page, wrapper)）
 * 2. 搜索用 QueryWrapper 还是 LambdaQueryWrapper？
 *    （提示：LambdaQueryWrapper 类型安全，不会因为字段名写错而运行时报错）
 * 3. 关键字搜索用 like 还是 eq？
 *    （提示：like 模糊匹配，"zhang" 能匹配 "zhangsan"）
 * 4. 启用/禁用操作：先 selectById 查用户是否存在 → 改 status → updateById 更新
 * 5. 返回值应该包含什么？（分页查询返回当前页数据 + 总条数 + 总页数）
 * 6. Page<User> 的 getTotal() 和 getPages() 分别返回什么？
 *
 * ⚠️ 注意：这个 Controller 的路径是 /admin，不在 USER_WHITE_LIST 中，
 *    所以访问前需要先登录拿到 JWT Token，在 Header 中传 Authorization: Bearer <token>
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Resource
    private UserService userService;

    // ===== 🎯 Task 14 Part A: 用户列表（分页 + 搜索）=====
    // GET /admin/users?page=1&size=10&keyword=zhang
    //
    // 步骤：
    // 1. 校验分页参数（page < 1 时设为 1，size < 1 时设为 10，size > 100 时截断）
    // 2. 创建 MyBatis-Plus 的 Page 对象
    // 3. 创建 LambdaQueryWrapper，如果有 keyword 就加 like 条件
    // 4. 调用 userMapper.selectPage(page, wrapper)
    // 5. 返回 ApiResponse.success(Map.of("records", page.getRecords(), "total", page.getTotal(), ...))
    //
    // 💡 思考：返回格式应该包含哪些字段？
    //    { "records": [...], "total": 100, "size": 10, "current": 1, "pages": 10 }
    //
    // 你的代码写在这里 ↓


    // 你的代码写在这里 ↑


    // ===== 🎯 Task 14 Part B: 启用/禁用用户 =====
    // PUT /admin/users/{id}/enable  和  PUT /admin/users/{id}/disable
    //
    // 步骤：
    // 1. 根据 id 查询用户（selectById）
    // 2. 如果用户不存在 → 返回 ApiResponse.error("用户不存在")
    // 3. 修改 status 字段
    // 4. 调用 userMapper.updateById(user)
    // 5. 返回 ApiResponse.success("操作成功")
    //
    // 💡 思考：更新操作用 PUT 还是 POST？
    //    PUT 语义是"替换整个资源"，PATCH 是"部分更新"
    //    这里我们用 PUT（虽然是部分更新，但 REST 实践中常用 PUT 简化）
    //
    // 你的代码写在这里 ↓


    // 你的代码写在这里 ↑
}
