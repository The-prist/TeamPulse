// 截图：雨满水 → 切阴天（清空）
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\overlayfix'
fs.mkdirSync(DIR, { recursive: true })

const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1800)
await page.evaluate(() => window.__tpLoginSky.setHour(12))

await page.evaluate(() => {
  const k = window.__tpLoginSky
  k.setWeather('rain')
  k.setAccum(1)
})
await page.waitForTimeout(1500)
await page.screenshot({ path: `${DIR}\\1-rain-full.png` })
console.log('  雨满水截图完成')

await page.evaluate(() => window.__tpLoginSky.setWeather('overcast'))
await page.waitForTimeout(900)
await page.screenshot({ path: `${DIR}\\2-after-switch.png` })
console.log('  切阴天后截图完成')

await page.evaluate(() => window.__tpLoginSky.setWeather('auto'))
await b.close()
