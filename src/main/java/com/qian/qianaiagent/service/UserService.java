package com.qian.qianaiagent.service;

import com.qian.qianaiagent.model.dto.ApiResponse;
import com.qian.qianaiagent.model.dto.LoginRequest;
import com.qian.qianaiagent.model.dto.RegisterRequest;
import com.qian.qianaiagent.model.entity.User;

import java.util.Map;

/**
 * 用户业务逻辑接口
 *
 * <h2>📖 为什么要先定义接口再写实现类？</h2>
 * <p>
 * 这是 Java 最重要的设计原则之一："面向接口编程"。
 * <ul>
 *   <li>接口 = 契约（我承诺能做这些事）</li>
 *   <li>实现类 = 契约的具体履行方式</li>
 * </ul>
 * <p>
 * 好处：以后换个实现方式（比如换数据库），只改实现类，接口不变，调用方不用改。
 * 现在可能理解不深，但养成分层的习惯很重要。
 *
 * <h2>📖 ApiResponse&lt;T&gt; 泛型</h2>
 * <p>
 * T 是返回数据的类型，不同接口返回不同数据：
 * <ul>
 *   <li>注册：ApiResponse&lt;Void&gt; — 成功时 data 为 null</li>
 *   <li>登录：ApiResponse&lt;Map&lt;String, Object&gt;&gt; — 返回 token 和昵称</li>
 *   <li>查用户：ApiResponse&lt;User&gt; — 返回用户对象</li>
 * </ul>
 */
//注册，登录，用户信息
public interface UserService {

    /**
     * 用户注册
     *
     * <h2>📖 注册要做哪些事？</h2>
     * <ol>
     *   <li>检查用户名是否已存在 → 已存在则返回错误</li>
     *   <li>密码加密（BCrypt，明文永远不能存数据库）</li>
     *   <li>保存用户到数据库</li>
     *   <li>返回成功响应</li>
     * </ol>
     */
    ApiResponse<Void> register(RegisterRequest request);

    /**
     * 用户登录
     *
     * <h2>📖 登录要做哪些事？</h2>
     * <ol>
     *   <li>根据用户名查用户 → 不存在则返回"用户名或密码错误"</li>
     *   <li>比对密码 → 密码不匹配则返回"用户名或密码错误"</li>
     *   <li>⚠️ 为什么不确定地告诉用户"用户名不存在"还是"密码错误"？
     *       因为这样攻击者就能用这个区别来探测哪些用户名已注册（安全考量）</li>
     *   <li>生成 JWT Token</li>
     *   <li>返回 Token + 昵称等信息</li>
     * </ol>
     */
    ApiResponse<Map<String, Object>> login(LoginRequest request);

    /**
     * 根据 ID 获取用户信息
     * <p>
     * 用于 /api/user/me 接口（用户查看自己的信息）
     */
    User getById(Long userId);
}
