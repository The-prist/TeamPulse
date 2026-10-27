// 精确定位：月亮实际绘制在屏幕哪一侧？与方位角是否一致
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'

const run = async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)

  const probe = async (h) => {
    await page.evaluate((hh) => window.__tpLoginSky.setHour(hh), h)
    await page.waitForTimeout(260)
    return page.evaluate(() => {
      const k = window.__tpLoginSky
      const c = document.querySelector('canvas.login-sky')
      const g = c.getContext('2d')
      const dpr = c.width / c.clientWidth
      // 扫整幅画布，找最亮的圆盘状区域（月亮/太阳），返回其中心
      const { data } = g.getImageData(0, 0, c.width, c.height)
      let bestX = 0
      let bestY = 0
      let bestScore = -1
      for (let gy = 0; gy < c.height; gy += 6) {
        for (let gx = 0; gx < c.width; gx += 6) {
          const i = (gy * c.width + gx) * 4
          const lum = data[i] + data[i + 1] + data[i + 2]
          if (lum > bestScore) { bestScore = lum; bestX = gx; bestY = gy }
        }
      }
      return {
        hour: k.hour,
        moon: {
          alt: +k.moon.altitude.toFixed(1),
          az: +k.moon.azimuth.toFixed(0),
          frac: +k.moon.fraction.toFixed(2),
          x: Math.round(k.moon.x),
          y: Math.round(k.moon.y),
          alpha: +k.moon.alpha.toFixed(2),
        },
        sunAlt: +k.sun.altitude.toFixed(1),
        brightest: { x: Math.round(bestX / dpr), y: Math.round(bestY / dpr), lum: bestScore },
      }
    })
  }

  console.log('  时刻   月亮方位  月亮x  月亮y  alpha   画布最亮点(x,y)   一致?')
  for (const h of [0, 3, 6, 18, 21, 23]) {
    const s = await probe(h)
    const ok = Math.abs(s.brightest.x - s.moon.x) < 90 || s.moon.alpha < 0.2
    console.log(
      `  ${String(h).padStart(2)}:00  ${String(s.moon.az).padStart(6)}°  ${String(s.moon.x).padStart(5)}  ${String(s.moon.y).padStart(5)}  ${String(s.moon.alpha).padStart(4)}   (${s.brightest.x},${s.brightest.y})  ${ok ? '✓' : '✗'}`,
    )
    await page.screenshot({ path: `E:\\codestorge\\.run-logs\\moon\\h${String(h).padStart(2, '0')}.png` })
  }
  await b.close()
}
await run()
