<template>
  <div class="kanban-detail-container">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <div class="breadcrumb">
            <el-button text @click="$router.push('/u/kanban')" class="breadcrumb-link">
              <el-icon><ArrowLeft /></el-icon>
              返回看板列表
            </el-button>
          </div>
          <h1 class="page-title">{{ detail.kanban?.kanbanName || '看板详情' }}</h1>
          <div class="page-meta">
            <div class="meta-item">
              <el-icon><UserFilled /></el-icon>
              <span>团队 #{{ detail.kanban?.teamId || '-' }}</span>
            </div>
            <div class="meta-item">
              <el-icon><User /></el-icon>
              <span>创建者 #{{ detail.kanban?.creatorId || '-' }}</span>
            </div>
            <div class="meta-item">
              <el-icon><Calendar /></el-icon>
              <span>{{ formatDate(detail.kanban?.createdAt) }}</span>
            </div>
          </div>
        </div>
        <div class="header-stats">
          <div class="stat-item">
            <div class="stat-number">{{ detail.items?.length || 0 }}</div>
            <div class="stat-label">总任务</div>
          </div>
          <div class="stat-item">
            <div class="stat-number">{{ getActiveTaskCount() }}</div>
            <div class="stat-label">进行中</div>
          </div>
          <div class="stat-item">
            <div class="stat-number">{{ detail.columns?.length || 0 }}</div>
            <div class="stat-label">列数</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 工具栏 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar-content">
        <div class="view-mode">
          <el-radio-group v-model="viewMode" size="default">
            <el-radio-button label="board">
              <el-icon><Grid /></el-icon>
              看板视图
            </el-radio-button>
            <el-radio-button label="list">
              <el-icon><List /></el-icon>
              列表视图
            </el-radio-button>
          </el-radio-group>
        </div>
        <div class="toolbar-actions">
          <el-button type="primary" @click="openNewForList" :icon="Plus">新建任务</el-button>
        </div>
      </div>
    </el-card>

    <!-- 内容区域 -->
    <el-card class="content-card" shadow="never">
      <el-skeleton v-if="loading" :rows="3" animated />
      <div v-else-if="error" class="err">{{ error }}</div>
        <!-- 列表模式 -->
        <div v-else-if="viewMode==='list'" class="list-view">
          <!-- 搜索筛选 -->
          <div class="search-filters">
            <div class="filter-row">
              <div class="filter-item">
                <el-icon class="filter-icon"><Search /></el-icon>
                <el-input 
                  v-model="q.keyword" 
                  placeholder="搜索任务标题或描述" 
                  clearable 
                  class="search-input"
                />
              </div>
              <div class="filter-item">
                <el-icon class="filter-icon"><Filter /></el-icon>
                <el-select v-model="q.columnId" clearable placeholder="选择列" class="filter-select">
                  <el-option v-for="c in detail.columns" :key="c.columnId" :label="c.columnName" :value="c.columnId" />
                </el-select>
              </div>
              <div class="filter-item">
                <el-icon class="filter-icon"><UserFilled /></el-icon>
                <el-select v-model="q.assigneeId" clearable filterable placeholder="选择受让人" class="filter-select">
                  <el-option v-for="m in members" :key="m.userId" :label="displayName(m.userId)" :value="m.userId" />
                </el-select>
              </div>
              <div class="filter-item">
                <el-icon class="filter-icon"><Flag /></el-icon>
                <el-select v-model="q.priority" clearable placeholder="选择优先级" class="filter-select">
                  <el-option label="低" value="low" />
                  <el-option label="中" value="medium" />
                  <el-option label="高" value="high" />
                </el-select>
              </div>
            </div>
          </div>
          <!-- 任务表格 -->
          <div class="task-table">
            <el-table 
              :data="pagedItems" 
              class="modern-table" 
              row-key="itemId"
              :header-cell-style="{ background: 'var(--tp-bg-subtle)', color: '#374151', fontWeight: '600' }"
              :row-style="{ cursor: 'pointer' }"
              @row-click="openSchedule"
            >
              <el-table-column prop="itemId" label="编号" width="80" align="center">
                <template #default="{row}">
                  <span class="task-id">#{{ row.itemId }}</span>
                </template>
              </el-table-column>
              
              <el-table-column label="任务标题" min-width="250">
                <template #default="{row}">
                  <div class="task-title">
                    <div class="title-text">{{ row.schedule_title || '无标题' }}</div>
                    <div v-if="row.description" class="title-desc">{{ row.description }}</div>
                  </div>
                </template>
              </el-table-column>
              
              <el-table-column label="所属列" width="160">
                <template #default="{ row }">
                  <el-select v-model="row.columnId" size="small" @change="(v)=>moveRow(row, v)" class="column-select">
                    <el-option v-for="c in detail.columns" :key="c.columnId" :label="c.columnName" :value="c.columnId" />
                  </el-select>
                </template>
              </el-table-column>
              
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.schedule_status)" class="status-tag">
                    {{ row.schedule_status || '-' }}
                  </el-tag>
                </template>
              </el-table-column>
              
              <el-table-column prop="schedule_priority" label="优先级" width="120">
                <template #default="{row}">
                  <el-tag 
                    :type="row.schedule_priority === 'high' ? 'danger' : row.schedule_priority === 'medium' ? 'warning' : 'info'" 
                    class="priority-tag"
                  >
                    <el-icon v-if="row.schedule_priority === 'high'"><Flag /></el-icon>
                    {{ row.schedule_priority || '-' }}
                  </el-tag>
                </template>
              </el-table-column>
              
              <el-table-column label="受让人" width="180">
                <template #default="{ row }">
                  <div v-if="displayName(row.assigneeId)" class="assignee-info">
                    <el-avatar :size="24" class="assignee-avatar">{{ initials(displayName(row.assigneeId)) }}</el-avatar>
                    <span class="assignee-name">{{ displayName(row.assigneeId) }}</span>
                  </div>
                  <span v-else class="unassigned">未分配</span>
                </template>
              </el-table-column>
              
              <el-table-column label="操作" width="160" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions" @click.stop>
                    <el-button size="small" text type="primary" @click="openSchedule(row)">
                      <el-icon><View /></el-icon>
                      详情
                    </el-button>
                    <el-button size="small" text type="primary" @click="prepAssign(row)">
                      <el-icon><UserFilled /></el-icon>
                      分配
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        <div class="list-pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="filteredItems.length"
            layout="total, prev, pager, next, sizes"
            :page-sizes="[10,20,50]"
          />
        </div>
      </div>
      <!-- 看板模式 -->
      <div v-else class="board-view">
        <div class="kanban-board">
          <div v-for="col in detail.columns" :key="col.columnId" class="kanban-column">
            <div class="column-header">
              <div class="column-title">
                <h3>{{ col.columnName }}</h3>
                <div class="column-count">
                  {{ (col.items||[]).length }}
                </div>
              </div>
              <div class="column-actions">
                <el-button size="small" text @click="openNew(col)" class="add-task-btn">
                  <el-icon><Plus /></el-icon>
                </el-button>
              </div>
            </div>
            
            <div class="column-body">
              <div class="task-list">
                <div 
                  v-for="element in col.items" 
                  :key="element.itemId" 
                  class="task-card"
                  @click="openSchedule(element)"
                >
                  <div class="task-card-header">
                    <div class="task-id">#{{ element.itemId }}</div>
                    <div class="task-actions">
                      <el-dropdown @command="(cmd) => onItemCommand(cmd, element)" trigger="click">
                        <el-button size="small" text class="action-btn" @click.stop>
                          <el-icon><MoreFilled /></el-icon>
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="assign">
                              <el-icon><UserFilled /></el-icon>
                              分配任务
                            </el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </div>
                  </div>
                  
                  <div class="task-card-content">
                    <div class="task-title">{{ element.schedule_title || '无标题' }}</div>
                    <div v-if="element.description" class="task-description">{{ element.description }}</div>
                  </div>
                  
                  <div class="task-card-footer">
                    <div class="task-meta">
                      <el-tag 
                        v-if="element.schedule_priority" 
                        :type="element.schedule_priority === 'high' ? 'danger' : element.schedule_priority === 'medium' ? 'warning' : 'info'" 
                        size="small" 
                        class="priority-tag"
                      >
                        <el-icon v-if="element.schedule_priority === 'high'"><Flag /></el-icon>
                        {{ element.schedule_priority === 'high' ? '高' : element.schedule_priority === 'medium' ? '中' : '低' }}
                      </el-tag>
                      <el-tag v-if="element.schedule_status" :type="statusType(element.schedule_status)" size="small" class="status-tag">
                        {{ element.schedule_status }}
                      </el-tag>
                    </div>
                    <div v-if="displayName(element.assigneeId)" class="assignee-info">
                      <div class="assignee-avatar">{{ initials(displayName(element.assigneeId)) }}</div>
                      <span class="assignee-name">{{ displayName(element.assigneeId) }}</span>
                    </div>
                  </div>
                </div>
              </div>
              
              <div class="add-task-card" @click="openNew(col)">
                <el-icon><Plus /></el-icon>
                <span>添加新任务</span>
              </div>
              
              <div v-if="!col.items || col.items.length===0" class="empty-state">暂无任务</div>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 新建日程 -->
    <el-dialog v-model="newVisible" title="新建日程" width="520px">
      <el-form :model="form" label-width="88px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="时间">
          <el-date-picker v-model="form.range" type="datetimerange" range-separator="至" start-placeholder="开始" end-placeholder="结束" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" style="width:160px">
            <el-option label="低" value="low" />
            <el-option label="中" value="medium" />
            <el-option label="高" value="high" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newVisible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="createSchedule">创建</el-button>
      </template>
    </el-dialog>

    <!-- 分配弹窗 -->
    <el-dialog v-model="assignVisible" title="分配成员" width="420px">
      <el-select v-model="assignTargetUser" filterable style="width:100%" placeholder="选择成员">
        <el-option v-for="m in members" :key="m.userId" :label="displayName(m.userId)" :value="m.userId" />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible=false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="doAssign">确定</el-button>
      </template>
    </el-dialog>
  </div>
  
