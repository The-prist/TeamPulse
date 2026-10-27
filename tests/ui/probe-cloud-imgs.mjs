import { chromium } from 'playwright-core'
const BASE = process.env.BASE
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
page.on('pageerror', (e) => console.log('  pageerror:', String(e).slice(0, 160)))
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.evaluate(() => window.__tpLoginSky.setHour(9))
await page.waitForTimeout(900)
const d = await page.evaluate(() => {
  const k = window.__tpLoginSky
  return { imgs: k.__cloudImages, clouds: k.clouds.slice(0, 2), vis: k.cloudsVisible }
})
console.log('  图片状态:', JSON.stringify(d.imgs, null, 1))
console.log('  云:', JSON.stringify(d.clouds), ' 可见度:', d.vis)
await b.close()