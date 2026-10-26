# ESP32 打卡机固件说明

本目录是 TeamPulse 物联网考勤的**设备端固件**，目标板为 **ESP32 DevKit v1**（ESP32-WROOM-32）。

```
device/esp32/
├── attendance_node/
│   └── attendance_node.ino     # Arduino IDE / PlatformIO 都能直接打开的固件
└── README.md                   # 本文档
```

固件只依赖三个东西：ESP32 内核自带的 WiFi / SNTP / mbedtls，加上一个 `PubSubClient`。
**不需要 ArduinoJson**（JSON 全部手工拼接，减少库依赖与 RAM 占用）。

---

## 1. 它和谁对接

接口契约**只有两个来源**，改固件前必须先看它们：

| 后端文件 | 作用 |
|---|---|
| `backend/src/main/java/com/teampulse/module/attendance/dto/DeviceMessages.java` | 上行/下行报文字段定义 |
| `backend/src/main/java/com/teampulse/module/attendance/service/DeviceSignatureVerifier.java` | 待签名串与 HMAC 算法 |

| 方向 | 主题 | 说明 |
|---|---|---|
| 上行 | `attendance/checkin` | 打卡报文，QoS1 |
| 上行 | `attendance/heartbeat` | 心跳，每 60 秒一次 |
| 下行 | `attendance/ack` | 服务端回执，设备订阅 |

待签名串（**字段顺序与分隔符不可变**）：

```
打卡：deviceSn|employeeNo|punchTime|nonce
心跳：deviceSn|nonce
```

签名 = `HMAC-SHA256(deviceSecret, UTF-8(待签名串))`，输出**小写十六进制**（64 字符）。

实际报文（设备真正发出的样子）：

```json
{"deviceSn":"TP-ESP32-DEMO","employeeNo":"E1000","punchTime":"2025-06-01 09:03:12","punchType":"AUTO","nonce":"3f2a9c1d8b7e4f60","sign":"<64 位小写 hex>","firmwareVersion":"1.0.0","matchScore":92}
```

> 这条报文实测 **245 字节**，而 `PubSubClient` 默认缓冲只有 **256 字节**；
> 下行的错误回执带中文详情时还会更长。固件里已调用 `setBufferSize(768)`，
> 如果你自己加了字段（比如 `messageId`），请同步确认没有超缓冲。

---

## 2. 硬件清单

| 物料 | 规格 | 数量 | 备注 |
|---|---|---|---|
| 主控板 | ESP32 DevKit v1（WROOM-32，4MB Flash，USB 串口芯片 CH340/CP2102） | 1 | 必须支持 **2.4GHz** WiFi |
| 读卡/识别模块 | 任意输出 `OK,<工号>,<分数>\r\n` 的模块；或用 AS608/R307 指纹模块（需改一处解析） | 1 | UART TTL 电平 3.3V |
| 蜂鸣器 | 有源或无源均可（无源由 `tone()` 的频率驱动） | 1 | 串 100Ω 限流电阻 |
| LED | 板载蓝色 LED（GPIO2）即可，无需外接 | 0 | 若用外置 LED 记得串 220Ω |
| 杜邦线 | 母-母若干 | ~6 | |
| 电源 | 5V/1A USB 电源 | 1 | 指纹模块峰值电流较大，建议独立 5V 供电并与 ESP32 **共地** |

---

## 3. 接线表

| ESP32 引脚 | 接到 | 方向 | 说明 |
|---|---|---|---|
| `GPIO16`（UART2 RX） | 读卡器 **TX** | ← | 读卡器输出数据 |
| `GPIO17`（UART2 TX） | 读卡器 **RX** | → | 固件目前只收不发，但仍建议接上，方便以后下发指令 |
| `GND` | 读卡器 **GND** | — | **必须共地**，否则串口必乱码 |
| `3V3` 或 `VIN(5V)` | 读卡器 **VCC** | — | 看模块电压；AS608 用 3.3V；光学/电容指纹模块多为 3.3V |
| `GPIO2` | 板载 LED | → | DevKit v1 上已焊好，**高电平点亮** |
| `GPIO4` | 蜂鸣器 **+** | → | 另一端接 `GND`；建议串 100Ω |
| `GND` | 蜂鸣器 **−** | — | |

接线示意：

