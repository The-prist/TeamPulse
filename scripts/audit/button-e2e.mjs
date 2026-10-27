// 全量按钮/接口测试：6 角色 × 90 接口 + 真实增删改流程
// 用法：node scripts/audit/button-e2e.mjs [--base http://www.team-pulse.site]
const args = process.argv.slice(2)
const BASE = (args.find((a) => a.startsWith('--base=')) || '').split('=')[1] || 'http://www.team-pulse.site'

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
  try { j = await r.json() } catch { /* 非 JSON */ }
  return { http: r.status, code: j?.code, message: j?.message, data: j?.data }
}

// 结果分类：auth=鉴权是否放行  biz=业务层拒绝
const kind = (r) => {
  if (r.http === 401 || r.code === 40100) return 'UNAUTH'
  if (r.http === 403 || r.code === 40300) return 'FORBID'
  if (r.http >= 500 || (r.code >= 50000 && r.code < 60000)) return 'SERVER'
  if (r.code === 0) return 'OK'
  if (r.http >= 400) return 'BIZ' // 400/404/409：鉴权已通过，参数或业务规则不符
  return 'OTHER'
}

// ------------------------------------------------------------------ 接口表
// 每条：method, path(fx), perm(需要的权限点，null=仅登录), 说明
const ENDPOINTS = [
  ['GET', () => '/api/v1/auth/me', null, '当前用户'],
  ['GET', () => '/api/v1/user/list?page=1&size=1', 'user:view', '用户列表'],
  ['GET', (f) => `/api/v1/user/${f.userId}`, 'user:view', '用户详情'],
  ['GET', () => '/api/v1/user/options', null, '用户下拉'],
  ['GET', () => '/api/v1/user/self', null, '我的资料'],
  ['POST', () => '/api/v1/user', 'user:manage', '创建用户'],
  ['PUT', (f) => `/api/v1/user/${f.userId}`, 'user:manage', '修改用户'],
  ['PUT', (f) => `/api/v1/user/${f.userId}/password`, 'user:manage', '重置密码'],
  ['DELETE', (f) => `/api/v1/user/${f.userId}`, 'user:manage', '删除用户'],
  ['GET', () => '/api/v1/team/list?page=1&size=1', 'team:view', '团队列表'],
  ['GET', () => '/api/v1/team/my', null, '我的团队'],
  ['GET', (f) => `/api/v1/team/${f.teamId}`, null, '团队详情'],
  ['GET', (f) => `/api/v1/team/${f.teamId}/members`, null, '团队成员'],
  ['POST', () => '/api/v1/team', null, '创建团队（登录即可，用户工作台也有入口）'],
  ['PUT', (f) => `/api/v1/team/${f.teamId}`, null, '修改团队'],
  ['DELETE', (f) => `/api/v1/team/${f.teamId}`, null, '删除团队'],
  ['POST', (f) => `/api/v1/team/${f.teamId}/members`, null, '加成员'],
  ['PUT', (f) => `/api/v1/team/${f.teamId}/members/${f.otherUserId}/role`, null, '改成员角色'],
  ['DELETE', (f) => `/api/v1/team/${f.teamId}/members/${f.otherUserId}`, null, '移成员'],
  ['GET', () => '/api/v1/schedule?page=1&size=1', null, '日程列表'],
  ['GET', (f) => `/api/v1/schedule/${f.scheduleId}`, null, '日程详情'],
  ['GET', () => '/api/v1/schedule/summary', null, '日程统计'],
  ['POST', () => '/api/v1/schedule', null, '创建日程'],
  ['PUT', (f) => `/api/v1/schedule/${f.scheduleId}`, null, '修改日程'],
  ['POST', (f) => `/api/v1/schedule/${f.scheduleId}/status`, null, '改日程状态'],
  ['DELETE', (f) => `/api/v1/schedule/${f.scheduleId}`, null, '删除日程'],
  ['GET', () => '/api/v1/kanban', 'kanban:view', '看板列表'],
  ['GET', () => '/api/v1/kanban/my', null, '我的看板（登录即可）'],
  ['GET', (f) => `/api/v1/kanban/${f.kanbanId}/detail`, 'kanban:view', '看板详情'],
  ['POST', () => '/api/v1/kanban', null, '创建看板（登录即可）'],
  ['PUT', (f) => `/api/v1/kanban/${f.kanbanId}`, null, '修改看板'],
  ['DELETE', (f) => `/api/v1/kanban/${f.kanbanId}`, null, '删除看板'],
  ['POST', (f) => `/api/v1/kanban/${f.kanbanId}/columns`, null, '加列'],
  ['DELETE', (f) => `/api/v1/kanban/columns/${f.columnId}`, null, '删列'],
  ['POST', (f) => `/api/v1/kanban/${f.kanbanId}/items`, null, '加卡片'],
  ['PUT', (f) => `/api/v1/kanban/items/${f.itemId}/move`, null, '移动卡片'],
  ['PUT', (f) => `/api/v1/kanban/items/${f.itemId}/assign`, null, '指派卡片'],
  ['GET', () => '/api/v1/worklog?page=1&size=1', 'worklog:view', '日志列表'],
  ['GET', (f) => `/api/v1/worklog/${f.worklogId}`, 'worklog:view', '日志详情'],
  ['GET', () => '/api/v1/worklog/statistics', 'worklog:view', '日志统计'],
  ['POST', () => '/api/v1/worklog', null, '写日志'],
  ['PUT', (f) => `/api/v1/worklog/${f.worklogId}`, null, '改日志'],
  ['DELETE', (f) => `/api/v1/worklog/${f.worklogId}`, 'worklog:view', '删日志'],
  ['GET', () => '/api/v1/comment?relatedType=schedule&relatedId=1', null, '评论列表'],
  ['GET', () => '/api/v1/comment/tree?relatedType=schedule&relatedId=1', null, '评论树'],
  ['GET', () => '/api/v1/comment/admin/page?page=1&size=1', 'comment:view', '评论管理'],
  ['POST', () => '/api/v1/comment', null, '发评论'],
  ['PUT', (f) => `/api/v1/comment/${f.commentId}`, null, '改评论'],
  ['DELETE', (f) => `/api/v1/comment/${f.commentId}`, null, '删评论'],
  ['GET', () => '/api/v1/sensitive-word', 'sensitive:manage', '敏感词列表'],
  ['POST', () => '/api/v1/sensitive-word', 'sensitive:manage', '加敏感词'],
  ['PUT', (f) => `/api/v1/sensitive-word/${f.wordId}`, 'sensitive:manage', '改敏感词'],
  ['DELETE', (f) => `/api/v1/sensitive-word/${f.wordId}`, 'sensitive:manage', '删敏感词'],
  ['GET', () => '/api/v1/sensitive-word/check?content=' + encodeURIComponent('测试'), 'sensitive:manage', '敏感词检测'],
  ['GET', () => '/api/v1/attendance/records?page=1&size=1', null, '打卡记录'],
  ['GET', () => '/api/v1/attendance/recent', null, '实时打卡流'],
  ['GET', () => '/api/v1/attendance/overview', null, '考勤概览'],
  ['GET', () => '/api/v1/attendance/trend?days=7', null, '考勤趋势'],
  ['GET', () => '/api/v1/attendance/my', null, '我的考勤'],
  ['GET', () => '/api/v1/attendance/calendar?from=2026-10-01&to=2026-10-31', 'attendance:view', '考勤日历'],
  ['GET', (f) => `/api/v1/attendance/employee/${f.employeeUserId}`, 'attendance:view', '员工考勤'],
  ['POST', () => '/api/v1/attendance/manual', 'attendance:manage', '手工补录'],
  ['GET', () => '/api/v1/attendance/rule', 'attendance:rule', '考勤规则'],
  ['PUT', () => '/api/v1/attendance/rule', 'attendance:rule', '改考勤规则'],
  ['GET', () => '/api/v1/devices', 'device:view', '设备列表'],
  ['GET', () => '/api/v1/devices/statistics', 'device:view', '设备统计'],
  ['POST', () => '/api/v1/devices', 'device:manage', '注册设备'],
  ['PUT', (f) => `/api/v1/devices/${f.deviceId}`, 'device:manage', '改设备'],
  ['PUT', (f) => `/api/v1/devices/${f.deviceId}/secret`, 'device:manage', '轮换密钥'],
  ['DELETE', (f) => `/api/v1/devices/${f.deviceId}`, 'device:manage', '删设备'],
  ['GET', () => '/api/v1/makeup/mine', 'makeup:submit', '我的补卡'],
  ['POST', () => '/api/v1/makeup', 'makeup:submit', '提交补卡'],
  ['GET', () => '/api/v1/makeup/pending', 'attendance:approve', '待审批'],
  ['GET', () => '/api/v1/makeup/reviewed', 'attendance:approve', '已审批'],
  ['POST', (f) => `/api/v1/makeup/${f.makeupId}/review`, 'attendance:approve', '审批补卡'],
  ['GET', () => '/api/v1/audit?page=1&size=1', 'audit:view', '审计日志'],
  ['GET', (f) => `/api/v1/audit/${f.auditId}`, 'audit:view', '审计详情'],
  ['GET', () => '/api/v1/audit/options', 'audit:view', '审计筛选选项'],
  ['GET', () => '/api/v1/dashboard/admin', 'dashboard:company', '公司概览'],
  ['GET', () => '/api/v1/dashboard/me', null, '我的概览'],
  ['GET', () => '/api/v1/dashboard/trend', null, '概览趋势'],
  ['GET', () => '/api/v1/department/options', null, '部门下拉'],
  ['GET', () => '/api/v1/role/options', null, '角色下拉'],
]

