#!/usr/bin/env node
/**
 * =============================================================================
 * TeamPulse 设备模拟器 —— MQTT 真实链路版
 * =============================================================================
 *
 * 模拟一台 ESP32 打卡机，走与真机完全相同的 MQTT 链路：
 *   连接 broker → 发布 attendance/checkin → 订阅 attendance/ack 打印回执
 *   → 每 60 秒发一次 attendance/heartbeat 心跳
 *
 * 与同目录 http-simulator.mjs 的分工：
 *   · 本脚本（MQTT）：验证 broker、主题、QoS、下行回执整条真实链路，最接近现场
 *   · http-simulator.mjs：零依赖、走 /iot/v1/punch 兜底 HTTP 接口，适合快速冒烟
 *
 * -----------------------------------------------------------------------------
 * 用法
 * -----------------------------------------------------------------------------
 *   npm install                 # 只装一个依赖：mqtt
 *   cp config.example.json config.json   # 然后把 deviceSecret 填上
 *   npm start                   # 等价于 node mqtt-simulator.mjs
 *
 *   # 指定配置文件 / 覆盖打卡间隔 / 指定工号
 *   node mqtt-simulator.mjs --config ./config.json --interval 5 --employee E1000
 *
 *   # 不写配置文件也能跑：全部走环境变量
 *   MQTT_BROKER_URL=mqtt://192.168.1.100:1883 \
 *   DEVICE_SN=TP-ESP32-DEMO \
 *   DEVICE_SECRET=6f1c...（64 位十六进制） \
 *   EMPLOYEE_NO=E1001 \
 *   node mqtt-simulator.mjs
 *
 *   # 设备密钥从哪来：后端首次启动日志里的「设备密钥: <64 位十六进制>」
 *
 * 注意：后端配置里写的是 tcp://host:1883（Paho 语法），
 * 而 mqtt 这个 npm 包的 URL 必须写成 mqtt://host:1883。
 *
 * 退出码：0 = 正常退出；1 = 运行期致命错误；2 = 配置错误
 * =============================================================================
 */

import { createHmac, randomBytes } from 'node:crypto';
import { readFileSync, existsSync } from 'node:fs';
import { dirname, isAbsolute, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { networkInterfaces } from 'node:os';
import mqtt from 'mqtt';

// ---------------------------------------------------------------------------
// 常量
// ---------------------------------------------------------------------------

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));

const TOPIC_CHECKIN = 'attendance/checkin';
const TOPIC_HEARTBEAT = 'attendance/heartbeat';
const TOPIC_ACK = 'attendance/ack';

const DEFAULT_CONFIG_FILE = 'config.json';
const EXAMPLE_CONFIG_FILE = 'config.example.json';
const DEFAULT_FIRMWARE_VERSION = '1.0.0';
const DEFAULT_BROKER_URL = 'mqtt://localhost:1883';

/** 与固件一致：punchType=AUTO 让服务端按当天首末次判签到/签退 */
const PUNCH_TYPE_AUTO = 'AUTO';
/** 工号白名单，与 ESP32 固件 isValidEmployeeNo() 一致 */
const EMPLOYEE_NO_PATTERN = /^[A-Za-z0-9_-]{1,16}$/;

/** 离线暂存上限，与固件的 RTC 环形队列容量对齐 */
const OFFLINE_QUEUE_CAPACITY = 20;
/** 等回执超时，超时后该条算失败但不再重发（避免制造重复记录） */
const ACK_TIMEOUT_MS = 5000;

/** 后端 ErrorCode 速查（仅用于把错误码翻成人话） */
const ERROR_CODE_TEXT = {
  0: '成功',
  40000: '请求参数不合法',
  50000: '服务器内部错误',
  60001: '设备未注册（检查 deviceSn 是否与 iot_device 表一致）',
  60002: '设备已被禁用',
  60003: '设备报文签名校验失败（检查 deviceSecret 与待签名串）',
  60004: '重复报文（nonce 在 10 分钟内被用过）',
  60005: '设备报文格式不合法（字段缺失或 punchTime 格式错）',
  60006: '设备时间戳超出允许范围（校时，允许 ±300 秒）',
  61001: '工号不存在或已离职（记录已留痕 status=INVALID）',
  61002: '未配置考勤规则（记录已留痕）',
  61003: '该员工该时刻已有打卡记录（重复刷卡）',
};