```
         ┌──────────────┐                    ┌──────────────┐
         │  ESP32 DevKit│                    │   读卡器      │
         │              │   GPIO16 (RX2) ────┤ TX           │
         │              │   GPIO17 (TX2) ────┤ RX           │
         │              │   GND ─────────────┤ GND          │
         │              │   3V3/5V ──────────┤ VCC          │
         │              │                    └──────────────┘
         │              │   GPIO4 ──[100Ω]──► 蜂鸣器 ──► GND
         │              │   GPIO2 ──► 板载 LED
         └──────────────┘
```

> ⚠️ 用的是 **ESP32-WROVER** 模块时，GPIO16/17 被 PSRAM 占用，请改 `PIN_READER_RX/TX`
> 到 GPIO25/26 等空闲引脚。WROOM-32（DevKit v1 常见）没有这个问题。

---

## 4. 依赖库版本

| 依赖 | 版本 | 安装方式 | 说明 |
|---|---|---|---|
| esp32（Arduino 内核） | **2.0.17**（推荐）；3.x 也可编译 | Arduino IDE → 开发板管理器 搜 `esp32` by Espressif Systems | 提供 `WiFi.h` / `time.h` / `mbedtls` / `esp_random()` |
| PubSubClient | **2.8.0** | 库管理器 搜 `PubSubClient` by Nick O'Leary | MQTT 客户端。**必须 ≥ 2.7**，因为用到了 `setBufferSize()` |
| ArduinoJson | — | **不需要** | JSON 手工拼/手工解析 |

PlatformIO 用户可用 `platformio.ini`：

```ini
[env:esp32dev]
platform = espressif32
board = esp32dev
framework = arduino
monitor_speed = 115200
lib_deps = knolleary/PubSubClient@^2.8
```

---

## 5. 配置（只改固件顶部 `#define` 区）

| 宏 | 默认值 | 必改 | 说明 |
|---|---|---|---|
| `WIFI_SSID` / `WIFI_PASSWORD` | `YOUR_WIFI_SSID` | ✅ | 只支持 2.4GHz |
| `MQTT_SERVER` | `192.168.1.100` | ✅ | 后端 / mosquitto 所在服务器 IP |
| `MQTT_PORT` | `1883` | | 与 `docker-compose.yml` 里暴露的端口一致 |
| `MQTT_USERNAME` / `MQTT_PASSWORD` | 空 | 视部署 | mosquitto 开了密码认证就填 `.env` 里的 `MQTT_USERNAME/MQTT_PASSWORD`；留空表示匿名 |
| `DEVICE_SN` | `TP-ESP32-DEMO` | ✅ | 必须与 `iot_device.device_sn` 一行一致 |
| `DEVICE_SECRET` | 占位串 | ✅ | **后端首次启动日志里打印的 64 位十六进制**，必须与 `iot_device.secret` 完全一致 |
| `FIRMWARE_VERSION` | `1.0.0` | | 会随报文上报，便于区分批次 |
| `PIN_LED` / `PIN_BUZZER` | `2` / `4` | | |
| `PIN_READER_RX/TX` | `16` / `17` | | 只用文本输出模块时这两个就是唯一串口接线 |
| `READER_BAUD` | `9600` | | 与读卡器一致（AS608 默认 57600，换模块时记得改） |
| `NTP_SERVER1..3` | 阿里云 / 国内池 / 全球池 | | 内网无外网时改成内网 NTP 服务器 |
| `TZ_INFO` | `CST-8` | | 东八区。`GMT_OFFSET_SEC` 为 `8*3600` |
| `QUEUE_CAPACITY` | `20` | | 离线队列容量，受 RTC 内存限制（20 条约 800 字节） |
| `HEARTBEAT_INTERVAL_MS` | `60000` | | 60 秒 |
| `ACK_TIMEOUT_MS` | `5000` | | 等回执超时，超时按可重试处理 |

**从哪里拿 `DEVICE_SECRET`**：后端首次启动（用户表或设备表为空时）会在日志里打印：

```
============================================================
 已创建演示打卡设备（仅用于联调，生产请删除）
   设备序列号: TP-ESP32-DEMO
   设备密钥  : 6f1c...（64 位十六进制）
 把这两项填进 device/simulator/config.json 即可模拟上报
============================================================
```

生产环境请用管理端接口 `POST /api/v1/devices` 新建设备（`deviceSn` 自定义），响应里会带密钥。

---

## 6. 烧录步骤

1. **装开发板支持**：Arduino IDE → 文件 → 首选项 → 附加开发板管理器网址（可选，2.x 一般自带），
   然后在「开发板管理器」搜索 `esp32` 安装 *esp32 by Espressif Systems*（2.0.17 最稳）。
