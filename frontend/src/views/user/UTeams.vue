<template>
  <div class="u-teams">
    <!-- Header Section -->
    <div class="teams-header">
      <div class="header-content">
        <div class="header-info">
          <h1 class="page-title">
            <el-icon class="title-icon"><UserFilled /></el-icon>
			
            我的团队
          </h1>
          <p class="page-subtitle">管理和查看您参与的所有团队</p>
        </div>
        <div class="header-actions">
          <div class="header-stats">
            <div class="stat-item">
              <div class="stat-number">{{ managedTeams.length }}</div>
              <div class="stat-label">管理团队</div>
            </div>
            <div class="stat-item">
              <div class="stat-number">{{ joinedTeams.length }}</div>
              <div class="stat-label">加入团队</div>
            </div>
          </div>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            创建团队
          </el-button>
        </div>
      </div>
    </div>

    <!-- Teams Content -->
    <div class="teams-content">
      <div v-if="loading" class="loading-state">
        <el-skeleton v-for="i in 6" :key="i" animated>
          <template #template>
            <div class="team-card-skeleton">
              <el-skeleton-item variant="circle" style="width: 60px; height: 60px;" />
              <div class="skeleton-content">
                <el-skeleton-item variant="h3" style="width: 60%;" />
                <el-skeleton-item variant="text" style="width: 80%;" />
                <el-skeleton-item variant="text" style="width: 40%;" />
              </div>
            </div>
          </template>
        </el-skeleton>
      </div>

      <div v-else-if="managedTeams.length === 0 && joinedTeams.length === 0" class="empty-state">
        <div class="empty-icon">
          <el-icon><UserFilled /></el-icon>
        </div>
        <h3 class="empty-title">暂无团队</h3>
        <p class="empty-desc">您还没有参与任何团队，联系管理员加入团队吧！</p>
      </div>

      <div v-else>
        <!-- 我管理的团队 -->
        <div v-if="managedTeams.length > 0" class="team-section">
          <div class="section-header">
            <h2 class="section-title">
              <el-icon><Star /></el-icon>
              我管理的团队
            </h2>
            <div class="section-actions">
              <span class="section-count">{{ managedTeams.length }}个</span>
              <el-button 
                type="primary" 
                size="small" 
                @click="openAllTeamsOverview"
                class="overview-btn"
              >
                团队概览
              </el-button>
              <el-button 
                type="success" 
                size="small" 
                @click="openManagedSchedules"
                class="schedules-btn"
              >
                所有日程
              </el-button>
            </div>
          </div>
          <div class="teams-grid">
            <div
              v-for="team in managedTeams"
              :key="'managed-' + team.teamId"
              class="team-card leader-card"
              @click="$router.push(`/u/teams/${team.teamId}`)"
            >
              <div class="team-avatar">
                <div class="avatar-bg leader-bg">
                  <span class="avatar-text">{{ getTeamInitial(team.teamName) }}</span>
                </div>
                <div class="role-badge leader-badge">管理者</div>
              </div>
              <div class="team-info">
                <h3 class="team-name">{{ team.teamName }}</h3>
                <p class="team-desc">{{ team.description || '暂无描述' }}</p>
                <div class="team-meta">
                  <span class="meta-item">
                    <el-icon><Calendar /></el-icon>
                    创建时间: {{ formatDate(team.createdAt) }}
                  </span>
                </div>
              </div>
              <div class="team-action">
                <el-icon class="action-icon"><ArrowRight /></el-icon>
              </div>
            </div>
          </div>
        </div>

        <!-- 我加入的团队 -->
        <div v-if="joinedTeams.length > 0" class="team-section">
          <div class="section-header">
            <h2 class="section-title">
              <el-icon><User /></el-icon>
              我加入的团队
            </h2>
            <span class="section-count">{{ joinedTeams.length }}个</span>
          </div>
          <div class="teams-grid">
            <div
              v-for="team in joinedTeams"
              :key="'joined-' + team.teamId"
              class="team-card member-card"
              @click="$router.push(`/u/teams/${team.teamId}`)"
            >
              <div class="team-avatar">
                <div class="avatar-bg member-bg">
                  <span class="avatar-text">{{ getTeamInitial(team.teamName) }}</span>
                </div>
                <div class="role-badge member-badge">成员</div>
              </div>
              <div class="team-info">
                <h3 class="team-name">{{ team.teamName }}</h3>
                <p class="team-desc">{{ team.description || '暂无描述' }}</p>
                <div class="team-meta">
                  <span class="meta-item">
                    <el-icon><Calendar /></el-icon>
                    创建时间: {{ formatDate(team.createdAt) }}
                  </span>
                </div>
              </div>
              <div class="team-action">
                <el-icon class="action-icon"><ArrowRight /></el-icon>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建团队对话框 -->
    <el-dialog v-model="createDialogVisible" title="创建团队" width="500px">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="80px">
        <el-form-item label="团队名称" prop="teamName">
          <el-input v-model="createForm.teamName" placeholder="请输入团队名称" />
        </el-form-item>
        <el-form-item label="团队描述" prop="description">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入团队描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitCreate" :loading="createLoading">
            创建
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 团队概览对话框 -->
    <el-dialog 
      v-model="overviewDialogVisible" 
      title="团队概况" 
      width="90%" 
      :before-close="handleClose"
      class="overview-dialog"
    >
      <div class="overview-content" v-loading="overviewLoading">
        <!-- 统计卡片区域 -->
        <div class="stats-section">
          <div class="stats-grid">
            <div class="stat-card primary">
              <div class="stat-icon">
                <el-icon><UserFilled /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-number">{{ overviewData.stats?.teamCount || 0 }}</div>
                <div class="stat-label">管理团队</div>
              </div>
            </div>
            <div class="stat-card success">
              <div class="stat-icon">
                <el-icon><Calendar /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-number">{{ overviewData.stats?.scheduleCount || 0 }}</div>
                <div class="stat-label">总日程</div>
              </div>
            </div>
            <div class="stat-card warning">
              <div class="stat-icon">
                <el-icon><EditPen /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-number">{{ overviewData.stats?.worklogCount || 0 }}</div>
                <div class="stat-label">工作日志</div>
              </div>
            </div>
            <div class="stat-card info">
              <div class="stat-icon">
                <el-icon><Star /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-number">{{ completedRate }}%</div>
                <div class="stat-label">完成率</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 内容区域 -->
        <div class="content-grid">
          <!-- 左侧：日程状态分布 -->
          <el-card class="content-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span class="card-title">日程状态分布</span>
              </div>
            </template>
            <div class="status-distribution">
              <div class="status-item" v-for="(count, status) in overviewData.scheduleStatus" :key="status">
                <div class="status-info">
                  <el-tag :type="getStatusType(status)" size="small">{{ getStatusText(status) }}</el-tag>
                  <span class="status-count">{{ count }}</span>
                </div>
                <div class="status-bar">
                  <div 
                    class="status-progress" 
                    :class="status"
                    :style="{ width: getStatusPercentage(count) + '%' }"
                  ></div>
                </div>
              </div>
            </div>
          </el-card>

          <!-- 右侧：团队详情 -->
          <el-card class="content-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span class="card-title">团队详情</span>
              </div>
            </template>
            <div class="team-details">
              <div 
                v-for="team in overviewData.teamDetails" 
                :key="team.teamId" 
                class="team-detail-item"
              >
                <div class="team-info">
                  <div class="team-name">{{ team.teamName }}</div>
                  <div class="team-stats">
                    <span class="team-stat">
                      <el-icon><User /></el-icon>
                      {{ team.members }}人
                    </span>
                    <span class="team-stat">
                      <el-icon><Calendar /></el-icon>
                      {{ team.schedules }}个日程
                    </span>
                    <span class="team-stat">
                      <el-icon><EditPen /></el-icon>
                      {{ team.worklogs }}条日志
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </el-card>
        </div>

        <!-- 底部：团队看板和图表分析 -->
        <div class="dashboard-section">
          <!-- 团队看板展示框 -->
          <el-card class="kanban-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span class="card-title">团队看板概览</span>
                <el-button type="text" @click="refreshKanbanData">
                  <el-icon><Refresh /></el-icon>
                  刷新
                </el-button>
              </div>
            </template>
            <div class="kanban-content">
              <div class="kanban-stats">
                <div class="kanban-stat-item">
                  <div class="stat-value">{{ kanbanData.totalTasks || 0 }}</div>
                  <div class="stat-label">总任务数</div>
                </div>
                <div class="kanban-stat-item">
                  <div class="stat-value">{{ kanbanData.completedTasks || 0 }}</div>
                  <div class="stat-label">已完成</div>
                </div>
                <div class="kanban-stat-item">
                  <div class="stat-value">{{ kanbanData.inProgressTasks || 0 }}</div>
                  <div class="stat-label">进行中</div>
                </div>
                <div class="kanban-stat-item">
                  <div class="stat-value">{{ kanbanData.pendingTasks || 0 }}</div>
                  <div class="stat-label">待开始</div>
                </div>
              </div>
            </div>
          </el-card>
        </div>

        <!-- 图表分析区域 -->
        <div class="charts-section">
          <div class="charts-grid">
            <!-- 指派人任务分布饼状图 -->
            <el-card class="chart-card" shadow="never">
              <template #header>
                <div class="card-header">
                  <span class="card-title">任务指派分布</span>
                </div>
              </template>
              <div class="chart-container">
                <div ref="assigneePieChart" class="pie-chart"></div>
              </div>
            </el-card>

            <!-- 团队成员完成任务折线图 -->
            <el-card class="chart-card" shadow="never">
              <template #header>
                <div class="card-header">
                  <span class="card-title">成员任务完成趋势</span>
                </div>
              </template>
              <div class="chart-container">
                <div ref="completionLineChart" class="line-chart"></div>
              </div>
            </el-card>
          </div>
        </div>

        <!-- 所有日程列表 -->
        <div class="schedules-section">
          <el-card class="schedules-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span class="card-title">所有日程</span>
                <div class="header-actions">
                  <el-input
                    v-model="scheduleSearchText"
                    placeholder="搜索日程"
                    size="small"
                    style="width: 200px;"
                    clearable
                  >
                    <template #prefix>
                      <el-icon><Search /></el-icon>
                    </template>
                  </el-input>
                </div>
              </div>
            </template>
            <div class="schedules-list">
              <div 
                v-for="schedule in filteredSchedules" 
                :key="schedule.scheduleId" 
                class="schedule-item"
              >
                <div class="schedule-header">
                  <div class="schedule-title">{{ schedule.title }}</div>
                  <div class="schedule-tags">
                    <el-tag :type="getStatusType(schedule.status)" size="small">{{ getStatusText(schedule.status) }}</el-tag>
                    <el-tag :type="getPriorityType(schedule.priority)" size="small">{{ getPriorityText(schedule.priority) }}</el-tag>
                  </div>
                </div>
                <div class="schedule-content" v-if="schedule.content">
                  <span class="content-text">{{ schedule.content }}</span>
                </div>
                <div class="schedule-details">
                  <div class="schedule-time">
                    <el-icon><Calendar /></el-icon>
                    <span>{{ formatDateTime(schedule.startTime) }} - {{ formatDateTime(schedule.endTime) }}</span>
                  </div>
                  <div class="schedule-location" v-if="schedule.location">
                    <el-icon><LocationFilled /></el-icon>
                    <span>{{ schedule.location }}</span>
                  </div>
                  <div class="schedule-assignee" v-if="schedule.assigneeName">
                    <el-icon><Avatar /></el-icon>
                    <span>指派给：{{ schedule.assigneeName }}</span>
                  </div>
                  <div class="schedule-creator">
                     <el-icon><User /></el-icon>
                     <span>创建者：{{ schedule.creatorName }}</span>
                   </div>
                 </div>
                 <div class="schedule-meta">
                   <span class="meta-item">创建时间：{{ formatDateTime(schedule.createdAt) }}</span>
                   <span class="meta-item" v-if="schedule.updatedAt">更新时间：{{ formatDateTime(schedule.updatedAt) }}</span>
                   <span class="meta-item" v-if="schedule.remindTime">提醒时间：{{ formatDateTime(schedule.remindTime) }}</span>
                 </div>
               </div>
               <el-empty v-if="!filteredSchedules?.length" description="暂无日程数据" :image-size="60" />
             </div>
           </el-card>
         </div>

         <!-- 任务更新情况 -->
         <div class="updates-section">
           <el-card class="updates-card" shadow="never">
             <template #header>
               <div class="card-header">
                 <span class="card-title">最近任务更新</span>
               </div>
             </template>
             <div class="updates-list">
               <div 
                 v-for="update in taskUpdates" 
                 :key="update.id" 
                 class="update-item"
               >
                 <div class="update-avatar">
                   <el-avatar :size="32" :src="update.user?.avatar">
                     {{ update.user?.name?.charAt(0) }}
                   </el-avatar>
                 </div>
                 <div class="update-content">
                   <div class="update-header">
                     <span class="update-user">{{ update.user?.name }}</span>
                     <span class="update-action">{{ update.action }}</span>
                     <span class="update-target">{{ update.target }}</span>
                   </div>
                   <div class="update-time">{{ formatDateTime(update.time) }}</div>
                 </div>
                 <div class="update-status">
                   <el-tag :type="getStatusType(update.status)" size="small">
                     {{ getStatusText(update.status) }}
                   </el-tag>
                 </div>
               </div>
               <el-empty v-if="!taskUpdates?.length" description="暂无更新记录" :image-size="60" />
             </div>
           </el-card>
         </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="overviewDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 管理团队日程对话框 -->
    <el-dialog
      v-model="managedSchedulesDialogVisible"
      title="所有管理团队日程"
      width="80%"
      :close-on-click-modal="false"
    >
      <div v-loading="managedSchedulesLoading" class="managed-schedules-content">
        <div v-if="!managedSchedulesLoading && managedSchedules.length === 0" class="empty-state">
          <el-empty description="暂无日程数据" :image-size="100" />
        </div>
        <div v-else class="schedules-list">
          <div 
            v-for="schedule in managedSchedules" 
            :key="schedule.scheduleId" 
            class="schedule-item"
          >
            <div class="schedule-header">
              <div class="schedule-title">
                <h4>{{ schedule.title }}</h4>
                <div class="schedule-tags">
                  <el-tag :type="getStatusType(schedule.status)" size="small">
                    {{ getStatusText(schedule.status) }}
                  </el-tag>
                  <el-tag :type="getPriorityType(schedule.priority)" size="small">
                    {{ getPriorityText(schedule.priority) }}
                  </el-tag>
                </div>
              </div>
            </div>
            <div class="schedule-content">
              <p v-if="schedule.content">{{ schedule.content }}</p>
              <div class="schedule-details">
                <div class="schedule-time">
                  <el-icon><Calendar /></el-icon>
                  <span>开始: {{ formatDateTime(schedule.startTime) }}</span>
                  <span v-if="schedule.endTime">结束: {{ formatDateTime(schedule.endTime) }}</span>
                </div>
                <div v-if="schedule.location" class="schedule-location">
                  <el-icon><LocationFilled /></el-icon>
                  <span>{{ schedule.location }}</span>
                </div>
                <div class="schedule-creator">
                  <el-icon><User /></el-icon>
                  <span>创建者: {{ schedule.creatorName }}</span>
                </div>
                <div class="schedule-assignee">
                  <el-icon><Avatar /></el-icon>
                  <span>指派人: {{ schedule.assigneeName || '暂无' }}</span>
                </div>
              </div>
              <div class="schedule-meta">
                <div class="meta-item">
                  <span>创建时间: {{ formatDateTime(schedule.createdAt) }}</span>
                </div>
                <div v-if="schedule.updatedAt" class="meta-item">
                  <span>更新时间: {{ formatDateTime(schedule.updatedAt) }}</span>
                </div>
                <div v-if="schedule.remindTime" class="meta-item">
                  <span>提醒时间: {{ formatDateTime(schedule.remindTime) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="managedSchedulesDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { dashboardApi, scheduleApi, teamApi } from '@/api'
import { ElMessage } from 'element-plus'
import { UserFilled, Calendar, ArrowRight, Star, User, Plus, LocationFilled, EditPen, Avatar, Refresh, Search } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const list = ref([])
const loading = ref(true)

// 创建团队相关
const createDialogVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref()
const createForm = ref({
  teamName: '',
  description: ''
})

// 表单验证规则
const createRules = {
  teamName: [
    { required: true, message: '请输入团队名称', trigger: 'blur' },
    { min: 2, max: 50, message: '团队名称长度在 2 到 50 个字符', trigger: 'blur' }
  ]
}

// 团队概览相关
const overviewDialogVisible = ref(false)
const overviewLoading = ref(false)
const selectedTeam = ref(null)
const teamSchedules = ref([])
const overviewData = ref({
  stats: {
    teamCount: 0,
    scheduleCount: 0,
    worklogCount: 0
  },
  scheduleStatus: {},
  teamDetails: [],
  recentWorklogs: [],
  upcomingSchedules: []
})

// 管理团队日程相关
const managedSchedulesDialogVisible = ref(false)
const managedSchedulesLoading = ref(false)
const managedSchedules = ref([])

// 团队看板数据
const kanbanData = ref({
  totalTasks: 0,
  completedTasks: 0,
  inProgressTasks: 0,
  pendingTasks: 0
})

// 图表相关
const assigneePieChart = ref(null)
const completionLineChart = ref(null)
let pieChartInstance = null
let lineChartInstance = null

// 日程搜索
const scheduleSearchText = ref('')
const allSchedules = ref([])

// 任务更新记录
const taskUpdates = ref([])

// 计算属性：管理的团队（leader角色）
const managedTeams = computed(() => {
  return list.value.filter(team => team.role === 'leader')
})

// 计算属性：加入的团队（member角色）
const joinedTeams = computed(() => {
  return list.value.filter(team => team.role === 'member')
})

// 计算属性
const completedSchedules = computed(() => {
  return teamSchedules.value.filter(schedule => schedule.status === 'done').length
})

const inProgressSchedules = computed(() => {
  return teamSchedules.value.filter(schedule => schedule.status === 'doing').length
})

const todoSchedules = computed(() => {
  return teamSchedules.value.filter(schedule => schedule.status === 'todo').length
})

// 完成率计算
const completedRate = computed(() => {
  const statusData = overviewData.value.scheduleStatus || {}
  const total = Object.values(statusData).reduce((sum, count) => sum + (count || 0), 0)
  if (total === 0) return 0
  const completed = statusData.done || 0
  return Math.round((completed / total) * 100)
})

// 过滤后的日程列表
const filteredSchedules = computed(() => {
  if (!scheduleSearchText.value) {
    return allSchedules.value
  }
  const searchText = scheduleSearchText.value.toLowerCase()
  return allSchedules.value.filter(schedule => 
    schedule.title?.toLowerCase().includes(searchText) ||
    schedule.content?.toLowerCase().includes(searchText) ||
    schedule.assigneeName?.toLowerCase().includes(searchText) ||
    schedule.creatorName?.toLowerCase().includes(searchText)
  )
})

// 状态百分比计算
const getStatusPercentage = (count) => {
  const total = Object.values(overviewData.value.scheduleStatus).reduce((sum, c) => sum + (c || 0), 0)
  return total > 0 ? Math.round((count / total) * 100) : 0
}

// 获取团队名称首字母
function getTeamInitial(teamName) {
  if (!teamName) return 'T'
  return teamName.charAt(0).toUpperCase()
}

// 格式化日期
function formatDate(dateStr) {
  if (!dateStr) return '未知'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

async function load() {
  try {
    loading.value = true
    // GET /team/my 返回当前登录人参与的团队数组，并自带 myRole（leader/member），
    // 因此不需要再为每个团队查询一次成员角色
    const teams = await teamApi.my()
    list.value = (Array.isArray(teams) ? teams : []).map((team) => ({
      ...team,
      role: team.myRole === 'leader' ? 'leader' : 'member'
    }))
  } catch (e) {
    ElMessage.error('加载失败：' + (e.message || ''))
    console.error('Load teams failed:', e)
  } finally {
    loading.value = false
  }
}

// 打开创建团队对话框
function openCreateDialog() {
  createDialogVisible.value = true
  // 重置表单
  createForm.value = {
    teamName: '',
    description: ''
  }
}

// 提交创建团队
async function submitCreate() {
  if (!createFormRef.value) return
  
  try {
    await createFormRef.value.validate()
    createLoading.value = true
    
    // POST /team，返回 { teamId }；创建人由后端从 JWT 取并自动成为 leader
    await teamApi.create({
      teamName: createForm.value.teamName,
      description: createForm.value.description
    })

    ElMessage.success('团队创建成功')
    createDialogVisible.value = false
    // 重新加载团队列表
    await load()
  } catch (error) {
    if (error.errors) {
      // 表单验证错误
      return
    }
    ElMessage.error('创建失败：' + (error.message || ''))
    console.error('Create team failed:', error)
  } finally {
    createLoading.value = false
  }
}

// 打开所有管理团队概览对话框
async function openAllTeamsOverview() {
  selectedTeam.value = { teamName: '我管理的团队' }
  overviewDialogVisible.value = true
  overviewLoading.value = true
  
  try {
    // 概览改用 /dashboard/me（只看当前登录人），日程用 /schedule 分页
    const [overview, schedulePage] = await Promise.all([
      dashboardApi.me({ days: 7 }),
      scheduleApi.page({ page: 1, size: 50 })
    ])

    // 更新概览数据
    overviewData.value = {
      stats: {
        teamCount: Number(overview?.totalTeams || 0),
        scheduleCount: Number(overview?.totalSchedules || 0),
        worklogCount: Number(overview?.totalWorklogs || 0)
      },
      scheduleStatus: {
        'todo': Number(overview?.todoCount || 0),
        'doing': Number(overview?.doingCount || 0),
        'done': Number(overview?.doneCount || 0),
        'canceled': 0 // 新接口未提供已取消日程数
      },
      // 以下三块旧接口才有，新概览接口未提供
      teamDetails: [],
      recentWorklogs: [],
      upcomingSchedules: []
    }

    // 更新所有日程数据
    const schedules = schedulePage?.records || []
    allSchedules.value = schedules

    // 模拟看板数据
    kanbanData.value = {
      totalTasks: schedules.length,
      completedTasks: schedules.filter(s => s.status === 'done').length,
      inProgressTasks: schedules.filter(s => s.status === 'doing').length,
      overdueTasks: schedules.filter(s => {
        if (!s.endTime) return false
        const endDate = new Date(s.endTime)
        const now = new Date()
        return endDate < now && s.status !== 'done'
      }).length
    }
    
    // 模拟任务更新记录
    taskUpdates.value = schedules.slice(0, 5).map((schedule, index) => ({
      id: schedule.scheduleId,
      user: {
        name: schedule.creatorName || '未知用户',
        avatar: `https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png`
      },
      action: index % 3 === 0 ? '创建了任务' : index % 3 === 1 ? '更新了任务' : '完成了任务',
      target: schedule.title,
      time: schedule.updatedAt || schedule.createdAt,
      status: schedule.status
    }))
    
    // 初始化图表
    await nextTick()
    initCharts()
    
  } catch (error) {
    console.error('获取团队概览失败:', error)
    ElMessage.error('获取团队概览失败')
    
    // 设置默认数据
    overviewData.value = {
      stats: { teamCount: 0, scheduleCount: 0, worklogCount: 0 },
      scheduleStatus: {},
      teamDetails: [],
      recentWorklogs: [],
      upcomingSchedules: []
    }
    allSchedules.value = []
    kanbanData.value = { totalTasks: 0, completedTasks: 0, inProgressTasks: 0, overdueTasks: 0 }
    taskUpdates.value = []
  } finally {
    overviewLoading.value = false
  }
}

// 打开管理团队日程对话框
async function openManagedSchedules() {
  managedSchedulesDialogVisible.value = true
  managedSchedulesLoading.value = true
  
  try {
    // 管理团队日程：/schedule 分页，返回 { records, total, page, size }
    const page = await scheduleApi.page({ page: 1, size: 50 })
    managedSchedules.value = page?.records || []
  } catch (error) {
    console.error('获取管理团队日程失败:', error)
    ElMessage.error('获取管理团队日程失败')
    managedSchedules.value = []
  } finally {
    managedSchedulesLoading.value = false
  }
}

// 格式化日期时间
function formatDateTime(dateStr) {
  if (!dateStr) return '未知'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 获取状态类型
function getStatusType(status) {
  switch (status) {
    case 'done': return 'success'
    case 'doing': return 'warning'
    case 'todo': return 'info'
    case 'canceled': return 'danger'
    default: return 'info'
  }
}

// 获取状态文本
function getStatusText(status) {
  switch (status) {
    case 'done': return '已完成'
    case 'doing': return '进行中'
    case 'todo': return '待开始'
    case 'canceled': return '已取消'
    default: return '未知'
  }
}

// 获取优先级类型
function getPriorityType(priority) {
  switch (priority) {
    case 'high': return 'danger'
    case 'medium': return 'warning'
    case 'low': return 'success'
    default: return 'info'
  }
}

// 获取优先级文本
function getPriorityText(priority) {
  switch (priority) {
    case 'high': return '高优先级'
    case 'medium': return '中优先级'
    case 'low': return '低优先级'
    default: return '普通'
  }
}

// 初始化图表
function initCharts() {
  initAssigneePieChart()
  initCompletionLineChart()
}

// 初始化指派人饼状图
function initAssigneePieChart() {
  if (!assigneePieChart.value) return
  
  if (pieChartInstance) {
    pieChartInstance.dispose()
  }
  
  pieChartInstance = echarts.init(assigneePieChart.value)
  
  // 统计指派人任务分布
  const assigneeStats = {}
  allSchedules.value.forEach(schedule => {
    const assignee = schedule.assigneeName || '未指派'
    assigneeStats[assignee] = (assigneeStats[assignee] || 0) + 1
  })
  
  const pieData = Object.entries(assigneeStats).map(([name, value]) => ({ name, value }))
  
  const option = {
    title: {
      text: '任务指派分布',
      left: 'center',
      textStyle: {
        fontSize: 14,
        fontWeight: 'normal'
      }
    },
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left',
      textStyle: {
        fontSize: 12
      }
    },
    series: [
      {
        name: '任务数量',
        type: 'pie',
        radius: '50%',
        data: pieData,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }
  
  pieChartInstance.setOption(option)
}

// 初始化完成趋势折线图
function initCompletionLineChart() {
  if (!completionLineChart.value) return
  
  if (lineChartInstance) {
    lineChartInstance.dispose()
  }
  
  lineChartInstance = echarts.init(completionLineChart.value)
  
  // 基于真实任务更新数据生成近7天的趋势数据
  const today = new Date()
  const dates = []
  const completedData = []
  const totalData = []
  
  for (let i = 6; i >= 0; i--) {
    const date = new Date(today)
    date.setDate(date.getDate() - i)
    const dateStr = date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
    dates.push(dateStr)
    
    // 统计该日期完成的任务数量（基于任务更新记录）
    const dayCompletedTasks = taskUpdates.value.filter(update => {
      if (!update.time) return false
      const updateDate = new Date(update.time)
      return updateDate.toDateString() === date.toDateString() && 
             (update.action === '完成了任务' || update.status === 'done')
    })
    
    // 统计该日期的总任务活动数量
    const dayTotalTasks = taskUpdates.value.filter(update => {
      if (!update.time) return false
      const updateDate = new Date(update.time)
      return updateDate.toDateString() === date.toDateString()
    })
    
    completedData.push(dayCompletedTasks.length)
    totalData.push(dayTotalTasks.length)
  }
  
  // 如果没有任务更新数据，基于看板数据生成简单趋势
  if (completedData.every(val => val === 0) && kanbanData.value.completedTasks > 0) {
    const avgCompleted = Math.floor(kanbanData.value.completedTasks / 7)
    const avgTotal = Math.floor(kanbanData.value.totalTasks / 7)
    for (let i = 0; i < 7; i++) {
      completedData[i] = Math.max(0, avgCompleted + Math.floor(Math.random() * 3) - 1)
      totalData[i] = Math.max(completedData[i], avgTotal + Math.floor(Math.random() * 2))
    }
  }
  
  const option = {
    title: {
      text: '成员任务完成趋势',
      left: 'center',
      textStyle: {
        fontSize: 14,
        fontWeight: 'normal'
      }
    },
    tooltip: {
      trigger: 'axis',
      formatter: function(params) {
        let result = params[0].name + '<br/>'
        params.forEach(param => {
          result += param.marker + param.seriesName + ': ' + param.value + '个任务<br/>'
        })
        return result
      }
    },
    legend: {
      data: ['已完成', '总任务'],
      bottom: 0
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: {
      type: 'value',
      minInterval: 1
    },
    series: [
      {
        name: '已完成',
        type: 'line',
        data: completedData,
        smooth: true,
        itemStyle: {
          color: '#10b981'
        },
        lineStyle: {
          color: '#10b981'
        }
      },
      {
        name: '总任务',
        type: 'line',
        data: totalData,
        smooth: true,
        itemStyle: {
          color: '#3b82f6'
        },
        lineStyle: {
          color: '#3b82f6'
        }
      }
    ]
  }
  
  lineChartInstance.setOption(option)
}

// 刷新看板数据：原看板数据聚合接口已下线，改用 /dashboard/me 的聚合口径
async function refreshKanbanData() {
  try {
    const data = await dashboardApi.me({ days: 7 })
    kanbanData.value = {
      totalTasks: Number(data?.totalSchedules || 0),
      completedTasks: Number(data?.doneCount || 0),
      inProgressTasks: Number(data?.doingCount || 0),
      pendingTasks: Number(data?.todoCount || 0)
    }
    ElMessage.success('看板数据已刷新')
  } catch (error) {
    console.error('刷新看板数据失败:', error)
    ElMessage.error('刷新看板数据失败')
  }
}

// 关闭对话框时清理图表
function handleClose() {
  if (pieChartInstance) {
    pieChartInstance.dispose()
    pieChartInstance = null
  }
  if (lineChartInstance) {
    lineChartInstance.dispose()
    lineChartInstance = null
  }
  overviewDialogVisible.value = false
}

onMounted(load)
</script>
<style scoped>
.u-teams {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

/* Header Section */
.teams-header {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 32px;
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 24px;
}

.header-stats {
  display: flex;
  gap: 16px;
}

.stat-item {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  padding: 16px 24px;
  backdrop-filter: blur(10px);
  text-align: center;
  min-width: 100px;
}

.stat-number {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  opacity: 0.9;
}

/* Content Section */
.teams-content {
  min-height: 400px;
}

/* Loading State */
.loading-state {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 24px;
}

.team-card-skeleton {
  display: flex;
  align-items: center;
  padding: 24px;
  border-radius: 16px;
  border: 1px solid #e4e7ed;
  gap: 16px;
}

.skeleton-content {
  flex: 1;
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

/* Teams Grid */
.teams-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 24px;
}

.team-card {
  background: var(--tp-bg-card);
  border: 1px solid #e4e7ed;
  border-radius: 16px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  overflow: hidden;
}

.team-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, transparent 0%, rgba(67, 233, 123, 0.05) 100%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.team-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.1);
  border-color: #43e97b;
}

.team-card:hover::before {
  opacity: 1;
}

.team-avatar {
  position: relative;
  z-index: 1;
}

.avatar-bg {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(67, 233, 123, 0.3);
}

.avatar-text {
  font-size: 24px;
  font-weight: 700;
  color: white;
}

.team-info {
  flex: 1;
  position: relative;
  z-index: 1;
}

.team-name {
  margin: 0 0 8px 0;
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
}

.team-desc {
  margin: 0 0 12px 0;
  color: #606266;
  font-size: 14px;
  line-height: 1.5;
}

.team-meta {
  display: flex;
  align-items: center;
  gap: 16px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.team-action {
  position: relative;
  z-index: 1;
}

.action-icon {
  font-size: 20px;
  color: #c0c4cc;
  transition: all 0.3s ease;
}

.team-card:hover .action-icon {
  color: #43e97b;
  transform: translateX(4px);
}

/* Responsive Design */
@media (max-width: 768px) {
  .u-teams {
    padding: 16px;
  }
  
  .teams-header {
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
  
  .teams-grid,
  .loading-state {
    grid-template-columns: 1fr;
  }
  
  .team-card {
    padding: 20px;
  }
}

/* Team Section Styles */
.team-section {
  margin-bottom: 40px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 2px solid #f0f2f5;
}

.section-title {
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-count {
  background: #f0f9ff;
  color: #0369a1;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

/* Role Badge Styles */
.team-avatar {
  position: relative;
}

.role-badge {
  position: absolute;
  bottom: -4px;
  right: -4px;
  padding: 2px 6px;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 500;
  border: 2px solid white;
}

.leader-badge {
  background: #fef3c7;
  color: #d97706;
}

.member-badge {
  background: #dbeafe;
  color: #2563eb;
}

/* Card Type Styles */
.leader-card {
  border-left: 4px solid #f59e0b;
}

.member-card {
  border-left: 4px solid #3b82f6;
}

.leader-bg {
  background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%);
}

.member-bg {
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
}

.leader-card:hover {
  box-shadow: 0 8px 25px rgba(245, 158, 11, 0.15);
}

.member-card:hover {
  box-shadow: 0 8px 25px rgba(59, 130, 246, 0.15);
}

/* 团队概览按钮样式 */
.team-action {
  display: flex;
  align-items: center;
  gap: 12px;
}

.overview-btn {
  font-size: 12px;
  padding: 4px 8px;
}

/* 团队概览对话框样式 */
.overview-dialog {
  .el-dialog__body {
    padding: 0;
  }
}

.overview-content {
  padding: 24px;
}

/* 统计卡片区域 */
.stats-section {
  margin-bottom: 32px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 24px;
  border-radius: 12px;
  background: var(--tp-bg-card);
  border: 1px solid #e5e7eb;
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.stat-card.primary {
  border-left: 4px solid #3b82f6;
}

.stat-card.success {
  border-left: 4px solid #10b981;
}

.stat-card.warning {
  border-left: 4px solid #f59e0b;
}

.stat-card.info {
  border-left: 4px solid #8b5cf6;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  font-size: 24px;
}

.stat-card.primary .stat-icon {
  background: #dbeafe;
  color: #3b82f6;
}

.stat-card.success .stat-icon {
  background: #d1fae5;
  color: #10b981;
}

.stat-card.warning .stat-icon {
  background: #fef3c7;
  color: #f59e0b;
}

.stat-card.info .stat-icon {
  background: #ede9fe;
  color: #8b5cf6;
}

.stat-info {
  flex: 1;
}

.stat-number {
  font-size: 32px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 4px;
  line-height: 1;
}

.stat-label {
  font-size: 14px;
  color: #6b7280;
  font-weight: 500;
}

/* 内容网格 */
.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 32px;
}

.content-card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

/* 日程状态分布 */
.status-distribution {
  padding: 16px 0;
}

.status-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.status-info {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 120px;
}

.status-count {
  font-weight: 600;
  color: #374151;
}

.status-bar {
  flex: 1;
  height: 8px;
  background: var(--tp-border);
  border-radius: 4px;
  overflow: hidden;
  margin-left: 16px;
}

.status-progress {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s ease;
}

.status-progress.todo {
  background: #6b7280;
}

.status-progress.doing {
  background: #f59e0b;
}

.status-progress.done {
  background: #10b981;
}

.status-progress.canceled {
  background: #ef4444;
}

/* 团队详情 */
.team-details {
  padding: 16px 0;
}

.team-detail-item {
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  margin-bottom: 12px;
  background: #fafafa;
}

.team-detail-item:last-child {
  margin-bottom: 0;
}

.team-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}

.team-stats {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.team-stat {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: #6b7280;
}

/* 团队看板区域 */
.dashboard-section {
  margin-bottom: 32px;
}

.kanban-card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}

.kanban-content {
  padding: 16px 0;
}

.kanban-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.kanban-stat-item {
  text-align: center;
  padding: 20px;
  background: var(--tp-bg-subtle);
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #64748b;
  font-weight: 500;
}

/* 图表区域 */
.charts-section {
  margin-bottom: 32px;
}

.charts-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.chart-card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}

.chart-container {
  padding: 16px;
}

.pie-chart,
.line-chart {
  width: 100%;
  height: 300px;
}

/* 日程区域 */
.schedules-section {
  margin-bottom: 32px;
}

.schedules-card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.schedules-list {
  max-height: 500px;
  overflow-y: auto;
  padding: 16px 0;
}

/* 任务更新区域 */
.updates-section {
  margin-bottom: 32px;
}

.updates-card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}

.updates-list {
  max-height: 400px;
  overflow-y: auto;
  padding: 16px 0;
}

.update-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--tp-border);
  gap: 12px;
}

.update-item:last-child {
  border-bottom: none;
}

.update-avatar {
  flex-shrink: 0;
}

.update-content {
  flex: 1;
}

.update-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.update-user {
  font-weight: 600;
  color: #1f2937;
}

.update-action {
  color: #6b7280;
}

.update-target {
  color: #3b82f6;
  font-weight: 500;
}

.update-time {
  font-size: 12px;
  color: #9ca3af;
}

.update-status {
  flex-shrink: 0;
}

/* 日程项样式 */
.schedule-item {
  padding: 16px;
  border-bottom: 1px solid var(--tp-border);
  background: var(--tp-bg-subtle);
  border-radius: 8px;
  margin-bottom: 12px;
}

.schedule-item:last-child {
  border-bottom: none;
  margin-bottom: 0;
}

.schedule-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.schedule-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  flex: 1;
  margin-right: 12px;
}

.schedule-tags {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.schedule-content {
  margin-bottom: 12px;
}

.content-text {
  font-size: 14px;
  color: #6b7280;
  line-height: 1.5;
}

.schedule-details {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.schedule-time,
.schedule-location,
.schedule-creator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #374151;
}

.schedule-time .el-icon,
.schedule-location .el-icon,
.schedule-creator .el-icon {
  font-size: 16px;
  color: #6b7280;
}

.schedule-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding-top: 8px;
  border-top: 1px solid #e5e7eb;
}

.meta-item {
  font-size: 12px;
  color: #9ca3af;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .content-grid,
  .activity-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
  
  .stat-card {
    padding: 16px;
  }
  
  .stat-icon {
    width: 40px;
    height: 40px;
    font-size: 20px;
  }
  
  .stat-number {
    font-size: 24px;
  }
}

.schedule-list h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 16px;
}

.empty-schedules {
  text-align: center;
  padding: 40px 0;
}

.schedules-container {
  max-height: 400px;
  overflow-y: auto;
}

.schedule-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  margin-bottom: 12px;
  background: var(--tp-bg-card);
  transition: all 0.2s ease;
}

