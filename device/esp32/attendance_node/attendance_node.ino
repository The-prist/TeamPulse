/*
 * =============================================================================
 *  TeamPulse 物联网考勤 —— ESP32 打卡机固件
 *  目标板：ESP32 DevKit v1（ESP32-WROOM-32 / 4MB Flash）
 *  固件版本：1.0.0
 * =============================================================================
 *
 *  一、这个固件干什么
 *  ---------------------------------------------------------------------------
 *  读卡器通过 UART2 输出「匹配成功 / 失败」的文本行，ESP32 拿到工号后：
 *    1. 用 NTP 校准过的本地时间（东八区）生成 punchTime
 *    2. 生成一次性 nonce，对 deviceSn|employeeNo|punchTime|nonce 做 HMAC-SHA256
 *    3. 手工拼 JSON，发布到 MQTT 主题 attendance/checkin
 *    4. 订阅 attendance/ack 等回执，按回执码播报成功/失败
 *    5. 离线时把记录存进 RTC 内存环形队列（20 条），联网后按顺序补发
 *  另外每 60 秒发一次 attendance/heartbeat 心跳，让服务端知道设备在线。
 *
 *  二、接线（ESP32 DevKit v1 ↔ 读卡器 ↔ 蜂鸣器 / LED）
 *  ---------------------------------------------------------------------------
 *    ESP32            读卡器 / 外设        说明
 *    ---------------  --------------------  ---------------------------------
 *    GPIO16 (RX2)     读卡器 TX            读卡器输出 OK,E1000,92\r\n
 *    GPIO17 (TX2)     读卡器 RX            ESP32 侧基本不发指令，但必须接
 *    GND              GND                  共地是必须的，否则串口必乱码
 *    3V3 或 5V(VIN)   读卡器 VCC           按模块电压选择，AS608 用 3.3V
 *    GPIO2            板载蓝色 LED         DevKit v1 板载 LED，高电平点亮
 *    GPIO4            有源蜂鸣器 + 端       串 100Ω 限流；另一端接 GND
 *  ⚠️ 蜂鸣器若为无源型，tone() 仍能驱动（频率由 tone 决定）；
 *     有源型则 tone 的频率参数无意义，响度不变，也照常可用。
 *  ⚠️ 读卡器与 ESP32 必须共地；用外部电源给读卡器供电时，GND 一定要连在一起。
 *
 *  三、烧录步骤
 *  ---------------------------------------------------------------------------
 *    1. Arduino IDE → 开发板管理器安装 "esp32 by Espressif Systems"（2.0.x 或 3.x 均可）
 *    2. 库管理器安装 "PubSubClient" by Nick O'Leary（2.8.0）
 *       （本固件不依赖 ArduinoJson，JSON 全部手工拼接）
 *    3. 打开本文件 → 工具 → 开发板选 "ESP32 Dev Module"
 *       Flash Size 4MB / Partition Scheme Default / Upload Speed 921600
 *    4. 改下面「四、必须修改的配置」四项 → 点上传
 *    5. 打开串口监视器 115200，看到 [NTP] 校时完成 + [MQTT] 已连接 即成功
 *
 *  四、必须修改的配置（都在下面的 #define 区）
 *  ---------------------------------------------------------------------------
 *    WIFI_SSID / WIFI_PASSWORD   —— 现场 2.4GHz WiFi（ESP32 不支持 5GHz！）
 *    MQTT_SERVER                 —— 后端所在服务器的 IP（broker 端口 1883）
 *    DEVICE_SN / DEVICE_SECRET   —— 与后端 iot_device 表一致；
 *                                   后端首次启动会在日志里打印演示设备的密钥
 *    MQTT_USERNAME / MQTT_PASSWORD —— 若 mosquitto 开了密码认证就必须填
 *
 *  五、几个容易踩的坑（已在本文件里处理）
 *  ---------------------------------------------------------------------------
 *    · PubSubClient 默认报文缓冲只有 256 字节。本固件的打卡报文实测 245 字节，
 *      已经贴着上限；而下行错误回执带中文详情时会超过 256，超限时 PubSubClient
 *      会整条丢弃（连回调都不进）。所以 setup() 里 setBufferSize(768)。
 *    · millis() 49.7 天回绕：所有定时比较统一写成 (uint32_t)(now - last) >= 间隔，
 *      不要写 now > last + 间隔。
 *    · ESP32 的 String 与 const char*：publish() 只接受 const char*，
 *      写成 mqtt.publish(topic, (String("...") + x).c_str()) 会拿到临时对象的
 *      悬垂指针。本固件全程用 char 缓冲区 + snprintf，不产生 String 临时对象。
 *    · 时间不对 → 服务端 60006 直接拒收（允许 ±300 秒）。所以 NTP 没同步前
 *      本固件拒绝上报，而不是拿 1970 年或 ESP32 启动计时去凑。
 *
 *  六、与后端的契约（改动前务必同步 backend）
 *  ---------------------------------------------------------------------------
 *    打卡主题 attendance/checkin
 *      待签名串 deviceSn|employeeNo|punchTime|nonce   （顺序与分隔符不可变）
 *      签名     HMAC-SHA256(deviceSecret, UTF-8(待签名串)) → 小写十六进制
 *    心跳主题 attendance/heartbeat
 *      待签名串 deviceSn|nonce
 *    回执主题 attendance/ack：code==0 成功；6xxxx 设备侧永久错误（不重试）；
 *      其余（5xxxx 等）可重试，进入离线队列。
 * =============================================================================
 */

#include <WiFi.h>
#include <PubSubClient.h>
#include <time.h>
#include <mbedtls/md.h>
#include <esp_system.h>   // esp_random()：硬件随机数，用于 nonce

// =============================================================================
// 一、可配置项（现场部署只改这一段）
// =============================================================================

// ---- WiFi（ESP32 只支持 2.4GHz）----
#define WIFI_SSID              "YOUR_WIFI_SSID"
#define WIFI_PASSWORD          "YOUR_WIFI_PASSWORD"

// ---- MQTT broker ----
#define MQTT_SERVER            "192.168.1.100"      // 后端 / mosquitto 所在服务器 IP
#define MQTT_PORT              1883
// mosquitto 未开密码认证时留空字符串即可；开了就必须填（对应 .env 的 MQTT_USERNAME）
#define MQTT_USERNAME          ""
#define MQTT_PASSWORD          ""

// ---- 设备身份（必须与后端 iot_device 表一致）----
#define DEVICE_SN              "TP-ESP32-DEMO"
// ⚠️ 必须替换成后端启动日志里打印的 64 位十六进制密钥，否则一律 60003 验签失败
#define DEVICE_SECRET          "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
#define FIRMWARE_VERSION       "1.0.0"

// ---- 引脚 ----
#define PIN_LED                2      // 板载 LED（GPIO2）
#define PIN_BUZZER             4      // 蜂鸣器
#define PIN_READER_RX          16     // UART2 RX ← 读卡器 TX
#define PIN_READER_TX          17     // UART2 TX → 读卡器 RX
#define READER_BAUD            9600

