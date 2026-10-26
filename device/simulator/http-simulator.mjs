#!/usr/bin/env node
/**
 * =============================================================================
 * TeamPulse 设备模拟器 —— HTTP 兜底链路版（零依赖）
 * =============================================================================
 *
 * 只使用 Node.js 18+ 内置的 fetch 与 node:crypto，**不需要 npm install**。
 * 它模拟一台 ESP32 打卡机向后端 /iot/v1/punch 上报打卡，用于：
 *   · 现场/本地调试：不搭 MQTT 客户端也能走通整条验签入库链路
 *   · 降级验证：MQTT broker 挂掉时，HTTP 通道是否可用
 *
 * 为什么叫「兜底链路」：生产正常走 MQTT，HTTP 只在 broker 故障或调试时用。
 * 想验证真实 MQTT 链路请用同目录的 mqtt-simulator.mjs。
 *
 * -----------------------------------------------------------------------------
 * 用法
 * -----------------------------------------------------------------------------
 *   # 1) 最简：密钥走环境变量（必填，后端首次启动日志里会打印）
 *   node http-simulator.mjs --secret 6f1c...（64 位十六进制）
 *
 *   # 2) 用环境变量（适合 docker/CI）
 *   BASE_URL=http://192.168.1.100:8080 \
 *   DEVICE_SN=TP-ESP32-DEMO \
 *   DEVICE_SECRET=6f1c... \
 *   EMPLOYEE_NO=E1000 \
 *   node http-simulator.mjs
 *
 *   # 3) 循环模式：每 5 秒随机挑一个员工（E1001/E1002/E1003）打卡
 *   node http-simulator.mjs --secret 6f1c... --loop
 *
 *   # 4) 指定后端地址与间隔
 *   node http-simulator.mjs --url http://10.0.0.5:8080 --interval 3000 --loop --secret 6f1c...
 *
 *   # 5) 本机时区不是东八区时，显式指定（punchTime 必须按东八区生成，
 *   #    否则服务端 ±300 秒时钟漂移检查会判 60006）
 *   ATTENDANCE_TZ=Asia/Shanghai node http-simulator.mjs --secret 6f1c...
 *
 * 参数（都可用同名大写环境变量代替，命令行优先）：
 *   --url        BASE_URL        后端地址，默认 http://localhost:8080
 *   --sn         DEVICE_SN       设备序列号，默认 TP-ESP32-DEMO
 *   --secret     DEVICE_SECRET   设备密钥，必填
 *   --employee   EMPLOYEE_NO     工号，默认 E1000
 *   --loop                       循环发送（默认只发一次）
 *   --interval                    循环间隔毫秒，默认 5000
 *   --help                       显示帮助
 *
 * 退出码：0 = 打卡成功；1 = 失败；2 = 参数/配置错误
 * =============================================================================
 */

import { createHmac, randomBytes } from 'node:crypto';

// ---------------------------------------------------------------------------
// 常量
// ---------------------------------------------------------------------------

/** 与后端固件保持一致：punchType=AUTO 由服务端按当天首末次判定签到/签退 */
const PUNCH_TYPE_AUTO = 'AUTO';
const FIRMWARE_VERSION = '1.0.0';
/** 循环模式下随机挑的工号（后端 bootstrap 只保证 E1000 是管理员，这几个需自行创建） */
const LOOP_EMPLOYEES = ['E1001', 'E1002', 'E1003'];
/** 工号白名单：与 ESP32 固件的 isValidEmployeeNo() 完全一致 */
const EMPLOYEE_NO_PATTERN = /^[A-Za-z0-9_-]{1,16}$/;

/** 后端 ErrorCode 速查表，只为把错误码翻成人话，不参与逻辑 */
const ERROR_CODE_TEXT = {
  0: '成功',
  40000: '请求参数不合法',
  40100: '未登录或登录已过期',
  50000: '服务器内部错误',
  60001: '设备未注册（检查 DEVICE_SN 是否与 iot_device 表一致）',
  60002: '设备已被禁用（找管理员把 iot_device.status 改为 ONLINE）',
  60003: '设备报文签名校验失败（检查 DEVICE_SECRET 与待签名串）',
  60004: '重复报文（nonce 已被使用过，10 分钟内去重）',
  60005: '设备报文格式不合法（字段缺失或 punchTime 格式错）',
  60006: '设备时间戳超出允许范围（校准时钟，允许 ±300 秒）',
  61001: '工号不存在或已离职（记录已留痕，status=INVALID）',
  61002: '未配置考勤规则（记录已留痕，需管理员配置）',
  61003: '该员工该时刻已有打卡记录（重复刷卡）',
};

// ---------------------------------------------------------------------------
// 签名与报文
// ---------------------------------------------------------------------------

/**
 * HMAC-SHA256，输出小写十六进制。
 *
 * 与后端 DeviceSignatureVerifier#sign 完全一致：
 *   key   = deviceSecret 的 UTF-8 字节
 *   data  = 待签名串的 UTF-8 字节
 *   hex   = 小写（HexFormat.of().formatHex 也是小写）
 */
