<!--
  我的概览（用户工作台）。

  数据全部来自 /dashboard/me，个人口径：
    · 我负责的团队数  = team_member 中 team_role=leader 的条数
    · 我参与的团队数  = team_member 中 status=active 的条数
    · 我的日程/日志   = creator_id = 当前登录人
  身份一律取自 JWT，接口不接受 userId 参数，避免越权查看他人概览。
-->
<template>
  <div class="overview-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">我的概览</h2>
        <p class="page-subtitle">个人视角：我负责的团队、我的日程与近期活动</p>
      </div>
      <el-select v-model="days" size="small" style="width: 120px" @change="load">
        <el-option :value="7" label="近 7 天" />
        <el-option :value="14" label="近 14 天" />
        <el-option :value="30" label="近 30 天" />
      </el-select>
    </div>

    <!-- 关键指标 -->
    <el-row :gutter="14" class="kpi-row">
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="kpi-card accent-blue">
          <div class="kpi-title">我负责的团队</div>
          <div class="kpi-num">{{ stats.ledTeamCount }}</div>
          <div class="kpi-unit">个</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-title">我参与的团队</div>
          <div class="kpi-num">{{ stats.myTeamCount }}</div>
          <div class="kpi-unit">个</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="kpi-card accent-green">
          <div class="kpi-title">我的日程</div>
          <div class="kpi-num">{{ stats.totalSchedules }}</div>
          <div class="kpi-unit">条</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="kpi-card accent-orange">
          <div class="kpi-title">我的日志</div>
          <div class="kpi-num">{{ stats.totalWorklogs }}</div>
          <div class="kpi-unit">篇</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14">
      <!-- 日志趋势 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="panel">
          <template #header><span class="panel-title">近 {{ days }} 天日志趋势</span></template>
          <div v-if="trend.length" class="bars">
            <div v-for="(p, i) in trend" :key="i" class="bar">
              <div class="bar-value">{{ p.worklogs }}</div>
              <div class="bar-col" :style="{ height: Math.max(4, p.worklogs * scale) + 'px' }"></div>
              <div class="bar-label">{{ String(p.date || '').slice(5) }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 日程状态分布 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="panel">
          <template #header><span class="panel-title">我的日程状态分布</span></template>
          <div class="status-grid">
            <div class="status-item">
              <div class="status-num">{{ scheduleStatus.todo }}</div>
              <div class="status-label">待办</div>
            </div>
            <div class="status-item warn">
              <div class="status-num">{{ scheduleStatus.doing }}</div>
              <div class="status-label">进行中</div>
            </div>
            <div class="status-item ok">
              <div class="status-num">{{ scheduleStatus.done }}</div>
              <div class="status-label">已完成</div>
            </div>
            <div class="status-item muted">
              <div class="status-num">{{ scheduleStatus.canceled }}</div>
              <div class="status-label">已取消</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 我负责的团队 -->
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">我负责的团队（{{ ledTeams.length }}）</span>
          <el-button link @click="$router.push('/u/teams')">查看全部团队</el-button>
        </div>
      </template>
      <el-table :data="ledTeams" size="small" empty-text="我目前不是任何团队的负责人">
        <el-table-column prop="teamId" label="#" width="70" />
        <el-table-column prop="teamName" label="团队名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="memberCount" label="成员数" width="90" />
        <el-table-column prop="leaderName" label="负责人" width="120" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-row :gutter="14">
      <!-- 最近工作日志 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="panel">
          <template #header><span class="panel-title">最近工作日志</span></template>
          <el-table :data="recentWorklogs" size="small" empty-text="还没有写过工作日志">
            <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
            <el-table-column prop="logDate" label="日志日期" width="110" />
            <el-table-column label="创建时间" width="160">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 即将开始的日程 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="panel">
          <template #header><span class="panel-title">即将开始的日程</span></template>
          <el-table :data="upcomingSchedules" size="small" empty-text="近期没有待开始的日程">
            <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="90">
              <template #default="{ row }">{{ priorityText(row.priority) }}</template>
            </el-table-column>
            <el-table-column label="开始时间" width="160">
              <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dashboardApi, teamApi } from '@/api'

const loading = ref(false)
const days = ref(7)

const stats = reactive({
  ledTeamCount: 0,
  myTeamCount: 0,
  totalSchedules: 0,
  totalWorklogs: 0,
})
const scheduleStatus = reactive({ todo: 0, doing: 0, done: 0, canceled: 0 })
const trend = ref([])
const teams = ref([])
const recentWorklogs = ref([])
const upcomingSchedules = ref([])

