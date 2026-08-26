<template>
  <span
    :class="[
      'inline-flex items-center px-2.5 py-0.5 text-xs font-medium rounded-full',
      variantClass,
    ]"
  >
    <slot />
  </span>
</template>

<script setup lang="ts">
// 通用 UI 组件：状态徽标，按 variant 渲染不同语义配色。
import { computed } from 'vue'

interface Props {
  variant?: 'primary' | 'success' | 'warning' | 'danger' | 'default'
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default',
})

const variantClass = computed(() => {
  const variants = {
    primary: 'bg-primary-50 text-primary-700',
    success: 'bg-success-50 text-success-700',
    warning: 'bg-warning-50 text-warning-700',
    danger: 'bg-danger-50 text-danger-700',
    default: 'bg-gray-100 text-foreground',
  }
  return variants[props.variant]
})
</script>
