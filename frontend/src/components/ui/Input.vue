<template>
  <div class="w-full">
    <label v-if="label" :for="inputId" class="block text-sm font-medium text-gray-700 mb-1.5">
      {{ label }}
      <span v-if="required" class="text-danger-500 ml-0.5">*</span>
    </label>
    <div class="relative">
      <Icon
        v-if="prefixIconName"
        :name="prefixIconName"
        :size="16"
        class="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none"
      />
      <input
        :id="inputId"
        :type="type"
        :placeholder="placeholder"
        :value="modelValue"
        :disabled="disabled"
        :class="[
          'w-full px-3 py-2 text-sm border rounded-sm focus:outline-none transition-all duration-200',
          prefixIconName ? 'pl-10' : '',
          suffixIconName ? 'pr-10' : '',
          error
            ? 'border-danger-300 focus:border-danger-500 focus:ring-2 focus:ring-danger-100'
            : 'border-gray-200 focus:border-primary-500 focus:ring-2 focus:ring-primary-100',
          disabled ? 'bg-gray-50 text-gray-400 cursor-not-allowed' : 'bg-white',
        ]"
        @input="handleInput"
        @blur="$emit('blur', $event)"
        @focus="$emit('focus', $event)"
      />
      <Icon
        v-if="suffixIconName"
        :name="suffixIconName"
        :size="16"
        class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none"
      />
    </div>
    <p v-if="error" class="mt-1.5 text-xs text-danger-500">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
// 通用 UI 组件：文本输入框，支持前后置图标、label/错误态，通过 v-model 双向绑定。
import Icon from '@/components/ui/Icon.vue'

interface Props {
  modelValue?: string
  type?: string
  placeholder?: string
  label?: string
  required?: boolean
  disabled?: boolean
  error?: string
  prefixIconName?: string
  suffixIconName?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  type: 'text',
  placeholder: '',
  required: false,
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  blur: [e: FocusEvent]
  focus: [e: FocusEvent]
}>()

const inputId = `input-${Math.random().toString(36).substring(2, 9)}`

const handleInput = (e: Event) => {
  const target = e.target as HTMLInputElement
  emit('update:modelValue', target.value)
}
</script>