// ---- NTP / 时区 ----
#define NTP_SERVER1            "ntp.aliyun.com"
#define NTP_SERVER2            "cn.pool.ntp.org"
#define NTP_SERVER3            "pool.ntp.org"
#define GMT_OFFSET_SEC         (8 * 3600)   // 东八区
#define DAYLIGHT_OFFSET_SEC    0
#define TZ_INFO                "CST-8"      // POSIX 时区串：中国标准时间 = UTC+8
// 小于这个纪元值视为「尚未校时」（2023-11-15 左右），用来识别 1970/2000 年的假时间
#define NTP_SYNC_MIN_EPOCH     1700000000L

// ---- MQTT 主题（与后端 application.yml 默认值一致）----
#define TOPIC_CHECKIN          "attendance/checkin"
#define TOPIC_HEARTBEAT        "attendance/heartbeat"
#define TOPIC_ACK              "attendance/ack"

// ---- 定时参数（单位毫秒，全部用差值与 millis() 比较，天然抗回绕）----
#define HEARTBEAT_INTERVAL_MS  60000UL   // 心跳 60 秒
#define MQTT_RECONNECT_INTERVAL_MS 5000UL
#define WIFI_RECONNECT_INTERVAL_MS 10000UL
#define NTP_RETRY_INTERVAL_MS  30000UL
#define ACK_TIMEOUT_MS         5000UL    // 等回执超时 → 按可重试处理
#define QUEUE_SEND_GAP_MS      1000UL    // 两条补发之间至少间隔，避免冲刷 broker
#define UART_LINE_TIMEOUT_MS   300UL     // 半行超时，避免断线后两行粘连

// ---- 缓冲区与容量 ----
#define JSON_BUF_LEN           512       // 组装报文用的缓冲区
#define MQTT_BUFFER_SIZE       768       // 见文件头「五」；默认 256 不够
#define SIGN_BUF_LEN           65        // 64 位 hex + '\0'
#define NONCE_BUF_LEN          17        // 16 位 hex + '\0'
#define EMPLOYEE_NO_MAX        16        // 工号最大长度
#define EMPLOYEE_BUF_LEN       17
#define PUNCH_TIME_BUF_LEN     20        // "yyyy-MM-dd HH:mm:ss" 共 19 + '\0'
#define UART_LINE_BUF_LEN      64
#define QUEUE_CAPACITY         20        // 离线队列容量
#define QUEUE_MAX_ATTEMPTS     5         // 同一记录最多重试次数，超过就丢弃并告警
#define QUEUE_MAGIC            0x54505131UL   // 'TPQ1'：校验 RTC 内存内容是否有效

#define PUNCH_TYPE_AUTO        "AUTO"    // 交给服务端按当天首末次判签到/签退

// =============================================================================
// 二、全局状态
// =============================================================================

// 离线队列放在 RTC 慢速内存里：软复位 / 看门狗复位 / 重启后仍在，
// 只有彻底断电才会丢（这也是选择 RTC 而不是普通 RAM 的全部理由）。
struct QueuedPunch {
  char employeeNo[EMPLOYEE_BUF_LEN];
  char punchTime[PUNCH_TIME_BUF_LEN];
  int16_t matchScore;
  uint8_t attempts;   // 已重试次数，防止一条永远失败的数据无限循环
};

RTC_DATA_ATTR QueuedPunch g_queue[QUEUE_CAPACITY];
RTC_DATA_ATTR uint8_t g_queueHead  = 0;
RTC_DATA_ATTR uint8_t g_queueCount = 0;
RTC_DATA_ATTR uint32_t g_queueMagic = 0;

// 在途报文（已 publish、等 ack）。只允许一条在途：MQTT 回执里没有 nonce 字段，
// 靠 messageId 里的 nonce 做归属判断，一条一条来最不容易串。
struct PendingPunch {
  char employeeNo[EMPLOYEE_BUF_LEN];
  char punchTime[PUNCH_TIME_BUF_LEN];
  char nonce[NONCE_BUF_LEN];
  int16_t matchScore;
  uint8_t attempts;
  uint32_t sentAt;
  bool active;
};

static PendingPunch s_pending;

static WiFiClient   s_wifiClient;
static PubSubClient s_mqtt(s_wifiClient);
static char         s_mqttClientId[48];

static bool     s_wifiOnline        = false;
static bool     s_timeReadyLogged   = false;
static bool     s_heartbeatDue      = true;
static uint32_t s_lastWifiAttempt   = 0;
static uint32_t s_lastMqttAttempt   = 0;
static uint32_t s_lastNtpAttempt    = 0;
static uint32_t s_lastHeartbeatAt   = 0;
static uint32_t s_lastQueueAttempt  = 0;

// LED / 蜂鸣器状态机（不使用 delay，避免阻塞读卡和收包）
static uint32_t s_ledOnUntil     = 0;
static uint8_t  s_ledBlinkLeft   = 0;
static bool     s_ledBlinkOn     = false;
static uint32_t s_ledBlinkNextAt = 0;
static uint32_t s_buzzerStopAt   = 0;

// 读卡器串口行缓冲
static char     s_uartLine[UART_LINE_BUF_LEN];
static size_t   s_uartLen        = 0;
static uint32_t s_uartLastByteAt = 0;

// =============================================================================
// 三、函数声明（Arduino 会自动生成原型，这里显式写出便于阅读与查错）
// =============================================================================

static void copyStr(char* dst, size_t dstSize, const char* src);
static bool isValidEmployeeNo(const char* value);
static void bytesToHexLower(const uint8_t* data, size_t len, char* outHex);
static bool hmacSha256Hex(const char* key, const char* payload, char* outHex);
static void makeNonce(char* out, size_t outSize);
static bool timeSynced();
static void formatLocalTime(time_t epochSeconds, char* out, size_t outSize);
static const char* jsonFindValue(const char* json, const char* key);
static bool jsonFindString(const char* json, const char* key, char* out, size_t outSize);
static long jsonFindInt(const char* json, const char* key, long fallback);

static void ledWrite(bool on);
static void beepStart(uint16_t freq, uint16_t durationMs);
static void indicateSuccess();
static void indicateError();
static void indicateQueued();
static void serviceIndicators(uint32_t now);

static void queueInit();
static void queueReset(const char* reason);
static bool isHeadInFlight();
static bool queuePush(const char* employeeNo, const char* punchTime, int matchScore, uint8_t attempts);
static void queueDropHead(const char* reason);
static void requeueHeadAfterFailure();
static void clearPending();

static bool buildPunchJson(const char* employeeNo, const char* punchTime, int matchScore,
                           char* outJson, size_t outSize, char* outNonce, size_t outNonceSize);
static void sendHeartbeat();
static void flushQueue(uint32_t now);
static void onMqttMessage(char* topic, byte* payload, unsigned int length);
static void handleCard(const char* employeeNo, int matchScore);

