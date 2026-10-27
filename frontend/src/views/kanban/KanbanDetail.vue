<template>
  <div class="kanban-detail">
    <el-card class="header-card">
      <template #header>
         <div class="header-container">
           <div class="header-left">
             <el-button type="text" @click="$router.back()" class="back-btn">
               <el-icon><ArrowLeft /></el-icon> 返回
             </el-button>
             <span class="header-title">看板详情</span>
           </div>
           <div class="header-actions">
             <el-button type="primary" size="small" @click="showAddItemDialog = true">
               <el-icon><Plus /></el-icon> 添加卡片
             </el-button>
             <el-button type="danger" size="small" v-if="canDeleteKanban" @click="deleteKanban">
               <el-icon><Delete /></el-icon> 删除看板
             </el-button>
           </div>
         </div>
       </template>
      <div v-if="loading">加载中...</div>
      <div v-else-if="error" class="err">{{ error }}</div>
      <div v-else class="kanban-info">
        <div class="title">{{ detail.kanban?.kanbanName }}</div>
        <div class="meta">
          <span>团队ID: {{ detail.kanban?.teamId }}</span>
          <span>创建者: {{ detail.kanban?.creatorId }}</span>
          <span>创建时间: {{ formatTime(detail.kanban?.createdAt) }}</span>
        </div>
        <div class="description" v-if="detail.kanban?.description">
          {{ detail.kanban.description }}
        </div>
        <div class="stats">
          <el-tag>{{ (detail.columns||[]).length }} 个列</el-tag>
          <el-tag type="success">{{ (detail.items||[]).length }} 张卡片</el-tag>
          <el-tag type="warning">在制品(WIP)：{{ wipCount }}</el-tag>
          <el-tag type="danger" v-if="overdueCount>0">逾期：{{ overdueCount }}</el-tag>
        </div>
      </div>
    </el-card>

    <!-- 看板列和卡片 -->
    <div class="kanban-board" v-if="!loading && !error">
      <div class="columns-container">
        <div 
          v-for="column in detail.columns" 
          :key="column.columnId" 
          class="column"
        >
          <div class="column-header">
            <h3>{{ column.columnName }}</h3>
            <el-tag size="small">{{ getColumnItemCount(column.columnId) }}</el-tag>
          </div>
          <div class="column-content">
            <div 
              v-for="item in getColumnItems(column.columnId)" 
              :key="item.itemId" 
              class="kanban-item"
              @click="viewItemDetail(item)"
            >
              <div class="item-title">{{ filterSensitiveWords(item.title || '无标题') }}</div>
              <div class="item-meta">
                <el-tag size="small" v-if="item.scheduleId">关联日程</el-tag>
              </div>
            </div>
            <div v-if="getColumnItems(column.columnId).length === 0" class="empty-column">
              暂无卡片
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 添加卡片对话框 -->
    <el-dialog v-model="showAddItemDialog" title="添加卡片" width="500px">
      <el-form :model="itemForm" label-width="80px" ref="itemFormRef">
        <el-form-item label="标题" prop="title">
          <el-input v-model="itemForm.title" placeholder="请输入卡片标题"></el-input>
        </el-form-item>
        <el-form-item label="所属列" prop="columnId">
          <el-select v-model="itemForm.columnId" placeholder="请选择列">
            <el-option 
              v-for="column in detail.columns" 
              :key="column.columnId" 
              :label="column.columnName" 
              :value="column.columnId"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="关联日程" prop="scheduleId" :rules="[{ required: true, message: '请输入日程ID', trigger: 'blur' }]">
          <el-input v-model.number="itemForm.scheduleId" placeholder="必须输入日程ID"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showAddItemDialog = false">取消</el-button>
          <el-button type="primary" @click="submitItem" :loading="submitting">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore()
import { onMounted, reactive, ref, computed } from 'vue'
import { filterSensitiveWords } from '@/utils/sensitive'
import { useRoute, useRouter } from 'vue-router'
import { kanbanApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, ArrowLeft, Delete } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref('')
const detail = reactive({ kanban: null, columns: [], items: [] })
const showAddItemDialog = ref(false)
const submitting = ref(false)
const itemFormRef = ref()

const itemForm = reactive({
  title: '',
  columnId: null,
  scheduleId: null
})

// 格式化时间
const formatTime = (timeStr) => {
  if (!timeStr) return ''
  return new Date(timeStr).toLocaleString('zh-CN')
}

// 简单度量
const wipCount = computed(() => (detail.items||[]).length)
const overdueCount = computed(() => {
  const now = Date.now()
  let c = 0
  for (const it of (detail.items||[])) {
    const s = it.schedule || {}
    const end = s && (s.endTime || s.dueDate)
    const status = (s && s.status || '').toLowerCase()
    if (!end) continue
    const d = new Date(String(end).includes('T') ? end : String(end).replace(' ','T'))
    if (!isNaN(d) && d.getTime() < now && status !== 'done' && status !== 'canceled') c++
  }
  return c
})

