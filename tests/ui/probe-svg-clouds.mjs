import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\svgclouds'
fs.mkdirSync(DIR, { recursive: true })
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()) })
page.on('requestfailed', (r) => errs.push('reqfail ' + r.url()))
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1600)

const st = await page.evaluate(() => {
  const k = window.__tpLoginSky
  const p = document.querySelector('.login-panel').getBoundingClientRect()
  const list = k.clouds
  return {
    count: list.length,
    visible: list.filter((c) => !(c.x > p.left && c.x < p.right && c.y > p.top && c.y < p.bottom)).length,
    visibleFactor: +k.cloudsVisible.toFixed(2),
    sample: list.slice(0, 3),
  }
})
console.log('  云:', JSON.stringify(st))

await page.locator('.dc-collapse').click()
await page.waitForTimeout(200)
for (const h of [9, 15]) {
  await page.evaluate((hh) => window.__tpLoginSky.setHour(hh), h)
  await page.waitForTimeout(800)
  await page.screenshot({ path: `${DIR}\\${h}.png` })
  console.log(`  ${h}:00 已截图`)
}
console.log(errs.length ? `  问题: ${errs.slice(0, 3).join(' | ')}` : '  无报错、无资源加载失败')
await b.close()