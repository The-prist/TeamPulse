<template>
  <div class="u-home">
    <!-- 顶部欢迎卡片（Hero） -->
    <div class="hero-section">
      <div class="hero-background"></div>
      <div class="hero-content">
        <div class="hero-text">
          <div class="hello">{{ greet }}, {{ username }} 👋</div>
          <div class="sub">今天也要元气满满，继续推进你的日程与任务。</div>
          <div class="date">{{ todayText }}</div>
          <div class="hero-actions">
            <el-button type="primary" size="large" @click="$router.push('/u/schedules')">
              <el-icon><Calendar /></el-icon>
              查看我的日程
            </el-button>
            <el-button size="large" @click="$router.push('/u/worklogs')">
              <el-icon><EditPen /></el-icon>
              填写工作日志
            </el-button>
          </div>
        </div>
        <div class="hero-illustration">
          <div class="floating-card card-1"></div>
          <div class="floating-card card-2"></div>
          <div class="floating-card card-3"></div>
        </div>
      </div>
    </div>

    <!-- 快捷入口（方块） -->
    <el-card class="quick-access-card" shadow="never">
      <template #header>
        <div class="section-header">
          <div class="section-title">
            <el-icon class="section-icon"><Grid /></el-icon>
            快捷入口
          </div>
        </div>
      </template>
      <div class="app-grid">
        <div class="app-tile schedules-tile" @click="$router.push('/u/schedules')">
          <div class="tile-icon">
            <el-icon><Calendar /></el-icon>
          </div>
          <div class="tile-content">
            <div class="tile-title">我的日程</div>
            <div class="tile-desc">规划与跟进你的待办</div>
          </div>
          <div class="tile-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
        <div class="app-tile worklogs-tile" @click="$router.push('/u/worklogs')">
          <div class="tile-icon">
            <el-icon><EditPen /></el-icon>
          </div>
          <div class="tile-content">
            <div class="tile-title">我的日志</div>
            <div class="tile-desc">记录每日工作与沉淀</div>
          </div>
          <div class="tile-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
        <div class="app-tile kanban-tile" @click="$router.push('/u/kanban')">
          <div class="tile-icon">
            <el-icon><Grid /></el-icon>
          </div>
          <div class="tile-content">
            <div class="tile-title">我的看板</div>
            <div class="tile-desc">全局掌握任务状态</div>
          </div>
          <div class="tile-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
        <div class="app-tile teams-tile" @click="$router.push('/u/teams')">
          <div class="tile-icon">
            <el-icon><UserFilled /></el-icon>
          </div>
          <div class="tile-content">
            <div class="tile-title">我的团队</div>
            <div class="tile-desc">协作与团队管理</div>
          </div>
          <div class="tile-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 相关日程（进行中/即将到期 优先） -->
  <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <div class="section-title">相关日程</div>
          <div class="section-actions">
            <el-input v-model="q.keyword" placeholder="按标题搜索" clearable style="width:220px" />
            <el-select v-model="q.status" placeholder="状态" clearable style="width:140px;margin-left:8px">
              <el-option label="全部" :value="''" />
              <el-option label="待办" value="todo" />
              <el-option label="进行中" value="doing" />
              <el-option label="已完成" value="done" />
            </el-select>
            <el-button type="primary" @click="load" style="margin-left:8px">查询</el-button>
          </div>
        </div>
      </template>
      <el-skeleton :loading="loading" animated>
        <template #template>
          <div class="skeleton-row" v-for="i in 3" :key="i">
            <el-skeleton-item variant="p" style="width: 60%" />
          </div>
        </template>
        <template #default>
          <div v-if="displayTop.length" class="schedule-list">
            <div v-for="row in displayTop" :key="row.scheduleId" class="schedule-item" @click="$router.push(`/u/schedules/${row.scheduleId}`)">
              <div class="title">{{ row.title || `日程#${row.scheduleId}` }}</div>
              <div class="meta">
                <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
                <span class="dot">•</span>
                <span :class="['due', { overdue: isOverdue(row) }]">{{ dueLabel(row) }}</span>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无相关日程">
            <el-button type="primary" @click="$router.push('/u/schedules')">去创建</el-button>
          </el-empty>
          <div class="more" v-if="displayList.length > 6">
            <el-link type="primary" @click="$router.push('/u/schedules')">查看更多 »</el-link>
          </div>
        </template>
      </el-skeleton>
    </el-card>
  </div>
