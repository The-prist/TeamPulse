import { chromium } from 'playwright-core'
const BASE = process.env.BASE
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
page.on('pageerror', (e) => console.log('  pageerror:', String(e).slice(0, 200)))
page.on('console', (m) => { if (m.type() === 'error') console.log('  console:', m.text().slice(0, 200)) })
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.evaluate(() => window.__tpLoginSky.setHour(9))
await page.waitForTimeout(900)
const d = await page.evaluate(() => {
  const k = window.__tpLoginSky
  const canvas = document.querySelector('canvas.login-sky')
  return {
    cloudsVisible: k.cloudsVisible,
    sunAlt: k.sun.altitude,
    clouds: k.clouds.length,
    sample: k.clouds[0],
    // 直接看画布上有没有白色像素
    whitePixels: (() => {
      const c = canvas
      const g = c.getContext('2d')
      const { data } = g.getImageData(0, 0, c.width, c.height)
      let n = 0
      for (let i = 0; i < data.length; i += 4) {
        if (data[i] > 248 && data[i + 1] > 248 && data[i + 2] > 248) n++
      }
      return n
    })(),
  }
})
console.log('  ', JSON.stringify(d))
await b.close()