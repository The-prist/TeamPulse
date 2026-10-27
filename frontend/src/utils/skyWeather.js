/**
 * 登录页「天空 × 天气」参数表（第二版：按真实天气观感校准）
 *
 * 现实依据：
 *   - 夜间的云依然存在，只是暗（比夜空略亮）→ 不能因为「天黑了」就把云淡出
 *   - 火烧云（橙红云）只出现在**少云/多云的傍晚**；阴雨雪天的傍晚是灰紫色，不透光
 *   - 阴、雨、雪、雷暴天：天空显著变暗，太阳/月亮基本看不见
 *   - 雾天：太阳/月亮是「隐约可见」的一团光，云也只剩淡影
 *   - 雨雪分大中小：影响粒子数量、大小、下落速度
 */

export const BUCKETS = ['clear', 'mostly-clear', 'partly', 'overcast', 'rain', 'snow', 'fog', 'thunder']

export const BUCKET_LABEL = {
  clear: '晴',
  'mostly-clear': '少云',
  partly: '多云',
  overcast: '阴',
  rain: '雨',
  snow: '雪',
  fog: '雾',
  thunder: '雷暴',
}

export const BUCKET_ICON = {
  clear: '☀',
  'mostly-clear': '🌤',
  partly: '⛅',
  overcast: '☁',
  rain: '🌧',
  snow: '🌨',
  fog: '🌫',
  thunder: '⛈',
}

/** 降水强度标签 */
export const INTENSITY_LABEL = { 1: '小', 2: '中', 3: '大' }

const CODE_BUCKET = {
  0: 'clear',
  1: 'mostly-clear',
  2: 'mostly-clear',
  3: 'overcast',
  45: 'fog',
  48: 'fog',
  51: 'rain',
  53: 'rain',
  55: 'rain',
  56: 'rain',
  57: 'rain',
  61: 'rain',
  63: 'rain',
  65: 'rain',
  66: 'rain',
  67: 'rain',
  71: 'snow',
  73: 'snow',
  75: 'snow',
  77: 'snow',
  80: 'rain',
  81: 'rain',
  82: 'rain',
  85: 'snow',
  86: 'snow',
  95: 'thunder',
  96: 'thunder',
  99: 'thunder',
}

/** WMO 代码里的强度：51/61/71/80 小，53/63/73/81 中，55/65/75/82 大 */
const CODE_INTENSITY = {
  51: 1, 53: 2, 55: 3,
  56: 2, 57: 3,
  61: 1, 63: 2, 65: 3,
  66: 2, 67: 3,
  71: 1, 73: 2, 75: 3,
  77: 2,
  80: 1, 81: 2, 82: 3,
  85: 2, 86: 3,
  95: 2, 96: 2, 99: 3,
  45: 2, 48: 3,
}

export function weatherBucket(code, cloudCover) {
  const byCode = CODE_BUCKET[code]
  const cover = Number.isFinite(cloudCover) ? cloudCover : 50
  if (byCode && byCode !== 'clear' && byCode !== 'mostly-clear') {
    return byCode
  }
  if (cover < 25) return 'clear'
  if (cover < 45) return 'mostly-clear'
  if (cover < 78) return 'partly'
  return 'overcast'
}

/** 降水强度：优先用天气码，缺失时用降水量兜底 */
export function weatherIntensity(code, precipitation) {
  if (CODE_INTENSITY[code]) return CODE_INTENSITY[code]
  const mm = Number.isFinite(precipitation) ? precipitation : 0
  if (mm <= 0) return 1
  if (mm < 2.5) return 1
  if (mm < 7.6) return 2
  return 3
}

/** 云的参数：数量 / 尺寸 / 透明度 / 飘动速度 */
export const CLOUD_PARAMS = {
  clear: { count: 0, size: 1, alpha: 1, speed: 1 },
  'mostly-clear': { count: 1, size: 0.85, alpha: 0.92, speed: 1 },
  partly: { count: 5, size: 1, alpha: 1, speed: 1 },
  overcast: { count: 8, size: 1.2, alpha: 1, speed: 1.4 },
  rain: { count: 8, size: 1.22, alpha: 1, speed: 1.7 },
  snow: { count: 7, size: 1.14, alpha: 1, speed: 0.95 },
  fog: { count: 4, size: 1.7, alpha: 0.45, speed: 0.5 },
  thunder: { count: 8, size: 1.26, alpha: 1, speed: 1.9 },
}

