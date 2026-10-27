<!--
  打卡设备管理。
  密钥可见性规则：只有「新增成功」和「轮换成功」两个时机返回明文密钥，
  表格里永远不展示 secret —— 这是后端强制的，前端也不做二次缓存。
-->
<template>
  <div class="device-page">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-input
            v-model="query.keyword"
            placeholder="搜索序列号 / 名称 / 位置"
            clearable
            style="width: 240px"
            @keyup.enter="handleSearch"
          />
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="在线" value="ONLINE" />
            <el-option label="离线" value="OFFLINE" />
            <el-option label="已禁用" value="DISABLED" />
          </el-select>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </div>
        <div class="actions">
          <el-tag type="success" effect="plain">在线 {{ statistics.online || 0 }}</el-tag>
          <el-tag effect="plain">共 {{ statistics.total || 0 }} 台</el-tag>
          <el-button type="primary" plain @click="openCreate" v-if="auth.hasPerm('device:manage')">注册设备</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
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
      <el-table v-loading="loading" :data="rows" size="small" empty-text="还没有注册任何打卡设备">
        <el-table-column prop="deviceSn" label="设备序列号" min-width="160" />
        <el-table-column prop="deviceName" label="设备名称" min-width="150" />
        <el-table-column prop="location" label="安装位置" min-width="130" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="deviceStatusType(row.status)">{{ deviceStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="固件" width="100">
          <template #default="{ row }">{{ row.firmwareVersion || '—' }}</template>
        </el-table-column>
        <el-table-column label="最近心跳" width="170">
          <template #default="{ row }">{{ formatDateTime(row.lastHeartbeat) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-if="auth.hasPerm('device:manage')" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="handleRotate(row)" v-if="auth.hasPerm('device:manage')">重置密钥</el-button>
            <el-button link type="danger" @click="handleDelete(row)" v-if="auth.hasPerm('device:manage')">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>

    <!-- 注册 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑设备' : '注册设备'" width="460px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item v-if="!editingId" label="序列号" prop="deviceSn">
          <el-input v-model="form.deviceSn" placeholder="如 TP-ESP32-0001" />
        </el-form-item>
        <el-form-item label="设备名称" prop="deviceName">
          <el-input v-model="form.deviceName" placeholder="如 一楼大厅打卡机" />
        </el-form-item>
        <el-form-item label="安装位置">
          <el-input v-model="form.location" placeholder="如 一楼大厅" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 密钥展示（仅一次） -->
    <el-dialog v-model="secretVisible" title="设备密钥（仅显示一次）" width="520px" :close-on-click-modal="false">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="请立即把序列号与密钥写入设备 NVS，关闭本窗口后将无法再次查看"
        class="secret-alert"
      />
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="设备序列号">
          <code class="mono">{{ secretInfo.deviceSn }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="设备密钥">
          <code class="mono">{{ secretInfo.secret }}</code>
        </el-descriptions-item>
      </el-descriptions>
      <div class="secret-tip">
        把这两项填入 <code>device/simulator/config.json</code> 即可用模拟器联调；
        真实设备请写入固件配置区。
      </div>
      <template #footer>
        <el-button type="primary" @click="secretVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore()
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deviceApi } from '@/api'
import { deviceStatusText, deviceStatusType, formatDateTime } from '@/utils/attendance'

const loading = ref(false)
// 加载失败与「没有数据」是两件事，用独立标志驱动告警与重试
const loadError = ref(false)
const rows = ref([])
const total = ref(0)
const statistics = ref({ total: 0, online: 0 })

const query = reactive({ keyword: '', status: '', page: 1, size: 10 })

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({ deviceSn: '', deviceName: '', location: '', remark: '' })
const formRules = {
  deviceSn: [
    { required: true, message: '请输入设备序列号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]{4,64}$/, message: '只能包含字母数字下划线短横线，长度 4~64', trigger: 'blur' },
  ],
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
}

const secretVisible = ref(false)
const secretInfo = reactive({ deviceSn: '', secret: '' })

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const params = { ...query }
    if (!params.keyword) delete params.keyword
    if (!params.status) delete params.status
    const data = await deviceApi.page(params)
    rows.value = data?.records || []
    total.value = Number(data?.total || 0)
    statistics.value = await deviceApi.statistics()
  } catch {
    loadError.value = true
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  load()
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { deviceSn: '', deviceName: '', location: '', remark: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    deviceSn: row.deviceSn,
    deviceName: row.deviceName || '',
    location: row.location || '',
    remark: row.remark || '',
  })
  dialogVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editingId.value) {
      await deviceApi.update(editingId.value, {
        deviceName: form.deviceName,
        location: form.location,
        remark: form.remark,
      })
      ElMessage.success('设备已更新')
    } else {
      const data = await deviceApi.create({
        deviceSn: form.deviceSn,
        deviceName: form.deviceName,
        location: form.location,
        remark: form.remark,
      })
      secretInfo.deviceSn = data?.device?.deviceSn || form.deviceSn
      secretInfo.secret = data?.secret || ''
      secretVisible.value = true
    }
    dialogVisible.value = false
    load()
  } catch {
    /* 提示已统一处理 */
  } finally {
    submitting.value = false
  }
}

async function handleRotate(row) {
  try {
    await ElMessageBox.confirm(
      `重置后设备「${row.deviceName || row.deviceSn}」的旧密钥立即失效，需要同步更新设备端配置。确定继续？`,
      '重置密钥',
      { type: 'warning', confirmButtonText: '确定重置', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const data = await deviceApi.rotateSecret(row.id)
    secretInfo.deviceSn = row.deviceSn
    secretInfo.secret = data?.secret || ''
    secretVisible.value = true
  } catch {
    /* 提示已统一处理 */
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除设备「${row.deviceName || row.deviceSn}」？`, '删除设备', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deviceApi.remove(row.id)
    ElMessage.success('已删除')
    load()
  } catch {
    /* 提示已统一处理 */
  }
}

onMounted(load)
</script>

<style scoped>
.device-page {
  display: grid;
  gap: 14px;
}
.toolbar-card,
.table-card {
  border-radius: 10px;
  border: 1px solid var(--tp-border);
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.mono {
  font-family: 'JetBrains Mono', Consolas, Monaco, monospace;
  font-size: 12.5px;
  background: var(--tp-bg-subtle);
  padding: 2px 6px;
  border-radius: 4px;
  word-break: break-all;
}
.secret-alert {
  margin-bottom: 14px;
}
.secret-tip {
  margin-top: 12px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.7;
}
.secret-tip code {
  background: var(--tp-bg-subtle);
  padding: 1px 5px;
  border-radius: 4px;
}

.load-error {
  margin-bottom: 10px;
  border-radius: 8px;
}
</style>
