// 文件用途：统一表格工具（仅注释，不改逻辑）
// 能力点：分页/排序/导出CSV/列显隐；返回 pagedData 与排序钩子 onSortChange
import { ref, computed } from 'vue'

export function useTable(options = {}) {
  const data = ref([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(options.pageSize || 10)
  const sort = ref({ prop: '', order: '' }) // order: ascending/descending
  const visibleCols = ref(new Set(options.defaultVisible || []))

  const setData = (arr = [], totalCount) => {
    data.value = Array.isArray(arr) ? arr : []
    total.value = totalCount != null ? Number(totalCount) : data.value.length
  }

  const onSortChange = ({ prop, order }) => {
    sort.value = { prop, order }
  }

  const sortedData = computed(() => {
    const arr = data.value.slice()
    const { prop, order } = sort.value
    if (!prop || !order) return arr
    const factor = order === 'ascending' ? 1 : -1
    return arr.sort((a,b)=>{
      const va = a[prop]
      const vb = b[prop]
      if (va == null && vb == null) return 0
      if (va == null) return -1 * factor
      if (vb == null) return 1 * factor
      if (typeof va === 'number' && typeof vb === 'number') return (va - vb) * factor
      return String(va).localeCompare(String(vb)) * factor
    })
  })

  const pagedData = computed(() => {
    const start = (page.value - 1) * pageSize.value
    return sortedData.value.slice(start, start + pageSize.value)
  })

  const toggleColumn = (key) => {
    if (visibleCols.value.has(key)) visibleCols.value.delete(key)
    else visibleCols.value.add(key)
  }

  const isColVisible = (key) => visibleCols.value.size === 0 || visibleCols.value.has(key)

  const exportCSV = (filename = 'export.csv', cols = []) => {
    const rows = data.value
    if (!rows.length) return
    const headers = cols.length ? cols : Object.keys(rows[0])
    const lines = []
    lines.push(headers.join(','))
    for (const r of rows) {
      lines.push(headers.map(h => {
        const v = r[h]
        const s = v == null ? '' : String(v).replace(/"/g,'""')
        return '"' + s + '"'
      }).join(','))
    }
    const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  }

  return { data, setData, loading, total, page, pageSize, sort, onSortChange, pagedData, toggleColumn, isColVisible, exportCSV }
}
