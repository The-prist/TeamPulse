// 验证：面板选择页的暗色配色 + 点击切换的圆形转场（颜色匹配主题）
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\paneltrans'
fs.mkdirSync(DIR, { recursive: true })

const b = await chromium.launch({ channel: 'msedge', headless: true })
const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 200)))
page.on('console', (m) => {
  if (m.type() === 'error') errs.push(m.text().slice(0, 200))
})

/** 登录并停在 /entry */
async function loginToEntry() {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await page.evaluate(() => localStorage.removeItem('preferredPanel'))
  await page.fill('.form-card input[autocomplete="username"]', 'admin')
  await page.fill('.form-card input[type="password"]', 'adminpassword')
  await page.locator('.submit-btn').click()
  await page.waitForTimeout(2600)
  return page.evaluate(() => location.pathname)
}

const colors = () =>
  page.evaluate(() => {
    const card = document.querySelector('.entry-card')
    const title = document.querySelector('.header .title')
    const sub = document.querySelector('.header .subtitle')
    const opt = document.querySelector('.option')
    const pageEl = document.querySelector('.entry-page')
    const pick = (el, prop = 'color') => (el ? getComputedStyle(el)[prop] : null)
    return {
      pageBg: pick(pageEl, 'backgroundColor'),
      cardBg: pick(card, 'backgroundColor'),
      titleColor: pick(title),
      subColor: pick(sub),
      optionBg: pick(opt, 'backgroundColor'),
      optionBorder: pick(opt, 'borderTopColor'),
      dark: document.documentElement.classList.contains('dark'),
    }
  })

// ---------- 暗色模式 ----------
console.log('--- 暗色模式 ---')
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.evaluate(() => {
  localStorage.setItem('tp_theme', 'dark')
  localStorage.setItem('tp_theme_manual', '1')
})
const path1 = await loginToEntry()
console.log('  当前路径:', path1)
await page.waitForTimeout(800)
console.log('  颜色:', JSON.stringify(await colors()))
await page.screenshot({ path: `${DIR}\\1-dark-entry.png` })

// ---------- 点击卡片的转场 ----------
console.log('--- 点击「管理后台」的转场 ---')
await page.locator('.option.admin').click()
const samples = []
for (let i = 0; i < 8; i++) {
  await page.waitForTimeout(80)
  samples.push(
    await page.evaluate(() => {
      const el = document.querySelector('.tp-circle-transition')
      if (!el) return { present: false }
      const cs = getComputedStyle(el)
      return { present: true, clip: cs.clipPath, bg: cs.backgroundColor }
    }),
  )
}
for (const [i, s] of samples.entries()) {
  console.log(`    ${(i * 80).toString().padStart(3)}ms ${s.present ? `bg=${s.bg} clip=${s.clip}` : '（已移除）'}`)
}
await page.waitForTimeout(1500)
const after = await page.evaluate(() => ({
  path: location.pathname,
  dark: document.documentElement.classList.contains('dark'),
  overlayGone: !document.querySelector('.tp-circle-transition'),
}))
console.log('  结果:', JSON.stringify(after))
await page.screenshot({ path: `${DIR}\\2-dark-dashboard.png` })

// ---------- 亮色模式对照 ----------
console.log('--- 亮色模式 ---')
await page.evaluate(() => {
  localStorage.setItem('tp_theme', 'light')
  localStorage.setItem('tp_theme_manual', '1')
})
await page.evaluate(() => localStorage.removeItem('tp_token'))
const path2 = await loginToEntry()
await page.waitForTimeout(800)
console.log('  颜色:', JSON.stringify(await colors()))
await page.screenshot({ path: `${DIR}\\3-light-entry.png` })

// 顶栏的「切换面板」也应有转场
await page.locator('.option.admin').click()
await page.waitForTimeout(2200)
const topBtn = page.locator('button:has-text("切换面板")').first()
if (await topBtn.count()) {
  await topBtn.click()
  await page.waitForTimeout(200)
  const mid = await page.evaluate(() => {
    const el = document.querySelector('.tp-circle-transition')
    return el ? { present: true, bg: getComputedStyle(el).backgroundColor } : { present: false }
  })
  console.log('  顶栏切换面板转场:', JSON.stringify(mid))
  await page.waitForTimeout(1800)
  console.log('  跳转后路径:', await page.evaluate(() => location.pathname))
  await page.screenshot({ path: `${DIR}\\4-back-to-entry.png` })
} else {
  console.log('  ✗ 未找到顶栏切换按钮')
}

await b.close()
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
