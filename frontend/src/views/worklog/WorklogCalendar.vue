<!--
  工作日志日历。

  设计诉求（来自实际使用）：
    · 格子里**只**显示「蓝色圆底 + 白字姓氏」的头像，不放标题正文
      —— 一眼看出"这天谁交了日志"，而不是一堆看不懂的截断文字；
    · 想看内容就点开那一天，右侧抽屉里列出当日**所有人**的日志全文。

  数据来源：GET /api/v1/worklog?from&to&size=200（后端已支持日期区间），
  按 log_date 归集；作者名统一用后端返回的 creatorName（昵称优先，不是登录账号）。
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
          <span class="legend-item">
            <i class="avatar demo">张</i>提交人姓氏
          </span>
          <span class="legend-tip">点任意一天查看当日全部日志</span>
          <el-tag v-if="truncated" size="small" type="warning">仅显示前 200 条</el-tag>
        </div>
      </div>
    </template>

    <div class="cal-weekdays">
      <div v-for="w in weekdays" :key="w" class="cal-weekday">{{ w }}</div>
    </div>

    <div class="cal-grid">
      <div
        v-for="cell in cells"
        :key="cell.date"
        class="cal-cell"
        :class="{
          'is-other-month': !cell.inMonth,
          'is-today': cell.isToday,
          'is-weekend': cell.weekday >= 5,
          'has-log': cell.total > 0,
        }"
        :title="cell.total ? cell.date + '：' + cell.total + ' 篇日志' : cell.date + '：无日志'"
        @click="openDay(cell)"
      >
        <div class="cell-head">
          <span class="cell-date">{{ cell.day }}</span>
          <span v-if="cell.total" class="cell-count">{{ cell.total }}</span>
        </div>

        <div class="cell-body">
          <span
            v-for="author in cell.authors.slice(0, 8)"
            :key="author.userId"
            class="avatar"
            :title="author.name + ' · ' + author.count + ' 篇'"
          >
            {{ author.surname }}
          </span>
          <span v-if="cell.authors.length > 8" class="avatar more">
            +{{ cell.authors.length - 8 }}
          </span>
        </div>
      </div>
    </div>

    <!-- 当日全部日志 -->
    <el-drawer v-model="dayDrawer.visible" :title="dayDrawer.title" size="620px">
      <el-empty v-if="!dayDrawer.logs.length" description="这一天没有人提交日志" :image-size="80" />
      <div v-for="log in dayDrawer.logs" :key="log.worklogId" class="log-item">
        <div class="log-top">
          <span class="avatar sm">{{ surnameOf(log.creatorName) }}</span>
          <strong class="log-author">{{ log.creatorName || ('用户 #' + log.creatorId) }}</strong>
          <el-tag v-if="log.teamName" size="small" type="info">{{ log.teamName }}</el-tag>
          <span v-if="log.scheduleTitle" class="log-meta">关联日程：{{ log.scheduleTitle }}</span>
          <span class="log-meta push-right">{{ formatTime(log.createdAt) }}</span>
        </div>
        <div class="log-title">{{ log.title }}</div>
        <div class="log-content">{{ log.content || '（无正文）' }}</div>
        <div class="log-foot">
          <el-button link type="primary" @click="$router.push(`/worklogs/${log.worklogId}`)">查看详情</el-button>
        </div>
      </div>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { worklogApi } from '@/api'

const WEEKDAY_NAMES = ['一', '二', '三', '四', '五', '六', '日']
const weekdays = WEEKDAY_NAMES.map((n) => '周' + n)

/** 单页上限（后端 MAX_PAGE_SIZE 为 200） */
const PAGE_SIZE = 200

const loading = ref(false)
const logs = ref([])
const truncated = ref(false)
const cursor = ref(startOfMonth(new Date()))
const dayDrawer = reactive({ visible: false, title: '', logs: [] })