static bool uartReadLine(char* outLine, size_t outSize, uint32_t now);
static bool readEmployeeFromUart(char* outEmployeeNo, size_t outSize, int* outScore);
static void serviceUart();

static void serviceWifi(uint32_t now);
static void serviceTime(uint32_t now);
static void serviceMqtt(uint32_t now);
static void serviceHeartbeat(uint32_t now);
static void servicePendingTimeout(uint32_t now);

// =============================================================================
// 四、通用工具
// =============================================================================

/** 安全的字符串拷贝：总是以 '\0' 结尾，src 为 NULL 时写成空串 */
static void copyStr(char* dst, size_t dstSize, const char* src) {
  if (dst == NULL || dstSize == 0) {
    return;
  }
  if (src == NULL) {
    dst[0] = '\0';
    return;
  }
  size_t len = strlen(src);
  if (len >= dstSize) {
    len = dstSize - 1;
  }
  memcpy(dst, src, len);
  dst[len] = '\0';
}

/**
 * 工号白名单校验。
 *
 * 为什么必须校验：工号是唯一一个来自外部设备（读卡器）的字符串字段，
 * 会被直接拼进 JSON。万一读卡器吐出 `E1000","sign":"x` 这种内容，
 * 手工拼 JSON 就会被注入出第二个字段。这里限制成
 * [A-Za-z0-9_-] 与有限长度，从源头上堵住。
 */
static bool isValidEmployeeNo(const char* value) {
  if (value == NULL) {
    return false;
  }
  size_t len = strlen(value);
  if (len == 0 || len > EMPLOYEE_NO_MAX) {
    return false;
  }
  for (size_t i = 0; i < len; i++) {
    char c = value[i];
    bool allowed = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '_' || c == '-';
    if (!allowed) {
      return false;
    }
  }
  return true;
}

/** 二进制转小写十六进制；outHex 至少需要 len*2+1 字节 */
static void bytesToHexLower(const uint8_t* data, size_t len, char* outHex) {
  static const char kHex[] = "0123456789abcdef";
  for (size_t i = 0; i < len; i++) {
    outHex[i * 2]     = kHex[(data[i] >> 4) & 0x0F];
    outHex[i * 2 + 1] = kHex[data[i] & 0x0F];
  }
  outHex[len * 2] = '\0';
}

/**
 * HMAC-SHA256，输出小写十六进制（64 位字符）。
 *
 * 用 mbedtls 而不是自己实现 HMAC：ESP32 的 Arduino 内核自带 mbedtls，
 * 手搓 HMAC 只有在没有现成库时才值得做。这里 key 是设备密钥（UTF-8 字节），
 * payload 是待签名串（UTF-8 字节），与后端 DeviceSignatureVerifier 完全一致。
 *
 * @param outHex 需要 SIGN_BUF_LEN(65) 字节
 * @return 成功返回 true
 */
static bool hmacSha256Hex(const char* key, const char* payload, char* outHex) {
  if (key == NULL || payload == NULL || outHex == NULL) {
    return false;
  }
  const mbedtls_md_info_t* info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
  if (info == NULL) {
    Serial.println("[签名] mbedtls 未找到 SHA256 实现（请检查内核配置）");
    return false;
  }

  mbedtls_md_context_t ctx;
  mbedtls_md_init(&ctx);
  // 第三个参数 = 1 表示 HMAC 模式，而不是裸 hash
  if (mbedtls_md_setup(&ctx, info, 1) != 0) {
    mbedtls_md_free(&ctx);
    Serial.println("[签名] mbedtls_md_setup 失败");
    return false;
  }

  uint8_t digest[32];
  bool ok = (mbedtls_md_hmac_starts(&ctx, (const unsigned char*) key, strlen(key)) == 0)
         && (mbedtls_md_hmac_update(&ctx, (const unsigned char*) payload, strlen(payload)) == 0)
         && (mbedtls_md_hmac_finish(&ctx, digest) == 0);

  if (ok) {
    bytesToHexLower(digest, sizeof(digest), outHex);
  } else {
    Serial.println("[签名] HMAC 计算失败");
  }

  mbedtls_md_free(&ctx);
  // 把栈上残留的摘要/中间状态清零，减少密钥派生数据在内存里的存活时间
  memset(digest, 0, sizeof(digest));
  return ok;
}

/**
 * 生成一次性 nonce（16 位小写十六进制）。
 *
 * 来源：esp_random() 读的是 ESP32 硬件真随机源，再异或 millis() 与一个自增计数，
 * 保证同一毫秒内连续读卡也不会撞（服务端按 deviceSn+nonce 在 10 分钟内去重，
 * 撞了会被判 60004 重放）。
 */
static void makeNonce(char* out, size_t outSize) {
  static uint32_t counter = 0;
  uint32_t randomPart = esp_random();
  uint32_t mixedPart  = esp_random() ^ (uint32_t) millis() ^ (counter++);
  snprintf(out, outSize, "%08lx%08lx", (unsigned long) randomPart, (unsigned long) mixedPart);
}

/** 是否已完成 NTP 校时（用纪元下限判断，而不是看 tm_year 之类的字段） */
static bool timeSynced() {
  return time(NULL) >= NTP_SYNC_MIN_EPOCH;
}

/** 把纪元秒格式化成后端要求的 yyyy-MM-dd HH:mm:ss（本地时区，已配置为东八区） */
static void formatLocalTime(time_t epochSeconds, char* out, size_t outSize) {
  struct tm timeInfo;
  if (localtime_r(&epochSeconds, &timeInfo) == NULL) {
    copyStr(out, outSize, "1970-01-01 00:00:00");
    return;
  }
  if (strftime(out, outSize, "%Y-%m-%d %H:%M:%S", &timeInfo) == 0) {
    copyStr(out, outSize, "1970-01-01 00:00:00");
  }
}

/** 在 JSON 里找 "key" 后面的值起点（跳过冒号与空白）；找不到返回 NULL */
static const char* jsonFindValue(const char* json, const char* key) {
  if (json == NULL || key == NULL) {
    return NULL;
  }
  char pattern[32];
  snprintf(pattern, sizeof(pattern), "\"%s\"", key);
  const char* cursor = strstr(json, pattern);
  if (cursor == NULL) {
    return NULL;
  }
  cursor += strlen(pattern);
  while (*cursor == ' ' || *cursor == '\t' || *cursor == '\r' || *cursor == '\n') {
    cursor++;
  }
  if (*cursor != ':') {
    return NULL;
  }
  cursor++;
  while (*cursor == ' ' || *cursor == '\t' || *cursor == '\r' || *cursor == '\n') {
    cursor++;
  }
  return cursor;
}

/**
 * 取 JSON 字符串字段。
 *
 * 这里只做「够用就好」的解析：回执是我们自己的服务端发的，结构固定，
 * 没必要为几个字段引入 ArduinoJson（多一个库依赖，还要吃几 KB RAM）。
 */
