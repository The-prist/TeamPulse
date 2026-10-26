-- =====================================================================
-- 压测用的合成数据（**仅本地/测试环境**，不要在生产执行）
--
-- 目的：让「索引与慢查询」这类对比有意义。演示库里只有几十行，
-- 任何索引都能在 1ms 内跑完，测不出差异。本脚本灌 5 万条打卡记录。
--
-- 用法：
--   docker exec -i tp-mysql mysql -uroot -p<密码> teampulse < tests/perf/seed-perf-data.sql
--   # 清理：
--   docker exec tp-mysql mysql -uroot -p<密码> teampulse -e "DELETE FROM attendance_record WHERE employee_no LIKE 'P%'"
-- =====================================================================
SET SESSION cte_max_recursion_depth = 60000;

INSERT IGNORE INTO attendance_record
  (user_id, employee_no, device_sn, punch_type, punch_time, work_date, status,
   late_minutes, early_leave_minutes, source, message_id, sign_verified, created_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 50000
)
SELECT
  1 + (n % 8),
  CONCAT('P', LPAD(n % 8 + 1, 3, '0')),
  'TP-ESP32-DEMO',
  IF(n % 2 = 0, 'CHECK_IN', 'CHECK_OUT'),
  TIMESTAMP('2025-01-01 00:00:00') + INTERVAL (n * 7) MINUTE,
  DATE(TIMESTAMP('2025-01-01 00:00:00') + INTERVAL (n * 7) MINUTE),
  ELT(1 + (n % 4), 'NORMAL', 'LATE', 'NORMAL', 'EARLY_LEAVE'),
  IF(n % 4 = 2, 12, 0),
  IF(n % 4 = 0, 18, 0),
  'DEVICE',
  CONCAT('perf-', LPAD(n, 8, '0'), '-', SUBSTRING(MD5(n), 1, 12)),
  1,
  NOW()
FROM seq;