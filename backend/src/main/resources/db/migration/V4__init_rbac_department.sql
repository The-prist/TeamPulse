-- =====================================================================
-- TeamPulse V4：企业级角色分工与数据权限
--
-- 设计说明：
--   1. department 用 parent_id 自关联，支持「公司 → 部门 → 小组」多级。
--   2. role / permission / role_permission 构成 RBAC 三表：
--      员工绑定**一个主角色**（user.role 存 role_key），符合大多数企业
--      「一人一岗」的实际做法；需要一人多岗时再拆 user_role 中间表。
--   3. 数据范围（data_scope）放在**角色**上而不是接口上：
--      ALL 全公司 / DEPT 本部门 / SELF 仅本人。
--      这样加角色只需插数据，不用改代码里的 if-else。
--   4. makeup_request 是补卡审批流：员工申请 → 部门经理或 HR 审批 → 通过才落考勤。
--      真实考勤系统的核心是**审批**，直接补卡会让考勤失去可信度。
--   5. 现有数据回填：admin→sysadmin、member→employee，并按人名分配部门与角色，
--      让演示时每个角色都有真实的人。
-- =====================================================================

-- ---------------------------------------------------------------- 部门
CREATE TABLE `department`
(
    `department_id` BIGINT      NOT NULL AUTO_INCREMENT,
    `parent_id`     BIGINT               DEFAULT NULL COMMENT '上级部门，NULL 表示顶级',
    `dept_name`     VARCHAR(64) NOT NULL COMMENT '部门名称',
    `dept_code`     VARCHAR(32) NOT NULL COMMENT '部门编码',
    `leader_id`     BIGINT               DEFAULT NULL COMMENT '部门负责人 user_id',
    `sort_no`       INT         NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`department_id`),
    UNIQUE KEY `uk_dept_code` (`dept_code`),
    KEY `idx_dept_parent` (`parent_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='部门（树形）';

-- ---------------------------------------------------------------- 角色
CREATE TABLE `role`
(
    `role_id`     BIGINT      NOT NULL AUTO_INCREMENT,
    `role_key`    VARCHAR(32) NOT NULL COMMENT '角色标识，与 user.role 对应',
    `role_name`   VARCHAR(64) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(255)         DEFAULT NULL,
    `data_scope`  VARCHAR(16) NOT NULL DEFAULT 'SELF' COMMENT '数据范围：ALL/DEPT/SELF',
    `built_in`    TINYINT     NOT NULL DEFAULT 1 COMMENT '内置角色不允许删除',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`role_id`),
    UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='角色';

-- ---------------------------------------------------------------- 权限点
CREATE TABLE `permission`
(
    `permission_id` BIGINT      NOT NULL AUTO_INCREMENT,
    `perm_key`      VARCHAR(64) NOT NULL COMMENT '权限标识，如 attendance:rule',
    `perm_name`     VARCHAR(64) NOT NULL,
    `module`        VARCHAR(32) NOT NULL COMMENT '所属模块',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`permission_id`),
    UNIQUE KEY `uk_perm_key` (`perm_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='权限点';

-- ---------------------------------------------------------------- 角色-权限
CREATE TABLE `role_permission`
(
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `role_key`   VARCHAR(32) NOT NULL,
    `perm_key`   VARCHAR(64) NOT NULL,
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`    TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_key`, `perm_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='角色权限关联';

-- ---------------------------------------------------------------- 补卡申请
CREATE TABLE `makeup_request`
(
    `request_id`     BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT      NOT NULL COMMENT '申请人',
    `employee_no`    VARCHAR(32) NOT NULL COMMENT '申请人工号',
    `punch_time`     DATETIME    NOT NULL COMMENT '申请补录的打卡时间',
    `punch_type`     VARCHAR(16) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO/CHECK_IN/CHECK_OUT',
    `reason`         VARCHAR(255) NOT NULL COMMENT '补卡原因',
    `status`         VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected/canceled',
    `approver_id`    BIGINT               DEFAULT NULL,
    `approve_time`   DATETIME             DEFAULT NULL,
    `approve_remark` VARCHAR(255)         DEFAULT NULL,
    `record_id`      BIGINT               DEFAULT NULL COMMENT '通过后生成的 attendance_record.id',
    `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`request_id`),
    KEY `idx_mr_user` (`user_id`),
    KEY `idx_mr_status` (`status`),
    KEY `idx_mr_work` (`punch_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='补卡申请与审批';

-- ---------------------------------------------------------------- 既有表扩展
ALTER TABLE `user`
    ADD COLUMN `department_id` BIGINT DEFAULT NULL COMMENT '所属部门' AFTER `role`,
    ADD KEY `idx_user_dept` (`department_id`);

ALTER TABLE `team`
    ADD COLUMN `department_id` BIGINT DEFAULT NULL COMMENT '所属部门' AFTER `description`,
    ADD KEY `idx_team_dept` (`department_id`);

-- ---------------------------------------------------------------- 种子：部门
INSERT INTO `department` (`department_id`, `parent_id`, `dept_name`, `dept_code`, `sort_no`)
VALUES (1, NULL, '总经办', 'ROOT', 1),
       (2, 1, '研发部', 'RD', 2),
       (3, 1, '产品部', 'PD', 3),
       (4, 1, '人力资源部', 'HR', 4),
       (5, 1, '行政部', 'ADM', 5);

-- ---------------------------------------------------------------- 种子：角色
INSERT INTO `role` (`role_key`, `role_name`, `description`, `data_scope`)
VALUES ('sysadmin', '系统管理员', '账号、权限、设备与系统配置；不参与业务审批', 'ALL'),
       ('hr', '人事专员', '考勤规则、补卡审批、考勤报表与员工档案', 'ALL'),
       ('manager', '部门经理', '本部门团队、日程、看板、日志与考勤异常审批', 'DEPT'),
       ('leader', '团队负责人', '本部门内的团队协作与日程管理', 'DEPT'),
       ('employee', '普通员工', '自己的日程、看板、日志、考勤与补卡申请', 'SELF'),
       ('auditor', '审计专员', '全公司只读，用于合规核查', 'ALL');

-- ---------------------------------------------------------------- 种子：权限点
INSERT INTO `permission` (`perm_key`, `perm_name`, `module`)
VALUES ('user:view', '查看用户', 'user'),
       ('user:manage', '维护用户', 'user'),
       ('team:view', '查看团队', 'team'),
       ('team:manage', '维护团队', 'team'),
       ('schedule:view', '查看日程', 'schedule'),
       ('schedule:manage', '维护日程', 'schedule'),
       ('kanban:view', '查看看板', 'kanban'),
       ('kanban:manage', '维护看板', 'kanban'),
       ('worklog:view', '查看日志', 'worklog'),
       ('worklog:manage', '维护日志', 'worklog'),
       ('comment:view', '查看全部评论', 'comment'),
       ('comment:manage', '管理评论', 'comment'),
       ('sensitive:manage', '维护敏感词库', 'comment'),
       ('attendance:view', '查看考勤', 'attendance'),
       ('attendance:manage', '手工补卡', 'attendance'),
       ('attendance:rule', '维护考勤规则', 'attendance'),
       ('attendance:approve', '审批补卡', 'attendance'),
       ('device:view', '查看设备', 'attendance'),
       ('device:manage', '维护设备', 'attendance'),
       ('dashboard:company', '查看公司概览', 'dashboard'),
       ('makeup:submit', '提交补卡申请', 'attendance'),
       ('audit:view', '查看审计数据', 'system');

-- ---------------------------------------------------------------- 种子：角色-权限
-- 系统管理员：全部
INSERT INTO `role_permission` (`role_key`, `perm_key`)
SELECT 'sysadmin', `perm_key`
FROM `permission`;

-- 人事：用户只读 + 考勤全权 + 审批 + 公司概览
INSERT INTO `role_permission` (`role_key`, `perm_key`)
VALUES ('hr', 'user:view'),
       ('hr', 'team:view'),
       ('hr', 'worklog:view'),
       ('hr', 'comment:view'),
       ('hr', 'attendance:view'),
       ('hr', 'attendance:manage'),
       ('hr', 'attendance:rule'),
       ('hr', 'attendance:approve'),
       ('hr', 'makeup:submit'),
       ('hr', 'device:view'),
       ('hr', 'dashboard:company'),
       ('hr', 'audit:view');

-- 部门经理：本部门业务全权 + 审批（无公司概览、无用户维护）
INSERT INTO `role_permission` (`role_key`, `perm_key`)
VALUES ('manager', 'user:view'),
       ('manager', 'team:view'),
       ('manager', 'team:manage'),
       ('manager', 'schedule:view'),
       ('manager', 'schedule:manage'),
       ('manager', 'kanban:view'),
       ('manager', 'kanban:manage'),
       ('manager', 'worklog:view'),
       ('manager', 'comment:view'),
       ('manager', 'comment:manage'),
       ('manager', 'attendance:view'),
       ('manager', 'attendance:approve'),
       ('manager', 'makeup:submit'),
       ('manager', 'device:view');

-- 团队负责人：本部门协作 + 日程管理，不含审批
INSERT INTO `role_permission` (`role_key`, `perm_key`)
VALUES ('leader', 'team:view'),
       ('leader', 'schedule:view'),
       ('leader', 'schedule:manage'),
       ('leader', 'kanban:view'),
       ('leader', 'worklog:view'),
       ('leader', 'comment:view'),
       ('leader', 'attendance:view'),
       ('leader', 'makeup:submit');

-- 普通员工：只看自己的东西 + 提补卡申请
INSERT INTO `role_permission` (`role_key`, `perm_key`)
VALUES ('employee', 'team:view'),
       ('employee', 'schedule:view'),
       ('employee', 'kanban:view'),
       ('employee', 'worklog:view'),
       ('employee', 'attendance:view'),
       ('employee', 'makeup:submit');

-- 审计：所有 *:view 只读
INSERT INTO `role_permission` (`role_key`, `perm_key`)
SELECT 'auditor', `perm_key`
FROM `permission`
WHERE `perm_key` LIKE '%:view';

-- ---------------------------------------------------------------- 数据回填
-- 1) 旧角色值映射到新角色
UPDATE `user` SET `role` = 'sysadmin' WHERE `role` = 'admin';
UPDATE `user` SET `role` = 'employee' WHERE `role` IN ('member', 'manager') OR `role` IS NULL;

-- 2) 按人员分配部门与角色（演示数据，让每个角色都有真实的人）
UPDATE `user` SET `department_id` = 1, `role` = 'sysadmin' WHERE `username` = 'admin';
UPDATE `user` SET `department_id` = 2, `role` = 'manager'  WHERE `username` = 'zhangsan';
UPDATE `user` SET `department_id` = 2, `role` = 'employee' WHERE `username` = 'lisi';
UPDATE `user` SET `department_id` = 2, `role` = 'leader'   WHERE `username` = 'wangwu';
UPDATE `user` SET `department_id` = 1, `role` = 'auditor'  WHERE `username` = 'zhaoliu';
UPDATE `user` SET `department_id` = 4, `role` = 'hr'       WHERE `username` = 'sunqi';
UPDATE `user` SET `department_id` = 2, `role` = 'employee' WHERE `username` = 'zhouba';
UPDATE `user` SET `department_id` = 3, `role` = 'employee' WHERE `username` = 'wujiu';

-- 3) 兜底：仍未分配部门的人归到研发部
UPDATE `user` SET `department_id` = 2 WHERE `department_id` IS NULL;

-- 4) 部门负责人
UPDATE `department` SET `leader_id` = (SELECT `user_id` FROM `user` WHERE `username` = 'admin')    WHERE `department_id` = 1;
UPDATE `department` SET `leader_id` = (SELECT `user_id` FROM `user` WHERE `username` = 'zhangsan') WHERE `department_id` = 2;
UPDATE `department` SET `leader_id` = (SELECT `user_id` FROM `user` WHERE `username` = 'sunqi')    WHERE `department_id` = 4;

-- 5) 团队归属部门
UPDATE `team` SET `department_id` = 2 WHERE `department_id` IS NULL;