function sign(secret, payload) {
  return createHmac('sha256', secret).update(payload, 'utf8').digest('hex');
}

/** 两位补零 */
const pad2 = (value) => String(value).padStart(2, '0');

/**
 * 把时间格式化成后端要求的 `yyyy-MM-dd HH:mm:ss`。
 *
 * 默认按**本机时区**格式化（本机与服务器同处东八区时即正确）；
 * 设置了 ATTENDANCE_TZ（如 Asia/Shanghai）则按该时区格式化，
 * 用于开发机时区不是东八区的情况 —— 服务端是用自己的本地时间做漂移比较的。
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

/**
 * 生成一条合规的打卡报文。
 *
 * 待签名串（字段顺序与分隔符不可变）：deviceSn|employeeNo|punchTime|nonce
 * nonce 用 8 字节随机数 → 16 位 hex，保证每次不同（服务端 10 分钟内去重）。
 */
function buildPunch({ deviceSn, employeeNo, secret, matchScore = 92 }) {
  const punchTime = formatPunchTime();
  const nonce = randomBytes(8).toString('hex');
  const canonical = [deviceSn, employeeNo, punchTime, nonce].join('|');

  return {
    payload: {
      deviceSn,
      employeeNo,
      punchTime,
      punchType: PUNCH_TYPE_AUTO,
      nonce,
      sign: sign(secret, canonical),
      firmwareVersion: FIRMWARE_VERSION,
      matchScore,
    },
    canonical,
  };
}

// ---------------------------------------------------------------------------
// 命令行参数
// ---------------------------------------------------------------------------

