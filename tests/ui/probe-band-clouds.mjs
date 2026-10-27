import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\bandclouds'
fs.mkdirSync(DIR, { recursive: true })
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()) })
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.evaluate(() => window.__tpLoginSky.setHour(9))
await page.waitForTimeout(700)

const info = await page.evaluate(() => {
  const k = window.__tpLoginSky
  const imgs = [...document.querySelectorAll('.cloud-layer img')]
  return {
    count: imgs.length,
    firstW: imgs[0]?.style.width,
    firstTransform: imgs[0]?.style.transform,
    firstSrcIdx: k.clouds[0]?.imgIndex,
    clouds: k.clouds.map((c) => ({ img: c.imgIndex, x: c.x, y: c.y, w: c.w })),
    viewport: { w: innerWidth, h: innerHeight },
  }
})
console.log('  云数量:', info.count, ' 第一个(底层)=img', info.firstSrcIdx, ' 宽', info.firstW)
console.log('  云带宽度 vs 视口:', info.clouds[0].w, 'vs', info.viewport.w, info.clouds[0].w > info.viewport.w ? '✓ 超出屏幕' : '✗ 未超')
for (const c of info.clouds) console.log(`    img${c.img}  x=${c.x} y=${c.y} w=${c.w}`)

await page.locator('.dc-collapse').click()
await page.waitForTimeout(300)
for (const h of [9, 15]) {
  await page.evaluate((hh) => window.__tpLoginSky.setHour(hh), h)
  await page.waitForTimeout(700)
  await page.screenshot({ path: `${DIR}\\${h}.png` })
}
console.log(errs.length ? `  错误: ${errs[0]}` : '  无报错')
await b.close()