static bool jsonFindString(const char* json, const char* key, char* out, size_t outSize) {
  const char* cursor = jsonFindValue(json, key);
  if (cursor == NULL || *cursor != '"') {
    return false;
  }
  cursor++;
  size_t n = 0;
  while (*cursor != '\0' && *cursor != '"' && (n + 1) < outSize) {
    if (*cursor == '\\' && *(cursor + 1) != '\0') {
      cursor++;   // 简单反转义：回执里的中文是 UTF-8 原文，不会命中这里
    }
    out[n++] = *cursor++;
  }
  out[n] = '\0';
  return *cursor == '"';
}

/** 取 JSON 整数字段；缺失或不是数字时返回 fallback */
static long jsonFindInt(const char* json, const char* key, long fallback) {
  const char* cursor = jsonFindValue(json, key);
  if (cursor == NULL) {
    return fallback;
  }
  char* end = NULL;
  long value = strtol(cursor, &end, 10);
  if (end == cursor) {
    return fallback;
  }
  return value;
}

// =============================================================================
// 五、LED / 蜂鸣器（全部非阻塞）
// =============================================================================

static void ledWrite(bool on) {
  digitalWrite(PIN_LED, on ? HIGH : LOW);   // DevKit v1 板载 LED 高电平点亮
}

static void beepStart(uint16_t freq, uint16_t durationMs) {
  // tone(pin, freq) + 自己在 serviceIndicators 里 noTone，
  // 而不是依赖 tone(pin, freq, duration)：不同内核版本对 duration 的实现有差异
  tone(PIN_BUZZER, freq);
  s_buzzerStopAt = millis() + durationMs;
}

/** 成功：绿灯常亮 300ms + 2000Hz 短鸣 */
static void indicateSuccess() {
  s_ledBlinkLeft = 0;
  ledWrite(true);
  s_ledOnUntil = millis() + 300;
  beepStart(2000, 80);
}

/** 失败：绿灯快闪 3 次 + 800Hz 长鸣 */
static void indicateError() {
  s_ledOnUntil = 0;
  s_ledBlinkLeft = 3;
  s_ledBlinkOn = false;
  s_ledBlinkNextAt = millis();
  beepStart(800, 600);
}

/** 已离线暂存（还没拿到服务端结果）：常亮 300ms + 1500Hz 极短鸣，音调与成功区分 */
static void indicateQueued() {
  s_ledBlinkLeft = 0;
  ledWrite(true);
  s_ledOnUntil = millis() + 300;
  beepStart(1500, 60);
}

static void serviceIndicators(uint32_t now) {
  if (s_ledBlinkLeft > 0) {
    if ((int32_t)(now - s_ledBlinkNextAt) >= 0) {
      s_ledBlinkOn = !s_ledBlinkOn;
      ledWrite(s_ledBlinkOn);
      if (!s_ledBlinkOn) {
        s_ledBlinkLeft--;   // 一个「亮→灭」算一次
      }
      s_ledBlinkNextAt = now + (s_ledBlinkOn ? 100 : 120);
    }
  } else if (s_ledOnUntil != 0 && (int32_t)(now - s_ledOnUntil) >= 0) {
    ledWrite(false);
    s_ledOnUntil = 0;
  }

  if (s_buzzerStopAt != 0 && (int32_t)(now - s_buzzerStopAt) >= 0) {
    noTone(PIN_BUZZER);
    s_buzzerStopAt = 0;
  }
}

// =============================================================================
// 六、离线队列（RTC 内存环形缓冲区）
// =============================================================================

static void queueReset(const char* reason) {
  g_queueHead  = 0;
  g_queueCount = 0;
  g_queueMagic = QUEUE_MAGIC;
  Serial.printf("[队列] 复位：%s\n", reason);
}

static void queueInit() {
  if (g_queueMagic != QUEUE_MAGIC) {
    queueReset("冷启动（RTC 内存已被清空），离线队列为空");
    return;
  }
  // 防御：RTC 内容理论上不会坏，但真坏了也绝不能越界访问
  if (g_queueHead >= QUEUE_CAPACITY || g_queueCount > QUEUE_CAPACITY) {
    queueReset("RTC 里的队头/计数非法，按损坏处理");
    return;
  }
  Serial.printf("[队列] 从 RTC 恢复 %u 条待补发记录（队头=%u）\n",
                (unsigned) g_queueCount, (unsigned) g_queueHead);
}

/**
 * 队头是不是「已发出、等回执」的那条？
 *
 * 需要这个判断是因为出队只发生在收到 code==0 之后：在途期间队头不能被动。
 * 若此时队列恰好满了又来了新打卡，覆盖队头会把在途记录挤掉，
 * 等回执回来时就会错删下一条 —— 那才是真正难查的数据错乱。
 */
static bool isHeadInFlight() {
  return s_pending.active
      && g_queueCount > 0
      && strcmp(g_queue[g_queueHead].employeeNo, s_pending.employeeNo) == 0
      && strcmp(g_queue[g_queueHead].punchTime, s_pending.punchTime) == 0;
}

/**
 * 入队（追加到队尾）。满了覆盖最旧的一条 —— 标准环形缓冲区语义。
 *
 * 取舍：覆盖最旧意味着丢掉最早的那次打卡；反过来拒收最新的一条，
 * 会让「刚刷的卡」立刻失败，现场体验更差。两条路都有信息损失，
 * 这里选覆盖并打印告警，让容量 20 条这件事在现场可见。
 * 唯一例外：队头正是等回执的在途记录时不允许覆盖，宁可拒收这一条。
 */
static bool queuePush(const char* employeeNo, const char* punchTime, int matchScore, uint8_t attempts) {
  if (g_queueCount >= QUEUE_CAPACITY) {
    if (isHeadInFlight()) {
      Serial.println("[队列] 已满且队头正在等回执，拒绝本次新打卡（避免挤掉在途记录）");
      return false;
    }
    g_queueHead = (uint8_t)((g_queueHead + 1) % QUEUE_CAPACITY);
    g_queueCount--;
    Serial.println("[队列] 已满，覆盖最旧的一条（该条打卡将永久丢失）");
  }
  uint8_t tail = (uint8_t)((g_queueHead + g_queueCount) % QUEUE_CAPACITY);
  copyStr(g_queue[tail].employeeNo, sizeof(g_queue[tail].employeeNo), employeeNo);
  copyStr(g_queue[tail].punchTime, sizeof(g_queue[tail].punchTime), punchTime);
  g_queue[tail].matchScore = (int16_t) matchScore;
  g_queue[tail].attempts   = attempts;
  g_queueCount++;
  Serial.printf("[队列] 入队 employeeNo=%s punchTime=%s 第%u次尝试 当前 %u/%u\n",
                g_queue[tail].employeeNo, g_queue[tail].punchTime,
                (unsigned) attempts + 1, (unsigned) g_queueCount, (unsigned) QUEUE_CAPACITY);
  return true;
}

