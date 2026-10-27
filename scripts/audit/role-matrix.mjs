// ============================================================================
// 逐角色权限审计（基于前端真实配置 + 线上接口实测）
//
// 判定依据来自代码而不是猜测：
//   1. 从 frontend/src/router/index.js 解析每条管理路由的 meta.perms
//   2. 从 frontend/src/layouts/AdminLayout.vue 解析菜单项的 v-if 权限条件
//   3. 用该角色 token 实测每个页面主接口，确认「看得见 = 读得到」
//
// 结论分类：
//   ✓  可见且可读
//   ⛔ 不可见（菜单无入口、路由被守卫拦）—— 期望状态
//   ❌ 可见但接口 403（看得到摸不到，必须修）
//   ⓘ  不可见但接口可读（前端无入口，属深度防御缺口，单独列出）
//
// 用法：node scripts/audit/role-matrix.mjs --base http://127.0.0.1 [--json out.json]
// ============================================================================
import { readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const argv = process.argv.slice(2)
const arg = (n, d) => { const i = argv.indexOf(n); return i >= 0 && argv[i + 1] ? argv[i + 1] : d }
const BASE = arg('--base', 'http://127.0.0.1').replace(/\/$/, '')
const OUT = arg('--json', '')
const ROOT = arg('--root', process.cwd())
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'admin123'

// ---- 1. 解析路由的 meta.perms -------------------------------------------
const routerSrc = readFileSync(join(ROOT, 'frontend/src/router/index.js'), 'utf8')
const ROUTES = []
for (const line of routerSrc.split('\n')) {
  const nameM = line.match(/name: '([\w-]+)'/)
  if (!nameM) continue
  const permsM = line.match(/perms: \[([^\]]*)\]/)
  if (!permsM) continue
  const perms = permsM[1].split(',').map((s) => s.trim().replace(/'/g, '')).filter(Boolean)
  const pathM = line.match(/path: '([^']+)'/)
  ROUTES.push({ name: nameM[1], path: pathM ? '/' + pathM[1].replace(/^\//, '') : '?', perms })
}

// 页面 → 主接口（用于实测「可见即可读」）
const PAGE_API = {
  companyOverview: '/api/v1/dashboard/admin',
  attendanceDashboard: '/api/v1/attendance/overview',
  attendanceRecords: '/api/v1/attendance/records?page=1&size=1',
  attendanceDevices: '/api/v1/devices?page=1&size=1',
  attendanceRule: '/api/v1/attendance/rule',
  attendanceApprovals: '/api/v1/makeup/pending?page=1&size=1',
  scheduleList: '/api/v1/schedule?page=1&size=1',
  kanbanList: '/api/v1/kanban',
  worklogList: '/api/v1/worklog?page=1&size=1',
  commentList: '/api/v1/comment/admin/page?relatedType=schedule&page=1&size=1',
  auditLogs: '/api/v1/audit?page=1&size=1',
  userList: '/api/v1/user/list?page=1&size=1',
  teamList: '/api/v1/team/list?page=1&size=1',
}

// ---- 1.5 解析「能否进入管理后台」的门槛（ADMIN_PANEL_PERMS）-----------
const panelMatch = routerSrc.match(/const ADMIN_PANEL_PERMS = \[([\s\S]*?)\]/)
const ADMIN_PANEL_PERMS = panelMatch
  ? panelMatch[1].split(',').map((s) => s.trim().replace(/'/g, '')).filter(Boolean)
  : []

// ---- 2. 解析 AdminLayout 的菜单权限条件 ---------------------------------
const layoutSrc = readFileSync(join(ROOT, 'frontend/src/layouts/AdminLayout.vue'), 'utf8')
const MENU = []
for (const line of layoutSrc.split('\n')) {
  const m = line.match(/<el-menu-item\s+v-if="auth\.hasPerm\('([^']+)'\)"\s+index="([^"]+)"/)
  if (m) MENU.push({ perm: m[1], path: m[2] })
}
const UNGUARDED_MENU = []
for (const line of layoutSrc.split('\n')) {
  const m = line.match(/<el-menu-item\s+index="([^"]+)"/)
  if (m && !line.includes('v-if')) UNGUARDED_MENU.push(m[1])
}

const ROLES = [
  ['admin', ADMIN_PASSWORD],
  ['zhangsan', '123456'],
  ['sunqi', '123456'],
  ['wangwu', '123456'],
  ['zhaoliu', '123456'],
  ['lisi', '123456'],
]

const login = async (u, p) => {
  const r = await fetch(BASE + '/api/v1/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: u, password: p }),
  })
  const j = await r.json().catch(() => ({}))
  return { token: j?.data?.token, role: j?.data?.role, perms: j?.data?.permissions || [] }
}

const call = async (path, token) => {
  const r = await fetch(BASE + path, { headers: token ? { Authorization: 'Bearer ' + token } : {} })
  await r.text().catch(() => '')
  return r.status
}

const results = []
const issues = []
const leaks = []

console.log(`目标 ${BASE}`)
console.log(`路由权限声明 ${ROUTES.length} 条，菜单权限条件 ${MENU.length} 条，无守卫菜单 ${UNGUARDED_MENU.length} 条\n`)

for (const [user, pwd] of ROLES) {
  const me = await login(user, pwd)
  if (!me.token) { console.log(`✗ ${user} 登录失败\n`); continue }
  console.log(`━━ ${user}（${me.role}，${me.perms.length} 个权限点）━━━━━━━━━━━━━━━━━━`)

  const rows = []
  for (const route of ROUTES) {
    const canPanel = me.perms.some((p) => ADMIN_PANEL_PERMS.includes(p))
    const visible = canPanel && route.perms.some((p) => me.perms.includes(p))
    const api = PAGE_API[route.name]
    const status = api ? await call(api, me.token) : null
    let verdict = visible ? '✓' : '⛔'
    if (!api) { verdict = '— 从列表页进入' }
    else if (visible && status !== 200) { verdict = '❌ 看得到摸不到'; issues.push(`${user} / ${route.name} 可见但接口 ${status}`) }
    if (!visible && status === 200) { verdict = 'ⓘ 无入口但接口可读'; leaks.push(`${user} / ${route.name}（${route.perms.join('/')}）`) }
    rows.push({ route: route.name, path: route.path, perms: route.perms, visible, status, verdict })
    console.log(`  ${visible ? '菜单可见' : '无入口  '}  ${route.name.padEnd(20)} ${String(status ?? '-').padEnd(4)} ${verdict}`)
  }
  results.push({ user, role: me.role, perms: me.perms, rows })
  console.log('')
}

console.log('════════ 汇总 ════════')
if (issues.length) {
  console.log(`❌ 看得到摸不到 ${issues.length} 处：\n  - ` + issues.join('\n  - '))
} else {
  console.log('✓ 所有「菜单可见」的页面，对应接口都能正常读取（不存在看得到摸不到）')
}
if (leaks.length) {
  console.log(`\nⓘ 前端无入口但接口可读 ${leaks.length} 处（深度防御缺口，见 ADR-10）：\n  - ` + leaks.join('\n  - '))
} else {
  console.log('\n✓ 不存在「无入口却可读」的接口')
}

if (OUT) {
  writeFileSync(OUT, JSON.stringify({ base: BASE, routes: ROUTES, menu: MENU, results, issues, leaks }, null, 2), 'utf8')
  console.log(`\n明细已写入 ${OUT}`)
}
process.exit(issues.length ? 1 : 0)
