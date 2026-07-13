package com.qian.qianaiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性类
 *
 * ===== 🎯 Task 6: 这个骨架已经生成好了，你来完成 =====
 * 当前只有两个字段，和 application.yml 中的 jwt.secret、jwt.expiration 对应。
 *
 * 💡 引导问题：
 * 1. @ConfigurationProperties(prefix = "jwt") 中的 "jwt" 对应 application.yml 中的哪个层级？
 * 2. 字段名 secret 和 expiration 如何映射到 yml 中的 jwt.secret 和 jwt.expiration？
 *    （提示：Spring 会自动做"点分隔"→"驼峰"的映射，但也支持完全相同的名字）
 * 3. 这个类需要什么注解来声明它是一个 Spring Bean？
 *    （提示：可以用 @Component，也可以只用 @ConfigurationProperties + @ConfigurationPropertiesScan）
 * 4. 如果 jwt.expiration 配置成了 "abc"（非数字），启动时会怎样？
 *    （提示：@ConfigurationProperties 是类型安全的，和 @Value 不同）
 *
 * 📖 完成后的使用方式（在 JwtUtil 里）：
 *    private final JwtProperties jwtProperties;
 *    public JwtUtil(JwtProperties jwtProperties) {
 *        this.jwtProperties = jwtProperties;
 *    }
 *    // 然后用 jwtProperties.getSecret() 替代 @Value("${jwt.secret}")
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** JWT 签名密钥（至少 256 位） */
    // TODO: Task 6 — 确认这个字段名和 yml 中的 jwt.secret 对应
    private String secret;

    /** Token 过期时间（毫秒） */
    // TODO: Task 6 — 确认这个字段名和 yml 中的 jwt.expiration 对应
    private long expiration;

    // 💡 扩展思考：如果你想增加 jwt.issuer 配置（签发者），需要改几个地方？
    //    用 @ConfigurationProperties：只在这里加一个字段 + yml 加一行 → JwtUtil 自动可用
    //    用 @Value：还要在 JwtUtil 加一个 @Value 字段
}