</template>

<script setup>
import { reactive, ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { kanbanApi, scheduleApi, teamApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { 
  ArrowLeft, User, UserFilled, Calendar, Grid, List, Plus, Search, Filter, Flag,
  View, Edit, Delete, MoreFilled
} from '@element-plus/icons-vue'
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const detail = reactive({ kanban:null, columns:[], items:[] })
const members = ref([])
const myId = Number(auth.user?.userId || 0)
const viewMode = ref('list')
const q = reactive({ keyword:'', columnId: null, assigneeId: null, priority: '' })

function statusType(s){ if(!s) return ''; const m={todo:'info',doing:'warning',done:'success'}; return m[s]||'info' }
function initials(name){ if(!name) return ''; return name.slice(0,2) }
function displayName(uid){
  if (!uid) return ''
  const m = members.value.find(x=>x.userId===uid)
  return m?.nickname || m?.username || `用户${uid}`
}

// 新接口卡片是拍平后的 KanbanCardVO（scheduleTitle/scheduleStatus/schedulePriority…），
// 模板沿用旧的 snake_case 字段，这里统一做一次别名映射
function normalizeCard(card) {
  return {
    ...card,
    title: card.scheduleTitle,
    description: '',
    schedule_title: card.scheduleTitle,
    schedule_status: card.scheduleStatus,
    schedule_priority: card.schedulePriority,
    schedule_end_time: card.scheduleEndTime,
    assignee_name: card.assigneeName
  }
}

async function load(){
  loading.value = true; error.value=''
  try{
    const id = route.params.id
    // 详情一次返回 kanban / columns / cards
    const data = await kanbanApi.detail(id)
    detail.kanban = data?.kanban || null
    detail.columns = (data?.columns || []).map(c=> ({...c, items: []}))
    detail.items = (data?.cards || []).map(normalizeCard)
    // 划归 items
    const map = new Map(detail.columns.map(c=>[c.columnId, c]))
    detail.items.forEach(it=>{ const c = map.get(it.columnId); if (c) c.items.push(it) })
    // 拉取团队成员用于分配
    const teamId = detail.kanban?.teamId
    if (teamId) {
      // GET /team/{teamId}/members 直接返回成员数组
      const list = await teamApi.members(teamId)
      members.value = Array.isArray(list) ? list : []
    } else {
      members.value = []
    }
  }catch(e){ error.value=String(e) } finally { loading.value=false }
}
onMounted(load)

// 打开日程详情（用户域）
function openSchedule(element){
  if (element?.scheduleId) router.push(`/u/schedules/${element.scheduleId}`)
}

// 已移除拖拽，保留静态列展示，移动仅通过列表模式的下拉选择

// 列表模式：数据派生与分页
const columnsMap = computed(()=> Object.fromEntries((detail.columns||[]).map(c=>[Number(c.columnId), c.columnName])))
const filteredItems = computed(()=>{
  const kw = (q.keyword||'').trim().toLowerCase()
  return (detail.items||[]).filter(it=>{
    if (q.columnId && Number(it.columnId) !== Number(q.columnId)) return false
    if (q.assigneeId && Number(it.assigneeId) !== Number(q.assigneeId)) return false
    if (q.priority && (it.schedule_priority||'') !== q.priority) return false
    if (kw) {
      const title = String(it.schedule_title||'').toLowerCase()
      const t2 = String(it.title||'').toLowerCase()
      if (!title.includes(kw) && !t2.includes(kw)) return false
    }
    return true
  }).map(it=> ({ ...it, columnName: columnsMap.value[Number(it.columnId)] || '' }))
})
const page = ref(1)
const size = ref(20)
const pagedItems = computed(()=> {
  const start = (page.value-1)*size.value
  return filteredItems.value.slice(start, start+size.value)
})

function openNewForList(){
  const col = detail.columns?.[0]
  if (!col) return ElMessage.warning('暂无可用列')
  openNew(col)
}

async function moveRow(row, newColumnId){
  const old = row.columnId
  try{
    // PUT /kanban/items/{itemId}/move
    await kanbanApi.moveItem(Number(row.itemId), { columnId: Number(newColumnId), sortOrder: 1 })
    // 成功后刷新，确保顺序一致
    await load()
  }catch(e){
    row.columnId = old
    ElMessage.error('移动失败：' + (e.message||''))
  }
}

function prepAssign(row){ assignTargetItem.value = row; assignVisible.value = true }

// 新建日程
const newVisible = ref(false)
const currentColumn = ref(null)
const submitting = ref(false)
const form = reactive({ title:'', range:[], priority:'medium' })
function openNew(col){ currentColumn.value = col; newVisible.value = true }
async function createSchedule(){
  if (!form.title.trim()) return ElMessage.warning('请输入标题')
  if (!form.range || form.range.length!==2) return ElMessage.warning('请选择时间范围')
  submitting.value = true
  try{
    const [start,end] = form.range
    // POST /schedule：创建人取自 JWT，时间用 ISO-8601
    const res = await scheduleApi.create({
      title: form.title,
      content: '',
      startTime: formatDT(start),
      endTime: formatDT(end),
      priority: form.priority,
      teamId: detail.kanban?.teamId || null
    })
    // 再把新建的日程投影到当前列（新接口拆成两步：先建日程，再加卡片）
    const kanbanId = detail.kanban?.kanbanId
    const columnId = currentColumn.value?.columnId
    if (kanbanId && columnId && res?.scheduleId) {
      await kanbanApi.addItem(kanbanId, { scheduleId: res.scheduleId, columnId })
    }
    ElMessage.success('已创建')
    newVisible.value = false
    form.title=''; form.range=[]; form.priority='medium'
    await load()
  }catch(e){ ElMessage.error('创建失败：' + (e.message||'')) } finally { submitting.value = false }
}

// 分配
const assignVisible = ref(false)
const assigning = ref(false)
const assignTargetItem = ref(null)
const assignTargetUser = ref()
function onItemCommand(cmd, element){
  if (cmd==='assign') { assignTargetItem.value = element; assignVisible.value = true }
}
async function doAssign(){
  if (!assignTargetItem.value || !assignTargetUser.value) return
  assigning.value = true
  try{
    const itemId = assignTargetItem.value.itemId
    const uid = assignTargetUser.value
    // PUT /kanban/items/{itemId}/assign?assigneeId=
    await kanbanApi.assignItem(itemId, uid)
    ElMessage.success('已分配')
    assignVisible.value = false
    assignTargetItem.value = null
    assignTargetUser.value = undefined
    await load()
  }catch(e){ ElMessage.error('分配失败：' + (e.message||'')) } finally { assigning.value = false }
}

function pad(n){ return String(n).padStart(2,'0') }
// 后端 LocalDateTime 按 ISO-8601 解析（2024-05-01T09:00:00）
function formatDT(d){ const dt = new Date(d); return `${dt.getFullYear()}-${pad(dt.getMonth()+1)}-${pad(dt.getDate())}T${pad(dt.getHours())}:${pad(dt.getMinutes())}:00` }

function formatDate(dateStr) {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

function getActiveTaskCount() {
  return (detail.items || []).filter(item => 
    item.schedule_status === 'doing' || item.schedule_status === 'todo'
  ).length
}
</script>

<style scoped>
.kanban-detail-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 0;
}

/* 页面头部 */
.page-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 2rem 2rem 1.5rem;
  margin-bottom: 1.5rem;
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 2rem;
}

.header-left {
  flex: 1;
}

.breadcrumb {
  margin-bottom: 1rem;
}

.breadcrumb-link {
  color: rgba(255, 255, 255, 0.8) !important;
  border: none !important;
  padding: 0 !important;
  font-size: 14px;
  transition: color 0.2s;
}

.breadcrumb-link:hover {
  color: white !important;
}

.page-title {
  font-size: 2.5rem;
  font-weight: 700;
  margin: 0 0 1rem 0;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.page-meta {
  display: flex;
  gap: 2rem;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
}

.header-stats {
  display: flex;
  gap: 2rem;
}

.stat-item {
  text-align: center;
  background: rgba(255, 255, 255, 0.1);
  padding: 1.5rem 1rem;
  border-radius: 12px;
  backdrop-filter: blur(10px);
  min-width: 80px;
}

.stat-number {
  font-size: 2rem;
  font-weight: 700;
  color: white;
  margin-bottom: 0.5rem;
}

.stat-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.8);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* 工具栏 */
.toolbar-card {
  margin: 0 2rem 1.5rem;
  border-radius: 12px;
  border: none;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
}

.toolbar-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0;
}

