<!--
  考勤日历（管理端）。

  为什么值得单独做一个视图：打卡记录列表只能一条条看「谁几点打的卡」，
  而管理者真正想知道的是「这个月哪天出问题、谁出问题」。日历把每天压成一格，
  正常/迟到/缺勤一眼可见，点开再看当天名单。

  数据源：GET /api/v1/attendance/calendar（读 attendance_daily 日汇总，
  不是原始打卡明细），因此每格代表「某人某天的最终判定」。
-->
<template>
  <el-card shadow="never" class="cal-card">
    <template #header>
      <div class="cal-header">
        <div class="cal-title-wrap">
          <el-button-group>
            <el-button :icon="ArrowLeft" @click="shiftMonth(-1)" />
            <el-button @click="goToday">今天</el-button>
            <el-button :icon="ArrowRight" @click="shiftMonth(1)" />
          </el-button-group>
          <span class="cal-title">{{ monthTitle }}</span>
          <el-tag v-if="loading" size="small" type="info">加载中</el-tag>
        </div>
        <div class="cal-legend">
          <span v-for="s in legend" :key="s.value" class="legend-item">
            <i class="legend-dot" :style="{ background: s.color }"></i>{{ s.label }}
          </span>
          <span class="legend-tip">双击/点「＋」可补卡</span>
        </div>
      </div>
    </template>

    <div class="cal-summary">
      <span>本月合计：</span>
      <el-tag size="small" type="success" effect="plain">正常 {{ monthStats.normal }}</el-tag>
      <el-tag size="small" type="warning" effect="plain">迟到 {{ monthStats.late }}</el-tag>
      <el-tag size="small" type="warning" effect="plain">早退 {{ monthStats.early }}</el-tag>
      <el-tag size="small" type="danger" effect="plain">缺勤 {{ monthStats.absent }}</el-tag>
    </div>

    <div class="cal-weekdays">
      <div v-for="w in weekdays" :key="w" class="cal-weekday">{{ w }}</div>
    </div>

    <div class="cal-grid">
      <div
        v-for="(cell, idx) in cells"
        :key="idx"
        class="cal-cell"
        :class="{
          'is-other-month': !cell.inMonth,
          'is-today': cell.isToday,
          'is-weekend': cell.weekday >= 5,
        }"
        @dblclick="openManual(cell.date)"
      >
        <div class="cell-head">
          <span class="cell-date">{{ cell.day }}</span>
          <div class="cell-badges" v-if="cell.rows.length">
            <span v-if="cell.counts.normal" class="badge ok">{{ cell.counts.normal }}</span>
            <span v-if="cell.counts.late" class="badge warn">{{ cell.counts.late }}</span>
            <span v-if="cell.counts.absent" class="badge danger">{{ cell.counts.absent }}</span>
          </div>
          <el-button
            v-if="cell.inMonth"
            class="cell-add"
            link
            type="primary"
            :icon="Plus"
            title="在这一天补卡"
            @click.stop="openManual(cell.date)"
          />
        </div>

        <div class="cell-body">
          <div
            v-for="row in cell.rows.slice(0, 2)"
            :key="row.userId + '-' + row.workDate"
            class="cal-chip"
            :class="'st-' + (row.attendanceStatus || 'NORMAL')"
            :title="row.userName + '：' + statusLabel(row)"
            @click.stop="openDay(cell)"
          >
            <span class="chip-name">{{ row.userName }}</span>
            <span class="chip-status">{{ statusShort(row) }}</span>
          </div>
          <button v-if="cell.rows.length > 2" class="cell-more" @click.stop="openDay(cell)">
            还有 {{ cell.rows.length - 2 }} 人
          </button>
        </div>
      </div>
    </div>

    <!-- 当天名单 -->
    <el-drawer v-model="dayDrawer.visible" :title="dayDrawer.title" size="560px">
      <el-empty v-if="!dayDrawer.rows.length" description="这一天没有考勤记录" :image-size="70" />
      <el-table v-else :data="dayDrawer.rows" size="small">
        <el-table-column prop="userName" label="姓名" width="100" />
        <el-table-column prop="employeeNo" label="工号" width="90" />
        <el-table-column label="签到" width="80">
          <template #default="{ row }">{{ timeOf(row.firstCheckIn) }}</template>
        </el-table-column>
        <el-table-column label="签退" width="80">
          <template #default="{ row }">{{ timeOf(row.lastCheckOut) }}</template>
        </el-table-column>
        <el-table-column label="在岗" width="90">
          <template #default="{ row }">{{ minutesText(row.workMinutes) }}</template>
        </el-table-column>
        <el-table-column label="异常" min-width="120">
          <template #default="{ row }">
            <span v-if="row.lateMinutes > 0" class="warn">迟到 {{ row.lateMinutes }} 分</span>
            <span v-if="row.earlyLeaveMinutes > 0" class="warn">
              {{ row.lateMinutes > 0 ? ' / ' : '' }}早退 {{ row.earlyLeaveMinutes }} 分
            </span>
            <span v-if="!row.lateMinutes && !row.earlyLeaveMinutes" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="判定" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.attendanceStatus)">
              {{ statusText(row.attendanceStatus) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="drawer-actions">
        <el-button size="small" @click="openManual(dayDrawer.date)">给这一天补卡</el-button>
      </div>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, ArrowRight, Plus } from '@element-plus/icons-vue'
import { attendanceApi } from '@/api'

const emit = defineEmits(['manual'])

const WEEKDAY_NAMES = ['一', '二', '三', '四', '五', '六', '日']
const weekdays = WEEKDAY_NAMES.map((n) => '周' + n)

const legend = [
  { value: 'NORMAL', label: '正常', color: '#10b981' },
  { value: 'LATE', label: '迟到', color: '#f59e0b' },
  { value: 'EARLY_LEAVE', label: '早退', color: '#fb923c' },
  { value: 'LATE_AND_EARLY', label: '迟到且早退', color: '#ef4444' },
  { value: 'ABSENT', label: '缺勤', color: '#b91c1c' },
]

const loading = ref(false)
const rows = ref([])
const cursor = ref(startOfMonth(new Date()))
const dayDrawer = reactive({ visible: false, title: '', date: '', rows: [] })

function startOfMonth(d) {
  return new Date(d.getFullYear(), d.getMonth(), 1)
}
function pad(n) {
  return String(n).padStart(2, '0')
}
function toDateKey(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
function timeOf(value) {
  if (!value) return '—'
  const t = String(value).replace('T', ' ')
  return t.length >= 16 ? t.slice(11, 16) : '—'
}
function minutesText(m) {
  const n = Number(m || 0)
  if (n <= 0) return '—'
  const h = Math.floor(n / 60)
  const r = n % 60
  return h > 0 ? `${h}h${r}m` : `${r}m`
}

const STATUS_TEXT = {
  NORMAL: '正常',
  LATE: '迟到',
  EARLY_LEAVE: '早退',
  LATE_AND_EARLY: '迟到且早退',
  ABSENT: '缺勤',
}
const STATUS_TAG = {
  NORMAL: 'success',
  LATE: 'warning',
  EARLY_LEAVE: 'warning',
  LATE_AND_EARLY: 'danger',
  ABSENT: 'danger',
}
function statusText(s) {
  return STATUS_TEXT[s] || s || '-'
}
function statusTagType(s) {
  return STATUS_TAG[s] || 'info'
}
function statusLabel(row) {
  const parts = [statusText(row.attendanceStatus)]
  if (row.lateMinutes > 0) parts.push(`迟到 ${row.lateMinutes} 分`)
  if (row.earlyLeaveMinutes > 0) parts.push(`早退 ${row.earlyLeaveMinutes} 分`)
  return parts.join('，')
}
/** 格子里的短标签：迟到时直接显示分钟数，比只写「迟到」信息量大 */
function statusShort(row) {
  if (row.attendanceStatus === 'ABSENT') return '缺勤'
  if (row.lateMinutes > 0) return `${row.lateMinutes}分`
  if (row.earlyLeaveMinutes > 0) return `早${row.earlyLeaveMinutes}`
  return '正常'
}

const monthTitle = computed(() => `${cursor.value.getFullYear()} 年 ${cursor.value.getMonth() + 1} 月`)

/** 按日期归集 */
const byDate = computed(() => {
  const map = new Map()
  for (const r of rows.value) {
    const key = String(r.workDate || '')
    if (!key) continue
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(r)
  }
  // 同一天内：缺勤/迟到排前面，方便一眼看到异常
  const weight = { ABSENT: 0, LATE_AND_EARLY: 1, LATE: 1, EARLY_LEAVE: 2, NORMAL: 3 }
  for (const list of map.values()) {
    list.sort((a, b) => (weight[a.attendanceStatus] ?? 9) - (weight[b.attendanceStatus] ?? 9))
  }
  return map
})

function countsOf(list) {
  const c = { normal: 0, late: 0, absent: 0 }
  for (const r of list) {
    if (r.attendanceStatus === 'ABSENT') c.absent++
    else if (Number(r.lateMinutes || 0) > 0 || r.attendanceStatus === 'LATE' || r.attendanceStatus === 'LATE_AND_EARLY') c.late++
    else c.normal++
  }
  return c
}

const cells = computed(() => {
  const year = cursor.value.getFullYear()
  const month = cursor.value.getMonth()
  const first = new Date(year, month, 1)
  const offset = (first.getDay() + 6) % 7 // 周一=0
  const start = new Date(year, month, 1 - offset)
  const todayKey = toDateKey(new Date())
  const result = []
  for (let i = 0; i < 42; i++) {
    const d = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i)
    const key = toDateKey(d)
    const list = byDate.value.get(key) || []
    result.push({
      date: key,
      day: d.getDate(),
      inMonth: d.getMonth() === month,
      isToday: key === todayKey,
      weekday: (d.getDay() + 6) % 7,
      rows: list,
      counts: countsOf(list),
    })
  }
  return result
})

/** 本月（只统计当前显示月份的格子）合计 */
const monthStats = computed(() => {
  const total = { normal: 0, late: 0, early: 0, absent: 0 }
  for (const cell of cells.value) {
    if (!cell.inMonth) continue
    for (const r of cell.rows) {
      if (r.attendanceStatus === 'ABSENT') total.absent++
      else if (r.attendanceStatus === 'LATE') total.late++
      else if (r.attendanceStatus === 'EARLY_LEAVE') total.early++
      else if (r.attendanceStatus === 'LATE_AND_EARLY') {
        total.late++
        total.early++
      } else total.normal++
    }
  }
  return total
})

async function load() {
  loading.value = true
  try {
    // 取当前显示月份前后各一周，保证 42 格里的跨月日期也有数据
    const y = cursor.value.getFullYear()
    const m = cursor.value.getMonth()
    const from = toDateKey(new Date(y, m, 1 - 7))
    const to = toDateKey(new Date(y, m + 1, 7))
    const data = await attendanceApi.calendar({ from, to })
    rows.value = Array.isArray(data) ? data : []
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

function shiftMonth(delta) {
  cursor.value = new Date(cursor.value.getFullYear(), cursor.value.getMonth() + delta, 1)
  load()
}

function goToday() {
  cursor.value = startOfMonth(new Date())
  load()
}

function openDay(cell) {
  dayDrawer.title = `${cell.date} 考勤名单（${cell.rows.length} 人）`
  dayDrawer.date = cell.date
  dayDrawer.rows = cell.rows
  dayDrawer.visible = true
}

/** 交给父组件打开「手工补卡」并预填日期 */
function openManual(dateKey) {
  emit('manual', dateKey)
}

onMounted(load)
</script>

<style scoped>
.cal-card {
  border-radius: 10px;
  border: 1px solid var(--tp-border);
}
.cal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.cal-title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}
.cal-title {
  font-size: 16px;
  font-weight: 650;
  color: #0f172a;
}
.cal-legend {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #64748b;
  flex-wrap: wrap;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.legend-tip {
  color: #94a3b8;
}

.cal-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 12.5px;
  color: #64748b;
}

.cal-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  border: 1px solid var(--tp-border);
  border-bottom: none;
  background: var(--tp-bg-subtle);
}
.cal-weekday {
  padding: 8px 0;
  text-align: center;
  font-size: 12.5px;
  font-weight: 600;
  color: #475569;
}
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  border-left: 1px solid var(--tp-border);
  border-top: 1px solid var(--tp-border);
}
.cal-cell {
  min-height: 96px;
  padding: 6px;
  border-right: 1px solid var(--tp-border);
  border-bottom: 1px solid var(--tp-border);
  background: #fcfdff;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.cal-cell:hover {
  background: var(--tp-bg-card);
}
.cal-cell.is-weekend {
  background: #f8fbff;
}
.cal-cell.is-other-month {
  background: #f2f6fb;
  opacity: 0.6;
}
.cal-cell.is-today {
  background: var(--tp-primary-soft);
  box-shadow: inset 0 0 0 2px #93c5fd;
}
.cell-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
}
.cell-date {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.is-today .cell-date {
  color: #1d4ed8;
}
.cell-badges {
  display: flex;
  gap: 3px;
}
.badge {
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  font-size: 10.5px;
  line-height: 16px;
  text-align: center;
  color: #fff;
}
.badge.ok {
  background: #10b981;
}
.badge.warn {
  background: #f59e0b;
}
.badge.danger {
  background: #ef4444;
}
.cell-add {
  opacity: 0;
  transition: opacity 0.15s ease;
}
.cal-cell:hover .cell-add {
  opacity: 1;
}
.cell-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  overflow: hidden;
}
.cal-chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  padding: 1px 6px;
  border-radius: 5px;
  font-size: 11.5px;
  cursor: pointer;
  border-left: 3px solid #10b981;
  background: var(--tp-bg-subtle);
  white-space: nowrap;
  overflow: hidden;
}
.cal-chip .chip-name {
  overflow: hidden;
  text-overflow: ellipsis;
}
.cal-chip .chip-status {
  color: #64748b;
  font-variant-numeric: tabular-nums;
  flex: none;
}
.cal-chip.st-NORMAL {
  border-left-color: #10b981;
}
.cal-chip.st-LATE {
  border-left-color: #f59e0b;
  background: #fef7e3;
}
.cal-chip.st-EARLY_LEAVE {
  border-left-color: #fb923c;
  background: #fff3e6;
}
.cal-chip.st-LATE_AND_EARLY {
  border-left-color: #ef4444;
  background: #fdeceb;
}
.cal-chip.st-ABSENT {
  border-left-color: #b91c1c;
  background: #fdeceb;
}
.cell-more {
  border: none;
  background: none;
  color: #2563eb;
  font-size: 11.5px;
  cursor: pointer;
  padding: 0 6px;
  text-align: left;
}
.drawer-actions {
  margin-top: 12px;
  text-align: right;
}
.warn {
  color: #d97706;
  font-size: 12.5px;
}
.muted {
  color: #94a3b8;
}
</style>
