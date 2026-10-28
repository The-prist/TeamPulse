<template>
  <div class="cap">
    <div class="cap-stage">
      <canvas ref="bgRef" class="cap-canvas"></canvas>
      <canvas
        ref="pieceRef"
        class="cap-canvas cap-piece"
        :style="{ transform: `translateX(${offset}px)`, width: `${pieceSize}px`, height: `${pieceSize}px`, top: `${holeY - pieceR}px` }"
      ></canvas>
    </div>

    <div class="cap-track" @pointerdown="onDown" @pointermove="onMove" @pointerup="onUp" @pointercancel="onUp">
      <div class="cap-fill" :class="{ ok: state === 'ok', bad: state === 'bad' }" :style="{ width: `${offset + 24}px` }"></div>
      <div class="cap-handle" :class="{ ok: state === 'ok', bad: state === 'bad' }" :style="{ transform: `translateX(${offset}px)` }">
        <span v-if="state === 'ok'">✓</span>
        <span v-else-if="state === 'bad'">✕</span>
        <span v-else>→</span>
      </div>
      <span class="cap-hint">{{ hint }}</span>
    </div>

    <div class="cap-foot">
      <span class="cap-tip">按住滑块，把拼图拖到缺口上</span>
      <button type="button" class="cap-refresh" @click="reset">换一张</button>
    </div>
  </div>
</template>

<script setup>
/**
 * 滑块拼图验证码（前端自制，无第三方依赖，也不需要联网取图）。
 *
 * 做法：程序化生成一张带纹理的底图，在上面挖一个圆形缺口；
 * 拼图块的像素取自缺口位置，拖动滑块把块对齐缺口即通过。
 *
 * 注意：**这只是本地校验**，可以挡住一般脚本的暴力提交，
 * 但挡不住会读 canvas 的爬虫。真要防撞库，必须把校验放到服务端
 * （服务端出题 + 校验 token + 失败次数限流）。
 */
import { computed, onMounted, ref } from 'vue'

const emit = defineEmits(['verified', 'failed'])

const W = 320
const H = 160
const pieceR = 20
const pieceSize = pieceR * 2
const tolerance = 6

const bgRef = ref(null)
const pieceRef = ref(null)
const offset = ref(0)
const holeY = ref(H / 2)
const targetOffset = ref(0)
const state = ref('idle') // idle | ok | bad
const dragging = ref(false)

const maxOffset = W - pieceSize

const hint = computed(() => {
  if (state.value === 'ok') return '验证通过'
  if (state.value === 'bad') return '没对齐，已换一张，请重试'
  return dragging.value ? '松手校验' : '向右拖动滑块'
})

/** 简单的线性同余随机，保证同一张图每次重绘一致 */
function makeRandom(seed) {
  let s = seed >>> 0
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0
    return s / 4294967296
  }
}

/** 画底图：渐变 + 色块 + 斜纹 + 噪点，足够“有内容”但不至于认不出缺口 */
function drawPattern(ctx, rnd) {
  const g = ctx.createLinearGradient(0, 0, W, H)
  g.addColorStop(0, '#1f5fa8')
  g.addColorStop(0.55, '#2a86c9')
  g.addColorStop(1, '#37b3d8')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, W, H)

  for (let i = 0; i < 12; i++) {
    ctx.beginPath()
    ctx.arc(rnd() * W, rnd() * H, 10 + rnd() * 44, 0, Math.PI * 2)
    ctx.fillStyle = `rgba(255,255,255,${0.04 + rnd() * 0.1})`
    ctx.fill()
  }
  for (let i = 0; i < 10; i++) {
    ctx.strokeStyle = `rgba(255,255,255,${0.05 + rnd() * 0.08})`
    ctx.lineWidth = 1 + rnd() * 2
    const x = rnd() * W
    ctx.beginPath()
    ctx.moveTo(x, 0)
    ctx.lineTo(x - 30 + rnd() * 60, H)
    ctx.stroke()
  }
  // 几何图形，让缺口周围有明确参照物
  for (let i = 0; i < 5; i++) {
    ctx.strokeStyle = `rgba(255, 255, 255, ${0.22 + rnd() * 0.25})`
    ctx.lineWidth = 2
    ctx.beginPath()
    if (i % 2 === 0) {
      ctx.arc(rnd() * W, rnd() * H, 14 + rnd() * 26, 0, Math.PI * 2)
    } else {
      const x = rnd() * W
      const y = rnd() * H
      ctx.rect(x - 20, y - 14, 40 + rnd() * 30, 28 + rnd() * 20)
    }
    ctx.stroke()
  }
  for (let i = 0; i < 240; i++) {
    ctx.fillStyle = `rgba(255,255,255,${rnd() * 0.1})`
    ctx.fillRect(rnd() * W, rnd() * H, 1.4, 1.4)
  }
}