/** 丢掉队头一条（成功入库、或判定为永久错误时调用） */
static void queueDropHead(const char* reason) {
  if (g_queueCount == 0) {
    return;
  }
  if (reason != NULL) {
    Serial.printf("[队列] 出队丢弃（%s）employeeNo=%s punchTime=%s，剩余 %u 条\n",
                  reason, g_queue[g_queueHead].employeeNo, g_queue[g_queueHead].punchTime,
                  (unsigned)(g_queueCount - 1));
  }
  g_queueHead = (uint8_t)((g_queueHead + 1) % QUEUE_CAPACITY);
  g_queueCount--;
}

/**
 * 队头这条重试失败：搬到队尾并累加重试次数。
 *
 * 为什么搬到队尾而不是原地重试：队列里可能还排着别人的打卡，
 * 一条卡住的记录不该把后面所有人都堵死。
 * 次数超过 QUEUE_MAX_ATTEMPTS 就丢弃，避免一条脏数据永远在队里转。
 */
static void requeueHeadAfterFailure() {
  if (g_queueCount == 0) {
    return;
  }
  QueuedPunch failed = g_queue[g_queueHead];
  uint8_t nextAttempts = (uint8_t)(failed.attempts + 1);
  queueDropHead(NULL);   // 先摘下来，queuePush 满了也只是覆盖更旧的一条

  if (nextAttempts >= QUEUE_MAX_ATTEMPTS) {
    Serial.printf("[队列] employeeNo=%s punchTime=%s 已重试 %u 次仍失败，丢弃\n",
                  failed.employeeNo, failed.punchTime, (unsigned) nextAttempts);
    return;
  }
  queuePush(failed.employeeNo, failed.punchTime, failed.matchScore, nextAttempts);
}

static void clearPending() {
  memset(&s_pending, 0, sizeof(s_pending));
}

// =============================================================================
// 七、报文组装与上行
// =============================================================================

/**
 * 组装一条打卡报文（含 nonce 与签名）。
 *
 * 用 snprintf 手工拼接而不是 ArduinoJson：
 *   · 少一个库依赖，也多不了几 KB flash/RAM
 *   · 字段顺序、转义行为完全可控 —— 这个报文格式是硬契约，
 *     被库的序列化细节（比如自动转义、字段重排）坑一次就要排查半天
 * employeeNo 已在读卡器入口做过白名单校验，这里天然安全。
 */
static bool buildPunchJson(const char* employeeNo, const char* punchTime, int matchScore,
                           char* outJson, size_t outSize, char* outNonce, size_t outNonceSize) {
  char nonce[NONCE_BUF_LEN];
  char sign[SIGN_BUF_LEN];
  char canonical[128];

  makeNonce(nonce, sizeof(nonce));

  // ⚠️ 待签名串的字段顺序与分隔符不可变，改了必然 60003
  snprintf(canonical, sizeof(canonical), "%s|%s|%s|%s", DEVICE_SN, employeeNo, punchTime, nonce);

  if (!hmacSha256Hex(DEVICE_SECRET, canonical, sign)) {
    return false;
  }

  int written = snprintf(outJson, outSize,
      "{\"deviceSn\":\"%s\",\"employeeNo\":\"%s\",\"punchTime\":\"%s\",\"punchType\":\"%s\","
      "\"nonce\":\"%s\",\"sign\":\"%s\",\"firmwareVersion\":\"%s\",\"matchScore\":%d}",
      DEVICE_SN, employeeNo, punchTime, PUNCH_TYPE_AUTO, nonce, sign, FIRMWARE_VERSION, matchScore);

  if (written < 0 || (size_t) written >= outSize) {
    Serial.printf("[上行] 报文长度 %d 超过缓冲区 %u，放弃本次发送\n", written, (unsigned) outSize);
    return false;
  }
  if (outNonce != NULL && outNonceSize > 0) {
    copyStr(outNonce, outNonceSize, nonce);   // 回执要靠这个 nonce 认领报文
  }
  return true;
}

/** 心跳：deviceSn|nonce 签名，带设备 IP 与 Unix 秒，便于服务端发现时钟故障 */
static void sendHeartbeat() {
  char nonce[NONCE_BUF_LEN];
  char canonical[64];
  char sign[SIGN_BUF_LEN];
  char json[JSON_BUF_LEN];
  char ip[16];

  makeNonce(nonce, sizeof(nonce));
  // ⚠️ 心跳待签名串只有两段
  snprintf(canonical, sizeof(canonical), "%s|%s", DEVICE_SN, nonce);

  if (!hmacSha256Hex(DEVICE_SECRET, canonical, sign)) {
    return;
  }

  // 不用 WiFi.localIP().toString()：避免产生 String 临时对象
  IPAddress addr = WiFi.localIP();
  snprintf(ip, sizeof(ip), "%u.%u.%u.%u", (unsigned) addr[0], (unsigned) addr[1],
           (unsigned) addr[2], (unsigned) addr[3]);

  long ts = (long) time(NULL);
  int written = snprintf(json, sizeof(json),
      "{\"deviceSn\":\"%s\",\"firmwareVersion\":\"%s\",\"ip\":\"%s\","
      "\"nonce\":\"%s\",\"sign\":\"%s\",\"ts\":%ld}",
      DEVICE_SN, FIRMWARE_VERSION, ip, nonce, sign, ts);
  if (written < 0 || (size_t) written >= sizeof(json)) {
    Serial.println("[上行] 心跳报文超长，跳过");
    return;
  }

  if (s_mqtt.publish(TOPIC_HEARTBEAT, json)) {
    Serial.printf("[上行] 心跳 ip=%s ts=%ld\n", ip, ts);
  } else {
    // 心跳丢一次无所谓，下个周期再发，不做离线缓存
    Serial.println("[上行] 心跳发布失败（忽略，60 秒后重试）");
  }
}

/**
 * 补发/发送队头一条。
 *
 * 队列是唯一事实来源：每条打卡先入队，发送只是「试着把队头推出去」，
 * 收到 code==0 才出队。这样「发出去了但回执没回来就断电」不会丢记录
 * （代价是可能重复上报一次，由服务端 messageId/nonce 与同秒唯一约束兜底）。
 */
