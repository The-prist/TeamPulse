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

// 矢量云素材（SVG 原文）：运行时按「时段 × 天气」重上色，因此不需要为每种天气准备素材
import cloud1Raw from '@/assets/sky/cloud-1.svg?raw'
import cloud2Raw from '@/assets/sky/cloud-2.svg?raw'
import cloud3Raw from '@/assets/sky/cloud-3.svg?raw'
import cloud4Raw from '@/assets/sky/cloud-4.svg?raw'
import cloud5Raw from '@/assets/sky/cloud-5.svg?raw'
import cloud6Raw from '@/assets/sky/cloud-6.svg?raw'
import cloud7Raw from '@/assets/sky/cloud-7.svg?raw'
import { useSkyStore } from '@/stores/sky'
import {
  adjustSky,
  BUCKET_LABEL,
  BUCKETS,
  CLOUD_PARAMS,
  cloudPalette,
  bucketHasIntensity,
  cloudVisibility,
  INTENSITY_FACTOR,
  INTENSITY_LABEL,
  recolorSvg,
  skyPhase,
  starDim,
  sunDim,
} from '@/utils/skyWeather'

const CLOUD_RAW = [cloud1Raw, cloud2Raw, cloud3Raw, cloud4Raw, cloud5Raw, cloud6Raw, cloud7Raw]
const skyStore = useSkyStore()

/** 当前生效的云图 URL 列表（颜色随天气/时段变化），按 key 缓存避免反复编码 */
const cloudSrcCache = new Map()
let currentCloudSrc = []
let currentWeatherBucket = 'partly'
let currentPhase = 'day'

function refreshCloudPalette() {
  const bucket = skyStore.bucket
  const palette = cloudPalette(bucket, currentPhase)
  const key = `${bucket}|${currentPhase}`
  if (cloudSrcCache.has(key)) {
    currentCloudSrc = cloudSrcCache.get(key)
    currentWeatherBucket = bucket
    return
  }
  const urls = CLOUD_RAW.map((raw) => recolorSvg(raw, palette.base, palette.body))
  cloudSrcCache.set(key, urls)
  currentCloudSrc = urls
  currentWeatherBucket = bucket
}

const cloudImages = []
/** 模板用的云列表（只放渲染需要的最小字段） */
const cloudList = ref([])
const cloudEls = []
import {
  beijingDateAtHour,
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
/** 风速：px/帧（60fps 基准），正数向右；实际速度还要乘天气系数 */
const WIND = 0.16
/** 天气特效粒子（雨丝 / 雪点） */
let particles = []
let particlesBucket = ''
/** 雷电闪光强度 0~1 */
let flash = 0
let flashTimer = 0

/* ---- 积水 / 积雪 ---- */
const overlayRef = ref(null)
let octx = null
let owidth = 0
let oheight = 0
/** 登录卡片在天空坐标系里的位置（用于「水涨到卡片下沿」「卡片上方积雪」） */
let cardBox = { left: 0, top: 0, right: 0, bottom: 0, width: 0, radius: 18 }
/** 堆积进度 0~1 */
let accum = 0
/** 当前堆积类型：rain / snow / '' */
let accumKind = ''
/** 飞溅的水花 */
let splashes = []
/** 水面波纹相位 */
let waterPhase = 0
/** 长云带的滚动偏移（0 ~ bandW 循环） */
let bandOffset = 0
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
  if (region === 'left') return { x: rand(width * 0.06, width * 0.12), y: rand(height * 0.3, height * 0.62) }
  if (region === 'right') return { x: rand(width * 0.88, width * 0.94), y: rand(height * 0.3, height * 0.62) }
  // 中段：只有阴雨雪这种「满天云」的天气才会用到
  if (region === 'mid') return { x: rand(width * 0.16, width * 0.84), y: rand(height * 0.3, height * 0.46) }
  return { x: rand(width * 0.12, width * 0.88), y: rand(height * 0.17, height * 0.27) }
}

