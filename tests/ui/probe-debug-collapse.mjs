import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\moon3'
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()) })
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1400)

const hasCollapse = await page.locator('.dc-collapse').count()
console.log(`  折叠按钮: ${hasCollapse ? '✓' : '✗'}`)

// 折叠后截图：月亮在左上也不会被挡
await page.evaluate(() => window.__tpLoginSky.setHour(0))
await page.waitForTimeout(400)
await page.screenshot({ path: `${DIR}\\expanded-0h.png` })
await page.locator('.dc-collapse').click()
await page.waitForTimeout(300)
const bodyVisible = await page.locator('.dc-slider').isVisible()
console.log(`  折叠后主体隐藏: ${bodyVisible ? '✗ 仍显示' : '✓ 已隐藏'}`)
await page.screenshot({ path: `${DIR}\\collapsed-0h.png` })

// 展开，切到 18:00 看傍晚 + 月亮
await page.locator('.dc-collapse').click()
await page.evaluate(() => window.__tpLoginSky.setHour(18))
await page.waitForTimeout(500)
const st = await page.evaluate(() => ({ moon: window.__tpLoginSky.moon, phase: document.querySelector('.dc-moon-label').innerText }))
console.log(`  18:00 月亮: ${JSON.stringify({ alt: +st.moon.altitude.toFixed(1), az: +st.moon.azimuth.toFixed(0), frac: +st.moon.fraction.toFixed(2), alpha: +st.moon.alpha.toFixed(2) })}  ${st.phase}`)
await page.screenshot({ path: `${DIR}\\18h.png` })
console.log(errs.length ? `  错误: ${errs[0]}` : '  控制台无报错')
await b.close()