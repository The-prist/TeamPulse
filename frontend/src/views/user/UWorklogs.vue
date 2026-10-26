<template>
  <div class="worklogs-container">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <h1 class="page-title">我的工作日志</h1>
          <p class="page-subtitle">记录每日工作与沉淀</p>
        </div>
        <div class="header-stats">
          <div class="stat-item">
            <div class="stat-number">{{ total }}</div>
            <div class="stat-label">总日志数</div>
          </div>
          <div class="stat-item">
            <div class="stat-number">{{ thisMonthCount }}</div>
            <div class="stat-label">本月新增</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <div class="search-content">
        <div class="search-filters">
          <div class="filter-item">
            <el-icon class="filter-icon"><Search /></el-icon>
            <el-input 
              v-model="q.keyword" 
              placeholder="搜索日志标题或内容" 
              clearable 
              class="search-input"
              @keyup.enter="onSearch"
            />
          </div>
          <div class="filter-item">
            <el-icon class="filter-icon"><Calendar /></el-icon>
            <el-date-picker 
              v-model="q.date" 
              type="date" 
              placeholder="选择日期" 
              class="date-picker"
            />
          </div>
        </div>
        <div class="search-actions">
          <el-button type="primary" @click="onSearch" :icon="Search">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="openCreate" :icon="Plus">新增日志</el-button>
        </div>
      </div>
    </el-card>

    <!-- 日志列表 -->
    <el-card class="list-card" shadow="never">
      <div v-loading="loading" class="list-content">
        <!-- 加载骨架屏 -->
        <div v-if="loading" class="loading-skeleton">
          <div v-for="i in 5" :key="i" class="skeleton-item">
            <el-skeleton animated>
              <template #template>
                <div class="skeleton-worklog">
                  <el-skeleton-item variant="h3" style="width: 60%; margin-bottom: 8px;" />
                  <el-skeleton-item variant="text" style="width: 40%; margin-bottom: 4px;" />
                  <el-skeleton-item variant="text" style="width: 80%;" />
                </div>
              </template>
            </el-skeleton>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else-if="list.length === 0" class="empty-state">
          <el-icon class="empty-icon"><Document /></el-icon>
          <h3>暂无工作日志</h3>
          <p>开始记录你的第一条工作日志吧</p>
          <el-button type="primary" @click="openCreate" :icon="Plus">新增日志</el-button>
        </div>

        <!-- 日志列表 -->
        <div v-else class="worklogs-list">
          <div 
            v-for="worklog in list" 
            :key="worklog.worklogId" 
            class="worklog-item"
            @click="$router.push(`/u/worklogs/${worklog.worklogId}`)"
          >
            <div class="worklog-header">
              <div class="worklog-title">
                <el-icon class="title-icon"><Document /></el-icon>
                {{ worklog.title || '无标题' }}
              </div>
              <div class="worklog-actions" @click.stop>
                <el-button size="small" text @click="openEdit(worklog)">
                  <el-icon><Edit /></el-icon>
                </el-button>
                <el-popconfirm title="确认删除该日志吗？" @confirm="remove(worklog)">
                  <template #reference>
                    <el-button size="small" text type="danger">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </template>
                </el-popconfirm>
                <el-button size="small" text type="primary">
                  <el-icon><ArrowRight /></el-icon>
                </el-button>
              </div>
            </div>
            <div class="worklog-content">
              <p class="content-preview">{{ getContentPreview(worklog.content) }}</p>
            </div>
            <div class="worklog-meta">
              <div class="meta-item">
                <el-icon><Calendar /></el-icon>
                <span>{{ formatDate(worklog.logDate) }}</span>
              </div>
              <div class="meta-item">
                <el-icon><Clock /></el-icon>
                <span>{{ formatDateTime(worklog.createdAt) }}</span>
              </div>
              <div v-if="worklog.relatedScheduleId" class="meta-item">
                <el-icon><Link /></el-icon>
                <span>关联日程 #{{ worklog.relatedScheduleId }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div v-if="list.length > 0" class="pagination-wrapper">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10,20,50,100]"
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </div>
      </div>
    </el-card>
    <el-drawer v-model="drawer.visible" :title="drawer.title" size="40%">
      <el-form :model="form" label-width="100px">
        <el-form-item label="标题"><el-input v-model="form.title"/></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" rows="5"/></el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="form.logDate" type="date"/></el-form-item>
        <el-form-item label="关联日程"><el-input v-model.number="form.relatedScheduleId"/></el-form-item>
        <el-form-item label="所属团队"><el-input v-model.number="form.relatedTeamId"/></el-form-item>
      </el-form>
      <template #footer>
        <div style="text-align:right">
          <el-button @click="drawer.visible=false">取消</el-button>
          <el-button type="primary" @click="submit">保存</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>
