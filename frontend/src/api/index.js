import http from './http'

/**
 * 认证接口。
 *
 * 新后端不再返回「登录成功」这种自然语言字符串，而是直接给 JWT + 用户快照，
 * 前端因此省掉「再查一次角色」的往返。
 */
export const authApi = {
  login: (payload) => http.post('/auth/login', payload),
  register: (payload) => http.post('/auth/register', payload),
  me: () => http.get('/auth/me'),
}

export const userApi = {
  /** 分页查询（管理员） */
  page: (params) => http.get('/user/list', { params }),
  /** 下拉选项：只返回 id/用户名/昵称/工号 */
  options: () => http.get('/user/options'),
  detail: (id) => http.get(`/user/${id}`),
  create: (payload) => http.post('/user', payload),
  update: (id, payload) => http.put(`/user/${id}`, payload),
  remove: (id) => http.delete(`/user/${id}`),
  resetPassword: (id, newPassword) => http.put(`/user/${id}/password`, { newPassword }),
  self: () => http.get('/user/self'),
  updateSelf: (payload) => http.put('/user/self', payload),
  changePassword: (payload) => http.put('/user/self/password', payload),
}

export const teamApi = {
  page: (params) => http.get('/team/list', { params }),
  my: () => http.get('/team/my'),
  detail: (teamId) => http.get(`/team/${teamId}`),
  members: (teamId) => http.get(`/team/${teamId}/members`),
  create: (payload) => http.post('/team', payload),
  update: (teamId, payload) => http.put(`/team/${teamId}`, payload),
  remove: (teamId) => http.delete(`/team/${teamId}`),
  addMember: (teamId, payload) => http.post(`/team/${teamId}/members`, payload),
  updateMemberRole: (teamId, userId, teamRole) =>
    http.put(`/team/${teamId}/members/${userId}/role`, { teamRole }),
  removeMember: (teamId, userId) => http.delete(`/team/${teamId}/members/${userId}`),
  memberRole: (teamId, userId) => http.get(`/team/${teamId}/members/${userId}/role`),
}

export const scheduleApi = {
  page: (params) => http.get('/schedule', { params }),
  detail: (id) => http.get(`/schedule/${id}`),
  create: (payload) => http.post('/schedule', payload),
  update: (id, payload) => http.put(`/schedule/${id}`, payload),
  updateStatus: (id, status) => http.post(`/schedule/${id}/status`, { status }),
  remove: (id) => http.delete(`/schedule/${id}`),
  summary: (params) => http.get('/schedule/summary', { params }),
}

export const kanbanApi = {
  page: (params) => http.get('/kanban', { params }),
  my: () => http.get('/kanban/my'),
  detail: (kanbanId) => http.get(`/kanban/${kanbanId}/detail`),
  create: (payload) => http.post('/kanban', payload),
  update: (kanbanId, payload) => http.put(`/kanban/${kanbanId}`, payload),
  remove: (kanbanId) => http.delete(`/kanban/${kanbanId}`),
  addColumn: (kanbanId, payload) => http.post(`/kanban/${kanbanId}/columns`, payload),
  removeColumn: (columnId) => http.delete(`/kanban/columns/${columnId}`),
  addItem: (kanbanId, payload) => http.post(`/kanban/${kanbanId}/items`, payload),
  moveItem: (itemId, payload) => http.put(`/kanban/items/${itemId}/move`, payload),
  assignItem: (itemId, assigneeId) => http.put(`/kanban/items/${itemId}/assign`, null, { params: { assigneeId } }),
}

export const worklogApi = {
  page: (params) => http.get('/worklog', { params }),
  detail: (id) => http.get(`/worklog/${id}`),
  create: (payload) => http.post('/worklog', payload),
  update: (id, payload) => http.put(`/worklog/${id}`, payload),
  remove: (id) => http.delete(`/worklog/${id}`),
  statistics: (params) => http.get('/worklog/statistics', { params }),
}

export const commentApi = {
  page: (params) => http.get('/comment', { params }),
  /** 管理端全量检索：不限关联对象，支持日期区间 / 时间段 / 评论人 / 关键词 */
  adminPage: (params) => http.get('/comment/admin/page', { params }),
  tree: (params) => http.get('/comment/tree', { params }),
  add: (payload) => http.post('/comment', payload),
  update: (id, content) => http.put(`/comment/${id}`, { content }),
  remove: (id) => http.delete(`/comment/${id}`),
}

