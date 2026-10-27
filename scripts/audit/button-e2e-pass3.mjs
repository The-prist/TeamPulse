// 第三轮：正面流程验证——每个角色真实执行「增 → 改 → 删」，验证按钮真的能用。
// 所有创建的数据都会在流程末尾删除，避免污染演示数据。
const BASE = (process.argv.find((a) => a.startsWith('--base=')) || '').split('=')[1] || 'http://www.team-pulse.site'

const ROLES = [
  { user: 'admin', pass: 'adminpassword', role: 'sysadmin' },
  { user: 'sunqi', pass: '123456', role: 'hr' },
  { user: 'zhangsan', pass: '123456', role: 'manager' },
  { user: 'wangwu', pass: '123456', role: 'leader' },
  { user: 'lisi', pass: '123456', role: 'employee' },
  { user: 'zhaoliu', pass: '123456', role: 'auditor' },
]

const api = async (method, path, token, body) => {
  const r = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  let j = null
  try { j = await r.json() } catch { }
  return { http: r.status, code: j?.code, message: j?.message, data: j?.data }
}
const ok = (r) => r.code === 0

const stamp = Date.now()
const results = []
const record = (role, flow, step, pass, detail = '') => results.push({ role, flow, step, pass, detail })

// ------------------------------------------------------------------ 各流程
const flows = {
  // 日程：任何登录用户都能建自己的日程
  async schedule(t, ctx) {
    const title = 'E2E日程-' + stamp
    const c = await api('POST', '/api/v1/schedule', t, {
      title, content: '自动化测试', startTime: '2026-11-01T10:00:00', endTime: '2026-11-01T11:00:00',
      location: '线上', priority: 'low', status: 'todo',
    })
    const id = c.data?.scheduleId ?? c.data?.id
    record(ctx.role, '日程', '创建', ok(c) && !!id, `${c.http}/${c.code}`)
    if (!id) return
    ctx.temp.scheduleId = id
    const u = await api('PUT', `/api/v1/schedule/${id}`, t, {
      title: title + '-改', content: '改过', startTime: '2026-11-01T10:00:00', endTime: '2026-11-01T12:00:00',
      location: '线上', priority: 'high', status: 'doing',
    })
    record(ctx.role, '日程', '修改', ok(u), `${u.http}/${u.code} ${u.message || ''}`)
    const s = await api('POST', `/api/v1/schedule/${id}/status`, t, { status: 'done' })
    record(ctx.role, '日程', '状态流转', ok(s), `${s.http}/${s.code} ${s.message || ''}`)
    const d = await api('DELETE', `/api/v1/schedule/${id}`, t)
    record(ctx.role, '日程', '删除', ok(d), `${d.http}/${d.code} ${d.message || ''}`)
    if (ok(d)) ctx.temp.scheduleId = null
  },

  // 工作日志：任何登录用户可写自己的
  async worklog(t, ctx) {
    const c = await api('POST', '/api/v1/worklog', t, {
      title: 'E2E日志-' + stamp, content: '自动化测试内容', logDate: '2026-10-27',
    })
    const id = c.data?.worklogId ?? c.data?.id
    record(ctx.role, '工作日志', '创建', ok(c) && !!id, `${c.http}/${c.code}`)
    if (!id) return
    ctx.temp.worklogId = id
    const u = await api('PUT', `/api/v1/worklog/${id}`, t, {
      title: 'E2E日志-改-' + stamp, content: '改过的内容', logDate: '2026-10-27',
    })
    record(ctx.role, '工作日志', '修改', ok(u), `${u.http}/${u.code} ${u.message || ''}`)
    const d = await api('DELETE', `/api/v1/worklog/${id}`, t)
    record(ctx.role, '工作日志', '删除', ok(d), `${d.http}/${d.code} ${d.message || ''}`)
    if (ok(d)) ctx.temp.worklogId = null
  },

  // 评论：任何登录用户可在日程下评论（需先有日程）
  async comment(t, ctx) {
    const s = await api('GET', '/api/v1/schedule?page=1&size=1', t)
    const sid = s.data?.records?.[0]?.scheduleId
    if (!sid) { record(ctx.role, '评论', '准备', false, '无日程可评论'); return }
    const content = 'E2E评论-' + stamp
    const c = await api('POST', '/api/v1/comment', t, {
      relatedType: 'schedule', relatedId: sid, content,
    })
    record(ctx.role, '评论', '发表', ok(c), `${c.http}/${c.code}`)
    // 接口返回 Void，回查拿到刚发的这条
    const list = await api('GET', `/api/v1/comment?relatedType=schedule&relatedId=${sid}`, t)
    const rows = list.data?.records || list.data || []
    const id = (Array.isArray(rows) ? rows : []).find((x) => x.content === content)?.commentId
    if (!id) { record(ctx.role, '评论', '回查', false, '未找到刚发表的评论'); return }
    const u = await api('PUT', `/api/v1/comment/${id}`, t, { content: 'E2E评论-改-' + stamp })
    record(ctx.role, '评论', '修改', ok(u), `${u.http}/${u.code} ${u.message || ''}`)
    const d = await api('DELETE', `/api/v1/comment/${id}`, t)
    record(ctx.role, '评论', '删除', ok(d), `${d.http}/${d.code} ${d.message || ''}`)
  },

  // 团队：登录即可创建（用户工作台也有该按钮），建完即删
  async team(t, ctx) {
    const name = 'E2E团队-' + ctx.role + '-' + stamp
    const c = await api('POST', '/api/v1/team', t, { teamName: name, description: '自动化测试' })
    const id = c.data?.teamId
    record(ctx.role, '团队', '创建', ok(c) && !!id, `${c.http}/${c.code} ${c.message || ''}`)
    if (!id) return
    const u = await api('PUT', `/api/v1/team/${id}`, t, { teamName: name + '-改', description: '改过' })
    record(ctx.role, '团队', '修改', ok(u), `${u.http}/${u.code} ${u.message || ''}`)
    // 加成员 / 移成员（把另一个账号拉进来再踢掉）
    const users = await api('GET', '/api/v1/user/options', t)
    const other = (users.data || []).find((x) => x.userId !== ctx.me.userId)?.userId
    if (other) {
      const a = await api('POST', `/api/v1/team/${id}/members`, t, { teamId: id, userId: other, teamRole: 'member' })
      record(ctx.role, '团队', '加成员', ok(a), `${a.http}/${a.code} ${a.message || ''}`)
      const rm = await api('DELETE', `/api/v1/team/${id}/members/${other}`, t)
      record(ctx.role, '团队', '移成员', ok(rm), `${rm.http}/${rm.code} ${rm.message || ''}`)
    }
    const d = await api('DELETE', `/api/v1/team/${id}`, t)
    record(ctx.role, '团队', '删除', ok(d), `${d.http}/${d.code} ${d.message || ''}`)
  },

  // 补卡：员工/人事/经理可提交（需要 makeup:submit 或登录即可？按权限点）
  async makeup(t, ctx) {
    if (!ctx.me.perms.includes('makeup:submit')) { record(ctx.role, '补卡', '提交', null, '无 makeup:submit，跳过'); return }
    // 用一个不太可能重复的过去日期（按角色错开，避免「该时间点已有待审批」）
    const offset = 20 + Object.keys(ctx.temp).length
    const day = new Date(Date.now() - offset * 86400000).toISOString().slice(0, 10)
    const c = await api('POST', '/api/v1/makeup', t, { punchTime: `${day} 09:00:00`, reason: 'E2E补卡-' + stamp })
    record(ctx.role, '补卡', '提交', ok(c), `${c.http}/${c.code} ${c.message || ''}`)
    const mine = await api('GET', '/api/v1/makeup/mine', t)
    record(ctx.role, '补卡', '我的申请', ok(mine), `${mine.http}/${mine.code}`)
  },

  // 敏感词：admin 专属
  async word(t, ctx) {
    if (!ctx.me.perms.includes('sensitive:manage')) return
    const w = 'E2E词' + stamp
    const c = await api('POST', '/api/v1/sensitive-word', t, { word: w, replacement: '**' })
    const id = c.data?.wordId ?? c.data?.id
    record(ctx.role, '敏感词', '新增', ok(c), `${c.http}/${c.code} ${c.message || ''}`)
    if (id) {
      const u = await api('PUT', `/api/v1/sensitive-word/${id}`, t, { word: w, replacement: '##', status: 'active' })
      record(ctx.role, '敏感词', '修改', ok(u), `${u.http}/${u.code} ${u.message || ''}`)
      const d = await api('DELETE', `/api/v1/sensitive-word/${id}`, t)
      record(ctx.role, '敏感词', '删除', ok(d), `${d.http}/${d.code} ${d.message || ''}`)
    }
  },

  // 设备：admin 专属
  async device(t, ctx) {
    if (!ctx.me.perms.includes('device:manage')) return
    const sn = 'E2E-SN-' + stamp
    const c = await api('POST', '/api/v1/devices', t, { deviceSn: sn, deviceName: 'E2E设备', location: '测试', remark: 'x' })
    const id = c.data?.id ?? c.data?.deviceId
    record(ctx.role, '设备', '注册', ok(c), `${c.http}/${c.code} ${c.message || ''}`)
    if (id) {
      const u = await api('PUT', `/api/v1/devices/${id}`, t, { deviceName: 'E2E设备-改', location: '测试2', remark: 'y', status: 'active' })
      record(ctx.role, '设备', '修改', ok(u), `${u.http}/${u.code} ${u.message || ''}`)
      const r = await api('PUT', `/api/v1/devices/${id}/secret`, t)
      record(ctx.role, '设备', '轮换密钥', ok(r), `${r.http}/${r.code} ${r.message || ''}`)
      const d = await api('DELETE', `/api/v1/devices/${id}`, t)
      record(ctx.role, '设备', '删除', ok(d), `${d.http}/${d.code} ${d.message || ''}`)
    }
  },

  // 用户：admin 专属
  async user(t, ctx) {
    if (!ctx.me.perms.includes('user:manage')) return
    const uname = 'e2e' + stamp
    const c = await api('POST', '/api/v1/user', t, {
      username: uname, password: '123456', email: uname + '@teampulse.local',
      nickname: 'E2E用户', employeeNo: 'E2E' + (stamp % 100000), role: 'employee',
    })
    const id = c.data?.userId ?? c.data?.id
    record(ctx.role, '用户', '创建', ok(c), `${c.http}/${c.code} ${c.message || ''}`)
    if (id) {
      const u = await api('PUT', `/api/v1/user/${id}`, t, { nickname: 'E2E用户-改', role: 'employee', status: 'active' })
      record(ctx.role, '用户', '修改', ok(u), `${u.http}/${u.code} ${u.message || ''}`)
      const p = await api('PUT', `/api/v1/user/${id}/password`, t, { newPassword: 'abcd1234' })
      record(ctx.role, '用户', '重置密码', ok(p), `${p.http}/${p.code} ${p.message || ''}`)
      const d = await api('DELETE', `/api/v1/user/${id}`, t)
      record(ctx.role, '用户', '删除', ok(d), `${d.http}/${d.code} ${d.message || ''}`)
    }
  },

  // 考勤规则：读 + 改回原值（不改变现状）
  async rule(t, ctx) {
    const g = await api('GET', '/api/v1/attendance/rule', t)
    if (!ctx.me.perms.includes('attendance:rule')) {
      record(ctx.role, '考勤规则', '读取', g.http === 403, `${g.http}/${g.code}（预期 403）`)
      return
    }
    record(ctx.role, '考勤规则', '读取', ok(g), `${g.http}/${g.code}`)
    const r = g.data || {}
    const u = await api('PUT', '/api/v1/attendance/rule', t, {
      workStart: r.workStart || '09:00', workEnd: r.workEnd || '18:00',
      lateGraceMinutes: r.lateGraceMinutes ?? 5,
    })
    record(ctx.role, '考勤规则', '修改（原值回写）', ok(u), `${u.http}/${u.code} ${u.message || ''}`)
  },

  // 只读页面：所有角色都能开
  async reads(t, ctx) {
    const paths = [
      ['我的概览', '/api/v1/dashboard/me'],
      ['我的考勤', '/api/v1/attendance/my'],
      ['日程列表', '/api/v1/schedule?page=1&size=1'],
      ['我的团队', '/api/v1/team/my'],
    ]
    for (const [name, p] of paths) {
      const r = await api('GET', p, t)
      record(ctx.role, '只读页面', name, ok(r), `${r.http}/${r.code}`)
    }
  },
}

