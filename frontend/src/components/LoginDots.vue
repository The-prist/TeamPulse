<!--
  登录页背景场景（夜间星星 / 白天云朵 + 昼夜切换动画 + 偶发流星/飞机尾迹）

  场景
    夜间  —— 白色小圆点（星星）缓慢漂浮，每颗只在自己附近的小矩形内活动
    白天  —— 白色云朵缓慢飘移，活动范围略大

  切换动画（接管全局的全屏覆盖动画，见 utils/theme.js 的 registerThemeTransition）
    夜 → 昼：星星先轻微颤抖 → 加速向下坠落出屏幕 → 右侧太阳升起 → 云朵浮现
    昼 → 夜：太阳西沉 → 云朵向上飘散淡出 → 星星从屏幕下方升起并归位
    整段动画只发生在背景层（canvas 在登录卡片下层），卡片不受影响

  偶发事件（两次间隔 ≥ 60 秒）
    夜间 —— 流星划过，尾巴残留一段后消散
    白天 —— 飞机横穿，留下尾迹云；尾迹扩散变粗后再淡出

  实现要点
    - 星星带「家 (hx,hy)」与活动半径 (rx,ry)，每帧 clamp 回 [hx±rx, hy±ry]，
      鼠标吸引也不会让它越界
    - 吸引只在距离 < attractR 时生效，力 ∝ (1 - d/R)²；另有很轻的「回家力」，
      鼠标移开后点会散回原位
    - 主题切换由 setTheme() 在动画中途触发，天空渐变靠 CSS opacity 交叉淡入，
      不对两个渐变做补间（不可靠）
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

let stars = []
let clouds = []
const sun = { x: 0, y: 0, r: 0, alpha: 0 }
let events = []
let contrail = []
let meteorTrail = []
let nextEventAt = 0

const mouse = { x: -9999, y: -9999, active: false }

const rand = (a, b) => a + Math.random() * (b - a)
const clamp01 = (v) => Math.max(0, Math.min(1, v))
const easeOut = (t) => 1 - (1 - t) ** 3
const reduced = () => window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches

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
    alpha: rand(0.22, 0.72),
    glow: Math.random() < 0.18,
    fallVel: 0,
    falling: false,
    rise: rising ? 0 : 1,
    riseDelay: rand(0, 300),
    jitterPhase: rand(0, Math.PI * 2),
  }
}

function makeCloud() {
  const hx = rand(0, width)
  const hy = rand(height * 0.04, height * 0.86)
  const scale = rand(0.55, 1.25)
  const puffs = Array.from({ length: 5 }, () => ({
    dx: rand(-46, 46) * scale,
    dy: rand(-14, 12) * scale,
    r: rand(16, 34) * scale,
  }))
  return {
    hx,
    hy,
    rx: rand(80, 190),
    ry: rand(26, 62),
    x: hx,
    y: hy,
    vx: rand(-0.03, 0.03),
    vy: rand(-0.015, 0.015),
    alpha: rand(0.45, 0.8),
    alphaScale: 0,
    puffs,
  }
}

function buildStars(rising = false) {
  const count = Math.max(36, Math.min(190, Math.round((width * height) / props.density)))
  stars = Array.from({ length: count }, () => makeStar(rising))
}

function buildClouds() {
  const count = Math.max(5, Math.min(12, Math.round(width / 210)))
  clouds = Array.from({ length: count }, makeCloud)
}

/* ------------------------------------------------------------------ 偶发事件 */

function scheduleNextEvent(now) {
  // 两次间隔至少 60 秒（需求），上限 150 秒
  nextEventAt = now + rand(60_000, 150_000)
}

