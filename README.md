# TeamPulse · 团队协作与物联网考勤平台

> 一套账号与权限体系，同时覆盖「团队协作」与「硬件打卡考勤」。
> ESP32 打卡机经 MQTT 上报，服务端验签、防重放、幂等判定后实时刷新考勤看板；
> 所有管理动作留痕可查。

![状态](https://img.shields.io/badge/阶段-核心功能完成-blue)
![后端](https://img.shields.io/badge/后端-Java%2017%20%2F%20Spring%20Boot%203.2-6db33f)
![前端](https://img.shields.io/badge/前端-Vue%203%20%2F%20Vite%207-42b883)
![数据库](https://img.shields.io/badge/数据库-MySQL%208.4-00758f)
![权限](https://img.shields.io/badge/RBAC-6%20角色%20%C2%B7%2022%20权限点-8b5cf6)

---

## 1. 它解决什么问题

中小企业常见状态是：协作工具（日程/看板/日志）和考勤机是两套互不相通的系统，
人员名单要维护两遍，考勤数据只能月底从机器里导表格。

TeamPulse 把这些收进同一个平台、同一份人员数据：

| 域 | 能力 |
|---|---|
| 协作域 | 用户与团队管理、日程（含跨天日历）、看板（拖拽改状态并反向同步日程）、工作日志、评论（两级回复 + 敏感词过滤） |
| 考勤域 | 设备注册与密钥轮换、ESP32/MQTT 打卡接入、验签与防重放、迟到早退判定、日汇总、缺勤补齐、考勤看板与打卡分析 |
| 权限域 | 6 个角色、22 个权限点、部门树、数据范围（全公司/本部门/仅本人）、菜单与按钮按权限渲染 |
| 治理域 | 补卡审批流（申请 → 审批 → 生成记录）、操作审计日志（谁改了什么、成没成、对应哪次请求） |

---

## 2. 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.2.4 · Java 17 · MyBatis-Plus 3.5.7 · MySQL 8.4 · Redis 7 · Flyway 9 |
| 安全与权限 | Spring Security 6 + JWT（jjwt 0.12）· BCrypt（兼容历史明文自动升级）· RBAC 权限点 + 数据范围 |
| 治理 | Spring AOP（审计切面）· Micrometer + Prometheus · Logback（带 traceId 的滚动日志） |
| 物联网 | Spring Integration MQTT 6.2 + Eclipse Paho · Mosquitto · ESP32（Arduino C++） |
| 前端 | Vue 3 · Vite 7 · Element Plus 2.14 · Pinia · ECharts · Axios |
| 部署 | Docker Compose（5 容器）· Nginx 同源反代 · 单台 4C4G 云服务器 |
| 工程 | GitHub Actions（后端测试 + 前端构建 + 契约检查）· 单元测试（JUnit 5） |

---

## 3. 架构

```
浏览器 ──► Nginx ──/api/──► backend (Spring Boot)
                        ├── MySQL    业务数据（21 张表，Flyway V1~V6）
                        ├── Redis    nonce 防重放（不可用时降级内存）
                        └── Mosquitto ◄── ESP32 打卡机 (MQTT)
```

一次打卡的链路：

```
指纹模块 --UART--> ESP32 --拼 JSON + HMAC 签名--> MQTT attendance/checkin
    --> Spring Integration 入站适配器
    --> 校验(设备 / 时钟漂移 ±300s / 签名 / 三层幂等 / nonce 10 分钟去重)
    --> 判定签到签退 + 迟到早退
    --> 写 attendance_record + 重算 attendance_daily
    --> 回执 attendance/ack
前端考勤看板每 5 秒轮询 /api/v1/attendance/recent
```

一次管理动作的链路（审计）：

```
PUT /api/v1/attendance/rule  ──► RequestObservabilityFilter（生成 traceId，写 MDC）
    ──► AuditLogAspect（@Audited 拦截）──► audit_log（操作人快照/动作/结果/耗时/IP/traceId）
    ──► Prometheus 指标（teampulse_api_requests_total 等）
    ──► logs/teampulse.log（每条日志带同一个 traceId）
```

---

## 4. 角色与权限

6 个角色、22 个权限点（`module:action`），数据范围挂在角色上，由 `DataScopeService` 下推到 SQL。
完整矩阵见 [`docs/TeamPulse_需求规格说明书.docx`](docs/TeamPulse_需求规格说明书.docx) 第 4 章。

| 角色 | 数据范围 | 权限数 | 职责边界 |
|---|---|---|---|
| `sysadmin` 系统管理员 | ALL | 22 | 账号、权限、部门、设备、系统配置；不做业务审批决策 |
| `hr` 人事专员 | ALL | 12 | 考勤规则、补卡审批、考勤报表；员工档案只读 |
| `manager` 部门经理 | DEPT | 14 | 本部门团队/日程/看板/日志 + 考勤异常审批 |
| `leader` 团队负责人 | DEPT | 8 | 本部门协作与日程管理；无审批权 |
| `employee` 普通员工 | SELF | 6 | 自己的日程/看板/日志/考勤；可提交补卡申请 |
| `auditor` 审计专员 | ALL | 10 | 全公司只读 + 审计日志查询 |

实测差异（同一批数据，不同账号）：

| 账号 | 角色 | 可见日程 | 可见打卡 |
|---|---|---|---|
| `admin` | 系统管理员 | 11 条 | 84 条 |
| `zhangsan` | 部门经理 | 4 条（仅研发部） | 56 条（研发部 4 人） |
| `lisi` | 普通员工 | 1 条（仅自己） | 14 条（仅自己） |

---

## 5. 快速开始

### 5.1 方式一：Docker Compose（推荐）

前置：一台装了 Docker 与 Compose v2 的 Linux 服务器（4C4G 起）。

```bash
git clone <your-repo> teampulse && cd teampulse

# 1) 准备环境变量（务必修改所有 CHANGE_ME）
cp .env.example .env
vim .env

# 2) 生成 MQTT 账号密码文件（内容是哈希，不能手写）
chmod +x deploy/scripts/*.sh
./deploy/scripts/gen-mosquitto-passwd.sh

# 3) 启动
docker compose up -d --build

# 4) 查看状态与日志
docker compose ps
docker compose logs -f backend
```

首次启动会自动完成：

1. Flyway 执行 `V1`~`V6`：建表 + 字典种子 + RBAC/部门/审批 + 审计表
2. 创建初始管理员（密码见 `.env` 的 `TPP_ADMIN_PASSWORD`）
3. 创建一台演示打卡设备，并在**后端日志里打印序列号与密钥**——填进模拟器即可跑通链路

```bash
docker compose logs backend | grep -A3 "演示打卡设备"
```

访问 `http://<服务器IP>/`，用管理员登录。

> 云服务器安全组需放行：`80`（Web）、`1883`（MQTT，供打卡机接入）。
> `3306` 已在 compose 中绑定 `127.0.0.1`，公网不可直连。

> **已验证**：本机（Windows 11 + Docker Desktop/WSL2）已用它跑通 5 容器全链路，
> 清空数据卷后全新启动同样通过（Flyway V1~V7、21 表、admin 登录 22 权限点、关键接口全 200），
> 过程与踩坑见 [`docs/部署验证记录.md`](docs/部署验证记录.md)。
>
> **云上已跑通**：腾讯云轻量（Rocky Linux 10 / 4C4G）部署完成，公网接口断言 43/43 通过，详见 [`docs/部署验证记录.md`](docs/部署验证记录.md) §九。
> **尚未验证**：域名 + HTTPS、MQTT TLS、备份恢复演练。

### 5.2 方式二：本地开发（看页面原型用这个）

前置：JDK 17、Maven 3.9、Node 20+、MySQL 8。Redis 与 MQTT 都是**可选**的
（缺 Redis 时 nonce 去重自动降级为内存实现；缺 MQTT 时后端告警重试，Web 接口不受影响）。

**一键启动（推荐）**：

```powershell
# Windows
.\scripts\run-local.ps1 -DbPassword 你的密码   # 必须传密码（脚本无默认值）
.\scripts\run-local.ps1 -Stop                 # 停止
```

```bash
# macOS / Linux
DB_PASSWORD=你的密码 ./scripts/run-local.sh
./scripts/run-local.sh --stop
```

**手动启动**：

```bash
# 后端
cd backend
DB_PASSWORD=你的密码 MQTT_ENABLED=false mvn spring-boot:run

# 前端（另开终端）
cd frontend
pnpm install
pnpm dev          # http://localhost:5173
```

本地演示账号（迁移脚本会带上，仅用于开发）：

| 账号 | 密码 | 角色 | 看点 |
|---|---|---|---|
| `admin` | `admin123` | 系统管理员 | 全部菜单、审计日志、设备管理 |
| `zhangsan` | `123456` | 部门经理 | 数据范围收窄、补卡审批 |
| `lisi` | `123456` | 普通员工 | 只见本人数据、提交补卡 |
| `sunqi` | `123456` | 人事专员 | 考勤规则 + 审批 |
| `zhaoliu` | `123456` | 审计专员 | 全公司只读 + 审计日志 |

---

## 6. 设备接入

### 6.1 用模拟器验证（不需要硬件）

```bash
cd device/simulator
node http-simulator.mjs --server http://127.0.0.1:8080 \
  --sn <设备序列号> --secret <设备密钥> --employee-no E1001
```

### 6.2 真机固件

`device/esp32/attendance_node/attendance_node.ino`（单文件约 1 100 行）：
UART 读卡 → 组 JSON → `mbedtls` 计算 HMAC → PubSubClient 发布；`millis()` 非阻塞状态机 + WiFi 断线重连。

### 6.3 报文契约

| 通道 | 主题 / 路径 | 待签名串 |
|---|---|---|
| MQTT 打卡 | `attendance/checkin` | `deviceSn\|employeeNo\|punchTime\|nonce` |
| MQTT 心跳 | `attendance/heartbeat` | `deviceSn\|nonce` |
| HTTP 兜底 | `POST /iot/v1/punch` | 同上 |

---

## 7. 安全与密钥管理

### 7.1 设备密钥怎么流转

1. 后端首次启动，`BootstrapDataInitializer` 用 `SecureRandom` 生成 32 字节随机数，
   转成 64 位十六进制写入 `iot_device.secret`，并打印到启动日志
2. 固件把同一份密钥填进 `secrets.h` 的 `DEVICE_SECRET`
3. 每次打卡，固件对规范串做 **HMAC-SHA256**，签名随报文一起发出
4. 服务端用库里同一份密钥重算并比对，不一致返回 `60003`
5. `nonce` 进 Redis 去重窗口（默认 10 分钟），重复即拒绝；时间偏差超过 ±300 秒拒绝

因此密钥泄露等于别人可以伪造打卡数据——这也是它必须只留在本机的原因。

### 7.2 生产环境 fail-closed

| 位置 | 行为 |
|---|---|
| `application-prod.yml`：`teampulse.jwt.secret: ${TPP_JWT_SECRET:}` | 漏配时直接抛异常拒绝启动，**不会静默回退**到开发密钥 |
| `docker-compose.yml`：`TPP_ADMIN_PASSWORD: "${TPP_ADMIN_PASSWORD:?...}"` | 漏配时 compose 直接报错，**不会**用弱口令建管理员 |

### 7.3 提交前的自动检查

仓库自带 `.githooks/pre-commit`，提交时拦截四类问题（`.env`/`secrets.h`/64 位十六进制密钥/私钥内容）。
克隆后启用一次：

```bash
git config core.hooksPath .githooks
```

---

## 8. 可观测性

| 能力 | 实现 | 怎么看 |
|---|---|---|
| 请求串联 | `RequestObservabilityFilter` 注入/继承 `X-Trace-Id`，写 MDC 并回传响应头 | `curl -i` 看响应头；前端失败提示条会显示 traceId |
| 结构化日志 | `logback-spring.xml`：控制台 + 滚动文件，格式 `[%X{traceId}]` | `logs/teampulse.log`（20MB×14 天，总量封顶 500MB） |
| RED 指标 | `teampulse_api_requests_total{method,uri,status}`、`teampulse_api_failures_total`、`teampulse_api_latency_seconds` | `curl -s localhost:8080/actuator/prometheus` |
| 业务错误码 | `teampulse_api_errors_total{code}`（如 `60006` 时钟漂移） | 同上 |
| 打卡流水 | `teampulse_attendance_punch_total{result}`、`teampulse_attendance_idempotent_total{layer}` | 同上 |
| 设备连接 | `teampulse_mqtt_connected_total`、`teampulse_mqtt_connect_failed_total{reason}` | 同上 |
| 健康检查 | `/actuator/health`（含 DB、Redis、磁盘） | `curl -s localhost:8080/actuator/health` |

> 指标端点默认放行给抓取端；生产请在 Nginx/安全组层面限制来源 IP，不要直接暴露公网。

**监控栈已可一键启动**（`--profile monitoring`，本地实测通过）：

| 组件 | 地址 | 说明 |
|---|---|---|
| Prometheus | `http://127.0.0.1:9090` | 抓 `backend:8080/actuator/prometheus`，15s 一次，保留 15 天 |
| Grafana | `http://127.0.0.1:3000` | 预置 Prometheus 数据源 + 「TeamPulse 概览」看板（请求速率/错误率/P50·P95·P99/打卡成功失败/幂等命中/错误码分布/JVM 堆） |

```bash
docker compose --profile monitoring up -d prometheus grafana
```

两个端口默认只绑 `127.0.0.1`，外网请用 SSH 隧道，不要直接开放。

排障三条线索可以互相对齐：

```
页面上的 traceId  ↦  logs/teampulse.log 里同 traceId 的所有行
                  ↦  audit_log.trace_id（谁干的）
                  ↦  Prometheus 的 errors_total{code=...}（错在哪类）
```

---

## 9. 审计与合规

| 项 | 说明 |
|---|---|
| 留痕范围 | 20 个写操作：用户增改删/重置密码、团队增改删/加人移人、手工补卡、改考勤规则、审批补卡、设备注册/改/轮换密钥/删除、敏感词增改删、删评论 |
| 记录内容 | 操作人（昵称与角色**快照**）、动作、资源与资源 id、结果（SUCCESS/FAILURE）、耗时、IP、User-Agent、traceId |
| 失败也留痕 | 业务异常穿过切面时先记录 FAILURE 与错误信息，再原样抛出 |
| 不可篡改 | `audit_log` 无 `updated_at`、无 `deleted`，代码层与接口层都不提供修改/删除入口 |
| 谁能查 | `audit:view`：系统管理员、人事专员、审计专员 |
| 界面 | 「系统 → 审计日志」页：按人/模块/动作/结果/时间/traceId 过滤，traceId 可一键复制 |

---

## 10. 测试与 CI

**一条命令跑完三层检查**：

```powershell
.\scripts\run-tests.ps1              # 后端单测 + 前端构建 + 46 项接口断言
.\scripts\run-tests.ps1 -ReadOnly    # 跳过写数据用例（打卡写库、审批）
```

| 层 | 内容 | 结果 |
|---|---|---|
| 后端单元测试 | JUnit 5，考勤计算（迟到/早退/签到签退） | ✅ 11 / 11 |
| 前端构建校验 | `vite build`，能发现模板/语法/引用错误 | ✅ 2362 模块 |
| 接口端到端 | 认证、6 角色权限矩阵、数据范围、打卡验签/漂移/幂等、补卡审批、审计留痕、分页 | ✅ **46 / 46** |
| 集成测试 | Testcontainers（真起 MySQL 容器）：迁移跑通、bootstrap 管理员角色、未认证 401、打卡验签+幂等、数据范围隔离 | ⚠️ 已实现；**本机跳过**（见下） |
| 压测 | QPS / P99 | ❌ 未做（性能数字均为设计目标，非实测） |

明细报告：[`docs/测试报告.md`](docs/测试报告.md)（由 `scripts/run-tests.ps1` 运行后自动生成）。

> **集成测试现状**：`TeamPulseIntegrationTest`（5 个用例）用 `@Testcontainers(disabledWithoutDocker = true)`，
> 本机 Docker Desktop 引擎 API 为 1.56，而 docker-java 握手不兼容（`Could not find a valid Docker environment`），
> 因此本地 `mvn test` 显示 **11 通过 + 5 跳过**（BUILD SUCCESS）；在标准 Docker 环境（CI runner / Linux 服务器）会真实执行。

**压测**（零依赖脚本 + 5 万行合成数据）：

```powershell
docker exec -i tp-mysql mysql -uroot -p<密码> teampulse < tests/perf/seed-perf-data.sql
node tests/perf/load.mjs --base http://127.0.0.1 --admin-password <密码> `
  --path '/api/v1/attendance/records?page=1&size=20' --concurrency 20 --duration 15
```

实测（20 并发）：健康检查 p50 11ms、打卡记录 p50 31ms / p95 40ms / p99 46ms，0 失败；
SQL 层最差 0.62ms —— **瓶颈不在数据库**，详见 [`docs/性能测试报告.md`](docs/性能测试报告.md)。
用例说明与参数见 [`tests/README.md`](tests/README.md)。

**CI**：`.github/workflows/ci.yml` 有 3 个 job（后端测试 / 前端构建 / 契约检查）。
当前运行状态为失败，原因与代码无关：

> `The job was not started because your account is locked due to a billing issue.`
> —— GitHub 账号账单问题（job 未分配 runner），处理账单后手动触发即可。

---

## 11. 项目结构与文档

```
teampulse/
├── backend/            Spring Boot 后端（CONVENTIONS.md 是开发契约）
├── frontend/           Vue 3 前端（含生产 Nginx 配置）
├── device/             物联网设备侧（ESP32 固件 + 模拟器）
├── deploy/             部署资产（MySQL/MQTT 配置、发布与备份脚本）
├── docs/               文档（需求规格、交接文档、用例图/ER 图）
├── .github/workflows/  CI 流水线
├── docker-compose.yml  五容器编排
└── .env.example        环境变量模板
```

规模：**125 个 Java 文件 / 9 841 行**、**16 个控制器 / 90 个 REST 接口**、
**42 个 Vue 组件 / 16 586 行**、**21 张表 / 6 个迁移**、固件约 1 100 行。

| 文档 | 内容 |
|---|---|
| [`docs/TeamPulse_需求规格说明书.docx`](docs/TeamPulse_需求规格说明书.docx) | 需求基线：项目背景、29 项总目标与达成状态、6 角色矩阵、83 项功能清单、范围边界（明确不做微服务与分布式）、非功能需求、17 条已知限制 |
| [`docs/TeamPulse_全过程交接文档.md`](docs/TeamPulse_全过程交接文档.md) | 从老项目重构到 RBAC/审计/观测的完整过程、Bug 与踩坑记录、环境约定、验证基线、未完成事项 |
| [`docs/角色与权限矩阵.md`](docs/角色与权限矩阵.md) | 6 角色的权限矩阵、数据范围规则、补卡审批流程、审计日志落地说明 |
| [`docs/代码文件结构.docx`](docs/代码文件结构.docx) | 目录结构速查（125 Java / 42 Vue / 21 表 / 90 接口） |
| [`docs/测试文档.docx`](docs/TeamPulse_测试文档.docx) | 测试策略（五层）、46+42 项接口用例明细（结果由运行产物回填）、6 角色 × 16 路由权限审计矩阵、压测数据、11 条真实缺陷记录与回归验证、需求覆盖矩阵、未覆盖项 |
| [`docs/数据库设计说明书.docx`](docs/TeamPulse_数据库设计说明书.docx) | 21 表 / 218 字段全量字典（来自运行库导出）、索引与唯一约束、8 条迁移演进、Mermaid ER 图、无物理外键的原因 |
| [`docs/概要设计说明书.docx`](docs/TeamPulse_概要设计说明书.docx) | 分层架构、7 服务部署拓扑、13 个后端模块与规模、关键设计（认证/物联/审计/可观测）、4 条时序、选型理由与演进点 |
| [`docs/接口文档.docx`](docs/TeamPulse_接口文档.docx) | 70 路径 / 92 操作 / 109 模型；含两种鉴权、17 个错误码、按模块明细、8 个核心接口详解、设备验签规则与 curl 示例 |
| [`docs/详细设计与ADR.docx`](docs/TeamPulse_详细设计与ADR.docx) | 12 个关键类职责、打卡/审批/数据范围/审计的逐步实现、判定算法伪代码、指标清单、8 条 ADR、已知技术债 |
| [`docs/部署手册.md`](docs/部署手册.md) | 从装 Docker 到上线验收的逐步 Runbook：环境检查、.env 各项、首次启动验收、HTTPS、监控、备份恢复演练、回滚、故障排查表、上线安全检查清单 |
| [`docs/性能测试报告.md`](docs/性能测试报告.md) | 20 并发下的 QPS 与分位延迟、索引前后对比、瓶颈定位（数据库只占 2%）、改进建议 |
| [`docs/部署验证记录.md`](docs/部署验证记录.md) | 本机首次容器化的环境准备、7 个问题的根因与修复、验证证据、耗时、未验证项 |
| `docs/diagrams/` | 用例图、ER 图（PlantUML 源文件 + PNG/SVG，v1 尚未包含 RBAC 新增表） |

---

## 12. 项目状态

| 维度 | 状态 |
|---|---|
| 功能开发 | ✅ 8 大模块可运行，90 个接口实测通过 |
| 权限体系 | ✅ 6 角色 / 22 权限点 / 数据范围 / 补卡审批，实测 403 与数据隔离生效 |
| 审计留痕 | ✅ 20 个写操作，前端可查 |
| 可观测性 | ✅ traceId + Prometheus 指标 + 日志落盘；**Prometheus + Grafana 已接入并实测** |
| 性能 | ✅ 有压测数据（20 并发 p99 46ms，0 失败）；索引效果与瓶颈定位见性能报告 |
| **本地容器化** | ✅ **已跑通**：镜像构建 + 5 容器 healthy + 全新库启动验证（见部署验证记录） |
| **云端部署** | ✅ **已完成**（2026-10-27）：腾讯云轻量 81.70.102.116，5 容器 healthy，公网 43/43 断言通过 |
| 测试 | ⚠️ 仅单元测试；集成测试与压测未做 |
| CI | 🚫 **按决策终止推进**：GitHub 账号账单锁定，不再尝试在 GitHub 上运行；本地等价验证（`scripts/run-tests.ps1`）已覆盖 |

**已知限制（摘要）**：一人一岗（`user.role` 单值）、DEPT 范围不含子部门、`team_role=leader` 等同 DEPT、
无权限配置界面、缺勤只回补"昨天"、定时任务无分布式锁、考勤规则全局单行、
MQTT 未启用 TLS、无登录失败限流、日程完成时间用 `updated_at` 近似。
完整 17 条与改进方向见需求规格说明书第 11 章。

---

## 13. 常用运维命令

```bash
# 发布（备份 → 构建 → 上线 → 健康检查）
./deploy/scripts/deploy.sh

# 手动备份数据库
./deploy/scripts/backup.sh

# 实时看后端日志
docker compose logs -f --tail=100 backend

# 抓指标
curl -s http://127.0.0.1:8080/actuator/prometheus | grep teampulse_

# 直接订阅 MQTT，确认设备报文有没有到 broker
docker exec -it tp-mosquitto mosquitto_sub -t 'attendance/#' -v \
  -u "$MQTT_USERNAME" -P "$MQTT_PASSWORD"

# 进数据库看最近打卡
docker exec -it tp-mysql mysql -uroot -p teampulse \
  -e "select id,employee_no,punch_type,punch_time,status,late_minutes from attendance_record order by id desc limit 10"

# 查最近的管理动作
docker exec -it tp-mysql mysql -uroot -p teampulse \
  -e "select created_at,actor_name,action,result,resource,resource_id from audit_log order by id desc limit 20"
```

排障顺序：设备是否联网 → 是否连上 broker → 报文是否到 broker → 后端日志错误码 → 数据库是否有记录。

---

## 14. 与原始团队项目的关系

本项目由一支团队的老项目（Java + Vue + 鸿蒙三端的协作系统）重构而来，主要变更：

- 包名 `com.soft` → `com.teampulse`，工程目录 `houduan` → `backend`，`TodayJob_Vue_Project` → `frontend`
- 伪鉴权（请求头透传 userId）→ Spring Security + JWT；明文密码 → BCrypt（含自动升级）
- 手写建表脚本 → Flyway 版本化迁移（V1~V6）；删除 4 张从未接入业务的僵尸表
- 统一响应/异常/错误码/分页/校验规范
- **新增物联网考勤域**（设备、MQTT、打卡、判定、汇总、看板）
- **角色从 2 个硬编码改为 RBAC**：6 角色 / 22 权限点 / 部门 / 数据范围 / 补卡审批
- **新增审计日志与可观测性**：@Audited 切面、traceId、Prometheus 指标、日志落盘
- 新增 Docker Compose 部署、健康检查、备份与发布脚本、CI 流水线
- 前端整体重写：42 个组件，含三套日历、图表、权限驱动的菜单渲染

---

## 15. License

仅供学习与求职展示使用。

---

## 16. 关于仓库可见性与演示密码

本仓库是 **public**（按决策不改为私有），因此：

- 文档与种子脚本里出现的 `admin123` / `123456` / `Admin@TP2026` 等**均为演示值**，只用于本地或演示环境；
- 生产部署时数据库密码、JWT 密钥、管理员密码、MQTT 密码、Grafana 密码**全部走 `.env` 环境变量**，
  且 `application-prod.yml` 与 `docker-compose.yml` 做了 fail-closed（漏配直接启动失败，不会回退到默认值）；
- `.gitignore` 排除了 `.env`、`secrets.h`、`deploy/mosquitto/passwd`、`deploy/certs/`，
  仓库自带的 `.githooks/pre-commit` 会在提交时拦截硬编码密码与 64 位十六进制密钥；
- 已自查：被跟踪文件中出现的两处 64 位十六进制均为占位符（`0123456789abcdef...`），不是真实密钥。

---

## 17. 线上环境

| 项 | 值 |
|---|---|
| 地址 | **http://81.70.102.116/** |
| 云厂商 / 区域 | 腾讯云轻量应用服务器，ap-beijing |
| 系统 / 配置 | Rocky Linux 10.2，4 vCPU / 3.7 GB / 40 GB（含 2 GB swap） |
| 管理员 | `admin`（密码在服务器 `/opt/TeamPulse/.env` 的 `TPP_ADMIN_PASSWORD`，部署时随机生成） |
| 演示账号 | `zhangsan` / `lisi` / `wangwu` / `sunqi` / `zhaoliu`，密码统一 `123456` |
| 应用目录 | `/opt/TeamPulse` |
| 已开放端口 | 80（应用）。1883（设备上报）需在腾讯云控制台防火墙放行 |
| 未开放 | 8080 / 3000 / 9090（仅回环或 Docker 内网，看监控请用 SSH 隧道） |

日常发布：

```bash
ssh root@81.70.102.116
cd /opt/TeamPulse && bash deploy/scripts/deploy-server.sh
```
