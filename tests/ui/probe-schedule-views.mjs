// 定位「日程管理打不开」：分别测 列表视图的详情按钮 与 日历上点日程
import { chromium } from 'playwright-core'

const BASE = process.env.BASE || 'http://www.team-pulse.site'

const run = async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
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

  // ---------- A. 列表视图
  console.log('=== A. 切到「列表」视图 ===')
  await page.goto(`${BASE}/schedule`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  const tab = page.locator('.el-radio-button:has-text("列表"), .el-tabs__item:has-text("列表"), button:has-text("列表")').first()
  console.log('  找到「列表」切换控件:', await tab.count())
  if (await tab.count()) {
    await tab.click()
    await page.waitForTimeout(2000)
    const rows = await page.locator('.el-table__row').count()
    console.log('  切换后表格行数:', rows)
    const btn = page.locator('button:has-text("详情")').first()
    console.log('  找到「详情」按钮:', await btn.count())
    if (await btn.count()) {
      await btn.click()
      await page.waitForTimeout(2500)
      const text = await page.locator('body').innerText()
      console.log('  点击后 URL:', page.url())
      console.log('  是否「页面出现异常」:', text.includes('页面出现异常'))
      console.log('  是否含「日程详情」:', text.includes('日程详情'))
      console.log('  正文片段:', text.replace(/\s+/g, ' ').slice(0, 220))
    }
  }

  // ---------- B. 日历视图点日程
  console.log('\n=== B. 日历视图点击日程 ===')
  await page.goto(`${BASE}/schedule`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  const ev = page.locator('.el-calendar__body .event-item, .cal-event, .event, .schedule-item').first()
  console.log('  找到日历事件元素:', await ev.count())
  if (await ev.count()) {
    const label = (await ev.innerText().catch(() => '')).slice(0, 30)
    console.log('  事件文本:', label.replace(/\s+/g, ' '))
    await ev.click()
    await page.waitForTimeout(2000)
    console.log('  点击后 URL:', page.url())
  } else {
    // 退而求其次：点日历里出现的第一个日程标题文字
    const txt = page.locator('text=安全自查').first()
    console.log('  用文字定位「安全自查」:', await txt.count())
    if (await txt.count()) {
      await txt.click()
      await page.waitForTimeout(2000)
      console.log('  点击后 URL:', page.url())
    }
  }

  console.log('\n=== 错误 ===')
  console.log(errs.length ? errs.slice(0, 6).map((e) => '  ' + e.slice(0, 220)).join('\n') : '  （无）')
  await browser.close()
}
await run()
