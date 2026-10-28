<!--
  登录页。
  - 走 /api/v1/auth/login 换取 JWT，不再像旧版那样先拿「登录成功」字符串再查角色
  - 登录后按角色与面板偏好决定落点：管理员未选面板 → /entry；否则进入上次使用的面板
-->
<template>
  <div class="login-page">
    <!-- 实时天空：底色、太阳位置与颜色、星星、云都按北京时间渲染；左上角是调试卡片 -->
    <LoginSky />

    <div class="login-panel">
      <!-- 左侧品牌区 -->
      <div class="brand">
        <div class="brand-content">
          <div class="logo-row">
            <div class="logo-mark">TP</div>
            <span class="logo-text">TeamPulse</span>
          </div>
          <h1 class="headline">团队协作<br />与物联网考勤</h1>
          <p class="subline">
            日程、看板、工作日志与 ESP32 打卡机共用一套账号体系，<br />
            打卡数据经 MQTT 实时落到考勤看板。
          </p>
          <ul class="feature-list">
            <li><span class="dot"></span>JWT 无状态认证，接口统一错误码</li>
            <li><span class="dot"></span>设备 HMAC 签名 + nonce 防重放</li>
            <li><span class="dot"></span>迟到/早退自动判定与日汇总</li>
          </ul>

          <!-- 访客位置与当前天气：登录页的天空就是按这里的数据渲染的 -->
          <div class="sky-card">
            <div class="sky-main">
              <el-icon class="sky-icon" aria-hidden="true"><component :is="weatherIcon" /></el-icon>
              <span class="sky-temp">{{ temperature === null ? '--' : temperature }}<em>°C</em></span>
              <span class="sky-weather">{{ sky.bucketLabel }}</span>
              <span v-if="sky.cloudCover !== null" class="sky-cover">云量 {{ sky.cloudCover }}%</span>
            </div>
            <div class="sky-place">
              <span class="sky-pin" aria-hidden="true">◎</span>
              <span class="sky-place-text">{{ sky.placeLabel }}</span>
              <button type="button" class="sky-change" @click="openRegion">
                {{ sky.isManual ? '已手动选择' : '修改' }}
              </button>
            </div>
            <div class="sky-note">
              <span v-if="sky.ipCity">IP 定位：{{ sky.ipCity }}</span>
              <span v-else>IP 定位不可用，按默认城市展示</span>
              <button v-if="sky.isManual" type="button" class="sky-reset" @click="resetRegion">
                恢复 IP 定位
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 连续失败后要求过验证码 -->
      <el-dialog
        v-model="captchaOpen"
        title="安全验证"
        width="392px"
        align-center
        append-to-body
        destroy-on-close
        class="captcha-dialog"
        @open="onCaptchaOpen"
      >
        <p class="captcha-tip">密码已连续输错 {{ failCount }} 次，拖动滑块完成验证后继续</p>
        <p class="captcha-progress">
          本机已失败 <b>{{ guard.ipFailures }}</b> / 10 次，达到 10 次将临时限制登录 10 分钟
        </p>
        <p class="captcha-forgot">忘记了您的密码？请联系您的主管或服务器管理员重置密码。</p>
        <div class="captcha-body">
          <CaptchaSlider ref="captchaRef" @verified="onCaptchaVerified" />
        </div>
      </el-dialog>

      <!-- 城市选择：省 → 市 → 区 三级联动 -->
      <el-dialog
        v-model="regionOpen"
        title="选择你所在的城市"
        width="420px"
        align-center
        append-to-body
        class="region-dialog"
      >
        <p class="region-tip">用于展示当地天气与天色；只保存城市名，不保存 IP。</p>
        <el-cascader
          v-model="regionPath"
          :options="regionOptions"
          :props="{ expandTrigger: 'click' }"
          filterable
          clearable
          separator=" / "
          placeholder="省 / 市 / 区"
          style="width: 100%"
          @change="onRegionChange"
        />
        <template #footer>
          <el-button @click="regionOpen = false">取消</el-button>
          <el-button type="primary" :loading="sky.loading" @click="applyRegion">确定</el-button>
        </template>
      </el-dialog>

      <!-- 右侧表单区 -->
      <div class="form-side">
        <el-card class="form-card" shadow="never">
          <h2 class="form-title">登录</h2>
          <p class="form-subtitle">请使用企业账号登录</p>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            @submit.prevent="handleLogin"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="form.username"
                size="large"
                placeholder="请输入用户名"
                clearable
                :prefix-icon="User"
                autocomplete="username"
              />
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                size="large"
                placeholder="请输入密码"
                show-password
                :prefix-icon="Lock"
                autocomplete="current-password"
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <el-button
              ref="loginBtnRef"
              type="primary"
              size="large"
              class="submit-btn"
              :class="{ 'is-failed': loginFailed }"
              :loading="loading"
              :disabled="isBlocked"
              @click="handleLogin"
            >
              {{ loading ? '登录中…' : '登 录' }}
            </el-button>
          </el-form>

          <div v-if="isBlocked" class="block-tip">
            <el-icon><InfoFilled /></el-icon>
            登录尝试过于频繁，已临时限制登录，请 {{ blockedMinutes }} 分钟后重试
          </div>

          <div v-else-if="guard.ipFailures > 0 || captchaTriggered" class="guard-tip">
            <el-icon><InfoFilled /></el-icon>
            本机连续失败 <b>{{ guard.ipFailures }}</b> / 10 次，达到 10 次将临时限制登录 10 分钟
          </div>

          <div v-if="captchaTriggered" class="forgot-tip">
            <el-icon><InfoFilled /></el-icon>
            忘记了您的密码？请联系您的主管或服务器管理员重置密码。
          </div>

          <div class="hint-box">
            <el-icon><InfoFilled /></el-icon>
            <div>
              首次启动会自动创建管理员账号
              <code>admin / admin123</code>，登录后请立即修改密码。
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import CaptchaSlider from '@/components/CaptchaSlider.vue'
import LoginSky from '@/components/LoginSky.vue'
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { InfoFilled, Lock, User } from '@element-plus/icons-vue'
import { Cloudy, Drizzling, Lightning, Moon, MostlyCloudy, PartlyCloudy, Pouring, Sunny, Sunrise } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { loginGuardApi } from '@/api'
import { useSkyStore } from '@/stores/sky'
import { applyTimeBasedTheme } from '@/utils/theme'
import { circleTransition } from '@/utils/pageTransition'
import { computed, nextTick } from 'vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const sky = useSkyStore()
const regionOpen = ref(false)
const regionPath = ref([])
const regionOptions = ref([])

