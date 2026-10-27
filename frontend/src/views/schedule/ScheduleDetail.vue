<template>
  <div class="schedule-detail-page">
    <el-card class="detail-card" shadow="never">
      <template #header>
        <div class="header">
          <div class="header-left">
            <el-button type="text" @click="$router.back()" class="back-btn">
              <el-icon><ArrowLeft /></el-icon> 返回
            </el-button>
            <span class="header-title">日程详情</span>
          </div>
        </div>
      </template>
      <div v-if="loading">加载中...</div>
      <div v-else-if="error" class="err">{{ error }}</div>
      <div v-else>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="标题">{{ d.title }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ scheduleStatusText(d.status) }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ priorityText(d.priority) }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ format(d.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ format(d.endTime) }}</el-descriptions-item>
          <el-descriptions-item label="地点">{{ d.location }}</el-descriptions-item>
          <el-descriptions-item label="团队">{{ d.teamId ?? '个人' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ creatorName || d.creatorId }}</el-descriptions-item>
        </el-descriptions>
        <div class="actions">
          <span class="actions-label">状态：</span>
          <div class="status-buttons">
            <el-button size="small" plain :loading="updatingStatus" @click="setStatus('todo')" :type="d.status==='todo' ? 'primary' : 'info'">待办</el-button>
            <el-button size="small" plain :loading="updatingStatus" @click="setStatus('doing')" :type="d.status==='doing' ? 'primary' : 'info'">进行中</el-button>
            <el-button size="small" plain :loading="updatingStatus" @click="setStatus('done')" :type="d.status==='done' ? 'success' : 'info'">已完成</el-button>
            <el-button size="small" plain :loading="updatingStatus" @click="setStatus('canceled')" :type="d.status==='canceled' ? 'warning' : 'info'">作废</el-button>
            <el-popconfirm v-if="canDelete" title="确认删除该日程吗？" @confirm="deleteSchedule">
              <template #reference>
                <el-button size="small" type="danger" plain :loading="deleting">删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>
      </div>
    </el-card>
    <!-- 日程相关评论 -->
    <el-card class="comments-card" shadow="never">
      <template #header>
        <span>相关评论</span>
      </template>
      <CommentsPanel mode="schedule" :related-id="route.params.id" />
    </el-card>
  </div>
</template>

<script setup>
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore()
import { reactive, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { scheduleApi } from '@/api'
import { scheduleStatusText, priorityText } from '@/utils/dict'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import CommentsPanel from '@/components/CommentsPanel.vue'

const route = useRoute()
const loading = ref(false)
const error = ref('')
const d = reactive({})
const creatorName = ref('加载中...')
const updatingStatus = ref(false)
const deleting = ref(false)

// 评论相关代码已移至CommentsPanel组件

function format(val) {
  if (!val) return ''
  const s = String(val).replace('T', ' ').replace(/Z$/, '')
  return s
}

// 加载日程详情
async function loadScheduleDetail() {
  const id = route.params.id
  loading.value = true
  try {
    // 详情直接返回 ScheduleVO（creatorName 已由后端补齐）
    const data = await scheduleApi.detail(id)
    Object.assign(d, data || {})
    creatorName.value = data?.creatorName || '未知用户'
  } catch (err) {
    console.error('获取日程详情失败:', err)
    error.value = '获取日程详情失败'
  } finally {
    loading.value = false
  }
}

// 设置状态
async function setStatus(status) {
  if (!d.scheduleId) return
  if (d.status === status) return
  updatingStatus.value = true
  const id = d.scheduleId
  try {
    // POST /schedule/{id}/status
    const res = await scheduleApi.updateStatus(id, status)
    d.status = res?.status || status
    ElMessage.success('状态已更新')
  } catch (err) {
    console.error('更新状态失败:', err)
    ElMessage.error('更新状态失败: ' + String(err))
  } finally {
    updatingStatus.value = false
  }
}

// 删除日程
async function deleteSchedule() {
  if (!d.scheduleId) return
  deleting.value = true
  const id = d.scheduleId
  try {
    await scheduleApi.remove(id)
    ElMessage.success('删除成功')
    history.length > 1 ? window.history.back() : window.location.assign('/schedule')
  } catch (err) {
    console.error('删除日程失败:', err)
    ElMessage.error('删除失败: ' + String(err))
  } finally {
    deleting.value = false
  }
}

// 评论功能已移至CommentsPanel组件

onMounted(async () => {
  await loadScheduleDetail()
})
/** 与后端一致：日程创建人 或 拥有 schedule:manage 才能删除 */
const canDelete = computed(() => {
  // 注意：本组件的数据放在 reactive 的 d 里（不是 detail/schedule）。
  // 之前这里误写成 detail.value / schedule.value，渲染时抛 ReferenceError，
  // 被 main.js 的全局错误处理捕获，整页显示「页面出现异常，请刷新后重试」。
  // 同时用 try/catch 兜底：模板里的 computed 一旦抛错会让整页白屏，代价太大。
  try {
    if (auth.hasPerm('schedule:manage')) return true
    const me = auth.user?.userId
    const creator = d.creatorId
    return creator != null && me != null && String(creator) === String(me)
  } catch (e) {
    return false
  }
})

</script>

<style scoped>
.schedule-detail-page { padding: 20px; display: flex; flex-direction: column; gap: 20px; }
.detail-card, .comments-card { border-radius: 12px; }
.err { color: #f56c6c; }
.header { display: flex; justify-content: space-between; align-items: center; }
.header-left { display: flex; align-items: center; gap: 12px; }
.back-btn { color: #606266; font-size: 14px; padding: 8px 12px; }
.back-btn:hover { color: #409eff; background-color: #ecf5ff; }
.header-title { font-weight: 600; font-size: 16px; }
.actions { margin-top: 20px; display: flex; align-items: center; gap: 12px; }
.actions-label { color: #909399; font-size: 13px; }
.status-buttons { display: flex; gap: 8px; flex-wrap: wrap; }
</style>
