<!--
  登录页背景场景（夜间星星 / 白天云朵 + 昼夜切换动画 + 偶发流星/飞机尾迹）

  场景
    夜间  —— 白色小圆点（星星）缓慢漂浮，每颗只在自己附近的小矩形内活动
    白天  —— 大的白色积云（动漫式，底部平、顶部圆），缓慢飘移

  切换动画（接管全局的全屏覆盖动画，见 utils/theme.js 的 registerThemeTransition）
    夜 → 昼：
        1. 所有星星轻微颤抖
        2. 依次加速向下坠落（透明度不变，不淡出）
        3. **等最后一颗星掉出屏幕之后**，天色才由深蓝切到亮蓝，太阳同时开始升起
        4. 云朵从散开的状态"聚拢"成完整云形
    昼 → 夜：太阳先行西沉、云朵上飘散开，然后天色转夜，星星从屏幕下方升起归位
    整段动画只发生在背景层（canvas 在登录卡片下层），卡片不受任何影响

  偶发事件（两次间隔 ≥ 60 秒）
    夜间 —— 流星划过，尾巴按年龄淡出
    白天 —— 飞机（纯白、很小）横穿，留下尾迹云；尾迹扩散变粗后再淡出

  调试：左上角有「动效」按钮，点一下立刻触发当前场景的事件，
        也可以在控制台执行 __tpLoginDots.spawn('night' | 'day')
-->
<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { currentTheme, registerThemeTransition, setTheme, THEMES } from '@/utils/theme'

const canvasRef = ref(null)
const props = defineProps({
  /** 星星密度：每多少平方像素一颗 */
  density: { type: Number, default: 8200 },
  /** 鼠标吸附半径 */
  attractR: { type: Number, default: 185 },
})

let ctx = null
let raf = 0
let running = false
let last = 0
let width = 0
let height = 0
let dpr = 1

let scene = 'night' // 'night' | 'day'
let phase = 'idle' // 'idle' | 'to-day' | 'to-night'
let phaseStart = 0
let themeFlipped = false
let sunStartAt = 0

let stars = []
let clouds = []
const sun = { x: 0, y: 0, r: 0, alpha: 0 }
let events = []
let contrail = []
let meteorTrail = []
let nextEventAt = 0
const triggering = ref(false)

const mouse = { x: -9999, y: -9999, active: false }

const rand = (a, b) => a + Math.random() * (b - a)
const clamp01 = (v) => Math.max(0, Math.min(1, v))
const easeOut = (t) => 1 - (1 - t) ** 3
const reduced = () => window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches

/* 节奏参数：整体放慢，坠落与变色分阶段 */
const T_TREMBLE = 620 // 颤抖时长
const T_FALL_STAGGER = 320 // 依次坠落的错峰范围
const G_FALL = 0.09 // 坠落加速度（px/frame²）
const T_SUNRISE = 1250 // 太阳升起
const T_SKY = 1150 // 天色过渡（CSS 同步）

/* ------------------------------------------------------------------ 建模 */

function makeStar(rising = false) {
  const hx = rand(0, width)
  const hy = rand(0, height)
  return {
    hx,
    hy,
    rx: rand(22, 72),
    ry: rand(18, 54),
    x: hx + rand(-20, 20),
    y: rising ? height + rand(60, 260) : hy + rand(-16, 16),
    vx: rand(-0.06, 0.06),
    vy: rand(-0.06, 0.06),
    size: rand(1, 2.6),
    alpha: rand(0.22, 0.72), // 坠落过程中不改变
    glow: Math.random() < 0.18,
    fallVel: 0,
    falling: false,
    rise: rising ? 0 : 1,
    riseDelay: rand(0, 300),
    jitterPhase: rand(0, Math.PI * 2),
  }
}

