package com.qian.qianaiagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qian.qianaiagent.mapper.UserMapper;
import com.qian.qianaiagent.model.dto.ApiResponse;
import com.qian.qianaiagent.model.dto.LoginRequest;
import com.qian.qianaiagent.model.dto.RegisterRequest;
import com.qian.qianaiagent.model.entity.User;
import com.qian.qianaiagent.service.UserService;
import com.qian.qianaiagent.util.JwtUtil;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static com.qian.qianaiagent.model.dto.ApiResponse.error;

/**
 * 用户业务逻辑实现类
 *
 * <h2>📖 分层架构的关键一环</h2>
 * <p>
 * Controller → Service → Mapper → Database
 * Controller 只管"接请求、返响应"，不写业务逻辑。
 * Service 只管"业务规则和逻辑"，不关心 HTTP。
 *
 * <h2>📖 @Service 注解</h2>
 * <p>
 * 告诉 Spring：这是一个 Service 层 Bean，请帮我管理。
 *
 * <h2>📖 @Resource vs @Autowired</h2>
 * <p>
 * 两者都是注入依赖。@Resource 是 Java 标准（按名称注入），@Autowired 是 Spring 原生（按类型注入）。
 * 这个项目用 @Resource。
 */
@Service
public class UserServiceImpl implements UserService {

    // ============================================================
    // 已生成：依赖注入（这三个是操作数据库/加密/Token 的核心工具）
    // 理解它们即可，不用改
    // ============================================================
    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private JwtUtil jwtUtil;


    // ============================================================
    // TODO: 实现注册方法
    //
    // 思路引导（先想清楚再写代码）：
    //
    // 第1步：检查用户名是否已存在
    //   提示：用 QueryWrapper<User> 构建查询条件
    //         new QueryWrapper<User>().eq("username", request.getUsername())
    //         然后用 userMapper.selectOne(wrapper) 查
    //         如果查出来的不是 null → 用户名已存在，返回 error
    //
    // 📖 QueryWrapper 是 MyBatis-Plus 的"条件构造器"。
    //    eq("username", "zhangsan") → WHERE username = 'zhangsan'
    //    selectOne(wrapper) → 查一条记录，没查到返回 null
    //
    // 第2步：加密密码
    //   提示：passwordEncoder.encode(request.getPassword())
    //   这个方法返回 BCrypt 加密后的密文，形如 "$2a$10$N9qo..."
    //
    // 📖 BCrypt 是单向加密（能加密不能解密），
    //    验证密码时用 matches(明文, 密文) —— 内部再加密一次然后比较
    //
    // 第3步：构建 User 对象并保存
    //   提示：
    //     User user = new User();
    //     user.setId(null);           // null 表示让数据库自增
    //     user.setUsername(request.getUsername());
    //     user.setPassword(加密后的密码);  // ⚠️ 存密文，不存明文！
    //     user.setNickname(request.getNickname());
    //     // createdAt 和 updatedAt 数据库自动填，不需要设置
    //     userMapper.insert(user);
    //
    // 📖 insert() 后 user.getId() 会自动回填数据库生成的 ID
    //    （MyBatis-Plus 的自动回填功能）
    //
    // 第4步：返回成功
    //   提示：return ApiResponse.success("注册成功", null);
    // ============================================================
    @Override
    public ApiResponse<Void> register(RegisterRequest request) {
        // ===== 在这里写你的注册逻辑（4 步）=====
       String userName = request.getUsername();
       String password = request.getPassword();
       String nickName = request.getNickname();
        QueryWrapper<User>wrapper=new QueryWrapper<>();
        wrapper.eq("username", request.getUsername());  // 数据库列名是 username（全小写）
        // 返回long，匹配到几条数据
        long count = userMapper.selectCount(wrapper);
        String encryptedText=passwordEncoder.encode(password);
        User user = new User();
        user.setUsername(userName);
        user.setNickname(nickName);
        user.setPassword(encryptedText);

        if (count > 0) {
            return ApiResponse.error("用户名已存在");
        }
        // count == 0，用户名不存在，可以注册
        userMapper.insert(user);
        return ApiResponse.success("注册成功", null);
        // ===== 你的代码结束 =====
    }

    // ============================================================
    // TODO: 实现登录方法
    //
    // 思路引导：
    //
    // 第1步：根据用户名查用户
    //   提示：和注册一样用 QueryWrapper + selectOne
    //         如果 user == null → return ApiResponse.error("用户名或密码错误");
    //
    //
    // 第2步：比对密码
    //   提示：passwordEncoder.matches(request.getPassword(), user.getPassword())
    //         第1个参数是用户输入的明文，第2个参数是数据库里的密文
    //         如果 matches 返回 false → return error
    //
    // 📖 为什么不能用 user.getPassword().equals(加密后的输入密码)？
    //    因为 BCrypt 每次加密结果都不同（每次加不同的"盐"），
    //    所以必须用 matches() 方法，它内部处理了"盐"的提取和比对
    //
    // 第3步：生成 Token
    //   提示：String token = jwtUtil.generateToken(user.getId(), user.getUsername());
    //
    // 第4步：组装返回数据
    //   提示：
    //     Map<String, Object> data = new HashMap<>();
    //     data.put("token", token);
    //     data.put("nickname", user.getNickname());
    //     return ApiResponse.success("登录成功", data);
    //
    // 📖 为什么返回 Map 而不是直接返回 String？
    //    因为前端需要多个信息（Token + 昵称），用 Map 灵活装多个值
    // ============================================================
    @Override
    public ApiResponse<Map<String, Object>> login(LoginRequest request) {
        // ===== 在这里写你的登录逻辑（4 步）=====
        QueryWrapper<User>wrapper=new QueryWrapper<>();
        wrapper.eq("username", request.getUsername());
        // ⚠️ 不能加 password 条件！数据库存的是 BCrypt 密文，明文永远匹配不上
        User user = userMapper.selectOne(wrapper);
        if (user==null){
            return error("用户名或密码错误");
        }
        boolean matches = passwordEncoder.matches(request.getPassword(), user.getPassword());

        if(!matches){
            return error("用户名或者密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("nickname", user.getNickname());
        return ApiResponse.success("登录成功", data);
        // ===== 你的代码结束 =====
         // TODO: 写完上面代码后删掉这行
    }

    // ============================================================
    // TODO: 实现根据 ID 查询用户
    //
    // 提示：一行代码 —— return userMapper.selectById(userId);
    //
    // 📖 selectById 是 BaseMapper 提供的方法，MyBatis-Plus 自动生成 SQL：
    //    SELECT * FROM user WHERE id = ?
    // ============================================================
    @Override
    public User getById(Long userId) {
        // ===== 在这里写你的代码（就一行）=====
            return userMapper.selectById(userId);
        // ===== 你的代码结束 =====

    }
}
