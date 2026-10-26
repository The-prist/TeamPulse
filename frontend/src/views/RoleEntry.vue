<!--
  面板选择页。
  管理员同时拥有「管理后台」和「用户工作台」两套界面，首次登录强制选一次，
  选择结果写入 localStorage.preferredPanel，路由守卫据此决定落点。
-->
<template>
  <div class="entry-page">
    <el-card class="entry-card" shadow="never">
      <template #header>
        <div class="header">
          <div class="title">选择进入的面板</div>
          <div class="subtitle">管理员也可以使用用户工作台；之后可在顶栏随时切换</div>
        </div>
      </template>

      <div class="options">
        <button class="option admin" type="button" @click="choose('admin')">
          <el-icon class="option-icon"><DataAnalysis /></el-icon>
          <div class="option-body">
            <div class="option-title">管理后台</div>
            <div class="option-desc">用户 / 团队 / 日程 / 看板 / 打卡设备与考勤统计</div>
          </div>
          <el-icon class="option-arrow"><ArrowRight /></el-icon>
        </button>

        <button class="option user" type="button" @click="choose('user')">
          <el-icon class="option-icon"><User /></el-icon>
          <div class="option-body">
            <div class="option-title">用户工作台</div>
            <div class="option-desc">我的日程 / 看板 / 日志 / 考勤</div>
          </div>
          <el-icon class="option-arrow"><ArrowRight /></el-icon>
        </button>
      </div>

      <div class="footer-tip">
        <el-icon><InfoFilled /></el-icon>
        当前账号：{{ auth.displayName }}（{{ auth.roleName || auth.role }}）
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, DataAnalysis, InfoFilled, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

onMounted(() => {
  // 非管理员没有选择权，直接回工作台
  if (!auth.canUseAdminPanel) {
    router.replace('/u/home')
  }
})

function choose(panel) {
  auth.setPreferredPanel(panel)
  router.push(panel === 'admin' ? '/dashboard' : '/u/home')
}
</script>

<style scoped>
.entry-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(160deg, #eff6ff 0%, #e0f2fe 45%, var(--tp-bg-subtle) 100%);
}
.entry-card {
  width: 100%;
  max-width: 620px;
  border-radius: 14px;
}
.header .title {
  font-size: 18px;
  font-weight: 650;
  color: #0f172a;
}
.header .subtitle {
  margin-top: 5px;
  font-size: 12.5px;
  color: #64748b;
}
.options {
  display: grid;
  gap: 14px;
}
.option {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 18px 20px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}
.option:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px -10px rgba(15, 23, 42, 0.28);
}
.option.admin:hover {
  border-color: #93c5fd;
}
.option.user:hover {
  border-color: #6ee7b7;
}
.option-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-size: 20px;
  flex: none;
}
.option.admin .option-icon {
  background: #dbeafe;
  color: #1d4ed8;
}
.option.user .option-icon {
  background: #d1fae5;
  color: #047857;
}
.option-body {
  flex: 1;
}
.option-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}
.option-desc {
  margin-top: 3px;
  font-size: 12.5px;
  color: #64748b;
}
.option-arrow {
  color: #94a3b8;
}
.footer-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 18px;
  font-size: 12px;
  color: #64748b;
}
</style>