2. **装库**：工具 → 管理库 → 搜索 `PubSubClient` → 安装 2.8.0。
3. **改配置**：打开 `attendance_node/attendance_node.ino`，把第 5 节的 5 个必改项填好。
4. **选开发板**：工具 →
   - 开发板：`ESP32 Dev Module`
   - Upload Speed：`921600`（不稳定就降到 `115200`）
   - Flash Size：`4MB (32Mb)`
   - Partition Scheme：`Default 4MB with spiffs`
   - Port：插上板子后出现的新串口（CH340 需装驱动）
5. **上传**：点「上传」。若卡在 `Connecting...`，按住板子上的 `BOOT` 键再点上传，
   出现 `Writing at 0x...` 后松手。
6. **看日志**：工具 → 串口监视器，波特率 **115200**，行尾选「换行」。

正常启动日志（按时间顺序）：

```
============================================================
 TeamPulse 打卡机固件 v1.0.0  deviceSn=TP-ESP32-DEMO
 读卡器 UART2: RX=GPIO16 TX=GPIO17 @9600 8N1
 LED=GPIO2  蜂鸣器=GPIO4
============================================================
[队列] 冷启动（RTC 内存已被清空），离线队列为空
[WiFi] 正在连接 MyWiFi ...
[MQTT] 客户端 ID=esp32-TP-ESP32-DEMO-1a2b3c，报文缓冲 768 字节
[WiFi] 已连接 SSID=MyWiFi IP=192.168.1.50 RSSI=-58 dBm
[NTP] 正在校时（ntp.aliyun.com / cn.pool.ntp.org）...
[MQTT] 正在连接 192.168.1.100:1883 ...
[NTP] 校时完成，设备本地时间 = 2025-06-01 09:00:01（东八区）
[MQTT] 已连接 clientId=esp32-TP-ESP32-DEMO-1a2b3c
[MQTT] 已订阅回执主题 attendance/ack（QoS1）
[上行] 心跳 ip=192.168.1.50 ts=1748768401
```

刷一次卡后的日志：

```
[读卡器] OK employeeNo=E1000 score=92
[队列] 入队 employeeNo=E1000 punchTime=2025-06-01 09:03:12 第1次尝试 当前 1/20
[上行] 打卡 employeeNo=E1000 punchTime=2025-06-01 09:03:12 nonce=3f2a9c1d8b7e4f60 第1次尝试
[下行] topic=attendance/ack payload={"deviceSn":"TP-ESP32-DEMO",...,"code":0,"message":"打卡成功","punchType":"CHECK_IN","status":"LATE","lateMinutes":3}
[回执] 打卡成功 recordId=1 punchType=CHECK_IN status=LATE lateMinutes=3
```

---

## 7. 设备行为说明（运维需要知道的）

### 7.1 指示灯 / 蜂鸣器含义

| 场景 | LED（GPIO2） | 蜂鸣器（GPIO4） | 含义 |
|---|---|---|---|
| 打卡成功（收到 `code==0`） | 常亮 300ms | 2000Hz 短鸣 80ms | 已入库 |
| 失败（`6xxxx` / 识别失败 / 队列拒收） | 快闪 3 次 | 800Hz 长鸣 600ms | 需要处理，**不会重试** |
| 已离线暂存 | 常亮 300ms | 1500Hz 极短鸣 60ms | 还没拿到服务端结果，联网后补发 |

全部是**非阻塞状态机**（没有 `delay()` 卡住主循环），刷卡响应不受影响。

### 7.2 离线队列（RTC 内存，容量 20）

- 位置：`RTC_DATA_ATTR QueuedPunch g_queue[20]`，约 800 字节 RTC 慢速内存。
- **软复位 / 看门狗复位 / 重新烧录后仍在**；**彻底断电会丢**（RTC 内存没有电池）。
- 行为：每条打卡先入队 → 发布队头 → 收到 `code==0` 才出队。
  因此「发出去但还没等到回执就重启」不会丢记录（代价是可能重复上报一次，
  由服务端 `messageId`/`nonce` 与「同员工同秒唯一」约束兜底）。
- 队列满了：覆盖最旧的一条并打印告警；但若队头正是等回执的在途记录，则**拒收**新打卡
  （避免错删在途记录）。
- 重发时**重新生成 nonce 与 sign，punchTime 保持原值**（契约要求，也保证考勤时间不漂）。
- 同一条最多重试 5 次（`QUEUE_MAX_ATTEMPTS`），超过就丢弃并打印日志。

### 7.3 时间

