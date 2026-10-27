#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成「充实演示数据」的 SQL（幂等，可重复执行）。

设计原则
  1. 只用 INSERT IGNORE / ON DUPLICATE KEY UPDATE，重复执行不会报错也不会重复插入；
  2. 唯一键严格对齐：user(username/employee_no/email)、attendance_record(employee_no+punch_time, message_id)、
     attendance_daily(user_id,work_date)、team_member(team_id,user_id)、sensitive_word(word)；
  3. 日汇总不手写，由原始打卡记录聚合生成（保证与记录一致）；
  4. 顺手修两个既有数据问题：zhouba/wujiu 的密码是明文、team.creator_id 可能为空。

用法：
  python scripts/docs/gen_demo_data.py --out .run-logs/seed-extra.sql
"""
import argparse
import os
import random
from datetime import date, datetime, timedelta

# 复用 zhangsan 的散列（密码 123456），保证新账号都能登录
HASH_123456 = "$2a$10$KjPkarmCwQOJEw0y1FHcde.Ze9E1ktNzhQqJP1u6nEvI5Oy8aIkBK"

DEPTS = {1: "总经办", 2: "研发部", 3: "产品部", 4: "人力资源部", 5: "行政部"}

NEW_USERS = [
    # username, nickname, dept, role, employee_no, phone
    ("chenjing", "陈静", 2, "employee", "E1101", "13800001101"),
    ("liuyang", "刘洋", 2, "leader", "E1102", "13800001102"),
    ("zhaomin", "赵敏", 2, "employee", "E1103", "13800001103"),
    ("sunhao", "孙浩", 2, "employee", "E1104", "13800001104"),
    ("zhoujie", "周杰", 2, "employee", "E1105", "13800001105"),
    ("wuqian", "吴倩", 2, "employee", "E1106", "13800001106"),
    ("zhengshuang", "郑爽", 3, "manager", "E1107", "13800001107"),
    ("wangfang", "王芳", 3, "employee", "E1108", "13800001108"),
    ("fenglei", "冯磊", 3, "employee", "E1109", "13800001109"),
    ("chuyan", "褚燕", 3, "leader", "E1110", "13800001110"),
    ("weiqing", "卫青", 4, "employee", "E1111", "13800001111"),
    ("jiangwen", "蒋雯", 4, "employee", "E1112", "13800001112"),
    ("shenyu", "沈玉", 5, "manager", "E1113", "13800001113"),
    ("hanyu", "韩雨", 5, "employee", "E1114", "13800001114"),
    ("yangbo", "杨波", 5, "employee", "E1115", "13800001115"),
    ("xufeng", "徐枫", 2, "employee", "E1116", "13800001116"),
    ("hexin", "何欣", 2, "employee", "E1117", "13800001117"),
    ("gaoyuan", "高远", 3, "employee", "E1118", "13800001118"),
    ("linna", "林娜", 4, "employee", "E1119", "13800001119"),
    ("machao", "马超", 5, "employee", "E1120", "13800001120"),
]

NEW_TEAMS = [
    ("研发二组", 2, "负责考勤与物联网接入的迭代开发", 2),
    ("产品设计组", 3, "需求分析与原型设计", 7),
    ("人事行政组", 4, "人事流程与行政事务", 6),
    ("数据平台组", 2, "数据看板与报表", 3),
]

SCHEDULE_TITLES = [
    ("每日站会", "阻塞项同步", "todo", "medium"),
    ("需求评审会", "评审下一迭代需求范围", "todo", "high"),
    ("迭代计划会", "拆任务、估点", "done", "medium"),
    ("接口联调", "后端与前端对齐字段", "doing", "high"),
    ("设备联调", "ESP32 上报链路排查", "doing", "high"),
    ("代码评审", "本周合并请求走查", "done", "low"),
    ("版本发布", "灰度发布与回滚预案", "todo", "high"),
    ("客户演示", "向业务方演示考勤看板", "todo", "medium"),
    ("数据核对", "打卡记录与日汇总一致性校验", "done", "medium"),
    ("故障复盘", "上周 MQTT 断连复盘", "done", "low"),
    ("月度总结", "汇总本月迭代产出", "todo", "low"),
    ("安全自查", "权限矩阵与审计日志复核", "doing", "high"),
]

WORKLOG_TITLES = [
    ("完成打卡验签与幂等", "补齐了 nonce 去重与唯一索引兜底，重复报文返回 200 不新增记录。"),
    ("修复权限越权问题", "isAdmin 语义漂移导致人事可删他人数据，改为按 *:manage 权限点判断。"),
    ("优化打卡记录查询", "新增 (work_date, punch_time) 组合索引，SQL 层耗时降到 0.6ms。"),
    ("补齐前端失败态", "17 个列表页加失败告警条与重试按钮，统一网络错误提示。"),
    ("接入 Prometheus", "暴露 /actuator/prometheus，配好 Grafana 看板与阈值。"),
    ("排查设备离线", "心跳 180s 未上报判定离线，原因是安全组未放行 1883。"),
    ("补卡审批流联调", "审批通过后回写打卡记录并刷新日汇总，重复审批返回 409。"),
    ("整理接口文档", "从 /v3/api-docs 导出 92 个操作，补齐错误码与 curl 示例。"),
    ("部署到云服务器", "腾讯云 4C4G 一次部署成功，构建 295s、5 容器 healthy。"),
    ("编写运维手册", "补日周月巡检清单、故障 SOP 与容量规划。"),
]

KANBAN_ITEMS = [
    ("打卡上报幂等改造", "high"), ("权限矩阵回归用例", "high"), ("设备注册页交互优化", "medium"),
    ("日报导出 CSV", "low"), ("看板拖拽排序", "medium"), ("审计日志筛选", "medium"),
    ("补卡审批批量操作", "low"), ("监控告警接入", "high"), ("部署脚本幂等化", "medium"),
    ("数据库慢查询治理", "high"), ("敏感词词库更新", "low"), ("移动端适配初探", "low"),
]

COMMENTS = [
    "这个方案我同意，注意兼容历史数据。", "麻烦补充一下边界情况：跨天打卡怎么算？",
    "已按评审意见修改，见最新提交。", "这里的错误码建议统一到 6xxxx 段。",
    "设备时钟偏差要卡在 300 秒内，否则现场容易出问题。", "收到，今天下班前给结果。",
    "建议加个索引，5 万行以后会明显变慢。", "日志里带上 traceId 更好排查。",
    "这个页面在 1366 分辨率下有点挤。", "已联调通过，可以合并。",
]

SENSITIVE_WORDS = ["加粗", "代打卡", "刷单", "内推费", "私单", "泄露", "外挂", "作弊",
                   "翻墙", "水军", "黑产", "洗钱", "套现", "传销", "返利", "刷屏",
                   "拉黑", "对骂", "人身攻击", "抄袭"]

DEVICES = [
    ("TP-ESP32-DEV04", "产品部打卡机", "五楼产品区", "OFFLINE", "1.0.0"),
    ("TP-ESP32-DEV05", "人事部打卡机", "四楼人事区", "OFFLINE", "1.0.2"),
    ("TP-ESP32-DEV06", "研发二区打卡机", "三楼东侧", "OFFLINE", "1.0.2"),
]

AUDIT_SAMPLES = [
    ("user", "user.create", "POST", "/api/v1/user", "SUCCESS"),
    ("user", "user.update", "PUT", "/api/v1/user/9", "SUCCESS"),
    ("team", "team.member.add", "POST", "/api/v1/team/3/members", "SUCCESS"),
    ("schedule", "schedule.create", "POST", "/api/v1/schedule", "SUCCESS"),
    ("schedule", "schedule.delete", "DELETE", "/api/v1/schedule/31", "SUCCESS"),
    ("comment", "comment.delete", "DELETE", "/api/v1/comment/12", "FAILURE"),
    ("device", "device.register", "POST", "/api/v1/devices", "SUCCESS"),
    ("device", "device.rotate_secret", "PUT", "/api/v1/devices/2/secret", "SUCCESS"),
    ("attendance", "attendance.rule.update", "PUT", "/api/v1/attendance/rule", "SUCCESS"),
    ("attendance", "makeup.approve", "POST", "/api/v1/makeup/3/review", "SUCCESS"),
    ("attendance", "makeup.reject", "POST", "/api/v1/makeup/5/review", "SUCCESS"),
    ("worklog", "worklog.create", "POST", "/api/v1/worklog", "SUCCESS"),
    ("sensitive", "sensitive.create", "POST", "/api/v1/sensitive-word", "SUCCESS"),
    ("user", "user.reset_password", "PUT", "/api/v1/user/12/password", "SUCCESS"),
    ("audit", "audit.query", "GET", "/api/v1/audit", "SUCCESS"),
    ("attendance", "attendance.manual", "POST", "/api/v1/attendance/manual", "SUCCESS"),
]


def sql_escape(s: str) -> str:
    return s.replace("\\", "\\\\").replace("'", "''")


def dt(d: datetime) -> str:
    return d.strftime("%Y-%m-%d %H:%M:%S")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--start", default="2026-08-01", help="打卡数据起始日期")
    ap.add_argument("--end", default="2026-10-27", help="打卡数据结束日期")
    ap.add_argument("--seed", type=int, default=20261027)
    ap.add_argument("--users", default="", help="用户工号映射文件（user_id|employee_no|role|dept|username|nickname）")
    args = ap.parse_args()

    rnd = random.Random(args.seed)

    # 真实工号映射（避免「按 user_id 推算工号」与实际数据不一致）
    EMP = {}      # user_id -> employee_no
    ROLE = {}     # user_id -> role
    if args.users and os.path.exists(args.users):
        for line in open(args.users, encoding="utf-8"):
            parts = line.strip().split("|")
            if len(parts) < 4 or not parts[0].isdigit():
                continue
            uid = int(parts[0])
            if parts[1] in ("", "NONE"):
                continue
            EMP[uid] = parts[1]
            ROLE[uid] = parts[2]
        print(f"  读取到 {len(EMP)} 个真实工号")
    out = []
    out.append("-- 自动生成：充实演示数据（幂等，可重复执行）")
    out.append("SET NAMES utf8mb4;")
    out.append("SET FOREIGN_KEY_CHECKS = 0;")
    out.append("START TRANSACTION;")

    # ---------------------------------------------------------- 1 修历史问题
    out.append("\n-- 1. 修复明文密码（zhouba/wujiu 存的是 123456 明文，无法登录）")
    out.append(f"UPDATE `user` SET password = '{HASH_123456}' "
               "WHERE password NOT LIKE '$2%' AND deleted = 0;")

    # ---------------------------------------------------------- 2 用户
    out.append("\n-- 2. 新增 20 个员工（覆盖 4 个部门，含 2 个经理 2 个负责人）")
    for uname, nick, dept, role, emp, phone in NEW_USERS:
        out.append(
            "INSERT IGNORE INTO `user` (username, password, nickname, email, phone, employee_no, role, department_id, status) "
            f"VALUES ('{uname}', '{HASH_123456}', '{nick}', '{uname}@teampulse.local', '{phone}', "
            f"'{emp}', '{role}', {dept}, 'active');")

    # ---------------------------------------------------------- 3 团队
    out.append("\n-- 3. 新增 4 个团队（creator_id 必填；负责人通过 team_member.team_role 表达）")
    for name, dept, desc, creator in NEW_TEAMS:
        out.append(
            "INSERT IGNORE INTO team (team_name, description, department_id, creator_id) "
            f"VALUES ('{name}', '{desc}', {dept}, {creator});")

    out.append("\n-- 3.1 修既有团队的 creator_id 为空（NOT NULL 语义要求）")
    out.append("UPDATE team SET creator_id = 2 WHERE creator_id IS NULL OR creator_id = 0;")

    out.append("\n-- 3.2 团队成员：按部门把新员工编入团队，并保证每个团队有 leader")
    out.append("""
