import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\accum'
fs.mkdirSync(DIR, { recursive: true })
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 200)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text().slice(0, 200)) })
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1800)
await page.evaluate(() => window.__tpLoginSky.setHour(12))

async function shot(name, bucket, intensity, percent) {
  await page.evaluate(([bb, ii, pp]) => {
    const k = window.__tpLoginSky
    k.setWeather(bb)
    const sel = document.querySelectorAll('.dc-select')[1]
    if (sel && ii) { sel.value = String(ii); sel.dispatchEvent(new Event('change')) }
    k.setAccum(pp)
  }, [bucket, intensity, percent])
  await page.waitForTimeout(1200)
  const st = await page.evaluate(() => ({ accum: window.__tpLoginSky.accum, w: window.__tpLoginSky.weather }))
  await page.screenshot({ path: `${DIR}\\${name}.png` })
  console.log(`  ${name.padEnd(16)} 堆积=${st.accum.percent}% kind=${st.accum.kind} 云=${st.w.cloudCount}`)
}

await shot('rain-40', 'rain', 2, 0.4)
await shot('rain-100', 'rain', 3, 1)
await shot('snow-40', 'snow', 2, 0.4)
await shot('snow-100', 'snow', 3, 1)
await shot('rain-clear-add', 'rain', 1, 0.7)

// 涨速验证：小雨 / 中雨 / 大雨 各自每秒增量
const rates = await page.evaluate(async () => {
  const k = window.__tpLoginSky
  const out = {}
  for (const lvl of [1, 2, 3]) {
    k.setWeather('rain')
    const sel = document.querySelectorAll('.dc-select')[1]
    sel.value = String(lvl); sel.dispatchEvent(new Event('change'))
    k.setAccum(0)
    await new Promise((r) => setTimeout(r, 3000))
    out[lvl] = +(k.accum.value / 3).toFixed(5)   // 每秒增量
  }
  return out
})
console.log('  每秒增量:', JSON.stringify(rates))
for (const [lvl, per] of Object.entries(rates)) {
  const total = 1 / per
  console.log(`    强度 ${lvl}: 每秒 ${(per * 100).toFixed(3)}% → 涨满约 ${Math.round(total)} 秒（${(total / 60).toFixed(1)} 分钟）`)
}
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
await b.close()