// 复现用户场景：空表单点 12 次登录，观察 N/10 是否递增到拉黑
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\emptyform'
fs.mkdirSync(DIR, { recursive: true })

const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()

const st = () =>
  page.evaluate(() => ({
    guard: document.querySelector('.guard-tip')?.textContent?.replace(/\s+/g, ' ').trim().slice(0, 46) || null,
    block: document.querySelector('.block-tip')?.textContent?.replace(/\s+/g, ' ').trim().slice(0, 46) || null,
    dialog: Boolean(document.querySelector('.captcha-dialog')?.offsetParent),
    disabled: document.querySelector('.submit-btn')?.disabled,
  }))

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.evaluate(() => sessionStorage.clear())

console.log('  场景：什么都不填，直接点登录（和用户操作一致）')
for (let i = 1; i <= 12; i++) {
  await page.locator('.submit-btn').click({ force: true })
  await page.waitForTimeout(800)
  const s = await st()
  console.log(`  第 ${String(i).padStart(2)} 次 | ${s.guard || '—'} | 弹窗=${s.dialog ? 'Y' : 'N'} | 拉黑=${s.block ? 'Y' : 'N'}`)
  if (s.block) {
    console.log('  → 拉黑提示:', s.block, ' 按钮禁用:', s.disabled)
    break
  }
  if (s.dialog) {
    const close = page.locator('.captcha-dialog .el-dialog__headerbtn').first()
    if (await close.count()) await close.click()
    else await page.keyboard.press('Escape')
    await page.waitForTimeout(320)
  }
}
await page.screenshot({ path: `${DIR}\\result.png` })
await b.close()