<script setup>
import { reactive, ref, onMounted, computed } from 'vue'
import { worklogApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { Search, Calendar, Document, Edit, Delete, ArrowRight, Plus, Clock, Link } from '@element-plus/icons-vue'

const auth = useAuthStore()
const list = ref([])
const total = ref(0)
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const q = reactive({ keyword: '', date: null })
const drawer = reactive({ visible: false, title: '' })
const form = reactive({ worklogId: null, title: '', content: '', logDate: '', relatedScheduleId: null, relatedTeamId: null })

// 计算本月新增日志数量
const thisMonthCount = computed(() => {
  const now = new Date()
  const currentMonth = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0')
  return list.value.filter(item => {
    if (!item.logDate) return false
    const itemMonth = item.logDate.slice(0, 7)
    return itemMonth === currentMonth
  }).length
})

// 辅助函数
function getContentPreview(content) {
  if (!content) return '暂无内容'
  return content.length > 100 ? content.slice(0, 100) + '...' : content
}

function formatDate(date) {
  if (!date) return ''
  return new Date(date).toLocaleDateString('zh-CN')
}

function formatDateTime(datetime) {
  if (!datetime) return ''
  return new Date(datetime).toLocaleString('zh-CN')
}

function resetSearch() {
  q.keyword = ''
  q.date = null
  onSearch()
}

async function load() {
  loading.value = true
  try {
    const uid = auth.user?.userId
    const params = { page: currentPage.value, size: pageSize.value }
    if (uid) params.creatorId = uid
    if (q.keyword) params.keyword = q.keyword
    if (q.date) {
      // /worklog 的日期区间参数是 from / to（yyyy-MM-dd），不再是 date
      const ymd = new Date(q.date).toISOString().slice(0,10)
      params.from = ymd
      params.to = ymd
    }
    // /worklog 返回分页对象 { records, total, page, size }
    const page = await worklogApi.page(params)
    list.value = page?.records || []
    total.value = Number(page?.total || 0)
  } catch (e) {
    ElMessage.error('加载失败：' + (e.message||''))
  } finally {
    loading.value = false
  }
}

function onPageChange(p) { currentPage.value = p; load() }
function onSizeChange(s) { pageSize.value = s; currentPage.value = 1; load() }
function onSearch() { currentPage.value = 1; load() }

onMounted(load)

function openCreate() {
  Object.assign(form, { worklogId: null, title: '', content: '', logDate: '', relatedScheduleId: null, relatedTeamId: null })
  drawer.title = '新增日志'
  drawer.visible = true
}

function openEdit(row) {
  Object.assign(form, row)
  drawer.title = '编辑日志'
  drawer.visible = true
}

async function submit() {
  try {
    // 记录人由后端从 JWT 取
    const payload = { ...form }

    // 统一日期为 YYYY-MM-DD，避免后端 LocalDate 解析 400
    if (payload.logDate) {
      if (payload.logDate instanceof Date) {
        payload.logDate = payload.logDate.toISOString().slice(0, 10)
      } else if (typeof payload.logDate === 'string') {
        payload.logDate = payload.logDate.includes('T') ? payload.logDate.slice(0, 10) : payload.logDate
      }
    }

    // 去除空值，避免把空字符串/空关联传给后端
    Object.keys(payload).forEach(k => {
      if (payload[k] === '' || payload[k] == null) delete payload[k]
    })
  if (form.worklogId) {
      // PUT /worklog/{id}
      await worklogApi.update(form.worklogId, payload)
      ElMessage.success('更新成功')
    } else {
      // POST /worklog，返回 { worklogId }
      const res = await worklogApi.create(payload)
      if (res && res.worklogId) form.worklogId = res.worklogId
      ElMessage.success('创建成功')
    }
    drawer.visible = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message||''))
  }
}

