-- =====================================================================
-- TeamPulse V8：为「打卡记录列表」补一个覆盖日期过滤 + 时间排序的复合索引
--
-- 背景：attendance_record 原有索引：
--   idx_record_date_status (work_date, status)
--   idx_record_user_date   (user_id, work_date)
--   idx_record_device      (device_sn, punch_time)
--   uk_record_emp_time     (employee_no, punch_time)
-- 而打卡记录页的主查询是：
--   WHERE work_date BETWEEN ? AND ? ORDER BY punch_time DESC LIMIT ?
-- 用 (work_date, status) 只能过滤日期，排序仍需 filesort（5 万行时 p95 ≈ 225ms）。
-- 加 (work_date, punch_time) 后，日期过滤与倒序排列都走索引，避免额外排序。
--
-- 压测对比数据见 docs/性能测试报告.md。
-- =====================================================================
ALTER TABLE `attendance_record`
    ADD INDEX `idx_record_date_punch` (`work_date`, `punch_time`);