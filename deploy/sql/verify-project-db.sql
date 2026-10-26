-- =====================================================================
-- TeamPulse 数据库自检脚本
-- 在 DBeaver 中连上 teampulse 库后，整段执行（Alt+X），确认表和初始数据都在
-- =====================================================================

-- 0. 版本与当前库
SELECT VERSION() AS mysql_version, DATABASE() AS current_db;

-- 1. 建表是否成功：应返回 15 行（14 张业务表 + flyway_schema_history）
SELECT TABLE_NAME        AS 表名,
       ENGINE            AS 引擎,
       TABLE_COLLATION   AS 排序规则,
       TABLE_COMMENT     AS 说明
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'teampulse'
ORDER BY TABLE_NAME;

-- 2. Flyway 迁移记录：应有 3 行，且 success 全为 1
SELECT installed_rank AS 序号,
       version        AS 版本,
       description    AS 描述,
       success        AS 成功,
       installed_on   AS 执行时间
FROM flyway_schema_history
ORDER BY installed_rank;

-- 3. 初始数据是否已写入
SELECT 'user（应 1 行：admin）'            AS 检查项, COUNT(*) AS 行数 FROM `user`
UNION ALL SELECT 'iot_device（应 1 行：演示设备）', COUNT(*) FROM iot_device
UNION ALL SELECT 'attendance_rule（应 1 行：默认规则）', COUNT(*) FROM attendance_rule
UNION ALL SELECT 'sensitive_word（应 5 行）', COUNT(*) FROM sensitive_word;

-- 4. 看具体内容
SELECT user_id, username, nickname, employee_no, role, status, created_at FROM `user`;
SELECT id, device_sn, device_name, location, status, last_heartbeat FROM iot_device;
SELECT id, rule_name, work_start, work_end, late_grace_minutes, early_grace_minutes, work_days FROM attendance_rule;
SELECT word_id, word, replacement, status FROM sensitive_word;

-- 5. 校验中文是否正常（不应出现乱码；若乱码请检查 DBeaver 连接属性里的字符集设置）
SELECT '中文校验：系统管理员 / 标准工作制（9:00-18:00）' AS 中文测试;

-- 6. 打卡链路的两张核心表（当前应为空，跑一次模拟器后会有数据）
SELECT COUNT(*) AS 打卡明细行数 FROM attendance_record;
SELECT COUNT(*) AS 日汇总行数 FROM attendance_daily;

-- 7. 每张表的实际行数一览（排查时用）
SELECT 'user' AS 表, COUNT(*) AS 行数 FROM `user`
UNION ALL SELECT 'team', COUNT(*) FROM team
UNION ALL SELECT 'team_member', COUNT(*) FROM team_member
UNION ALL SELECT 'schedule', COUNT(*) FROM schedule
UNION ALL SELECT 'worklog', COUNT(*) FROM worklog
UNION ALL SELECT 'kanban', COUNT(*) FROM kanban
UNION ALL SELECT 'kanban_column', COUNT(*) FROM kanban_column
UNION ALL SELECT 'kanban_item', COUNT(*) FROM kanban_item
UNION ALL SELECT 'comment', COUNT(*) FROM comment
UNION ALL SELECT 'sensitive_word', COUNT(*) FROM sensitive_word
UNION ALL SELECT 'iot_device', COUNT(*) FROM iot_device
UNION ALL SELECT 'attendance_rule', COUNT(*) FROM attendance_rule
UNION ALL SELECT 'attendance_record', COUNT(*) FROM attendance_record
UNION ALL SELECT 'attendance_daily', COUNT(*) FROM attendance_daily;
