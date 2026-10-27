import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\final-weather'
fs.mkdirSync(DIR, { recursive: true })
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 200)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text().slice(0, 200)) })
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(2000)
await page.evaluate(() => {
  const s = document.querySelectorAll('.dc-select')[1]
  if (s) { s.value = 'auto'; s.dispatchEvent(new Event('change')) }
})

async function shot(name, hour, bucket) {
  await page.evaluate(([h, bb]) => {
    window.__tpLoginSky.setHour(h)
    window.__tpLoginSky.setWeather(bb)
  }, [hour, bucket])
  await page.waitForTimeout(1100)
  await page.screenshot({ path: `${DIR}\\${name}.png` })
  const st = await page.evaluate(() => ({ c: window.__tpLoginSky.weather.cloudCount, s: window.__tpLoginSky.sky.top }))
  console.log(`  ${name.padEnd(16)} 云=${st.c} 天色=${st.s}`)
}

await shot('night-partly', 22, 'partly')
await shot('night-rain', 22, 'rain')
await shot('dusk-fire', 17.15, 'partly')
await shot('dusk-overcast', 17.15, 'overcast')
await shot('day-rain', 13, 'rain')
await shot('day-fog', 13, 'fog')
await shot('day-snow', 13, 'snow')
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
await b.close()