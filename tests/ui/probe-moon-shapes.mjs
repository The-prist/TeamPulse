import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\moon2'
fs.mkdirSync(DIR, { recursive: true })
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
await page.addInitScript(() => localStorage.setItem('tp_theme', 'dark'))
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.evaluate(() => window.__tpLoginSky.setHour(22))
await page.waitForTimeout(300)

// 放在右侧留白里（卡片右边缘 1190），避免被卡片遮住
const POS = [0.93, 0.17]
const CLIP = { x: 1240, y: 60, width: 200, height: 200 }
const shapes = [
  ['1-crescent-waxing', 0.10],
  ['2-quarter-waxing', 0.25],
  ['3-gibbous', 0.38],
  ['4-full', 0.50],
  ['5-quarter-waning', 0.75],
  ['6-crescent-waning', 0.90],
]
for (const [name, f] of shapes) {
  await page.evaluate(([frac, x, y]) => window.__tpLoginSky.__setMoonPhase(frac, x, y), [f, POS[0], POS[1]])
  await page.waitForTimeout(140)
  await page.screenshot({ path: `${DIR}\\${name}.png`, clip: CLIP })
  console.log(`  ${name}  fraction=${f}`)
}
await b.close()