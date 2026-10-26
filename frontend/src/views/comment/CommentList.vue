<!--
  评论管理。

  两个页签：
    · 评论浏览：管理员视角的**全量**评论检索（不限关联对象）
    · 敏感词管理：词库增删改查

  检索维度：关键词 / 日期区间 / 时间段（每天的这个时段）/ 评论人 / 关联类型 / 关联编号。
  默认按时间正序、单页取满 200 条，进页面就能看到全部讨论，
  不需要先选某个日程才能查评论（旧版必须带 relatedId，导致页面初次打开是空的）。
-->
<template>
  <div class="comment-page">
    <div class="page-head">
      <h2 class="page-title">评论管理</h2>
      <p class="page-subtitle">按日期、时间段、评论人筛选全部评论；默认按时间正序从上往下排列</p>
    </div>

    <el-tabs v-model="activeTab" class="comment-tabs">
      <el-tab-pane label="评论浏览" name="comments">
        <div class="filter-area">
          <el-card shadow="never" class="filter-card">
            <template #header>
              <div class="card-head">
                <span>检索条件</span>
                <el-radio-group v-model="query.order" size="small" @change="reload">
                  <el-radio-button label="asc">时间正序</el-radio-button>
                  <el-radio-button label="desc">时间倒序</el-radio-button>
                </el-radio-group>
              </div>
            </template>

            <el-form :inline="true" class="filter-form" @submit.prevent>
              <el-form-item label="关键词">
                <el-input
                  v-model="query.keyword"
                  placeholder="正文或原文包含"
                  clearable
                  style="width: 190px"
                  @keyup.enter="reload"
                />
              </el-form-item>

              <el-form-item label="日期范围">
                <el-date-picker
                  v-model="query.dateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  value-format="YYYY-MM-DD"
                style="width: 240px"
                />
              </el-form-item>

              <el-form-item label="时间段">
                <el-time-picker
                  v-model="query.timeRange"
                  is-range
                  range-separator="至"
                  start-placeholder="起"
                  end-placeholder="止"
                  format="HH:mm"
                  value-format="HH:mm"
                style="width: 210px"
                />
              </el-form-item>

              <el-form-item label="评论人">
                <el-select
                  v-model="query.creatorId"
                  placeholder="全部"
                  clearable
                  filterable
                style="width: 170px"

                >
                  <el-option
                    v-for="u in users"
                    :key="u.userId"
                    :label="displayName(u)"
                    :value="u.userId"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="关联类型">
                <el-select v-model="query.relatedType" placeholder="全部" clearable style="width: 130px">
                  <el-option label="日程" value="schedule" />
                  <el-option label="工作日志" value="worklog" />
                </el-select>
              </el-form-item>

              <el-form-item label="关联编号">
                <el-input
                  v-model="query.relatedId"
                  placeholder="如 12"
                  clearable
                  style="width: 110px"
                  @keyup.enter="reload"
                />
              </el-form-item>

              <el-form-item class="filter-actions">
                <el-button type="primary" @click="reload">查询</el-button>
                <el-button @click="resetQuery">重置</el-button>
              </el-form-item>
          </el-form>
          </el-card>

          <div class="result-bar">
            <span>共 <b>{{ total }}</b> 条评论</span>
            <span class="result-range" v-if="rangeText">{{ rangeText }}</span>
            <span class="logic-hint">条件之间是「且」的关系；只填一个条件也能查</span>
            <span v-if="truncated" class="warn-text">单页最多 200 条，请缩小日期范围或翻页查看</span>
          </div>
          </div>

        <el-card shadow="never" class="list-card">
          <el-table :data="rows" v-loading="loading" size="small">
            <template #empty>
              <div class="empty-box">
                <el-empty :description="emptyText" :image-size="70" />
                <el-button v-if="hasFilter" size="small" @click="resetQuery">清空筛选条件</el-button>
              </div>
            </template>
            <el-table-column prop="commentId" label="编号" width="80" />
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="评论人" width="130">
              <template #default="{ row }">
                {{ row.creatorName || ('用户 #' + row.creatorId) }}
              </template>
            </el-table-column>
            <el-table-column label="关联" width="150">
              <template #default="{ row }">
                <el-tag size="small" :type="row.relatedType === 'schedule' ? 'primary' : 'success'">
                  {{ relatedTypeText(row.relatedType) }}
                </el-tag>
                <span class="rel-id">#{{ row.relatedId }}</span>
              </template>
            </el-table-column>
            <el-table-column label="内容" min-width="320">
              <template #default="{ row }">
                <el-tag v-if="isMasked(row)" type="warning" size="small" class="mask-tag">含敏感词</el-tag>
                <span>{{ row.content }}</span>
                <el-tooltip v-if="isMasked(row)" placement="top">
                  <template #content>
                    原文：{{ row.originalContent }}
                  </template>
                  <el-icon class="ori-icon"><View /></el-icon>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="回复" width="90">
              <template #default="{ row }">
                <span v-if="row.replyTo" class="rel-id">#{{ row.replyTo }}</span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" @click="removeComment(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="query.page"
              v-model:page-size="query.size"
              :total="total"
              :page-sizes="[50, 100, 200]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="load"
              @current-change="load"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="敏感词管理" name="sensitive">
        <SensitiveWordPanel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { commentApi, userApi } from '@/api'
import { relatedTypeText } from '@/utils/dict'
import SensitiveWordPanel from './SensitiveWordPanel.vue'

const activeTab = ref('comments')
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const users = ref([])

