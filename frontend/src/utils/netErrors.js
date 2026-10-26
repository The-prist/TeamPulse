import { reactive } from 'vue'

/**
 * 全局网络异常状态。
 *
 * 为什么需要它：接口层的错误提示是一条 3 秒就消失的 toast，用户点完才发现页面是空的，
 * 而表格的「暂无数据」又会让人以为真的没有数据。这里把「最近一次失败」留在页面上，
 * 并给出一个「重试」动作（重放失败的那次请求），让失败可见、可恢复。
 *
 * 不依赖 Pinia：http.js 在应用初始化阶段就会用到它，用普通的 reactive 对象最稳。
 */
export const netState = reactive({
  /** 最近一次失败提示文案，null 表示当前无异常 */
  message: null,
  /** 重放失败请求的函数 */
  retry: null,
  /** 失败计数，用于让重复的相同错误也能触发重新计时 */
  count: 0,
  /** 后端回传的 X-Trace-Id，用户截图反馈时可直接对应到服务端日志 */
  traceId: null,
})

/** 自动消失时间（毫秒）；重试成功或手动关闭会立即清除 */
const AUTO_HIDE_MS = 12000
let hideTimer = null

export function reportNetError(message, retry, traceId) {
  netState.message = message || '请求失败'
  netState.traceId = traceId || null
  netState.retry = typeof retry === 'function' ? retry : null
  netState.count += 1
  if (hideTimer) clearTimeout(hideTimer)
  hideTimer = setTimeout(() => {
    // 只清提示，不打断用户当前操作
    netState.message = null
    netState.retry = null
    netState.traceId = null
  }, AUTO_HIDE_MS)
}

export function clearNetError() {
  if (hideTimer) clearTimeout(hideTimer)
  hideTimer = null
  netState.message = null
  netState.retry = null
  netState.traceId = null
}

/** 重试：先清提示，再重放请求；失败时拦截器会再次上报 */
export async function retryNetError() {
  const fn = netState.retry
  clearNetError()
  if (fn) {
    await fn()
  }
}