/** 天气图标：用 Element Plus 图标，避免 emoji 在不同系统上渲染不一致 */
const WEATHER_ICON = {
  clear: Sunny,
  'mostly-clear': PartlyCloudy,
  partly: MostlyCloudy,
  overcast: Cloudy,
  rain: Pouring,
  snow: Drizzling,
  fog: Cloudy,
  thunder: Lightning,
}
const weatherIcon = computed(() => WEATHER_ICON[sky.bucket] || MostlyCloudy)
const temperature = computed(() => sky.temperature)

async function openRegion() {
  regionOpen.value = true
  regionOptions.value = await sky.loadRegions()
  regionPath.value = sky.manual
    ? [sky.manual.province, sky.manual.city, sky.manual.district].filter(Boolean)
    : [sky.province, sky.city, sky.district].filter(Boolean)
}

function onRegionChange(value) {
  regionPath.value = value || []
}

async function applyRegion() {
  const [province, city, district] = regionPath.value || []
  if (!city && !district) {
    regionOpen.value = false
    return
  }
  await sky.setManual({ province, city, district })
  regionOpen.value = false
  ElMessage.success(`已切换到 ${district || city}`)
}

async function resetRegion() {
  await sky.resetManual()
  regionPath.value = []
  ElMessage.success('已恢复按 IP 定位')
}

const formRef = ref(null)
const loginBtnRef = ref(null)
/** 登录失败时按钮短暂变红 */
const loginFailed = ref(false)

/** 连续失败次数（存在 sessionStorage，刷新页面不会清零） */
const FAIL_KEY = 'tp_login_fail_count'
const CAPTCHA_THRESHOLD = 5
const failCount = ref(Number(sessionStorage.getItem(FAIL_KEY) || 0))
const captchaOpen = ref(false)
const captchaPassed = ref(false)
/** 服务端发放的一次性票据（一次性，用掉就要重新验） */
const captchaTicket = ref('')
/** 是否已经触发过验证码：一旦触发，之后每次登录都要重新验证 */
const captchaTriggered = ref(false)
/** 服务端返回的防护状态（失败计数 / 是否拉黑 / 剩余秒数） */
const guard = ref({ userFailures: 0, ipFailures: 0, captchaRequired: false, blocked: false, blockedRemainingSeconds: 0 })

