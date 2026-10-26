"""第 1 章：项目概览与架构。"""


def build(d):
    d.h1("第 1 章  先建立全局认知")
    d.toc_hint()

    # ---------------- 1.1 ----------------
    d.h2("1.1 一句话说清这个项目")
    d.p(
        "**TeamPulse 是一个「团队协作 + 物联网考勤」一体化平台**："
        "同一套账号体系下，员工可以在网页上管理日程、看板与工作日志，"
        "同时通过部署在办公区的 ESP32 指纹打卡机完成考勤；"
        "打卡数据经 MQTT 上行到服务端，实时判定迟到/早退并刷新考勤看板。"
    )
    d.p(
        "它由两部分组成：**协作域**（用户/团队/日程/看板/日志/评论）和 **考勤域**（设备/打卡/规则/汇总）。"
        "协作域是原团队项目的骨架，考勤域是为物联网能力新增的核心模块，也是这个项目在简历上最有辨识度的部分。"
    )

    d.h3("1.1.1 简历项目描述（可直接使用）")
    d.code(
        "TeamPulse —— 团队协作与物联网考勤平台（个人独立开发）\n"
        "\n"
        "· 后端：Spring Boot 3.2 + MyBatis-Plus + MySQL 8 + Redis + Spring Security(JWT)\n"
        "· 物联网：ESP32(Arduino C++) → UART 读卡 → MQTT(Mosquitto) → Spring Integration 入站适配器\n"
        "· 前端：Vue 3 + Vite + Element Plus + Pinia + ECharts\n"
        "· 部署：Docker Compose 五容器编排 + Nginx 同源反代，单台 4C4G 云服务器\n"
        "\n"
        "核心工作：\n"
        "1. 设计并实现物联网考勤链路：设备端 HMAC-SHA256 签名 + nonce 防重放 + 时钟漂移校验，\n"
        "   服务端幂等入库（messageId / 工号+时间双唯一键），打卡成功率与可追溯性兼顾；\n"
        "2. 抽象考勤判定内核为纯函数（迟到/早退/工作日/签到签退判定），配套单元测试，\n"
        "   避免业务规则散落在 Service 中难以验证；\n"
        "3. 设计 attendance_record（事件流）+ attendance_daily（日汇总）双层存储，\n"
        "   报表查询从「扫明细表」降为「按日期索引查汇总」；\n"
        "4. 重构原团队项目的伪鉴权（请求头透传 userId）为 Spring Security + JWT 无状态认证，\n"
        "   并实现历史明文密码登录时自动升级为 BCrypt；\n"
        "5. 统一接口响应/异常/错误码规范，用 Flyway 管理数据库版本，Docker Compose 一键部署。",
        caption="简历可直接引用的项目描述（按你的实际投入再微调）",
    )

    # ---------------- 1.2 ----------------
    d.h2("1.2 技术栈与选型理由")
    d.p("面试时最容易被问「为什么用这个」，所以每一项都要能说出一句理由，而不是「教程这么写的」。")
    d.table(
        ["层次", "技术", "版本", "选型理由（面试口径）"],
        [
            ["语言/运行时", "Java", "17 (LTS)", "Spring Boot 3.x 最低要求 17；LTS 版本企业环境最常见，虚拟线程等新特性不是必需"],
            ["Web 框架", "Spring Boot Web", "3.2.4", "约定优于配置；生态完整（Security/Integration/Actuator 都是官方 starter）"],
            ["持久层", "MyBatis-Plus", "3.5.7", "相比 JPA 更贴近 SQL、分页与逻辑删除开箱即用；国内企业项目普及率高"],
            ["数据库", "MySQL", "8.0", "关系模型契合考勤/协作这类强一致业务；8.0 支持窗口函数与 CTE"],
            ["缓存/去重", "Redis", "7", "nonce 去重需要 SETNX + TTL 的原子语义，数据库做不到低延迟"],
            ["认证", "Spring Security + JWT", "6.2 / 0.12.5", "无状态、可水平扩容；Security 负责密码编码与鉴权链，JWT 负责会话"],
            ["物联网接入", "Spring Integration MQTT + Paho", "6.2 / 1.2.5", "入站适配器自带重连/QoS/会话，比裸写 Paho 客户端少一半样板代码"],
            ["MQTT Broker", "Mosquitto", "2.x", "内存占用约 10MB，4G 机器上比 EMQX（数百 MB）更合适"],
            ["数据库迁移", "Flyway", "10.x", "脚本即版本，环境间可复现；避免「手动改表忘了同步」"],
            ["接口文档", "springdoc-openapi", "2.5.0", "注解即文档，配合 Swagger UI 可直接调试"],
            ["前端框架", "Vue 3 + Vite", "3.5 / 7", "组合式 API 适合按领域拆逻辑；Vite 冷启动与 HMR 明显快于 Webpack"],
            ["UI 组件", "Element Plus", "2.11", "中后台组件齐（表格/表单/弹窗），省掉大量样式工作"],
            ["状态管理", "Pinia", "3", "Vue 3 官方推荐，替代 Vuex；TS 友好、模块化天然"],
            ["图表", "ECharts", "6", "考勤趋势/分布图能力最强，中文文档完善"],
            ["容器化", "Docker Compose", "v2", "单机多容器编排的最小方案，一台 4C4G 服务器足够承载"],
        ],
        widths=[2.2, 3.3, 1.9, 9.0],
    )

    d.callout(
        "反问准备",
        "如果面试官问「为什么不直接上 Kubernetes？」——回答：项目定位是单机企业内网/小团队，"
        "K8s 的控制面本身就要 2C4G 起步，收益（弹性伸缩、滚动发布）远小于运维成本；"
        "当服务数量和流量增长到需要多实例时，再迁移到 K8s 或云托管是平滑的，因为应用已经是无状态设计。",
    )

    # ---------------- 1.3 ----------------
    d.h2("1.3 系统架构")
    d.h3("1.3.1 部署拓扑（单台 4C4G 云服务器）")
    d.code(
        "                       ┌──────────────────────────────┐\n"
        "   浏览器 (Vue SPA) ───►│  Nginx :80                   │\n"
        "                       │   /        → 静态资源         │\n"
        "                       │   /api/    → backend:8080    │\n"
        "                       │   /iot/    → backend:8080    │\n"
        "                       └───────────────┬──────────────┘\n"
        "                                       │ (docker 内网)\n"
        "                       ┌───────────────▼──────────────┐\n"
        "                       │  backend (Spring Boot)       │\n"
        "                       │   Web API · 定时任务 · 鉴权   │\n"
        "                       └──┬────────┬────────┬─────────┘\n"
        "                          │        │        │\n"
        "                  ┌───────▼──┐ ┌───▼────┐ ┌─▼──────────┐\n"
        "                  │ MySQL 8  │ │ Redis  │ │ Mosquitto  │\n"
        "                  │ 业务数据  │ │ nonce  │ │ MQTT Broker│\n"
        "                  └──────────┘ └────────┘ └─▲──────────┘\n"
        "                                             │ tcp:1883 (公网)\n"
        "                                  ┌──────────┴──────────┐\n"
        "                                  │  ESP32 打卡机        │\n"
        "                                  │  UART 读卡 → HMAC   │\n"
        "                                  │  → MQTT 上报         │\n"
        "                                  └─────────────────────┘",
        caption="五个容器，全部由一份 docker-compose.yml 编排",
    )

    d.h3("1.3.2 后端模块划分")
    d.p("包结构按「领域」而不是「技术层」横向切分，这样改一个业务只需要动一个目录：")
    d.code(
        "com.teampulse\n"
        "├── common            公共基础设施（响应体、异常、审计基类）\n"
        "├── config            全局配置（Security、MyBatis-Plus、MQTT、启动初始化）\n"
        "├── security          JWT、登录主体、密码编码器\n"
        "└── module\n"
        "    ├── auth          登录 / 注册 / 当前用户\n"
        "    ├── user          用户 CRUD、工号绑定\n"
        "    ├── team          团队与成员\n"
        "    ├── schedule      日程（业务实体核心）\n"
        "    ├── kanban        看板（日程的视图投影）\n"
        "    ├── worklog       工作日志\n"
        "    ├── comment       评论 + 敏感词过滤\n"
        "    ├── dashboard     管理端/个人概览统计\n"
        "    └── attendance    物联网考勤（设备、打卡、规则、汇总、MQTT 适配）",
        caption="每个 module 内部再分 entity / mapper / dto / vo / service / controller",
    )

    d.h3("1.3.3 一次请求的完整链路")
    d.p("以「管理员打开打卡记录页」为例：")
    d.numbered(
        [
            "浏览器请求 http://服务器/ → Nginx 命中 location / → try_files 回退到 index.html（SPA 路由）",
            "前端加载 JS，路由守卫检查 localStorage 里的 JWT，未登录跳 /login",
            "页面调用 GET /api/v1/attendance/records → Nginx location /api/ 反代到 backend:8080",
            "Spring Security 过滤器链：JwtAuthenticationFilter 解析 Bearer token，写入 SecurityContext",
            "DispatcherServlet 路由到 AttendanceController#records",
            "Controller 参数绑定（RecordQuery）+ Bean Validation",
            "Service 校验并组装查询，MyBatis-Plus 生成物理分页 SQL（LIMIT）",
            "MySQL 执行，返回 IPage",
            "Service 批量补齐姓名/设备位置（selectBatchIds + Map，避免 N+1）",
            "Controller 包装成 ApiResponse<PageResult<VO>>",
            "前端 axios 响应拦截器发现 code===0，直接把 data 交给页面，表格渲染",
        ]
    )
    d.muted(
        "对应源码：frontend/nginx.conf、backend/.../security/JwtAuthenticationFilter.java、"
        "AttendanceController.java、AttendanceService.java、frontend/src/api/http.js"
    )

    # ---------------- 1.4 ----------------
    d.h2("1.4 目录结构速查")
    d.code(
        "E:\\codestorge\n"
        "├── backend/                      Spring Boot 后端\n"
        "│   ├── pom.xml\n"
        "│   ├── CONVENTIONS.md            开发契约（必读）\n"
        "│   ├── Dockerfile                多阶段构建\n"
        "│   └── src/main/\n"
        "│       ├── java/com/teampulse/   源码\n"
        "│       └── resources/\n"
        "│           ├── application.yml   主配置（环境变量驱动）\n"
        "│           ├── application-dev.yml / -prod.yml\n"
        "│           └── db/migration/     Flyway 脚本 V1/V2/V3\n"
        "├── frontend/                     Vue 3 前端\n"
        "│   ├── src/api/                  axios 实例 + 各模块 API\n"
        "│   ├── src/stores/               Pinia（auth）\n"
        "│   ├── src/router/               路由与守卫\n"
        "│   ├── src/views/attendance/     考勤四个页面（新增）\n"
        "│   └── nginx.conf                生产环境 Nginx 配置\n"
        "├── device/                       物联网设备侧\n"
        "│   ├── esp32/attendance_node/    ESP32 固件（.ino）\n"
        "│   └── simulator/                设备模拟器（HTTP / MQTT）\n"
        "├── deploy/                       部署资产\n"
        "│   ├── mysql/conf.d/my.cnf       4G 机器参数\n"
        "│   ├── mosquitto/mosquitto.conf  Broker 配置\n"
        "│   └── scripts/                  发布 / 备份 / 生成密码\n"
        "├── docs/                         本文档与生成脚本\n"
        "├── docker-compose.yml            五容器编排\n"
        "└── .env.example                  环境变量模板",
        caption="与原项目相比：houduan → backend，TodayJob_Vue_Project → frontend，新增 device/ 与 deploy/",
    )