// ---------------------------------------------------------------------------
// 小工具（与 http-simulator.mjs 有意重复：两个脚本各自独立可跑，不额外引入共享模块）
// ---------------------------------------------------------------------------

/** HMAC-SHA256 → 小写十六进制，与后端 DeviceSignatureVerifier#sign 一致 */
function sign(secret, payload) {
  return createHmac('sha256', secret).update(payload, 'utf8').digest('hex');
}

const pad2 = (value) => String(value).padStart(2, '0');

/**
 * 格式化为后端要求的 `yyyy-MM-dd HH:mm:ss`。
 *
 * 默认按本机时区；设置 ATTENDANCE_TZ（如 Asia/Shanghai）可按指定时区格式化。
 * 服务端是用自己的本地时间做 ±300 秒漂移比较的，模拟机与服务器不同时区时必须指定。
 */
function formatPunchTime(date = new Date(), timeZone = process.env.ATTENDANCE_TZ) {
  if (!timeZone) {
    return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} `
      + `${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`;
  }
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).formatToParts(date);
  const pick = (type) => parts.find((part) => part.type === type)?.value ?? '00';
  return `${pick('year')}-${pick('month')}-${pick('day')} `
    + `${pick('hour')}:${pick('minute')}:${pick('second')}`;
}

/** 取本机第一个非回环 IPv4，用于心跳里的 ip 字段（让管理端能看出设备在哪个网段） */
function localIpv4() {
  for (const list of Object.values(networkInterfaces())) {
    for (const entry of list ?? []) {
      const isV4 = entry.family === 'IPv4' || entry.family === 4;
      if (isV4 && !entry.internal) {
        return entry.address;
      }
    }
  }
  return '127.0.0.1';
}

function maskSecret(secret) {
  if (!secret) {
    return '(空)';
  }
  return secret.length <= 8 ? '****' : `${secret.slice(0, 4)}...${secret.slice(-4)}`;
}

const sleep = (ms) => new Promise((resolveWait) => setTimeout(resolveWait, ms));

// ---------------------------------------------------------------------------
// 配置加载：config.json → 环境变量 → 命令行，后者覆盖前者
// ---------------------------------------------------------------------------

function parseArgs(argv) {
  const options = {};
  for (let i = 0; i < argv.length; i++) {
    const raw = argv[i];
    const equalAt = raw.indexOf('=');
    const flag = equalAt >= 0 ? raw.slice(0, equalAt) : raw;
    const inlineValue = equalAt >= 0 ? raw.slice(equalAt + 1) : undefined;
    const takeValue = () => {
      if (inlineValue !== undefined) {
        return inlineValue;
      }
      const next = argv[++i];
      if (next === undefined) {
        throw new Error(`参数 ${flag} 缺少取值`);
      }
      return next;
    };

    switch (flag) {
      case '--config':
        options.configPath = takeValue();
        break;
      case '--interval':
        options.punchIntervalSeconds = Number(takeValue());
        break;
      case '--employee':
      case '--employee-no':
        options.employeeNo = takeValue();
        break;
      case '--help':
      case '-h':
        options.help = true;
        break;
      default:
        if (flag.startsWith('-')) {
          throw new Error(`未知参数: ${raw}`);
        }
    }
  }
  return options;
}

function printHelp() {
  console.log('TeamPulse 设备模拟器（MQTT 真实链路版）\n');
  console.log('用法：npm start   或   node mqtt-simulator.mjs [选项]\n');
  console.log('准备一份配置：cp config.example.json config.json，然后填写 deviceSecret\n');
  console.log('选项：');
  console.log('  --config <路径>     配置文件，默认 ./config.json（不存在时退回 config.example.json）');
  console.log('  --interval <秒>     打卡间隔，覆盖配置里的 punchIntervalSeconds');
  console.log('  --employee <工号>   固定工号，覆盖配置里的 employeeNos 随机挑选');
  console.log('  --help              显示本帮助\n');
  console.log('环境变量（优先级高于配置文件，低于命令行）：');
  console.log('  MQTT_BROKER_URL  MQTT_USERNAME  MQTT_PASSWORD');
  console.log('  DEVICE_SN  DEVICE_SECRET  EMPLOYEE_NO');
  console.log('  PUNCH_INTERVAL_SECONDS  HEARTBEAT_INTERVAL_SECONDS  ATTENDANCE_TZ\n');
  console.log('示例：');
  console.log('  node mqtt-simulator.mjs --config ./config.json --interval 5');
  console.log('  MQTT_BROKER_URL=mqtt://192.168.1.100:1883 DEVICE_SECRET=6f1c... node mqtt-simulator.mjs');
}

/** 读 JSON 文件，失败时给出可操作的提示而不是抛裸异常 */
function readJsonFile(path) {
  try {
    // 去掉 UTF-8 BOM：Windows 记事本 / PowerShell 的 Set-Content 默认会写 BOM，
    // 带 BOM 的文本 JSON.parse 会直接抛「Unexpected token」，报错信息还看不出是 BOM
    const text = readFileSync(path, 'utf8').replace(/^\uFEFF/, '');
    return JSON.parse(text);
  } catch (error) {
    console.error(`✗ 读取配置失败：${path}`);
    console.error(`  原因：${error.message}`);
    console.error('  提示：配置必须是合法 JSON（双引号、不能有注释、末尾不能多逗号）');
    process.exit(2);
  }
}

function resolveConfigPath(cliPath) {
  // 显式 --config 但文件不存在时直接报错，不去猜别的文件：
  // 静默回退到 config.example.json 会让人以为「配置生效了」，排查成本极高
  if (cliPath) {
    const explicit = isAbsolute(cliPath) ? cliPath : resolve(process.cwd(), cliPath);
    return existsSync(explicit) ? explicit : null;
  }

  const candidates = [
    join(process.cwd(), DEFAULT_CONFIG_FILE),
    join(SCRIPT_DIR, DEFAULT_CONFIG_FILE),
    join(SCRIPT_DIR, EXAMPLE_CONFIG_FILE),
  ];
  for (const candidate of candidates) {
    if (existsSync(candidate)) {
      return candidate;
    }
  }
  return null;
}

function loadConfig(options) {
  const configPath = resolveConfigPath(options.configPath);
  if (!configPath) {
    console.error('✗ 找不到配置文件。');
    if (options.configPath) {
      console.error(`  指定的路径不存在：${options.configPath}`);
    } else {
      console.error('  请先执行：cp config.example.json config.json，然后填写 deviceSecret');
      console.error('  或全部走环境变量：MQTT_BROKER_URL / DEVICE_SN / DEVICE_SECRET');
    }
    process.exit(2);
  }
  const usingExample = configPath.endsWith(EXAMPLE_CONFIG_FILE);
  if (usingExample) {
    console.warn(`⚠ 未找到 config.json，临时使用示例配置：${configPath}`);
    console.warn('  请执行 cp config.example.json config.json 并填写 deviceSecret\n');
  }

  const fileConfig = readJsonFile(configPath);

  const employeeNos = Array.isArray(fileConfig.employeeNos) && fileConfig.employeeNos.length > 0
    ? fileConfig.employeeNos.map((value) => String(value).trim())
    : [String(fileConfig.employeeNo ?? process.env.EMPLOYEE_NO ?? 'E1000').trim()];

  const merged = {
    configPath,
    brokerUrl: String(process.env.MQTT_BROKER_URL ?? fileConfig.brokerUrl ?? DEFAULT_BROKER_URL).trim(),
    username: String(process.env.MQTT_USERNAME ?? fileConfig.username ?? ''),
    password: String(process.env.MQTT_PASSWORD ?? fileConfig.password ?? ''),
    deviceSn: String(process.env.DEVICE_SN ?? fileConfig.deviceSn ?? 'TP-ESP32-DEMO').trim(),
    deviceSecret: String(process.env.DEVICE_SECRET ?? fileConfig.deviceSecret ?? '').trim(),
    firmwareVersion: String(fileConfig.firmwareVersion ?? DEFAULT_FIRMWARE_VERSION).trim(),
    employeeNos,
    punchIntervalSeconds: Number(
      options.punchIntervalSeconds
      ?? process.env.PUNCH_INTERVAL_SECONDS
      ?? fileConfig.punchIntervalSeconds
      ?? 10,
    ),
    heartbeatIntervalSeconds: Number(
      process.env.HEARTBEAT_INTERVAL_SECONDS ?? fileConfig.heartbeatIntervalSeconds ?? 60,
    ),
    matchScore: Number(fileConfig.matchScore ?? 92),
    clientIdPrefix: String(fileConfig.clientIdPrefix ?? 'sim-esp32').trim(),
  };

  // 命令行 --employee 覆盖成单一工号
  if (options.employeeNo !== undefined) {
    merged.employeeNos = [String(options.employeeNo).trim()];
  }

  return merged;
}

/** 配置自检：把能提前发现的错都拦在开始跑之前 */
function validateConfig(config) {
  const problems = [];

  if (!config.deviceSecret) {
    problems.push('deviceSecret 为空：填后端首次启动日志里打印的 64 位十六进制密钥（或设 DEVICE_SECRET）');
  } else if (!/^[0-9a-fA-F]{64}$/.test(config.deviceSecret)) {
    // 不强制退出：有些环境用非 hex 密钥也是合法的，只提示
    console.warn(`⚠ deviceSecret 看起来不是 64 位十六进制（长度 ${config.deviceSecret.length}），请确认与后端 iot_device.secret 完全一致`);
  }
  if (!config.deviceSn) {
    problems.push('deviceSn 为空：必须与后端 iot_device.device_sn 一致');
  }
  if (!/^mqtts?:\/\//.test(config.brokerUrl)) {
    problems.push(`brokerUrl 必须以 mqtt:// 或 mqtts:// 开头（当前 "${config.brokerUrl}"；后端配置里的 tcp:// 是 Paho 语法）`);
  }
  if (!Number.isFinite(config.punchIntervalSeconds) || config.punchIntervalSeconds < 1) {
    problems.push('punchIntervalSeconds 必须是 ≥1 的秒数');
  }
  if (!Number.isFinite(config.heartbeatIntervalSeconds) || config.heartbeatIntervalSeconds < 5) {
    problems.push('heartbeatIntervalSeconds 必须是 ≥5 的秒数');
  }
  for (const employeeNo of config.employeeNos) {
    if (!EMPLOYEE_NO_PATTERN.test(employeeNo)) {
      problems.push(`工号不合法："${employeeNo}"（只允许 A-Za-z0-9_-，长度 1~16）`);
    }
  }

  if (problems.length > 0) {
    console.error('✗ 配置有误：');
    for (const problem of problems) {
      console.error(`  · ${problem}`);
    }
    console.error('\n  参考：device/simulator/README.md');
    process.exit(2);
  }
}

