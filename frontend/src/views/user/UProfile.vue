<template>
  <div class="u-profile">
    <!-- Profile Header -->
    <div class="profile-header">
      <div class="header-content">
        <div class="avatar-section">
          <div class="avatar-preview">
            <img v-if="form.avatar" :src="form.avatar" alt="头像" class="avatar-img" />
            <div v-else class="avatar-placeholder">
              <el-icon><User /></el-icon>
            </div>
          </div>
          <div class="avatar-info">
            <h2 class="username">{{ form.nickname || form.username }}</h2>
            <p class="user-role">普通用户</p>
          </div>
        </div>
      </div>
    </div>

    <div class="profile-content">
      <!-- Personal Information -->
      <el-card class="info-card">
        <template #header>
          <div class="card-header">
            <el-icon class="header-icon"><User /></el-icon>
            <span class="header-title">个人信息</span>
          </div>
        </template>
        <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" class="profile-form">
          <div class="form-grid">
            <el-form-item label="用户名" class="form-item">
              <el-input v-model="form.username" disabled class="disabled-input">
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="昵称" prop="nickname" class="form-item">
              <el-input v-model="form.nickname" placeholder="请输入昵称">
                <template #prefix>
                  <el-icon><Edit /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="邮箱" prop="email" class="form-item">
              <el-input v-model="form.email" placeholder="请输入邮箱">
                <template #prefix>
                  <el-icon><Message /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="手机" class="form-item">
              <el-input v-model="form.phone" placeholder="请输入手机号">
                <template #prefix>
                  <el-icon><Phone /></el-icon>
                </template>
              </el-input>
            </el-form-item>
          </div>
          <el-form-item label="头像链接" class="avatar-input">
            <el-input v-model="form.avatar" placeholder="请输入头像链接">
              <template #prefix>
                <el-icon><Picture /></el-icon>
              </template>
            </el-input>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- Password Change -->
      <el-card class="password-card">
        <template #header>
          <div class="card-header">
            <el-icon class="header-icon"><Lock /></el-icon>
            <span class="header-title">修改密码</span>
          </div>
        </template>
        <el-form :model="passwords" label-width="100px" class="password-form">
          <div class="form-grid">
            <el-form-item label="旧密码" class="form-item">
              <el-input v-model="passwords.oldPwd" type="password" show-password placeholder="修改密码时必填">
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="新密码" class="form-item">
              <el-input v-model="passwords.newPwd" type="password" show-password placeholder="留空则不修改">
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="确认密码" class="form-item">
              <el-input v-model="passwords.confirm" type="password" show-password placeholder="再次输入新密码">
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>
          </div>
        </el-form>
      </el-card>

      <!-- Action Buttons -->
      <div class="action-buttons">
        <el-button size="large" @click="onReset">重置</el-button>
        <el-button type="primary" size="large" @click="onSave" :loading="saving">
          <el-icon><Check /></el-icon>
          保存更改
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, Edit, Message, Phone, Picture, Lock, Check } from '@element-plus/icons-vue'
import { userApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const formRef = ref(null)
const form = reactive({ userId: null, username: '', nickname: '', email: '', phone: '', avatar: '' })
const passwords = reactive({ oldPwd: '', newPwd: '', confirm: '' })
const saving = ref(false)

const rules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: ['blur','change'] }],
}

async function load() {
  const uid = auth.user?.userId
  if (!uid) {
    ElMessage.error('未登录')
    router.push('/login')
    return
  }
  try {
    // GET /user/self 直接返回当前登录人资料（身份取自 JWT）
    const user = await userApi.self()
    if (!user || typeof user !== 'object') throw new Error('返回异常')
    Object.assign(form, user)
  } catch (e) {
    ElMessage.error('加载失败：' + (e.message||''))
  }
}

async function onSave() {
  // 校验基本表单
  try { await formRef.value?.validate() } catch { return }
  // 校验密码一致
  if (passwords.newPwd || passwords.confirm) {
    if (passwords.newPwd !== passwords.confirm) {
      ElMessage.error('两次输入的密码不一致')
      return
    }
    if (!passwords.oldPwd) {
      ElMessage.error('请填写旧密码')
      return
    }
  }
  saving.value = true
  try {
    const base = { nickname: form.nickname, email: form.email, phone: form.phone, avatar: form.avatar }
    // PUT /user/self
    await userApi.updateSelf(base)
    if (passwords.newPwd) {
      // PUT /user/self/password
      try { await userApi.changePassword({ oldPassword: passwords.oldPwd, newPassword: passwords.newPwd }) } catch {}
    }
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message||''))
  } finally {
    saving.value = false
  }
}

function onReset() {
  passwords.oldPwd = ''
  passwords.newPwd = ''
  passwords.confirm = ''
  load()
}

onMounted(load)
</script>

<style scoped>
.u-profile {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
}

.profile-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  padding: 30px;
  margin-bottom: 24px;
  color: white;
}

.header-content {
  display: flex;
  align-items: center;
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 20px;
}

.avatar-preview {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  border: 3px solid rgba(255, 255, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.1);
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  font-size: 32px;
  color: rgba(255, 255, 255, 0.8);
}

.username {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 600;
}

.user-role {
  margin: 0;
  opacity: 0.8;
  font-size: 14px;
}

.profile-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.info-card, .password-card {
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  border: none;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #303133;
}

.header-icon {
  font-size: 18px;
  color: #409eff;
}

.profile-form, .password-form {
  padding: 20px 0;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}

.form-item {
  margin-bottom: 0;
}

.avatar-input {
  grid-column: 1 / -1;
}

.disabled-input :deep(.el-input__inner) {
  background-color: var(--tp-bg-subtle);
  color: #909399;
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 20px 0;
}

.action-buttons .el-button {
  min-width: 120px;
}

@media (max-width: 768px) {
  .u-profile {
    padding: 16px;
  }
  
  .profile-header {
    padding: 20px;
  }
  
  .avatar-section {
    flex-direction: column;
    text-align: center;
    gap: 16px;
  }
  
  .form-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  
  .action-buttons {
    flex-direction: column;
    align-items: center;
  }
}
</style>
