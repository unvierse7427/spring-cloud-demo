-- =====================================================================
-- 日志相关表：登录/登出日志、接口调用日志
-- 依赖 schema.sql 已创建 project 库
-- =====================================================================
USE project;

-- ---------------------------------------------------------------------
-- 登录/登出日志：记录账号登录、登出、刷新令牌的结果
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_login_log
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT                DEFAULT NULL COMMENT '用户ID，登录失败时可能无法确定',
    username   VARCHAR(64)  NOT NULL COMMENT '登录账号',
    login_type VARCHAR(16)  NOT NULL DEFAULT 'LOGIN' COMMENT 'LOGIN 登录 / LOGOUT 登出 / REFRESH 刷新令牌',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1 成功，0 失败',
    message    VARCHAR(255)          DEFAULT NULL COMMENT '结果描述',
    client_ip  VARCHAR(64)           DEFAULT NULL COMMENT '客户端IP',
    user_agent VARCHAR(512)          DEFAULT NULL COMMENT 'User-Agent',
    login_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_login_log_username (username),
    KEY idx_login_log_time (login_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '登录/登出日志';

-- ---------------------------------------------------------------------
-- 接口调用日志：由各业务服务的过滤器统一记录
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_operation_log
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    service_name   VARCHAR(64)           DEFAULT NULL COMMENT '来源服务',
    user_id        BIGINT                DEFAULT NULL COMMENT '操作用户ID',
    username       VARCHAR(64)           DEFAULT NULL COMMENT '操作用户',
    request_method VARCHAR(10)           DEFAULT NULL COMMENT 'HTTP 方法',
    request_uri    VARCHAR(255)          DEFAULT NULL COMMENT '请求路径',
    query_string   VARCHAR(512)          DEFAULT NULL COMMENT '查询参数',
    client_ip      VARCHAR(64)           DEFAULT NULL COMMENT '客户端IP',
    user_agent     VARCHAR(512)          DEFAULT NULL COMMENT 'User-Agent',
    status_code    INT                   DEFAULT NULL COMMENT 'HTTP 状态码',
    duration_ms    BIGINT                DEFAULT NULL COMMENT '耗时（毫秒）',
    success        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 成功，0 失败',
    error_message  VARCHAR(512)          DEFAULT NULL COMMENT '异常信息',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_op_log_username (username),
    KEY idx_op_log_uri (request_uri),
    KEY idx_op_log_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '接口调用日志';
