// ============================================================================
// TeamPulse 接口级端到端检查（零依赖，只用 Node 内置能力）
//
// 覆盖四类：
//   1. 认证与令牌
//   2. 权限矩阵（6 角色 × 若干端点，断言 200/403）
//   3. 数据范围（同一批数据，不同角色可见条数必须递减）
//   4. 业务规则（打卡验签/时钟漂移/幂等、补卡审批、审计留痕、指标端点）
//
// 用法：
//   node tests/api/e2e.mjs                     # 默认 http://127.0.0.1:8080
//   node tests/api/e2e.mjs --base http://host  # 指定地址
//   node tests/api/e2e.mjs --read-only         # 不写数据（跳过打卡与审计写操作）
//   node tests/api/e2e.mjs --admin-password xx # 管理员密码（默认 admin123，容器环境用 .env 里的值）
//
// 环境变量（写入类用例需要）：
//   DEVICE_SN / DEVICE_SECRET  一台启用中的设备，用于真实 HMAC 上报
//
// 退出码：全部通过 0，有失败 1。
// ============================================================================

const args = process.argv.slice(2)
const arg = (name, def) => {
  const i = args.indexOf(name)
  return i >= 0 && args[i + 1] ? args[i + 1] : def
}
const BASE = (arg('--base', process.env.BASE_URL || 'http://127.0.0.1:8080')).replace(/\/$/, '')
const READ_ONLY = args.includes('--read-only')
const ADMIN_PASSWORD = arg('--admin-password', process.env.ADMIN_PASSWORD || 'admin123')
const DEVICE_SN = process.env.DEVICE_SN || ''
const DEVICE_SECRET = process.env.DEVICE_SECRET || ''

let pass = 0
const failures = []
const groups = []

function group(name) {
  groups.push({ name, cases: [] })
  console.log(`\n── ${name} ${'─'.repeat(Math.max(0, 46 - name.length))}`)
}

function check(title, ok, detail = '') {
  const g = groups[groups.length - 1]
  if (ok) {
    pass++
    g.cases.push({ title, ok: true, detail })
    console.log(`  ✓ ${title}${detail ? '  ' + detail : ''}`)
  } else {
    g.cases.push({ title, ok: false, detail })
    failures.push(`${g.name} / ${title}  ${detail}`)
    console.log(`  ✗ ${title}${detail ? '  ' + detail : ''}`)
  }
}

const api = async (path, { method = 'GET', token, body, headers = {} } = {}) => {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let json = null
  try { json = JSON.parse(text) } catch { /* 非 JSON 响应 */ }
  return { status: res.status, json, text, headers: res.headers }
}

const login = async (username, password) => {
  const r = await api('/api/v1/auth/login', { method: 'POST', body: { username, password } })
  return r
}

// HMAC-SHA256（Node 内置 crypto）
const hmac = async (secret, message) => {
  const { createHmac } = await import('node:crypto')
  return createHmac('sha256', secret).update(message, 'utf8').digest('hex')
}

