#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成《TeamPulse 详细设计与架构决策记录（ADR）》DOCX。

内容全部对应真实类与方法：类名、职责、行数取自源码统计。
用法：
    python scripts/docs/make_ddd_adr_docx.py --out docs/TeamPulse_详细设计与ADR.docx
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import docx_common as C  # noqa: E402

KEY_CLASSES = [
    ("AttendanceService", "module/attendance/service", 700,
     "打卡主流程：验签编排、幂等、判定、落库、日汇总刷新；MQTT 与 HTTP 两个入口共用"),
    ("DeviceSignatureVerifier", "module/attendance/service", 0,
     "HMAC-SHA256 验签与时钟漂移校验；签名串构造集中在这里，避免两处入口写不一致"),
    ("NonceDedupService", "module/attendance/service", 0,
     "nonce 防重放：Redis SET NX EX 600，Redis 不可用时降级为本地 TTL 缓存"),
    ("AttendanceCalculator", "module/attendance/service", 129,
     "纯函数式判定：根据时间与规则算出 NORMAL / LATE / EARLY_LEAVE，可单测"),
    ("AttendanceSummaryService", "module/attendance/service", 130,
     "日汇总（attendance_daily）的重算与增量更新"),
    ("MakeupRequestService", "module/attendance/service", 235,
     "补卡审批状态机：pending → approved/rejected，通过时回写打卡记录"),
    ("DataScopeService", "module/rbac/service", 0,
     "把角色的 data_scope 换算成 userId 集合；ALL 直接放行，DEPT/SELF 下推 IN 条件"),
    ("RbacService", "module/rbac/service", 0,
     "角色-权限映射，带进程内缓存；登录时组装 22 个权限点"),
    ("AuditLogAspect", "module/audit/aspect", 128,
     "环绕 @Audited 方法，成功失败都落库，写入 traceId 与耗时"),
    ("RequestObservabilityFilter", "config", 86,
     "每个请求生成 traceId（MDC + X-Trace-Id），记录 RED 指标"),
    ("JwtAuthenticationFilter", "security", 0,
     "解析 Bearer token → SecurityContext；401 时走统一 JSON 响应"),
    ("GlobalExceptionHandler", "common/exception", 152,
     "业务异常/参数校验/未捕获异常统一转 ApiResponse，并累加错误指标"),
]