/** 动漫式积云：底部平、中间鼓，多个圆沿一条基线排开 */
function makeCloud() {
  const hx = rand(0, width)
  const hy = rand(height * 0.06, height * 0.78)
  const base = rand(56, 108) // 整体越大越接近动漫云
  const n = 6
  const puffs = []
  for (let i = 0; i < n; i++) {
    const t = i / (n - 1) - 0.5 // -0.5 .. 0.5
    const r = base * (0.62 + 0.42 * Math.cos(t * Math.PI * 0.85))
    puffs.push({
      dx: t * base * 1.85 + rand(-5, 5),
      dy: -r * 0.55 + rand(-3, 3), // 圆心高于基线，底边大致齐平
      r,
    })
  }
  return {
    hx,
    hy,
    rx: rand(120, 260),
    ry: rand(40, 90),
    x: hx,
    y: hy,
    vx: rand(-0.03, 0.03),
    vy: rand(-0.015, 0.015),
    alpha: rand(0.5, 0.82),
    alphaScale: 0,
    gather: 0, // 0=散开，1=聚拢成形
    puffs,
  }
}

function buildStars(rising = false) {
  const count = Math.max(36, Math.min(190, Math.round((width * height) / props.density)))
  stars = Array.from({ length: count }, () => makeStar(rising))
}

function buildClouds(count) {
  const n = count ?? Math.max(4, Math.min(8, Math.round(width / 300)))
  clouds = Array.from({ length: n }, makeCloud)
}

/* ------------------------------------------------------------------ 偶发事件 */

function scheduleNextEvent(now) {
  nextEventAt = now + rand(60_000, 150_000)
}

function spawnEvent(forceType) {
  const kind = forceType || scene
  if (kind === 'night') {
    // 从左侧偏上进入、角度偏陡：轨迹落在左侧留白区，不会被中间卡片整段挡住
    const speed = rand(0.75, 1.05)
    const angle = rand(0.62, 0.95)
    events.push({
      type: 'meteor',
      x: rand(-40, width * 0.16),
      y: rand(-10, height * 0.22),
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed,
      life: 0,
      ttl: (height + 80) / (Math.sin(angle) * speed),
    })
    meteorTrail = []
  } else {
    const fromLeft = Math.random() < 0.5
    events.push({
      type: 'plane',
      x: fromLeft ? -60 : width + 60,
      y: rand(height * 0.12, height * 0.4),
      vx: (fromLeft ? 1 : -1) * rand(0.19, 0.27),
      vy: rand(-0.012, -0.004),
      life: 0,
      ttl: 99999,
      puffTimer: 0,
    })
  }
  scheduleNextEvent(performance.now())
}

/** 左上角调试按钮：立刻触发一个当前场景的动效 */
function triggerRandom() {
  if (phase !== 'idle') return
  triggering.value = true
  spawnEvent(scene)
  setTimeout(() => (triggering.value = false), 700)
}

/* ------------------------------------------------------------------ 物理 */

