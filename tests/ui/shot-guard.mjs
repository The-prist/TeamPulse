// 抓「进度中」和「已拉黑」两张图
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\guard'
fs.mkdirSync(DIR, { recursive: true })

const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.evaluate(() => sessionStorage.clear())
await page.fill('.form-card input[autocomplete="username"]', 'progress_probe')

for (let i = 1; i <= 7; i++) {
  await page.fill('.form-card input[type="password"]', `p-${i}-${Date.now()}`)
  await page.locator('.submit-btn').click({ force: true })
  await page.waitForTimeout(780)
  if (i === 7) break
  const close = page.locator('.captcha-dialog .el-dialog__headerbtn').first()
  if (await close.count()) await close.click()
  await page.waitForTimeout(320)
}
await page.screenshot({ path: `${DIR}\\progress.png` })
console.log('  进度图已保存（应为 7/10 + 验证码弹窗）')

for (let i = 8; i <= 12; i++) {
  const blk = await page.evaluate(() => Boolean(document.querySelector('.block-tip')))
  if (blk) break
  const close = page.locator('.captcha-dialog .el-dialog__headerbtn').first()
  if (await close.count()) await close.click()
  await page.waitForTimeout(300)
  await page.fill('.form-card input[type="password"]', `p-${i}-${Date.now()}`)
  await page.locator('.submit-btn').click({ force: true })
  await page.waitForTimeout(780)
}
await page.waitForTimeout(500)
await page.screenshot({ path: `${DIR}\\blocked2.png` })
console.log('  拉黑图已保存')
await b.close()
