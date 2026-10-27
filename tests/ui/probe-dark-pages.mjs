// 暗色下之前问题最严重的两个页面：出勤记录（chip/标签）、用户管理（整页浅灰底）
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const OUT = 'E:\\codestorge\\.run-logs\\theme-dark-pages'
fs.mkdirSync(OUT, { recursive: true })

const run = async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(String(e)))
  await page.addInitScript(() => localStorage.setItem('tp_theme', 'dark'))
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
  await page.locator('input').first().fill('admin')
  await page.locator('input[type="password"]').first().fill('adminpassword')
  await Promise.all([page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 15000 }).catch(() => {}), page.keyboard.press('Enter')])
  await page.waitForTimeout(1200)
  if (page.url().includes('/entry')) {
    await page.locator('button.option.admin').first().click().catch(() => {})
    await page.waitForTimeout(1500)
  }
  for (const [name, path] of [['records', '/attendance/records'], ['users', '/users'], ['rule', '/attendance/rule'], ['company', '/overview/company']]) {
    await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(1000)
    fs.writeFileSync(`${OUT}\\${name}.png`, await page.screenshot())
    const dark = await page.evaluate(() => document.documentElement.classList.contains('dark'))
    console.log(`  ${path} → dark=${dark}`)
  }
  console.log(errs.length ? `错误: ${errs[0]}` : '  无报错')
  await b.close()
}
await run()
