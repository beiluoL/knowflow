<template>
  <div class="flex flex-col items-center justify-center py-16 text-center">
    <div
      :class="[
        'w-16 h-16 rounded-full flex items-center justify-center mb-4',
        iconBgClass,
      ]"
    >
      <Icon :name="icon" :size="32" :class="iconClass" />
    </div>
    <h3 class="text-lg font-semibold mb-2 text-foreground">{{ title }}</h3>
    <div class="text-sm text-muted-foreground max-w-xs">
      <slot>
        <p>暂无数据</p>
      </slot>
    </div>
    <div v-if="$slots.action" class="mt-6">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
// 通用 UI 组件：空状态占位，支持图标、标题、默认描述与操作插槽。
import { computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'

interface Props {
  icon?: string
  title: string
  variant?: 'default' | 'info' | 'warning' | 'success'
}

const props = withDefaults(defineProps<Props>(), {
  icon: 'folder-open',
  variant: 'default',
})

const iconBgClass = computed(() => {
  switch (props.variant) {
    case 'info':
      return 'bg-blue-50'
    case 'warning':
      return 'bg-yellow-50'
    case 'success':
      return 'bg-green-50'
    default:
      return 'bg-gray-100'
  }
})

const iconClass = computed(() => {
  switch (props.variant) {
    case 'info':
      return 'text-primary-500'
    case 'warning':
      return 'text-warning-500'
    case 'success':
      return 'text-success-500'
    default:
      return 'text-muted-foreground'
  }
})
</script>