/** 我负责的团队：优先用 myRole 过滤；接口没给 myRole 时退化为「我参与的团队」 */
const ledTeams = computed(() => {
  const list = teams.value || []
  const hasRole = list.some((t) => t && t.myRole)
  return hasRole ? list.filter((t) => t.myRole === 'leader') : list
})

// 柱状图高度基准：最高柱 90px
const maxVal = computed(() => Math.max(1, ...trend.value.map((t) => Number(t.worklogs || 0))))
const scale = computed(() => 90 / maxVal.value)

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function statusText(status) {
  return { todo: '待办', doing: '进行中', done: '已完成', canceled: '已取消' }[status] || status || '-'
}

function statusType(status) {
  return { todo: 'info', doing: 'warning', done: 'success', canceled: 'danger' }[status] || 'info'
}

function priorityText(priority) {
  return { low: '低', medium: '中', high: '高' }[priority] || priority || '-'
}

async function load() {
  loading.value = true
  try {
    const data = await dashboardApi.me({ days: days.value })

    stats.ledTeamCount = Number(data?.ledTeamCount || 0)
    stats.myTeamCount = Number(data?.myTeamCount || 0)
    stats.totalSchedules = Number(data?.totalSchedules || 0)
    stats.totalWorklogs = Number(data?.totalWorklogs || 0)

    scheduleStatus.todo = Number(data?.todoCount || 0)
    scheduleStatus.doing = Number(data?.doingCount || 0)
    scheduleStatus.done = Number(data?.doneCount || 0)
    scheduleStatus.canceled = Number(data?.canceledCount || 0)

    trend.value = (data?.recentDays || []).map((p) => ({
      date: p.date,
      worklogs: Number(p.worklogCreated || 0),
    }))
    recentWorklogs.value = data?.recentWorklogs || []
    upcomingSchedules.value = data?.upcomingSchedules || []

    // 团队列表走 /team/my（不分页数组），用于「我负责的团队」表格
    try {
      teams.value = (await teamApi.my()) || []
    } catch {
      teams.value = []
    }
  } catch (e) {
    ElMessage.error('加载失败：' + (e?.message || ''))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.overview-page {
  display: grid;
  gap: 14px;
}
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}
.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 650;
  color: #0f172a;
}
.page-subtitle {
  margin: 5px 0 0;
  font-size: 12.5px;
  color: #64748b;
}

.kpi-row {
  row-gap: 14px;
}
.kpi-card {
  border-radius: 10px;
  border: 1px solid var(--tp-border);
  position: relative;
}
.kpi-card :deep(.el-card__body) {
  padding: 16px 18px;
}
.kpi-title {
  font-size: 12px;
  color: #64748b;
}
.kpi-num {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 650;
  line-height: 1.1;
  color: #0f172a;
}
.kpi-unit {
  position: absolute;
  right: 16px;
  bottom: 14px;
  font-size: 11px;
  color: #94a3b8;
}
.kpi-card.accent-blue .kpi-num {
  color: #2563eb;
}
.kpi-card.accent-green .kpi-num {
  color: #059669;
}
.kpi-card.accent-orange .kpi-num {
  color: #d97706;
}

.panel {
  border-radius: 10px;
  border: 1px solid var(--tp-border);
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

/* 柱状图（纯 CSS，不引图表库：这里只有一条序列，用 ECharts 反而重） */
.bars {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 150px;
  padding: 0 4px;
}
.bar {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  height: 100%;
}
.bar-value {
  font-size: 11px;
  color: #64748b;
}
.bar-col {
  width: 60%;
  max-width: 26px;
  border-radius: 4px 4px 0 0;
  background: linear-gradient(180deg, #60a5fa, #2563eb);
  transition: height 0.25s ease;
}
.bar-label {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.status-item {
  padding: 14px 8px;
  border-radius: 9px;
  background: var(--tp-bg-subtle);
  text-align: center;
}
.status-num {
  font-size: 22px;
  font-weight: 650;
  color: #0f172a;
}
.status-label {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}
.status-item.warn .status-num {
  color: #d97706;
}
.status-item.ok .status-num {
  color: #059669;
}
.status-item.muted .status-num {
  color: #94a3b8;
}
</style>
