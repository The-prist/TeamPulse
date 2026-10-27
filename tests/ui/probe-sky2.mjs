// 云朵/太阳/输入框专项验收：截图 9:00（云多）、12:00（太阳是否在卡片上方）、输入框颜色、云尺寸分布
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\sky2'
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

  // ---- 输入框颜色
  const inputs = await page.evaluate(() => {
    const w = document.querySelector('.el-input__wrapper')
    const inner = document.querySelector('.el-input__inner')
    const label = document.querySelector('.el-form-item__label')
    return {
      wrapperBg: w ? getComputedStyle(w).backgroundColor : null,
      text: inner ? getComputedStyle(inner).color : null,
      placeholder: inner ? getComputedStyle(inner, '::placeholder').color : null,
      label: label ? getComputedStyle(label).color : null,
      cardBg: getComputedStyle(document.querySelector('.form-side')).backgroundColor,
    }
  })
  console.log('输入框:', JSON.stringify(inputs))

  // ---- 云尺寸分布 + 太阳位置
  const info = async (h) => {
    await page.evaluate((hh) => window.__tpLoginSky.setHour(hh), h)
    await page.waitForTimeout(280)
    return page.evaluate(() => {
      const k = window.__tpLoginSky
      const c = document.querySelector('canvas.login-sky')
      const panel = document.querySelector('.login-panel').getBoundingClientRect()
      return {
        hour: k.hour,
        sunAlt: Number(k.sun.altitude.toFixed(1)),
        sunX: Math.round(k.sun.x),
        sunY: Math.round(k.sun.y),
        sunAlpha: Number(k.sun.alpha.toFixed(2)),
        cloudsVisible: Number(k.cloudsVisible.toFixed(2)),
        panel: { x: Math.round(panel.x), y: Math.round(panel.y), w: Math.round(panel.width), h: Math.round(panel.height) },
        canvasH: c.clientHeight,
      }
    })
  }

  for (const h of [8, 9, 12, 15, 17]) {
    const s = await info(h)
    const blocked = s.sunAlpha > 0.05 &&
      s.sunX > s.panel.x && s.sunX < s.panel.x + s.panel.w &&
      s.sunY > s.panel.y && s.sunY < s.panel.y + s.panel.h
    console.log(`  ${String(h).padStart(2)}:00 高度=${String(s.sunAlt).padStart(5)}° 太阳=(${s.sunX},${s.sunY}) 可见=${s.sunAlpha} 云=${s.cloudsVisible} ${blocked ? '✗ 被卡片挡住' : '✓ 没被挡'}`)
    await page.screenshot({ path: `${DIR}\\${String(h).padStart(2, '0')}.png` })
  }

  console.log(errs.length ? `\n错误: ${errs.slice(0, 3).join(' | ')}` : '\n控制台：无报错')
  await b.close()
}
await run()
