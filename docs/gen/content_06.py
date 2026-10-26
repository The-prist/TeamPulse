"""第 6 章：部署与运维（单台 4C4G 云服务器）。"""


def build(d):
    d.h1("第 6 章  部署与运维")
    d.p(
        "这一章是「能不能真的跑起来」的部分。所有命令都在一台 4 核 4G、"
        "系统为 Ubuntu 22.04 / CentOS Stream 9 的云服务器上验证过思路。"
    )

    # ---------------- 6.1 ----------------
    d.h2("6.1 服务器准备")
    d.h3("6.1.1 安全组（云控制台）")
    d.table(
        ["端口", "用途", "是否对公网"],
        [
            ["22", "SSH 运维", "建议只对固定 IP 开放"],
            ["80", "HTTP（Web 前端 + API 反代）", "是"],
            ["443", "HTTPS", "配好证书后开放"],
            ["1883", "MQTT（ESP32 打卡机接入）", "是，且必须改默认账号密码"],
            ["3306", "MySQL", "**否**（compose 已绑定 127.0.0.1）"],
            ["6379", "Redis", "**否**"],
            ["8080", "后端", "**否**（只走 Nginx 反代）"],
        ],
        widths=[2.0, 8.0, 6.4],
    )

    d.h3("6.1.2 系统初始化")
    d.code(
        "# 1) 基础工具\n"
        "apt update && apt install -y git curl vim ufw\n"
        "\n"
        "# 2) 安装 Docker（官方脚本）\n"
        "curl -fsSL https://get.docker.com | sh\n"
        "systemctl enable --now docker\n"
        "\n"
        "# 3) 防火墙（与云安全组二选一或叠加）\n"
        "ufw allow 22/tcp\n"
        "ufw allow 80,443/tcp\n"
        "ufw allow 1883/tcp\n"
        "ufw --force enable\n"
        "\n"
        "# 4) 时区（考勤判定依赖本地时区，必须设对）\n"
        "timedatectl set-timezone Asia/Shanghai\n"
        "date",
        caption="时区错了会导致所有打卡被判为「时钟漂移」，这是最容易忽略的一步",
    )
    d.callout(
        "4G 机器务必先做这两件事",
        "① 加 2G swap（防止构建镜像时 OOM）：`fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile`；"
        "② 在服务器上构建前端镜像时 Node 会吃内存，若失败可改为本地构建后只传 dist。",
        fill="FFF1F0",
    )

    # ---------------- 6.2 ----------------
    d.h2("6.2 内存预算")
    d.p("4G 内存要塞下 5 个容器，必须精算。下面是各部分的上限配置与依据：")
    d.table(
        ["容器", "内存上限", "关键参数", "依据"],
        [
            ["mysql", "1.0G", "innodb_buffer_pool_size=384M", "数据量在十万行级，缓冲池 384M 足够；再大就挤占 JVM"],
            ["backend", "1.0G", "JAVA_OPTS=-XX:MaxRAMPercentage=70", "堆约 700M；G1GC 在 700M 堆下停顿可控"],
            ["redis", "192M", "maxmemory=128mb, allkeys-lru", "只存 nonce 与少量热点，128M 远超实际需求"],
            ["mosquitto", "64M", "message_size_limit=64KB", "纯转发，不做大量消息堆积"],
            ["frontend(nginx)", "64M", "—", "静态资源 + 反代，几乎不占内存"],
            ["合计", "≈2.3G", "—", "留 1.5G 给系统、Docker 守护进程与突发流量"],
        ],
        widths=[3.2, 2.4, 6.4, 5.4],
    )

    # ---------------- 6.3 ----------------
    d.h2("6.3 部署步骤（完整版）")
    d.code(
        "# 1) 拉代码\n"
        "mkdir -p /opt && cd /opt\n"
        "git clone <你的仓库地址> teampulse && cd teampulse\n"
        "\n"
        "# 2) 环境变量（每一个 CHANGE_ME 都必须改）\n"
        "cp .env.example .env\n"
        "vim .env\n"
        "\n"
        "# 生成强随机值：\n"
        "openssl rand -hex 32      # 用于 JWT_SECRET / MYSQL_ROOT_PASSWORD / REDIS_PASSWORD\n"
        "\n"
        "# 3) MQTT 密码文件（内容是哈希，不能手写）\n"
        "chmod +x deploy/scripts/*.sh\n"
        "./deploy/scripts/gen-mosquitto-passwd.sh\n"
        "\n"
        "# 4) 构建并启动\n"
        "docker compose up -d --build\n"
        "\n"
        "# 5) 观察启动（首次约 1~3 分钟）\n"
        "docker compose ps\n"
        "docker compose logs -f backend\n"
        "\n"
        "# 6) 拿到演示设备密钥\n"
        "docker compose logs backend | grep -A3 \"演示打卡设备\"",
    )

    d.h3("6.3.1 验证清单")
    d.code(
        "curl -s http://127.0.0.1/healthz                 # 前端存活\n"
        "curl -s http://127.0.0.1/actuator/health         # 后端健康（含 MySQL/Redis 探测）\n"
        "curl -s -X POST http://127.0.0.1/api/v1/auth/login \\\n"
        "  -H 'Content-Type: application/json' \\\n"
        "  -d '{\"username\":\"admin\",\"password\":\"你的密码\"}'   # 应返回 token\n"
        "\n"
        "docker exec -it tp-mosquitto mosquitto_sub -t 'attendance/#' -v \\\n"
        "  -u $MQTT_USERNAME -P $MQTT_PASSWORD                 # 另开终端，等待设备报文",
    )

    # ---------------- 6.4 ----------------
    d.h2("6.4 Nginx 配置要点")
    d.p("配置在 `frontend/nginx.conf`，随前端镜像一起打进容器。四个关键点：")
    d.table(
        ["配置", "作用", "不加会怎样"],
        [
            ["try_files $uri $uri/ /index.html", "SPA history 路由回退", "刷新 /attendance/records 会 404"],
            ["location /api/ 反代到 backend", "同源调用，无需 CORS", "前端只能跨域直连 8080，暴露后端端口且要配 CORS"],
            ["/assets/ 强缓存 1 年", "哈希文件名可长缓存", "每次访问都重新下载 JS/CSS，首屏变慢"],
            ["index.html no-cache", "发版后立刻生效", "用户可能长时间看到旧版引用的失效资源"],
            ["/actuator/ 限制内网 IP", "不对外暴露健康详情", "外部可探测内部依赖状态"],
        ],
        widths=[6.2, 5.4, 5.8],
    )

    # ---------------- 6.5 ----------------
    d.h2("6.5 域名与 HTTPS")
    d.code(
        "# 1) DNS：把域名 A 记录指向服务器公网 IP\n"
        "\n"
        "# 2) 申请证书（Let's Encrypt）\n"
        "apt install -y certbot\n"
        "certbot certonly --standalone -d your-domain.com\n"
        "# 证书在 /etc/letsencrypt/live/your-domain.com/\n"
        "\n"
        "# 3) 挂载到容器：docker-compose.yml 已预留\n"
        "#    - ./deploy/certs:/etc/nginx/certs:ro\n"
        "mkdir -p deploy/certs\n"
        "cp /etc/letsencrypt/live/your-domain.com/fullchain.pem deploy/certs/\n"
        "cp /etc/letsencrypt/live/your-domain.com/privkey.pem  deploy/certs/\n"
        "\n"
        "# 4) 取消 nginx.conf 里 HTTPS server 块的注释，并放开 443 端口映射\n"
        "docker compose up -d frontend\n"
        "\n"
        "# 5) 证书自动续期（证书 90 天过期）\n"
        "echo \"0 3 * * 1 certbot renew --quiet && cp ... && docker compose restart frontend\" | crontab -",
    )
    d.callout(
        "MQTT 的 TLS",
        "1883 是明文端口。若要加密，需在 Mosquitto 里配置 8883 + 证书，"
        "同时 ESP32 固件改用 WiFiClientSecure 并烧录 CA 证书。"
        "演示环境可以先用 1883（报文本身有 HMAC 签名保证完整性），但要能说清「生产应上 TLS」这个判断。",
    )

    # ---------------- 6.6 ----------------
    d.h2("6.6 备份与恢复")
    d.p("备份脚本 `deploy/scripts/backup.sh` 用 `docker exec` 调容器内 mysqldump，宿主机不需要装 MySQL 客户端。")
    d.code(
        "# 手动备份\n"
        "./deploy/scripts/backup.sh\n"
        "# 产物：deploy/backups/teampulse_20250601_030000.sql.gz\n"
        "\n"
        "# 每日 03:00 自动备份，保留 7 天\n"
        "crontab -e\n"
        "0 3 * * * /opt/teampulse/deploy/scripts/backup.sh >> /var/log/tp-backup.log 2>&1",
    )
    d.p("**恢复演练**（一定要真的演练一次，否则备份等于没有）：")
    d.code(
        "gunzip -c deploy/backups/teampulse_20250601_030000.sql.gz \\\n"
        "  | docker exec -i tp-mysql mysql -uroot -p'密码' teampulse\n"
        "\n"
        "docker exec -it tp-mysql mysql -uroot -p -e \"select count(*) from teampulse.attendance_record\"",
    )

    # ---------------- 6.7 ----------------
    d.h2("6.7 发布与回滚")
    d.code(
        "# 一键发布：备份 → 构建 → 上线 → 健康检查 → 清理旧镜像\n"
        "./deploy/scripts/deploy.sh\n"
        "\n"
        "# 跳过备份（仅限非数据结构变更的小改）\n"
        "./deploy/scripts/deploy.sh --skip-backup",
    )
    d.p("**回滚策略**：镜像打了固定 tag（`teampulse/backend:1.0.0`）。出问题时的回滚路径：")
    d.numbered(
        [
            "改 `.env` 或 compose 里的 tag 为上一个版本，`docker compose up -d`",
            "如果涉及数据结构，先停服 → 恢复备份 → 回滚镜像 → 启动（因为 Flyway 不支持自动降级）",
            "因此**破坏性 DDL 一定要拆成两次发布**：先加新列（兼容旧代码），下一个版本再删旧列",
        ]
    )

    # ---------------- 6.8 ----------------
    d.h2("6.8 监控与日志")
    d.table(
        ["需求", "当前做法", "下一步可做"],
        [
            ["容器存活", "healthcheck + `docker compose ps`", "接 Uptime Kuma 做外部探测"],
            ["应用日志", "logback 落文件 + json-file 轮转（20MB×3）", "接 Loki 或 ELK 集中检索"],
            ["JVM 指标", "Actuator /actuator/metrics", "接 Prometheus + Grafana（4G 机器要评估内存）"],
            ["慢 SQL", "my.cnf 已开启 slow_query_log（>2s）", "定期 review 慢查询日志"],
            ["业务监控", "考勤看板的实时流 + 设备在线数", "设备离线超 5 分钟触发告警（企微/钉钉机器人）"],
        ],
        widths=[2.6, 6.8, 7.0],
    )

    # ---------------- 6.9 ----------------
    d.h2("6.9 常见故障速查")
    d.table(
        ["现象", "最可能的原因", "定位命令"],
        [
            ["容器反复重启", "JWT_SECRET 不足 32 字节 / 数据库连不上", "`docker compose logs backend | tail -50`"],
            ["502 Bad Gateway", "backend 未就绪或已崩", "`docker compose ps` / `curl 127.0.0.1:8080/actuator/health`"],
            ["刷新页面 404", "Nginx 少了 try_files 回退", "检查 frontend/nginx.conf 的 location /"],
            ["登录报 500", "Flyway 未执行完或库名不一致", "`docker compose logs backend | grep -i flyway`"],
            ["打卡一直 60006", "服务器或设备时区错误", "`docker exec tp-backend date` 与设备串口日志对比"],
            ["打卡 60003 验签失败", "固件与库里的 secret 不一致", "设备页「重置密钥」后重新烧录"],
            ["前端拿不到数据但接口 200", "响应结构判断错误", "浏览器 Network 看响应体是否为 {code,data}"],
            ["构建镜像 OOM", "4G 机器构建前端时内存不足", "加 swap 或本地构建后只传产物"],
            ["磁盘满", "镜像与日志堆积", "`docker system prune -af` / `du -sh /var/lib/docker`"],
        ],
        widths=[3.4, 5.0, 9.0],
    )

    # ---------------- 6.10 ----------------
    d.h2("6.10 成本估算（面试可讲）")
    d.table(
        ["项", "规格", "参考价格"],
        [
            ["轻量应用服务器", "4C4G, 80G SSD, 5M 带宽", "约 60~120 元/月（新用户或活动价更低）"],
            ["域名", ".com / .cn", "约 30~80 元/年"],
            ["HTTPS 证书", "Let's Encrypt", "免费"],
            ["对象存储备份（可选）", "备份文件异地存一份", "几元/月"],
            ["合计", "—", "**约 100 元/月以内**"],
        ],
        widths=[5.0, 6.4, 5.0],
    )
    d.p("这个成本结构本身就是很好的面试素材：能说清「为什么选轻量服务器而不是 K8s」，"
        "以及「在 4G 内存约束下如何做技术取舍」。")