.view-mode :deep(.el-radio-button__inner) {
  border-radius: 8px;
  margin-right: 8px;
  padding: 8px 16px;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 内容区域 */
.content-card {
  margin: 0 2rem 2rem;
  border-radius: 12px;
  border: none;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
  min-height: 600px;
}

/* 列表视图样式 */
.list-view {
  padding: 1rem 0;
}

.search-filters {
  margin-bottom: 2rem;
  padding: 1.5rem;
  background: var(--tp-bg-subtle);
  border-radius: 12px;
}

.filter-row {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  align-items: center;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 200px;
}

.filter-icon {
  color: #6b7280;
  font-size: 16px;
}

.search-input {
  flex: 1;
}

.filter-select {
  flex: 1;
}

.task-table {
  margin-top: 1rem;
}

.modern-table {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.modern-table :deep(.el-table__header) {
  background: var(--tp-bg-subtle);
}

.modern-table :deep(.el-table__row) {
  transition: background-color 0.2s;
}

.modern-table :deep(.el-table__row:hover) {
  background-color: var(--tp-bg-subtle);
}

.task-id {
  font-weight: 600;
  color: #6366f1;
  font-size: 12px;
}

.task-title {
  margin-bottom: 0.25rem;
}

.title-text {
  font-weight: 500;
  color: #1f2937;
  margin-bottom: 0.25rem;
}

.title-desc {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
}

.column-tag, .status-tag, .priority-tag {
  border-radius: 6px;
  font-weight: 500;
}

.priority-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}

.assignee-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.assignee-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 600;
}

