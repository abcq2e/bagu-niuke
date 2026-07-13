-- ============================================
-- 用户表：存储注册用户信息
-- 在 MySQL 中执行此脚本前，请先确保数据库存在
-- ============================================
CREATE TABLE IF NOT EXISTS `user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`   VARCHAR(50)  NOT NULL COMMENT '登录用户名',
    `password`   VARCHAR(255) NOT NULL COMMENT 'BCrypt加密后的密码（永远不存明文）',
    `nickname`   VARCHAR(50)  DEFAULT NULL COMMENT '展示昵称',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
