// 把用户给的 7 个 SVG 规范化：
//   1) 读入 → 内联到页面 → getBBox() 拿到实际内容包围盒
//   2) 重写 viewBox 为紧包围盒（去掉编辑器留下的空白/位移）
//   3) 去掉 width/height（交给页面按 CSS 缩放），写回 assets 目录
//   4) 生成一张 7 个云的预览图
import { chromium } from 'playwright-core'
import fs from 'node:fs'
import path from 'node:path'

const SRC = 'C:\\Users\\12986\\Desktop\\clouds_svg_vectors'
const DST = 'E:\\codestorge\\frontend\\src\\assets\\sky'
const OUT_PREVIEW = 'E:\\codestorge\\.run-logs\\cloud-user-preview.png'

fs.mkdirSync(DST, { recursive: true })
const files = fs.readdirSync(SRC).filter((f) => f.toLowerCase().endsWith('.svg')).sort()
console.log(`  源文件 ${files.length} 个`)

const browser = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await browser.newContext({ viewport: { width: 1200, height: 800 } })).newPage()

const normalized = []
for (const f of files) {
  const raw = fs.readFileSync(path.join(SRC, f), 'utf8')
  // 内联渲染，量真实包围盒
  await page.setContent(
    `<html><body style="margin:0"><div id="host">${raw}</div></body></html>`,
    { waitUntil: 'load' },
  )
  await page.waitForTimeout(80)
  const bbox = await page.evaluate(() => {
    const svg = document.querySelector('#host svg')
    if (!svg) return null
    const g = svg.querySelector('g') || svg
    const b = g.getBBox()
    return { x: b.x, y: b.y, w: b.width, h: b.height }
  })
  if (!bbox || !bbox.w) {
    console.log(`  ! ${f} 无法测量，跳过规范化`)
    continue
  }

  const pad = 2
  const vb = [bbox.x - pad, bbox.y - pad, bbox.w + pad * 2, bbox.h + pad * 2]
    .map((n) => Math.round(n * 10) / 10)
    .join(' ')

  // 重写：viewBox 收紧、去掉 width/height、保留其余内容
  let out = raw
    .replace(/viewBox="[^"]*"/i, `viewBox="${vb}"`)
    .replace(/(<svg[^>]*?)\swidth="[^"]*"/i, '$1')
    .replace(/(<svg[^>]*?)\sheight="[^"]*"/i, '$1')
  if (!/xmlns=/.test(out)) out = out.replace('<svg', '<svg xmlns="http://www.w3.org/2000/svg"')

  const dstName = `cloud-${normalized.length + 1}.svg`
  fs.writeFileSync(path.join(DST, dstName), out, 'utf8')
  normalized.push({ from: f, to: dstName, vb, ar: (bbox.h / bbox.w).toFixed(3) })
  console.log(`  ✓ ${f} → ${dstName}  viewBox=${vb}  宽高比=${(bbox.h / bbox.w).toFixed(2)}`)
}

// 预览：把所有规范化后的云铺在蓝天上
const cells = normalized
  .map((n) => {
    const svg = fs.readFileSync(path.join(DST, n.to), 'utf8')
    return `<figure><div class="box">${svg}</div><figcaption>${n.to}</figcaption></figure>`
  })
  .join('\n')
await page.setViewportSize({ width: 1200, height: 900 })
await page.setContent(
  `<!doctype html><html><head><meta charset="utf-8"><style>
   body{margin:0;background:linear-gradient(#3f9be0,#8ecdf3 60%,#cfe9fb);font:12px system-ui;color:#0b3556}
   .grid{display:grid;grid-template-columns:1fr 1fr;gap:8px;padding:8px}
   figure{margin:0;background:rgba(255,255,255,.12);border-radius:10px;padding:6px}
   .box{display:flex;align-items:flex-end;justify-content:center;height:170px}
   svg{max-width:100%;max-height:160px}
   figcaption{text-align:center;opacity:.8;margin-top:2px}
   </style></head><body><div class="grid">${cells}</div></body></html>`,
  { waitUntil: 'load' },
)
await page.waitForTimeout(200)
await page.screenshot({ path: OUT_PREVIEW, fullPage: true })
await browser.close()
console.log(`  ✓ 预览图: ${OUT_PREVIEW}`)
