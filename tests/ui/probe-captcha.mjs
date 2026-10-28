// 验证：空表单变红 / 错误密码变红且无抖动 / 第 5 次失败弹验证码 / 拖动通过后能登录
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

const btnState = () =>
  page.evaluate(() => {
    const btn = document.querySelector('.submit-btn')
    const cs = getComputedStyle(btn)
    return { failed: btn.classList.contains('is-failed'), bg: cs.backgroundColor, animation: cs.animationName }
  })

// ---------- ① 什么都不填直接点 ----------
console.log('--- ① 空表单点击 ---')
await page.locator('.submit-btn').click()
await page.waitForTimeout(250)
let st = await btnState()
console.log('  按钮:', JSON.stringify(st), st.failed ? '✓ 变红' : '✗ 没变红')
console.log('  无抖动:', st.animation === 'none' ? '✓' : `✗ 有动画 ${st.animation}`)
await page.screenshot({ path: `${DIR}\\1-empty-click.png` })
await page.waitForTimeout(700)
console.log('  600ms 后复原:', JSON.stringify(await btnState()))

// ---------- ② 连错 5 次 ----------
console.log('--- ② 连续错误密码 5 次 ---')
await page.fill('.form-card input[autocomplete="username"]', 'admin')
for (let i = 1; i <= 5; i++) {
  await page.fill('.form-card input[type="password"]', `wrong-${i}-pass`)
  await page.locator('.submit-btn').click()
  await page.waitForTimeout(900)
  const failCount = await page.evaluate(() => Number(sessionStorage.getItem('tp_login_fail_count') || 0))
  const dialog = await page.evaluate(() => Boolean(document.querySelector('.captcha-dialog')))
  const inner = dialog ? await page.evaluate(() => {
    const d = document.querySelector('.captcha-dialog')
    return d ? d.innerText.replace(/\n+/g, ' | ').slice(0, 120) : ''
  }) : ''
  console.log(`  第 ${i} 次：失败计数=${failCount} 验证码弹窗=${dialog ? '✓' : '—'} ${inner ? `| ${inner}` : ''}`)
}
await page.waitForTimeout(600)
const captchaVisible = await page.evaluate(() => {
  const d = document.querySelector('.captcha-dialog')
  return d ? { open: true, hasSlider: Boolean(document.querySelector('.cap-track')), text: d.innerText.replace(/\n+/g, ' | ').slice(0, 100) } : { open: false }
})
console.log('  最终:', JSON.stringify(captchaVisible))
await page.screenshot({ path: `${DIR}\\2-captcha.png` })

// ---------- ③ 拖动滑块通过 ----------
console.log('--- ③ 拖动滑块通过验证 ---')
const target = await page.evaluate(() => (window.__tpCaptchaTarget ? window.__tpCaptchaTarget() : -1))
console.log('  正确偏移:', target)
const track = await page.locator('.cap-track').boundingBox()
if (track && target >= 0) {
  await page.mouse.move(track.x + 22, track.y + track.height / 2)
  await page.mouse.down()
  // 分几段移动，模拟人类拖动
  for (const step of [0.3, 0.6, 0.85, 1]) {
    await page.mouse.move(track.x + 24 + (target + 24 - 24) * step, track.y + track.height / 2)
    await page.waitForTimeout(60)
  }
  await page.mouse.move(track.x + 24 + target, track.y + track.height / 2)
  await page.waitForTimeout(80)
  await page.mouse.up()
  await page.waitForTimeout(900)
}
const afterCaptcha = await page.evaluate(() => ({
  dialog: Boolean(document.querySelector('.captcha-dialog')),
  failCount: Number(sessionStorage.getItem('tp_login_fail_count') || 0),
}))
console.log('  验证后:', JSON.stringify(afterCaptcha))
await page.screenshot({ path: `${DIR}\\3-verified.png` })

// ---------- ④ 通过后能正常登录 ----------
console.log('--- ④ 验证后登录 ---')
await page.fill('.form-card input[type="password"]', 'adminpassword')
await page.locator('.submit-btn').click()
await page.waitForTimeout(2600)
const done = await page.evaluate(() => ({
  path: location.pathname,
  failCount: Number(sessionStorage.getItem('tp_login_fail_count') || 0),
}))
console.log('  结果:', JSON.stringify(done), done.path !== '/login' ? '✓ 已进入系统' : '✗ 仍在登录页')

await b.close()
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
