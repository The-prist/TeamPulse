// 第二轮：对第一轮里"因参数校验先于鉴权而无法判定"的接口，用「合法但必然冲突」的
// 请求体复测。这样：无权限 → 403；若权限真的失效 → 会得到 409/404/业务错误（不会写脏数据）。
import fs from 'node:fs'

const BASE = (process.argv.find((a) => a.startsWith('--base=')) || '').split('=')[1] || 'http://www.team-pulse.site'

const ROLES = [
  { user: 'admin', pass: 'adminpassword', role: 'sysadmin' },
  { user: 'sunqi', pass: '123456', role: 'hr' },
  { user: 'zhangsan', pass: '123456', role: 'manager' },
  { user: 'wangwu', pass: '123456', role: 'leader' },
  { user: 'lisi', pass: '123456', role: 'employee' },
  { user: 'zhaoliu', pass: '123456', role: 'auditor' },
]

const call = async (method, path, token, body) => {
  const r = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  let j = null
  try { j = await r.json() } catch { }
  return { http: r.status, code: j?.code, message: j?.message, data: j?.data }
}

const kind = (r) => {
  if (r.http === 401 || r.code === 40100) return 'UNAUTH'
  if (r.http === 403 || r.code === 40300) return 'FORBID'
  if (r.http >= 500) return 'SERVER'
  if (r.code === 0) return 'OK'
  return 'BIZ'
}

const main = async () => {
  const t = {}
  for (const r of ROLES) {
    const res = await call('POST', '/api/v1/auth/login', null, { username: r.user, password: r.pass })
    t[r.role] = { token: res.data?.token, perms: res.data?.permissions || [] }
  }
  const A = t.sysadmin.token

  // 取真实值用于「冲突型」负载
  const teams = await call('GET', '/api/v1/team/list?page=1&size=1', A)
  const dupTeam = teams.data?.records?.[0]?.teamName || '研发一组'
  const devices = await call('GET', '/api/v1/devices', A)
  const devArr = devices.data?.records || devices.data || []
  const dupDeviceSn = devArr[0]?.deviceSn || 'TP-ESP32-DEMO'
  const words = await call('GET', '/api/v1/sensitive-word', A)
  const wArr = words.data?.records || words.data || []
  const dupWord = wArr[0]?.word || '测试'

  // label, method, path, perm, probeBody（合法但冲突/不会写脏数据）
  const PROBES = [
    ['创建用户', 'POST', '/api/v1/user', 'user:manage',
      { username: 'admin', password: '123456', email: 'dup@teampulse.local', nickname: '冲突测试' }],
    ['重置密码', 'PUT', '/api/v1/user/999999/password', 'user:manage', { newPassword: '12345678' }],
    ['创建团队', 'POST', '/api/v1/team', 'team:manage', { teamName: dupTeam, description: '冲突测试' }],
    ['加敏感词', 'POST', '/api/v1/sensitive-word', 'sensitive:manage', { word: dupWord, replacement: '**' }],
    ['敏感词检测', 'GET', `/api/v1/sensitive-word/check?content=${encodeURIComponent('测试内容')}`, 'sensitive:manage', undefined],
    ['注册设备', 'POST', '/api/v1/devices', 'device:manage',
      { deviceSn: dupDeviceSn, deviceName: '冲突测试', location: 'x', remark: 'x' }],
    ['手工补录', 'POST', '/api/v1/attendance/manual', 'attendance:manage',
      { employeeNo: 'NOT-EXIST-9999', punchTime: '2026-10-27 09:00:00', remark: '冲突测试' }],
    ['改考勤规则', 'PUT', '/api/v1/attendance/rule', 'attendance:rule',
      { workStart: '18:00', workEnd: '09:00', lateGraceMinutes: 5 }], // 起止颠倒，服务端会拒
    ['提交补卡', 'POST', '/api/v1/makeup', 'makeup:submit',
      { punchTime: '2099-01-01 09:00:00', reason: '未来时间，服务端应拒绝' }],
    ['我的看板', 'GET', '/api/v1/kanban/my', 'kanban:view', undefined],
  ]

  const rows = []
  for (const r of ROLES) {
    for (const [label, method, path, perm, body] of PROBES) {
      const allowed = t[r.role].perms.includes(perm)
      const res = await call(method, path, t[r.role].token, body)
      const k = kind(res)
      let ok
      if (allowed) ok = k !== 'FORBID' && k !== 'SERVER'
      else ok = k === 'FORBID'
      rows.push({ label, role: r.role, allowed, k, http: res.http, code: res.code, msg: res.message, ok })
    }
  }

  console.log('=== 第二轮：合法负载下的鉴权判定 ===\n')
  console.log('接口'.padEnd(14) + ROLES.map((r) => r.role.padEnd(10)).join(''))
  for (const [label] of PROBES) {
    let line = label.padEnd(14)
    for (const r of ROLES) {
      const row = rows.find((x) => x.label === label && x.role === r.role)
      const cell = row.k === 'FORBID' ? '⛔403' : (row.k === 'OK' ? '✓200' : (row.k === 'BIZ' ? `✓${row.http}` : `${row.k}`))
      line += (row.ok ? '' : '❌') + cell.padEnd(10)
    }
    console.log(line)
  }

  const bad = rows.filter((x) => !x.ok)
  console.log(`\n共 ${rows.length} 次，异常 ${bad.length} 次`)
  if (bad.length) {
    console.log('\n--- 异常明细 ---')
    for (const b of bad) {
      console.log(`  ${b.role.padEnd(9)} ${b.label.padEnd(12)} 期望${b.allowed ? '放行' : '拒绝'} 实际 ${b.k} ${b.http}/${b.code} ${b.msg || ''}`)
    }
  }
  fs.writeFileSync('E:\\codestorge\\.run-logs\\button-e2e-pass2.json', JSON.stringify(rows, null, 2), 'utf-8')
  return bad.length
}

process.exit(await main() > 0 ? 1 : 0)
