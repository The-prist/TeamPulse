<!--
  主题切换按钮（左下角弯月）

  动效两段：
    ① 覆盖层（目标主题色）用 clip-path 圆从按钮位置扩张到铺满全屏（420ms）
    ② 主题切换完成后，覆盖层用 mask 以「屏幕中心的圆」向外揭开（620ms）

  两个实现要点（都踩过）：
    - 覆盖层必须是「整屏尺寸」，不能用小圆点 + transform: scale()：
      scale 会把 mask/clip-path 的半径一起放大几十倍，圆形边缘瞬间越过屏幕，动画就看不见了。
    - --tp-reveal 必须设在覆盖层元素自身并内联 transition：设在 :root 上不参与过渡，
      而先设过的内联 transition 也会盖掉 class 里的过渡声明。
-->
<script setup>
import { computed, onMounted, ref } from 'vue'
import { bgOf, currentTheme, runThemeTransition, setTheme, THEMES } from '@/utils/theme'

const theme = ref(THEMES.LIGHT)
const busy = ref(false)

onMounted(() => {
  theme.value = currentTheme()
})

const isDark = computed(() => theme.value === THEMES.DARK)

const nextFrame = () => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))
const wait = (ms) => new Promise((r) => setTimeout(r, ms))

/** 从按钮扩张铺满 → 切主题 → 从屏幕中心圆形揭开 */
async function toggle(event) {
  if (busy.value) return
  busy.value = true

  const from = isDark.value ? THEMES.DARK : THEMES.LIGHT
  const next = isDark.value ? THEMES.LIGHT : THEMES.DARK

  // 页面注册了自己的切换动画（登录页：星星颤抖坠落 + 右侧日出）→ 交给它，不播全屏覆盖
  if (await runThemeTransition(next)) {
    theme.value = next
    busy.value = false
    return
  }

  const btn = event.currentTarget
  const rect = btn.getBoundingClientRect()
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 2

  // 按钮中心到四角的最大距离：扩张到它能铺满整屏
  const cornerR = Math.max(
    Math.hypot(cx, cy),
    Math.hypot(window.innerWidth - cx, cy),
    Math.hypot(cx, window.innerHeight - cy),
    Math.hypot(window.innerWidth - cx, window.innerHeight - cy),
  )
  // 屏幕正中心到四角：揭开时圆要长到能覆盖全屏
  const centerR = Math.hypot(window.innerWidth / 2, window.innerHeight / 2)

  const overlay = document.createElement('div')
  overlay.className = 'tp-theme-overlay'
  overlay.style.cssText = [
    'position:fixed',
    'inset:0',
    'width:100vw',
    'height:100vh',
    'pointer-events:none',
    'z-index:4000',
    // 用「目标主题」底色：亮→暗时看到的就是暗色从按钮扩散开（暗色先铺满，再从中心揭开）
    // 之后中心圆揭开时，露出的页面上有内容（卡片/文字），与纯色遮罩的区别足以看清边界
    `background:${bgOf(next)}`,
    `clip-path:circle(0px at ${cx}px ${cy}px)`,
    'will-change:clip-path,opacity',
  ].join(';')
  document.body.appendChild(overlay)

  // ---- ① 从按钮扩张铺满（420ms）
  await nextFrame()
  overlay.style.transition = 'clip-path 420ms cubic-bezier(.22,.61,.36,1)'
  overlay.style.clipPath = `circle(${cornerR}px at ${cx}px ${cy}px)`
  await wait(450)

  // ---- 切换主题（整屏被盖住，用户看不到跳变）
  setTheme(next)
  theme.value = next

  // ---- ② 从屏幕中心圆形揭开（620ms）
  overlay.style.setProperty('--tp-reveal', '0px')
  overlay.style.transition = '--tp-reveal 620ms cubic-bezier(0.4, 0, 0.2, 1)'
  overlay.classList.add('is-revealing')
  await nextFrame()
  overlay.style.setProperty('--tp-reveal', `${centerR + 4}px`)

  // 不支持 @property 的浏览器：退化成淡出，至少不留残影
  const supportsProperty = typeof CSS !== 'undefined' && typeof CSS.registerProperty === 'function'
  if (!supportsProperty) {
    overlay.style.transition = 'opacity 560ms ease'
    overlay.style.opacity = '0'
  }

  await wait(680)
  overlay.remove()
  busy.value = false
}
</script>

<template>
  <button
    class="theme-toggle"
    :class="{ 'is-dark': isDark, 'is-busy': busy }"
    type="button"
    :aria-label="isDark ? '切换到亮色模式' : '切换到暗色模式'"
    :title="isDark ? '切换到亮色模式' : '切换到暗色模式'"
    @click="toggle"
  >
    <!-- 弯月：外圆 + 偏移镂空圆 -->
    <span class="moon" aria-hidden="true">
      <span class="moon-core"></span>
    </span>
  </button>
</template>

<style scoped>
.theme-toggle {
  position: fixed;
  left: 22px;
  bottom: 22px;
  z-index: 3200;
  width: 46px;
  height: 46px;
  padding: 0;
  display: grid;
  place-items: center;
  border-radius: 50%;
  border: 1px solid rgba(47, 111, 208, 0.35);
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(10px);
  box-shadow: 0 6px 18px -6px rgba(16, 42, 67, 0.45);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease, border-color 0.2s ease;
}

.theme-toggle:hover {
  transform: translateY(-2px) scale(1.04);
  box-shadow: 0 10px 24px -8px rgba(16, 42, 67, 0.55);
  border-color: rgba(47, 111, 208, 0.6);
}

.theme-toggle:active {
  transform: translateY(0) scale(0.96);
}

.theme-toggle.is-busy {
  pointer-events: none;
}

/* ---- 弯月 */
.moon {
  position: relative;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #2f6fd0;
  transition: transform 0.45s cubic-bezier(0.22, 0.61, 0.36, 1), background 0.3s ease;
}

.moon-core {
  position: absolute;
  top: -3px;
  left: 6px;
  width: 21px;
  height: 21px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.86);
  transition: background 0.3s ease, top 0.45s cubic-bezier(0.22, 0.61, 0.36, 1),
    left 0.45s cubic-bezier(0.22, 0.61, 0.36, 1);
}

/* 暗色：深底 + 金色弯月，转向另一侧 */
.theme-toggle.is-dark {
  background: rgba(22, 27, 34, 0.9);
  border-color: rgba(240, 196, 96, 0.4);
}

.theme-toggle.is-dark .moon {
  background: #f0c460;
  transform: rotate(-18deg);
}

.theme-toggle.is-dark .moon-core {
  background: rgba(22, 27, 34, 0.92);
  top: -2px;
  left: -4px;
}
</style>