INSERT IGNORE INTO team_member (team_id, user_id, team_role)
SELECT t.team_id, u.user_id,
       CASE WHEN u.role IN ('leader','manager') THEN 'leader' ELSE 'member' END
FROM team t
JOIN `user` u ON u.department_id = t.department_id AND u.deleted = 0
WHERE t.deleted = 0;""")
    out.append("""
-- 每个团队至少一个 leader（若上面没选出来，取部门内第一个负责人/经理）
INSERT IGNORE INTO team_member (team_id, user_id, team_role)
SELECT t.team_id,
       (SELECT u.user_id FROM `user` u WHERE u.department_id = t.department_id
          AND u.role IN ('leader','manager') AND u.deleted = 0 ORDER BY u.user_id LIMIT 1),
       'leader'
FROM team t
WHERE t.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM team_member m WHERE m.team_id = t.team_id AND m.team_role = 'leader')
  AND EXISTS (SELECT 1 FROM `user` u2 WHERE u2.department_id = t.department_id AND u2.role IN ('leader','manager'));""")

    # ---------------------------------------------------------- 4 日程
    out.append("\n-- 4. 日程：过去 45 天到未来 21 天，按用户铺开")
    base = datetime.strptime(args.end, "%Y-%m-%d")
    sched_rows = []
    for i in range(-45, 22):
        day = base + timedelta(days=i)
        if day.weekday() >= 5:  # 周末少排
            if rnd.random() > 0.15:
                continue
        for uid in range(2, 29):
            if rnd.random() > 0.32:
                continue
            title, content, status, prio = SCHEDULE_TITLES[rnd.randrange(len(SCHEDULE_TITLES))]
            hour = rnd.choice([9, 10, 11, 14, 15, 16, 17])
            start = day.replace(hour=hour, minute=rnd.choice([0, 15, 30, 45]))
            end = start + timedelta(minutes=rnd.choice([30, 45, 60, 90]))
            team = rnd.choice([1, 2, 3, 4, 5, 6, None])
            team_sql = "NULL" if team is None else str(team)
            sched_rows.append(
                "('" + sql_escape(title) + "', '" + sql_escape(content) + "', "
                f"'{dt(start)}', '{dt(end)}', '{rnd.choice(['线上','3F 会议室','4F 大会议室','客户现场'])}', "
                f"'{status}', '{prio}', {uid}, {team_sql})")
    for i in range(0, len(sched_rows), 200):
        chunk = ",\n".join(sched_rows[i:i + 200])
        out.append("INSERT IGNORE INTO schedule (title, content, start_time, end_time, location, status, priority, creator_id, team_id) VALUES\n" + chunk + ";")

    # ---------------------------------------------------------- 5 工作日志
    out.append("\n-- 5. 工作日志：近 60 天，每天约 3~6 人提交")
    log_rows = []
    for i in range(-60, 1):
        day = base + timedelta(days=i)
        if day.weekday() >= 5:
            continue
        for uid in rnd.sample(range(2, 29), rnd.randint(3, 6)):
            title, content = WORKLOG_TITLES[rnd.randrange(len(WORKLOG_TITLES))]
            log_rows.append(
                f"('{sql_escape(title)}', '{sql_escape(content)}', '{day.date()}', {uid}, "
                f"{rnd.choice([None, 1, 2, 3]) or 'NULL'}, {rnd.choice([1, 2, 3, 4, 5, 6, None]) or 'NULL'})".replace("None", "NULL"))
    for i in range(0, len(log_rows), 200):
        chunk = ",\n".join(str(r).replace("NULL, NULL", "NULL, NULL") for r in log_rows[i:i + 200])
        out.append("INSERT IGNORE INTO worklog (title, content, log_date, creator_id, related_schedule_id, related_team_id) VALUES\n" + chunk + ";")

    # ---------------------------------------------------------- 6 看板与卡片
    out.append("\n-- 6. 看板：每个新团队一块板，三列，卡片挂到已有日程上")
    out.append("""