/** 把当前配色的云图塞进 Image 缓存，供取宽高比用 */
function preloadCloudImages() {
  refreshCloudPalette()
  currentCloudSrc.forEach((src, i) => {
    const img = new Image()
    img.onload = () => {
      cloudList.value.forEach((c) => {
        if (c.imgIndex === i) c.h = c.w * (img.naturalHeight / img.naturalWidth)
      })
    }
    img.src = src
    cloudImages[i] = img
  })
}

function buildClouds() {
  refreshCloudPalette()
  const params = CLOUD_PARAMS[currentWeatherBucket] || CLOUD_PARAMS.partly
  const list = []

  // ① 底层长云带：两张同样大小拼接，向右移动时无缝循环
  const bandImg = cloudImages[6]
  const bandRatio = bandImg?.naturalHeight ? bandImg.naturalHeight / bandImg.naturalWidth : 0.157
  const bandW = width * 1.25
  bandOffset = 0
  if (params.count > 0) {
    for (let i = 0; i < 2; i++) {
      list.push({
        src: currentCloudSrc[6],
        imgIndex: 6,
        isBand: true,
        bandIndex: i,
        x: width / 2 - i * bandW,
        y: height * 0.95,
        hx: width / 2,
        hy: height * 0.95,
        w: bandW,
        h: bandW * bandRatio,
        alpha: 0.95 * params.alpha,
        vx: 0,
        vy: 0,
        rx: 0,
        ry: 0,
      })
    }
  }

  // ② 单体云：数量由天气决定（晴 = 0 朵）
  const singles = Math.max(0, params.count - (params.count >= 5 ? 1 : 0))
  const dense = currentWeatherBucket === 'overcast' || currentWeatherBucket === 'rain'
    || currentWeatherBucket === 'snow' || currentWeatherBucket === 'thunder'
  const fullPlan = dense
    ? ['top', 'mid', 'top', 'left', 'right', 'mid', 'top', 'left', 'right', 'mid']
    : ['top', 'left', 'right', 'top', 'left', 'right', 'top']
  const plan = fullPlan.slice(0, singles)
  const usedIdx = new Set([6])
  for (const region of plan) {
    let imgIndex = Math.floor(Math.random() * 6)
    for (let k = 0; k < 6 && usedIdx.has(imgIndex); k++) imgIndex = (imgIndex + 1) % 6
    usedIdx.add(imgIndex)

    const img = cloudImages[imgIndex]
    const ratio = img?.naturalHeight ? img.naturalHeight / img.naturalWidth : 0.38
    const baseW = region === 'top' ? rand(320, 440) : rand(270, 360)
    const targetW = baseW * params.size

    let spot = null
    for (let tries = 0; tries < 30; tries++) {
      const candidate = pickCloudSpot(region)
      const gap = 380 * params.size * (dense ? 0.72 : 1)
      if (!list.some((c) => !c.isBand && Math.hypot(c.hx - candidate.x, c.hy - candidate.y) < gap)) {
        spot = candidate
        break
      }
    }
    if (!spot) continue

    list.push({
      src: currentCloudSrc[imgIndex],
      imgIndex,
      region,
      x: spot.x,
      y: spot.y,
      hx: spot.x,
      hy: spot.y,
      w: targetW,
      h: targetW * ratio,
      alpha: rand(0.9, 1) * params.alpha,
      vx: 0,
      vy: 0,
      rx: 0,
      ry: 0,
    })
  }

  cloudList.value = list
  cloudEls.length = 0
}

/** 给一朵云换新形象，并从左侧重新进场 */
function respawnCloud(c) {
  const params = CLOUD_PARAMS[currentWeatherBucket] || CLOUD_PARAMS.partly
  const imgIndex = Math.floor(Math.random() * 6)
  const img = cloudImages[imgIndex]
  const ratio = img?.naturalHeight ? img.naturalHeight / img.naturalWidth : 0.38
  const baseW = c.region === 'top' ? rand(320, 440) : rand(270, 360)
  const targetW = baseW * params.size
  const spot = pickCloudSpot(c.region)

  c.src = currentCloudSrc[imgIndex]
  c.imgIndex = imgIndex
  c.w = targetW
  c.h = targetW * ratio
  c.alpha = rand(0.9, 1) * params.alpha
  c.x = -c.w / 2 - rand(40, 320)
  c.y = spot.y
  c.hx = spot.x
  c.hy = spot.y
}

