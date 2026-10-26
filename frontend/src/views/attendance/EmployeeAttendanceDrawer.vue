<!--
  员工考勤详情抽屉。

  入口：打卡记录列表里点「姓名」。
  一次请求拿三块数据（后端 /attendance/employee/{userId} 已聚合好）：
    1. 汇总统计（应出勤 / 正常 / 迟到 / 早退 / 缺勤 / 平均在岗）
    2. 按天明细（每天的签到、签退、在岗时长、判定）
    3. 原始打卡流水（每一次打卡的类型、判定、来源、设备）

  为什么不用三个接口拼：一个人连打三次会增加等待和失败面，
  后端一次查完更稳，也保证三块数据的统计口径完全一致。
-->
<template>
  <el-drawer
    :model-value="modelValue"
    :title="drawerTitle"
    size="720px"
    destroy-on-close
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <div v-loading="loading" class="emp-detail">
      <el-empty v-if="!loading && !data" description="没有查到该员工的考勤数据" :image-size="80" />

      <template v-else-if="data">
        <div class="range-line">
          <el-icon><Calendar /></el-icon>
          <span>统计区间：{{ data.from }} ~ {{ data.to }}</span>
          <span class="range-tip">（未指定区间时默认取最近 30 天）</span>
        </div>

        <!-- 汇总 -->
        <div class="stat-grid">
          <div class="stat-item">
            <div class="stat-num">{{ data.summary.workDays }}</div>
            <div class="stat-label">应出勤（天）</div>
          </div>
          <div class="stat-item ok">
            <div class="stat-num">{{ data.summary.normalCount }}</div>
            <div class="stat-label">正常</div>
          </div>
          <div class="stat-item warn">
            <div class="stat-num">{{ data.summary.lateCount }}</div>
            <div class="stat-label">迟到</div>
          </div>
          <div class="stat-item warn">
            <div class="stat-num">{{ data.summary.earlyLeaveCount }}</div>
            <div class="stat-label">早退</div>
          </div>
          <div class="stat-item danger">
            <div class="stat-num">{{ data.summary.absentCount }}</div>
            <div class="stat-label">缺勤</div>
          </div>
          <div class="stat-item">
            <div class="stat-num">{{ minutesText(data.summary.avgWorkMinutes) }}</div>
            <div class="stat-label">平均在岗</div>
          </div>
        </div>

        <!-- 按天明细 -->
        <div class="section-title">
          按天明细
          <span class="section-sub">共 {{ data.days.length }} 天</span>
        </div>
        <el-table :data="data.days" size="small" empty-text="该区间没有日汇总数据" max-height="260">
          <el-table-column prop="workDate" label="日期" width="110" />
          <el-table-column label="签到" width="80">
            <template #default="{ row }">{{ timeOf(row.firstCheckIn) }}</template>
          </el-table-column>
          <el-table-column label="签退" width="80">
            <template #default="{ row }">{{ timeOf(row.lastCheckOut) }}</template>
          </el-table-column>
          <el-table-column label="在岗" width="90">
            <template #default="{ row }">{{ minutesText(row.workMinutes) }}</template>
          </el-table-column>
          <el-table-column label="异常" min-width="130">
            <template #default="{ row }">
              <span v-if="row.lateMinutes > 0" class="warn-text">迟到 {{ row.lateMinutes }} 分</span>
              <span v-if="row.earlyLeaveMinutes > 0" class="warn-text">
                {{ row.lateMinutes > 0 ? ' / ' : '' }}早退 {{ row.earlyLeaveMinutes }} 分
              </span>
              <span v-if="!row.lateMinutes && !row.earlyLeaveMinutes" class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="判定" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.attendanceStatus)">
                {{ statusText(row.attendanceStatus) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <!-- 原始流水 -->
        <div class="section-title">
          打卡流水
          <span class="section-sub">共 {{ data.records.length }} 条</span>
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
        <el-table :data="data.records" size="small" empty-text="该区间没有打卡流水" max-height="260">
          <el-table-column label="打卡时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.punchTime) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ punchTypeText(row.punchType) }}</template>
          </el-table-column>
          <el-table-column label="判定" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="punchStatusType(row.status)">{{ punchStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="100">
            <template #default="{ row }">{{ sourceText(row.source) }}</template>
          </el-table-column>
          <el-table-column label="设备" min-width="130">
            <template #default="{ row }">{{ row.deviceLocation || row.deviceSn || '—' }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        </el-table>
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { attendanceApi } from '@/api'
import {
  formatDateTime,
  punchStatusText,
  punchStatusType,
  punchTypeText,
  sourceText,
} from '@/utils/attendance'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  userId: { type: [Number, String], default: null },
  userName: { type: String, default: '' },
  from: { type: String, default: '' },
  to: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue'])

const loading = ref(false)
// 加载失败与「没有数据」是两件事，用独立标志驱动告警与重试
const loadError = ref(false)
const data = ref(null)

const drawerTitle = computed(() => {
  const name = data.value?.userName || props.userName || '员工'
  const no = data.value?.employeeNo ? `（${data.value.employeeNo}）` : ''
  return `${name}${no} 的考勤详情`
})

const STATUS_TEXT = {
  NORMAL: '正常',
  LATE: '迟到',
  EARLY_LEAVE: '早退',
  LATE_AND_EARLY: '迟到且早退',
  ABSENT: '缺勤',
}
const STATUS_TAG = {
  NORMAL: 'success',
  LATE: 'warning',
  EARLY_LEAVE: 'warning',
  LATE_AND_EARLY: 'danger',
  ABSENT: 'danger',
}

function statusText(s) {
  return STATUS_TEXT[s] || s || '-'
}
function statusTagType(s) {
  return STATUS_TAG[s] || 'info'
}
function timeOf(value) {
  if (!value) return '—'
  const t = String(value).replace('T', ' ')
  return t.length >= 16 ? t.slice(11, 16) : '—'
}
function minutesText(m) {
  const n = Number(m || 0)
  if (n <= 0) return '—'
  const h = Math.floor(n / 60)
  const r = n % 60
  return h > 0 ? `${h}h${r}m` : `${r}m`
}

async function load() {
  if (!props.userId) return
  loading.value = true
  loadError.value = false
  data.value = null
  try {
    const params = {}
    if (props.from) params.from = props.from
    if (props.to) params.to = props.to
    data.value = await attendanceApi.employeeDetail(props.userId, params)
  } catch {
    loadError.value = true
    data.value = null
  } finally {
    loading.value = false
  }
}

// 打开时（或切换了人）才请求，关闭不请求
watch(
  () => [props.modelValue, props.userId, props.from, props.to],
  ([visible]) => {
    if (visible) load()
  },
)
</script>

<style scoped>
.emp-detail {
  min-height: 200px;
}
.range-line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--tp-text-muted);
  margin-bottom: 12px;
}
.range-tip {
  color: var(--tp-text-faint);
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
  margin-bottom: 18px;
}
.stat-item {
  padding: 12px 8px;
  border-radius: 9px;
  background: var(--tp-bg-card);
  border: 1px solid var(--tp-border);
  text-align: center;
}
.stat-num {
  font-size: 20px;
  font-weight: 650;
  color: var(--tp-text);
  line-height: 1.2;
}
.stat-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--tp-text-muted);
}
.stat-item.ok .stat-num {
  color: #059669;
}
.stat-item.warn .stat-num {
  color: #d97706;
}
.stat-item.danger .stat-num {
  color: #dc2626;
}
.section-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 6px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--tp-text);
}
.section-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--tp-text-faint);
}
.warn-text {
  color: #d97706;
  font-size: 12.5px;
}
.muted {
  color: var(--tp-text-faint);
}

.load-error {
  margin-bottom: 10px;
  border-radius: 8px;
}
</style>