static void flushQueue(uint32_t now) {
  if (!s_mqtt.connected() || s_pending.active || !timeSynced() || g_queueCount == 0) {
    return;
  }
  if (s_lastQueueAttempt != 0 && (uint32_t)(now - s_lastQueueAttempt) < QUEUE_SEND_GAP_MS) {
    return;
  }

  QueuedPunch head = g_queue[g_queueHead];   // 先拷一份，发送失败时原样留在队里
  char json[JSON_BUF_LEN];
  char nonce[NONCE_BUF_LEN];
  if (!buildPunchJson(head.employeeNo, head.punchTime, (int) head.matchScore,
                      json, sizeof(json), nonce, sizeof(nonce))) {
    Serial.println("[补发] 队头报文组装失败，丢弃以免堵塞后续记录");
    queueDropHead("报文组装失败");
    return;
  }

  s_lastQueueAttempt = now;

  if (!s_mqtt.publish(TOPIC_CHECKIN, json)) {
    // publish 返回 bool：false 通常是连接已断或报文超过缓冲区。
    // 队列不动，等下个周期重试，绝不在这里出队。
    Serial.println("[上行] 打卡发布失败（连接已断或超出缓冲区），记录留在队列里");
    return;
  }

  s_pending.active = true;
  s_pending.sentAt = now;
  s_pending.attempts = head.attempts;
  s_pending.matchScore = head.matchScore;
  copyStr(s_pending.employeeNo, sizeof(s_pending.employeeNo), head.employeeNo);
  copyStr(s_pending.punchTime, sizeof(s_pending.punchTime), head.punchTime);
  // 服务端会把 messageId 派生为 deviceSn-nonce，回执靠这个 nonce 认领
  copyStr(s_pending.nonce, sizeof(s_pending.nonce), nonce);

  Serial.printf("[上行] 打卡 employeeNo=%s punchTime=%s nonce=%s 第%u次尝试\n",
                s_pending.employeeNo, s_pending.punchTime, s_pending.nonce,
                (unsigned) head.attempts + 1);
}

// =============================================================================
// 八、回执处理
// =============================================================================

static void onMqttMessage(char* topic, byte* payload, unsigned int length) {
  // PubSubClient 给的 payload 不带 '\0'，必须先拷成 C 字符串；
  // 直接 (char*)payload 当字符串用会越界读到缓冲区后面的残留数据
  static char buffer[MQTT_BUFFER_SIZE];
  size_t n = length < (sizeof(buffer) - 1) ? (size_t) length : (sizeof(buffer) - 1);
  memcpy(buffer, payload, n);
  buffer[n] = '\0';

  Serial.printf("[下行] topic=%s payload=%s\n", topic, buffer);

  if (strcmp(topic, TOPIC_ACK) != 0) {
    return;
  }

  // 下行 ack 是一个全局主题，所有设备都能收到，必须按 deviceSn 过滤
  char ackSn[24];
  if (!jsonFindString(buffer, "deviceSn", ackSn, sizeof(ackSn)) || strcmp(ackSn, DEVICE_SN) != 0) {
    return;
  }
  if (!s_pending.active) {
    return;   // 没有在途报文（可能是上一条超时后迟到的回执），忽略
  }

  // 认领：ack 里没有 nonce 字段，但服务端把 messageId 派生成了 deviceSn-nonce，
  // 用 nonce 子串判断最精确；取不到时退回按工号匹配
  char ackMessageId[64];
  char ackEmployee[EMPLOYEE_BUF_LEN];
  bool mine = false;
  if (jsonFindString(buffer, "messageId", ackMessageId, sizeof(ackMessageId))
      && strstr(ackMessageId, s_pending.nonce) != NULL) {
    mine = true;
  } else if (jsonFindString(buffer, "employeeNo", ackEmployee, sizeof(ackEmployee))
             && strcmp(ackEmployee, s_pending.employeeNo) == 0) {
    mine = true;
  }
  if (!mine) {
    Serial.println("[回执] 不是当前在途报文的回执，忽略");
    return;
  }

  long code = jsonFindInt(buffer, "code", -1);
  char message[128];
  message[0] = '\0';
  jsonFindString(buffer, "message", message, sizeof(message));

  if (code == 0) {
    char punchType[16];
    char status[16];
    punchType[0] = '\0';
    status[0] = '\0';
    jsonFindString(buffer, "punchType", punchType, sizeof(punchType));
    jsonFindString(buffer, "status", status, sizeof(status));
    long lateMinutes = jsonFindInt(buffer, "lateMinutes", -1);
    long recordId = jsonFindInt(buffer, "recordId", -1);

    Serial.printf("[回执] 打卡成功 recordId=%ld punchType=%s status=%s lateMinutes=%ld\n",
                  recordId, punchType, status, lateMinutes);
    queueDropHead(NULL);   // 真正入库了才出队
    clearPending();
    indicateSuccess();
    return;
  }

  if (code >= 60000 && code < 70000) {
    // 设备侧永久错误：60001 设备未注册 / 60002 设备禁用 / 60003 验签失败 /
    // 60004 重放 / 60005 报文非法 / 60006 时钟漂移 / 61001 工号不存在 …
    // 重试没有意义，直接丢弃并告警，否则会一条条把队列刷满。
    Serial.printf("[回执] 设备侧永久错误 code=%ld message=%s —— 不重试，丢弃该条\n", code, message);
    queueDropHead("服务端返回设备侧永久错误");
    clearPending();
    indicateError();
    return;
  }

  // 其余（5xxxx 服务端临时错误、或未识别的码）：按可重试处理，回队列等下次补发
  Serial.printf("[回执] 可重试错误 code=%ld message=%s —— 记录回队列\n", code, message);
  requeueHeadAfterFailure();
  clearPending();
}

/** 等回执超时：按可重试处理（服务端可能已入库但回执丢了，见 requeueHeadAfterFailure 注释） */
static void servicePendingTimeout(uint32_t now) {
  if (!s_pending.active) {
    return;
  }
  if ((uint32_t)(now - s_pending.sentAt) < ACK_TIMEOUT_MS) {
    return;
  }
  Serial.printf("[回执] 等待超时 %lu ms（nonce=%s），记录回队列重试\n",
                (unsigned long) ACK_TIMEOUT_MS, s_pending.nonce);
  requeueHeadAfterFailure();
  clearPending();
}

// =============================================================================
// 九、读卡器（UART2）
// =============================================================================

/**
 * 非阻塞读一行：逐字节取，遇到 '\n' 认为一行结束。
 *
 * 不做 readStringUntil()：那会阻塞到超时，期间心跳/收包全停。
 * 半行超时（UART_LINE_TIMEOUT_MS）用于在读卡器掉线、波特率不对时
 * 丢掉残缺行，避免和下一次输出粘成一行。
 */
static bool uartReadLine(char* outLine, size_t outSize, uint32_t now) {
  while (Serial2.available() > 0) {
    int c = Serial2.read();
    if (c < 0) {
      break;
    }
    s_uartLastByteAt = now;
    if (c == '\r') {
      continue;   // 只认 '\n' 作行尾，兼容 \r\n 与 \n 两种换行
    }
    if (c == '\n') {
      s_uartLine[s_uartLen] = '\0';
      copyStr(outLine, outSize, s_uartLine);
      s_uartLen = 0;
      return true;
    }
    if (s_uartLen + 1 < sizeof(s_uartLine)) {
      s_uartLine[s_uartLen++] = (char) c;
    } else {
      s_uartLen = 0;   // 超长行：整行丢弃并重新同步，绝不越界
      Serial.println("[读卡器] 收到超长行，已丢弃并重新同步");
    }
  }

  if (s_uartLen > 0 && (uint32_t)(now - s_uartLastByteAt) > UART_LINE_TIMEOUT_MS) {
    s_uartLine[s_uartLen] = '\0';
    Serial.printf("[读卡器] 半行超时丢弃：%s\n", s_uartLine);
    s_uartLen = 0;
  }
  return false;
}

