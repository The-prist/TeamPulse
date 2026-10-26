# 设备模拟器

不用硬件也能把「设备 → 后端」这条链路跑通。两个脚本模拟的是**同一台打卡机**，走两条不同的入口：

| | `http-simulator.mjs` | `mqtt-simulator.mjs` |
|---|---|---|
| 链路 | HTTP `POST /iot/v1/punch` | MQTT broker → `attendance/checkin` → 订阅 `attendance/ack` |
| 依赖 | **零依赖**，只用 Node 内置 `fetch` + `node:crypto` | 需要 `npm install`（唯一依赖 `mqtt`） |
| 用途 | 现场/本地快速冒烟、broker 挂掉时的降级验证、CI 里造数据 | 验证真实链路：broker、主题、QoS、下行回执 |
| 心跳 | 不发心跳（`/iot/v1/heartbeat` 虽然存在，但本脚本专注打卡链路的快速验证） | 每 60 秒发 `attendance/heartbeat` |
| 回执 | 直接在 HTTP 响应体里拿到 | 订阅 `attendance/ack` 异步收 |
| 离线队列 | 无 | 有（内存 20 条，重连后补发） |
| 环境要求 | Node.js **18+** | Node.js **18+** + 一个可用的 MQTT broker |

> 一句话：**先跑 HTTP 版确认签名/设备密钥是对的，再跑 MQTT 版确认 broker 与回执链路是通的。**
> 两者共用同一套 `deviceSn` / `deviceSecret`，所以 HTTP 版通了、MQTT 版报 `60003` 就是 broker 或报文传输的问题。

```
device/simulator/
├── http-simulator.mjs        # 零依赖 HTTP 版
├── mqtt-simulator.mjs        # MQTT 真实链路版
├── package.json              # 只声明 mqtt 一个依赖
├── config.example.json       # MQTT 版示例配置（复制成 config.json 再填密钥）
└── README.md                 # 本文档
```

---

## 0. 先拿到设备密钥（两个版本的共同前提）

后端**首次启动且设备表为空**时会自动创建演示设备并在日志里打印密钥：

```
============================================================
 已创建演示打卡设备（仅用于联调，生产请删除）
   设备序列号: TP-ESP32-DEMO
   设备密钥  : 6f1c8d...（64 位十六进制）
 把这两项填进 device/simulator/config.json 即可模拟上报
============================================================
```

想新建一台设备：管理端 `POST /api/v1/devices`（需要 admin 的 JWT），响应里带 `secret`。

---

## 1. HTTP 版（零依赖，推荐先跑这个）

```bash
# 单次打卡（工号默认 E1000，地址默认 http://localhost:8080）
node http-simulator.mjs --secret <64位十六进制密钥>

# 循环模式：每 5 秒随机从 E1001/E1002/E1003 挑一个打卡
node http-simulator.mjs --secret <64位十六进制密钥> --loop

# 全用环境变量（适合 docker / CI）
BASE_URL=http://192.168.1.100:8080 \
DEVICE_SN=TP-ESP32-DEMO \
DEVICE_SECRET=<64位十六进制密钥> \
EMPLOYEE_NO=E1000 \
node http-simulator.mjs
```

参数与环境变量：

| 命令行 | 环境变量 | 默认值 | 说明 |
|---|---|---|---|
| `--url` | `BASE_URL` | `http://localhost:8080` | 后端地址，末尾的 `/` 会被去掉 |
| `--sn` | `DEVICE_SN` | `TP-ESP32-DEMO` | 设备序列号 |
| `--secret` | `DEVICE_SECRET` | 无（**必填**） | 设备密钥 |
| `--employee` | `EMPLOYEE_NO` | `E1000` | 工号 |
| `--loop` | — | 关闭 | 循环发送 |
| `--interval` | — | `5000` | 循环间隔（毫秒） |
| — | `ATTENDANCE_TZ` | 本机时区 | 生成 `punchTime` 用的时区，如 `Asia/Shanghai` |

`--loop` 模式会随机挑 `E1001/E1002/E1003`，**这三个工号不一定存在于数据库**，
返回 `61001 工号不存在` 属于正常现象（记录仍会留痕）。想看到成功回执就用默认的 `E1000`（初始化管理员工号）。

输出示例：

