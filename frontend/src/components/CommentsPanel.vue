<!--
  评论面板组件：
  - 展示与指定业务对象（mode: 'worklog' | 'schedule'）相关的评论树（含子回复）
  - 支持新增评论、针对评论的回复、删除评论
  - 关键接口：
      GET    /comment/tree?relatedType=&relatedId=  加载两级评论树
      POST   /comment                               新增评论/回复
      DELETE /comment/{id}                          删除评论
  - 注：评论人一律由后端从 JWT 取，前端不再传 creatorId
-->
<template>
  <div class="comments">
    <div class="comments-header">
      <span>评论</span>
      <el-tag size="small">{{ totalCommentCount }}</el-tag>
    </div>
    <el-empty v-if="!loading && list.length===0" description="暂无评论" />
    <el-skeleton v-else-if="loading" :rows="3" animated />
    <div v-else class="comments-list">
      <div v-for="comment in list" :key="comment.commentId" class="comment-item">
        <el-card class="comment-card">
          <div class="comment-header">
            <div class="comment-meta">
              <span class="comment-author">{{ comment.nickname || comment.creatorId }}</span>
              <span class="comment-time">{{ formatTime(comment.createTime) }}</span>
            </div>
            <div class="comment-actions">
              <el-button 
                type="text" 
                size="small" 
                @click="showReplyForm(comment.commentId)"
                :icon="ChatDotRound"
              >
                回复
              </el-button>
              <el-popconfirm 
                title="确认删除这条评论吗？" 
                @confirm="deleteComment(comment.commentId)"
              >
                <template #reference>
                  <el-button 
                    type="text" 
                    size="small" 
                    style="color: #f56c6c;"
                    :loading="deleting === comment.commentId"
                  >
                    删除
                  </el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>
          <div class="comment-content">{{ filterSensitiveWords(comment.content) }}</div>
          
          <!-- 回复表单 -->
          <div v-if="replyingTo === comment.commentId" class="reply-form">
            <el-input
              v-model="replyContent"
              type="textarea"
              :rows="2"
              :placeholder="`回复 ${comment.nickname || comment.creatorId}...`"
            />
            <div class="reply-actions">
              <el-button size="small" @click="cancelReply">取消</el-button>
              <el-button 
                type="primary" 
                size="small" 
                :loading="posting" 
                @click="submitReply(comment.commentId)"
              >
                回复
              </el-button>
            </div>
          </div>
          
          <!-- 子回复列表 -->
          <div v-if="comment.replies && comment.replies.length > 0" class="replies-list">
            <div v-for="reply in comment.replies" :key="reply.commentId" class="reply-item">
              <div class="reply-header">
                <div class="reply-meta">
                  <span class="reply-author">{{ reply.nickname || reply.creatorId }}</span>
                  <span class="reply-time">{{ formatTime(reply.createTime) }}</span>
                </div>
                <div class="reply-actions">
                  <el-popconfirm 
                    title="确认删除这条回复吗？" 
                    @confirm="deleteComment(reply.commentId)"
                  >
                    <template #reference>
                      <el-button 
                        type="text" 
                        size="small" 
                        style="color: #f56c6c; font-size: 12px;"
                        :loading="deleting === reply.commentId"
                      >
                        删除
                      </el-button>
                    </template>
                  </el-popconfirm>
                </div>
              </div>
              <div class="reply-content">{{ filterSensitiveWords(reply.content) }}</div>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 新建评论表单 -->
    <div class="composer">
      <el-input
        v-model="content"
        type="textarea"
        :rows="3"
        :placeholder="mode === 'schedule' ? '对此日程发表评论...' : '写下你的评论...'"
      />
      <div style="text-align:right;margin-top:8px">
        <el-button type="primary" size="small" :loading="posting" @click="submit">发表评论</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
// 组件脚本：定义属性、状态与方法
import { ref, watch, onMounted, computed } from 'vue'
import { filterSensitiveWords } from '@/utils/sensitive'
import { commentApi } from '@/api'
import { ElMessage } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'

// 组件入参：
// - mode：评论关联的业务类型
// - relatedId：关联业务主键
const props = defineProps({
  mode: { type: String, default: 'worklog' }, // 'worklog' | 'schedule'
  relatedId: { type: [String, Number], required: true }
})

const list = ref([])
const loading = ref(false)
const content = ref('')
const posting = ref(false)
const replyingTo = ref(null)
const replyContent = ref('')
const deleting = ref(null)

