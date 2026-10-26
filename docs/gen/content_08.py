"""第 8 章：附录（速查表）。"""


def build(d):
    d.h1("第 8 章  附录")

    d.h2("8.1 接口清单")
    d.table(
        ["方法", "路径", "说明", "权限"],
        [
            ["POST", "/api/v1/auth/login", "登录，返回 JWT", "公开"],
            ["POST", "/api/v1/auth/register", "自助注册（角色固定 member）", "公开"],
            ["GET", "/api/v1/auth/me", "当前登录人", "登录"],
            ["GET", "/api/v1/user/list", "用户分页", "ADMIN"],
            ["GET", "/api/v1/user/options", "用户下拉选项", "登录"],
            ["GET/PUT", "/api/v1/user/self", "我的资料", "登录"],
            ["PUT", "/api/v1/user/self/password", "修改我的密码", "登录"],
            ["POST/PUT/DELETE", "/api/v1/user/{id}", "用户增改删", "ADMIN"],
            ["GET", "/api/v1/team/list", "团队分页", "ADMIN"],
            ["GET", "/api/v1/team/my", "我参与的团队", "登录"],
            ["GET", "/api/v1/team/{id}/members", "团队成员", "登录"],
            ["POST", "/api/v1/team", "创建团队", "登录"],
            ["POST", "/api/v1/team/{id}/members", "添加成员", "leader/ADMIN"],
            ["GET", "/api/v1/schedule", "日程分页", "登录"],
            ["POST", "/api/v1/schedule", "创建日程", "登录"],
            ["POST", "/api/v1/schedule/{id}/status", "状态流转（联动看板）", "登录"],
            ["GET", "/api/v1/kanban/{id}/detail", "看板详情（含列与卡片）", "登录"],
            ["PUT", "/api/v1/kanban/items/{id}/move", "移动卡片（反向改日程状态）", "登录"],
            ["GET", "/api/v1/worklog", "日志分页", "登录"],
            ["GET", "/api/v1/comment/tree", "评论树", "登录"],
            ["GET", "/api/v1/dashboard/admin", "公司概览", "ADMIN"],
            ["GET", "/api/v1/dashboard/me", "个人概览", "登录"],
            ["GET", "/api/v1/attendance/records", "打卡记录分页", "登录"],
            ["GET", "/api/v1/attendance/recent", "实时打卡流（轮询）", "登录"],
            ["GET", "/api/v1/attendance/overview", "考勤总览指标", "登录"],
            ["GET", "/api/v1/attendance/trend", "考勤趋势", "登录"],
            ["GET", "/api/v1/attendance/my", "我的考勤", "登录"],
            ["POST", "/api/v1/attendance/manual", "手工补卡", "ADMIN"],
            ["GET/PUT", "/api/v1/attendance/rule", "考勤规则", "读:登录 写:ADMIN"],
            ["GET", "/api/v1/devices", "设备分页", "ADMIN"],
            ["POST", "/api/v1/devices", "注册设备（返回一次性密钥）", "ADMIN"],
            ["PUT", "/api/v1/devices/{id}/secret", "重置密钥", "ADMIN"],
            ["POST", "/iot/v1/punch", "设备 HTTP 上报打卡", "设备签名"],
            ["POST", "/iot/v1/heartbeat", "设备 HTTP 上报心跳", "设备签名"],
            ["GET", "/actuator/health", "健康检查", "公开"],
        ],
        widths=[2.4, 6.4, 5.0, 3.2],
        font_size=9,
    )

    d.h2("8.2 错误码表")
    d.table(
        ["码", "含义", "典型触发"],
        [
            ["0", "成功", "—"],
            ["40000", "请求参数不合法", "校验失败、类型不匹配"],
            ["40100", "未登录或登录已过期", "token 缺失/无效/过期"],
            ["40300", "无权限", "非 ADMIN 调管理接口、非本人改数据"],
            ["40400", "资源不存在", "查不到用户/日程/设备"],
            ["40900", "资源冲突", "用户名/团队名/工号重复"],
            ["50000", "服务器内部错误", "未捕获异常（会打完整堆栈）"],
            ["60001", "设备未注册", "deviceSn 不在 iot_device"],
            ["60002", "设备已禁用", "status=DISABLED"],
            ["60003", "报文签名校验失败", "secret 不一致或 canonical 顺序错"],
            ["60004", "重复报文（重放）", "nonce 已被使用"],
            ["60005", "报文格式不合法", "缺字段、时间格式错"],
            ["60006", "设备时钟漂移超限", "设备未做 NTP 校时"],
            ["61001", "工号不存在", "user.employee_no 未配置"],
            ["61002", "未配置考勤规则", "attendance_rule 为空"],
            ["61003", "重复打卡", "同一人同一秒已有记录"],
        ],
        widths=[2.2, 6.0, 8.2],
    )

    d.h2("8.3 环境变量表")
    d.table(
        ["变量", "默认值", "说明"],
        [
            ["SPRING_PROFILES_ACTIVE", "dev", "prod 时关 Swagger、日志落文件"],
            ["SERVER_PORT", "8080", "后端端口"],
            ["DB_HOST / DB_PORT / DB_NAME", "localhost / 3306 / teampulse", "数据库连接"],
            ["DB_USERNAME / DB_PASSWORD", "root / root", "数据库账号（生产用 .env 注入）"],
            ["REDIS_HOST / REDIS_PORT / REDIS_PASSWORD", "localhost / 6379 / 空", "Redis"],
            ["MQTT_HOST / MQTT_PORT", "localhost / 1883", "Broker 地址"],
            ["MQTT_USERNAME / MQTT_PASSWORD", "空", "Broker 账号"],
            ["MQTT_ENABLED", "true", "false 时关闭 MQTT 链路（Web 仍可用）"],
            ["MQTT_CLIENT_ID", "teampulse-backend-1", "多实例时必须各不相同"],
            ["TPP_JWT_SECRET", "dev 默认值", "**必须 ≥32 字节**，生产必改"],
            ["TPP_CORS_ORIGINS", "空", "同源部署留空"],
            ["TPP_ATTENDANCE_SIGN_ENABLED", "true", "关闭仅用于本地调试"],
            ["TPP_BOOTSTRAP_ENABLED", "true", "是否首次启动建管理员"],
            ["TPP_ADMIN_PASSWORD", "admin123", "初始管理员密码"],
            ["TPP_OPENAPI_ENABLED", "false(prod)", "是否开 Swagger"],
            ["VITE_API_BASE", "/api/v1", "前端 API 基址"],
            ["VITE_PROXY_TARGET", "http://127.0.0.1:8080", "开发期 Vite 代理目标"],
        ],
        widths=[6.6, 4.6, 5.2],
        font_size=9,
    )

    d.h2("8.4 命令速查")
    d.code(
        "# ---------- 后端 ----------\n"
        "cd backend\n"
        "mvn -q compile                     # 编译\n"
        "mvn -q test                        # 单元测试\n"
        "mvn spring-boot:run                # 本地启动\n"
        "mvn -q clean package -DskipTests   # 打包 jar\n"
        "\n"
        "# ---------- 前端 ----------\n"
        "cd frontend\n"
        "npm install\n"
        "npm run dev                        # 开发服务器 5173\n"
        "npm run build                      # 生产构建到 dist/\n"
        "\n"
        "# ---------- 部署 ----------\n"
        "docker compose up -d --build       # 全量构建并启动\n"
        "docker compose ps                  # 容器状态\n"
        "docker compose logs -f backend     # 后端日志\n"
        "docker compose restart backend     # 只重启后端\n"
        "./deploy/scripts/deploy.sh         # 一键发布（含备份）\n"
        "./deploy/scripts/backup.sh         # 手动备份\n"
        "\n"
        "# ---------- 物联网 ----------\n"
        "docker exec -it tp-mosquitto mosquitto_sub -t 'attendance/#' -v -u <user> -P <pwd>\n"
        "node device/simulator/http-simulator.mjs --sn TP-ESP32-DEMO --secret <secret> --loop\n"
        "\n"
        "# ---------- 数据库 ----------\n"
        "docker exec -it tp-mysql mysql -uroot -p teampulse\n"
        "docker exec -it tp-mysql mysql -uroot -p teampulse \\\n"
        "  -e \"select work_date,count(*) from attendance_record group by work_date order by work_date desc limit 7\"",
    )

    d.h2("8.5 学习路径建议")
    d.p("如果你要在此基础上继续深入（也是面试时可以讲的「下一步」）：")
    d.numbered(
        [
            "**异步化打卡落库**：MQTT 消费后先入队（Redis Stream / RabbitMQ），再异步写库，削峰且可重试",
            "**集成测试**：Testcontainers 起 MySQL + 嵌入式 broker，覆盖验签失败、重放、未知工号、并发重复提交",
            "**分布式任务调度**：引入 ShedLock，让定时任务在多实例下只执行一次",
            "**可观测性**：Micrometer + Prometheus + Grafana，重点关注打卡处理耗时与失败率",
            "**考勤规则多维化**：按部门/班次配置，加请假与加班抵消",
            "**前端实时化**：把轮询换成 SSE，配合后端事件推送",
            "**设备 OTA**：给固件加版本号与升级通道，实现远程升级",
        ]
    )