// ------------------------------------------------------------------ 安全负载
// 对写接口用「必然失败但不改数据」的请求体：鉴权在参数校验之前，
// 有权限 → 400/BIZ；无权限 → 403/FORBID。
const BAD_BODY = {
  POST: {},
  PUT: {},
  DELETE: undefined,
}

// 有些接口的 @Valid 在鉴权之前执行，空 body 会先 400，导致测不出鉴权。
// 这些接口对「无权限角色」改用合法但必然冲突的负载：鉴权有效 → 403；鉴权失效 → 409/404（不写脏数据）
const CONFLICT_BODY = {
  '创建用户': { username: 'admin', password: '123456', email: 'dup@teampulse.local', nickname: '冲突', role: 'employee' },
  '重置密码': { newPassword: '12345678' },
  '注册设备': { deviceSn: 'TP-ESP32-DEMO', deviceName: '冲突', location: 'x', remark: 'x' },
  '加敏感词': { word: '__dup__', replacement: '**' },
  '手工补录': { employeeNo: 'NOT-EXIST-9999', punchTime: '2026-10-27 09:00:00', remark: 'x' },
  '改考勤规则': { workStart: '18:00', workEnd: '09:00', lateGraceMinutes: 5 },
  '提交补卡': { punchTime: '2099-01-01 09:00:00', reason: '未来时间' },
}

