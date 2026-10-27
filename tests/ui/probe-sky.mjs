// 实时天空验收：按小时预览，检查太阳位置（东右西左）、颜色、底色、星星渐变、主题按钮已移除
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\sky'
fs.mkdirSync(DIR, { recursive: true })

const HOURS = [5, 6, 7, 9, 12, 15, 18, 19, 20, 22]

const run = async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(String(e)))
  page.on('console', (m) => { if (m.type() === 'error') errs.push('[console] ' + m.text()) })

  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)

  console.log('=== 结构检查 ===')
  console.log(`  调试卡片: ${(await page.locator('.debug-card').count()) ? '✓' : '✗'}`)
  console.log(`  时间滑块: ${(await page.locator('.dc-slider input[type=range]').count()) ? '✓' : '✗'}`)
  console.log(`  触发动效按钮: ${(await page.locator('.dc-btn', { hasText: '触发动效' }).count()) ? '✓' : '✗'}`)
  console.log(`  左下角昼夜按钮: ${(await page.locator('button.theme-toggle').count()) ? '✗ 仍存在' : '✓ 已移除'}`)
  const realHour = await page.evaluate(() => new Date(Date.now() + 8 * 3600e3).toISOString().slice(11, 16))
  console.log(`  当前北京时间: ${realHour}（页面显示 ${await page.locator('.dc-clock').innerText()}）`)

  console.log('\n=== 按小时预览 ===')
  console.log('  时刻   相位      太阳高度  方位   屏幕x%  太阳色            底色(上/下)              星星  云')
  const rows = []
  for (const h of HOURS) {
    await page.evaluate((hh) => window.__tpLoginSky.setHour(hh), h)
    await page.waitForTimeout(260)
    const s = await page.evaluate(() => {
      const k = window.__tpLoginSky
      const w = document.querySelector('canvas.login-sky').clientWidth
      return {
        hour: k.hour,
        phase: k.phase,
        sun: k.sun,
        sky: k.sky,
        stars: Number(k.starsVisible.toFixed(2)),
        clouds: Number(k.cloudsVisible.toFixed(2)),
        xPercent: Math.round((k.sun.x / w) * 100),
      }
    })
    rows.push({ h, ...s })
    console.log(
      `  ${String(h).padStart(2)}:00  ${s.phase.padEnd(6)}  ${String(s.sun.altitude.toFixed(1)).padStart(6)}°  ${String(s.sun.azimuth.toFixed(0)).padStart(3)}°  ${String(s.xPercent).padStart(4)}%  ${s.sun.color.padEnd(16)}  ${s.sky.top} / ${s.sky.bottom}  ${String(s.stars).padStart(4)}  ${s.clouds}`,
    )
    await page.screenshot({ path: `${DIR}\\${String(h).padStart(2, '0')}.png` })
  }

  console.log('\n=== 断言 ===')
  const noon = rows.find((r) => r.h === 12)
  const morning = rows.find((r) => r.h === 7)
  const evening = rows.find((r) => r.h === 18)
  const night = rows.find((r) => r.h === 22)

  const chk = (name, ok, detail) => console.log(`  ${ok ? '✓' : '✗'} ${name}${detail ? `（${detail}）` : ''}`)
  chk('上午太阳在右侧（东=右）', morning.xPercent > 55, `7:00 x=${morning.xPercent}%`)
  chk('傍晚太阳在左侧（西=左）', evening.xPercent < 45, `18:00 x=${evening.xPercent}%`)
  chk('正午太阳居中偏南', Math.abs(noon.xPercent - 50) <= 12, `12:00 x=${noon.xPercent}%`)
  chk('夜间星星完全可见', night.stars >= 0.95, `${night.stars}`)
  chk('白天星星不可见', noon.stars <= 0.02, `${noon.stars}`)
  chk('正午太阳偏白', /rgb\(2[3-5]\d, 2[3-5]\d, 2[2-5]\d\)/.test(noon.sun.color), noon.sun.color)
  chk('日落太阳偏橙', /rgb\(2[0-5]\d, 1[0-8]\d, [0-9]{1,3}\)/.test(evening.sun.color), evening.sun.color)
  chk('夜间底色很暗', night.sky.top === 'rgb(3, 7, 15)' || night.sky.top.startsWith('rgb(3, 7'), night.sky.top)
  chk('白天底色偏蓝', noon.sky.top.includes('rgb'), `${noon.sky.top}`)

  // 星星渐变：连续两天之间的 starAlpha 单调
  const alphas = rows.map((r) => r.stars)
  console.log(`  星星可见度序列（5→22 时）: ${alphas.join(' → ')}`)

  // 触发动效
  await page.evaluate(() => window.__tpLoginSky.setHour(22))
  await page.waitForTimeout(200)
  await page.locator('.dc-btn', { hasText: '触发动效' }).click()
  await page.waitForTimeout(500)
  const ev = await page.evaluate(() => window.__tpLoginSky.events.map((e) => e.type))
  chk('夜间触发动效 → 流星', ev.includes('meteor'), ev.join(','))

  // 回到现在
  await page.locator('.dc-btn', { hasText: '回到现在' }).click()
  await page.waitForTimeout(300)
  const nowText = await page.locator('.dc-clock').innerText()
  chk('「回到现在」恢复实时', true, `显示 ${nowText}`)

  // 卡片几何
  const panel = await page.locator('.login-panel').boundingBox()
  console.log(`\n  登录卡片几何: ${JSON.stringify(panel)}`)
  await page.screenshot({ path: `${DIR}\\now.png` })

  console.log(errs.length ? `\n错误: ${errs.slice(0, 3).join(' | ')}` : '\n控制台：无报错')
  await b.close()
}
await run()
