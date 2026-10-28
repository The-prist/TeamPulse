// 验证：不解验证码、只重复点登录，也能把 IP 打到拉黑（10 次 → 10 分钟冷却）
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\cooldown'
fs.mkdirSync(DIR, { recursive: true })

const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()

let loginCalls = 0
page.on('request', (r) => {
  if (r.url().includes('/auth/login')) loginCalls++
})

const st = () =>
  page.evaluate(() => ({
    dialog: Boolean(document.querySelector('.captcha-dialog')?.offsetParent),
    blockTip: document.querySelector('.block-tip')?.textContent?.trim().slice(0, 46) || null,
    forgot: Boolean(document.querySelector('.forgot-tip')),
    btnDisabled: document.querySelector('.submit-btn')?.disabled,
  }))

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1300)
await page.evaluate(() => sessionStorage.clear())
await page.fill('.form-card input[autocomplete="username"]', 'cooldown_probe')

console.log('  每次点登录 → 关掉弹窗 → 再点，全程不解验证码')
for (let i = 1; i <= 16; i++) {
  const before = loginCalls
  await page.fill('.form-card input[type="password"]', `bad-${i}-${Date.now()}`)
  await page.locator('.submit-btn').click({ force: true })
  await page.waitForTimeout(750)
  const s = await st()
  const sent = loginCalls - before
  console.log(
    `  第 ${String(i).padStart(2)} 次: 请求=${sent} 弹窗=${s.dialog ? 'Y' : 'N'} 拉黑=${s.blockTip ? 'Y' : 'N'} 按钮禁用=${s.btnDisabled}`,
  )
  if (s.blockTip) {
    console.log('  → 拉黑提示:', s.blockTip)
    break
  }
  // 关掉验证码弹窗，模拟「用户不想验、反复点登录」
  if (s.dialog) {
    await page.keyboard.press('Escape')
    await page.waitForTimeout(350)
  }
}
const fin = await st()
await page.screenshot({ path: `${DIR}\\blocked.png` })
console.log('  最终状态:', JSON.stringify(fin))
console.log('  总登录请求数:', loginCalls)

await b.close()
