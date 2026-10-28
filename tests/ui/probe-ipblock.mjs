// 验证：粘性验证码（每次登录都要验）+ 忘记密码提示 + IP 拉黑 10 分钟
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://www.team-pulse.site'
const DIR = 'E:\\codestorge\\.run-logs\\ipblock'
fs.mkdirSync(DIR, { recursive: true })

const b = await chromium.launch({ channel: 'msedge', headless: true })
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 160)))
page.on('console', (m) => {
  if (m.type() === 'error') errs.push(m.text().slice(0, 160))
})

const state = () =>
  page.evaluate(() => ({
    dialog: Boolean(document.querySelector('.captcha-dialog') && document.querySelector('.captcha-dialog').offsetParent !== null),
    forgotTip: document.querySelector('.forgot-tip')?.textContent?.trim().slice(0, 40) || null,
    blockTip: document.querySelector('.block-tip')?.textContent?.trim().slice(0, 50) || null,
    btnDisabled: document.querySelector('.submit-btn')?.disabled,
    hint: document.querySelector('.cap-hint')?.textContent?.trim(),
  }))

async function wrongLogin(pwd) {
  await page.fill('.form-card input[autocomplete="username"]', 'admin')
  await page.fill('.form-card input[type="password"]', pwd)
  await page.locator('.submit-btn').click({ force: true })
  await page.waitForTimeout(950)
}

async function solve() {
  const solved = await page.evaluate(async () => {
    const bg = document.querySelector('.cap-bg')
    const piece = document.querySelector('.cap-piece')
    if (!bg || !piece) return null
    const W = bg.naturalWidth, H = bg.naturalHeight, pr = piece.naturalWidth
    const pieceTop = parseInt(piece.style.top, 10) || 0
    const c1 = document.createElement('canvas'); c1.width = pr; c1.height = pr
    const g1 = c1.getContext('2d'); const im1 = new Image(); im1.src = piece.src; await im1.decode(); g1.drawImage(im1, 0, 0)
    const pd = g1.getImageData(0, 0, pr, pr).data
    const c2 = document.createElement('canvas'); c2.width = W; c2.height = H
    const g2 = c2.getContext('2d'); const im2 = new Image(); im2.src = bg.src; await im2.decode(); g2.drawImage(im2, 0, 0)
    const bd = g2.getImageData(0, 0, W, H).data
    const pts = []
    for (let y = 0; y < pr; y++) for (let x = 0; x < pr; x++) {
      const i = (y * pr + x) * 4
      if (pd[i + 3] < 60) continue
      pts.push([x, y, (pd[i] + pd[i + 1] + pd[i + 2]) / 3])
    }
    let bestX = -1, bestCorr = -Infinity
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
  if (!solved) return null
  const track = await page.locator('.cap-track').boundingBox()
  await page.mouse.move(track.x + 22, track.y + track.height / 2)
  await page.mouse.down()
  for (const s of [0.35, 0.7, 1]) {
    await page.mouse.move(track.x + 22 + solved.offset * s, track.y + track.height / 2)
    await page.waitForTimeout(55)
  }
  await page.mouse.up()
  await page.waitForTimeout(950)
  return solved
}

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1400)
await page.evaluate(() => sessionStorage.clear())

// ---------- ① 连错 5 次 → 弹窗 + 忘记密码提示 ----------
for (let i = 1; i <= 5; i++) {
  if ((await state()).dialog) break
  await wrongLogin(`bad-${i}-${Date.now()}`)
}
await page.waitForTimeout(600)
const s1 = await state()
console.log('① 触发验证码:', JSON.stringify(s1))
const forgotInDialog = await page.evaluate(
  () => document.querySelector('.captcha-forgot')?.textContent?.trim().slice(0, 40) || null,
)
console.log('   弹窗内忘记密码提示:', forgotInDialog)
await page.screenshot({ path: `${DIR}\\1-triggered.png` })

// ---------- ② 通过验证 → 再错一次 → 必须再验 ----------
const sol = await solve()
console.log('② 第一次求解:', JSON.stringify(sol))
const afterSolve = await state()
console.log('   验证后: 弹窗=', afterSolve.dialog, ' 忘记密码提示=', Boolean(afterSolve.forgotTip))
await wrongLogin('bad-after-captcha')
const afterWrong = await state()
console.log('   再错一次后: 弹窗=', afterWrong.dialog, ' 提示=', afterWrong.forgotTip)
// 点登录应直接弹新题（因为 ticket 已被消耗）
await page.locator('.submit-btn').click({ force: true })
await page.waitForTimeout(900)
const s3 = await state()
console.log('   再点登录是否直接弹新题:', s3.dialog ? '✓' : '✗', ' 题目状态=', s3.hint)
await page.screenshot({ path: `${DIR}\\2-second-captcha.png` })
// 通过它，然后继续错，凑满 IP 拉黑
await solve()
await page.waitForTimeout(400)

// ---------- ③ 继续错到 IP 拉黑（累计 10 次失败）----------
console.log('③ 继续制造失败以触发 IP 拉黑…')
for (let i = 0; i < 12; i++) {
  const st = await state()
  if (st.blockTip) break
  if (st.dialog) {
    await solve()
    await page.waitForTimeout(300)
  }
  await wrongLogin(`flood-${i}-${Date.now()}`)
}
await page.waitForTimeout(800)
const s4 = await state()
console.log('   拉黑状态:', JSON.stringify(s4))
await page.screenshot({ path: `${DIR}\\3-blocked.png` })

// 拉黑期间再点登录：应被前端拦住，不产生请求
const before = Date.now()
await page.locator('.submit-btn').click({ force: true })
await page.waitForTimeout(600)
console.log('   拉黑期间点击耗时:', Date.now() - before, 'ms（应为本地拦截，按钮 disabled）')

await b.close()
console.log(errs.length ? `  错误: ${errs.slice(0, 2).join(' || ')}` : '  控制台无报错（401 属预期）')