```
◆ TeamPulse 设备 HTTP 模拟器（零依赖，走 /iot/v1/punch 兜底链路）
  BASE_URL  = http://localhost:8080
  DEVICE_SN = TP-ESP32-DEMO
  工号      = E1000
  时区      = 本机时区
  模式      = 单次

→ POST http://localhost:8080/iot/v1/punch
  {"deviceSn":"TP-ESP32-DEMO","employeeNo":"E1000","punchTime":"2025-06-01 09:03:12","punchType":"AUTO","nonce":"a1b2c3d4e5f6a7b8","sign":"...","firmwareVersion":"1.0.0","matchScore":92}
← HTTP 200 (37ms)
  {"code":0,"message":"ok","data":{"deviceSn":"TP-ESP32-DEMO",...,"code":0,"message":"打卡成功","recordId":7,"punchType":"CHECK_IN","status":"LATE","lateMinutes":3,"serverTime":1748768592000},"timestamp":1748768592000}
✔ 打卡成功 employeeNo=E1000 punchType=CHECK_IN status=LATE lateMinutes=3 recordId=7
```

退出码：`0` 成功、`1` 失败、`2` 参数/配置错。

> 注意后端返回的是统一包装体 `{code, message, data, timestamp}`，
> 真正的结果在 `data` 里（`data.code`）。脚本已经帮你拆了壳：
> 业务失败时 HTTP 状态是 4xx/5xx 且 `data` 为 `null`，两种形态都能识别。

---

## 2. MQTT 版（真实链路）

```bash
npm install                       # 只装 mqtt 一个包
cp config.example.json config.json

# 编辑 config.json，至少填 deviceSecret；broker 要开密码认证时再填 username/password
#   "deviceSecret": "6f1c8d...（64 位十六进制）"
#   "brokerUrl": "mqtt://192.168.1.100:1883"

npm start                         # 等价于 node mqtt-simulator.mjs
```

常用变体：

```bash
# 每 5 秒打一次卡（覆盖配置里的 punchIntervalSeconds）
node mqtt-simulator.mjs --interval 5

# 固定工号、指定配置文件
node mqtt-simulator.mjs --config ./config.json --employee E1000

# 完全不写配置文件，全走环境变量
MQTT_BROKER_URL=mqtt://192.168.1.100:1883 \
MQTT_USERNAME=teampulse MQTT_PASSWORD=<密码> \
DEVICE_SN=TP-ESP32-DEMO DEVICE_SECRET=<密钥> \
node mqtt-simulator.mjs
```

### config.json 字段

| 字段 | 默认值 | 说明 |
|---|---|---|
| `brokerUrl` | `mqtt://localhost:1883` | **必须 `mqtt://` 开头**。后端配置里的 `tcp://` 是 Paho 语法，`mqtt` 这个包不认 |
| `username` / `password` | 空 | broker 开了密码认证就填（对应 `.env` 的 `MQTT_USERNAME/MQTT_PASSWORD`） |
| `deviceSn` | `TP-ESP32-DEMO` | 必须与 `iot_device.device_sn` 一致 |
| `deviceSecret` | 空（**必填**） | 必须与 `iot_device.secret` 完全一致，否则 `60003` |
| `firmwareVersion` | `1.0.0` | 随报文上报，便于和设备区分批次 |
| `employeeNos` | `["E1000", ...]` | 工号池，每次随机挑一个 |
| `punchIntervalSeconds` | `10` | 打卡间隔（秒） |
| `heartbeatIntervalSeconds` | `60` | 心跳间隔（秒） |
| `matchScore` | `92` | 指纹/刷卡匹配分数，仅审计留痕 |
| `clientIdPrefix` | `sim-esp32` | clientId 前缀，实际值为 `<前缀>-<sn>-<随机6位hex>`，避免多开互顶 |

优先级：**命令行 > 环境变量 > config.json**。
找不到 `config.json` 时会退回 `config.example.json` 并告警（此时 `deviceSecret` 为空会直接报错退出）。

输出示例：

```
◆ TeamPulse 设备 MQTT 模拟器（真实链路：broker → 上行 → 回执）
  配置文件  = /path/to/config.json
  Broker    = mqtt://192.168.1.100:1883
  clientId  = sim-esp32-TP-ESP32-DEMO-9f3a1c
  设备 SN   = TP-ESP32-DEMO
  设备密钥  = 6f1c...b8d2
  工号池    = E1000, E1001
  打卡间隔  = 5 秒
  心跳间隔  = 60 秒

✔ 已连接 broker mqtt://192.168.1.100:1883
✔ 已订阅回执主题 attendance/ack（QoS1）
→ 心跳已发布 attendance/heartbeat
→ 打卡已发布 attendance/checkin employeeNo=E1000 punchTime=2025-06-01 09:03:12 nonce=a1b2c3d4e5f6a7b8
← 回执 code=0 打卡成功 employeeNo=E1000 punchType=CHECK_IN status=LATE lateMinutes=3 recordId=7
  —— 统计：发布 1 / 成功 1 / 失败 0 / 暂存 0 ——
```

