# TeamPulse · 团队协作与物联网考勤平台

> 一套账号体系，同时覆盖「团队协作」与「硬件打卡考勤」。
> ESP32 打卡机通过 MQTT 上报，服务端实时判定迟到/早退并刷新考勤看板。

---

## 1. 它解决什么问题

中小企业常见的状态是：协作工具（日程/看板/日志）和考勤机是两套互不相通的系统，
人员名单要维护两遍，考勤数据只能月底从机器里导表格。

TeamPulse 把这些收在同一个平台里：

| 域 | 能力 |
|---|---|
| 协作域 | 用户与团队管理、日程、看板（拖拽改状态并反向同步日程）、工作日志、评论（含敏感词过滤） |
| 考勤域 | 打卡设备注册与密钥轮换、ESP32/MQTT 打卡接入、迟到早退自动判定、实时考勤看板、日汇总 |

---

## 2. 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.2.4 · Java 17 · MyBatis-Plus 3.5.7 · MySQL 8 · Redis 7 · Flyway |
| 安全 | Spring Security 6 + JWT（jjwt 0.12）· BCrypt（兼容历史明文自动升级） |
| 物联网 | Spring Integration MQTT 6.2 + Eclipse Paho · Mosquitto · ESP32（Arduino C++） |
| 前端 | Vue 3 · Vite 7 · Element Plus · Pinia · ECharts · Axios |
| 部署 | Docker Compose（5 容器）· Nginx 同源反代 · 单台 4C4G 云服务器 |

---

## 3. 架构

```
浏览器 ──► Nginx ──/api/──► backend (Spring Boot)
                        ├── MySQL    业务数据
                        ├── Redis    nonce 防重放
                        └── Mosquitto ◄── ESP32 打卡机 (MQTT)
```

一次打卡的链路：

```
指纹模块 --UART--> ESP32 --拼 JSON + HMAC 签名--> MQTT attendance/checkin
    --> Spring Integration 入站适配器
    --> 校验(设备/时钟漂移/签名/幂等/nonce)
    --> 判定签到签退 + 迟到早退
    --> 写 attendance_record + 重算 attendance_daily
    --> 回执 attendance/ack
前端考勤看板每 5 秒轮询 /api/v1/attendance/recent
```

---

## 4. 快速开始

### 4.1 方式一：Docker Compose（推荐，一条命令）

前置：一台装了 Docker 与 Docker Compose v2 的 Linux 服务器（4C4G 起）。

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

1. Flyway 执行 `V1/V2/V3` 建表并写入默认考勤规则与敏感词
2. 创建初始管理员（账号密码见 `.env` 的 `TPP_ADMIN_PASSWORD`）
3. 创建一台演示打卡设备，并在**后端日志里打印它的序列号与密钥**——把它填进模拟器即可跑通链路

```bash
docker compose logs backend | grep -A3 "演示打卡设备"
```

访问 `http://<服务器IP>/`，用管理员登录。

> 云服务器安全组需放行：`80`（Web）、`1883`（MQTT，供打卡机接入）。
> `3306` 已在 compose 中绑定 127.0.0.1，公网不可直连。

### 4.2 方式二：本地开发（看页面原型用这个）

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
DB_PASSWORD=你的密码 ./scripts/run-local.sh    # 必须传密码（脚本无默认值）
./scripts/run-local.sh --stop
```

脚本会自动找工具链、释放端口、启动后端（首次自动建库建表）与前端开发服务器，并打印访问地址。

**手动启动**：

```bash
# 后端
cd backend
DB_PASSWORD=你的密码 MQTT_ENABLED=false mvn spring-boot:run

# 前端（另开终端）
cd frontend
npm install
npm run dev          # http://localhost:5173
```

启动后访问 `http://127.0.0.1:5173/`，账号 `admin / admin123`。
详细步骤、常见问题与已验证结果见 **[docs/如何运行页面原型.md](docs/如何运行页面原型.md)**。

本地没有 Mosquitto 时，可在 `application-dev.yml` 或环境变量里关闭 MQTT：

```bash
MQTT_ENABLED=false
```

Web 接口照常可用，打卡链路改为走 HTTP 兜底通道 `/iot/v1/punch`（同样需要 HMAC 签名）。

---

## 5. 设备接入

