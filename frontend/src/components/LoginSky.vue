<!--
  登录页天空（按北京时间实时渲染）

  与上一版的区别
    - 不再有「昼夜切换」概念：天空、太阳、星空、云、流星/飞机全部由**北京时间**决定
    - 太阳位置遵守现实：方位角决定横向（东=右、西=左、正南居中），
      高度角决定纵向；太阳颜色随高度角由白转橙红
    - 底色按太阳高度角插值：深夜近黑蓝、临日出「上深蓝下发白」、傍晚「夕阳橙」

  左上角是调试卡片（仅登录页）
    - 显示北京时间、太阳高度角/方位角
    - 0–24 小时滑块：拖动即可预览任意时刻的天空，不必等到那个点
    - 「触发动效」：立刻放一个流星（夜）或飞机（昼）
    - 「回到现在」：退出预览，回到实时时间

  星星仍然是「各自小矩形内漂浮 + 鼠标吸引 + 绝不越界」，透明度按太阳高度角整体渐变。
-->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  azimuthToX,
  beijingDateAtHour,
  beijingHour,
  cloudAlpha,
  skyPalette,
  starAlpha,
  sunColor,
  sunPosition,
  sunVisibility,
} from '@/utils/solar'

const canvasRef = ref(null)
const props = defineProps({
  density: { type: Number, default: 8200 },
  attractR: { type: Number, default: 185 },
})

let ctx = null
let raf = 0
let running = false
let last = 0
let width = 0
let height = 0
let dpr = 1

/* 时间：previewHour 为 null 时跟随实时北京时间 */
const previewHour = ref(null)
const clock = ref('')
const phaseText = ref('')
const sunInfo = ref('')
const dragging = ref(false)

let stars = []
let clouds = []
let events = []
let contrail = []
let meteorTrail = []
let nextEventAt = 0

/** 当前用于渲染的北京时间（小时） */
let renderHour = 12
let sunAlt = 0
let sunAz = 0
let starsVisible = 0
let cloudsVisible = 0
let sunAlpha = 0
let palette = skyPalette(20, true)
let sunCss = '#fffcf0'
let sunX = 0.5
let sunY = 0.5
let sunR = 40
let cachedGradient = null
let cachedGradientKey = ''

const mouse = { x: -9999, y: -9999, active: false }
const rand = (a, b) => a + Math.random() * (b - a)
const clamp01 = (v) => Math.max(0, Math.min(1, v))
const reduced = () => window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches

/* ------------------------------------------------------------------ 建模 */

function makeStar() {
  const hx = rand(0, width)
  const hy = rand(0, height)
  return {
    hx,
    hy,
    rx: rand(22, 72),
    ry: rand(18, 54),
    x: hx + rand(-20, 20),
    y: hy + rand(-16, 16),
    vx: rand(-0.06, 0.06),
    vy: rand(-0.06, 0.06),
    size: rand(1, 2.6),
    alpha: rand(0.28, 0.85),
    glow: Math.random() < 0.18,
  }
}

function makeCloud() {
  const hx = rand(0, width)
  const hy = rand(height * 0.06, height * 0.7)
  const base = rand(56, 108)
  const puffs = []
  const n = 6
  for (let i = 0; i < n; i++) {
    const t = i / (n - 1) - 0.5
    const r = base * (0.62 + 0.42 * Math.cos(t * Math.PI * 0.85))
    puffs.push({ dx: t * base * 1.85 + rand(-5, 5), dy: -r * 0.55 + rand(-3, 3), r })
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
    alpha: rand(0.5, 0.85),
    gather: 1,
    puffs,
  }
}

function buildStars() {
  const count = Math.max(40, Math.min(200, Math.round((width * height) / props.density)))
  stars = Array.from({ length: count }, makeStar)
}

function buildClouds() {
  const n = Math.max(4, Math.min(8, Math.round(width / 300)))
  clouds = Array.from({ length: n }, makeCloud)
}

/* ------------------------------------------------------------------ 太阳与天色 */

function currentDate() {
  return previewHour.value === null
    ? new Date()
    : beijingDateAtHour(previewHour.value)
}