function spawnEvent(forceType) {
  const kind = forceType || scene
  if (kind === 'night') {
    // 从左侧偏上进入、角度偏陡：这样流星的轨迹落在左侧留白区，不会被中间卡片整段挡住
    const startX = rand(-40, width * 0.16)
    const speed = rand(0.75, 1.05)
    const angle = rand(0.62, 0.95)
    events.push({
      type: 'meteor',
      x: startX,
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
      x: fromLeft ? -80 : width + 80,
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

/* ------------------------------------------------------------------ 物理 */

function stepStars(dt) {
  const k = Math.min(dt, 32) / 16.67
  const now = performance.now()

  for (const s of stars) {
    // ---- 升起（昼→夜）
    if (s.rise < 1) {
      const elapsed = now - phaseStart - s.riseDelay
      if (elapsed > 0) s.rise = clamp01(elapsed / 780)
      const t = easeOut(s.rise)
      s.y = (height + 140) * (1 - t) + s.hy * t
      s.alphaNow = s.alpha * s.rise
      if (s.rise >= 1) {
        s.x = s.hx
        s.alphaNow = s.alpha
      }
      continue
    }

    // ---- 坠落（夜→昼）：先颤抖 380ms，然后按 x 位置错峰下落
    if (phase === 'to-day') {
      const fallStart = phaseStart + 380 + (s.hx / Math.max(1, width)) * 170
      if (now > fallStart) {
        s.falling = true
        s.fallVel += 0.075 * k
        s.y += s.fallVel * k
      }
      if (s.falling) continue
    }

    // ---- 正常漂浮
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

    // 回家力
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

    // 绝不越界
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
    if (phase === 'to-day') c.alphaScale = Math.min(1, c.alphaScale + 0.02 * k)
    if (phase === 'to-night') c.alphaScale = Math.max(0, c.alphaScale - 0.03 * k)

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
        x: e.x - Math.sign(e.vx) * 14,
        y: e.y + 2,
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
  const trembling = phase === 'to-day' && now - phaseStart < 420

  for (const s of stars) {
    let alpha = s.alphaNow ?? s.alpha
    let x = s.x
    let y = s.y

    if (trembling) {
      const j = 2.6 * Math.sin((now - phaseStart) / 22 + s.jitterPhase)
      x += j
      y += j * 0.6
      alpha *= 1.2
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
    for (const p of c.puffs) {
      const px = c.x + p.dx
      const py = c.y + p.dy
      const g = ctx.createRadialGradient(px, py, 0, px, py, p.r)
      g.addColorStop(0, `rgba(255,255,255,${a})`)
      g.addColorStop(0.65, `rgba(255,255,255,${a * 0.72})`)
      g.addColorStop(1, 'rgba(255,255,255,0)')
      ctx.fillStyle = g
      ctx.beginPath()
      ctx.arc(px, py, p.r, 0, Math.PI * 2)
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

  // 流星头 / 飞机本体
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
      const dir = Math.sign(e.vx) || 1
      ctx.save()
      ctx.translate(e.x, e.y)
      ctx.scale(dir, 1)
      ctx.fillStyle = 'rgba(28, 52, 86, 0.92)'
      ctx.beginPath()
      ctx.moveTo(15, 0)
      ctx.lineTo(-10, 3.4)
      ctx.lineTo(-10, -3.4)
      ctx.closePath()
      ctx.fill()
      ctx.fillStyle = 'rgba(28, 52, 86, 0.78)'
      ctx.beginPath()
      ctx.moveTo(-1, 0)
      ctx.lineTo(-7, 9.5)
      ctx.lineTo(-2.5, 9.5)
      ctx.lineTo(4, 1)
      ctx.closePath()
      ctx.fill()
      ctx.beginPath()
      ctx.moveTo(-1, 0)
      ctx.lineTo(-7, -9.5)
      ctx.lineTo(-2.5, -9.5)
      ctx.lineTo(4, -1)
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
  if (phase === 'to-day') {
    const t = clamp01((now - phaseStart - 300) / 1000)
    sun.y = H * 1.06 - (H * 1.06 - H * 0.22) * easeOut(t)
    sun.alpha = easeOut(t)
  } else if (phase === 'to-night') {
    const t = clamp01((now - phaseStart) / 780)
    sun.y = H * 0.22 + (H * 1.06 - H * 0.22) * easeOut(t)
    sun.alpha = 1 - easeOut(t)
  } else if (scene === 'day') {
    sun.y = H * 0.22
    sun.alpha = 1
  }
}

function settleToDay() {
  scene = 'day'
  stars = []
  buildClouds()
  clouds.forEach((c) => (c.alphaScale = 1))
  sun.x = width * 0.905
  sun.y = height * 0.22
  sun.r = Math.max(34, Math.min(64, width * 0.037))
  sun.alpha = 1
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

let queue = Promise.resolve()

/** 串行化：连点两次时排队执行，而不是把第二次丢掉（否则主题和场景会不一致） */
function transitionTo(next) {
  queue = queue.then(() => doTransition(next)).catch(() => {})
  return queue
}

async function doTransition(next) {
  const targetScene = next === THEMES.LIGHT ? 'day' : 'night'
  if (phase === 'idle' && scene === targetScene) {
    setTheme(next) // 场景已是目标，仅同步主题（例如刚进页面就点）
    return
  }
  const wait = (ms) => new Promise((r) => setTimeout(r, ms))
  phaseStart = performance.now()
  themeFlipped = false
  sun.x = width * 0.905
  sun.r = Math.max(34, Math.min(64, width * 0.037))

  if (next === THEMES.LIGHT) {
    phase = 'to-day'
    stars.forEach((s) => { s.fallVel = 0; s.falling = false })
    setTimeout(() => {
      if (phase !== 'to-day' || themeFlipped) return
      setTheme(THEMES.LIGHT)
      themeFlipped = true
      setTimeout(() => { if (phase === 'to-day') buildClouds() }, 380)
    }, 620)
    await wait(2150)
    phase = 'idle'
    settleToDay()
    if (!themeFlipped) { setTheme(THEMES.LIGHT); themeFlipped = true }
  } else {
    phase = 'to-night'
    buildStars(true)
    setTimeout(() => {
      if (phase !== 'to-night' || themeFlipped) return
      setTheme(THEMES.DARK)
      themeFlipped = true
    }, 500)
    await wait(2050)
    phase = 'idle'
    settleToNight()
    if (!themeFlipped) { setTheme(THEMES.DARK); themeFlipped = true }
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
  if (scene === 'day') settleToDay()
  else buildStars(false)

  ro = new ResizeObserver(() => resize())
  ro.observe(canvasRef.value)
  window.addEventListener('pointermove', onMove, { passive: true })
  window.addEventListener('pointerleave', onLeave, { passive: true })
  document.addEventListener('visibilitychange', onVisibility)

  // 接管主题切换：登录页用自己的动画，不播全屏覆盖
  unregister = registerThemeTransition(transitionTo)

  if (!reduced()) {
    running = true
    raf = requestAnimationFrame(loop)
  } else {
    draw()
  }

  // 调试句柄：供 tests/ui 的探针读取状态 / 强制触发事件
  window.__tpLoginDots = {
    get stars() { return stars },
    get clouds() { return clouds },
    get scene() { return scene },
    get phase() { return phase },
    get events() { return events },
    get contrail() { return contrail },
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
</style>
