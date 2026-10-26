# 图表说明：角色用例图 与 ER 图

本目录存放项目的两张核心设计图，提供**图片**（直接插入文档/PPT）与**源码**（可改可重生成）两种形态。

| 文件 | 说明 |
|---|---|
| `TeamPulse-用例图.png` | 角色用例图，1401×1867，140 DPI |
| `TeamPulse-用例图.svg` | 同上，矢量版（放大不失真，适合打印与 Word） |
| `usecase.puml` | 用例图源码（PlantUML） |
| `TeamPulse-ER图.png` | ER 实体关系图，2660×2113，140 DPI |
| `TeamPulse-ER图.svg` | 同上，矢量版 |
| `er.puml` | ER 图源码（PlantUML） |

---

## 一、角色用例图

### 1.1 四个参与者

| 参与者 | 类型 | 说明 |
|---|---|---|
| **管理员**（行政 / HR / 团队负责人） | 人员 | 拥有全公司视角：人员、团队、打卡设备、考勤规则、全量打卡记录与统计 |
| **普通员工** | 人员 | 只操作自己的数据：日程、看板、日志、评论、个人考勤 |
| **打卡机**（ESP32 / 模拟器） | **外部系统** | 不是「用户」，没有账号密码；靠设备密钥签名证明身份，只能上报打卡与心跳 |
| **定时任务**（系统调度） | **系统内部** | 每天 00:05 生成日汇总、每 60 秒做设备离线巡检 |

> 打卡机与定时任务用「参与者」表示是刻意的：它们和人员一样是系统的**外部触发源**，
> 区别在于一个走设备签名、一个由时钟触发，都不经过用户登录。

### 1.2 三组用例

| 分组 | 用例 | 主要参与者 |
|---|---|---|
| 通用 | 登录获取访问令牌、维护个人资料 | 管理员、员工 |
| 协作域 | 用户管理、团队与成员管理、日程管理、看板协作、工作日志、评论与回复、敏感词维护、统计概览 | 管理员为主，员工参与个人部分 |
| 考勤域 | 设备管理（注册/停用/轮换密钥）、配置考勤规则、打卡记录与补卡、考勤看板、我的考勤、上报打卡报文、上报设备心跳、校验设备身份与签名、判定迟到/早退、生成考勤日汇总、设备离线巡检 | 管理员、打卡机、定时任务 |

### 1.3 两条 `<<include>>` 关系（图上的关键设计）

```
上报打卡报文  ──<<include>>──►  校验设备身份与签名
上报打卡报文  ──<<include>>──►  判定迟到 / 早退
```

含义：**每一次打卡都必须执行这两步**，不是可选分支。这也是为什么把它们画成 include 而不是独立用例——
「上报打卡」不是一个简单的写入动作，而是一套带安全校验与业务判定的流程。

如果把这条流程展开，实际顺序是：

1. 字段完整性校验
2. 设备是否注册 / 已禁用
3. 设备时钟漂移是否 ≤ 5 分钟
4. HMAC 签名是否正确
5. 报文是否重复（messageId）
6. nonce 是否已用过（防重放）
7. 工号是否存在于系统
8. 判定签到 / 签退，计算迟到 / 早退分钟数
9. 落库 + 立即重算当日汇总
10. 下发回执

### 1.4 两条值得在答辩/面试时讲的规则

- **看板协作隐含了日程状态同步**：拖动卡片会把对应日程的 `status` 一起改掉（反之亦然）。
  图里没有额外画一条 include，是因为这是「看板协作」的内部一致性要求，写在此处说明更清楚。
- **考勤规则是全局单条**：所以它不与任何表显式关联，管理员改一次即对之后所有打卡生效。

---

## 二、ER 图

### 2.1 两个域、14 张表

| 域 | 表 | 作用 |
|---|---|---|
| 协作域 | `user` | 账号、密码散列、**工号**、角色、状态 |
| | `team` / `team_member` | 团队；成员与团队内角色（leader/member） |
| | `schedule` | 日程——业务核心实体 |
| | `kanban` / `kanban_column` / `kanban_item` | 看板、列、卡片（卡片指向日程） |
| | `worklog` | 工作日志，可关联日程与团队 |
| | `comment` | 评论，通过 `related_type + related_id` 多态关联 |
| | `sensitive_word` | 敏感词字典 |
| 考勤域 | `iot_device` | 打卡机：序列号、密钥、在线状态、最近心跳 |
| | `attendance_rule` | 考勤规则：上下班时间、宽限分钟、工作日 |
| | `attendance_record` | 打卡事件流（只增不改） |
| | `attendance_daily` | 每人每天的汇总 |

