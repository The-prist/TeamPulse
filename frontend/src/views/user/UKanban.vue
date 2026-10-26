<template>
  <div class="kanban-container">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <h1 class="page-title">我的看板</h1>
          <p class="page-subtitle">全局掌握任务状态</p>
        </div>
        <div class="header-stats">
          <div class="stat-item">
            <div class="stat-number">{{ list.length }}</div>
            <div class="stat-label">看板总数</div>
          </div>
          <div class="stat-item">
            <div class="stat-number">{{ activeKanbanCount }}</div>
            <div class="stat-label">活跃看板</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 看板分类标签页 -->
    <el-card class="kanban-list-card" shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="管理团队" name="managed">
          <div v-loading="managedLoading" class="list-content">
            <!-- 管理团队看板内容 -->
            <div v-if="managedLoading" class="loading-skeleton">
              <div v-for="i in 6" :key="i" class="skeleton-item">
                <el-skeleton animated>
                  <template #template>
                    <el-skeleton-item variant="rect" style="width: 100%; height: 200px" />
                  </template>
                </el-skeleton>
              </div>
            </div>
            
            <!-- 管理团队空状态 -->
            <div v-else-if="managedList.length === 0" class="empty-state">
              <el-empty description="暂无管理的团队看板">
                <el-button type="primary" @click="showCreateDialog = true">
                  <el-icon><Plus /></el-icon>
                  创建看板
                </el-button>
              </el-empty>
            </div>
            
            <!-- 管理团队看板网格 -->
            <div v-else class="kanban-grid">
              <div v-for="kanban in managedList" :key="kanban.kanbanId" class="kanban-card" @click="goToDetail(kanban.kanbanId)">
                <div class="kanban-header">
                  <h3 class="kanban-title">{{ kanban.kanbanName }}</h3>
                  <el-tag size="small" type="success">管理</el-tag>
                </div>
                <p class="kanban-description">{{ kanban.description || '暂无描述' }}</p>
                <div class="kanban-meta">
                  <div class="meta-item">
                    <el-icon><Calendar /></el-icon>
                    <span>{{ formatDate(kanban.createdAt) }}</span>
                  </div>
                  <div class="meta-item">
                    <el-icon><User /></el-icon>
                    <span>团队ID: {{ kanban.teamId }}</span>
                  </div>
                </div>
                <div class="kanban-stats">
                  <div class="stat">
                    <span class="stat-number">{{ getTaskCount(kanban.kanbanId) }}</span>
                    <span class="stat-label">任务</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="参加团队" name="joined">
          <div v-loading="joinedLoading" class="list-content">
            <!-- 参加团队看板内容 -->
            <div v-if="joinedLoading" class="loading-skeleton">
              <div v-for="i in 6" :key="i" class="skeleton-item">
                <el-skeleton animated>
                  <template #template>
                    <el-skeleton-item variant="rect" style="width: 100%; height: 200px" />
                  </template>
                </el-skeleton>
              </div>
            </div>
            
            <!-- 参加团队空状态 -->
            <div v-else-if="joinedList.length === 0" class="empty-state">
              <el-empty description="暂无参加的团队看板" />
            </div>
            
            <!-- 参加团队看板网格 -->
            <div v-else class="kanban-grid">
              <div v-for="kanban in joinedList" :key="kanban.kanbanId" class="kanban-card" @click="goToDetail(kanban.kanbanId)">
                <div class="kanban-header">
                  <h3 class="kanban-title">{{ kanban.kanbanName }}</h3>
                  <el-tag size="small" type="info">成员</el-tag>
                </div>
                <p class="kanban-description">{{ kanban.description || '暂无描述' }}</p>
                <div class="kanban-meta">
                  <div class="meta-item">
                    <el-icon><Calendar /></el-icon>
                    <span>{{ formatDate(kanban.createdAt) }}</span>
                  </div>
                  <div class="meta-item">
                    <el-icon><User /></el-icon>
                    <span>团队ID: {{ kanban.teamId }}</span>
                  </div>
                </div>
                <div class="kanban-stats">
                  <div class="stat">
                    <span class="stat-number">{{ getTaskCount(kanban.kanbanId) }}</span>
                    <span class="stat-label">任务</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { kanbanApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { Grid, Calendar, UserFilled, ArrowRight, Plus, User } from '@element-plus/icons-vue'

const auth = useAuthStore()
const router = useRouter()

const activeTab = ref('managed')
const managedList = ref([])
const joinedList = ref([])
const managedLoading = ref(false)
const joinedLoading = ref(false)
const showCreateDialog = ref(false)

// 计算活跃看板数量
const activeKanbanCount = computed(() => {
  const allKanbans = [...managedList.value, ...joinedList.value]
  return allKanbans.filter(kanban => getTaskCount(kanban.kanbanId) > 0).length
})

// 计算看板总数
const list = computed(() => {
  return [...managedList.value, ...joinedList.value]
})

// 获取看板任务总数
function getTaskCount(kanbanId) {
  // 这里可以根据需要实现任务计数逻辑
  return 0
}

// 跳转到看板详情
function goToDetail(kanbanId) {
  router.push(`/u/kanban/${kanbanId}`)
}

// 标签页切换处理
function handleTabChange(tabName) {
  if (tabName === 'managed' && managedList.value.length === 0) {
    loadManagedKanbans()
  } else if (tabName === 'joined' && joinedList.value.length === 0) {
    loadJoinedKanbans()
  }
}

// 格式化日期
function formatDate(dateStr) {
  if (!dateStr) return '未知时间'
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now - date
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 7) return `${days}天前`
  if (days < 30) return `${Math.floor(days / 7)}周前`
  if (days < 365) return `${Math.floor(days / 30)}个月前`
  return `${Math.floor(days / 365)}年前`
}

