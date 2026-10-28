// 验证：空表单变红 / 无抖动 / 第 5 次失败弹验证码 / 拖动通过后能登录
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\captcha'
fs.mkdirSync(DIR, { recursive: true })

const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 200)))
page.on('console', (m) => {
  if (m.type() === 'error') errs.push(m.text().slice(0, 200))
})

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1600)
await page.evaluate(() => sessionStorage.removeItem('tp_login_fail_count'))
await page.waitForTimeout(200)

const btnState = () =>
  page.evaluate(() => {
    const btn = document.querySelector('.submit-btn')
    const cs = getComputedStyle(btn)
    return { failed: btn.classList.contains('is-failed'), bg: cs.backgroundColor, animation: cs.animationName }
  })
const failCount = () => page.evaluate(() => Number(sessionStorage.getItem('tp_login_fail_count') || 0))
const dialogOpen = () => page.evaluate(() => Boolean(document.querySelector('.captcha-dialog')))

// ---------- ① 空表单直接点 ----------
console.log('--- ① 空表单点击 ---')
await page.locator('.submit-btn').click()
await page.waitForTimeout(230)
const st1 = await btnState()
console.log('  按钮:', JSON.stringify(st1))
console.log('  变红:', st1.failed ? '✓' : '✗', '  无抖动:', st1.animation === 'none' ? '✓' : `✗(${st1.animation})`)
console.log('  失败计数:', await failCount())
await page.screenshot({ path: `${DIR}\\1-empty-click.png` })
await page.waitForTimeout(700)
const st1b = await btnState()
console.log('  600ms 后复原:', st1b.failed === false ? '✓' : '✗', st1b.bg)

// ---------- ② 连错直到弹验证码 ----------
console.log('--- ② 连续错误密码 ---')
await page.fill('.form-card input[autocomplete="username"]', 'admin')
let appeared = false
for (let i = 1; i <= 8 && !appeared; i++) {
  await page.fill('.form-card input[type="password"]', `wrong-${i}-pass`)
  await page.locator('.submit-btn').click()
  await page.waitForTimeout(950)
  appeared = await dialogOpen()
  console.log(`  第 ${i} 次错误：计数=${await failCount()} 验证码=${appeared ? '✓ 弹出' : '—'}`)
}
const dlgText = appeared
  ? await page.evaluate(() => document.querySelector('.captcha-dialog')?.innerText.replace(/\n+/g, ' | ').slice(0, 90))
  : ''
console.log('  弹窗内容:', dlgText)
console.log('  再点登录是否被挡:', await page.evaluate(() => {
  const btn = document.querySelector('.submit-btn')
  return Boolean(document.querySelector('.el-overlay')) && Boolean(btn)
}) ? '✓ 有遮罩挡住' : '?')
await page.screenshot({ path: `${DIR}\\2-captcha.png` })

// ---------- ③ 拖滑块通过 ----------
console.log('--- ③ 拖动滑块 ---')
const target = await page.evaluate(() => window.__tpCaptchaTarget?.() ?? -1)
console.log('  正确偏移:', target)
const track = await page.locator('.cap-track').boundingBox()
if (track && target >= 0) {
  await page.mouse.move(track.x + 22, track.y + track.height / 2)
  await page.mouse.down()
  const steps = [0.35, 0.65, 0.9]
  for (const s of steps) {
    await page.mouse.move(track.x + 22 + (target + 22 - 22) * s * 0.98, track.y + track.height / 2)
    await page.waitForTimeout(70)
  }
  await page.mouse.move(track.x + 22 + target, track.y + track.height / 2)
  await page.waitForTimeout(90)
  await page.mouse.up()
  await page.waitForTimeout(1000)
}
const afterCaptcha = { dialog: await dialogOpen(), failCount: await failCount() }
console.log('  验证后:', JSON.stringify(afterCaptcha))
await page.screenshot({ path: `${DIR}\\3-verified.png` })

// ---------- ④ 登录 ----------
console.log('--- ④ 验证后登录 ---')
await page.fill('.form-card input[type="password"]', 'adminpassword')
await page.locator('.submit-btn').click()
await page.waitForTimeout(2800)
const done = await page.evaluate(() => ({ path: location.pathname, fail: sessionStorage.getItem('tp_login_fail_count') }))
console.log('  结果:', JSON.stringify(done), done.path !== '/login' ? '✓ 已进入系统' : '✗ 仍在登录页')
await page.screenshot({ path: `${DIR}\\4-logged-in.png` })

await b.close()
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