// ---------------------------------------------------------------------------
// 报文组装
// ---------------------------------------------------------------------------

/**
 * 打卡报文：待签名串 deviceSn|employeeNo|punchTime|nonce
 *
 * punchTime 允许外部传入：离线补发时必须用「刷卡当时」的时间，
 * 而且要和时间一起参与签名 —— 先签名再改报文里的 punchTime 会让验签直接失败（60003）。
 */
function buildPunchPayload(config, employeeNo, punchTime = formatPunchTime()) {
  const nonce = randomBytes(8).toString('hex');
  const canonical = [config.deviceSn, employeeNo, punchTime, nonce].join('|');

  return {
    nonce,
    punchTime,
    canonical,
    json: JSON.stringify({
      deviceSn: config.deviceSn,
      employeeNo,
      punchTime,
      punchType: PUNCH_TYPE_AUTO,
      nonce,
      sign: sign(config.deviceSecret, canonical),
      firmwareVersion: config.firmwareVersion,
      matchScore: config.matchScore,
    }),
  };
}

/** 心跳报文：待签名串 deviceSn|nonce（只有两段，别和打卡搞混） */
function buildHeartbeatPayload(config) {
  const nonce = randomBytes(8).toString('hex');
  const canonical = [config.deviceSn, nonce].join('|');

  return JSON.stringify({
    deviceSn: config.deviceSn,
    firmwareVersion: config.firmwareVersion,
    ip: localIpv4(),
    nonce,
    sign: sign(config.deviceSecret, canonical),
    ts: Math.floor(Date.now() / 1000),
  });
}

