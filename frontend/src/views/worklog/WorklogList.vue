<!--
  工作日志管理。

  两个视图：
    · 日历（默认）：格子里只有蓝色圆底姓氏，点开看当日全部日志
    · 列表：检索 + 编辑 + 删除

  列表的检索条件与管理端其它页面保持一致的口径：
    关键词 / 日志日期区间 / 时间段（提交时间）/ 提交人，条件之间是「且」，
    只填一个也能查；默认按日期正序、单页取满 200 篇（进页面即看到全部）。
-->
<template>
  <div class="admin-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">日志管理</h1>
        <p class="page-subtitle">日历看「谁在哪天交了日志」，列表做检索与编辑</p>
      </div>
      <el-radio-group v-model="viewMode" size="default">
        <el-radio-button label="calendar">日历</el-radio-button>
        <el-radio-button label="list">列表</el-radio-button>
      </el-radio-group>
    </div>

    <WorklogCalendar v-if="viewMode === 'calendar'" />

    <template v-else>
      <el-card class="content-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">检索条件</span>
            <el-radio-group v-model="query.order" size="small" @change="reload">
              <el-radio-button label="desc">日期倒序（新在上）</el-radio-button>
              <el-radio-button label="asc">日期正序（老在上）</el-radio-button>
            </el-radio-group>
          </div>
        </template>

        <el-form :inline="true" class="filter-form" @submit.prevent>
          <el-form-item label="关键词">
            <el-input
              v-model="query.keyword"
              placeholder="标题或内容包含"
              clearable
              style="width: 190px"
              @keyup.enter="reload"
            />
          </el-form-item>

          <el-form-item label="日志日期">
            <el-date-picker
              v-model="query.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 240px"
            />
          </el-form-item>

          <el-form-item label="时间段">
            <el-time-picker
              v-model="query.timeRange"
              is-range
              range-separator="至"
              start-placeholder="起"
              end-placeholder="止"
              format="HH:mm"
              value-format="HH:mm"
              style="width: 210px"
            />
          </el-form-item>

          <el-form-item label="提交人">
            <el-select
              v-model="query.creatorId"
              placeholder="全部"
              clearable
              filterable
              style="width: 170px"
            >
              <el-option
                v-for="u in users"
                :key="u.userId"
                :label="displayName(u)"
                :value="u.userId"
              />
            </el-select>
          </el-form-item>

          <el-form-item class="filter-actions">
            <el-button type="primary" @click="reload">查询</el-button>
            <el-button @click="resetQuery">重置</el-button>
            <el-button type="success" @click="openCreate">新增日志</el-button>
          </el-form-item>
        </el-form>

        <div class="result-bar">
          <span>共 <b>{{ total }}</b> 篇日志</span>
          <span class="result-range" v-if="rangeText">{{ rangeText }}</span>
          <span class="logic-hint">条件之间是「且」的关系，只填一个也能查；时间段按「提交时间」筛选</span>
          <span v-if="truncated" class="warn-text">单页最多 200 篇，请缩小日期范围或翻页</span>
        </div>
      </el-card>

      <el-card class="content-card list-card" shadow="never">
        <!-- 加载失败与「没有数据」要能区分，否则用户会误以为真的没有数据 -->
        <el-alert
          v-if="loadError"
          type="error"
          :closable="false"
          show-icon
          class="load-error"
          title="数据加载失败：网络或后端异常，当前显示的可能不是最新数据"
        >
          <el-button size="small" type="primary" link :loading="loading" @click="load">重新加载</el-button>
        </el-alert>
        <el-table :data="list" size="small" v-loading="loading" :row-key="(row) => row.worklogId">
          <template #empty>
            <div class="empty-box">
              <el-empty :description="emptyText" :image-size="70" />
              <el-button v-if="hasFilter" size="small" @click="resetQuery">清空筛选条件</el-button>
            </div>
          </template>

          <el-table-column prop="worklogId" label="编号" width="70" />
          <el-table-column prop="logDate" label="日志日期" width="110" sortable />
          <el-table-column label="提交人" width="100">
            <template #default="{ row }">
              {{ row.creatorName || getUserName(row.creatorId) }}
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
          <el-table-column label="内容" min-width="220">
            <template #default="{ row }">
              <span class="content-cell">{{ filterSensitiveWords(row.content || '') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="关联" min-width="150">
            <template #default="{ row }">
              <span v-if="row.scheduleTitle">日程：{{ row.scheduleTitle }}</span>
              <span v-if="row.scheduleTitle && row.teamName"> · </span>
              <span v-if="row.teamName">团队：{{ row.teamName }}</span>
              <span v-if="!row.scheduleTitle && !row.teamName" class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="提交时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="152" fixed="right" v-if="hasAnyAction">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" @click="openEdit(row)" v-if="canEditRow(row)">编辑</el-button>
                <el-button link type="success" @click="$router.push(`/worklogs/${row.worklogId}`)">详情</el-button>
                <el-popconfirm title="确认删除该日志吗？" @confirm="remove(row)" v-if="canEditRow(row)">
                  <template #reference>
                    <el-button link type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="query.page"
            v-model:page-size="query.size"
            :total="total"
            :page-sizes="[50, 100, 200]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="onSizeChange"
            @current-change="onPageChange"
          />
        </div>
      </el-card>
    </template>

    <!-- 新增 / 编辑 -->
    <el-drawer v-model="drawer.visible" :title="drawer.title" size="40%">
      <el-form :model="form" label-width="100px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" rows="5" /></el-form-item>
        <el-form-item label="日志日期">
          <el-date-picker v-model="form.logDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="关联日程"><el-input v-model.number="form.relatedScheduleId" /></el-form-item>
        <el-form-item label="所属团队"><el-input v-model.number="form.relatedTeamId" /></el-form-item>
      </el-form>
      <template #footer>
        <div style="text-align: right">
          <el-button @click="drawer.visible = false">取消</el-button>
          <el-button type="primary" @click="submit">保存</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { userApi, worklogApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { filterSensitiveWords } from '@/utils/sensitive'
import WorklogCalendar from './WorklogCalendar.vue'

const auth = useAuthStore()

/** 视图模式：日历（默认）/ 列表 */
const viewMode = ref('calendar')

const list = ref([])
const loading = ref(false)
// 加载失败与「没有数据」是两件事，用独立标志驱动告警与重试
const loadError = ref(false)
const total = ref(0)
const users = ref([])

const query = reactive({
  keyword: '',
  /** ['YYYY-MM-DD','YYYY-MM-DD']，按日志日期 */
  dateRange: null,
  /** ['HH:mm','HH:mm']，按提交时间 */
  timeRange: null,
  creatorId: null,
  /** 默认日期倒序：新日期在上、老日期在下 */
  order: 'desc',
  page: 1,
  /** 默认一次取满 200 条（后端上限），实现「进页面就看到全部」 */
  size: 200,
})

const drawer = reactive({ visible: false, title: '' })
const form = reactive({
  worklogId: null,
  title: '',
  content: '',
  logDate: '',
  relatedScheduleId: null,
  relatedTeamId: null,
})

/** 用户映射：用于表格里回退显示（后端 creatorName 已是昵称优先） */
const userMap = ref(new Map())

const truncated = computed(() => total.value > list.value.length && list.value.length >= query.size)

const hasFilter = computed(
  () =>
    !!query.keyword.trim() ||
    !!query.dateRange?.length ||
    !!query.timeRange?.length ||
    !!query.creatorId,
)

const emptyText = computed(() => (hasFilter.value ? '没有符合条件的日志' : '还没有任何日志'))

const rangeText = computed(() => {
  const parts = []
  if (query.dateRange?.length === 2) parts.push(`日期 ${query.dateRange[0]} ~ ${query.dateRange[1]}`)
  if (query.timeRange?.length === 2) parts.push(`提交时间 ${query.timeRange[0]} ~ ${query.timeRange[1]}`)
  if (query.creatorId) {
    const u = users.value.find((x) => x.userId === query.creatorId)
    parts.push(`提交人 ${u ? displayName(u) : '#' + query.creatorId}`)
  }
  return parts.join(' · ')
})

function displayName(user) {
  return user.nickname || user.username || '#' + user.userId
}

function getUserName(creatorId) {
  if (!creatorId) return '-'
  const user = userMap.value.get(creatorId)
  // 优先昵称（真实姓名），没有再退回登录账号
  return user ? user.nickname || user.username || creatorId : creatorId
}

function formatDateTime(dateTime) {
  if (!dateTime) return '-'
  return String(dateTime).replace('T', ' ').slice(0, 16)
}

/** 表格里的作者名回退用：把本页出现的 creatorId 补进映射 */
async function ensureUserMap() {
  const ids = [...new Set(list.value.map((x) => x.creatorId).filter(Boolean))]
  if (!ids.length) return
  try {
    const page = await userApi.page({ page: 1, size: 200 })
    for (const u of page?.records || []) {
      userMap.value.set(u.userId, { username: u.username, nickname: u.nickname })
    }
  } catch {
    /* 拿不到用户表时表格会退回显示 creatorName / ID，不影响主流程 */
  }
}

async function loadUsers() {
  if (auth.dataScope !== 'ALL') return
  try {
    const page = await userApi.page({ page: 1, size: 200 })
    users.value = page?.records || []
  } catch {
    users.value = []
  }
}

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const params = { order: query.order, page: query.page, size: query.size }

    // 数据范围非全公司时，前端先把条件收窄到本人（后端还有一道 DataScope 兜底）
    if (auth.dataScope !== 'ALL') {
      const me = auth.user?.userId
      if (me) params.creatorId = me
    } else if (query.creatorId) {
      params.creatorId = query.creatorId
    }

    if (query.keyword.trim()) params.keyword = query.keyword.trim()
    if (query.dateRange?.length === 2) {
      params.from = query.dateRange[0]
      params.to = query.dateRange[1]
    }
    if (query.timeRange?.length === 2) {
      params.timeFrom = query.timeRange[0]
      params.timeTo = query.timeRange[1]
    }

    const page = await worklogApi.page(params)
    list.value = page?.records || []
    total.value = Number(page?.total || 0)
    await ensureUserMap()
  } catch (e) {
    loadError.value = true
    ElMessage.error('加载失败：' + (e.message || ''))
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 改筛选条件要回到第 1 页，否则可能停在越界页码上看到空列表 */
function reload() {
  query.page = 1
  load()
}

function resetQuery() {
  query.keyword = ''
  query.dateRange = null
  query.timeRange = null
  query.creatorId = null
  query.order = 'desc'
  query.page = 1
  query.size = 200
  load()
}

function onPageChange(p) {
  query.page = p
  load()
}

function onSizeChange(s) {
  query.size = s
  query.page = 1
  load()
}

function openCreate() {
  Object.assign(form, {
    worklogId: null,
    title: '',
    content: '',
    logDate: new Date().toISOString().slice(0, 10),
    relatedScheduleId: null,
    relatedTeamId: null,
  })
  drawer.title = '新增日志'
  drawer.visible = true
}

function openEdit(row) {
  Object.assign(form, {
    worklogId: row.worklogId,
    title: row.title,
    content: row.content,
    logDate: String(row.logDate || '').slice(0, 10),
    relatedScheduleId: row.relatedScheduleId ?? null,
    relatedTeamId: row.relatedTeamId ?? null,
  })
  drawer.title = '编辑日志'
  drawer.visible = true
}

async function submit() {
  try {
    // 记录人由后端从 JWT 取，请求体不带 creatorId
    const payload = { ...form }
    if (payload.logDate) payload.logDate = String(payload.logDate).slice(0, 10)

    // 过滤空值，避免后端外键/非空校验报错
    Object.keys(payload).forEach((key) => {
      if (payload[key] === null || payload[key] === undefined || payload[key] === '') {
        delete payload[key]
      }
    })

    if (form.worklogId) {
      await worklogApi.update(form.worklogId, payload)
      ElMessage.success('更新成功')
    } else {
      await worklogApi.create(payload)
      ElMessage.success('创建成功')
    }
    drawer.visible = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || ''))
  }
}

/** 与后端一致：日志作者本人 或 拥有 worklog:manage 才能编辑/删除 */
function canEditRow(row) {
  if (auth.hasPerm('worklog:manage')) return true
  const me = auth.user?.userId
  return !!row && row.creatorId != null && me != null && row.creatorId === me
}

/** 本页当前用户至少对某行有操作权时才显示操作列，避免空列 */
const hasAnyAction = computed(() => auth.hasPerm('worklog:manage') || rows.value.some((r) => canEditRow(r)))

async function remove(row) {
  try {
    await worklogApi.remove(row.worklogId)
    ElMessage.success('删除成功')
    if (list.value.length === 1 && query.page > 1) query.page -= 1
    await load()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || ''))
  }
}