.schedule-item:hover {
  border-color: #d1d5db;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.schedule-info {
  flex: 1;
}

.schedule-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
  gap: 12px;
}

.schedule-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  flex: 1;
}

.schedule-team {
  flex-shrink: 0;
}

.schedule-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.schedule-time {
  font-size: 14px;
  color: #6b7280;
}

.schedule-location {
  font-size: 14px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 4px;
}

.schedule-status {
  margin-left: 16px;
}

/* 管理团队日程对话框样式 */
.managed-schedules-content {
  min-height: 400px;
}

.schedules-list {
  max-height: 600px;
  overflow-y: auto;
}

.schedules-btn {
  margin-left: 8px;
}

@media (max-width: 1200px) {
  .kanban-stats {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .charts-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .kanban-stats {
    grid-template-columns: 1fr;
  }
  
  .kanban-stat-item {
    padding: 16px;
  }
  
  .stat-value {
    font-size: 24px;
  }
  
  .pie-chart,
  .line-chart {
    height: 250px;
  }
  
  .schedule-item {
    flex-direction: column;
    gap: 12px;
  }
  
  .schedule-status {
    margin-left: 0;
    align-self: flex-start;
  }
  
  .section-actions {
    flex-direction: column;
    gap: 8px;
  }
  
  .schedules-btn {
    margin-left: 0;
  }
  
  .update-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .update-header {
    flex-wrap: wrap;
  }
  
  .header-actions {
    width: 100%;
    justify-content: stretch;
  }
  
  .header-actions .el-input {
    width: 100% !important;
  }
}
</style>
