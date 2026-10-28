<template>
  <div class="cap">
    <div class="cap-stage" :style="{ width: `${size.width}px`, height: `${size.height}px` }">
      <img v-if="challenge" class="cap-bg" :src="challenge.background" :width="size.width" :height="size.height" alt="" />
      <img
        v-if="challenge"
        class="cap-piece"
        :src="challenge.piece"
        :style="{
          width: `${size.piece}px`,
          height: `${size.piece}px`,
          top: `${size.pieceTop}px`,
          transform: `translateX(${offset}px)`,
        }"
        alt=""
      />
      <div v-if="loading" class="cap-loading">题目加载中…</div>
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
      <button type="button" class="cap-refresh" @click="reload">换一张</button>
    </div>
  </div>
</template>

<script setup>
/**
 * 滑块拼图验证码（**服务端出题 + 服务端校验**）。
 *
 * 这里拿到的只有两张渲染好的图片：带缺口的底图 + 拼图块，
 * 答案（缺口 x 坐标）在服务端，前端无从得知。
 * 拖动结束把偏移量提交给 /public/captcha/verify，通过后拿到一次性 ticket，
 * 登录时带上，服务端验签并作废。
 */
import { computed, onMounted, ref } from 'vue'
import { captchaApi } from '@/api'

const emit = defineEmits(['verified', 'failed'])

const challenge = ref(null)
const offset = ref(0)
const state = ref('idle') // idle | ok | bad
const dragging = ref(false)
const loading = ref(false)

const size = computed(() => ({
  width: challenge.value?.width ?? 320,
  height: challenge.value?.height ?? 160,
  piece: (challenge.value?.pieceR ?? 20) * 2,
  pieceTop: challenge.value?.pieceTop ?? 40,
}))

const maxOffset = computed(() => challenge.value?.maxOffset ?? 0)

const hint = computed(() => {
  if (loading.value) return '题目加载中…'
  if (state.value === 'ok') return '验证通过'
  if (state.value === 'bad') return '没对齐，已换一张，请重试'
  return dragging.value ? '松手校验' : '向右拖动滑块'
})

async function reload() {
  state.value = 'idle'
  offset.value = 0
  dragging.value = false
  challenge.value = null
  loading.value = true
  try {
    challenge.value = await captchaApi.issue()
  } catch (e) {
    challenge.value = null
  } finally {
    loading.value = false
  }
}

function localX(evt) {
  const rect = evt.currentTarget.getBoundingClientRect()
  return evt.clientX - rect.left
}

function onDown(evt) {
  if (state.value === 'ok' || !challenge.value) return
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
  submit()
}

function moveTo(x) {
  offset.value = Math.max(0, Math.min(maxOffset.value, x - 24))
}

async function submit() {
  if (!challenge.value) return
  try {
    const res = await captchaApi.verify(challenge.value.token, Math.round(offset.value))
    state.value = 'ok'
    emit('verified', res.ticket)
  } catch (e) {
    state.value = 'bad'
    emit('failed')
    setTimeout(reload, 620)
  }
}

onMounted(reload)
defineExpose({ reload })
</script>

<style scoped>
.cap {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cap-stage {
  position: relative;
  border-radius: 10px;
  overflow: hidden;
  background: #10233c;
}

.cap-bg {
  display: block;
}

.cap-piece {
  position: absolute;
  left: 0;
  will-change: transform;
  filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.45));
}

.cap-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #cfe0f2;
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
