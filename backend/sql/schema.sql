-- =============================================================
-- 项目数据库初始化脚本
-- MySQL 8.0 / 数据库名：project
-- 执行方式：mysql -uroot -p < schema.sql
-- =============================================================

CREATE DATABASE IF NOT EXISTS `project`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `project`;

-- -------------------------------------------------------------
-- 用户表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(64)     NOT NULL COMMENT '登录账号',
    `password`    VARCHAR(100)    NOT NULL COMMENT 'BCrypt 加密后的密码',
    `nickname`    VARCHAR(64)              DEFAULT NULL COMMENT '昵称',
    `email`       VARCHAR(128)             DEFAULT NULL COMMENT '邮箱',
    `phone`       VARCHAR(20)              DEFAULT NULL COMMENT '手机号',
    `status`      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户表';

-- -------------------------------------------------------------
-- 角色表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_code`   VARCHAR(64)     NOT NULL COMMENT '角色标识，如 ROLE_ADMIN',
    `role_name`   VARCHAR(64)     NOT NULL COMMENT '角色名称',
    `description` VARCHAR(255)             DEFAULT NULL COMMENT '描述',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统角色表';

-- -------------------------------------------------------------
-- 权限表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `perm_code`   VARCHAR(64)     NOT NULL COMMENT '权限码，如 user:create',
    `perm_name`   VARCHAR(64)     NOT NULL COMMENT '权限名称',
    `description` VARCHAR(255)             DEFAULT NULL COMMENT '描述',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统权限表';

-- -------------------------------------------------------------
-- 用户角色关联表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`
(
    `id`      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 id',
    `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色 id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户角色关联表';

-- -------------------------------------------------------------
-- 角色权限关联表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`       BIGINT UNSIGNED NOT NULL COMMENT '角色 id',
    `permission_id` BIGINT UNSIGNED NOT NULL COMMENT '权限 id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='角色权限关联表';

-- =============================================================
-- 初始化数据
-- 账号：admin / admin123（管理员）   zhangsan、lisi / user123（普通用户）
-- =============================================================

INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`)
VALUES (1, 'ROLE_ADMIN', '系统管理员', '拥有全部权限'),
       (2, 'ROLE_USER', '普通用户', '只读权限');

INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `description`)
VALUES (1, 'user:list', '用户查询', '查询用户列表与详情'),
       (2, 'user:create', '用户新增', '新增用户'),
       (3, 'user:update', '用户修改', '修改用户信息'),
       (4, 'user:delete', '用户删除', '删除用户');

INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `email`, `phone`, `status`)
VALUES (1, 'admin', '$2a$10$gjENxgptNo1.X1cnrdvQi.1rfc7/ekHzn/V2g5lUHK83KFEVZ/y/m', '管理员', 'admin@example.com', '13800000001', 1),
       (2, 'zhangsan', '$2a$10$VsdL5T9t9oO7fsbeoKPD4eGgP6FMUMbo1k6vPmiN9GD7PqfSEkhQW', '张三', 'zhangsan@example.com', '13800000002', 1),
       (3, 'lisi', '$2a$10$VsdL5T9t9oO7fsbeoKPD4eGgP6FMUMbo1k6vPmiN9GD7PqfSEkhQW', '李四', 'lisi@example.com', '13800000003', 1);

INSERT INTO `sys_user_role` (`user_id`, `role_id`)
VALUES (1, 1),
       (2, 2),
       (3, 2);

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
VALUES (1, 1), (1, 2), (1, 3), (1, 4),
       (2, 1);
