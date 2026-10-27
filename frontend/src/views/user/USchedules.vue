<template>
  <div class="u-schedules">
    <!-- Header Section -->
    <div class="schedules-header">
      <div class="header-content">
        <div class="header-info">
          <h1 class="page-title">
            <el-icon class="title-icon"><Calendar /></el-icon>
            我的日程
          </h1>
          <p class="page-subtitle">管理和查看您的所有日程安排</p>
        </div>
        <div class="header-stats">
          <div class="stat-item">
            <div class="stat-number">{{ list.length }}</div>
            <div class="stat-label">总日程</div>
          </div>
          <div class="stat-item">
            <div class="stat-number">{{ pendingCount }}</div>
            <div class="stat-label">待完成</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Search Section -->
    <el-card class="search-card">
      <div class="search-content">
        <div class="search-filters">
          <el-input 
            v-model="q.keyword" 
            placeholder="搜索日程标题..." 
            clearable 
            class="search-input"
            @keyup.enter="onSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-date-picker 
            v-model="q.date" 
            type="date" 
            placeholder="选择日期" 
            class="date-picker"
            @change="onSearch"
          />
          <el-select v-model="q.status" placeholder="状态" clearable class="status-select" @change="onSearch">
            <el-option label="全部" value="" />
            <el-option label="待办" value="todo" />
            <el-option label="进行中" value="doing" />
            <el-option label="已完成" value="done" />
            <el-option label="已取消" value="canceled" />
          </el-select>
        </div>
        <div class="action-buttons">
          <el-button type="primary" @click="onSearch" :loading="loading" class="search-btn">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button type="success" @click="showCreateDialog = true" class="create-btn">
            <el-icon><Plus /></el-icon>
            创建日程
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- Schedules List -->
    <el-card class="schedules-card">
      <div v-if="loading" class="loading-state">
        <el-skeleton v-for="i in 5" :key="i" animated>
          <template #template>
            <div class="schedule-skeleton">
              <el-skeleton-item variant="h3" style="width: 40%;" />
              <el-skeleton-item variant="text" style="width: 60%;" />
              <el-skeleton-item variant="text" style="width: 30%;" />
            </div>
          </template>
        </el-skeleton>
      </div>

      <div v-else-if="list.length === 0" class="empty-state">
        <div class="empty-icon">
          <el-icon><Calendar /></el-icon>
        </div>
        <h3 class="empty-title">暂无日程</h3>
        <p class="empty-desc">您还没有创建任何日程，开始规划您的时间吧！</p>
      </div>

      <div v-else class="schedules-list">
        <div 
          v-for="schedule in list" 
          :key="schedule.scheduleId" 
          class="schedule-item"
          @click="$router.push(`/u/schedules/${schedule.scheduleId}`)"
        >
          <div class="schedule-status">
            <div :class="['status-dot', getStatusClass(schedule.status)]"></div>
          </div>
          <div class="schedule-content">
            <h3 class="schedule-title">{{ schedule.title }}</h3>
            <div class="schedule-meta">
              <span class="meta-item">
                <el-icon><Clock /></el-icon>
                截止: {{ formatDate(schedule.endTime) }}
              </span>
              <span :class="['status-tag', getStatusClass(schedule.status)]">
                {{ getStatusText(schedule.status) }}
              </span>
            </div>
          </div>
          <div class="schedule-action">
            <el-icon class="action-icon"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 创建日程对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建日程" width="500px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="createForm.title" placeholder="请输入日程标题" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="createForm.content" type="textarea" placeholder="请输入日程内容" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker 
            v-model="createForm.startTime" 
            type="datetime" 
            placeholder="选择开始时间" 
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker 
            v-model="createForm.endTime" 
            type="datetime" 
            placeholder="选择结束时间" 
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="createForm.location" placeholder="请输入地点" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="createForm.priority" placeholder="选择优先级" style="width: 100%">
            <el-option label="低" value="low" />
            <el-option label="中" value="medium" />
            <el-option label="高" value="high" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showCreateDialog = false">取消</el-button>
          <el-button type="primary" @click="createSchedule" :loading="createLoading">创建</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { reactive, ref, onMounted, computed } from 'vue'
import { scheduleApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { Calendar, Search, Clock, ArrowRight, Plus } from '@element-plus/icons-vue'

const auth = useAuthStore()
const list = ref([])
const loading = ref(false)
const q = reactive({ keyword: '', date: null, status: '' })

// 创建日程相关
const showCreateDialog = ref(false)
const createLoading = ref(false)
const createForm = reactive({
  title: '',
  content: '',
  startTime: null,
  endTime: null,
  location: '',
  priority: 'medium'
})

// 计算待完成数量（todo / doing）
const pendingCount = computed(() => {
  return list.value.filter(item => 
    item.status === 'todo' || item.status === 'doing'
  ).length
})

// 获取状态样式类（状态枚举：todo / doing / done / canceled）
function getStatusClass(status) {
  const statusMap = {
    'todo': 'status-pending',
    'doing': 'status-progress',
    'done': 'status-completed',
    'canceled': 'status-overdue'
  }
  return statusMap[status] || 'status-pending'
}

// 获取状态文本
function getStatusText(status) {
  const statusMap = {
    'todo': '待办',
    'doing': '进行中',
    'done': '已完成',
    'canceled': '已取消'
  }
  return statusMap[status] || '待办'
}

// 格式化日期
function formatDate(dateStr) {
  if (!dateStr) return '未设置'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

async function load() {
  loading.value = true
  try {
    const uid = auth.user?.userId
    // /schedule 返回分页对象 { records, total, page, size }；本页一次性取足够展示的条数
    const params = { page: 1, size: 200 }
    if (uid) params.creatorId = uid
    if (q.keyword) params.keyword = q.keyword
    if (q.status) params.status = q.status
    const page = await scheduleApi.page(params)
    let records = page?.records || []
    // 新接口没有「按日期」参数，日期筛选在前端按 startTime 的日期部分完成
    if (q.date) {
      const d = new Date(q.date)
      const ymd = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
      records = records.filter((r) => String(r.startTime || '').slice(0, 10) === ymd)
    }
    list.value = records
  } catch (e) {
    ElMessage.error('加载失败：' + (e.message||''))
  } finally {
    loading.value = false
  }
}

function onSearch() { 
  load() 
}

// 创建日程
async function createSchedule() {
  if (!createForm.title.trim()) {
    ElMessage.warning('请输入日程标题')
    return
  }
  if (!createForm.startTime) {
    ElMessage.warning('请选择开始时间')
    return
  }
  if (!createForm.endTime) {
    ElMessage.warning('请选择结束时间')
    return
  }
  if (new Date(createForm.endTime) <= new Date(createForm.startTime)) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  
  createLoading.value = true
  try {
    // 后端 LocalDateTime 按 ISO-8601 解析（如 2024-05-01T09:00:00）
    const toIsoLocal = (date) => {
      const d = new Date(date)
      const pad = (n) => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
    }
    
    const scheduleData = {
      title: createForm.title,
      content: createForm.content,
      startTime: toIsoLocal(createForm.startTime),
      endTime: toIsoLocal(createForm.endTime),
      location: createForm.location,
      priority: createForm.priority
    }
    
    // POST /schedule，创建人由后端从 JWT 取
    await scheduleApi.create(scheduleData)
    ElMessage.success('日程创建成功')
    
    // 重置表单
    Object.assign(createForm, {
      title: '',
      content: '',
      startTime: null,
      endTime: null,
      location: '',
      priority: 'medium'
    })
    
    showCreateDialog.value = false
    load() // 重新加载列表
  } catch (e) {
    ElMessage.error('创建失败：' + (e.message || ''))
  } finally {
    createLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.u-schedules {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

/* Header Section */
.schedules-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 24px;
  color: white;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-icon {
  font-size: 32px;
}

.page-subtitle {
  margin: 0;
  font-size: 16px;
  opacity: 0.9;
}

.header-stats {
  display: flex;
  gap: 20px;
}

.stat-item {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  padding: 16px 24px;
  text-align: center;
  backdrop-filter: blur(10px);
}

.stat-number {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  opacity: 0.9;
}

/* Search Section */
.search-card {
  margin-bottom: 24px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  border: none;
}

.search-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.search-filters {
  display: flex;
  gap: 12px;
  flex: 1;
}

.search-input {
  width: 300px;
}

.date-picker {
  width: 200px;
}

.status-select {
  width: 150px;
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.search-btn {
  min-width: 100px;
}

.create-btn {
  min-width: 120px;
}

/* Schedules Section */
.schedules-card {
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  border: none;
  min-height: 400px;
}

/* Loading State */
.loading-state {
  padding: 20px;
}

.schedule-skeleton {
  padding: 20px;
  border-bottom: 1px solid #f0f2f5;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* Empty State */
.empty-state {
  text-align: center;
  padding: 80px 20px;
}

.empty-icon {
  font-size: 64px;
  color: #c0c4cc;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 20px;
  color: #606266;
  margin: 0 0 8px 0;
}

.empty-desc {
  color: #909399;
  margin: 0;
}

/* Schedules List */
.schedules-list {
  padding: 0;
}

.schedule-item {
  display: flex;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #f0f2f5;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
}

.schedule-item:hover {
  background: #f8f9fa;
  transform: translateX(4px);
}

.schedule-item:last-child {
  border-bottom: none;
}

.schedule-status {
  margin-right: 16px;
}

.status-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  display: inline-block;
}

.status-pending .status-dot {
  background: #e6a23c;
}

.status-progress .status-dot {
  background: #409eff;
}

.status-completed .status-dot {
  background: #67c23a;
}

.status-overdue .status-dot {
  background: #f56c6c;
}

.schedule-content {
  flex: 1;
}

.schedule-title {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
}

.schedule-meta {
  display: flex;
  align-items: center;
  gap: 16px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: #606266;
}

.status-tag {
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.status-pending {
  background: var(--tp-bg-subtle);
  color: #e6a23c;
}

.status-progress {
  background: #ecf5ff;
  color: #409eff;
}

.status-completed {
  background: #f0f9ff;
  color: #67c23a;
}

.status-overdue {
  background: #fef0f0;
  color: #f56c6c;
}

.schedule-action {
  margin-left: 16px;
}

.action-icon {
  font-size: 16px;
  color: #c0c4cc;
  transition: all 0.3s ease;
}

.schedule-item:hover .action-icon {
  color: #409eff;
  transform: translateX(4px);
}

/* Responsive Design */
@media (max-width: 768px) {
  .u-schedules {
    padding: 16px;
  }
  
  .schedules-header {
    padding: 24px;
  }
  
  .header-content {
    flex-direction: column;
    gap: 20px;
    text-align: center;
  }
  
  .page-title {
    font-size: 24px;
  }
  
  .header-stats {
    justify-content: center;
  }
  
  .search-content {
    flex-direction: column;
    align-items: stretch;
  }
  
  .search-filters {
    flex-direction: column;
  }
  
  .search-input,
  .date-picker,
  .status-select {
    width: 100%;
  }
  
  .schedule-item {
    padding: 16px;
  }
  
  .schedule-meta {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
