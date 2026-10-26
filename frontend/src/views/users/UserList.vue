<template>
  <div class="admin-page">
    <div class="page-header">
      <h1 class="page-title">用户管理</h1>
      <p class="page-subtitle">管理系统中的所有用户</p>
    </div>
    
    <el-card class="content-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">用户列表</span>
          <div class="header-actions">
            <el-input v-model="keyword" placeholder="搜索用户名/邮箱/昵称" clearable style="width: 260px" @keyup.enter.native="onSearch" />
            <el-button type="primary" @click="onSearch" style="margin-left: 8px">搜索</el-button>
            <el-button type="success" @click="openCreate" style="margin-left: 8px">新增用户</el-button>
          </div>
        </div>
      </template>
  <el-table :data="users" size="small" style="width: 100%" :row-key="row => row.userId" v-loading="loading">
        <template #empty>
          <el-empty description="暂无数据" />
        </template>
        <el-table-column prop="username" label="编号" width="120" />
        <el-table-column prop="nickname" label="用户名" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="phone" label="电话" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="row.role==='admin' ? 'danger' : 'info'">{{ userRoleText(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status==='active' ? 'success' : 'warning'">{{ userStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
      <el-button size="small" @click="openEdit(row)" :disabled="loading">编辑</el-button>
      <el-popconfirm title="确认删除该用户吗？" @confirm="remove(row)">
              <template #reference>
        <el-button size="small" type="danger" :disabled="loading">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    <div style="margin-top:12px; display:flex; justify-content:flex-end">
        <el-pagination
          background
      layout="total, sizes, prev, pager, next, jumper"
      :total="total"
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :page-sizes="[10,20,50,100]"
      @current-change="load"
      @size-change="onSizeChange"
        />
      </div>
    </el-card>
  </div>

  <el-drawer v-model="drawer.visible" :title="drawer.title" size="40%">
      <el-form :model="form" label-width="88px">
        <el-form-item label="用户名"><el-input v-model="form.username" :disabled="!!form.userId"/></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" :placeholder="form.userId ? '留空则不修改密码' : '请输入初始密码'" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname"/></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email"/></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone"/></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
      <el-option label="管理员" value="admin" />
      <el-option label="普通成员" value="member" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
      <el-option label="启用" value="active" />
      <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div style="flex: 1; text-align: right">
          <el-button @click="drawer.visible=false">取消</el-button>
          <el-button type="primary" @click="submit">保存</el-button>
        </div>
      </template>
    </el-drawer>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api'
import { userRoleText, userStatusText } from '@/utils/dict'

const users = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const drawer = reactive({ visible: false, title: '' })
const form = reactive({ userId: null, username: '', password: '', nickname: '', email: '', phone: '', role: 'member', status: 'active' })
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    // /user/list 返回分页对象 { records, total, page, size }
    const resp = await userApi.page({ keyword: keyword.value, page: currentPage.value, size: pageSize.value })
    users.value = resp?.records || []
    total.value = Number(resp?.total || 0)
  } catch (e) {
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
    currentPage.value = 1
    load()
  }, 250)
}

function onSizeChange(val) {
  pageSize.value = val
  currentPage.value = 1
  load()
}

function openCreate() {
  Object.assign(form, { userId: null, username: '', password: '', nickname: '', email: '', phone: '', role: 'member', status: 'active' })
  drawer.title = '新增用户'
  drawer.visible = true
}

async function openEdit(row) {
  try {
    // 获取完整的用户信息，包括密码
    const userDetail = await userApi.detail(row.userId)
    Object.assign(form, userDetail)
    drawer.title = '编辑用户'
    drawer.visible = true
  } catch (e) {
    ElMessage.error('获取用户详情失败：' + (e.message || ''))
  }
}

async function submit() {
  try {
    if (form.userId) {
      // 编辑用户：包含密码字段，如果密码为空则后端不更新密码
      const payload = { username: form.username, nickname: form.nickname, email: form.email, phone: form.phone, role: form.role, status: form.status }
      if (form.password && form.password.trim()) {
        payload.password = form.password
      }
      await userApi.update(form.userId, payload)
      ElMessage.success('更新成功')
    } else {
      // 新增用户：密码必填
      if (!form.password || !form.password.trim()) {
        ElMessage.warning('请输入初始密码')
        return
      }
      const payload = { username: form.username, password: form.password, nickname: form.nickname, email: form.email, phone: form.phone, role: form.role, status: form.status }
      // POST /user 返回 { userId }
      const res = await userApi.create(payload)
      if (res && res.userId) form.userId = res.userId
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
    await userApi.remove(row.userId)
    ElMessage.success('删除成功')
    await load()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || ''))
  }
}

onMounted(load)
</script>

<style scoped>
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
</style>
