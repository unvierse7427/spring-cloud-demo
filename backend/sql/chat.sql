-- =====================================================================
-- 聊天室相关表：聊天房间、聊天历史记录
-- 依赖 schema.sql 已创建 project 库
-- 执行方式：mysql -uroot -p < chat.sql
-- =====================================================================
USE project;

-- ---------------------------------------------------------------------
-- 聊天房间：用户创建后其他用户可加入
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_room
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '房间名称',
    description VARCHAR(255)          DEFAULT NULL COMMENT '房间简介',
    owner_id    BIGINT       NOT NULL COMMENT '创建者用户ID',
    owner_name  VARCHAR(64)  NOT NULL COMMENT '创建者用户名',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '聊天房间表';

-- ---------------------------------------------------------------------
-- 聊天历史记录：用户消息与系统消息（加入/离开房间）统一落库
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_message
(
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    room_id     BIGINT        NOT NULL COMMENT '房间ID',
    user_id     BIGINT        NOT NULL DEFAULT 0 COMMENT '发送者用户ID，系统消息为 0',
    username    VARCHAR(64)   NOT NULL COMMENT '发送者用户名，系统消息为 system',
    content     VARCHAR(1000) NOT NULL COMMENT '消息内容',
    msg_type    VARCHAR(16)   NOT NULL DEFAULT 'CHAT' COMMENT 'CHAT 用户消息 / SYSTEM 系统消息',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (id),
    KEY idx_msg_room_time (room_id, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '聊天历史记录表';

-- ---------------------------------------------------------------------
-- 初始化一个公共房间，便于首次进入聊天室直接体验
-- ---------------------------------------------------------------------
INSERT INTO chat_room (id, name, description, owner_id, owner_name)
SELECT 1, '公共大厅', '所有人可加入的默认聊天室', 1, 'admin'
WHERE NOT EXISTS(SELECT 1 FROM chat_room WHERE id = 1);
