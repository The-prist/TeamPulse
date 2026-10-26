<template>
  <div class="admin-page">
    <div class="page-header">
      <h1 class="page-title">公司概览</h1>
      <p class="page-subtitle">管理员视角：全公司团队与近期活动</p>
    </div>

    <!-- 打卡分析：把考勤数据变成「结论 + 可执行的改进提醒」 -->
    <el-card class="content-card analysis-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">打卡分析（近 {{ days }} 天）</span>
          <el-tag v-if="attendanceAnalysis.total" size="small" type="info" effect="plain">
            样本 {{ attendanceAnalysis.total }} 人次
          </el-tag>
        </div>
      </template>

      <el-row :gutter="12">
        <el-col :xs="24" :md="10">
          <div class="analysis-stats">
            <div class="as-item">
              <div class="as-num">{{ pct(attendanceAnalysis.attendanceRate) }}</div>
              <div class="as-label">出勤率</div>
            </div>
            <div class="as-item warn">
              <div class="as-num">{{ pct(attendanceAnalysis.lateRate) }}</div>
              <div class="as-label">迟到率</div>
            </div>
            <div class="as-item danger">
              <div class="as-num">{{ pct(attendanceAnalysis.absentRate) }}</div>
              <div class="as-label">缺勤率</div>
            </div>
            <div class="as-item">
              <div class="as-num">{{ minutesText(attendanceAnalysis.avgWorkMinutes) }}</div>
              <div class="as-label">平均在岗</div>
            </div>
          </div>

          <div class="mini-title">异常排行（滚动查看）</div>
          <el-table :data="analysisRanking" size="small" max-height="150" empty-text="本期没有迟到或缺勤">
            <el-table-column prop="name" label="员工" min-width="90" />
            <el-table-column prop="late" label="迟到" width="70" />
            <el-table-column prop="absent" label="缺勤" width="70" />
            <el-table-column prop="avgWork" label="平均在岗" width="100" />
          </el-table>
        </el-col>

        <el-col :xs="24" :md="14">
          <div class="mini-title">改进提醒（{{ attendanceAnalysis.tips.length }}）</div>
          <div class="tips-list">
            <div
              v-for="(t, i) in attendanceAnalysis.tips"
              :key="i"
              class="tip-item"
              :class="'tip-' + t.type"
            >
              <el-icon class="tip-icon">
                <component :is="t.icon" />
              </el-icon>
              <div class="tip-body">
                <div class="tip-title">{{ t.title }}</div>
                <div class="tip-desc">{{ t.desc }}</div>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card class="content-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">关键指标（近 {{ days }} 天）</span>
          <div class="header-actions">
            <el-select v-model="days" size="small" style="width: 120px" @change="load">
              <el-option :value="7" label="近7天" />
              <el-option :value="14" label="近14天" />
              <el-option :value="30" label="近30天" />
            </el-select>
          </div>
        </div>
      </template>

      <el-row :gutter="12">
        <el-col :xs="24" :sm="12" :md="8">
          <el-card shadow="hover" class="kpi-card"><div class="kpi-num">{{ stats.teamCount }}</div><div class="kpi-title">团队数量</div></el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="8">
          <el-card shadow="hover" class="kpi-card"><div class="kpi-num">{{ stats.scheduleCount }}</div><div class="kpi-title">日程数量</div></el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="8">
          <el-card shadow="hover" class="kpi-card"><div class="kpi-num">{{ stats.worklogCount }}</div><div class="kpi-title">工作日志（总计）</div></el-card>
        </el-col>
      </el-row>

      <div class="trend-wrap">
        <div class="trend-title">近期工作日志趋势</div>
        <div class="bars">
          <div v-for="(p,i) in trend" :key="i" class="bar">
            <div class="bar-col" :style="{ height: Math.max(6, (p.worklogs || 0) * scale) + 'px' }" />
            <div class="bar-label">{{ p.date }}</div>
          </div>
        </div>
      </div>

      <div class="teams-wrap">
        <div class="teams-title">团队列表（{{ teams.length }}）</div>
        <el-table :data="teams" size="small" v-loading="loading" max-height="240">
          <el-table-column prop="teamId" label="#" width="80" />
          <el-table-column prop="teamName" label="团队名称" />
          <el-table-column prop="description" label="描述" />
          <el-table-column prop="createdAt" label="创建时间" width="180" />
        </el-table>
      </div>

      <el-divider />
      
      <!-- 公司看板展示区域 -->
      <div class="company-kanban-section">
        <el-card class="kanban-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">公司看板概览</span>
              <el-button type="primary" size="small" :icon="Refresh" @click="refreshKanbanData">刷新</el-button>
            </div>
          </template>
          <div class="kanban-stats">
            <div class="kanban-stat-item">
              <div class="stat-icon total">
                <el-icon><Document /></el-icon>
              </div>
              <div class="stat-content">
                <div class="stat-value">{{ companyKanbanData.totalTasks }}</div>
                <div class="stat-label">总任务数</div>
              </div>
            </div>
            <div class="kanban-stat-item">
              <div class="stat-icon completed">
                <el-icon><CircleCheck /></el-icon>
              </div>
              <div class="stat-content">
                <div class="stat-value">{{ companyKanbanData.completedTasks }}</div>
                <div class="stat-label">已完成</div>
              </div>
            </div>
            <div class="kanban-stat-item">
              <div class="stat-icon progress">
                <el-icon><Clock /></el-icon>
              </div>
              <div class="stat-content">
                <div class="stat-value">{{ companyKanbanData.inProgressTasks }}</div>
                <div class="stat-label">进行中</div>
              </div>
            </div>
            <div class="kanban-stat-item">
              <div class="stat-icon overdue">
                <el-icon><Warning /></el-icon>
              </div>
              <div class="stat-content">
                <div class="stat-value">{{ companyKanbanData.overdueTasks }}</div>
                <div class="stat-label">逾期任务</div>
              </div>
            </div>
          </div>
        </el-card>
      </div>
      
      <!-- 图表分析区域 -->
      <div class="charts-section">
        <div class="charts-grid">
          <el-card class="chart-card" shadow="never">
            <template #header>
              <span class="card-title">任务指派分布</span>
            </template>
            <div ref="assigneePieChart" class="pie-chart"></div>
          </el-card>
          <el-card class="chart-card" shadow="never">
            <template #header>
              <span class="card-title">近 {{ days }} 天任务趋势</span>
            </template>
            <div ref="completionLineChart" class="line-chart"></div>
          </el-card>
        </div>
      </div>
      
      <div class="detail-sections">
        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="sub-card">
              <template #header><span class="sub-title">日程状态分布</span></template>
              <el-descriptions :column="2" size="small" border>
                <el-descriptions-item label="待办">{{ scheduleStatus.todo ?? 0 }}</el-descriptions-item>
                <el-descriptions-item label="进行中">{{ scheduleStatus.doing ?? 0 }}</el-descriptions-item>
                <el-descriptions-item label="已完成">{{ scheduleStatus.done ?? 0 }}</el-descriptions-item>
                <el-descriptions-item label="已取消">{{ scheduleStatus.canceled ?? 0 }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="sub-card">
              <template #header><span class="sub-title">Top 团队（按日程数）</span></template>
              <el-table :data="topTeams" size="small" empty-text="暂无团队日程" max-height="240">
                <el-table-column prop="teamId" label="#" width="80" />
                <el-table-column prop="teamName" label="团队" />
                <el-table-column prop="schedules" label="日程数" width="100" />
              </el-table>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="12" style="margin-top: 12px">
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="sub-card">
              <template #header><span class="sub-title">Top 用户（近{{ days }}天按日志数）</span></template>
              <el-table :data="topUsers" size="small" max-height="240">
                <el-table-column prop="userId" label="#" width="80" />
                <el-table-column prop="name" label="用户" />
                <el-table-column prop="worklogs" label="日志数" width="100" />
              </el-table>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="sub-card">
              <template #header><span class="sub-title">所有团队日程</span></template>
              <el-table :data="allTeamSchedules" size="small" empty-text="暂无团队日程" max-height="240">
                <el-table-column prop="scheduleId" label="#" width="80" />
                <el-table-column prop="title" label="日程标题" />
                <el-table-column prop="teamName" label="所属团队" width="120" />
                <el-table-column prop="status" label="状态" width="80">
                  <template #default="{ row }">
                    <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusText(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="startTime" label="开始时间" width="160">
                  <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="12" style="margin-top: 12px">
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="sub-card">
              <template #header><span class="sub-title">即将开始的日程（未来{{ days }}天）</span></template>
              <el-table :data="upcomingSchedules" size="small" max-height="240">
                <el-table-column prop="scheduleId" label="#" width="80" />
                <el-table-column prop="title" label="标题" />
                <el-table-column prop="status" label="状态" width="90" :formatter="(row) => scheduleStatusText(row.status)" />
                <el-table-column prop="priority" label="优先级" width="90" :formatter="(row) => priorityText(row.priority)" />
                <el-table-column prop="startTime" label="开始时间" width="160" />
              </el-table>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="sub-card">
              <template #header><span class="sub-title">团队明细（近{{ days }}天日志）</span></template>
              <el-table :data="teamDetails" size="small" empty-text="暂无团队数据" max-height="240">
                <el-table-column prop="teamId" label="#" width="80" />
                <el-table-column prop="teamName" label="团队" />
                <el-table-column prop="members" label="成员数" width="90" />
                <el-table-column prop="schedules" label="日程数" width="90" />
                <el-table-column prop="worklogs" label="日志数" width="90" />
              </el-table>
            </el-card>
          </el-col>
        </el-row>
        
        <!-- 全公司任务更新展示 -->
        <el-row :gutter="12" style="margin-top: 12px;">
          <el-col :span="24">
            <el-card shadow="never" class="sub-card">
              <template #header><span class="sub-title">最近任务更新</span></template>
              <div class="task-updates-list">
                <div v-if="companyTaskUpdates.length === 0" class="empty-state">
                  <el-empty description="暂无任务更新记录" :image-size="80" />
                </div>
                <div v-else class="updates-container">
                  <div v-for="update in companyTaskUpdates" :key="update.id" class="update-item">
                    <div class="update-avatar">
                      <el-avatar :size="32" :src="update.user?.avatar">
                        {{ update.user?.name?.charAt(0) || 'U' }}
                      </el-avatar>
                    </div>
                    <div class="update-content">
                      <div class="update-header">
                        <span class="user-name">{{ update.user?.name || '未知用户' }}</span>
                        <span class="update-action">{{ update.action }}</span>
                        <span class="task-title">{{ update.taskTitle }}</span>
                      </div>
                      <div class="update-meta">
                        <span class="team-name">{{ update.teamName }}</span>
                        <span class="update-time">{{ formatDateTime(update.timestamp) }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, CircleCheck, Clock, Warning, Refresh } from '@element-plus/icons-vue'
import { dashboardApi, scheduleApi, teamApi, worklogApi, attendanceApi } from '@/api'
import { scheduleStatusText, priorityText } from '@/utils/dict'
import * as echarts from 'echarts'
import { InfoFilled, CircleCheckFilled } from '@element-plus/icons-vue'

const loading = ref(false)
const days = ref(7)
const stats = ref({ teamCount: 0, scheduleCount: 0, worklogCount: 0 })
const trend = ref([])
/** 原样保留 /dashboard/admin 的 recentDays，供趋势图取多列真实数据 */
const dailyPoints = ref([])
const teams = ref([])
const scheduleStatus = ref({})
const topTeams = ref([])
const topUsers = ref([])
const recentWorklogs = ref([])
const allTeamSchedules = ref([])
const upcomingSchedules = ref([])
const teamDetails = ref([])
const companyKanbanData = ref({
  totalTasks: 0,
  completedTasks: 0,
  inProgressTasks: 0,
  overdueTasks: 0
})
const companyTaskUpdates = ref([])

/**
 * 打卡分析结果。
 * 数据源是 /attendance/calendar（每人每天的最终判定），在前端聚合成
 * 「出勤率 / 迟到率 / 缺勤率 / 平均在岗」+ 一份可执行的改进提醒。
 */
const attendanceAnalysis = ref({
  total: 0,
  normal: 0,
  late: 0,
  early: 0,
  absent: 0,
  attendanceRate: 0,
  lateRate: 0,
  absentRate: 0,
  avgWorkMinutes: 0,
  onlineDevices: 0,
  totalDevices: 0,
  ranking: [],
  tips: [],
})

/** 异常排行：迟到权重 2、缺勤权重 3，只保留有异常的员工 */
const analysisRanking = computed(() => attendanceAnalysis.value.ranking)
const assigneePieChart = ref(null)
const completionLineChart = ref(null)

const maxVal = computed(()=> Math.max(1, ...trend.value.map(t=>Number(t.worklogs||0))))
const scale = computed(()=> 80 / maxVal.value) // 最高条80px

// 看板指标：新接口只给 todo/doing/done 三个计数，没有逾期与团队维度统计
function buildKanbanData(data) {
  const todo = Number(data?.todoCount || 0)
  const doing = Number(data?.doingCount || 0)
  const done = Number(data?.doneCount || 0)
  return {
    totalTasks: todo + doing + done,
    completedTasks: done,
    inProgressTasks: doing,
    overdueTasks: todo, // 旧接口把「待办」当作逾期口径，新接口无逾期统计
    teamStats: [],
    statusCounts: { todo, doing, done }
  }
}

// 趋势点 → 「最近任务更新」列表项（/dashboard/trend 只返回每日汇总）
function toTaskUpdates(points) {
  return (Array.isArray(points) ? points : [])
    .map((p) => {
      const created = Number(p.scheduleCreated || 0)
      const done = Number(p.scheduleDone || 0)
      const worklogs = Number(p.worklogCreated || 0)
      return {
        id: p.date,
        user: { name: '全员' },
        action: '当日动态',
        taskTitle: `新增日程 ${created} · 完成日程 ${done} · 新提交日志 ${worklogs}`,
        teamName: '',
        timestamp: p.date,
        // 当天没有任何活动就不占一行，否则列表会被一串 0 撑满
        __activity: created + done + worklogs,
      }
    })
    .filter((x) => x.__activity > 0)
    // 新日期在上（后端按日期正序返回，这里倒过来）
    .sort((a, b) => String(b.timestamp).localeCompare(String(a.timestamp)))
}

/** 百分比显示：0.1534 → 15.3% */
function pct(rate) {
  return (Number(rate || 0) * 100).toFixed(1) + '%'
}

/** 分钟 → 8h20m */
function minutesText(m) {
  const n = Number(m || 0)
  if (n <= 0) return '—'
  const h = Math.floor(n / 60)
  const r = n % 60
  return r === 0 ? `${h}h` : `${h}h${r}m`
}

/**
 * 打卡分析：用 /attendance/calendar 的「每人每天」结果做聚合，
 * 再按几条阈值规则生成改进提醒。
 *
 * 阈值集中写在这里（迟到率 15%、在岗 8 小时），方便按公司实际情况调整：
 * 迟到率 = 迟到人次 / 应出勤人次，缺勤率 = 缺勤人次 / 应出勤人次。
 */
async function loadAttendanceAnalysis(from, to) {
  const base = {
    total: 0,
    normal: 0,
    late: 0,
    early: 0,
    absent: 0,
    attendanceRate: 0,
    lateRate: 0,
    absentRate: 0,
    avgWorkMinutes: 0,
    onlineDevices: 0,
    totalDevices: 0,
    ranking: [],
    tips: [],
  }

  let rows = []
  try {
    const data = await attendanceApi.calendar({ from, to })
    rows = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('获取考勤数据失败:', e)
  }

  // 设备在线情况（独立接口，失败不影响考勤统计）
  try {
    const ov = await attendanceApi.overview()
    base.onlineDevices = Number(ov?.onlineDevices || 0)
    base.totalDevices = Number(ov?.totalDevices || 0)
  } catch {
    /* 拿不到设备状态就按 0 处理，提醒里会提示离线 */
  }

  const perUser = new Map()
  let workMinutes = 0
  for (const r of rows) {
    const key = r.userId ?? r.userName
    if (!perUser.has(key)) {
      perUser.set(key, { name: r.userName || '未知员工', late: 0, absent: 0, workMinutes: 0, attended: 0 })
    }
    const u = perUser.get(key)
    const st = r.attendanceStatus
    if (st === 'ABSENT') {
      base.absent++
      u.absent++
      continue
    }
    const mins = Number(r.workMinutes || 0)
    workMinutes += mins
    u.workMinutes += mins
    u.attended++
    if (st === 'LATE' || st === 'LATE_AND_EARLY') {
      base.late++
      u.late++
    }
    if (st === 'EARLY_LEAVE' || st === 'LATE_AND_EARLY') base.early++
    if (st === 'NORMAL') base.normal++
  }

  const total = rows.length
  const attended = total - base.absent
  base.total = total
  base.attendanceRate = total ? attended / total : 0
  base.lateRate = total ? base.late / total : 0
  base.absentRate = total ? base.absent / total : 0
  base.avgWorkMinutes = attended ? Math.round(workMinutes / attended) : 0
  base.ranking = [...perUser.values()]
    .filter((u) => u.late > 0 || u.absent > 0)
    .map((u) => ({
      name: u.name,
      late: u.late,
      absent: u.absent,
      avgWork: u.attended ? minutesText(Math.round(u.workMinutes / u.attended)) : '—',
      __score: u.late * 2 + u.absent * 3,
    }))
    .sort((a, b) => b.__score - a.__score)
    .slice(0, 10)

  // ---------------- 改进提醒 ----------------
  const tips = []
  if (total === 0) {
    tips.push({
      type: 'info',
      icon: InfoFilled,
      title: '本期暂无考勤数据',
      desc: `近 ${days.value} 天没有日汇总记录。缺勤由每天 00:05 的定时任务补齐，刚导入数据的话请稍后再看。`,
    })
  } else {
    if (base.absent > 0) {
      tips.push({
        type: base.absentRate > 0.2 ? 'error' : 'warning',
        icon: Warning,
        title: `缺勤 ${base.absent} 人次（${pct(base.absentRate)}）`,
        desc: `${base.absent} 天次完全没有打卡。请核实是漏打卡还是未请假，可在「打卡记录 → 手工补卡」里补录。`,
      })
    }
    if (base.lateRate > 0.15) {
      tips.push({
        type: 'warning',
        icon: Warning,
        title: `迟到率偏高 ${pct(base.lateRate)}（${base.late} 人次）`,
        desc: '已超过 15% 的预警线。建议核对考勤规则里的上班时间与迟到宽限，或提醒常迟到的同事提前出门。',
      })
    } else if (base.late > 0) {
      tips.push({
        type: 'info',
        icon: InfoFilled,
        title: `迟到 ${base.late} 人次（${pct(base.lateRate)}）`,
        desc: '在正常波动范围内，可关注下方「异常排行」里靠前的同事。',
      })
    }
    if (base.early > 0) {
      tips.push({
        type: 'info',
        icon: InfoFilled,
        title: `早退 ${base.early} 人次`,
        desc: '签退早于下班时间。如有弹性工时，建议在「考勤规则」里调整下班时间，避免误判早退。',
      })
    }
    if (base.avgWorkMinutes > 0 && base.avgWorkMinutes < 480) {
      tips.push({
        type: 'warning',
        icon: Warning,
        title: `平均在岗 ${minutesText(base.avgWorkMinutes)}，低于 8 小时`,
        desc: '可能是下班时间设置偏早，或存在较多早退。建议先核对「考勤规则」里的上下班时间。',
      })
    }
    if (base.totalDevices > 0 && base.onlineDevices < base.totalDevices) {
      const offline = base.totalDevices - base.onlineDevices
      tips.push({
        type: 'warning',
        icon: Warning,
        title: `${offline} 台打卡机离线`,
        desc: `当前在线 ${base.onlineDevices}/${base.totalDevices} 台。离线设备无法上报打卡，建议检查电源、网络与 MQTT 连接。`,
      })
    }
    if (tips.length === 0) {
      tips.push({
        type: 'success',
        icon: CircleCheckFilled,
        title: '本期考勤表现良好',
        desc: '未发现迟到、早退或缺勤异常，设备也全部在线，保持即可。',
      })
    }
  }
  base.tips = tips
  attendanceAnalysis.value = base
}
async function load() {
  loading.value = true
  try {
    // 全公司概览：用户/团队/日程/日志/看板聚合指标 + 近 N 天趋势
    const data = await dashboardApi.admin({ days: days.value })
    stats.value = {
      teamCount: Number(data?.totalTeams || 0),
      scheduleCount: Number(data?.totalSchedules || 0),
      worklogCount: Number(data?.totalWorklogs || 0)
    }
    companyKanbanData.value = buildKanbanData(data)
    scheduleStatus.value = {
      todo: Number(data?.todoCount || 0),
      doing: Number(data?.doingCount || 0),
      done: Number(data?.doneCount || 0),
      canceled: Number(data?.canceledCount || 0)
    }
    // recentDays[].worklogCreated → 模板沿用的 trend[].worklogs
    trend.value = (data?.recentDays || []).map((p) => ({ date: p.date, worklogs: Number(p.worklogCreated || 0) }))
    dailyPoints.value = Array.isArray(data?.recentDays) ? data.recentDays : []

    // 团队列表：/team/list 分页返回，管理端一次取足够展示的条数
    try {
      const teamPage = await teamApi.page({ page: 1, size: 200 })
      teams.value = teamPage?.records || []
    } catch (e) {
      console.error('获取团队列表失败:', e)
      teams.value = []
    }

    // ---- 团队 / 用户 / 日程 / 日志 的聚合 ----
    const endDate = new Date()
    const beginDate = new Date(endDate.getTime() - (Number(days.value) - 1) * 86400000)
    const fmtDate = (d) =>
      `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

    // 1) 日志（近 N 天）：既算 Top 用户，也算每个团队的日志数
    let wlRecords = []
    try {
      const wlPage = await worklogApi.page({
        from: fmtDate(beginDate),
        to: fmtDate(endDate),
        page: 1,
        size: 200,
      })
      wlRecords = wlPage?.records || []
      const byUser = new Map()
      for (const w of wlRecords) {
        const key = w.creatorId ?? w.creatorName
        if (!byUser.has(key)) {
          byUser.set(key, {
            userId: w.creatorId,
            name: w.creatorName || '用户 #' + w.creatorId,
            worklogs: 0,
          })
        }
        byUser.get(key).worklogs += 1
      }
      topUsers.value = [...byUser.values()].sort((a, b) => b.worklogs - a.worklogs).slice(0, 8)
    } catch (e) {
      console.error('获取工作日志失败:', e)
      topUsers.value = []
    }

    // 2) 日程（取满 200 条）：既用于「所有团队日程」表，也用于按团队统计
    try {
      const schedulePage = await scheduleApi.page({ page: 1, size: 200, order: 'desc' })
      allTeamSchedules.value = schedulePage?.records || []
    } catch (e) {
      console.error('获取团队日程数据失败:', e)
      allTeamSchedules.value = []
    }

    // 3) 团队明细 + Top 团队：
    //    成员数来自 /team/list，日程数按 teamId 统计，日志数按 relatedTeamId 统计
    try {
      const schedByTeam = new Map()
      for (const s of allTeamSchedules.value) {
        if (s.teamId == null) continue
        schedByTeam.set(s.teamId, (schedByTeam.get(s.teamId) || 0) + 1)
      }
      const logsByTeam = new Map()
      for (const w of wlRecords) {
        if (w.relatedTeamId == null) continue
        logsByTeam.set(w.relatedTeamId, (logsByTeam.get(w.relatedTeamId) || 0) + 1)
      }
      teamDetails.value = (teams.value || [])
        .map((t) => ({
          teamId: t.teamId,
          teamName: t.teamName,
          members: Number(t.memberCount || 0),
          schedules: schedByTeam.get(t.teamId) || 0,
          worklogs: logsByTeam.get(t.teamId) || 0,
        }))
        .sort((a, b) => b.schedules + b.worklogs - (a.schedules + a.worklogs))

      topTeams.value = teamDetails.value
        .filter((t) => t.schedules > 0)
        .sort((a, b) => b.schedules - a.schedules)
        .slice(0, 8)
    } catch (e) {
      console.error('团队聚合失败:', e)
      teamDetails.value = []
      topTeams.value = []
    }

    // 4) 最近任务更新：新日期在上（排序在 toTaskUpdates 内完成）
    try {
      const points = await dashboardApi.trend({ days: days.value })
      companyTaskUpdates.value = toTaskUpdates(points)
    } catch (e) {
      console.error('获取任务更新记录失败:', e)
      companyTaskUpdates.value = []
    }
    // 5) 打卡分析：出勤率 / 迟到率 / 缺勤率 / 平均在岗 + 改进提醒
    await loadAttendanceAnalysis(fmtDate(beginDate), fmtDate(endDate))
    // 初始化图表
    await nextTick()
    initCharts()
  } catch (e) {
    ElMessage.error('加载失败：' + (e.message||''))
  } finally {
    loading.value = false
  }
}

// 「刷新」按钮：重新拉取 /dashboard/admin 与 /dashboard/trend
async function refreshKanbanData() {
  try {
    await load()
    ElMessage.success('数据已刷新')
  } catch (e) {
    ElMessage.error('刷新失败：' + (e.message || ''))
  }
}

function initCharts() {
  // 初始化任务指派分布饼图
  if (assigneePieChart.value) {
    const pieChart = echarts.init(assigneePieChart.value)
    
    // 使用后端返回的团队统计数据
    let pieData = []
    if (companyKanbanData.value.teamStats && Array.isArray(companyKanbanData.value.teamStats)) {
      pieData = companyKanbanData.value.teamStats
        .filter(team => team.totalTasks > 0)
        .slice(0, 8) // 只显示前8个团队
        .map(team => ({
          value: team.totalTasks,
          name: team.teamName || `团队${team.teamId}`
        }))
    }
    
    // 如果没有团队数据，使用状态分布
    if (pieData.length === 0) {
      pieData = [
        { value: companyKanbanData.value.completedTasks, name: '已完成' },
        { value: companyKanbanData.value.inProgressTasks, name: '进行中' },
        { value: companyKanbanData.value.overdueTasks, name: '待办' }
      ].filter(item => item.value > 0)
    }
    
    const pieOption = {
      tooltip: {
        trigger: 'item',
        formatter: '{a} <br/>{b}: {c} ({d}%)'
      },
      series: [{
        name: '任务分布',
        type: 'pie',
        radius: '50%',
        data: pieData
      }]
    }
    pieChart.setOption(pieOption)
  }
  
  // 初始化任务完成趋势折线图
  if (completionLineChart.value) {
    const lineChart = echarts.init(completionLineChart.value)
    
    // 真实数据：日期轴与三条曲线全部来自后端 recentDays，不做任何补数/造数
    const points = dailyPoints.value
    const dateLabels = points.map((p) => String(p.date || '').slice(5))
    const trendData = points.map((p) => Number(p.scheduleDone || 0))
    const createdData = points.map((p) => Number(p.scheduleCreated || 0))
    const logData = points.map((p) => Number(p.worklogCreated || 0))
    
    const lineOption = {
      tooltip: {
        trigger: 'axis'
      },
      legend: {
        data: ['新增日程', '完成日程', '新增日志'],
        bottom: 0,
        itemWidth: 12,
        itemHeight: 8
      },
      grid: {
        top: 20,
        left: 10,
        right: 18,
        bottom: 46,
        containLabel: true
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: dateLabels
      },
      yAxis: {
        type: 'value',
        minInterval: 1
      },
      series: [
        { name: '新增日程', type: 'line', smooth: true, data: createdData, itemStyle: { color: '#409EFF' } },
        { name: '完成日程', type: 'line', smooth: true, data: trendData, itemStyle: { color: '#67C23A' } },
        { name: '新增日志', type: 'line', smooth: true, data: logData, itemStyle: { color: '#E6A23C' } }
      ]
    }
    lineChart.setOption(lineOption)
  }
}

function formatDateTime(timestamp) {
   if (!timestamp) return '-'
   return new Date(timestamp).toLocaleString('zh-CN')
 }
 
 function getStatusType(status) {
   const statusMap = {
     'todo': '',
     'doing': 'warning',
     'done': 'success',
     'canceled': 'danger'
   }
   return statusMap[status] || ''
 }
 
 function getStatusText(status) {
   const statusMap = {
     'todo': '待办',
     'doing': '进行中',
     'done': '已完成',
     'canceled': '已取消'
   }
   return statusMap[status] || status
 }

onMounted(load)
</script>

<style scoped>
.admin-page { padding: 12px; background: var(--tp-bg-page); min-height: 100%; display: grid; gap: 10px; }
.content-card { border-radius: 10px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 15px; font-weight: 600; color: var(--tp-text); }
.kpi-card { text-align:center }
.kpi-num { font-size:22px; font-weight:700; color: var(--tp-text); line-height:1.2 }
.kpi-title { color: var(--tp-text-muted); margin-top:4px; font-size:12px }
.trend-wrap { margin-top: 12px; }
.trend-title { font-weight: 600; margin-bottom: 8px; }
.bars { display:flex; gap:8px; align-items:flex-end; height: 110px; }
.bar { display:flex; flex-direction:column; align-items:center; gap:6px; }
.bar-col { width: 18px; background:#409eff; border-radius:4px; transition:height .2s; }
.bar-label { font-size:12px; color:#6b7280 }
.teams-wrap { margin-top: 12px; }

/* 公司看板样式 */
.company-kanban-section { margin: 20px 0; }
.kanban-card { border-radius: 8px; }
.kanban-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; }
.kanban-stat-item { display: flex; align-items: center; padding: 12px; border-radius: 8px; background: var(--tp-bg-subtle); transition: all 0.25s ease; }
.kanban-stat-item:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1); }
.stat-icon { width: 48px; height: 48px; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin-right: 12px; font-size: 20px; }
.stat-icon.total { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }
.stat-icon.completed { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); color: white; }
.stat-icon.progress { background: linear-gradient(135deg, #ffecd2 0%, #fcb69f 100%); color: #d68910; }
.stat-icon.overdue { background: linear-gradient(135deg, #ff9a9e 0%, #fecfef 100%); color: #e74c3c; }
.stat-content .stat-value { font-size: 24px; font-weight: bold; margin-bottom: 4px; color: #303133; }
.stat-content .stat-label { font-size: 14px; color: #909399; }

/* 图表区域样式 */
.charts-section { margin: 20px 0; }
.charts-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(400px, 1fr)); gap: 16px; }
.chart-card { border-radius: 8px; }
.pie-chart, .line-chart { height: 220px; width: 100%; }

/* 任务更新样式 */
.task-updates-list { max-height: 220px; overflow-y: auto; }
.updates-container { display: flex; flex-direction: column; gap: 8px; }
.update-item { display: flex; align-items: flex-start; padding: 10px 12px; border-radius: 8px; background: var(--tp-bg-subtle); transition: all 0.25s ease; }
.update-item:hover { background: #e9ecef; }
.update-avatar { margin-right: 12px; flex-shrink: 0; }
.update-content { flex: 1; }
.update-header { margin-bottom: 4px; }
.user-name { font-weight: 600; color: #303133; margin-right: 8px; }
.update-action { color: #409eff; margin-right: 8px; }
.task-title { color: #606266; font-weight: 500; }
.update-meta { display: flex; align-items: center; gap: 12px; font-size: 12px; color: #909399; }
.team-name { background: #e1f3ff; color: #409eff; padding: 2px 6px; border-radius: 4px; }
.empty-state { text-align: center; padding: 40px 20px; }

.detail-sections { margin-top: 16px; }
.sub-card { margin-bottom: 12px; }
.sub-title { font-weight: 600; }

/* ---------- 打卡分析 ---------- */
.analysis-card :deep(.el-card__body) {
  padding: 12px 14px;
}
.analysis-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}
.as-item {
  padding: 10px 6px;
  border-radius: 8px;
  background: var(--tp-bg-card);
  border: 1px solid var(--tp-border);
  text-align: center;
}
.as-num {
  font-size: 18px;
  font-weight: 700;
  color: var(--tp-text);
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.as-label {
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--tp-text-muted);
}
.as-item.warn .as-num {
  color: #d97706;
}
.as-item.danger .as-num {
  color: #dc2626;
}
.mini-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--tp-text-muted);
  margin: 4px 0 6px;
}
.tips-list {
  max-height: 200px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 2px;
}
.tip-item {
  display: flex;
  gap: 8px;
  padding: 9px 11px;
  border-radius: 8px;
  border: 1px solid var(--tp-border);
  background: var(--tp-bg-card);
  border-left-width: 3px;
}
.tip-item.tip-warning {
  border-left-color: #f59e0b;
  background: #fffaf0;
}
.tip-item.tip-error {
  border-left-color: #ef4444;
  background: #fff5f5;
}
.tip-item.tip-info {
  border-left-color: #3b82f6;
}
.tip-item.tip-success {
  border-left-color: #10b981;
  background: #f4fdf8;
}
.tip-icon {
  margin-top: 2px;
  font-size: 15px;
  flex: none;
}
.tip-warning .tip-icon {
  color: #d97706;
}
.tip-error .tip-icon {
  color: #dc2626;
}
.tip-info .tip-icon {
  color: #2563eb;
}
.tip-success .tip-icon {
  color: #059669;
}
.tip-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--tp-text);
}
.tip-desc {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tp-text-muted);
}
</style>