const query = reactive({
  keyword: '',
  /** ['YYYY-MM-DD','YYYY-MM-DD'] */
  dateRange: null,
  /** ['HH:mm','HH:mm'] */
  timeRange: null,
  creatorId: null,
  relatedType: '',
  relatedId: '',
  order: 'asc',
  page: 1,
  /** 默认一次取满 200 条（后端单页上限），实现「进页面就看到全部」 */
  size: 200,
})

/** 命中条数被单页上限截断时提示用户缩小范围 */
const truncated = computed(() => total.value > rows.value.length && rows.value.length >= query.size)

/** 是否有任一筛选条件生效：空结果时用来决定提示语和是否给「清空条件」按钮 */
const hasFilter = computed(
  () =>
    !!query.keyword.trim() ||
    !!query.dateRange?.length ||
    !!query.timeRange?.length ||
    !!query.creatorId ||
    !!query.relatedType ||
    !!String(query.relatedId).trim(),
)

const emptyText = computed(() => (hasFilter.value ? '没有符合条件的评论' : '还没有任何评论'))

const rangeText = computed(() => {
  const parts = []
  if (query.dateRange?.length === 2) {
    parts.push(`日期 ${query.dateRange[0]} ~ ${query.dateRange[1]}`)
  }
  if (query.timeRange?.length === 2) {
    parts.push(`时段 ${query.timeRange[0]} ~ ${query.timeRange[1]}`)
  }
  if (query.creatorId) {
    const u = users.value.find((x) => x.userId === query.creatorId)
    parts.push(`评论人 ${u ? displayName(u) : '#' + query.creatorId}`)
  }
  return parts.join(' · ')
})

function displayName(user) {
  return user.nickname || user.username || '#' + user.userId
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

/** content 与 originalContent 不一致，说明入库时被敏感词替换过 */
function isMasked(row) {
  return !!row.originalContent && row.originalContent !== row.content
}

async function load() {
  loading.value = true
  try {
    const params = {
      order: query.order,
      page: query.page,
      size: query.size,
    }
    if (query.keyword.trim()) params.keyword = query.keyword.trim()
    if (query.dateRange?.length === 2) {
      params.from = query.dateRange[0]
      params.to = query.dateRange[1]
    }
    if (query.timeRange?.length === 2) {
      params.timeFrom = query.timeRange[0]
      params.timeTo = query.timeRange[1]
    }
    if (query.creatorId) params.creatorId = query.creatorId
    if (query.relatedType) params.relatedType = query.relatedType
    if (String(query.relatedId).trim()) params.relatedId = String(query.relatedId).trim()

    const data = await commentApi.adminPage(params)
    rows.value = data?.records || []
    total.value = Number(data?.total || 0)
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 改筛选条件要回到第 1 页，否则会停在越界页码上看到空列表 */
function reload() {
  query.page = 1
  load()
}

function resetQuery() {
  query.keyword = ''
  query.dateRange = null
  query.timeRange = null
  query.creatorId = null
  query.relatedType = ''
  query.relatedId = ''
  query.order = 'asc'
  query.page = 1
  query.size = 200
  load()
}

async function removeComment(row) {
  try {
    await ElMessageBox.confirm(`确定删除这条评论（#${row.commentId}）？删除后无法恢复。`, '删除评论', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await commentApi.remove(row.commentId)
    ElMessage.success('已删除')
    // 删掉当前页最后一条时往前退一页，避免停在空页
    if (rows.value.length === 1 && query.page > 1) query.page -= 1
    load()
  } catch {
    /* 错误提示由 http 拦截器统一处理 */
  }
}

async function loadUsers() {
  try {
    const data = await userApi.page({ page: 1, size: 200 })
    users.value = data?.records || []
  } catch {
    users.value = []
  }
}

onMounted(async () => {
  await loadUsers()
  await load()
})
</script>

<style scoped>
.comment-page {
  display: grid;
  gap: 14px;
}
.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 650;
  color: var(--tp-text);
}
.page-subtitle {
  margin: 5px 0 0;
  font-size: 12.5px;
  color: var(--tp-text-muted);
}
.filter-area {
  width: 100%;
}
/* 检索框按内容宽度收缩：宽屏下一行排满条件后卡片就结束，右侧不再留大片空白 */
.filter-card {
  width: fit-content;
  max-width: 100%;
}
.filter-card,
.list-card {
  border-radius: 10px;
}
.filter-card :deep(.el-card__body) {
  padding: 12px 14px 0;
}
.list-card {
  margin-top: 16px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0 8px;
}
.filter-form :deep(.el-form-item) {
  margin-bottom: 10px;
  margin-right: 0;
}
/* 换行时按钮贴右，避免末行左侧堆着按钮、右边一片空 */
.filter-form .filter-actions {
  margin-left: auto;
}
.result-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 8px;
  padding: 0 2px;
  font-size: 12.5px;
  color: var(--tp-text-muted);
  flex-wrap: wrap;
}
.result-bar b {
  color: var(--tp-text);
}
.warn-text {
  color: #d97706;
}
.logic-hint {
  color: var(--tp-text-faint);
}
.empty-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 0;
}
.rel-id {
  margin-left: 6px;
  color: var(--tp-text-muted);
  font-variant-numeric: tabular-nums;
}
.mask-tag {
  margin-right: 6px;
}
.ori-icon {
  margin-left: 6px;
  color: var(--tp-text-faint);
  cursor: help;
  vertical-align: middle;
}
.muted {
  color: var(--tp-text-faint);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.comment-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}
</style>
