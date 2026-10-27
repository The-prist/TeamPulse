import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\noclouds'
fs.mkdirSync(DIR, { recursive: true })
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()) })
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)

const probe = await page.evaluate(() => {
  const k = window.__tpLoginSky
  return {
    hasCloudsKey: 'clouds' in k,
    scene: k.scene, hour: +k.hour.toFixed(2),
    sun: { alt: +k.sun.altitude.toFixed(1), x: Math.round(k.sun.x), y: Math.round(k.sun.y) },
    moon: { alt: +k.moon.altitude.toFixed(1), frac: +k.moon.fraction.toFixed(2), alpha: +k.moon.alpha.toFixed(2) },
    starsVisible: +k.starsVisible.toFixed(2),
  }
})
console.log('  调试句柄:', JSON.stringify(probe))
console.log(`  云相关键已移除: ${probe.hasCloudsKey ? '✗ 仍在' : '✓'}`)

await page.locator('.dc-collapse').click()
await page.waitForTimeout(200)
for (const h of [9, 12, 18, 22]) {
  await page.evaluate((hh) => window.__tpLoginSky.setHour(hh), h)
  await page.waitForTimeout(650)
  await page.screenshot({ path: `${DIR}\\${h}.png` })
  console.log(`  ${h}:00 已截图`)
}
console.log(errs.length ? `  错误: ${errs[0]}` : '  控制台无报错')
await b.close()