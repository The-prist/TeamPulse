// 暗色模式对比度审计：逐页扫描文本节点，算 WCAG 对比度，列出不达标的元素
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const THEME = process.env.THEME || 'dark'
const ROLES = [
  { user: 'admin', pass: 'adminpassword', panel: 'admin' },
  { user: 'lisi', pass: '123456', panel: 'user' },
]

const ADMIN_PAGES = ['/dashboard', '/overview/company', '/attendance/dashboard', '/attendance/records',
  '/attendance/devices', '/attendance/rule', '/attendance/approvals', '/kanban', '/schedule',
  '/comments', '/worklogs', '/audit', '/users', '/teams']
const USER_PAGES = ['/u/home', '/u/overview/leader', '/u/schedules', '/u/attendance', '/u/kanban',
  '/u/worklogs', '/u/teams', '/u/profile']

const auditJs = () => {
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
  // 注意：a 必须按「叠加」公式算，不能直接置 1。
  // 之前置 1 会让半透明白（如 rgba(255,255,255,.08)）被当成不透明白，
  // 于是把暗底上的标签误判成 1.86:1 —— 是审计工具本身的假阳性。
  const blend = (fg, bg) => ({
    r: fg.r * fg.a + bg.r * (1 - fg.a),
    g: fg.g * fg.a + bg.g * (1 - fg.a),
    b: fg.b * fg.a + bg.b * (1 - fg.a),
    a: fg.a + bg.a * (1 - fg.a),
  })
  const bgOf = (el) => {
    let node = el
    let acc = null
    while (node && node !== document.documentElement) {
      const st = getComputedStyle(node)
      // 渐变/图片背景没法用 backgroundColor 表达（例如品牌块、统计卡），
      // 继续往上找会把它的浅色父级当成背景，得出 1.02:1 这种假阳性。遇到就不判。
      if (st.backgroundImage && st.backgroundImage !== 'none') return null
      const c = parse(st.backgroundColor)
      if (c && c.a > 0) {
        acc = acc ? blend(acc, c) : c
        if (acc.a >= 0.999) return acc
      }
      node = node.parentElement
    }
    const bodyBg = parse(getComputedStyle(document.body).backgroundColor) || { r: 13, g: 17, b: 23, a: 1 }
    return acc ? blend(acc, bodyBg) : bodyBg
  }
  const ratio = (a, b) => {
    const l1 = lum(a)
    const l2 = lum(b)
    return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05)
  }

  const out = []
  for (const el of document.querySelectorAll('body *')) {
    // 只看直接含文字的元素
    const own = Array.from(el.childNodes).some((n) => n.nodeType === 3 && n.textContent.trim().length > 0)
    if (!own) continue
    const rect = el.getBoundingClientRect()
    if (rect.width < 2 || rect.height < 2) continue
    const st = getComputedStyle(el)
    if (st.visibility === 'hidden' || st.display === 'none' || Number(st.opacity) < 0.15) continue

    const fg = parse(st.color)
    if (!fg) continue
    const bg = bgOf(el)
    if (!bg) continue // 背景含渐变，无法可靠计算
    const r = ratio(blend(fg, bg), bg)
    const fontSize = parseFloat(st.fontSize)
    const bold = Number(st.fontWeight) >= 600
    const large = fontSize >= 24 || (fontSize >= 18.66 && bold)
    const need = large ? 3 : 4.5
    if (r < need) {
      out.push({
        sel: el.tagName.toLowerCase() + (el.className && typeof el.className === 'string'
          ? '.' + el.className.trim().split(/\s+/).slice(0, 3).join('.')
          : ''),
        text: (el.textContent || '').trim().slice(0, 30),
        color: st.color,
        bg: `rgb(${Math.round(bg.r)}, ${Math.round(bg.g)}, ${Math.round(bg.b)})`,
        ratio: Math.round(r * 100) / 100,
        need,
        size: st.fontSize,
      })
    }
  }
  return out
}

const run = async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const all = []
  for (const r of ROLES) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
    const page = await ctx.newPage()
    // 预置暗色，避免登录页/入口页先渲染亮色
    await page.addInitScript((t) => localStorage.setItem('tp_theme', t), THEME)
    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
    await page.locator('input').first().fill(r.user)
    await page.locator('input[type="password"]').first().fill(r.pass)
    await Promise.all([
      page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 15000 }).catch(() => {}),
      page.keyboard.press('Enter'),
    ])
    await page.waitForTimeout(1200)
    if (page.url().includes('/entry')) {
      const opt = r.panel === 'admin' ? 'button.option.admin' : 'button.option.user'
      await page.locator(opt).first().click().catch(() => {})
      await page.waitForTimeout(1500)
    }
    const pages = r.panel === 'admin' ? ADMIN_PAGES : USER_PAGES
    for (const p of pages) {
      await page.goto(`${BASE}${p}`, { waitUntil: 'networkidle' })
      await page.waitForTimeout(700)
      const dark = await page.evaluate(() => document.documentElement.classList.contains('dark'))
      const items = await page.evaluate(auditJs)
      for (const it of items) all.push({ page: p, dark, ...it })
    }
    await ctx.close()
  }
  await browser.close()

  // 聚合：同一 selector + 相同颜色合并
  const agg = new Map()
  for (const it of all) {
    const key = `${it.sel}|${it.color}|${it.bg}`
    if (!agg.has(key)) agg.set(key, { ...it, pages: new Set([it.page]), count: 0 })
    const e = agg.get(key)
    e.pages.add(it.page)
    e.count++
    if (it.ratio < e.ratio) e.ratio = it.ratio
  }
  const list = [...agg.values()].sort((a, b) => a.ratio - b.ratio)
  console.log(`${THEME === 'dark' ? '暗色' : '亮色'}模式低对比文字：${all.length} 处，去重后 ${list.length} 类\n`)
  for (const e of list.slice(0, 40)) {
    console.log(`  ${String(e.ratio).padEnd(5)} (需 ${e.need}) ${e.sel.slice(0, 58).padEnd(58)} ${e.color} on ${e.bg}`)
    console.log(`        「${e.text}」  页面: ${[...e.pages].slice(0, 3).join(', ')}`)
  }
  fs.writeFileSync(`E:\\codestorge\\.run-logs\\contrast-${THEME}.json`, JSON.stringify(list, null, 2))
  console.log(`\n明细已写入 .run-logs/contrast-${THEME}.json`)
}
await run()
