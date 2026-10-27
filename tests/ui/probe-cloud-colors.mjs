import { chromium } from 'playwright-core'
const BASE = process.env.BASE
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)

async function colors(name, hour, bucket) {
  await page.evaluate(([h, bb]) => {
    window.__tpLoginSky.setHour(h)
    window.__tpLoginSky.setWeather(bb)
  }, [hour, bucket])
  await page.waitForTimeout(700)
  const res = await page.evaluate(() => {
    const imgs = [...document.querySelectorAll('.cloud-layer img')]
    if (!imgs.length) return { fills: [], count: 0 }
    const src = decodeURIComponent(imgs[0].src.replace('data:image/svg+xml;charset=utf-8,', ''))
    const fills = [...src.matchAll(/fill="(#[0-9a-fA-F]{6})"/g)].map((m) => m[1])
    return { fills, count: imgs.length }
  })
  console.log(`  ${name.padEnd(16)} 云=${res.count} 颜色=${res.fills.join(' / ')}`)
}
await colors('12:00 snow', 12, 'snow')
await colors('12:00 rain', 12, 'rain')
await colors('12:00 overcast', 12, 'overcast')
await colors('17:15 partly', 17.15, 'partly')
await colors('17:15 overcast', 17.15, 'overcast')
await colors('22:00 rain', 22, 'rain')
await colors('22:00 partly', 22, 'partly')
await b.close()