const main = async () => {
  console.log(`目标: ${BASE}\n`)

  // 1) 登录
  const tokens = {}
  for (const r of ROLES) {
    const res = await call('POST', '/api/v1/auth/login', null, { username: r.user, password: r.pass })
    tokens[r.role] = { token: res.data?.token, perms: res.data?.permissions || [], userId: res.data?.userId }
    console.log(`登录 ${r.user.padEnd(10)} role=${r.role.padEnd(9)} 权限=${tokens[r.role].perms.length}`)
  }

  // 2) 夹具：全部自建（不再借用真实数据），测试结束后统一清理。
  //    原因见 docs/按钮回归测试记录.md：借用真实 id 会让 DELETE 接口删掉演示数据。
  const A = tokens.sysadmin.token
  const T = Date.now()
  const created = {}

  const mkTeam = await call('POST', '/api/v1/team', A, { teamName: 'E2E夹具团队-' + T, description: 'x' })
  created.teamId = mkTeam.data?.teamId
  const mkUser = await call('POST', '/api/v1/user', A, {
    username: 'e2efix' + T, password: '123456', email: 'e2efix' + T + '@teampulse.local',
    nickname: 'E2E夹具用户', role: 'employee',
  })
  created.userId = mkUser.data?.userId
  const mkSchedule = await call('POST', '/api/v1/schedule', A, {
    title: 'E2E夹具日程-' + T, content: 'x',
    startTime: '2026-12-01T10:00:00', endTime: '2026-12-01T11:00:00',
  })
  created.scheduleId = mkSchedule.data?.scheduleId
  const mkWorklog = await call('POST', '/api/v1/worklog', A, {
    title: 'E2E夹具日志-' + T, content: 'x', logDate: '2026-12-01',
  })
  created.worklogId = mkWorklog.data?.worklogId
  const mkComment = await call('POST', '/api/v1/comment', A, {
    relatedType: 'schedule', relatedId: created.scheduleId, content: 'E2E夹具评论-' + T,
  })
  if (mkComment.code === 0) {
    const cl = await call('GET', `/api/v1/comment?relatedType=schedule&relatedId=${created.scheduleId}`, A)
    const rows = cl.data?.records || cl.data || []
    created.commentId = (Array.isArray(rows) ? rows : []).find((x) => String(x.content).startsWith('E2E夹具评论'))?.commentId
  }
  const mkKanban = await call('POST', '/api/v1/kanban', A, {
    kanbanName: 'E2E夹具看板-' + T, description: 'x', teamId: created.teamId,
  })
  created.kanbanId = mkKanban.data?.kanbanId
  let columnId, itemId
  if (created.kanbanId) {
    const kd = await call('GET', `/api/v1/kanban/${created.kanbanId}/detail`, A)
    columnId = (kd.data?.columns || [])[0]?.columnId
    if (columnId) {
      const it = await call('POST', `/api/v1/kanban/${created.kanbanId}/items`, A, {
        scheduleId: created.scheduleId, columnId, assigneeId: created.userId,
      })
      itemId = it.data?.itemId ?? it.data?.id
    }
  }
  const mkDevice = await call('POST', '/api/v1/devices', A, {
    deviceSn: 'E2E-SN-' + T, deviceName: 'E2E夹具设备', location: 'x', remark: 'x',
  })
  created.deviceId = mkDevice.data?.id ?? mkDevice.data?.deviceId
  const mkW = await call('POST', '/api/v1/sensitive-word', A, { word: 'E2E词' + T, replacement: '**' })
  created.wordId = mkW.data?.wordId ?? mkW.data?.id
  const mkMakeup = await call('POST', '/api/v1/makeup', A, {
    punchTime: '2026-12-01 09:00:00', reason: 'E2E夹具补卡',
  })
  const pend = await call('GET', '/api/v1/makeup/pending?page=1&size=50', A)
  const pendRows = pend.data?.records || pend.data || []
  created.makeupId = (Array.isArray(pendRows) ? pendRows : []).find((x) => String(x.reason || '').includes('E2E夹具'))?.requestId
    ?? mkMakeup.data?.requestId
  const auditList = await call('GET', '/api/v1/audit?page=1&size=1', A)
  const auditRow = (auditList.data?.records || [])[0]

  const fx = {
    scheduleId: created.scheduleId,
    teamId: created.teamId,
    kanbanId: created.kanbanId,
    worklogId: created.worklogId,
    auditId: auditRow?.id ?? auditRow?.auditId,
    deviceId: created.deviceId,
    makeupId: created.makeupId,
    wordId: created.wordId,
    userId: created.userId,
    otherUserId: tokens.sysadmin.userId,
    employeeUserId: created.userId,
    commentId: created.commentId,
    columnId,
    itemId,
  }

  console.log('\n自建夹具:', JSON.stringify(created))

  // 3) 逐角色 × 逐接口
  const rows = []
  for (const r of ROLES) {
    const t = tokens[r.role]
    for (const [method, pathFn, perm, label] of ENDPOINTS) {
      let path
      try { path = pathFn(fx) } catch { rows.push([label, r.role, 'SKIP', '路径构造失败']); continue }
      if (path.includes('undefined')) { rows.push([label, r.role, 'SKIP', '缺夹具']); continue }
      const allowed0 = !perm || t.perms.includes(perm)
      const body = method === 'GET'
        ? undefined
        : (allowed0 ? BAD_BODY[method] : (CONFLICT_BODY[label] ?? BAD_BODY[method]))
      const res = await call(method, path, t.token, body)
      const k = kind(res)
      const allowed = !perm || t.perms.includes(perm)
      // 期望：有权限 → 不能 FORBID；无权限 → 必须 FORBID
      let verdict
      if (allowed) verdict = k === 'FORBID' ? '❌ 应有权限却被拒' : (k === 'SERVER' ? '❌ 500' : '✓')
      else verdict = k === 'FORBID' ? '✓' : (k === 'UNAUTH' ? '❌ 未登录' : (k === 'SERVER' ? '❌ 500' : '❌ 无权限却放行'))
      rows.push([label, r.role, verdict, `${method} ${path} → ${res.http}/${res.code ?? '-'} ${k}`])
    }
  }

  // 4) 汇总
  const bad = rows.filter((r) => r[2].startsWith('❌'))
  const skip = rows.filter((r) => r[2] === 'SKIP')
  console.log(`\n=== 结果：共 ${rows.length} 次请求，通过 ${rows.length - bad.length - skip.length}，失败 ${bad.length}，跳过 ${skip.length} ===`)
  if (bad.length) {
    console.log('\n--- 失败明细 ---')
    for (const [label, role, verdict, detail] of bad) console.log(`  ${role.padEnd(9)} ${label.padEnd(14)} ${verdict}  ${detail}`)
  }
  if (skip.length) {
    console.log('\n--- 跳过明细（前 10）---')
    for (const [label, role, , detail] of skip.slice(0, 10)) console.log(`  ${role.padEnd(9)} ${label.padEnd(14)} ${detail}`)
  }

  // 4.5) 清理临时夹具（顺序：先子后父）
  const cleanup = []
  if (created.commentId) cleanup.push(['评论', `/api/v1/comment/${created.commentId}`])
  if (created.kanbanId) cleanup.push(['看板', `/api/v1/kanban/${created.kanbanId}`])
  if (created.wordId) cleanup.push(['敏感词', `/api/v1/sensitive-word/${created.wordId}`])
  if (created.deviceId) cleanup.push(['设备', `/api/v1/devices/${created.deviceId}`])
  if (created.worklogId) cleanup.push(['日志', `/api/v1/worklog/${created.worklogId}`])
  if (created.scheduleId) cleanup.push(['日程', `/api/v1/schedule/${created.scheduleId}`])
  if (created.userId) cleanup.push(['用户', `/api/v1/user/${created.userId}`])
  if (created.teamId) cleanup.push(['团队', `/api/v1/team/${created.teamId}`])
  for (const [name, path] of cleanup) {
    const r = await call('DELETE', path, A)
    console.log(`  清理 ${name}: ${r.http}/${r.code}`)
  }

  // 5) 各角色可见接口统计
  console.log('\n=== 各角色可访问的接口数（非 FORBID/非 UNAUTH）===')
  for (const r of ROLES) {
    const mine = rows.filter((x) => x[1] === r.role)
    const ok = mine.filter((x) => !x[3].includes('FORBID') && !x[3].includes('UNAUTH')).length
    console.log(`  ${r.role.padEnd(9)} ${ok}/${mine.length}`)
  }

  return bad.length
}

process.exit(await main() > 0 ? 1 : 0)
