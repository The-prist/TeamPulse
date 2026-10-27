/**
 * 太阳位置与天色计算（真实天文近似）
 *
 * 用途：登录页天空按北京时间渲染，太阳位置严格遵守现实——西为左、东为右。
 *
 * 算法：采用 SunCalc 同源的简化天文公式（误差 < 1°，够用于视觉呈现）
 *   - 儒略日 → 太阳平近点角 → 黄经 → 赤纬/赤经 → 时角
 *   - 高度角 altitude：太阳在地平线以上的角度（负值表示在地平线下）
 *   - 方位角 azimuth：从正北顺时针（0=N, 90=E, 180=S, 270=W）
 * 坐标取北京（39.9042°N, 116.4074°E），时区固定 UTC+8。
 */

const RAD = Math.PI / 180
const DAY_MS = 86400000
const J1970 = 2440588
const J2000 = 2451545
const OBLIQUITY = RAD * 23.4397

export const BEIJING = { lat: 39.9042, lng: 116.4074, tzOffsetHours: 8 }

const toJulian = (date) => date.valueOf() / DAY_MS - 0.5 + J1970
const toDays = (date) => toJulian(date) - J2000
const solarMeanAnomaly = (d) => RAD * (357.5291 + 0.98560028 * d)

const eclipticLongitude = (M) => {
  const C = RAD * (1.9148 * Math.sin(M) + 0.02 * Math.sin(2 * M) + 0.0003 * Math.sin(3 * M))
  const P = RAD * 102.9372
  return M + C + P + Math.PI
}

const declination = (l) => Math.asin(Math.sin(OBLIQUITY) * Math.sin(l))
const rightAscension = (l) => Math.atan2(Math.sin(l) * Math.cos(OBLIQUITY), Math.cos(l))

/** 当地恒星时 */
function siderealTime(d, lw) {
  return RAD * (280.16 + 360.9856235 * d) - lw
}

/** 太阳高度角（度）与方位角（度，正北顺时针） */
export function sunPosition(date, { lat, lng } = BEIJING) {
  const phi = RAD * lat
  const lw = RAD * -lng
  const d = toDays(date)
  const M = solarMeanAnomaly(d)
  const L = eclipticLongitude(M)
  const dec = declination(L)
  const ra = rightAscension(L)
  const H = siderealTime(d, lw) - ra

  const altitude = Math.asin(
    Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(H),
  )
  // SunCalc 的 azimuth 以正南为 0、向西为正；这里转成「正北顺时针」
  const azSouth = Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(phi) - Math.tan(dec) * Math.cos(phi))
  const azimuth = ((azSouth / RAD + 180) % 360 + 360) % 360

  return { altitude: altitude / RAD, azimuth }
}

/** 屏幕横向位置：东（az≈90°）→ 右侧，西（az≈270°）→ 左侧，正南居中 */
export function azimuthToX(azimuth, westOnLeft = true) {
  const s = Math.sin((180 - azimuth) * RAD) // az=90 → +1（东），az=270 → -1（西）
  const v = westOnLeft ? s : -s
  return 0.5 + 0.5 * v
}

/** 北京时间某个小时对应的绝对时刻（date：北京时间当天） */
export function beijingDateAtHour(hour, now = new Date()) {
  // 把 now 平移到 UTC 字段显示北京时间，取当天日期
  const shifted = new Date(now.getTime() + BEIJING.tzOffsetHours * 3600e3)
  const midnightUtc =
    Date.UTC(shifted.getUTCFullYear(), shifted.getUTCMonth(), shifted.getUTCDate()) -
    BEIJING.tzOffsetHours * 3600e3
  return new Date(midnightUtc + hour * 3600e3)
}

/** 当前北京时间（小时，含小数） */
export function beijingHour(now = new Date()) {
  const shifted = new Date(now.getTime() + BEIJING.tzOffsetHours * 3600e3)
  return shifted.getUTCHours() + shifted.getUTCMinutes() / 60 + shifted.getUTCSeconds() / 3600
}

const clamp01 = (v) => Math.max(0, Math.min(1, v))
const smoothstep = (edge0, edge1, x) => {
  const t = clamp01((x - edge0) / (edge1 - edge0))
  return t * t * (3 - 2 * t)
}

const hexToRgb = (hex) => {
  const h = hex.replace('#', '')
  return {
    r: parseInt(h.slice(0, 2), 16),
    g: parseInt(h.slice(2, 4), 16),
    b: parseInt(h.slice(4, 6), 16),
  }
}
const rgbToCss = ({ r, g, b }) => `rgb(${Math.round(r)}, ${Math.round(g)}, ${Math.round(b)})`
const mix = (a, b, t) => ({
  r: a.r + (b.r - a.r) * t,
  g: a.g + (b.g - a.g) * t,
  b: a.b + (b.b - a.b) * t,
})

