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
              type="primary"
              size="large"
              class="submit-btn"
              :loading="loading"
              @click="handleLogin"
            >
              {{ loading ? '登录中…' : '登 录' }}
            </el-button>
          </el-form>

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
import LoginSky from '@/components/LoginSky.vue'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { InfoFilled, Lock, User } from '@element-plus/icons-vue'
import { Cloudy, Drizzling, Lightning, Moon, MostlyCloudy, PartlyCloudy, Pouring, Sunny, Sunrise } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useSkyStore } from '@/stores/sky'
import { computed } from 'vue'

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
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  if (loading.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    ElMessage.success('登录成功')

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    if (!auth.canUseAdminPanel) {
      router.replace(redirect || '/u/home')
      return
    }
    // 管理员：沿用上次选择的面板，没选过就先让其选一次
    const preferred = localStorage.getItem('preferredPanel')
    if (!preferred) {
      router.replace('/entry')
    } else {
      router.replace(preferred === 'user' ? '/u/home' : '/dashboard')
    }
  } catch (error) {
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