function stepStars(dt) {
  const k = Math.min(dt, 32) / 16.67
  const now = performance.now()

  for (const s of stars) {
    // 升起（昼→夜）
    if (s.rise < 1) {
      const elapsed = now - phaseStart - s.riseDelay
      if (elapsed > 0) s.rise = clamp01(elapsed / 900)
      const t = easeOut(s.rise)
      s.y = (height + 140) * (1 - t) + s.hy * t
      s.alphaNow = s.alpha * s.rise
      if (s.rise >= 1) {
        s.x = s.hx
        s.alphaNow = s.alpha
      }
      continue
    }

    // 坠落（夜→昼）：先颤抖，再按 x 位置错峰加速下落；透明度始终不变
    if (phase === 'to-day') {
      const fallStart = phaseStart + T_TREMBLE + (s.hx / Math.max(1, width)) * T_FALL_STAGGER
      if (now > fallStart) {
        s.falling = true
        s.fallVel = Math.min(s.fallVel + G_FALL * k, 38) // 限速：出屏后继续加速没有意义
        s.y += s.fallVel * k
        continue
      }
    }

    // 正常漂浮
    s.vx += rand(-0.004, 0.004) * k
    s.vy += rand(-0.004, 0.004) * k

    if (mouse.active) {
      const dx = mouse.x - s.x
      const dy = mouse.y - s.y
      const dist = Math.hypot(dx, dy)
      if (dist < props.attractR && dist > 0.001) {
        const pull = (1 - dist / props.attractR) ** 2 * 0.26
        s.vx += (dx / dist) * pull * k
        s.vy += (dy / dist) * pull * k
      }
    }

    s.vx += (s.hx - s.x) * 0.0006 * k
    s.vy += (s.hy - s.y) * 0.0006 * k

    s.vx *= 0.968
    s.vy *= 0.968
    const speed = Math.hypot(s.vx, s.vy)
    const maxSpeed = mouse.active ? 1.9 : 0.45
    if (speed > maxSpeed) {
      s.vx = (s.vx / speed) * maxSpeed
      s.vy = (s.vy / speed) * maxSpeed
    }

    s.x += s.vx * k
    s.y += s.vy * k

    const minX = Math.max(0, s.hx - s.rx)
    const maxX = Math.min(width, s.hx + s.rx)
    const minY = Math.max(0, s.hy - s.ry)
    const maxY = Math.min(height, s.hy + s.ry)
    if (s.x < minX) { s.x = minX; s.vx = Math.abs(s.vx) * 0.4 }
    if (s.x > maxX) { s.x = maxX; s.vx = -Math.abs(s.vx) * 0.4 }
    if (s.y < minY) { s.y = minY; s.vy = Math.abs(s.vy) * 0.4 }
    if (s.y > maxY) { s.y = maxY; s.vy = -Math.abs(s.vy) * 0.4 }
  }
}

function stepClouds(dt) {
  const k = Math.min(dt, 32) / 16.67
  for (const c of clouds) {
    if (phase === 'to-day') {
      c.alphaScale = Math.min(1, c.alphaScale + 0.018 * k)
      c.gather = Math.min(1, c.gather + 0.012 * k) // 聚拢
    }
    if (phase === 'to-night') {
      c.alphaScale = Math.max(0, c.alphaScale - 0.022 * k)
      c.gather = Math.max(0, c.gather - 0.01 * k) // 散开
    }

    c.vx += rand(-0.002, 0.002) * k
    c.vy += rand(-0.0015, 0.0015) * k
    c.vx += (c.hx - c.x) * 0.0002 * k
    c.vy += (c.hy - c.y) * 0.0002 * k
    if (phase === 'to-night') c.vy -= 0.004 * k

    c.vx *= 0.99
    c.vy *= 0.99
    c.x += c.vx * k
    c.y += c.vy * k

    c.x = Math.max(Math.max(0, c.hx - c.rx), Math.min(Math.min(width, c.hx + c.rx), c.x))
    c.y = Math.max(Math.max(0, c.hy - c.ry), Math.min(Math.min(height, c.hy + c.ry), c.y))
  }
}

function stepEvents(dt) {
  const k = Math.min(dt, 32) / 16.67
  const now = performance.now()

  events = events.filter((e) => {
    e.life += dt
    e.x += e.vx * dt
    e.y += e.vy * dt
    const off = e.x < -160 || e.x > width + 160 || e.y > height + 160 || e.y < -160

    if (e.type === 'meteor') {
      meteorTrail.push({ x: e.x, y: e.y, t: now })
      return !off && e.life < e.ttl + 700
    }

    e.puffTimer += dt
    if (e.puffTimer > 26) {
      e.puffTimer = 0
      contrail.push({
        x: e.x - Math.sign(e.vx) * 10,
        y: e.y + 1,
        r: 4.2,
        age: 0,
        life: rand(6200, 9000),
        drift: rand(-0.004, 0.004),
      })
    }
    return !off
  })

  meteorTrail = meteorTrail.filter((p) => p.t > now - 1400)

  for (const p of contrail) {
    p.age += dt
    p.x += p.drift * k * 6
    p.y += 0.02 * k
  }
  contrail = contrail.filter((p) => p.age < p.life)

  if (phase === 'idle' && now >= nextEventAt && !events.length) spawnEvent()
}

