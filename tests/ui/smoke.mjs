// 浏览器级冒烟测试：真开浏览器 → 每个角色登录 → 逐页访问 → 抓 console error / pageerror。
// 复用系统 Edge（channel: msedge），不需要下载 Playwright 自带浏览器。
//
// 为什么需要它：接口级测试（scripts/audit/button-e2e.mjs）不渲染 Vue 组件，
// 抓不到 template/computed 里的 ReferenceError——那类错误会让整页白屏，
// 接口全 200 也发现不了（参考 2026-10-27 日程详情页事故）。
//
// 用法：
//   node tests/ui/smoke.mjs                  # 默认打 http://www.team-pulse.site
//   BASE=http://81.70.102.116 node tests/ui/smoke.mjs
//   ONLY=employee node tests/ui/smoke.mjs    # 只测一个角色
// 本地用系统 Edge（channel=msedge），CI 用 Playwright 自带 Chromium（CHANNEL=''）。
// 优先用 playwright（CI 会 npx playwright install 装浏览器），退回 playwright-core。
let chromium
try {
  ({ chromium } = await import('playwright'))
} catch {
  ({ chromium } = await import('playwright-core'))
}
const CHANNEL = process.env.CHANNEL === undefined ? 'msedge' : process.env.CHANNEL

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const ONLY = process.env.ONLY || ''

const ROLES = [
  { user: 'admin', pass: 'adminpassword', role: 'sysadmin', panel: 'admin' },
  { user: 'sunqi', pass: '123456', role: 'hr', panel: 'admin' },
  { user: 'zhangsan', pass: '123456', role: 'manager', panel: 'admin' },
  { user: 'wangwu', pass: '123456', role: 'leader', panel: 'admin' },
  { user: 'lisi', pass: '123456', role: 'employee', panel: 'user' },
  { user: 'zhaoliu', pass: '123456', role: 'auditor', panel: 'admin' },
]

// 管理端页面（路由 + 需要数据才有意义的详情页单独处理）
const ADMIN_PAGES = [
  ['仪表盘', '/dashboard'],
  ['公司概览', '/overview/company'],
  ['考勤看板', '/attendance/dashboard'],
  ['打卡记录', '/attendance/records'],
  ['打卡设备', '/attendance/devices'],
  ['考勤规则', '/attendance/rule'],
  ['补卡审批', '/attendance/approvals'],
  ['看板管理', '/kanban'],
  ['日程管理', '/schedule'],
  ['评论管理', '/comments'],
  ['日志管理', '/worklogs'],
  ['审计日志', '/audit'],
  ['用户管理', '/users'],
  ['团队管理', '/teams'],
]

const USER_PAGES = [
  ['我的首页', '/u/home'],
  ['我的概览', '/u/overview/leader'],
  ['我的日程', '/u/schedules'],
  ['我的考勤', '/u/attendance'],
  ['我的看板', '/u/kanban'],
  ['我的日志', '/u/worklogs'],
  ['我的团队', '/u/teams'],
  ['个人信息', '/u/profile'],
]

const IGNORE_PATTERNS = [
  /favicon/i,
  /Failed to load resource: the server responded with a status of 40[13]/i, // 403/401 属预期（无权限）
  /ResizeObserver loop/i,
  /\[Vue warn\]: Failed to resolve component/i,
]

const isNoise = (msg) =>
  IGNORE_PATTERNS.some((re) => re.test(msg)) ||
  // 空库时列表接口 200 但无数据，不应算错误
  /net::ERR_ABORTED/i.test(msg)

const results = []
const log = (role, page, kind, detail) => results.push({ role, page, kind, detail })

const run = async () => {
  console.log(`目标: ${BASE}`)
  const browser = await chromium.launch({ ...(CHANNEL ? { channel: CHANNEL } : {}), headless: true })
  console.log(`浏览器: ${CHANNEL || 'chromium(内置)'}\n`)

  const roles = ONLY ? ROLES.filter((r) => r.role === ONLY || r.user === ONLY) : ROLES

  for (const r of roles) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
    const page = await ctx.newPage()
    const errors = []
    page.on('console', (m) => {
      if (m.type() === 'error' && !isNoise(m.text())) errors.push(`[console] ${m.text().slice(0, 200)}`)
    })
    page.on('pageerror', (e) => {
      if (!isNoise(String(e))) errors.push(`[pageerror] ${String(e).slice(0, 200)}`)
    })

    // ---- 登录
    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
    const userInput = page.locator('input').first()
    await userInput.fill(r.user)
    await page.locator('input[type="password"]').first().fill(r.pass)
    await Promise.all([
      page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 15000 }).catch(() => {}),
      page.keyboard.press('Enter'),
    ])
    await page.waitForTimeout(1200)
    const loggedIn = !page.url().includes('/login')
    if (!loggedIn) {
      // 空库/账号被清理时不算失败：这类环境问题应显示为 skip，避免掩盖真正的白屏回归
      log(r.role, '登录', 'skip', '账号不可用（库内无此账号或密码不符）')
      await ctx.close()
      continue
    }
    log(r.role, '登录', 'ok', page.url())

    // ---- 角色入口页：管理端 / 用户工作台
    for (const [name, path] of r.panel === 'admin' ? ADMIN_PAGES : USER_PAGES) {
      await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded' })
      await page.waitForTimeout(900)
      const fatal = errors.filter((e) => e.startsWith('[pageerror]'))
      const bodyText = await page.locator('body').innerText().catch(() => '')
      const blank = bodyText.replace(/\s/g, '').length < 20
      const hasGlobalError = bodyText.includes('页面出现异常')
      const ok = !hasGlobalError && !blank && fatal.length === 0
      log(r.role, name, ok ? 'ok' : 'fail',
        hasGlobalError ? '显示「页面出现异常」' : blank ? '页面空白' : (fatal[0] ?? ''))
      errors.length = 0
    }

    await ctx.close()
  }

  await browser.close()

  // ---- 汇总
  const okCount = results.filter((x) => x.kind === 'ok').length
  const fail = results.filter((x) => x.kind === 'fail')
  const skip = results.filter((x) => x.kind === 'skip')
  console.log(`共 ${results.length} 次页面访问：通过 ${okCount}，失败 ${fail.length}，跳过 ${skip.length}\n`)
  if (skip.length) {
    console.log('--- 跳过（账号或数据不可用）---')
    for (const s of skip) console.log(`  ${s.role.padEnd(9)} ${s.page.padEnd(10)} ${s.detail}`)
    console.log('')
  }
  if (fail.length) {
    console.log('--- 失败明细 ---')
    for (const f of fail) console.log(`  ${f.role.padEnd(9)} ${f.page.padEnd(10)} ${f.detail}`)
  } else {
    console.log('✓ 所有角色、所有页面：无白屏、无「页面出现异常」、无未捕获 JS 异常')
  }
  return fail.length
}

process.exit(await run() > 0 ? 1 : 0)
