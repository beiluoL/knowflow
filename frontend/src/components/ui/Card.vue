<template>
  <div
    :class="[
      'rounded-lg transition-all duration-300 overflow-hidden',
      variantClass,
      { 'hover:shadow-card-hover hover:-translate-y-0.5 cursor-pointer': hoverable },
    ]"
  >
    <div v-if="$slots.header" class="px-6 py-4 border-b border-border">
      <slot name="header" />
    </div>
    <div :class="bodyClass">
      <slot />
    </div>
    <div v-if="$slots.footer" class="px-6 py-4 border-t border-border bg-muted/50">
      <slot name="footer" />
    </div>
  </div>
</template>

<script setup lang="ts">
// 通用 UI 组件：卡片容器，支持 variant/padding/header/footer。
//
// UI/UX 6.0 变更：
//   新增 variant prop，支持 4 种视觉重量：
//   - 'default'：白底 + 边框 + 轻阴影（原有行为，向后兼容）
//   - 'flat'：白底 + 边框，无阴影（轻量容器，替代 Card-in-Card）
//   - 'outlined'：透明底 + 边框（极简分组）
//   - 'elevated'：白底 + 强阴影（需要视觉强调的主卡片）
//
//   原有 hoverable / padding / header / footer 行为完全不变。
import { computed } from 'vue'

interface Props {
  /** 卡片视觉变体 */
  variant?: 'default' | 'flat' | 'outlined' | 'elevated'
  hoverable?: boolean
  padding?: 'sm' | 'md' | 'lg' | 'none'
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default',
  hoverable: false,
  padding: 'md',
})

const variantClass = computed(() => {
  const variants = {
    default: 'bg-card border border-border shadow-card',
    flat: 'bg-card border border-border',
    outlined: 'bg-transparent border border-border',
    elevated: 'bg-card border border-border shadow-md',
  }
  return variants[props.variant]
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
