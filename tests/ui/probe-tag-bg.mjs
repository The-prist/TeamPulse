
import { chromium } from 'playwright-core'
const BASE = process.env.BASE || 'http://www.team-pulse.site'
const run = async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  await page.addInitScript(() => localStorage.setItem('tp_theme', 'dark'))
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
  await page.locator('input').first().fill('admin')
  await page.locator('input[type="password"]').first().fill('adminpassword')
  await Promise.all([page.waitForURL(u => !u.pathname.includes('/login'), { timeout: 15000 }).catch(() => {}), page.keyboard.press('Enter')])
  await page.waitForTimeout(1200)
  if (page.url().includes('/entry')) { await page.locator('button.option.admin').first().click().catch(() => {}); await page.waitForTimeout(1500) }
  await page.goto(`${BASE}/attendance/records`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  const info = await page.evaluate(() => {
    const el = [...document.querySelectorAll('.el-tag__content')].find(e => e.textContent.includes('正常'))
    if (!el) return { found: false }
    const chain = []
    let n = el
    for (let i = 0; i < 6 && n; i++) {
      const s = getComputedStyle(n)
      chain.push({ tag: n.tagName.toLowerCase(), cls: (n.className || '').toString().slice(0, 40), bg: s.backgroundColor, color: s.color })
      n = n.parentElement
    }
    return { found: true, chain }
  })
  console.log(JSON.stringify(info, null, 2))
  await b.close()
}
await run()
