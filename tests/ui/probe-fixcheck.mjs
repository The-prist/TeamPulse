// 验证：①天气定时刷新 ②切天气堆积清零 ③水花会自动消失
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\fixcheck'
fs.mkdirSync(DIR, { recursive: true })

const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 200)))
page.on('console', (m) => {
  if (m.type() === 'error') errs.push(m.text().slice(0, 200))
})
let skyCallCount = 0
page.on('response', (r) => {
  if (r.url().includes('sky-context')) skyCallCount++
})

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(2000)
await page.evaluate(() => window.__tpLoginSky.setHour(12))

// ---- ① 定时刷新：把 10 分钟压成 2 秒来验证机制存在
console.log('--- ① 天气刷新 ---')
const before = skyCallCount
const refreshInfo = await page.evaluate(async () => {
  // 拿到 pinia store 实例
  const app = document.querySelector('#app').__vue_app__
  const pinia = app.config.globalProperties.$pinia
  const sky = pinia._s.get('sky')
  const hasStart = typeof sky.startAutoRefresh === 'function'
  // 用很短的间隔启动，验证定时器真的会触发 load()
  sky.startAutoRefresh(1500)
  await new Promise((r) => setTimeout(r, 4000))
  sky.stopAutoRefresh()
  return { hasStart, lastLoadedAt: sky.lastLoadedAt }
})
console.log('  store 有 startAutoRefresh:', refreshInfo.hasStart, ' lastLoadedAt:', new Date(refreshInfo.lastLoadedAt).toLocaleTimeString())
console.log('  4 秒内额外请求次数:', skyCallCount - before)

// ---- ② 切天气：堆积立即清零
console.log('--- ② 切换天气清空堆积 ---')
await page.evaluate(() => {
  const k = window.__tpLoginSky
  k.setWeather('rain')
  k.setAccum(0.85)
})
await page.waitForTimeout(600)
const p1 = await page.evaluate(() => window.__tpLoginSky.accum)
console.log('  雨 + 手动 85% →', JSON.stringify(p1))
await page.evaluate(() => window.__tpLoginSky.setWeather('overcast'))
await page.waitForTimeout(500)
const p2 = await page.evaluate(() => window.__tpLoginSky.accum)
console.log('  切到阴天 →', JSON.stringify(p2), p2.value === 0 ? '✓ 立即清零' : '✗ 未清零')
await page.evaluate(() => window.__tpLoginSky.setWeather('snow'))
await page.evaluate(() => window.__tpLoginSky.setAccum(0.6))
await page.waitForTimeout(400)
await page.evaluate(() => window.__tpLoginSky.setWeather('rain'))
await page.waitForTimeout(400)
const p3 = await page.evaluate(() => window.__tpLoginSky.accum)
console.log('  雪 60% → 切雨 →', JSON.stringify(p3), p3.value === 0 ? '✓ 立即清零' : '✗ 未清零')

// ---- ③ 水花生命周期：注入水花后看它是否归零
console.log('--- ③ 水花自动消失 ---')
const splashTest = await page.evaluate(async () => {
  const k = window.__tpLoginSky
  k.setWeather('rain')
  k.setAccum(1)
  await new Promise((r) => setTimeout(r, 800))
  const samples = []
  for (let i = 0; i < 6; i++) {
    samples.push(k.splashCount)
    await new Promise((r) => setTimeout(r, 500))
  }
  return samples
})
console.log('  水花数量采样（每 0.5s）:', JSON.stringify(splashTest))

// ---- ④ 停雨后的表现
await page.evaluate(() => window.__tpLoginSky.setWeather('clear'))
await page.waitForTimeout(700)
const p4 = await page.evaluate(() => window.__tpLoginSky.accum)
console.log('  切到晴 →', JSON.stringify(p4), p4.value === 0 ? '✓ 已清空' : '✗ 仍在')
await page.screenshot({ path: `${DIR}\\after-clear.png` })

await page.evaluate(() => window.__tpLoginSky.setWeather('auto'))
await b.close()
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
