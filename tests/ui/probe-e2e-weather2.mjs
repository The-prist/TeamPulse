// 端到端验证：天气接口 → 主页表现
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\e2e-weather'
fs.mkdirSync(DIR, { recursive: true })

const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 200)))
page.on('console', (m) => {
  if (m.type() === 'error') errs.push(m.text().slice(0, 200))
})

async function snap(tag) {
  const st = await page.evaluate(() => {
    const card = document.querySelector('.sky-card')
    const k = window.__tpLoginSky
    return {
      card: card ? card.innerText.replace(/\n+/g, ' | ') : '(无)',
      bucket: k?.weather.bucket,
      clouds: k?.weather.cloudCount,
      city: k?.weather.city,
      temp: k?.weather.temperature,
      applies: k?.weather.intensityApplies,
      disabled: document.querySelectorAll('.dc-select')[1]?.disabled,
    }
  })
  console.log(
    `  [${tag}] 城市=${st.city} ${st.temp}°C 档位=${st.bucket} 云=${st.clouds} 强度可用=${st.applies} 下拉禁用=${st.disabled}`,
  )
  console.log(`        卡片: ${st.card}`)
  return st
}

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(2000)
await page.evaluate(() => localStorage.removeItem('tp_sky_region'))
await page.reload({ waitUntil: 'networkidle' })
await page.waitForTimeout(2200)
const auto = await snap('自动 IP')
await page.screenshot({ path: `${DIR}\\1-auto.png` })

// 手动选择 西安市（接口侧：云量 100%、天气码 3 → 阴）
await page.click('.sky-change')
await page.waitForTimeout(900)
await page.fill('.region-dialog .el-cascader input', '西安')
await page.waitForTimeout(900)
const node = page.locator('.el-cascader-node__label').filter({ hasText: /^西安市$/ }).first()
if (await node.count()) {
  await node.click()
  await page.waitForTimeout(500)
}
await page.keyboard.press('Escape')
await page.waitForTimeout(400)
await page.locator('.region-dialog .el-button--primary').click()
await page.waitForTimeout(3000)
const manual = await snap('手动 西安')
await page.screenshot({ path: `${DIR}\\2-xian.png` })

await page.reload({ waitUntil: 'networkidle' })
await page.waitForTimeout(2500)
const persist = await snap('刷新后')
await page.screenshot({ path: `${DIR}\\3-persist.png` })

console.log('  --- 强度下拉是否跟随天气启用/禁用 ---')
for (const bucket of ['clear', 'overcast', 'snow', 'rain']) {
  await page.evaluate((bb) => window.__tpLoginSky.setWeather(bb), bucket)
  await page.waitForTimeout(800)
  const st = await page.evaluate(() => ({
    applies: window.__tpLoginSky.weather.intensityApplies,
    disabled: document.querySelectorAll('.dc-select')[1]?.disabled,
    clouds: window.__tpLoginSky.weather.cloudCount,
  }))
  console.log(`  ${bucket.padEnd(9)} 可用=${st.applies} 禁用=${st.disabled} 云=${st.clouds}`)
}
await page.screenshot({ path: `${DIR}\\4-rain-xian.png` })

await page.evaluate(() => window.__tpLoginSky.setWeather('auto'))
await b.close()
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
console.log(
  `  结论: 自动=${auto.bucket}(${auto.clouds}云) 手动=${manual.city}/${manual.bucket}(${manual.clouds}云) 刷新后=${persist.city}`,
)