/* ------------------------------------------------------------------ 雨 / 雪 / 雾 / 雷电 */

function weatherParticleCount(bucket) {
  const area = (width * height) / (1440 * 900)
  const factor = INTENSITY_FACTOR[skyStore.intensity] || INTENSITY_FACTOR[2]
  if (bucket === 'rain' || bucket === 'thunder') {
    return Math.round(200 * factor.particles * area)
  }
  if (bucket === 'snow') {
    // 雪分大中小：小的稀疏细雪，大的密而大
    return Math.round(120 * factor.particles * area)
  }
  return 0
}

function rebuildParticles(bucket) {
  particlesBucket = `${bucket}|${skyStore.intensity}`
  const n = weatherParticleCount(bucket)
  particles = []
  for (let i = 0; i < n; i++) {
    particles.push(makeParticle(bucket, true))
  }
  flash = 0
  flashTimer = bucket === 'thunder' ? rand(2, 6) : 0
}

function makeParticle(bucket, scatterY) {
  const snow = bucket === 'snow'
  const factor = INTENSITY_FACTOR[skyStore.intensity] || INTENSITY_FACTOR[2]
  const len = snow ? 0 : rand(10, 26) * factor.particleSize
  const sz = factor.particleSize
  return {
    x: rand(0, width),
    y: scatterY ? rand(0, height) : -rand(0, 60),
    // 雨落得快；雪是慢慢飘
    vy: snow ? rand(0.35, 0.9) * sz : rand(7, 13) * sz,
    vx: snow ? rand(-0.25, 0.25) : -rand(1.2, 2.4),
    r: snow ? rand(0.8, 2.2) * sz : 0,
    len,
    alpha: snow ? rand(0.45, 0.9) : rand(0.18, 0.42),
    sway: snow ? rand(0, Math.PI * 2) : 0,
  }
}

function stepWeather(dt) {
  const bucket = currentWeatherBucket
  const key = `${bucket}|${skyStore.intensity}`
  if (key !== particlesBucket) rebuildParticles(bucket)

  const k = Math.min(dt, 32) / 16.67
  // 有积水/积雪时，雨滴打在表面上就消失并溅起水花
  const hasAccum = accumKind === 'rain' && accum > 0.02
  const hitY = hasAccum ? accumSurfaceY() : height + 30
  for (const p of particles) {
    p.y += p.vy * k
    p.x += (p.vx + (p.sway ? Math.sin(p.sway + p.y * 0.02) * 0.5 : 0)) * k
    if (p.y > hitY || p.x < -40) {
      // 频率跟强度走：雨越大水花越多（0.02 → 0.06）
      const splashRate = 0.05 * (skyStore.intensity || 1)
      if (hasAccum && p.y > hitY && Math.random() < splashRate) spawnSplash(p.x, hitY)
      const fresh = makeParticle(bucket, false)
      Object.assign(p, fresh)
    }
  }

  // 雷电：隔几秒闪一下
  if (bucket === 'thunder') {
    flashTimer -= dt / 1000
    if (flashTimer <= 0) {
      flash = 1
      flashTimer = rand(4, 11)
    }
    flash = Math.max(0, flash - dt / 420)
  } else {
    flash = 0
  }
}

/** 量一下登录卡片的位置（换成别的布局也照样能用） */
function measureCard() {
  const canvas = canvasRef.value
  const panel = document.querySelector('.login-panel')
  if (!canvas || !panel) return
  const a = canvas.getBoundingClientRect()
  const b = panel.getBoundingClientRect()
  cardBox = {
    left: b.left - a.left,
    top: b.top - a.top,
    right: b.right - a.left,
    bottom: b.bottom - a.top,
    width: b.width,
    radius: 18,
  }
}

/** 涨满所需秒数：小雨 5 分钟、中雨 3 分钟、大雨 1 分半（雪同理） */
function accumSeconds() {
  return { 1: 300, 2: 180, 3: 90 }[skyStore.intensity] || 180
}

