import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\sky3'
fs.mkdirSync(DIR, { recursive: true })
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()) })
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)

const at = async (h) => {
  await page.evaluate((hh) => window.__tpLoginSky.setHour(hh), h)
  await page.waitForTimeout(260)
  return page.evaluate(() => {
    const k = window.__tpLoginSky
    const p = document.querySelector('.login-panel').getBoundingClientRect()
    const inCard = (x, y) => x > p.left && x < p.right && y > p.top && y < p.bottom
    return {
      hour: k.hour,
      sun: { alt: +k.sun.altitude.toFixed(1), az: +k.sun.azimuth.toFixed(0), x: Math.round(k.sun.x), y: Math.round(k.sun.y), a: +k.sun.alpha.toFixed(2), inCard: inCard(k.sun.x, k.sun.y) },
      moon: { alt: +k.moon.altitude.toFixed(1), az: +k.moon.azimuth.toFixed(0), x: Math.round(k.moon.x), y: Math.round(k.moon.y), a: +k.moon.alpha.toFixed(2), frac: +k.moon.fraction.toFixed(2), inCard: inCard(k.moon.x, k.moon.y) },
    }
  })
}

console.log('  时刻   太阳(高度/方位/x,y)  被挡  月亮(高度/方位/x,y) 被挡  月亮可见')
for (const h of [6, 7, 9, 12, 15, 17, 18, 21, 0]) {
  const s = await at(h)
  console.log(
    `  ${String(h).padStart(2)}:00  ${String(s.sun.alt).padStart(6)}°/${String(s.sun.az).padStart(3)}° (${String(s.sun.x).padStart(4)},${String(s.sun.y).padStart(3)}) ${s.sun.a > 0.05 ? (s.sun.inCard ? '✗' : '✓') : '—'}   ${String(s.moon.alt).padStart(6)}°/${String(s.moon.az).padStart(3)}° (${String(s.moon.x).padStart(4)},${String(s.moon.y).padStart(3)}) ${s.moon.a > 0.05 ? (s.moon.inCard ? '✗' : '✓') : '—'}  ${s.moon.a}`,
  )
  if ([9, 12].includes(h)) await page.screenshot({ path: `${DIR}\\${String(h).padStart(2, '0')}.png` })
}

// 云的特写（右侧留白区，9:00 云多）
await page.evaluate(() => window.__tpLoginSky.setHour(9))
await page.waitForTimeout(400)
await page.screenshot({ path: `${DIR}\\clouds-closeup.png`, clip: { x: 980, y: 0, width: 460, height: 460 } })
await page.screenshot({ path: `${DIR}\\clouds-full.png` })
console.log(errs.length ? `  错误: ${errs[0]}` : '  控制台无报错')
await b.close()