/** 按服务端数据同步本地状态；拿不到就保留原值 */
async function refreshGuard() {
  try {
    const data = await loginGuardApi.status(form.username.trim() || '')
    guard.value = data
    if (data?.blocked) {
      blockedUntil.value = Date.now() + (data.blockedRemainingSeconds || 600) * 1000
    }
  } catch {
    /* 拿不到就静默，不影响登录 */
  }
}

/** IP 被拉黑时的解封时间戳（0 表示未拉黑） */
const blockedUntil = ref(0)
const nowTick = ref(Date.now())

/** 拉黑剩余秒数 */
const blockedSeconds = computed(() => Math.max(0, Math.ceil((blockedUntil.value - nowTick.value) / 1000)))
/** 展示用分钟数：四舍五入，避免 600.4s 被算成 11 分钟 */
const blockedMinutes = computed(() => Math.max(1, Math.round(blockedSeconds.value / 60)))
const isBlocked = computed(() => blockedSeconds.value > 0)
const captchaRef = ref(null)

/**
 * 每次打开验证码弹窗都要重置：
 * ticket 是一次性的（上次已经用掉了），滑块也要换一道新题，
 * 否则第二次会看到上一次「已验证」的状态。
 */
window.setInterval(() => {
  nowTick.value = Date.now()
}, 1000)

onMounted(() => {
  refreshGuard()
})

function onCaptchaOpen() {
  captchaTriggered.value = true
  captchaTicket.value = ''
  captchaPassed.value = false
  nextTick(() => captchaRef.value?.reload?.())
}

function bumpFail() {
  failCount.value += 1
  try {
    sessionStorage.setItem(FAIL_KEY, String(failCount.value))
  } catch {
    /* ignore */
  }
}

function resetFail() {
  failCount.value = 0
  captchaPassed.value = false
  try {
    sessionStorage.removeItem(FAIL_KEY)
  } catch {
    /* ignore */
  }
}

function onCaptchaVerified(ticket) {
  captchaPassed.value = true
  captchaTicket.value = ticket || ''
  captchaOpen.value = false
  // 刻意不重置失败计数：票据一次性，验证只对紧接着这一次登录有效，
  // 再错一次就要重新验（符合「触发之后每错一次验一次」的要求）
  ElMessage.success('验证通过，请重新登录')
}
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

/** 登录成功后要去哪 */
function targetAfterLogin() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  if (!auth.canUseAdminPanel) return redirect || '/u/home'
  const preferred = localStorage.getItem('preferredPanel')
  if (!preferred) return '/entry'
  return preferred === 'user' ? '/u/home' : '/dashboard'
}

/** 失败反馈：按钮变红（560ms 后复原）。没有抖动。 */
function flashLoginError() {
  loginFailed.value = false
  nextTick(() => {
    loginFailed.value = true
    window.setTimeout(() => {
      loginFailed.value = false
    }, 560)
  })
}

/**
 * 登录成功转场：从登录按钮升起一个顶层圆 → 扩张铺满 → 切路由 → 再缩回。
 * 用的是 Web Animations 的 clip-path 动画，等价于「圆形揭开」。
 */
async function playLoginTransition(target) {
  const btn = loginBtnRef.value?.$el ?? loginBtnRef.value
  await circleTransition({ origin: btn, navigate: () => router.replace(target).catch(() => {}) })
}

