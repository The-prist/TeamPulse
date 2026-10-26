# TeamPulse 前端（teampulse-frontend）

TeamPulse 团队协作与物联网考勤平台的浏览器端，Vue 3 + Vite + Element Plus + Pinia。

后端在 [../backend](../backend)，本地如何一起启动见 [../docs/如何运行页面原型.md](../docs/如何运行页面原型.md)。

## 环境要求

- Node.js `^20.19.0 || >=22.12.0`（见 `package.json` 的 `engines`）

## 安装依赖

仓库中同时存在 `pnpm-lock.yaml`，两种方式任选其一，但**不要混用**：

```sh
npm install     # README、scripts/run-local.* 与 Dockerfile 默认走这条
# 或者
pnpm install
```

## 开发

```sh
npm run dev     # http://localhost:5173
```

开发期以 `/api/v1` 为基址，由 Vite 代理转发到后端（见 `vite.config.js`），浏览器侧不产生跨域。

| 环境变量 | 作用 | 默认值 |
| --- | --- | --- |
| `VITE_API_BASE` | 前端请求的 API 基址 | `/api/v1` |
| `VITE_PROXY_TARGET` | 开发代理指向的后端地址 | `http://127.0.0.1:8080` |

改后端地址时在 `frontend/.env.development.local` 里覆盖即可（`.env.*.local` 不入库）。

## 构建与预览

```sh
npm run build   # 产物在 dist/
npm run preview # 本地预览构建产物（4173 端口）
```

## 目录约定

```
src/
├── api/         axios 实例与各模块接口（统一 { code, message, data } 解包）
├── stores/      Pinia 状态（登录态）
├── router/      路由与访问控制守卫
├── layouts/     管理后台 / 用户工作台两套骨架
├── views/       页面（admin 与 user 两个域）
├── components/  复用组件
├── composables/ 组合式函数（useTable 等）
└── utils/       工具（敏感词过滤等）
```