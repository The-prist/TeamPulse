-- =====================================================================
-- TeamPulse V1：协作域基础表
-- 设计说明：
--   1. 统一使用 utf8mb4 + InnoDB；主键沿用「业务名_id」命名，与前端字段保持一致
--   2. 所有可变表带 created_at / updated_at / deleted（逻辑删除）
--   3. 相比旧版删除了 attachment / permission / permission_action /
--      team_default_permission —— 它们在旧代码中从未真正接入鉴权链路
-- =====================================================================

CREATE TABLE `user`
(
    `user_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`    VARCHAR(50)  NOT NULL COMMENT '登录名',
    `password`    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt，兼容历史明文自动升级）',
    `nickname`    VARCHAR(50)           DEFAULT NULL COMMENT '昵称',
    `email`       VARCHAR(100) NOT NULL COMMENT '邮箱',
    `phone`       VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    `employee_no` VARCHAR(32)           DEFAULT NULL COMMENT '员工工号（打卡机上报的唯一标识）',
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'member' COMMENT '系统角色 admin/manager/member',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT '账号状态 active/inactive',
    `avatar`      VARCHAR(255)          DEFAULT NULL COMMENT '头像地址',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_email` (`email`),
    UNIQUE KEY `uk_user_employee_no` (`employee_no`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

CREATE TABLE `team`
(
    `team_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '团队ID',
    `team_name`   VARCHAR(100) NOT NULL COMMENT '团队名称',
    `description` TEXT COMMENT '团队描述',
    `creator_id`  BIGINT       NOT NULL COMMENT '创建人',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`team_id`),
    UNIQUE KEY `uk_team_name` (`team_name`),
    KEY `idx_team_creator` (`creator_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='团队表';

CREATE TABLE `team_member`
(
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `team_id`    BIGINT      NOT NULL COMMENT '团队ID',
    `user_id`    BIGINT      NOT NULL COMMENT '成员用户ID',
    `team_role`  VARCHAR(20) NOT NULL DEFAULT 'member' COMMENT '团队内角色 leader/member',
    `status`     VARCHAR(20) NOT NULL DEFAULT 'active',
    `join_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_team_user` (`team_id`, `user_id`),
    KEY `idx_tm_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='团队成员表';

CREATE TABLE `schedule`
(
    `schedule_id` BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日程ID',
    `title`       VARCHAR(200) NOT NULL COMMENT '标题',
    `content`     TEXT COMMENT '详情',
    `start_time`  DATETIME     NOT NULL COMMENT '开始时间',
    `end_time`    DATETIME     NOT NULL COMMENT '结束时间',
    `location`    VARCHAR(200)          DEFAULT NULL COMMENT '地点',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'todo' COMMENT 'todo/doing/done/canceled',
    `priority`    VARCHAR(20)  NOT NULL DEFAULT 'medium' COMMENT 'low/medium/high',
    `creator_id`  BIGINT       NOT NULL COMMENT '创建人',
    `team_id`     BIGINT                DEFAULT NULL COMMENT '所属团队，NULL 表示个人日程',
    `remind_time` DATETIME              DEFAULT NULL COMMENT '提醒时间',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`schedule_id`),
    KEY `idx_schedule_team_time` (`team_id`, `start_time`),
    KEY `idx_schedule_creator` (`creator_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='日程表';

CREATE TABLE `worklog`
(
    `worklog_id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `title`                VARCHAR(200) NOT NULL COMMENT '标题',
    `content`              TEXT         NOT NULL COMMENT '内容',
    `log_date`             DATE         NOT NULL COMMENT '所属日期',
    `creator_id`           BIGINT       NOT NULL COMMENT '记录人',
    `related_schedule_id`  BIGINT                DEFAULT NULL COMMENT '关联日程',
    `related_team_id`      BIGINT                DEFAULT NULL COMMENT '关联团队',
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`              TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`worklog_id`),
    KEY `idx_worklog_creator_date` (`creator_id`, `log_date`),
    KEY `idx_worklog_team` (`related_team_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='工作日志表';

CREATE TABLE `kanban`
(
    `kanban_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '看板ID',
    `kanban_name` VARCHAR(100) NOT NULL COMMENT '看板名称',
    `description` TEXT COMMENT '描述',
    `team_id`     BIGINT       NOT NULL COMMENT '所属团队',
    `creator_id`  BIGINT       NOT NULL COMMENT '创建人',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`kanban_id`),
    KEY `idx_kanban_team` (`team_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='看板表';

CREATE TABLE `kanban_column`
(
    `column_id`   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '列ID',
    `kanban_id`   BIGINT      NOT NULL COMMENT '所属看板',
    `column_name` VARCHAR(50) NOT NULL COMMENT '列名',
    `sort_order`  INT         NOT NULL DEFAULT 0 COMMENT '列排序',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`column_id`),
    KEY `idx_column_kanban` (`kanban_id`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='看板列表';

CREATE TABLE `kanban_item`
(
    `item_id`     BIGINT   NOT NULL AUTO_INCREMENT COMMENT '卡片ID',
    `kanban_id`   BIGINT   NOT NULL COMMENT '所属看板',
    `schedule_id` BIGINT   NOT NULL COMMENT '关联日程（卡片是日程在看板上的投影）',
    `column_id`   BIGINT   NOT NULL COMMENT '所在列',
    `assignee_id` BIGINT            DEFAULT NULL COMMENT '承接人',
    `assign_time` DATETIME          DEFAULT NULL COMMENT '承接时间',
    `sort_order`  INT      NOT NULL DEFAULT 0 COMMENT '列内排序',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`item_id`),
    KEY `idx_item_column_sort` (`column_id`, `sort_order`),
    KEY `idx_item_schedule` (`schedule_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='看板卡片表';

CREATE TABLE `comment`
(
    `comment_id`       BIGINT      NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `related_type`     VARCHAR(20) NOT NULL COMMENT '关联类型 schedule/worklog',
    `related_id`       BIGINT      NOT NULL COMMENT '关联业务ID',
    `creator_id`       BIGINT      NOT NULL COMMENT '评论人',
    `content`          TEXT        NOT NULL COMMENT '过滤后内容',
    `original_content` TEXT COMMENT '原始内容（未过滤敏感词，便于审计）',
    `reply_to`         BIGINT               DEFAULT NULL COMMENT '回复的父评论',
    `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`          TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`comment_id`),
    KEY `idx_comment_related` (`related_type`, `related_id`),
    KEY `idx_comment_reply` (`reply_to`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='评论表';

CREATE TABLE `sensitive_word`
(
    `word_id`     BIGINT       NOT NULL AUTO_INCREMENT,
    `word`        VARCHAR(100) NOT NULL COMMENT '敏感词',
    `replacement` VARCHAR(100) NOT NULL DEFAULT '***' COMMENT '替换文本',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
    `creator_id`  BIGINT                DEFAULT NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`word_id`),
    UNIQUE KEY `uk_word` (`word`),
    KEY `idx_word_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='敏感词表';
