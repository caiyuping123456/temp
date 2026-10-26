import { onBeforeUnmount, ref } from 'vue'
import { INDENT_UNIT } from '@/utils/tree'

/**
 * 行拖拽（原生 HTML5 Drag & Drop，事件委托挂在表格容器上）。
 *
 * 交互约定：
 * - 拖动左侧手柄开始拖拽，拖动到目标行的上半区 / 下半区决定插入到前 / 后；
 * - 拖拽过程中左右移动指针，按 INDENT_UNIT 换算目标层级（与 vxe-table 行拖拽一致）；
 * - 组件本身不修改数据，只把落点信息通过 onDrop 抛给上层。
 */
export function useRowDrag({ getRows, getDepth, onDrop }) {
  const draggingId = ref(null)
  const draggingDepth = ref(0)
  const startX = ref(0)
  const dropState = ref(null)

  const clearDragState = () => {
    draggingId.value = null
    dropState.value = null
    if (typeof document !== 'undefined') document.body.classList.remove('is-row-dragging')
  }

  const handleDragStart = (event, row) => {
    if (!row) return

    draggingId.value = row.id
    draggingDepth.value = getDepth(row)
    startX.value = event.clientX

    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move'
      event.dataTransfer.setData('text/plain', row.id)
      const tr = event.currentTarget.closest('tr')
      if (tr) event.dataTransfer.setDragImage(tr, 36, 20)
    }

    const tr = event.currentTarget.closest('tr')
    if (tr) tr.classList.add('row-dragging')
    document.body.classList.add('is-row-dragging')
  }

  const handleDragEnd = () => {
    clearDragState()
    document.querySelectorAll('.row-dragging').forEach((el) => el.classList.remove('row-dragging'))
  }

  const handleDragOver = (event, wrapper) => {
    if (!draggingId.value) return
    event.preventDefault()
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'

    const target = event.target?.closest?.('tr.el-table__row')
    if (!target || !wrapper?.contains(target)) {
      dropState.value = null
      return
    }

    const domRows = Array.from(wrapper.querySelectorAll('.el-table__body tr.el-table__row'))
    const index = domRows.indexOf(target)
    const row = getRows()[index]
    if (!row || row.id === draggingId.value) {
      dropState.value = null
      return
    }

    const rect = target.getBoundingClientRect()
    const wrapperRect = wrapper.getBoundingClientRect()
    const position = event.clientY < rect.top + rect.height / 2 ? 'before' : 'after'
    const shift = Math.round((event.clientX - startX.value) / INDENT_UNIT)
    const depth = Math.max(0, draggingDepth.value + shift)

    dropState.value = {
      targetId: row.id,
      position,
      depth,
      lineTop: (position === 'before' ? rect.top : rect.bottom) - wrapperRect.top,
      lineLeft: rect.left - wrapperRect.left + 10,
      lineWidth: Math.max(120, rect.width - 20),
      badgeLeft: event.clientX + 14,
      badgeTop: event.clientY + 16,
    }
  }

  const handleDrop = (event) => {
    if (!draggingId.value || !dropState.value) {
      clearDragState()
      return
    }
    event.preventDefault()
    const payload = {
      sourceId: draggingId.value,
      targetId: dropState.value.targetId,
      position: dropState.value.position,
      depth: dropState.value.depth,
    }
    clearDragState()
    onDrop?.(payload)
  }

  const handleDragLeave = (event, wrapper) => {
    if (!wrapper) return
    const related = event.relatedTarget
    if (related && wrapper.contains(related)) return

    // 拖拽过程中 dragleave 的 relatedTarget 常为 null，用坐标兜底判断是否真的离开容器
    const rect = wrapper.getBoundingClientRect()
    const { clientX, clientY } = event
    const inside =
      clientX >= rect.left && clientX <= rect.right && clientY >= rect.top && clientY <= rect.bottom
    if (!inside) dropState.value = null
  }

  onBeforeUnmount(clearDragState)

  return {
    draggingId,
    dropState,
    handleDragStart,
    handleDragEnd,
    handleDragOver,
    handleDrop,
    handleDragLeave,
    clearDragState,
  }
}