async function remove(row) {
  try {
    // DELETE /worklog/{id}
    await worklogApi.remove(row.worklogId)
    ElMessage.success('删除成功')
    await load()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message||''))
  }
}
</script>

<style scoped>
.worklogs-container {
  padding: 20px;
  background: var(--tp-bg-subtle);
  min-height: 100vh;
}

/* 页面头部 */
.page-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 24px;
  color: white;
  position: relative;
  overflow: hidden;
}

.page-header::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><filter id="noiseFilter"><feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="1" stitchTiles="stitch"/><feColorMatrix type="saturate" values="0"/></filter></defs><rect width="100%" height="100%" filter="url(%23noiseFilter)" opacity="0.1"/></svg>') repeat;
  pointer-events: none;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  z-index: 1;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  margin: 0 0 8px 0;
  text-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.page-subtitle {
  font-size: 16px;
  opacity: 0.9;
  margin: 0;
}

.header-stats {
  display: flex;
  gap: 32px;
}

.stat-item {
  text-align: center;
}

.stat-number {
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  opacity: 0.8;
}

/* 搜索卡片 */
.search-card {
  margin-bottom: 24px;
  border-radius: 12px;
  border: none;
}

.search-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
}

.search-filters {
  display: flex;
  gap: 16px;
  flex: 1;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-icon {
  color: #909399;
  font-size: 16px;
}

.search-input {
  width: 280px;
}

.date-picker {
  width: 200px;
}

.search-actions {
  display: flex;
  gap: 12px;
}

/* 列表卡片 */
.list-card {
  border-radius: 12px;
  border: none;
  min-height: 400px;
}

.list-content {
  min-height: 300px;
}

/* 加载骨架屏 */
.loading-skeleton {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.skeleton-worklog {
  padding: 20px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: #909399;
}

.empty-icon {
  font-size: 64px;
  color: var(--tp-border-strong);
  margin-bottom: 16px;
}

.empty-state h3 {
  font-size: 18px;
  color: #606266;
  margin: 0 0 8px 0;
}

.empty-state p {
  font-size: 14px;
  margin: 0 0 24px 0;
}

/* 日志列表 */
.worklogs-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.worklog-item {
  background: var(--tp-bg-card);
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
}

.worklog-item:hover {
  border-color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.1);
  transform: translateY(-2px);
}

.worklog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.worklog-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.title-icon {
  color: #409eff;
  font-size: 18px;
}

.worklog-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.worklog-item:hover .worklog-actions {
  opacity: 1;
}

.worklog-content {
  margin-bottom: 16px;
}

.content-preview {
  color: #606266;
  line-height: 1.6;
  margin: 0;
  font-size: 14px;
}

.worklog-meta {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.meta-item .el-icon {
  font-size: 14px;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .worklogs-container {
    padding: 16px;
  }
  
  .page-header {
    padding: 24px 20px;
  }
  
  .header-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 20px;
  }
  
  .header-stats {
    gap: 20px;
  }
  
  .page-title {
    font-size: 24px;
  }
  
  .search-content {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }
  
  .search-filters {
    flex-direction: column;
    gap: 12px;
  }
  
  .search-input,
  .date-picker {
    width: 100%;
  }
  
  .search-actions {
    justify-content: center;
  }
  
  .worklog-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  
  .worklog-actions {
    opacity: 1;
  }
  
  .worklog-meta {
    gap: 16px;
  }
}
</style>
