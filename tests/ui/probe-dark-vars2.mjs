import { chromium } from 'playwright-core'
const BASE = process.env.BASE
const b = await chromium.launch({ channel: 'msedge', headless: true })
const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
// 先设置主题再进入页面（确保 initTheme 生效）
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.evaluate(() => {
  localStorage.setItem('tp_theme', 'dark')
  localStorage.setItem('tp_theme_manual', '1')
  localStorage.removeItem('preferredPanel')
})
await page.reload({ waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.fill('.form-card input[autocomplete="username"]', 'admin')
await page.fill('.form-card input[type="password"]', 'adminpassword')
await page.locator('.submit-btn').click()
await page.waitForTimeout(2800)
const info = await page.evaluate(() => {
  const root = getComputedStyle(document.documentElement)
  const sub = document.querySelector('.header .subtitle')
  const title = document.querySelector('.header .title')
  const card = document.querySelector('.entry-card')
  return {
    path: location.pathname,
    htmlClass: document.documentElement.className,
    varMuted: root.getPropertyValue('--tp-text-muted').trim(),
    varStrong: root.getPropertyValue('--tp-text-strong').trim(),
    varSurface: root.getPropertyValue('--tp-surface').trim(),
    subColor: sub && getComputedStyle(sub).color,
    titleColor: title && getComputedStyle(title).color,
    cardBg: card && getComputedStyle(card).backgroundColor,
    pageBg: getComputedStyle(document.querySelector('.entry-page')).backgroundColor,
  }
})
console.log('  ', JSON.stringify(info, null, 1))
await b.close()