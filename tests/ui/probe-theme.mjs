// 验证主题功能：亮色截图 → 点弯月 → 抓动画中间帧 → 暗色截图 → 刷新看是否持久化
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const OUT = 'E:\\codestorge\\.run-logs\\theme'
fs.mkdirSync(OUT, { recursive: true })

const run = async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push('[pageerror] ' + String(e)))
  page.on('console', (m) => { if (m.type() === 'error') errs.push('[console] ' + m.text()) })

  // 登录
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

  // 日程管理（有选项卡：日历 / 列表）
  await page.goto(`${BASE}/schedule`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)

  console.log('=== 1. 亮色状态 ===')
  console.log('  html.class:', await page.evaluate(() => document.documentElement.className || '(空)'))
  console.log('  主题按钮存在:', await page.locator('button.theme-toggle').count())
  console.log('  localStorage.tp_theme:', await page.evaluate(() => localStorage.getItem('tp_theme')))
  fs.writeFileSync(`${OUT}\\1-light.png`, await page.screenshot())

  // 选项卡度量（可见度是否提高）
  const tabMetrics = await page.evaluate(() => {
    const el = document.querySelector('.el-radio-button__inner, .el-tabs__item')
    if (!el) return null
    const s = getComputedStyle(el)
    return { text: (el.textContent || '').trim().slice(0, 10), fontSize: s.fontSize, fontWeight: s.fontWeight, color: s.color, padding: s.padding }
  })
  console.log('  选项卡样式:', JSON.stringify(tabMetrics))

  // 点弯月
  console.log('\n=== 2. 点击弯月 ===')
  const t0 = Date.now()
  await page.locator('button.theme-toggle').click()
  await page.waitForTimeout(300)
  const mid = await page.evaluate(() => {
    const o = document.querySelector('.tp-theme-overlay')
    return o ? { exists: true, bg: getComputedStyle(o).backgroundColor, transform: getComputedStyle(o).transform } : { exists: false }
  })
  console.log('  动画中（300ms）:', JSON.stringify(mid))
  fs.writeFileSync(`${OUT}\\2-mid.png`, await page.screenshot())

  await page.waitForTimeout(500)
  const mid2 = await page.evaluate(() => {
    const o = document.querySelector('.tp-theme-overlay')
    return o ? { exists: true, revealing: o.classList.contains('is-revealing'), reveal: getComputedStyle(document.documentElement).getPropertyValue('--tp-reveal') } : { exists: false }
  })
  console.log('  揭开阶段（800ms）:', JSON.stringify(mid2))
  fs.writeFileSync(`${OUT}\\3-reveal.png`, await page.screenshot())

  await page.waitForTimeout(900)
  const after = await page.evaluate(() => ({
    overlay: !!document.querySelector('.tp-theme-overlay'),
    cls: document.documentElement.className,
    stored: localStorage.getItem('tp_theme'),
    bodyBg: getComputedStyle(document.body).backgroundColor,
    textColor: getComputedStyle(document.body).color,
  }))
  console.log('  结束后:', JSON.stringify(after))
  fs.writeFileSync(`${OUT}\\4-dark.png`, await page.screenshot())

  // 刷新看持久化
  console.log('\n=== 3. 刷新后是否保持暗色 ===')
  await page.reload({ waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  const reloaded = await page.evaluate(() => ({
    cls: document.documentElement.className,
    stored: localStorage.getItem('tp_theme'),
    bodyBg: getComputedStyle(document.body).backgroundColor,
  }))
  console.log('  ', JSON.stringify(reloaded))
  fs.writeFileSync(`${OUT}\\5-reload-dark.png`, await page.screenshot())

  // 再切回亮色
  console.log('\n=== 4. 再点一次切回亮色 ===')
  await page.locator('button.theme-toggle').click()
  await page.waitForTimeout(1400)
  const back = await page.evaluate(() => ({
    cls: document.documentElement.className,
    stored: localStorage.getItem('tp_theme'),
    bodyBg: getComputedStyle(document.body).backgroundColor,
  }))
  console.log('  ', JSON.stringify(back))
  fs.writeFileSync(`${OUT}\\6-back-light.png`, await page.screenshot())

  console.log('\n=== 错误 ===')
  console.log(errs.length ? errs.slice(0, 5).map((e) => '  ' + e.slice(0, 200)).join('\n') : '  （无）')
  await browser.close()
}
await run()