/* ------------------------------------------------------------------ 绘制 */

function drawSun() {
  if (sun.alpha <= 0.01) return
  const g = ctx.createRadialGradient(sun.x, sun.y, 0, sun.x, sun.y, sun.r * 4.2)
  g.addColorStop(0, `rgba(255, 246, 214, ${0.95 * sun.alpha})`)
  g.addColorStop(0.28, `rgba(255, 228, 156, ${0.5 * sun.alpha})`)
  g.addColorStop(1, 'rgba(255, 228, 156, 0)')
  ctx.fillStyle = g
  ctx.beginPath()
  ctx.arc(sun.x, sun.y, sun.r * 4.2, 0, Math.PI * 2)
  ctx.fill()

  ctx.fillStyle = `rgba(255, 251, 232, ${sun.alpha})`
  ctx.beginPath()
  ctx.arc(sun.x, sun.y, sun.r, 0, Math.PI * 2)
  ctx.fill()
}

function drawStars() {
  const now = performance.now()
  const trembling = phase === 'to-day' && now - phaseStart < T_TREMBLE

  for (const s of stars) {
    // 坠落期间透明度保持不变（需求：不要降低星星透明度）
    let alpha = s.alphaNow ?? s.alpha
    let x = s.x
    let y = s.y

    if (trembling) {
      const j = 2.6 * Math.sin((now - phaseStart) / 22 + s.jitterPhase)
      x += j
      y += j * 0.6
    }
    if (alpha <= 0.01) continue

    if (s.glow) {
      const g = ctx.createRadialGradient(x, y, 0, x, y, s.size * 6)
      g.addColorStop(0, `rgba(255,255,255,${Math.min(1, alpha * 0.55)})`)
      g.addColorStop(1, 'rgba(255,255,255,0)')
      ctx.fillStyle = g
      ctx.beginPath()
      ctx.arc(x, y, s.size * 6, 0, Math.PI * 2)
      ctx.fill()
    }
    ctx.beginPath()
    ctx.fillStyle = `rgba(255,255,255,${Math.min(1, alpha)})`
    ctx.arc(x, y, s.size, 0, Math.PI * 2)
    ctx.fill()
  }
}

function drawClouds() {
  for (const c of clouds) {
    const a = c.alpha * c.alphaScale
    if (a <= 0.01) continue
    // 聚拢：gather 0 → 1 时，puff 从散开的位置收拢成完整云形
    const spread = 1.75 - 0.75 * c.gather
    const rise = 0.55 + 0.45 * c.gather
    const scale = 0.82 + 0.18 * c.gather

    for (const p of c.puffs) {
      const px = c.x + p.dx * spread
      const py = c.y + p.dy * rise
      const r = p.r * scale
      const g = ctx.createRadialGradient(px, py - r * 0.25, 0, px, py, r)
      g.addColorStop(0, `rgba(255,255,255,${a})`)
      g.addColorStop(0.62, `rgba(255,255,255,${a * 0.7})`)
      g.addColorStop(1, 'rgba(255,255,255,0)')
      ctx.fillStyle = g
      ctx.beginPath()
      ctx.arc(px, py, r, 0, Math.PI * 2)
      ctx.fill()
    }
  }
}

