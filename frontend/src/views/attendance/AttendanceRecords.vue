<!--
  打卡记录。
  支持按工号 / 状态 / 日期区间筛选，管理员可手工补卡（补录忘打卡的场景）。
-->
<template>
  <div class="records-page">
    <div class="view-bar">
      <div class="view-hint">日历看「哪天出问题」，列表看「每条打卡明细」</div>
      <el-radio-group v-model="viewMode" size="default">
        <el-radio-button label="calendar">日历</el-radio-button>
        <el-radio-button label="list">列表</el-radio-button>
      </el-radio-group>
    </div>

    <AttendanceCalendar v-if="viewMode === 'calendar'" @manual="openManualForDate" />

    <template v-else>
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item label="员工">
          <el-input
            v-model="query.userName"
            placeholder="姓名 / 账号 / 工号，支持模糊"
            clearable
            style="width: 210px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="判定">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item class="right-item">
          <el-button type="success" plain @click="openManualDialog" v-if="auth.hasPerm('attendance:manage')">手工补卡</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="result-bar">
        <span>共 <b>{{ total }}</b> 条打卡记录</span>
        <span class="logic-hint">员工支持姓名模糊查询；条件之间是「且」的关系</span>
      </div>
      <!-- 加载失败与「确实没有数据」要能区分，否则用户会误以为当天没人打卡 -->
      <el-alert
        v-if="loadError"
        type="error"
        :closable="false"
        show-icon
        class="load-error"
        title="打卡记录加载失败：网络或后端异常，当前显示的可能不是最新数据"
      >
        <el-button size="small" type="primary" link :loading="loading" @click="load">重新加载</el-button>
      </el-alert>
      <el-table v-loading="loading" :data="rows" size="small" empty-text="没有符合条件的打卡记录">
        <el-table-column prop="punchTime" label="打卡时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.punchTime) }}</template>
        </el-table-column>
        <el-table-column prop="employeeNo" label="工号" width="100" />
        <el-table-column label="姓名" width="130">
          <template #default="{ row }">
            <!-- 点人名看这个人的考勤详情（统计 + 按天明细 + 流水） -->
            <el-button v-if="row.userId" link type="primary" @click="openEmployee(row)">
              {{ row.userName || '未注册工号' }}
            </el-button>
            <span v-else class="muted">未注册工号</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="row.punchType === 'CHECK_IN' ? 'primary' : 'info'">
              {{ punchTypeText(row.punchType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="判定" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="punchStatusType(row.status)">{{ punchStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="迟到/早退" width="130">
          <template #default="{ row }">
            <span v-if="row.lateMinutes > 0" class="warn-text">迟到 {{ row.lateMinutes }} 分</span>
            <span v-else-if="row.earlyLeaveMinutes > 0" class="warn-text">早退 {{ row.earlyLeaveMinutes }} 分</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <el-tooltip :content="`签名校验：${row.signVerified ? '通过' : '未校验'}`" placement="top">
              <span>{{ sourceText(row.source) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="设备位置" min-width="130">
          <template #default="{ row }">{{ row.deviceLocation || row.deviceSn || '—' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
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
    </template>

    <!-- 员工考勤详情 -->
    <EmployeeAttendanceDrawer
      v-model="employeeVisible"
      :user-id="employeeTarget.userId"
      :user-name="employeeTarget.userName"
      :from="employeeRange.from"
      :to="employeeRange.to"
    />

    <!-- 手工补卡 -->
    <el-dialog v-model="manualVisible" title="手工补卡" width="440px" destroy-on-close>
      <el-form ref="manualFormRef" :model="manualForm" :rules="manualRules" label-width="90px">
        <el-form-item label="工号" prop="employeeNo">
          <el-input v-model="manualForm.employeeNo" placeholder="如 E1001" />
        </el-form-item>
        <el-form-item label="打卡时间" prop="punchTime">
          <el-date-picker
            v-model="manualForm.punchTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择日期时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="类型" prop="punchType">
          <el-radio-group v-model="manualForm.punchType">
            <el-radio-button label="AUTO">自动判定</el-radio-button>
            <el-radio-button label="CHECK_IN">上班</el-radio-button>
            <el-radio-button label="CHECK_OUT">下班</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="manualForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitManual">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore()
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { attendanceApi } from '@/api'
import AttendanceCalendar from './AttendanceCalendar.vue'
import EmployeeAttendanceDrawer from './EmployeeAttendanceDrawer.vue'
import {
  formatDateTime,
  nowText,
  punchStatusText,
  punchStatusType,
  punchTypeText,
  sourceText,
} from '@/utils/attendance'

const loading = ref(false)
// 加载失败与「没有数据」是两件事，用独立标志驱动错误提示与重试
const loadError = ref(false)
/** 视图模式：日历（默认，看哪天出问题）/ 列表（逐条明细与筛选） */
const viewMode = ref('calendar')
const rows = ref([])
const total = ref(0)
const dateRange = ref([])

const query = reactive({
  /** 姓名 / 账号 / 工号的模糊关键字（后端会解析成 userId 集合） */
  userName: '',
  status: '',
  page: 1,
  size: 100,
})

// ---------- 员工考勤详情 ----------
const employeeVisible = ref(false)
const employeeTarget = reactive({ userId: null, userName: '' })
/** 详情区间跟随列表的日期筛选；未选日期时后端默认最近 30 天 */
const employeeRange = reactive({ from: '', to: '' })

function openEmployee(row) {
  if (!row?.userId) return
  employeeTarget.userId = row.userId
  employeeTarget.userName = row.userName || ''
  if (Array.isArray(dateRange.value) && dateRange.value.length === 2) {
    employeeRange.from = dateRange.value[0]
    employeeRange.to = dateRange.value[1]
  } else {
    employeeRange.from = ''
    employeeRange.to = ''
  }
  employeeVisible.value = true
}

const statusOptions = [
  { label: '正常', value: 'NORMAL' },
  { label: '迟到', value: 'LATE' },
  { label: '早退', value: 'EARLY_LEAVE' },
  { label: '迟到且早退', value: 'LATE_AND_EARLY' },
  { label: '无效', value: 'INVALID' },
]

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const params = { ...query }
    if (Array.isArray(dateRange.value) && dateRange.value.length === 2) {
      params.from = dateRange.value[0]
      params.to = dateRange.value[1]
    }
    if (!params.userName) delete params.userName
    if (!params.status) delete params.status

    const data = await attendanceApi.records(params)
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

function handleSearch() {
  query.page = 1
  load()
}

function handleReset() {
  query.userName = ''
  query.status = ''
  query.page = 1
  query.size = 100
  dateRange.value = []
  load()
}

// ---------- 手工补卡 ----------
const manualVisible = ref(false)
const submitting = ref(false)
const manualFormRef = ref(null)
const manualForm = reactive({
  employeeNo: '',
  punchTime: '',
  punchType: 'AUTO',
  remark: '',
})
const manualRules = {
  employeeNo: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  punchTime: [{ required: true, message: '请选择打卡时间', trigger: 'change' }],
}

function openManualDialog() {
  manualForm.employeeNo = ''
  manualForm.punchTime = nowText()
  manualForm.punchType = 'AUTO'
  manualForm.remark = ''
  manualVisible.value = true
}

/**
 * 从日历里点「补卡」进来：预填那一天的 09:00。
 * 工号仍然留空让管理员输入——补卡的对象是人，不该由日历日期推断。
 */
function openManualForDate(dateKey) {
  openManualDialog()
  if (typeof dateKey === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(dateKey)) {
    manualForm.punchTime = `${dateKey} 09:00:00`
  }
}

async function submitManual() {
  const valid = await manualFormRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await attendanceApi.manual({ ...manualForm })
    ElMessage.success('补卡成功')
    manualVisible.value = false
    handleSearch()
  } catch {
    /* 提示已由 http 层处理 */
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.records-page {
  display: grid;
  gap: 14px;
}
.view-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.view-hint {
  font-size: 12.5px;
  color: #64748b;
}
.result-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 2px 10px;
  font-size: 12.5px;
  color: var(--tp-text-muted);
  flex-wrap: wrap;
}
.result-bar b {
  color: var(--tp-text);
}
.logic-hint {
  color: var(--tp-text-faint);
}
.muted {
  color: var(--tp-text-faint);
}
.filter-card,
.table-card {
  border-radius: 10px;
  border: 1px solid var(--tp-border);
}
.filter-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}
.filter-form :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 14px;
}
.right-item {
  margin-left: auto;
  margin-right: 0 !important;
}
.warn-text {
  color: #d97706;
  font-size: 12.5px;
}
.muted {
  color: #94a3b8;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.load-error {
  margin-bottom: 10px;
  border-radius: 8px;
}
</style>
