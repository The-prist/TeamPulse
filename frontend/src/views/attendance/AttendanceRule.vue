<!--
  考勤规则配置。
  规则直接决定「迟到/早退」的判定口径，因此页面里把计算方式写清楚，避免使用者误解宽限时间的含义。
-->
<template>
  <div class="rule-page">
    <el-card shadow="never" class="rule-card">
      <template #header>
        <div class="header">
          <div class="title">考勤规则</div>
          <div class="subtitle">当前系统为全局单条规则；修改后立即对新的打卡生效</div>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" class="rule-form">
        <el-form-item label="上班时间" prop="workStart">
          <el-time-picker
            v-model="form.workStart"
            value-format="HH:mm:ss"
            placeholder="选择上班时间"
            style="width: 200px"
          />
        </el-form-item>

        <el-form-item label="下班时间" prop="workEnd">
          <el-time-picker
            v-model="form.workEnd"
            value-format="HH:mm:ss"
            placeholder="选择下班时间"
            style="width: 200px"
          />
        </el-form-item>

        <el-form-item label="迟到宽限" prop="lateGraceMinutes">
          <el-input-number v-model="form.lateGraceMinutes" :min="0" :max="240" :step="5" />
          <span class="unit">分钟</span>
        </el-form-item>

        <el-form-item label="早退宽限" prop="earlyGraceMinutes">
          <el-input-number v-model="form.earlyGraceMinutes" :min="0" :max="240" :step="5" />
          <span class="unit">分钟</span>
        </el-form-item>

        <el-form-item label="工作日" prop="workDays">
          <el-checkbox-group v-model="selectedDays">
            <el-checkbox v-for="day in weekdays" :key="day.value" :label="day.value" :disabled="saving">
              {{ day.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave" v-if="auth.hasPerm('attendance:rule')">保存规则</el-button>
          <el-button @click="load">重新加载</el-button>
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false" show-icon class="explain">
        <template #title>判定口径说明</template>
        <ul class="explain-list">
          <li>
            <b>迟到</b>：打卡时间晚于「上班时间 + 迟到宽限」，迟到分钟数从<b>上班时间</b>开始计算，
            例如 9:00 上班、宽限 5 分钟、9:03 打卡 → 不算迟到；9:08 打卡 → 记迟到 8 分钟。
          </li>
          <li>
            <b>早退</b>：打卡时间早于「下班时间 - 早退宽限」，早退分钟数计算到<b>下班时间</b>为止。
          </li>
          <li>
            <b>签到 / 签退</b>：设备上报 <code>AUTO（自动判定）</code> 时，当天第一条记录判为签到，之后均判为签退。
          </li>
          <li>
            <b>缺勤</b>：工作日内没有任何有效打卡，由每日 00:05 的汇总任务补齐为缺勤。
          </li>
          <li>非工作日打卡仍会留痕，但备注为「非工作日打卡」，不参与迟到/早退统计。</li>
        </ul>
      </el-alert>
    </el-card>
  </div>
</template>

<script setup>
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore()
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { attendanceApi } from '@/api'

const formRef = ref(null)
const saving = ref(false)
const selectedDays = ref([1, 2, 3, 4, 5])

const weekdays = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 7 },
]

const form = reactive({
  workStart: '09:00:00',
  workEnd: '18:00:00',
  lateGraceMinutes: 5,
  earlyGraceMinutes: 5,
})

const rules = {
  workStart: [{ required: true, message: '请选择上班时间', trigger: 'change' }],
  workEnd: [{ required: true, message: '请选择下班时间', trigger: 'change' }],
}

async function load() {
  try {
    const data = await attendanceApi.getRule()
    if (!data) return
    form.workStart = normalizeTime(data.workStart, '09:00:00')
    form.workEnd = normalizeTime(data.workEnd, '18:00:00')
    form.lateGraceMinutes = Number(data.lateGraceMinutes ?? 0)
    form.earlyGraceMinutes = Number(data.earlyGraceMinutes ?? 0)
    selectedDays.value = String(data.workDays || '1,2,3,4,5')
      .split(',')
      .map((d) => Number(d.trim()))
      .filter((d) => d >= 1 && d <= 7)
  } catch {
    /* 提示已统一处理 */
  }
}

/** 后端可能返回 "09:00:00" 或 "09:00"，统一补齐成 HH:mm:ss */
function normalizeTime(value, fallback) {
  if (!value) return fallback
  const text = String(value)
  if (/^\d{2}:\d{2}:\d{2}$/.test(text)) return text
  if (/^\d{2}:\d{2}$/.test(text)) return `${text}:00`
  return fallback
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (selectedDays.value.length === 0) {
    ElMessage.warning('至少选择一个工作日')
    return
  }
  if (form.workStart >= form.workEnd) {
    ElMessage.warning('上班时间必须早于下班时间')
    return
  }

  saving.value = true
  try {
    await attendanceApi.updateRule({
      workStart: form.workStart,
      workEnd: form.workEnd,
      lateGraceMinutes: form.lateGraceMinutes,
      earlyGraceMinutes: form.earlyGraceMinutes,
      workDays: [...selectedDays.value].sort((a, b) => a - b).join(','),
    })
    ElMessage.success('规则已保存')
    load()
  } catch {
    /* 提示已统一处理 */
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.rule-card {
  border-radius: 10px;
  border: 1px solid var(--tp-border);
  max-width: 860px;
}
.header .title {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}
.header .subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}
.rule-form {
  max-width: 640px;
}
.unit {
  margin-left: 8px;
  font-size: 12.5px;
  color: #64748b;
}
.explain {
  margin-top: 8px;
}
.explain-list {
  margin: 6px 0 0;
  padding-left: 18px;
  font-size: 12.5px;
  line-height: 1.9;
  color: #475569;
}
.explain-list code {
  background: var(--tp-primary-soft);
  color: #1d4ed8;
  padding: 1px 5px;
  border-radius: 4px;
}
</style>