/** 堆积类型：雨/雷暴 → 水；雪 → 雪；其它 → 空（慢慢排干/融化） */
function currentAccumKind() {
  const b = currentWeatherBucket
  if (b === 'rain' || b === 'thunder') return 'rain'
  if (b === 'snow') return 'snow'
  return ''
}

/** 水面/雪面的 y 坐标（底部为 oheight） */
function accumSurfaceY() {
  const maxH = Math.max(8, oheight - cardBox.bottom)
  return oheight - maxH * accum
}

function stepAccumulation(dt) {
  const kind = currentAccumKind()
  if (kind !== accumKind) {
    // 切换天气（雨→雪、雨→晴、晴→雨…）一律清零：
    // 之前是「雨↔雪才清零，其它按 3 倍慢速排干」，小雨要 15 分钟才掉完，看着就像没消失
    accum = 0
    splashes = []
    accumKind = kind
  }
  if (!kind) return
  accum = Math.min(1, accum + dt / (accumSeconds() * 1000))
}

function spawnSplash(x, y) {
  splashes.push({
    x,
    y,
    t: 0,
    life: rand(0.8, 1.35),
    r: rand(2.2, 4.4),
    drops: Math.round(rand(1, 2)),
    seed: Math.random() * Math.PI * 2,
  })
  if (splashes.length > 10) splashes.shift()
}

function stepSplashes(dt) {
  const s = dt / 1000
  for (const sp of splashes) sp.t += s
  splashes = splashes.filter((sp) => sp.t < sp.life)
}

