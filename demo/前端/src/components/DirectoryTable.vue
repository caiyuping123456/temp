<script setup>
import { ref } from 'vue'
import { ArrowDown, ArrowUp, Bottom, CaretRight, Rank, Top } from '@element-plus/icons-vue'
import { EDITABLE_FIELDS } from '@/constants/directory'
import { INDENT_UNIT, getDepthFromNo } from '@/utils/tree'
import { useRowDrag } from '@/composables/useRowDrag'

const props = defineProps({
  /** 当前页的一维先序数据（与 store.rows 中的对象是同一引用，直接编辑即可持久化）。 */
  rows: { type: Array, default: () => [] },
  /** 所有存在子级的节点 id 集合，用于显示「有下级」箭头。 */
  parentIds: { type: Object, default: () => new Set() },
})

const emit = defineEmits(['selection-change', 'move', 'level', 'drag-drop'])

const tableRef = ref(null)
const wrapperRef = ref(null)

const editableFields = EDITABLE_FIELDS.filter((field) => field.key !== 'name')

const depthOf = (row) => getDepthFromNo(row.no)
const hasChild = (row) => props.parentIds?.has?.(row.id) ?? false

const { dropState, handleDragStart, handleDragEnd, handleDragOver, handleDrop, handleDragLeave } =
  useRowDrag({
    getRows: () => props.rows,
    getDepth: depthOf,
    onDrop: (payload) => emit('drag-drop', payload),
  })

const onDragOver = (event) => handleDragOver(event, wrapperRef.value)
const onDragLeave = (event) => handleDragLeave(event, wrapperRef.value)

const onSelectionChange = (rows) => emit('selection-change', rows)

defineExpose({
  clearSelection: () => tableRef.value?.clearSelection(),
})
</script>

<template>
  <div
    ref="wrapperRef"
    class="table-shell"
    @dragover="onDragOver"
    @drop="handleDrop"
    @dragleave="onDragLeave"
  >
    <el-table
      ref="tableRef"
      :data="rows"
      row-key="id"
      class="dir-table"
      empty-text="暂无目录，点击「新增第一级」开始"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="46" align="center" />

      <el-table-column label="操作" width="150" align="center" class-name="col-actions">
        <template #default="{ row }">
          <div class="row-actions">
            <span
              class="drag-handle"
              draggable="true"
              title="拖拽排序，左右拖动可调整层级"
              @dragstart="handleDragStart($event, row)"
              @dragend="handleDragEnd"
            >
              <el-icon><Rank /></el-icon>
            </span>
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
              title="向上移一级（减少缩进）"
              @click="emit('level', { id: row.id, delta: -1 })"
            >
              <el-icon><Top /></el-icon>
            </button>
            <button
              type="button"
              class="op-btn"
              title="向下移一级（增加缩进）"
              @click="emit('level', { id: row.id, delta: 1 })"
            >
              <el-icon><Bottom /></el-icon>
            </button>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="序号" width="82" align="center" class-name="col-no">
        <template #default="{ row }">
          <span class="no-text">{{ row.no }}</span>
        </template>
      </el-table-column>

      <el-table-column label="目录名称" min-width="300">
        <template #default="{ row }">
          <div class="name-cell" :style="{ paddingLeft: `${depthOf(row) * INDENT_UNIT}px` }">
            <el-icon v-if="hasChild(row)" class="name-caret"><CaretRight /></el-icon>
            <span v-else class="name-caret is-placeholder"></span>
            <el-input
              v-model="row.name"
              class="cell-input name-input"
              placeholder="请输入目录名称"
              maxlength="60"
              :aria-label="`目录名称，序号 ${row.no}`"
            />
          </div>
        </template>
      </el-table-column>

      <el-table-column
        v-for="field in editableFields"
        :key="field.key"
        :label="field.label"
        :width="field.width"
        :min-width="field.minWidth"
      >
        <template #default="{ row }">
          <el-input
            v-model="row[field.key]"
            class="cell-input"
            :placeholder="field.placeholder || '—'"
            maxlength="120"
            :aria-label="`${field.label}，序号 ${row.no}`"
          />
        </template>
      </el-table-column>
    </el-table>

    <div
      v-if="dropState"
      class="drop-line"
      :style="{
        top: `${dropState.lineTop}px`,
        left: `${dropState.lineLeft}px`,
        width: `${dropState.lineWidth}px`,
      }"
    ></div>

    <div
      v-if="dropState"
      class="drag-badge"
      :style="{ left: `${dropState.badgeLeft}px`, top: `${dropState.badgeTop}px` }"
    >
      层级 {{ dropState.depth + 1 }}
    </div>
  </div>
</template>

<style scoped>
.table-shell {
  position: relative;
}

.row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  color: #9aa8c2;
  cursor: grab;
  transition: color 0.15s ease, background-color 0.15s ease;
}

.drag-handle:hover {
  color: var(--brand);
  background: var(--brand-soft);
}

.drag-handle:active {
  cursor: grabbing;
}

.op-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #7c8aa8;
  font-size: 13px;
  cursor: pointer;
  transition: color 0.15s ease, background-color 0.15s ease;
}

.op-btn:hover {
  color: var(--brand);
  background: var(--brand-soft);
}

.no-text {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: #3b4a68;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.name-caret {
  flex: 0 0 auto;
  color: #8fa4c8;
  font-size: 12px;
}

.name-caret.is-placeholder {
  display: inline-block;
  width: 12px;
  height: 12px;
}

.name-input {
  flex: 1;
  min-width: 0;
}

.drop-line {
  position: absolute;
  z-index: 20;
  height: 2px;
  border-radius: 2px;
  background: var(--brand);
  box-shadow: 0 0 0 3px rgba(47, 107, 255, 0.12);
  pointer-events: none;
}

.drop-line::before {
  content: '';
  position: absolute;
  top: -3px;
  left: -4px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--brand);
}

.drag-badge {
  position: fixed;
  z-index: 3000;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  line-height: 18px;
  box-shadow: 0 6px 16px rgba(47, 107, 255, 0.35);
  pointer-events: none;
}
</style>
