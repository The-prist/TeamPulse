// 文件用途：敏感词过滤工具
// 功能概览：
// - 评论/日志的敏感词替换由后端在写入时统一完成（SensitiveWordService.filter）
// - 这里只做展示侧兜底遮罩：词库默认用内置词表，管理端可调用 loadSensitiveWords() 拉取最新词库
// - 词库接口 /sensitive-word 仅管理员可读，因此不做模块加载时自动请求，避免普通成员被 403 弹窗打扰
import { ref } from 'vue'
import http from '@/api/http'

// 内置兜底词表：后端词库不可读时仍然能在页面上遮罩常见敏感词
const FALLBACK_WORDS = ['敏感词', '违禁词', '不当言论', '垃圾信息', '广告']

export const sensitiveWords = ref(
  FALLBACK_WORDS.map((word, index) => ({ word, status: 'active', wordId: index + 1, replacement: '***' }))
)

// 从后端拉取 active 词库（管理端调用；失败时保持内置词表不变）
export async function loadSensitiveWords() {
  try {
    const page = await http.get('/sensitive-word', { params: { page: 1, size: 200 }, silent: true })
    const records = Array.isArray(page?.records) ? page.records : []
    const parsed = records
      .filter((item) => item && item.word && String(item.word).trim() && item.status === 'active')
      .map((item) => ({
        word: String(item.word).trim(),
        status: item.status,
        wordId: item.wordId,
        replacement: item.replacement || '***'
      }))
    if (parsed.length > 0) {
      sensitiveWords.value = parsed
    }
    return sensitiveWords.value
  } catch (error) {
    console.warn('敏感词词库拉取失败，继续使用内置词表:', error?.message || error)
    return sensitiveWords.value
  }
}

// 过滤敏感词：默认替换为***；若 action=block 则返回屏蔽文案
export function filterSensitiveWords(content) {
  if (!content) return ''
  
  let result = content
  
  sensitiveWords.value.forEach(wordObj => {
    if (wordObj && wordObj.word && wordObj.status === 'active') {
      const word = wordObj.word
      const replacement = wordObj.replacement || '***'
      const action = wordObj.action || 'replace'
      
      if (action === 'replace') {
        // 全局替换，忽略大小写
        const regex = new RegExp(word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gi')
        result = result.replace(regex, replacement)
      } else if (action === 'block') {
        if (result.toLowerCase().includes(word.toLowerCase())) {
          return '[内容包含敏感词，已被屏蔽]'
        }
      }
    }
  })
  
  return result
}

// 检查是否包含敏感词：忽略大小写
export function containsSensitiveWords(content) {
  if (!content) return false
  
  const lowerContent = content.toLowerCase()
  
  return sensitiveWords.value.some(wordObj => {
    return wordObj && 
           wordObj.word && 
           wordObj.status === 'active' && 
           lowerContent.includes(wordObj.word.toLowerCase())
  })
}

// 获取内容中的敏感词：返回命中的词列表
export function findSensitiveWords(content) {
  if (!content) return []
  
  const lowerContent = content.toLowerCase()
  const foundWords = []
  
  sensitiveWords.value.forEach(wordObj => {
    if (wordObj && 
        wordObj.word && 
        wordObj.status === 'active' && 
        lowerContent.includes(wordObj.word.toLowerCase())) {
      foundWords.push(wordObj.word)
    }
  })
  
  return foundWords
}