function startOfMonth(d) {
  return new Date(d.getFullYear(), d.getMonth(), 1)
}
function pad(n) {
  return String(n).padStart(2, '0')
}
function toDateKey(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

/**
 * 取姓氏：中文取第一个字，英文取首字母并大写。
 * 目的只有一个——圆圈里只放一个字，多了就挤不下。
 *
 * 特例：角色类昵称（如「系统管理员」）不是真实姓名，取首字会得到「系」，
 * 意义不明；这类统一显示「管」，一眼能认出是管理员。
 */
const NICKNAME_ALIAS = {
  系统管理员: '管',
  超级管理员: '管',
}

function surnameOf(name) {
  const n = String(name || '').trim()
  if (!n) return '?'
  if (NICKNAME_ALIAS[n]) return NICKNAME_ALIAS[n]
  if (n.includes('管理员')) return '管'
  const first = Array.from(n)[0]
  return /[a-zA-Z]/.test(first) ? first.toUpperCase() : first
}

const monthTitle = computed(() => `${cursor.value.getFullYear()} 年 ${cursor.value.getMonth() + 1} 月`)

/** 按 logDate 归集，并算出每天有哪些人提交（同一人多篇只出现一个头像） */
const byDay = computed(() => {
  const map = new Map()
  for (const log of logs.value) {
    const key = String(log.logDate || '').slice(0, 10)
    if (!key) continue
    if (!map.has(key)) map.set(key, { logs: [], authors: new Map() })
    const bucket = map.get(key)
    bucket.logs.push(log)
    const uid = log.creatorId ?? log.creatorName ?? 'unknown'
    if (!bucket.authors.has(uid)) {
      bucket.authors.set(uid, {
        userId: uid,
        name: log.creatorName || '未知',
        surname: surnameOf(log.creatorName),
        count: 0,
      })
    }
    bucket.authors.get(uid).count += 1
  }
  // 同一天内按创建时间正序，抽屉里读起来符合时间线
  for (const bucket of map.values()) {
    bucket.logs.sort((a, b) => String(a.createdAt || '').localeCompare(String(b.createdAt || '')))
  }
  return map
})

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
    const bucket = byDay.value.get(key)
    result.push({
      date: key,
      day: d.getDate(),
      inMonth: d.getMonth() === month,
      isToday: key === todayKey,
      weekday: (d.getDay() + 6) % 7,
      authors: bucket ? [...bucket.authors.values()] : [],
      total: bucket ? bucket.logs.length : 0,
      logs: bucket ? bucket.logs : [],
    })
  }
  return result
})

async function load() {
  loading.value = true
  try {
    const y = cursor.value.getFullYear()
    const m = cursor.value.getMonth()
    // 取当前月份的 1 号到月末，多给一周余量（格子会显示相邻月的日期）
    const from = toDateKey(new Date(y, m, 1 - 7))
    const to = toDateKey(new Date(y, m + 1, 7))
    const data = await worklogApi.page({ from, to, page: 1, size: PAGE_SIZE })
    const records = data?.records || []
    logs.value = records
    truncated.value = Number(data?.total || 0) > records.length
  } catch {
    logs.value = []
    truncated.value = false
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
  dayDrawer.title = `${cell.date} 的日志（${cell.total} 篇 / ${cell.authors.length} 人）`
  dayDrawer.logs = cell.logs
  dayDrawer.visible = true
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
  color: var(--tp-text);
}
.cal-legend {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--tp-text-muted);
  flex-wrap: wrap;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.legend-tip {
  color: var(--tp-text-faint);
}

.cal-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  border: 1px solid var(--tp-border);
  border-bottom: none;
  background: var(--tp-bg-card-head);
}
.cal-weekday {
  padding: 8px 0;
  text-align: center;
  font-size: 12.5px;
  font-weight: 600;
  color: #24486e;
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
  background: var(--tp-bg-card);
  display: flex;
  flex-direction: column;
  gap: 6px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}
.cal-cell:hover {
  background: var(--tp-bg-subtle);
}
.cal-cell.is-weekend {
  background: #f8fbff;
}
.cal-cell.is-other-month {
  background: #f2f6fb;
  opacity: 0.55;
}
.cal-cell.is-today {
  background: #eff6ff;
  box-shadow: inset 0 0 0 2px #93c5fd;
}

.cell-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.cell-date {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.is-today .cell-date {
  color: #1d4ed8;
}
.cell-count {
  font-size: 11px;
  color: var(--tp-text-faint);
  font-variant-numeric: tabular-nums;
}

.cell-body {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

/* 蓝色圆底白字姓氏 —— 日历里唯一的身份标识 */
.avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #2f6fd0;
  color: #fff;
  font-size: 12px;
  line-height: 24px;
  text-align: center;
  display: inline-block;
  user-select: none;
  box-shadow: 0 1px 2px rgba(47, 111, 208, 0.35);
}
.avatar.sm {
  width: 22px;
  height: 22px;
  line-height: 22px;
  font-size: 11.5px;
  margin-right: 6px;
}
.avatar.demo {
  width: 18px;
  height: 18px;
  line-height: 18px;
  font-size: 10.5px;
}
.avatar.more {
  background: #cbd9ea;
  color: #3f5c7a;
  font-size: 11px;
}

/* 抽屉里的日志条目 */
.log-item {
  padding: 12px 14px;
  border-radius: 9px;
  border: 1px solid var(--tp-border);
  background: var(--tp-bg-card);
  margin-bottom: 12px;
}
.log-top {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.log-author {
  font-size: 14px;
  color: var(--tp-text);
}
.log-meta {
  font-size: 12px;
  color: var(--tp-text-muted);
}
.push-right {
  margin-left: auto;
}
.log-title {
  margin-top: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--tp-text);
}
.log-content {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: #2c4a68;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 220px;
  overflow-y: auto;
}
.log-foot {
  margin-top: 6px;
  text-align: right;
}
</style>