INSERT IGNORE INTO kanban (kanban_name, description, team_id, creator_id)
SELECT CONCAT(t.team_name, ' 迭代看板'), '自动生成的迭代看板', t.team_id, t.creator_id
FROM team t WHERE t.deleted = 0 AND t.team_id >= 3;""")
    out.append("""
INSERT IGNORE INTO kanban_column (kanban_id, column_name, sort_order)
SELECT k.kanban_id, c.name, c.sort
FROM kanban k
JOIN (SELECT '待办' AS name, 1 AS sort UNION ALL SELECT '进行中', 2 UNION ALL SELECT '已完成', 3) c
WHERE k.deleted = 0;""")
    out.append("""
INSERT IGNORE INTO kanban_item (kanban_id, schedule_id, column_id, assignee_id, sort_order)
SELECT c.kanban_id,
       (SELECT s.schedule_id FROM schedule s WHERE s.deleted = 0 ORDER BY s.schedule_id LIMIT 1 OFFSET 0),
       c.column_id,
       (SELECT u.user_id FROM `user` u WHERE u.status='active' ORDER BY u.user_id LIMIT 1),
       c.sort_order
FROM kanban_column c
WHERE c.sort_order = 1
LIMIT 0;""")  # 占位：下面用真实卡片替换

    # 每块板生成 8 张卡片（关联已有日程，assignee 随机）
    out.append("""