/** 强度加成：云量、尺寸、粒子数量、粒子大小 */
export const INTENSITY_FACTOR = {
  1: { clouds: 0, size: 1.0, particles: 0.5, particleSize: 0.8 },
  2: { clouds: 0, size: 1.05, particles: 1, particleSize: 1 },
  3: { clouds: 1, size: 1.12, particles: 2, particleSize: 1.35 },
}

/** 时段 */
export function skyPhase(sunAltitude) {
  if (sunAltitude > 6) return 'day'
  if (sunAltitude > -6) return 'dusk'
  return 'night'
}

/**
 * 云的两层颜色。
 * 白天：素材原色；傍晚：**只有少云/多云才是火烧云**；夜间：统一夜间云。
 */
const PALETTE = {
  default: {
    day: { base: '#d0ecfe', body: '#fcfeff' },
    dusk: { base: '#f09361', body: '#ffe4cd' },
    night: { base: '#2c3d58', body: '#4b5f80' },
  },
  overcast: {
    day: { base: '#b7cede', body: '#e6eff7' },
    dusk: { base: '#8c7f86', body: '#bdb2b8' },
    night: { base: '#26364d', body: '#3b4c68' },
  },
  rain: {
    day: { base: '#59646f', body: '#8e99a5' },
    dusk: { base: '#5b5258', body: '#8a8088' },
    night: { base: '#1c2836', body: '#33425a' },
  },
  snow: {
    day: { base: '#c6d6e4', body: '#f2f8ff' },
    dusk: { base: '#a99a9c', body: '#d8cfd3' },
    night: { base: '#28384e', body: '#42566f' },
  },
  fog: {
    day: { base: '#c9d5df', body: '#eef4f8' },
    dusk: { base: '#a89b9a', body: '#d3cbc9' },
    night: { base: '#202b3a', body: '#354356' },
  },
  thunder: {
    day: { base: '#4c5866', body: '#75828f' },
    dusk: { base: '#4a4149', body: '#726a74' },
    night: { base: '#161f2b', body: '#2c3849' },
  },
}

/** 火烧云只在这些档位的傍晚出现 */
const FIRE_CLOUD_BUCKETS = new Set(['mostly-clear', 'partly'])

export function cloudPalette(bucket, phase) {
  const group = PALETTE[bucket] || PALETTE.default
  if (phase === 'dusk' && !FIRE_CLOUD_BUCKETS.has(bucket)) {
    // 阴雨雪的傍晚不是火烧云，用各自的灰紫/灰褐版本
    return group.dusk || group.day
  }
  return group[phase] || group.day
}

/** 云在该天气下是否可见：晴为 0；夜间仍然存在，只是压暗 */
export function cloudVisibility(bucket, sunAltitude) {
  if (bucket === 'clear') return 0
  const base =
    { 'mostly-clear': 0.85, partly: 1, overcast: 1, rain: 1, snow: 1, fog: 0.5, thunder: 1 }[bucket] ?? 1
  if (sunAltitude > 0) return base
  if (sunAltitude > -8) {
    return base * (0.82 + 0.18 * (1 + sunAltitude / 8))
  }
  return base * 0.82
}

/** 太阳 / 月亮可见度：阴雨雪雷暴基本看不见，雾是隐约一团 */
export function sunDim(bucket) {
  return (
    {
      clear: 1,
      'mostly-clear': 0.95,
      partly: 0.8,
      overcast: 0.22,
      rain: 0.12,
      snow: 0.3,
      fog: 0.42,
      thunder: 0.06,
    }[bucket] ?? 1
  )
}