function drawEvents() {
  const now = performance.now()

  // 飞机尾迹云：扩散 + 淡出
  for (const p of contrail) {
    const t = p.age / p.life
    const r = p.r * (1 + 2.2 * t)
    const a = (1 - t) ** 1.3 * 0.62
    if (a <= 0.01) continue
    const g = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, r * 2.4)
    g.addColorStop(0, `rgba(255,255,255,${a})`)
    g.addColorStop(1, 'rgba(255,255,255,0)')
    ctx.fillStyle = g
    ctx.beginPath()
    ctx.arc(p.x, p.y, r * 2.4, 0, Math.PI * 2)
    ctx.fill()
  }

  // 流星尾迹
  if (meteorTrail.length > 1) {
    for (let i = 1; i < meteorTrail.length; i++) {
      const p0 = meteorTrail[i - 1]
      const p1 = meteorTrail[i]
      const age = (now - p1.t) / 1100
      const a = Math.max(0, 1 - age) ** 1.6
      if (a <= 0.01) continue
      ctx.strokeStyle = `rgba(255,255,255,${a * 0.85})`
      ctx.lineWidth = 2.6 * a + 0.3
      ctx.lineCap = 'round'
      ctx.beginPath()
      ctx.moveTo(p0.x, p0.y)
      ctx.lineTo(p1.x, p1.y)
      ctx.stroke()
    }
  }

  for (const e of events) {
    if (e.type === 'meteor') {
      const g = ctx.createRadialGradient(e.x, e.y, 0, e.x, e.y, 16)
      g.addColorStop(0, 'rgba(255,255,255,0.95)')
      g.addColorStop(0.4, 'rgba(205,228,255,0.5)')
      g.addColorStop(1, 'rgba(205,228,255,0)')
      ctx.fillStyle = g
      ctx.beginPath()
      ctx.arc(e.x, e.y, 16, 0, Math.PI * 2)
      ctx.fill()
      ctx.fillStyle = 'rgba(255,255,255,0.98)'
      ctx.beginPath()
      ctx.arc(e.x, e.y, 2.1, 0, Math.PI * 2)
      ctx.fill()
    } else {
      // 纯白、很小：远了看几乎只是一个白点（手绘轮廓粗糙，不做细节）
      const dir = Math.sign(e.vx) || 1
      ctx.save()
      ctx.translate(e.x, e.y)
      ctx.scale(dir, 1)
      ctx.fillStyle = 'rgba(255,255,255,0.95)'
      ctx.beginPath()
      ctx.moveTo(9, 0)
      ctx.lineTo(-6, 2.1)
      ctx.lineTo(-6, -2.1)
      ctx.closePath()
      ctx.fill()
      ctx.beginPath()
      ctx.moveTo(0.5, 0)
      ctx.lineTo(-4, 5.2)
      ctx.lineTo(-1.6, 5.2)
      ctx.lineTo(2.6, 0.7)
      ctx.closePath()
      ctx.fill()
      ctx.beginPath()
      ctx.moveTo(0.5, 0)
      ctx.lineTo(-4, -5.2)
      ctx.lineTo(-1.6, -5.2)
      ctx.lineTo(2.6, -0.7)
      ctx.closePath()
      ctx.fill()
      ctx.restore()
    }
  }
}

function draw() {
  if (!ctx) return
  ctx.clearRect(0, 0, width, height)
  drawSun()
  if (scene === 'night' || phase === 'to-day') drawStars()
  if (scene === 'day' || phase === 'to-night') drawClouds()
  drawEvents()
}

/* ------------------------------------------------------------------ 昼夜切换 */

function updateSun(now) {
  const H = height
  const dayY = H * 0.22

  if (phase === 'to-day') {
    // 关键：天色切换之前太阳一点都不许升起来
    if (!themeFlipped) {
      sun.alpha = 0
      sun.y = H * 1.06
      return
    }
    const t = clamp01((now - sunStartAt) / T_SUNRISE)
    sun.y = H * 1.06 - (H * 1.06 - dayY) * easeOut(t)
    sun.alpha = easeOut(t)
  } else if (phase === 'to-night') {
    if (!themeFlipped) {
      const t = clamp01((now - phaseStart) / 950)
      sun.y = dayY + (H * 1.06 - dayY) * easeOut(t)
      sun.alpha = 1 - easeOut(t)
    } else {
      sun.alpha = 0
    }
  } else if (scene === 'day') {
    sun.y = dayY
    sun.alpha = 1
  }
}

