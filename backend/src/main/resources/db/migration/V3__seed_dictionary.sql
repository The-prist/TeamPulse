-- =====================================================================
-- TeamPulse V3：基础字典数据
-- 说明：管理员账号不在此处插入——密码需要 BCrypt 动态生成，
--       改由 BootstrapDataInitializer 在首次启动时创建（见 backend/README）。
-- =====================================================================

INSERT INTO `sensitive_word` (`word`, `replacement`, `status`)
VALUES ('违禁词', '***', 'active'),
       ('不当言论', '***', 'active'),
       ('垃圾信息', '***', 'active'),
       ('广告', '***', 'active'),
       ('刷屏', '***', 'active')
ON DUPLICATE KEY UPDATE `replacement` = VALUES(`replacement`);

INSERT INTO `attendance_rule` (`rule_name`, `work_start`, `work_end`,
                               `late_grace_minutes`, `early_grace_minutes`, `work_days`, `enabled`)
VALUES ('标准工作制（9:00-18:00）', '09:00:00', '18:00:00', 5, 5, '1,2,3,4,5', 1)
ON DUPLICATE KEY UPDATE `work_start` = VALUES(`work_start`);
