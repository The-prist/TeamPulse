-- =====================================================================
-- TeamPulse V2：物联网考勤域
-- 设计说明：
--   1. iot_device 保存设备身份 + HMAC 密钥，heartbeat 由 MQTT 心跳维护
--   2. attendance_record 是「打卡事件流」，source 区分 MQTT/HTTP/MANUAL
--   3. attendance_daily 是「日汇总」，由定时任务产出，供报表与看板直接查询
--   4. 幂等：uk_message 保证同一报文集重入不会产生重复记录
--   5. 防重放：nonce 校验在应用层（Redis），数据库用 uk_emp_time 兜底
-- =====================================================================

CREATE TABLE `iot_device`
(
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '设备主键',
    `device_sn`        VARCHAR(64) NOT NULL COMMENT '设备序列号（报文里的 deviceSn）',
    `device_name`      VARCHAR(100)         DEFAULT NULL COMMENT '设备名称',
    `location`         VARCHAR(120)         DEFAULT NULL COMMENT '安装位置',
    `secret`           VARCHAR(64) NOT NULL COMMENT 'HMAC-SHA256 共享密钥',
    `status`           VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE/DISABLED',
    `firmware_version` VARCHAR(40)          DEFAULT NULL COMMENT '固件版本',
    `ip_address`       VARCHAR(64)          DEFAULT NULL COMMENT '最近上报 IP',
    `last_heartbeat`   DATETIME             DEFAULT NULL COMMENT '最近心跳时间',
    `remark`           VARCHAR(255)         DEFAULT NULL COMMENT '备注',
    `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`          TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_sn` (`device_sn`),
    KEY `idx_device_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='物联网打卡设备表';

CREATE TABLE `attendance_rule`
(
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT,
    `rule_name`           VARCHAR(100) NOT NULL COMMENT '规则名称',
    `work_start`          TIME        NOT NULL COMMENT '上班时间',
    `work_end`            TIME        NOT NULL COMMENT '下班时间',
    `late_grace_minutes`  INT         NOT NULL DEFAULT 0 COMMENT '迟到宽限（分钟）',
    `early_grace_minutes` INT         NOT NULL DEFAULT 0 COMMENT '早退宽限（分钟）',
    `work_days`           VARCHAR(20) NOT NULL DEFAULT '1,2,3,4,5' COMMENT '工作日，ISO 星期，1=周一',
    `enabled`             TINYINT     NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_name` (`rule_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='考勤规则表';

CREATE TABLE `attendance_record`
(
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`             BIGINT               DEFAULT NULL COMMENT '关联系统用户（工号未匹配时为 NULL）',
    `employee_no`         VARCHAR(32) NOT NULL COMMENT '工号',
    `device_sn`           VARCHAR(64) NOT NULL COMMENT '来源设备',
    `punch_type`          VARCHAR(16) NOT NULL COMMENT 'CHECK_IN/CHECK_OUT',
    `punch_time`          DATETIME    NOT NULL COMMENT '打卡时间',
    `work_date`           DATE        NOT NULL COMMENT '归属工作日',
    `status`              VARCHAR(24) NOT NULL COMMENT 'NORMAL/LATE/EARLY_LEAVE/LATE_AND_EARLY/INVALID',
    `late_minutes`        INT         NOT NULL DEFAULT 0,
    `early_leave_minutes` INT         NOT NULL DEFAULT 0,
    `source`              VARCHAR(16) NOT NULL DEFAULT 'MQTT' COMMENT 'MQTT/HTTP/MANUAL',
    `message_id`          VARCHAR(64)          DEFAULT NULL COMMENT '报文幂等键',
    `sign_verified`       TINYINT     NOT NULL DEFAULT 0 COMMENT '签名是否校验通过',
    `raw_payload`         TEXT COMMENT '原始报文，便于排障与审计',
    `remark`              VARCHAR(255)         DEFAULT NULL,
    `created_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_message` (`message_id`),
    UNIQUE KEY `uk_record_emp_time` (`employee_no`, `punch_time`),
    KEY `idx_record_user_date` (`user_id`, `work_date`),
    KEY `idx_record_date_status` (`work_date`, `status`),
    KEY `idx_record_device` (`device_sn`, `punch_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='打卡记录表';

CREATE TABLE `attendance_daily`
(
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`             BIGINT      NOT NULL,
    `employee_no`         VARCHAR(32) NOT NULL,
    `work_date`           DATE        NOT NULL,
    `first_check_in`      DATETIME             DEFAULT NULL,
    `last_check_out`      DATETIME             DEFAULT NULL,
    `work_minutes`        INT         NOT NULL DEFAULT 0 COMMENT '在岗分钟数',
    `late_minutes`        INT         NOT NULL DEFAULT 0,
    `early_leave_minutes` INT         NOT NULL DEFAULT 0,
    `attendance_status`   VARCHAR(24) NOT NULL COMMENT 'NORMAL/LATE/EARLY_LEAVE/ABSENT',
    `created_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_daily_user_date` (`user_id`, `work_date`),
    KEY `idx_daily_date` (`work_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='考勤日汇总表';
