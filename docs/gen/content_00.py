"""第 0 章：5 分钟跑起来 + 术语表。"""


def build(d):
    d.h1("第 0 章  先跑起来，再读下去")
    d.p(
        "读一个陌生项目最有效的方式不是从第一行代码看，而是先让它跑起来，"
        "点几下页面、看一眼数据库，再回头读代码。这一章就是那条最短路径。"
    )

    d.h2("0.1 本地最小启动（3 个终端）")
    d.code(
        "# 终端 1：数据库（用 Docker 最省事，也可以用本机已装的 MySQL）\n"
        "docker run -d --name tp-mysql -p 3306:3306 \\\n"
        "  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=teampulse mysql:8.0\n"
        "\n"
        "docker run -d --name tp-redis -p 6379:6379 redis:7-alpine\n"
        "\n"
        "# 终端 2：后端（会自动执行 Flyway 建表 + 创建管理员 + 创建演示设备）\n"
        "cd backend\n"
        "mvn spring-boot:run\n"
        "# 启动完成后，日志里会打印演示设备的序列号与密钥，复制下来备用\n"
        "\n"
        "# 终端 3：前端\n"
        "cd frontend\n"
        "npm install\n"
        "npm run dev\n"
        "# 打开 http://localhost:5173 ，用 admin / admin123 登录",
    )
    d.callout(
        "没有 Mosquitto 也能跑",
        "本地没装 MQTT broker 时，后端启动日志会告警「MQTT 入站启动失败…30s 后自动重试」，"
        "这是**预期行为**（Web 接口不受影响）。此时用 HTTP 模拟器验证打卡链路：\n"
        "node device/simulator/http-simulator.mjs --sn TP-ESP32-DEMO --secret <日志里的密钥> --employee E1000\n"
        "如果想验证完整 MQTT 链路，再启动一个 broker：docker run -d -p 1883:1883 eclipse-mosquitto:2",
        fill="E8F1FF",
    )

    d.h2("0.2 第一次登录后按这个顺序点一遍")
    d.numbered(
        [
            "**考勤看板** `/attendance/dashboard`：看指标卡、实时打卡流、7 天趋势（此时应该都是 0）",
            "**打卡设备** `/attendance/devices`：能看到系统预置的「演示打卡机」",
            "跑模拟器：终端执行 0.1 里的命令，成功后看板会在 5 秒内出现一条记录",
            "**打卡记录** `/attendance/records`：看到明细，点「手工补卡」再补一条",
            "**考勤规则** `/attendance/rule`：把上班时间改成当前时间之后，再打卡就能复现「迟到」",
            "**用户管理** `/users`：给自己配一个 employee_no（工号是打卡匹配的键）",
        ]
    )
    d.p("把这六步走通，你就已经理解了 80% 的运行时行为，剩下的时间用来看代码为什么这么写。")

    d.h2("0.3 本文档怎么读")
    d.table(
        ["你的目标", "读哪几章"],
        [
            ["只想把项目跑起来 / 部署上线", "第 0 章 + 第 6 章"],
            ["准备面试，要能讲清技术点", "第 1、2、3 章 + 第 7 章"],
            ["要把代码看懂、能改", "第 4、5 章 + 对应源码"],
            ["要接手继续开发", "第 4 章 + backend/CONVENTIONS.md"],
        ],
        widths=[6.4, 8.0],
    )

    d.h2("0.4 术语表（看代码遇到不认识的词，先查这里）")
    d.table(
        ["术语", "一句话解释", "在本项目哪里出现"],
        [
            ["JWT", "JSON Web Token，服务端签发的一段自包含字符串，里面带了用户 ID 和角色，客户端每次请求带上它", "第 3 章 / security 包"],
            ["BCrypt", "一种密码散列算法，自带随机盐、故意算得慢，专门用于存密码", "MigratingPasswordEncoder"],
            ["HMAC", "带密钥的散列，用来证明「这条消息确实是持有密钥的人发的」，且没被篡改", "设备验签"],
            ["nonce", "一次性随机数，保证同一条报文不会被重复使用（防重放）", "NonceDedupService"],
            ["MQTT", "物联网常用的轻量消息协议，发布订阅模型，报文头极小", "第 2 章"],
            ["QoS", "MQTT 的消息投递保证等级：0 最多一次、1 至少一次、2 恰好一次。本项目用 1（可能重复，靠幂等兜底）", "MqttProperties"],
            ["Topic", "MQTT 的消息频道，形如 attendance/checkin", "第 2 章"],
            ["Broker", "MQTT 的消息中转服务器，本项目用 Mosquitto", "docker-compose"],
            ["Spring Integration", "Spring 的集成框架，用「通道 + 适配器」把外部系统消息接进应用", "MqttConfig"],
            ["Flyway", "数据库版本管理工具，把建表脚本按 V1/V2/V3 顺序执行并记录版本", "db/migration"],
            ["MyBatis-Plus", "MyBatis 的增强版，单表 CRUD 不用写 SQL，支持分页与逻辑删除", "所有 Mapper"],
            ["逻辑删除", "不真删数据，只把 deleted 置 1，查询时自动过滤", "BaseEntity"],
            ["幂等", "同一操作执行多次与执行一次结果相同（重复打卡不会产生两条记录）", "第 2.5 节"],
            ["DTO / VO", "DTO 是接收请求的对象，VO 是返回给前端的对象，都不直接用数据库实体", "各模块 dto/vo"],
            ["Profile", "Spring 的多环境配置机制（dev / prod）", "application-*.yml"],
            ["SSE", "Server-Sent Events，服务端单向推送，比 WebSocket 轻，本项目暂未用", "第 5.5 节"],
            ["Actuator", "Spring Boot 提供的运维端点，如 /actuator/health", "docker healthcheck"],
            ["NTP", "网络时间协议，设备用来校准时钟（打卡必须时间准确）", "ESP32 固件"],
        ],
        widths=[2.8, 8.4, 3.2],
        font_size=9,
    )
