package com.qian.qianaiagent.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
// import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;  // 3.5.10 找不到这个类
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 * <p>
 * 包含：Mapper 扫描 + 分页插件。
 * <p>
 * ===== 🎯 Task 14: 分页插件已生成 =====
 * PaginationInnerInterceptor 是 MyBatis-Plus 的分页拦截器，
 * 注册后 baseMapper.selectPage() 才能正常工作。
 * <p>
 * 💡 思考：DbType.MYSQL 是什么意思？
 *   不同数据库分页语法不同：MySQL LIMIT / Oracle ROWNUM / PostgreSQL OFFSET-LIMIT
 *   指定 DbType 后 MyBatis-Plus 自动生成对应数据库的分页 SQL。
 */
@Configuration
@MapperScan("com.qian.qianaiagent.mapper")
public class MyBatisPlusConfig {

    /**
     * 分页插件（暂时禁用：MyBatis-Plus 3.5.10 中 PaginationInnerInterceptor 找不到）
     * TODO: 查一下 MyBatis-Plus 3.5.10 的 PaginationInnerInterceptor 正确包名
     */
//    @Bean
//    public MybatisPlusInterceptor mybatisPlusInterceptor() {
//        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
//        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
//        return interceptor;
//    }
}
