<!--
  补卡审批。

  真实企业的补卡流程：员工提交 → 直属主管 / HR 审批 → 通过后系统才生成打卡记录。
  直接让管理员改数据会让考勤失去证据效力，所以这里必须走审批。

  权限：只有拥有 attendance:approve 的角色（部门经理 / HR / 系统管理员）能进这个页面；
  数据范围由后端控制——部门经理只看到本部门成员的申请。
-->
<template>
  <div class="approval-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">补卡审批</h2>
        <p class="page-subtitle">
          审批通过后系统才会生成正式打卡记录，并重建当天的考勤汇总
          <span v-if="!isUnrestricted" class="scope-tip">（你只能审批本部门成员）</span>
        </p>
      </div>
    </div>

    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane label="待审批" name="pending">
        <el-card shadow="never" class="list-card">
          <el-table :data="pendingRows" v-loading="loading" size="small" empty-text="没有待审批的补卡申请">
            <el-table-column prop="userName" label="申请人" width="110" />
            <el-table-column prop="employeeNo" label="工号" width="100" />
            <el-table-column label="补卡时间" width="160">
              <template #default="{ row }">{{ formatTime(row.punchTime) }}</template>
            </el-table-column>
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ punchTypeText(row.punchType) }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="补卡原因" min-width="200" show-overflow-tooltip />
            <el-table-column label="提交时间" width="160">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button link type="success" @click="openReview(row, true)" v-if="auth.hasPerm('attendance:approve')">通过</el-button>
                  <el-button link type="danger" @click="openReview(row, false)" v-if="auth.hasPerm('attendance:approve')">驳回</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="size"
              :total="total"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next"
              background
              @size-change="load"
              @current-change="load"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="已处理" name="reviewed">
        <el-card shadow="never" class="list-card">
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
          <el-table :data="reviewedRows" v-loading="loading" size="small" empty-text="还没有处理记录">
            <el-table-column prop="userName" label="申请人" width="110" />
            <el-table-column label="补卡时间" width="160">
              <template #default="{ row }">{{ formatTime(row.punchTime) }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="补卡原因" min-width="180" show-overflow-tooltip />
            <el-table-column label="结果" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'approved' ? 'success' : 'danger'">
                  {{ row.status === 'approved' ? '已通过' : '已驳回' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="approverName" label="审批人" width="110" />
            <el-table-column label="审批时间" width="160">
              <template #default="{ row }">{{ formatTime(row.approveTime) }}</template>
            </el-table-column>
            <el-table-column prop="approveRemark" label="审批备注" min-width="140" show-overflow-tooltip />
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="size"
              :total="total"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next"
              background
              @size-change="load"
              @current-change="load"
            />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialog.visible" :title="dialog.approved ? '通过补卡申请' : '驳回补卡申请'" width="460px">
      <el-descriptions :column="1" size="small" border class="review-desc">
        <el-descriptions-item label="申请人">{{ dialog.row?.userName }}（{{ dialog.row?.employeeNo }}）</el-descriptions-item>
        <el-descriptions-item label="补卡时间">{{ formatTime(dialog.row?.punchTime) }}</el-descriptions-item>
        <el-descriptions-item label="原因">{{ dialog.row?.reason }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="80px" class="review-form">
        <el-form-item label="审批备注">
          <el-input v-model="dialog.remark" type="textarea" :rows="3" maxlength="200" show-word-limit
                    :placeholder="dialog.approved ? '可选，例如：已与本人确认' : '请填写驳回原因'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button :type="dialog.approved ? 'success' : 'danger'" :loading="submitting" @click="submitReview">
          确认{{ dialog.approved ? '通过' : '驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { makeupApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { punchTypeText } from '@/utils/attendance'

const auth = useAuthStore()
const isUnrestricted = computed(() => auth.dataScope === 'ALL')

const tab = ref('pending')
const loading = ref(false)
// 加载失败与「没有数据」是两件事，用独立标志驱动告警与重试
const loadError = ref(false)
const submitting = ref(false)
const pendingRows = ref([])
const reviewedRows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const dialog = reactive({ visible: false, approved: true, row: null, remark: '' })

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const params = { page: page.value, size: size.value }
    if (tab.value === 'pending') {
      const data = await makeupApi.pending(params)
      pendingRows.value = data?.records || []
      total.value = Number(data?.total || 0)
    } else {
      const data = await makeupApi.reviewed(params)
      reviewedRows.value = data?.records || []
      total.value = Number(data?.total || 0)
    }
  } catch {
    loadError.value = true
    pendingRows.value = []
    reviewedRows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function openReview(row, approved) {
  dialog.visible = true
  dialog.approved = approved
  dialog.row = row
  dialog.remark = ''
}

async function submitReview() {
  if (!dialog.approved && !dialog.remark.trim()) {
    ElMessage.warning('驳回时请填写原因')
    return
  }
  submitting.value = true
  try {
    await makeupApi.review(dialog.row.requestId, {
      approved: dialog.approved,
      remark: dialog.remark.trim() || undefined,
    })
    ElMessage.success(dialog.approved ? '已通过，考勤记录已生成' : '已驳回')
    dialog.visible = false
    await load()
  } catch {
    /* 错误提示由 http 拦截器统一处理 */
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.approval-page {
  display: grid;
  gap: 12px;
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
.scope-tip {
  color: var(--tp-text-faint);
}
.list-card {
  border-radius: 10px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.review-desc {
  margin-bottom: 12px;
}
.review-form {
  margin-top: 4px;
}

.load-error {
  margin-bottom: 10px;
  border-radius: 8px;
}
</style>