function settleToDay() {
  scene = 'day'
  stars = []
  sun.x = width * 0.905
  sun.y = height * 0.22
  sun.r = Math.max(34, Math.min(64, width * 0.037))
  sun.alpha = 1
  clouds.forEach((c) => {
    c.alphaScale = 1
    c.gather = 1
  })
}

function settleToNight() {
  scene = 'night'
  clouds = []
  contrail = []
  sun.alpha = 0
  sun.x = width * 0.905
  sun.r = Math.max(34, Math.min(64, width * 0.037))
  buildStars(false)
}

/** 等所有星星都掉出屏幕（或超时），返回是否确实掉完了 */
function waitStarsGone(timeout = 7000) {
  const started = performance.now()
  return new Promise((resolve) => {
    const tick = () => {
      const gone = !stars.length || stars.every((s) => s.y > height + 60)
      if (gone) return resolve(true)
      if (performance.now() - started > timeout) return resolve(false)
      setTimeout(tick, 60)
    }
    tick()
  })
}

let queue = Promise.resolve()

/** 串行化：连点时排队执行，而不是把第二次丢掉 */
function transitionTo(next) {
  queue = queue.then(() => doTransition(next)).catch(() => {})
  return queue
}

async function doTransition(next) {
  const wait = (ms) => new Promise((r) => setTimeout(r, ms))
  const targetScene = next === THEMES.LIGHT ? 'day' : 'night'
  if (phase === 'idle' && scene === targetScene) {
    setTheme(next)
    return
  }

  phaseStart = performance.now()
  themeFlipped = false
  sun.x = width * 0.905
  sun.r = Math.max(34, Math.min(64, width * 0.037))

  if (next === THEMES.LIGHT) {
    // ---------------- 夜 → 昼 ----------------
    phase = 'to-day'
    sun.alpha = 0 // 太阳在变色之前保持不出现
    stars.forEach((s) => { s.fallVel = 0; s.falling = false })

    // 1) 等最后一颗星掉出屏幕
    await waitStarsGone()
    if (phase !== 'to-day') return

    // 2) 这一刻才切天色（CSS 渐变层淡入），太阳同时开始升起
    setTheme(THEMES.LIGHT)
    themeFlipped = true
    sunStartAt = performance.now()

    // 3) 云朵聚拢出现
    await wait(220)
    if (phase !== 'to-day') return
    buildClouds()

    await wait(T_SUNRISE + 420)
    phase = 'idle'
    settleToDay()
  } else {
    // ---------------- 昼 → 夜 ----------------
    phase = 'to-night'
    buildStars(true)
    stars.forEach((s) => (s.alphaNow = 0))

    // 1) 太阳先落山、云先散开
    await wait(950)
    if (phase !== 'to-night') return

    // 2) 再转夜色
    setTheme(THEMES.DARK)
    themeFlipped = true

    // 3) 星星升起归位
    await wait(1900)
    phase = 'idle'
    settleToNight()
  }
}

/* ------------------------------------------------------------------ 生命周期 */

function resize() {
  const el = canvasRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const w = Math.max(1, rect.width)
  const h = Math.max(1, rect.height)
  const resized = Math.abs(w - width) > 2 || Math.abs(h - height) > 2
  width = w
  height = h
  dpr = Math.min(window.devicePixelRatio || 1, 2)
  el.width = Math.round(width * dpr)
  el.height = Math.round(height * dpr)
  ctx = el.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  sun.x = width * 0.905
  sun.r = Math.max(34, Math.min(64, width * 0.037))

  if (resized || (!stars.length && !clouds.length)) {
    if (scene === 'night') buildStars(false)
    else buildClouds()
  }
  if (scene === 'day') {
    sun.y = height * 0.22
    sun.alpha = 1
  }
  if (!nextEventAt) scheduleNextEvent(performance.now())
  draw()
}

