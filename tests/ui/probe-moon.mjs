// 月亮与方位对调验收：东左西右、月亮位置/月相、五种月相形状截图
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\moon'
fs.mkdirSync(DIR, { recursive: true })

const run = async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(String(e)))
  page.on('console', (m) => { if (m.type() === 'error') errs.push('[console] ' + m.text()) })

  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)

  const info = async (h) => {
    await page.evaluate((hh) => window.__tpLoginSky.setHour(hh), h)
    await page.waitForTimeout(240)
    return page.evaluate(() => {
      const k = window.__tpLoginSky
      const c = document.querySelector('canvas.login-sky')
      return {
        hour: k.hour,
        sunAlt: Number(k.sun.altitude.toFixed(1)),
        sunX: Math.round(k.sun.x),
        phaseText: k.phase,
        moon: {
          alt: Number(k.moon.altitude.toFixed(1)),
          az: Number(k.moon.azimuth.toFixed(0)),
          frac: Number(k.moon.fraction.toFixed(2)),
          phase: Number(k.moon.phase.toFixed(3)),
          x: Math.round(k.moon.x),
          y: Math.round(k.moon.y),
          alpha: Number(k.moon.alpha.toFixed(2)),
        },
        width: c.clientWidth,
      }
    })
  }

  console.log('=== 方位对调检查（东左西右）===')
  const m7 = await info(7)
  const m17 = await info(17)
  const m12 = await info(12)
  const chk = (name, ok, d) => console.log(`  ${ok ? '✓' : '✗'} ${name}（${d}）`)
  chk('上午太阳在左侧（东=左）', m7.sunX / m7.width < 0.45, `7:00 x=${Math.round((m7.sunX / m7.width) * 100)}%`)
  chk('下午太阳在右侧（西=右）', m17.sunX / m17.width > 0.55, `17:00 x=${Math.round((m17.sunX / m17.width) * 100)}%`)
  chk('正午居中', Math.abs(m12.sunX / m12.width - 0.5) <= 0.12, `12:00 x=${Math.round((m12.sunX / m12.width) * 100)}%`)

  console.log('\n=== 月亮（北京时间今天）===')
  console.log('  时刻   太阳高度  月亮高度  月亮方位  月亮屏幕x  照亮   月相   可见度')
  for (const h of [0, 6, 12, 18, 21]) {
    const s = await info(h)
    console.log(
      `  ${String(h).padStart(2)}:00  ${String(s.sunAlt).padStart(6)}°  ${String(s.moon.alt).padStart(7)}°  ${String(s.moon.az).padStart(6)}°  ${String(Math.round((s.moon.x / s.width) * 100)).padStart(6)}%  ${String(s.moon.frac).padStart(4)}  ${s.moon.phase.toFixed(2)}  ${s.moon.alpha}`,
    )
  }

  // 月相形状：强制五种相位截图
  console.log('\n=== 月相绘制检查（强制相位）===')
  await page.evaluate(() => window.__tpLoginSky.setHour(22))
  await page.waitForTimeout(200)
  const shapes = [
    ['crescent-waxing', 0.12],
    ['quarter-waxing', 0.25],
    ['full', 0.5],
    ['quarter-waning', 0.75],
    ['crescent-waning', 0.88],
  ]
  for (const [name, f] of shapes) {
    await page.evaluate((frac) => window.__tpLoginSky.__setMoonPhase(frac), f)
    await page.waitForTimeout(120)
    await page.screenshot({ path: `${DIR}\\${name}.png` })
    console.log(`  ${name}: fraction=${f}`)
  }

  // 恢复真实月相
  await page.evaluate(() => window.__tpLoginSky.setHour(null))
  await page.waitForTimeout(300)

  const label = await page.locator('.dc-moon-label').innerText()
  console.log(`\n调试卡片月亮一行: ${label}`)
  const cardText = await page.locator('.debug-card').innerText()
  console.log('调试卡片全文:')
  console.log(cardText.split('\n').map((l) => '    ' + l).join('\n'))

  await page.screenshot({ path: `${DIR}\\now.png` })
  console.log(errs.length ? `\n错误: ${errs.slice(0, 3).join(' | ')}` : '\n控制台：无报错')
  await b.close()
}
await run()
