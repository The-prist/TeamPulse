import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import { tokenStorage } from '@/api/http'

/**
 * 登录态 + 权限。
 *
 * 用 Pinia 而不是直接读 localStorage 的原因：
 *   - 响应式：顶栏用户名、菜单权限能随登录/退出即时变化
 *   - 单一入口：登录、退出、刷新资料都走同一处，避免散落在各页面
 *
 * RBAC 改造后，这里除了账号信息还保存**角色、数据范围与权限点**：
 * 菜单和按钮按 hasPerm() 渲染，后端用同一套权限点做接口校验，
 * 前后端共用一份权限字典，避免「前端藏了按钮、后端没拦」的假安全。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(tokenStorage.getToken())
  const user = ref(tokenStorage.getUser())

  const isLoggedIn = computed(() => Boolean(token.value))
  const role = computed(() => (user.value?.role || '').toLowerCase())
  const roleName = computed(() => user.value?.roleName || role.value)
  /** 数据范围：ALL / DEPT / SELF */
  const dataScope = computed(() => (user.value?.dataScope || 'SELF').toUpperCase())
  const permissions = computed(() => user.value?.permissions || [])
/** 当前登录用户 id（评论/日志等「本人可操作」判断用） */
const userId = computed(() => user.value?.userId ?? null)
  const departmentId = computed(() => user.value?.departmentId || null)
  const departmentName = computed(() => user.value?.departmentName || '')

  /** 系统管理员：不受数据范围限制（用于展示身份标签） */
  const isAdmin = computed(() => dataScope.value === 'ALL' && permissions.value.length > 0)

  /**
   * 能否进入「管理后台」面板。
   * 不能再用角色名判断（角色已细分为 6 个），改为看是否拥有任一"管理类"权限：
   * 部门经理有 team:manage / attendance:approve，HR 有 attendance:rule，
   * 审计有 dashboard:company，普通员工一个都没有 → 只能待在用户工作台。
   */
  const ADMIN_PANEL_PERMS = [
    'dashboard:company', 'user:manage', 'team:manage', 'schedule:manage', 'kanban:manage',
    'worklog:manage', 'comment:manage', 'sensitive:manage', 'attendance:rule',
    'attendance:approve', 'device:manage',
  ]
  const canUseAdminPanel = computed(() => permissions.value.some((p) => ADMIN_PANEL_PERMS.includes(p)))
  const displayName = computed(() => user.value?.nickname || user.value?.username || '')
  /** 管理员可在「管理后台」和「用户工作台」两套界面间切换 */
  const preferredPanel = computed(() => localStorage.getItem('preferredPanel') || '')

  /** 是否拥有某个权限点，如 attendance:approve */
  function hasPerm(key) {
    if (!key) return true
    // 超管恒为 true：与后端 LoginUser.hasPermission 保持一致。
    // 即使登录态里缓存的是旧权限（空数组）或库里漏了授权行，
    // 超管也不会出现「菜单按钮突然消失」这种像 bug 的现象。
    if (role.value === 'sysadmin') return true
    return permissions.value.includes(key)
  }

  /** 任意一个权限点命中即可 */
  function hasAnyPerm(keys) {
    if (!keys || !keys.length) return true
    if (role.value === 'sysadmin') return true
    return keys.some((k) => permissions.value.includes(k))
  }

  function applyLoginData(data) {
    user.value = {
      userId: data.userId,
      username: data.username,
      nickname: data.nickname,
      role: data.role,
      roleName: data.roleName,
      employeeNo: data.employeeNo,
      departmentId: data.departmentId,
      departmentName: data.departmentName,
      dataScope: data.dataScope,
      permissions: data.permissions || [],
    }
    tokenStorage.setUser(user.value)
  }

  async function login(username, password, captchaTicket) {
    const payload = { username, password }
    if (captchaTicket) payload.captchaTicket = captchaTicket
    const data = await authApi.login(payload)
    token.value = data.token
    tokenStorage.setToken(data.token)
    applyLoginData(data)
    return data
  }

  /** 刷新页面时用 token 换回最新用户信息，顺便验证 token 是否仍有效 */
  async function refreshProfile() {
    if (!token.value) return null
    const me = await authApi.me()
    // /auth/me 现在会返回 permissions / dataScope / roleName / departmentName，
    // 因此刷新页面就能「自愈」：旧会话缺权限字段时，这里会被后端的最新值覆盖，
    // 不再出现「刷新一次按钮全消失、必须重新登录」的问题。
    const previous = user.value || {}
    user.value = {
      ...previous,
      userId: me.userId,
      username: me.username,
      nickname: me.nickname,
      role: me.role,
      roleName: me.roleName ?? previous.roleName,
      employeeNo: me.employeeNo,
      departmentId: me.departmentId ?? previous.departmentId,
      departmentName: me.departmentName ?? previous.departmentName,
      dataScope: me.dataScope ?? previous.dataScope,
      permissions: Array.isArray(me.permissions) ? me.permissions : (previous.permissions || []),
    }
    tokenStorage.setUser(user.value)
    return user.value
  }

  function setPreferredPanel(panel) {
    localStorage.setItem('preferredPanel', panel)
  }

  function logout() {
    token.value = ''
    user.value = null
    tokenStorage.clear()
    localStorage.removeItem('preferredPanel')
  }

  return {
    token,
    user,
    isLoggedIn,
    role,
    roleName,
    userId,
    dataScope,
    permissions,
    departmentId,
    departmentName,
    isAdmin,
    canUseAdminPanel,
    displayName,
    preferredPanel,
    hasPerm,
    hasAnyPerm,
    login,
    refreshProfile,
    setPreferredPanel,
    logout,
  }
})
