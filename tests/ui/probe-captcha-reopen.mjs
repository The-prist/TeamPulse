// 验证：验证码第二次触发时是全新题目（状态不残留）+ 能再次通过
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\captcha3'
fs.mkdirSync(DIR, { recursive: true })

const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 160)))
page.on('console', (m) => {
  if (m.type() === 'error') errs.push(m.text().slice(0, 160))
})

const dialogState = () =>
  page.evaluate(() => {
    const d = document.querySelector('.captcha-dialog')
    const open = Boolean(d && d.offsetParent !== null)
    if (!open) return { open: false }
    return {
      open: true,
      hint: document.querySelector('.cap-hint')?.textContent?.trim(),
      handle: document.querySelector('.cap-handle')?.className,
      bgSrc: (document.querySelector('.cap-bg')?.src || '').slice(0, 64),
      offset: document.querySelector('.cap-piece')?.style.transform,
    }
  })

/** 用归一化互相关解缺口并拖动 */
async function solve() {
  const solved = await page.evaluate(async () => {
    const bg = document.querySelector('.cap-bg')
    const piece = document.querySelector('.cap-piece')
    if (!bg || !piece) return null
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
    const pts = []
    for (let y = 0; y < pr; y++) {
      for (let x = 0; x < pr; x++) {
        const i = (y * pr + x) * 4
        if (pd[i + 3] < 60) continue
        pts.push([x, y, (pd[i] + pd[i + 1] + pd[i + 2]) / 3])
      }
    }
    let bestX = -1
    let bestCorr = -Infinity
    for (let ox = 0; ox <= W - pr; ox++) {
      let s = 0, s2 = 0, sb = 0, sb2 = 0, spb = 0
      for (const [x, y, v] of pts) {
        const i = ((y + pieceTop) * W + (ox + x)) * 4
        const w = (bd[i] + bd[i + 1] + bd[i + 2]) / 3
        s += v; s2 += v * v; sb += w; sb2 += w * w; spb += v * w
      }
      const n = pts.length
      const cov = spb / n - (s / n) * (sb / n)
      const sd = Math.sqrt(s2 / n - (s / n) ** 2) * Math.sqrt(sb2 / n - (sb / n) ** 2)
      const corr = sd === 0 ? 0 : cov / sd
      if (corr > bestCorr) { bestCorr = corr; bestX = ox }
    }
    return { offset: bestX, corr: Number(bestCorr.toFixed(3)) }
  })
  if (!solved || solved.offset < 0) return solved
  const track = await page.locator('.cap-track').boundingBox()
  await page.mouse.move(track.x + 22, track.y + track.height / 2)
  await page.mouse.down()
  for (const s of [0.35, 0.7, 1]) {
    await page.mouse.move(track.x + 22 + solved.offset * s, track.y + track.height / 2)
    await page.waitForTimeout(60)
  }
  await page.mouse.up()
  await page.waitForTimeout(1000)
  return solved
}

async function failUntilCaptcha(max = 8) {
  await page.fill('.form-card input[autocomplete="username"]', 'admin')
  for (let i = 1; i <= max; i++) {
    const st = await dialogState()
    if (st.open) return i - 1
    await page.fill('.form-card input[type="password"]', `bad-${Date.now()}-${i}`)
    await page.locator('.submit-btn').click()
    await page.waitForTimeout(900)
  }
  return max
}

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.evaluate(() => sessionStorage.removeItem('tp_login_fail_count'))

// ---------- 第一次验证码 ----------
const n1 = await failUntilCaptcha()
console.log(`  第一次触发：错了 ${n1} 次`)
await page.waitForTimeout(700)
const s1 = await dialogState()
console.log('  第一次弹窗状态:', JSON.stringify(s1))
const sol1 = await solve()
console.log('  第一次求解:', JSON.stringify(sol1))
await page.waitForTimeout(600)
await page.screenshot({ path: `${DIR}\\1-first.png` })

// ---------- 再错两次，第二次触发 ----------
// 第 1 次：ticket 还在，会被服务端消费 → 返回「密码错误」
// 第 2 次：ticket 已用且计数仍 ≥5 → 40101 → 重新弹验证码
for (let i = 1; i <= 3; i++) {
  if ((await dialogState()).open) break
  await page.fill('.form-card input[type="password"]', `bad-again-${i}`)
  await page.locator('.submit-btn').click()
  await page.waitForTimeout(1500)
  console.log(`  第 ${i} 次补错后弹窗:`, JSON.stringify(await dialogState()))
}
const s2 = await dialogState()
console.log('  第二次弹窗状态:', JSON.stringify(s2))
console.log('  状态是否已复位:', s2.open && s2.hint === '向右拖动滑块' && !String(s2.handle).includes('ok') ? '✓ 是新题、未验证' : '✗ 仍是旧状态')
console.log('  题目是否换了:', s1.bgSrc !== s2.bgSrc ? '✓ 新图' : '✗ 还是旧图')
await page.screenshot({ path: `${DIR}\\2-second.png` })

// 第二次也能通过
const sol2 = await solve()
console.log('  第二次求解:', JSON.stringify(sol2))
const s3 = await dialogState()
console.log('  通过后弹窗关闭:', s3.open ? '✗ 仍开着' : '✓')
await page.screenshot({ path: `${DIR}\\3-second-solved.png` })

// 登录
await page.fill('.form-card input[type="password"]', 'adminpassword')
await page.locator('.submit-btn').click()
await page.waitForTimeout(3000)
console.log('  登录结果:', await page.evaluate(() => location.pathname))

await b.close()
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错')
