<!--
  管理后台布局：左侧分组菜单 + 顶栏（身份 / 切换面板 / 退出）+ 内容区。
  meta.hasList 决定内容区是否使用「列表页」容器（见 styles/index.css 的 .content-container）。
-->
<template>
  <el-container class="layout">
    <el-aside width="216px" class="aside">
      <div class="brand">
        <div class="brand-mark">TP</div>
        <span class="brand-name">TeamPulse</span>
      </div>

      <el-menu router :default-active="activeMenu" class="menu">
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/overview/company">
          <el-icon><DataAnalysis /></el-icon>
          <span>公司概览</span>
        </el-menu-item>

        <!-- 物联网考勤：本项目的差异点，单独分组 -->
        <el-menu-item-group title="物联网考勤">
          <el-menu-item index="/attendance/dashboard">
            <el-icon><Monitor /></el-icon>
            <span>考勤看板</span>
          </el-menu-item>
          <el-menu-item index="/attendance/records">
            <el-icon><Clock /></el-icon>
            <span>打卡记录</span>
          </el-menu-item>
          <el-menu-item index="/attendance/devices">
            <el-icon><Cpu /></el-icon>
            <span>打卡设备</span>
          </el-menu-item>
          <el-menu-item index="/attendance/rule">
            <el-icon><Setting /></el-icon>
            <span>考勤规则</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="协作">
          <el-menu-item index="/schedule">
            <el-icon><Calendar /></el-icon>
            <span>日程管理</span>
          </el-menu-item>
          <el-menu-item index="/kanban">
            <el-icon><Grid /></el-icon>
            <span>看板管理</span>
          </el-menu-item>
          <el-menu-item index="/worklogs">
            <el-icon><Document /></el-icon>
            <span>日志管理</span>
          </el-menu-item>
          <el-menu-item index="/comments">
            <el-icon><ChatDotRound /></el-icon>
            <span>评论管理</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="组织">
          <el-menu-item index="/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/teams">
            <el-icon><UserFilled /></el-icon>
            <span>团队管理</span>
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
          <el-tag size="small" :type="auth.isAdmin ? 'danger' : 'info'" effect="plain">
            {{ auth.role || 'member' }}
          </el-tag>
          <span class="user-name">{{ auth.displayName }}</span>
          <el-button v-if="auth.isAdmin" link @click="switchPanel">切换面板</el-button>
          <el-button link type="danger" @click="handleLogout">退出</el-button>
        </div>
      </el-header>

      <el-main class="main">
        <div :class="['content-container', { 'has-list': hasList, 'is-wide': isWide }]">
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
/** 详情页高亮其父级菜单，避免左侧菜单失去选中态 */
const activeMenu = computed(() => route.meta?.parent || route.path)
/** 仪表盘与看板这类宽内容页不加最大宽度限制 */
const isWide = computed(() => ['/dashboard', '/overview/company', '/attendance/dashboard'].includes(route.path))

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录？', '退出', { type: 'warning' })
  } catch {
    return
  }
  auth.logout()
  router.replace('/login')
}

function switchPanel() {
  router.push('/entry')
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
  background: linear-gradient(135deg, #1d4ed8, #0ea5e9);
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
  background: var(--tp-primary-soft);
  color: #1d4ed8;
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
.user-name {
  font-size: 13px;
  color: #334155;
}
.main {
  padding: 0;
  background: var(--tp-bg-page);
  overflow-y: auto;
}
</style>
