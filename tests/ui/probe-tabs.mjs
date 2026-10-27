// 选项卡可见度检查：量「边界与背景的对比度」并截图（亮/暗各来一遍）
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const OUT = 'E:\\codestorge\\.run-logs\\tabs'
fs.mkdirSync(OUT, { recursive: true })

const PAGES = [
  ['日程管理', '/schedule'],
  ['打卡记录', '/attendance/records'],
  ['评论管理', '/comments'],
  ['日志管理', '/worklogs'],
]

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
  const ratio = (a, b) => {
    const l1 = lum(a)
    const l2 = lum(b)
    return Math.round(((Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05)) * 100) / 100
  }

  // 卡片背景（tab 通常落在卡片上）
  const card = document.querySelector('.el-card') || document.body
  const cardBg = parse(getComputedStyle(card).backgroundColor) || { r: 255, g: 255, b: 255, a: 1 }

  const out = []
  // 分段控件
  const group = document.querySelector('.el-radio-group')
  if (group) {
    const gs = getComputedStyle(group)
    const segs = [...group.querySelectorAll('.el-radio-button__inner')].map((el) => {
      const s = getComputedStyle(el)
      const border = parse(s.borderColor)
      const bg = parse(s.backgroundColor)
      return {
        text: el.textContent.trim().slice(0, 8),
        checked: el.previousElementSibling?.classList?.contains('is-active') || el.closest('.el-radio-button')?.classList?.contains('is-active'),
        color: s.color,
        fgVsCard: ratio(parse(s.color), cardBg),
        bgVsCard: bg && bg.a > 0 ? ratio(bg, cardBg) : 0,
        borderVsCard: border ? ratio(border, cardBg) : 0,
      }
    })
    out.push({
      kind: 'segmented',
      containerBgVsCard: ratio(parse(gs.backgroundColor) || cardBg, cardBg),
      containerBorderVsCard: ratio(parse(gs.borderColor) || cardBg, cardBg),
      segs,
    })
  }
  // el-tabs
  const tabs = document.querySelector('.el-tabs')
  if (tabs) {
    const items = [...tabs.querySelectorAll('.el-tabs__item')].map((el) => {
      const s = getComputedStyle(el)
      return {
        text: el.textContent.trim().slice(0, 10),
        active: el.classList.contains('is-active'),
        color: s.color,
        fgVsCard: ratio(parse(s.color), cardBg),
        bgVsCard: (() => { const b = parse(s.backgroundColor); return b && b.a > 0 ? ratio(b, cardBg) : 0 })(),
      }
    })
    const track = tabs.querySelector('.el-tabs__nav-wrap')
    out.push({
      kind: 'tabs',
      trackVsCard: track ? ratio(parse(getComputedStyle(track, '::after').backgroundColor || 'rgb(0,0,0)'), cardBg) : null,
      items,
    })
  }
  return out
}

const run = async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  for (const theme of ['light', 'dark']) {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 820 } })
    const page = await ctx.newPage()
    await page.addInitScript((t) => localStorage.setItem('tp_theme', t), theme)
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
    console.log(`\n===== ${theme} =====`)
    for (const [name, path] of PAGES) {
      await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle' })
      await page.waitForTimeout(800)
      const data = await page.evaluate(measure)
      const tabBox = await page.locator('.el-radio-group, .el-tabs__header').first().boundingBox().catch(() => null)
      if (tabBox) {
        await page.screenshot({
          path: `${OUT}\\${theme}-${name}.png`,
          clip: { x: Math.max(0, tabBox.x - 16), y: Math.max(0, tabBox.y - 16), width: Math.min(760, tabBox.width + 32), height: tabBox.height + 32 },
        })
      }
      console.log(`  ${name}`)
      for (const d of data) {
        if (d.kind === 'segmented') {
          console.log(`    分段容器 底/卡=${d.containerBgVsCard}  边框/卡=${d.containerBorderVsCard}`)
          for (const s of d.segs) {
            console.log(`      段「${s.text}」 selected=${s.checked} 文字/卡=${s.fgVsCard} 底/卡=${s.bgVsCard}`)
          }
        } else {
          console.log(`    选项卡轨道/卡=${d.trackVsCard}`)
          for (const s of d.items) {
            console.log(`      「${s.text}」 active=${s.active} 文字/卡=${s.fgVsCard} 底/卡=${s.bgVsCard}`)
          }
        }
      }
    }
    await ctx.close()
  }
  await browser.close()
}
await run()
