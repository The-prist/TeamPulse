<template>
  <div class="admin-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">日程管理</h1>
        <p class="page-subtitle">日历视图看时间分布；需要批量操作、导出时切到列表</p>
      </div>
      <el-radio-group v-model="viewMode" size="default">
        <el-radio-button label="calendar">日历</el-radio-button>
        <el-radio-button label="list">列表</el-radio-button>
      </el-radio-group>
    </div>

    <ScheduleCalendar v-if="viewMode === 'calendar'" @create="openCreateDialog" />

    <el-card v-else class="content-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">检索条件</span>
          <el-radio-group v-model="query.order" size="small" @change="load">
            <el-radio-button label="desc">开始时间倒序（新在上）</el-radio-button>
            <el-radio-button label="asc">开始时间正序（老在上）</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="标题或地点" clearable style="width: 170px" @keyup.enter="reload" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker
            v-model="query.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
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
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="创建人">
          <el-select v-model="query.creatorId" placeholder="全部" clearable filterable style="width: 150px">
            <el-option v-for="u in users" :key="u.userId" :label="displayName(u)" :value="u.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="待办" value="todo" />
            <el-option label="进行中" value="doing" />
            <el-option label="已完成" value="done" />
            <el-option label="已取消" value="canceled" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="success" @click="openCreateDialog">新增日程</el-button>
          <el-button @click="exportCSV('schedules.csv', ['scheduleId','title','status','priority'])">导出CSV</el-button>
        </el-form-item>
      </el-form>

      <div class="result-bar">
        <span>共 <b>{{ total }}</b> 条日程</span>
        <span class="result-range" v-if="rangeText">{{ rangeText }}</span>
        <span class="logic-hint">条件之间是「且」的关系，只填一个也能查；时间段按开始时间筛选</span>
      </div>

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
      <el-table :data="pagedData" v-loading="loading" @sort-change="onSortChange">
        <el-table-column prop="scheduleId" label="编号" width="70" />
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag size="small" :type="scheduleStatusTagType(row.status)">{{ scheduleStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="86">
          <template #default="{ row }">{{ priorityText(row.priority) }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="146">
          <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="146">
          <template #default="{ row }">{{ formatDateTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="location" label="地点" width="120" show-overflow-tooltip />
        <el-table-column label="创建人" width="100">
          <template #default="{ row }">{{ row.creatorName || ('用户 #' + row.creatorId) }}</template>
        </el-table-column>
        <el-table-column label="团队" width="110">
          <template #default="{ row }">{{ row.teamName || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="$router.push(`/schedule/${row.scheduleId}`)">详情</el-button>
              <el-button link type="warning" @click="editSchedule(row)">修改</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-box">
            <el-empty :description="emptyText" :image-size="70" />
            <el-button v-if="hasFilter" size="small" @click="resetQuery">清空筛选条件</el-button>
          </div>
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="(v) => (page = v)"
          @size-change="(v) => (pageSize = v)"
        />
      </div>
    </el-card>
  
  <!-- 修改日程对话框 -->
  <el-dialog v-model="editDialogVisible" title="修改日程" width="600px">
    <el-form :model="editForm" label-width="100px" ref="editFormRef">
      <el-form-item label="标题" prop="title" :rules="[{ required: true, message: '请输入标题' }]">
        <el-input v-model="editForm.title" placeholder="请输入日程标题" />
      </el-form-item>
      <el-form-item label="内容" prop="content">
        <el-input v-model="editForm.content" type="textarea" :rows="3" placeholder="请输入日程内容" />
      </el-form-item>
      <el-form-item label="开始时间" prop="startTime" :rules="[{ required: true, message: '请选择开始时间' }]">
        <el-date-picker
          v-model="editForm.startTime"
          type="datetime"
          placeholder="选择开始时间"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DD HH:mm"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="结束时间" prop="endTime" :rules="[{ required: true, message: '请选择结束时间' }]">
        <el-date-picker
          v-model="editForm.endTime"
          type="datetime"
          placeholder="选择结束时间"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DD HH:mm"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="地点" prop="location">
        <el-input v-model="editForm.location" placeholder="请输入地点" />
      </el-form-item>
      <el-form-item label="优先级" prop="priority">
        <el-select v-model="editForm.priority" placeholder="请选择优先级" style="width: 100%">
          <el-option label="低" value="low" />
          <el-option label="中" value="medium" />
          <el-option label="高" value="high" />
        </el-select>
      </el-form-item>
      <el-form-item label="提醒时间" prop="remindTime">
        <el-date-picker
          v-model="editForm.remindTime"
          type="datetime"
          placeholder="选择提醒时间（可选）"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DD HH:mm"
          style="width: 100%"
          clearable
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSchedule" :loading="saving">保存</el-button>
      </span>
    </template>
  </el-dialog>
  
  <!-- 新增日程对话框 -->
  <el-dialog v-model="createDialogVisible" title="新增日程" width="600px">
    <el-form :model="createForm" label-width="100px" ref="createFormRef">
      <el-form-item label="标题" prop="title" :rules="[{ required: true, message: '请输入标题' }]">
        <el-input v-model="createForm.title" placeholder="请输入日程标题" />
      </el-form-item>
      <el-form-item label="内容" prop="content">
        <el-input v-model="createForm.content" type="textarea" :rows="3" placeholder="请输入日程内容" />
      </el-form-item>
      <el-form-item label="开始时间" prop="startTime" :rules="[{ required: true, message: '请选择开始时间' }]">
        <el-date-picker
          v-model="createForm.startTime"
          type="datetime"
          placeholder="选择开始时间"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DD HH:mm"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="结束时间" prop="endTime" :rules="[{ required: true, message: '请选择结束时间' }]">
        <el-date-picker
          v-model="createForm.endTime"
          type="datetime"
          placeholder="选择结束时间"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DD HH:mm"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="地点" prop="location">
        <el-input v-model="createForm.location" placeholder="请输入地点" />
      </el-form-item>
      <el-form-item label="优先级" prop="priority">
        <el-select v-model="createForm.priority" placeholder="请选择优先级" style="width: 100%">
          <el-option label="低" value="low" />
          <el-option label="中" value="medium" />
          <el-option label="高" value="high" />
        </el-select>
      </el-form-item>
      <el-form-item label="提醒时间" prop="remindTime">
        <el-date-picker
          v-model="createForm.remindTime"
          type="datetime"
          placeholder="选择提醒时间（可选）"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DD HH:mm"
          style="width: 100%"
          clearable
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createSchedule" :loading="creating">创建</el-button>
      </span>
    </template>
  </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { scheduleApi, userApi } from '@/api'
import { scheduleStatusText, scheduleStatusTagType, priorityText } from '@/utils/dict'
import { useTable } from '@/composables/useTable'
import ScheduleCalendar from './ScheduleCalendar.vue'

/** 视图模式：日历（默认，更直观）/ 列表（便于批量编辑与导出 CSV） */
const viewMode = ref('calendar')

const { data, setData, loading, total, page, pageSize, pagedData, onSortChange, isColVisible, exportCSV } = useTable({ pageSize: 10, defaultVisible: ['title','status','priority'] })
// 加载失败与「没有数据」是两件事，用独立标志驱动告警与重试
const loadError = ref(false)

/** 列表筛选条件（条件之间是「且」，只填一个也能查） */
const query = reactive({
  keyword: '',
  /** ['YYYY-MM-DD','YYYY-MM-DD']，按开始时间 */
  dateRange: null,
  /** ['HH:mm','HH:mm']，按开始时间的时段 */
  timeRange: null,
  creatorId: null,
  status: '',
  /** 默认开始时间倒序：新日程在上 */
  order: 'desc',
})

const users = ref([])

const hasFilter = computed(
  () =>
    !!query.keyword.trim() ||
    !!query.dateRange?.length ||
    !!query.timeRange?.length ||
    !!query.creatorId ||
    !!query.status,
)

const emptyText = computed(() => (hasFilter.value ? '没有符合条件的日程' : '还没有任何日程'))

const rangeText = computed(() => {
  const parts = []
  if (query.dateRange?.length === 2) parts.push(`开始日期 ${query.dateRange[0]} ~ ${query.dateRange[1]}`)
  if (query.timeRange?.length === 2) parts.push(`时段 ${query.timeRange[0]} ~ ${query.timeRange[1]}`)
  if (query.creatorId) {
    const u = users.value.find((x) => x.userId === query.creatorId)
    parts.push(`创建人 ${u ? displayName(u) : '#' + query.creatorId}`)
  }
  if (query.status) parts.push(`状态 ${scheduleStatusText(query.status)}`)
  return parts.join(' · ')
})

function displayName(user) {
  return user.nickname || user.username || '#' + user.userId
}

function formatDateTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function loadUsers() {
  try {
    const userPage = await userApi.page({ page: 1, size: 200 })
    users.value = userPage?.records || []
  } catch {
    users.value = []
  }
}

// 修改日程相关
const editDialogVisible = ref(false)
const editFormRef = ref()
const saving = ref(false)
const editForm = ref({
  scheduleId: null,
  creatorId: null,
  title: '',
  content: '',
  startTime: '',
  endTime: '',
  location: '',
  priority: 'medium',
  remindTime: ''
})

// 新增日程相关
const createDialogVisible = ref(false)
const createFormRef = ref()
const creating = ref(false)
const createForm = ref({
  title: '',
  content: '',
  startTime: '',
  endTime: '',
  location: '',
  priority: 'medium',
  remindTime: ''
})

// 后端 LocalDateTime 按 ISO-8601 解析（如 2024-05-01T09:00:00），不能再用 "yyyy-MM-dd HH:mm"
function toIsoLocal(date) {
  if (!date) return null
  const d = new Date(date)
  if (isNaN(d.getTime())) return null
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}

async function load() {
  loading.value = true
  loadError.value = false
  try {
    // /schedule：筛选交给后端，单页取满 200 条后在前端分页/排序
    const params = { page: 1, size: 200, order: query.order }
    if (query.keyword.trim()) params.keyword = query.keyword.trim()
    if (query.dateRange?.length === 2) {
      params.from = query.dateRange[0]
      params.to = query.dateRange[1]
    }
    if (query.timeRange?.length === 2) {
      params.timeFrom = query.timeRange[0]
      params.timeTo = query.timeRange[1]
    }
    if (query.creatorId) params.creatorId = query.creatorId
    if (query.status) params.status = query.status

    const resp = await scheduleApi.page(params)
    setData(resp?.records || [])
    page.value = 1
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}
/** 改筛选条件回到第 1 页 */
function reload() {
  page.value = 1
  load()
}

function resetQuery() {
  query.keyword = ''
  query.dateRange = null
  query.timeRange = null
  query.creatorId = null
  query.status = ''
  query.order = 'desc'
  page.value = 1
  load()
}

// 编辑日程函数
function editSchedule(row) {
  editForm.value = {
    scheduleId: row.scheduleId,
    creatorId: row.creatorId,
    title: row.title || '',
    content: row.content || '',
    startTime: row.startTime || '',
    endTime: row.endTime || '',
    location: row.location || '',
    priority: row.priority || 'medium',
    remindTime: row.remindTime || ''
  }
  editDialogVisible.value = true
}

// 保存日程函数
async function saveSchedule() {
  if (!editForm.value.title) {
    ElMessage.error('请输入日程标题')
    return
  }
  
  saving.value = true
  try {
    const updateData = {
      title: editForm.value.title,
      content: editForm.value.content,
      startTime: toIsoLocal(editForm.value.startTime),
      endTime: toIsoLocal(editForm.value.endTime),
      location: editForm.value.location,
      priority: editForm.value.priority,
      remindTime: toIsoLocal(editForm.value.remindTime)
    }
    
    
    // PUT /schedule/{id}
    const result = await scheduleApi.update(editForm.value.scheduleId, updateData)
    
    ElMessage.success('日程修改成功')
    editDialogVisible.value = false
    await load() // 重新加载数据
  } catch (error) {
    console.error('保存日程失败:', error)
    ElMessage.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

// 打开新增对话框
// dateStr 可选：日历里在某一天点「+」时传入 yyyy-MM-dd，预填当天的 09:00-10:00
function openCreateDialog(dateStr) {
  const day = typeof dateStr === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(dateStr) ? dateStr : ''
  // 重置表单
  createForm.value = {
    title: '',
    content: '',
    startTime: day ? `${day} 09:00` : '',
    endTime: day ? `${day} 10:00` : '',
    location: '',
    priority: 'medium',
    remindTime: ''
  }
  createDialogVisible.value = true
}

// 创建日程函数
async function createSchedule() {
  if (!createForm.value.title) {
    ElMessage.error('请输入日程标题')
    return
  }
  
  if (!createForm.value.startTime) {
    ElMessage.error('请选择开始时间')
    return
  }
  
  if (!createForm.value.endTime) {
    ElMessage.error('请选择结束时间')
    return
  }
  
  creating.value = true
  try {
    const scheduleData = {
      title: createForm.value.title,
      content: createForm.value.content,
      startTime: toIsoLocal(createForm.value.startTime),
      endTime: toIsoLocal(createForm.value.endTime),
      location: createForm.value.location,
      priority: createForm.value.priority,
      remindTime: toIsoLocal(createForm.value.remindTime)
    }
    
    console.log('发送创建数据:', scheduleData)
    
    // POST /schedule，创建人由后端从 JWT 取
    const result = await scheduleApi.create(scheduleData)
    console.log('创建响应:', result)
    
    ElMessage.success('日程创建成功')
    createDialogVisible.value = false
    await load() // 重新加载数据
  } catch (error) {
    console.error('创建日程失败:', error)
    ElMessage.error('创建失败，请重试')
  } finally {
    creating.value = false
  }
}

onMounted(async () => {
  await loadUsers()
  await load()
})
</script>

<style scoped>
.admin-page {
  padding: 16px;
  background: var(--tp-bg-subtle);
  min-height: 100vh;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 650;
  color: var(--tp-text);
  margin: 0 0 8px 0;
}

.page-subtitle {
  font-size: 12.5px;
  color: var(--tp-text-muted);
  margin: 0;
}

.content-card {
  border-radius: 12px;
  border: 1px solid var(--tp-border);
}

/* 筛选栏：与日志/评论管理保持同一套写法 */
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
  padding: 0 2px 10px;
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #374151;
}

.header-actions {
  display: flex;
  align-items: center;
}

:deep(.el-card__header) {
  padding: 20px 24px;
  border-bottom: 1px solid var(--tp-border);
  background: var(--tp-bg-page);
}

:deep(.el-card__body) {
  padding: 24px;
}

:deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-table th) {
  background: var(--tp-bg-subtle);
  color: #374151;
  font-weight: 600;
}

:deep(.el-table td) {
  border-bottom: 1px solid var(--tp-border);
}

:deep(.el-table tr:hover > td) {
  background: var(--tp-bg-subtle);
}

:deep(.el-pagination) {
  justify-content: flex-end;
}
</style>

<style scoped>
.actions { margin-bottom: 12px; display: flex; gap: 8px; }

.load-error {
  margin-bottom: 10px;
  border-radius: 8px;
}
</style>