function refreshSky() {
  const date = currentDate()

  // 用于渲染的北京时间（预览时用滑块值，否则实时）
  if (previewHour.value !== null) {
    renderHour = previewHour.value
  } else {
    renderHour = beijingHour()
  }

  const { altitude, azimuth } = sunPosition(date)
  sunAlt = altitude
  sunAz = azimuth

  // 上午 / 下午：按方位角判断（正南 180° 之前算上午），比按 12 点更贴近现实
  const rising = azimuth < 181
  palette = skyPalette(altitude, rising)
  sunCss = sunColor(altitude).css
  sunAlpha = sunVisibility(altitude)
  starsVisible = starAlpha(altitude)
  cloudsVisible = cloudAlpha(altitude)

  // 屏幕坐标：方位角 → 横向（东右西左），高度角 → 纵向
  sunX = azimuthToX(azimuth, true) * width
  const altNorm = clamp01(altitude / 70)
  sunY = height * 0.94 - Math.pow(altNorm, 0.85) * height * 0.86
  sunR = Math.max(26, Math.min(56, width * 0.032)) * (1 + (1 - altNorm) * 0.18)

  // 调试信息
  const hh = Math.floor(renderHour)
  const mm = Math.floor((renderHour - hh) * 60)
  phaseText.value = describePhase(altitude, rising)
  clock.value = `${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}`
  sunInfo.value = `高度 ${altitude.toFixed(1)}°　方位 ${azimuth.toFixed(0)}°`
}

function describePhase(alt, rising) {
  if (alt > 12) return rising ? '上午' : '下午'
  if (alt > 3) return rising ? '清晨' : '傍晚'
  if (alt > -1.5) return rising ? '日出' : '日落'
  if (alt > -6) return rising ? '拂晓' : '暮色'
  if (alt > -12) return rising ? '航海曙光' : '航海暮光'
  if (alt > -18) return rising ? '天文曙光' : '天文暮光'
  return '夜间'
}

/* ------------------------------------------------------------------ 偶发事件 */

function scheduleNextEvent(now) {
  nextEventAt = now + rand(60_000, 150_000)
}

function spawnEvent(forceType) {
  const kind = forceType || (starsVisible > 0.45 ? 'night' : 'day')
  if (kind === 'night') {
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
      y: rand(height * 0.1, height * 0.32),
      vx: (fromLeft ? 1 : -1) * rand(0.19, 0.27),
      vy: rand(-0.012, -0.004),
      life: 0,
      puffTimer: 0,
    })
  }
  scheduleNextEvent(performance.now())
}

/* ------------------------------------------------------------------ 物理 */

