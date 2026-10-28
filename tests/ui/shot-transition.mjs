// 抓一张转场中途的截图
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\logineffects'
fs.mkdirSync(DIR, { recursive: true })

const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1600)
await page.fill('.form-card input[autocomplete="username"]', 'admin')
await page.fill('.form-card input[type="password"]', 'adminpassword')
await page.locator('.submit-btn').click()
await page.waitForTimeout(300)
await page.screenshot({ path: `${DIR}\\transition-mid.png` })
console.log('  转场中途截图完成')
await page.waitForTimeout(2000)
await page.screenshot({ path: `${DIR}\\after-login-dark.png` })
await b.close()
