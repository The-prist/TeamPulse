// 精确复现「日程管理打不开」：登录 → /schedule → 点第一条详情 → 抓错误与截图
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const USER = process.env.USER_NAME || 'admin'
const PASS = process.env.USER_PASS || 'adminpassword'

const run = async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()

  const errors = []
  const logs = []
  page.on('pageerror', (e) => errors.push('[pageerror] ' + String(e)))
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push('[console.error] ' + m.text())
    else logs.push(m.text())
  })

  // ---- 登录
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
  await page.locator('input').first().fill(USER)
  await page.locator('input[type="password"]').first().fill(PASS)
  await Promise.all([
    page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 15000 }).catch(() => {}),
    page.keyboard.press('Enter'),
  ])
  await page.waitForTimeout(1500)
  console.log('登录后 URL:', page.url())

  // 管理员首次登录会停在 /entry，必须先选面板
  if (page.url().includes('/entry')) {
    const adminOpt = page.locator('button.option.admin').first()
    if (await adminOpt.count()) {
      await adminOpt.click()
      await page.waitForTimeout(2000)
      console.log('选择「管理后台」后 URL:', page.url())
    }
  }

  // ---- 日程管理列表
  await page.goto(`${BASE}/schedule`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(2500)
  const listText = await page.locator('body').innerText()
  console.log('列表页 URL:', page.url())
  console.log('列表页是否出现「页面出现异常」:', listText.includes('页面出现异常'))
  console.log('列表页文本长度:', listText.replace(/\s/g, '').length)

  // 行数（el-table 的 tbody tr）
  const rowCount = await page.locator('.el-table__body tbody tr').count().catch(() => 0)
  console.log('表格行数:', rowCount)

  // ---- 点第一条「详情」
  const detailBtn = page.locator('button:has-text("详情")').first()
  const hasDetail = await detailBtn.count()
  console.log('找到「详情」按钮:', hasDetail)
  if (hasDetail) {
    await detailBtn.click()
    await page.waitForTimeout(2500)
    const url = page.url()
    const text = await page.locator('body').innerText()
    console.log('详情页 URL:', url)
    console.log('详情页出现「页面出现异常」:', text.includes('页面出现异常'))
    console.log('详情页文本长度:', text.replace(/\s/g, '').length)
    console.log('详情页前 200 字:', text.replace(/\s+/g, ' ').slice(0, 200))
    fs.writeFileSync('E:\\codestorge\\.run-logs\\schedule-detail.png', await page.screenshot())
  }

  console.log('\n--- 捕获到的错误 ---')
  if (!errors.length) console.log('  （无）')
  for (const e of errors.slice(0, 10)) console.log('  ' + e.slice(0, 300))

  await browser.close()
}

await run()