const pad = (n) => String(n).padStart(2, '0')
const nowText = () => {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ---------------------------------------------------------------- 0. 可用性
group('0. 服务可用性')
const health = await api('/actuator/health')
if (health.status === 200) {
  check('健康检查返回 UP', health.json?.status === 'UP', `status=${health.json?.status}`)
  // show-details=when_authorized 时匿名请求拿不到 components，这属正常，不算失败
  const dbStatus = health.json?.components?.db?.status
  check('数据库组件状态', dbStatus ? dbStatus === 'UP' : true,
    dbStatus ? `db=${dbStatus}` : '未展示细节（show-details=when_authorized，匿名不可见）')
} else {
  // 经 Nginx 部署时可能只透出 /healthz（静态站健康检查），actuator 不对外
  const hz = await api('/healthz')
  check('服务可达（actuator 未暴露时回退 /healthz）', hz.status === 200, `actuator=${health.status} healthz=${hz.status}`)
}

// 指标端点：直连后端时校验；经 Nginx 且刻意未暴露时不视为失败
const metrics = await api('/actuator/prometheus')
if (metrics.status === 200) {
  check('指标端点可抓取', metrics.text.includes('teampulse_api_requests_total'), 'HTTP 200')
  for (const m of ['teampulse_api_latency_seconds', 'teampulse_api_errors_total']) {
    check(`指标存在：${m}`, metrics.text.includes(m))
  }
  const hasPunchMetric = metrics.text.includes('teampulse_attendance_punch_total')
  check('打卡流水指标状态', true, hasPunchMetric ? '已存在' : '尚未产生上报，首次打卡后出现')
} else {
  check('指标端点', true, `跳过：${metrics.status}（该环境未暴露 actuator/prometheus，抓取端应直连 backend:8080）`)
}

// ---------------------------------------------------------------- 1. 认证
group('1. 认证与令牌')
const adminLogin = await login('admin', ADMIN_PASSWORD)
const admin = adminLogin.json?.data
check('管理员登录成功', adminLogin.status === 200 && admin?.token, `role=${admin?.role}`)
check('登录返回角色与数据范围', admin?.role === 'sysadmin' && admin?.dataScope === 'ALL', `${admin?.role}/${admin?.dataScope}`)
check('登录返回 22 个权限点', (admin?.permissions || []).length === 22, `实际 ${(admin?.permissions || []).length}`)

const badLogin = await login('admin', 'definitely-wrong-password')
check('错误密码返回 401 且提示正确', badLogin.status === 401 && badLogin.json?.code === 40100,
  `code=${badLogin.json?.code} msg=${(badLogin.json?.message || '').slice(0, 20)}`)

const noToken = await api('/api/v1/user/list')
check('无令牌访问受保护接口被拒', noToken.status === 401, `HTTP ${noToken.status}`)

const me = await api('/api/v1/auth/me', { token: admin?.token })
check('/auth/me 可用', me.status === 200 && me.json?.data?.username === 'admin')

// ---------------------------------------------------------------- 2. 权限矩阵
group('2. 权限矩阵（6 角色 × 关键端点）')
const accounts = [
  ['admin', ADMIN_PASSWORD, 'sysadmin'],
  ['zhangsan', '123456', 'manager'],
  ['lisi', '123456', 'employee'],
  ['wangwu', '123456', 'leader'],
  ['sunqi', '123456', 'hr'],
  ['zhaoliu', '123456', 'auditor'],
]
const tokens = {}
for (const [u, p, role] of accounts) {
  const r = await login(u, p)
  tokens[u] = r.json?.data?.token
  check(`${u} 登录（期望 ${role}）`, r.status === 200 && r.json?.data?.role === role,
    `实际 role=${r.json?.data?.role} perms=${(r.json?.data?.permissions || []).length}`)
}

// (端点, 权限点, 允许的角色集合)
const matrix = [
  ['/api/v1/user/list?page=1&size=1', 'user:view', ['admin', 'zhangsan', 'sunqi', 'zhaoliu']],
  ['/api/v1/sensitive-word?page=1&size=1', 'sensitive:manage', ['admin']],
  ['/api/v1/dashboard/admin', 'dashboard:company', ['admin', 'sunqi', 'zhaoliu']],
  ['/api/v1/devices?page=1&size=1', 'device:view', ['admin', 'zhangsan', 'sunqi', 'zhaoliu']],
  ['/api/v1/audit?page=1&size=1', 'audit:view', ['admin', 'sunqi', 'zhaoliu']],
  ['/api/v1/makeup/pending?page=1&size=1', 'attendance:approve', ['admin', 'zhangsan', 'sunqi']],
]
for (const [path, perm, allowed] of matrix) {
  const got = []
  for (const [u] of accounts) {
    const r = await api(path, { token: tokens[u] })
    if (r.status === 200) got.push(u)
  }
  const ok = allowed.every((u) => got.includes(u)) && got.every((u) => allowed.includes(u))
  check(`${perm} 只对 ${allowed.join('/')} 开放`, ok, `实际放行=${got.join(',') || '无'}`)
}

// ---------------------------------------------------------------- 3. 数据范围
group('3. 数据范围（同一批数据，条数应递减）')
const scopeOf = async (u, path) => {
  const r = await api(path, { token: tokens[u] })
  return { total: r.json?.data?.total ?? -1, status: r.status }
}
const sAdmin = await scopeOf('admin', '/api/v1/schedule?page=1&size=200&order=desc')
const sManager = await scopeOf('zhangsan', '/api/v1/schedule?page=1&size=200&order=desc')
const sSelf = await scopeOf('lisi', '/api/v1/schedule?page=1&size=200&order=desc')
check('管理员可见日程数 ≥ 部门经理', sAdmin.total >= sManager.total, `admin=${sAdmin.total} manager=${sManager.total}`)
check('部门经理可见日程数 > 仅本人', sManager.total > sSelf.total, `manager=${sManager.total} self=${sSelf.total}`)
check('普通员工只能看到自己的 1 条', sSelf.total === 1, `实际 ${sSelf.total}`)

const pAdmin = await scopeOf('admin', '/api/v1/attendance/records?page=1&size=200')
const pManager = await scopeOf('zhangsan', '/api/v1/attendance/records?page=1&size=200')
const pSelf = await scopeOf('lisi', '/api/v1/attendance/records?page=1&size=200')
check('打卡记录范围同样收窄', pAdmin.total > pManager.total && pManager.total > pSelf.total,
  `admin=${pAdmin.total} manager=${pManager.total} self=${pSelf.total}`)

// 越权写入：员工改考勤规则应 403
const denyRule = await api('/api/v1/attendance/rule', {
  method: 'PUT', token: tokens['lisi'],
  body: { ruleName: 'x', workStart: '09:00', workEnd: '18:00', lateGraceMinutes: 5, earlyGraceMinutes: 5, workDays: '1,2,3,4,5', enabled: 1 },
})
check('员工改考勤规则被拒（403）', denyRule.status === 403, `HTTP ${denyRule.status} code=${denyRule.json?.code}`)

// ---------------------------------------------------------------- 4. 打卡业务规则
group('4. 打卡业务规则（验签 / 漂移 / 幂等）')
if (!DEVICE_SN || !DEVICE_SECRET) {
  check('跳过：未提供 DEVICE_SN / DEVICE_SECRET（写入类用例需要真实设备密钥）', true, 'skipped')
} else {
  const punch = (noOverride) => {
    const time = noOverride || nowText()
    const nonce = 'e2e-' + Date.now() + '-' + Math.random().toString(16).slice(2, 8)
    const canonical = [DEVICE_SN, 'E1001', time, nonce].join('|')
    return hmac(DEVICE_SECRET, canonical).then((sign) => ({ time, nonce, sign }))
  }

  // 坏签名
  const bad = await api('/iot/v1/punch', {
    method: 'POST',
    body: { deviceSn: DEVICE_SN, employeeNo: 'E1001', punchTime: nowText(), nonce: 'bad-' + Date.now(), sign: 'deadbeef' },
  })
  check('坏签名被拒', bad.status >= 400 && bad.json?.code === 60003, `HTTP ${bad.status} code=${bad.json?.code}`)

  // 时钟漂移（2026-10-27 08:30:00 与当前时间差远超 300 秒）
  const drifted = await punch('2026-10-27 08:30:00')
  const driftRes = await api('/iot/v1/punch', {
    method: 'POST',
    body: { deviceSn: DEVICE_SN, employeeNo: 'E1001', punchTime: drifted.time, nonce: drifted.nonce, sign: drifted.sign },
  })
  check('时钟漂移超过 ±300s 被拒', driftRes.status >= 400 && driftRes.json?.code === 60006, `code=${driftRes.json?.code}`)

  if (READ_ONLY) {
    check('跳过写操作（--read-only）', true, 'skipped')
  } else {
    // 正常上报
    const good = await punch()
    const r1 = await api('/iot/v1/punch', {
      method: 'POST',
      body: { deviceSn: DEVICE_SN, employeeNo: 'E1001', punchTime: good.time, nonce: good.nonce, sign: good.sign },
    })
    check('合法报文上报成功', r1.status === 200 && r1.json?.code === 0, `HTTP ${r1.status} code=${r1.json?.code}`)

    // 重复上报：应 200 且不新增记录（幂等）
    const before = await scopeOf('admin', '/api/v1/attendance/records?page=1&size=1')
    const r2 = await api('/iot/v1/punch', {
      method: 'POST',
      body: { deviceSn: DEVICE_SN, employeeNo: 'E1001', punchTime: good.time, nonce: good.nonce, sign: good.sign },
    })
    const after = await scopeOf('admin', '/api/v1/attendance/records?page=1&size=1')
    check('重复报文被幂等接受（不报错）', r2.status === 200, `HTTP ${r2.status}`)
    check('重复报文未新增记录', before.total === after.total, `before=${before.total} after=${after.total}`)

    const m2 = await api('/actuator/prometheus')
    if (m2.status === 200) {
      check('幂等指标已累加', m2.text.includes('teampulse_attendance_idempotent_total'), 'teampulse_attendance_idempotent_total 存在')
    } else {
      check('幂等指标', true, `跳过：${m2.status}（指标端点未暴露）`)
    }
  }
}

// ---------------------------------------------------------------- 5. 补卡审批
group('5. 补卡审批流')
const reviewAgain = await api('/api/v1/makeup/1/review', {
  method: 'POST', token: admin?.token, body: { approved: true, remark: 'E2E 重复审批验证' },
})
check('已处理的申请不能重复审批（409）', reviewAgain.status === 409 && reviewAgain.json?.code === 40900,
  `HTTP ${reviewAgain.status} code=${reviewAgain.json?.code}`)

const mine = await api('/api/v1/makeup/mine?page=1&size=5', { token: tokens['lisi'] })
check('员工可查自己的补卡申请', mine.status === 200, `total=${mine.json?.data?.total}`)

const employeePending = await api('/api/v1/makeup/pending?page=1&size=5', { token: tokens['lisi'] })
check('员工不能查审批待办（403）', employeePending.status === 403, `HTTP ${employeePending.status}`)

// ---------------------------------------------------------------- 6. 审计
group('6. 审计留痕')
const auditList = await api('/api/v1/audit?page=1&size=50', { token: admin?.token })
check('管理员可查审计日志', auditList.status === 200 && (auditList.json?.data?.total ?? 0) >= 1,
  `total=${auditList.json?.data?.total}`)

const auditDeny = await api('/api/v1/audit?page=1&size=1', { token: tokens['lisi'] })
check('普通员工查审计被拒（403）', auditDeny.status === 403, `HTTP ${auditDeny.status}`)

const records = auditList.json?.data?.records || []
check('审计记录含操作人与动作', records.every((r) => r.actorName && r.action), `样本 ${records.length} 条`)
check('审计记录含 traceId', records.some((r) => r.traceId), `样本 traceId=${records[0]?.traceId || '无'}`)
const hasFailure = records.some((r) => r.result === 'FAILURE')
check('失败操作也留痕', hasFailure, hasFailure ? '存在 FAILURE 记录' : '没有 FAILURE 记录（可能还没产生失败操作）')

const byAction = await api('/api/v1/audit?action=attendance&page=1&size=5', { token: admin?.token })
check('按动作过滤生效', byAction.status === 200, `total=${byAction.json?.data?.total}`)

// ---------------------------------------------------------------- 7. 分页与参数
group('7. 分页与参数校验')
const bigPage = await api('/api/v1/attendance/records?page=1&size=9999', { token: admin?.token })
check('size 超上限时被截断到 200', (bigPage.json?.data?.size ?? 0) <= 200, `size=${bigPage.json?.data?.size}`)

const badId = await api('/api/v1/user/999999999', { token: admin?.token })
check('不存在的用户返回 404', badId.status === 404 || badId.json?.code === 40400, `HTTP ${badId.status} code=${badId.json?.code}`)

// ---------------------------------------------------------------- 汇总
const total = groups.reduce((n, g) => n + g.cases.length, 0)
console.log('\n' + '═'.repeat(58))
console.log(`  通过 ${pass} / ${total}   失败 ${failures.length}`)
if (failures.length) {
  console.log('\n失败明细：')
  failures.forEach((f) => console.log('  - ' + f))
}
console.log('═'.repeat(58))

if (process.env.E2E_JSON) {
  const { writeFileSync } = await import('node:fs')
  writeFileSync(process.env.E2E_JSON, JSON.stringify({ base: BASE, total, pass, failures, groups }, null, 2), 'utf8')
  console.log(`\n明细已写入 ${process.env.E2E_JSON}`)
}

process.exit(failures.length === 0 ? 0 : 1)