// ------------------------------------------------------------------ 主流程
const main = async () => {
  console.log(`目标: ${BASE}\n`)
  for (const r of ROLES) {
    const login = await api('POST', '/api/v1/auth/login', null, { username: r.user, password: r.pass })
    const me = {
      token: login.data?.token, perms: login.data?.permissions || [],
      userId: login.data?.userId, role: r.role,
    }
    const ctx = { role: r.role, me, temp: {} }
    console.log(`\n──────── ${r.user} / ${r.role}（${me.perms.length} 权限）────────`)
    for (const [name, fn] of Object.entries(flows)) {
      try { await fn(me.token, ctx) } catch (e) { record(r.role, name, '异常', false, String(e).slice(0, 80)) }
    }
  }

  // 汇总
  const done = results.filter((x) => x.pass !== null)
  const pass = done.filter((x) => x.pass === true)
  const fail = done.filter((x) => x.pass === false)
  const skip = results.filter((x) => x.pass === null)

  console.log(`\n\n=== 第三轮汇总：执行 ${done.length} 步，通过 ${pass.length}，失败 ${fail.length}，跳过 ${skip.length} ===`)
  if (fail.length) {
    console.log('\n--- 失败明细 ---')
    for (const f of fail) console.log(`  ${f.role.padEnd(9)} ${f.flow.padEnd(8)} ${f.step.padEnd(16)} ${f.detail}`)
  }
  console.log('\n--- 通过的流程（按角色）---')
  for (const r of ROLES) {
    const mine = done.filter((x) => x.role === r.role)
    const okc = mine.filter((x) => x.pass).length
    console.log(`  ${r.role.padEnd(9)} ${okc}/${mine.length}`)
  }
  return fail.length
}

process.exit(await main() > 0 ? 1 : 0)