/** 关键帧：按太阳高度角插值，晨（日出前）与昏（日落后）用两套色 */
const SKY_NIGHT = { top: '#03070f', mid: '#060e1c', bottom: '#0a1424' }

const SKY_KEYS_DAWN = [
  { h: -18, top: '#03070f', mid: '#060e1c', bottom: '#0a1424' },
  { h: -12, top: '#061428', mid: '#0d2138', bottom: '#1b3350' },
  // 临日出：上方深蓝、下方发白（用户明确要求的观感）
  { h: -7, top: '#0a2038', mid: '#38618c', bottom: '#d6e3ef' },
  { h: -3, top: '#14385f', mid: '#6d97bb', bottom: '#f2f8fd' },
  { h: 1, top: '#1e5285', mid: '#7fb2d6', bottom: '#f7fbff' },
  { h: 6, top: '#2a72bd', mid: '#8ac0e8', bottom: '#e8f4ff' },
  { h: 15, top: '#1f6fc8', mid: '#77b9e9', bottom: '#d9edfc' },
  { h: 35, top: '#1465c2', mid: '#69ade2', bottom: '#cfe6f8' },
  { h: 60, top: '#0f58b3', mid: '#5ba4dd', bottom: '#c9e2f6' },
  { h: 90, top: '#0d54ad', mid: '#57a0db', bottom: '#c6e0f5' },
]

const SKY_KEYS_DUSK = [
  { h: -18, top: '#03070f', mid: '#060e1c', bottom: '#0a1424' },
  { h: -12, top: '#071023', mid: '#1d2138', bottom: '#4a3a4e' },
  // 傍晚：天空偏暖，接近地平线是夕阳橙
  { h: -7, top: '#0c1c36', mid: '#4a3a5c', bottom: '#c47a5c' },
  { h: -3, top: '#152c4a', mid: '#6d4a5e', bottom: '#e08a5c' },
  { h: 1, top: '#23456c', mid: '#a05f55', bottom: '#ffab63' },
  { h: 6, top: '#2e5f92', mid: '#c4835e', bottom: '#ffd39a' },
  { h: 15, top: '#2370c2', mid: '#8fbde3', bottom: '#ffe2bc' },
  { h: 35, top: '#1465c2', mid: '#69ade2', bottom: '#cfe6f8' },
  { h: 60, top: '#0f58b3', mid: '#5ba4dd', bottom: '#c9e2f6' },
  { h: 90, top: '#0d54ad', mid: '#57a0db', bottom: '#c6e0f5' },
]

function pickKeyframe(keys, h) {
  if (h <= keys[0].h) return { a: keys[0], b: keys[0], t: 0 }
  for (let i = 1; i < keys.length; i++) {
    if (h <= keys[i].h) {
      const a = keys[i - 1]
      const b = keys[i]
      return { a, b, t: (h - a.h) / (b.h - a.h) }
    }
  }
  const last = keys[keys.length - 1]
  return { a: last, b: last, t: 0 }
}

/**
 * 天色：返回三段渐变颜色与被盖住的底色
 * rising=true 表示当日太阳还在升起阶段（上午），false 表示已过正午（下午/傍晚）
 */
export function skyPalette(altitude, rising = true) {
  const keys = rising ? SKY_KEYS_DAWN : SKY_KEYS_DUSK
  if (altitude <= -18) {
    return {
      top: SKY_NIGHT.top,
      mid: SKY_NIGHT.mid,
      bottom: SKY_NIGHT.bottom,
      css: `linear-gradient(to bottom, ${SKY_NIGHT.top}, ${SKY_NIGHT.mid} 55%, ${SKY_NIGHT.bottom})`,
    }
  }
  const { a, b, t } = pickKeyframe(keys, altitude)
  const top = rgbToCss(mix(hexToRgb(a.top), hexToRgb(b.top), t))
  const mid = rgbToCss(mix(hexToRgb(a.mid), hexToRgb(b.mid), t))
  const bottom = rgbToCss(mix(hexToRgb(a.bottom), hexToRgb(b.bottom), t))
  return {
    top,
    mid,
    bottom,
    css: `linear-gradient(to bottom, ${top} 0%, ${mid} 58%, ${bottom} 100%)`,
  }
}

/** 太阳本色：高悬时偏白，接近地平线转橙红 */
export function sunColor(altitude) {
  const keys = [
    { h: -1, c: '#e9632c' },
    { h: 1, c: '#ff9a4d' },
    { h: 4, c: '#ffc266' },
    { h: 9, c: '#ffe0a0' },
    { h: 18, c: '#fff2cf' },
    { h: 35, c: '#fffcf0' },
    { h: 90, c: '#ffffff' },
  ]
  const { a, b, t } = pickKeyframe(keys, altitude)
  const c = mix(hexToRgb(a.c), hexToRgb(b.c), t)
  return { ...c, css: rgbToCss(c) }
}