/** 星星可见度：云越多越看不到 */
export function starDim(bucket) {
  return (
    {
      clear: 1,
      'mostly-clear': 0.9,
      partly: 0.45,
      overcast: 0.05,
      rain: 0.02,
      snow: 0.12,
      fog: 0.1,
      thunder: 0.02,
    }[bucket] ?? 1
  )
}

/** 天空整体压暗：阴雨雪雷暴明显更黑，且夜间有单独的更暗目标色 */
export function adjustSky(palette, bucket, cloudCover, phase) {
  if (!palette) return palette
  const night = phase === 'night'

  const targets = {
    rain: night
      ? { top: '#080d14', mid: '#111823', bottom: '#1b2531' }
      : { top: '#2f3742', mid: '#49535f', bottom: '#69747f' },
    thunder: night
      ? { top: '#05080d', mid: '#0c1119', bottom: '#161d27' }
      : { top: '#252c36', mid: '#3b444f', bottom: '#59636e' },
    overcast: night
      ? { top: '#0a1018', mid: '#131b26', bottom: '#1d2836' }
      : { top: '#7f8f9e', mid: '#94a3b0', bottom: '#a7b5c0' },
    snow: night
      ? { top: '#0b121c', mid: '#151e2a', bottom: '#1f2b3a' }
      : { top: '#b9c8d8', mid: '#cbd8e4', bottom: '#e2ebf3' },
    fog: night
      ? { top: '#131a24', mid: '#1b2430', bottom: '#28323d' }
      : { top: '#c3ccd6', mid: '#d3dbe3', bottom: '#e6ebf0' },
  }

  const t = targets[bucket]
  if (!t) return palette

  // 该天气本身至少有这么多云（调试强制切档时真实 cloudCover 可能是 0）
  const assumed = { overcast: 0.85, rain: 0.9, snow: 0.85, fog: 0.8, thunder: 0.95 }[bucket] || 0
  const cover = Math.max(Number.isFinite(cloudCover) ? cloudCover / 100 : 0.6, assumed)
  let k = night ? 0.55 : 0.3 + cover * 0.35
  if (bucket === 'rain' || bucket === 'thunder') k = night ? 0.62 : 0.45 + cover * 0.35
  if (bucket === 'fog') k = night ? 0.5 : 0.35 + cover * 0.3
  const clamp = Math.max(0, Math.min(1, k))

  return {
    top: mix(palette.top, t.top, clamp),
    mid: mix(palette.mid, t.mid, clamp),
    bottom: mix(palette.bottom, t.bottom, clamp),
  }
}

/** 把素材里的两个 fill 换成指定颜色 → data URI */
export function recolorSvg(raw, baseColor, bodyColor) {
  let index = 0
  const replaced = raw.replace(/fill="#[0-9a-fA-F]{6}"/g, () => {
    const color = index++ === 0 ? baseColor : bodyColor
    return `fill="${color}"`
  })
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(replaced)}`
}

/** 颜色混合：skyPalette 给的是 rgb(...)，重上色表是 #rrggbb，两种都要支持 */
function parseColor(color) {
  if (typeof color !== 'string') return null
  const rgbMatch = color.match(/rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)/)
  if (rgbMatch) {
    return [Number(rgbMatch[1]), Number(rgbMatch[2]), Number(rgbMatch[3])]
  }
  if (!color.startsWith('#')) return null
  const v = color.slice(1)
  const full = v.length === 3 ? v.split('').map((c) => c + c).join('') : v
  const n = Number.parseInt(full, 16)
  if (Number.isNaN(n)) return null
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}

function mix(from, to, k) {
  const a = parseColor(from)
  const b = parseColor(to)
  if (!a || !b) return from
  return rgbToHex(
    a[0] + (b[0] - a[0]) * k,
    a[1] + (b[1] - a[1]) * k,
    a[2] + (b[2] - a[2]) * k,
  )
}

function rgbToHex(r, g, b) {
  const h = (x) => Math.max(0, Math.min(255, Math.round(x))).toString(16).padStart(2, '0')
  return `#${h(r)}${h(g)}${h(b)}`
}
