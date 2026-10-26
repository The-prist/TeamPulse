<template>
  <div class="admin-page">
    <div class="page-header">
      <h1 class="page-title">团队管理</h1>
      <p class="page-subtitle">管理系统中的所有团队</p>
    </div>
    
    <el-card class="content-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">团队列表</span>
          <div class="header-actions">
            <el-input v-model="keyword" placeholder="搜索团队名称/描述" clearable style="width: 260px" @keyup.enter.native="onSearch" />
            <el-button type="primary" @click="onSearch" style="margin-left: 8px">搜索</el-button>
            <el-button type="success" @click="openCreate" style="margin-left: 8px">新增团队</el-button>
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
        <el-button size="small" type="primary" link :loading="loading" @click="load">重新加载</el-button>
      </el-alert>
      <el-table :data="teams" v-loading="loading" @sort-change="onSortChange" :default-sort="{ prop: 'teamId', order: 'ascending' }">
        <el-table-column prop="teamId" label="#" width="80" />
        <el-table-column prop="teamName" label="团队名称" sortable="custom" />
        <el-table-column prop="description" label="描述" sortable="custom" />
        <el-table-column prop="creatorId" label="创建者" width="120" sortable="custom">
          <template #default="{ row }">
            {{ getUserName(row.creatorId) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" sortable="custom">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该团队吗？" @confirm="remove(row)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无数据">
            <el-button type="primary" @click="openCreate">去创建</el-button>
          </el-empty>
        </template>
      </el-table>
    </el-card>

    <el-drawer v-model="drawer.visible" :title="drawer.title" size="60%">
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="基本信息" name="basic">
          <el-form :model="form" label-width="88px">
            <el-form-item label="团队名称"><el-input v-model="form.teamName" /></el-form-item>
            <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="成员管理" name="members" v-if="form.teamId">
          <div class="member-management">
            <div class="member-toolbar">
              <el-button type="primary" size="small" @click="openAddMember">添加成员</el-button>
            </div>
            <el-table :data="members" size="small" style="width: 100%; margin-top: 16px" v-loading="membersLoading">
              <template #empty>
                <el-empty description="暂无成员" />
              </template>
              <el-table-column prop="userId" label="用户编号" width="80" />
              <el-table-column label="姓名" min-width="140">
                <template #default="{ row }">
                  {{ row.nickname || row.username || ('用户 #' + row.userId) }}
                </template>
              </el-table-column>
              <el-table-column prop="teamRole" label="角色" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.teamRole === 'leader' ? 'danger' : 'primary'">{{ getRoleText(row.teamRole) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="joinTime" label="加入时间" width="180" />
              <el-table-column label="操作" width="150">
                <template #default="{ row }">
                  <el-button size="small" @click="editMemberRole(row)">修改角色</el-button>
                  <el-popconfirm title="确认移除该成员吗？" @confirm="removeMember(row)">
                    <template #reference>
                      <el-button size="small" type="danger">移除</el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <div style="flex: 1; text-align: right">
          <el-button @click="drawer.visible=false">取消</el-button>
          <el-button type="primary" @click="submit" v-if="activeTab === 'basic'">保存</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 添加成员对话框 -->
    <el-dialog v-model="memberDialog.visible" title="添加成员" width="400px">
      <el-form :model="memberForm" label-width="80px">
        <el-form-item label="用户编号">
          <el-input v-model="memberForm.userId" placeholder="请输入用户ID" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="memberForm.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="普通成员" value="member" />
            <el-option label="团队领导" value="leader" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="addMember">确定</el-button>
      </template>
    </el-dialog>

    <!-- 修改角色对话框 -->
    <el-dialog v-model="roleDialog.visible" title="修改角色" width="400px">
      <el-form :model="roleForm" label-width="80px">
        <el-form-item label="用户">
          <el-input v-model="roleForm.userName" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="roleForm.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="普通成员" value="member" />
            <el-option label="团队领导" value="leader" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="updateMemberRole">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { teamApi, userApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const teams = ref([])
const keyword = ref('')
const drawer = reactive({ visible: false, title: '' })
const form = reactive({ teamId: null, teamName: '', description: '' })
const loading = ref(false)
// 加载失败与「没有数据」是两件事，用独立标志驱动告警与重试
const loadError = ref(false)
const activeTab = ref('basic')

// 用户映射
const userMap = ref(new Map())

// 成员管理相关数据
const members = ref([])
const membersLoading = ref(false)
const memberDialog = reactive({ visible: false })
const memberForm = reactive({ userId: '', role: 'member' })
const roleDialog = reactive({ visible: false })
const roleForm = reactive({ userId: '', userName: '', role: '' })

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

async function load() {
  loading.value = true
  loadError.value = false
  try {
    if (!auth.user?.userId) {
      ElMessage.error('请先登录')
      teams.value = []
      return
    }

    // 有 team:view 权限走分页接口（后端按数据范围过滤）；否则退回「我参与的团队」
    let records = []
    if (auth.hasPerm('team:view')) {
      const page = await teamApi.page({ keyword: keyword.value, page: 1, size: 200 })
      records = page?.records || []
    } else {
      records = (await teamApi.my()) || []
    }
    teams.value = Array.isArray(records) ? records : []

    // 获取所有创建者的用户信息
    const creatorIds = [...new Set(teams.value.map(team => team.creatorId).filter(Boolean))]
    if (creatorIds.length > 0) {
      await loadUserInfo(creatorIds)
    }
  } catch (e) {
    loadError.value = true
    teams.value = []
    if (String(e.message || '').startsWith('403')) {
      ElMessage.error('没有权限，请使用管理员账号登录');
    } else {
      ElMessage.error('加载失败：' + (e.message || ''))
    }
  } finally {
    loading.value = false
  }
}

let timer = null
function onSearch() {
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => {
    load()
  }, 250)
}

function onSortChange({ column, prop, order }) {
  // 处理排序逻辑
  console.log('排序变化:', { column, prop, order })
  // 这里可以根据需要实现排序逻辑
}

function openCreate() {
  Object.assign(form, { teamId: null, teamName: '', description: '' })
  drawer.title = '新增团队'
  activeTab.value = 'basic'
  members.value = []
  drawer.visible = true
}

function openEdit(row) {
  Object.assign(form, row)
  drawer.title = '编辑团队'
  activeTab.value = 'basic'
  drawer.visible = true
  // 如果是编辑现有团队，加载成员数据
  if (row.teamId) {
    loadMembers(row.teamId)
  }
}

async function submit() {
  try {
    if (!auth.user?.userId) {
      ElMessage.error('请先登录')
      return
    }
    
    if (form.teamId) {
      // PUT /team/{teamId}
      await teamApi.update(form.teamId, {
        teamName: form.teamName,
        description: form.description
      })
      ElMessage.success('更新成功')
    } else {
      // POST /team，返回 { teamId }；创建人由后端从 JWT 取并自动成为 leader
      const response = await teamApi.create({
        teamName: form.teamName,
        description: form.description
      })
      if (response?.teamId) {
        form.teamId = response.teamId
      }
      ElMessage.success('创建成功')
    }
    drawer.visible = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || ''))
  }
}