function stepStars(dt) {
  const k = Math.min(dt, 32) / 16.67
  for (const s of stars) {
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
    c.vx += rand(-0.002, 0.002) * k
    c.vy += rand(-0.0015, 0.0015) * k
    c.vx += (c.hx - c.x) * 0.0002 * k
    c.vy += (c.hy - c.y) * 0.0002 * k
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
      return !off
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

  if (now >= nextEventAt && !events.length) {
    // 只有星星可见时才放流星，白天才放飞机
    if (starsVisible > 0.45) spawnEvent('night')
    else if (sunAlt > 4) spawnEvent('day')
    else scheduleNextEvent(now)
  }
}

/* ------------------------------------------------------------------ 绘制 */

function skyGradient() {
  const key = `${palette.top}|${palette.mid}|${palette.bottom}|${height}`
  if (cachedGradient && cachedGradientKey === key) return cachedGradient
  const g = ctx.createLinearGradient(0, 0, 0, height)
  g.addColorStop(0, palette.top)
  g.addColorStop(0.58, palette.mid)
  g.addColorStop(1, palette.bottom)
  cachedGradient = g
  cachedGradientKey = key
  return g
}

function drawSky() {
  ctx.fillStyle = skyGradient()
  ctx.fillRect(0, 0, width, height)
}

function drawSun() {
  if (sunAlpha <= 0.01) return

  // 太阳本身的颜色随高度角变化；地平线附近光晕更大更暖
  const glowR = sunR * (5.2 + (1 - clamp01(sunAlt / 25)) * 2.4)
  const g = ctx.createRadialGradient(sunX, sunY, 0, sunX, sunY, glowR)
  g.addColorStop(0, sunCss.replace('rgb', 'rgba').replace(')', `, ${0.55 * sunAlpha})`))
  g.addColorStop(0.22, sunCss.replace('rgb', 'rgba').replace(')', `, ${0.28 * sunAlpha})`))
  g.addColorStop(1, sunCss.replace('rgb', 'rgba').replace(')', ', 0)'))
  ctx.fillStyle = g
  ctx.beginPath()
  ctx.arc(sunX, sunY, glowR, 0, Math.PI * 2)
  ctx.fill()

  ctx.fillStyle = sunCss
  ctx.globalAlpha = sunAlpha
  ctx.beginPath()
  ctx.arc(sunX, sunY, sunR, 0, Math.PI * 2)
  ctx.fill()
  ctx.globalAlpha = 1
}

function drawStars() {
  if (starsVisible <= 0.01) return
  const now = performance.now()
  for (const s of stars) {
    const twinkle = 0.75 + 0.25 * Math.sin(now / 900 + s.hx)
    const a = s.alpha * starsVisible * twinkle
    if (a <= 0.01) continue
    if (s.glow) {
      const g = ctx.createRadialGradient(s.x, s.y, 0, s.x, s.y, s.size * 6)
      g.addColorStop(0, `rgba(255,255,255,${Math.min(1, a * 0.55)})`)
      g.addColorStop(1, 'rgba(255,255,255,0)')
      ctx.fillStyle = g
      ctx.beginPath()
      ctx.arc(s.x, s.y, s.size * 6, 0, Math.PI * 2)
      ctx.fill()
    }
    ctx.beginPath()
    ctx.fillStyle = `rgba(255,255,255,${Math.min(1, a)})`
    ctx.arc(s.x, s.y, s.size, 0, Math.PI * 2)
    ctx.fill()
  }
}

function drawClouds() {
  if (cloudsVisible <= 0.01) return
  // 低太阳时把云染上一点暖色
  const warm = clamp01((12 - sunAlt) / 14)
  const tint = warm > 0.35 ? `rgba(255, 214, 170, ${warm * 0.55})` : null

  for (const c of clouds) {
    const a = c.alpha * cloudsVisible
    if (a <= 0.01) continue
    for (const p of c.puffs) {
      const px = c.x + p.dx
      const py = c.y + p.dy
      const g = ctx.createRadialGradient(px, py - p.r * 0.25, 0, px, py, p.r)
      g.addColorStop(0, `rgba(255,255,255,${a})`)
      g.addColorStop(0.62, `rgba(255,255,255,${a * 0.7})`)
      g.addColorStop(1, 'rgba(255,255,255,0)')
      ctx.fillStyle = g
      ctx.beginPath()
      ctx.arc(px, py, p.r, 0, Math.PI * 2)
      ctx.fill()

      if (tint) {
        const gt = ctx.createRadialGradient(px, py + p.r * 0.35, 0, px, py, p.r)
        gt.addColorStop(0, tint)
        gt.addColorStop(1, 'rgba(255,214,170,0)')
        ctx.fillStyle = gt
        ctx.beginPath()
        ctx.arc(px, py, p.r, 0, Math.PI * 2)
        ctx.fill()
      }
    }
  }
}

function drawEvents() {
  const now = performance.now()

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
  drawSky()
  drawSun()
  drawStars()
  drawClouds()
  drawEvents()
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
  cachedGradient = null

  if (resized || !stars.length) buildStars()
  if (resized || !clouds.length) buildClouds()
  refreshSky()
  if (!nextEventAt) scheduleNextEvent(performance.now())
  draw()
}

function loop(ts) {
  if (!running) return
  const dt = last ? ts - last : 16.67
  last = ts
  refreshSky()
  stepStars(dt)
  stepClouds(dt)
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

/* ------------------------------------------------------------------ 调试卡片交互 */

const sliderValue = computed({
  get: () => (previewHour.value === null ? Number(beijingHour().toFixed(2)) : previewHour.value),
  set: (v) => { previewHour.value = Number(v) },
})

function onSliderInput(e) {
  dragging.value = true
  previewHour.value = Number(e.target.value)
}

function onSliderChange() {
  dragging.value = false
}

function backToNow() {
  previewHour.value = null
  cachedGradient = null
  refreshSky()
}

function triggerEffect() {
  spawnEvent(starsVisible > 0.45 ? 'night' : 'day')
}

/** 外部（测试 / 控制台）调用：直接跳到某个小时 */
function setHour(h) {
  previewHour.value = h === null ? null : Math.max(0, Math.min(24, Number(h)))
  cachedGradient = null
  refreshSky()
  draw()
}

let ro = null

onMounted(() => {
  resize()
  ro = new ResizeObserver(() => resize())
  ro.observe(canvasRef.value)
  window.addEventListener('pointermove', onMove, { passive: true })
  window.addEventListener('pointerleave', onLeave, { passive: true })
  document.addEventListener('visibilitychange', onVisibility)

  if (!reduced()) {
    running = true
    raf = requestAnimationFrame(loop)
  } else {
    draw()
  }

  window.__tpLoginSky = {
    setHour,
    backToNow,
    get hour() { return renderHour },
    get sun() { return { altitude: sunAlt, azimuth: sunAz, x: sunX, y: sunY, alpha: sunAlpha, color: sunCss } },
    get sky() { return { top: palette.top, mid: palette.mid, bottom: palette.bottom } },
    get starsVisible() { return starsVisible },
    get cloudsVisible() { return cloudsVisible },
    get phase() { return phaseText.value },
    spawn: (t) => spawnEvent(t),
    get events() { return events },
  }
})

onBeforeUnmount(() => {
  running = false
  cancelAnimationFrame(raf)
  ro?.disconnect()
  window.removeEventListener('pointermove', onMove)
  window.removeEventListener('pointerleave', onLeave)
  document.removeEventListener('visibilitychange', onVisibility)
  delete window.__tpLoginSky
})
</script>

<template>
  <canvas ref="canvasRef" class="login-sky" aria-hidden="true"></canvas>

  <!-- 主页调试卡片：北京时间 / 太阳位置 / 0–24 时间轴 / 触发按钮 -->
  <section class="debug-card" aria-label="天空调试">
    <header class="dc-head">
      <span class="dc-title">调试 · 天空</span>
      <span class="dc-clock">{{ clock }}</span>
    </header>

    <div class="dc-row">
      <span class="dc-phase">{{ phaseText }}</span>
      <span class="dc-sun">{{ sunInfo }}</span>
    </div>

    <div class="dc-slider">
      <input
        type="range"
        min="0"
        max="24"
        step="0.25"
        :value="sliderValue"
        aria-label="按小时预览天空"
        @input="onSliderInput"
        @change="onSliderChange"
      />
      <div class="dc-ticks">
        <span>0</span><span>6</span><span>12</span><span>18</span><span>24</span>
      </div>
    </div>

    <div class="dc-actions">
      <button type="button" class="dc-btn" @click="triggerEffect">触发动效</button>
      <button type="button" class="dc-btn ghost" :disabled="previewHour === null" @click="backToNow">
        回到现在
      </button>
    </div>

    <p v-if="previewHour !== null" class="dc-hint">预览中：拖动滑块观察不同时刻，点「回到现在」恢复实时</p>
    <p v-else class="dc-hint">跟随北京时间实时渲染</p>
  </section>
</template>

<style scoped>
.login-sky {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  pointer-events: none;
  z-index: 0;
}

.debug-card {
  position: fixed;
  left: 18px;
  top: 18px;
  z-index: 40;
  width: 268px;
  padding: 12px 14px 10px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  background: rgba(8, 18, 34, 0.62);
  backdrop-filter: blur(10px);
  box-shadow: 0 12px 32px -12px rgba(2, 6, 23, 0.7);
  color: rgba(255, 255, 255, 0.92);
  font-size: 12px;
  line-height: 1.5;
  user-select: none;
}

.dc-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 6px;
}

.dc-title {
  font-weight: 600;
  letter-spacing: 0.4px;
}

.dc-clock {
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #ffe6a8;
}

.dc-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: rgba(255, 255, 255, 0.72);
  margin-bottom: 8px;
}