### 5.1 用模拟器验证（不需要硬件）

```bash
# 零依赖，只用 Node 内置能力，走 HTTP 兜底通道
node device/simulator/http-simulator.mjs \
  --base http://localhost:8080 \
  --sn TP-ESP32-DEMO \
  --secret <后端日志里打印的密钥> \
  --employee E1000 \
  --loop
```

走真实 MQTT 链路：

```bash
cd device/simulator
npm install
npm start -- --broker mqtt://<服务器IP>:1883 --sn TP-ESP32-DEMO --secret <密钥>
```

### 5.2 真机固件

固件在 `device/esp32/attendance_node/attendance_node.ino`，接线、依赖库、烧录步骤见
`device/esp32/README.md`。

需要修改固件顶部的配置区：WiFi、MQTT 地址与账号、`deviceSn`、`deviceSecret`。

### 5.3 报文契约

打卡上行（`attendance/checkin`）：

```json
{
  "deviceSn": "TP-ESP32-DEMO",
  "employeeNo": "E1000",
  "punchTime": "2025-06-01 09:03:12",
  "punchType": "AUTO",
  "nonce": "a1b2c3d4e5f6",
  "sign": "3f9c...(HMAC-SHA256 小写hex)"
}
```

- 签名内容：`deviceSn|employeeNo|punchTime|nonce`
- 服务端校验：设备已注册 → 时钟漂移 ≤300s → HMAC 验签 → 幂等 → nonce 去重
- 回执（`attendance/ack`）：`code=0` 成功；`6xxxx` 为设备侧永久错误（不要重试）；`5xxxx` 可重试

---

## 6. 关键配置

所有敏感配置走环境变量，详见 `.env.example`。

| 变量 | 说明 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `REDIS_PASSWORD` | Redis 密码 |
| `MQTT_USERNAME` / `MQTT_PASSWORD` | MQTT 账号（固件里要填同样的值） |
| `JWT_SECRET` | JWT 签名密钥，**必须 ≥32 字节**，否则应用启动即失败 |
| `TPP_ADMIN_PASSWORD` | 初始管理员密码（仅首次启动生效） |
| `TPP_OPENAPI_ENABLED` | 是否开启 Swagger（生产建议 false） |

---

## 7. 安全与密钥管理

这个仓库是公开的，所以代码里**不含任何真实凭据**。真实值只存在于三个不会进入 Git 的地方：

| 位置 | 放什么 | 是否入库 |
|---|---|---|
| 环境变量（`.env` 或 `setx`） | 数据库 / Redis / MQTT 密码、JWT 密钥 | `.env` 已被忽略 |
| `device/esp32/attendance_node/secrets.h` | WiFi 账号、设备 `DEVICE_SECRET` | 已忽略，模板见 `secrets.h.example` |
| `device/simulator/config.json` | 模拟器用的设备密钥 | 已忽略，模板见 `config.example.json` |

配置文件里的敏感项一律写成 `${VAR:默认值}` 形式，例如 `application.yml`：

```yaml
spring:
  datasource:
    password: ${DB_PASSWORD:root}
```

固件则是「有则覆盖、无则占位」，保证别人 clone 下来仍能编译：

```cpp
#if __has_include("secrets.h")
#include "secrets.h"          // 本机真实值
#endif
#ifndef DEVICE_SECRET
#define DEVICE_SECRET "…"     // 占位值，验签必然失败
#endif
```

### 7.1 设备密钥怎么流转

1. 后端首次启动，`BootstrapDataInitializer` 用 `SecureRandom` 生成 32 字节随机数，
   转成 64 位十六进制写入 `iot_device.secret`，并打印到启动日志
2. 固件把同一份密钥填进 `secrets.h` 的 `DEVICE_SECRET`
3. 每次打卡，固件对 `deviceSn|employeeNo|punchTime|nonce` 做 **HMAC-SHA256**，
   签名随报文一起发出
4. 服务端用库里同一份密钥重算并比对，不一致返回 `60003`
5. `nonce` 进 Redis 去重窗口（默认 10 分钟），重复即拒绝，**防止报文重放**

因此密钥泄露等于别人可以伪造打卡数据——这也是它必须只留在本机的原因。

### 7.2 生产环境 fail-closed

