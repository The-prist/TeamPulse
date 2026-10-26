import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  const target = env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080'

  return {
    plugins: [vue(), vueDevTools()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      host: true,
      proxy: {
        // 前端统一以 /api/v1 为基址，开发期由 Vite 转发到后端，浏览器侧不存在跨域
        '/api': { target, changeOrigin: true },
        // 设备 HTTP 兜底接入通道
        '/iot': { target, changeOrigin: true },
      },
    },
    build: {
      outDir: 'dist',
      chunkSizeWarningLimit: 1600,
      rollupOptions: {
        output: {
          // 体积大且少变动的依赖单独成块，利用浏览器长缓存，避免每次发版全量失效
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia'],
            element: ['element-plus', '@element-plus/icons-vue'],
            echarts: ['echarts'],
          },
        },
      },
    },
  }
})
