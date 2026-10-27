-- =====================================================================
-- 额外 20 个演示账号（幂等，可重复执行）
--
-- 为什么要单独一个文件：gen_demo_data.py 的打卡数据必须用真实工号生成，
-- 而它自己也会插入这 20 个账号。若直接跑两遍（先建账号、再补打卡）会把
-- schedule/worklog 这类无唯一键的表插两遍。因此把「建账号」拆出来：
--   1) seed-demo.sql        8 个基础账号 + 初始数据
--   2) 本文件               20 个账号
--   3) gen_demo_data.py     一次性灌全量业务数据（用 28 人工号映射）
--
-- 密码统一 123456。
-- =====================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO `user` (username, password, nickname, email, phone, employee_no, role, department_id, status, deleted) VALUES
  ('chenjing', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '陈静', 'chenjing@teampulse.local', '13800001101', 'E1101', 'employee', 2, 'active', 0),
  ('liuyang', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '刘洋', 'liuyang@teampulse.local', '13800001102', 'E1102', 'leader', 2, 'active', 0),
  ('zhaomin', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '赵敏', 'zhaomin@teampulse.local', '13800001103', 'E1103', 'employee', 2, 'active', 0),
  ('sunhao', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '孙浩', 'sunhao@teampulse.local', '13800001104', 'E1104', 'employee', 2, 'active', 0),
  ('zhoujie', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '周杰', 'zhoujie@teampulse.local', '13800001105', 'E1105', 'employee', 2, 'active', 0),
  ('wuqian', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '吴倩', 'wuqian@teampulse.local', '13800001106', 'E1106', 'employee', 2, 'active', 0),
  ('zhengshuang', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '郑爽', 'zhengshuang@teampulse.local', '13800001107', 'E1107', 'manager', 3, 'active', 0),
  ('wangfang', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '王芳', 'wangfang@teampulse.local', '13800001108', 'E1108', 'employee', 3, 'active', 0),
  ('fenglei', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '冯磊', 'fenglei@teampulse.local', '13800001109', 'E1109', 'employee', 3, 'active', 0),
  ('chuyan', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '褚燕', 'chuyan@teampulse.local', '13800001110', 'E1110', 'leader', 3, 'active', 0),
  ('weiqing', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '卫青', 'weiqing@teampulse.local', '13800001111', 'E1111', 'employee', 4, 'active', 0),
  ('jiangwen', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '蒋雯', 'jiangwen@teampulse.local', '13800001112', 'E1112', 'employee', 4, 'active', 0),
  ('shenyu', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '沈玉', 'shenyu@teampulse.local', '13800001113', 'E1113', 'manager', 5, 'active', 0),
  ('hanyu', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '韩雨', 'hanyu@teampulse.local', '13800001114', 'E1114', 'employee', 5, 'active', 0),
  ('yangbo', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '杨波', 'yangbo@teampulse.local', '13800001115', 'E1115', 'employee', 5, 'active', 0),
  ('xufeng', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '徐枫', 'xufeng@teampulse.local', '13800001116', 'E1116', 'employee', 2, 'active', 0),
  ('hexin', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '何欣', 'hexin@teampulse.local', '13800001117', 'E1117', 'employee', 2, 'active', 0),
  ('gaoyuan', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '高远', 'gaoyuan@teampulse.local', '13800001118', 'E1118', 'employee', 3, 'active', 0),
  ('linna', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '林娜', 'linna@teampulse.local', '13800001119', 'E1119', 'employee', 4, 'active', 0),
  ('machao', '$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK', '马超', 'machao@teampulse.local', '13800001120', 'E1120', 'employee', 5, 'active', 0);

-- 校验：应得到 28 个账号
-- SELECT COUNT(*) AS users FROM `user` WHERE deleted = 0;
