<!--
  管理后台首页（仪表盘）。

  布局分层（自上而下）：
    1. 问候条：问候语 + 日期 + 四个主入口按钮（原来是一个 220px 高的渐变 Hero，
       占据了首屏一半，指标却被挤到最下面，信息密度太低）
    2. KPI 指标行：6 个指标卡，一行铺满，含考勤与设备（这两项是物联网部分的核心数据）
    3. 主体双列：左侧「最近动态」时间线（占 15/24），右侧「快捷入口 + 系统告警」（9/24）
       小屏幕下自动堆叠成一列

  数据来源：/schedule、/worklog、/kanban/my、/user、/attendance/overview，
  均为既有的列表/统计接口，任一失败都不影响其它区块渲染。
-->
<template>
  <div class="dashboard">
    <!-- 1. 问候条 -->
    <section class="hero">
      <div class="hero-text">
        <div class="hello">{{ greet }}，{{ bossName }}</div>
        <div class="sub">公司管理台 · 关键指标、进展与风险总览</div>
        <div class="date">{{ todayText }}</div>
      </div>
      <div class="hero-actions">
        <el-button type="primary" @click="go('/overview/company')">
          <el-icon><TrendCharts /></el-icon>公司概览
        </el-button>
        <el-button @click="go('/attendance/dashboard')">
          <el-icon><Clock /></el-icon>考勤看板
        </el-button>
        <el-button @click="go('/teams')">
          <el-icon><User /></el-icon>团队管理
        </el-button>
        <el-button @click="go('/kanban')">
          <el-icon><Grid /></el-icon>看板总览
        </el-button>
      </div>
    </section>

    <!-- 2. KPI 指标行 -->
    <section class="kpi-grid">
      <div
        v-for="k in kpis"
        :key="k.key"
        class="kpi"
        :class="k.tone"
        role="button"
        tabindex="0"
        @click="go(k.to)"
        @keyup.enter="go(k.to)"
      >
        <div class="kpi-top">
          <el-icon class="kpi-icon"><component :is="k.icon" /></el-icon>
          <span class="kpi-label">{{ k.label }}</span>
        </div>
        <div class="kpi-value">
          {{ k.value }}<span v-if="k.unit" class="kpi-unit">{{ k.unit }}</span>
        </div>
        <div class="kpi-foot">{{ k.foot }}</div>
      </div>
    </section>

    <!-- 3. 主体双列 -->
    <el-row :gutter="14" class="main-row">
      <!-- 最近动态 -->
      <el-col :xs="24" :lg="15">
        <el-card class="panel" shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">最近动态</span>
              <el-button link type="primary" @click="load">刷新</el-button>
            </div>
          </template>

          <!-- 固定高度 + 内部滚动：动态条数再多也不会把整页拉长 -->
          <div class="timeline-scroll">
            <el-skeleton :loading="loading.schedules || loading.worklogs" animated>
              <template #template>
                <div v-for="i in 3" :key="i" class="skeleton-row">
                  <el-skeleton-item variant="circle" style="width: 28px; height: 28px" />
                  <div style="flex: 1">
                    <el-skeleton-item variant="p" style="width: 60%; margin-bottom: 8px" />
                    <el-skeleton-item variant="text" style="width: 40%" />
                  </div>
                </div>
              </template>

              <template #default>
                <el-timeline v-if="recentActivities.length">
                  <el-timeline-item
                    v-for="activity in recentActivities"
                    :key="activity.id"
                    :color="activity.color"
                    :timestamp="formatStamp(activity.timestamp)"
                    placement="top"
                  >
                    <div class="activity" @click="activity.to && go(activity.to)">
                      <div class="activity-title">
                        <el-icon class="activity-icon" :style="{ color: activity.color }">
                          <component :is="activity.icon" />
                        </el-icon>
                        <span>{{ activity.title }}</span>
                      </div>
                      <div class="activity-desc">{{ activity.description }}</div>
                      <div class="activity-meta">
                        <el-tag
                          v-for="t in activity.tags || []"
                          :key="t.text"
                          size="small"
                          :type="t.type || 'info'"
                          effect="plain"
                        >
                          {{ t.text }}
                        </el-tag>
                      </div>
                    </div>
                  </el-timeline-item>
                </el-timeline>
                <el-empty v-else description="暂无最近动态" :image-size="72">
                  <el-button size="small" @click="load">刷新</el-button>
                </el-empty>
              </template>
            </el-skeleton>
          </div>
        </el-card>
      </el-col>

      <!-- 快捷入口 + 告警 -->
      <el-col :xs="24" :lg="9">
        <el-card class="panel" shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">快捷入口</span>
            </div>
          </template>
          <div class="quick-grid">
            <div v-for="q in quickLinks" :key="q.to" class="quick-item" @click="go(q.to)">
              <el-icon class="quick-icon"><component :is="q.icon" /></el-icon>
              <div class="quick-text">
                <div class="quick-title">{{ q.title }}</div>
                <div class="quick-desc">{{ q.desc }}</div>
              </div>
            </div>
          </div>
        </el-card>

        <el-card v-if="alerts.length" class="panel alerts-panel" shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">系统告警</span>
              <el-tag size="small" type="danger" effect="plain">{{ alerts.length }}</el-tag>
            </div>
          </template>
          <el-alert
            v-for="(a, idx) in alerts"
            :key="idx"
            :title="a.title"
            :type="a.type"
            :description="a.desc"
            :closable="false"
            show-icon
            class="alert-item"
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, markRaw, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { attendanceApi, kanbanApi, scheduleApi, userApi, worklogApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import {
  Calendar,
  ChatDotRound,
  Clock,
  Document,
  Grid,
  Monitor,
  TrendCharts,
  User,
} from '@element-plus/icons-vue'

// 角色与用户名统一取自 Pinia（登录时由 /auth/login 写入），不读浏览器缓存
const auth = useAuthStore()
const router = useRouter()

const bossName = ref(auth.displayName || '管理员')
const greet = ref('')
const todayText = ref('')

function computeGreeting() {
  const h = new Date().getHours()
  greet.value = h < 6 ? '凌晨好' : h < 12 ? '早上好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好'
  const d = new Date()
  const w = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  todayText.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} 星期${w}`
}

// ---------- 指标数据 ----------
const scheduleCount = ref(0)
const kanbanCount = ref(0)
const worklogCount = ref(0)
const usersTotal = ref(0)
const health = ref({ ok: false, text: '未连通' })

/** 今日考勤与设备（来自 /attendance/overview，失败时保持 0，不影响其它区块） */
const attendanceStats = ref({
  checkedIn: 0,
  totalEmployees: 0,
  lateCount: 0,
  absentCount: 0,
  onlineDevices: 0,
  totalDevices: 0,
})

const allSchedules = ref([])
const allWorklogs = ref([])
const allUsers = ref([])
const recentActivities = ref([])
const alerts = ref([])

const loading = ref({ schedules: false, worklogs: false })

function go(to) {
  if (to) router.push(to)
}

/** 时间戳统一显示成 MM-dd HH:mm，动态列表里不需要年份 */
function formatStamp(v) {
  if (!v) return ''
  const s = typeof v === 'string' ? (v.includes('T') ? v : v.replace(' ', 'T')) : v
  const d = new Date(s)
  if (isNaN(d.getTime())) return String(v).slice(0, 16)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 6 个 KPI：用户 / 日程 / 日志 / 看板 / 今日打卡 / 设备在线 */
const kpis = computed(() => [
  {
    key: 'users',
    label: '用户总数',
    value: usersTotal.value,
    unit: '人',
    icon: User,
    tone: '',
    foot: '账号与成员',
    to: '/users',
  },
  {
    key: 'schedules',
    label: '日程',
    value: scheduleCount.value,
    unit: '条',
    icon: Calendar,
    tone: 'tone-blue',
    foot: '全部日程',
    to: '/schedule',
  },
  {
    key: 'worklogs',
    label: '工作日志',
    value: worklogCount.value,
    unit: '篇',
    icon: Document,
    tone: 'tone-green',
    foot: '累计提交',
    to: '/worklogs',
  },
  {
    key: 'kanbans',
    label: '看板',
    value: kanbanCount.value,
    unit: '个',
    icon: Grid,
    tone: '',
    foot: '我可见的看板',
    to: '/kanban',
  },
  {
    key: 'punch',
    label: '今日打卡',
    value: attendanceStats.value.checkedIn,
    unit: `/ ${attendanceStats.value.totalEmployees || 0} 人`,
    icon: Clock,
    tone: 'tone-orange',
    foot: `迟到 ${attendanceStats.value.lateCount} · 缺勤 ${attendanceStats.value.absentCount}`,
    to: '/attendance/records',
  },
  {
    key: 'devices',
    label: '设备在线',
    value: attendanceStats.value.onlineDevices,
    unit: `/ ${attendanceStats.value.totalDevices || 0} 台`,
    icon: Monitor,
    tone: '',
    foot: '打卡机状态',
    to: '/attendance/devices',
  },
])

/** 快捷入口：右侧栏竖排两列 */
const quickLinks = [
  { title: '公司概览', desc: '关键指标与趋势', icon: markRaw(TrendCharts), to: '/overview/company' },
  { title: '考勤看板', desc: '实时打卡与异常', icon: markRaw(Clock), to: '/attendance/dashboard' },
  { title: '团队管理', desc: '团队与成员', icon: markRaw(User), to: '/teams' },
  { title: '看板总览', desc: '项目与流程', icon: markRaw(Grid), to: '/kanban' },
  { title: '日志管理', desc: '日志与进度', icon: markRaw(Document), to: '/worklogs' },
  { title: '评论管理', desc: '评论与敏感词', icon: markRaw(ChatDotRound), to: '/comments' },
]

// ---------- 告警 ----------
function computeAlerts() {
  const arr = []
  if (!health.value.ok) {
    arr.push({ type: 'error', title: '后端未连通', desc: '请检查服务状态与前端代理配置。' })
  }
  let overdue = 0
  const now = Date.now()
  const titles = []
  for (const s of allSchedules.value || []) {
    const st = (s.status || '').toLowerCase()
    const et = s.endTime
    if (!et) continue
    const dt = new Date(String(et).includes('T') ? et : String(et).replace(' ', 'T'))
    const ms = dt.getTime()
    if (!isNaN(ms) && ms < now && st !== 'done' && st !== 'canceled') {
      overdue++
      if (titles.length < 3) titles.push(s.title || `日程#${s.scheduleId}`)
    }
  }
  if (overdue > 0) {
    arr.push({
      type: 'warning',
      title: '日程超期',
      desc: `有 ${overdue} 个日程已超过截止时间：${titles.join('，')}${overdue > titles.length ? ' 等' : ''}`,
    })
  }
  if (attendanceStats.value.absentCount > 0) {
    arr.push({
      type: 'info',
      title: '今日有缺勤',
      desc: `今日 ${attendanceStats.value.absentCount} 人未打卡，可到打卡记录里补卡。`,
    })
  }
  alerts.value = arr
}