export const dashboardApi = {
  admin: (params) => http.get('/dashboard/admin', { params }),
  me: (params) => http.get('/dashboard/me', { params }),
  trend: (params) => http.get('/dashboard/trend', { params }),
}

export const attendanceApi = {
  records: (params) => http.get('/attendance/records', { params }),
  /** 单个员工的考勤详情：汇总 + 按天明细 + 打卡流水 */
  employeeDetail: (userId, params) => http.get(`/attendance/employee/${userId}`, { params }),
  /** 实时打卡流：看板每 5 秒轮询一次 */
  recent: (limit = 20) => http.get('/attendance/recent', { params: { limit }, silent: true }),
  overview: (params) => http.get('/attendance/overview', { params }),
  trend: (params) => http.get('/attendance/trend', { params }),
  my: (params) => http.get('/attendance/my', { params }),
  /** 考勤日历：区间内「每人每天」的考勤结果（管理员） */
  calendar: (params) => http.get('/attendance/calendar', { params }),
  manual: (payload) => http.post('/attendance/manual', payload),
  getRule: () => http.get('/attendance/rule'),
  updateRule: (payload) => http.put('/attendance/rule', payload),
}

export const deviceApi = {
  page: (params) => http.get('/devices', { params }),
  statistics: () => http.get('/devices/statistics'),
  create: (payload) => http.post('/devices', payload),
  update: (id, payload) => http.put(`/devices/${id}`, payload),
  rotateSecret: (id) => http.put(`/devices/${id}/secret`),
  remove: (id) => http.delete(`/devices/${id}`),
}

/** 敏感词库（管理员）。后端在增删改后会立即失效词库缓存，因此改动即时生效 */
export const sensitiveWordApi = {
  page: (params) => http.get('/sensitive-word', { params }),
  create: (payload) => http.post('/sensitive-word', payload),
  update: (wordId, payload) => http.put(`/sensitive-word/${wordId}`, payload),
  remove: (wordId) => http.delete(`/sensitive-word/${wordId}`),
  /** 文本命中检测：返回 { hit, words }，不落库 */
  check: (content) => http.get('/sensitive-word/check', { params: { content } }),
}

/** 部门下拉 */
export const departmentApi = {
  options: () => http.get('/department/options'),
}

/** 角色下拉（含数据范围） */
export const roleApi = {
  options: () => http.get('/role/options'),
}

/** 补卡申请与审批 */
export const makeupApi = {
  submit: (payload) => http.post('/makeup', payload),
  mine: (params) => http.get('/makeup/mine', { params }),
  pending: (params) => http.get('/makeup/pending', { params }),
  reviewed: (params) => http.get('/makeup/reviewed', { params }),
  review: (requestId, payload) => http.post(`/makeup/${requestId}/review`, payload),
}

/** 审计日志（只读） */
export const auditApi = {
  page: (params) => http.get('/audit', { params }),
  detail: (id) => http.get(`/audit/${id}`),
  options: () => http.get('/audit/options'),
}

/**
 * 登录页天空相关接口（公开，无需登录）。
 *
 * sky-context 不传参数时由后端按访客真实 IP 定位；
 * 传 province/city/district 时按手动选择的地点取天气。
 */
export const loginGuardApi = {
  /** 查询当前访客的失败计数 / 是否被拉黑（只返回自己的状态） */
  status: (username) => http.get('/public/login-guard', { params: { username }, silent: true }),
  /** 登记一次「点了登录但没真正认证」的尝试（表单未填等），让服务端计数与界面一致 */
  attempt: (username) => http.post('/public/login-guard/attempt', { username }, { silent: true }),
}

export const captchaApi = {
  /** 取一道新题（答案只在服务端） */
  issue: () => http.get('/public/captcha', { silent: true }),
  /** 提交滑块偏移，通过则拿到一次性 ticket */
  verify: (token, offset) => http.post('/public/captcha/verify', { token, offset }, { silent: true }),
}

export const skyApi = {
  context: (params) => http.get('/public/sky-context', { params }),
  regions: () => http.get('/public/regions'),
}
