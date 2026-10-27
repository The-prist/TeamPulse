// 端到端验证：天气接口 → 主页表现（级联搜索用 suggestion-item）
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
const calls = []
page.on('response', async (r) => {
  if (r.url().includes('sky-context')) {
    try {
      const j = await r.json()
      calls.push({ url: decodeURIComponent(r.url().split('?')[1] || '(无参数)'), data: j.data })
    } catch {}
  }
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
      sky: k?.sky.top,
    }
  })
  console.log(`  [${tag}] ${st.city} ${st.temp}°C ${st.bucket} 云=${st.clouds} 天色=${st.sky}`)
  console.log(`        卡片: ${st.card}`)
  return st
}

/** 用级联搜索选中某个市/区 */
async function pickCity(keyword, exact) {
  await page.click('.sky-change')
  await page.waitForTimeout(800)
  await page.fill('.region-dialog .el-cascader input', keyword)
  await page.waitForTimeout(900)
  const item = page.locator('.el-cascader__suggestion-item').filter({ hasText: exact }).first()
  const n = await item.count()
  if (!n) {
    console.log(`  ! 未找到建议项: ${exact}`)
    await page.keyboard.press('Escape')
    await page.locator('.region-dialog .el-button').first().click()
    return false
  }
  await item.click()
  await page.waitForTimeout(500)
  await page.locator('.region-dialog .el-button--primary').click()
  await page.waitForTimeout(2800)
  return true
}

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1800)
await page.evaluate(() => localStorage.removeItem('tp_sky_region'))
await page.reload({ waitUntil: 'networkidle' })
await page.waitForTimeout(2200)
await snap('自动 IP')
await page.screenshot({ path: `${DIR}\\1-auto.png` })

console.log('  --- 手动改成 西安市（接口侧云量 100%、天气码 3 → 阴）---')
await pickCity('西安', '西安市')
await snap('手动 西安')
await page.screenshot({ path: `${DIR}\\2-xian.png` })

console.log('  --- 刷新看是否记住 ---')
await page.reload({ waitUntil: 'networkidle' })
await page.waitForTimeout(2500)
await snap('刷新后')

console.log('  --- 再改成 深圳市南山区（云量 83% → 阴）---')
await pickCity('南山', '南山区')
await snap('手动 南山')
await page.screenshot({ path: `${DIR}\\3-nanshan.png` })

console.log('  --- 恢复 IP 定位 ---')
await page.locator('.sky-reset').click()
await page.waitForTimeout(2500)
await snap('恢复 IP')

console.log('  --- 强度下拉跟随天气 ---')
for (const bucket of ['clear', 'overcast', 'snow', 'rain']) {
  await page.evaluate((bb) => window.__tpLoginSky.setWeather(bb), bucket)
  await page.waitForTimeout(700)
  const st = await page.evaluate(() => ({
    applies: window.__tpLoginSky.weather.intensityApplies,
    disabled: document.querySelectorAll('.dc-select')[1]?.disabled,
  }))
  console.log(`  ${bucket.padEnd(9)} 可用=${st.applies} 禁用=${st.disabled}`)
}
await page.evaluate(() => window.__tpLoginSky.setWeather('auto'))

console.log('  --- 接口调用记录 ---')
for (const c of calls) console.log(`    ${c.url} → ${c.data.city} ${c.data.bucket || ''} 云=${c.data.cloudCover}% source=${c.data.source}`)

await b.close()
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
