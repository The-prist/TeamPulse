import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\moon3'
fs.mkdirSync(DIR, { recursive: true })
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
await page.addInitScript(() => localStorage.setItem('tp_theme', 'dark'))
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.evaluate(() => window.__tpLoginSky.setHour(22))
await page.waitForTimeout(300)

// [名称, 点亮比例, 相位(0新月 .25上弦 .5满 .75下弦)]
const shapes = [
  ['1-蛾眉月', 0.12, 0.10],
  ['2-上弦月', 0.50, 0.25],
  ['3-盈凸月', 0.86, 0.40],
  ['4-满月', 1.00, 0.50],
  ['5-亏凸月', 0.86, 0.60],
  ['6-下弦月', 0.50, 0.75],
  ['7-残月', 0.12, 0.90],
]
const CLIP = { x: 1240, y: 60, width: 200, height: 200 }
for (const [name, frac, phase] of shapes) {
  await page.evaluate(([f, p]) => window.__tpLoginSky.__setMoonPhase(f, p, 0.93, 0.17), [frac, phase])
  await page.waitForTimeout(140)
  await page.screenshot({ path: `${DIR}\\${name}.png`, clip: CLIP })
  console.log(`  ${name}  fraction=${frac} phase=${phase}`)
}
await page.evaluate(() => window.__tpLoginSky.__clearMoonOverride())
await b.close()