/**
 * 读卡器结果读取（非阻塞）。识别成功返回 true，并把工号与匹配分数带出去。
 *
 * 本项目约定的读卡模块「文本模式」输出：
 *   成功：OK,<employeeNo>,<score>\r\n     例：OK,E1000,92
 *   失败：NG\r\n
 *
 * ═════════════════════════════════════════════════════════════════════════
 *  替换点：若使用 AS608，请在此处替换为它的确认码解析
 *  （AS608 / R307 / R503 等光学指纹模块都走二进制包，不输出文本行）
 * ═════════════════════════════════════════════════════════════════════════
 *  AS608 走的是二进制包（不是文本行），收包格式形如：
 *      EF 01 FF FF FF FF 07 00 03 00 0A 00 08 ...
 *    偏移 0-1：包头 0xEF01
 *    偏移 6  ：包标识 0x07（确认包）
 *    偏移 9  ：确认码 —— 0x00 表示成功；0x02 未检测到手指；0x09 未找到匹配
 *    匹配成功时，返回包里会带指纹库中的模板 ID（page id，2 字节），
 *    需要把它映射成本系统里的 employeeNo（例如后端下发工号↔模板 ID 对照表，
 *    或在设备端烧录一张 EEPROM 映射表）。
 *
 *  换模块时的正确做法：
 *    1. 保留本函数的签名（outEmployeeNo / outSize / outScore / 返回值语义）
 *    2. 把下面「文本行解析」整段替换成 AS608 的收包状态机
 *    3. 在函数末尾仍然调用 isValidEmployeeNo() 做白名单校验
 *    4. 调用处 serviceUart()、handleCard() 一行都不用改
 * ═════════════════════════════════════════════════════════════════════════
 */
static bool readEmployeeFromUart(char* outEmployeeNo, size_t outSize, int* outScore) {
  char line[UART_LINE_BUF_LEN];
  if (!uartReadLine(line, sizeof(line), millis())) {
    return false;
  }

  // 去掉首尾空格/制表符：不少读卡模块会在行尾补空格，不裁剪会导致工号校验失败
  char* trimmed = line;
  while (*trimmed == ' ' || *trimmed == '\t') {
    trimmed++;
  }
  size_t trimmedLen = strlen(trimmed);
  while (trimmedLen > 0 && (trimmed[trimmedLen - 1] == ' ' || trimmed[trimmedLen - 1] == '\t')) {
    trimmed[--trimmedLen] = '\0';
  }
  if (trimmedLen == 0) {
    return false;
  }

  // ↓↓↓ 替换点：AS608 等二进制协议模块，把这段文本解析换成确认码解析 ↓↓↓
  int score = 0;
  char employeeNo[EMPLOYEE_BUF_LEN];
  employeeNo[0] = '\0';

  if (strncmp(trimmed, "OK,", 3) == 0) {
    char* cursor = trimmed + 3;
    char* comma = strchr(cursor, ',');
    if (comma == NULL) {
      Serial.printf("[读卡器] 协议错误，缺少分数段：%s\n", trimmed);
      return false;
    }
    *comma = '\0';
    score = atoi(comma + 1);
    copyStr(employeeNo, sizeof(employeeNo), cursor);
  } else if (strncmp(trimmed, "NG", 2) == 0) {
    Serial.println("[读卡器] NG：识别失败（指纹/卡片未匹配），提示用户重试");
    indicateError();
    return false;
  } else {
    Serial.printf("[读卡器] 无法识别的一行：%s（检查波特率 9600 与协议）\n", trimmed);
    return false;
  }
  // ↑↑↑ 替换点结束 ↑↑↑

  if (!isValidEmployeeNo(employeeNo)) {
    Serial.printf("[读卡器] 工号不合法（只允许 A-Za-z0-9_-，长度 1~%d）：%s\n",
                  EMPLOYEE_NO_MAX, employeeNo);
    indicateError();
    return false;
  }

  copyStr(outEmployeeNo, outSize, employeeNo);
  if (outScore != NULL) {
    *outScore = score;
  }
  return true;
}

static void serviceUart() {
  char employeeNo[EMPLOYEE_BUF_LEN];
  int score = 0;
  if (!readEmployeeFromUart(employeeNo, sizeof(employeeNo), &score)) {
    return;
  }
  Serial.printf("[读卡器] OK employeeNo=%s score=%d\n", employeeNo, score);
  handleCard(employeeNo, score);
}

/** 一次完整打卡受理：时间 → 入队 → 立刻尝试发送 → 没发出去就明确提示「已暂存」 */
static void handleCard(const char* employeeNo, int matchScore) {
  if (!timeSynced()) {
    // 服务端允许 ±300 秒偏差，时间没校准就用「猜的时间」上报必然被打回 60006，
    // 所以这里直接拒收，让用户重刷一次，而不是把脏时间塞进队列污染数据。
    Serial.println("[打卡] 设备时间尚未校准（NTP 未同步），拒绝本次上报");
    indicateError();
    return;
  }

  char punchTime[PUNCH_TIME_BUF_LEN];
  formatLocalTime(time(NULL), punchTime, sizeof(punchTime));

  // 先入队：即使发出去还没等到回执就断电，记录也在 RTC 里
  if (!queuePush(employeeNo, punchTime, matchScore, 0)) {
    // 队列满且队头在途：这条只能当场拒收，让用户稍后重刷
    Serial.println("[打卡] 离线队列已满，本次打卡未被受理，请稍后重试");
    indicateError();
    return;
  }

  flushQueue(millis());
  if (!s_pending.active) {
    // 没拿到在途状态 = 还没发出去（离线 / broker 连不上），先给「已暂存」音，
    // 真正的成败等补发拿到回执时再播报
    Serial.println("[打卡] 当前离线或前面还有待发记录，本次已暂存，联网后补发");
    indicateQueued();
  }
}

// =============================================================================
// 十、WiFi / NTP / MQTT 服务
// =============================================================================

