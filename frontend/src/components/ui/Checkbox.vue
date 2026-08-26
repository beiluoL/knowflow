<template>
  <label
    :class="[
      'kb-checkbox',
      disabled ? 'kb-checkbox-disabled' : '',
      checked ? 'kb-checkbox-checked' : '',
    ]"
  >
    <span class="kb-checkbox-box" :class="checked ? 'kb-checkbox-box-checked' : ''">
      <input
        type="checkbox"
        class="kb-checkbox-input"
        :checked="checked"
        :indeterminate.prop="indeterminate"
        :disabled="disabled"
        @change="handleChange"
      />
      <Icon v-if="indeterminate" name="minus" :size="12" class="kb-checkbox-mark" />
      <Icon v-else-if="checked" name="check" :size="12" class="kb-checkbox-mark" />
    </span>
    <span v-if="label || $slots.default" class="kb-checkbox-label">
      <slot>{{ label }}</slot>
    </span>
  </label>
</template>

<script setup lang="ts">
// U6：通用复选框，封装 label/checked/disabled/indeterminate，v-model 双绑。
// 复用全局 .kb-checkbox 类，自定义方框 + 对勾/横线指示器，原生 input 负责表单提交与无障碍。
import { computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'

interface Props {
  modelValue?: boolean
  label?: string
  disabled?: boolean
  indeterminate?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  label: '',
  disabled: false,
  indeterminate: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  change: [value: boolean]
}>()

const checked = computed(() => props.modelValue)

const handleChange = (e: Event) => {
  const target = e.target as HTMLInputElement
  emit('update:modelValue', target.checked)
  emit('change', target.checked)
}
</script>
