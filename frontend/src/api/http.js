import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一 HTTP 层。
 *
 * 约定：后端所有接口返回 { code, message, data, timestamp }。
 * 本层在响应拦截器里完成三件事：
 *   1. 自动注入 Authorization: Bearer <token>
 *   2. code === 0 时把 data 直接抛给调用方（页面里拿到的就是业务数据，不用层层 .data.data）
 *   3. 401 时清理登录态并跳登录页；其他错误统一提示，避免每个页面重复写 try/catch 提示
 */

const TOKEN_KEY = 'tp_token'
const USER_KEY = 'tp_user'

export const tokenStorage = {
  getToken: () => localStorage.getItem(TOKEN_KEY) || '',
  setToken: (token) => localStorage.setItem(TOKEN_KEY, token),
  getUser: () => {
    try {
      return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
    } catch {
      return null
    }
  },
  setUser: (user) => localStorage.setItem(USER_KEY, JSON.stringify(user || null)),
  clear: () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  },
}

/** 业务异常：页面可据此判断 code 做差异化处理 */
export class BizError extends Error {
  constructor(message, code) {
    super(message)
    this.name = 'BizError'
    this.code = code
  }
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = tokenStorage.getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function redirectToLogin() {
  tokenStorage.clear()
  if (!window.location.pathname.startsWith('/login')) {
    // 用整页跳转而不是 router.push：确保内存中的旧状态与定时器（如轮询）一并销毁
    window.location.assign('/login')
  }
}

http.interceptors.response.use(
  (response) => {
    const body = response.data
    // 非统一包装（例如未来接入的文件流）原样返回
    if (!body || typeof body !== 'object' || !('code' in body)) {
      return body
    }
    if (body.code === 0) {
      return body.data
    }
    const message = body.message || '请求失败'
    if (!response.config?.silent) {
      ElMessage.error(message)
    }
    return Promise.reject(new BizError(message, body.code))
  },
  (error) => {
    const status = error?.response?.status
    const payload = error?.response?.data

    // 后端没启动 / 挂了时，开发服务器的代理会返回 5xx，且响应体不是统一 JSON 结构。
    // 这时给一句能直接照做的提示，比 "Request failed with status code 500" 有用得多。
    const looksLikeProxyFailure =
      !error.response ||
      (status >= 500 && (payload == null || typeof payload !== 'object' || payload.code == null))

    if (looksLikeProxyFailure) {
      const hint = '无法连接后端服务（默认 127.0.0.1:8080）。请确认后端已启动，可在项目根目录执行 .\\scripts\\run-local.ps1'
      if (!error.config?.silent) {
        ElMessage.error(hint)
      }
      return Promise.reject(new BizError(hint, status || 0))
    }

    const message = (payload && (payload.message || payload.error)) || error.message || '网络异常'

    if (status === 401) {
      if (!error.config?.silent) {
        ElMessage.warning('登录已过期，请重新登录')
      }
      redirectToLogin()
      return Promise.reject(new BizError('未登录或登录已过期', 40100))
    }
    if (status === 403) {
      if (!error.config?.silent) {
        ElMessage.error('没有该操作的权限')
      }
      return Promise.reject(new BizError('没有该操作的权限', 40300))
    }
    if (!error.config?.silent) {
      ElMessage.error(message)
    }
    return Promise.reject(new BizError(message, status || 0))
  },
)

export default http
