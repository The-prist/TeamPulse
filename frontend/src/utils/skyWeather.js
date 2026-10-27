/**
 * 登录页「天空 × 天气」配色与参数表。
 *
 * 设计原则：**形状由天气决定，颜色由时间决定**，两组正交相乘，
 * 所以不需要为「12 种天气 × 3 个时段」准备 36 套素材。
 *
 *   - 天气（WMO code + 云量）→ 云的数量、大小、透明度、飘动速度、雨雪雾特效
 *   - 时段（太阳高度角）    → 云的两层颜色（淡蓝底 / 白主体）
 */

/** 天气档位（从晴到雷暴，够用且能覆盖 WMO 全部代码） */
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

/** WMO 天气代码 → 档位（代码 3 之后再按云量细分） */
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

/**
 * 由天气代码和云量推出档位。
 * 天气代码给「有没有降水」，云量给「云多不多」——两者都要看：
 * 代码 1（晴间多云）但云量 80% 时，画面上应该更像阴天。
 */
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

/** 每个档位的云参数：(数量, 尺寸倍率, 透明度倍率, 飘动速度倍率) */
export const CLOUD_PARAMS = {
  clear: { count: 0, size: 1, alpha: 1, speed: 1 },
  'mostly-clear': { count: 1, size: 0.85, alpha: 0.92, speed: 1 },
  partly: { count: 5, size: 1, alpha: 1, speed: 1 },
  overcast: { count: 7, size: 1.16, alpha: 1, speed: 1.35 },
  rain: { count: 7, size: 1.18, alpha: 1, speed: 1.7 },
  snow: { count: 6, size: 1.1, alpha: 1, speed: 0.95 },
  fog: { count: 3, size: 1.6, alpha: 0.5, speed: 0.55 },
  thunder: { count: 7, size: 1.22, alpha: 1, speed: 1.9 },
}

/** 时段判定：白天 / 晨昏 / 夜间 */
export function skyPhase(sunAltitude) {
  if (sunAltitude > 6) return 'day'
  if (sunAltitude > -6) return 'dusk'
  return 'night'
}

/** 云的两层颜色：先按降水类天气取，再按时段取 */
const PALETTE = {
  // 普通云（晴到多云）：白天就是素材原色
  default: {
    day: { base: '#d0ecfe', body: '#fcfeff' },
    dusk: { base: '#f09361', body: '#ffe4cd' },
    night: { base: '#2c3d58', body: '#4b5f80' },
  },
  overcast: {
    day: { base: '#b7cede', body: '#e6eff7' },
    dusk: { base: '#c98d6a', body: '#f2d9c6' },
    night: { base: '#26364d', body: '#3f5170' },
  },
  rain: {
    day: { base: '#59646f', body: '#8e99a5' },
    dusk: { base: '#6a5a5f', body: '#9a8d92' },
    night: { base: '#1f2833', body: '#39465a' },
  },
  snow: {
    day: { base: '#c6d6e4', body: '#f2f8ff' },
    dusk: { base: '#cfa79c', body: '#f6e8e2' },
    night: { base: '#2a3a50', body: '#455a78' },
  },
  fog: {
    day: { base: '#c9d5df', body: '#eef4f8' },
    dusk: { base: '#c9a998', body: '#efe2d8' },
    night: { base: '#232f42', body: '#39465c' },
  },
  thunder: {
    day: { base: '#4c5866', body: '#75828f' },
    dusk: { base: '#5c4a55', body: '#8a7684' },
    night: { base: '#1a2330', body: '#334052' },
  },
}

export function cloudPalette(bucket, phase) {
  const group = PALETTE[bucket] || PALETTE.default
  return group[phase] || group.day
}

/**
 * 按天气给天空底色「压一层」：
 * 阴天/雨天要明显灰一些，晴天则更通透、更饱和。
 */
export function adjustSky(palette, bucket, cloudCover) {
  if (!palette) return palette
  const mix = (hex, target, ratio) => {
    const a = hexToRgb(hex)
    const b = hexToRgb(target)
    if (!a || !b) return hex
    const r = Math.round(a[0] + (b[0] - a[0]) * ratio)
    const g = Math.round(a[1] + (b[1] - a[1]) * ratio)
    const bl = Math.round(a[2] + (b[2] - a[2]) * ratio)
    return rgbToHex(r, g, bl)
  }
  if (bucket === 'rain' || bucket === 'thunder') {
    return {
      top: mix(palette.top, '#2f3742', 0.5),
      mid: mix(palette.mid, '#49535f', 0.5),
      bottom: mix(palette.bottom, '#69747f', 0.45),
    }
  }
  if (bucket === 'snow') {
    return {
      top: mix(palette.top, '#b9c8d8', 0.35),
      mid: mix(palette.mid, '#cbd8e4', 0.35),
      bottom: mix(palette.bottom, '#e2ebf3', 0.4),
    }
  }
  if (bucket === 'fog') {
    return {
      top: mix(palette.top, '#c3ccd6', 0.45),
      mid: mix(palette.mid, '#d3dbe3', 0.5),
      bottom: mix(palette.bottom, '#e6ebf0', 0.55),
    }
  }
  if (bucket === 'overcast') {
    const k = 0.22 + (Number.isFinite(cloudCover) ? cloudCover / 100 : 0.7) * 0.2
    return {
      top: mix(palette.top, '#7f8f9e', k),
      mid: mix(palette.mid, '#94a3b0', k),
      bottom: mix(palette.bottom, '#a7b5c0', k),
    }
  }
  return palette
}

/** 把素材里的两个 fill 换成指定颜色，返回可直接给 <img src> 的 data URI */
export function recolorSvg(raw, baseColor, bodyColor) {
  let index = 0
  const replaced = raw.replace(/fill="#[0-9a-fA-F]{6}"/g, () => {
    const color = index++ === 0 ? baseColor : bodyColor
    return `fill="${color}"`
  })
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(replaced)}`
}

function hexToRgb(color) {
  if (typeof color !== 'string') return null
  // skyPalette 返回的是 rgb(...)，重上色表里是 #rrggbb，两种都要能解析
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

function rgbToHex(r, g, b) {
  const h = (x) => Math.max(0, Math.min(255, Math.round(x))).toString(16).padStart(2, '0')
  return `#${h(r)}${h(g)}${h(b)}`
}
