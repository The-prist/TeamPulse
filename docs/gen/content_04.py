"""第 4 章：数据模型与后端工程实践。"""


def build(d):
    d.h1("第 4 章  数据模型与后端工程实践")

    # ---------------- 4.1 ----------------
    d.h2("4.1 数据模型总览")
    d.p("一共 15 张表，分两个域。理解它们的关键是先分清「实体」与「投影」：")
    d.code(
        "协作域\n"
        "  user ──┬── team (creator_id)\n"
        "         │      └── team_member  (多对多，带 team_role)\n"
        "         ├── schedule (creator_id, team_id)   ★ 业务实体核心\n"
        "         │      ├── kanban_item (schedule_id)  ★ 日程在看板上的投影\n"
        "         │      └── worklog (related_schedule_id)\n"
        "         ├── worklog\n"
        "         └── comment (related_type + related_id 多态关联)\n"
        "  sensitive_word\n"
        "\n"
        "考勤域\n"
        "  iot_device          设备身份 + 密钥 + 在线状态\n"
        "  attendance_rule     考勤规则（全局单条）\n"
        "  attendance_record   打卡事件流（只增不改）\n"
        "  attendance_daily    每人每天汇总\n"
        "\n"
        "   user.employee_no ◄──── 匹配 ──── attendance_record.employee_no",
    )
    d.callout(
        "最关键的一句话",
        "kanban_item 不是独立任务，它必须指向一个 schedule_id。也就是说「日程」是唯一真实实体，"
        "看板卡片只是它在某个列里的投影。理解这一点，才能理解为什么改日程状态会自动挪卡片。",
    )

    # ---------------- 4.2 ----------------
    d.h2("4.2 表设计要点（面试可能逐张问）")
    d.table(
        ["表", "主键", "关键设计"],
        [
            ["user", "user_id", "username/email/employee_no 三个唯一索引；employee_no 允许 NULL（非考勤员工）；password 列 100 长度（BCrypt 60 位 + 余量）"],
            ["team", "team_id", "team_name 唯一；creator_id 记录创建人，用于「只有创建人能删」的判定"],
            ["team_member", "id", "(team_id, user_id) 唯一，天然防重复加入；team_role 区分 leader/member"],
            ["schedule", "schedule_id", "team_id 允许 NULL 表示个人日程；(team_id, start_time) 联合索引支撑团队日程列表"],
            ["kanban", "kanban_id", "必须属于某个团队（team_id NOT NULL），个人看板用「个人团队」实现"],
            ["kanban_column", "column_id", "sort_order 保证列顺序；(kanban_id, sort_order) 索引"],
            ["kanban_item", "item_id", "schedule_id NOT NULL 是关键约束；(column_id, sort_order) 支撑列内排序"],
            ["worklog", "worklog_id", "(creator_id, log_date) 索引，个人日志按日期查是最高频场景"],
            ["comment", "comment_id", "related_type + related_id 多态；original_content 存原文、content 存过滤后内容"],
            ["sensitive_word", "word_id", "word 唯一，避免重复词；status 支持临时停用而不是删除"],
            ["iot_device", "id", "device_sn 唯一；secret 只下发一次；last_heartbeat 驱动在线判定"],
            ["attendance_rule", "id", "work_days 存 \"1,2,3,4,5\" 这样的 ISO 星期串，比 7 个布尔列更易扩展"],
            ["attendance_record", "id", "message_id 唯一（幂等）+ (employee_no, punch_time) 唯一（防重）"],
            ["attendance_daily", "id", "(user_id, work_date) 唯一，保证每人每天只有一行汇总"],
        ],
        widths=[3.4, 2.6, 10.4],
    )

    # ---------------- 4.3 ----------------
    d.h2("4.3 为什么删掉了 4 张表")
    d.p("原项目有 11 张表，其中 4 张是「建了但从未真正接入业务」的僵尸表。重构时直接删除，并在文档里说明理由——"
        "留着它们只会在评审时被追问「这个权限表怎么用的」。")
    d.table(
        ["表", "原状", "处理", "理由"],
        [
            ["permission", "有表、有实体、无 Mapper、无接口", "删除", "从未参与任何鉴权判定"],
            ["permission_action", "同上", "删除", "同上"],
            ["team_default_permission", "仅 TeamService 引用 mapper，无实际校验逻辑", "删除", "权限模型改为「系统角色 + 团队角色 + 数据归属」"],
            ["attachment", "有表和实体，无 Mapper/Controller/前端", "删除", "附件功能完全未实现，属于半成品"],
        ],
        widths=[3.8, 5.6, 1.8, 5.4],
    )

    # ---------------- 4.4 ----------------
    d.h2("4.4 审计字段与逻辑删除")
    d.p("所有可变表统一带三列，由 BaseEntity + MetaObjectHandler 自动维护：")
    d.code(
        "created_at   DATETIME  DEFAULT CURRENT_TIMESTAMP\n"
        "updated_at   DATETIME  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP\n"
        "deleted      TINYINT   DEFAULT 0     -- 0 未删，1 已删",
    )
    d.p("MyBatis-Plus 的 `@TableLogic` 会自动把 `selectById` 改写成 `where id=? and deleted=0`，"
        "`deleteById` 改写成 `update set deleted=1`。这样业务代码完全不用关心软删除。")
    d.callout(
        "两种方案怎么选",
        "本项目的考勤明细表（attendance_record）**不带** deleted：它是事件流，只追加不修改，"
        "物理删除在业务上不存在。而 user/team/schedule 这类需要「可恢复」的主数据用逻辑删除。"
        "不是所有表都要一刀切。",
    )
    d.p("**注意**：唯一索引与逻辑删除会冲突——如果 username 有唯一索引，删除后重新注册同名会因为「已删除的行仍占着唯一键」而失败。"
        "本项目对 user 不做同名重建（走停用而非删除），属于有意的产品取舍。")

    # ---------------- 4.5 ----------------
    d.h2("4.5 索引设计")
    d.p("索引不是「加上就好」，每一条都要能对应一个真实查询。本项目的索引清单与对应场景：")
    d.table(
        ["索引", "支撑的查询"],
        [
            ["uk_user_username / uk_user_email", "登录、注册查重"],
            ["uk_user_employee_no", "打卡时按工号反查用户（最高频查询之一）"],
            ["idx_schedule_team_time (team_id, start_time)", "团队日程列表按时间倒序"],
            ["idx_item_column_sort (column_id, sort_order)", "看板某列的卡片排序"],
            ["idx_record_user_date (user_id, work_date)", "个人考勤明细"],
            ["idx_record_date_status (work_date, status)", "某天迟到人数统计"],
            ["uk_record_message / uk_record_emp_time", "幂等与防重复（也是业务约束的一部分）"],
            ["uk_daily_user_date (user_id, work_date)", "日汇总唯一性 + 个人月度查询"],
        ],
        widths=[7.0, 9.4],
    )
    d.p("**联合索引的顺序原则**：区分度高、且查询中最左的列放前面。例如 `(user_id, work_date)` 而不是 `(work_date, user_id)`，"
        "因为个人考勤查询一定带 user_id。")

    # ---------------- 4.6 ----------------
    d.h2("4.6 后端分层与包结构")
    d.p(
        "按**领域**纵向切分（module/user、module/attendance…），而不是按技术层横向切分"
        "（把所有 Service 放一个包）。好处是「改一个业务只动一个目录」，坏处是有时会出现跨模块的实体引用，"
        "需要在 CONVENTIONS.md 里明确约定。"
    )
    d.table(
        ["层", "职责", "不该做什么"],
        [
            ["controller", "参数绑定、校验、调 service、包 ApiResponse", "不写业务逻辑、不直接调 Mapper"],
            ["service", "业务规则、事务边界、跨表编排", "不处理 HTTP 语义（不返回 ResponseEntity）"],
            ["mapper", "单表 CRUD + 必要的手写 SQL", "不写业务判断"],
            ["entity", "表映射", "不放业务方法"],
            ["dto / vo", "入参与出参", "不直接复用 entity 暴露出去（密码、密钥等敏感字段）"],
        ],
        widths=[2.4, 8.4, 5.6],
    )
    d.callout(
        "一个容易被问的点",
        "为什么 Service 不写接口？因为项目里每个 Service 只有一个实现，接口层是纯样板代码；"
        "需要多实现时用 @ConditionalOnProperty 或策略模式即可（参考 AckPublisher 的两个实现）。"
        "如果团队规范强制要求接口，那就写——但要有理由，而不是「大家都这么写」。",
    )

    # ---------------- 4.7 ----------------
    d.h2("4.7 统一响应与分页")
    d.code(
        "{\n"
        '  "code": 0,              // 0 成功，非 0 为业务错误码\n'
        '  "message": "ok",\n'
        '  "data": { ... },        // 业务数据\n'
        '  "timestamp": 1748765000000\n'
        "}",
        caption="所有接口的统一返回结构",
    )
    d.p("分页统一返回 `PageResult`，字段名与前端表格组件约定一致：")
    d.code(
        '{ "records": [...], "total": 128, "page": 1, "size": 10 }',
    )
    d.p("**为什么让前端拦截器剥壳**：页面代码里 `const data = await api.get(...)` 拿到的是业务数据而不是 `res.data.data`，"
        "少一层嵌套就少一类低级错误。代价是拦截器必须严格判断响应形态（非标准包装的数据要原样返回）。")

    # ---------------- 4.8 ----------------
    d.h2("4.8 全局异常与错误码")
    d.p("业务代码只负责 `throw BizException.of(ErrorCode.NOT_FOUND, \"用户不存在\")`，"
        "其余（HTTP 状态码、响应体、日志级别）全部由 `@RestControllerAdvice` 统一处理。")
    d.table(
        ["码段", "含义", "示例"],
        [
            ["0", "成功", "—"],
            ["4xxxx", "客户端错误", "40000 参数不合法、40100 未登录、40300 无权、40400 不存在、40900 冲突"],
            ["5xxxx", "服务端错误", "50000 内部错误"],
            ["6xxxx", "设备侧错误（独立码段）", "60001 设备未注册、60003 验签失败、60004 重放、60006 时钟漂移"],
            ["61xxx", "考勤业务错误", "61001 工号不存在、61002 未配置规则、61003 重复打卡"],
        ],
        widths=[2.4, 5.4, 8.6],
    )
    d.p("**给设备单独划码段**不是形式主义：设备端固件要根据 code 决定「重试还是丢弃」，"
        "码段语义清晰才能写出正确的重试策略（见第 2.2 节）。")

    # ---------------- 4.9 ----------------
    d.h2("4.9 参数校验")
    d.p("用 Jakarta Bean Validation，Controller 上 `@Valid @RequestBody`，错误信息写中文直接面向用户：")
    d.code(
        "@Data\n"
        "public static class DeviceCreateRequest {\n"
        "    @NotBlank(message = \"设备序列号不能为空\")\n"
        "    @Pattern(regexp = \"^[A-Za-z0-9_-]{4,64}$\", message = \"序列号只能包含字母数字下划线短横线，长度 4~64\")\n"
        "    private String deviceSn;\n"
        "}",
    )
    d.p("校验失败的异常由 `MethodArgumentNotValidException` 处理器统一收集，拼成「字段 错误信息; 字段 错误信息」，"
        "前端直接 toast 出来即可。")

    # ---------------- 4.10 ----------------
    d.h2("4.10 MyBatis-Plus 的两个关键插件")
    d.code(
        "@Bean\n"
        "public MybatisPlusInterceptor mybatisPlusInterceptor() {\n"
        "    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();\n"
        "\n"
        "    PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);\n"
        "    pagination.setMaxLimit(500L);   // 防止前端传 size=999999 打爆内存\n"
        "    pagination.setOverflow(true);   // 页码超出总页数时回到首页\n"
        "    interceptor.addInnerInterceptor(pagination);\n"
        "\n"
        "    // 拦截「无 where 条件的 update/delete」，防止一次误调用清空整表\n"
        "    interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());\n"
        "    return interceptor;\n"
        "}",
        caption="backend/.../config/MybatisPlusConfig.java",
    )
    d.p("这两行配置能挡住两类真实事故：**深分页把数据库拖死**、**手滑全表删除**。属于「几行代码换一个保险」的典型。")

    # ---------------- 4.11 ----------------
    d.h2("4.11 事务")
    d.p("写多张表的方法加 `@Transactional`。有两个必须知道的坑：")
    d.bullets(
        [
            "**同类内部调用不走代理**：`this.methodB()` 不会开启 methodB 上的事务注解。需要嵌套事务时，把方法拆到另一个 Bean，"
            "或者注入自身代理（本项目选择拆 Bean，例如 AttendanceService 与 AttendanceSummaryService 分开）。",
            "**事务里不要吞异常**：catch 住持久层异常后继续写库，会导致事务被标记 rollback-only，最终抛 UnexpectedRollbackException，"
            "排查起来非常隐蔽。",
        ]
    )

    # ---------------- 4.12 ----------------
    d.h2("4.12 数据库版本管理：Flyway")
    d.p("把建表脚本纳入代码仓库，按 V1/V2/V3 递增执行，每个环境都从同一份脚本构建，杜绝「手动改表忘了同步」。")
    d.code(
        "backend/src/main/resources/db/migration/\n"
        "├── V1__init_collaboration.sql     协作域 9 张表\n"
        "├── V2__init_attendance_iot.sql    考勤域 4 张表\n"
        "└── V3__seed_dictionary.sql        敏感词 + 默认考勤规则",
    )
    d.p("**为什么初始管理员不写在 SQL 里**：密码需要 BCrypt 动态生成，写死散列会出现"
        "「按文档建库后登不进去」的情况——原项目 init.sql 里就是 `'$2a$10$placeholderHash'` 这样的假散列。"
        "现在改由 `BootstrapDataInitializer` 在首次启动时用 PasswordEncoder 真实加密生成。")

    # ---------------- 4.13 ----------------
    d.h2("4.13 定时任务")
    d.table(
        ["任务", "频率", "作用"],
        [
            ["DeviceHealthScheduler", "每 60 秒", "把超过 2 分钟没心跳的 ONLINE 设备置为 OFFLINE"],
            ["AttendanceSummaryScheduler", "每天 00:05", "补齐前一天的日汇总，标记缺勤"],
            ["MqttLifecycleManager#retryIfNotRunning", "每 30 秒", "MQTT 入站适配器未运行时尝试重连"],
        ],
        widths=[5.0, 3.0, 8.4],
    )
    d.p("**多实例部署要注意**：定时任务会在每个实例上各跑一遍。当前是单机部署所以没问题；"
        "如果扩成多实例，需要用分布式锁（Redis SETNX）或 ShedLock 保证只执行一次——这是必须能回答的延伸问题。")

    # ---------------- 4.14 ----------------
    d.h2("4.14 配置管理")
    d.p("所有可变配置走环境变量，代码里不出现任何密码：")
    d.code(
        "# application.yml\n"
        "spring:\n"
        "  datasource:\n"
        "    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:teampulse}?...\n"
        "    username: ${DB_USERNAME:root}\n"
        "    password: ${DB_PASSWORD:root}\n"
        "teampulse:\n"
        "  jwt:\n"
        "    secret: ${TPP_JWT_SECRET:dev-only-default}\n"
        "  mqtt:\n"
        "    broker-url: tcp://${MQTT_HOST:localhost}:${MQTT_PORT:1883}",
    )
    d.p("三套 profile 的职责边界：`dev`（本机、开 SQL 日志、开 Swagger）、`prod`（关 Swagger、日志落文件、禁止 Flyway 自动基线）、"
        "以及默认 application.yml（公共部分）。")

    # ---------------- 4.15 ----------------
    d.h2("4.15 测试策略")
    d.p(
        "这个项目没有铺满集成测试（受时间限制），但有意识地给**最容易出错、最容易回归**的部分加了单元测试："
        "考勤判定内核。因为它是纯函数，测试成本极低、收益极高。"
    )
    d.code(
        "@Test\n"
        "void 迟到分钟数从上班时间起算而不是宽限点() {\n"
        "    // 9:00 上班，宽限 5 分钟，9:08 打卡 → 迟到 8 分钟（不是 3 分钟）\n"
        "    int minutes = AttendanceCalculator.lateMinutes(\n"
        "            LocalDateTime.of(2025, 6, 3, 9, 8), LocalTime.of(9, 0), 5);\n"
        "    assertEquals(8, minutes);\n"
        "}",
        caption="backend/src/test/java/.../AttendanceCalculatorTest.java",
    )
    d.callout(
        "如果被问「为什么测试不多写」",
        "诚实回答：时间有限，优先保证「规则代码有测试、关键链路能手工验证」。"
        "并说明下一步会怎么做：用 Testcontainers 起 MySQL，对 handlePunch 做集成测试（覆盖验签失败、重放、未知工号等分支）。"
        "这比硬说「测试覆盖率高」更可信。",
    )
