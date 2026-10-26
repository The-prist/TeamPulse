<!--
  文件用途：通用面包屑组件（仅注释，不改逻辑）
  规则：
  - 首项根据当前域自动选择首页（/dashboard 或 /u/home）
  - 通过 route.matched 构建路径，支持 meta.parent 插入父级列表
  - 去重策略：按 path 保留首次出现
-->
<template>
  <div v-if="crumbs.length > 1" class="breadcrumb">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item
        v-for="(c,i) in crumbs"
        :key="c.path + i"
        :to="i < crumbs.length - 1 ? { path: c.path } : undefined"
        :replace="true"
      >
        <span :class="['crumb-text', { active: i === crumbs.length-1 }]">{{ c.title }}</span>
      </el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useRouter } from 'vue-router'
const route = useRoute()
const router = useRouter()

// 基础首页映射
function homeCrumb(path) {
  if (path.startsWith('/u')) {
    return { title: '首页', path: '/u/home' }
  }
  return { title: '仪表盘', path: '/dashboard' }
}

const crumbs = computed(() => {
  const list = []
  list.push(homeCrumb(route.path))

  // 使用 matched 构建（忽略布局级路由无 meta.title 的）
  route.matched.forEach(r => {
    if (!r.meta) return
    if (!r.meta.title) return
    // 跳过与首页重复的
    if (r.path === '/' || r.path === '/u' || r.path === '' ) return
    // 详情页如果有父级 meta.parent 先插入父级
    if (r.meta.parent) {
      const parentExists = list.some(c => c.path === r.meta.parent)
      if (!parentExists) {
        // 通过路由表找到父级标题
        const pr = router.getRoutes().find(rr => ('/' + rr.path.replace(/^\//,'')) === r.meta.parent || rr.path === r.meta.parent)
        const pTitle = pr?.meta?.title || '列表'
        list.push({ title: pTitle, path: r.meta.parent })
      }
    }
    list.push({ title: r.meta.title, path: '/' + r.path.replace(/^\//,'') })
  })

  // 去重（按 path 保留首次）
  const seen = new Set()
  const dedup = []
  list.forEach(c => { if (!seen.has(c.path)) { seen.add(c.path); dedup.push(c) } })
  return dedup
})
</script>

<style scoped>
.breadcrumb { padding: 12px 20px 0 20px; }
.crumb-text.active { font-weight: 600; color: #303133; }
.crumb-text { font-size: 13px; }
</style>
