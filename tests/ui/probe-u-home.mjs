import { chromium } from 'playwright-core'
import fs from 'node:fs'
const BASE = process.env.BASE
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
await page.addInitScript(() => localStorage.setItem('tp_theme', 'light'))
await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
await page.locator('input').first().fill('lisi')
await page.locator('input[type="password"]').first().fill('123456')
await Promise.all([page.waitForURL(u => !u.pathname.includes('/login'), { timeout: 15000 }).catch(() => {}), page.keyboard.press('Enter')])
await page.waitForTimeout(1500)
if (page.url().includes('/entry')) { await page.locator('button.option.user').first().click().catch(() => {}); await page.waitForTimeout(1500) }
await page.goto(`${BASE}/u/home`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
fs.writeFileSync('E:\\codestorge\\.run-logs\\light\\u-home.png', await page.screenshot())
const info = await page.evaluate(() => {
  const el = document.querySelector('.hello')
  if (!el) return null
  const hero = el.closest('.hero') || el.parentElement
  const s = getComputedStyle(hero)
  return { heroClass: hero.className, bgImage: s.backgroundImage.slice(0, 80), bgColor: s.backgroundColor, color: getComputedStyle(el).color }
})
console.log(JSON.stringify(info))
await b.close()