/** 星星可见度：日出后完全不可见，日落后渐显（渐变出现） */
export function starAlpha(altitude) {
  // altitude > 2° 全无；altitude < -12° 全亮；中间平滑过渡
  return smoothstep(2, 12, -altitude)
}

/** 云朵可见度：白天最亮，入夜后逐渐隐去 */
export function cloudAlpha(altitude) {
  return smoothstep(-4, 5, altitude) * 0.92
}

/** 太阳圆面是否可见（落山后完全隐藏） */
export function sunVisibility(altitude) {
  return smoothstep(-1.2, 1.6, altitude)
}

/* ================================================================== 月亮 */

/** 黄纬 b 的赤纬（太阳那条只算了 b=0） */
const declinationLB = (l, b) =>
  Math.asin(Math.sin(b) * Math.cos(OBLIQUITY) + Math.cos(b) * Math.sin(OBLIQUITY) * Math.sin(l))
const rightAscensionLB = (l, b) =>
  Math.atan2(
    Math.sin(l) * Math.cos(OBLIQUITY) - Math.tan(b) * Math.sin(OBLIQUITY),
    Math.cos(l),
  )

/** 月球地心坐标（SunCalc 同源的简化式，误差 ~1°） */
function moonCoords(d) {
  const L = RAD * (218.316 + 13.176396 * d) // 平黄经
  const M = RAD * (134.963 + 13.064993 * d) // 平近点角
  const F = RAD * (93.272 + 13.22935 * d) // 升交点角距

  const l = L + RAD * 6.289 * Math.sin(M) // 黄经（主要摄动项）
  const b = RAD * 5.128 * Math.sin(F) // 黄纬
  const dist = 385001 - 20905 * Math.cos(M) // 地月距离 km

  return { ra: rightAscensionLB(l, b), dec: declinationLB(l, b), dist }
}

function sunCoords(d) {
  const M = solarMeanAnomaly(d)
  const L = eclipticLongitude(M)
  return { dec: declination(L), ra: rightAscension(L) }
}

/**
 * 月亮位置：高度角（含大气折射修正）与方位角（正北顺时针）
 */
export function moonPosition(date, { lat, lng } = BEIJING) {
  const phi = RAD * lat
  const lw = RAD * -lng
  const d = toDays(date)
  const c = moonCoords(d)
  const H = siderealTime(d, lw) - c.ra

  let h = Math.asin(Math.sin(phi) * Math.sin(c.dec) + Math.cos(phi) * Math.cos(c.dec) * Math.cos(H))
  // 大气折射：贴近地平线时月亮看起来更高
  h += RAD * 0.017 / Math.tan(h + RAD * 10.26 / (h + RAD * 5.1))

  const pa = Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(phi) - Math.tan(c.dec) * Math.cos(phi))
  const azimuth = (((pa / RAD + 180) % 360) + 360) % 360

  return { altitude: h / RAD, azimuth, distance: c.dist }
}

/**
 * 月相：phase 0=新月 0.25=上弦 0.5=满月 0.75=下弦；fraction 为被照亮的比例
 */
export function moonIllumination(date) {
  const d = toDays(date)
  const s = sunCoords(d)
  const m = moonCoords(d)
  const sunDist = 149598000 // 日地距离 km

  const phi = Math.acos(
    Math.sin(s.dec) * Math.sin(m.dec) + Math.cos(s.dec) * Math.cos(m.dec) * Math.cos(s.ra - m.ra),
  )
  const inc = Math.atan2(sunDist * Math.sin(phi), m.dist - sunDist * Math.cos(phi))
  const angle = Math.atan2(
    Math.cos(s.dec) * Math.sin(s.ra - m.ra),
    Math.sin(s.dec) * Math.cos(m.dec) - Math.cos(s.dec) * Math.sin(m.dec) * Math.cos(s.ra - m.ra),
  )

  const fraction = (1 + Math.cos(inc)) / 2
  const phase = 0.5 + (0.5 * inc * (angle < 0 ? -1 : 1)) / Math.PI
  return { fraction, phase: (phase + 1) % 1, angle }
}

/** 月相中文名 */
export function moonPhaseName(phase) {
  const p = ((phase % 1) + 1) % 1
  if (p < 0.03 || p > 0.97) return '新月'
  if (p < 0.22) return '蛾眉月'
  if (p < 0.28) return '上弦月'
  if (p < 0.47) return '盈凸月'
  if (p < 0.53) return '满月'
  if (p < 0.72) return '亏凸月'
  if (p < 0.78) return '下弦月'
  return '残月'
}
