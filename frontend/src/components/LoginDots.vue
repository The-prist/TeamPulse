<!--
  登录页背景粒子（白色圆点）

  需求：
    - 一堆白点，缓慢随机漂浮
    - 每颗点只在自己「附近一个小矩形」里活动，不跑到别处
    - 鼠标靠近时被吸引过去，但**不会**因此跑出自己的矩形

  实现要点：
    1. 每颗点带「家」(hx, hy) 与「活动半径」(rx, ry)。位置每帧算完都 clamp 回
       [hx ± rx, hy ± ry]，所以无论吸引力多大都出不去。
    2. 漂移用「缓慢随机加速度 + 阻尼」，而不是固定速度：这样轨迹是柔和的曲线，
       不会像匀速直线那样死板。
    3. 只有当鼠标距离 < ATTRACT_R 时才加吸引力，力的强度随距离线性衰减（近处更强），
       出圈后自然回到随机漂移。
    4. 画布 pointer-events: none，不挡登录表单；鼠标坐标从 window 监听。
    5. 尊重 prefers-reduced-motion：开启时只画静态点，不跑动画。
-->
<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const canvasRef = ref(null)
const props = defineProps({
  /** 目标密度：每多少平方像素一颗点 */
  density: { type: Number, default: 8200 },
  /** 吸附半径（px） */
  attractR: { type: Number, default: 185 },
})

let raf = 0
let ctx = null
let dots = []
let width = 0
let height = 0
let dpr = 1
let last = 0
let running = true
const mouse = { x: -9999, y: -9999, active: false }

const reduced = () => window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches

const rand = (a, b) => a + Math.random() * (b - a)

/** 建点：位置、家的范围、半径、透明度 */
function build() {
  const count = Math.max(36, Math.min(190, Math.round((width * height) / props.density)))
  dots = Array.from({ length: count }, () => {
    const hx = rand(0, width)
    const hy = rand(0, height)
    // 活动矩形：宽一点、扁一点，看起来更像"附近一小块区域"而不是沿圆周运动
    const rx = rand(22, 72)
    const ry = rand(18, 54)
    return {
      hx,
      hy,
      rx,
      ry,
      x: hx + rand(-rx, rx),
      y: hy + rand(-ry, ry),
      vx: rand(-0.06, 0.06),
      vy: rand(-0.06, 0.06),
      r: rand(1.0, 2.6),
      alpha: rand(0.22, 0.72),
      // 少数点带柔光，增加层次
      glow: Math.random() < 0.18,
    }
  })
}

function resize() {
  const el = canvasRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  width = Math.max(1, rect.width)
  height = Math.max(1, rect.height)
  dpr = Math.min(window.devicePixelRatio || 1, 2)
  el.width = Math.round(width * dpr)
  el.height = Math.round(height * dpr)
  ctx = el.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  build()
  // 调试句柄：仅供 tests/ui/probe-dots-bounds.mjs 校验「点是否越界」，不参与渲染逻辑
  window.__tpLoginDots = dots
  drawStatic()
}

function step(dt) {
  const k = Math.min(dt, 32) / 16.67 // 以 60fps 为基准的时间缩放，掉帧时速度一致
  for (const d of dots) {
    // ---- 缓慢随机漂移（加速度小，配合阻尼形成柔和游走）
    d.vx += rand(-0.004, 0.004) * k
    d.vy += rand(-0.004, 0.004) * k

    // ---- 鼠标吸引：距离越近力越大
    if (mouse.active) {
      const dx = mouse.x - d.x
      const dy = mouse.y - d.y
      const dist = Math.hypot(dx, dy)
      if (dist < props.attractR && dist > 0.001) {
        const pull = (1 - dist / props.attractR) ** 2 * 0.26
        d.vx += (dx / dist) * pull * k
        d.vy += (dy / dist) * pull * k
      }
    }

    // ---- 回家力：始终有一股很轻的力拉向自己的家，
    //      这样鼠标移开后点会慢慢散回原位，而不是一直挤在被吸过去的那一侧
    d.vx += (d.hx - d.x) * 0.0006 * k
    d.vy += (d.hy - d.y) * 0.0006 * k

    // ---- 阻尼 + 限速：避免被吸引时突然窜出去
    d.vx *= 0.968
    d.vy *= 0.968
    const speed = Math.hypot(d.vx, d.vy)
    const maxSpeed = mouse.active ? 1.9 : 0.45
    if (speed > maxSpeed) {
      d.vx = (d.vx / speed) * maxSpeed
      d.vy = (d.vy / speed) * maxSpeed
    }

    d.x += d.vx * k
    d.y += d.vy * k

    // ---- 绝不越界：撞到自己的矩形边界就停住并把速度反弹/衰减
    const minX = Math.max(0, d.hx - d.rx)
    const maxX = Math.min(width, d.hx + d.rx)
    const minY = Math.max(0, d.hy - d.ry)
    const maxY = Math.min(height, d.hy + d.ry)
    if (d.x < minX) { d.x = minX; d.vx = Math.abs(d.vx) * 0.4 }
    if (d.x > maxX) { d.x = maxX; d.vx = -Math.abs(d.vx) * 0.4 }
    if (d.y < minY) { d.y = minY; d.vy = Math.abs(d.vy) * 0.4 }
    if (d.y > maxY) { d.y = maxY; d.vy = -Math.abs(d.vy) * 0.4 }
  }
}

function draw() {
  if (!ctx) return
  ctx.clearRect(0, 0, width, height)
  for (const d of dots) {
    if (d.glow) {
      const g = ctx.createRadialGradient(d.x, d.y, 0, d.x, d.y, d.r * 6)
      g.addColorStop(0, `rgba(255,255,255,${d.alpha * 0.55})`)
      g.addColorStop(1, 'rgba(255,255,255,0)')
      ctx.fillStyle = g
      ctx.beginPath()
      ctx.arc(d.x, d.y, d.r * 6, 0, Math.PI * 2)
      ctx.fill()
    }
    ctx.beginPath()
    ctx.fillStyle = `rgba(255,255,255,${d.alpha})`
    ctx.arc(d.x, d.y, d.r, 0, Math.PI * 2)
    ctx.fill()
  }
}

function drawStatic() {
  if (!ctx) return
  ctx.clearRect(0, 0, width, height)
  for (const d of dots) {
    ctx.beginPath()
    ctx.fillStyle = `rgba(255,255,255,${d.alpha})`
    ctx.arc(d.x, d.y, d.r, 0, Math.PI * 2)
    ctx.fill()
  }
}

function loop(ts) {
  if (!running) return
  const dt = last ? ts - last : 16.67
  last = ts
  step(dt)
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
  } else if (!running) {
    running = true
    last = 0
    raf = requestAnimationFrame(loop)
  }
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
    raf = requestAnimationFrame(loop)
  }
})

onBeforeUnmount(() => {
  running = false
  cancelAnimationFrame(raf)
  ro?.disconnect()
  window.removeEventListener('pointermove', onMove)
  window.removeEventListener('pointerleave', onLeave)
  document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<template>
  <canvas ref="canvasRef" class="login-dots" aria-hidden="true"></canvas>
</template>

<style scoped>
.login-dots {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  pointer-events: none;
  z-index: 0;
}
</style>