function toArray(x) {
  if (Array.isArray(x)) return x
  if (x && typeof x === 'string') {
    try {
      const j = JSON.parse(x)
      if (Array.isArray(j)) return j
    } catch {
      /* 非数组结构直接忽略 */
    }
  }
  return []
}

// ---------- 加载 ----------
async function load() {
  let connected = false

  loading.value.schedules = true
  try {
    const page = await scheduleApi.page({ page: 1, size: 50 })
    allSchedules.value = page?.records || []
    scheduleCount.value = Number(page?.total || 0)
    connected = true
  } catch {
    allSchedules.value = []
  } finally {
    loading.value.schedules = false
  }

  try {
    const kb = await kanbanApi.my()
    kanbanCount.value = Array.isArray(kb) ? kb.length : 0
    connected = true
  } catch {
    /* 看板接口失败不影响其它指标 */
  }

  loading.value.worklogs = true
  try {
    const page = await worklogApi.page({ page: 1, size: 50 })
    allWorklogs.value = page?.records || []
    worklogCount.value = Number(page?.total || 0)
    connected = true
  } catch {
    allWorklogs.value = []
  } finally {
    loading.value.worklogs = false
  }

  try {
    const page = await userApi.page({ page: 1, size: 200 })
    const arr = page?.records || []
    allUsers.value = arr
    usersTotal.value = Number(page?.total || arr.length)
    connected = true
  } catch {
    /* 用户接口失败时保留 0 */
  }

  // 今日考勤 + 设备在线
  try {
    const ov = await attendanceApi.overview()
    attendanceStats.value = {
      checkedIn: Number(ov?.checkedIn || 0),
      totalEmployees: Number(ov?.totalEmployees || 0),
      lateCount: Number(ov?.lateCount || 0),
      absentCount: Number(ov?.absentCount || 0),
      onlineDevices: Number(ov?.onlineDevices || 0),
      totalDevices: Number(ov?.totalDevices || 0),
    }
    connected = true
  } catch {
    /* 考勤统计失败时保留 0 */
  }

  health.value = connected ? { ok: true, text: '后端已连通' } : { ok: false, text: '后端未连通' }

  buildRecentActivities()
  computeAlerts()
}

