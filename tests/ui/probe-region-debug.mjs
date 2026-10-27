// 诊断：手动选择后到底存了什么、请求带了什么参数
import { chromium } from 'playwright-core'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()

page.on('request', (r) => {
  if (r.url().includes('sky-context')) console.log('  → 请求:', r.url())
})
page.on('response', async (r) => {
  if (r.url().includes('sky-context')) {
    try {
      const j = await r.json()
      console.log('  ← 响应:', JSON.stringify(j.data))
    } catch {}
  }
})

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.evaluate(() => localStorage.removeItem('tp_sky_region'))
await page.reload({ waitUntil: 'networkidle' })
await page.waitForTimeout(2000)

console.log('  localStorage 初始:', await page.evaluate(() => localStorage.getItem('tp_sky_region')))

await page.click('.sky-change')
await page.waitForTimeout(800)
await page.fill('.region-dialog .el-cascader input', '西安')
await page.waitForTimeout(900)
const node = page.locator('.el-cascader-node__label').filter({ hasText: /^西安市$/ }).first()
console.log('  匹配到节点数:', await node.count())
if (await node.count()) {
  await node.click()
  await page.waitForTimeout(600)
}
console.log('  级联当前值:', await page.evaluate(() => {
  const inp = document.querySelector('.region-dialog .el-cascader input')
  return inp ? inp.value : '(无)'
}))
await page.keyboard.press('Escape')
await page.waitForTimeout(300)
await page.locator('.region-dialog .el-button--primary').click()
await page.waitForTimeout(2500)
console.log('  localStorage 之后:', await page.evaluate(() => localStorage.getItem('tp_sky_region')))
console.log('  卡片:', await page.evaluate(() => document.querySelector('.sky-card')?.innerText.replace(/\n+/g, ' | ')))

await b.close()
