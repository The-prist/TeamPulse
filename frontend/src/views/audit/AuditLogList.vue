<!--
  审计日志查询（只读）。

  谁能进：拥有 audit:view 的角色（系统管理员、人事专员、审计专员）。
  回答的问题：谁、在什么时候、改了什么、成没成、耗时多久、对应哪次请求（traceId）。
  审计表没有修改与删除入口，这里也就不提供任何写操作。
-->
<template>
  <div class="audit-page">
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" size="small">
        <el-form-item label="操作人 ID">
          <el-input v-model="query.actorId" placeholder="留空=全部" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item label="模块">
          <el-select v-model="query.module" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="m in options.modules" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作">
          <el-input v-model="query.action" placeholder="如 user.delete" clearable style="width: 170px" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.result" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="r in options.results" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item label="traceId">
          <el-input v-model="query.traceId" placeholder="精确匹配" clearable style="width: 170px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="list-card">
      <div class="result-bar">
        <span>共 <b>{{ total }}</b> 条审计记录</span>
        <span class="logic-hint">审计表只追加不修改；点击行可看详情与 traceId</span>
      </div>

      <el-alert
        v-if="loadError"
        type="error"
        :closable="false"
        show-icon
        class="load-error"
        title="审计日志加载失败：网络或后端异常"
      >
        <el-button size="small" type="primary" link :loading="loading" @click="load">重新加载</el-button>
      </el-alert>

      <el-table :data="rows" v-loading="loading" size="small" stripe empty-text="没有符合条件的审计记录"
                @row-click="openDetail">
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="130">
          <template #default="{ row }">
            <div>{{ row.actorName || '未知' }}</div>
            <div class="muted">{{ row.actorRole || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="动作" min-width="170">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="actionTagType(row.action)">{{ row.action }}</el-tag>
            <span v-if="row.detail" class="detail-text">{{ row.detail }}</span>
          </template>
        </el-table-column>
        <el-table-column label="对象" width="130">
          <template #default="{ row }">
            {{ row.resource || '—' }}<span v-if="row.resourceId" class="muted"> #{{ row.resourceId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.result === 'SUCCESS' ? 'success' : 'danger'">
              {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="80">
          <template #default="{ row }">{{ row.costMs != null ? row.costMs + ' ms' : '—' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="120" />
        <el-table-column label="traceId" width="150">
          <template #default="{ row }">
            <span class="mono">{{ row.traceId || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>

    <el-drawer v-model="drawer.visible" title="审计详情" size="42%">
      <el-descriptions v-if="drawer.row" :column="1" border size="small">
        <el-descriptions-item label="记录 ID">{{ drawer.row.id }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatTime(drawer.row.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">
          {{ drawer.row.actorName || '未知' }}（ID {{ drawer.row.actorId || '—' }}，角色 {{ drawer.row.actorRole || '—' }}）
        </el-descriptions-item>
        <el-descriptions-item label="动作">{{ drawer.row.action }}</el-descriptions-item>
        <el-descriptions-item label="变更说明">{{ drawer.row.detail || '—' }}</el-descriptions-item>
        <el-descriptions-item label="对象">
          {{ drawer.row.resource || '—' }}<span v-if="drawer.row.resourceId"> #{{ drawer.row.resourceId }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="请求">{{ drawer.row.method }} {{ drawer.row.path }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag size="small" :type="drawer.row.result === 'SUCCESS' ? 'success' : 'danger'">
            {{ drawer.row.result === 'SUCCESS' ? '成功' : '失败' }}
          </el-tag>
          <span v-if="drawer.row.errorMsg" class="error-text">{{ drawer.row.errorMsg }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ drawer.row.costMs != null ? drawer.row.costMs + ' ms' : '—' }}</el-descriptions-item>
        <el-descriptions-item label="来源 IP">{{ drawer.row.ip || '—' }}</el-descriptions-item>
        <el-descriptions-item label="User-Agent">{{ drawer.row.userAgent || '—' }}</el-descriptions-item>
        <el-descriptions-item label="traceId">
          <span class="mono">{{ drawer.row.traceId || '—' }}</span>
          <el-button v-if="drawer.row.traceId" link type="primary" size="small"
                     style="margin-left:8px" @click="copyTrace(drawer.row.traceId)">复制</el-button>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { auditApi } from '@/api'

const loading = ref(false)
const loadError = ref(false)
const rows = ref([])
const total = ref(0)
const dateRange = ref([])
const options = reactive({ modules: [], results: [] })

const query = reactive({
  actorId: '',
  module: '',
  action: '',
  result: '',
  traceId: '',
  page: 1,
  size: 20,
})

const drawer = reactive({ visible: false, row: null })

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

/** 动作按语义配色：删除类红、权限/密钥类橙、其余蓝 */
function actionTagType(action) {
  const a = String(action || '')
  if (a.includes('delete') || a.includes('remove')) return 'danger'
  if (a.includes('secret') || a.includes('rule') || a.includes('reset')) return 'warning'
  return 'primary'
}

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const params = { page: query.page, size: query.size }
    if (query.actorId) params.actorId = Number(query.actorId)
    if (query.module) params.module = query.module
    if (query.action) params.action = query.action.trim()
    if (query.result) params.result = query.result
    if (query.traceId) params.traceId = query.traceId.trim()
    if (Array.isArray(dateRange.value) && dateRange.value.length === 2) {
      params.from = dateRange.value[0]
      params.to = dateRange.value[1]
    }
    const data = await auditApi.page(params)
    rows.value = data?.records || []
    total.value = Number(data?.total || 0)
  } catch {
    rows.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function reload() {
  query.page = 1
  load()
}

function resetQuery() {
  query.actorId = ''
  query.module = ''
  query.action = ''
  query.result = ''
  query.traceId = ''
  query.page = 1
  dateRange.value = []
  load()
}

async function openDetail(row) {
  drawer.row = row
  drawer.visible = true
}

async function copyTrace(traceId) {
  try {
    await navigator.clipboard.writeText(traceId)
    ElMessage.success('traceId 已复制，可在服务端日志中检索')
  } catch {
    ElMessage.warning('浏览器不允许自动复制，请手动选中')
  }
}

onMounted(async () => {
  try {
    const dict = await auditApi.options()
    options.modules = dict?.modules || []
    options.results = dict?.results || []
  } catch {
    options.modules = []
    options.results = []
  }
  await load()
})
</script>

<style scoped>
.audit-page {
  display: grid;
  gap: 12px;
}
.filter-card :deep(.el-form-item) {
  margin-bottom: 6px;
}
.result-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--tp-text-muted);
}
.logic-hint {
  color: var(--tp-text-faint);
  font-size: 12px;
}
.load-error {
  margin-bottom: 10px;
  border-radius: 8px;
}
.detail-text {
  margin-left: 8px;
  color: var(--tp-text-muted);
  font-size: 12px;
}
.muted {
  color: var(--tp-text-faint);
  font-size: 12px;
}
.mono {
  font-family: Consolas, Menlo, monospace;
  font-size: 12px;
}
.error-text {
  margin-left: 8px;
  color: #e5484d;
  font-size: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