async function handleLogin() {
  if (loading.value) return

  // ① IP 被拉黑中：直接提示，不发请求
  if (isBlocked.value) {
    flashLoginError()
    ElMessage.error(`登录尝试过于频繁，请 ${blockedMinutes.value} 分钟后再试`)
    return
  }

  // ② 已触发过验证码时不再本地拦截：照常发请求（可能没有 ticket），
  //    让后端把这次失败计入 IP 计数，否则 10 次拉黑永远触发不到。
  //    没有 ticket 时后端会返回 40101，前端收到后再弹新题。

  // ② 表单没填/格式不对：同样算一次"点了没进去"，变红并计数
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    flashLoginError()
    bumpFail()
    // 表单没填/格式不对时请求不会到认证接口，服务端统计不到，
    // 这里显式登记一次，保证「N/10」与服务端一致
    try {
      guard.value = await loginGuardApi.attempt(form.username.trim() || '')
      if (guard.value?.blocked) {
        blockedUntil.value = Date.now() + (guard.value.blockedRemainingSeconds || 600) * 1000
      }
    } catch {
      /* 登记失败不影响登录流程 */
    }
    if (failCount.value >= CAPTCHA_THRESHOLD) {
      captchaTriggered.value = true
      captchaOpen.value = true
    }
    return
  }

  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password, captchaTicket.value || undefined)
    resetFail()
    // 登录进来按当前时间决定白天/夜间（用户手动切过则以手动为准）
    applyTimeBasedTheme()
    await playLoginTransition(targetAfterLogin())
    ElMessage.success('登录成功')
  } catch (error) {
    flashLoginError()
    bumpFail()
    captchaPassed.value = false
    captchaTicket.value = ''
    // 40103 / HTTP 429：IP 被拉黑，设置倒计时
    if (error?.code === 40103 || error?.code === 429) {
      const minutes = Number((error?.message || '').match(/(\d+)\s*分钟/)?.[1] || 10)
      blockedUntil.value = Date.now() + minutes * 60 * 1000
      ElMessage.error(`登录尝试过于频繁，请 ${minutes} 分钟后再试`)
    } else if (error?.code === 40101 || error?.code === 423) {
      // 40101：只弹验证码，不额外提示（避免和拦截器重复）
      // 服务端要求验证码：被动弹出新题（说明这次请求已被计入失败次数）
      captchaTriggered.value = true
      captchaOpen.value = true
    } else if (failCount.value >= CAPTCHA_THRESHOLD) {
      captchaTriggered.value = true
      captchaOpen.value = true
      ElMessage.warning('连续输错 5 次，请完成安全验证')
    }
    refreshGuard()
    // 具体错误文案由 http 拦截器统一提示，这里只兜住未登录的边界情况
    if (!error?.code) ElMessage.error(error?.message || '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  /* 100vh + 上下 padding 会撑出竖向滚动条，用 border-box 把 padding 算进高度 */
  box-sizing: border-box;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  /* 兜底底色：实际天空由 LoginSky 的 canvas 整片绘制 */
  background: #0a1729;
  overflow: hidden;
}

.login-panel {
  position: relative;
  z-index: 1;
  /* 暗色下多出 1px 边框：用 border-box 保证昼夜两种状态的几何完全一致 */
  box-sizing: border-box;
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  width: 100%;
  max-width: 940px;
  min-height: 520px;
  border-radius: 18px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 24px 64px -16px rgba(2, 6, 23, 0.65);
}

/* ---------- 品牌区 ---------- */
.brand {
  position: relative;
  display: flex;
  align-items: center;
  padding: 48px 44px;
  color: #f8fafc;
  background: linear-gradient(150deg, #1d4ed8 0%, #0ea5e9 55%, #06b6d4 100%);
}
.brand::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 78% 18%, rgba(255, 255, 255, 0.32), transparent 58%);
  pointer-events: none;
}
.brand-content {
  position: relative;
  z-index: 1;
}
.logo-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 28px;
}
.logo-mark {
  width: 36px;
  height: 36px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 15px;
  letter-spacing: 0.5px;
  background: rgba(255, 255, 255, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.35);
}
.logo-text {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.4px;
}
.headline {
  margin: 0 0 14px;
  font-size: 30px;
  line-height: 1.32;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.subline {
  margin: 0 0 26px;
  font-size: 13px;
  line-height: 1.75;
  color: rgba(248, 250, 252, 0.86);
}
.feature-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 11px;
}
.feature-list li {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 12.5px;
  color: rgba(248, 250, 252, 0.9);
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #a5f3fc;
  box-shadow: 0 0 0 3px rgba(165, 243, 252, 0.26);
  flex: none;
}

/* ---------- 表单区 ---------- */
.sky-card {
  margin-top: 26px;
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.28);
  backdrop-filter: blur(6px);
  max-width: 380px;
}

.sky-main {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.sky-icon {
  font-size: 24px;
  line-height: 1;
  color: #ffffff;
}

.sky-temp {
  font-size: 26px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: #ffffff;
}

.sky-temp em {
  font-size: 13px;
  font-style: normal;
  opacity: 0.8;
  margin-left: 2px;
}

.sky-weather {
  font-size: 14px;
  font-weight: 500;
  color: #eaf4ff;
}

.sky-cover {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.72);
}

