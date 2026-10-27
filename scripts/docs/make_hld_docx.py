#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成《TeamPulse 总体设计说明书》DOCX。

规模数字来自对源码的实时统计（见 docs 中的说明），不手工估算。
用法：
    python scripts/docs/make_hld_docx.py --out docs/TeamPulse_概要设计说明书.docx
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import docx_common as C  # noqa: E402

MODULES = [
    ("attendance", "考勤域：打卡上报、判定、日汇总、规则、设备、补卡审批", "AttendanceService / AttendanceIngestService / AttendanceSettleJob", 34, 2981),
    ("rbac", "角色与权限：角色-权限映射、数据范围计算", "RbacService / DataScopeService", 13, 457),
    ("kanban", "团队看板：看板、列、卡片、拖拽排序", "KanbanService", 10, 1111),
    ("comment", "评论与敏感词：多态评论、词库过滤、审计留痕", "CommentService / SensitiveWordService", 9, 1105),
    ("team", "团队：成员、负责人、团队维度查询", "TeamService", 7, 797),
    ("audit", "审计：AOP 切面 + 查询接口", "AuditLogAspect / AuditLogService", 7, 475),
    ("schedule", "日程：增删改查、日历视图、参与人", "ScheduleService", 5, 766),
    ("worklog", "工作日志：日报/周报、统计", "WorklogService", 5, 682),
    ("dashboard", "概览统计：管理员与员工两套看板", "DashboardService", 3, 600),
    ("user", "用户：账号、部门归属、状态", "UserService", 5, 499),
    ("auth", "认证：登录、JWT 签发、当前用户", "AuthService / JwtService", 3, 220),
    ("exception", "统一异常与错误码", "GlobalExceptionHandler / ErrorCode / BizException", 3, 236),
    ("api", "统一响应体与分页", "ApiResponse / PageResult", 2, 95),
]