function render() {
  const bg = bgRef.value
  const piece = pieceRef.value
  if (!bg || !piece) return

  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  const seed = Math.floor(Math.random() * 1e9)
  const rnd = makeRandom(seed)

  holeY.value = 40 + Math.floor(rnd() * (H - 80))
  targetOffset.value = Math.round(pieceR + 20 + rnd() * (maxOffset - pieceR - 40))

  // 底图（不含缺口）
  bg.width = Math.round(W * dpr)
  bg.height = Math.round(H * dpr)
  bg.style.width = `${W}px`
  bg.style.height = `${H}px`
  const bctx = bg.getContext('2d')
  bctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  bctx.clearRect(0, 0, W, H)
  drawPattern(bctx, makeRandom(seed))

  // 缺口：挖掉一块 + 描边
  const holeCx = targetOffset.value + pieceR
  bctx.save()
  bctx.beginPath()
  bctx.arc(holeCx, holeY.value, pieceR, 0, Math.PI * 2)
  bctx.fillStyle = 'rgba(4, 16, 30, 0.62)'
  bctx.fill()
  bctx.strokeStyle = 'rgba(255, 255, 255, 0.75)'
  bctx.lineWidth = 1.4
  bctx.stroke()
  bctx.restore()

  // 拼图块：内容取自缺口位置
  piece.width = Math.round(pieceSize * dpr)
  piece.height = Math.round(pieceSize * dpr)
  const pctx = piece.getContext('2d')
  pctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  pctx.clearRect(0, 0, pieceSize, pieceSize)
  pctx.save()
  pctx.beginPath()
  pctx.arc(pieceR, pieceR, pieceR, 0, Math.PI * 2)
  pctx.clip()
  const off = document.createElement('canvas')
  off.width = Math.round(W * dpr)
  off.height = Math.round(H * dpr)
  const octx = off.getContext('2d')
  octx.setTransform(dpr, 0, 0, dpr, 0, 0)
  drawPattern(octx, makeRandom(seed))
  pctx.drawImage(off, -(holeCx - pieceR), -(holeY.value - pieceR))
  pctx.restore()
  pctx.strokeStyle = 'rgba(255, 255, 255, 0.9)'
  pctx.lineWidth = 1.6
  pctx.beginPath()
  pctx.arc(pieceR, pieceR, pieceR - 1, 0, Math.PI * 2)
  pctx.stroke()

  // 测试用：把正确答案挂在 window 上（仅用于自动化验收）
  window.__tpCaptchaTarget = () => targetOffset.value
}

function reset() {
  state.value = 'idle'
  offset.value = 0
  dragging.value = false
  render()
}

function localX(evt) {
  const track = evt.currentTarget.getBoundingClientRect()
  return evt.clientX - track.left
}

function onDown(evt) {
  if (state.value === 'ok') return
  state.value = 'idle'
  dragging.value = true
  evt.currentTarget.setPointerCapture?.(evt.pointerId)
  moveTo(localX(evt))
}

function onMove(evt) {
  if (!dragging.value) return
  moveTo(localX(evt))
}

function onUp() {
  if (!dragging.value) return
  dragging.value = false
  check()
}

function moveTo(x) {
  offset.value = Math.max(0, Math.min(maxOffset, x - 24))
}

function check() {
  if (Math.abs(offset.value - targetOffset.value) <= tolerance) {
    state.value = 'ok'
    offset.value = targetOffset.value
    emit('verified')
  } else {
    state.value = 'bad'
    emit('failed')
    setTimeout(reset, 620)
  }
}

onMounted(render)

defineExpose({ reset })
</script>

<style scoped>
.cap {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cap-stage {
  position: relative;
  width: 320px;
  height: 160px;
  border-radius: 10px;
  overflow: hidden;
  background: #10233c;
}

.cap-canvas {
  position: absolute;
  left: 0;
  top: 0;
  display: block;
}

.cap-piece {
  left: 0;
  will-change: transform;
  filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.45));
}

.cap-track {
  position: relative;
  height: 44px;
  border-radius: 10px;
  background: #eef3f9;
  border: 1px solid #cfdcea;
  overflow: hidden;
  user-select: none;
  touch-action: none;
  cursor: grab;
}

.cap-track:active {
  cursor: grabbing;
}

.cap-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: rgba(45, 122, 208, 0.22);
}

.cap-fill.ok {
  background: rgba(46, 160, 96, 0.28);
}

.cap-fill.bad {
  background: rgba(198, 62, 62, 0.25);
}

.cap-handle {
  position: absolute;
  left: 0;
  top: 0;
  width: 44px;
  height: 42px;
  border-radius: 9px;
  background: #2d7ad0;
  color: #fff;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(20, 60, 110, 0.28);
}

.cap-handle.ok {
  background: #2ea060;
}

.cap-handle.bad {
  background: #c63e3e;
}

.cap-hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12.5px;
  color: #6b7d92;
  pointer-events: none;
}

.cap-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11.5px;
  color: #8798ac;
}

.cap-refresh {
  border: none;
  background: none;
  color: #2d7ad0;
  font-size: 11.5px;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
}
</style>
