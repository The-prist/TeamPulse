import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()) })
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
// 折叠调试卡片，露出完整天空
await page.locator('.dc-collapse').click()
await page.waitForTimeout(200)
for (const h of [9, 12, 17]) {
  await page.evaluate((hh) => window.__tpLoginSky.setHour(hh), h)
  await page.waitForTimeout(700)
  await page.screenshot({ path: `E:\\codestorge\\.run-logs\\sky3\\final-${h}.png` })
  console.log(`  ${h}:00 已截图`)
}
await b.close()
console.log(errs.length ? `  错误: ${errs[0]}` : '  控制台无报错')