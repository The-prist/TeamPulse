<template>
  <el-card>
    <template #header>日程详情</template>
    <div v-if="loading">加载中...</div>
    <div v-else-if="!detail">未找到</div>
    <div v-else>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ scheduleStatusText(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ priorityText(detail.priority) }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ detail.startTime }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ detail.endTime }}</el-descriptions-item>
        <el-descriptions-item label="地点">{{ detail.location }}</el-descriptions-item>
      </el-descriptions>
      <div class="actions">
        <span class="actions-label">操作：</span>
        <el-button size="small" :loading="updating" @click="setStatus('todo')" :type="detail.status==='todo' ? 'primary' : 'default'">待办</el-button>
        <el-button size="small" :loading="updating" @click="setStatus('doing')" :type="detail.status==='doing' ? 'primary' : 'default'">进行中</el-button>
        <el-button size="small" :loading="updating" @click="setStatus('done')" :type="detail.status==='done' ? 'success' : 'default'">已完成</el-button>
        <el-button size="small" :loading="updating" @click="setStatus('canceled')" :type="detail.status==='canceled' ? 'warning' : 'default'">作废</el-button>
        <el-popconfirm title="确认删除该日程吗？" @confirm="deleteSchedule">
          <template #reference>
            <el-button size="small" type="danger" :loading="deleting">删除</el-button>
          </template>
        </el-popconfirm>
      </div>
      <div style="margin-top:16px">
        <CommentsPanel mode="schedule" :related-id="detail.scheduleId" />
      </div>
    </div>
  </el-card>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { scheduleApi } from '@/api'
import { scheduleStatusText, priorityText } from '@/utils/dict'
import CommentsPanel from '@/components/CommentsPanel.vue'
const route = useRoute()
const loading = ref(false)
const detail = ref(null)
const updating = ref(false)
const deleting = ref(false)
async function load(){
  loading.value = true
  try{
    const { id } = route.params
    // 详情直接返回 ScheduleVO
    detail.value = await scheduleApi.detail(id)
  } finally { loading.value = false }
}
async function setStatus(status){
  if (!detail.value) return
  if (detail.value.status === status) return
  updating.value = true
  try{
    const id = detail.value.scheduleId
    // POST /schedule/{id}/status，返回 { scheduleId, status }
    const res = await scheduleApi.updateStatus(id, status)
    detail.value.status = res?.status || status
  } catch(err){
    console.error('更新状态失败', err)
  } finally { updating.value = false }
}
async function deleteSchedule(){
  if (!detail.value) return
  deleting.value = true
  try{
    const id = detail.value.scheduleId
    // DELETE /schedule/{id}
    await scheduleApi.remove(id)
    history.length > 1 ? window.history.back() : window.location.assign('/u/schedules')
  } catch(err){
    console.error('删除失败', err)
  } finally { deleting.value = false }
}
onMounted(load)
</script>
<style scoped>
.actions { margin-top: 16px; display: flex; align-items: center; gap: 8px; }
.actions-label { color: #909399; }
</style>
