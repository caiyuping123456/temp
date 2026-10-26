<script setup>
import { nextTick, ref } from 'vue'
import { ArrowDown, ArrowUp, Bottom, CaretRight, Top } from '@element-plus/icons-vue'
import { EDITABLE_FIELDS } from '@/constants/directory'
import { INDENT_UNIT, getDepthFromNo } from '@/utils/tree'
import { uniqueRowsById } from '@/utils/selection'

const props = defineProps({
  rows: { type: Array, default: () => [] },
  parentIds: { type: Object, default: () => new Set() },
})

const emit = defineEmits(['selection-change', 'move', 'level', 'drag-drop'])

const tableRef = ref(null)
const editableFields = EDITABLE_FIELDS.filter((field) => field.key !== 'name')

const depthOf = (row) => getDepthFromNo(row.no)
const hasChild = (row) => props.parentIds?.has?.(row.id) ?? false

const getSelection = () => {
  const table = tableRef.value
  if (!table) return []
  const currentRows = table.getCheckboxRecords?.() || []
  const reservedRows = table.getCheckboxReserveRecords?.() || []
  return uniqueRowsById(currentRows, reservedRows)
}

const syncSelection = () => {
  nextTick(() => emit('selection-change', getSelection()))
}

const clearSelection = () => {
  tableRef.value?.clearCheckboxRow?.()
  tableRef.value?.clearCheckboxReserve?.()
  emit('selection-change', [])
}

const handleRowDragEnd = ({ oldRow, newRow, dragPos } = {}) => {
  if (!oldRow || !newRow || oldRow.id === newRow.id) return
  emit('drag-drop', {
    sourceId: oldRow.id,
    targetId: newRow.id,
    position: dragPos === 'before' ? 'before' : 'after',
    depth: depthOf(oldRow),
  })
}

defineExpose({ clearSelection, getSelection })
</script>

<template>
  <div class="table-shell">
    <vxe-table
      ref="tableRef"
      class="directory-table"
      :data="rows"
      :row-config="{ keyField: 'id', drag: true }"
      :row-drag-config="{ trigger: 'cell', showIcon: true, animation: true }"
      :checkbox-config="{ reserve: true, highlight: true }"
      :show-overflow="false"
      border
      empty-text="暂无目录"
      @checkbox-change="syncSelection"
      @checkbox-all="syncSelection"
      @row-dragend="handleRowDragEnd"
    >
      <vxe-column type="checkbox" width="42" align="center" />
      <vxe-column drag-sort width="42" align="center" />
      <vxe-column field="no" title="序号" width="82" align="center">
        <template #default="{ row }">
          <span class="no-text">{{ row.no }}</span>
        </template>
      </vxe-column>
      <vxe-column field="name" title="目录名称" min-width="280">
        <template #default="{ row }">
          <div class="name-cell" :style="{ paddingLeft: `${depthOf(row) * INDENT_UNIT}px` }">
            <el-icon v-if="hasChild(row)" class="name-caret"><CaretRight /></el-icon>
            <span v-else class="name-caret is-placeholder"></span>
            <el-input
              v-model="row.name"
              class="cell-input name-input"
              size="small"
              placeholder="请输入目录名称"
              maxlength="60"
              :aria-label="`目录名称，序号 ${row.no}`"
            />
          </div>
        </template>
      </vxe-column>
      <vxe-column
        v-for="field in editableFields"
        :key="field.key"
        :field="field.key"
        :title="field.label"
        :width="field.width"
        :min-width="field.minWidth"
      >
        <template #default="{ row }">
          <el-input
            v-model="row[field.key]"
            class="cell-input"
            size="small"
            :placeholder="field.placeholder || '—'"
            maxlength="120"
            :aria-label="`${field.label}，序号 ${row.no}`"
          />
        </template>
      </vxe-column>
      <vxe-column title="操作" width="132" align="center" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <button
              type="button"
              class="op-btn"
              title="上移一行"
              @click="emit('move', { id: row.id, offset: -1 })"
            >
              <el-icon><ArrowUp /></el-icon>
            </button>
            <button
              type="button"
              class="op-btn"
              title="下移一行"
              @click="emit('move', { id: row.id, offset: 1 })"
            >
              <el-icon><ArrowDown /></el-icon>
            </button>
            <button
              type="button"
              class="op-btn"
              title="向上移一级"
              @click="emit('level', { id: row.id, delta: -1 })"
            >
              <el-icon><Top /></el-icon>
            </button>
            <button
              type="button"
              class="op-btn"
              title="向下移一级"
              @click="emit('level', { id: row.id, delta: 1 })"
            >
              <el-icon><Bottom /></el-icon>
            </button>
          </div>
        </template>
      </vxe-column>
    </vxe-table>
  </div>
</template>
