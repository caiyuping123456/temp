<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, Collection, Delete, Plus, RefreshLeft, User } from '@element-plus/icons-vue'
import DirectoryTable from '@/components/DirectoryTable.vue'
import DeleteConfirmDialog from '@/components/DeleteConfirmDialog.vue'
import QuickSetDialog from '@/components/QuickSetDialog.vue'
import { useDirectoryStore } from '@/composables/useDirectoryStore'
import { PAGE_SIZES, QUICK_SET_FIELDS } from '@/constants/directory'

const {
  page,
  pageSize,
  pageRows,
  total,
  parentIds,
  addRoot,
  addChildren,
  removeRows,
  moveSibling,
  changeLevel,
  moveByDrag,
  setField,
  resetDemo,
  hasChildren,
} = useDirectoryStore()

const tableRef = ref(null)
const selection = ref([])
const deleteDialog = reactive({ visible: false, ids: [], count: 0 })
const quickSet = reactive({ visible: false, field: null, ids: [] })

const selectedIds = computed(() => selection.value.map((row) => row.id))
const selectedCount = computed(() => selectedIds.value.length)
const quickSetIcons = { owner: User, dueDate: Calendar, standard: Collection }

const clearSelection = () => {
  tableRef.value?.clearSelection()
  selection.value = []
}

const ensureSelection = () => {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选目录')
    return null
  }
  return [...selectedIds.value]
}

const handleSelectionChange = (rows) => {
  selection.value = rows
}

const handleAddRoot = () => {
  addRoot()
  clearSelection()
  ElMessage.success('已新增一级目录')
}

const handleAddChild = () => {
  const ids = ensureSelection()
  if (!ids) return
  const created = addChildren(ids)
  clearSelection()
  ElMessage.success(`已新增 ${created.length} 条子级目录`)
}

const handleDelete = () => {
  const ids = ensureSelection()
  if (!ids) return

  if (ids.some((id) => hasChildren(id))) {
    Object.assign(deleteDialog, { visible: true, ids, count: ids.length })
    return
  }

  removeRows(ids, 'subtree')
  clearSelection()
  ElMessage.success('已删除所选目录')
}

const handleDeleteConfirm = (mode) => {
  const ids = [...deleteDialog.ids]
  const removed = removeRows(ids, mode)
  deleteDialog.visible = false
  clearSelection()
  ElMessage.success(mode === 'promote' ? '已删除目录并提升子级' : `已删除 ${removed} 条目录`)
}

const handleMove = ({ id, offset }) => {
  const result = moveSibling(id, offset)
  if (!result.ok) {
    ElMessage.warning(result.message)
    return
  }
  clearSelection()
}

const handleLevel = ({ id, delta }) => {
  const result = changeLevel(id, delta)
  if (!result.ok) {
    ElMessage.warning(result.message)
    return
  }
  clearSelection()
}

const handleDragDrop = (payload) => {
  const result = moveByDrag(payload)
  if (!result.ok) ElMessage.warning(result.message)
  clearSelection()
}

const openQuickSet = (field) => {
  const ids = ensureSelection()
  if (!ids) return
  quickSet.field = field
  quickSet.ids = ids
  quickSet.visible = true
}

const handleQuickSetConfirm = (value) => {
  if (!quickSet.field) return
  setField(quickSet.ids, quickSet.field.key, value)
  ElMessage.success('批量设置已保存')
  clearSelection()
}

const handleReset = () => {
  ElMessageBox.confirm('将清除当前浏览器中的修改，并恢复示例目录。', '恢复示例数据', {
    type: 'warning',
    confirmButtonText: '恢复',
    cancelButtonText: '取消',
  })
    .then(() => {
      resetDemo()
      clearSelection()
      ElMessage.success('已恢复示例数据')
    })
    .catch(() => {})
}
</script>

<template>
  <section class="panel">
    <div class="panel-head">
      <div class="panel-title">
        <h2>目录清单</h2>
        <span>共 {{ total }} 条</span>
        <span v-if="selectedCount" class="selection-count">已选 {{ selectedCount }} 条</span>
      </div>
      <div class="panel-actions">
        <el-button size="small" :icon="Plus" type="primary" @click="handleAddRoot">新增第一级</el-button>
        <el-button size="small" :icon="Plus" @click="handleAddChild">新增下级</el-button>
        <el-button size="small" :icon="Delete" @click="handleDelete">删除</el-button>
      </div>
    </div>

    <div class="toolbar">
      <span class="toolbar-label">批量设置</span>
      <el-button
        v-for="field in QUICK_SET_FIELDS"
        :key="field.key"
        size="small"
        :icon="quickSetIcons[field.key]"
        @click="openQuickSet(field)"
      >
        {{ field.label }}
      </el-button>
      <span class="toolbar-spacer"></span>
      <el-button size="small" text :icon="RefreshLeft" @click="handleReset">恢复示例数据</el-button>
    </div>

    <p class="table-tip">拖拽行首调整顺序；勾选行后可新增下级、删除或批量设置字段。</p>

    <DirectoryTable
      ref="tableRef"
      :rows="pageRows"
      :parent-ids="parentIds"
      @selection-change="handleSelectionChange"
      @move="handleMove"
      @level="handleLevel"
      @drag-drop="handleDragDrop"
    />

    <div class="pager-row">
      <span class="pager-note">修改内容会自动保存</span>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="PAGE_SIZES"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        small
      />
    </div>

    <DeleteConfirmDialog
      v-model="deleteDialog.visible"
      :count="deleteDialog.count"
      @confirm="handleDeleteConfirm"
    />

    <QuickSetDialog
      v-model="quickSet.visible"
      :field-label="quickSet.field?.label || ''"
      :placeholder="quickSet.field?.placeholder || '请输入内容'"
      :maxlength="quickSet.field?.maxlength || 60"
      :count="quickSet.ids.length"
      @confirm="handleQuickSetConfirm"
    />
  </section>
</template>
