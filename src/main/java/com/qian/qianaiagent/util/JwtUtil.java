package com.qian.qianaiagent.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类 —— 负责生成和验证登录令牌
 *
 * <h2>📖 什么是 JWT（JSON Web Token）？</h2>
 * <p>
 * JWT 就是一个"有签名防伪的身份证"。服务端登录成功后签发一个 Token 给前端，
 * 后续请求带着它，服务端验证签名就知道"这个人是合法的"。
 * <p>
 * JWT 结构（三部分，用 . 分隔）：
 * <pre>
 *   Header.Payload.Signature
 * </pre>
 * <ul>
 *   <li><b>Header</b>：{"alg": "HS256"}  — 说明用 HMAC-SHA256 算法签名</li>
 *   <li><b>Payload</b>：{"userId": 1, "username": "zhangsan", "exp": 到期时间}  — 存业务数据</li>
 *   <li><b>Signature</b>：对 前两部分 + 密钥 做哈希 — 防止篡改</li>
 * </ul>
 *
 * <h2>📖 JWT 安全原理</h2>
 * <p>
 * 能不能自己改 Payload 里的 userId 冒充别人？不行！改了 Payload 后，
 * 签名就对不上了（因为签名是用 Header+Payload+密钥 算出来的），服务端一验就拒绝。
 * 除非你知道密钥，否则无法伪造合法签名。
 *
 * <h2>🎯 Task 6: 用 @ConfigurationProperties 替代 @Value</h2>
 * <p>
 * 当前用 @Value 逐个注入配置，你需要创建一个 JwtProperties 类来统一管理 JWT 配置。
 * 完成后，这个类中的 @Value 改为注入 JwtProperties。
 *
 * 💡 引导问题：
 * 1. @ConfigurationProperties 类需要什么字段？和 @Value 的名字有什么关系？
 * 2. 需要在 @SpringBootApplication 类上加什么注解来启用它？
 * 3. 这个类字段如何从 @Value 改为注入 JwtProperties？（构造函数注入 vs @Autowired）
 * 4. 注入一个 JwtProperties 对象后，secret 和 expiration 怎么获取？
 */
@Component
public class JwtUtil {

    // ===== 🎯 Task 6: 这两个 @Value 可以用 JwtProperties 替代 =====
    // 思考：如果以后再加一个 jwt.issuer 配置，用 @Value 要加第 3 个字段
    // 用 @ConfigurationProperties 只需要在 JwtProperties 里加一个字段
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * 生成 JWT Token
     *
     * <h2>📖 你需要实现什么：</h2>
     * <ol>
     *   <li>把 String 类型的 secret 转成 SecretKey 对象
     *       — 用 Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))</li>
     *   <li>计算过期时间：new Date(System.currentTimeMillis() + expiration)</li>
     *   <li>用 Jwts.builder() 链式构建 Token</li>
     * </ol>
     *
     * <h2>📖 Builder 模式（链式调用）</h2>
     * <p>
     * Jwts.builder().xxx().yyy().zzz() — 每个方法返回 this（自己），所以可以一直 . 下去。
     * 构建步骤：.subject() → .claim() → .issuedAt() → .expiration() → .signWith() → .compact()
     * 最后 .compact() 生成最终的 Token 字符串。
     */
    //生成token
    public String generateToken(Long userId, String username) {
        // ============================================================
        // TODO: 按照上面的步骤实现 Token 生成逻辑
        //
        // 伪代码（你需要把它翻译成 Java）：
        //   SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        //   Date now = new Date();
        //   Date expireDate = new Date(now.getTime() + expiration);
        //   return Jwts.builder()
        //       .subject(username)
        //       .claim("userId", userId)
        //       .issuedAt(now)
        //       .expiration(expireDate)
        //       .signWith(key)
        //       .compact();
        //
        // 📖 知识点：
        // - System.currentTimeMillis() 返回当前时间的毫秒数（从1970年开始算）
        // - claim("userId", userId) 把自定义数据放进 Payload
        // - subject(username) 设置主题（JWT 标准字段，习惯放用户名）
        // - compact() 把前面设置的所有内容打包成一个字符串返回
        // ============================================================
        // ===== 在这里写你的代码 =====
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiration);   //更新令牌过期的时间
        return Jwts.builder()    //主要是进行构建
                .subject(username)           // 主题
                .claim("userId", userId)     // 自定义数据
                .issuedAt(now)               // 签发时间
                .expiration(expireDate)      // 过期时间
                .signWith(key)               // 签名
                .compact();
        // ===== 你的代码结束 =====

    }

    /**
     * 解析并验证 JWT Token
     *
     * <h2>📖 你需要实现什么：</h2>
     * <ol>
     *   <li>把 String 类型的 secret 转成 SecretKey 对象（同上）</li>
     *   <li>用 Jwts.parser().verifyWith(key).build() 创建解析器</li>
     *   <li>用 parser.parseSignedClaims(token) 解析 Token → 得到 Jws<Claims></li>
     *   <li>jws.getPayload() 获取 Payload（Claims 对象）</li>
     *   <li>返回 Claims 对象</li>
     * </ol>
     *
     * <h2>📖 如果 Token 过期或伪造了会怎样？</h2>
     * <p>
     * parseSignedClaims() 会自动验签和检查过期时间。
     * Token 不合法会抛出异常（ExpiredJwtException / JwtException 等）。
     * 你不用在这里处理异常——调用方（JwtAuthFilter）会 catch。
     */
    public Claims parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token 不能为空");
        }
        // ============================================================
        // TODO: 按照上面的步骤实现 Token 解析逻辑
        //
        // 伪代码：
        //   SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        //   Jws<Claims> jws = Jwts.parser()
        //       .verifyWith(key)
        //       .build()
        //       .parseSignedClaims(token);
        //   return jws.getPayload();
        //
        // 📖 调用方怎么用这个 Claims？
        //   Claims claims = jwtUtil.parseToken(token);
        //   Long userId = claims.get("userId", Long.class);   // 自定义字段
        //   String username = claims.getSubject();             // 标准字段(subject)
        // ============================================================
        // ===== 在这里写你的代码 =====
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));//本方法校验，匹配，封装，返回，提供签名
        Jws<Claims> jws = Jwts.parser()     //准备解析工厂，进行校验
                .verifyWith(key)   //对应Signature签名段
                .build()      //组装配置好的解析器对象
                .parseSignedClaims(token);    //完整解析三段，分两步：切割token为Header,Claims,Signature三部分，执行verifyWith的签名校验，打包进行Jws<Claims>返回
        Claims claims = jws.getPayload();
        return claims;

        // ===== 你的代码结束 =====

    }
}