ADRS = [
    ("ADR-01", "采用单体架构而非微服务",
     "团队规模 1 人、部署目标 1 台 4C4G 服务器；模块间调用是进程内方法调用，"
     "拆成微服务后需要服务发现、链路追踪、分布式事务，运维成本远超收益。",
     "启动快、排错简单、部署只有一条 compose 命令。",
     "无法按模块独立扩容；单点故障影响全站。",
     "模块已按业务域分包（attendance/rbac/kanban…），未来要拆时边界清晰。"),
    ("ADR-02", "不建物理外键",
     "审计日志不能因删用户而级联失败；设备上报是高频写路径，外键检查会增加锁竞争；"
     "单机场景加外键需锁表，风险高于收益。",
     "写入更快、迁移更自由、审计可长期保留。",
     "一致性靠应用层保证，直接改库可能产生孤儿数据。",
     "删除一律逻辑删除（deleted 标记），从根本上避免父记录消失；关键关系用唯一索引兜底。"),
    ("ADR-03", "打卡采用「原始记录 + 日汇总」双表",
     "日历、报表、公司概览都要按天聚合；若每次实时聚合 5 万行记录，接口会明显变慢。",
     "查询走日汇总表，单日查询从 O(记录数) 降到 O(1)；原始记录仍完整保留可追溯。",
     "存在两份数据，需保证同步。",
     "写入时增量更新 + cron 每日 00:05 全量重算兜底；日汇总可从原始记录重建。"),
    ("ADR-04", "权限用「表驱动」而不是代码常量",
     "22 个权限点 × 6 个角色的组合如果写死在代码里，每次调整都要发版。",
     "改 role_permission 表即可生效，RbacService 有缓存不会拖慢请求。",
     "权限变更缺少界面，目前需写 SQL。",
     "已在 SRS 列为下一步：权限配置界面 + 变更审计。"),
    ("ADR-05", "data_scope 挂在角色上而不是用户上",
     "数据范围是「岗位属性」（HR 看全公司、员工只看自己），不是个人特例；"
     "挂角色可以避免为每个人单独配规则。",
     "6 个角色只需 3 种范围（ALL/DEPT/SELF），配置量最小。",
     "同一人兼任多岗时无法细粒度组合（当前 user.role 是单值）。",
     "若引入一人多岗，改为 user_role 后取并集。"),
    ("ADR-06", "审计用 AOP 注解而不是在每个方法里手写",
     "20 个写操作都要留痕，手写容易漏、且改动业务代码。",
     "加一行 @Audited 即可，切面统一处理成功/失败/耗时/traceId。",
     "切面异常会影响主流程（已用 try-catch 隔离，审计失败只记日志）。",
     "审计表只增不改，应用层无删除接口。"),
    ("ADR-07", "Redis 只做「可降级」的用途",
     "nonce 防重放需要 TTL 语义；但 Redis 挂了不能导致打卡失败。",
     "Redis 不可用时降级为进程内 TTL 缓存，功能可用（单实例下语义等价）。",
     "多实例部署时降级会导致防重放失效。",
     "多实例前必须保证 Redis 高可用；已在已知限制中标注。"),
    ("ADR-08", "统一响应体 + 分段错误码",
     "前端需要按错误类型决定行为（跳登录 / 提示无权限 / 显示 traceId）。",
     "code=0 成功，4xxxx 客户端，5xxxx 服务端，6xxxx 设备业务；前端映射规则集中。",
     "错误码是自造约定，非 HTTP 标准。",
     "HTTP 状态码同时保持语义（401/403/404/409），便于网关与日志观察。"),
    ("ADR-09", "授权判断不复用数据范围判断（isAdmin 语义漂移的教训）",
     "SecurityUtils.isAdmin() 曾被重新定义为「data_scope=ALL」，于是 hr 与 auditor 也满足该条件；"
     "而 6 处授权代码把它当「管理员」用，结果人事和只读审计员可以删除他人的评论、日程、看板、团队、工作日志。",
     "现在改为按各域已有的 *:manage 权限点判断（comment/manage、kanban/manage、schedule/manage、"
     "team/manage、worklog/manage），权限调整只需改 role_permission 表。",
     "方法名 isAdmin() 容易误导，读代码时容易再次用错。",
     "isAdmin() 保留但补了注释说明其语义；授权一律用 hasPermission(Perms.X)；"
     "接口断言新增 5b 组做回归（人事删他人评论必须 403）。"),
    ("ADR-10", "读接口的权限粒度：哪些必须有注解，哪些刻意开放",
     "盘点时发现部分读接口（GET /schedule、/worklog、/kanban 等）没有 @PreAuthorize，"
     "任何登录用户都能调用；而 UI 层已经按权限隐藏入口。",
     "已给「不会打挂任何角色」的读接口补注解：GET /attendance/rule → attendance:rule、"
     "GET /kanban 与 /kanban/{id}/detail → kanban:view、GET /worklog、/{id}、/statistics → worklog:view；"
     "PUT /devices/{id} 由 device:view 提升为 device:manage（读权限不该能改数据）。",
     "GET /schedule 刻意保持开放：人事没有 schedule:view，但用户工作台的「我的日程」用的就是它，"
     "加注解会把人事自己的页面打挂。",
     "残留风险：人事（data_scope=ALL）理论上能通过 API 读到全公司日程。"
     "若要彻底收敛，应把数据范围从「角色级」改为「权限级」，属下一阶段工作；"
     "scripts/audit/role-matrix.mjs 会把这类情况列为 ⓘ 提示。")
]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    doc = C.new_document(
        "TeamPulse 详细设计与架构决策记录",
        "团队协作与物联网考勤平台 · 实现细节与取舍依据",
        ["覆盖：认证、打卡、审批、数据范围、审计、可观测、定时结算",
         "包含 10 条 ADR（Architecture Decision Record）",
         "所有类名与行数取自源码，伪代码对应真实实现顺序",
         "文档版本 v1.0"],
    )

    # ---------------------------------------------------------------- 1
    C.h1(doc, "1　说明")
    C.para(doc, "本文面向「要改这个系统的人」：说明关键流程的逐步实现顺序、核心算法的判定规则、"
                "以及当初为什么这么选。与《概要设计》的区别是——概要设计回答「有哪些模块」，"
                "本文回答「具体怎么跑、改哪里」。")

    # ---------------------------------------------------------------- 2 关键类
    C.h1(doc, "2　关键类职责")
    C.table(doc, ["类", "所在包", "行数", "职责"],
            [[k[0], k[1], k[2] or "—", k[3]] for k in KEY_CLASSES],
            widths=[1.6, 1.5, 0.45, 3.0])

    # ---------------------------------------------------------------- 3 打卡
    C.h1(doc, "3　打卡上报详细设计")
    C.h2(doc, "3.1　入口与共用逻辑")
    C.para(doc, "两个入口最终都调用 AttendanceService.handlePunch(message, source)：")
    C.table(doc, ["入口", "触发方式", "source 取值"],
            [["MQTT", "订阅 attendance/checkin（客户端 ID teampulse-backend-1）", "MQTT"],
             ["HTTP", "IotIngestController → POST /iot/v1/punch", "HTTP"]],
            widths=[0.9, 4.0, 1.3])

    C.h2(doc, "3.2　处理顺序（与代码一致）")
    C.code_block(doc, """handlePunch(msg, source):
  1. 参数基本校验（sn/工号/时间/nonce/sign 非空）        → 60005
  2. DeviceSignatureVerifier.verify(...)
       a. 按 deviceSn 查设备                              → 60001 未注册 / 60002 已禁用
       b. 校验 |设备时间 - 服务端时间| <= 300s             → 60006 时钟漂移
       c. canonical = sn|empNo|punchTime|nonce
          sign == HEX(HMAC-SHA256(secret, canonical))     → 60003 签名错误
  3. NonceDedupService.claim(nonce, ttl=600s)             → 首次返回 true
       Redis SET NX EX；Redis 不可用 → 本地 ConcurrentHashMap + 过期清理
  4. 工号 → 用户（离职/不存在）                            → 61001
  5. 读考勤规则（未配置）                                  → 61002
  6. AttendanceCalculator 判定状态（NORMAL/LATE/EARLY_LEAVE）
  7. 插入 attendance_record
       唯一索引 (message_id) / (employee_no, punch_time) 冲突 → 幂等返回 200，不新增
  8. 刷新当日 attendance_daily
  9. micormeter: punch_total{result=success|failure}、idempotent_total{layer=...}
 10. 返回 AckMessage(id, status, workDate)""", size=8)

    C.h2(doc, "3.3　三层幂等的分工")
    C.table(doc, ["层", "机制", "作用范围", "命中后行为"],
            [["报文层", "message_id 唯一索引", "同一报文重复投递（MQTT QoS1 重发）", "捕获冲突 → 返回成功"],
             ["请求层", "nonce + Redis SET NX", "同一 nonce 的短时重放（含攻击）", "60005 / 60004 拒绝"],
             ["业务层", "employee_no + punch_time 唯一索引", "不同报文但同一人同一时刻", "视为重复，返回成功"]],
            widths=[0.8, 2.2, 2.3, 1.6])

    C.h2(doc, "3.4　判定算法")
    C.code_block(doc, """判定（只依赖「打卡时间」与「规则」两个输入，因此可纯函数单测）：
  上班：t <= workStart                     → NORMAL
        workStart < t <= workStart + grace  → NORMAL（宽限期内不算迟到）
        t > workStart + grace               → LATE，late_minutes = t - workStart
  下班：t >= workEnd                       → NORMAL
        workEnd - grace <= t < workEnd      → NORMAL
        t < workEnd - grace                 → EARLY_LEAVE，early_leave_minutes = workEnd - t
  缺勤：当日无任何打卡 → 由结算任务补 ABSENT""", size=8)

    # ---------------------------------------------------------------- 4 审批
    C.h1(doc, "4　补卡审批详细设计")
    C.code_block(doc, """提交：POST /api/v1/makeup（权限 makeup:submit）
  → 校验日期不在未来、该日确实缺少对应打卡
  → 落 makeup_request(status=pending)

审批：POST /api/v1/makeup/{id}/review（权限 attendance:approve）
  → 读记录；若 status != pending → 409（这就是重复审批返回 40900 的来源）
  → approved=true：
       UPDATE status=approved, approver_id, reviewed_at
       INSERT attendance_record(source=MANUAL, sign_verified=0)
       重算该日 attendance_daily
  → approved=false：UPDATE status=rejected + 审批意见

并发：状态判断用「条件更新」(WHERE status='pending')，受影响行数为 0 即视为已被处理。""", size=8)

    # ---------------------------------------------------------------- 5 数据范围
    C.h1(doc, "5　数据范围实现")
    C.code_block(doc, """DataScopeService.current():
  user = SecurityUtils.currentUserId()
  role = 当前用户的角色 key
  scope = 角色的 data_scope
  if scope == ALL:   return DataScope.unrestricted()
  if scope == SELF:  return DataScope.ofUsers(List.of(user))
  if scope == DEPT:
      ids = userMapper.selectByDepartment(user.departmentId)
      return DataScope.ofUsers(ids)          # 空集合时调用方加 "1 = 0"，避免 IN ()

调用方（Service）统一写法：
  DataScope scope = dataScopeService.current();
  if (!scope.isUnrestricted()) {
      if (scope.userIds().isEmpty()) wrapper.apply("1 = 0");
      else wrapper.in(Entity::getUserId, scope.userIds());
  }
→ 范围条件在 SQL 层生效，不存在「查全量再过滤」""", size=8)
    C.para(doc, "这样做的收益：分页 total 也是准确的（先过滤再分页），且数据库只返回该看见的行。")

    # ---------------------------------------------------------------- 6 审计
    C.h1(doc, "6　审计与可观测")
    C.code_block(doc, """@Audited(module="attendance", action="APPROVE_MAKEUP", detail="补卡审批")
   ↓ 环绕通知
try:
    result = pjp.proceed()
    记 result=SUCCESS
except e:
    记 result=FAILURE + 错误信息，异常继续抛出（不影响主流程语义）
finally:
    写 audit_log(operator, module, action, target, detail, result, duration_ms, trace_id)""", size=8)
    C.table(doc, ["指标", "类型", "标签", "用途"],
            [["teampulse_api_requests_total", "counter", "method, uri（路由模板）, status", "RED 的 Rate"],
             ["teampulse_api_latency_seconds", "timer", "uri", "RED 的 Duration（P50/P95/P99）"],
             ["teampulse_api_errors_total", "counter", "code", "业务错误码分布"],
             ["teampulse_attendance_punch_total", "counter", "result", "打卡成功/失败率"],
             ["teampulse_attendance_idempotent_total", "counter", "layer", "幂等命中（排查重复上报）"]],
            widths=[2.2, 0.7, 1.7, 2.0])

    # ---------------------------------------------------------------- 7 定时
    C.h1(doc, "7　定时结算")
    C.code_block(doc, """@Scheduled(cron = "0 5 0 * * ?")   # 每天 00:05，结算前一天
settleDaily(yesterday):
  for each 在职用户:
       agg = 聚合 attendance_record（当日最早/最晚打卡）
       status = 判定（缺任一侧 → ABSENT / 半天）
       upsert attendance_daily(user_id, work_date)   # 唯一索引保证不重复
说明：写入路径已增量更新过，本任务是「兜底重算」，因此可安全重复执行。""", size=8)

    # ---------------------------------------------------------------- 8 ADR
    C.h1(doc, "8　架构决策记录（ADR）")
    C.para(doc, "每条记录：决定、背景、收益、代价、缓解。看代码前先看这里，能省很多「为什么这么写」的疑问。")
    for no, title, ctx, gain, cost, hedge in ADRS:
        C.h2(doc, f"{no}　{title}")
        C.table(doc, ["项", "内容"],
                [["背景", ctx], ["收益", gain], ["代价", cost], ["缓解", hedge]],
                widths=[0.7, 5.5], font_size=9)

    # ---------------------------------------------------------------- 9 约定与债
    C.h1(doc, "9　编码约定")
    C.table(doc, ["约定", "做法"],
            [["响应体", "控制器只返回 ApiResponse<T>，不直接返回实体"],
             ["异常", "业务失败抛 BizException.of(ErrorCode.X)；不吞异常、不回 null"],
             ["权限", "写操作必须有 @PreAuthorize；数据范围在 Service 层统一处理"],
             ["事务", "@Transactional 只标注在 Service 的写方法上"],
             ["命名", "Controller/Service/Mapper/entity/DTO 后缀固定；DTO 用内部类分域"],
             ["日志", "关键路径 log.info 带业务标识；异常路径 log.warn 带 traceId（MDC 自动注入）"]],
            widths=[1.2, 5.0])

    C.h1(doc, "10　已知技术债")
    C.table(doc, ["项", "影响", "建议"],
            [["各域「代他人操作」目前靠创建者 + *:manage 权限点判断", "权限点齐全，但缺跨团队的「管理全部」列表入口", "管理端补跨团队管理视图"],
             ["user.role 单值", "一人多岗无法表达", "user_role 关联表"],
             ["DEPT 不含子部门", "经理看不到下属部门数据", "递归 CTE 或闭包表"],
             ["@Scheduled 无分布式锁", "多实例时会重复结算（幂等但不经济）", "ShedLock"],
             ["无物理外键", "直接改库可能产生孤儿数据", "只通过应用写入；或加外键到非审计表"],
             ["缺勤只补「昨天」", "历史缺失需要手工补", "补缺勤区间回补接口"]],
            widths=[2.1, 2.4, 1.7])

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    doc.save(args.out)
    print(f"已生成 {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
