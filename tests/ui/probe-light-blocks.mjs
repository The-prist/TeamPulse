// 亮色对比度检查：卡片/表格分隔线/表头底 与页面背景的比值 + 截图
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const OUT = 'E:\\codestorge\\.run-logs\\light'
fs.mkdirSync(OUT, { recursive: true })

const measure = () => {
  const parse = (c) => {
    const m = c.match(/rgba?\(([^)]+)\)/)
    if (!m) return null
    const p = m[1].split(/[,\s/]+/).map(Number)
    return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 }
  }
  const lum = ({ r, g, b }) => {
    const f = (v) => {
      const s = v / 255
      return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
  }
  const ratio = (a, b) => Math.round(((Math.max(lum(a), lum(b)) + 0.05) / (Math.min(lum(a), lum(b)) + 0.05)) * 100) / 100
  const bg1 = (el) => (el ? parse(getComputedStyle(el).backgroundColor) : null)

  const page = parse(getComputedStyle(document.body).backgroundColor)
  const card = document.querySelector('.el-card')
  const cardStyle = card ? getComputedStyle(card) : null
  const table = document.querySelector('.el-table')
  const th = document.querySelector('.el-table th.el-table__cell')
  const td = document.querySelector('.el-table td.el-table__cell')
  const cardBody = card ? card.querySelector('.el-card__body') : null

  return {
    pageBg: getComputedStyle(document.body).backgroundColor,
    cardBg: cardBody ? getComputedStyle(cardBody).backgroundColor : null,
    cardBorderVsPage: cardStyle ? ratio(parse(cardStyle.borderTopColor), page) : null,
    cardBgVsPage: cardBody ? ratio(bg1(cardBody), page) : null,
    thBg: th ? getComputedStyle(th).backgroundColor : null,
    thBgVsCard: th && cardBody ? ratio(bg1(th), bg1(cardBody)) : null,
    rowDivider: td ? getComputedStyle(td).borderBottomColor : null,
    rowDividerVsCard: td && cardBody ? ratio(parse(getComputedStyle(td).borderBottomColor), bg1(cardBody)) : null,
    tableBorderVsCard: table && cardBody ? ratio(parse(getComputedStyle(table).borderTopColor), bg1(cardBody)) : null,
    rowCount: document.querySelectorAll('.el-table__body tbody tr').length,
  }
}

const run = async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  await page.addInitScript(() => localStorage.setItem('tp_theme', 'light'))
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

  for (const [name, path] of [['设备管理', '/attendance/devices'], ['用户管理', '/users'], ['审计日志', '/audit']]) {
    await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(1000)
    const m = await page.evaluate(measure)
    console.log(`\n${name}`)
    console.log(`  页面底 ${m.pageBg}  卡片底 ${m.cardBg}`)
    console.log(`  卡片底/页面底 = ${m.cardBgVsPage}   卡片边框/页面底 = ${m.cardBorderVsPage}`)
    console.log(`  表头底/卡片底 = ${m.thBgVsCard}      行分隔线/卡片底 = ${m.rowDividerVsCard}`)
    console.log(`  表格外框/卡片底 = ${m.tableBorderVsCard}   行数 = ${m.rowCount}`)
    await page.screenshot({ path: `${OUT}\\${name}.png` })
  }
  await browser.close()
}
await run()