-- 6.1 为每块看板的每一列生成若干卡片（schedule_id 取已有日程，保证非空）
INSERT IGNORE INTO kanban_item (kanban_id, schedule_id, column_id, assignee_id, sort_order)
SELECT c.kanban_id,
       (SELECT s.schedule_id FROM schedule s WHERE s.deleted = 0 ORDER BY s.schedule_id LIMIT 1),
       c.column_id,
       2 + (c.column_id % 20),
       c.sort_order * 10 + n.n
FROM kanban_column c
JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3) n
WHERE c.sort_order <= 3;""")

    # ---------------------------------------------------------- 7 评论
    out.append("\n-- 7. 评论：挂到日程与日志上，含少量回复")
    out.append("""
INSERT IGNORE INTO comment (related_type, related_id, creator_id, content, original_content)
SELECT 'schedule', s.schedule_id, 2 + (s.schedule_id % 25),
       ELT(1 + (s.schedule_id % 10),
           '这个方案我同意，注意兼容历史数据。',
           '麻烦补充跨天打卡的边界情况。',
           '已按评审意见修改，见最新提交。',
           '错误码建议统一到 6xxxx 段。',
           '设备时钟要卡在 300 秒内。',
           '收到，今天下班前给结果。',
           '建议加索引，5 万行后会变慢。',
           '日志带上 traceId 更好排查。',
           '1366 分辨率下这个页面有点挤。',
           '已联调通过，可以合并。'),
       NULL
