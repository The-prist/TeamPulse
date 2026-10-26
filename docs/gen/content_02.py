"""第 2 章：物联网考勤子系统（项目核心差异点）。"""


def build(d):
    d.h1("第 2 章  物联网考勤子系统")
    d.p(
        "这一章是整个项目最需要在面试中讲清楚的部分。它串联了 **嵌入式固件 → 消息中间件 → 服务端 → 数据库 → 前端** "
        "五个层次，是「真的做过物联网」与「只是调过接口」的分界线。"
    )

    # ---------------- 2.1 ----------------
    d.h2("2.1 端到端链路")
    d.code(
        "①  员工把手指按在指纹模块上\n"
        "        │\n"
        "②  ESP32 通过 UART(Serial2) 读到匹配结果：\"OK,E1001,92\\r\\n\"\n"
        "        │   （employeeNo=E1001，匹配分 92）\n"
        "③  取 NTP 校准后的本地时间，生成随机 nonce\n"
        "④  拼规范化串： deviceSn|employeeNo|punchTime|nonce\n"
        "⑤  HMAC-SHA256(设备密钥, 规范化串) → 小写 hex 作为 sign\n"
        "⑥  组装 JSON，publish 到 MQTT 主题 attendance/checkin（QoS 1）\n"
        "        │\n"
        "⑦  Mosquitto 转发给已订阅的 backend\n"
        "⑧  Spring Integration 入站适配器收到消息 → mqttInboundChannel\n"
        "⑨  AttendanceInboundHandler 按主题分流到打卡处理\n"
        "⑩  AttendanceService.handlePunch 依次校验：\n"
        "      字段完整性 → 设备存在/未禁用 → 时钟漂移 → HMAC 签名\n"
        "      → 幂等(messageId) → 同秒去重 → nonce 防重放\n"
        "      → 工号匹配用户 → 判定签到/签退 → 计算迟到/早退\n"
        "⑪  写 attendance_record（唯一键兜底防重复）\n"
        "⑫  立即重算 attendance_daily（当日汇总）\n"
        "⑬  更新设备最近心跳（视为设备存活）\n"
        "⑭  向 attendance/ack 下发回执（code=0 成功）\n"
        "        │\n"
        "⑮  ESP32 收到回执 → 蜂鸣器短鸣 + LED 常亮 300ms\n"
        "⑯  前端考勤看板每 5 秒轮询 /attendance/recent，新记录出现在「实时打卡流」",
        caption="一次打卡的完整生命周期",
    )
    d.muted(
        "对应源码：device/esp32/attendance_node/attendance_node.ino → "
        "backend/.../attendance/mqtt/AttendanceInboundHandler.java → "
        "backend/.../attendance/service/AttendanceService.java#handlePunch"
    )

    # ---------------- 2.2 ----------------
    d.h2("2.2 报文契约")
    d.p(
        "设备与服务端之间只有三个主题、两种上行报文、一种下行报文。契约一旦固定，固件与服务端就可以并行开发——"
        "这也是我在文档里把它单独列一节的原因：**接口契约是最容易沟通出错的地方**。"
    )

    d.h3("2.2.1 打卡上行：attendance/checkin")
    d.table(
        ["字段", "类型", "必填", "说明"],
        [
            ["deviceSn", "string", "是", "设备序列号，必须已在 iot_device 表注册"],
            ["employeeNo", "string", "是", "员工工号，对应 user.employee_no"],
            ["punchTime", "string", "是", "设备本地时间，格式固定 yyyy-MM-dd HH:mm:ss"],
            ["punchType", "string", "否", "AUTO / CHECK_IN / CHECK_OUT；缺省 AUTO 由服务端判定"],
            ["nonce", "string", "是", "一次性随机串，10 分钟内不可重复"],
            ["sign", "string", "是", "HMAC-SHA256 小写十六进制签名"],
            ["firmwareVersion", "string", "否", "固件版本，写入设备心跳记录便于灰度排查"],
            ["messageId", "string", "否", "设备侧消息 ID；不传则服务端按 deviceSn+nonce 派生"],
            ["matchScore", "int", "否", "指纹/刷卡匹配分数，仅留痕不参与判定"],
        ],
        widths=[3.2, 1.8, 1.4, 10.0],
    )
    d.code(
        "{\n"
        '  "deviceSn":  "TP-ESP32-DEMO",\n'
        '  "employeeNo": "E1000",\n'
        '  "punchTime": "2025-06-01 09:03:12",\n'
        '  "punchType": "AUTO",\n'
        '  "nonce":     "a1b2c3d4e5f6",\n'
        '  "sign":      "3f9c...(64位小写hex)",\n'
        '  "firmwareVersion": "1.0.0",\n'
        '  "matchScore": 92\n'
        "}",
        caption="打卡报文示例",
    )

    d.h3("2.2.2 心跳上行：attendance/heartbeat")
    d.p("每 60 秒一次，用于维护设备在线状态。心跳的待签名串只有两个字段：`deviceSn|nonce`。")
    d.code(
        '{ "deviceSn": "TP-ESP32-DEMO", "firmwareVersion": "1.0.0",\n'
        '  "ip": "192.168.1.50", "nonce": "9f8e7d", "sign": "...", "ts": 1748765000 }',
    )

    d.h3("2.2.3 回执下行：attendance/ack")
    d.code(
        '{ "deviceSn": "TP-ESP32-DEMO", "employeeNo": "E1000", "messageId": "TP-ESP32-DEMO-a1b2c3d4e5f6",\n'
        '  "code": 0, "message": "打卡成功", "recordId": 128, "punchType": "CHECK_IN",\n'
        '  "status": "LATE", "lateMinutes": 3, "serverTime": 1748765000000 }',
    )
    d.callout(
        "错误码语义（重要设计）",
        "code=0 成功；6xxxx 为设备侧永久错误（设备未注册/验签失败/工号不存在 → 设备不应重试，直接播报错误）；"
        "5xxxx 为服务端临时错误（设备可重试）。设备端固件据此决定「进离线队列还是丢弃」，"
        "避免了无意义的无限重试把 broker 打满。",
    )

    # ---------------- 2.3 ----------------
    d.h2("2.3 设备认证：为什么用 HMAC 而不是 JWT")
    d.p(
        "用户侧用 JWT，设备侧用预共享密钥 HMAC，这是一个有意的差异化设计，理由有三条："
    )
    d.bullets(
        [
            "**算力与存储**：ESP32 做 HMAC-SHA256 只需几毫秒；而 JWT 需要维护令牌生命周期、过期刷新逻辑，"
            "还要在设备上安全存一个可长期使用的 refresh token，复杂度反而更高。",
            "**设备是固定终端**：一台打卡机 7×24 挂在墙上，不存在「用户换设备登录」的场景，"
            "预共享密钥 + 轮换机制比令牌更合适。",
            "**影响面隔离**：设备密钥泄露只能伪造打卡，拿不到任何用户权限；"
            "而如果复用用户 JWT 体系，密钥泄露等于账号泄露。",
        ]
    )

    d.h3("2.3.1 签名算法与规范化串")
    d.p(
        "签名 = HMAC-SHA256(secret, canonical)，输出小写十六进制。canonical 的字段顺序、分隔符**必须**在固件与服务端完全一致，"
        "否则验签必然失败——这是对接阶段最容易踩的坑。"
    )
    d.code(
        "# 打卡\n"
        'canonical = f"{deviceSn}|{employeeNo}|{punchTime}|{nonce}"\n'
        "\n"
        "# 心跳\n"
        'canonical = f"{deviceSn}|{nonce}"\n'
        "\n"
        "secret = 32 字节随机数的 hex（64 字符），只在设备注册/密钥轮换时下发一次",
    )
    d.code(
        "// 服务端实现要点（DeviceSignatureVerifier）\n"
        "Mac mac = Mac.getInstance(\"HmacSHA256\");\n"
        "mac.init(new SecretKeySpec(secret.getBytes(UTF_8), \"HmacSHA256\"));\n"
        "byte[] digest = mac.doFinal(payload.getBytes(UTF_8));\n"
        "String expected = HexFormat.of().formatHex(digest);\n"
        "\n"
        "// 用常量时间比较，而不是 String.equals，避免时序侧信道\n"
        "MessageDigest.isEqual(expected.getBytes(UTF_8), provided.getBytes(UTF_8));",
        caption="backend/.../attendance/service/DeviceSignatureVerifier.java",
    )

    d.h3("2.3.2 密钥的完整生命周期")
    d.table(
        ["阶段", "动作", "实现位置"],
        [
            ["注册", "管理员在「打卡设备」页注册设备，服务端生成 32 字节随机密钥，仅在创建响应里返回一次", "DeviceService#create"],
            ["烧录", "把 deviceSn + secret 写入 ESP32 的配置区（或 NVS）", "attendance_node.ino 顶部 #define"],
            ["使用", "每次上报用密钥计算 HMAC；服务端用库里的密钥验签", "DeviceSignatureVerifier"],
            ["轮换", "设备丢失或密钥疑似泄露时，管理员点「重置密钥」，旧密钥立即失效", "DeviceService#rotateSecret"],
            ["展示", "除创建/轮换外，任何接口都不返回 secret（实体上手动置空）", "DeviceService#page"],
        ],
        widths=[1.8, 11.0, 3.6],
    )

    # ---------------- 2.4 ----------------
    d.h2("2.4 防重放：nonce + 时钟漂移双保险")
    d.p(
        "HMAC 只解决「报文没被篡改」，解决不了「报文被原样重放」——攻击者抓到一次合法报文，"
        "原封不动再发一遍，签名依然合法。所以必须再加两道闸门："
    )
    d.numbered(
        [
            "**时钟漂移窗口**：服务端校验 |punchTime - 服务器当前时间| ≤ 300 秒。"
            "超窗直接拒收（错误码 60006）。这保证了「今天截获的报文下周才重放」这条路被堵死。",
            "**nonce 去重**：每个报文的 nonce 在 10 分钟窗口内只允许出现一次。"
            "服务端用 Redis 的 SET key val NX EX ttl 原子命令实现，重复即拒（错误码 60004）。",
        ]
    )
    d.p("把这两条组合起来看，攻击窗口被压缩到「300 秒内 + nonce 未用过」，实际上已不可利用。")

    d.h3("2.4.1 为什么 nonce 去重一定要用 Redis")
    d.p(
        "如果用数据库唯一键做去重，每条打卡都会多一次写库；而 nonce 是短期数据，"
        "写完 10 分钟就没用了，放在 MySQL 里会产生大量垃圾行并拖慢清理。"
        "Redis 的 `SET NX EX` 是单命令原子操作，天然适合「一次写入 + 自动过期」。"
    )
    d.code(
        "// NonceDedupService\n"
        "Boolean ok = redisTemplate.opsForValue()\n"
        "        .setIfAbsent(\"tp:nonce:\" + deviceSn + \":\" + nonce, \"1\", Duration.ofMinutes(10));\n"
        "return Boolean.TRUE.equals(ok);   // true=首次使用，false=重放",
    )
    d.callout(
        "降级设计（面试加分点）",
        "Redis 挂掉不能让打卡停摆。NonceDedupService 在 Redis 不可用时自动降级为进程内 ConcurrentHashMap（带过期时间与惰性清理），"
        "同时数据库上还有 (employee_no, punch_time) 唯一索引作为最后一道防线。"
        "代价是降级期间多实例部署的去重能力下降——这个取舍必须能主动说出来。",
    )

    # ---------------- 2.5 ----------------
    d.h2("2.5 幂等：三个层次")
    d.p("MQTT 的 QoS 1 语义是「至少一次」，也就是**可能重复投递**。所以服务端必须有幂等能力。")
    d.table(
        ["层次", "机制", "作用"],
        [
            ["报文级", "attendance_record.message_id 唯一索引", "同一报文重复投递 → 查到已有记录，直接返回原结果（code=0），不产生新行"],
            ["业务级", "attendance_record(employee_no, punch_time) 唯一索引", "同一人同一秒的两次上报（可能 messageId 不同）只保留一条"],
            ["代码级", "处理前先 selectOne 查询，命中直接返回", "避免依赖异常做流程控制（DuplicateKeyException 会让事务标记回滚）"],
        ],
        widths=[1.8, 6.4, 8.2],
    )
    d.p(
        "**这里有个容易被追问的细节**：为什么不直接 insert 然后 catch DuplicateKeyException？"
        "因为在 @Transactional 方法里，一旦抛过持久层异常，Spring 会把当前事务标记为 rollback-only，"
        "后续即使 catch 住也无法提交，最终在方法返回时抛 UnexpectedRollbackException。"
        "所以正确做法是「先查后插」，让唯一索引只在并发极端情况下兜底。"
    )

    # ---------------- 2.6 ----------------
    d.h2("2.6 迟到 / 早退判定口径")
    d.p(
        "判定逻辑被抽成纯函数类 `AttendanceCalculator`，不依赖 Spring、不访问数据库，"
        "因此可以用单元测试把所有边界固定下来。这是我在重构时特意做的——"
        "旧项目把这段逻辑埋在 Service 里，改一个宽限规则要人工回归所有场景。"
    )

    d.h3("2.6.1 规则定义")
    d.table(
        ["参数", "含义", "示例"],
        [
            ["work_start", "规定上班时间", "09:00"],
            ["work_end", "规定下班时间", "18:00"],
            ["late_grace_minutes", "迟到宽限：晚于「上班+宽限」才算迟到", "5 分钟"],
            ["early_grace_minutes", "早退宽限：早于「下班-宽限」才算早退", "5 分钟"],
            ["work_days", "工作日，ISO 星期（1=周一）", "1,2,3,4,5"],
        ],
        widths=[4.0, 8.0, 4.4],
    )

    d.h3("2.6.2 边界用例（面试常问）")
    d.table(
        ["场景", "打卡时间", "结果", "说明"],
        [
            ["刚好在宽限内", "09:05", "正常", "不晚于 09:00+5min，不算迟到"],
            ["刚过宽限一秒", "09:05:01", "迟到 5 分钟", "迟到分钟数从 09:00 起算，不是从宽限点起算"],
            ["提前到岗", "08:40", "正常", "早到不产生任何异常状态"],
            ["卡在下班边界", "18:00", "正常", "不早于 18:00-5min"],
            ["提前 4 分钟走", "17:56", "正常", "在早退宽限内"],
            ["提前 10 分钟走", "17:50", "早退 10 分钟", "早退分钟数算到 18:00 为止"],
            ["迟到 + 早退", "09:30 打卡、17:30 下班", "LATE_AND_EARLY", "日汇总里合并为一个状态"],
            ["只打一次卡", "只有 09:00 一条", "lastCheckOut 为空", "在岗时长按 0 计，日报里标记异常待人工确认"],
            ["非工作日打卡", "周六 10:00", "正常（备注非工作日）", "仍留痕，但不进入统计口径"],
            ["设备时间快 10 分钟", "设备显示 09:10", "拒收 60006", "时钟漂移超窗，提示校准 NTP"],
        ],
        widths=[3.2, 4.6, 3.6, 5.0],
    )
    d.p("**关键口径必须能一句话说清**：迟到分钟数从「规定上班时间」算起，宽限只是「算不算迟到」的阈值。")
    d.p("之所以这样设计，是因为员工关心的是「我迟到了几分钟」，而不是「我超出了宽限几分钟」——后者在人力口径上没有意义。")

    d.h3("2.6.3 签到与签退的判定")
    d.p(
        "设备上报 `AUTO` 时，服务端按「当天已有记录数」判断：**当天第一条 = 签到，之后都是签退**。"
        "这样一台设备、一次按键就能覆盖「上班打一次、下班打一次」，不需要用户选择。"
        "如果设备固件明确知道按键含义（比如两个物理按钮），也可以直接上报 CHECK_IN / CHECK_OUT 覆盖该逻辑。"
    )
    d.code(
        "// AttendanceCalculator#resolvePunchType\n"
        "if (reportedType 是 IN/CHECK_IN)  return CHECK_IN;\n"
        "if (reportedType 是 OUT/CHECK_OUT) return CHECK_OUT;\n"
        "return existingRecordsToday == 0 ? CHECK_IN : CHECK_OUT;",
    )

    # ---------------- 2.7 ----------------
    d.h2("2.7 双层存储：事件流 + 日汇总")
    d.p(
        "这是考勤子系统里最能体现「工程判断」的设计。原始打卡是**事件流**"
        "（只追加，永不修改），但报表要的是**每人每天一行**。如果每次都从事件流 group by 出报表，"
        "当月数据量到十万级后接口就会明显变慢。"
    )
    d.table(
        ["表", "定位", "写入时机", "查询场景"],
        [
            ["attendance_record", "事件流，只增不改，含原始报文", "每次打卡", "明细追溯、审计、排障"],
            ["attendance_daily", "每人每天一行汇总", "打卡后立即重算 + 每日 00:05 兜底", "看板、趋势图、个人月度"],
        ],
        widths=[3.6, 5.2, 4.6, 3.4],
    )
    d.p("汇总的触发有两个来源，缺一不可：")
    d.bullets(
        [
            "**打卡后立即重算**：保证看板实时。用户打完卡刷新页面就能看到状态变化。",
            "**每日 00:05 定时补齐**：解决「当天一次卡都没打」的人——这类人没有任何事件能触发计算，"
            "只能靠定时任务扫一遍在职员工，标记为 ABSENT。同时它也是接口异常回滚后的修复通道。",
        ]
    )

    # ---------------- 2.8 ----------------
    d.h2("2.8 设备在线状态与离线巡检")
    d.p(
        "在线状态不是靠「记住连接」而是靠**心跳 + 超时判定**，因为 MQTT 的 TCP 连接可能假死"
        "（网络中断但对端不知道），只有应用层心跳最可靠。"
    )
    d.numbered(
        [
            "设备每 60 秒发一次心跳（topic: attendance/heartbeat）",
            "服务端 IotDeviceMapper#touchHeartbeat 用 UPDATE 只改 status/last_heartbeat，不整行覆盖，避免冲掉管理员刚改的名称",
            "DeviceHealthScheduler 每 60 秒执行一次：把 last_heartbeat 早于 now-2min 的 ONLINE 设备批量置为 OFFLINE",
            "前端设备列表用颜色标签展示在线/离线/已禁用",
        ]
    )
    d.p(
        "**为什么用「定时扫描 + 单条 UPDATE」而不是给每台设备起一个定时器**："
        "设备数量增长时，前者是 O(1) 条 SQL，后者是 O(n) 个线程。这是一个典型的「用查询换调度」的取舍。"
    )

    # ---------------- 2.9 ----------------
    d.h2("2.9 MQTT 可靠性设计")
    d.table(
        ["关注点", "做法", "理由"],
        [
            ["消息不丢", "QoS 1 + cleanSession=false + 持久化", "设备短暂离线期间的消息在重连后补投；broker 重启后会话仍在"],
            ["连接自愈", "MqttConnectOptions#setAutomaticReconnect(true)", "Paho 客户端自动重连，指数退避"],
            ["启动不阻塞", "入站适配器 setAutoStartup(false)，由 MqttLifecycleManager 手动启动 + 30s 重试", "broker 没起来时应用仍能启动，Web 接口照常可用，只是实时打卡降级"],
            ["回执不阻塞", "AckPublisher 抽成接口，MQTT 关闭时装配 Noop 实现", "单元测试/本地无 broker 也能跑通业务逻辑"],
            ["客户端 ID 唯一", "配置项 MQTT_CLIENT_ID，多实例部署时每台不同", "同 ID 会被 broker 互相踢下线"],
            ["消息大小限制", "broker 端 message_size_limit=64KB", "防止异常固件发大包打爆内存"],
        ],
        widths=[2.6, 6.8, 7.0],
    )
    d.callout(
        "一个真实的坑",
        "Spring Integration 6.x 移除了 MqttPahoMessageDrivenChannelAdapter#setRecoveryInterval（我编译时才发现，"
        "5.x 的教程里到处都是这个方法）。6.x 的重连完全交给 MqttConnectOptions#setAutomaticReconnect。"
        "这类「教程版本落后于依赖版本」的问题，只能靠真正跑一遍编译来解决。",
    )

    # ---------------- 2.10 ----------------
    d.h2("2.10 前端实时看板：为什么用轮询而不是 WebSocket")
    d.p(
        "「实时」这个词容易让人本能地选 WebSocket，但在这个场景下轮询是更优解，理由是可以量化的："
    )
    d.table(
        ["维度", "5 秒轮询", "WebSocket"],
        [
            ["服务端连接数", "无状态，用完即走", "每个打开看板的浏览器占一条长连接"],
            ["4C4G 资源开销", "几乎为零", "连接维持、心跳、代理配置都需要额外内存与调优"],
            ["Nginx 配置", "普通反代即可", "需要 Upgrade 头 + 长连接超时 + 可能的跨域额外处理"],
            ["实时延迟", "平均 2.5 秒", "毫秒级"],
            ["单个响应体", "约 1~2KB（20 条记录）", "增量推送，更小"],
        ],
        widths=[3.4, 6.0, 7.0],
    )
    d.p(
        "对「打卡」这种低频事件（一个公司一天也就几百次），2.5 秒的平均延迟完全够用，"
        "而 WebSocket 带来的运维复杂度与内存占用在 4G 机器上不划算。"
        "**如果将来要做秒级推送**，把 `/attendance/recent` 换成 SSE 即可，前端只需要改数据来源，页面结构不变——"
        "这也是为什么我把轮询逻辑收敛在一个 `startPolling/stopPolling` 里。"
    )
    d.code(
        "// AttendanceDashboard.vue\n"
        "timer = window.setInterval(loadRecent, 5000);       // 组件挂载后启动\n"
        "onBeforeUnmount(() => stopPolling());               // 卸载时必须清理，否则路由切走后仍在请求\n"
        "\n"
        "// axios 侧对该接口开启 silent：轮询失败不弹 toast，避免网络抖动时刷屏",
    )
    d.callout(
        "容易犯的错",
        "页面切走后忘记 clearInterval，会导致定时器越积越多、后台持续请求，"
        "严重时把浏览器标签页卡死。这类问题在评审时很扎眼，务必在 onBeforeUnmount 里清理。",
        fill="FFF1F0",
    )

    # ---------------- 2.11 ----------------
    d.h2("2.11 设备侧固件要点")
    d.p("固件是纯 C/Arduino，核心难点在「资源受限下保证可靠」：")
    d.table(
        ["要点", "实现", "为什么"],
        [
            ["时间准确", "configTime + NTP，时区 CST-8", "服务端允许 300 秒漂移；设备时钟错了整条链路都会被拒"],
            ["JSON 组装", "手工字符串拼接，不依赖 ArduinoJson", "少一个库依赖，编译体积小；但必须对 employeeNo 做白名单校验防注入"],
            ["HMAC", "mbedtls/md.h（ESP32 SDK 自带）", "无需额外引库"],
            ["离线队列", "RTC_DATA_ATTR 环形缓冲，20 条", "断网时不丢打卡；重连后重发并重新签名"],
            ["nonce 生成", "硬件随机数 + millis 混合", "保证唯一，避免被服务端判为重放"],
            ["非阻塞循环", "loop() 里用 millis() 差值判断，不用 delay()", "delay 会阻塞心跳与串口读取，导致掉线"],
            ["millis 溢出", "用 (uint32_t)(now - last) 写法", "49 天溢出是嵌入式经典坑"],
            ["状态反馈", "LED + 蜂鸣器区分成功/失败", "现场员工需要即时反馈，不能靠网页"],
        ],
        widths=[2.6, 6.4, 7.4],
    )

    # ---------------- 2.12 ----------------
    d.h2("2.12 排障地图（设备不上报时按这个顺序查）")
    d.numbered(
        [
            "**设备连不上 WiFi**：看串口日志是否打印 IP；确认 SSID/密码、是否为 5G-only 频段（ESP32 只支持 2.4G）。",
            "**连不上 broker**：`docker logs tp-mosquitto` 看有没有认证失败；确认 1883 端口在云服务器安全组里放行。",
            "**报文发出但后端没反应**：`docker exec -it tp-mosquitto mosquitto_sub -t 'attendance/#' -v` 直接订阅，确认报文是否真的到了 broker。",
            "**后端日志报 60003 验签失败**：核对 canonical 字段顺序与分隔符；确认密钥没有前后空格；确认固件与库里的 secret 一致。",
            "**报 60006 时钟漂移**：设备没同步 NTP，或时区设置错误（东八区应为 CST-8）。",
            "**报 60001 设备未注册**：deviceSn 与 iot_device 表不一致；注意大小写。",
            "**报 61001 工号不存在**：user.employee_no 没填或填错。",
            "**打卡成功但看板不刷新**：检查 /attendance/recent 是否 200；检查前端轮询开关是否被关掉。",
        ]
    )
    d.code(
        "# 最常用的一组排障命令\n"
        "docker compose ps                             # 各容器状态\n"
        "docker compose logs -f --tail=100 backend     # 后端实时日志\n"
        "docker exec -it tp-mosquitto mosquitto_sub -t 'attendance/#' -v -u $MQTT_USERNAME -P $MQTT_PASSWORD\n"
        "docker exec -it tp-mysql mysql -uroot -p -e \"select * from teampulse.attendance_record order by id desc limit 5\"",
        caption="建议把这段存成备忘录，演示前先跑一遍",
    )
