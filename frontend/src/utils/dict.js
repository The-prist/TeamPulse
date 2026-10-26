/**
 * 枚举值 → 中文显示。
 *
 * 为什么单独抽一个文件：这些值（todo / doing / done、low / medium / high、
 * admin / member …）散落在十几个页面里被直接渲染，导致界面上出现英文。
 * 集中在一处后，新增一个状态只需要改这里一次，不用满项目搜。
 *
 * 约定：**只做显示映射，不改变提交给后端的值**。
 */

// ---------------- 字典 ----------------
export const SCHEDULE_STATUS = {
  todo: '待办',
  doing: '进行中',
  done: '已完成',
  canceled: '已取消',
}

export const PRIORITY = {
  low: '低',
  medium: '中',
  high: '高',
}

export const USER_ROLE = {
  admin: '管理员',
  manager: '部门负责人',
  member: '普通成员',
}

export const USER_STATUS = {
  active: '启用',
  inactive: '停用',
}

export const TEAM_ROLE = {
  leader: '负责人',
  member: '成员',
}

/** 评论的关联对象类型 */
export const RELATED_TYPE = {
  schedule: '日程',
  worklog: '工作日志',
}

/** 打卡来源 */
export const PUNCH_SOURCE = {
  MQTT: 'MQTT 设备',
  HTTP: 'HTTP 设备',
  MANUAL: '手工补卡',
}

// ---------------- 文案 ----------------
export function scheduleStatusText(value) {
  return SCHEDULE_STATUS[value] || value || '-'
}

export function priorityText(value) {
  return PRIORITY[value] || value || '-'
}

export function userRoleText(value) {
  return USER_ROLE[value] || value || '-'
}

export function userStatusText(value) {
  return USER_STATUS[value] || value || '-'
}

export function teamRoleText(value) {
  return TEAM_ROLE[value] || value || '-'
}

export function relatedTypeText(value) {
  return RELATED_TYPE[value] || value || '-'
}

export function punchSourceText(value) {
  return PUNCH_SOURCE[value] || value || '-'
}

// ---------------- 标签颜色（Element Plus 的 type 取值） ----------------
export function scheduleStatusTagType(value) {
  return { todo: 'info', doing: 'warning', done: 'success', canceled: 'danger' }[value] || 'info'
}

export function priorityTagType(value) {
  return { low: 'info', medium: 'primary', high: 'danger' }[value] || 'info'
}

export function userRoleTagType(value) {
  return { admin: 'danger', manager: 'warning', member: 'info' }[value] || 'info'
}

export function userStatusTagType(value) {
  return value === 'active' ? 'success' : 'warning'
}

export function teamRoleTagType(value) {
  return value === 'leader' ? 'warning' : 'info'
}
