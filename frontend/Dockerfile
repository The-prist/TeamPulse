# =====================================================================
# 前端镜像：Node 构建 → Nginx 托管静态资源 + 反向代理后端
#
# 用 pnpm 安装依赖：仓库里只有 pnpm-lock.yaml，
# 之前用 npm ci/npm install 会绕过锁文件，导致不同机器可能装到不同版本（构建不可复现）。
# =====================================================================
FROM node:20-alpine AS builder

WORKDIR /build

# 用 corepack 固定 pnpm 主版本，与 CI（pnpm/action-setup@v4 version: 9）保持一致
RUN corepack enable && corepack prepare pnpm@9 --activate

# 先拷锁文件与清单，利用分层缓存；--frozen-lockfile 保证与锁文件完全一致
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .

# 生产构建时 API 基址走相对路径，由 Nginx 同源反代，天然规避跨域
ARG VITE_API_BASE=/api/v1
ENV VITE_API_BASE=${VITE_API_BASE}

RUN pnpm build

# =====================================================================
FROM nginx:1.27-alpine

RUN rm -rf /usr/share/nginx/html/*
COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=20s --timeout=5s --retries=5 \
    CMD wget -q --spider http://127.0.0.1/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
