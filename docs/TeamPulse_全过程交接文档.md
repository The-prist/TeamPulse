# TeamPulse 项目全过程交接文档

> 用途：把整段对话的核心事实、编写过程、踩坑记录、当前状态完整交接给另一个 Agent。
> 生成时间：2026-10-27　代码位置：`E:\codestorge`　文档位置：桌面 `项目过程文档/`
> 阅读顺序：先读 §1~§3 建立全局，再按需查 §5 代码地图与 §8 Bug 清单。

---

## 0. 如何使用这份文档

- **要改代码**：先读 §5（代码地图）、§6（数据库）、§9（环境坑），再读 §14（工作规范与禁止项）。
- **要写报告/简历**：读 §1~§4、§7（技术决策）、§11（限制）、§12（未完成）。
- **要复现运行**：读 §9、§10。
- **不要相信本文档的"计划"，只相信"已验证事实"**：§10 的验证命令都能跑。

---

## 1. 起点：被改造的老项目

原始形态是一个团队实训项目（"今日事 · 工作日程管理系统"），Java + Vue + 鸿蒙三端原型，代码散乱、注释缺失、大量遗留实现。磁盘上原始物料仍在：

| 位置 | 内容 |
|---|---|
| `E:\codestorge\_legacy\` | 改造前的旧代码归档（不参与运行） |
| 桌面 `项目过程文档源\` | 旧实训报告（1442 段）、StarUML 模型、vue/鸿蒙原型 zip、四位组员的实训日志 |

改造方式：**保留业务骨架（协作域），重写工程结构与前端，新增物联网考勤域**，最终命名为 **TeamPulse**（包名 `com.teampulse`）。

---

## 2. 需求演进（按对话顺序）

| # | 用户的原始诉求 | 落地结果 |
|---|---|---|
| 1 | 查看文件夹代码，帮我理解这个项目 | 阅读全部旧代码，产出技术手册/项目报告 |
| 2 | 优化所有代码与结构，改造成自己的项目，并加物联网打卡机功能 | 全量重构为 TeamPulse + 新增 IoT 考勤域 |
| 3 | 最终要部署到云端服务器，做真实企业工程模拟 | 部署资产齐全，但**从未上过真实服务器**（见 §12） |
| 4 | 出 Word 文档，保证我了解所有技术点和细节 | 技术手册（75 页 / 98 表）+ 项目报告 + 用例/ER 图 |
| 5 | 运行页面原型 | 本地跑通（MySQL + 后端 + 前端），修复登录 500 等 |
| 6 | 把 DBeaver 连上项目数据库 | 写入连接配置并验证 |
| 7 | 概览页数据全无、没有负责团队数、趋势图数据不对 | 后端补字段 + 前端重写（见 §8-B1） |
| 8 | 添加今天打卡记录 | 补 12 条打卡 + 缺勤汇总 |
| 9 | 前端大量英文，全部汉化 | 新建字典模块，12 处枚举渲染修正（§8-B4） |
| 10 | 评论管理加敏感词管理；管理后台改日历形态 | 敏感词 CRUD 面板 + 日程日历 |
| 11 | 卡片换淡蓝提升对比度 | 主题变量化（§8-B9） |
| 12 | 日程/日志/考勤打点：日历化、检索优化、姓名模糊查、点人名看详情 | 三项日历 + 多条件检索 + 员工考勤详情抽屉 |
| 13 | 角色分类要符合现实企业 | RBAC 改造：6 角色 / 22 权限 / 部门 / 数据范围 / 补卡审批（§8-B13） |
| 14 | 检查所有前端按钮是否失效 | 41 组件编译审计 + 事件函数审计，修 6 处角色判断（§8-B14） |
| 15 | 更新整合桌面所有项目过程文档 | 备份 + 整合版 + 三份文档追加附录 + 索引（§8-B15） |
| 16 | 分析嵌入式岗位匹配度、职业方向 | 结论：Java/IoT 后端 85%，嵌入式 30%（§4.9） |
| 17 | 不急着找工作，先把项目技术栈补全 | 给出五阶段补全路线（§12-B） |

---

## 3. 最终形态

**一句话**：面向中小企业的「团队协作 + 物联网考勤」一体化平台——员工在网页管理日程/看板/日志，在 ESP32 打卡机刷卡，数据经 MQTT 上报后完成验签、迟到早退判定、日汇总与异常分析。

**技术栈**

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.2.4 / Java 17 / MyBatis-Plus 3.5.7 / Spring Security 6 + jjwt 0.12.5 / Flyway 9.22.3 / springdoc 2.5.0 / Jackson / Lombok |
| 数据 | MySQL 8.4（utf8mb4）/ Redis（可选，nonce 防重放，失败降级内存） |
| 物联网 | spring-integration-mqtt 6.2.3 + Paho 1.2.5；ESP32 固件（Arduino 框架）；Node.js HTTP/MQTT 模拟器 |
| 前端 | Vue 3 + Vite 7 + Element Plus 2.14 + Pinia 3 + ECharts 6 + Axios |
| 部署 | docker-compose（MySQL/Redis/EMQX/后端/前端）、run-local 脚本、deploy 脚本与 SQL |

**规模（已核实）**

| 维度 | 数值 |
|---|---|
| 后端 Java | 125 个文件 / 约 9 841 行 / 16 个控制器 / **90 个 REST 接口** |
| 前端 | 42 个 Vue 组件 / 约 16 586 行 / 11 个 JS 模块 |
| 数据库 | **21 张表** / 6 个 Flyway 迁移（V1~V6 全 success） |
| 固件 | 1 083 行单文件（Arduino 框架） |
| 权限 | 6 角色 / 22 权限点 / 5 部门 |
| 测试 | 1 个测试类（AttendanceCalculatorTest，11 用例） |

---

## 4. 项目编写过程（时间线）

### 4.1 阶段一：理解与梳理
- 通读旧项目：Java 后端（多模块混乱）、Vue 原型、鸿蒙原型、StarUML 模型、实训报告。
- 产出：技术手册、项目介绍报告、用例图/ER 图（PlantUML 生成 `docs/diagrams/`）。

### 4.2 阶段二：定方向（关键决策）
- 名称 **TeamPulse**，包名 `com.teampulse`。
- IoT 方案：**ESP32 通过 UART 读卡 → C 代码组 JSON → MQTT 发布 `attendance/checkin`**；同时保留 HTTP `POST /iot/v1/punch` 供联调。
- 部署目标：**单台 4C4G 云服务器**（因此刻意不引入微服务）。

### 4.3 阶段三：后端重构
- 重新分层：`common / config / security / module.{auth,user,rbac,team,schedule,kanban,worklog,comment,attendance,dashboard}`。
- 统一契约：`ApiResponse{code,message,data,timestamp}`、`PageResult`、分段 `ErrorCode`（0 成功 / 4xxxx 客户端 / 5xxxx 服务端 / 6xxxx 设备）、`BizException` + `GlobalExceptionHandler`。
- 全部状态改为**逻辑删除**（`deleted` + `@TableLogic`），时间字段由 `BaseEntity` 统一。
- 数据库用 **Flyway** 管理：V1 协作域、V2 考勤域、V3 字典、V4 RBAC+部门+审批、V5 审计权限修补。
- 写 `backend/CONVENTIONS.md` 作为强制开发契约（响应格式、鉴权、分页、事务、命名）。

### 4.4 阶段四：物联网考勤域（项目差异点）
- 设备身份：`iot_device`（SN + HMAC 密钥 + 状态 + 心跳），支持密钥轮换与禁用。
- 上报校验七项：设备合法 → **HMAC-SHA256**（规范串 `deviceSn|employeeNo|punchTime|nonce`）→ **nonce 防重放**（Redis `SET NX EX`，失败降级进程内 Map）→ **±300s 时钟漂移** → 工号匹配在职员工 → **三层幂等**（`message_id` 唯一、`(employee_no,punch_time)` 唯一、写前预查）→ 判定。
- 判定规则：当天首条=签到，其后=签退；迟到分钟从上班时间起算（宽限只决定是否算迟到，不改变分钟数）。
- **双层存储**：`attendance_record`（事件流，可审计）+ `attendance_daily`（日汇总，供看板直查）。
- 定时任务：每天 00:05 把"工作日完全没打卡"的人补为缺勤（非工作日不产生记录）。
- MQTT 入站适配器 `autoStartup=false` + `MqttLifecycleManager` 重试，broker 不可用时不阻塞启动。
- 固件：UART 读卡 → `snprintf` 组 JSON → PubSubClient 发布；`mbedtls` 做 HMAC；`esp_random` 生成 nonce；`millis()` 非阻塞状态机；WiFi 断线重连。

### 4.5 阶段五：前端重写
- 41 个组件，覆盖：登录/角色入口、管理后台（仪表盘、公司概览、考勤看板、打卡记录、设备、规则、补卡审批、日程、看板、日志、评论+敏感词、团队、用户）、用户工作台（首页、我的概览、我的考勤、我的团队/日程/看板/日志、个人信息）。
- 图表用 ECharts（趋势、任务指派分布）；日历为自研（日程/日志/考勤三套）。
- 主题：**淡蓝视觉体系**，全部颜色收敛到 CSS 变量（`--tp-*`）。

### 4.6 阶段六：跑起来与联调
- 本机无 java/mvn/docker，用 Node fetch 下载 JDK17 + Maven 到 `.toolchain`；MySQL 便携版在 `E:\dev\mysql`。
- 踩坑：Tomcat tempDir 拒绝访问（改 `-Djava.io.tmpdir`）、Vite/esbuild 临时目录权限（改 `TMP`/`TEMP`）、登录 500（后端未启动，Vite 代理返回 500）。
- DBeaver 连接写入 `data-sources.json` 并验证（需在关闭 DBeaver 时写，且注意 `allowPublicKeyRetrieval=true`）。

### 4.7 阶段七：本会话的密集迭代
按用户反馈逐项完成：仪表盘数据修复 → 汉化 → 敏感词管理面板 → 日程/日志/考勤三套日历 → 检索体系重构 → 卡片空白治理 → 淡蓝主题 → 布局与滚动优化 → RBAC 改造 → 按钮失效排查 → 文档整合。

### 4.8 阶段八：RBAC 改造（本会话最大改动）
- 新增 5 表：`department`、`role`、`permission`、`role_permission`、`makeup_request`；`user.department_id`、`team.department_id`。
- 6 角色：sysadmin(ALL,22) / hr(ALL,12) / manager(DEPT,14) / leader(DEPT,8) / employee(SELF,6) / auditor(ALL,10)。
- 15 处 `hasRole('ADMIN')` → `hasAuthority('module:action')`；`LoginUser` 携带角色/数据范围/权限点；JWT 过滤器**每请求按 userId 重新加载**（角色权限有进程内缓存，改权限即时生效）。
- 数据范围：`DataScopeService` 解析 ALL/DEPT/SELF，落到 `creator_id IN(...)` / `user_id IN(...)` / `department_id = ?`。
- 补卡审批：申请 → 待办（按数据范围过滤）→ 审批 → 通过时**复用 manualPunch** 生成正式记录并重建日汇总。
- 前端：`auth.hasPerm()`、`canUseAdminPanel`、菜单按权限渲染（11 项）、新增补卡审批页、用户表单加部门与角色。

### 4.9 阶段九：文档整合
- 备份桌面 9 份成果文档到 `_备份_20261027/`。
- 新增《TeamPulse_项目文档_整合版.docx》（11 章 / 18 表 / 25 节，当前代码唯一权威说明）。
- 重算《代码文件结构.docx》；给《1.docx》《技术手册》《项目介绍报告》追加附录更正过时结论；新增 `README_文档索引.md`。

### 4.10 阶段十：职业与方向分析（非代码，但已形成结论）
- 该 JD（嵌入式软件工程师：C/C++ + BSP 驱动 + RTOS/Linux + 硬件调试）匹配度仅 **≈30%**；缺口在驱动广度、RTOS/Linux、硬件调试证据。
- 本项目匹配度：**Java 后端 85~90%**、**IoT 平台后端 85%**、全栈 80%、嵌入式 30%。
- 用户画像：燕山大学（双非）、大四（2027 届）、无竞赛/奖学金、有挂科、CET4 586 / CET6 483、不要户口、目标北京、毕设答辩前在秦皇岛。
- 由此给出：主投 Java/IoT 后端与民企外企，放弃大厂校招与院所；用作品集替代学历背书；英语是差异化。

---

## 5. 代码地图

### 5.1 顶层
```
E:\codestorge\
├── backend/      Spring Boot（125 Java + 资源与迁移）+ CONVENTIONS.md
├── frontend/     Vue 3（41 组件 + 10 JS）
├── device/       esp32/attendance_node/attendance_node.ino（1083 行）+ simulator/（HTTP/MQTT 模拟器）
├── deploy/       mysql 配置、mosquitto 配置、backup.sh / deploy.sh / gen-mosquitto-passwd.sh、verify-project-db.sql
├── scripts/      run-local.ps1 / run-local.sh / dbeaver-add-connection.ps1
├── docs/         diagrams（用例图/ER 图 puml+png+svg）、gen（文档生成 Python 脚本）
├── _legacy/      旧代码归档
├── docker-compose.yml  根级
├── backend/Dockerfile、frontend/Dockerfile、.env.example、README.md
```

### 5.2 后端关键包
| 包 | 职责 |
|---|---|
| `common` | `ApiResponse`、`PageResult`、`BizException`、`ErrorCode`、`BaseEntity` |
| `config` | Security、MyBatis-Plus、MQTT、Swagger、Jackson 等 |
| `security` | `JwtTokenProvider`、`JwtAuthenticationFilter`（每请求重载主体）、`LoginUser`（角色/数据范围/权限）、`SecurityUtils`、`Perms`（权限常量） |
| `module/auth` | 登录/注册，登录响应含 role/roleName/dataScope/department/permissions |
| `module/user` | 用户 CRUD、部门与角色绑定、自助修改 |
| `module/rbac` | `RbacService`（角色/权限缓存）、`DataScopeService`、`DataScope`、部门/角色 options 接口 |
| `module/team` | 团队与成员（`team_role` leader/member） |
| `module/schedule` | 日程 + 与看板卡片双向状态同步 |
| `module/kanban` | 看板/列/卡片 |
| `module/worklog` | 工作日志 |
| `module/comment` | 评论 + 两级回复 + 敏感词（原文另存 `original_content`） |
| `module/attendance` | 打卡、规则、日汇总、设备、**补卡审批**（`MakeupRequest*`）、考勤分析 |
| `module/dashboard` | 公司概览、个人概览、趋势 |

### 5.3 前端关键点
| 路径 | 说明 |
|---|---|
| `src/api/http.js` | axios 实例：baseURL `/api/v1`、注入 Bearer、统一拆包、401 跳登录、代理失败友好提示 |
| `src/api/index.js` | 各模块接口封装（含 `departmentApi`/`roleApi`/`makeupApi`） |
| `src/stores/auth.js` | 登录态 + `hasPerm` / `hasAnyPerm` / `canUseAdminPanel` / `dataScope` |
| `src/router/index.js` | 守卫：无 permissions 的旧会话直接清 token；按管理类权限决定可进面板 |
| `src/layouts/` | AdminLayout（菜单按权限渲染，`el-menu-item v-if="auth.hasPerm(...)"`）/ UserLayout |
| `src/utils/dict.js` | 枚举中文映射（日程状态/优先级/角色/团队角色/关联类型/打卡来源） |
| `src/utils/attendance.js` | 打卡文案与格式化 |
| `src/views/attendance/` | 考勤看板、打卡记录、员工详情抽屉、设备、规则、**补卡审批**、考勤日历 |
| `src/views/schedule/ScheduleCalendar.vue` | 月历 + 跨天彩虹染色 + 最后一天显示结束时间 + 日期旁色块 |
| `src/views/worklog/WorklogCalendar.vue` | 月历 + 蓝色圆底姓氏头像（管理员特例显示「管」） |
| `src/styles/index.css` | 淡蓝主题变量 + 全局卡片/表格/操作列样式 |

---

## 6. 数据库（21 张表）

| 分组 | 表 |
|---|---|
| 账号与组织 | `user`、`department`（树形 parent_id）、`team`、`team_member` |
| 协作 | `schedule`、`kanban`、`kanban_column`、`kanban_item`、`worklog`、`comment`、`sensitive_word` |
| 考勤 | `attendance_rule`、`attendance_record`、`attendance_daily`、`iot_device` |
| 权限 | `role`（含 `data_scope`）、`permission`、`role_permission` |
| 审批 | `makeup_request` |
| 框架 | `flyway_schema_history` |

**迁移**：V1 协作域 → V2 考勤域 → V3 字典种子 → V4 RBAC+部门+补卡审批（含用户/团队部门回填与角色映射）→ V5 补审计角色概览权限。

**两个核心设计**
- 双表存储：事件流（可审计）+ 日汇总（查询快），典型"空间换时间"。
- 评论双列：`content` 过滤后给所有人看，`original_content` 原文供审计；词库变化不回改历史。

---

## 7. 关键技术决策（面试可讲）

| 决策 | 理由 | 代价/边界 |
|---|---|---|
| 不做微服务，单体分包 | 20~200 人规模，单机部署成本最低；分包保留按域拆分空间 | 无法展示服务治理能力 |
| 权限点入库 + 数据范围挂角色 | 加角色只插数据不改代码；数据边界统一由 `DataScope` 表达 | 一人一岗（`user.role` 单值） |
| JWT 每请求重载主体 | 角色权限变更即时生效，无需重新登录 | 每请求一次主键查询（角色权限有缓存） |
| 三层幂等 | 网络重试、设备重发、并发都能挡住 | 依赖唯一索引，需要 DDL 配合 |
| HMAC + nonce + 时间窗 | MQTT 可能明文，需保证来源真实与防重放 | 未上 TLS（见限制） |
| Redis 只做缓冲能力 | 挂掉不影响主流程（降级内存 Map） | 多实例下 nonce 不共享 |
| 补卡走审批 | 考勤是证据，直接改数据会让证据失效 | 无审批人分组/多级审批 |
| 前端按权限渲染菜单 | 前后端共用同一套权限点，避免"藏了按钮但接口没拦" | 权限点改动需改前端映射 |

---

## 8. 本会话修过的 Bug 与改进（按根因分类）

### A. 环境/工程类
| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| A1 | `mvn compile` 显示成功但运行时 `Unresolved compilation problems` | **VS Code Java 扩展用 JDT 把带错误的 class 写进 target/classes**，Maven 增量构建跳过 | 一律 `mvn clean compile` |
| A2 | 重启后 `NoSuchFileException: target/tmp/tomcat.8080.*` | `mvn clean` 删了 `target/tmp` | 启动前重建该目录 |
| A3 | Vite build 报 `remove ...esbuild-...: Access is denied` | 系统 TEMP 权限 | `TMP`/`TEMP` 指向项目内 `.run-logs/tmp` |
| A4 | 登录报 500 | **后端没启动**，Vite 代理返回 500 | 起后端 + 前端加友好提示 |
| A5 | PowerShell 里中文变 `?????` | 脚本用 `-Encoding Ascii` 写出，中文被替换 | 用 Unicode 转义或 UTF8 |
| A6 | 替换文本没命中 | 文件 CRLF vs 脚本 LF | 统一换行后再替换 |

### B. 业务/前端类
| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| B1 | 概览页数据全无、无"负责团队数"、趋势图数据不对 | `/dashboard/me` 公司级字段恒 0；前端把 `totalTeams` 当负责团队数；趋势图**用 `Math.random()` 造假** | 后端新增 `ledTeamCount/myTeamCount/canceledCount/recentWorklogs/upcomingSchedules/scheduleDone`；重写两个概览页，删除随机数 |
| B2 | 打卡记录/控制台缺今日数据 | 无当日数据 | 补 12 条打卡（含 1 条真实设备 HMAC 上报）+ 缺勤汇总 |
| B3 | 大量英文 | 枚举值（todo/high/admin/active/schedule）直接渲染 | 新建 `utils/dict.js` + 12 处替换 |
| B4 | 评论页"临时添加敏感词"无效 | 只写浏览器内存 | 改成真实后端 CRUD 面板 |
| B5 | 卡片下方大片空白 | 全局 `.content-container.has-list .el-card:first-of-type { min-height:540px }` 把**卡片**撑高 | 最小高度移到**容器** |
| B6 | 输入框与卡片同色、失去可编辑感 | 误把 `--el-fill-color-blank` 指向淡蓝（EP 输入框也用它） | 移除该覆盖，只对 `.el-card` 显式设背景 |
| B7 | 操作列按钮被逐字换行 | EP 单元格默认 `word-break: break-all` + 列宽贴着极限 | `.el-table .cell{word-break:normal}` + `.table-actions{display:flex;gap:10px;nowrap}` + 加宽列 |
| B8 | 检索条件框下方大片空白 | 同 B5（卡片被撑高） | 同上 |
| B9 | 日程/日志/考勤列表不好用 | 只能填编号、无日期/时段/人筛选 | 重构检索（关键词/日期/时段/人/状态/排序 + 空状态清空 + 默认排序） |
| B10 | 跨天日程只在起始日显示 | 归集只按开始日期 | 展开到每一天，彩虹取色，续期显示「续」，**最后一天显示结束时间** |
| B11 | 日志日历看不到人 | 无日历视图 | 新增日志日历：蓝色圆底姓氏头像，点开看当日全部；姓氏特例（管理员→「管」） |
| B12 | 人名显示账号（zhangsan） | `username \|\| nickname` 顺序反了 | 改为昵称优先；后端 `creatorName` 也是昵称优先 |
| B13 | 角色不符合企业 | 只有 admin/member，18 处 `hasRole('ADMIN')` | RBAC 改造（§4.8） |
| B14 | **切换面板点不开** | RBAC 后前端 6 处仍判断 `role === 'admin'`，路由守卫把后台重定向到 `/u/home` | 改按权限判断（`canUseAdminPanel`）+ 旧会话无 permissions 时清 token |
| B15 | 文档过时 | 文档停留在 RBAC 之前 | 备份 + 整合版 + 三份追加附录 + 索引 |
| B16 | 追加的表格出现「表\|格」错行 | 表头参数误传字符串（"表格"两字被当成两列表头） | 重做表格，表头传数组 |
| B17 | 删附录后文档结构损坏 | 删除 body 末尾元素时**连带删掉 `sectPr`** | 从备份恢复，重做时保留 sectPr |

### C. 数据/验证类
| # | 现象 | 处理 |
|---|---|---|
| C1 | 演示数据不足，日历/看板很空 | 补一周考勤、补 4 篇多人日志、补 3 条跨天日程 |
| C2 | 测试垃圾数据（日程"123/测试/123123"、设备"1111"、用户 tp_probe_user） | 已删三条垃圾日程与一条残留评论；设备与用户仍在（可清） |
| C3 | 缺勤当天为 0 | 缺勤由次日 00:05 任务补齐；历史缺失用同口径 SQL 补 |

---

## 9. 环境与工具链（本机特殊约定）

| 项 | 值 |
|---|---|
| 项目根 | `E:\codestorge` |
| JDK 17 | `E:\codestorge\.toolchain\jdk`（另有 `E:\dev\jdk17`）、`JAVA_HOME` 需显式设置 |
| Maven | `E:\codestorge\.toolchain\maven\bin\mvn.cmd`，必须带 `-s E:\codestorge\.toolchain\settings.xml` |
| MySQL | `E:\dev\mysql`（`mysqld.exe --defaults-file=E:\dev\mysql\my.ini`），root/123456，库 `teampulse` |
| Node | `C:\Users\12986\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe`（无系统 node） |
| Python | 同上目录下 `python\python.exe`（含 python-docx） |
| LibreOffice | `E:\ds\resources\app.asar.unpacked\dsh\node_modules\@deepseek-ai\libreoffice-kit\lib\cli.js`（Node 驱动） |
| 前端 | `node modules\vite\bin\vite.js`（dev）/ `vite build`；**必须设 `TMP`/`TEMP` 到项目内** |
| 后端启动 | `mvn spring-boot:run -Dspring-boot.run.jvmArguments=-Djava.io.tmpdir=<项目内路径>` |
| 端口 | 3306 / 8080 / 5173 |
| 无 Docker | 本机没有 docker，compose 未验证过 |

**必须记住的三条**
1. 改后端后 **一律 `mvn clean compile`**（防 JDT 坏 class）。
2. `clean` 会删 `target/tmp`，**启动前重建**。
3. 用 PowerShell 写含中文的脚本 → 用 `-Encoding UTF8` 或 Unicode 转义，否则中文变 `?`。

---

## 10. 验证基线（可复现）

```powershell
# 1) 服务
Get-NetTCPConnection -LocalPort 3306,8080,5173 -State Listen

