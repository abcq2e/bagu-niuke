package com.qian.qianaiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体类 —— 对应数据库的 user 表
 *
 * <h2>📖 实体类是什么？</h2>
 * <p>
 * 实体类（Entity）就是"数据库表的 Java 镜像"。
 * 数据库 user 表的每一行数据  →  Java 的一个 User 对象。
 * 数据库 user 表的每一列      →  User 的一个字段。
 * <p>
 * MyBatis-Plus 通过注解自动完成映射，你不用手写 SQL。
 *
 * <h2>📖 关键注解说明</h2>
 * <ul>
 *   <li>{@code @TableName("user")} — 这个类映射到数据库的 user 表</li>
 *   <li>{@code @TableId(type = IdType.AUTO)} — 标记主键，数据库自增</li>
 *   <li>{@code @TableField("列名")} — 当 Java 字段名和数据库列名不一样时使用</li>
 * </ul>
 *
 * <h2>📖 Lombok 注解说明</h2>
 * <ul>
 *   <li>{@code @Data} — 自动生成所有字段的 get/set + toString + equals + hashCode</li>
 *   <li>{@code @NoArgsConstructor} — 自动生成空参构造方法 new User()</li>
 *   <li>{@code @AllArgsConstructor} — 自动生成全参构造方法</li>
 * </ul>
 *
 * <h2>⚠️ 重要：命名规则</h2>
 * <p>
 * MyBatis-Plus 默认把 Java 驼峰命名转成数据库下划线命名：
 * <ul>
 *   <li>{@code userName} → user_name（❌ 数据库列名是 username，对不上！）</li>
 *   <li>{@code username} → username（✅ 数据库列名就是 username）</li>
 *   <li>{@code createdAt} → created_at（✅ 加上 {@code @TableField("created_at")} 手动指定）</li>
 * </ul>
 * <p>
 * <b>规则：单词本身是完整且小写的（如 username、password、nickname），
 * Java 字段也用小写，不拆写驼峰。</b>
 *
 *
 */
@TableName("user")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /**
     * 用户唯一 ID（主键，数据库自增）
     * <p>
     * 📖 插入数据时不需要设置 id（设为 null），
     * MyBatis-Plus 在 insert 后会自动回填数据库生成的 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名 */
    private String username;

    /** BCrypt 加密后的密码（永远不能存明文！） */
    private String password;

    /** 展示昵称（注册时可选） */
    private String nickname;

    // ===== 🎯 Task 14: 添加用户状态字段 =====
    // 管理后台需要启用/禁用用户。添加一个 status 字段：
    // 💡 思考：用什么类型？int 还是 enum？
    //    1 = 正常（默认），0 = 禁用
    // 📖 注意: 加完字段后要在对应的 SQL 脚本和数据库中也加列！
    //    数据库加列 SQL: ALTER TABLE user ADD COLUMN status INT DEFAULT 1;
    // 你的代码写在这里 ↓

    // 你的代码写在这里 ↑

    /**
     * 注册时间
     * <p>
     * 📖 数据库列名 created_at（下划线），Java 字段 createdAt（驼峰），
     * 所以用 @TableField 手动指定映射。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 最后修改时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
