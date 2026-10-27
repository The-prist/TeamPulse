import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\windclouds'
fs.mkdirSync(DIR, { recursive: true })
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()) })
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)

// 滚动条检查
const scroll = await page.evaluate(() => ({
  scrollH: document.documentElement.scrollHeight,
  clientH: document.documentElement.clientHeight,
  scrollW: document.documentElement.scrollWidth,
  clientW: document.documentElement.clientWidth,
  vertical: document.documentElement.scrollHeight > document.documentElement.clientHeight,
  horizontal: document.documentElement.scrollWidth > document.documentElement.clientWidth,
}))
console.log('  滚动条:', JSON.stringify(scroll))

await page.evaluate(() => window.__tpLoginSky.setHour(9))
await page.waitForTimeout(500)

// 记录 0s / 4s 的云位置，确认向右移动
const snap = () => page.evaluate(() => window.__tpLoginSky.clouds.map((c) => ({ img: c.imgIndex, x: Math.round(c.x), w: c.w })))
const s0 = await snap()
await page.waitForTimeout(4000)
const s1 = await snap()
console.log('  云宽（前 3 个）:', s0.slice(0, 3).map((c) => c.w).join(', '))
console.log('  4 秒位移:')
for (let i = 0; i < s0.length; i++) {
  if (!s1[i]) continue
  console.log(`    #${i} img${s0[i].img} x ${s0[i].x} → ${s1[i].x}  位移 ${s1[i].x - s0[i].x}px`)
}
await page.locator('.dc-collapse').click()
await page.waitForTimeout(300)
await page.screenshot({ path: `${DIR}\\9.png` })
console.log(errs.length ? `  错误: ${errs[0]}` : '  无报错')
await b.close()