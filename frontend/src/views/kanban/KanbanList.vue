<template>
  <div class="admin-page">
    <div class="page-header">
      <h1 class="page-title">看板管理</h1>
      <p class="page-subtitle">管理系统中的所有看板</p>
    </div>
    
    <el-card class="content-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">看板列表</span>
          <div class="header-actions">
            <el-radio-group v-model="viewMode" size="small" style="margin-right: 12px">
              <el-radio-button label="all">所有看板</el-radio-button>
              <el-radio-button label="user">我的看板</el-radio-button>
            </el-radio-group>
            <el-button type="primary" @click="openCreateDialog" v-if="auth.hasPerm('kanban:manage')">
              <el-icon><Plus /></el-icon> 新增看板
            </el-button>
          </div>
        </div>
      </template>
    <!-- 加载失败与「没有数据」要能区分，否则用户会误以为真的没有数据 -->
    <el-alert
      v-if="loadError"
      type="error"
      :closable="false"
      show-icon
      class="load-error"
      title="数据加载失败：网络或后端异常，当前显示的可能不是最新数据"
    >
      <el-button size="small" type="primary" link :loading="loading" @click="loadKanbans">重新加载</el-button>
    </el-alert>
    <el-table :data="pagedList" v-loading="loading">
      <el-table-column prop="kanbanId" label="编号" width="80" />
      <el-table-column prop="kanbanName" label="名称" />
      <el-table-column prop="teamId" label="团队" width="120" />
      <el-table-column prop="description" label="描述" />
      <el-table-column prop="creatorId" label="创建者" width="100">
        <template #default="{ row }">
          {{ getUserName(row.creatorId) }}
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/kanban/${row.kanbanId}`)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div style="margin-top:12px; display:flex; justify-content:flex-end">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="list.length"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10,20,50,100]"
      />
    </div>
    
    <!-- 新增看板对话框 -->
    <el-dialog v-model="showAddDialog" title="新增看板" width="500px">
      <el-form :model="kanbanForm" label-width="80px" :rules="rules" ref="formRef">
        <el-form-item label="名称" prop="kanbanName">
          <el-input v-model="kanbanForm.kanbanName" placeholder="请输入看板名称"></el-input>
        </el-form-item>
        <el-form-item label="团队" prop="teamId">
          <el-select v-model="kanbanForm.teamId" placeholder="请选择团队" style="width: 100%" :loading="teamsLoading">
            <el-option
              v-for="team in availableTeams"
              :key="team.teamId"
              :label="`${team.teamName}`"
              :value="team.teamId"
            />
            <template #empty>
              <div style="padding: 12px; text-align: center; color: #909399;">
                <p style="margin: 0 0 8px 0;">暂无可用团队</p>
                <p style="margin: 0; font-size: 12px;">请先创建或加入团队</p>
              </div>
            </template>
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="kanbanForm.description" type="textarea" rows="3" placeholder="请输入看板描述"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showAddDialog = false">取消</el-button>
          <el-button 
            type="primary" 
            @click="submitForm" 
            :loading="submitting"
            :disabled="availableTeams.length === 0"
          >
            确定
          </el-button>
          <el-button 
            v-if="availableTeams.length === 0" 
            type="success" 
            @click="goToTeamManagement"
            style="margin-left: 8px;"
          >
            去创建团队
          </el-button>
        </div>
      </template>
    </el-dialog>
  </el-card>
  </div>
</template>

<script setup>
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore()
import { onMounted, ref, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { kanbanApi, teamApi, userApi } from '@/api'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()

const list = ref([])
const loading = ref(false)
// 加载失败与「没有数据」是两件事，用独立标志驱动告警与重试
const loadError = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const pagedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return list.value.slice(start, start + pageSize.value)
})
const viewMode = ref('all')
const showAddDialog = ref(false)
const submitting = ref(false)
const formRef = ref(null)

// 用户映射
const userMap = ref(new Map())

// 团队相关数据
const availableTeams = ref([])
const teamsLoading = ref(false)

// 表单数据
const kanbanForm = ref({
  kanbanName: '',
  teamId: '',
  description: ''
})

// 格式化时间显示
function formatDateTime(dateTime) {
  if (!dateTime) return '-'
  const date = new Date(dateTime)
  if (isNaN(date.getTime())) return dateTime
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 获取用户信息
async function loadUserInfo(userIds) {
  if (!userIds || userIds.length === 0) return
  
  try {
    // 过滤掉已经获取过的用户ID
    const newUserIds = userIds.filter(id => !userMap.value.has(id))
    if (newUserIds.length === 0) return
    
    // 批量获取用户信息（/user/list 分页返回）
    const userPage = await userApi.page({ page: 1, size: 200 })
    const allUsers = userPage?.records || []
    // 过滤出需要的用户
    const userList = allUsers.filter(user => newUserIds.includes(user.userId))
    
    // 更新用户映射
    userList.forEach(user => {
      if (user.userId) {
        userMap.value.set(user.userId, {
          username: user.username,
          nickname: user.nickname
        })
      }
    })
  } catch (e) {
    console.warn('获取用户信息失败:', e)
  }
}

// 根据用户ID获取用户名
function getUserName(creatorId) {
  if (!creatorId) return '-'
  const user = userMap.value.get(creatorId)
  return user ? (user.nickname || user.username || creatorId) : creatorId
}

// 表单验证规则
const rules = {
  kanbanName: [{ required: true, message: '请输入看板名称', trigger: 'blur' }],
  teamId: [{ required: true, message: '请选择团队', trigger: 'change' }]
}

// 加载用户可访问的团队列表
const loadAvailableTeams = async () => {
  teamsLoading.value = true
  try {
    // /team/my 返回当前登录人参与的团队数组，并自带 myRole（leader/member），
    // 因此不需要再为每个团队查询一次成员角色
    const teams = await teamApi.my()
    availableTeams.value = (Array.isArray(teams) ? teams : []).map((team) => ({
      ...team,
      role: team.myRole === 'leader' ? 'leader' : 'member'
    }))
  } catch (error) {
    console.error('加载团队列表失败:', error)
    ElMessage.error('加载团队列表失败：' + (error.message || '未知错误'))
  } finally {
    teamsLoading.value = false
  }
}

// 加载看板数据
const loadKanbans = async () => {
  loading.value = true
  loadError.value = false
  try {
    // /kanban/my 返回「本人创建的 + 所在团队」的看板数组，两种视图共用同一数据源
    const kanbans = await kanbanApi.my()
    list.value = Array.isArray(kanbans) ? kanbans : []

    // 获取所有创建者的用户信息
    const creatorIds = [...new Set(list.value.map(kanban => kanban.creatorId).filter(Boolean))]
    if (creatorIds.length > 0) {
      await loadUserInfo(creatorIds)
    }

    currentPage.value = 1
  } catch (error) {
    console.error('加载看板失败:', error)
    loadError.value = true
  } finally {
    loading.value = false
  }
}

// 打开创建对话框
const openCreateDialog = () => {
  showAddDialog.value = true
  // 重置表单
  kanbanForm.value = {
    kanbanName: '',
    teamId: '',
    description: ''
  }
  // 加载团队列表
  loadAvailableTeams()
}

// 提交表单
const submitForm = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        // 创建人由后端从 JWT 取，请求体只需 kanbanName/teamId/description
        await kanbanApi.create({
          kanbanName: kanbanForm.value.kanbanName,
          teamId: Number(kanbanForm.value.teamId),
          description: kanbanForm.value.description
        })
        ElMessage.success('看板创建成功')
        showAddDialog.value = false
        // 重置表单
        kanbanForm.value = {
          kanbanName: '',
          teamId: '',
          description: ''
        }
        // 重新加载看板列表
        loadKanbans()
      } catch (error) {
        ElMessage.error(`创建失败: ${error.message || '未知错误'}`)
      } finally {
        submitting.value = false
      }
    }
  })
}

// 跳转到团队管理页面
const goToTeamManagement = () => {
  showAddDialog.value = false
  router.push('/u/teams')
}

// 监听视图模式变化
watch(viewMode, () => {
  currentPage.value = 1
  loadKanbans()
})

onMounted(() => {
  loadKanbans()
})
</script>

<style scoped>
.admin-page {
  padding: 24px;
  background: var(--tp-bg-subtle);
  min-height: 100vh;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 8px 0;
}

.page-subtitle {
  font-size: 14px;
  color: #6b7280;
  margin: 0;
}

.content-card {
  border-radius: 12px;
  border: 1px solid #e5e7eb;
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
  background: var(--tp-bg-subtle);
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

.load-error {
  margin-bottom: 10px;
  border-radius: 8px;
}
</style>