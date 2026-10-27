import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './styles/index.css'
import './styles/theme.css'
import './styles/dark-fixes.css'
import './styles/tabs.css'
import './styles/light.css'
import { initTheme } from './utils/theme'

// 必须在 mount 之前确定主题，否则暗色用户会看到一帧白屏
initTheme()

const app = createApp(App)

// 图标全局注册，模板里可直接写 <el-icon><Calendar /></el-icon>
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

/**
 * 全局兜底：这两类错误原本只会进控制台，用户看到的是空白或"点了没反应"。
 *  - errorHandler：组件渲染、生命周期、事件回调里抛出的异常
 *  - unhandledrejection：某处忘了 await / catch 的 Promise 拒绝
 * 业务错误（BizError）已由 http 拦截器提示过，这里不再重复弹窗。
 */
app.config.errorHandler = (err, instance, info) => {
  console.error('[vue errorHandler]', info, err)
  ElMessage.error('页面出现异常，请刷新后重试')
}

window.addEventListener('unhandledrejection', (event) => {
  const reason = event.reason
  if (reason && reason.name === 'BizError') {
    // 接口错误已由拦截器统一提示，避免二次弹窗
    event.preventDefault()
    return
  }
  console.error('[unhandled rejection]', reason)
  ElMessage.error('操作未完成，请重试')
  event.preventDefault()
})

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
