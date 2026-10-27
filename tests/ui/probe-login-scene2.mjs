// 登录页昼夜场景验收 v2（按真实时间轴采样，并检查暗色卡片、星空坠落、日出、云朵、流星/飞机）
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const OUT = 'E:\\.run-logs\\login2'
fs.mkdirSync('E:\\codestorge\\.run-logs\\login2', { recursive: true })
const DIR = 'E:\\codestorge\\.run-logs\\login2'

const run = async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(String(e)))
  page.on('console', (m) => { if (m.type() === 'error') errs.push('[console] ' + m.text()) })

  const snap = () => page.evaluate(() => {
    const d = window.__tpLoginDots
    const p = document.querySelector('.login-panel')?.getBoundingClientRect()
    const stars = d?.stars ?? []
    // 星星平均 y（判断是否在坠落）
    const avgY = stars.length ? Math.round(stars.reduce((s, x) => s + x.y, 0) / stars.length) : 0
    const offscreen = stars.filter((s) => s.y > (document.querySelector('canvas')?.clientHeight ?? 0) + 5).length
    // 右侧太阳区域亮度（采样 canvas 像素）
    let sunLum = 0
    const c = document.querySelector('canvas.login-scene')
    if (c) {
      const g = c.getContext('2d')
      const dpr = c.width / c.clientWidth
      const sx = Math.round(c.clientWidth * 0.84 * dpr)
      const sy = Math.round(c.clientHeight * 0.26 * dpr)
      const size = Math.round(60 * dpr)
      const x0 = Math.max(0, sx - size)
      const y0 = Math.max(0, sy - size)
      const w = Math.min(c.width - x0, size * 2)
      const h = Math.min(c.height - y0, size * 2)
      if (w > 0 && h > 0) {
        const { data } = g.getImageData(x0, y0, w, h)
        let sum = 0
        for (let i = 0; i < data.length; i += 4) sum += data[i] + data[i + 1] + data[i + 2]
        sunLum = Math.round(sum / (data.length / 4))
      }
    }
    return {
      scene: d?.scene, phase: d?.phase,
      stars: stars.length, offscreen, avgY,
      clouds: d?.clouds?.length ?? 0,
      events: (d?.events ?? []).map((e) => e.type),
      contrail: d?.contrail?.length ?? 0,
      dark: document.documentElement.classList.contains('dark'),
      sky: getComputedStyle(document.querySelector('.day-sky')).opacity,
      formBg: getComputedStyle(document.querySelector('.form-side')).backgroundColor,
      sunLum,
      panel: p ? { x: Math.round(p.x), y: Math.round(p.y), w: Math.round(p.width), h: Math.round(p.height) } : null,
    }
  })

  const sleep = (ms) => page.waitForTimeout(ms)

  await page.addInitScript(() => localStorage.setItem('tp_theme', 'dark'))
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await sleep(1500)

  const night = await snap()
  console.log('【夜间】', JSON.stringify(night))
  await page.screenshot({ path: `${DIR}\\1-night.png` })

  // ---------------- 夜 → 昼
  console.log('\n【夜 → 昼】点击弯月')
  const t0 = Date.now()
  await page.locator('button.theme-toggle').click()
  const marks = [200, 450, 700, 1000, 1400, 2200, 2600]
  for (const m of marks) {
    const wait = m - (Date.now() - t0)
    if (wait > 0) await sleep(wait)
    const s = await snap()
    console.log(`  ${String(m).padStart(4)}ms phase=${String(s.phase).padEnd(8)} stars=${String(s.stars).padStart(3)} 已在屏下=${String(s.offscreen).padStart(3)} 平均Y=${String(s.avgY).padStart(4)} 云=${s.clouds} dark=${s.dark} sky=${s.sky} 太阳亮度=${s.sunLum}`)
    if (m === 450 || m === 1000) await page.screenshot({ path: `${DIR}\\2-transition-${m}.png` })
  }
  const day = await snap()
  console.log('【白天】', JSON.stringify({ scene: day.scene, phase: day.phase, clouds: day.clouds, dark: day.dark, sky: day.sky, sunLum: day.sunLum }))
  await page.screenshot({ path: `${DIR}\\3-day.png` })

  console.log(`\n卡片几何 ${JSON.stringify(night.panel)} → ${JSON.stringify(day.panel)}  ${JSON.stringify(night.panel) === JSON.stringify(day.panel) ? '✓ 未变化' : '✗ 变化了'}`)
  console.log(`夜间卡片底 ${night.formBg}（期望暗色）  ${night.formBg !== 'rgb(255, 255, 255)' ? '✓' : '✗ 还是白的'}`)

  // ---------------- 白天：飞机 + 尾迹
  console.log('\n【白天事件】飞机 + 尾迹云')
  await page.evaluate(() => window.__tpLoginDots.spawn('day'))
  await sleep(900)
  console.log('  飞行中:', JSON.stringify((({ events, contrail }) => ({ events, contrail }))(await snap())))
  await page.screenshot({ path: `${DIR}\\4-plane.png` })
  await sleep(2500)
  const c1 = await snap()
  console.log('  尾迹扩散中:', JSON.stringify({ events: c1.events, contrail: c1.contrail }))
  await page.screenshot({ path: `${DIR}\\5-contrail.png` })
  await sleep(7000)
  const c2 = await snap()
  console.log('  7s 后:', JSON.stringify({ events: c2.events, contrail: c2.contrail }))

  // ---------------- 昼 → 夜
  console.log('\n【昼 → 夜】再点弯月')
  const t1 = Date.now()
  await page.locator('button.theme-toggle').click()
  for (const m of [250, 700, 1200, 2000, 2600]) {
    const wait = m - (Date.now() - t1)
    if (wait > 0) await sleep(wait)
    const s = await snap()
    console.log(`  ${String(m).padStart(4)}ms phase=${String(s.phase).padEnd(9)} stars=${s.stars} 云=${s.clouds} dark=${s.dark} sky=${s.sky} 太阳亮度=${s.sunLum}`)
    if (m === 700) await page.screenshot({ path: `${DIR}\\6-to-night.png` })
  }
  const back = await snap()
  console.log('【回到夜间】', JSON.stringify({ scene: back.scene, phase: back.phase, stars: back.stars, clouds: back.clouds, dark: back.dark, sky: back.sky }))
  await page.screenshot({ path: `${DIR}\\7-back-night.png` })

  // ---------------- 夜间：流星
  console.log('\n【夜间事件】流星')
  await page.evaluate(() => window.__tpLoginDots.spawn('night'))
  await sleep(450)
  console.log('  流星:', JSON.stringify((({ events, contrail }) => ({ events, contrail }))(await snap())))
  await page.screenshot({ path: `${DIR}\\8-meteor.png` })

  console.log(errs.length ? `\n错误: ${errs.slice(0, 3).join(' | ')}` : '\n控制台：无报错')
  await b.close()
}
await run()
