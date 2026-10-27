import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()) })
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
// 找一个月亮在右侧留白且可见的时刻
const found = await page.evaluate(async () => {
  const k = window.__tpLoginSky
  const c = document.querySelector('canvas.login-sky')
  const panel = document.querySelector('.login-panel').getBoundingClientRect()
  const out = []
  for (let h = 0; h < 24; h += 0.5) {
    k.setHour(h)
    await new Promise((r) => setTimeout(r, 12))
    const m = k.moon
    const visible = m.alpha > 0.6 && m.altitude > 8
    const outsideCard = m.x < panel.left + 10 || m.x > panel.right - 10 || m.y < panel.top - 10
    if (visible && outsideCard) out.push({ h, x: Math.round(m.x), y: Math.round(m.y), alt: +m.altitude.toFixed(1), frac: +m.fraction.toFixed(2) })
  }
  return out.slice(0, 6)
})
console.log('  月亮可见且不被卡片挡住的时刻:', JSON.stringify(found))
const pick = found.find((f) => f.h >= 18) || found[0] || { h: 22 }
await page.evaluate((hh) => window.__tpLoginSky.setHour(hh), pick.h)
await page.waitForTimeout(600)
const st = await page.evaluate(() => {
  const k = window.__tpLoginSky
  return { hour: k.hour, moon: k.moon, phase: document.querySelector('.dc-moon-label').innerText }
})
console.log('  选定时刻:', st.hour, '月亮:', JSON.stringify(st.moon), st.phase)
await page.screenshot({ path: 'E:\\codestorge\\.run-logs\\moon3\\fullpage-moon.png' })
console.log(errs.length ? `  错误: ${errs[0]}` : '  控制台无报错')
await b.close()