import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\realism'
fs.mkdirSync(DIR, { recursive: true })
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 200)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text().slice(0, 200)) })
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(2000)

// 夜间 + 各天气：云必须还在
async function shot(name, hour, bucket, intensity = null) {
  await page.evaluate(([h, b2, i2]) => {
    const k = window.__tpLoginSky
    k.setHour(h)
    k.setWeather(b2)
    if (i2) document.querySelectorAll('.dc-select')[1].value = String(i2)
  }, [hour, bucket, intensity])
  if (intensity) {
    await page.evaluate((i2) => {
      const sel = document.querySelectorAll('.dc-select')[1]
      sel.value = String(i2)
      sel.dispatchEvent(new Event('change'))
    }, intensity)
  }
  await page.waitForTimeout(900)
  const st = await page.evaluate(() => {
    const k = window.__tpLoginSky
    return {
      clouds: k.weather.cloudCount,
      bucket: k.weather.bucket,
      intensity: k.weather.intensity,
      sky: k.sky,
      sunAlpha: +k.sun.alpha.toFixed(2),
      stars: +k.starsVisible.toFixed(2),
    }
  })
  await page.screenshot({ path: `${DIR}\\${name}.png` })
  console.log(`  ${name.padEnd(14)} 云=${String(st.clouds).padStart(2)} 强度=${st.intensity} 太阳α=${st.sunAlpha} 星=${st.stars} 天色=${st.sky.top}`)
  return st
}

console.log('--- 夜间（22:00）各天气：云应仍然存在 ---')
await shot('night-partly', 22, 'partly')
await shot('night-overcast', 22, 'overcast')
await shot('night-rain', 22, 'rain')
await shot('night-snow', 22, 'snow')
console.log('--- 雪 大中小（12:00）---')
await shot('snow-small', 12, 'snow', 1)
await shot('snow-mid', 12, 'snow', 2)
await shot('snow-large', 12, 'snow', 3)
console.log('--- 傍晚（17:40）：火烧云只在少云/多云 ---')
await shot('dusk-partly', 17.7, 'partly')
await shot('dusk-overcast', 17.7, 'overcast')
await shot('dusk-rain', 17.7, 'rain')
console.log('--- 白天：阴雨雪更暗、日月不可见 ---')
await shot('day-overcast', 13, 'overcast')
await shot('day-rain', 13, 'rain')

console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
await b.close()