#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成《TeamPulse 数据库设计说明书》DOCX。

事实来源：.run-logs/schema.json（由 scripts/docs/extract_schema.py 从运行库导出），
不手写字段，保证与代码/迁移一致。

用法：
    python scripts/docs/make_db_design_docx.py --schema .run-logs/schema.json --out docs/TeamPulse_数据库设计说明书.docx
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import docx_common as C  # noqa: E402

# 表 → 所属域 / 中文名 / 关系说明（人工补充，字段靠自动导出）
DOMAIN = {
    "user": ("协作域", "用户表", "department_id → department（逻辑关联）；role → role.role_key"),
    "department": ("协作域", "部门表", "parent_id 自关联（树形）；leader_id → user"),
    "team": ("协作域", "团队表", "leader_id → user；成员关系见 team_member"),
    "team_member": ("协作域", "团队成员表", "team_id → team；user_id → user；唯一键 (team_id,user_id)"),
    "schedule": ("协作域", "日程表", "user_id → user（创建人）；team_id → team（可空）"),
    "kanban": ("协作域", "看板表", "team_id → team；列见 kanban_column"),
    "kanban_column": ("协作域", "看板列表", "kanban_id → kanban；卡片见 kanban_item"),
    "kanban_item": ("协作域", "看板卡片表", "column_id → kanban_column；assignee_id → user"),
    "worklog": ("协作域", "工作日志表", "user_id → user"),
    "comment": ("协作域", "评论表", "user_id → user；target_type + target_id 多态指向日程/日志/卡片"),
    "sensitive_word": ("协作域", "敏感词表", "无外键；供评论写入时做词库校验"),
    "role": ("权限域", "角色表", "role_key 是 RBAC 判权依据；权限关系见 role_permission"),
    "permission": ("权限域", "权限点表", "permission_key 形如 module:action"),
    "role_permission": ("权限域", "角色-权限关联表", "role_id → role；permission_id → permission"),
    "iot_device": ("考勤域", "物联网设备表", "secret 为 HMAC-SHA256 密钥，只在注册/轮换时明文返回"),
    "attendance_rule": ("考勤域", "考勤规则表", "单行配置表（上班/下班时间、迟到阈值）"),
    "attendance_record": ("考勤域", "打卡记录表", "user_id → user；device_sn → iot_device；message_id 唯一（幂等）"),
    "attendance_daily": ("考勤域", "考勤日汇总表", "user_id → user；唯一键 (user_id,work_date)；由定时任务生成"),
    "makeup_request": ("考勤域", "补卡申请表", "user_id → user；approver_id → user；审批通过后写 attendance_record"),
    "audit_log": ("审计域", "审计日志表", "无外键（刻意不关联，避免用户删除后日志失效）；trace_id 关联请求链路"),
    "flyway_schema_history": ("基础设施", "Flyway 迁移历史", "由 Flyway 维护，业务代码只读"),
}

DOMAIN_ORDER = ["协作域", "权限域", "考勤域", "审计域", "基础设施"]

