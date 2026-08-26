<template>
  <div class="w-full">
    <label v-if="label" :for="textareaId" class="block text-sm font-medium text-foreground mb-1.5">
      {{ label }}
      <span v-if="required" class="text-danger-500 ml-0.5">*</span>
    </label>
    <textarea
      :id="textareaId"
      ref="textareaRef"
      :value="modelValue"
      :placeholder="placeholder"
      :rows="rows"
      :maxlength="maxlength"
      :disabled="disabled"
      :class="[
        'kb-textarea',
        autoResize ? 'kb-textarea-autoresize' : '',
        error ? 'kb-textarea-error' : '',
        disabled ? 'kb-textarea-disabled' : '',
      ]"
      :style="autoResize ? { height: 'auto', minHeight: minHeight + 'px' } : undefined"
      @input="handleInput"
      @blur="$emit('blur', $event)"
      @focus="$emit('focus', $event)"
    />
    <div v-if="error || (maxlength && showCount)" class="flex items-center justify-between mt-1.5">
      <p v-if="error" class="text-xs text-danger-500">{{ error }}</p>
      <span v-else />
      <span v-if="maxlength && showCount" class="text-xs text-muted-foreground tabular-nums">
        {{ count }}/{{ maxlength }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
// U6：通用文本域，封装 label/placeholder/rows/auto-resize/字数统计/错误态，v-model 双绑。
// 复用全局 .kb-textarea 类，与 Input.vue 视觉一致；autoResize 时根据内容自动撑高。
import { computed, nextTick, onMounted, ref, watch } from 'vue'

interface Props {
  modelValue?: string
  placeholder?: string
  label?: string
  rows?: number
  maxlength?: number
  showCount?: boolean
  required?: boolean
  disabled?: boolean
  error?: string
  autoResize?: boolean
  minHeight?: number
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '',
  label: '',
  rows: 4,
  showCount: false,
  required: false,
  disabled: false,
  autoResize: false,
  minHeight: 96,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  blur: [e: FocusEvent]
  focus: [e: FocusEvent]
}>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const textareaId = `textarea-${Math.random().toString(36).substring(2, 9)}`

const count = computed(() => (props.modelValue ? props.modelValue.length : 0))

const handleInput = (e: Event) => {
  const target = e.target as HTMLTextAreaElement
  emit('update:modelValue', target.value)
}

/** 自适应高度：重置高度后按 scrollHeight 撑开 */
const resize = () => {
  const el = textareaRef.value
  if (!el || !props.autoResize) return
  el.style.height = 'auto'
  el.style.height = el.scrollHeight + 'px'
}

watch(
  () => props.modelValue,
  () => {
    if (props.autoResize) nextTick(resize)
  },
)

onMounted(() => {
  if (props.autoResize) resize()
})
</script>
