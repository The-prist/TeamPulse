import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\realism2'
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1800)
await page.evaluate(() => window.__tpLoginSky.setHour(12))
for (const [name, lvl] of [['snow-small', 1], ['snow-large', 3]]) {
  await page.evaluate((l) => {
    const k = window.__tpLoginSky
    k.setWeather('snow')
    const sel = document.querySelectorAll('.dc-select')[1]
    sel.value = String(l)
    sel.dispatchEvent(new Event('change'))
  }, lvl)
  await page.waitForTimeout(1200)
  const st = await page.evaluate(() => ({ intensity: window.__tpLoginSky.weather.intensity, clouds: window.__tpLoginSky.weather.cloudCount }))
  await page.screenshot({ path: `${DIR}\\${name}.png` })
  console.log(`  ${name} 强度=${st.intensity} 云=${st.clouds}`)
}
await b.close()