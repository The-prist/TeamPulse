// 验证可见进度：每次失败后界面上的「失败 N/10」是否递增，到 10 是否拉黑
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\guard'
fs.mkdirSync(DIR, { recursive: true })

const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()

const st = () =>
  page.evaluate(() => ({
    guard: document.querySelector('.guard-tip')?.textContent?.replace(/\s+/g, ' ').trim().slice(0, 60) || null,
    block: document.querySelector('.block-tip')?.textContent?.replace(/\s+/g, ' ').trim().slice(0, 50) || null,
    dialog: Boolean(document.querySelector('.captcha-dialog')?.offsetParent),
    disabled: document.querySelector('.submit-btn')?.disabled,
  }))

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.evaluate(() => sessionStorage.clear())
await page.fill('.form-card input[autocomplete="username"]', 'guard_ui_probe')

console.log('  点登录 → 关弹窗 → 再点，观察「失败 N/10」')
for (let i = 1; i <= 13; i++) {
  await page.fill('.form-card input[type="password"]', `x-${i}-${Date.now()}`)
  await page.locator('.submit-btn').click({ force: true })
  await page.waitForTimeout(800)
  const s = await st()
  console.log(`  第 ${String(i).padStart(2)} 次 | 失败进度: ${s.guard || '—'} | 弹窗=${s.dialog ? 'Y' : 'N'} | 拉黑=${s.block ? 'Y' : 'N'}`)
  if (s.block) {
    console.log('  → 拉黑:', s.block, ' 按钮禁用:', s.disabled)
    break
  }
  if (s.dialog) {
    // 用右上角 X 关闭（更接近真实用户操作）
    const close = page.locator('.captcha-dialog .el-dialog__headerbtn').first()
    if (await close.count()) await close.click()
    else await page.keyboard.press('Escape')
    await page.waitForTimeout(350)
  }
}
await page.screenshot({ path: `${DIR}\\blocked.png` })
await b.close()
