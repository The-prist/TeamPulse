import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const DIR = 'E:\\codestorge\\.run-logs\\paneltrans2'
fs.mkdirSync(DIR, { recursive: true })
const b = await chromium.launch({ channel: 'msedge', headless: true })
const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 160)))
page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text().slice(0, 160)) })

async function toEntry(theme) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.evaluate((t) => {
    localStorage.setItem('tp_theme', t)
    localStorage.setItem('tp_theme_manual', '1')
    localStorage.removeItem('preferredPanel')
    localStorage.removeItem('tp_token')
  }, theme)
  await page.reload({ waitUntil: 'networkidle' })
  await page.waitForTimeout(1100)
  await page.fill('.form-card input[autocomplete="username"]', 'admin')
  await page.fill('.form-card input[type="password"]', 'adminpassword')
  await page.locator('.submit-btn').click()
  await page.waitForTimeout(2800)
  return page.evaluate(() => location.pathname)
}

const measure = () => page.evaluate(() => {
  const cs = (sel, prop = 'color') => {
    const el = document.querySelector(sel)
    return el ? getComputedStyle(el)[prop] : null
  }
  const parse = (c) => (c || '').match(/\d+/g)?.slice(0, 3).map(Number) || [0, 0, 0]
  const lum = (c) => {
    const [r, g, bl] = parse(c).map((v) => {
      const s = v / 255
      return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4
    })
    return 0.2126 * r + 0.7152 * g + 0.0722 * bl
  }
  const ratio = (a, b) => {
    const l1 = lum(a), l2 = lum(b)
    return Number(((Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05)).toFixed(2))
  }
  const cardBg = cs('.entry-card', 'backgroundColor')
  return {
    dark: document.documentElement.classList.contains('dark'),
    pageBg: cs('.entry-page', 'backgroundColor'),
    cardBg,
    title: cs('.header .title'),
    sub: cs('.header .entry-sub'),
    optionBg: cs('.option', 'backgroundColor'),
    optionTitle: cs('.option-title'),
    optionDesc: cs('.option-desc'),
    contrast: {
      title: ratio(cs('.header .title'), cardBg),
      sub: ratio(cs('.header .entry-sub'), cardBg),
      optionTitle: ratio(cs('.option-title'), cs('.option', 'backgroundColor')),
      optionDesc: ratio(cs('.option-desc'), cs('.option', 'backgroundColor')),
    },
  }
})

console.log('--- 暗色 ---')
console.log('  路径:', await toEntry('dark'))
await page.waitForTimeout(600)
const dark = await measure()
console.log('  ' + JSON.stringify(dark))
await page.screenshot({ path: `${DIR}\\1-dark.png` })

// 点击动效
await page.locator('.option.admin').click()
const seen = []
for (let i = 0; i < 6; i++) {
  await page.waitForTimeout(90)
  seen.push(await page.evaluate(() => {
    const el = document.querySelector('.tp-circle-transition')
    return el ? { bg: getComputedStyle(el).backgroundColor, clip: getComputedStyle(el).clipPath } : null
  }))
}
console.log('  转场采样:')
seen.forEach((s, i) => console.log(`    ${i * 90}ms ${s ? `${s.bg} ${s.clip}` : '（完成）'}`))
await page.waitForTimeout(1600)
console.log('  落点:', await page.evaluate(() => location.pathname))
await page.screenshot({ path: `${DIR}\\2-dark-dashboard.png` })

console.log('--- 亮色 ---')
console.log('  路径:', await toEntry('light'))
await page.waitForTimeout(600)
const light = await measure()
console.log('  ' + JSON.stringify(light))
await page.screenshot({ path: `${DIR}\\3-light.png` })

await b.close()
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')