// 获取指定列的卡片数量
const getColumnItemCount = (columnId) => {
  return detail.items.filter(item => item.columnId === columnId).length
}

// 获取指定列的卡片
const getColumnItems = (columnId) => {
  return detail.items.filter(item => item.columnId === columnId)
}

// 查看卡片详情
const viewItemDetail = (item) => {
  if (item.scheduleId) {
    router.push(`/schedule/${item.scheduleId}`)
  } else {
    ElMessage.info('该卡片未关联日程')
  }
}

// 提交新卡片
const submitItem = async () => {
  if (!itemForm.title || !itemForm.columnId || !itemForm.scheduleId) {
    ElMessage.warning('请填写标题、选择列和输入日程ID')
    return
  }
  
  submitting.value = true
  try {
    const kanbanId = parseInt(route.params.id)
    // 新接口的 ItemCreateRequest 只有 scheduleId/columnId/assigneeId：
    // 卡片标题来自关联日程，创建人由后端从 JWT 取
    await kanbanApi.addItem(kanbanId, {
      columnId: itemForm.columnId,
      scheduleId: itemForm.scheduleId
    })
    ElMessage.success('添加卡片成功')
    showAddItemDialog.value = false
    
    // 重置表单
    Object.assign(itemForm, {
      title: '',
      columnId: null,
      scheduleId: null
    })
    
    // 重新加载数据
    await loadKanbanDetail()
  } catch (e) {
    console.error('添加卡片失败:', e)
    ElMessage.error('添加卡片失败: ' + String(e))
  } finally {
    submitting.value = false
  }
}

// 删除看板
const deleteKanban = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要删除这个看板吗？删除后无法恢复。',
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    
    const id = route.params.id
    await kanbanApi.remove(id)
    ElMessage.success('删除看板成功')
    router.push('/kanban')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除看板失败: ' + String(error))
    }
  }
}

// 加载看板详情
const loadKanbanDetail = async () => {
  loading.value = true
  error.value = ''
  try {
    const id = route.params.id
    // 详情一次返回 kanban / columns / cards 三段数据；cards 是拍平后的卡片（字段是 scheduleTitle 等）
    const data = await kanbanApi.detail(id)
    detail.kanban = data?.kanban || null
    detail.columns = data?.columns || []
    detail.items = (data?.cards || []).map((card) => ({
      ...card,
      // 模板沿用 item.title / item.schedule 两个旧字段，这里做一次映射
      title: card.scheduleTitle,
      schedule: {
        title: card.scheduleTitle,
        status: card.scheduleStatus,
        priority: card.schedulePriority,
        endTime: card.scheduleEndTime
      }
    }))
  } catch (e) {
    error.value = String(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadKanbanDetail()
})
/** 与后端一致：看板创建者 或 拥有 kanban:manage 才能删除看板 */
const canDeleteKanban = computed(() => {
  if (auth.hasPerm('kanban:manage')) return true
  const me = auth.user?.userId
  const creator = detail.value?.creatorId
  return creator != null && me != null && creator === me
})

</script>

<style scoped>
.kanban-detail {
  padding: 20px;
}

.header-card {
  margin-bottom: 20px;
}

.header-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  color: #606266;
  font-size: 14px;
  padding: 8px 12px;
}

.back-btn:hover {
  color: #409eff;
  background-color: #ecf5ff;
}

.header-title {
  font-weight: 600;
  font-size: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.kanban-info {
  padding: 10px 0;
}

.title {
  font-weight: 600;
  font-size: 24px;
  margin-bottom: 12px;
  color: #303133;
}

.meta {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
  color: #606266;
  font-size: 14px;
}

.description {
  margin-bottom: 12px;
  color: #606266;
  line-height: 1.5;
}

.stats {
  display: flex;
  gap: 10px;
}

.err {
  color: #f56c6c;
  text-align: center;
  padding: 20px;
}

/* 看板样式 */
.kanban-board {
  background: var(--tp-bg-subtle);
  border-radius: 8px;
  padding: 20px;
  min-height: 500px;
}

.columns-container {
  display: flex;
  gap: 20px;
  overflow-x: auto;
  padding-bottom: 10px;
}

.column {
  flex: 0 0 300px;
  background: var(--tp-bg-card);
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  max-height: 600px;
}

.column-header {
  padding: 16px;
  border-bottom: 1px solid var(--tp-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fafafa;
  border-radius: 8px 8px 0 0;
}

.column-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.column-content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.kanban-item {
  background: var(--tp-bg-card);
  border: 1px solid var(--tp-border);
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.kanban-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
  transform: translateY(-2px);
}

.item-title {
  font-weight: 500;
  color: #303133;
  margin-bottom: 8px;
  line-height: 1.4;
}

.item-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.empty-column {
  text-align: center;
  color: #909399;
  padding: 40px 20px;
  font-style: italic;
}

.dialog-footer {
  text-align: right;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .kanban-detail {
    padding: 10px;
  }
  
  .columns-container {
    gap: 10px;
  }
  
  .column {
    flex: 0 0 250px;
  }
  
  .meta {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