/** 把日程与日志合成一条按时间倒序的动态流（最多 15 条） */
function buildRecentActivities() {
  const usersArr = toArray(allUsers.value)
  const schedulesArr = toArray(allSchedules.value)
  const worklogsArr = toArray(allWorklogs.value)

  const userById = new Map(usersArr.map((u) => [u.userId, u]))
  const uname = (id) => {
    const u = userById.get(id)
    return u ? u.nickname || u.username || `用户#${id}` : id ? `用户#${id}` : '用户'
  }
  const scheduleById = new Map(schedulesArr.map((s) => [s.scheduleId, s]))
  const stamp = (v) => {
    if (!v) return ''
    const s = typeof v === 'string' ? (v.includes('T') ? v : v.replace(' ', 'T')) : v
    const d = new Date(s)
    if (isNaN(d.getTime())) return ''
    const pad = (n) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  const STATUS_TEXT = { todo: '待办', doing: '进行中', done: '已完成', canceled: '已取消' }
  const PRIORITY_TEXT = { low: '低', medium: '中', high: '高' }

  const activities = []

  schedulesArr.slice(-50).forEach((s) => {
    const tags = [
      s.status ? { text: STATUS_TEXT[s.status] || s.status, type: s.status === 'done' ? 'success' : s.status === 'doing' ? 'warning' : 'info' } : null,
      s.priority ? { text: `优先级 ${PRIORITY_TEXT[s.priority] || s.priority}`, type: s.priority === 'high' ? 'danger' : 'info' } : null,
      s.endTime ? { text: `截止 ${stamp(s.endTime).slice(0, 16)}`, type: 'info' } : null,
    ].filter(Boolean)
    activities.push({
      id: `schedule-${s.scheduleId}`,
      title: `${uname(s.creatorId)} 创建了日程`,
      description: `“${s.title}”`,
      timestamp: s.createdAt || s.startTime,
      icon: markRaw(Calendar),
      color: '#2f6fd0',
      tags,
      to: s?.scheduleId ? `/schedule/${s.scheduleId}` : undefined,
    })
  })

  worklogsArr.slice(-50).forEach((w) => {
    const rel = w.relatedScheduleId ? scheduleById.get(w.relatedScheduleId) : null
    const tags = [
      w.logDate ? { text: `日志日期 ${String(w.logDate)}`, type: 'info' } : null,
      rel ? { text: `关联 ${rel.title || '日程#' + rel.scheduleId}`, type: 'primary' } : null,
    ].filter(Boolean)
    const snippet = (w.title && w.title.trim().length > 0 ? w.title : w.content || '').toString().slice(0, 40)
    activities.push({
      id: `worklog-${w.worklogId}`,
      title: `${uname(w.creatorId)} 提交了工作日志`,
      description: snippet || '提交了日志',
      timestamp: w.createdAt || w.logDate,
      icon: markRaw(Document),
      color: '#059669',
      tags,
      to: w?.worklogId ? `/worklogs/${w.worklogId}` : undefined,
    })
  })

  activities.sort((a, b) => {
    const ta = Date.parse(a.timestamp)
    const tb = Date.parse(b.timestamp)
    if (!isNaN(tb) && !isNaN(ta)) return tb - ta
    return (Number(String(b.id).split('-').pop()) || 0) - (Number(String(a.id).split('-').pop()) || 0)
  })

  recentActivities.value = activities.slice(0, 15)
}

onMounted(() => {
  computeGreeting()
  load()
})
</script>

<style scoped>
.dashboard {
  display: grid;
  gap: 14px;
}

/* ---------- 问候条 ---------- */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 18px 20px;
  border-radius: 12px;
  background: linear-gradient(120deg, #eaf3ff 0%, var(--tp-bg-card) 60%);
  border: 1px solid var(--tp-border);
}
.hero-text {
  min-width: 220px;
}
.hello {
  font-size: 20px;
  font-weight: 700;
  color: var(--tp-text);
  letter-spacing: 0.3px;
}
.sub {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--tp-text-muted);
}
.date {
  margin-top: 6px;
  font-size: 12px;
  color: var(--tp-text-faint);
  font-variant-numeric: tabular-nums;
}
.hero-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* ---------- KPI 行 ---------- */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}
@media (max-width: 1400px) {
  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 768px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.kpi {
  padding: 14px 16px;
  border-radius: 10px;
  background: var(--tp-bg-card);
  border: 1px solid var(--tp-border);
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}
.kpi:hover {
  border-color: var(--tp-border-strong);
  box-shadow: var(--tp-shadow-hover);
  transform: translateY(-1px);
}
.kpi-top {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--tp-text-muted);
}
.kpi-icon {
  font-size: 14px;
  color: var(--tp-primary);
}
.kpi-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 700;
  color: var(--tp-text);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.kpi-unit {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--tp-text-faint);
}
.kpi-foot {
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--tp-text-faint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kpi.tone-blue .kpi-value {
  color: #2563eb;
}
.kpi.tone-green .kpi-value {
  color: #059669;
}
.kpi.tone-orange .kpi-value {
  color: #d97706;
}

/* ---------- 主体双列 ---------- */
.main-row {
  row-gap: 14px;
}
.panel {
  border-radius: 10px;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tp-text);
}
.alerts-panel {
  margin-top: 14px;
}
.alert-item {
  margin-bottom: 8px;
}
.alert-item:last-child {
  margin-bottom: 0;
}