.sky-place {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.9);
}

.sky-pin {
  opacity: 0.8;
}

.sky-place-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sky-change {
  border: 1px solid rgba(255, 255, 255, 0.45);
  background: rgba(255, 255, 255, 0.12);
  color: #ffffff;
  font-size: 12px;
  padding: 2px 9px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.sky-change:hover {
  background: rgba(255, 255, 255, 0.24);
}

.sky-note {
  margin-top: 7px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 11.5px;
  color: rgba(255, 255, 255, 0.62);
}

.sky-reset {
  border: none;
  background: none;
  color: rgba(255, 255, 255, 0.78);
  text-decoration: underline;
  font-size: 11.5px;
  cursor: pointer;
  padding: 0;
}

.region-tip {
  margin: 0 0 12px;
  font-size: 12.5px;
  color: #64748b;
}

.form-side {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 48px;
  background: #fff;
}
.form-card {
  width: 100%;
  border: none;
  /* 登录页的卡片嵌在白色表单区里，跟随全局淡蓝底会露出色块，这里保持透明 */
  background: transparent;
  box-shadow: none;
}
/*
  登录页的卡片固定亮色（天空负责氛围）。但全局暗色主题里有
  `html.dark .el-input__wrapper { background:#131a22 }` 这类规则，
  会把卡片里的输入框染黑，看起来像"只有输入框变了色"。
  这里用更高的选择器权重把它固定成亮色。
*/
.form-card :deep(.el-input__wrapper) {
  background-color: #ffffff;
  box-shadow: 0 0 0 1px #c4d9f1 inset;
}

.form-card :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #a9c9ec inset;
}

.form-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--tp-primary) inset;
}

.form-card :deep(.el-input__inner) {
  color: #0f172a;
}

.form-card :deep(.el-input__inner::placeholder) {
  color: #94a3b8;
}

.form-card :deep(.el-form-item__label) {
  color: #334155;
}

.form-card :deep(.el-card__body) {
  padding: 0;
}
.form-title {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 650;
  color: #0f172a;
}
.form-subtitle {
  margin: 0 0 26px;
  font-size: 13px;
  color: #64748b;
}
/* 登录失败：按钮变红（不抖动） */
.submit-btn.is-failed {
  background-color: #d64545 !important;
  border-color: #d64545 !important;
}

/* 验证码弹窗 */
.captcha-progress {
  margin: -8px 0 10px;
  font-size: 12px;
  color: #2c5282;
  text-align: center;
}

.captcha-progress b {
  color: #c53030;
}

.captcha-forgot {
  margin: -6px 0 14px;
  font-size: 12px;
  color: #b45309;
  text-align: center;
}

.guard-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 2px 0 8px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.5;
  background: #eef4ff;
  border: 1px solid #cddff8;
  color: #2c5282;
}

.guard-tip b {
  color: #c53030;
}

.forgot-tip,
.block-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 2px 0 12px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.forgot-tip {
  background: #fff7e6;
  border: 1px solid #f5d9a8;
  color: #8a5a00;
}

.block-tip {
  background: #fdecec;
  border: 1px solid #f3bcbc;
  color: #a32b2b;
}

.captcha-body {
  display: flex;
  justify-content: center;
}

.captcha-tip {
  margin: 0 0 14px;
  font-size: 12.5px;
  color: #64748b;
  text-align: center;
}

.submit-btn {
  width: 100%;
  height: 44px;
  margin-top: 4px;
  font-size: 15px;
  letter-spacing: 3px;
  border-radius: 9px;
}
.hint-box {
  display: flex;
  gap: 8px;
  margin-top: 22px;
  padding: 11px 13px;
  border-radius: 9px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
}
.hint-box code {
  padding: 1px 5px;
  border-radius: 4px;
  background: #e2e8f0;
  color: #1d4ed8;
  font-size: 11.5px;
}

/* 窄屏只保留表单 */
@media (max-width: 860px) {
  .login-panel {
    grid-template-columns: 1fr;
    max-width: 430px;
    min-height: unset;
  }
  .brand {
    display: none;
  }
  .form-side {
    padding: 38px 30px;
  }
}
</style>
