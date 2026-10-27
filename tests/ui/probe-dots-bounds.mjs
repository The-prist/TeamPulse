// 精确验证（读取 window.__tpLoginDots）：
//   1. 越界检查：每颗点必须在自己的矩形内，且不超出画布
//   2. 吸引检查：光标附近（家在其吸附半径内）的点，平均距离应明显变小
//   3. 释放检查：光标移开后，平均距离应回升
import { chromium } from 'playwright-core'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const X = 210
const Y = 640

const run = async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)

  const snapshot = () => page.evaluate(() => {
    const dots = window.__tpLoginDots || []
    const c = document.querySelector('canvas.login-dots')
    return {
      count: dots.length,
      w: c.clientWidth,
      h: c.clientHeight,
      dots: dots.map((d) => ({ hx: d.hx, hy: d.hy, rx: d.rx, ry: d.ry, x: d.x, y: d.y })),
    }
  })

  const nearStats = (s, mx, my, R) => {
    const near = s.dots.filter((d) => Math.hypot(d.hx - mx, d.hy - my) <= R)
    if (!near.length) return { n: 0, avg: 0 }
    const avg = near.reduce((sum, d) => sum + Math.hypot(d.x - mx, d.y - my), 0) / near.length
    return { n: near.length, avg: Math.round(avg * 10) / 10 }
  }

  const s0 = await snapshot()
  console.log(`点数 = ${s0.count}  画布 = ${s0.w}×${s0.h}`)

  // ---- 1) 越界检查
  let bad = 0
  let worst = 0
  for (const d of s0.dots) {
    const ox = Math.abs(d.x - d.hx) - d.rx
    const oy = Math.abs(d.y - d.hy) - d.ry
    const out = Math.max(ox, oy)
    if (out > 0.5) bad++
    worst = Math.max(worst, out)
    if (d.x < -0.5 || d.y < -0.5 || d.x > s0.w + 0.5 || d.y > s0.h + 0.5) bad++
  }
  console.log(`越界检查：${bad === 0 ? '✓ 全部在各自矩形内' : `✗ ${bad} 颗越界`}（最大超出 ${worst.toFixed(2)}px）`)

  // ---- 2) 吸引
  const before = nearStats(s0, X, Y, 185)
  await page.mouse.move(X, Y)
  await page.waitForTimeout(1600)
  const s1 = await snapshot()
  const during = nearStats(s1, X, Y, 185)
  console.log(`光标附近点数 = ${before.n}`)
  console.log(`  平均距离 无光标 = ${before.avg}px  →  悬停 1.6s = ${during.avg}px`)

  // ---- 3) 释放
  await page.mouse.move(1300, 80)
  await page.waitForTimeout(3200)
  const s2 = await snapshot()
  const after = nearStats(s2, X, Y, 185)
  console.log(`  移开 3.2s = ${after.avg}px`)

  // ---- 4) 移动端/小窗
  await page.setViewportSize({ width: 780, height: 620 })
  await page.waitForTimeout(800)
  const s3 = await snapshot()
  console.log(`缩小到 780×620 后：点数 = ${s3.count}，画布 = ${s3.w}×${s3.h}`)

  const pulled = during.avg < before.avg - 3
  console.log(pulled ? '  ✓ 吸引生效（平均距离明显变小）' : '  ✗ 吸引偏弱')
  console.log(after.avg > during.avg + 2 ? '  ✓ 移开后回落' : '  · 移开后未明显回落')

  await b.close()
}
await run()
