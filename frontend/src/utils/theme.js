/**
 * 主题（亮 / 暗）
 *
 * 状态存在 <html class="dark">：Element Plus 的暗色变量挂在 html.dark 上，
 * 我们自己的 --tp-* 变量也在 theme.css 里按 html.dark 覆盖，因此只需要切一个 class。
 *
 * 首屏不能闪：main.js 里在 mount 之前调用 initTheme()，从 localStorage 读偏好，
 * 没有记录时跟随系统 prefers-color-scheme。
 */

const STORAGE_KEY = 'tp_theme'
/** 用户手动选过主题后置 1：此后不再按时间自动切换 */
const MANUAL_KEY = 'tp_theme_manual'

export const THEMES = { LIGHT: 'light', DARK: 'dark' }

/** 当前主题 */
export function currentTheme() {
  return document.documentElement.classList.contains('dark') ? THEMES.DARK : THEMES.LIGHT
}

/** 应用主题（不改动画，仅切 class） */
export function applyTheme(theme) {
  const dark = theme === THEMES.DARK
  document.documentElement.classList.toggle('dark', dark)
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
  // 让浏览器调整滚动条、表单控件配色
  document.documentElement.style.colorScheme = dark ? 'dark' : 'light'
}

/** 持久化并应用 */
export function setTheme(theme) {
  applyTheme(theme)
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    /* 隐私模式下写不进去，忽略 */
  }
}

/** 本地时间决定主题：6:00–18:00 白天，其余夜间 */
export function timeBasedTheme(date = new Date()) {
  const hour = date.getHours()
  return hour >= 6 && hour < 18 ? THEMES.LIGHT : THEMES.DARK
}

/** 用户是否手动选过主题 */
export function hasManualTheme() {
  try {
    return localStorage.getItem(MANUAL_KEY) === '1'
  } catch {
    return false
  }
}

/** 标记「用户手动选过」，之后不再按时间覆盖 */
export function markThemeManual() {
  try {
    localStorage.setItem(MANUAL_KEY, '1')
  } catch {
    /* ignore */
  }
}

/** 清除手动标记，恢复按时间自动切换 */
export function clearThemeManual() {
  try {
    localStorage.removeItem(MANUAL_KEY)
  } catch {
    /* ignore */
  }
}

/**
 * 首屏初始化：手动选择优先，其次按本地时间（白天/夜间），
 * 这样登录进去看到的就是符合当前时间的主题。
 */
export function initTheme() {
  let saved = ''
  try {
    saved = localStorage.getItem(STORAGE_KEY) || ''
  } catch {
    /* ignore */
  }
  if (hasManualTheme() && (saved === THEMES.DARK || saved === THEMES.LIGHT)) {
    applyTheme(saved)
    return saved
  }
  const theme = timeBasedTheme()
  applyTheme(theme)
  return theme
}

/** 登录成功后按当前时间重设主题（用户手动选过则保持不动） */
export function applyTimeBasedTheme() {
  if (hasManualTheme()) {
    return currentTheme()
  }
  const theme = timeBasedTheme()
  applyTheme(theme)
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    /* ignore */
  }
  return theme
}

/** 目标主题的背景色（遮罩层要用它铺满，切换瞬间才不会闪一下） */
export function bgOf(theme) {
  return theme === THEMES.DARK ? '#0d1117' : '#e1eaf6'
}

/* ------------------------------------------------------------------ 切换接管 */
/*
  某些页面有自己的一套切换动画（登录页：星星颤抖坠落 + 右侧日出）。
  这些页面挂载时注册一个 handler，ThemeToggle 检测到就交给它，
  不再播放全屏覆盖 + 圆形揭开。
  handler 负责在合适的时机调用 setTheme(主题)，并返回 Promise 表示动画结束。
*/
let transitionHandler = null

export function registerThemeTransition(fn) {
  transitionHandler = fn
  return () => {
    if (transitionHandler === fn) transitionHandler = null
  }
}

export function hasThemeTransition() {
  return typeof transitionHandler === 'function'
}

/** 交给页面自己的动画；没有注册则返回 false，由调用方走默认动画 */
export async function runThemeTransition(nextTheme) {
  if (!hasThemeTransition()) return false
  await transitionHandler(nextTheme)
  return true
}
