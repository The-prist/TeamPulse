<!--
  日程日历视图。

  为什么做日历：日程是有「时间分布」属性的数据，用列表看只能看到一行行文字，
  看不出「这周是不是挤爆了」「哪天有空」。月视图一眼就能看出来，这是列表替代不了的。

  数据来源与限制：后端 /api/v1/schedule 目前不支持按日期区间过滤，
  因此这里一次取最新的 200 条（接口单页上限），再在前端按月份归集。
  数据量超过 200 条时会给出提示——真要支撑大数据量，应该给列表接口加 from/to 参数。
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
            <i class="legend-rainbow"></i>每个日程一种颜色，跨天会持续染色
          </span>
          <span class="legend-item">
            <i class="legend-dot" style="background: #94a3b8"></i>标题划线＝已完成 / 已取消
          </span>
          <el-tag v-if="truncated" size="small" type="warning">
            仅显示最近 200 条日程
          </el-tag>
        </div>
      </div>
    </template>

    <!-- 星期表头 -->
    <div class="cal-weekdays">
      <div v-for="w in weekdays" :key="w" class="cal-weekday">{{ w }}</div>
    </div>

    <!-- 日历格子 -->
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
        @dblclick="onCreate(cell.date)"
      >
        <div class="cell-head">
          <span class="cell-date">{{ cell.day }}</span>
          <!-- 日期右边的小色块：一条日程一个方块，颜色与下方胶囊一致。
               用来一眼看出"这天有几件事、分别是什么颜色"，不用读文字。 -->
          <span v-if="cell.items.length" class="cell-dots">
            <i
              v-for="entry in cell.items.slice(0, 6)"
              :key="'dot-' + entry.dayKey + '-' + entry.item.scheduleId"
              class="cell-dot"
              :style="{ background: entry.color }"
              :title="entry.item.title + '（' + timeRange(entry.item) + '）'"
            ></i>
            <em v-if="cell.items.length > 6" class="cell-dot-more">+{{ cell.items.length - 6 }}</em>
          </span>
          <el-button
            v-if="cell.inMonth"
            class="cell-add"
            link
            type="primary"
            :icon="Plus"
            title="在这一天新增日程"
            @click.stop="onCreate(cell.date)"
          />
        </div>

        <div class="cell-body">
          <div
            v-for="entry in cell.items.slice(0, 3)"
            :key="entry.dayKey + '-' + entry.item.scheduleId"
            class="cal-item"
            :class="'st-' + (entry.item.status || 'todo')"
            :style="{ borderLeftColor: entry.color, background: entry.tint }"
            :title="entry.item.title + '（' + timeRange(entry.item) + '）'"
            @click.stop="openDetail(entry.item)"
          >
            <span class="item-time">{{ timeLabel(entry) }}</span>
            <span class="item-title">{{ entry.item.title }}</span>
          </div>
          <button v-if="cell.items.length > 3" class="cell-more" @click.stop="openDay(cell)">
            还有 {{ cell.items.length - 3 }} 条
          </button>
        </div>
      </div>
    </div>

    <!-- 某一天的完整清单 -->
    <el-drawer v-model="dayDrawer.visible" :title="dayDrawer.title" size="420px">
      <el-empty v-if="!dayDrawer.items.length" description="这一天没有日程" :image-size="70" />
      <div
        v-for="item in dayDrawer.items"
        :key="item.scheduleId"
        class="drawer-item"
        :style="{ borderLeftColor: item.__color, borderLeftWidth: '4px' }"
        @click="openDetail(item)"
      >
        <div class="drawer-item-top">
          <el-tag size="small" :type="scheduleStatusTagType(item.status)">
            {{ scheduleStatusText(item.status) }}
          </el-tag>
          <span class="drawer-item-time">{{ timeRange(item) }}</span>
          <span v-if="!item.__isStart" class="drawer-item-cont">
            {{ item.__isEnd ? '跨天结束' : '跨天续期' }}
          </span>
        </div>
        <div class="drawer-item-title">{{ item.title }}</div>
        <div v-if="item.location" class="drawer-item-meta">地点：{{ item.location }}</div>
      </div>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Plus } from '@element-plus/icons-vue'
import { scheduleApi } from '@/api'
import { scheduleStatusText, scheduleStatusTagType } from '@/utils/dict'

const emit = defineEmits(['create'])
const router = useRouter()

const WEEKDAY_NAMES = ['一', '二', '三', '四', '五', '六', '日']
const weekdays = WEEKDAY_NAMES.map((n) => '周' + n)

/**
 * 彩虹配色：同一条日程在整个生效周期里用同一种颜色。
 * 取色用 scheduleId 取模，保证「同一条日程颜色恒定」「相邻日程颜色不同」，
 * 不需要后端存颜色字段。
 */
const RAINBOW = [
  '#ef4444', '#f97316', '#f59e0b', '#84cc16', '#22c55e', '#14b8a6',
  '#06b6d4', '#3b82f6', '#6366f1', '#a855f7', '#ec4899', '#f43f5e',
]

