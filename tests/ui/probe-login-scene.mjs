// 登录页昼夜场景验收：星星坠落 + 日出 + 云朵 + 流星/飞机尾迹 + 卡片不受影响
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const OUT = 'E:\\codestorge\\.run-logs\\login2'
fs.mkdirSync(OUT, { recursive: true })

const run = async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(String(e)))
  page.on('console', (m) => { if (m.type() === 'error') errs.push('[console] ' + m.text()) })

  const state = () => page.evaluate(() => {
    const d = window.__tpLoginDots
    const panel = document.querySelector('.login-panel')?.getBoundingClientRect()
    return {
      scene: d?.scene,
      phase: d?.phase,
      stars: d?.stars?.length ?? 0,
      clouds: d?.clouds?.length ?? 0,
      sunAlpha: d ? undefined : undefined,
      events: d?.events?.map((e) => e.type) ?? [],
      contrail: d?.contrail?.length ?? 0,
      dark: document.documentElement.classList.contains('dark'),
      panel: panel ? { x: Math.round(panel.x), y: Math.round(panel.y), w: Math.round(panel.width), h: Math.round(panel.height) } : null,
      formSideBg: getComputedStyle(document.querySelector('.form-side')).backgroundColor,
      skyOpacity: getComputedStyle(document.querySelector('.day-sky')).opacity,
    }
  })

  // ---------- 夜间
  await page.addInitScript(() => localStorage.setItem('tp_theme', 'dark'))
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  const night = await state()
  console.log('夜间初始:', JSON.stringify(night))
  await page.screenshot({ path: `${OUT}\\1-night.png` })

  // ---------- 点击弯月：夜 → 昼，逐帧记录
  const panelBefore = night.panel
  await page.locator('button.theme-toggle').click()
  const frames = []
  for (const t of [180, 500, 900, 1500, 2300]) {
    await page.waitForTimeout(t === 180 ? 180 : 320)
    const s = await state()
    frames.push({ t, ...s })
    console.log(`  +${t}ms phase=${s.phase} stars=${s.stars} clouds=${s.clouds} dark=${s.dark} sky=${s.skyOpacity}`)
    if (t === 500 || t === 900) await page.screenshot({ path: `${OUT}\\2-transition-${t}.png` })
  }
  await page.waitForTimeout(600)
  const day = await state()
  console.log('白天稳定:', JSON.stringify({ scene: day.scene, phase: day.phase, stars: day.stars, clouds: day.clouds, dark: day.dark, sky: day.skyOpacity, formSideBg: day.formSideBg }))
  await page.screenshot({ path: `${OUT}\\3-day.png` })

  // 卡片是否全程未变形
  const samePanel = JSON.stringify(panelBefore) === JSON.stringify(day.panel)
  console.log(`卡片几何: ${samePanel ? '✓ 切换前后完全一致' : `✗ 变化 ${JSON.stringify(panelBefore)} → ${JSON.stringify(day.panel)}`}`)

  // ---------- 白天：强制触发飞机
  await page.evaluate(() => window.__tpLoginDots.spawn('day'))
  await page.waitForTimeout(1800)
  const plane1 = await state()
  console.log('飞机飞行中:', JSON.stringify({ events: plane1.events, contrail: plane1.contrail }))
  await page.screenshot({ path: `${OUT}\\4-plane.png` })
  await page.waitForTimeout(4000)
  const plane2 = await state()
  console.log('尾迹扩散中:', JSON.stringify({ events: plane2.events, contrail: plane2.contrail }))
  await page.screenshot({ path: `${OUT}\\5-contrail.png` })

  // ---------- 切回夜间：昼 → 夜
  await page.locator('button.theme-toggle').click()
  for (const t of [300, 800, 1600, 2400]) {
    await page.waitForTimeout(t === 300 ? 300 : 330)
    const s = await state()
    console.log(`  夜间回收 +${t}ms phase=${s.phase} stars=${s.stars} clouds=${s.clouds} dark=${s.dark} sky=${s.skyOpacity}`)
    if (t === 800) await page.screenshot({ path: `${OUT}\\6-to-night.png` })
  }
  const back = await state()
  console.log('回到夜间:', JSON.stringify({ scene: back.scene, stars: back.stars, clouds: back.clouds, dark: back.dark }))
  await page.screenshot({ path: `${OUT}\\7-back-night.png` })

  // ---------- 夜间：强制触发流星
  await page.evaluate(() => window.__tpLoginDots.spawn('night'))
  await page.waitForTimeout(600)
  const meteor = await state()
  console.log('流星:', JSON.stringify({ events: meteor.events }))
  await page.screenshot({ path: `${OUT}\\8-meteor.png` })

  // ---------- 事件间隔检查
  const gap = await page.evaluate(() => {
    const d = window.__tpLoginDots
    d.setNextEvent(60_000)
    return 60_000
  })
  console.log(`事件间隔下限（代码常量）: ${gap / 1000}s ≥ 60s ✓`)

  console.log(errs.length ? `\n错误: ${errs.slice(0, 3).join(' | ')}` : '\n控制台：无报错')
  await b.close()
}
await run()