// 加载我创建的看板：/kanban/my 返回「自己创建的 + 所在团队的」，
// 新后端没有 managed/joined 两个独立接口，这里按 creatorId 在前端拆分
async function loadManagedKanbans() {
  managedLoading.value = true
  try {
    const kanbans = await kanbanApi.my()
    const uid = auth.user?.userId
    managedList.value = (Array.isArray(kanbans) ? kanbans : []).filter(k => k.creatorId === uid)
  } catch(e) {
    ElMessage.error('加载管理团队看板失败：' + (e.message||''))
  } finally {
    managedLoading.value = false
  }
}

// 加载我参与（非本人创建）的看板
async function loadJoinedKanbans() {
  joinedLoading.value = true
  try {
    const kanbans = await kanbanApi.my()
    const uid = auth.user?.userId
    joinedList.value = (Array.isArray(kanbans) ? kanbans : []).filter(k => k.creatorId !== uid)
  } catch(e) {
    ElMessage.error('加载参加团队看板失败：' + (e.message||''))
  } finally {
    joinedLoading.value = false
  }
}

// 初始加载
function load() {
  loadManagedKanbans()
}

onMounted(load)
</script>
<style scoped>
.kanban-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 0;
}

/* 页面头部 */
.page-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 2rem 1.5rem;
  margin-bottom: 1.5rem;
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  flex: 1;
}

.page-title {
  font-size: 2rem;
  font-weight: 700;
  margin: 0 0 0.5rem 0;
  color: white;
}

.page-subtitle {
  font-size: 1rem;
  opacity: 0.9;
  margin: 0;
}

.header-stats {
  display: flex;
  gap: 2rem;
}

.stat-item {
  text-align: center;
  background: rgba(255, 255, 255, 0.1);
  padding: 1rem 1.5rem;
  border-radius: 12px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.stat-number {
  font-size: 1.8rem;
  font-weight: 700;
  color: white;
  margin-bottom: 0.25rem;
}

.stat-label {
  font-size: 0.875rem;
  opacity: 0.9;
  color: white;
}

/* 看板列表卡片 */
.kanban-list-card {
  max-width: 1200px;
  margin: 0 auto 2rem auto;
  border-radius: 16px;
  border: none;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.list-content {
  min-height: 400px;
}

/* 加载状态 */
.loading-skeleton {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 1.5rem;
  padding: 1rem;
}

.skeleton-kanban {
  padding: 1.5rem;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 4rem 2rem;
  color: #666;
}

.empty-icon {
  font-size: 4rem;
  color: #ddd;
  margin-bottom: 1rem;
}

.empty-state h3 {
  font-size: 1.25rem;
  margin: 1rem 0 0.5rem 0;
  color: #333;
}

.empty-state p {
  margin: 0 0 2rem 0;
  color: #666;
}

/* 看板网格 */
.kanban-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 1.5rem;
  padding: 1.5rem;
}

.kanban-card {
  background: var(--tp-bg-card);
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 1.5rem;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.kanban-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
  border-color: #667eea;
}

.kanban-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #667eea, #764ba2);
  transform: scaleX(0);
  transition: transform 0.3s ease;
}

.kanban-card:hover::before {
  transform: scaleX(1);
}

.kanban-header {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1rem;
}

.kanban-avatar {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar-icon {
  font-size: 1.5rem;
  color: white;
}

.kanban-info {
  flex: 1;
  min-width: 0;
}

.kanban-name {
  font-size: 1.125rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
  color: #333;
  line-height: 1.4;
}

.kanban-desc {
  font-size: 0.875rem;
  color: #666;
  margin: 0;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.kanban-arrow {
  color: #ccc;
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.kanban-card:hover .kanban-arrow {
  color: #667eea;
  transform: translateX(4px);
}

.kanban-meta {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: #666;
}

.meta-item .el-icon {
  font-size: 1rem;
  color: #999;
}

.kanban-stats {
  display: flex;
  gap: 0.75rem;
}

.stat-badge {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.375rem 0.75rem;
  background: #f8f9fa;
  border-radius: 20px;
  font-size: 0.75rem;
  border: 1px solid #e9ecef;
}

.stat-badge.active {
  background: #e3f2fd;
  border-color: #bbdefb;
  color: #1976d2;
}

.stat-value {
  font-weight: 600;
}

.stat-text {
  color: #666;
}

.stat-badge.active .stat-text {
  color: #1976d2;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .page-header {
    padding: 1.5rem 1rem;
  }
  
  .header-content {
    flex-direction: column;
    gap: 1.5rem;
    text-align: center;
  }
  
  .header-stats {
    gap: 1rem;
  }
  
  .stat-item {
    padding: 0.75rem 1rem;
  }
  
  .kanban-grid {
    grid-template-columns: 1fr;
    padding: 1rem;
    gap: 1rem;
  }
  
  .kanban-card {
    padding: 1rem;
  }
  
  .kanban-header {
    gap: 0.75rem;
  }
  
  .kanban-avatar {
    width: 40px;
    height: 40px;
  }
  
  .avatar-icon {
    font-size: 1.25rem;
  }
  
  .kanban-name {
    font-size: 1rem;
  }
  
  .kanban-meta {
    gap: 0.75rem;
  }
}

@media (max-width: 480px) {
  .header-stats {
    flex-direction: column;
    gap: 0.75rem;
    width: 100%;
  }
  
  .stat-item {
    padding: 0.5rem 1rem;
  }
  
  .kanban-meta {
    flex-direction: column;
    gap: 0.5rem;
  }
  
  .kanban-stats {
    gap: 0.5rem;
  }
}
</style>
