import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\e2e-weather'
fs.mkdirSync(DIR, { recursive: true })
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 200)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text().slice(0, 200)) })

async function snapshot(tag) {
  const st = await page.evaluate(() => {
    const card = document.querySelector('.sky-card')
    const k = window.__tpLoginSky
    return {
      card: card ? card.innerText.replace(/\n+/g, ' | ') : '(无卡片)',
      bucket: k?.weather.bucket,
      clouds: k?.weather.cloudCount,
      city: k?.weather.city,
      temp: k?.weather.temperature,
      intensityApplies: k?.weather.intensityApplies,
      selectDisabled: document.querySelectorAll('.dc-select')[1]?.disabled,
      sky: k?.sky.top,
    }
  })
  console.log(`  [${tag}] ${st.city || '?'} ${st.temp}°C ${st.bucket} 云=${st.clouds} 强度可用=${st.intensityApplies} 下拉禁用=${st.selectDisabled} 天色=${st.sky}`)
  console.log(`        卡片: ${st.card}`)
  return st
}

// 1) 自动（IP 定位）
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(2200)
await page.evaluate(() => localStorage.removeItem('tp_sky_region'))
await page.reload({ waitUntil: 'networkidle' })
await page.waitForTimeout(2200)
const auto = await snapshot('自动/IP')
await page.screenshot({ path: `${DIR}\\1-auto.png` })

// 2) 手动选 西安市（API 显示云量 100%、天气码 3 → 阴）
await page.click('.sky-change')
await page.waitForTimeout(1000)
await page.evaluate(() => {
  // 直接操作 store，跳过多级点选
  const vm = document.querySelector('#app').__vue_app__
  return null
})
await page.fill('.region-dialog .el-cascader input', '西安市')
await page.waitForTimeout(700)
const opt = page.locator('.el-cascader-node__label', { hasText: '西安市' }).first()
if (await opt.count()) { await opt.click(); await page.waitForTimeout(300) }
await page.locator('.region-dialog .el-button--primary').click()
await page.waitForTimeout(2500)
const manual = await snapshot('手动/西安')
await page.screenshot({ path: `${DIR}\\2-xian.png` })

// 3) 刷新后是否记住
await page.reload({ waitUntil: 'networkidle' })
await page.waitForTimeout(2500)
const after = await snapshot('刷新后')
await page.screenshot({ path: `${DIR}\\3-persist.png` })

// 4) 强度下拉：晴禁用、雨启用
const intensityCheck = async (bucket) => {
  await page.evaluate((bb) => window.__tpLoginSky.setWeather(bb), bucket)
  await page.waitForTimeout(700)
  const st = await snapshot(`强度-${bucket}`)
  return st
}
await intensityCheck('clear')
await intensityCheck('overcast')
await intensityCheck('rain')
const rainState = await snapshot('雨-终态')
await page.screenshot({ path: `${DIR}\\4-rain.png` })

// 5) 恢复自动
await page.evaluate(() => window.__tpLoginSky.setWeather('auto'))
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
await b.close()