FROM schedule s WHERE s.deleted = 0 AND (s.schedule_id % 3) = 0;""")
    out.append("""
INSERT IGNORE INTO comment (related_type, related_id, creator_id, content, original_content)
SELECT 'worklog', w.worklog_id, 3 + (w.worklog_id % 24),
       '日志写得清楚，建议把风险点也记一下。', NULL
FROM worklog w WHERE w.deleted = 0 AND (w.worklog_id % 5) = 0;""")
    out.append("""
-- 回复：给部分评论挂子回复
INSERT IGNORE INTO comment (related_type, related_id, creator_id, content, original_content, reply_to)
SELECT c.related_type, c.related_id, 2, '已处理，见最新提交。', NULL, c.comment_id
FROM comment c
WHERE c.reply_to IS NULL AND c.deleted = 0 AND (c.comment_id % 7) = 0;""")

    # ---------------------------------------------------------- 8 敏感词
    out.append("\n-- 8. 敏感词 20 个")
    out.append("INSERT IGNORE INTO sensitive_word (word, replacement, status, creator_id) VALUES\n"
               + ",\n".join(f"('{w}', '***', 'active', 1)" for w in SENSITIVE_WORDS) + ";")

    # ---------------------------------------------------------- 9 设备
    out.append("\n-- 9. 新增 3 台设备（密钥随机 64 位十六进制，仅演示用）")
    for sn, name, loc, status, fw in DEVICES:
        secret = "".join(rnd.choice("0123456789abcdef") for _ in range(64))
        out.append("INSERT IGNORE INTO iot_device (device_sn, device_name, location, secret, status, firmware_version) "
                   f"VALUES ('{sn}', '{name}', '{loc}', '{secret}', '{status}', '{fw}');")

    # ---------------------------------------------------------- 10 打卡记录
    out.append("\n-- 10. 打卡记录：区间内的工作日，每人在岗打卡（含迟到/早退/缺卡）")
    start = datetime.strptime(args.start, "%Y-%m-%d")
    records = []
    day = start
    while day <= base:
        if day.weekday() < 5:  # 只在工作日
            for uid in sorted(EMP):
                if ROLE.get(uid) == "sysadmin":   # 管理员不做打卡数据，避免演示时奇怪
                    continue
                emp = EMP[uid]
                # 缺勤 3%
                if rnd.random() < 0.03:
                    continue
                # 上班：08:40 ~ 09:25
                # 08:40~08:55 早到 / 08:56~09:05 正常 / 09:06~09:25 迟到
                in_min = rnd.choices([rnd.randint(520, 535), rnd.randint(536, 545), rnd.randint(546, 565)],
                                     weights=[6, 2, 2])[0]
                in_at = day.replace(hour=in_min // 60, minute=in_min % 60, second=rnd.randint(0, 59))
                # 下班：17:20~17:59 早退 / 18:00~18:19 正常 / 18:20~19:10 加班
                out_min = rnd.choices([rnd.randint(1040, 1079), rnd.randint(1080, 1099), rnd.randint(1100, 1150)],
                                      weights=[1, 6, 3])[0]
                out_at = day.replace(hour=out_min // 60, minute=out_min % 60, second=rnd.randint(0, 59))
                for punch_at, ptype in ((in_at, "CHECK_IN"), (out_at, "CHECK_OUT")):
                    msg = f"seed-{uid}-{punch_at.strftime('%Y%m%d%H%M%S')}-{ptype[:2]}"
                    records.append(
                        f"({uid}, '{emp}', 'TP-ESP32-DEMO', '{ptype}', '{dt(punch_at)}', "
                        f"'{punch_at.date()}', 'NORMAL', 0, 0, 'DEVICE', '{msg}', 1)")
        day += timedelta(days=1)

    for i in range(0, len(records), 400):
        out.append("INSERT IGNORE INTO attendance_record\n"
                   "  (user_id, employee_no, device_sn, punch_type, punch_time, work_date, status, "
                   "late_minutes, early_leave_minutes, source, message_id, sign_verified) VALUES\n"
                   + ",\n".join(records[i:i + 400]) + ";")

    # ---------------------------------------------------------- 11 日汇总（由记录聚合）
    out.append("""
