import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  addChildren,
  addRoot,
  findRow,
  findRowIndex,
  hasChildren,
  countDescendants,
  moveByDrag,
  moveSibling,
  changeLevel,
  normalizeRows,
  removeRows,
  renumber,
} from '@/utils/tree'
import { clearSnapshot, loadSnapshot, saveSnapshot } from '@/utils/storage'
import { createSeedRows, DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/constants/directory'

/**
 * 目录表格的数据仓库：
 * - 负责初始化 / 持久化（localStorage，一维数组）
 * - 暴露所有结构操作（增、删、同级移动、层级调整、拖拽）
 * - 结构变化后统一由 utils/tree 重算序号
 */
export function useDirectoryStore() {
  const snapshot = loadSnapshot()
  const initialRows = snapshot?.rows?.length ? normalizeRows(snapshot.rows) : createSeedRows()

  const rows = ref(renumber(initialRows))
  const pageSize = ref(PAGE_SIZES.includes(snapshot?.pageSize) ? snapshot.pageSize : DEFAULT_PAGE_SIZE)

  const total = computed(() => rows.value.length)
  const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
  const initialPage = Math.min(Math.max(1, Number(snapshot?.page) || 1), pageCount.value)
  const page = ref(initialPage)

  const pageRows = computed(() => {
    const start = (page.value - 1) * pageSize.value
    return rows.value.slice(start, start + pageSize.value)
  })

  /** 所有存在子级的节点 id，用于表格里的「有下级」箭头。 */
  const parentIds = computed(() => {
    const ids = new Set()
    rows.value.forEach((row) => {
      if (row.parentId) ids.add(row.parentId)
    })
    return ids
  })

  let saveTimer = null

  const scheduleSave = () => {
    if (saveTimer) window.clearTimeout(saveTimer)
    saveTimer = window.setTimeout(() => {
      saveTimer = null
      saveSnapshot({ rows: rows.value, page: page.value, pageSize: pageSize.value })
    }, 180)
  }

  const flushSave = () => {
    if (saveTimer) {
      window.clearTimeout(saveTimer)
      saveTimer = null
    }
    saveSnapshot({ rows: rows.value, page: page.value, pageSize: pageSize.value })
  }

  watch([rows, page, pageSize], scheduleSave, { deep: true })

  watch([total, pageSize], () => {
    if (page.value > pageCount.value) page.value = pageCount.value
  })

  watch(page, (value) => {
    if (value < 1) page.value = 1
    else if (value > pageCount.value) page.value = pageCount.value
  })

  let listenersBound = false
  onMounted(() => {
    if (typeof window === 'undefined') return
    window.addEventListener('beforeunload', flushSave)
    listenersBound = true
  })
  onBeforeUnmount(() => {
    if (listenersBound) window.removeEventListener('beforeunload', flushSave)
    flushSave()
  })

  /** 定位到包含指定行的分页。 */
  const revealRow = (id) => {
    const index = findRowIndex(rows.value, id)
    if (index >= 0) page.value = Math.floor(index / pageSize.value) + 1
  }

  /* --------------------------- 结构操作 --------------------------- */

  const handleAddRoot = () => {
    const result = addRoot(rows.value)
    rows.value = result.rows
    revealRow(result.id)
    return result.id
  }

  const handleAddChildren = (ids) => {
    const result = addChildren(rows.value, ids)
    if (!result.ids.length) return []
    rows.value = result.rows
    revealRow(result.ids[0])
    return result.ids
  }

  const handleRemoveRows = (ids, mode = 'subtree') => {
    const before = rows.value.length
    rows.value = removeRows(rows.value, ids, mode)
    return before - rows.value.length
  }

  const handleMoveSibling = (id, offset) => {
    const result = moveSibling(rows.value, id, offset)
    if (!result.ok) return result
    rows.value = result.rows
    return result
  }

  const handleChangeLevel = (id, delta) => {
    const result = changeLevel(rows.value, id, delta)
    if (!result.ok) return result
    rows.value = result.rows
    return result
  }

  const handleDragDrop = (payload) => {
    const result = moveByDrag(rows.value, payload)
    if (!result.ok) return result
    rows.value = result.rows
    return result
  }

  /** 批量写入某列，例如给选中行统一设置负责人。 */
  const handleSetField = (ids, key, value) => {
    const targets = new Set(ids)
    rows.value = rows.value.map((row) => (targets.has(row.id) ? { ...row, [key]: value } : row))
  }

  const resetDemo = () => {
    clearSnapshot()
    rows.value = createSeedRows()
    page.value = 1
    scheduleSave()
  }

  return {
    rows,
    page,
    pageSize,
    total,
    pageCount,
    pageRows,
    parentIds,
    addRoot: handleAddRoot,
    addChildren: handleAddChildren,
    removeRows: handleRemoveRows,
    moveSibling: handleMoveSibling,
    changeLevel: handleChangeLevel,
    moveByDrag: handleDragDrop,
    setField: handleSetField,
    resetDemo,
    flushSave,
    revealRow,
    hasChildren: (id) => hasChildren(rows.value, id),
    countDescendants: (id) => countDescendants(rows.value, id),
    findRow: (id) => findRow(rows.value, id),
  }
}
