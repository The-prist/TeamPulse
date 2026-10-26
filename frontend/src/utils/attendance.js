/**
 * 考勤相关的展示映射与格式化。
 *
 * 集中放置的原因：状态文案、颜色标签在「看板 / 记录 / 个人考勤」三个页面都要用，
 * 分散写会导致三处文案不一致（旧项目就吃了这个亏）。
 */

/** 打卡记录状态 */
export const PUNCH_STATUS_MAP = {
  NORMAL: { text: '正常', type: 'success' },
  LATE: { text: '迟到', type: 'warning' },
  EARLY_LEAVE: { text: '早退', type: 'warning' },
  LATE_AND_EARLY: { text: '迟到且早退', type: 'danger' },
  INVALID: { text: '无效', type: 'info' },
  ABSENT: { text: '缺勤', type: 'danger' },
}

export function punchStatusText(status) {
  return PUNCH_STATUS_MAP[status]?.text || status || '-'
}

export function punchStatusType(status) {
  return PUNCH_STATUS_MAP[status]?.type || 'info'
}

/** 打卡类型 */
export function punchTypeText(type) {
  if (type === 'CHECK_IN') return '上班'
  if (type === 'CHECK_OUT') return '下班'
  return type || '-'
}

/** 数据来源 */
export function sourceText(source) {
  const map = { MQTT: 'MQTT 设备', HTTP: 'HTTP 设备', MANUAL: '手工补卡' }
  return map[source] || source || '-'
}

/** 设备状态 */
export function deviceStatusType(status) {
  if (status === 'ONLINE') return 'success'
  if (status === 'DISABLED') return 'info'
  return 'warning'
}

export function deviceStatusText(status) {
  const map = { ONLINE: '在线', OFFLINE: '离线', DISABLED: '已禁用' }
  return map[status] || status || '-'
}

/** 把 'yyyy-MM-ddTHH:mm:ss' 或 'yyyy-MM-dd HH:mm:ss' 统一成展示格式 */
export function formatDateTime(value) {
  if (!value) return '-'
  const text = String(value).replace('T', ' ')
  return text.length > 19 ? text.slice(0, 19) : text
}

export function formatDate(value) {
  if (!value) return '-'
  return String(value).slice(0, 10)
}

/** 分钟数转「x 小时 y 分」 */
export function formatMinutes(minutes) {
  const n = Number(minutes || 0)
  if (n <= 0) return '0 分'
  const h = Math.floor(n / 60)
  const m = n % 60
  return h > 0 ? `${h} 小时 ${m} 分` : `${m} 分`
}

/** 生成本地时间 yyyy-MM-dd HH:mm:ss（补卡表单默认值） */
export function nowText() {
  const d = new Date()
  const pad = (v) => String(v).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
