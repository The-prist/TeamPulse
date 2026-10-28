/**
 * 页面切换的「圆形揭幕」转场。
 *
 * 用法：点某个按钮要跳走时，以这个按钮为圆心扩散一个纯色圆铺满屏幕，
 * 圆遮住整屏后再切路由，然后反向收缩露出新页面。
 *
 * 颜色取**目标主题**的页面底色（bgOf(currentTheme())），
 * 所以暗色下是深色扩散、亮色下是浅色扩散，和落点页面同色，切换瞬间不会闪。
 */

import { bgOf, currentTheme } from './theme'

/**
 * @param {object} options
 * @param {HTMLElement|{ $el: HTMLElement }} options.origin 触发元素（按钮）
 * @param {() => Promise<any>} options.navigate 实际的路由跳转
 * @param {number} [options.expandMs] 扩散时长
 * @param {number} [options.shrinkMs] 收缩时长
 */
export async function circleTransition({ origin, navigate, expandMs = 440, shrinkMs = 360 }) {
  const el = origin?.$el ?? origin
  if (!el || typeof document === 'undefined') {
    await navigate?.()
    return
  }

  const rect = el.getBoundingClientRect()
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 2
  const radius =
    Math.max(
      Math.hypot(cx, cy),
      Math.hypot(window.innerWidth - cx, cy),
      Math.hypot(cx, window.innerHeight - cy),
      Math.hypot(window.innerWidth - cx, window.innerHeight - cy),
    ) + 8

  const overlay = document.createElement('div')
  overlay.className = 'tp-circle-transition'
  overlay.style.background = bgOf(currentTheme())
  overlay.style.clipPath = `circle(0px at ${cx}px ${cy}px)`
  document.body.appendChild(overlay)

  try {
    const expand = overlay.animate(
      [
        { clipPath: `circle(0px at ${cx}px ${cy}px)` },
        { clipPath: `circle(${radius}px at ${cx}px ${cy}px)` },
      ],
      { duration: expandMs, easing: 'cubic-bezier(.22,.61,.36,1)', fill: 'forwards' },
    )
    await expand.finished.catch(() => {})

    await navigate?.()

    // 等新页面挂载并完成一帧渲染，否则收缩时会先露出半截白屏
    await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))

    const shrink = overlay.animate(
      [
        { clipPath: `circle(${radius}px at ${cx}px ${cy}px)` },
        { clipPath: `circle(0px at ${cx}px ${cy}px)` },
      ],
      { duration: shrinkMs, easing: 'cubic-bezier(.4,0,.2,1)', fill: 'forwards' },
    )
    await shrink.finished.catch(() => {})
  } finally {
    overlay.remove()
  }
}

/** 注入一次全局样式（幂等） */
export function ensureCircleTransitionStyle() {
  if (typeof document === 'undefined' || document.getElementById('tp-circle-transition-style')) return
  const style = document.createElement('style')
  style.id = 'tp-circle-transition-style'
  style.textContent = `.tp-circle-transition{position:fixed;inset:0;z-index:5000;pointer-events:none;will-change:clip-path}`
  document.head.appendChild(style)
}

ensureCircleTransitionStyle()
