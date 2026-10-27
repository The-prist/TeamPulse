import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\realism2'
fs.mkdirSync(DIR, { recursive: true })
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 200)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text().slice(0, 200)) })
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(2000)

await page.evaluate(() => {
  const sel = document.querySelectorAll('.dc-select')[1]
  sel.value = 'auto'
  sel.dispatchEvent(new Event('change'))
})

async function shot(name, hour, bucket) {
  await page.evaluate(([h, b2]) => {
    window.__tpLoginSky.setHour(h)
    window.__tpLoginSky.setWeather(b2)
  }, [hour, bucket])
  await page.waitForTimeout(900)
  const st = await page.evaluate(() => {
    const k = window.__tpLoginSky
    return { clouds: k.weather.cloudCount, sky: k.sky, sa: +k.sun.alpha.toFixed(3), stars: +k.starsVisible.toFixed(2) }
  })
  await page.screenshot({ path: `${DIR}\\${name}.png` })
  console.log(`  ${name.padEnd(14)} 云=${String(st.clouds).padStart(2)} 太阳α=${st.sa} 星=${st.stars} 天色=${st.sky.top}`)
}

console.log('--- 夜间 ---')
await shot('night-clear', 22, 'clear')
await shot('night-partly', 22, 'partly')
await shot('night-rain', 22, 'rain')
console.log('--- 傍晚 17:10（太阳刚落地平线，火烧云窗口）---')
await shot('dusk-partly', 17.15, 'partly')
await shot('dusk-overcast', 17.15, 'overcast')
console.log('--- 白天 ---')
await shot('day-rain', 13, 'rain')
await shot('day-overcast', 13, 'overcast')
await shot('day-fog', 13, 'fog')
await shot('day-snow', 13, 'snow')
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
await b.close()