.dc-phase {
  color: #9ecbff;
  font-weight: 600;
}

.dc-sun {
  font-variant-numeric: tabular-nums;
}

.dc-slider input[type='range'] {
  width: 100%;
  height: 4px;
  appearance: none;
  border-radius: 999px;
  background: linear-gradient(to right, #0b1a30, #2a5f9e, #7fc0ea, #ffd9a0, #2a5f9e, #0b1a30);
  outline: none;
  cursor: pointer;
}

.dc-slider input[type='range']::-webkit-slider-thumb {
  appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid #2f6fd0;
  box-shadow: 0 0 8px rgba(255, 255, 255, 0.6);
}

.dc-ticks {
  display: flex;
  justify-content: space-between;
  margin-top: 2px;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.45);
}

.dc-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.dc-btn {
  flex: 1;
  height: 28px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.26);
  background: rgba(47, 111, 208, 0.75);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.16s ease, transform 0.1s ease;
}

.dc-btn:hover {
  background: rgba(47, 111, 208, 0.95);
}

.dc-btn:active {
  transform: scale(0.97);
}

.dc-btn.ghost {
  background: rgba(255, 255, 255, 0.1);
}

.dc-btn.ghost:disabled {
  opacity: 0.45;
  cursor: default;
}

.dc-hint {
  margin: 8px 0 0;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.55);
}
</style>