`Ctrl+C` 会先断开连接再退出，退出码 0。

### 它的行为刻意对齐真机固件

- 连上 broker 立刻发一次心跳 + 一次打卡，然后按间隔循环。
- 断线期间打卡进入**内存离线队列**（容量 20，满了丢最旧），重连后按顺序补发；
  补发时**重新生成 nonce 与 sign、保留原 punchTime**（改时间就是错的考勤）。
- 回执按 `deviceSn` 过滤（`attendance/ack` 是全局主题，其他设备的回执也会收到），
  再用 `messageId` 里的 nonce 认领在途报文。
- 5 秒等不到回执就打印超时告警，**不自动重发**（重发会换 nonce，可能变成重复打卡，与固件的取舍一致）。

与真机唯一的不同：队列在内存里，进程退出即丢；真机是 RTC 内存，重启还在（断电才丢）。

---

## 3. 签名到底怎么算（对不上时自己验证）

固件、两个模拟器、后端四方**必须是同一套算法**：

```
打卡：HMAC-SHA256(deviceSecret, UTF-8("deviceSn|employeeNo|punchTime|nonce")) → 小写 hex
心跳：HMAC-SHA256(deviceSecret, UTF-8("deviceSn|nonce"))                    → 小写 hex
```

一行命令独立验证（把输出和报文里的 `sign` 对比）：

```bash
node -e "const c=require('node:crypto');console.log(c.createHmac('sha256','<你的secret>').update('TP-ESP32-DEMO|E1000|2025-06-01 09:03:12|a1b2c3d4e5f6a7b8','utf8').digest('hex'))"
```

调试时想看完整待签名串：

```bash
DEBUG=1 node mqtt-simulator.mjs --interval 60
```

---

## 4. 常见问题

| 现象 | 原因与处理 |
|---|---|
| `60001 设备未注册` | `deviceSn` 与 `iot_device` 表不一致（注意大小写/空格）；或后端没跑初始化（`TPP_BOOTSTRAP_ENABLED=true` 且首次启动时设备表为空） |
| `60003 签名校验失败` | `deviceSecret` 填错/多了空格；或你自己改过报文却忘了同步改签名；按第 3 节独立验证 |
| `60004 重复报文` | 同一个 nonce 被用了两次（10 分钟去重窗口）。两个脚本每次都会生成新 nonce，除非你手动重放 |
| `60006 时间戳超范围` | 模拟机与服务器时区/时间不一致。开发机不在东八区时加 `ATTENDANCE_TZ=Asia/Shanghai`；服务器时区见 `docker-compose.yml` 的 `TZ=Asia/Shanghai` |
| `61001 工号不存在` | 工号没在 `user.employee_no` 里；`--loop` 模式的 `E1001~E1003` 需要自己先创建用户，否则属正常。用 `E1000`（初始化管理员）可看到成功回执 |
| `61003 该员工该时刻已有打卡记录` | 同一员工同一秒重复上报。把 `--interval` 调大即可 |
| HTTP 版连不上 | 后端没启动、`BASE_URL` 写错、端口 8080 被防火墙拦。先 `curl http://localhost:8080/actuator/health` |
| MQTT 版 `ECONNREFUSED` | broker 地址/端口错、mosquitto 未启动、云服务器安全组没放行 1883 |
| MQTT 版 `Not authorized` | `username`/`password` 与 broker 的密码文件不一致 |
| `brokerUrl 必须以 mqtt:// 开头` | 把后端配置里的 `tcp://` 直接抄过来了；Paho 用 `tcp://`，Node 的 `mqtt` 包用 `mqtt://` |
| Node 版本报错（`fetch is not defined` / ESM 语法错） | 需要 **Node.js 18+**（`node -v` 确认） |

---

## 5. 无网络也能自测签名？

可以。HTTP 版会打印完整报文；把 `sign` 用第 3 节的命令重算一次，一致就说明设备侧实现正确，
剩下不一致只可能是「后端存的 secret 不是你以为的那一个」。
