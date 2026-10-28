// 验证：①登录失败按钮变红+抖动 ②登录成功圆形转场 ③登录后主题按当前时间 ④左下角切换按钮仍在
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\logineffects'
fs.mkdirSync(DIR, { recursive: true })

const b = await chromium.launch({ channel: 'msedge', headless: true })
const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 200)))
page.on('console', (m) => {
  if (m.type() === 'error') errs.push(m.text().slice(0, 200))
})

const hour = new Date().getHours()
const expectTheme = hour >= 6 && hour < 18 ? 'light' : 'dark'
console.log(`  当前本地时间 ${hour} 点 → 期望主题 ${expectTheme}`)

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1800)

// ---------- ① 登录失败 ----------
console.log('--- ① 错误密码 ---')
await page.fill('.form-card input[autocomplete="username"]', 'admin')
await page.fill('.form-card input[type="password"]', 'wrong-password-123')
await page.locator('.submit-btn').click()
await page.waitForTimeout(350)
const failState = await page.evaluate(() => {
  const btn = document.querySelector('.submit-btn')
  const cs = getComputedStyle(btn)
  return {
    hasClass: btn.classList.contains('is-failed'),
    bg: cs.backgroundColor,
    animation: cs.animationName,
    transform: cs.transform,
  }
})
console.log('  按钮状态:', JSON.stringify(failState))
await page.screenshot({ path: `${DIR}\\1-login-failed.png` })
await page.waitForTimeout(900)
const recovered = await page.evaluate(() => {
  const btn = document.querySelector('.submit-btn')
  return { hasClass: btn.classList.contains('is-failed'), bg: getComputedStyle(btn).backgroundColor }
})
console.log('  560ms 后恢复:', JSON.stringify(recovered))

// ---------- ② 登录成功转场 ----------
console.log('--- ② 正确密码 + 圆形转场 ---')
await page.fill('.form-card input[type="password"]', 'adminpassword')
await page.locator('.submit-btn').click()
// 转场过程中采样覆盖圆
const samples = []
for (let i = 0; i < 10; i++) {
  await page.waitForTimeout(90)
  const s = await page.evaluate(() => {
    const el = document.querySelector('.tp-login-circle')
    if (!el) return { present: false }
    const cs = getComputedStyle(el)
    return { present: true, clip: cs.clipPath, bg: cs.backgroundColor, z: cs.zIndex }
  })
  samples.push(s)
}
console.log('  覆盖圆采样:')
for (const [i, s] of samples.entries()) {
  console.log(`    ${(i * 90).toString().padStart(3)}ms  ${s.present ? `clip=${s.clip} bg=${s.bg} z=${s.z}` : '（已移除）'}`)
}
await page.waitForTimeout(1500)
await page.screenshot({ path: `${DIR}\\2-after-login.png` })

const after = await page.evaluate(() => ({
  url: location.pathname,
  dark: document.documentElement.classList.contains('dark'),
  toggle: Boolean(document.querySelector('.theme-toggle, [aria-label*="夜间"], [aria-label*="白天"]')),
  circleGone: !document.querySelector('.tp-login-circle'),
}))
console.log('  登录后:', JSON.stringify(after))
console.log('  主题判定:', after.dark ? 'dark' : 'light', expectTheme === (after.dark ? 'dark' : 'light') ? '✓ 符合当前时间' : '✗ 与时间不符')

// ---------- ③ 左下角切换按钮仍可用 ----------
console.log('--- ③ 左下角昼夜切换 ---')
const toggleBtn = page.locator('.theme-toggle').first()
if (await toggleBtn.count()) {
  const beforeTheme = await page.evaluate(() => (document.documentElement.classList.contains('dark') ? 'dark' : 'light'))
  await toggleBtn.click({ force: true })
  await page.waitForTimeout(1600)
  const afterTheme = await page.evaluate(() => (document.documentElement.classList.contains('dark') ? 'dark' : 'light'))
  const manual = await page.evaluate(() => localStorage.getItem('tp_theme_manual'))
  console.log(`  ${beforeTheme} → ${afterTheme}（点击切换 ${beforeTheme !== afterTheme ? '生效 ✓' : '未生效 ✗'}），手动标记=${manual}`)
  await page.screenshot({ path: `${DIR}\\3-toggled.png` })
} else {
  console.log('  ✗ 未找到切换按钮')
}

await b.close()
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