function drawAccumulation() {
  accumPercent.value = Math.round(accum * 100)
  if (!octx || accum <= 0.002) return
  const kind = accumKind
  if (!kind) return

  const surfaceY = accumSurfaceY()
  octx.save()

  if (kind === 'rain') {
    // ---- 水：自上而下的渐变 + 轻微波纹 + 与卡片下沿的弯月面
    waterPhase += 0.02
    const g = octx.createLinearGradient(0, surfaceY, 0, oheight)
    g.addColorStop(0, 'rgba(163, 209, 238, 0.45)')
    g.addColorStop(0.45, 'rgba(126, 178, 216, 0.55)')
    g.addColorStop(1, 'rgba(88, 138, 184, 0.68)')
    octx.fillStyle = g
    octx.beginPath()
    octx.moveTo(0, surfaceY)
    for (let x = 0; x <= owidth; x += 8) {
      const wave = Math.sin(x / 70 + waterPhase) * 1.4 + Math.sin(x / 23 - waterPhase * 1.7) * 0.6
      octx.lineTo(x, surfaceY + wave)
    }
    octx.lineTo(owidth, oheight)
    octx.lineTo(0, oheight)
    octx.closePath()
    octx.fill()

    // 水面高光
    octx.strokeStyle = 'rgba(255, 255, 255, 0.4)'
    octx.lineWidth = 1.2
    octx.beginPath()
    for (let x = 0; x <= owidth; x += 8) {
      const wave = Math.sin(x / 70 + waterPhase) * 1.4
      if (x === 0) octx.moveTo(x, surfaceY + wave)
      else octx.lineTo(x, surfaceY + wave)
    }
    octx.stroke()

    // 与卡片下沿接触的弯月面：两侧圆角高光 + 中间轻微下凹
    if (accum > 0.35 && cardBox.width > 0) {
      const left = cardBox.left
      const right = cardBox.right
      const midY = surfaceY + 2.4
      octx.beginPath()
      octx.moveTo(left, surfaceY - 2)
      octx.quadraticCurveTo(left + 14, midY, left + 34, midY)
      octx.lineTo(right - 34, midY)
      octx.quadraticCurveTo(right - 14, midY, right, surfaceY - 2)
      octx.strokeStyle = 'rgba(255, 255, 255, 0.45)'
      octx.lineWidth = 1.6
      octx.stroke()
    }

    // 水花：涟漪 + 水珠
    for (const sp of splashes) {
      const k = sp.t / sp.life
      const rr = sp.r + k * 20
      octx.globalAlpha = Math.pow(Math.max(0, 1 - k), 1.6) * 0.6
      octx.strokeStyle = 'rgba(240, 250, 255, 0.95)'
      octx.lineWidth = 1.3
      octx.beginPath()
      octx.ellipse(sp.x, sp.y, rr, rr * 0.34, 0, 0, Math.PI * 2)
      octx.stroke()

      if (k < 0.5) {
        octx.globalAlpha = Math.pow(Math.max(0, 1 - k / 0.5), 1.4) * 0.75
        octx.fillStyle = 'rgba(244, 252, 255, 0.95)'
        for (let i = 0; i < sp.drops; i++) {
          const ang = sp.seed + (i / sp.drops) * Math.PI * 2
          const dx = Math.cos(ang) * (5 + k * 16)
          const dy = -Math.sin(ang) * (8 + k * 20) + k * k * 26
          octx.beginPath()
          octx.arc(sp.x + dx, sp.y + dy, Math.max(0.5, 1.05 * (1 - k)), 0, Math.PI * 2)
          octx.fill()
        }
      }
    }
    octx.globalAlpha = 1
  } else {
    // ---- 雪：底部积雪（上表面平整、两端圆肩）+ 卡片上方积雪
    const snowTop = (x) => {
      // 中间略高、两端压低，整体接近水平 —— 雪堆比水面平整
      const t = x / Math.max(1, owidth)
      const dome = Math.sin(Math.PI * t) * 4 * accum
      const bump = Math.sin(x / 58) * 1.2 + Math.sin(x / 130 + 1.4) * 1.6
      return surfaceY - dome - bump
    }

    const g = octx.createLinearGradient(0, surfaceY, 0, oheight)
    g.addColorStop(0, 'rgba(255, 255, 255, 0.97)')
    g.addColorStop(0.5, 'rgba(243, 249, 255, 0.98)')
    g.addColorStop(1, 'rgba(216, 232, 246, 0.98)')
    octx.fillStyle = g
    octx.beginPath()
    octx.moveTo(0, snowTop(0))
    for (let x = 0; x <= owidth; x += 6) octx.lineTo(x, snowTop(x))
    octx.lineTo(owidth, oheight)
    octx.lineTo(0, oheight)
    octx.closePath()
    octx.fill()

    // 雪堆上部阴影（体积感）
    octx.strokeStyle = 'rgba(176, 205, 229, 0.5)'
    octx.lineWidth = 1.4
    octx.beginPath()
    for (let x = 0; x <= owidth; x += 6) {
      if (x === 0) octx.moveTo(x, snowTop(x) + 3)
      else octx.lineTo(x, snowTop(x) + 3)
    }
    octx.stroke()

    // ---- 卡片上方积雪：沿卡片顶边（含圆角）生长，中间略厚
    if (cardBox.width > 0) {
      const thickness = 3 + 19 * accum
      if (thickness > 1) {
        const r = Math.min(cardBox.radius, cardBox.width / 2)
        // 卡片顶边在某 x 处的 y（圆角处会往下弯）
        const topEdgeAt = (x) => {
          const dl = x - cardBox.left
          const dr = cardBox.right - x
          if (dl >= r && dr >= r) return cardBox.top
          const d = dl < r ? r - dl : r - dr
          return cardBox.top + (r - Math.sqrt(Math.max(0, r * r - d * d)))
        }

        const step = 3
        const gs = octx.createLinearGradient(0, cardBox.top - thickness, 0, cardBox.top + 2)
        gs.addColorStop(0, 'rgba(255, 255, 255, 1)')
        gs.addColorStop(0.72, 'rgba(245, 250, 255, 1)')
        gs.addColorStop(1, 'rgba(222, 236, 249, 1)')
        octx.fillStyle = gs

        octx.beginPath()
        // 上沿：从中间往两侧略厚
        let first = true
        for (let x = cardBox.left; x <= cardBox.right; x += step) {
          const t = (x - cardBox.left) / Math.max(1, cardBox.width)
          const dome = Math.sin(Math.PI * t) * 2.6
          const y = topEdgeAt(x) - thickness - dome
          if (first) { octx.moveTo(x, y); first = false } else octx.lineTo(x, y)
        }
        // 下沿：回到卡片顶边（略微压进卡片 1px，避免缝隙）
        for (let x = cardBox.right; x >= cardBox.left; x -= step) {
          octx.lineTo(x, topEdgeAt(x) + 1)
        }
        octx.closePath()
        octx.fill()

        // 雪檐下沿淡阴影
        octx.strokeStyle = 'rgba(146, 180, 212, 0.32)'
        octx.lineWidth = 1
        octx.beginPath()
        let started = false
        for (let x = cardBox.left + 4; x <= cardBox.right - 4; x += step) {
          const y = topEdgeAt(x) + 0.6
          if (!started) { octx.moveTo(x, y); started = true } else octx.lineTo(x, y)
        }
        octx.stroke()

        // 雪面高光
        octx.strokeStyle = 'rgba(255, 255, 255, 0.85)'
        octx.lineWidth = 1.1
        octx.beginPath()
        started = false
        for (let x = cardBox.left + 6; x <= cardBox.right - 6; x += step) {
          const t = (x - cardBox.left) / Math.max(1, cardBox.width)
          const y = topEdgeAt(x) - thickness - Math.sin(Math.PI * t) * 2.6 + 1.6
          if (!started) { octx.moveTo(x, y); started = true } else octx.lineTo(x, y)
        }
        octx.stroke()
      }
    }
  }

  octx.restore()
}

