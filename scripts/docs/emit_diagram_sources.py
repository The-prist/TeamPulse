#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""输出 9 张图的 UML 源码（PlantUML 为主，Mermaid 备选），供导入 UML 工具自行修改。

内容与 docs/diagrams/v2 的 9 张 PNG 完全对应（取自 make_diagrams.py 的画布元素与连线）。

用法：
    python scripts/docs/emit_diagram_sources.py --out docs/diagrams/src
"""
import argparse
import os

SOURCES = {}

# ------------------------------------------------------------------ 1 架构图
SOURCES["fig1-architecture.puml"] = r'''@startuml 图1-系统架构图
title 图 1　系统架构图（三层 + 六业务域 + 可观测）

package "终端" as EP {
  component "ESP32 打卡机\nUART 读卡 · HMAC 签名" as ESP32
  component "设备模拟器 / curl\n联调与回归用" as SIM
}

package "接入层" as GW {
  component "Nginx\n静态托管 · 反向代理 · 同源" as NGX
  component "Mosquitto\nMQTT 1883 · 密码认证" as MQ
  component "HTTP 兜底 /iot/v1\n同一套验签" as HTTPF
}

package "应用层（Spring Boot 单体）" as APP {
  component "安全过滤器链\nJWT + @PreAuthorize + 数据范围" as SEC

  package "业务域" {
    component "协作域\n用户 / 团队 / 日程\n看板 / 日志 / 评论" as D1
    component "考勤域\n设备 / 规则 / 打卡\n补卡审批" as D2
    component "权限域\n角色 / 权限\n数据范围" as D3
    component "审计域\nAOP 切面\n审计查询" as D4
    component "概览统计\n仪表盘 / 公司概览" as D5
    component "横切能力\ntraceId · 指标\n统一异常" as D6
  }

  component "MyBatis-Plus 持久层" as DAO
  component "定时结算 00:05\n日汇总重算" as CRON
}

package "数据层" as DATA {
  database "MySQL 8.0\n21 表 · utf8mb4" as MYSQL
  database "Redis 7\nnonce 防重放" as REDIS
}

package "可观测（可选）" as OBS {
  component "Prometheus\n抓取 /actuator/prometheus" as PROM
  component "Grafana\n7 个面板 · 只绑回环" as GRAF
}

ESP32 --> NGX : HTTP 80
ESP32 --> MQ : MQTT 1883
SIM --> HTTPF : HTTP
NGX --> SEC : 静态 + /api
MQ --> D2 : attendance/checkin
HTTPF --> D2
SEC --> D1
SEC --> D2
SEC --> D3
SEC --> D4
SEC --> D5
D1 --> DAO
D2 --> DAO
D3 --> DAO
D4 --> DAO
D5 --> DAO
DAO --> MYSQL : JDBC
D1 --> REDIS : SET NX
CRON --> DAO
PROM ..> APP : 指标
APP ..> PROM
GRAF --> PROM

note bottom of APP
  单体部署：一个 JVM 承载 6 个业务域；
  模块化分包（module.*）保留将来按域拆分的边界。
end note
@enduml
'''

SOURCES["fig1-architecture.mmd"] = r'''flowchart LR
  subgraph EP[终端]
    ESP32["ESP32 打卡机<br/>UART 读卡 · HMAC 签名"]
    SIM["设备模拟器 / curl<br/>联调与回归用"]
  end
  subgraph GW[接入层]
    NGX["Nginx<br/>静态托管 · 反向代理 · 同源"]
    MQ["Mosquitto<br/>MQTT 1883 · 密码认证"]
    HTTPF["HTTP 兜底 /iot/v1<br/>同一套验签"]
  end
  subgraph APP[应用层 Spring Boot 单体]
    SEC["安全过滤器链<br/>JWT + @PreAuthorize + 数据范围"]
    D1["协作域<br/>用户/团队/日程/看板/日志/评论"]
    D2["考勤域<br/>设备/规则/打卡/补卡审批"]
    D3["权限域<br/>角色/权限/数据范围"]
    D4["审计域<br/>AOP 切面/审计查询"]
    D5["概览统计<br/>仪表盘/公司概览"]
    D6["横切能力<br/>traceId · 指标 · 统一异常"]
    DAO["MyBatis-Plus 持久层"]
    CRON["定时结算 00:05<br/>日汇总重算"]
  end
  subgraph DATA[数据层]
    MYSQL[("MySQL 8.0<br/>21 表 · utf8mb4")]
    REDIS[("Redis 7<br/>nonce 防重放")]
  end
  subgraph OBS[可观测 可选]
    PROM["Prometheus<br/>抓取 /actuator/prometheus"]
    GRAF["Grafana<br/>7 个面板 · 只绑回环"]
  end
  ESP32 -->|HTTP 80| NGX
  ESP32 -->|MQTT 1883| MQ
  SIM -->|HTTP| HTTPF
  NGX -->|静态 + /api| SEC
  MQ -->|attendance/checkin| D2
  HTTPF --> D2
  SEC --> D1
  SEC --> D2
  SEC --> D3
  SEC --> D4
  SEC --> D5
  D1 --> DAO
  D2 --> DAO
  D3 --> DAO
  D4 --> DAO
  D5 --> DAO
  DAO -->|JDBC| MYSQL
  D1 -->|SET NX| REDIS
  CRON --> DAO
  APP -.->|指标| PROM
  PROM -.-> GRAF
'''

# ------------------------------------------------------------------ 2 部署图
SOURCES["fig2-deployment.puml"] = r'''@startuml 图2-部署图
title 图 2　部署图（腾讯云轻量 4C4G · Rocky Linux 10）

actor "浏览器" as BROWSER
actor "ESP32 打卡机" as ESP32
actor "运维终端" as OPS

node "云服务器 81.70.102.116（公网 IP）\nap-beijing · Docker 29.9\n盘 40G · 内存 3.7G + 2G swap · firewalld 关闭（用腾讯云控制台防火墙）" as CLOUD {

  node "Docker 网络 teampulse_default" as NET {
    component "tp-frontend\nnginx:1.27-alpine\n80:80（唯一对公网）\n托管 SPA + 反代 /api /iot" as FE
    component "tp-backend\nteampulse/backend:1.0.0\n8080（仅内网 expose）\nSpring Boot 单体" as BE
    database "tp-mysql\nmysql:8.0\n127.0.0.1:3306\n数据卷 mysql-data" as DB
    database "tp-redis\nredis:7-alpine\n6379（仅内网）· 128M LRU" as RC
    queue "tp-mosquitto\neclipse-mosquitto:2\n1883（对公网）· 密码文件挂载" as MQ
    component "tp-prometheus\nprofile=monitoring\n127.0.0.1:9090" as PROM
    component "tp-grafana\nprofile=monitoring\n127.0.0.1:3000" as GRAF
    folder "数据卷\nmysql-data / redis-data / mosquitto-data /\nbackend-logs / prometheus-data / grafana-data" as VOL
  }
}

BROWSER --> FE : "公网 http://81.70.102.116/ (80)"
ESP32 --> MQ : "MQTT 1883（需控制台放行）"
OPS --> CLOUD : "SSH 22（密钥登录）"

FE --> BE
BE --> DB
BE --> RC
BE --> MQ
PROM --> GRAF
BE ..> PROM : 指标抓取

note bottom
  8080 / 3000 / 9090 均不对公网开放；
  看监控用 SSH 隧道：ssh -L 3000:127.0.0.1:3000 root@81.70.102.116
end note
@enduml
'''

SOURCES["fig2-deployment.mmd"] = r'''flowchart TB
  B["浏览器"] -->|80 公网| FE
  E["ESP32 打卡机"] -->|1883 需放行| MQ
  O["运维终端"] -->|SSH 22 密钥| CLOUD
  subgraph CLOUD["云服务器 81.70.102.116 · ap-beijing · Docker 29.9"]
    subgraph NET["Docker 网络 teampulse_default"]
      FE["tp-frontend<br/>nginx:1.27-alpine · 80:80 唯一公网"]
      BE["tp-backend<br/>8080 仅内网 · Spring Boot 单体"]
      DB[("tp-mysql<br/>127.0.0.1:3306")]
      RC[("tp-redis<br/>6379 仅内网")]
      MQ["tp-mosquitto<br/>1883 对公网"]
      PROM["tp-prometheus<br/>127.0.0.1:9090"]
      GRAF["tp-grafana<br/>127.0.0.1:3000"]
      VOL["数据卷 mysql/redis/mosquitto/backend-logs/prometheus/grafana"]
    end
    FE --> BE
    BE --> DB
    BE --> RC
    BE --> MQ
    BE -.->|指标| PROM
    PROM --> GRAF
    DB -.-> VOL
    RC -.-> VOL
  end
'''

# ------------------------------------------------------------------ 3 业务流程图
SOURCES["fig3-business-flow.puml"] = r'''@startuml 图3-业务流程图
title 图 3　业务流程图：设备打卡上报（含异常分支）

|设备（ESP32）|
start
:读卡 → 组 JSON;
:HMAC 签名\nsign = HMAC(deviceSn|employeeNo|punchTime|nonce);
:写入 nonce;

|消息 / 接入|
:MQTT 发布 attendance/checkin;
:Nginx / Mosquitto 转发;

|后端服务|
:按 deviceSn 取 secret;
if (验签 + 时钟漂移 ±300s) then (通过)
  :nonce 去重（SET NX EX 600）;
  if (nonce 首次) then (是)
    :工号 → 用户;
    if (工号存在且规则已配) then (是)
      :按规则判定状态\n迟到 / 早退 / 正常;
      :唯一索引防重复\n(message_id、employee_no+punch_time);
    else (否)
      #FFE0E0:失败 61001 工号不存在\n61002 未配置规则;
      stop
    endif
  else (否)
    #FFE0E0:失败 60004 重放\n或 幂等返回 200;
    stop
  endif
else (否)
  #FFE0E0:失败 60003 签名错\n60006 时钟漂移;
  stop
endif

|存储|
:写 attendance_record;
:刷新 attendance_daily;
:返回 Ack(id, status, workDate);
stop

note bottom
  MQTT 与 HTTP 两个入口共用同一段处理逻辑（AttendanceService.handlePunch），
  source 字段区分 MQTT / HTTP。
end note
@enduml
'''

SOURCES["fig3-business-flow.mmd"] = r'''flowchart TB
  subgraph D[设备 ESP32]
    A1[读卡 → 组 JSON] --> A2[HMAC 签名] --> A3[写入 nonce]
  end
  subgraph M[消息 / 接入]
    B1[MQTT 发布 attendance/checkin] --> B2[Nginx / Mosquitto 转发]
  end
  subgraph S[后端服务]
    C1[按 deviceSn 取 secret] --> C2{验签 + 时钟漂移 ±300s}
    C2 -->|通过| C3{nonce 去重 SET NX}
    C3 -->|首次| C4[工号 → 用户]
    C4 --> C5{工号存在且规则已配}
    C5 -->|是| C6[按规则判定状态]
    C6 --> C7[唯一索引防重复]
  end
  subgraph T[存储]
    D1[写 attendance_record] --> D2[刷新 attendance_daily]
  end
  A3 --> B1
  B2 --> C1
  C2 -->|不通过| F1[失败 60003 签名错 / 60006 时钟漂移]
  C3 -->|已存在| F2[失败 60004 重放 / 幂等返回 200]
  C5 -->|否| F3[失败 61001 工号不存在 / 61002 未配置规则]
  C7 --> D1
'''

# ------------------------------------------------------------------ 4 用例图
SOURCES["fig4-use-case.puml"] = r'''@startuml 图4-用例图
title 图 4　用例图（6 个角色 × 15 个用例）
left to right direction
skinparam packageStyle rectangle

actor "系统管理员\nsysadmin" as A1
actor "人事\nhr" as A2
actor "部门经理\nmanager" as A3
actor "团队负责人\nleader" as A4
actor "普通员工\nemployee" as A5
actor "审计专员\nauditor" as A6

rectangle "TeamPulse 协作 + 物联考勤平台" {
  usecase "登录 / 个人信息" as UC01
  usecase "用户与角色管理" as UC02
  usecase "设备注册与密钥轮换" as UC03
  usecase "考勤规则配置" as UC04
  usecase "补卡审批" as UC05
  usecase "公司考勤概览" as UC06
  usecase "部门成员打卡查询" as UC07
  usecase "团队日程管理" as UC08
  usecase "评论与敏感词" as UC09
  usecase "团队与成员维护" as UC10
  usecase "看板协作" as UC11
  usecase "日程 / 日志查看" as UC12
  usecase "我的打卡与补卡申请" as UC13
  usecase "我的日程 / 日志 / 看板" as UC14
  usecase "审计日志查询（全公司只读）" as UC15
}

A1 --> UC01
A1 --> UC02
A1 --> UC03
A1 --> UC05
A1 --> UC07
A1 --> UC09

A2 --> UC01
A2 --> UC04
A2 --> UC05
A2 --> UC06

A3 --> UC01
A3 --> UC07
A3 --> UC08
A3 --> UC09
A3 --> UC05

A4 --> UC01
A4 --> UC10
A4 --> UC11
A4 --> UC12

A5 --> UC01
A5 --> UC13
A5 --> UC14

A6 --> UC01
A6 --> UC15

note bottom
  「补卡审批」的操作为 人事 / 部门经理 / 系统管理员；普通员工只能提交补卡申请。
  审计专员的全部用例均为只读。
end note
@enduml
'''

# ------------------------------------------------------------------ 5 时序图
SOURCES["fig5-sequence.puml"] = r'''@startuml 图5-时序图
title 图 5　时序图：打卡上报（验签 → 防重放 → 幂等落库）

participant "ESP32" as DEV
participant "Mosquitto" as MQ
participant "Backend" as BE
participant "Redis" as RDS
participant "MySQL" as DB

DEV -> MQ : publish checkin{sn, emp, time, nonce, sign}
MQ -> BE : 投递（订阅者 teampulse-backend-1）
BE -> DB : 按 deviceSn 取 secret
DB --> BE : secret
BE -> BE : 校验 HMAC 与时钟漂移(±300s)
BE -> RDS : SET NX nonce EX 600
RDS --> BE : 首次 = true
BE -> DB : INSERT attendance_record
DB --> BE : 唯一键冲突 → 幂等返回 200
BE -> DB : 刷新 attendance_daily
BE -> BE : 指标 punch_total{result}
BE --> MQ : Ack(id, status, workDate)
MQ --> DEV : 上报结果

note over BE
  ① 幂等三层：message_id 唯一索引（报文重投）
     / nonce 防重放（攻击）
     / employee_no + punch_time 唯一索引（业务重复）
  ② 错误码：验签失败 60003、时钟漂移 60006、重放 60004、
     工号不存在 61001、规则缺失 61002
end note
@enduml
'''

SOURCES["fig5-sequence.mmd"] = r'''sequenceDiagram
  autonumber
  participant DEV as ESP32
  participant MQ as Mosquitto
  participant BE as Backend
  participant RDS as Redis
  participant DB as MySQL
  DEV->>MQ: publish checkin{sn,emp,time,nonce,sign}
  MQ->>BE: 投递（订阅者 teampulse-backend-1）
  BE->>DB: 按 deviceSn 取 secret
  DB-->>BE: secret
  BE->>BE: 校验 HMAC 与时钟漂移 ±300s
  BE->>RDS: SET NX nonce EX 600
  RDS-->>BE: 首次 = true
  BE->>DB: INSERT attendance_record
  DB-->>BE: 唯一键冲突 → 幂等返回 200
  BE->>DB: 刷新 attendance_daily
  BE->>BE: 指标 punch_total{result}
  BE-->>MQ: Ack(id,status,workDate)
  MQ-->>DEV: 上报结果
'''

# ------------------------------------------------------------------ 6 状态图
SOURCES["fig6-state.puml"] = r'''@startuml 图6-状态图
title 图 6　状态图：补卡审批 与 设备状态

state "（a）补卡申请状态机" as Makeup {
  [*] --> 提交 : 员工提交补卡
  提交 : status = pending
  提交 --> 审批中 : 进入待办
  审批中 : status = pending
  审批中 --> 已通过 : approved = true
  审批中 --> 已驳回 : approved = false
  已通过 : status = approved
  已驳回 : status = rejected
  已通过 --> 回写打卡记录 : 事务内
  回写打卡记录 : source = MANUAL\n刷新日汇总
  回写打卡记录 --> [*]
  已驳回 --> [*]
  回写打卡记录 --> 审批中 : 重复审批 → 409
}

state "（b）设备状态" as Device {
  [*] --> 未注册 : 出厂 / 新设备
  未注册 --> 已注册 : 后台注册 → 下发密钥
  已注册 : status = active\n离线标记 OFFLINE
  已注册 --> 已禁用 : 停用
  已禁用 : status = DISABLED
  已禁用 --> 已注册 : 启用
  已注册 --> 密钥轮换 : 轮换密钥（仅 active）
  密钥轮换 : 旧密钥立即失效
  密钥轮换 --> 已注册
}

note bottom of Device
  在线状态由心跳更新（MQTT attendance/heartbeat，60s 一次）；
  超过 180s 无心跳标记 OFFLINE。
end note
@enduml
'''

SOURCES["fig6-state.mmd"] = r'''stateDiagram-v2
  direction LR
  state "补卡申请" as MK {
    [*] --> pending : 员工提交
    pending --> pending : 进入待办
    pending --> approved : approved=true
    pending --> rejected : approved=false
    approved --> MANUAL : 事务内回写打卡记录
    MANUAL --> [*]
    rejected --> [*]
    MANUAL --> pending : 重复审批 409
  }
  state "设备" as DEV {
    [*] --> 未注册
    未注册 --> active : 后台注册下发密钥
    active --> DISABLED : 停用
    DISABLED --> active : 启用
    active --> 密钥轮换 : 轮换密钥
    密钥轮换 --> active
  }
'''

# ------------------------------------------------------------------ 7 类图
SOURCES["fig7-class.puml"] = r'''@startuml 图7-类图
title 图 7　类图：核心领域模型（节选，字段已简化）
skinparam classAttributeIconSize 0

package "组织与权限" {
  class User {
    - userId : Long
    - username : String
    - role : String
    - departmentId : Long
    - employeeNo : String
  }
  class Department {
    - departmentId : Long
    - parentId : Long
    - deptCode : String
    - leaderId : Long
  }
  class Role {
    - roleKey : String
    - roleName : String
    - dataScope : ALL/DEPT/SELF
  }
  class Permission {
    - permKey : String
    - permName : String
    - module : String
  }
  class RolePermission {
    - roleKey : String
    - permKey : String
  }
}

package "协作域" {
  class Team {
    - teamId : Long
    - teamName : String
    - leaderId : Long
  }
  class TeamMember {
    - teamId : Long
    - userId : Long
    - teamRole : leader/member
  }
  class Schedule {
    - scheduleId : Long
    - creatorId : Long
    - teamId : Long
    - startTime : Date
  }
  class Kanban {
    - kanbanId : Long
    - teamId : Long
    - creatorId : Long
  }
  class Worklog {
    - worklogId : Long
    - creatorId : Long
    - content : Text
  }
  class Comment {
    - commentId : Long
    - creatorId : Long
    - relatedType : String
    - relatedId : Long
    - content : Text
  }
}

package "考勤域" {
  class IotDevice {
    - deviceSn : String
    - secret : String(64hex)
    - status : active/DISABLED
  }
  class AttendanceRecord {
    - recordId : Long
    - userId : Long
    - deviceSn : String
    - punchTime : DateTime
    - messageId : String(UK)
  }
  class AttendanceDaily {
    - userId : Long
    - workDate : Date
    - status : NORMAL/LATE/…
  }
  class MakeupRequest {
    - requestId : Long
    - userId : Long
    - status : pending/approved
    - approverId : Long
  }
}

package "审计" {
  class AuditLog {
    - auditId : Long
    - operatorId : Long
    - module : String
    - action : String
    - result : SUCCESS/FAILURE
    - traceId : String
  }
}

Department "1" --> "N" User : 归属
User "N" --> "1" Role : 岗位
Role "1" --> "N" RolePermission
Permission "1" --> "N" RolePermission
Team "1" --> "N" TeamMember : 成员
TeamMember "N" --> "1" User
IotDevice "1" --> "N" AttendanceRecord : 上报
AttendanceRecord "N" --> "1" User
AttendanceDaily "1" --> "N" AttendanceRecord : 汇总
MakeupRequest "N" --> "1" AttendanceRecord : 审批通过→写记录
AuditLog ..> User : 操作留痕
Schedule "1" --> "N" Comment : 评论
Worklog "1" --> "N" Comment : 评论

note bottom
  全库无物理外键，上面所有连线都是「逻辑关联」（见数据库设计说明书第 6 章）。
  唯一索引承担业务约束：attendance_record(message_id)、(employee_no, punch_time)。
end note
@enduml
'''

SOURCES["fig7-class.mmd"] = r'''classDiagram
  class User {
    Long userId
    String username
    String role
    Long departmentId
    String employeeNo
  }
  class Department {
    Long departmentId
    Long parentId
    String deptCode
    Long leaderId
  }
  class Role {
    String roleKey
    String dataScope
  }
  class Permission {
    String permKey
    String module
  }
  class Team {
    Long teamId
    String teamName
  }
  class TeamMember {
    Long teamId
    Long userId
    String teamRole
  }
  class Schedule {
    Long scheduleId
    Long creatorId
    Date startTime
  }
  class Kanban {
    Long kanbanId
    Long teamId
  }
  class IotDevice {
    String deviceSn
    String secret
    String status
  }
  class AttendanceRecord {
    Long recordId
    String employeeNo
    DateTime punchTime
    String messageId
  }
  class AttendanceDaily {
    Long userId
    Date workDate
    String status
  }
  class MakeupRequest {
    Long requestId
    String status
    Long approverId
  }
  class AuditLog {
    Long auditId
    String module
    String action
    String result
  }
  Department "1" --> "N" User
  User "N" --> "1" Role
  Team "1" --> "N" TeamMember
  TeamMember "N" --> "1" User
  IotDevice "1" --> "N" AttendanceRecord
  AttendanceRecord "N" --> "1" User
  AttendanceDaily "1" --> "N" AttendanceRecord
  MakeupRequest "N" --> "1" AttendanceRecord
  AuditLog ..> User
'''

# ------------------------------------------------------------------ 8 组件图
SOURCES["fig8-component.puml"] = r'''@startuml 图8-组件图
title 图 8　组件图：前端组件与后端模块依赖（41 个 Vue / 116 个 Java）

package "前端（Vue 3 + Vite）" as FE {
  component "layouts\nAdminLayout / UserLayout" as L
  component "views（37 个页面）\nattendance / schedule / kanban\nteams / worklog / comment\nusers / audit / overview / user" as V
  component "components（5 个）\n布局 · 图表 · 空状态" as C
  component "router（31 路由）\nmeta.perms 逐页权限 + 守卫拦截" as R
  component "stores（Pinia）\nauth：用户/权限/hasPerm\nui：偏好面板" as S
  component "api（Axios 封装）\n统一 token 注入 · 统一错误分层" as API
  component "utils\nnetErrors · 字典 · 格式化" as U
  component "echarts / element-plus\n图表与组件库" as LIB
}

package "后端（Spring Boot 单体）" as BE {
  component "controller（16 个）\n@PreAuthorize 34 处\n统一 ApiResponse" as CTL
  component "service（业务域）\nattendance · rbac · kanban\nteam · schedule · worklog\ncomment · dashboard · audit" as SVC
  component "mapper + entity\nMyBatis-Plus · 21 张表" as MAP
  component "config / security\nJWT 过滤链 · SecurityConfig\n可观测性 · OpenAPI · 启动初始化" as CFG
  component "横切组件\nAuditLogAspect（AOP）\nGlobalExceptionHandler\ntraceId + Micrometer" as X
  component "定时任务\n@Scheduled 00:05 日汇总重算" as JOB
}

L --> V
V --> C
V --> API
API --> R
R --> S
S --> API
V --> U
V --> LIB
API --> CTL : HTTP /api/v1
CTL --> SVC : 调用
SVC --> MAP
CTL ..> X : 切面
SVC ..> X
CFG ..> CTL : 过滤器链
JOB --> SVC
S ..> CTL : 携带 token

note bottom
  components 目录只有 5 个通用组件，业务页面直接写在 views 里——
  这是单人项目刻意的取舍（少一层抽象，改起来更快）。
end note
@enduml
'''

SOURCES["fig8-component.mmd"] = r'''flowchart LR
  subgraph FE[前端 Vue 3]
    L[layouts] --> V[views 37 页]
    V --> C[components 5 个]
    V --> API[api Axios 封装]
    API --> R[router 31 路由 · meta.perms]
    R --> S[stores Pinia]
    V --> U[utils]
    V --> LIB[echarts / element-plus]
  end
  subgraph BE[后端 Spring Boot]
    CTL[controller 16 个 · 34 处 @PreAuthorize]
    SVC[service 业务域 9 个]
    MAP[mapper + entity · 21 表]
    CFG[config / security]
    X[横切组件 AOP · 异常 · traceId]
    JOB[定时任务 00:05]
    CTL --> SVC --> MAP
    CTL -.-> X
    CFG -.-> CTL
    JOB --> SVC
  end
  API -->|HTTP /api/v1| CTL
'''

# ------------------------------------------------------------------ 9 数据模型图
SOURCES["fig9-data-model.puml"] = r'''@startuml 图9-数据模型图
title 图 9　数据模型图（21 表的核心关系，无物理外键）
hide circle
skinparam linetype ortho

entity "department" as department {
  * department_id : bigint <<PK>>
  --
  parent_id : bigint
  dept_name / dept_code
  leader_id : bigint
}

entity "user" as user {
  * user_id : bigint <<PK>>
  --
  username / password
  role : varchar  (RBAC key)
  department_id : bigint
  employee_no : varchar
}

entity "role" as role {
  * role_id : bigint <<PK>>
  --
  role_key : varchar
  data_scope : ALL/DEPT/SELF
}

entity "permission" as permission {
  * permission_id : bigint <<PK>>
  --
  perm_key : varchar
  module : varchar
}

entity "role_permission" as role_permission {
  * id : bigint <<PK>>
  --
  role_key : varchar
  perm_key : varchar
}

entity "team" as team {
  * team_id : bigint <<PK>>
  --
  team_name : varchar
  leader_id : bigint
  creator_id : bigint
}

entity "team_member" as team_member {
  * id : bigint <<PK>>
  --
  team_id : bigint
  user_id : bigint
  team_role : leader/member
}

entity "schedule" as schedule {
  * schedule_id : bigint <<PK>>
  --
  creator_id : bigint
  team_id : bigint
  start_time / end_time
}

entity "kanban" as kanban {
  * kanban_id : bigint <<PK>>
  --
  team_id : bigint
  creator_id : bigint
}

entity "kanban_column" as kanban_column {
  * column_id : bigint <<PK>>
  --
  kanban_id : bigint
  sort_order : int
}

entity "kanban_item" as kanban_item {
  * item_id : bigint <<PK>>
  --
  column_id : bigint
  assignee_id : bigint
}

entity "iot_device" as iot_device {
  * id : bigint <<PK>>
  --
  device_sn : varchar <<UK>>
  secret : char(64)
  status : varchar
}

entity "attendance_record" as attendance_record {
  * id : bigint <<PK>>
  --
  user_id / employee_no
  device_sn : varchar
  punch_time : datetime
  message_id : varchar <<UK>>
  work_date + status
}

entity "attendance_daily" as attendance_daily {
  * id : bigint <<PK>>
  --
  user_id + work_date <<UK>>
  status / late_minutes
}

entity "attendance_rule" as attendance_rule {
  * id : bigint <<PK>>
  --
  work_start / work_end
  late_grace_minutes
}

entity "makeup_request" as makeup_request {
  * request_id : bigint <<PK>>
  --
  user_id / approver_id
  status : pending/…
  punch_date : date
}

entity "audit_log" as audit_log {
  * audit_id : bigint <<PK>>
  --
  operator_id : bigint
  module / action
  result : varchar
  trace_id : varchar
}

entity "worklog" as worklog {
  * worklog_id : bigint <<PK>>
  --
  creator_id : bigint
  content : text
}

entity "comment" as comment {
  * comment_id : bigint <<PK>>
  --
  creator_id : bigint
  related_type / related_id
  reply_to : bigint
}

entity "sensitive_word" as sensitive_word {
  * word_id : bigint <<PK>>
  --
  word : varchar <<UK>>
  enabled : tinyint
}

entity "flyway_schema_history" as flyway_schema_history {
  * installed_rank : int <<PK>>
  --
  version : varchar
  success : tinyint
}

department ||--o{ user : 归属
role ||--o{ user : 岗位
role ||--o{ role_permission
permission ||--o{ role_permission
team ||--o{ team_member
user ||--o{ team_member
team ||--o{ schedule
user ||--o{ schedule
team ||--o{ kanban
kanban ||--o{ kanban_column
kanban_column ||--o{ kanban_item
iot_device ||--o{ attendance_record : 上报
user ||--o{ attendance_record
user ||--o{ attendance_daily
attendance_record ||--o{ attendance_daily : 按天汇总
makeup_request ||--o| attendance_record : 审批通过写记录
user ||--o{ audit_log : 操作留痕
user ||--o{ worklog
user ||--o{ comment
schedule ||--o{ comment
worklog ||--o{ comment

note bottom
  主键统一 bigint 自增；逻辑删除 deleted；无 FOREIGN KEY（见 ADR-02）。
  唯一索引：attendance_record(message_id)、(employee_no, punch_time)、
  user(username / employee_no)、attendance_daily(user_id, work_date)。
end note
@enduml
'''

SOURCES["fig9-data-model.mmd"] = r'''erDiagram
  department ||--o{ user : 归属
  role ||--o{ user : 岗位
  role ||--o{ role_permission : ""
  permission ||--o{ role_permission : ""
  team ||--o{ team_member : ""
  user ||--o{ team_member : ""
  team ||--o{ schedule : ""
  team ||--o{ kanban : ""
  kanban ||--o{ kanban_column : ""
  kanban_column ||--o{ kanban_item : ""
  iot_device ||--o{ attendance_record : 上报
  user ||--o{ attendance_record : ""
  user ||--o{ attendance_daily : ""
  attendance_record ||--o{ attendance_daily : 按天汇总
  makeup_request ||--o| attendance_record : 审批通过写记录
  user ||--o{ audit_log : 操作留痕
  user ||--o{ worklog : ""
  user ||--o{ comment : ""
  schedule ||--o{ comment : ""
  worklog ||--o{ comment : ""

  user {
    bigint user_id PK
    varchar username
    varchar role
    bigint department_id
    varchar employee_no
  }
  attendance_record {
    bigint id PK
    varchar employee_no
    datetime punch_time
    varchar message_id UK
    varchar status
  }
  attendance_daily {
    bigint id PK
    bigint user_id UK
    date work_date UK
    varchar status
    int late_minutes
  }
  iot_device {
    bigint id PK
    varchar device_sn UK
    char secret
    varchar status
  }
  makeup_request {
    bigint request_id PK
    bigint user_id
    varchar status
    bigint approver_id
  }
'''


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    for name, src in sorted(SOURCES.items()):
        path = os.path.join(args.out, name)
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(src)
        print(f"  ✓ {name:<34} {len(src.splitlines()):>4} 行")
    print(f"\n输出 {len(SOURCES)} 个文件 → {args.out}")


if __name__ == "__main__":
    main()
