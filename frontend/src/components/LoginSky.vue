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

// 矢量云素材（SVG）：来自项目方提供的 7 个变体，白团 + 横向蓝色雾带
import cloud1 from '@/assets/sky/cloud-1.svg'
import cloud2 from '@/assets/sky/cloud-2.svg'
import cloud3 from '@/assets/sky/cloud-3.svg'
import cloud4 from '@/assets/sky/cloud-4.svg'
import cloud5 from '@/assets/sky/cloud-5.svg'
import cloud6 from '@/assets/sky/cloud-6.svg'
import cloud7 from '@/assets/sky/cloud-7.svg'

const CLOUD_SRC = [cloud1, cloud2, cloud3, cloud4, cloud5, cloud6, cloud7]
const cloudImages = []
/** 模板用的云列表（只放渲染需要的最小字段） */
const cloudList = ref([])
const cloudEls = []
import {
  beijingDateAtHour,
  cloudAlpha,
  beijingHour,
  moonIllumination,
  moonPhaseName,
  moonPosition,
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
const collapsed = ref(false)

let stars = []
const cloudsVisible = ref(0)
let events = []
let contrail = []
let meteorTrail = []
let nextEventAt = 0

/** 当前用于渲染的北京时间（小时） */
let renderHour = 12
let sunAlt = 0
let sunAz = 0
let starsVisible = 0
let sunAlpha = 0
let palette = skyPalette(20, true)
let sunCss = '#fffcf0'
let sunX = 0.5
let sunY = 0.5
let sunR = 40
let cachedGradient = null
let cachedGradientKey = ''
let moonAlt = 0
let moonAz = 0
let moonFrac = 0
let moonPhase = 0
let moonAlpha = 0
let moonX = 0
let moonY = 0
let moonR = 24
const moonLabel = ref('')
/** 测试用：固定月相与位置（null 表示跟随真实天文计算） */
let moonOverride = null

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

/**
 * 动漫式积云：用较多小圆堆出轮廓
 *   - 沿基线的"底排"圆：底边大致齐平，形成云的平底
 *   - 中排更大，顶排 2~3 个"鼓包"，做出顶部起伏
 *   - 每朵云尺度不同（22~150），小的做远景点缀
 */
/** 云落点：只在可见区域（顶部带 / 左右留白），避免被中间卡片挡住 */
function pickCloudSpot(region) {
  if (region === 'left') return { x: rand(width * 0.06, width * 0.12), y: rand(height * 0.28, height * 0.68) }
  if (region === 'right') return { x: rand(width * 0.88, width * 0.94), y: rand(height * 0.28, height * 0.68) }
  return { x: rand(width * 0.12, width * 0.88), y: rand(height * 0.1, height * 0.21) }
}

function buildClouds() {
  const list = []

  // ① 底层云带（cloud-7）：横跨整个屏幕，左右被屏幕边缘自然切掉，永远在最下层
  const bandImg = cloudImages[6]
  const bandRatio = bandImg?.naturalHeight ? bandImg.naturalHeight / bandImg.naturalWidth : 0.157
  const bandW = width * 1.34
  list.push({
    src: CLOUD_SRC[6],
    imgIndex: 6,
    isBand: true,
    x: width / 2,
    y: height * 0.93,
    hx: width / 2,
    hy: height * 0.93,
    w: bandW,
    h: bandW * bandRatio,
    alpha: 0.95,
    vx: 0,
    vy: 0,
    rx: 0,
    ry: 0,
  })

  // ② 其余 6 朵：分布在上方与左右留白，尺寸放大
  const plan = ['top', 'top', 'left', 'right', 'top', 'left']
  const usedIdx = new Set([6])
  for (const region of plan) {
    let imgIndex = Math.floor(Math.random() * 6)
    for (let k = 0; k < 6 && usedIdx.has(imgIndex); k++) {
      imgIndex = (imgIndex + 1) % 6
    }
    usedIdx.add(imgIndex)

    const img = cloudImages[imgIndex]
    const ratio = img?.naturalHeight ? img.naturalHeight / img.naturalWidth : 0.38
    const targetW = region === 'top' ? rand(300, 430) : rand(250, 340)

    let spot = null
    for (let tries = 0; tries < 30; tries++) {
      const candidate = pickCloudSpot(region)
      spot = candidate
      if (!list.some((c) => !c.isBand && Math.hypot(c.hx - candidate.x, c.hy - candidate.y) < 340)) {
        spot = candidate
        break
      }
      spot = null
    }
    if (!spot) continue // 放不下就少放一朵，避免两朵叠在一起

    list.push({
      src: CLOUD_SRC[imgIndex],
      imgIndex,
      x: spot.x,
      y: spot.y,
      hx: spot.x,
      hy: spot.y,
      w: targetW,
      h: targetW * ratio,
      alpha: rand(0.9, 1),
      vx: rand(-0.012, 0.012),
      vy: rand(-0.004, 0.004),
      rx: rand(50, 110),
      ry: rand(16, 40),
    })
  }

  cloudList.value = list
  cloudEls.length = 0
}

function stepClouds(dt) {
  const k = Math.min(dt, 32) / 16.67
  for (const c of cloudList.value) {
    if (c.isBand) continue // 全宽云带固定不动，否则两侧会露出缝隙
    c.vx += rand(-0.0006, 0.0006) * k
    c.vy += rand(-0.0004, 0.0004) * k
    // 很轻的回家力：云慢慢漂回原位附近
    c.vx += (c.hx - c.x) * 0.00012 * k
    c.vy += (c.hy - c.y) * 0.00012 * k
    c.vx *= 0.995
    c.vy *= 0.995
    c.x += c.vx * k
    c.y += c.vy * k
    c.x = Math.max(Math.max(0, c.hx - c.rx), Math.min(Math.min(width, c.hx + c.rx), c.x))
    c.y = Math.max(Math.max(0, c.hy - c.ry), Math.min(Math.min(height, c.hy + c.ry), c.y))
  }
  // 位置每帧直接写 DOM，避免 Vue 在 60fps 下反复 diff
  for (let i = 0; i < cloudEls.length; i++) {
    const el = cloudEls[i]
    const c = cloudList.value[i]
    if (!el || !c) continue
    el.style.transform = `translate3d(${(c.x - c.w / 2).toFixed(1)}px, ${(c.y - c.h / 2).toFixed(1)}px, 0)`
    el.style.opacity = String(c.alpha * cloudsVisible.value)
  }
}

function buildStars() {
  const count = Math.max(40, Math.min(200, Math.round((width * height) / props.density)))
  stars = Array.from({ length: count }, makeStar)
}

/* ------------------------------------------------------------------ 太阳与天色 */

function currentDate() {
  return previewHour.value === null
    ? new Date()
    : beijingDateAtHour(previewHour.value)
}

/**
 * 天体统一圆弧
 *
 * 需求：太阳与月亮走同一条圆弧。所以两者的屏幕位置都用同一组参数、同一个公式：
 *   纵向上，高度角映射到 0~90°，0° 落在地平线、42° 及以上就是弧顶；
 *   横向上，方位角决定偏向（东=左 / 西=右），并用「离正南多远」控制横向偏移：
 *     正南（180°）在中线，正东/正西（90°/270°）到两侧最远处。
 * 这样两颗天体的轨迹是同一个椭圆弧，只是因为各自的高度角/方位角不同而取不同位置。
 */
const ARC = {
  inset: 0.06, // 左右留白，避免贴边被裁
  horizon: 0.94, // 地平线的纵向位置
  height: 0.9, // 弧的高度占比
  apexAlt: 42, // 高度角达到它就算弧顶
}

function arcPos(altitude, azimuth) {
  const u = Math.max(0, Math.min(1, altitude / ARC.apexAlt))
  const ang = (u * Math.PI) / 2
  const lateral = Math.max(0, Math.min(1, Math.abs(azimuth - 180) / 90))
  const side = azimuth >= 180 ? 1 : -1
  const halfW = 0.5 - ARC.inset
  const xNorm = 0.5 + side * halfW * Math.cos(ang) * lateral
  // 幂次 0.62：把中等高度角抬得更高一些，避免 9~15 点的太阳落在中间卡片后面；
  // 低高度角（清早/傍晚）横向本来就在左右留白，纵向仍贴近地平线
  const yNorm = ARC.horizon - ARC.height * Math.pow(Math.sin(ang), 0.62)
  return { x: xNorm * width, y: yNorm * height }
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
  cloudsVisible.value = cloudAlpha(altitude)

  // 与月亮共用同一条圆弧
  const sunPos = arcPos(altitude, azimuth)
  sunX = sunPos.x
  sunY = sunPos.y
  const altNorm = clamp01(altitude / ARC.apexAlt)
  sunR = Math.max(26, Math.min(56, width * 0.032)) * (1 + (1 - altNorm) * 0.18)

  // ---- 月亮：位置、月相、可见度
  const moon = moonPosition(date)
  moonAlt = moon.altitude
  moonAz = moon.azimuth
  const illum = moonIllumination(date)
  moonFrac = illum.fraction
  moonPhase = illum.phase
  const moonPos = arcPos(moonAlt, moonAz)
  moonX = moonPos.x
  moonY = moonPos.y
  moonR = sunR * 0.92
  // 高度角 < -1.5° 时落到地平线下；白天月亮仍然可见，只是被天空压暗
  const dayFactor = 1 - 0.55 * clamp01((altitude + 4) / 14)
  moonAlpha = Math.max(0, Math.min(1, (moonAlt + 1.5) / 4)) * dayFactor
  moonLabel.value = `${moonPhaseName(moonPhase)} ${(moonFrac * 100).toFixed(0)}%`

  if (moonOverride) {
    moonFrac = moonOverride.frac
    moonPhase = moonOverride.phase
    moonX = width * moonOverride.x
    moonY = height * moonOverride.y
    moonAlpha = 1
    moonR = Math.max(34, Math.min(64, width * 0.037)) * 0.92
    moonLabel.value = `测试月相 ${(moonFrac * 100).toFixed(0)}%`
  }

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

/**
 * 画月亮（含真实月相）
 *
 * 月相形状用逐行扫描填充：对每一行 y，圆面上该行的水平半宽是
 *   half = sqrt(r² − y²)
 * 终结线在该行的横坐标是 aNorm·half，其中 aNorm = 1 − 2·fraction：
 *   waxing（0<phase<0.5）亮面在右，填充 [aNorm·half, half]
 *   waning（0.5<phase<1）亮面在左，填充 [−half, −aNorm·half]
 * f=0.5 时 aNorm=0 → 正好是半圆；f→0 退化成细月牙；f→1 变成满圆。
 */
function drawMoon() {
  if (moonAlpha <= 0.01) return

  const f = moonFrac
  const waxing = moonPhase < 0.5
  const aNorm = 1 - 2 * f

  ctx.save()

  // ① 整轮暗面（地照），让新月也能看出圆盘位置
  ctx.globalAlpha = moonAlpha * 0.5
  ctx.fillStyle = 'rgba(24, 34, 52, 0.85)'
  ctx.beginPath()
  ctx.arc(moonX, moonY, moonR, 0, Math.PI * 2)
  ctx.fill()

  // ② 柔光
  const halo = ctx.createRadialGradient(moonX, moonY, moonR * 0.9, moonX, moonY, moonR * 3.4)
  halo.addColorStop(0, `rgba(226, 236, 250, ${0.22 * moonAlpha})`)
  halo.addColorStop(1, 'rgba(226, 236, 250, 0)')
  ctx.globalAlpha = 1
  ctx.fillStyle = halo
  ctx.beginPath()
  ctx.arc(moonX, moonY, moonR * 3.4, 0, Math.PI * 2)
  ctx.fill()

  // ③ 亮面：逐行填充
  ctx.globalAlpha = moonAlpha
  ctx.fillStyle = '#f2f5ec'
  const step = Math.max(0.5, moonR / 48)
  for (let dy = -moonR; dy <= moonR; dy += step) {
    const half = Math.sqrt(Math.max(0, moonR * moonR - dy * dy))
    let x0
    let x1
    if (waxing) {
      x0 = aNorm * half
      x1 = half
    } else {
      x0 = -half
      x1 = -aNorm * half
    }
    const w = x1 - x0
    if (w <= 0.05) continue
    ctx.fillRect(moonX + x0, moonY + dy, w, step + 0.4)
  }

  // ④ 环形山质感：只在亮面范围内点几处淡灰
  ctx.globalAlpha = moonAlpha * 0.16
  ctx.fillStyle = '#9aa6b4'
  for (const [cx, cy, cr] of [
    [-0.28, -0.22, 0.16],
    [0.1, -0.36, 0.1],
    [0.24, 0.12, 0.13],
    [-0.12, 0.3, 0.09],
    [0.02, 0.02, 0.07],
  ]) {
    const px = moonX + cx * moonR
    const py = moonY + cy * moonR
    // 只画在亮面那一侧，避免月牙被灰点污染
    if (waxing ? px < moonX + aNorm * moonR : px > moonX - aNorm * moonR) continue
    ctx.beginPath()
    ctx.arc(px, py, cr * moonR, 0, Math.PI * 2)
    ctx.fill()
  }

  ctx.restore()
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

/**
 * 画云：单一轮廓 + 内部渐变（这是参考图的关键）
 *   ① 把所有小圆合成一条路径，一次性填充"上白下淡蓝"的竖向渐变
 *   ② clip 到轮廓内，叠底部蓝色阴影 → 天空反光/明暗层次
 *   ③ 每个大鼓包底边再补一小块阴影，做出"一朵朵"的分离感
 *   ④ 顶部几处高光，让云顶更亮
 */
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
  drawMoon()
  drawStars()
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
  if (resized || !cloudList.value.length) buildClouds()
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
  // 预加载矢量云（构建后被内联成 data URI），加载完成后回填真实宽高比
  CLOUD_SRC.forEach((src, i) => {
    const img = new Image()
    img.onload = () => {
      cloudList.value.forEach((c) => {
        if (c.imgIndex === i) c.h = c.w * (img.naturalHeight / img.naturalWidth)
      })
    }
    img.src = src
    cloudImages[i] = img
  })

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
    get clouds() { return cloudList.value.map((c) => ({ x: Math.round(c.x), y: Math.round(c.y), w: Math.round(c.w), imgIndex: c.imgIndex })) },
    get cloudsVisible() { return cloudsVisible.value },
    get __cloudImages() { return cloudImages.map((i) => (i ? { complete: i.complete, w: i.naturalWidth, h: i.naturalHeight, src: String(i.src).slice(0, 40) } : null)) },
    get starsVisible() { return starsVisible },
    get moon() { return { altitude: moonAlt, azimuth: moonAz, fraction: moonFrac, phase: moonPhase, x: moonX, y: moonY, alpha: moonAlpha } },
    // 仅供测试：临时覆盖月相与屏幕位置，验证不同月牙形状的绘制
    __setMoonPhase(fraction, phase = fraction, xRatio = 0.5, yRatio = 0.3) {
      moonOverride = { frac: fraction, phase, x: xRatio, y: yRatio }
    },
    __clearMoonOverride() { moonOverride = null },
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

  <!-- 云层：用 DOM <img> 渲染 SVG，浏览器原生栅格化，任意 DPI 都清晰 -->
  <div class="cloud-layer" aria-hidden="true">
    <img
      v-for="(c, i) in cloudList"
      :key="i"
      :ref="(el) => { if (el) cloudEls[i] = el }"
      :src="c.src"
      class="cloud"
      :style="{ width: c.w + 'px', height: c.h + 'px' }"
      alt=""
    />
  </div>

  <!-- 主页调试卡片：北京时间 / 太阳位置 / 0–24 时间轴 / 触发按钮 -->
  <section class="debug-card" aria-label="天空调试">
    <header class="dc-head">
      <span class="dc-title">调试 · 天空</span>
      <span class="dc-right">
        <span class="dc-clock">{{ clock }}</span>
        <button
          type="button"
          class="dc-collapse"
          :aria-label="collapsed ? '展开调试卡片' : '折叠调试卡片'"
          :title="collapsed ? '展开' : '折叠'"
          @click="collapsed = !collapsed"
        >{{ collapsed ? '▸' : '▾' }}</button>
      </span>
    </header>

    <div v-show="!collapsed" class="dc-row">
      <span class="dc-phase">{{ phaseText }}</span>
      <span class="dc-sun">{{ sunInfo }}</span>
    </div>

    <div v-show="!collapsed" class="dc-row dc-moon">
      <span class="dc-moon-label">月亮 {{ moonLabel }}</span>
      <span class="dc-sun">高度 {{ moonAlt.toFixed(0) }}°　方位 {{ moonAz.toFixed(0) }}°</span>
    </div>

    <div v-show="!collapsed" class="dc-slider">
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

    <div v-show="!collapsed" class="dc-actions">
      <button type="button" class="dc-btn" @click="triggerEffect">触发动效</button>
      <button type="button" class="dc-btn ghost" :disabled="previewHour === null" @click="backToNow">
        回到现在
      </button>
    </div>

    <p v-show="!collapsed" v-if="previewHour !== null" class="dc-hint">预览中：拖动滑块观察不同时刻，点「回到现在」恢复实时</p>
    <p v-show="!collapsed" v-else class="dc-hint">跟随北京时间实时渲染</p>
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

.cloud-layer {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}

.cloud {
  position: absolute;
  left: 0;
  top: 0;
  will-change: transform, opacity;
  user-select: none;
  -webkit-user-drag: none;
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

.dc-right {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.dc-collapse {
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  border-radius: 5px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.8);
  font-size: 10px;
  line-height: 1;
  cursor: pointer;
  padding: 0;
}

.dc-collapse:hover {
  background: rgba(255, 255, 255, 0.18);
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

.dc-moon {
  margin-top: -4px;
}

.dc-moon-label {
  color: #ffd9a0;
  font-weight: 600;
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
