<!--
  考勤实时看板。

  数据刷新策略：
    - 指标卡与趋势图：进入页面加载一次，可手动刷新
    - 实时打卡流：每 5 秒轮询 /attendance/recent（该接口 silent 模式，失败不弹提示，避免断网时刷屏）
  之所以用轮询而不是 WebSocket：4C4G 服务器上长连接的开销与运维复杂度不成正比，
  5 秒一次的轻量 GET 完全够用；若后续要做秒级推送，把 recent 换成 SSE 即可，页面改动很小。
-->
<template>
  <div class="attendance-dashboard">
    <!-- 指标卡 -->
    <el-row :gutter="14" class="metric-row">
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="never" class="metric-card">
          <div class="metric-label">应出勤</div>
          <div class="metric-value">{{ overview.totalEmployees }}</div>
          <div class="metric-unit">人</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="never" class="metric-card success">
          <div class="metric-label">已打卡</div>
          <div class="metric-value">{{ overview.checkedIn }}</div>
          <div class="metric-unit">人</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="never" class="metric-card warning">
          <div class="metric-label">迟到</div>
          <div class="metric-value">{{ overview.lateCount }}</div>
          <div class="metric-unit">人</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="never" class="metric-card danger">
          <div class="metric-label">缺勤</div>
          <div class="metric-value">{{ overview.absentCount }}</div>
          <div class="metric-unit">人</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="never" class="metric-card">
          <div class="metric-label">设备在线</div>
          <div class="metric-value">{{ overview.onlineDevices }}/{{ overview.totalDevices }}</div>
          <div class="metric-unit">台</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="never" class="metric-card">
          <div class="metric-label">最近打卡</div>
          <div class="metric-value small">{{ lastPunchText }}</div>
          <div class="metric-unit">时间</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14">
      <!-- 实时打卡流 -->
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-header">
              <div class="panel-title">
                <span class="live-dot" :class="{ active: polling }"></span>
                实时打卡流
                <span class="panel-sub">每 5 秒自动刷新</span>
              </div>
              <div class="panel-actions">
                <el-button link :loading="loadingRecent" @click="loadRecent">立即刷新</el-button>
                <el-switch v-model="polling" active-text="轮询" inline-prompt />
              </div>
            </div>
          </template>

          <el-table :data="recent" size="small" height="420" empty-text="暂无打卡数据">
            <el-table-column prop="punchTime" label="时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.punchTime) }}</template>
            </el-table-column>
            <el-table-column prop="employeeNo" label="工号" width="90" />
            <el-table-column label="姓名" min-width="100">
              <template #default="{ row }">{{ row.userName || '未注册工号' }}</template>
            </el-table-column>
            <el-table-column label="类型" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.punchType === 'CHECK_IN' ? 'primary' : 'info'" effect="plain">
                  {{ punchTypeText(row.punchType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="判定" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="punchStatusType(row.status)">{{ punchStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="设备" min-width="120">
              <template #default="{ row }">
                <div class="device-cell">{{ row.deviceLocation || row.deviceSn }}</div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 趋势 -->
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-header">
              <div class="panel-title">近 7 天考勤趋势</div>
              <el-button link @click="loadTrend">刷新</el-button>
            </div>
          </template>
          <div ref="trendRef" class="chart"></div>
        </el-card>

        <el-card shadow="never" class="panel rule-panel">
          <template #header>
            <div class="panel-header">
              <div class="panel-title">当前考勤规则</div>
              <el-button link @click="$router.push('/attendance/rule')">修改</el-button>
            </div>
          </template>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="上班时间">{{ rule.workStart || '-' }}</el-descriptions-item>
            <el-descriptions-item label="下班时间">{{ rule.workEnd || '-' }}</el-descriptions-item>
            <el-descriptions-item label="迟到宽限">{{ rule.lateGraceMinutes ?? '-' }} 分钟</el-descriptions-item>
            <el-descriptions-item label="早退宽限">{{ rule.earlyGraceMinutes ?? '-' }} 分钟</el-descriptions-item>
            <el-descriptions-item label="工作日" :span="2">{{ workDaysText }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { attendanceApi } from '@/api'
import {
  formatDateTime,
  punchStatusText,
  punchStatusType,
  punchTypeText,
} from '@/utils/attendance'

const overview = reactive({
  totalEmployees: 0,
  checkedIn: 0,
  lateCount: 0,
  absentCount: 0,
  onlineDevices: 0,
  totalDevices: 0,
  lastPunchTime: null,
})
const recent = ref([])
const rule = ref({})
const trend = ref([])
const loadingRecent = ref(false)
const polling = ref(true)

const trendRef = ref(null)
let chart = null
let timer = null

const lastPunchText = computed(() =>
  overview.lastPunchTime ? formatDateTime(overview.lastPunchTime).slice(11, 16) : '--:--',
)

const workDaysText = computed(() => {
  const names = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const days = String(rule.value.workDays || '')
    .split(',')
    .map((d) => d.trim())
    .filter(Boolean)
    .map((d) => names[Number(d) - 1])
    .filter(Boolean)
  return days.length ? days.join('、') : '-'
})

async function loadOverview() {
  try {
    const data = await attendanceApi.overview()
    Object.assign(overview, data || {})
  } catch {
    /* 错误提示已由 http 层统一处理 */
  }
}

async function loadRecent() {
  loadingRecent.value = true
  try {
    const data = await attendanceApi.recent(20)
    recent.value = Array.isArray(data) ? data : []
  } catch {
    // silent：轮询失败时保持上一次数据，不打断用户
  } finally {
    loadingRecent.value = false
  }
}

async function loadRule() {
  try {
    rule.value = (await attendanceApi.getRule()) || {}
  } catch {
    rule.value = {}
  }
}

async function loadTrend() {
  try {
    const data = await attendanceApi.trend()
    trend.value = Array.isArray(data) ? data : []
    renderTrend()
  } catch {
    trend.value = []
  }
}

function renderTrend() {
  if (!trendRef.value) return
  if (!chart) {
    chart = echarts.init(trendRef.value)
  }
  const dates = trend.value.map((d) => String(d.workDate || '').slice(5))
  const late = trend.value.map((d) => d.lateCount || 0)
  const absent = trend.value.map((d) => d.absentCount || 0)
  const normal = trend.value.map((d) => d.normalCount || 0)

  chart.setOption(
    {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
      grid: { top: 22, left: 8, right: 12, bottom: 42, containLabel: true },
      xAxis: {
        type: 'category',
        data: dates,
        axisLine: { lineStyle: { color: '#e2e8f0' } },
        axisLabel: { color: '#64748b', fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        splitLine: { lineStyle: { color: '#f1f5f9' } },
        axisLabel: { color: '#64748b', fontSize: 11 },
      },
      series: [
        { name: '正常', type: 'bar', stack: 'total', data: normal, itemStyle: { color: '#10b981' }, barWidth: 18 },
        { name: '迟到', type: 'bar', stack: 'total', data: late, itemStyle: { color: '#f59e0b' } },
        { name: '缺勤', type: 'bar', stack: 'total', data: absent, itemStyle: { color: '#ef4444' } },
      ],
    },
    true,
  )
}

function startPolling() {
  stopPolling()
  timer = window.setInterval(loadRecent, 5000)
}

function stopPolling() {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
}

watch(polling, (enabled) => {
  if (enabled) {
    startPolling()
    ElMessage.success('已开启实时轮询')
  } else {
    stopPolling()
  }
})

function handleResize() {
  chart?.resize()
}

onMounted(async () => {
  await Promise.all([loadOverview(), loadRecent(), loadRule(), loadTrend()])
  startPolling()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  stopPolling()
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.attendance-dashboard {
  display: grid;
  gap: 14px;
}
.metric-row {
  row-gap: 14px;
}
.metric-card {
  border-radius: 10px;
  border: 1px solid var(--tp-border);
}
.metric-card :deep(.el-card__body) {
  padding: 14px 16px;
  position: relative;
}
.metric-label {
  font-size: 12px;
  color: #64748b;
}
.metric-value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 650;
  line-height: 1.1;
  color: #0f172a;
}
.metric-value.small {
  font-size: 20px;
}
.metric-unit {
  position: absolute;
  right: 14px;
  bottom: 12px;
  font-size: 11px;
  color: #94a3b8;
}
.metric-card.success .metric-value {
  color: #059669;
}
.metric-card.warning .metric-value {
  color: #d97706;
}
.metric-card.danger .metric-value {
  color: #dc2626;
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
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}
.panel-sub {
  font-size: 11.5px;
  font-weight: 400;
  color: #94a3b8;
}
.panel-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #cbd5e1;
}
.live-dot.active {
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.2);
  animation: pulse 1.6s ease-in-out infinite;
}
@keyframes pulse {
  50% {
    opacity: 0.35;
  }
}
.chart {
  height: 300px;
}
.device-cell {
  font-size: 12.5px;
  color: #475569;
}
.rule-panel {
  margin-top: 14px;
}
</style>