-- 11. 日汇总：由打卡记录聚合（只有单边打卡时用 COALESCE 兜底，避免 NULL 违反 NOT NULL）
INSERT INTO attendance_daily
  (user_id, employee_no, work_date, first_check_in, last_check_out, work_minutes,
   late_minutes, early_leave_minutes, attendance_status)
SELECT t.user_id,
       t.employee_no,
       t.work_date,
       t.ci,
       t.co,
       COALESCE(TIMESTAMPDIFF(MINUTE, t.ci, t.co), 0),
       COALESCE(GREATEST(0, TIMESTAMPDIFF(MINUTE, TIMESTAMP(t.work_date, '09:05:00'), t.ci)), 0),
       COALESCE(GREATEST(0, TIMESTAMPDIFF(MINUTE, t.co, TIMESTAMP(t.work_date, '18:00:00'))), 0),
       CASE
         WHEN t.ci IS NOT NULL AND TIMESTAMPDIFF(MINUTE, TIMESTAMP(t.work_date, '09:05:00'), t.ci) > 0 THEN 'LATE'
         WHEN t.co IS NOT NULL AND TIMESTAMPDIFF(MINUTE, t.co, TIMESTAMP(t.work_date, '18:00:00')) > 0 THEN 'EARLY_LEAVE'
         ELSE 'NORMAL'
       END
FROM (
  SELECT r.user_id,
         MIN(r.employee_no) AS employee_no,
         r.work_date,
         MIN(CASE WHEN r.punch_type = 'CHECK_IN' THEN r.punch_time END) AS ci,
         MAX(CASE WHEN r.punch_type = 'CHECK_OUT' THEN r.punch_time END) AS co
  FROM attendance_record r
  WHERE r.work_date BETWEEN '{start}' AND '{end}'
  GROUP BY r.user_id, r.work_date
) t
ON DUPLICATE KEY UPDATE
  first_check_in = VALUES(first_check_in),
  last_check_out = VALUES(last_check_out),
  work_minutes = VALUES(work_minutes),
  late_minutes = VALUES(late_minutes),
  early_leave_minutes = VALUES(early_leave_minutes),
  attendance_status = VALUES(attendance_status);""".replace("{start}", args.start).replace("{end}", args.end))

    # ---------------------------------------------------------- 12 补卡申请
    out.append("\n-- 12. 补卡申请：待审批/已通过/已驳回三种状态")
    out.append("""
