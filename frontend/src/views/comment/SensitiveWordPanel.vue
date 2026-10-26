<!--
  敏感词管理面板。

  为什么单独抽成组件：评论浏览与敏感词维护是两件独立的事，
  放一个页面里用页签切换，比在评论表格上方堆一排输入框清楚得多。

  数据全部走后端 /api/v1/sensitive-word（增删改查 + 命中检测），
  提交后后端会立即让词库缓存失效，所以改完马上生效。
-->
<template>
  <el-card shadow="never" class="sw-card">
    <template #header>
      <div class="sw-header">
        <div>
          <span class="sw-title">敏感词库</span>
          <span class="sw-sub">评论入库时会自动把命中词替换为右侧的替换文本</span>
        </div>
        <div class="sw-actions">
          <el-input
            v-model="keyword"
            placeholder="搜索词条或替换文本"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-button @click="handleSearch">查询</el-button>
          <el-button type="primary" @click="openCreate">新增敏感词</el-button>
        </div>
      </div>
    </template>

    <el-alert type="info" :closable="false" show-icon class="sw-tip">
      <template #title>
        词库为全局生效：管理员修改后，新的评论立即按新规则过滤；已发布的评论不会被回溯改写。
      </template>
    </el-alert>

    <el-table :data="rows" v-loading="loading" size="small" empty-text="词库为空，先新增几个敏感词">
      <el-table-column prop="wordId" label="编号" width="80" />
      <el-table-column prop="word" label="敏感词" min-width="160">
        <template #default="{ row }">
          <span class="sw-word">{{ row.word }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="replacement" label="替换为" width="140">
        <template #default="{ row }">
          <code class="sw-code">{{ row.replacement || '***' }}</code>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
            {{ row.status === 'active' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">{{ formatTime(row.updatedAt || row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.status === 'active' ? 'warning' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 'active' ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="sw-pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="load"
        @current-change="load"
      />
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑敏感词' : '新增敏感词'" width="440px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="敏感词" prop="word">
          <el-input v-model="form.word" placeholder="如：违禁词" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="替换为" prop="replacement">
          <el-input v-model="form.replacement" placeholder="留空则替换为 ***" maxlength="100" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sensitiveWordApi } from '@/api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({ word: '', replacement: '' })

const rules = {
  word: [
    { required: true, message: '请输入敏感词', trigger: 'blur' },
    { max: 100, message: '不能超过 100 字', trigger: 'blur' },
  ],
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function load() {
  loading.value = true
  try {
    const data = await sensitiveWordApi.page({
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value,
    })
    rows.value = data?.records || []
    total.value = Number(data?.total || 0)
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  load()
}

function openCreate() {
  editingId.value = null
  form.word = ''
  form.replacement = ''
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.wordId
  form.word = row.word || ''
  form.replacement = row.replacement || ''
  dialogVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editingId.value) {
      await sensitiveWordApi.update(editingId.value, {
        word: form.word,
        replacement: form.replacement || '***',
      })
      ElMessage.success('已更新')
    } else {
      await sensitiveWordApi.create({ word: form.word, replacement: form.replacement || '***' })
      ElMessage.success('已新增')
    }
    dialogVisible.value = false
    load()
  } catch {
    /* 错误提示由 http 拦截器统一处理（重复词条后端返回 409） */
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row) {
  const next = row.status === 'active' ? 'inactive' : 'active'
  try {
    await sensitiveWordApi.update(row.wordId, { status: next })
    ElMessage.success(next === 'active' ? '已启用' : '已停用')
    load()
  } catch {
    /* 统一提示 */
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除敏感词「${row.word}」？删除后历史评论的替换结果不会回溯。`, '删除敏感词', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await sensitiveWordApi.remove(row.wordId)
    ElMessage.success('已删除')
    load()
  } catch {
    /* 统一提示 */
  }
}

onMounted(load)
</script>

<style scoped>
.sw-card {
  border-radius: 10px;
  border: 1px solid var(--tp-border);
}
.sw-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.sw-title {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}
.sw-sub {
  margin-left: 10px;
  font-size: 12px;
  color: #94a3b8;
}
.sw-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.sw-tip {
  margin-bottom: 12px;
}
.sw-word {
  font-weight: 600;
  color: #b91c1c;
}
.sw-code {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--tp-bg-subtle);
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}
.sw-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