onMounted(async () => {
  await loadUsers()
  await load()
})
</script>

<style scoped>
.admin-page {
  display: grid;
  gap: 14px;
  padding: 16px;
  background-color: var(--tp-bg-page);
  min-height: 100%;
}
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}
.page-title {
  font-size: 18px;
  font-weight: 650;
  color: var(--tp-text);
  margin: 0;
}
.page-subtitle {
  margin: 5px 0 0;
  font-size: 12.5px;
  color: var(--tp-text-muted);
}
.content-card {
  border-radius: 10px;
}
.list-card {
  margin-top: 0;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tp-text);
}
.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0 8px;
}
.filter-form :deep(.el-form-item) {
  margin-bottom: 10px;
  margin-right: 0;
}
.filter-form .filter-actions {
  margin-left: auto;
}
.result-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 2px 2px;
  font-size: 12.5px;
  color: var(--tp-text-muted);
  flex-wrap: wrap;
}
.result-bar b {
  color: var(--tp-text);
}
.logic-hint {
  color: var(--tp-text-faint);
}
.warn-text {
  color: #d97706;
}
.muted {
  color: var(--tp-text-faint);
}
.content-cell {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.empty-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 0;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.load-error {
  margin-bottom: 10px;
  border-radius: 8px;
}
</style>
