// 验证：切天气后叠加层是否真的被清空（像素级）+ 水花 0.5s 淡出
import { chromium } from 'playwright-core'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 200)))
page.on('console', (m) => {
  if (m.type() === 'error') errs.push(m.text().slice(0, 200))
})

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(2000)
await page.evaluate(() => window.__tpLoginSky.setHour(12))

const px = () => page.evaluate(() => window.__tpLoginSky.overlayPixels())
const acc = () => page.evaluate(() => window.__tpLoginSky.accum)
const spl = () => page.evaluate(() => window.__tpLoginSky.splashCount)

console.log('--- 初始（晴天）---')
console.log('  叠加层非透明像素:', await px(), ' 堆积:', JSON.stringify(await acc()))

console.log('--- 下雨并拉到 100% ---')
await page.evaluate(() => {
  const k = window.__tpLoginSky
  k.setWeather('rain')
  k.setAccum(1)
})
await page.waitForTimeout(1200)
console.log('  叠加层像素:', await px(), ' 堆积:', JSON.stringify(await acc()))

console.log('--- 切到阴天（应立刻清空）---')
await page.evaluate(() => window.__tpLoginSky.setWeather('overcast'))
await page.waitForTimeout(800)
const pOvercast = await px()
const aOvercast = await acc()
console.log('  叠加层像素:', pOvercast, ' 堆积:', JSON.stringify(aOvercast))
console.log('  判定:', pOvercast === 0 && aOvercast.value === 0 ? '✓ 已彻底清空' : '✗ 仍有残留')

console.log('--- 切到雪并拉到 60% ---')
await page.evaluate(() => {
  const k = window.__tpLoginSky
  k.setWeather('snow')
  k.setAccum(0.6)
})
await page.waitForTimeout(900)
const pSnow = await px()
await page.evaluate(() => window.__tpLoginSky.setWeather('clear'))
await page.waitForTimeout(800)
const pClear = await px()
console.log('  雪 60% 像素:', pSnow, ' → 切晴后像素:', pClear, pClear === 0 ? '✓' : '✗')

console.log('--- 水花 0.5 秒淡出（每 250ms 采样）---')
await page.evaluate(() => {
  const k = window.__tpLoginSky
  k.setWeather('rain')
  k.setAccum(1)
})
await page.waitForTimeout(1500)
const samples = []
for (let i = 0; i < 8; i++) {
  samples.push(await spl())
  await new Promise((r) => setTimeout(r, 250))
}
console.log('  水花数量:', JSON.stringify(samples))

console.log('--- 反复切天气，确认不会重复堆积 ---')
for (let i = 0; i < 4; i++) {
  await page.evaluate(() => new Promise((r) => setTimeout(r, 0)) )
  await page.evaluate(() => {
    const k = window.__tpLoginSky
    k.setWeather('rain')
    k.setAccum(1)
  })
  await page.waitForTimeout(500)
  const p1 = await px()
  await page.evaluate(() => window.__tpLoginSky.setWeather('partly'))
  await page.waitForTimeout(500)
  const p2 = await px()
  console.log(`  第 ${i + 1} 轮：雨时像素 ${p1} → 切多云后 ${p2} ${p2 === 0 ? '✓' : '✗'}`)
}

await page.evaluate(() => window.__tpLoginSky.setWeather('auto'))
await b.close()
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