function colorOf(item) {
  const id = Math.abs(Number(item?.scheduleId) || 0)
  return RAINBOW[id % RAINBOW.length]
}

/** 胶囊底色：同色 12% 透明（hex + alpha），避免每条日程都做一次 color-mix 计算 */
function tintOf(item) {
  return colorOf(item) + '1f'
}

const legend = [
  { value: 'todo', label: '待办', color: '#94a3b8' },
  { value: 'doing', label: '进行中', color: '#f59e0b' },
  { value: 'done', label: '已完成', color: '#10b981' },
  { value: 'canceled', label: '已取消', color: '#ef4444' },
]

/** 单页最多取多少条（与后端分页上限一致） */
const PAGE_SIZE = 200

const loading = ref(false)
const schedules = ref([])
const truncated = ref(false)

/** 当前展示的月份（取该月 1 号） */
const cursor = ref(startOfMonth(new Date()))

const dayDrawer = reactive({ visible: false, title: '', items: [] })

function startOfMonth(d) {
  return new Date(d.getFullYear(), d.getMonth(), 1)
}

function pad(n) {
  return String(n).padStart(2, '0')
}

function toDateKey(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 'yyyy-MM-ddTHH:mm:ss' 或 'yyyy-MM-dd HH:mm' → Date */
function parseTime(value) {
  if (!value) return null
  const t = String(value).replace('T', ' ')
  const [datePart, timePart = '00:00:00'] = t.split(' ')
  const [y, m, d] = datePart.split('-').map(Number)
  const [hh, mm, ss] = timePart.split(':').map(Number)
  return new Date(y, (m || 1) - 1, d || 1, hh || 0, mm || 0, ss || 0)
}

function timeOf(value) {
  const d = parseTime(value)
  return d ? `${pad(d.getHours())}:${pad(d.getMinutes())}` : ''
}

function timeRange(item) {
  const s = timeOf(item.startTime)
  const e = timeOf(item.endTime)
  return e ? `${s} - ${e}` : s
}

/**
 * 胶囊左侧的时间标记。
 *   单日      → 开始时间（如 09:00）
 *   跨天起点  → 开始时间
 *   跨天中间  → 续
 *   跨天终点  → 结束时间（如 →18:00），一眼看出这条占用到几点收尾
 */
function timeLabel(entry) {
  if (entry.isStart) return timeOf(entry.item.startTime)
  if (entry.isEnd) return '→' + timeOf(entry.item.endTime)
  return '续'
}

const monthTitle = computed(() => {
  const y = cursor.value.getFullYear()
  const m = cursor.value.getMonth() + 1
  return `${y} 年 ${m} 月`
})

/**
 * 把日程按 yyyy-MM-dd 归集。
 *
 * 关键点：**跨天日程要把起始日到结束日之间每一天都填上**，
 * 这样 3 天的任务会在日历上连成 3 格同色，形成"色带"，
 * 一眼就能看出占用周期——这是列表视图完全给不了的信息。
 *
 * 每个格子里的元素是 { item, isStart, dayKey, color, tint } 包装对象，
 * isStart 用来区分"起点"和"续期"，续期在胶囊里显示「续」而不是时间。
 */
const itemsByDay = computed(() => {
  const map = new Map()
  for (const item of schedules.value) {
    const start = parseTime(item.startTime)
    if (!start) continue
    const end = parseTime(item.endTime)
    const lastDay = end && end >= start ? end : start

    const cursorDay = new Date(start.getFullYear(), start.getMonth(), start.getDate())
    const endDay = new Date(lastDay.getFullYear(), lastDay.getMonth(), lastDay.getDate())
    const startKey = toDateKey(start)
    const endKey = toDateKey(lastDay)
    const color = colorOf(item)
    const tint = tintOf(item)

    // guard 防止脏数据（结束时间远在未来）把循环跑爆
    let guard = 0
    while (cursorDay <= endDay && guard++ < 400) {
      const key = toDateKey(cursorDay)
      if (!map.has(key)) map.set(key, [])
      map.get(key).push({
        item,
        isStart: key === startKey,
        isEnd: key === endKey,
        dayKey: key,
        color,
        tint,
      })
      cursorDay.setDate(cursorDay.getDate() + 1)
    }
  }
  for (const list of map.values()) {
    list.sort((a, b) => parseTime(a.item.startTime) - parseTime(b.item.startTime))
  }
  return map
})

/**
 * 生成 6×7 = 42 个格子。
 * 从「当月 1 号所在周的周一」开始，保证跨月的日期也显示出来（灰化），
 * 这样切换月份时布局不会跳。
 */
const cells = computed(() => {
  const year = cursor.value.getFullYear()
  const month = cursor.value.getMonth()
  const first = new Date(year, month, 1)
  // getDay(): 0=周日 … 6=周六；转成「周一=0」
  const offset = (first.getDay() + 6) % 7
  const start = new Date(year, month, 1 - offset)

  const todayKey = toDateKey(new Date())
  const today = new Date()
  const result = []
  for (let i = 0; i < 42; i++) {
    const d = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i)
    const key = toDateKey(d)
    result.push({
      date: key,
      day: d.getDate(),
      inMonth: d.getMonth() === month,
      isToday: key === todayKey,
      weekday: (d.getDay() + 6) % 7,
      items: itemsByDay.value.get(key) || [],
    })
  }
  return result
})