function drawWeather() {
  const bucket = currentWeatherBucket

  // 雾：底部一层白雾，越往下越厚
  if (bucket === 'fog') {
    const g = ctx.createLinearGradient(0, height * 0.45, 0, height)
    g.addColorStop(0, 'rgba(238, 244, 249, 0)')
    g.addColorStop(0.55, 'rgba(238, 244, 249, 0.42)')
    g.addColorStop(1, 'rgba(240, 246, 251, 0.72)')
    ctx.fillStyle = g
    ctx.fillRect(0, 0, width, height)
  }

  // 雨丝 / 雪点
  if (particles.length) {
    ctx.save()
    ctx.lineCap = 'round'
    for (const p of particles) {
      if (p.len) {
        ctx.strokeStyle = `rgba(214, 232, 245, ${p.alpha})`
        ctx.lineWidth = 1.1
        ctx.beginPath()
        ctx.moveTo(p.x, p.y)
        ctx.lineTo(p.x + p.vx * 0.6, p.y + p.len)
        ctx.stroke()
      } else {
        ctx.fillStyle = `rgba(248, 252, 255, ${p.alpha})`
        ctx.beginPath()
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
        ctx.fill()
      }
    }
    ctx.restore()
  }

  // 雷电闪光：整屏泛白一下
  if (flash > 0.01) {
    ctx.fillStyle = `rgba(236, 244, 255, ${flash * 0.5})`
    ctx.fillRect(0, 0, width, height)
  }
}

