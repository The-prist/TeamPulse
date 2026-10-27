// 登录页粒子验证：截图（含鼠标位置）、检查点是否越界、动画是否在跑
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const OUT = 'E:\\codestorge\\.run-logs\\login'
fs.mkdirSync(OUT, { recursive: true })

const run = async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(String(e)))
  page.on('console', (m) => { if (m.type() === 'error') errs.push('[console] ' + m.text()) })

  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)

  // 背景是否纯色 + 网格是否已移除
  const bg = await page.evaluate(() => {
    const el = document.querySelector('.login-page')
    const s = getComputedStyle(el)
    return {
      bgColor: s.backgroundColor,
      bgImage: s.backgroundImage,
      gridExists: !!document.querySelector('.bg-grid'),
      canvas: !!document.querySelector('canvas.login-dots'),
      canvasSize: (() => {
        const c = document.querySelector('canvas.login-dots')
        return c ? { w: c.width, h: c.height, cssW: c.clientWidth, cssH: c.clientHeight } : null
      })(),
    }
  })
  console.log('背景:', JSON.stringify(bg))

  // 取画布里画了多少点（读像素统计白色像素数量级）
  const whitePixels = async () => page.evaluate(() => {
    const c = document.querySelector('canvas.login-dots')
    if (!c) return -1
    const g = c.getContext('2d')
    const { data } = g.getImageData(0, 0, c.width, c.height)
    let n = 0
    for (let i = 3; i < data.length; i += 4) if (data[i] > 8) n++
    return n
  })
  console.log('白色像素（帧1）:', await whitePixels())
  await page.screenshot({ path: `${OUT}\\1-initial.png` })

  // 鼠标移到左侧空白区，观察吸引（像素分布应发生变化）
  await page.mouse.move(320, 300)
  await page.waitForTimeout(900)
  await page.screenshot({ path: `${OUT}\\2-cursor.png` })
  console.log('白色像素（鼠标悬停 0.9s 后）:', await whitePixels())

  // 运行动画 2 秒后再看：应仍在跑且像素量稳定（没被清空/没画爆）
  await page.mouse.move(700, 760)
  await page.waitForTimeout(2000)
  const px = await whitePixels()
  console.log('白色像素（2s 后）:', px)
  await page.screenshot({ path: `${OUT}\\3-later.png` })

  // 缩小窗口，验证 resize 后仍在正常渲染
  await page.setViewportSize({ width: 900, height: 640 })
  await page.waitForTimeout(800)
  const px2 = await whitePixels()
  console.log('缩小窗口后白色像素:', px2)
  await page.screenshot({ path: `${OUT}\\4-resized.png` })

  console.log(errs.length ? `\n错误: ${errs.slice(0, 3).join(' | ')}` : '\n无报错')
  await b.close()
}
await run()
