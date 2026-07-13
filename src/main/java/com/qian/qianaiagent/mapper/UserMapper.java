package com.qian.qianaiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qian.qianaiagent.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问层（Mapper）
 *
 * <h2>📖 Mapper 是什么？</h2>
 * <p>
 * Mapper = 操作数据库的接口。你只需要定义接口，MyBatis-Plus 会自动生成实现类。
 * 继承 BaseMapper&lt;User&gt; 后，免费获得这些方法（不用写一行 SQL）：
 * <ul>
 *   <li>insert(user) — 插入一条用户记录</li>
 *   <li>selectById(id) — 根据 ID 查询</li>
 *   <li>selectOne(queryWrapper) — 条件查询一条（比如查用户名是否存在）</li>
 *   <li>selectList(queryWrapper) — 条件查询多条</li>
 *   <li>updateById(user) — 根据 ID 更新</li>
 *   <li>deleteById(id) — 根据 ID 删除</li>
 * </ul>
 *
 * <h2>📖 接口继承 vs 类继承</h2>
 * <p>
 * Java 中：类继承类用 extends，接口继承接口也用 extends。
 * 这里 UserMapper 是一个接口，BaseMapper 也是一个接口，所以用 extends。
 * 泛型 &lt;User&gt; 告诉 BaseMapper 操作的是 user 表对应的 User 实体类。
 *
 * <h2>📖 @Mapper 注解</h2>
 * <p>
 * 告诉 Spring："这是一个 MyBatis 的 Mapper 接口，启动时帮我扫描并生成代理对象"。
 * 如果不加这个注解，Spring 不知道这个接口的存在，注入时会报错。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 继承 BaseMapper 后，基础的增删改查已经有了
    // 如果以后有特殊查询需求（比如"查找最近注册的10个用户"），在这里加方法即可
}
