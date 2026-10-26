<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Bottom,
  Calendar,
  Collection,
  Delete,
  InfoFilled,
  Plus,
  RefreshLeft,
  User,
} from '@element-plus/icons-vue'
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

const selectedCount = computed(() => selection.value.length)
const selectedIds = computed(() => selection.value.map((row) => row.id))

const quickSetIcons = {
  owner: User,
  dueDate: Calendar,
  standard: Collection,
}

const clearSelection = () => {
  tableRef.value?.clearSelection()
  selection.value = []
}

const ensureSelection = () => {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选需要操作的目录行')
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
  ElMessage.success('已新增一级目录，序号已重新生成')
}

const handleAddChild = () => {
  const ids = ensureSelection()
  if (!ids) return
  const created = addChildren(ids)
  clearSelection()
  ElMessage.success(`已在 ${ids.length} 行下新增子级，共新增 ${created.length} 条`)
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
  ElMessage.success(`已删除 ${ids.length} 条目录`)
}

const handleDeleteConfirm = (mode) => {
  const ids = [...deleteDialog.ids]
  const removed = removeRows(ids, mode)
  deleteDialog.visible = false
  clearSelection()
  ElMessage.success(
    mode === 'promote'
      ? `已删除 ${ids.length} 条目录，其子级已提升一级`
      : `已删除 ${removed} 条目录（含全部子级）`,
  )
}

const handleMove = ({ id, offset }) => {
  const result = moveSibling(id, offset)
  if (!result.ok) {
    ElMessage.warning(result.message)
    return
  }
  selection.value = []
}

const handleLevel = ({ id, delta }) => {
  const result = changeLevel(id, delta)
  if (!result.ok) {
    ElMessage.warning(result.message)
    return
  }
  selection.value = []
}

const handleDragDrop = (payload) => {
  const result = moveByDrag(payload)
  selection.value = []
  if (!result.ok) ElMessage.warning(result.message)
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
  ElMessage.success(`已将 ${quickSet.ids.length} 行的${quickSet.field.label}设为「${value}」`)
  clearSelection()
}

const handleReset = () => {
  ElMessageBox.confirm(
    '将清除浏览器中保存的数据，并恢复为示例目录。是否继续？',
    '恢复示例数据',
    {
      type: 'warning',
      confirmButtonText: '恢复',
      cancelButtonText: '取消',
    },
  )
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
        <span class="bar"></span>
        <h2>目录信息</h2>
        <span class="panel-sub">支持多选、层级调整、拖拽排序，序号自动生成</span>
      </div>
      <div class="panel-meta">
        <el-tag effect="plain" round type="info">共 {{ total }} 条</el-tag>
        <span class="selection-chip" :class="{ 'is-active': selectedCount > 0 }">
          已选 {{ selectedCount }} 条
        </span>
      </div>
    </div>

    <div class="toolbar">
      <el-button type="primary" :icon="Plus" @click="handleAddRoot">新增第一级</el-button>
      <el-button type="primary" plain :icon="Bottom" @click="handleAddChild">新增下级</el-button>
      <el-button plain :icon="Delete" @click="handleDelete">删除行</el-button>
      <el-divider direction="vertical" />
      <el-button
        v-for="field in QUICK_SET_FIELDS"
        :key="field.key"
        :icon="quickSetIcons[field.key]"
        @click="openQuickSet(field)"
      >
        {{ field.button }}
      </el-button>
      <span class="toolbar-spacer"></span>
      <el-button text type="info" :icon="RefreshLeft" @click="handleReset">恢复示例数据</el-button>
    </div>

    <p class="toolbar-hint">
      <el-icon><InfoFilled /></el-icon>
      “新增下级 / 删除行 / 批量设置”需先勾选行；删除含子级的目录时会询问「一并删除」或「子级提升一级」。
    </p>

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
      <span class="pager-tip">拖动左侧手柄可排序，拖拽时左右移动指针可调整层级</span>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="PAGE_SIZES"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
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