// ---------------------------------------------------------------------------
// 主流程
// ---------------------------------------------------------------------------

async function main() {
  let options;
  try {
    options = parseArgs(process.argv.slice(2));
  } catch (error) {
    console.error(`✗ ${error.message}`);
    console.error('  用 --help 查看用法');
    process.exit(2);
  }
  if (options.help) {
    printHelp();
    process.exit(0);
  }

  const config = loadConfig(options);
  validateConfig(config);

  const clientId = `${config.clientIdPrefix}-${config.deviceSn}-${randomBytes(3).toString('hex')}`;

  console.log('◆ TeamPulse 设备 MQTT 模拟器（真实链路：broker → 上行 → 回执）');
  console.log(`  配置文件  = ${config.configPath}`);
  console.log(`  Broker    = ${config.brokerUrl}`);
  console.log(`  clientId  = ${clientId}`);
  console.log(`  设备 SN   = ${config.deviceSn}`);
  console.log(`  设备密钥  = ${maskSecret(config.deviceSecret)}`);
  console.log(`  工号池    = ${config.employeeNos.join(', ')}`);
  console.log(`  打卡间隔  = ${config.punchIntervalSeconds} 秒`);
  console.log(`  心跳间隔  = ${config.heartbeatIntervalSeconds} 秒`);
  console.log(`  时区      = ${process.env.ATTENDANCE_TZ ?? '本机时区'}`);
  console.log('');

  const connectOptions = {
    clientId,
    clean: true,
    // 与后端 Paho 侧一致的 keepalive 量级；模拟器固定 30 秒
    keepalive: 30,
    reconnectPeriod: 5000,
    connectTimeout: 10000,
    resubscribe: true,
  };
  if (config.username) {
    connectOptions.username = config.username;
  }
  if (config.password) {
    connectOptions.password = config.password;
  }

  const client = mqtt.connect(config.brokerUrl, connectOptions);

  /** 离线期间暂存的打卡（与固件的 RTC 队列同容量，超了丢最旧） */
  const offlineQueue = [];
  /** 已发出、等回执的报文：nonce → { employeeNo, punchTime, sentAt } */
  const pendingPunches = new Map();

  let punchTimer = null;
  let heartbeatTimer = null;
  let ackTimer = null;
  let stopping = false;
  let stats = { sent: 0, success: 0, failed: 0, queued: 0 };

  /** 把一条打卡推上去；未连接时进离线队列（连带刷卡当时的 punchTime 一起存） */
  function sendPunch(employeeNo) {
    const punchTime = formatPunchTime();

    if (!client.connected) {
      if (offlineQueue.length >= OFFLINE_QUEUE_CAPACITY) {
        const dropped = offlineQueue.shift();
        console.warn(`⚠ 离线队列已满（${OFFLINE_QUEUE_CAPACITY} 条），丢弃最旧的一条：employeeNo=${dropped.employeeNo}`);
      }
      offlineQueue.push({ employeeNo, punchTime });
      stats.queued++;
      console.log(`… 未连接 broker，打卡已暂存（队列 ${offlineQueue.length}/${OFFLINE_QUEUE_CAPACITY}）employeeNo=${employeeNo} punchTime=${punchTime}`);
      return;
    }

    const payload = buildPunchPayload(config, employeeNo, punchTime);
    client.publish(TOPIC_CHECKIN, payload.json, { qos: 1 }, (error) => {
      if (error) {
        stats.failed++;
        console.error(`✗ 发布打卡失败：${error.message}`);
        return;
      }
      stats.sent++;
      pendingPunches.set(payload.nonce, {
        employeeNo,
        punchTime: payload.punchTime,
        sentAt: Date.now(),
      });
      console.log(`→ 打卡已发布 ${TOPIC_CHECKIN} employeeNo=${employeeNo} punchTime=${payload.punchTime} nonce=${payload.nonce}`);
      if (process.env.DEBUG) {
        console.log(`  待签名串：${payload.canonical}`);
        console.log(`  报文：${payload.json}`);
      }
    });
  }

  /**
   * 重连后按顺序补发离线队列。
   *
   * 与固件行为一致：**重新生成 nonce 与 sign，但保留原 punchTime** ——
   * 补发的是「当时那次打卡」，时间改成现在就是错的考勤数据。
   */
  function flushOfflineQueue() {
    if (offlineQueue.length === 0) {
      return;
    }
    const buffered = offlineQueue.splice(0, offlineQueue.length);
    console.log(`[补发] 开始补发 ${buffered.length} 条离线打卡（重新签名，保留原打卡时间）`);
    for (const item of buffered) {
      const payload = buildPunchPayload(config, item.employeeNo, item.punchTime);
      client.publish(TOPIC_CHECKIN, payload.json, { qos: 1 }, (error) => {
        if (error) {
          stats.failed++;
          console.error(`✗ 补发失败：${error.message}`);
          return;
        }
        stats.sent++;
        pendingPunches.set(payload.nonce, {
          employeeNo: item.employeeNo,
          punchTime: payload.punchTime,
          sentAt: Date.now(),
        });
        console.log(`→ 补发打卡 employeeNo=${item.employeeNo} punchTime=${payload.punchTime} nonce=${payload.nonce}`);
      });
    }
  }

  function sendHeartbeat() {
    if (!client.connected) {
      return;
    }
    const json = buildHeartbeatPayload(config);
    client.publish(TOPIC_HEARTBEAT, json, { qos: 1 }, (error) => {
      if (error) {
        console.error(`✗ 心跳发布失败：${error.message}`);
        return;
      }
      console.log(`→ 心跳已发布 ${TOPIC_HEARTBEAT}`);
      if (process.env.DEBUG) {
        console.log(`  报文：${json}`);
      }
    });
  }

  function randomEmployee() {
    return config.employeeNos[Math.floor(Math.random() * config.employeeNos.length)];
  }

  function startTimers() {
    stopTimers();
    punchTimer = setInterval(() => sendPunch(randomEmployee()), config.punchIntervalSeconds * 1000);
    heartbeatTimer = setInterval(sendHeartbeat, config.heartbeatIntervalSeconds * 1000);
    // 每 5 秒清理一次超时未回执的记录，避免 Map 无限增长
    ackTimer = setInterval(() => {
      const now = Date.now();
      for (const [nonce, entry] of pendingPunches) {
        if (now - entry.sentAt > ACK_TIMEOUT_MS + 1000) {
          pendingPunches.delete(nonce);
          console.warn(`⚠ 等回执超时 employeeNo=${entry.employeeNo} nonce=${nonce}`);
          console.warn('  服务端可能已入库但回执丢了；模拟器不自动重发（重发会换 nonce，可能变成重复打卡）');
        }
      }
    }, 5000);
  }

  function stopTimers() {
    if (punchTimer) clearInterval(punchTimer);
    if (heartbeatTimer) clearInterval(heartbeatTimer);
    if (ackTimer) clearInterval(ackTimer);
    punchTimer = null;
    heartbeatTimer = null;
    ackTimer = null;
  }

  // ---- 连接事件 ----
  client.on('connect', () => {
    console.log(`✔ 已连接 broker ${config.brokerUrl}`);
    client.subscribe(TOPIC_ACK, { qos: 1 }, (error) => {
      if (error) {
        console.error(`✗ 订阅 ${TOPIC_ACK} 失败：${error.message}`);
        return;
      }
      console.log(`✔ 已订阅回执主题 ${TOPIC_ACK}（QoS1）`);
    });

    sendHeartbeat();          // 上线立刻报一次，管理端马上能看到设备 ONLINE
    flushOfflineQueue();      // 先补发积压的打卡
    sendPunch(randomEmployee()); // 再做一次即时打卡，方便看到端到端效果
    startTimers();
    console.log('');
  });

  client.on('reconnect', () => {
    console.log('… 正在重连 broker ...');
  });

  client.on('close', () => {
    if (!stopping) {
      console.warn('⚠ 与 broker 的连接已断开，将在 5 秒后自动重连（期间打卡进入离线队列）');
    }
  });

  client.on('error', (error) => {
    console.error(`✗ MQTT 错误：${error.message}`);
    if (/ECONNREFUSED|ENOTFOUND|EAI_AGAIN/.test(error.message)) {
      console.error('  排查：broker 地址/端口是否正确？mosquitto 是否已启动？1883 端口是否放行？');
    }
    if (/Not authorized|bad user name|bad password/i.test(error.message)) {
      console.error('  排查：MQTT_USERNAME / MQTT_PASSWORD 是否与 broker 的密码文件一致？');
    }
  });

  // ---- 回执处理 ----
  client.on('message', (topic, buffer) => {
    const raw = buffer.toString('utf8');
    if (topic !== TOPIC_ACK) {
      return;
    }

    let ack = null;
    try {
      ack = JSON.parse(raw);
    } catch {
      console.warn(`⚠ 收到无法解析的回执：${raw}`);
      return;
    }

    // ack 是全局主题，所有设备都会收到；只认自己 deviceSn 的
    if (ack.deviceSn && ack.deviceSn !== config.deviceSn) {
      return;
    }

    // 认领：服务端把 messageId 派生为 deviceSn-nonce，用它找回对应的在途报文
    let matchedNonce = null;
    for (const nonce of pendingPunches.keys()) {
      if (typeof ack.messageId === 'string' && ack.messageId.includes(nonce)) {
        matchedNonce = nonce;
        break;
      }
    }
    const local = matchedNonce ? pendingPunches.get(matchedNonce) : null;
    if (matchedNonce) {
      pendingPunches.delete(matchedNonce);
    }

    const code = Number(ack.code ?? -1);
    const employeeNo = ack.employeeNo ?? local?.employeeNo ?? '?';
    const detail = [
      `punchType=${ack.punchType ?? '-'}`,
      `status=${ack.status ?? '-'}`,
      ack.lateMinutes !== null && ack.lateMinutes !== undefined ? `lateMinutes=${ack.lateMinutes}` : null,
      ack.recordId !== null && ack.recordId !== undefined ? `recordId=${ack.recordId}` : null,
    ].filter(Boolean).join(' ');

    if (code === 0) {
      stats.success++;
      console.log(`← 回执 code=0 打卡成功 employeeNo=${employeeNo} ${detail}`);
      console.log(`  —— 统计：发布 ${stats.sent} / 成功 ${stats.success} / 失败 ${stats.failed} / 暂存 ${stats.queued} ——`);
      return;
    }

    stats.failed++;
    const hint = ERROR_CODE_TEXT[code];
    console.error(`← 回执 code=${code} 失败 employeeNo=${employeeNo} message=${ack.message ?? ''}`);
    if (hint) {
      console.error(`  含义：${hint}`);
    }
    if (code >= 60000 && code < 70000) {
      console.error('  分类：设备侧永久错误，真机固件不会重试，需要人工修配置');
    } else {
      console.error('  分类：可重试错误，真机固件会把记录放回离线队列稍后补发');
    }
    console.log(`  —— 统计：发布 ${stats.sent} / 成功 ${stats.success} / 失败 ${stats.failed} / 暂存 ${stats.queued} ——`);
  });

  // ---- 退出处理 ----
  async function shutdown(reason) {
    if (stopping) {
      return;
    }
    stopping = true;
    console.log(`\n收到 ${reason}，正在停止...`);
    stopTimers();
    // 给在途 QoS1 报文一点时间落地，再断开
    await sleep(300);
    client.end(false, {}, () => {
      console.log(`已停止。发布 ${stats.sent} 次，成功 ${stats.success} 次，失败 ${stats.failed} 次，离线暂存 ${stats.queued} 次。`);
      // 用 exitCode 让标准输出自然 flush 完再退出，避免日志被管道截断
      process.exitCode = 0;
    });
    // client.end 回调没来也别挂着（unref 的定时器不会阻止进程自然退出）
    setTimeout(() => {
      process.exitCode = 0;
      process.exit(0);
    }, 3000).unref();
  }

  process.on('SIGINT', () => void shutdown('Ctrl+C'));
  process.on('SIGTERM', () => void shutdown('SIGTERM'));
  process.on('unhandledRejection', (error) => {
    console.error('✗ 未捕获的异步异常：', error);
  });
}

await main();
