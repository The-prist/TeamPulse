import http from '@/api/http'

/**
 * 兼容层。
 *
 * 旧页面的写法是 `api.get('/user/list')` 这种「相对模块路径」，
 * 新的 axios 实例 baseURL 已经是 `/api/v1`，因此这些调用天然指向新接口，
 * 不需要逐个页面改写传输层。
 *
 * 保留 postForm 是为了兼容仍以表单方式提交的页面；新页面请直接用 `@/api` 里的模块方法。
 */
export const api = {
  get: (path, params) => http.get(path, { params }),
  post: (path, body) => http.post(path, body),
  put: (path, body) => http.put(path, body),
  del: (path, params) => http.delete(path, { params }),
  postForm: async (path, form) => {
    const body = new URLSearchParams()
    Object.entries(form || {}).forEach(([key, value]) => body.append(key, value == null ? '' : String(value)))
    return http.post(path, body, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    })
  },
}

export default api