function parseArgs(argv) {
  const options = { loop: false, interval: 5000 };

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
      case '--loop':
        options.loop = true;
        break;
      case '--url':
      case '--base-url':
        options.baseUrl = takeValue();
        break;
      case '--sn':
      case '--device-sn':
        options.deviceSn = takeValue();
        break;
      case '--secret':
      case '--device-secret':
        options.secret = takeValue();
        break;
      case '--employee':
      case '--employee-no':
        options.employeeNo = takeValue();
        break;
      case '--interval': {
        const value = Number(takeValue());
        if (!Number.isFinite(value) || value < 100) {
          throw new Error('--interval 必须是 ≥100 的毫秒数');
        }
        options.interval = value;
        break;
      }
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
  console.log('TeamPulse 设备模拟器（HTTP 兜底链路，零依赖）\n');
  console.log('用法：node http-simulator.mjs --secret <设备密钥> [选项]\n');
  console.log('选项：');
  console.log('  --url <url>       后端地址，默认 http://localhost:8080（环境变量 BASE_URL）');
  console.log('  --sn <sn>         设备序列号，默认 TP-ESP32-DEMO（环境变量 DEVICE_SN）');
  console.log('  --secret <hex>    设备密钥，必填（环境变量 DEVICE_SECRET）');
  console.log('  --employee <no>   工号，默认 E1000（环境变量 EMPLOYEE_NO）');
  console.log('  --loop            循环发送，每 --interval 毫秒一次');
  console.log('  --interval <ms>   循环间隔，默认 5000');
  console.log('  --help            显示本帮助\n');
  console.log('环境变量：ATTENDANCE_TZ=Asia/Shanghai 可按指定时区生成 punchTime\n');
  console.log('示例：');
  console.log('  node http-simulator.mjs --secret 6f1c... --loop');
  console.log('  BASE_URL=http://192.168.1.100:8080 DEVICE_SECRET=6f1c... node http-simulator.mjs');
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// ---------------------------------------------------------------------------
// 请求与输出
// ---------------------------------------------------------------------------

/** 单行输出：报文本身约 245 字节，单行最方便和 ESP32 串口日志逐字对照 */
function compactJson(value) {
  return JSON.stringify(value);
}

/**
 * 发一条打卡并解析回执。
 *
 * 注意后端返回的是统一包装体 ApiResponse：
 *   成功：HTTP 200 { code:0, message:"ok", data:{ ...AckMessage... }, timestamp }
 *   业务失败：HTTP 4xx/5xx { code:60003, message:"...", data:null, timestamp }
 * 而 AckMessage 自己也有 code 字段（61001 工号不存在时 HTTP 仍是 200）。
 * 所以判断成败要看「包装体 code」与「data.code」两层，不能只看 HTTP 状态。
 */
async function sendPunch({ baseUrl, payload, canonical }) {
  const url = `${baseUrl}/iot/v1/punch`;
  const startedAt = Date.now();

  console.log(`→ POST ${url}`);
  console.log(`  ${compactJson(payload)}`);
  if (process.env.DEBUG) {
    console.log(`  待签名串: ${canonical}`);
  }

  let response;
  let rawText;
  try {
    response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    rawText = await response.text();
  } catch (error) {
    console.error(`✗ 请求失败：${error.message}`);
    console.error('  排查：后端是否已启动？--url/BASE_URL 是否正确？端口是否被防火墙挡住？');
    return { ok: false, code: -1 };
  }

  const elapsed = Date.now() - startedAt;
  console.log(`← HTTP ${response.status} (${elapsed}ms)`);

  let envelope = null;
  try {
    envelope = rawText ? JSON.parse(rawText) : null;
  } catch {
    // 非 JSON（例如网关返回 HTML 错误页）时原样打印，便于定位
  }
  console.log(`  ${envelope === null ? rawText.trim() || '(空响应)' : compactJson(envelope)}`);

  const ack = envelope?.data ?? null;
  const code = ack ? Number(ack.code ?? -1) : Number(envelope?.code ?? response.status);
  const message = ack?.message ?? envelope?.message ?? '';
  const ok = response.ok && code === 0;

  if (ok) {
    const extras = [
      ack.punchType ? `punchType=${ack.punchType}` : null,
      ack.status ? `status=${ack.status}` : null,
      Number.isFinite(Number(ack.lateMinutes)) && ack.lateMinutes !== null
        ? `lateMinutes=${ack.lateMinutes}`
        : null,
      ack.recordId !== null && ack.recordId !== undefined ? `recordId=${ack.recordId}` : null,
    ].filter(Boolean);
    console.log(`✔ 打卡成功 employeeNo=${payload.employeeNo} ${extras.join(' ')}`);
    return { ok: true, code, ack };
  }

  const hint = ERROR_CODE_TEXT[code];
  console.error(`✗ 打卡失败 employeeNo=${payload.employeeNo} code=${code}${message ? ` message=${message}` : ''}`);
  if (hint) {
    console.error(`  错误码含义：${hint}`);
  }
  if (code >= 60000 && code < 70000) {
    console.error('  提示：6xxxx 为设备侧永久错误，ESP32 固件不会重试，需要人工处理配置。');
  }
  return { ok: false, code, ack };
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

  const baseUrl = String(options.baseUrl ?? process.env.BASE_URL ?? 'http://localhost:8080')
    .replace(/\/+$/, '');
  const deviceSn = String(options.deviceSn ?? process.env.DEVICE_SN ?? 'TP-ESP32-DEMO').trim();
  const secret = String(options.secret ?? process.env.DEVICE_SECRET ?? '').trim();
  const employeeNo = String(options.employeeNo ?? process.env.EMPLOYEE_NO ?? 'E1000').trim();

  if (!secret) {
    console.error('✗ 缺少设备密钥 DEVICE_SECRET。');
    console.error('  取值位置：后端首次启动日志里的「设备密钥: <64 位十六进制>」。');
    console.error('  也可以用命令行：--secret <hex>，或环境变量 DEVICE_SECRET。');
    process.exit(2);
  }
  if (!EMPLOYEE_NO_PATTERN.test(employeeNo)) {
    console.error(`✗ 工号不合法："${employeeNo}"（只允许 A-Za-z0-9_-，长度 1~16，与固件校验收敛）`);
    process.exit(2);
  }

  console.log('◆ TeamPulse 设备 HTTP 模拟器（零依赖，走 /iot/v1/punch 兜底链路）');
  console.log(`  BASE_URL  = ${baseUrl}`);
  console.log(`  DEVICE_SN = ${deviceSn}`);
  console.log(`  工号      = ${employeeNo}`);
  console.log(`  时区      = ${process.env.ATTENDANCE_TZ ?? '本机时区'}`);
  console.log(`  模式      = ${options.loop ? `循环（每 ${options.interval}ms）` : '单次'}`);
  console.log('');

  if (!options.loop) {
    const { payload, canonical } = buildPunch({ deviceSn, employeeNo, secret });
    const result = await sendPunch({ baseUrl, payload, canonical });
    // 用 exitCode 而不是 process.exit()：后者在 stdout 被管道/文件重定向时
    // 有概率把还没 flush 完的日志截断，CI 里排查最怕这个
    process.exitCode = result.ok ? 0 : 1;
    return;
  }

  // 循环模式：每轮随机挑一个工号，单次失败不退出，方便长时间放压/联调
  let successCount = 0;
  let failureCount = 0;
  let stopping = false;
  process.on('SIGINT', () => {
    console.log('\n收到 Ctrl+C，准备停止...');
    stopping = true;
  });

  while (!stopping) {
    const randomEmployee = LOOP_EMPLOYEES[Math.floor(Math.random() * LOOP_EMPLOYEES.length)];
    const { payload, canonical } = buildPunch({ deviceSn, employeeNo: randomEmployee, secret });
    const result = await sendPunch({ baseUrl, payload, canonical });
    if (result.ok) {
      successCount++;
    } else {
      failureCount++;
    }
    console.log(`  —— 统计：成功 ${successCount} 次 / 失败 ${failureCount} 次 ——\n`);
    if (!stopping) {
      await sleep(options.interval);
    }
  }

  console.log(`已停止。共成功 ${successCount} 次，失败 ${failureCount} 次。`);
  process.exitCode = failureCount === 0 ? 0 : 1;
}

await main();
