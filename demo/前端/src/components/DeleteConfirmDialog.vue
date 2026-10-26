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
    width="480px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="delete-body">
      <el-icon class="delete-icon"><WarningFilled /></el-icon>
      <div>
        <p class="delete-title">所选 {{ count }} 条目录中包含下级目录</p>
        <p class="delete-desc">一并删除：所选目录及其全部下级都会被移除。</p>
        <p class="delete-desc">提升子级：仅删除所选目录，下级目录上移一级。</p>
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button @click="close('promote')">提升子级</el-button>
      <el-button type="danger" @click="close('subtree')">一并删除</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.delete-body {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.delete-icon {
  margin-top: 2px;
  color: #b7791f;
  font-size: 20px;
}

.delete-title {
  margin: 0 0 8px;
  color: #26364a;
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
}

.delete-desc {
  margin: 0 0 4px;
  color: #68788d;
  font-size: 12px;
  line-height: 20px;
}
</style>
