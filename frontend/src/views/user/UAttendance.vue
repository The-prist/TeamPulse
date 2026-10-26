<!--
  我的考勤（用户工作台）。
  直连 /attendance/my，读取的是 attendance_daily 日汇总表，比逐条算明细快得多。
-->
<template>
  <div class="my-attendance">
    <el-row :gutter="14" class="summary-row">
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="summary-card">
          <div class="label">统计天数</div>
          <div class="value">{{ summary.days }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="summary-card ok">
          <div class="label">正常</div>
          <div class="value">{{ summary.normal }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="summary-card warn">
          <div class="label">迟到次数</div>
          <div class="value">{{ summary.late }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="summary-card info">
          <div class="label">累计迟到</div>
          <div class="value small">{{ formatMinutes(summary.lateMinutes) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="list-card">
      <template #header>
        <div class="header">
          <div class="title">我的考勤明细</div>
          <div class="actions">
            <el-date-picker
              v-model="range"
              type="daterange"
              value-format="YYYY-MM-DD"
              start-placeholder="开始"
              end-placeholder="结束"
              style="width: 250px"
              @change="load"
            />
            <el-button @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="!hasEmployeeNo"
        type="warning"
        :closable="false"
        show-icon
        class="no-emp-tip"
        title="当前账号未绑定工号，无法产生打卡记录。请联系管理员在「用户管理」中补充工号。"
      />

      <el-table v-loading="loading" :data="rows" size="small" empty-text="暂无考勤记录">
        <el-table-column prop="workDate" label="日期" width="120">
          <template #default="{ row }">{{ formatDate(row.workDate) }}</template>
        </el-table-column>
        <el-table-column label="上班打卡" width="170">
          <template #default="{ row }">{{ formatDateTime(row.firstCheckIn) }}</template>
        </el-table-column>
        <el-table-column label="下班打卡" width="170">
          <template #default="{ row }">{{ formatDateTime(row.lastCheckOut) }}</template>
        </el-table-column>
        <el-table-column label="在岗时长" width="130">
          <template #default="{ row }">{{ formatMinutes(row.workMinutes) }}</template>
        </el-table-column>
        <el-table-column label="判定" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="punchStatusType(row.attendanceStatus)">
              {{ punchStatusText(row.attendanceStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="异常说明" min-width="180">
          <template #default="{ row }">
            <span v-if="row.lateMinutes > 0" class="warn">迟到 {{ row.lateMinutes }} 分钟</span>
            <span v-if="row.earlyLeaveMinutes > 0" class="warn">
              {{ row.lateMinutes > 0 ? '；' : '' }}早退 {{ row.earlyLeaveMinutes }} 分钟
            </span>
            <span v-if="!row.lateMinutes && !row.earlyLeaveMinutes" class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { attendanceApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import {
  formatDate,
  formatDateTime,
  formatMinutes,
  punchStatusText,
  punchStatusType,
} from '@/utils/attendance'

const auth = useAuthStore()
const loading = ref(false)
const rows = ref([])
const range = ref([])
const summary = reactive({ days: 0, normal: 0, late: 0, lateMinutes: 0 })

const hasEmployeeNo = computed(() => Boolean(auth.user?.employeeNo))

async function load() {
  loading.value = true
  try {
    const params = {}
    if (Array.isArray(range.value) && range.value.length === 2) {
      params.from = range.value[0]
      params.to = range.value[1]
    }
    const data = await attendanceApi.my(params)
    rows.value = Array.isArray(data) ? data : []

    summary.days = rows.value.length
    summary.normal = rows.value.filter((r) => r.attendanceStatus === 'NORMAL').length
    summary.late = rows.value.filter((r) => Number(r.lateMinutes || 0) > 0).length
    summary.lateMinutes = rows.value.reduce((sum, r) => sum + Number(r.lateMinutes || 0), 0)
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.my-attendance {
  display: grid;
  gap: 14px;
}
.summary-row {
  row-gap: 12px;
}
.summary-card {
  border-radius: 10px;
  border: 1px solid var(--tp-border);
}
.summary-card :deep(.el-card__body) {
  padding: 14px 16px;
}
.summary-card .label {
  font-size: 12px;
  color: #64748b;
}
.summary-card .value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 650;
  color: #0f172a;
}
.summary-card .value.small {
  font-size: 18px;
}
.summary-card.ok .value {
  color: #059669;
}
.summary-card.warn .value {
  color: #d97706;
}
.summary-card.info .value {
  color: #2563eb;
}
.list-card {
  border-radius: 10px;
  border: 1px solid var(--tp-border);
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.header .title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}
.header .actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.no-emp-tip {
  margin-bottom: 12px;
}
.warn {
  color: #d97706;
  font-size: 12.5px;
}
.muted {
  color: #94a3b8;
}
</style>