CORE_NOTES = {
    "user": "密码存 BCrypt 散列；role 存 RBAC 的角色 key（V7 把历史值 admin/member 归一为 sysadmin/employee）；"
            "deleted 为逻辑删除标记，登录与列表查询都会过滤。",
    "attendance_record": "打卡的**唯一事实源**。三层幂等：message_id 唯一索引 → Redis nonce → 业务判重；"
                         "punch_time 为设备上报时间（经 ±300s 漂移校验），work_date 由服务端按东八区截断生成。",
    "attendance_daily": "按天汇总，供日历与报表快速查询，避免每次对 record 做全表聚合；"
                        "由 cron `0 5 0 * * ?` 每日 00:05 结算前一天。",
    "iot_device": "secret 为 64 位十六进制；sign_verified 字段落在 record 上，用于区分「验签通过」与「人工补录」。",
    "audit_log": "记录 module/action/detail/result/trace_id；失败操作也落库（result=FAILURE），"
                 "便于追查越权尝试；无 updated_at，写入即不可变。",
    "makeup_request": "状态机 pending → approved / rejected；审批通过后回写 attendance_record（source=MANUAL）。",
    "role": "6 个内置角色：sysadmin/hr/manager/leader/employee/auditor；data_scope 挂在角色上（ALL/DEPT/SELF）。",
    "role_permission": "22 个权限点 × 6 角色的映射；改权限只需改这张表，不需要改代码。",
}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--schema", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--commit", default="main")
    args = ap.parse_args()

    data = json.load(open(args.schema, encoding="utf-8"))
    tables = data["tables"]
    tnames = {t["name"] for t in tables}
    total_cols = sum(len(t["columns"]) for t in tables)
    biz_tables = [t for t in tables if t["name"] != "flyway_schema_history"]

    doc = C.new_document(
        "TeamPulse 数据库设计说明书",
        "团队协作与物联网考勤平台 · 数据层设计",
        [f"数据库：MySQL 8.0　字符集：utf8mb4 / utf8mb4_unicode_ci　时区：+08:00",
         f"规模：{len(tables)} 张表（业务表 {len(biz_tables)} 张）/ {total_cols} 个字段 / "
         f"{len(data['migrations'])} 条 Flyway 迁移",
         f"基线：代码 {args.commit}　文档版本 v1.0",
         "本文件由脚本从运行中的数据库导出，字段与索引与代码保持一致"],
    )

    # ---------------------------------------------------------------- 1 概述
    C.h1(doc, "1　概述")
    C.h2(doc, "1.1　编写目的")
    C.para(doc, "说明 TeamPulse 数据层的表结构、字段语义、索引与约束、演进方式，"
                "供开发、测试、运维与答辩评审使用。所有字段信息来自运行中的数据库，"
                "而非手工整理，因此与代码里的实体和 Flyway 迁移严格一致。")
    C.h2(doc, "1.2　数据来源与可复现性")
    C.para(doc, "本文档的数据由 scripts/docs/extract_schema.py 从 MySQL 容器导出为 JSON，"
                "再由 scripts/docs/make_db_design_docx.py 渲染成 Word。")
    C.code_block(doc, "python scripts/docs/extract_schema.py --password <root密码> --out .run-logs/schema.json\n"
                      "python scripts/docs/make_db_design_docx.py --schema .run-logs/schema.json "
                      "--out docs/TeamPulse_数据库设计说明书.docx")
    C.h2(doc, "1.3　设计原则")
    for text in [
        "一切以代码与迁移为准：文档不描述「计划中的表」，只描述已建成的表。",
        "逻辑外键、无物理外键：不建 FOREIGN KEY 约束，关联完整性由服务层保证（原因见第 6 章）。",
        "统一审计字段：created_at / updated_at / deleted 三件套（审计日志表除外）。",
        "幂等优先：设备上报、审批流转都用唯一索引兜底，重复请求不会产生脏数据。",
    ]:
        C.bullet(doc, text)

    # ---------------------------------------------------------------- 2 命名与约定
    C.h1(doc, "2　设计约定")
    C.table(doc,
            ["约定项", "规则", "示例"],
            [["表名", "小写下划线，单数语义", "attendance_record、makeup_request"],
             ["主键", "bigint 自增，字段名 <表名>_id", "user_id、audit_log_id"],
             ["逻辑删除", "deleted tinyint，0 未删 / 1 已删", "user.deleted"],
             ["时间字段", "created_at / updated_at，datetime", "attendance_record.created_at"],
             ["枚举存储", "varchar 存英文常量，不用数字", "punch_type=CHECK_IN、status=LATE"],
             ["字符集", "utf8mb4 / utf8mb4_unicode_ci", "支持 emoji 与中文"],
             ["时区", "服务端与数据库均为 +08:00", "compose 里 TZ=Asia/Shanghai"],
             ["金额/精度", "本系统无金额字段", "—"]],
            widths=[1.1, 3.0, 2.6])

    # ---------------------------------------------------------------- 3 表总览
    C.h1(doc, "3　表清单总览")
    rows = []
    for t in tables:
        domain, cn, _ = DOMAIN.get(t["name"], ("其他", t["comment"] or t["name"], ""))
        rows.append([t["name"], t["comment"] or cn, domain, len(t["columns"]), len(t["indexes"])])
    C.table(doc, ["表名", "中文名", "所属域", "字段数", "索引数"], rows,
            widths=[2.1, 1.9, 0.9, 0.7, 0.7])
    C.note(doc, f"共 {len(tables)} 张表；业务表 {len(biz_tables)} 张，"
                f"flyway_schema_history 由迁移工具维护。字段总数 {total_cols}。")

    # ---------------------------------------------------------------- 4 详细设计
    C.h1(doc, "4　表结构详解")
    C.para(doc, "按业务域分组。每张表给出字段表、索引表与关系说明；"
                "字段注释直接取数据库 COLUMN_COMMENT，未写注释的字段以「—」标注。")

    for domain in DOMAIN_ORDER:
        group = [t for t in tables if DOMAIN.get(t["name"], ("其他",))[0] == domain]
        if not group:
            continue
        C.h2(doc, f"4.{DOMAIN_ORDER.index(domain) + 1}　{domain}（{len(group)} 张表）")
        for t in group:
            _, cn, rel = DOMAIN.get(t["name"], ("其他", t["comment"], ""))
            C.h3(doc, f"{t['name']}　{cn}")
            if t["name"] in CORE_NOTES:
                C.para(doc, CORE_NOTES[t["name"]], size=10)
            if rel:
                C.para(doc, f"关系：{rel}", size=9.5, bold=False)

            frows = []
            for c in t["columns"]:
                key = {"PRI": "PK", "UNI": "UK", "MUL": "MUL"}.get(c["key"] or "", "")
                # 默认值缩写，避免 CURRENT_TIMESTAMP 在窄列里竖排断行
                dv = c["default"]
                if dv is None:
                    dv_text = "—"
                else:
                    dvs = str(dv)
                    if dvs.upper().startswith("CURRENT_TIMESTAMP"):
                        dv_text = "now()"
                    else:
                        dv_text = dvs
                note_text = c["comment"] or "—"
                extra = (c["extra"] or "").lower()
                if "auto_increment" in extra:
                    note_text += "（自增）"
                elif "on update" in extra:
                    note_text += "（更新时刷新）"
                frows.append([
                    c["name"], c["type"], key,
                    "是" if c["nullable"] else "否",
                    dv_text, note_text,
                ])
            C.table(doc, ["字段", "类型", "键", "可空", "默认", "说明"], frows,
                    widths=[1.50, 1.28, 0.45, 0.42, 0.62, 2.60], font_size=8)

            if t["indexes"]:
                irows = []
                for i in t["indexes"]:
                    kind = "唯一" if i["unique"] else "普通"
                    if i["name"] == "PRIMARY":
                        kind = "主键"
                    irows.append([i["name"], kind, i["columns"]])
                C.table(doc, ["索引名", "类型", "列（按序）"], irows,
                        widths=[2.0, 0.8, 3.4], font_size=8)

    # ---------------------------------------------------------------- 5 索引与约束
    C.h1(doc, "5　索引与唯一约束汇总")
    C.para(doc, "唯一索引同时承担业务约束，是本系统「幂等」与「不重复」的底层保证。")
    uniq_rows = []
    for t in tables:
        for i in t["indexes"]:
            if i["unique"] and i["name"] != "PRIMARY":
                uniq_rows.append([t["name"], i["name"], i["columns"],
                                  "防止重复写入" if "message" in i["name"].lower() else "业务唯一性"])
    C.table(doc, ["表", "唯一索引", "列", "作用"], uniq_rows, widths=[1.7, 2.0, 2.0, 1.6])

    C.h2(doc, "5.1　按查询场景设计的组合索引")
    C.table(doc,
            ["索引", "表", "列", "对应查询"],
            [["idx_record_date_status", "attendance_record", "work_date, status", "记录列表按日期+状态过滤"],
             ["idx_record_user_date", "attendance_record", "user_id, work_date", "个人日历按用户+日期范围"],
             ["idx_record_device", "attendance_record", "device_sn, punch_time", "设备维度审计"],
             ["idx_record_date_punch", "attendance_record", "work_date, punch_time", "V8 新增：日期范围 + 时间排序（压测后补）"],
             ["idx_daily_user_date", "attendance_daily", "user_id, work_date", "日汇总按人按天查"],
             ["idx_audit_time", "audit_log", "created_at", "审计按时间倒序翻页"]],
            widths=[2.0, 1.6, 1.9, 2.4])
    C.note(doc, "压测结论（docs/性能测试报告.md）：这些索引把 SQL 层耗时压到 0.6ms 以内，"
                "接口耗时主要来自应用层的多次查询组装，而非索引缺失。")

    # ---------------------------------------------------------------- 6 无物理外键
    C.h1(doc, "6　为什么没有物理外键")
    C.para(doc, "全库 FOREIGN KEY 数量为 0，这是刻意设计，不是遗漏。")
    for text in [
        "审计与留痕不能因为删除用户而级联失败：audit_log 若加外键，删用户时要么无法删、要么级联删日志，两者都不可接受。",
        "设备上报是高频写路径：外键会在每次 INSERT 前做父表检查，增加锁竞争与延迟。",
        "迁移友好：加/删外键需要锁表，在只有一台 4C4G 服务器的场景下风险高于收益。",
        "代价与兜底：应用层通过 DataScopeService、业务校验、唯一索引保证一致性；"
        "删除采用逻辑删除（deleted 标记），从根本上避免「父记录消失」。",
    ]:
        C.bullet(doc, text)

    # ---------------------------------------------------------------- 7 迁移演进
    C.h1(doc, "7　迁移演进（Flyway）")
    C.para(doc, "所有结构变更以增量 SQL 迁移落地，启动时自动执行；生产环境只前进不回退。")
    mrows = [[m["rank"], m["version"], m["description"], m["script"], "成功" if m["success"] else "失败"]
             for m in data["migrations"]]
    C.table(doc, ["顺序", "版本", "说明", "脚本", "结果"], mrows, widths=[0.5, 0.6, 2.4, 2.2, 0.6])
    C.h2(doc, "7.1　历史数据兼容")
    C.para(doc, "V7 是一次「数据订正」类的迁移：改造前 user.role 只有 admin / member 两个值，"
                "而 RBAC 只认 role 表的 6 个 key。若库里残留旧值，该用户查不到角色 → 权限集合为空 → "
                "登录后几乎全部接口 403。V7 用三条幂等 UPDATE 把旧值归一到 sysadmin / employee，"
                "并兼容 role 为空的历史行。")

    # ---------------------------------------------------------------- 8 关系图
    C.h1(doc, "8　实体关系图")
    C.para(doc, "由于全库无物理外键，关系图依据业务语义绘制。以下 Mermaid 源码可直接在 VS Code "
                "（Markdown Preview Mermaid）或 mermaid.live 渲染；本机未安装渲染器，故以源码形式给出。")
    mer = """erDiagram
    department ||--o{ user : "部门成员"
    department ||--o{ department : "父子部门"
    user ||--o{ team_member : "加入团队"
    team ||--o{ team_member : "团队成员"
    team ||--o{ schedule : "团队日程"
    team ||--o{ kanban : "团队看板"
    kanban ||--o{ kanban_column : "看板列"
    kanban_column ||--o{ kanban_item : "列内卡片"
    user ||--o{ schedule : "创建日程"
    user ||--o{ worklog : "提交日志"
    user ||--o{ comment : "发表评论"
    role ||--o{ role_permission : "角色权限"
    permission ||--o{ role_permission : "权限归属"
    role ||--o{ user : "用户角色(逻辑)"
    user ||--o{ attendance_record : "打卡记录"
    user ||--o{ attendance_daily : "日汇总"
    iot_device ||--o{ attendance_record : "设备上报"
    user ||--o{ makeup_request : "提交补卡"
    user ||--o{ audit_log : "操作留痕(逻辑)"
    """
    C.code_block(doc, mer, size=8)
    C.h2(doc, "8.1　核心关系的读取方式")
    C.table(doc,
            ["关系", "关联字段", "代码中的体现"],
            [["用户 ↔ 部门", "user.department_id → department.department_id", "DataScopeService 计算 DEPT 范围时按此字段下推 SQL"],
             ["用户 ↔ 角色", "user.role → role.role_key", "RbacService 启动时加载角色-权限映射"],
             ["角色 ↔ 权限", "role_permission(role_id, permission_id)", "登录时组装 22 个权限点，写入 JWT 与响应"],
             ["打卡 ↔ 设备", "attendance_record.device_sn → iot_device.device_sn", "验签前按 SN 查密钥"],
             ["补卡 ↔ 打卡", "makeup_request → attendance_record(source=MANUAL)", "审批通过时回写一条打卡"],
             ["审计 ↔ 请求", "audit_log.trace_id ↔ 响应头 X-Trace-Id", "前端报错提示里可直接核对"]],
            widths=[1.4, 2.4, 2.4])

    # ---------------------------------------------------------------- 9 容量与性能
    C.h1(doc, "9　容量与性能基线")
    C.para(doc, "下表是压测时（5 万条打卡记录）的实际数据，用于估算扩容点。")
    C.table(doc,
            ["指标", "实测值", "说明"],
            [["打卡记录写入", "单条 4 次 DB 往返以内", "验签 → nonce → 唯一索引 → 日汇总"],
             ["记录列表查询（SQL 层）", "0.30 ~ 0.62 ms（5 万行）", "见性能测试报告"],
             ["接口 P95（20 并发）", "40.5 ms", "含 JSON 序列化与 3 次补全查询"],
             ["单表容量估算", "500 万行以内无需分表", "每天 200 人 × 2 次 ≈ 400 行/天，约 34 年"],
             ["备份大小", "约 2 MB / 万行", "mysqldump gzip 后实测"]],
            widths=[2.0, 2.0, 2.2])

    # ---------------------------------------------------------------- 10 数据保留
    C.h1(doc, "10　数据保留与删除")
    C.table(doc,
            ["数据", "策略", "实现"],
            [["打卡记录", "长期保留", "不物理删除；纠错走补卡审批，不覆盖原始记录"],
             ["考勤日汇总", "可从原始记录重建", "定时任务按天重算，异常时可重跑"],
             ["审计日志", "长期保留", "应用层不提供删除接口，仅管理员可查"],
             ["用户", "逻辑删除", "deleted=1，登录与列表查询过滤；历史打卡保留"],
             ["设备密钥", "可轮换", "轮换后旧密钥立即失效，历史记录不受影响"]],
            widths=[1.3, 1.7, 3.2])

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    doc.save(args.out)
    print(f"已生成 {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
