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

    <!-- 补卡申请：员工不能直接改考勤，只能发起申请，由主管/HR 审批 -->
    <el-card shadow="never" class="list-card makeup-card">
      <template #header>
        <div class="card-head">
          <span>我的补卡申请</span>
          <el-button type="primary" size="small" @click="openMakeup">申请补卡</el-button>
        </div>
      </template>
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
      <el-table :data="myRequests" size="small" v-loading="makeupLoading" empty-text="还没有提交过补卡申请">
        <el-table-column label="补卡时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.punchTime) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approverName" label="审批人" width="110" />
        <el-table-column prop="approveRemark" label="审批备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="makeupVisible" title="申请补卡" width="460px">
      <el-form label-width="90px">
        <el-form-item label="补卡时间">
          <el-date-picker
            v-model="makeupForm.punchTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择漏打卡的时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="打卡类型">
          <el-radio-group v-model="makeupForm.punchType">
            <el-radio-button label="AUTO">自动判定</el-radio-button>
            <el-radio-button label="CHECK_IN">上班</el-radio-button>
            <el-radio-button label="CHECK_OUT">下班</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="补卡原因">
          <el-input v-model="makeupForm.reason" type="textarea" :rows="3" maxlength="200" show-word-limit
                    placeholder="例如：设备故障未能打卡" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="makeupVisible = false">取消</el-button>
        <el-button type="primary" :loading="makeupSubmitting" @click="submitMakeup">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { attendanceApi, makeupApi } from '@/api'
import { ElMessage } from 'element-plus'
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
// 加载失败与「没有数据」是两件事，用独立标志驱动告警与重试
const loadError = ref(false)
const rows = ref([])
const range = ref([])
const summary = reactive({ days: 0, normal: 0, late: 0, lateMinutes: 0 })

const hasEmployeeNo = computed(() => Boolean(auth.user?.employeeNo))

async function load() {
  loading.value = true
  loadError.value = false
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
    loadError.value = true
    rows.value = []
  } finally {
    loading.value = false
  }
}

// ---------- 补卡申请 ----------
const makeupVisible = ref(false)
const makeupSubmitting = ref(false)
const makeupLoading = ref(false)
const myRequests = ref([])
const makeupForm = reactive({ punchTime: '', punchType: 'AUTO', reason: '' })

const STATUS_TEXT = { pending: '待审批', approved: '已通过', rejected: '已驳回', canceled: '已撤销' }
const STATUS_TAG = { pending: 'warning', approved: 'success', rejected: 'danger', canceled: 'info' }
function statusText(s) {
  return STATUS_TEXT[s] || s || '—'
}
function statusTagType(s) {
  return STATUS_TAG[s] || 'info'
}

function openMakeup() {
  makeupForm.punchTime = ''
  makeupForm.punchType = 'AUTO'
  makeupForm.reason = ''
  makeupVisible.value = true
}

async function submitMakeup() {
  if (!makeupForm.punchTime) {
    ElMessage.warning('请选择补卡时间')
    return
  }
  if (!makeupForm.reason.trim()) {
    ElMessage.warning('请填写补卡原因')
    return
  }
  makeupSubmitting.value = true
  try {
    await makeupApi.submit({
      punchTime: makeupForm.punchTime,
      punchType: makeupForm.punchType,
      reason: makeupForm.reason.trim(),
    })
    ElMessage.success('已提交，等待审批')
    makeupVisible.value = false
    await loadMyRequests()
  } catch {
    /* 错误提示由拦截器统一处理 */
  } finally {
    makeupSubmitting.value = false
  }
}

async function loadMyRequests() {
  makeupLoading.value = true
  try {
    const data = await makeupApi.mine({ page: 1, size: 20 })
    myRequests.value = data?.records || []
  } catch {
    myRequests.value = []
  } finally {
    makeupLoading.value = false
  }
}
onMounted(async () => {
  await load()
  await loadMyRequests()
})
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

.load-error {
  margin-bottom: 10px;
  border-radius: 8px;
}
</style>