async function remove(row) {
  try {
    await teamApi.remove(row.teamId)
    ElMessage.success('删除成功')
    await load()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || ''))
  }
}

// 成员管理相关方法
async function loadMembers(teamId) {
  membersLoading.value = true
  try {
    // GET /team/{teamId}/members 直接返回成员数组
    const list = await teamApi.members(teamId)
    members.value = Array.isArray(list) ? list : []
  } catch (e) {
    members.value = []
    ElMessage.error('加载成员失败：' + (e.message || ''))
  } finally {
    membersLoading.value = false
  }
}

function openAddMember() {
  Object.assign(memberForm, { userId: '', role: 'member' })
  memberDialog.visible = true
}

async function addMember() {
  if (!memberForm.userId) {
    ElMessage.error('请输入用户ID')
    return
  }
  try {
    // POST /team/{teamId}/members
    await teamApi.addMember(form.teamId, {
      userId: Number(memberForm.userId),
      teamRole: memberForm.role
    })
    ElMessage.success('添加成员成功')
    memberDialog.visible = false
    await loadMembers(form.teamId)
  } catch (e) {
    ElMessage.error('添加成员失败：' + (e.message || ''))
  }
}

function editMemberRole(row) {
  Object.assign(roleForm, {
    userId: row.userId,
    userName: row.userName || row.userId,
    role: row.teamRole
  })
  roleDialog.visible = true
}

async function updateMemberRole() {
  try {
    // PUT /team/{teamId}/members/{userId}/role，请求体只有 teamRole
    await teamApi.updateMemberRole(form.teamId, roleForm.userId, roleForm.role)
    ElMessage.success('修改角色成功')
    roleDialog.visible = false
    await loadMembers(form.teamId)
  } catch (e) {
    ElMessage.error('修改角色失败：' + (e.message || ''))
  }
}

async function removeMember(row) {
  try {
    // DELETE /team/{teamId}/members/{userId}
    await teamApi.removeMember(form.teamId, row.userId)
    ElMessage.success('移除成员成功')
    await loadMembers(form.teamId)
  } catch (e) {
    ElMessage.error('移除成员失败：' + (e.message || ''))
  }
}

function getRoleText(role) {
  return role === 'leader' ? '团队领导' : '普通成员'
}

onMounted(load)
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

.member-management {
  padding: 16px 0;
}

.member-toolbar {
  display: flex;
  justify-content: flex-start;
  align-items: center;
}

:deep(.el-card__header) {
  padding: 20px 24px;
  border-bottom: 1px solid var(--tp-border);
  background: #fafbfc;
}

:deep(.el-card__body) {
  padding: 24px;
}

.admin-page {
  padding: 24px;
  background-color: #f5f5f5;
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
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 500;
  color: #1f2937;
}

.header-actions {
  display: flex;
  align-items: center;
}

:deep(.el-card__header) {
  padding: 20px 24px;
  border-bottom: 1px solid var(--tp-border);
  background: #fafbfc;
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