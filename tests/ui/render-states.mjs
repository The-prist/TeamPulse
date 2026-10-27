import { chromium } from 'playwright-core'
const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1100, height: 800 } })).newPage()
for (const k of ['sunset', 'night', 'rain']) {
  await page.goto(`file:///C:/Users/12986/Desktop/clouds_svg_states/preview-${k}.html`, { waitUntil: 'load' })
  await page.waitForTimeout(250)
  await page.screenshot({ path: `E:\\codestorge\\.run-logs\\state-${k}.png`, fullPage: true })
  console.log(`  ✓ ${k} 预览已渲染`)
}
await b.close()