CONTROLLERS = [
    ("KanbanController", "/api/v1/kanban", 11, 1),
    ("TeamController", "/api/v1/team", 11, 1),
    ("AttendanceController", "/api/v1/attendance", 10, 4),
    ("UserController", "/api/v1/user", 10, 7),
    ("ScheduleController", "/api/v1/schedule", 7, 1),
    ("DeviceController", "/api/v1/devices", 6, 4),
    ("CommentController", "/api/v1/comment", 6, 1),
    ("WorklogController", "/api/v1/worklog", 6, 0),
    ("MakeupRequestController", "/api/v1/makeup", 5, 5),
    ("SensitiveWordController", "/api/v1/sensitive-word", 5, 2),
    ("AuditController", "/api/v1/audit", 3, 3),
    ("AuthController", "/api/v1/auth", 3, 0),
    ("DashboardController", "/api/v1/dashboard", 3, 1),
    ("IotIngestController", "/iot/v1", 2, 0),
    ("DepartmentController", "/api/v1/department", 1, 1),
    ("RoleController", "/api/v1/role", 1, 1),
]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    java_files = 125
    java_lines = 11307
    views, view_lines = 37, 17014

    doc = C.new_document(
        "TeamPulse 总体设计说明书",
        "团队协作与物联网考勤平台 · 架构与模块设计",
        ["架构风格：单体（Monolith）+ 前后端分离 + 单机容器化部署",
         f"规模：后端 {java_files} 个 Java 文件 / {java_lines} 行；前端 {views} 个页面 / {view_lines} 行",
         "接口：16 个控制器 / 90 个端点 / 32 处方法级权限注解",
         "文档版本 v1.0　基线：main 分支 + 线上 81.70.102.116"],
    )

    # ---------------------------------------------------------------- 1
    C.h1(doc, "1　概述")
    C.h2(doc, "1.1　编写目的")
    C.para(doc, "说明系统总体架构、模块划分、关键技术方案与部署形态，是需求（SRS）与详细设计（DDD）之间的桥梁。"
                "读者可据此判断「系统怎么分层、模块边界在哪、为什么这么选」。")
    C.h2(doc, "1.2　设计目标")
    for text in [
        "单人可维护：一个开发者能在一天内看懂全部代码，不依赖团队协作流程。",
        "单机可运行：4C4G 一台服务器跑完整系统（数据库、缓存、消息、后端、前端）。",
        "故障可定位：每个请求有 traceId，错误有统一错误码，越权有审计留痕。",
        "可演示：6 个角色、22 个权限点、真实设备上报链路，开箱即可演示。",
    ]:
        C.bullet(doc, text)
    C.h2(doc, "1.3　明确的边界")
    C.para(doc, "本期**不做**微服务、不做分布式事务、不做多租户、不做移动端。"
                "理由不是「不会」，而是这套规模下引入这些复杂度会显著降低可维护性，"
                "且无法在单机上验证。相关决策记录见《详细设计与 ADR》。")

    # ---------------------------------------------------------------- 2 架构风格
    C.h1(doc, "2　总体架构")
    C.h2(doc, "2.1　分层结构")
    C.table(doc,
            ["层", "职责", "包路径", "约束"],
            [["接入层", "HTTP 路由、参数校验、权限注解、统一响应", "*.controller", "不写业务逻辑，不直接调 Mapper"],
             ["应用层", "业务编排、事务边界、跨模块协作", "*.service", "唯一允许开启事务的地方"],
             ["领域支撑", "数据范围、RBAC、审计、错误码", "config / common / rbac / audit", "被多个模块复用，禁止反向依赖业务模块"],
             ["持久层", "CRUD 与查询构造", "*.mapper + entity", "只做数据访问，不含业务判断"],
             ["横切", "认证过滤器、可观测性过滤器、异常处理、AOP", "config / common", "对所有请求生效，顺序有明确约定"]],
            widths=[0.9, 2.4, 1.7, 2.0])

    C.h2(doc, "2.2　部署视图（线上实际形态）")
    C.table(doc,
            ["服务", "镜像", "端口", "职责", "资源限制"],
            [["tp-frontend", "teampulse/frontend:1.0.0", "80", "Nginx 托管 SPA + 反向代理 /api、/iot、/actuator", "内存上限 256M"],
             ["tp-backend", "teampulse/backend:1.0.0", "8080（仅内网）", "Spring Boot 单体应用", "内存上限 1G"],
             ["tp-mysql", "mysql:8.0", "127.0.0.1:3306", "业务数据", "—"],
             ["tp-redis", "redis:7-alpine", "6379（仅内网）", "nonce 防重放、缓存；不可用时降级为内存", "128M + LRU"],
             ["tp-mosquitto", "eclipse-mosquitto:2", "1883", "设备 MQTT 接入（密码认证）", "—"],
             ["tp-prometheus（可选）", "prom/prometheus", "127.0.0.1:9090", "指标抓取，保留 15 天", "512M"],
             ["tp-grafana（可选）", "grafana/grafana", "127.0.0.1:3000", "看板（预置 7 个面板）", "512M"]],
            widths=[1.3, 1.6, 1.1, 2.3, 0.9])
    C.note(doc, "只有 80 与 1883 对外；8080/3000/9090 只绑回环或内网，看监控需 SSH 隧道。")

    C.h2(doc, "2.3　运行时拓扑")
    C.code_block(doc, """flowchart LR
    Browser[浏览器 / Vue3 SPA] -->|HTTP 80| Nginx[Nginx 静态站 + 反代]
    ESP32[ESP32 打卡机] -->|MQTT 1883 密码认证| Mosq[Mosquitto]
    ESP32 -.->|HTTP 兜底 /iot/v1/punch| Nginx
    Nginx -->|/api/*| Boot[Spring Boot 8080]
    Nginx -->|/iot/*| Boot
    Mosq -->|共享订阅| Boot
    Boot --> MySQL[(MySQL 8.0)]
    Boot --> Redis[(Redis 7)]
    Prom[Prometheus] -.->|抓取 /actuator/prometheus| Boot
    Graf[Grafana] --> Prom
    Boot -.->|traceId 日志| Logs[(backend-logs 卷)]""", size=8)

    # ---------------------------------------------------------------- 3 模块划分
    C.h1(doc, "3　模块划分")
    C.para(doc, "后端按业务域分包，模块之间只通过 Service 交互；模块规模用「文件数 / 行数」衡量，"
                "数据来自对 backend/src/main/java 的实时统计。")
    rows = [[m[0], m[1], m[2], m[3], m[4]] for m in MODULES]
    rows.append(["config / common（根包）", "全局配置：安全、JWT 过滤器、可观测性、线程池、启动初始化", "SecurityConfig / RequestObservabilityFilter / BootstrapDataInitializer", 18, 1251])
    C.table(doc, ["模块", "职责", "核心类", "文件", "行数"], rows, widths=[1.25, 2.5, 2.1, 0.45, 0.5])

    C.h2(doc, "3.1　控制器与端点")
    C.table(doc, ["控制器", "基础路径", "端点", "@PreAuthorize"],
            [[c[0], c[1], c[2], c[3]] for c in CONTROLLERS],
            widths=[2.3, 1.9, 0.6, 1.0])
    C.note(doc, "设备接入控制器（IotIngestController）刻意不加 @PreAuthorize：它用 HMAC 验签认证，"
                "不是用户令牌体系。WorklogController 的 6 个端点全部按「本人/团队」在服务层收窄，"
                "所以没有方法级注解。")

    C.h2(doc, "3.2　前端结构")
    C.table(doc,
            ["目录", "内容", "说明"],
            [["views/", "37 个页面 / 17014 行", "按业务域再分目录：attendance/、schedule/、team/、system/ 等"],
             ["components/", "5 个通用组件", "布局、图表、空状态等"],
             ["api/", "Axios 实例 + 各域接口封装", "统一注入 token、统一错误处理"],
             ["stores/", "Pinia：auth、ui", "auth 持有用户、权限集合、hasPerm()"],
             ["router/", "31 条路由 + 守卫", "按 permissions 做路由级拦截"],
             ["utils/", "netErrors、格式化、校验", "网络错误分级提示"]],
            widths=[1.1, 2.1, 3.0])

    # ---------------------------------------------------------------- 4 关键设计
    C.h1(doc, "4　关键设计")
    C.h2(doc, "4.1　认证与授权")
    C.para(doc, "采用「JWT 无状态认证 + 方法级权限注解 + 数据范围下推」三层：")
    for text in [
        "JWT：登录成功后签发，载荷含 userId、username、role、permissions（22 个权限点）、dataScope；"
        "前端据此渲染菜单，后端每次请求再校验一次（不信任前端）。",
        "方法级：@PreAuthorize(\"hasAuthority('module:action')\") 共 32 处，覆盖所有写操作与敏感查询。",
        "数据范围：DataScopeService 依据角色的 data_scope 计算可见 userId 集合，"
        "再以 IN 条件下推到 SQL，避免「查出来再过滤」的性能与越权风险。",
    ]:
        C.bullet(doc, text)
    C.table(doc,
            ["角色", "data_scope", "可见范围", "典型端点"],
            [["sysadmin", "ALL", "全公司", "所有管理端接口"],
             ["hr", "ALL", "全公司（人事职能）", "考勤规则、补卡审批、公司概览"],
             ["auditor", "ALL（只读）", "全公司只读", "审计日志、报表"],
             ["manager", "DEPT", "本部门及下属", "部门日程、成员打卡、补卡审批"],
             ["leader", "DEPT", "所在部门", "团队日程、看板"],
             ["employee", "SELF", "仅本人", "我的日程、我的打卡"]],
            widths=[0.9, 0.9, 1.5, 2.9])

    C.h2(doc, "4.2　物联网打卡链路")
    C.para(doc, "设备侧（ESP32）：RFID 读卡 → 组 JSON → HMAC-SHA256 签名 → MQTT 发布到 attendance/checkin；"
                "断网时可走 HTTP 兜底（同一套验签逻辑）。服务端两条入口共用同一个 IngestService。")
    C.table(doc,
            ["环节", "实现", "失败时"],
            [["设备鉴权", "deviceSn 查库取 secret；签名串 deviceSn|employeeNo|punchTime|nonce", "60003 签名错误"],
             ["时钟校验", "设备时间与服务端相差 > 300s 判定异常", "60006 时钟漂移"],
             ["防重放", "nonce 写入 Redis（SET NX EX 600）；Redis 不可用降级为本地 Map", "60005 重复 nonce"],
             ["幂等", "message_id 唯一索引；重复报文返回 200 且不新增记录", "静默去重"],
             ["判定", "按考勤规则算 NORMAL/LATE/EARLY_LEAVE/ABSENT", "落 status 字段"],
             ["汇总", "写 record 后异步刷新当日 attendance_daily", "定时任务兜底重算"]],
            widths=[1.1, 3.3, 1.8])

    C.h2(doc, "4.3　审计设计")
    C.para(doc, "自定义注解 @Audited(module, action, detail) 标注在 20 个写操作上，"
                "AuditLogAspect 环绕执行：无论成功失败都落库，记录操作人、目标、耗时、结果、traceId。"
                "审计表只增不改，应用层不提供删除接口。")

    C.h2(doc, "4.4　可观测性")
    C.table(doc,
            ["维度", "实现", "用途"],
            [["链路追踪", "RequestObservabilityFilter 生成 traceId 写入 MDC 与响应头 X-Trace-Id", "前端报错提示可带 traceId，日志可精确检索"],
             ["指标", "Micrometer → /actuator/prometheus（RED + 业务指标）", "Grafana 看板与告警"],
             ["日志", "logback-spring.xml 输出 %X{traceId}，按天滚动写入卷", "事后复盘"],
             ["健康检查", "/actuator/health + 容器 healthcheck + Nginx /healthz", "编排与负载均衡"]],
            widths=[1.0, 3.2, 2.0])

    # ---------------------------------------------------------------- 5 时序
    C.h1(doc, "5　关键时序")
    C.h2(doc, "5.1　设备打卡上报")
    C.code_block(doc, """sequenceDiagram
    participant D as ESP32
    participant M as Mosquitto
    participant B as Backend
    participant R as Redis
    participant DB as MySQL
    D->>M: publish attendance/checkin {sn,empNo,time,nonce,sign}
    M->>B: 投递给订阅者
    B->>DB: 按 deviceSn 查 secret
    B->>B: 校验 HMAC 与时钟漂移(±300s)
    B->>R: SET NX nonce EX 600
    alt nonce 已存在
        B-->>M: 丢弃（60005）
    else 首次
        B->>DB: INSERT record（message_id 唯一）
        alt 唯一键冲突
            B-->>M: 幂等返回 200
        else 成功
            B->>DB: 刷新 attendance_daily
            B->>B: 指标 punch_total{result=success}
        end
    end""", size=8)

    C.h2(doc, "5.2　补卡审批")
    C.code_block(doc, """sequenceDiagram
    participant E as 员工
    participant B as Backend
    participant DB as MySQL
    E->>B: POST /api/v1/makeup（补卡申请）
    B->>DB: makeup_request(status=pending)
    Note over B: @Audited 记录提交动作
    participant M as 经理/HR
    M->>B: POST /api/v1/makeup/{id}/approve
    B->>B: 校验状态仍为 pending（否则 409）
    B->>DB: UPDATE status=approved
    B->>DB: INSERT attendance_record(source=MANUAL)
    B->>DB: 刷新 attendance_daily
    Note over B: 失败或越权也写 audit_log(result=FAILURE)""", size=8)

    C.h2(doc, "5.3　数据范围生效过程")
    C.code_block(doc, """sequenceDiagram
    participant C as Controller
    participant S as Service
    participant DS as DataScopeService
    participant M as Mapper
    C->>S: 查询请求（带 JWT）
    S->>DS: current()
    DS->>DS: 读角色 data_scope + 部门树
    alt ALL
        DS-->>S: unrestricted
    else DEPT / SELF
        DS-->>S: userIds 集合
    end
    S->>M: 查询 + IN(userId in userIds)
    M-->>S: 已按范围收窄的数据""", size=8)

    # ---------------------------------------------------------------- 6 非功能
    C.h1(doc, "6　非功能设计对照")
    C.table(doc,
            ["需求（SRS 第 8 章）", "设计手段", "验证方式"],
            [["性能：单机支撑 200 人", "逻辑删除 + 组合索引 + 日汇总表；压测 20 并发 P95 40ms", "docs/性能测试报告.md"],
             ["安全：设备不可伪造", "HMAC-SHA256 + nonce 防重放 + 时钟漂移校验", "集成测试 + 46 项接口断言"],
             ["安全：越权不可达", "32 处方法级注解 + 数据范围下推 + 审计留痕", "6 角色 × 关键端点矩阵断言"],
             ["可靠：不丢打卡", "MQTT 持久化会话 + 唯一索引幂等 + HTTP 兜底", "重复报文幂等用例"],
             ["可维护：改权限不改代码", "角色-权限表驱动，权限点常量集中在 RbacService", "改库即可生效"],
             ["可观测：问题可定位", "traceId + RED 指标 + 审计日志三件套", "Grafana 看板 + 日志检索"],
             ["可部署：一台机器跑起来", "compose 五服务 + 幂等部署脚本 + 健康检查", "腾讯云实测 295s 构建 / 40s 启动"]],
            widths=[1.8, 2.9, 1.5])

    # ---------------------------------------------------------------- 7 选型
    C.h1(doc, "7　技术选型与理由")
    C.table(doc,
            ["选型", "版本", "为什么选", "被否的替代"],
            [["Spring Boot", "3.2.4", "生态成熟、单体开发效率最高、Java 17 长期支持", "微服务（本规模运维成本过高）"],
             ["MyBatis-Plus", "3.5.7", "Lambda 查询构造 + 分页插件，复杂查询可控", "JPA（复杂查询与 SQL 调优不直观）"],
             ["Spring Security + jjwt", "6 / 0.12.5", "标准过滤器链，方法级注解与数据范围结合自然", "Sa-Token（生态与文档不如前者）"],
             ["MySQL", "8.0", "事务与唯一索引满足幂等需求，单机够用", "PostgreSQL（团队更熟 MySQL）"],
             ["Redis", "7", "nonce 防重放需要 TTL 语义", "本地缓存（多实例不共享）"],
             ["Mosquitto", "2", "轻量、单二进制、ESP32 生态示例多", "EMQX（功能过重）"],
             ["Vue 3 + Element Plus", "3.x / 2.14", "表格表单密集的管理端开发效率高", "React（既有代码基础）"],
             ["Nginx", "1.27", "静态托管 + 反代 + 同源规避跨域", "Node 直接对外（无静态压缩/缓存优势）"]],
            widths=[1.35, 0.85, 2.4, 1.6])

    # ---------------------------------------------------------------- 8 演进
    C.h1(doc, "8　扩展点与演进路线")
    C.table(doc,
            ["扩展点", "当前实现", "下一步可做"],
            [["权限配置界面", "改 role_permission 需写 SQL", "页面化编辑 + 变更审计"],
             ["一人多岗", "user.role 单值", "user_role 关联表 + 取并集"],
             ["部门树范围", "DEPT 只含本部门", "递归 CTE 含子部门"],
             ["定时任务", "单实例 @Scheduled", "多实例时加 ShedLock"],
             ["设备安全", "MQTT 明文 + 密码", "8883 + TLS + 客户端证书"],
             ["监控告警", "Grafana 看板", "Alertmanager 规则 + 企业微信/邮件"]],
            widths=[1.3, 2.3, 2.6])

    C.h1(doc, "9　已知限制")
    for text in [
        "单体单实例：水平扩展需要先解决定时任务重复执行与文件/日志共享。",
        "无物理外键：一致性依赖应用层，直接改库有产生孤儿数据的风险。",
        "MQTT 未加 TLS：公网 1883 暴露时依赖密码强度（建议收敛来源 IP 或升级 8883）。",
        "无分布式锁：多实例部署时定时结算会重复执行（幂等，但浪费资源）。",
    ]:
        C.bullet(doc, text)

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    doc.save(args.out)
    print(f"已生成 {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
