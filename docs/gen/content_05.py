"""第 5 章：前端工程实践。"""


def build(d):
    d.h1("第 5 章  前端工程实践")

    d.h2("5.1 目录与职责")
    d.code(
        "frontend/src\n"
        "├── api/\n"
        "│   ├── http.js        axios 实例 + 拦截器（注入 token、剥壳、401 处理）\n"
        "│   └── index.js       按模块聚合的 API（authApi / userApi / attendanceApi ...）\n"
        "├── stores/\n"
        "│   └── auth.js        Pinia：登录态、角色、面板偏好\n"
        "├── router/\n"
        "│   └── index.js       路由表 + 全局守卫（双面板隔离）\n"
        "├── layouts/           AdminLayout / UserLayout\n"
        "├── views/             页面（attendance 为新增模块）\n"
        "├── utils/             api 兼容层、考勤展示映射\n"
        "└── styles/index.css   全局栅格与容器规范",
    )

    d.h2("5.2 HTTP 层：为什么自己封一层 axios")
    d.p("原项目每个页面直接用 fetch，导致三件事反复出现：手动拼 BASE、手动加身份头、每处自己判错误。"
        "现在收敛到 `api/http.js` 一个地方：")
    d.code(
        "const http = axios.create({\n"
        "  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',\n"
        "  timeout: 20000,\n"
        "})\n"
        "\n"
        "// ① 请求拦截：自动带 token\n"
        "http.interceptors.request.use((config) => {\n"
        "  const token = tokenStorage.getToken()\n"
        "  if (token) config.headers.Authorization = `Bearer ${token}`\n"
        "  return config\n"
        "})\n"
        "\n"
        "// ② 响应拦截：剥壳 + 统一错误提示 + 401 跳登录\n"
        "http.interceptors.response.use(\n"
        "  (response) => {\n"
        "    const body = response.data\n"
        "    if (!body || typeof body !== 'object' || !('code' in body)) return body  // 非标准包装原样返回\n"
        "    if (body.code === 0) return body.data                                      // 页面直接拿业务数据\n"
        "    ...\n"
        "  },\n"
        "  (error) => { if (error.response?.status === 401) redirectToLogin() ... }\n"
        ")",
        caption="frontend/src/api/http.js",
    )
    d.callout(
        "一个关键设计：baseURL 统一为 /api/v1",
        "旧页面里写的是 `api.get('/user/list')` 这种相对路径。把 baseURL 设成 /api/v1 之后，"
        "这些调用天然指向新接口，不需要把 20 多个页面全部改写——这是我在设计接口前缀时刻意对齐旧路径的结果，"
        "大幅降低了迁移风险。",
    )
    d.p("`silent` 选项用于轮询类接口：失败不弹 toast，避免网络抖动时刷屏。")
    d.code(
        "recent: (limit = 20) => http.get('/attendance/recent', { params: { limit }, silent: true })",
    )

    d.h2("5.3 状态管理：Pinia 存什么")
    d.table(
        ["状态", "存储位置", "为什么"],
        [
            ["token", "localStorage + Pinia", "刷新页面不丢登录态；Pinia 提供响应式视图"],
            ["user（id/name/role/工号）", "localStorage + Pinia", "顶栏、菜单、权限判断都要读，避免每次请求 /auth/me"],
            ["preferredPanel", "localStorage", "管理员在 Admin/User 两套界面间的偏好，刷新后要保留"],
            ["页面级数据（表格、表单）", "组件内 ref", "不需要跨页面共享，放全局反而增加耦合"],
        ],
        widths=[4.2, 4.6, 7.6],
    )
    d.p("路由守卫里**直接读 tokenStorage 而不是 useAuthStore()**，原因是守卫在组件树之外执行，"
        "要避免「Pinia 尚未激活」的初始化顺序问题。这是一个很多项目会踩的坑。")

    d.h2("5.4 路由守卫与双面板")
    d.p("同一个系统里有两套界面：`/`（管理后台）与 `/u`（用户工作台）。规则：")
    d.bullets(
        [
            "未登录：除 `/login` 外一律重定向到登录页，并带上 `redirect` 参数以便登录后回跳",
            "管理员：两个域都能进；首次登录必须先选面板（写 `preferredPanel`），之后记住选择",
            "普通成员：只能进 `/u`，访问 `/` 下任何路径都被踢回 `/u/home`",
        ]
    )
    d.code(
        "router.beforeEach((to) => {\n"
        "  if (to.meta?.public) return true\n"
        "  const token = tokenStorage.getToken()\n"
        "  if (!token) return to.path === '/login' ? true : { path: '/login', query: { redirect: to.fullPath } }\n"
        "  const role = String(tokenStorage.getUser()?.role || '').toLowerCase()\n"
        "  if (role === 'admin') { ...允许两域，无偏好则去 /entry... }\n"
        "  if (!isUserDomain(to.path)) return { path: '/u/home' }   // 非管理员禁止进入后台\n"
        "  return true\n"
        "})",
    )
    d.p("**前端守卫只是体验优化，真正的权限在后端**：即使有人绕过前端进入页面，接口仍然会 403。"
        "这一点如果面试官追问，要能明确区分「前端拦截」与「服务端鉴权」的职责。")

    d.h2("5.5 考勤看板的实时刷新")
    d.p("三个要点：定时器必须清理、失败不打扰、开关可关闭。")
    d.code(
        "function startPolling() { stopPolling(); timer = window.setInterval(loadRecent, 5000) }\n"
        "function stopPolling()  { if (timer) { window.clearInterval(timer); timer = null } }\n"
        "\n"
        "onMounted(async () => {\n"
        "  await Promise.all([loadOverview(), loadRecent(), loadRule(), loadTrend()])\n"
        "  startPolling()\n"
        "  window.addEventListener('resize', handleResize)   // ECharts 需要手动 resize\n"
        "})\n"
        "onBeforeUnmount(() => {\n"
        "  stopPolling()\n"
        "  window.removeEventListener('resize', handleResize)\n"
        "  chart?.dispose()                                  // 不 dispose 会泄漏 canvas 与监听\n"
        "})",
        caption="frontend/src/views/attendance/AttendanceDashboard.vue",
    )

    d.h2("5.6 构建优化")
    d.code(
        "build: {\n"
        "  rollupOptions: {\n"
        "    output: {\n"
        "      manualChunks: {\n"
        "        vue:     ['vue', 'vue-router', 'pinia'],\n"
        "        element: ['element-plus', '@element-plus/icons-vue'],\n"
        "        echarts: ['echarts'],\n"
        "      },\n"
        "    },\n"
        "  },\n"
        "}",
        caption="按依赖变动频率分包：业务代码常改，三方库很少变，分开后发版不会让用户重下 1MB 的库",
    )
    d.p("配合 Nginx 对 `/assets/` 设置一年强缓存（带内容哈希的文件名天然可长缓存），"
        "而 `index.html` 设置为 no-cache，保证发版后立刻拿到新的资源引用。")

    d.h2("5.7 前端改动的自查清单")
    d.bullets(
        [
            "所有接口调用都走 `@/api` 或兼容层，没有裸 fetch",
            "组件卸载时清理定时器、事件监听、ECharts 实例",
            "表单有校验规则，提交时 loading 防重复点击",
            "列表页有 loading 态与空数据提示",
            "错误提示不重复（统一由拦截器处理时，页面 catch 里只做兜底）",
            "路由守卫与后端权限一致，不假设「前端拦住了就安全」",
        ]
    )