宁可启动失败，也不要带着默认密钥上线：

| 位置 | 行为 |
|---|---|
| `application-prod.yml`：`teampulse.jwt.secret: ${TPP_JWT_SECRET:}` | 漏配时密钥不足 32 字节，`JwtTokenProvider` 直接抛异常拒绝启动，**不会静默回退**到 `application.yml` 里的开发密钥（否则拿到仓库的人就能伪造管理员 token） |
| `docker-compose.yml`：`TPP_ADMIN_PASSWORD: "${TPP_ADMIN_PASSWORD:?...}"` | 漏配时 compose 直接报错，**不会**用 `admin123` 建出弱口令管理员 |

### 7.3 提交前的自动检查

仓库自带 `.githooks/pre-commit`，提交时拦截四类问题：

| 检查项 | 会被拦的内容 |
|---|---|
| 敏感文件本身 | `.env`、`secrets.h`、`device/simulator/config.json`、`deploy/certs/` |
| 硬编码密码 | `DB_PASSWORD=realpass`（`${DB_PASSWORD:...}` 写法放行） |
| 设备密钥 | 任何 64 位十六进制串（`secrets.h.example` 里的占位值放行） |
| 私钥内容 | `-----BEGIN … PRIVATE KEY-----` |

**克隆仓库后需要启用一次**（钩子文件在仓库里，但启用开关是本机配置）：

```bash
git config core.hooksPath .githooks
```

确认是误报时可 `git commit --no-verify` 跳过。

> 文档里的环境相关路径统一写成 `<MySQL安装目录>` 这类占位符；
> 自己机器的真实路径记在 `docs/本机环境.local.md`（匹配 `*.local.md`，已被忽略）。

---

## 8. 项目结构

```
teampulse/
├── backend/            Spring Boot 后端（CONVENTIONS.md 是开发契约）
├── frontend/           Vue 3 前端（含生产 Nginx 配置）
├── device/             物联网设备侧（ESP32 固件 + 模拟器）
├── deploy/             部署资产（MySQL/MQTT 配置、发布与备份脚本）
├── docs/               技术手册（Word）与生成脚本
├── docker-compose.yml  五容器编排
└── .env.example        环境变量模板
```

---

## 9. 常用运维命令

```bash
# 发布（备份 → 构建 → 上线 → 健康检查）
./deploy/scripts/deploy.sh

# 手动备份数据库
./deploy/scripts/backup.sh

# 实时看后端日志
docker compose logs -f --tail=100 backend

# 直接订阅 MQTT，确认设备报文有没有到 broker
docker exec -it tp-mosquitto mosquitto_sub -t 'attendance/#' -v \
  -u "$MQTT_USERNAME" -P "$MQTT_PASSWORD"

# 进数据库看最近打卡
docker exec -it tp-mysql mysql -uroot -p teampulse \
  -e "select id,employee_no,punch_type,punch_time,status,late_minutes from attendance_record order by id desc limit 10"
```

排障请按这个顺序：设备是否联网 → 是否连上 broker → 报文是否到 broker → 后端日志错误码 → 数据库是否有记录。

---

## 10. 与原始团队项目的关系

本项目由一支团队的老项目重构而来，主要变更：

- 包名 `com.soft` → `com.teampulse`，工程目录 `houduan` → `backend`，`TodayJob_Vue_Project` → `frontend`
- 伪鉴权（请求头透传 userId）→ Spring Security + JWT；明文密码 → BCrypt（含自动升级）
- 手写建表脚本 → Flyway 版本化迁移；删除 4 张从未接入业务的僵尸表
- 统一响应/异常/错误码/分页/校验规范
- 新增物联网考勤域（设备、MQTT、打卡、判定、汇总、看板）
- 新增 Docker Compose 部署、健康检查、备份与发布脚本

详细的技术点拆解见 `docs/TeamPulse_技术手册.docx`。

---

## 11. 已知限制

- 定时任务未做分布式锁，多实例部署时需引入 Redis 锁或 ShedLock
- 考勤规则为全局单条，未支持按部门差异化
- 附件功能未实现（原项目的半成品已随表一起移除）
- 前端部分沿用旧页面交互，仅完成接口与状态管理的迁移

---

## 12. License

仅供学习与求职展示使用。
