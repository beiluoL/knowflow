<template>
  <div class="w-full">
    <div v-if="showLabel" class="flex justify-between mb-1.5">
      <span class="text-sm text-muted-foreground">{{ label }}</span>
      <span class="text-sm font-medium text-foreground">{{ percentage }}%</span>
    </div>
    <div class="w-full h-2 bg-gray-100 rounded-full overflow-hidden">
      <div
        :class="['h-full rounded-full transition-all duration-500 ease-out', variantClass]"
        :style="{ width: `${percentage}%` }"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
// 通用 UI 组件：进度条，按 percentage 控制宽度并支持显示标签与配色变体。
import { computed } from 'vue'

interface Props {
  percentage: number
  variant?: 'primary' | 'success' | 'warning' | 'danger'
  label?: string
  showLabel?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'primary',
  showLabel: false,
  label: '',
})

const variantClass = computed(() => {
  const variants = {
    primary: 'bg-primary-500',
    success: 'bg-success-500',
    warning: 'bg-warning-500',
    danger: 'bg-danger-500',
  }
  return variants[props.variant]
})
</script>
