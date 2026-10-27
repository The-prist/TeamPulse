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
await page.locator('.dc-collapse').click()
await page.waitForTimeout(200)
for (const h of [9, 15]) {
  await page.evaluate((hh) => window.__tpLoginSky.setHour(hh), h)
  await page.waitForTimeout(800)
  const st = await page.evaluate(() => {
    const k = window.__tpLoginSky
    const p = document.querySelector('.login-panel').getBoundingClientRect()
    const visible = k.clouds.filter((c) => !(c.hx > p.left && c.hx < p.right && c.hy > p.top && c.hy < p.bottom))
    return { total: k.clouds.length, visible: visible.length }
  })
  console.log(`  ${h}:00 云 ${st.total} 朵，其中 ${st.visible} 朵不在卡片区域内`)
  await page.screenshot({ path: `E:\\codestorge\\.run-logs\\sky4\\${h}.png` })
}
console.log(errs.length ? `  错误: ${errs[0]}` : '  控制台无报错')
await b.close()