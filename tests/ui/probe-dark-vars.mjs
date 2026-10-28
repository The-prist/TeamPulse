import { chromium } from 'playwright-core'
const BASE = process.env.BASE
const b = await chromium.launch({ channel: 'msedge', headless: true })
const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.evaluate(() => { localStorage.setItem('tp_theme', 'dark'); localStorage.setItem('tp_theme_manual', '1') })
await page.evaluate(() => localStorage.removeItem('preferredPanel'))
await page.fill('.form-card input[autocomplete="username"]', 'admin')
await page.fill('.form-card input[type="password"]', 'adminpassword')
await page.locator('.submit-btn').click()
await page.waitForTimeout(2800)
const info = await page.evaluate(() => {
  const root = getComputedStyle(document.documentElement)
  const sub = document.querySelector('.header .subtitle')
  const title = document.querySelector('.header .title')
  return {
    path: location.pathname,
    varMuted: root.getPropertyValue('--tp-text-muted').trim(),
    varStrong: root.getPropertyValue('--tp-text-strong').trim(),
    subColor: sub ? getComputedStyle(sub).color : null,
    subMatched: sub ? getComputedStyle(sub).getPropertyValue('color') : null,
    titleColor: title ? getComputedStyle(title).color : null,
    htmlClass: document.documentElement.className,
  }
})
console.log('  ', JSON.stringify(info, null, 1))
await b.close()