// 计算总评论数（包括回复）
const totalCommentCount = computed(() => {
  let count = list.value.length
  list.value.forEach(comment => {
    if (comment.replies && comment.replies.length > 0) {
      count += comment.replies.length
    }
  })
  return count
})

function formatTime(t){ if(!t) return ''; return new Date(t).toLocaleString('zh-CN') }

// 新接口返回的是 CommentVO（子回复在 children、昵称在 creatorName），
// 这里统一映射成模板沿用的 nickname / replies 字段，避免改动整块模板
function normalizeComment(c) {
  return {
    ...c,
    nickname: c.creatorName || c.nickname,
    replies: (c.children || []).map(normalizeComment)
  }
}

// 加载评论树
async function load(){
  loading.value = true
  try {
    // GET /comment/tree?relatedType=&relatedId= 一次返回两级评论
    const data = await commentApi.tree({
      relatedType: props.mode,
      relatedId: Number(props.relatedId)
    })
    list.value = (Array.isArray(data) ? data : []).map(normalizeComment)
  } catch(e) {
    ElMessage.error('评论加载失败：' + (e.message||''))
  } finally {
    loading.value = false
  }
}

// 发表新评论
async function submit(){
  if (!content.value.trim()) return ElMessage.warning('请输入内容')
  posting.value = true
  try {
    // POST /comment：relatedType + relatedId + content，评论人由后端从 JWT 取
    await commentApi.add({
      content: content.value,
      relatedType: props.mode,
      relatedId: Number(props.relatedId)
    })
    content.value = ''
    ElMessage.success('已发表')
    await load()
  } catch(e) {
    ElMessage.error('发表评论失败：' + (e.message||''))
  } finally {
    posting.value = false
  }
}

// 显示回复表单
function showReplyForm(commentId) {
  replyingTo.value = commentId
  replyContent.value = ''
}

// 取消回复
function cancelReply() {
  replyingTo.value = null
  replyContent.value = ''
}

// 提交回复
async function submitReply(parentCommentId) {
  if (!replyContent.value.trim()) return ElMessage.warning('请输入回复内容')
  posting.value = true
  try {
    // 回复复用同一个新增接口，replyTo 指向父评论
    await commentApi.add({
      content: replyContent.value,
      relatedType: props.mode,
      relatedId: Number(props.relatedId),
      replyTo: parentCommentId
    })
    cancelReply()
    ElMessage.success('回复成功')
    await load()
  } catch(e) {
    ElMessage.error('回复失败：' + (e.message||''))
  } finally {
    posting.value = false
  }
}

// 删除评论
async function deleteComment(commentId) {
  deleting.value = commentId
  try {
    // DELETE /comment/{id}
    await commentApi.remove(commentId)
    ElMessage.success('删除成功')
    await load()
  } catch(e) {
    ElMessage.error('删除失败：' + (e.message||''))
  } finally {
    deleting.value = null
  }
}

// 监听入参变化并初始化加载
watch(() => [props.mode, props.relatedId], load)
onMounted(load)
</script>

<style scoped>
.comments-header { 
  display: flex; 
  align-items: center; 
  gap: 8px; 
  margin-bottom: 16px; 
}

.comments-list {
  margin-bottom: 16px;
}

.comment-item {
  margin-bottom: 16px;
}

.comment-card {
  border-radius: 8px;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.comment-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.comment-author {
  font-weight: 500;
  color: #303133;
}

.comment-time {
  color: #909399;
  font-size: 12px;
}

.comment-actions {
  display: flex;
  gap: 8px;
}

.comment-content {
  white-space: pre-wrap;
  line-height: 1.6;
  color: #606266;
  margin-bottom: 12px;
}

.reply-form {
  margin-top: 12px;
  padding: 12px;
  background-color: #f8f9fa;
  border-radius: 6px;
}

.reply-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.replies-list {
  margin-top: 12px;
  padding-left: 16px;
  border-left: 2px solid #e4e7ed;
}

.reply-item {
  padding: 8px 0;
  border-bottom: 1px solid #f0f2f5;
}

.reply-item:last-child {
  border-bottom: none;
}

.reply-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.reply-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.reply-actions {
  display: flex;
  gap: 4px;
}

.reply-author {
  font-weight: 500;
  color: #409eff;
  font-size: 13px;
}

.reply-time {
  color: #909399;
  font-size: 11px;
}

.reply-content {
  white-space: pre-wrap;
  line-height: 1.5;
  color: #606266;
  font-size: 13px;
}

.composer { 
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e4e7ed;
}
</style>