static void serviceWifi(uint32_t now) {
  bool online = (WiFi.status() == WL_CONNECTED);

  if (online && !s_wifiOnline) {
    // 这几行里用到的 String 都是临时对象，.c_str() 只在本次 printf 内有效，
    // 绝不能把它存进全局变量 —— 那是最常见的悬垂指针来源
    Serial.printf("[WiFi] 已连接 SSID=%s IP=%s RSSI=%d dBm\n",
                  WiFi.SSID().c_str(), WiFi.localIP().toString().c_str(), WiFi.RSSI());
    s_lastMqttAttempt = 0;      // 立刻尝试连 broker
    s_lastNtpAttempt  = 0;      // 立刻重新走一次 SNTP
    s_timeReadyLogged = false;
  } else if (!online && s_wifiOnline) {
    Serial.println("[WiFi] 连接已断开，等待重连");
  }
  s_wifiOnline = online;

  if (online) {
    return;
  }
  if (s_lastWifiAttempt != 0 && (uint32_t)(now - s_lastWifiAttempt) < WIFI_RECONNECT_INTERVAL_MS) {
    return;
  }
  s_lastWifiAttempt = now;
  Serial.printf("[WiFi] 未连接（status=%d），重试 %s ...\n", (int) WiFi.status(), WIFI_SSID);
  WiFi.disconnect();
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

static void serviceTime(uint32_t now) {
  if (WiFi.status() != WL_CONNECTED) {
    return;
  }
  if (timeSynced()) {
    if (!s_timeReadyLogged) {
      char localTime[PUNCH_TIME_BUF_LEN];
      formatLocalTime(time(NULL), localTime, sizeof(localTime));
      Serial.printf("[NTP] 校时完成，设备本地时间 = %s（东八区）\n", localTime);
      s_timeReadyLogged = true;
    }
    return;
  }
  if (s_lastNtpAttempt != 0 && (uint32_t)(now - s_lastNtpAttempt) < NTP_RETRY_INTERVAL_MS) {
    return;
  }
  s_lastNtpAttempt = now;
  Serial.printf("[NTP] 正在校时（%s / %s）...\n", NTP_SERVER1, NTP_SERVER2);
  // configTime 负责启动 SNTP 并传入东八区偏移；再显式设置 POSIX TZ 串，
  // 保证 localtime_r() 无论内核版本怎么变都按 UTC+8 格式化
  configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET_SEC, NTP_SERVER1, NTP_SERVER2, NTP_SERVER3);
  setenv("TZ", TZ_INFO, 1);
  tzset();
}

static void serviceMqtt(uint32_t now) {
  if (WiFi.status() != WL_CONNECTED || s_mqtt.connected()) {
    return;
  }
  if (s_lastMqttAttempt != 0 && (uint32_t)(now - s_lastMqttAttempt) < MQTT_RECONNECT_INTERVAL_MS) {
    return;
  }
  s_lastMqttAttempt = now;

  Serial.printf("[MQTT] 正在连接 %s:%d ...\n", MQTT_SERVER, MQTT_PORT);
  // 注意：connect 的返回值是 bool，不要只看 mqtt.connected()
  bool connected;
  if (strlen(MQTT_USERNAME) > 0) {
    connected = s_mqtt.connect(s_mqttClientId, MQTT_USERNAME, MQTT_PASSWORD);
  } else {
    connected = s_mqtt.connect(s_mqttClientId);   // 匿名 broker
  }

  if (!connected) {
    Serial.printf("[MQTT] 连接失败 state=%d，%lu ms 后重试\n",
                  s_mqtt.state(), (unsigned long) MQTT_RECONNECT_INTERVAL_MS);
    return;
  }

  Serial.printf("[MQTT] 已连接 clientId=%s\n", s_mqttClientId);
  if (s_mqtt.subscribe(TOPIC_ACK, 1)) {
    Serial.printf("[MQTT] 已订阅回执主题 %s（QoS1）\n", TOPIC_ACK);
  } else {
    Serial.printf("[MQTT] 订阅 %s 失败，将收不到回执（打卡仍会入库）\n", TOPIC_ACK);
  }

  s_heartbeatDue    = true;   // 上线立刻报一次心跳
  s_lastQueueAttempt = 0;     // 别让断线前的时间戳挡住第一次补发
}

static void serviceHeartbeat(uint32_t now) {
  if (!s_mqtt.connected() || !timeSynced()) {
    return;
  }
  if (!s_heartbeatDue
      && (uint32_t)(now - s_lastHeartbeatAt) < HEARTBEAT_INTERVAL_MS) {
    return;
  }
  s_heartbeatDue = false;
  s_lastHeartbeatAt = now;
  sendHeartbeat();
}

// =============================================================================
// 十一、setup / loop
// =============================================================================

void setup() {
  Serial.begin(115200);
  delay(50);
  Serial.println();
  Serial.println("============================================================");
  Serial.printf(" TeamPulse 打卡机固件 v%s  deviceSn=%s\n", FIRMWARE_VERSION, DEVICE_SN);
  Serial.println(" 读卡器 UART2: RX=GPIO16 TX=GPIO17 @9600 8N1");
  Serial.println(" LED=GPIO2  蜂鸣器=GPIO4");
  Serial.println("============================================================");

  pinMode(PIN_LED, OUTPUT);
  ledWrite(false);
  pinMode(PIN_BUZZER, OUTPUT);
  digitalWrite(PIN_BUZZER, LOW);

  queueInit();

  // 读卡器串口：GPIO16/17 在 WROOM-32 上是空闲引脚（WROVER 模块上它们接 PSRAM，
  // 如果你用的是 ESP32-WROVER 请换到 GPIO25/26 等引脚）
  Serial2.begin(READER_BAUD, SERIAL_8N1, PIN_READER_RX, PIN_READER_TX);

  WiFi.persistent(false);
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.setSleep(false);   // 关省电，显著降低 MQTT 掉线率
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.printf("[WiFi] 正在连接 %s ...\n", WIFI_SSID);

  // clientId 必须全局唯一：用 SN + eFuse MAC 低 24 位，
  // 同型号多台设备也不会互相顶下线
  snprintf(s_mqttClientId, sizeof(s_mqttClientId), "esp32-%s-%06lx",
           DEVICE_SN, (unsigned long)(ESP.getEfuseMac() & 0xFFFFFF));

  s_mqtt.setServer(MQTT_SERVER, MQTT_PORT);
  s_mqtt.setCallback(onMqttMessage);
  s_mqtt.setKeepAlive(30);
  // 默认 256 字节：打卡报文 245 字节已经贴着上限，而下行错误回执带中文详情时
  // 会超限，PubSubClient 超限会整条丢弃（回调都不进），所以放大到 768
  s_mqtt.setBufferSize(MQTT_BUFFER_SIZE);
  Serial.printf("[MQTT] 客户端 ID=%s，报文缓冲 %d 字节\n", s_mqttClientId, MQTT_BUFFER_SIZE);
}

void loop() {
  const uint32_t now = millis();

  serviceWifi(now);
  serviceTime(now);

  if (WiFi.status() == WL_CONNECTED) {
    serviceMqtt(now);
    // mqtt.loop() 负责收包与 keepalive；断线时它返回 false，重连交给 serviceMqtt
    s_mqtt.loop();
  }

  serviceHeartbeat(now);
  flushQueue(now);
  servicePendingTimeout(now);
  serviceUart();
  serviceIndicators(now);

  // 5ms 让出 CPU：ESP32 的 WiFi 协议栈跑在另一个任务里，
  // 这个 delay 主要是让出时间片，不影响收包时延
  delay(5);
}
