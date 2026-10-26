import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import { tokenStorage } from '@/api/http'

/**
 * 登录态。
 *
 * 用 Pinia 而不是直接读 localStorage 的原因：
 *   - 响应式：顶栏用户名、菜单权限能随登录/退出即时变化
 *   - 单一入口：登录、退出、刷新资料都走同一处，避免散落在各页面
 *
 * 注意：token 仍然写入 localStorage，保证刷新页面后不丢登录态；
 * Pinia 只是运行期的视图状态。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(tokenStorage.getToken())
  const user = ref(tokenStorage.getUser())

  const isLoggedIn = computed(() => Boolean(token.value))
  const role = computed(() => (user.value?.role || '').toLowerCase())
  const isAdmin = computed(() => role.value === 'admin')
  const displayName = computed(() => user.value?.nickname || user.value?.username || '')
  /** 管理员可在「管理后台」和「用户工作台」两套界面间切换 */
  const preferredPanel = computed(() => localStorage.getItem('preferredPanel') || '')

  async function login(username, password) {
    const data = await authApi.login({ username, password })
    token.value = data.token
    tokenStorage.setToken(data.token)

    user.value = {
      userId: data.userId,
      username: data.username,
      nickname: data.nickname,
      role: data.role,
      employeeNo: data.employeeNo,
    }
    tokenStorage.setUser(user.value)
    return data
  }

  /** 刷新页面时用 token 换回最新用户信息，顺便验证 token 是否仍有效 */
  async function refreshProfile() {
    if (!token.value) return null
    const me = await authApi.me()
    user.value = {
      userId: me.userId,
      username: me.username,
      nickname: me.nickname,
      role: me.role,
      employeeNo: me.employeeNo,
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
    isAdmin,
    displayName,
    preferredPanel,
    login,
    refreshProfile,
    setPreferredPanel,
    logout,
  }
})
