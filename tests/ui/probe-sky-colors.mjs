import { chromium } from 'playwright-core'
const BASE = process.env.BASE
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.evaluate(() => window.__tpLoginSky.setHour(9))
for (const bucket of ['auto', 'rain', 'overcast', 'fog']) {
  await page.evaluate((x) => window.__tpLoginSky.setWeather(x), bucket)
  await page.waitForTimeout(500)
  const sky = await page.evaluate(() => window.__tpLoginSky.sky)
  console.log(`  ${bucket.padEnd(9)}`, JSON.stringify(sky))
}
await b.close()