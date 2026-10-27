// 登录场景 v3 验收：
//   1. 左上角调试按钮存在且能触发动效
//   2. 夜→昼：天色/太阳必须在最后一颗星掉出屏幕之后才发生
//   3. 节奏放慢（记录各阶段时刻）
//   4. 云朵聚拢（gather 0→1）、飞机纯白小尺寸、卡片几何不变
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\login3'
fs.mkdirSync(DIR, { recursive: true })

const run = async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(String(e)))
  page.on('console', (m) => { if (m.type() === 'error') errs.push('[console] ' + m.text()) })

  const st = () => page.evaluate(() => {
    const d = window.__tpLoginDots
    const c = document.querySelector('canvas.login-scene')
    const h = c?.clientHeight ?? 0
    const stars = d?.stars ?? []
    return {
      scene: d?.scene, phase: d?.phase,
      stars: stars.length,
      maxStarY: stars.length ? Math.round(Math.max(...stars.map((s) => s.y))) : 0,
      allGone: stars.length ? stars.every((s) => s.y > h + 60) : true,
      clouds: d?.clouds?.length ?? 0,
      gather: d?.clouds?.[0] ? Number(d.clouds[0].gather.toFixed(2)) : null,
      sunAlpha: Number((d?.sunAlpha ?? 0).toFixed(2)),
      dark: document.documentElement.classList.contains('dark'),
      sky: Number(parseFloat(getComputedStyle(document.querySelector('.day-sky')).opacity).toFixed(2)),
      events: (d?.events ?? []).map((e) => e.type),
    }
  })

  await page.addInitScript(() => localStorage.setItem('tp_theme', 'dark'))
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)

  // ---- 1) 调试按钮
  const btn = page.locator('button.fx-trigger')
  console.log(`调试按钮：${(await btn.count()) ? '✓ 存在' : '✗ 缺失'}  位置=${JSON.stringify(await btn.boundingBox())}`)
  await btn.click()
  await page.waitForTimeout(400)
  const afterClick = await st()
  console.log(`点击后事件：${JSON.stringify(afterClick.events)}  ${afterClick.events.length ? '✓ 触发成功' : '✗ 没触发'}`)
  await page.screenshot({ path: `${DIR}\\1-debug-click.png` })

  // ---- 2) 夜 → 昼：按 150ms 采样，记录「最后一颗星落出」与「天色变化」的先后
  await page.evaluate(() => { window.__tpLoginDots.events.length = 0 })
  await page.waitForTimeout(1200)
  console.log('\n【夜 → 昼】开始')
  const t0 = Date.now()
  const panelBefore = await page.locator('.login-panel').boundingBox()
  await page.locator('button.theme-toggle').click()

  let flipAt = null
  let allGoneAt = null
  const rows = []
  while (Date.now() - t0 < 7000) {
    const s = await st()
    const t = Date.now() - t0
    rows.push({ t, ...s })
    if (s.allGone && allGoneAt === null) allGoneAt = t
    if (!s.dark && flipAt === null) flipAt = t
    if (s.phase === 'idle' && s.scene === 'day') break
    await page.waitForTimeout(150)
  }
  for (const r of rows) {
    console.log(`  ${String(r.t).padStart(4)}ms phase=${String(r.phase).padEnd(7)} dark=${String(r.dark).padEnd(5)} sky=${r.sky} 太阳=${r.sunAlpha} 星=${String(r.stars).padStart(3)}(maxY=${r.maxStarY}) 全落出=${r.allGone} 云=${r.clouds} gather=${r.gather}`)
  }
  console.log(`\n最后一颗星落出屏幕: ${allGoneAt}ms    天色切换: ${flipAt}ms`)
  console.log(flipAt !== null && allGoneAt !== null && flipAt >= allGoneAt
    ? '  ✓ 顺序正确：先落完星，再变色/日出'
    : '  ✗ 顺序不对：变色发生在星星落完之前')

  // 太阳亮度在变色前必须为 0
  const beforeFlip = rows.filter((r) => r.t < flipAt)
  const sunLeak = beforeFlip.filter((r) => r.sunAlpha > 0.01)
  console.log(`变色前太阳可见的采样点: ${sunLeak.length}  ${sunLeak.length === 0 ? '✓ 太阳没有提前升起' : '✗ 太阳提前出现了'}`)

  const day = await st()
  console.log(`\n【白天】scene=${day.scene} clouds=${day.clouds} gather=${day.gather} 太阳=${day.sunAlpha} sky=${day.sky}`)
  const panelAfter = await page.locator('.login-panel').boundingBox()
  console.log(`卡片几何 ${JSON.stringify(panelBefore)} → ${JSON.stringify(panelAfter)}  ${JSON.stringify(panelBefore) === JSON.stringify(panelAfter) ? '✓ 未变化' : '✗ 变化了'}`)
  await page.screenshot({ path: `${DIR}\\2-day.png` })

  // ---- 3) 白天点调试按钮 → 飞机
  await btn.click()
  await page.waitForTimeout(700)
  const plane = await st()
  console.log(`\n白天点按钮：${JSON.stringify(plane.events)}  ${plane.events.includes('plane') ? '✓ 触发飞机' : '✗ 未触发'}`)
  await page.screenshot({ path: `${DIR}\\3-plane.png` })
  await page.waitForTimeout(2500)
  await page.screenshot({ path: `${DIR}\\4-contrail.png` })

  // ---- 4) 昼 → 夜
  console.log('\n【昼 → 夜】')
  await page.locator('button.theme-toggle').click()
  await page.waitForTimeout(1200)
  const mid = await st()
  console.log(`  1.2s: phase=${mid.phase} dark=${mid.dark} sky=${mid.sky} 太阳=${mid.sunAlpha} 云=${mid.clouds} gather=${mid.gather}`)
  await page.screenshot({ path: `${DIR}\\5-to-night.png` })
  await page.waitForTimeout(2200)
  const night = await st()
  console.log(`  结束: scene=${night.scene} stars=${night.stars} 云=${night.clouds} dark=${night.dark} sky=${night.sky}`)
  await page.screenshot({ path: `${DIR}\\6-night.png` })

  console.log(errs.length ? `\n错误: ${errs.slice(0, 3).join(' | ')}` : '\n控制台：无报错')
  await b.close()
}
await run()
