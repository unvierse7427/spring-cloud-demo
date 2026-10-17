-- ============================================================
-- RAG 知识库：知识库表与文档表
-- 库名沿用现有微服务使用的 project
-- ============================================================

CREATE TABLE IF NOT EXISTS `kb_knowledge_base`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(64)  NOT NULL COMMENT '知识库名称',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
    `embedding_model` VARCHAR(64) NOT NULL DEFAULT 'text-embedding-v2' COMMENT '使用的嵌入模型',
    `owner_id`    BIGINT       NOT NULL COMMENT '创建者用户 id',
    `owner_name`  VARCHAR(64)  DEFAULT NULL COMMENT '创建者用户名',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kb_name` (`name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='RAG 知识库';

CREATE TABLE IF NOT EXISTS `kb_document`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `kb_id`        BIGINT       NOT NULL COMMENT '所属知识库 id',
    `file_name`    VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `doc_key`      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '逻辑文档标识，同一知识库内同名文件视为同一文档',
    `version`      INT          NOT NULL DEFAULT 1 COMMENT '版本号，同名文档首次上传为 1，内容变化后递增',
    `content_hash` VARCHAR(64)  DEFAULT NULL COMMENT '文件内容 SHA-256，用于比对两次上传的内容是否一致',
    `latest`       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否最新版本：1 是 0 否（历史版本保留存档但不参与检索）',
    `visible_roles` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '可见角色码，逗号分隔（如 ROLE_ADMIN,ROLE_USER），为空表示所有用户可见',
    `object_key`   VARCHAR(512) NOT NULL COMMENT '在 MinIO 中的对象 key',
    `content_type` VARCHAR(128) DEFAULT NULL COMMENT '文件类型',
    `file_size`    BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    -- 0 待处理 1 处理中 2 已完成 3 失败
    `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '入库状态',
    `chunk_count`  INT          NOT NULL DEFAULT 0 COMMENT '切分出的分片数',
    `error_msg`    VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
    `owner_id`     BIGINT       NOT NULL COMMENT '上传者用户 id',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_kb_id` (`kb_id`),
    KEY `idx_kb_doc_key` (`kb_id`, `doc_key`, `latest`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='RAG 知识库文档';
