import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\svgclouds2'
fs.mkdirSync(DIR, { recursive: true })
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()) })
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.evaluate(() => window.__tpLoginSky.setHour(9))
await page.waitForTimeout(900)

const st = await page.evaluate(() => {
  const imgs = [...document.querySelectorAll('.cloud-layer img')]
  return {
    count: imgs.length,
    loaded: imgs.filter((i) => i.complete && i.naturalWidth > 0).length,
    first: imgs[0] ? { nw: imgs[0].naturalWidth, nh: imgs[0].naturalHeight, styleW: imgs[0].style.width, opacity: imgs[0].style.opacity, transform: imgs[0].style.transform } : null,
    visibleFactor: window.__tpLoginSky.cloudsVisible,
  }
})
console.log('  云图片:', JSON.stringify(st, null, 1))

await page.locator('.dc-collapse').click()
await page.waitForTimeout(300)
for (const h of [9, 15]) {
  await page.evaluate((hh) => window.__tpLoginSky.setHour(hh), h)
  await page.waitForTimeout(700)
  await page.screenshot({ path: `${DIR}\\${h}.png` })
  console.log(`  ${h}:00 已截图`)
}
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' | ')}` : '  无报错')
await b.close()