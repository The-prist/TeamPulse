<!--
  日志详情页：
  - 根据路由参数 id 加载工作日志详情
  - 展示标题、日期、创建者、内容、关联对象
  - 使用 CommentsPanel 渲染与该日志相关的评论
  - 为了显示创建者昵称，这里会按需加载用户列表并建立 userId->name 映射
-->
<template>
  <el-card>
    <template #header>
      <div class="header">
        <div class="header-left">
          <el-button type="text" @click="goBack" class="back-btn">
            <el-icon><ArrowLeft /></el-icon> 返回
          </el-button>
          <span class="header-title">日志详情</span>
        </div>
      </div>
    </template>
    <div v-if="loading">加载中...</div>
    <div v-else-if="!detail">未找到</div>
    <div v-else>
      <h2 style="margin: 0 0 8px;">{{ detail.title }}</h2>
      <div style="color:#666;margin-bottom:8px">
        日期：{{ detail.logDate }} · 创建者：{{ getUserName(detail.creatorId) }} · 创建时间：{{ detail.createdAt }}
      </div>
      <div style="white-space:pre-wrap;line-height:1.6">{{ filterSensitiveWords(detail?.content || '') }}</div>
      <div style="margin-top:12px;color:#666">
        关联日程：{{ detail.relatedScheduleId || '无' }} · 所属团队：{{ detail.relatedTeamId || '无' }}
      </div>
      <div style="margin-top:16px">
        <CommentsPanel mode="worklog" :related-id="detail.worklogId" />
      </div>
    </div>
  </el-card>
</template>

<script setup>
// 组合式 API 脚本区域
import { onMounted, ref } from 'vue'
import { filterSensitiveWords } from '@/utils/sensitive'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { userApi, worklogApi } from '@/api'
import CommentsPanel from '@/components/CommentsPanel.vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const loading = ref(false)     // 页面加载状态
const detail = ref(null)       // 日志详情数据
const userMap = ref(new Map()) // userId -> 用户名 映射缓存

function goBack() {
  router.back()
}

// 获取用户信息（按需，避免重复请求）：
// - 从 /user/list（分页）批量获取后在前端筛选需要的用户
// - 若后端提供单查接口，可在兜底分支中开启
async function loadUserInfo(userIds) {
  if (!userIds || userIds.length === 0) return
  const needFetchIds = userIds.filter(id => !userMap.value.has(id))
  if (needFetchIds.length === 0) return

  try {
    const userPage = await userApi.page({ page: 1, size: 200 })
    const allUsers = userPage?.records || []
    const target = allUsers.filter(u => needFetchIds.includes(u.userId))
    target.forEach(u => {
      userMap.value.set(u.userId, u.username || u.nickname || u.name || `用户${u.userId}`)
    })
    // 仍未命中的 ID 走单个详情兜底（GET /user/{id}，管理员可用）
    const stillMissing = needFetchIds.filter(id => !userMap.value.has(id))
    for (const mid of stillMissing) {
      try {
        const single = await userApi.detail(mid)
        if (single?.userId) userMap.value.set(single.userId, single.nickname || single.username || `用户${single.userId}`)
      } catch { /* 忽略单个失败 */ }
    }
  } catch (err) {
    console.warn('批量获取用户信息失败，使用占位符:', err)
    needFetchIds.forEach(id => {
      if (!userMap.value.has(id)) userMap.value.set(id, `用户${id}`)
    })
  }
}

// 获取用户名（若未命中缓存则回退为“用户{ID}”）
function getUserName(userId) {
  if (!userId) return '未知用户'
  return userMap.value.get(userId) || `用户${userId}`
}

// 主加载流程：
// 1) 读取路由参数 id
// 2) 请求 GET /worklog/{id}（WorklogVO，已带 creatorName）
// 3) 若包含 creatorId，则加载其用户信息用于展示
async function load() {
  loading.value = true
  try {
    const { id } = route.params
    detail.value = await worklogApi.detail(id)
    
  // 获取创建者用户信息（片段选中位置）
    if (detail.value?.creatorId) {
      await loadUserInfo([detail.value.creatorId])
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  padding: 0;
  font-size: 14px;
}

.header-title {
  font-weight: 500;
  font-size: 16px;
}
</style>