.assignee-name {
  font-size: 13px;
  color: #374151;
}

.unassigned {
  color: #9ca3af;
  font-size: 13px;
}

.table-actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.column-select {
  width: 100%;
}

.list-pager {
  display: flex;
  justify-content: center;
  margin-top: 2rem;
  padding-top: 1rem;
  border-top: 1px solid #e5e7eb;
}

/* 看板视图样式 */
.board-view {
  padding: 1rem 0;
}

.kanban-board {
  display: flex;
  gap: 1.5rem;
  overflow-x: auto;
  padding: 1rem;
  min-height: 600px;
}

.kanban-column {
  min-width: 320px;
  background: var(--tp-bg-subtle);
  border-radius: 12px;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.column-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding-bottom: 0.75rem;
  border-bottom: 2px solid #e5e7eb;
}

.column-title {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.column-title h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.column-count {
  background: #6366f1;
  color: white;
  font-size: 12px;
  font-weight: 600;
  padding: 0.25rem 0.5rem;
  border-radius: 12px;
  min-width: 20px;
  text-align: center;
}

.add-task-btn {
  color: #6b7280 !important;
  padding: 4px !important;
  border-radius: 6px;
  transition: all 0.2s;
}

.add-task-btn:hover {
  background: #e5e7eb !important;
  color: #374151 !important;
}

.column-body {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.task-card {
  background: var(--tp-bg-card);
  border-radius: 8px;
  padding: 1rem;
  border: 1px solid #e5e7eb;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.task-card:hover {
  border-color: #6366f1;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.15);
  transform: translateY(-1px);
}

.task-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.task-card .task-id {
  font-size: 11px;
  color: #6b7280;
  font-weight: 500;
}

.action-btn {
  color: #9ca3af !important;
  padding: 2px !important;
  border-radius: 4px;
  opacity: 0;
  transition: all 0.2s;
}

.task-card:hover .action-btn {
  opacity: 1;
}

.action-btn:hover {
  background: var(--tp-border) !important;
  color: #374151 !important;
}

.task-card-content {
  margin-bottom: 0.75rem;
}

.task-card .task-title {
  font-weight: 500;
  color: #1f2937;
  margin-bottom: 0.5rem;
  line-height: 1.4;
}

.task-description {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
}

.task-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-meta {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.task-card .assignee-info {
  font-size: 12px;
}

.task-card .assignee-avatar {
  width: 20px;
  height: 20px;
  font-size: 9px;
}

.task-card .assignee-name {
  font-size: 12px;
}

.add-task-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 1rem;
  border: 2px dashed #d1d5db;
  border-radius: 8px;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
  margin-top: auto;
}

.add-task-card:hover {
  border-color: #6366f1;
  color: #6366f1;
  background: rgba(99, 102, 241, 0.05);
}

.empty-state {
  text-align: center;
  color: #9ca3af;
  font-size: 14px;
  padding: 2rem 1rem;
  font-style: italic;
}

.err {
  color: #ef4444;
  text-align: center;
  padding: 3rem;
  font-size: 16px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .page-header {
    padding: 1.5rem 1rem 1rem;
  }
  
  .header-content {
    flex-direction: column;
    gap: 1.5rem;
  }
  
  .header-stats {
    gap: 1rem;
  }
  
  .stat-item {
    padding: 1rem 0.75rem;
    min-width: 60px;
  }
  
  .toolbar-card, .content-card {
    margin-left: 1rem;
    margin-right: 1rem;
  }
  
  .toolbar-content {
    flex-direction: column;
    gap: 1rem;
    align-items: stretch;
  }
  
  .filter-row {
    flex-direction: column;
    gap: 0.75rem;
  }
  
  .filter-item {
    min-width: auto;
  }
  
  .kanban-board {
    flex-direction: column;
    gap: 1rem;
  }
  
  .kanban-column {
    min-width: auto;
  }
}
</style>
