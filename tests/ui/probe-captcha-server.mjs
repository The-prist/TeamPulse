// 端到端：连续失败 → 服务端要求验证码 → 用图像相关解出缺口 → 拿 ticket 登录
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\captcha2'
fs.mkdirSync(DIR, { recursive: true })

const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 200)))
page.on('console', (m) => {
  if (m.type() === 'error') errs.push(m.text().slice(0, 200))
})
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)

// 连续错 5 次（服务端计数也在累加）
await page.fill('.form-card input[autocomplete="username"]', 'admin')
let attempts = 0
for (let i = 1; i <= 6; i++) {
  const open = await page.evaluate(() => {
    const d = document.querySelector('.captcha-dialog')
    return Boolean(d && d.offsetParent !== null)
  })
  if (open) break
  await page.fill('.form-card input[type="password"]', `bad-${i}`)
  await page.locator('.submit-btn').click()
  await page.waitForTimeout(900)
  attempts = i
}
console.log(`  已尝试 ${attempts} 次错误密码`)
await page.waitForTimeout(1200)
const dialogOpen = await page.evaluate(() => {
  const d = document.querySelector('.captcha-dialog')
  return d ? !d.closest('.el-overlay')?.style.display : false
})
console.log('  验证码弹窗:', dialogOpen ? '✓' : '✗')
await page.waitForTimeout(800)
await page.screenshot({ path: `${DIR}\\1-dialog.png` })

// 用归一化互相关解出缺口位置（缺口被压暗，梯度依然相关）
const solved = await page.evaluate(async () => {
  const bg = document.querySelector('.cap-bg')
  const piece = document.querySelector('.cap-piece')
  if (!bg || !piece) return { error: 'no images' }
  const W = bg.naturalWidth
  const H = bg.naturalHeight
  const pr = piece.naturalWidth
  const pieceTop = parseInt(piece.style.top, 10) || 0

  const c1 = document.createElement('canvas')
  c1.width = pr
  c1.height = pr
  const g1 = c1.getContext('2d')
  const im1 = new Image()
  im1.src = piece.src
  await im1.decode()
  g1.drawImage(im1, 0, 0)
  const pd = g1.getImageData(0, 0, pr, pr).data

  const c2 = document.createElement('canvas')
  c2.width = W
  c2.height = H
  const g2 = c2.getContext('2d')
  const im2 = new Image()
  im2.src = bg.src
  await im2.decode()
  g2.drawImage(im2, 0, 0)
  const bd = g2.getImageData(0, 0, W, H).data

  // 取拼图块的灰度（跳过透明边）
  const pts = []
  for (let y = 0; y < pr; y += 1) {
    for (let x = 0; x < pr; x += 1) {
      const i = (y * pr + x) * 4
      if (pd[i + 3] < 60) continue
      pts.push([x, y, (pd[i] + pd[i + 1] + pd[i + 2]) / 3])
    }
  }
  if (!pts.length) return { error: 'empty piece' }

  let bestX = -1
  let bestCorr = -Infinity
  for (let ox = 0; ox <= W - pr; ox++) {
    let sum = 0
    let sum2 = 0
    let sumB = 0
    let sumB2 = 0
    let sumPB = 0
    for (const [x, y, v] of pts) {
      const i = ((y + pieceTop) * W + (ox + x)) * 4
      const w = (bd[i] + bd[i + 1] + bd[i + 2]) / 3
      sum += v
      sum2 += v * v
      sumB += w
      sumB2 += w * w
      sumPB += v * w
    }
    const n = pts.length
    const cov = sumPB / n - (sum / n) * (sumB / n)
    const sd = Math.sqrt(sum2 / n - (sum / n) ** 2) * Math.sqrt(sumB2 / n - (sumB / n) ** 2)
    const corr = sd === 0 ? 0 : cov / sd
    if (corr > bestCorr) {
      bestCorr = corr
      bestX = ox
    }
  }
  return { offset: bestX, corr: Number(bestCorr.toFixed(3)), points: pts.length, pr, pieceTop, W }
})
console.log('  求解结果:', JSON.stringify(solved))

// 拖动到解出的位置
const track = await page.locator('.cap-track').boundingBox()
if (track && solved.offset >= 0) {
  await page.mouse.move(track.x + 22, track.y + track.height / 2)
  await page.mouse.down()
  for (const s of [0.3, 0.65, 0.9, 1]) {
    await page.mouse.move(track.x + 22 + solved.offset * s, track.y + track.height / 2)
    await page.waitForTimeout(60)
  }
  await page.mouse.up()
  await page.waitForTimeout(1200)
}
const ticketState = await page.evaluate(() => {
  const d = document.querySelector('.captcha-dialog')
  return { dialogStillOpen: Boolean(d && d.offsetParent !== null) }
})
console.log('  验证后弹窗状态:', JSON.stringify(ticketState))
await page.screenshot({ path: `${DIR}\\2-verified.png` })

// 登录
await page.fill('.form-card input[autocomplete="username"]', 'admin')
await page.fill('.form-card input[type="password"]', 'adminpassword')
await page.locator('.submit-btn').click()
await page.waitForTimeout(3000)
const done = await page.evaluate(() => ({ path: location.pathname }))
console.log('  登录结果:', JSON.stringify(done), done.path !== '/login' ? '✓ 带 ticket 登录成功' : '✗ 失败')
await page.screenshot({ path: `${DIR}\\3-logged-in.png` })

await b.close()
console.log(errs.length ? `  错误(前2): ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
