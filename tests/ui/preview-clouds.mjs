// 本地预览：把 6 个 SVG 云铺在蓝天上，看形态是否接近参考图
import { chromium } from 'playwright-core'
import fs from 'node:fs'
import path from 'node:path'

const DIR = 'E:\\codestorge\\frontend\\src\\assets\\sky'
const files = fs.readdirSync(DIR).filter((f) => f.endsWith('.svg')).sort()

const cells = files
  .map((f) => {
    const svg = fs.readFileSync(path.join(DIR, f), 'utf8')
    return `<figure><div class="box">${svg}</div><figcaption>${f}</figcaption></figure>`
  })
  .join('\n')

const html = `<!doctype html><html><head><meta charset="utf-8"><style>
  body{margin:0;background:linear-gradient(#3f9be0,#8ecdf3 60%,#cfe9fb);font:12px system-ui;color:#0b3556}
  .grid{display:grid;grid-template-columns:1fr 1fr;gap:10px;padding:10px}
  figure{margin:0;background:rgba(255,255,255,.12);border-radius:10px;padding:8px}
  .box{display:flex;align-items:flex-end;justify-content:center;height:190px}
  svg{max-width:100%;max-height:180px}
  figcaption{text-align:center;opacity:.75;margin-top:4px}
</style></head><body><div class="grid">${cells}</div></body></html>`

const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1100, height: 760 } })).newPage()
await page.setContent(html, { waitUntil: 'networkidle' })
await page.waitForTimeout(300)
await page.screenshot({ path: 'E:\\codestorge\\.run-logs\\cloud-preview.png', fullPage: true })
console.log('  ✓ 已生成预览 .run-logs/cloud-preview.png')
await b.close()