/* ---------- 动态流 ---------- */
.dashboard :deep(.el-timeline) {
  padding-left: 2px;
}
.dashboard :deep(.el-timeline-item__timestamp) {
  font-size: 11.5px;
  color: var(--tp-text-faint);
}
.activity {
  padding: 6px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}
.activity:hover {
  background: var(--tp-bg-subtle);
}
.activity-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--tp-text);
}
.activity-icon {
  font-size: 15px;
}
.activity-desc {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--tp-text-muted);
  line-height: 1.55;
  word-break: break-word;
}
.activity-meta {
  margin-top: 6px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
/* 最近动态：固定高度 + 内部滚动，条数再多也不拉长页面 */
.timeline-scroll {
  height: 380px;
  overflow-y: auto;
  padding-right: 6px;
  /* 预留滚动条宽度，滚动出现时内容不抖 */
  scrollbar-gutter: stable;
}
.timeline-scroll::-webkit-scrollbar {
  width: 6px;
}
.timeline-scroll::-webkit-scrollbar-thumb {
  background: #c8d8ea;
  border-radius: 3px;
}
.timeline-scroll::-webkit-scrollbar-thumb:hover {
  background: #a9c9ec;
}
.timeline-scroll::-webkit-scrollbar-track {
  background: transparent;
}
.dashboard :deep(.el-timeline-item) {
  padding-bottom: 14px;
}
.dashboard :deep(.el-timeline-item__node) {
  width: 8px;
  height: 8px;
}
.skeleton-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

/* ---------- 快捷入口 ---------- */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.quick-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 9px;
  border: 1px solid var(--tp-border);
  background: var(--tp-bg-card);
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}
.quick-item:hover {
  border-color: var(--tp-border-strong);
  box-shadow: var(--tp-shadow-card);
  transform: translateY(-1px);
}
.quick-icon {
  font-size: 18px;
  color: var(--tp-primary);
  margin-top: 2px;
}
.quick-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--tp-text);
}
.quick-desc {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--tp-text-faint);
}
</style>