- 时间来源：SNTP（`configTime`）+ 显式 `setenv("TZ","CST-8")`，双保险保证 `localtime_r()` 按东八区格式化。
- **NTP 没同步前拒绝上报**：服务端允许 ±300 秒偏差，用假时间上报必然被判 `60006`，
  所以宁可不收，也不把脏时间写进考勤。
- ESP32 没有 RTC 电池，断电重启后需要重新校时（约 2~5 秒）。

### 7.4 网络与重连

- WiFi 断开：每 10 秒重连一次；恢复后立刻重连 MQTT。
- MQTT 断开：每 5 秒重连一次；重连成功立刻发一次心跳并开始补发队列。
- `clientId` = `esp32-<deviceSn>-<MAC 低 24 位>`，同型号多台不会互相顶下线。

---

## 8. 常见问题排查

### 8.1 连不上 WiFi

| 检查项 | 怎么查 |
|---|---|
| 频段 | ESP32 **只支持 2.4GHz**。5GHz 网络（SSID 常带 `-5G`）一定连不上 |
| SSID/密码 | 大小写、特殊字符、中文 SSID 注意转义；密码里不要有 `\` |
| 路由器限制 | MAC 白名单、AP 隔离、连接数上限；企业级 WPA2-Enterprise(EAP) **不支持** |
| 信号 | 串口日志的 `RSSI` 低于 -80dBm 建议加 AP 或改位置 |
| 验证手段 | 用手机开 2.4GHz 热点，改 SSID/密码试一次，能通就说明是路由器侧问题 |
| 一直 `status=6` | `WL_DISCONNECTED`：检查密码；`status=1` 等 1 秒再试属正常 |

### 8.2 时间不对（日志时间戳怪 / 服务端回 60006）

| 现象 | 原因与处理 |
|---|---|
| 一直停在 `[NTP] 正在校时` | UDP **123 端口**被防火墙/公司网络拦截；改 `NTP_SERVER1` 为内网 NTP，或先确认设备能上外网 |
| 时间比本地早 8 小时 | `TZ_INFO` 或 `GMT_OFFSET_SEC` 被改错了；恢复 `CST-8` + `8*3600` |
| 重启后回到 1970 | ESP32 无 RTC 电池，属正常；等 NTP 同步即可（固件在校时完成前拒绝打卡） |
| 服务端 `60006 设备时间戳超出允许范围` | 说明偏差超 300 秒。检查设备与服务器是否同一个时区/是否都做过校时；服务器时区见 `docker-compose.yml` 的 `TZ=Asia/Shanghai` |
| 想手动核对 | 串口日志 `[NTP] 校时完成，设备本地时间 = ...` 与手机时间对比，误差应 < 2 秒 |

### 8.3 验签失败（回执 `60003`）

按顺序排查，**99% 是密钥或待签名串**：

1. **密钥是否一致**：`DEVICE_SECRET` 必须与数据库 `iot_device.secret` **完全一致**
   （64 位小写十六进制，前后不能有空格、不能带引号）。
   可用下面的命令独立验证一次签名，把结果与报文里的 `sign` 对比：

   ```bash
   node -e "const c=require('node:crypto');console.log(c.createHmac('sha256','<你的secret>').update('TP-ESP32-DEMO|E1000|2025-06-01 09:03:12|3f2a9c1d8b7e4f60','utf8').digest('hex'))"
   ```

2. **待签名串是否一致**：`deviceSn|employeeNo|punchTime|nonce`，四段、三个竖线、**顺序不能变**，
   `punchTime` 必须是 `yyyy-MM-dd HH:mm:ss`（19 位，中间一个空格），
   且**必须与报文里 `punchTime` 字段逐字相同**（后端拿报文里的字符串参与签名）。
3. **`sign` 是否小写**：后端用常量时间比较时会把提供值转小写，但建议直接输出小写，别赌。
4. **是否被中间层改过报文**：比如自己加了 `messageId` 却忘了它不参与签名（这是允许的），
   或者把 `nonce` 截断了（`NONCE_BUF_LEN` 不够）。
5. 回 `60002` 说明设备被禁用（`iot_device.status='DISABLED'`），不是签名问题。

### 8.4 设备未注册（回执 `60001`）

- `DEVICE_SN` 与库里没有任何一行 `device_sn` 匹配（注意大小写与前后空格）。
- 后端没跑初始化：确认 `TPP_BOOTSTRAP_ENABLED=true` 且首次启动时设备表为空，
  日志里会出现「已创建演示打卡设备」。
- 想新增设备：`POST /api/v1/devices`（管理端需要 JWT），把返回的密钥填进固件。
- 也可以用 `device/simulator/http-simulator.mjs` 先验证「同一套 SN/密钥」在后端是通的。

### 8.5 读卡器没反应 / 读到乱码

- TX/RX 是否交叉（读卡器 TX → ESP32 GPIO16）。
- 波特率：文本模块多为 **9600**，AS608 默认 **57600**，改 `READER_BAUD`。
- **共地**：没接 GND 时收到的多是乱码，日志会出现「无法识别的一行」。
- 先用 USB-TTL 转接板 + 串口助手单独验证读卡器输出格式是不是 `OK,E1000,92`。
- 日志出现「半行超时丢弃」= 收到半行就断了，多为波特率不匹配或接线松动。

### 8.6 MQTT 连不上（`state` 非 0）

| `state` | 含义 | 处理 |
|---|---|---|
| `-4` | 连接超时 | IP/端口不对，或服务器防火墙/云安全组没放行 1883 |
| `-3` | 连接丢失 | 网络抖动，固件会自动重连 |
| `-2` | 连接失败 | broker 未启动，或 `MQTT_USERNAME/PASSWORD` 错 |
| `-1` | 断开 | 正常断线状态 |
| `1/2/3/4` | 协议版本/cid/认证被拒 | 多半是 clientId 重复或账号密码错 |

用 `mosquitto_sub` 快速验证 broker 是否可达：

```bash
mosquitto_sub -h <服务器IP> -p 1883 -u teampulse -P '<密码>' -t 'attendance/#' -v
```

### 8.7 打卡成功但网页看不到

- 回执 `code==0` 只代表**已入库**。前端看板有轮询延迟（约 5 秒）。
- 若回执 `code==0` 但 `status=INVALID`，多为「工号未注册」或「未配置考勤规则」
  （服务端仍会留痕，只是不参与迟到/早退判定）。

### 8.8 断电后离线记录没了

RTC 内存靠 VBAT/RTC 域供电，**彻底断电必然丢**。若现场必须做到掉电不丢，
把队列换成 `Preferences`（NVS）或 SPIFFS/LittleFS 落盘——但要注意 Flash 擦写寿命
（每次打卡写一次，一天几十次完全没问题）；本固件选 RTC 是为了「重启不丢、写入零损耗」的平衡。

---

## 9. 回执错误码速查（与后端 `ErrorCode` 一致）

| code | 含义 | 设备侧动作 |
|---|---|---|
| `0` | 成功 | 成功提示，出队 |
| `60001` | 设备未注册 | **不重试**，错误提示；检查 `DEVICE_SN` |
| `60002` | 设备已被禁用 | **不重试**，错误提示；找管理员启用 |
| `60003` | 报文签名校验失败 | **不重试**，错误提示；按 8.3 排查 |
| `60004` | 重复报文（重放保护） | **不重试**；多为同一 nonce 重发 |
| `60005` | 报文格式不合法 | **不重试**；字段缺失/`punchTime` 格式错 |
| `60006` | 设备时间戳超范围 | **不重试**；校时后再用（按 8.2 排查） |
| `61001` | 工号不存在或已离职 | **不重试**；但记录已留痕（`status=INVALID`） |
| `61002` | 未配置考勤规则 | 记录已留痕，需管理员配置规则 |
| `61003` | 该员工该时刻已有打卡记录 | 重复刷卡；设备按永久错误处理 |
| `5xxxx` | 服务端临时错误 | **可重试**，记录回队列等下次补发 |

> 固件判定规则：`code` 在 `60000~69999` 区间视为设备侧永久错误，直接丢弃不重试；
> 其余非 0 码一律按可重试处理。

---

## 10. 现场部署检查清单

- [ ] `DEVICE_SN` / `DEVICE_SECRET` 与后端 `iot_device` 表一致
- [ ] 固件串口日志能看到「校时完成」+「MQTT 已连接」+「已订阅回执主题」
- [ ] 刷一次卡，串口出现 `[回执] 打卡成功`
- [ ] 管理端「设备」页显示该设备为 **ONLINE**（心跳生效）
- [ ] 拔掉网线/断 WiFi 刷一次卡 → 日志「已暂存」→ 恢复网络后自动补发成功
- [ ] 板子装进外壳前，确认 GPIO4 蜂鸣器音量与 GPIO2 指示灯可见
- [ ] 多台设备部署时，每台的 `DEVICE_SN` / `DEVICE_SECRET` 都不同
