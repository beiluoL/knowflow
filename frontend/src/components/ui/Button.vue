<template>
  <button
    :type="type"
    :class="[
      'inline-flex items-center justify-center font-medium rounded-sm transition-colors duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed',
      sizeClass,
      variantClass,
      { 'w-full': block },
    ]"
    :disabled="disabled || loading"
    @click="$emit('click', $event)"
  >
    <svg
      v-if="loading"
      class="animate-spin -ml-1 mr-2 h-4 w-4"
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
    >
      <circle
        class="opacity-25"
        cx="12"
        cy="12"
        r="10"
        stroke="currentColor"
        stroke-width="4"
      />
      <path
        class="opacity-75"
        fill="currentColor"
        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
      />
    </svg>
    <Icon v-if="iconName && !loading" :name="iconName" :size="16" class="mr-2" />
    <slot />
  </button>
</template>

<script setup lang="ts">
// 通用 UI 组件：按钮，支持 variant/size/loading/block 等形态与图标。
import { computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'

interface Props {
  variant?: 'primary' | 'secondary' | 'text' | 'ghost'
  size?: 'sm' | 'md' | 'lg'
  type?: 'button' | 'submit' | 'reset'
  disabled?: boolean
  loading?: boolean
  block?: boolean
  iconName?: string
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'primary',
  size: 'md',
  type: 'button',
  disabled: false,
  loading: false,
  block: false,
})

defineEmits<{
  click: [e: MouseEvent]
}>()

const sizeClass = computed(() => {
  const sizes = {
    sm: 'px-3 py-1.5 text-sm',
    md: 'px-4 py-2 text-sm',
    lg: 'px-6 py-3 text-base',
  }
  return sizes[props.size]
})

const variantClass = computed(() => {
  const variants = {
    primary: 'bg-primary-500 text-white hover:bg-primary-600 active:bg-primary-700 shadow-sm',
    secondary:
      'bg-white text-gray-700 border border-gray-200 hover:bg-gray-50 active:bg-gray-100 shadow-sm',
    text: 'text-primary-500 hover:bg-primary-50 active:bg-primary-100',
    ghost: 'bg-transparent text-primary-500 border border-primary-500 hover:bg-primary-50 active:bg-primary-100',
  }
  return variants[props.variant]
})
</script>
