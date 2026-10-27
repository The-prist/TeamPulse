import { chromium } from 'playwright-core'
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1200, height: 900 } })).newPage()
await page.goto('file:///E:/codestorge/.run-logs/cloud-split-preview.html', { waitUntil: 'load' })
await page.waitForTimeout(300)
await page.screenshot({ path: 'E:\\codestorge\\.run-logs\\cloud-split-preview.png', fullPage: true })
console.log('  ✓ 预览图已生成')
await b.close()