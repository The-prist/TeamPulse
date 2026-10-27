// 逐帧抓主题切换动画：亮→暗 + 暗→亮，确认「扩张铺满 + 中心圆揭开」都看得见
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const OUT = 'E:\\codestorge\\.run-logs\\theme-frames'
fs.mkdirSync(OUT, { recursive: true })

const run = async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1200, height: 760 } })
  const page = await ctx.newPage()
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
  await page.locator('input').first().fill('admin')
  await page.locator('input[type="password"]').first().fill('adminpassword')
  await Promise.all([
    page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 15000 }).catch(() => {}),
    page.keyboard.press('Enter'),
  ])
  await page.waitForTimeout(1200)
  if (page.url().includes('/entry')) {
    await page.locator('button.option.admin').first().click().catch(() => {})
    await page.waitForTimeout(1500)
  }
  await page.goto(`${BASE}/schedule`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1000)

  const shot = async (name) => { fs.writeFileSync(`${OUT}\\${name}.png`, await page.screenshot()) }

  // ---- 亮 → 暗：在 150 / 400 / 700 / 900ms 抓帧
  console.log('=== 亮 → 暗 ===')
  await shot('a0-light')
  await page.locator('button.theme-toggle').click()
  for (const t of [150, 400, 700, 900]) {
    await page.waitForTimeout(t === 150 ? 150 : 250)
    const st = await page.evaluate(() => {
      const o = document.querySelector('.tp-theme-overlay')
      return {
        overlay: !!o,
        reveal: o ? getComputedStyle(o).getPropertyValue('--tp-reveal').trim() : '-',
        mask: o ? (getComputedStyle(o).maskImage || getComputedStyle(o).webkitMaskImage || 'none').slice(0, 60) : '-',
        dark: document.documentElement.classList.contains('dark'),
      }
    })
    console.log(`  ~${t}ms`, JSON.stringify(st))
    await shot(`b-dark-${t}`)
  }
  await page.waitForTimeout(800)

  // ---- 暗 → 亮：同样抓帧
  console.log('\n=== 暗 → 亮 ===')
  await page.locator('button.theme-toggle').click()
  for (const t of [150, 400, 700, 900]) {
    await page.waitForTimeout(t === 150 ? 150 : 250)
    const st = await page.evaluate(() => {
      const o = document.querySelector('.tp-theme-overlay')
      return {
        overlay: !!o,
        reveal: o ? getComputedStyle(o).getPropertyValue('--tp-reveal').trim() : '-',
        mask: o ? (getComputedStyle(o).maskImage || getComputedStyle(o).webkitMaskImage || 'none').slice(0, 60) : '-',
        dark: document.documentElement.classList.contains('dark'),
      }
    })
    console.log(`  ~${t}ms`, JSON.stringify(st))
    await shot(`c-light-${t}`)
  }
  await page.waitForTimeout(800)
  await shot('d-back-light')

  await browser.close()
}
await run()
