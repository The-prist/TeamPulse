-- =====================================================================
-- TeamPulse V7：把历史遗留的角色标识归一到 RBAC 的 6 个角色 key
--
-- 背景：改造前 user.role 只有 admin / member 两个值；RBAC 上线后
-- 只认 role 表里的 key（sysadmin/hr/manager/leader/employee/auditor）。
-- 若库里还留着旧值，该用户查不到角色 → 权限为 0 → 登录后几乎全部接口 403。
--
-- 本迁移是幂等的：只改仍然等于旧值的行，重复执行无副作用。
-- =====================================================================
UPDATE `user` SET `role` = 'sysadmin' WHERE `role` = 'admin';
UPDATE `user` SET `role` = 'employee' WHERE `role` = 'member';
-- 空角色也归到最小权限，避免登录后权限表为空导致前端菜单空白
UPDATE `user` SET `role` = 'employee' WHERE `role` IS NULL OR `role` = '';