# 2) 后端健康
Invoke-RestMethod http://127.0.0.1:8080/actuator/health     # status UP, db UP

# 3) 登录（经前端代理也通）
POST http://127.0.0.1:5173/api/v1/auth/login
  {username: admin, password: admin123}
  → data.role=sysadmin, data.permissions=22, data.departmentName=总经办

# 4) 编译与测试
mvn -B -s <settings> -f E:\codestorge\backend\pom.xml clean compile
mvn -B -s <settings> -f E:\codestorge\backend\pom.xml test      # 11 个用例

# 5) 迁移
SELECT version, success FROM teampulse.flyway_schema_history;   # V1~V6 全 1
```

**已验证关键数据**

| 项 | 值 |
|---|---|
| 角色权限数 | sysadmin 22 / manager 14 / hr 12 / auditor 10 / leader 8 / employee 6 |
| 数据范围（日程/打卡） | admin 11/84；zhangsan(DEPT) 4/56；lisi(SELF) 1/14；zhaoliu(ALL) 11/84 |
| 权限拦截 | 员工访问敏感词/公司概览/设备 → 403；审计审批 → 403 |
| 补卡闭环 | 提交 → 经理待办 → 批准 → 生成 10-25 09:05 CHECK_IN（MANUAL，备注含单号） |

---

## 11. 已知限制（9 条，文档中已如实记录）

1. 一人一岗（`user.role` 单值），无 `user_role` 多对多。
2. `DEPT` 范围只统计本部门，不含子部门（`parent_id` 已预留）。
3. `team_role=leader` 等同 DEPT，不能只看"我带的团队"。
4. 角色权限只能改 SQL，无管理界面。
5. **无操作审计日志**（`audit:view` 权限已预留）。
6. 缺勤回补只覆盖"昨天"一天，跨天停机需手工补。
7. 日程"完成时间"用 `updated_at` 近似（表无 `finish_time`）。
8. 定时任务未加分布式锁（多实例会重复执行）。
9. 考勤规则全局单行，不分班次/部门。

---

## 12. 未完成事项

### A. 项目侧
| 级别 | 事项 |
|---|---|
| **P0** | **云端真实部署**（唯一没达成的原始目标）：资产齐全但从未在服务器上跑通，缺 Nginx/HTTPS/域名/备份演练 |
| P1 | 业务文档只在桌面，仓库 `docs/` 只有 diagrams 与 gen，需回填 |
| P2 | 用例图/ER 图仍是 15 表 3 角色，缺 5 张新表与 6 角色 |
| P2 | 无 CI（`.github` 存在但无 workflow）、测试仅 1 个类、无压测数据 |
| P2 | 固件未深化：**无 FreeRTOS、无 NVS、无看门狗、无驱动分层、无 OTA、无单元测试** |
| P3 | 无 Linux 侧代码（最大技术空白） |

### B. 已规划的技术栈补全路线（5 阶段，按性价比）
1. **可测试性（1~2 周）**：Testcontainers + 5 个集成测试（鉴权/幂等/审批/数据范围）、固件逻辑层 Unity 单测、GitHub Actions。
2. **可观测性 + 真部署（2 周）**：`logback-spring` + TraceIdFilter、Micrometer/Prometheus 业务指标、`docker compose up` 打通 EMQX、备份恢复演练。
3. **固件深化（3~4 周）**：驱动分层、FreeRTOS 三任务+队列、NVS 离线队列补传、看门狗+复位原因、OTA。
4. **Linux 网关（4~6 周）**：C + termios + epoll + pthread + MQTT + Makefile/交叉编译 + systemd（**同时是最佳毕设题目**：MCU → 网关 → 云）。
5. **安全与性能（2 周）**：MQTT over TLS、压测出 QPS/P99、索引优化、越权矩阵、登录限流。

**明确不做**：再加业务 CRUD、微服务拆分、引 Kafka/ES、做手机 App（对该方向无帮助）。

---

## 13. 演示账号与数据现状

| 账号 | 密码 | 角色 | 部门 |
|---|---|---|---|
| admin | admin123 | 系统管理员 sysadmin | 总经办 |
| zhangsan | 123456 | 部门经理 manager | 研发部 |
| wangwu | 123456 | 团队负责人 leader | 研发部 |
| lisi / zhouba | 123456 | 普通员工 employee | 研发部 |
| sunqi | 123456 | 人事专员 hr | 人力资源部 |
| zhaoliu | 123456 | 审计专员 auditor | 总经办 |
| wujiu | 123456 | 普通员工（停用） | 产品部 |

数据量：用户 9、团队 2、日程 11、看板 2、日志 10、评论 9、设备 4（含测试设备"1111"）、考勤记录约 100+、日汇总 50+、补卡申请 1（已通过）。

---

## 14. 给接手 Agent 的工作规范

**必须遵守**
1. 以代码为唯一事实来源；文档只作参考（部分章节已标注过时）。
2. 改后端后 `mvn clean compile`；改前端后 `vite build` 必须过。
3. 新增接口沿用统一响应体、`ErrorCode` 分段、`@Transactional`、权限注解（`hasAuthority`）。
4. 前端新增菜单/按钮必须挂 `auth.hasPerm('module:action')`，并在 `Perms.java` 与数据库里同步权限点。
5. 查询列表要过 `DataScopeService`（否则会绕过部门边界）。
6. 中文文案统一中文标点；枚举值必须经 `utils/dict.js` 映射，不要直接渲染。

**禁止**
- 编造未验证的功能或数据（本项目限制见 §11）。
- 用 `Math.random()` 之类伪造业务数据。
- 改动 `V1~V6` 已执行的迁移文件（Flyway 会校验 checksum），新增改动请写 V7+。
- 在文档里写"已部署到云端"——**没有**。

**验收方式**
- 后端：`clean compile` + `mvn test` + 目标接口用 curl/Node 实测返回体。
- 前端：`vite build` + 逐组件 HTTP 200（dev server 编译）+ 事件函数存在性审计。
- 文档：`python <office-skill>/../scripts/check_office.py <docx>` 必须 `pass`；渲染首页确认无溢出。

---

## 15. 术语表

| 词 | 含义 |
|---|---|
| 协作域 / 考勤域 | 项目的两个业务域（前者是旧项目骨架，后者是新增 IoT 能力） |
| 三层幂等 | message_id 唯一 + (employee_no,punch_time) 唯一 + 写前预查 |
| 双层存储 | `attendance_record` 事件流 + `attendance_daily` 日汇总 |
| 数据范围 | ALL 全公司 / DEPT 本部门 / SELF 仅本人，挂在角色上 |
| 补卡审批 | 员工申请 → 主管/HR 审批 → 通过才生成打卡记录 |
| 权限点 | `module:action` 形式的字符串，如 `attendance:rule` |
| 面板 | 管理后台（`/`）与用户工作台（`/u`）两套界面 |
| 淡蓝主题 | 前端视觉体系，颜色收敛在 `--tp-*` CSS 变量 |

---

## 16. 2026-10-27 第二批更新：审计、可观测、CI 与版本管理

本节记录第一批（RBAC/补卡审批）之后又完成的三块工程能力，以及仓库治理现状。

### 16.1 操作审计日志

| 项 | 内容 |
|---|---|
| 表 | `audit_log`（迁移 V6）：追加写入，**无 `updated_at`、无 `deleted`**，操作人与角色是写入时快照 |
| 记录方式 | `@Audited(module, action, detail)` 注解 + `AuditLogAspect` 切面，0 侵入业务代码 |
| 记录内容 | 操作人、动作、资源与资源 id、结果、耗时、IP（含 X-Forwarded-For）、UA、traceId |
| 失败留痕 | 业务异常穿过切面时先记 `FAILURE` 与错误信息，再原样抛出 |
| 埋点范围 | 20 个写操作：用户 4、团队 5、考勤 3（含审批）、设备 4、敏感词 3、评论 1 |
| 查询 | `GET /api/v1/audit`（分页 + 6 个过滤条件）、`/{id}`、`/options`，权限点 `audit:view` |
| 前端 | `views/audit/AuditLogList.vue`，「系统 → 审计日志」菜单按 `audit:view` 显示 |
| 依赖 | 新增 `spring-boot-starter-aop` |

### 16.2 可观测性

| 能力 | 实现 |
|---|---|
| traceId | `RequestObservabilityFilter`（`Ordered.HIGHEST_PRECEDENCE + 10`）：继承或生成 `X-Trace-Id`，写 MDC，回写响应头，`finally` 清理 |
| 日志 | `logback-spring.xml`：控制台 + 滚动文件，格式含 `[%X{traceId}]`，20MB×14 天、总量封顶 500MB；`LOG_PATH` 可覆盖 |
| RED 指标 | `teampulse_api_requests_total{method,uri,status}`、`teampulse_api_failures_total`、`teampulse_api_latency_seconds{uri}`；tag 用路由模板避免时间序列爆炸 |
| 业务指标 | `teampulse_attendance_punch_total{result}`、`teampulse_attendance_idempotent_total{layer}`、`teampulse_api_errors_total{code}`、`teampulse_mqtt_connected_total`、`teampulse_mqtt_connect_failed_total{reason}` |
| 端点 | `/actuator/prometheus`（已在 SecurityConfig 放行，生产需限来源 IP）、`/actuator/health` |
| 前端闭环 | 失败提示条显示 `编号 <traceId>`，用户截图即可对上服务端日志 |

实测样本（本机）：

```
teampulse_api_requests_total{method="POST",status="401",uri="/iot/v1/punch"} 1.0
teampulse_api_errors_total{code="60001"} 1.0        # 设备未注册
teampulse_api_errors_total{code="60006"} 2.0        # 时钟漂移
teampulse_attendance_punch_total{result="success"} 2.0
teampulse_attendance_idempotent_total{layer="message"} 1.0   # 重复报文被幂等拦截
```

### 16.3 CI 与版本管理

| 项 | 状态 |
|---|---|
| CI 配置 | `.github/workflows/ci.yml`：3 个 job（后端 `mvn clean test` / 前端 pnpm 构建 / 契约检查） |
| CI 运行 | 🚫 **已终止推进**：`The job was not started because your account is locked due to a billing issue.` 原因为`The job was not started because your account is locked due to a billing issue.`（job 未分配 runner，`runner_id=0`、`steps=[]`）。需处理 GitHub 账号账单后重跑 |
| 本地等价验证 | `mvn test` 11 用例全绿；`vite build` 成功；迁移命名检查通过 |
| Git 远程 | `git@github.com:The-prist/TeamPulse.git`，分支 `main` |
| 提交规范 | conventional commits，按功能分组：rbac / attendance(补卡) / audit / observability / frontend-fix / ci / docs |
| 安全 | `.gitignore` 覆盖 `secrets.h`、`node_modules`、`target`、`.toolchain`、`.run-logs`；提交前扫描无密钥/证书类文件 |

### 16.4 部署现状（仍然是未完成项）

| 资产 | 状态 |
|---|---|
| `docker-compose.yml` | 5 容器（mysql/redis/mosquitto/backend/frontend）+ 4 卷 + healthcheck + depends_on |
| `backend/Dockerfile` / `frontend/Dockerfile` | 多阶段构建（maven→JRE / node→nginx），共 55 行 |
| `frontend/nginx.conf` | 反代 `/api/v1`、gzip、`/healthz`；HTTPS 段已写好但**被注释** |
| `deploy/mosquitto.conf` | `allow_anonymous false` + 密码文件（鉴权已开，**TLS 未开**） |
| `deploy/scripts/` | `deploy.sh`（备份→构建→上线→健康轮询）、`backup.sh`、`gen-mosquitto-passwd.sh` |
| **是否真跑过** | ✅ 2026-10-27 本机首次跑通（详见 `docs/部署验证记录.md`）；云服务器仍未验证 |
| 已知风险 | ① `frontend/Dockerfile` 用 `npm ci/npm install`，但仓库只有 `pnpm-lock.yaml` → 会绕过锁文件 ② nginx `/healthz` 与 `wget` 可用性未验证 ③ MySQL 就绪与 Flyway 首次迁移的时序未验证 |

> 结论：**有服务器即可当天上线最小可用版**（HTTP + IP 访问）；到"敢说部署完成"（HTTPS + MQTT 鉴权 + 备份恢复演练 + 部署手册）还需 1~2 天。

### 16.5 本轮修复的关键问题（供后人避坑）

| 现象 | 根因 | 处理 |
|---|---|---|
| `/actuator/prometheus` 返回 401 | SecurityConfig 只放行 health/info | 放行 prometheus 与 metrics，并注明生产要在网关层限来源 |
| 前端提示条看不到 traceId | 拦截器没读响应头 | `reportNetError` 增加 traceId 参数，`App.vue` 显示编号 |
| `git ls-tree` 比对中文路径失败 | 默认 `core.quotepath=true` 会转义成 `\346\212\200` | 所有 git 命令加 `-c core.quotepath=false` |
| 批量改 `.vue` 后渲染报 "Missing semicolon" | 脚本给单行 `catch` 体插入语句时没补换行/分号 | 插入逻辑判断 `rest` 是否以换行开头 |
| `robocopy /MIR` 清空了 `src/views` 顶层 | 备份目录多了一层 `views/`，镜像时把宿主目录当多余文件删了 | 从 `views-backup/views/` 逐项上移恢复；**以后备份用显式目标目录** |
| Word 占用导致 docx 覆盖失败 | 用户开着 Word | 另存 `_v1.1` 后关闭 Word 再覆盖回原名，删除重复文件 |

### 16.6 本机容器化验证（2026-10-27 补充）

首次在 Windows 11 + Docker Desktop（WSL2）跑通 compose，修掉 7 个问题，完整记录见 `docs/部署验证记录.md`。摘要：

| 分类 | 问题 | 修复 |
|---|---|---|
| 环境 | winget 安装被拒（无管理员）、WSL 未装、Docker Hub 不可达 | 管理员启用 WSL → 重启 → `wsl --update`；`daemon.json` 配 registry-mirrors |
| 构建 | 缺 `frontend/.dockerignore`（193MB 上下文 + 平台不匹配） | 新增 .dockerignore，上下文降到 0.6MB |
| 构建 | `pnpm-workspace.yaml` 只有 `onlyBuiltDependencies` 无 `packages` | 移入 `package.json` 的 `pnpm` 字段并删除该文件 |
| 构建 | Dockerfile 用 npm 安装，绕过 pnpm 锁文件 | 改 corepack + `pnpm install --frozen-lockfile` |
| 运行 | mosquitto 用 bridge 专用参数 `keepalive_interval` 导致重启循环 | 改 `max_keepalive 120` + `max_packet_size` |
| 功能 | **全新库 admin 权限为 0**（bootstrap 写旧角色值 `admin`） | 改 `sysadmin`，并新增 V7 归一历史角色值 |
| 功能 | admin 部门为空 | bootstrap 建号时挂到根部门（总经办） |

验证证据：清空数据卷后 `compose up` → 5 容器 healthy；Flyway V1~V7；admin 登录返回 `role=sysadmin`、22 权限点、`dept=总经办`、`scope=ALL`；`/user/list`、`/audit`、`/devices`、`/attendance/rule`、`/dashboard/admin` 全 200；后端以 `teampulse-backend-1` 连上 MQTT。

### 16.7 决策记录（2026-10-27）

| # | 决策 | 状态 | 原因与替代 |
|---|---|---|---|
| D1 | **不把仓库改为私有** | 终止 | 改可见性需要仓库管理员在网页操作；按用户决定不再推进。**替代措施**：文档统一标注演示密码为演示值，生产密码全部走 `.env` 且 fail-closed；`.gitignore` 与 pre-commit 钩子拦截密钥 |
| D2 | **不在 GitHub Actions 上运行 CI** | 终止 | GitHub 账号因账单问题锁定，job 无法分配 runner（`runner_id=0`、`steps=[]`）。`ci.yml` 保留作配置留存；**替代措施**：本地 `scripts/run-tests.ps1` 跑三层检查（单测 / 前端构建 / 46 项接口断言），已在本地与容器版各验证一次全绿 |

> 这两条原先记为「未完成」，现改为「决策不做」。后续若要恢复，只需：处理 GitHub 账单 → 手动触发 workflow；
> 以及网页 Settings → Danger Zone → Change visibility。
