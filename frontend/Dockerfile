# =====================================================================
# 前端镜像：Node 构建 → Nginx 托管静态资源 + 反向代理后端
# =====================================================================
FROM node:20-alpine AS builder

WORKDIR /build

# 先拷依赖清单，利用分层缓存
COPY package*.json ./
# 有 lock 文件用 npm ci（可复现），没有则退回 npm install
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi

COPY . .

# 生产构建时 API 基址走相对路径，由 Nginx 同源反代，天然规避跨域
ARG VITE_API_BASE=/api/v1
ENV VITE_API_BASE=${VITE_API_BASE}

RUN npm run build

# =====================================================================
FROM nginx:1.27-alpine

RUN rm -rf /usr/share/nginx/html/*
COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=20s --timeout=5s --retries=5 \
    CMD wget -q --spider http://127.0.0.1/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
