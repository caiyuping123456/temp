<script setup>
import { WarningFilled } from '@element-plus/icons-vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  count: { type: Number, default: 0 },
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const close = (mode) => {
  emit('confirm', mode)
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="删除目录"
    width="540px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="delete-body">
      <el-icon class="delete-icon"><WarningFilled /></el-icon>
      <div class="delete-text">
        <p class="delete-title">
          选中的 {{ count }} 条目录中存在子目录，请确认处理方式
        </p>
        <p class="delete-desc">
          选择「是」：删除所选目录及其全部子目录。
        </p>
        <p class="delete-desc">
          选择「否」：仅删除所选目录，其子目录整体提升一级，序号实时重算。
        </p>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="danger" plain @click="close('promote')">否，提升子级</el-button>
      <el-button type="danger" @click="close('subtree')">是，一并删除</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.delete-body {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 4px 2px 8px;
}

.delete-icon {
  margin-top: 2px;
  color: #e6a23c;
  font-size: 22px;
}

.delete-title {
  margin: 0 0 10px;
  color: #26324b;
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
}

.delete-desc {
  margin: 0 0 6px;
  color: #64708a;
  font-size: 13px;
  line-height: 21px;
}

.delete-desc:last-child {
  margin-bottom: 0;
}
</style>