async function load() {
  loading.value = true
  try {
    const data = await scheduleApi.page({ page: 1, size: PAGE_SIZE })
    const records = data?.records || []
    schedules.value = records
    truncated.value = Number(data?.total || 0) > records.length
  } catch {
    schedules.value = []
    truncated.value = false
  } finally {
    loading.value = false
  }
}

function shiftMonth(delta) {
  cursor.value = new Date(cursor.value.getFullYear(), cursor.value.getMonth() + delta, 1)
}

function goToday() {
  cursor.value = startOfMonth(new Date())
}

function openDetail(item) {
  router.push(`/schedule/${item.scheduleId}`)
}

function openDay(cell) {
  // 格子里的元素是包装对象（含颜色/是否起点），抽屉里只要日程本体
  dayDrawer.title = `${cell.date} 的日程（${cell.items.length}）`
  dayDrawer.items = cell.items.map((e) => ({
    ...e.item,
    __color: e.color,
    __isStart: e.isStart,
    __isEnd: e.isEnd,
  }))
  dayDrawer.visible = true
}

/** 双击格子或在格子里点 + ：交给父组件打开「新增日程」并预填日期 */
function onCreate(dateKey) {
  emit('create', dateKey)
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
  letter-spacing: 0.5px;
}
.cal-legend {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: #64748b;
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
/* 图例里的彩虹条：说明「颜色是按日程分配的」 */
.legend-rainbow {
  width: 42px;
  height: 8px;
  border-radius: 4px;
  display: inline-block;
  background: linear-gradient(
    90deg,
    #ef4444,
    #f59e0b,
    #22c55e,
    #06b6d4,
    #3b82f6,
    #a855f7,
    #f43f5e
  );
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
  min-height: 108px;
  padding: 6px;
  border-right: 1px solid var(--tp-border);
  border-bottom: 1px solid var(--tp-border);
  background: #fcfdff;
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: background-color 0.15s ease;
}
.cal-cell:hover {
  background: var(--tp-bg-card);
}
.cal-cell.is-weekend {
  background: #f8fbff;
}
.cal-cell.is-other-month {
  background: var(--tp-bg-page);
  opacity: 0.55;
}
.cal-cell.is-today {
  background: var(--tp-primary-soft);
  box-shadow: inset 0 0 0 2px #93c5fd;
}

.cell-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
/* 日期旁的小色块：方形，颜色同日程 */
.cell-dots {
  display: flex;
  align-items: center;
  gap: 3px;
  flex-wrap: wrap;
  max-width: 60%;
}
.cell-dot {
  width: 7px;
  height: 7px;
  border-radius: 2px;
  display: inline-block;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.7);
}
.cell-dot-more {
  font-size: 10px;
  font-style: normal;
  line-height: 1;
  color: #64748b;
}
.cell-date {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.is-today .cell-date {
  color: #1d4ed8;
}
.cell-add {
  margin-left: auto;
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
.cal-item {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 2px 6px;
  border-radius: 5px;
  font-size: 11.5px;
  cursor: pointer;
  border-left: 3px solid #94a3b8;
  background: var(--tp-bg-subtle);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cal-item:hover {
  filter: brightness(0.96);
}
.cal-item .item-time {
  color: #64748b;
  font-variant-numeric: tabular-nums;
  flex: none;
}
.cal-item .item-title {
  overflow: hidden;
  text-overflow: ellipsis;
}
.cal-item.st-todo {
  border-left-color: #94a3b8;
}
.cal-item.st-doing {
  border-left-color: #f59e0b;
  background: #fef7e3;
}
.cal-item.st-done {
  border-left-color: #10b981;
  background: #eafaf0;
}
.cal-item.st-done .item-title {
  text-decoration: line-through;
  color: #94a3b8;
}
.cal-item.st-canceled {
  border-left-color: #ef4444;
  background: #fdeceb;
}
.cal-item.st-canceled .item-title {
  text-decoration: line-through;
  color: #94a3b8;
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

.drawer-item {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--tp-border);
  margin-bottom: 10px;
  cursor: pointer;
}
.drawer-item:hover {
  background: var(--tp-bg-subtle);
}
.drawer-item-top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.drawer-item-time {
  font-size: 12px;
  color: #64748b;
}
.drawer-item-title {
  margin-top: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}
.drawer-item-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #94a3b8;
}
.drawer-item-cont {
  font-size: 11.5px;
  color: #64748b;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--tp-bg-subtle);
}
</style>