</template>
<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { scheduleApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { Calendar, EditPen, Grid, UserFilled, ArrowRight } from '@element-plus/icons-vue'

const auth = useAuthStore()
const username = ref(auth.displayName || '用户')
const list = ref([])
const loading = ref(false)
const q = reactive({ keyword: '', status: '' })

const greet = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})
const todayText = computed(() => {
  const d = new Date()
  const w = ['日','一','二','三','四','五','六'][d.getDay()]
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} 星期${w}`
})

function toArray(x){ return Array.isArray(x) ? x : [] }

async function load(){
  loading.value = true
  try{
    const uid = auth.user?.userId
    const params = { page: 1, size: 50 }
    if (uid) params.creatorId = uid
    // /schedule 返回分页对象 { records, total, page, size }
    const page = await scheduleApi.page(params)
    list.value = toArray(page?.records)
  }catch(e){ ElMessage.error('加载失败：' + (e.message||'')) } finally { loading.value=false }
}

const displayList = computed(()=>{
  const kw = (q.keyword||'').trim().toLowerCase()
  const st = (q.status||'').trim().toLowerCase()
  const now = new Date()
  const getDue = (r) => r.endTime || r.dueDate || r.remindTime
  const score = (row)=>{
    const s = String(row.status||'').toLowerCase()
    let base = s==='doing' ? 0 : s==='todo' ? 1 : 2
    let delta = 0
    const due = getDue(row)
    if (due) {
      const d = new Date(String(due).includes('T') ? due : String(due).replace(' ','T'))
      if (!isNaN(d)) delta = Math.abs(d - now) / (1000*60*60)
    }
    return base * 1e6 + delta
  }
  return toArray(list.value)
    .filter(r=> !st || String(r.status||'').toLowerCase()===st)
    .filter(r=> !kw || String(r.title||'').toLowerCase().includes(kw))
    .slice()
    .sort((a,b)=> score(a) - score(b))
})

const displayTop = computed(()=> displayList.value.slice(0, 6))

function statusText(s){
  const v = String(s||'').toLowerCase()
  if (v==='doing') return '进行中'
  if (v==='done') return '已完成'
  if (v==='todo') return '待办'
  if (v==='canceled') return '已取消'
  return v || '未知'
}
function statusType(s){
  const v = String(s||'').toLowerCase()
  if (v==='doing') return 'warning'
  if (v==='done') return 'success'
  if (v==='todo') return 'info'
  if (v==='canceled') return 'default'
  return 'info'
}
function isOverdue(r){
  const v = r.endTime || r.dueDate
  if (!v) return false
  const d = new Date(String(v).includes('T') ? v : String(v).replace(' ','T'))
  const st = String(r.status||'').toLowerCase()
  return !isNaN(d) && d.getTime() < Date.now() && st !== 'done' && st !== 'canceled'
}
function dueLabel(r){
  const v = r.endTime || r.dueDate
  if (!v) return '未设置截止'
  const d = new Date(String(v).includes('T') ? v : String(v).replace(' ','T'))
  if (isNaN(d)) return '日期异常'
  const days = Math.ceil((d.getTime() - Date.now())/(1000*60*60*24))
  if (days < 0) return `已逾期 ${Math.abs(days)} 天`
  if (days === 0) return '今天截止'
  if (days === 1) return '明天截止'
  return `${days} 天后截止`
}

onMounted(load)
</script>
<style scoped>
.u-home { display: grid; gap: 24px; padding: 20px; }

/* Hero Section */
.hero-section {
  position: relative;
  border-radius: 20px;
  overflow: hidden;
  min-height: 280px;
  display: flex;
  align-items: center;
}

.hero-background {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  opacity: 0.9;
}

.hero-background::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><pattern id="grain" width="100" height="100" patternUnits="userSpaceOnUse"><circle cx="25" cy="25" r="1" fill="%23ffffff" opacity="0.1"/><circle cx="75" cy="75" r="1" fill="%23ffffff" opacity="0.1"/><circle cx="50" cy="10" r="0.5" fill="%23ffffff" opacity="0.15"/><circle cx="10" cy="60" r="0.5" fill="%23ffffff" opacity="0.15"/><circle cx="90" cy="40" r="0.5" fill="%23ffffff" opacity="0.15"/></pattern></defs><rect width="100" height="100" fill="url(%23grain)"/></svg>') repeat;
}

.hero-content {
  position: relative;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding: 40px;
  color: white;
}

.hero-text {
  flex: 1;
}

.hello {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 8px;
  text-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.sub {
  font-size: 16px;
  opacity: 0.9;
  margin-bottom: 8px;
  line-height: 1.5;
}

.date {
  font-size: 14px;
  opacity: 0.8;
  margin-bottom: 24px;
}

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.hero-actions .el-button {
  border-radius: 12px;
  padding: 12px 24px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  transition: all 0.3s ease;
}

.hero-actions .el-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0,0,0,0.2);
}

.hero-illustration {
  position: relative;
  width: 200px;
  height: 200px;
  margin-left: 40px;
}

.floating-card {
  position: absolute;
  border-radius: 12px;
  background: rgba(255,255,255,0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255,255,255,0.2);
}

.card-1 {
  width: 80px;
  height: 60px;
  top: 20px;
  right: 20px;
  animation: float 6s ease-in-out infinite;
}

.card-2 {
  width: 100px;
  height: 80px;
  top: 60px;
  right: 60px;
  animation: float 6s ease-in-out infinite 2s;
}

.card-3 {
  width: 60px;
  height: 40px;
  top: 120px;
  right: 40px;
  animation: float 6s ease-in-out infinite 4s;
}

@keyframes float {
  0%, 100% { transform: translateY(0px); }
  50% { transform: translateY(-10px); }
}

/* Quick Access Cards */
.quick-access-card {
  border: 1px solid #e4e7ed;
  border-radius: 16px;
  overflow: hidden;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  font-weight: 600;
  font-size: 18px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-icon {
  color: #409eff;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.app-tile {
  display: flex;
  align-items: center;
  padding: 20px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid #e4e7ed;
  background: var(--tp-bg-card);
  position: relative;
  overflow: hidden;
}

.app-tile::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, transparent 0%, rgba(64, 158, 255, 0.05) 100%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.app-tile:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
  border-color: #409eff;
}

.app-tile:hover::before {
  opacity: 1;
}

.tile-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  font-size: 24px;
  position: relative;
  z-index: 1;
}

.schedules-tile .tile-icon {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.worklogs-tile .tile-icon {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
}

.kanban-tile .tile-icon {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  color: white;
}

.teams-tile .tile-icon {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
  color: white;
}

.tile-content {
  flex: 1;
  position: relative;
  z-index: 1;
}

.tile-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 4px;
}

.tile-desc {
  font-size: 14px;
  color: #606266;
  line-height: 1.4;
}

.tile-arrow {
  color: #c0c4cc;
  font-size: 16px;
  transition: all 0.3s ease;
  position: relative;
  z-index: 1;
}

.app-tile:hover .tile-arrow {
  color: #409eff;
  transform: translateX(4px);
}

/* Schedule List */
.schedule-list {
  display: grid;
  gap: 12px;
}

.schedule-item {
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  padding: 16px;
  background: var(--tp-bg-card);
  transition: all 0.3s ease;
  cursor: pointer;
}

.schedule-item:hover {
  border-color: #409eff;
  box-shadow: 0 4px 20px rgba(64, 158, 255, 0.12);
  transform: translateY(-2px);
}

.schedule-item .title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #1f2d3d;
  font-size: 15px;
}

.schedule-item .meta {
  color: #606266;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.schedule-item .meta .dot {
  opacity: 0.5;
}

.due.overdue {
  color: #f56c6c;
  font-weight: 500;
}

.more {
  text-align: right;
  margin-top: 12px;
}

.skeleton-row {
  padding: 12px 0;
}

.u-home :deep(.el-card) {
  margin-bottom: 24px;
}

.section-card :deep(.el-card__header) {
  font-weight: 600;
  border-bottom: 1px solid #f0f2f5;
  padding: 20px 24px;
}

.section-card :deep(.el-card__body) {
  padding: 24px;
}

/* Responsive Design */
@media (max-width: 768px) {
  .u-home {
    padding: 16px;
    gap: 16px;
  }
  
  .hero-content {
    flex-direction: column;
    text-align: center;
    padding: 24px;
  }
  
  .hero-illustration {
    display: none;
  }
  
  .hello {
    font-size: 24px;
  }
  
  .app-grid {
    grid-template-columns: 1fr;
  }
  
  .hero-actions {
    justify-content: center;
  }
}
</style>
