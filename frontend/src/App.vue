<!--
  应用根组件。
  除了渲染路由，这里还挂一个「网络异常提示条」：接口失败时错误提示只闪 3 秒，
  用户点完才发现表格是空的，还会误以为「真的没有数据」。
  提示条把最近一次失败留在页面上，并提供重试（重放那次请求）。
-->
<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { clearNetError, netState, retryNetError } from '@/utils/netErrors'
import ThemeToggle from '@/components/ThemeToggle.vue'

const route = useRoute()
// 登录页的天空由北京时间驱动，不再提供手动昼夜切换
const showToggle = computed(() => route.path !== '/login')
</script>

<template>
  <router-view />

  <!-- 左下角弯月：切换亮/暗主题（登录页不显示，那里的天空由时间决定） -->
  <ThemeToggle v-if="showToggle" />

  <transition name="net-fade">
    <div v-if="netState.message" class="net-banner" role="alert">
      <span class="net-dot"></span>
      <span class="net-text">{{ netState.message }}</span>
      <el-button v-if="netState.retry" link type="primary" size="small" class="net-btn" @click="retryNetError">
        重试
      </el-button>
      <span v-if="netState.traceId" class="net-trace">编号 {{ netState.traceId }}</span>
      <el-button link size="small" class="net-btn" @click="clearNetError">关闭</el-button>
    </div>
  </transition>
</template>

<style scoped>
.net-banner {
  position: fixed;
  left: 50%;
  bottom: 22px;
  transform: translateX(-50%);
  z-index: 3000;
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: min(760px, calc(100vw - 40px));
  padding: 10px 14px;
  border: 1px solid #fbc4c4;
  border-left: 4px solid #e5484d;
  border-radius: 10px;
  background: #fff6f6;
  box-shadow: 0 10px 26px rgba(15, 43, 70, 0.14);
  font-size: 13px;
  color: #8a2b2f;
}

.net-dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e5484d;
}

.net-text {
  flex: 1;
  line-height: 1.5;
  word-break: break-word;
}

.net-btn {
  flex: none;
}

.net-trace {
  flex: none;
  font-family: Consolas, Menlo, monospace;
  font-size: 11.5px;
  color: #a4464a;
  opacity: 0.8;
}

.net-fade-enter-active,
.net-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.net-fade-enter-from,
.net-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, 8px);
}
</style>
