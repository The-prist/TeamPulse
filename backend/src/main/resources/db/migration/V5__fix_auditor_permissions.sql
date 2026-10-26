-- =====================================================================
-- TeamPulse V5：补齐审计角色的公司概览权限
--
-- 背景：V4 里审计角色是按「所有 *:view 权限」批量授权的，
-- 但 dashboard:company 的命名不是 *:view 结尾，导致审计看不到公司概览。
-- 审计的核心工作恰恰是看数据，这里补上。
-- =====================================================================
INSERT INTO `role_permission` (`role_key`, `perm_key`)
SELECT 'auditor', 'dashboard:company'
WHERE NOT EXISTS(
    SELECT 1 FROM `role_permission`
    WHERE `role_key` = 'auditor' AND `perm_key` = 'dashboard:company'
);
