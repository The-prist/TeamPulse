<template>
  <el-card>
    <template #header>团队详情</template>
    <div v-if="loading">加载中...</div>
    <div v-else-if="err">{{ err }}</div>
    <div v-else>
      <div class="title">{{ team.teamName }}</div>
      <div class="sub">{{ team.description }}</div>
      <el-divider />
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px">
        <div style="font-weight:600">成员</div>
        <div>
          <el-input v-model="newUserId" placeholder="用户ID" style="width:120px; margin-right:6px" />
          <el-select v-model="newRole" style="width:120px; margin-right:6px">
            <el-option label="成员" value="member" />
            <el-option label="负责人" value="leader" />
          </el-select>
          <el-button type="primary" size="small" @click="addMember">添加成员</el-button>
        </div>
      </div>
      <!-- 加载失败与「没有数据」要能区分，否则用户会误以为真的没有数据 -->
      <el-alert
        v-if="loadError"
        type="error"
        :closable="false"
        show-icon
        class="load-error"
        title="数据加载失败：网络或后端异常，当前显示的可能不是最新数据"
      >
        <el-button size="small" type="primary" link :loading="loading" @click="load">重新加载</el-button>
      </el-alert>
      <el-table :data="members" size="small" stripe>
        <el-table-column prop="userId" label="用户编号" width="100" />
        <el-table-column prop="nickname" label="姓名" />
        <el-table-column prop="teamRole" label="角色" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="teamRoleTagType(row.teamRole)">{{ teamRoleText(row.teamRole) }}</el-tag>
            </template>
          </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-select v-model="row.teamRole" size="small" style="width:110px; margin-right:6px"
                       @change="(v)=>updateRole(row.userId, v)"
                       :disabled="!isLeader && row.userId !== myId">
              <el-option label="成员" value="member" />
              <el-option label="负责人" value="leader" />
            </el-select>
            <el-button size="small" type="danger" @click="removeMember(row.userId)" :disabled="(!isLeader && row.userId !== myId) || row.teamRole==='leader'">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-card>
</template>
<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { teamApi } from '@/api'
import { teamRoleText, teamRoleTagType } from '@/utils/dict'
import { useAuthStore } from '@/stores/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
const route = useRoute()
const auth = useAuthStore()
const teamId = Number(route.params.id)
const loading = ref(false)
// 加载失败与「没有数据」是两件事，用独立标志驱动告警与重试
const loadError = ref(false)
const err = ref('')
const team = reactive({})
const members = ref([])
const newUserId = ref('')
const newRole = ref('member')
const myId = Number(auth.user?.userId || 0)
const isLeader = computed(()=> members.value.some(m=> m.userId===myId && m.teamRole==='leader'))

async function load(){
  loading.value = true; err.value = ''
  loadError.value = false
  try{
    // 详情与成员都直接返回业务对象/数组，不再有 { success, data } 外壳
    const t = await teamApi.detail(teamId)
    Object.assign(team, t || {})
    const list = await teamApi.members(teamId)
    members.value = Array.isArray(list) ? list : []
  }catch(e){
    loadError.value = true;
    err.value = String(e) } finally { loading.value = false }
}

async function addMember(){
  if (!newUserId.value) return ElMessage.warning('请输入用户ID')
  try{
    // POST /team/{teamId}/members
    await teamApi.addMember(teamId, { userId: Number(newUserId.value), teamRole: newRole.value })
    ElMessage.success('已添加'); newUserId.value=''; await load()
  }catch(e){ ElMessage.error(String(e)) }
}

async function removeMember(userId){
  try{
  await ElMessageBox.confirm(userId===myId ? '确定要退出该团队吗？' : '确定移除该成员？', '提示')
  // 前端保护：禁止移除任何leader
  const target = members.value.find(m=>m.userId===userId)
  if (target && target.teamRole==='leader') return ElMessage.warning('不能移除负责人')
  // 非leader不能移除他人
  if (userId!==myId && !isLeader.value) return ElMessage.warning('无权限移除他人')
    // DELETE /team/{teamId}/members/{userId}
    await teamApi.removeMember(teamId, userId)
    ElMessage.success('已移除'); await load()
  }catch(e){ if (e !== 'cancel') ElMessage.error(String(e)) }
}

async function updateRole(userId, role){
  try{
  // 非leader仅能修改自己
  if (userId!==myId && !isLeader.value) return ElMessage.warning('无权限更改他人角色')
    // PUT /team/{teamId}/members/{userId}/role
    await teamApi.updateMemberRole(teamId, userId, role)
    ElMessage.success('已更新')
  }catch(e){ ElMessage.error(String(e)) }
}

onMounted(load)
</script>
<style scoped>
.title{ font-weight:600; font-size:18px }
.sub{ color:#666; margin-top:4px }

.load-error {
  margin-bottom: 10px;
  border-radius: 8px;
}
</style>
