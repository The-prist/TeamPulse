import { createRouter, createWebHistory } from 'vue-router'
import { tokenStorage } from '@/api/http'

/**
 * 路由表与全局守卫。
 *
 * 两个路由域：
 *   /     管理后台（AdminLayout）—— 仅 admin 可访问
 *   /u    用户工作台（UserLayout）—— 所有登录用户；admin 也可切换进入
 *
 * meta.hasList 控制内容区是否套用「列表页」容器宽度（见 layouts 与 styles/index.css）。
 */
const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/entry',
    name: 'roleEntry',
    component: () => import('@/views/RoleEntry.vue'),
    meta: { title: '选择入口' },
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '仪表盘' } },
      { path: 'overview/company', name: 'companyOverview', component: () => import('@/views/overview/CompanyOverview.vue'), meta: { title: '公司概览', perms: ['dashboard:company'], } },

      // ---- 物联网考勤 ----
      { path: 'attendance/dashboard', name: 'attendanceDashboard', component: () => import('@/views/attendance/AttendanceDashboard.vue'), meta: { title: '考勤看板', perms: ['attendance:view'], } },
      { path: 'attendance/records', name: 'attendanceRecords', component: () => import('@/views/attendance/AttendanceRecords.vue'), meta: { title: '打卡记录', hasList: true, perms: ['attendance:view'], } },
      { path: 'attendance/devices', name: 'attendanceDevices', component: () => import('@/views/attendance/DeviceList.vue'), meta: { title: '打卡设备', hasList: true, perms: ['device:view'], } },
      { path: 'attendance/rule', name: 'attendanceRule', component: () => import('@/views/attendance/AttendanceRule.vue'), meta: { title: '考勤规则', perms: ['attendance:rule'], } },
  { path: 'attendance/approvals', name: 'attendanceApprovals', component: () => import('@/views/attendance/MakeupApproval.vue'), meta: { title: '补卡审批', hasList: true, perms: ['attendance:approve'], } },

      { path: 'kanban', name: 'kanbanList', component: () => import('@/views/kanban/KanbanList.vue'), meta: { title: '看板管理', hasList: true, perms: ['kanban:view'], } },
      { path: 'kanban/:id', name: 'kanbanDetail', component: () => import('@/views/kanban/KanbanDetail.vue'), meta: { title: '看板详情', parent: '/kanban', perms: ['kanban:view'], } },
      { path: 'schedule', name: 'scheduleList', component: () => import('@/views/schedule/ScheduleList.vue'), meta: { title: '日程管理', hasList: true, perms: ['schedule:view'], } },
      { path: 'schedule/:id', name: 'scheduleDetail', component: () => import('@/views/schedule/ScheduleDetail.vue'), meta: { title: '日程详情', parent: '/schedule', perms: ['schedule:view'], } },
      { path: 'comments', name: 'commentList', component: () => import('@/views/comment/CommentList.vue'), meta: { title: '评论管理', hasList: true, perms: ['comment:view'], } },
      { path: 'worklogs', name: 'worklogList', component: () => import('@/views/worklog/WorklogList.vue'), meta: { title: '日志管理', hasList: true, perms: ['worklog:view'], } },
      { path: 'worklogs/:id', name: 'worklogDetail', component: () => import('@/views/worklog/WorklogDetail.vue'), meta: { title: '日志详情', parent: '/worklogs', perms: ['worklog:view'], } },
      { path: 'audit', name: 'auditLogs', component: () => import('@/views/audit/AuditLogList.vue'), meta: { title: '审计日志', hasList: true, perms: ['audit:view'], } },
  { path: 'users', name: 'userList', component: () => import('@/views/users/UserList.vue'), meta: { title: '用户管理', hasList: true, perms: ['user:view'], } },
      { path: 'teams', name: 'teamList', component: () => import('@/views/teams/TeamList.vue'), meta: { title: '团队管理', hasList: true, perms: ['team:view'], } },
    ],
  },
  {
    path: '/u',
    component: () => import('@/layouts/UserLayout.vue'),
    children: [
      { path: '', redirect: '/u/home' },
      { path: 'home', name: 'uHome', component: () => import('@/views/user/UHome.vue'), meta: { title: '首页' } },
      { path: 'overview/leader', name: 'leaderOverview', component: () => import('@/views/overview/LeaderOverview.vue'), meta: { title: '我的概览' } },
      { path: 'profile', name: 'uProfile', component: () => import('@/views/user/UProfile.vue'), meta: { title: '个人信息' } },
      { path: 'teams', name: 'uTeams', component: () => import('@/views/user/UTeams.vue'), meta: { title: '我的团队' } },
      { path: 'teams/:id', name: 'uTeamDetail', component: () => import('@/views/user/UTeamDetail.vue'), meta: { title: '团队详情', parent: '/u/teams' } },
      { path: 'schedules', name: 'uSchedules', component: () => import('@/views/user/USchedules.vue'), meta: { title: '我的日程', hasList: true } },
      { path: 'schedules/:id', name: 'uScheduleDetail', component: () => import('@/views/user/UScheduleDetail.vue'), meta: { title: '日程详情', parent: '/u/schedules' } },
      { path: 'kanban', name: 'uKanban', component: () => import('@/views/user/UKanban.vue'), meta: { title: '我的看板' } },
      { path: 'kanban/:id', name: 'uKanbanDetail', component: () => import('@/views/user/UKanbanDetail.vue'), meta: { title: '看板详情', parent: '/u/kanban' } },
      { path: 'worklogs', name: 'uWorklogs', component: () => import('@/views/user/UWorklogs.vue'), meta: { title: '我的日志', hasList: true } },
      { path: 'worklogs/:id', name: 'uWorklogDetail', component: () => import('@/views/user/UWorklogDetail.vue'), meta: { title: '日志详情', parent: '/u/worklogs' } },
      { path: 'attendance', name: 'uAttendance', component: () => import('@/views/user/UAttendance.vue'), meta: { title: '我的考勤', hasList: true } },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'notFound', component: () => import('@/views/NotFound.vue'), meta: { public: true, title: '页面不存在' } },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

function isUserDomain(path) {
  return path === '/u' || path.startsWith('/u/')
}

router.afterEach((to) => {
  if (to.meta?.title) {
    const prefix = isUserDomain(to.path) ? 'User' : 'Admin'
    document.title = `${to.meta.title} - TeamPulse ${prefix}`
  }
})

/**
 * 访问控制。
 *
 * 这里刻意直接读 tokenStorage 而不是 useAuthStore()：
 * 守卫在组件树之外执行，避免「Pinia 尚未激活」造成的初始化顺序问题。
 */
router.beforeEach((to) => {
  if (to.meta?.public) return true

  const token = tokenStorage.getToken()
  if (!token) {
    return to.path === '/login' ? true : { path: '/login', query: { redirect: to.fullPath } }
  }

  const user = tokenStorage.getUser() || {}
  // RBAC 改造前的登录态没有 permissions 字段，继续用它会被静默降级到用户工作台，
  // 不如直接清掉让用户重新登录一次，行为可预期。
  if (!Array.isArray(user.permissions)) {
    tokenStorage.clear()
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const perms = user.permissions
  const preferred = localStorage.getItem('preferredPanel')
  // 能不能进管理后台不再看角色名，而是看是否拥有任一管理类权限（与 auth store 保持一致）
  const ADMIN_PANEL_PERMS = [
    'dashboard:company', 'user:manage', 'team:manage', 'schedule:manage', 'kanban:manage',
    'worklog:manage', 'comment:manage', 'sensitive:manage', 'attendance:rule',
    'attendance:approve', 'device:manage',
  ]
  const canAdmin = perms.some((p) => ADMIN_PANEL_PERMS.includes(p))

  if (canAdmin) {
    // 管理员首次登录必须先选择进入哪套面板
    if (!preferred && to.path !== '/entry') {
      return { path: '/entry' }
    }
    if (to.path === '/login') {
      return { path: preferred === 'user' ? '/u/home' : '/dashboard' }
    }
    // 逐页权限校验：没有该页所需权限时，不进页面（否则页面会刷出一堆 403）
    const need = to.meta?.perms
    if (Array.isArray(need) && need.length && !need.some((p) => perms.includes(p))) {
      return { path: preferred === 'user' ? '/u/home' : '/dashboard', query: { denied: to.path } }
    }
    return true
  }

  // 非管理员只能待在 /u 域
  if (!isUserDomain(to.path)) {
    return { path: '/u/home' }
  }
  return true
})

export default router