INSERT IGNORE INTO makeup_request
  (user_id, employee_no, punch_time, punch_type, reason, status, approver_id, approve_time, approve_remark)
SELECT u.user_id,
       u.employee_no,
       TIMESTAMP(DATE_SUB('{end}', INTERVAL (u.user_id % 12) DAY), '09:00:00'),
       'CHECK_IN',
       '外出拜访客户，忘记打卡',
       ELT(1 + (u.user_id % 3), 'pending', 'approved', 'rejected'),
       CASE WHEN (u.user_id % 3) = 1 THEN NULL ELSE 2 END,
       CASE WHEN (u.user_id % 3) = 1 THEN NULL ELSE TIMESTAMP(DATE_SUB('{end}', INTERVAL (u.user_id % 12) DAY), '18:30:00') END,
       CASE WHEN (u.user_id % 3) = 1 THEN NULL
            WHEN (u.user_id % 3) = 2 THEN '已核实，通过'
            ELSE '缺少外出审批单，驳回' END
FROM `user` u
WHERE u.deleted = 0 AND u.user_id BETWEEN 2 AND 15;""".replace("{end}", args.end))

    # ---------------------------------------------------------- 13 审计日志
    out.append("\n-- 13. 审计日志：覆盖成功/失败与多个模块")
    audit_rows = []
    for i in range(120):
        module, action, method, path, result = AUDIT_SAMPLES[rnd.randrange(len(AUDIT_SAMPLES))]
        actor = rnd.randint(1, 20)
        ts = base - timedelta(days=rnd.randint(0, 45),
                              hours=rnd.randint(0, 9), minutes=rnd.randint(0, 59))
        trace = "".join(rnd.choice("0123456789abcdef") for _ in range(16))
        cost = rnd.randint(3, 320)
        err = "NULL" if result == "SUCCESS" else "'没有该操作的权限'"
        # created_at 必须显式给值：该列 NOT NULL 且无默认，漏填会写成零日期，
        # 之后 JDBC 读取会抛 "Zero date value prohibited"，审计接口直接 500
        audit_rows.append(
            f"('{trace}', {actor}, 'user{actor}', 'role', '{module}', '{action}', "
            f"'{action.split('.')[0]}', '{rnd.randint(1, 60)}', '{method}', '{path}', "
            f"'演示数据', '{result}', {err}, {cost}, '127.0.0.1', 'seed', '{dt(ts)}')")
    out.append("INSERT IGNORE INTO audit_log\n"
               "  (trace_id, actor_id, actor_name, actor_role, module, action, resource, resource_id, "
               "method, path, detail, result, error_msg, cost_ms, ip, user_agent, created_at) VALUES\n"
               + ",\n".join(audit_rows) + ";")

    # ---------------------------------------------------------- 14 清理测试残留
    out.append("\n-- 14. 清理联调期残留（probe-kanban 及其列/卡片）")
    out.append("DELETE FROM kanban_item WHERE kanban_id IN (SELECT kanban_id FROM kanban WHERE kanban_name LIKE 'probe%');")
    out.append("DELETE FROM kanban_column WHERE kanban_id IN (SELECT kanban_id FROM kanban WHERE kanban_name LIKE 'probe%');")
    out.append("DELETE FROM kanban WHERE kanban_name LIKE 'probe%';")

    out.append("\nCOMMIT;")
    out.append("SET FOREIGN_KEY_CHECKS = 1;")

    sql = "\n".join(out) + "\n"
    with open(args.out, "w", encoding="utf-8") as fh:
        fh.write(sql)
    print(f"已生成 {args.out}（{len(sql) // 1024} KB，{sql.count(chr(10))} 行）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
