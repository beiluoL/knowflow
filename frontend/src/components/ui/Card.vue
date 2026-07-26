<template>
  <div
    :class="[
      'bg-white rounded-lg shadow-card transition-all duration-300 overflow-hidden',
      { 'hover:shadow-card-hover hover:-translate-y-0.5 cursor-pointer': hoverable },
    ]"
  >
    <div v-if="$slots.header" class="px-6 py-4 border-b border-gray-100">
      <slot name="header" />
    </div>
    <div :class="bodyClass">
      <slot />
    </div>
    <div v-if="$slots.footer" class="px-6 py-4 border-t border-gray-100 bg-gray-50/50">
      <slot name="footer" />
    </div>
  </div>
</template>

<script setup lang="ts">
// 通用 UI 组件：卡片容器，支持 header/footer 插槽与可选悬浮效果，按 padding 控制内边距。
import { computed } from 'vue'

interface Props {
  hoverable?: boolean
  padding?: 'sm' | 'md' | 'lg' | 'none'
}

const props = withDefaults(defineProps<Props>(), {
  hoverable: false,
  padding: 'md',
})

const bodyClass = computed(() => {
  const paddings = {
    sm: 'p-4',
    md: 'p-6',
    lg: 'p-8',
    none: '',
  }
  return paddings[props.padding]
})
</script>
