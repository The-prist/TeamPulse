-- =====================================================================
-- TeamPulse V6：操作审计日志
--
-- 背景：原来只有「补卡审批」留痕（makeup_request 里有审批人/时间/备注），
-- 改考勤规则、删评论、重置密码这类管理动作完全查不到谁干的。
-- 审计日志是不可修改、不可删除的追加表，因此：
--   1) 不继承 BaseEntity（没有 updated_at / deleted，写入后即不可变）；
--   2) 单独建索引支撑「按人 / 按动作 / 按时间 / 按 traceId」四种查法。
-- =====================================================================
CREATE TABLE IF NOT EXISTS `audit_log` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `trace_id`     VARCHAR(64)  DEFAULT NULL COMMENT '与请求头 X-Trace-Id 一致，用于串联服务端日志',
    `actor_id`     BIGINT       DEFAULT NULL COMMENT '操作人用户 id',
    `actor_name`   VARCHAR(64)  DEFAULT NULL COMMENT '操作人昵称快照（用户改名后仍保留当时值）',
    `actor_role`   VARCHAR(32)  DEFAULT NULL COMMENT '操作人角色快照',
    `module`       VARCHAR(32)  DEFAULT NULL COMMENT '模块：user/team/attendance/device/comment/sensitive',
    `action`       VARCHAR(64)  NOT NULL COMMENT '动作标识，如 user.create / attendance.rule.update',
    `resource`     VARCHAR(64)  DEFAULT NULL COMMENT '资源类型，如 user/team/device',
    `resource_id`  VARCHAR(64)  DEFAULT NULL COMMENT '资源 id（取被注解方法的第一个简单参数）',
    `method`       VARCHAR(8)   DEFAULT NULL COMMENT 'HTTP 方法',
    `path`         VARCHAR(255) DEFAULT NULL COMMENT '请求路径',
    `detail`       VARCHAR(500) DEFAULT NULL COMMENT '变更要点（注解里的静态描述）',
    `result`       VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAILURE',
    `error_msg`    VARCHAR(500) DEFAULT NULL COMMENT '失败原因（业务异常 message）',
    `cost_ms`      INT          DEFAULT NULL COMMENT '方法耗时（毫秒）',
    `ip`           VARCHAR(64)  DEFAULT NULL COMMENT '来源 IP',
    `user_agent`   VARCHAR(255) DEFAULT NULL COMMENT 'User-Agent（截断）',
    `created_at`   DATETIME     NOT NULL COMMENT '发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_audit_actor` (`actor_id`, `created_at`),
    KEY `idx_audit_action` (`action`, `created_at`),
    KEY `idx_audit_created` (`created_at`),
    KEY `idx_audit_trace` (`trace_id`),
    KEY `idx_audit_module` (`module`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作审计日志（追加写入，不可修改）';
