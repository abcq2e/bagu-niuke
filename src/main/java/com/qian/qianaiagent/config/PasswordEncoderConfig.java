package com.qian.qianaiagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密配置
 * <p>
 * 把 BCryptPasswordEncoder 注册为 Spring Bean，方便在 Service 中注入使用。
 * <p>
 * BCrypt 是一种"单向哈希"算法——只能加密，不能解密。
 * 验证密码时，用同样的算法把用户输入的密码再加密一次，和数据库里的密文对比。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
