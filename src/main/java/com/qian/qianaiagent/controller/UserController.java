package com.qian.qianaiagent.controller;

import com.qian.qianaiagent.annotation.OperationLog;
import com.qian.qianaiagent.annotation.RateLimit;
import com.qian.qianaiagent.context.UserContext;
import jakarta.validation.Valid;
import com.qian.qianaiagent.model.dto.ApiResponse;
import com.qian.qianaiagent.model.dto.LoginRequest;
import com.qian.qianaiagent.model.dto.RegisterRequest;
import com.qian.qianaiagent.model.entity.User;
import com.qian.qianaiagent.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户控制器 —— 处理登录注册相关的 HTTP 请求
 *
 * <h2>📖 RESTful API 注解速查表</h2>
 * <table border="1">
 *   <tr><th>注解</th><th>作用</th><th>例子</th></tr>
 *   <tr><td>@RestController</td><td>标记这是 REST 控制器（返回 JSON，不走页面渲染）</td><td>加在类上</td></tr>
 *   <tr><td>@RequestMapping("/user")</td><td>给所有接口统一加前缀</td><td>类上加，所有方法路径自动加 /user</td></tr>
 *   <tr><td>@PostMapping("/login")</td><td>处理 POST 请求到 /user/login</td><td>加在方法上</td></tr>
 *   <tr><td>@GetMapping("/me")</td><td>处理 GET 请求到 /user/me</td><td>加在方法上</td></tr>
 *   <tr><td>@RequestBody</td><td>把请求体中的 JSON 自动转成 Java 对象</td><td>加在方法参数前</td></tr>
 * </table>
 *
 * <h2>📖 HTTP 方法选择原则</h2>
 * <ul>
 *   <li>GET — 获取数据（查询）</li>
 *   <li>POST — 创建数据（注册、登录都属于"创建会话/用户"）</li>
 *   <li>PUT — 完整更新数据</li>
 *   <li>DELETE — 删除数据</li>
 * </ul>
 * <p>
 * 为什么登录注册用 POST 而不是 GET？
 * GET 把参数拼在 URL 后面，密码就暴露在 URL 里了（不安全）。
 * POST 把参数放在请求体里，不会暴露在 URL 中。
 */

@RestController
@RequestMapping("/user")
public class UserController {

    // ============================================================
    // TODO: 注入 UserService
    //
    // 把下面这行注释的代码取消注释即可：
    //   @Resource
    //   private UserService userService;
    //
    // 📖 这里声明的是接口类型 UserService，不是实现类 UserServiceImpl。
    // Spring 会自动找到唯一实现了 UserService 接口的 Bean（UserServiceImpl）注入。
    // 这就是"依赖倒置原则"：高层模块（Controller）不依赖低层模块（ServiceImpl），
    // 两者都依赖抽象（Service 接口）。
    // ============================================================
    @Resource
    private UserService userService;


    /**
     * 用户注册
     *
     * <h2>📖 POST /api/user/register</h2>
     * <p>
     * 请求体示例：
     * <pre>{"username": "zhangsan", "password": "123456", "nickname": "张三"}</pre>
     * <p>
     * 响应示例：
     * <pre>{"code": 0, "message": "注册成功", "data": null}</pre>
     */
    @OperationLog(value = "注册", type = "注册")
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    /**
     * 用户登录
     *
     * <h2>📖 POST /api/user/login</h2>
     * <p>
     * 请求体示例：
     * <pre>{"username": "zhangsan", "password": "123456"}</pre>
     * <p>
     * 响应示例：
     * <pre>{"code": 0, "message": "登录成功", "data": {"token": "eyJ...", "nickname": "张三"}}</pre>
     */
  //  @OperationLog(value = "登录", type = "登录")
    // ===== 🎯 Task 13: 加 @RateLimit 防暴力破解！=====
    // 60 秒内最多登录 5 次
    // 💡 想想 maxRequests 设多少合适？为什么不是 3 也不是 10？
    @OperationLog(value = "登录", type = "登录")
    @RateLimit(maxRequests = 5, timeWindow = 60, message = "登录过于频繁，请 60 秒后再试")
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    /**
     * 获取当前登录用户的信息
     *
     * <h2>📖 GET /api/user/me</h2>
     * <p>
     * 这个接口需要登录才能访问（JwtAuthFilter 已经做了拦截）。
     * 当前用户 ID 可以从 UserContext.getCurrentUserId() 获取。
     * <p>
     * 响应示例：
     * <pre>{"code": 0, "data": {"id": 1, "username": "zhangsan", "nickname": "张三"}}</pre>
     *
     * <h2>📖 UserContext 是什么？</h2>
     * <p>
     * UserContext 用 ThreadLocal 存储当前请求的用户 ID。
     * JwtAuthFilter 验证 Token 通过后，把 userId 放入 UserContext，
     * Controller/Service 就能随时获取当前用户是谁。
     * 这是一种"请求级全局变量"，只在当前请求的生命周期内有效。
     */
    // ===== 🎯 Task 5: 安全问题！ =====
    // 当前返回的是 User 实体（包含 password 字段），BCrypt 哈希会泄露给前端。
    // 解决思路：
    // 💡 引导问题:
    //   1. 创建一个只包含安全字段的新 DTO 类（id, username, nickname, createdAt）
    //   2. 在返回前把 User → 新 DTO 转换
    //   3. 用 BeanUtils.copyProperties() 还是手动 setter？
    //      （思考：如果两个类都有 password 字段，copyProperties 会怎样？）
    //   4. 新 DTO 用 class 还是 record？
    //
    // ⚠️ 永远不要让 Entity 直接出现在 API 响应中！
    // 📖 这叫做 DTO Pattern（数据传输对象模式）
    @GetMapping("/me")
    public ApiResponse<User> getCurrentUser() {
        Long userId = UserContext.getCurrentUserId();
        User user = userService.getById(userId);
        if (user == null) {
            return ApiResponse.error("用户不存在");
        }
        // 🔴 安全：隐藏密码哈希，不泄露到前端
        user.setPassword(null);
        return ApiResponse.success(user);
    }
}