function loop(ts) {
  if (!running) return
  const dt = last ? ts - last : 16.67
  last = ts
  updateSun(performance.now())
  if (scene === 'night' || phase === 'to-day') stepStars(dt)
  if (scene === 'day' || phase === 'to-night') stepClouds(dt)
  stepEvents(dt)
  draw()
  raf = requestAnimationFrame(loop)
}

function onMove(e) {
  const el = canvasRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  mouse.x = e.clientX - rect.left
  mouse.y = e.clientY - rect.top
  mouse.active = true
}

function onLeave() {
  mouse.active = false
  mouse.x = -9999
  mouse.y = -9999
}

function onVisibility() {
  if (document.hidden) {
    running = false
    cancelAnimationFrame(raf)
  } else if (!running && !reduced()) {
    running = true
    last = 0
    raf = requestAnimationFrame(loop)
  }
}

let ro = null
let unregister = null

onMounted(() => {
  scene = currentTheme() === THEMES.DARK ? 'night' : 'day'
  resize()
  if (scene === 'day') {
    buildClouds()
    clouds.forEach((c) => { c.alphaScale = 1; c.gather = 1 })
    settleToDay()
  } else {
    buildStars(false)
  }

  ro = new ResizeObserver(() => resize())
  ro.observe(canvasRef.value)
  window.addEventListener('pointermove', onMove, { passive: true })
  window.addEventListener('pointerleave', onLeave, { passive: true })
  document.addEventListener('visibilitychange', onVisibility)

  unregister = registerThemeTransition(transitionTo)

  if (!reduced()) {
    running = true
    raf = requestAnimationFrame(loop)
  } else {
    draw()
  }

  window.__tpLoginDots = {
    get stars() { return stars },
    get clouds() { return clouds },
    get scene() { return scene },
    get phase() { return phase },
    get events() { return events },
    get contrail() { return contrail },
    get sunAlpha() { return sun.alpha },
    spawn: (t) => spawnEvent(t),
    setNextEvent: (ms) => (nextEventAt = performance.now() + ms),
  }
})

onBeforeUnmount(() => {
  running = false
  cancelAnimationFrame(raf)
  ro?.disconnect()
  unregister?.()
  window.removeEventListener('pointermove', onMove)
  window.removeEventListener('pointerleave', onLeave)
  document.removeEventListener('visibilitychange', onVisibility)
  delete window.__tpLoginDots
})
</script>

<template>
  <canvas ref="canvasRef" class="login-scene" aria-hidden="true"></canvas>

  <!-- 调试用：点一下立刻触发当前场景的随机动效 -->
  <button
    class="fx-trigger"
    :class="{ 'is-busy': triggering }"
    type="button"
    title="手动触发随机动效（调试用）"
    @click.stop="triggerRandom"
  >
    <span class="fx-dot"></span>动效
  </button>
</template>

<style scoped>
.login-scene {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  pointer-events: none;
  z-index: 0;
}

.fx-trigger {
  position: fixed;
  left: 18px;
  top: 18px;
  z-index: 40;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  background: rgba(10, 22, 40, 0.55);
  color: rgba(255, 255, 255, 0.9);
  font-size: 12px;
  letter-spacing: 0.5px;
  cursor: pointer;
  backdrop-filter: blur(6px);
  transition: background 0.18s ease, transform 0.12s ease, border-color 0.18s ease;
}

.fx-trigger:hover {
  background: rgba(10, 22, 40, 0.8);
  border-color: rgba(255, 255, 255, 0.5);
}

.fx-trigger:active {
  transform: scale(0.96);
}

.fx-trigger.is-busy {
  background: rgba(47, 111, 208, 0.75);
  border-color: rgba(138, 180, 248, 0.8);
}

.fx-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ffd479;
  box-shadow: 0 0 8px rgba(255, 212, 121, 0.9);
}
</style>
