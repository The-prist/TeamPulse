import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\skyweather'
fs.mkdirSync(DIR, { recursive: true })
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 300)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text().slice(0, 300)) })

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(2500)

const card = await page.evaluate(() => {
  const el = document.querySelector('.sky-card')
  return el ? el.innerText.replace(/\n+/g, ' | ') : '(未找到 .sky-card)'
})
console.log('  登录卡天气块:', card)

const w = await page.evaluate(() => window.__tpLoginSky.weather)
console.log('  store 天气:', JSON.stringify(w))

// 固定到 9:00，逐个天气截图
await page.evaluate(() => window.__tpLoginSky.setHour(9))
await page.waitForTimeout(600)
const buckets = ['clear', 'partly', 'overcast', 'rain', 'snow', 'fog', 'thunder']
for (const bucket of buckets) {
  await page.evaluate((b) => window.__tpLoginSky.setWeather(b), bucket)
  await page.waitForTimeout(900)
  const info = await page.evaluate(() => {
    const k = window.__tpLoginSky
    return { bucket: k.weather.bucket, clouds: k.weather.cloudCount }
  })
  await page.screenshot({ path: `${DIR}\\${bucket}.png` })
  console.log(`  ${bucket.padEnd(9)} 云=${String(info.clouds).padStart(2)} 档位=${info.bucket}`)
}

// 回到自动 + 打开城市选择弹窗
await page.evaluate(() => window.__tpLoginSky.setWeather('auto'))
await page.waitForTimeout(400)
await page.click('.sky-change')
await page.waitForTimeout(1200)
const dlg = await page.evaluate(() => {
  const d = document.querySelector('.region-dialog')
  const opts = document.querySelectorAll('.el-cascader-node').length
  return { open: Boolean(d), firstLevel: opts }
})
console.log('  城市选择弹窗:', JSON.stringify(dlg))
await page.screenshot({ path: `${DIR}\\dialog.png` })

console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
await b.close()