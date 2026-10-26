<!--
  用户工作台布局。与后台布局共用同一套视觉规范，菜单项按「个人视角」组织。
-->
<template>
  <el-container class="layout">
    <el-aside width="216px" class="aside">
      <div class="brand">
        <div class="brand-mark">TP</div>
        <span class="brand-name">TeamPulse</span>
      </div>

      <el-menu router :default-active="activeMenu" class="menu">
        <el-menu-item index="/u/home">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/u/overview/leader">
          <el-icon><DataAnalysis /></el-icon>
          <span>我的概览</span>
        </el-menu-item>

        <el-menu-item-group title="我的工作">
          <el-menu-item index="/u/schedules">
            <el-icon><Calendar /></el-icon>
            <span>我的日程</span>
          </el-menu-item>
          <el-menu-item index="/u/kanban">
            <el-icon><Grid /></el-icon>
            <span>我的看板</span>
          </el-menu-item>
          <el-menu-item index="/u/worklogs">
            <el-icon><Document /></el-icon>
            <span>我的日志</span>
          </el-menu-item>
          <el-menu-item index="/u/attendance">
            <el-icon><Clock /></el-icon>
            <span>我的考勤</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="协作">
          <el-menu-item index="/u/teams">
            <el-icon><UserFilled /></el-icon>
            <span>我的团队</span>
          </el-menu-item>
          <el-menu-item index="/u/profile">
            <el-icon><User /></el-icon>
            <span>个人信息</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <Breadcrumb />
        </div>
        <div class="header-right">
          <el-tag size="small" type="info" effect="plain">{{ auth.role || 'member' }}</el-tag>
          <el-button link @click="$router.push('/u/profile')">{{ auth.displayName }}</el-button>
          <el-button v-if="auth.isAdmin" link @click="$router.push('/entry')">切换面板</el-button>
          <el-button link type="danger" @click="handleLogout">退出</el-button>
        </div>
      </el-header>

      <el-main class="main">
        <div :class="['content-container', { 'has-list': hasList }]">
          <router-view />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const hasList = computed(() => Boolean(route.meta?.hasList))
const activeMenu = computed(() => route.meta?.parent || route.path)

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录？', '退出', { type: 'warning' })
  } catch {
    return
  }
  auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.layout {
  height: 100vh;
}
.aside {
  border-right: 1px solid var(--tp-border);
  background: #fbfdff;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}
.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid var(--tp-border);
  flex: none;
}
.brand-mark {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #047857, #10b981);
}
.brand-name {
  font-size: 15px;
  font-weight: 650;
  color: #0f172a;
  letter-spacing: 0.3px;
}
.menu {
  border-right: none;
  padding: 6px 0 16px;
}
.menu :deep(.el-menu-item-group__title) {
  padding-left: 18px !important;
  font-size: 11.5px;
  color: #94a3b8;
  letter-spacing: 0.4px;
}
.menu :deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  margin: 2px 8px;
  border-radius: 7px;
  font-size: 13.5px;
}
.menu :deep(.el-menu-item.is-active) {
  background: #ecfdf5;
  color: #047857;
  font-weight: 600;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  border-bottom: 1px solid var(--tp-border);
  background: #fbfdff;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.main {
  padding: 0;
  background: var(--tp-bg-page);
  overflow-y: auto;
}
</style>