### 2.2 关系一览（图中的连线）

| 关系 | 基数 | 含义 |
|---|---|---|
| user → team | 1 : N | 用户创建团队 |
| user ↔ team（经 team_member） | M : N | 用户加入团队，带 `team_role` |
| user → schedule | 1 : N | 用户创建日程 |
| team → schedule | 1 : N（可空） | 日程归属团队；`team_id` 为空表示个人日程 |
| schedule → worklog | 1 : N（可空） | 日志可关联日程 |
| team → kanban | 1 : N | 看板属于团队 |
| kanban → kanban_column | 1 : N | 一个看板多列 |
| kanban_column → kanban_item | 1 : N | 卡片落在某一列 |
| **schedule → kanban_item** | 1 : N | **卡片是日程的投影**（同一日程可在多个看板上出现） |
| user → kanban_item | 1 : N（可空） | 卡片承接人 |
| user → comment | 1 : N | 发表评论 |
| comment → comment | 1 : N（可空） | `reply_to` 构成两级回复 |
| iot_device → attendance_record | 1 : N | 哪台设备上报的 |
| user → attendance_record | 1 : N（可空） | 按工号匹配到人；未匹配时 `user_id` 为空但记录保留 |
| user → attendance_daily | 1 : N | 每人每天一行汇总 |
| attendance_record ⇢ attendance_daily | 聚合 | 由明细聚合生成（虚线） |

### 2.3 三个必须知道的建模决策

1. **不建物理外键。**
   表之间是「逻辑关联」，由业务层保证一致性（例如删日程时先删它的看板卡片）。
   原因：协作类系统的删除顺序复杂，物理外键会让清理逻辑变得僵硬；代价是要求代码更自律。

2. **`kanban_item.schedule_id` 不允许为空。**
   看板卡片不是独立任务，必须指向一条日程。这保证了「看板状态」与「日程状态」永远只有一份真相。

3. **`user.employee_no` 是两个域之间唯一的纽带。**
   打卡机只知道工号，不知道用户 ID。服务端用 `employee_no` 反查用户，
   这是「协作域」与「考勤域」联通的关键字段，也是唯一为空的合法字段（非考勤人员可不填）。

### 2.4 图中未列出的字段

为保持可读性，ER 图只列出**主键、唯一键、关联键与核心业务字段**。省略的字段有：

- 所有表的 `created_at` / `updated_at`（审计时间，`BaseEntity` 自动填充）
- 大部分表的 `deleted`（逻辑删除标记；`attendance_record`、`team_member`、`kanban_column`、`kanban_item`、`attendance_rule` 没有该列）
- `iot_device.ip_address`、`remark`、`attendance_record.remark`、`user.avatar` 等辅助字段

完整定义以建表脚本为准：`backend/src/main/resources/db/migration/V1__*.sql` 与 `V2__*.sql`。

---

## 三、如何修改并重新生成

两张图都是 PlantUML 源码，改完重新渲染即可。

### 3.1 在线改（最快）

打开 <https://www.plantuml.com/plantuml/uml/>，把 `.puml` 内容粘进去即可实时预览。

### 3.2 本地渲染

需要 JDK 与 `plantuml.jar`：

```bash
# PNG（140 DPI，适合插入文档）
java -jar plantuml.jar -charset UTF-8 -tpng usecase.puml er.puml

# SVG（矢量，适合打印/Word）
java -jar plantuml.jar -charset UTF-8 -tsvg usecase.puml er.puml
```

> 若提示找不到 Graphviz：PlantUML 会尝试解压内置的 dot 到系统临时目录。
> 临时目录不可写时（某些受限环境），加 `-Djava.io.tmpdir=<可写目录>` 即可。
> 也可直接安装 Graphviz 并把它加入 PATH。

### 3.3 常用调整点

| 想改什么 | 改哪里 |
|---|---|
| 图片清晰度 | `skinparam dpi 140`（数值越大越清晰、文件越大） |
| 中文用字 | `skinparam defaultFontName "Microsoft YaHei"`（可换 宋体/思源黑体） |
| 配色 | `skinparam usecase/entity { BackgroundColor ... BorderColor ... }` |
| 布局方向 | 用例图首行 `left to right direction`；ER 图 `skinparam linetype ortho` |
| 增删用例/实体 | 直接在对应 `package` 内加 `usecase` / `entity` 与关系行 |