function stepClouds(dt) {
  const k = Math.min(dt, 32) / 16.67
  const list = cloudList.value

  // 长云带：整体偏移循环（两张图交替拼接 → 视觉无缝）
  const speedMul = (CLOUD_PARAMS[currentWeatherBucket] || CLOUD_PARAMS.partly).speed
  const wind = WIND * speedMul
  const bands = list.filter((c) => c.isBand)
  if (bands.length === 2) {
    const bw = bands[0].w
    bandOffset = (bandOffset + wind * k) % bw
    bands[0].x = width / 2 + bandOffset
    bands[1].x = bands[0].x - bw
  }

  // 单体云：向右缓慢飘，完全移出右侧后从左侧换形状重新进场
  for (const c of list) {
    if (c.isBand) continue
    c.x += wind * k
    if (c.x - c.w / 2 > width + 20) respawnCloud(c)
  }

  // 每帧直接写 DOM
  for (let i = 0; i < cloudEls.length; i++) {
    const el = cloudEls[i]
    const c = list[i]
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
  // 天气压一层：阴天/雨天更灰、雾天更白，晴天保持通透
  palette = adjustSky(palette, skyStore.bucket, skyStore.cloudCover, skyPhase(altitude))
  sunCss = sunColor(altitude).css
  // 天气越差，太阳/月亮越看不见
  const dim = sunDim(skyStore.bucket)
  sunAlpha = sunVisibility(altitude) * dim
  // 云多的时候星星基本看不到
  starsVisible = starAlpha(altitude) * starDim(skyStore.bucket)
  // 夜间云不会消失，只是压暗（晴天仍然是 0 朵）
  cloudsVisible.value = cloudVisibility(skyStore.bucket, altitude)

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
  moonAlpha = Math.max(0, Math.min(1, (moonAlt + 1.5) / 4)) * dayFactor * dim
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

  // 阴雨雪雷暴天：太阳基本看不见，只是一团被云透出的微光
  if (sunAlpha < 0.14) {
    const glow = ctx.createRadialGradient(sunX, sunY, sunR * 0.5, sunX, sunY, sunR * 2.8)
    glow.addColorStop(0, `rgba(255, 240, 214, ${0.2 * sunAlpha})`)
    glow.addColorStop(1, 'rgba(255, 240, 214, 0)')
    ctx.fillStyle = glow
    ctx.beginPath()
    ctx.arc(sunX, sunY, sunR * 2.8, 0, Math.PI * 2)
    ctx.fill()
    return
  }

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

  // 阴雨雪雷暴天：月亮被云挡得只剩一点微光，画成清晰圆盘就不真实了
  if (moonAlpha < 0.12) {
    const faint = ctx.createRadialGradient(moonX, moonY, moonR * 0.6, moonX, moonY, moonR * 2.6)
    faint.addColorStop(0, `rgba(214, 226, 244, ${0.16 * moonAlpha})`)
    faint.addColorStop(1, 'rgba(214, 226, 244, 0)')
    ctx.fillStyle = faint
    ctx.beginPath()
    ctx.arc(moonX, moonY, moonR * 2.6, 0, Math.PI * 2)
    ctx.fill()
    return
  }

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
  drawWeather()
  drawAccumulation()
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

  // 叠加层 canvas（积水/积雪）
  const ov = overlayRef.value
  if (ov) {
    owidth = width
    oheight = height
    ov.width = Math.round(width * dpr)
    ov.height = Math.round(height * dpr)
    octx = ov.getContext('2d')
    octx.setTransform(dpr, 0, 0, dpr, 0, 0)
  }
  measureCard()

  if (resized || !stars.length) buildStars()
  if (resized || !cloudList.value.length) {
    currentPhase = skyPhase(sunAlt)
    buildClouds()
  }
  refreshSky()
  if (!nextEventAt) scheduleNextEvent(performance.now())
  draw()
}

function loop(ts) {
  if (!running) return
  const dt = last ? ts - last : 16.67
  last = ts
  refreshSky()

  // 天气档位或时段变化 → 换云色并重建（含晴天的 0 朵）；放在主循环里，
  // 因为拖时间滑块、切天气都不会触发 resize()
  const wantBucket = skyStore.bucket
  const wantPhase = skyPhase(sunAlt)
  if (wantBucket !== currentWeatherBucket || wantPhase !== currentPhase) {
    currentPhase = wantPhase
    refreshCloudPalette()
    buildClouds()
  }

  stepStars(dt)
  stepClouds(dt)
  stepEvents(dt)
  stepWeather(dt)
  stepAccumulation(dt)
  stepSplashes(dt)
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
  } else {
    // 回到前台：天气数据太久就刷新一次（切换标签页很久回来时很有用）
    skyStore.refreshIfStale()
    if (!running && !reduced()) {
      running = true
      last = 0
      raf = requestAnimationFrame(loop)
    }
  }
}

/* ------------------------------------------------------------------ 调试卡片交互 */

const sliderValue = computed({
  get: () => (previewHour.value === null ? Number(beijingHour().toFixed(2)) : previewHour.value),
  set: (v) => { previewHour.value = Number(v) },
})

/** 调试：手动指定天气档位（'auto' 跟随真实天气） */
function onWeatherChange(e) {
  skyStore.override = e.target.value
  refreshCloudPalette()
  buildClouds()
}

