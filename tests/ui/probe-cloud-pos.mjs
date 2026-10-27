import { chromium } from 'playwright-core'
const BASE = process.env.BASE
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.evaluate(() => window.__tpLoginSky.setHour(9))
await page.waitForTimeout(600)
const d = await page.evaluate(() => {
  const k = window.__tpLoginSky
  const p = document.querySelector('.login-panel').getBoundingClientRect()
  return {
    panel: { l: Math.round(p.left), r: Math.round(p.right), t: Math.round(p.top), b: Math.round(p.bottom) },
    clouds: k.clouds.map((c, i) => ({ i, x: c.x, y: c.y, w: c.w, img: c.imgIndex })),
    hidden: k.clouds.filter((c) => c.x > p.left && c.x < p.right && c.y > p.top && c.y < p.bottom).length,
  }
})
console.log('  卡片:', JSON.stringify(d.panel))
console.log('  云位置:')
for (const c of d.clouds) {
  const inCard = c.x > d.panel.l && c.x < d.panel.r && c.y > d.panel.t && c.y < d.panel.b
  console.log(`    #${c.i} img=${c.img} (${c.x},${c.y}) w=${c.w}  ${inCard ? '✗ 在卡片后面' : '✓ 可见'}`)
}
console.log('  被卡片挡住:', d.hidden, '/', d.clouds.length)
await b.close()