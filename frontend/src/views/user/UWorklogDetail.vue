<template>
  <el-card>
    <template #header>日志详情</template>
    <div v-if="loading">加载中...</div>
    <div v-else-if="!detail">未找到</div>
    <div v-else>
      <h2 style="margin:0 0 8px">{{ detail.title }}</h2>
      <div style="color:#666;margin-bottom:8px">
        日期：{{ detail.logDate }} · 创建时间：{{ detail.createdAt }}
      </div>
      <div style="white-space:pre-wrap;line-height:1.6">{{ detail.content }}</div>
      <div style="margin-top:16px">
        <CommentsPanel mode="worklog" :related-id="detail.worklogId" />
      </div>
    </div>
  </el-card>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { worklogApi } from '@/api'
import CommentsPanel from '@/components/CommentsPanel.vue'
const route = useRoute()
const loading = ref(false)
const detail = ref(null)
async function load(){
  loading.value = true
  try{
    const { id } = route.params
    // GET /worklog/{id} 直接返回 WorklogVO
    detail.value = await worklogApi.detail(id)
  } finally { loading.value = false }
}
onMounted(load)
</script>
