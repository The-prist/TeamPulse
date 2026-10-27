// 验证「鼠标吸引」确实生效：
//   在光标周围 70px 的圆内统计白色像素，对比「无光标」与「悬停 1.5s 后」两次数值。
//   吸引成立时，圆心附近的点会明显变多。
import { chromium } from 'playwright-core'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const X = 210
const Y = 640

const run = async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)

  const density = () => page.evaluate(({ X, Y }) => {
    const c = document.querySelector('canvas.login-dots')
    const dpr = c.width / c.clientWidth
    const g = c.getContext('2d')
    const r = Math.round(70 * dpr)
    const cx = Math.round(X * dpr)
    const cy = Math.round(Y * dpr)
    const x0 = Math.max(0, cx - r)
    const y0 = Math.max(0, cy - r)
    const w = Math.min(c.width - x0, r * 2)
    const h = Math.min(c.height - y0, r * 2)
    const { data } = g.getImageData(x0, y0, w, h)
    let inside = 0
    for (let py = 0; py < h; py++) {
      for (let px = 0; px < w; px++) {
        const dx = x0 + px - cx
        const dy = y0 + py - cy
        if (dx * dx + dy * dy <= r * r) {
          if (data[(py * w + px) * 4 + 3] > 8) inside++
        }
      }
    }
    return inside
  }, { X, Y })

  const before = await density()
  // 光标移入该点，等 1.5s 让吸引生效
  await page.mouse.move(X, Y)
  await page.waitForTimeout(1500)
  const after = await density()
  // 光标离开，等 3s 看是否散开
  await page.mouse.move(1200, 120)
  await page.waitForTimeout(3000)
  const left = await density()

  console.log(`圆内白像素（70px 半径）`)
  console.log(`  无光标      : ${before}`)
  console.log(`  悬停 1.5s   : ${after}   (${after >= before ? '+' : ''}${after - before})`)
  console.log(`  移开 3s 后  : ${left}`)
  console.log(after > before * 1.15 ? '  ✓ 吸引生效（圆心附近显著变密）' : '  ✗ 吸引不明显，需调大力度')
  console.log(left < after ? '  ✓ 光标移开后散开' : '  · 移开后密度未明显下降')

  await b.close()
}
await run()
