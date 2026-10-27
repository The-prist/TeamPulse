// 诊断：日程管理列表为什么没有数据
import { chromium } from 'playwright-core'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const run = async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
  const api = []
  page.on('response', async (r) => {
    const u = r.url()
    if (u.includes('/api/v1/schedule')) {
      let body = ''
      try { body = (await r.text()).slice(0, 300) } catch { }
      api.push(`${r.status()} ${u.replace(BASE, '')}  ← ${body.replace(/\s+/g, ' ')}`)
    }
  })
  const errs = []
  page.on('pageerror', (e) => errs.push(String(e)))
  page.on('console', (m) => { if (m.type() === 'error') errs.push('[console] ' + m.text()) })

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
  await page.waitForTimeout(2000)

  const text = await page.locator('body').innerText()
  console.log('=== 页面文本 ===')
  console.log(text.replace(/\n{3,}/g, '\n\n').slice(0, 1200))

  console.log('\n=== 表格结构探测 ===')
  for (const sel of ['.el-table__body tbody tr', '.el-table__body-wrapper tbody tr', 'tbody tr', '.el-table__row', '.el-empty']) {
    console.log(`  ${sel} → ${await page.locator(sel).count()}`)
  }

  console.log('\n=== 日程接口调用 ===')
  if (!api.length) console.log('  （没有任何 /api/v1/schedule 请求）')
  for (const a of api) console.log('  ' + a)

  console.log('\n=== 错误 ===')
  console.log(errs.length ? errs.slice(0, 5).map((e) => '  ' + e.slice(0, 200)).join('\n') : '  （无）')

  await browser.close()
}
await run()