/** 当前天气是否有降水（雨/雪/雷暴）——只有这时「大中小」才有意义 */
const intensityApplies = computed(() => bucketHasIntensity(skyStore.bucket))

/** 调试用：当前堆积百分比 */
const accumPercent = ref(0)

/** 调试：手动指定降水强度（'auto' 用天气码/降水量推断） */
function onIntensityChange(e) {
  skyStore.intensityOverride = e.target.value
  buildClouds()
}

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
  // 预加载当前配色下的云图（data URI），加载完成后回填真实宽高比
  preloadCloudImages()

  // 登录页一进来就取一次位置与天气；失败也只是回落多云，不影响渲染
  // 每 10 分钟刷新一次天气（后端缓存也是 10 分钟）
  skyStore.startAutoRefresh()
  skyStore.load().then(() => {
    refreshCloudPalette()
    preloadCloudImages()
    buildClouds()
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
    get weather() {
      return {
        bucket: skyStore.bucket,
        forced: skyStore.override,
        code: skyStore.weatherCode,
        cloudCover: skyStore.cloudCover,
        city: skyStore.city,
        district: skyStore.district,
        temperature: skyStore.temperature,
        source: skyStore.source,
        cloudCount: cloudList.value.length,
        intensity: skyStore.intensity,
        intensityForced: skyStore.intensityOverride,
        intensityApplies: bucketHasIntensity(skyStore.bucket),
      }
    },
    get accum() { return { kind: accumKind, value: Number(accum.toFixed(3)), percent: Math.round(accum * 100) } },
    get splashCount() { return splashes.length },
    setAccum(v) {
      accum = Math.max(0, Math.min(1, Number(v) || 0))
      accumKind = currentAccumKind()
      draw()
    },
    setWeather(bucket) {
      skyStore.override = bucket || 'auto'
      refreshCloudPalette()
      buildClouds()
    },
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
  skyStore.stopAutoRefresh()
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

  <!-- 积水/积雪层：要盖在登录卡片之上（z-index 高于卡片），只做展示不接收事件 -->
  <canvas ref="overlayRef" class="sky-accum" aria-hidden="true"></canvas>

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

    <div v-show="!collapsed" class="dc-row">
      <span class="dc-phase">堆积 {{ accumPercent }}%</span>
      <span class="dc-sun">{{ accumKind === 'rain' ? '积水' : accumKind === 'snow' ? '积雪' : '无降水' }}</span>
    </div>

    <div v-show="!collapsed" class="dc-row dc-weather">
      <span class="dc-weather-label">天气</span>
      <select class="dc-select" :value="skyStore.override" @change="onWeatherChange">
        <option value="auto">自动（{{ skyStore.bucketLabel }}）</option>
        <option v-for="b in BUCKETS" :key="b" :value="b">{{ BUCKET_LABEL[b] }}</option>
      </select>
    </div>

    <div v-show="!collapsed" class="dc-row dc-weather">
      <span class="dc-weather-label">强度</span>
      <select
        class="dc-select"
        :value="String(skyStore.intensityOverride)"
        :disabled="!intensityApplies"
        :title="intensityApplies ? '降水强度' : '当前天气没有降水，强度不适用'"
        @change="onIntensityChange"
      >
        <option v-if="intensityApplies" value="auto">自动（{{ INTENSITY_LABEL[skyStore.intensity] }}）</option>
        <option v-else value="auto">不适用（{{ skyStore.bucketLabel }}）</option>
        <option v-if="intensityApplies" value="1">小</option>
        <option v-if="intensityApplies" value="2">中</option>
        <option v-if="intensityApplies" value="3">大</option>
      </select>
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

.sky-accum {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
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
  width: 224px;
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

.dc-weather {
  align-items: center;
  gap: 8px;
}

.dc-weather-label {
  font-size: 11px;
  opacity: 0.75;
  flex: none;
}

.dc-select {
  flex: 1;
  appearance: none;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.28);
  color: #e8f1fb;
  font-size: 11.5px;
  padding: 3px 6px;
  border-radius: 6px;
  outline: none;
  cursor: pointer;
}

.dc-select:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.dc-select option {
  color: #0f172a;
  background: #ffffff;
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
