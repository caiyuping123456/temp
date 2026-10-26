<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  fieldLabel: { type: String, default: '' },
  placeholder: { type: String, default: '请输入内容' },
  maxlength: { type: Number, default: 60 },
  count: { type: Number, default: 0 },
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const value = ref('')

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) value.value = ''
  },
)

const submit = () => {
  const text = value.value.trim()
  if (!text) return
  emit('confirm', text)
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="`设置${fieldLabel}`"
    width="460px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="quick-tip">
      将写入已选中的
      <strong>{{ count }}</strong>
      行目录，原内容会被覆盖。
    </p>
    <el-input
      v-model="value"
      size="large"
      :placeholder="placeholder"
      :maxlength="maxlength"
      :aria-label="`设置${fieldLabel}`"
      show-word-limit
      clearable
      @keyup.enter="submit"
    />
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :disabled="!value.trim()" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.quick-tip {
  margin: 0 0 12px;
  color: #64708a;
  font-size: 13px;
  line-height: 21px;
}

.quick-tip strong {
  color: var(--brand);
}
</style>
