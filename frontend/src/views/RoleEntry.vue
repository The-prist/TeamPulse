<!--
  面板选择页。
  管理员同时拥有「管理后台」和「用户工作台」两套界面，首次登录强制选一次，
  选择结果写入 localStorage.preferredPanel，路由守卫据此决定落点。

  配色全部走 --tp-* 变量（暗色模式下靠 theme.css 里的覆盖自动适配）；
  点击时以卡片为圆心扩散一个与落点页同色的圆，完成转场。
-->
<template>
  <div class="entry-page">
    <el-card class="entry-card" shadow="never">
      <template #header>
        <div class="header">
          <div class="title">选择进入的面板</div>
          <div class="entry-sub">管理员也可以使用用户工作台；之后可在顶栏随时切换</div>
        </div>
      </template>

      <div class="options">
        <button class="option admin" type="button" @click="choose($event, 'admin')">
          <el-icon class="option-icon"><DataAnalysis /></el-icon>
          <div class="option-body">
            <div class="option-title">管理后台</div>
            <div class="option-desc">用户 / 团队 / 日程 / 看板 / 打卡设备与考勤统计</div>
          </div>
          <el-icon class="option-arrow"><ArrowRight /></el-icon>
        </button>

        <button class="option user" type="button" @click="choose($event, 'user')">
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
import { circleTransition } from '@/utils/pageTransition'

const router = useRouter()
const auth = useAuthStore()

onMounted(() => {
  // 非管理员没有选择权，直接回工作台
  if (!auth.canUseAdminPanel) {
    router.replace('/u/home')
  }
})

/** 记住选择，并用圆形转场进入对应面板 */
function choose(event, panel) {
  auth.setPreferredPanel(panel)
  const origin = event?.currentTarget ?? event?.target
  circleTransition({
    origin,
    navigate: () => router.push(panel === 'admin' ? '/dashboard' : '/u/home'),
  })
}
</script>

<style scoped>
.entry-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  /* 暗色下由 --tp-bg-page 接管，不再固定成浅蓝渐变 */
  background: var(--tp-bg-page);
  transition: background 0.3s ease;
}

.entry-card {
  width: 100%;
  max-width: 620px;
  border-radius: 14px;
  background: var(--tp-bg-card);
  border: 1px solid var(--tp-border);
}

.header .title {
  font-size: 18px;
  font-weight: 650;
  color: var(--tp-text-strong);
}

.header .entry-sub {
  margin-top: 5px;
  font-size: 12.5px;
  color: var(--tp-text-muted);
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
  border: 1px solid var(--tp-border);
  background: var(--tp-surface);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease, background 0.18s ease;
}

.option:hover {
  transform: translateY(-2px);
  box-shadow: var(--tp-shadow-hover);
}

.option.admin:hover {
  border-color: var(--tp-primary);
}

.option.user:hover {
  border-color: #10b981;
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

/* 图标底色用半透明主色，亮/暗两种主题下都不会变成"白底黑图" */
.option.admin .option-icon {
  background: rgba(37, 99, 235, 0.16);
  color: #4f8ef7;
}

.option.user .option-icon {
  background: rgba(16, 185, 129, 0.16);
  color: #34d1a0;
}

.option-body {
  flex: 1;
}

.option-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--tp-text-strong);
}

.option-desc {
  margin-top: 3px;
  font-size: 12.5px;
  color: var(--tp-text-muted);
}

.option-arrow {
  color: var(--tp-text-faint);
}

.footer-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 18px;
  font-size: 12px;
  color: var(--tp-text-muted);
}
</style>
