<template>
  <button
    :type="type"
    :class="[
      'inline-flex items-center justify-center font-medium rounded-sm transition-colors duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed',
      iconOnly ? iconOnlySizeClass : sizeClass,
      variantClass,
      { 'w-full': block },
    ]"
    :disabled="disabled || loading"
    :aria-label="iconOnly ? (ariaLabel || iconName) : ariaLabel"
    :title="iconOnly ? (title || ariaLabel) : title"
    @click="$emit('click', $event)"
  >
    <svg
      v-if="loading"
      class="animate-spin h-4 w-4"
      :class="iconOnly ? '' : '-ml-1 mr-2'"
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
      aria-hidden="true"
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
    <Icon
      v-else-if="iconName"
      :name="iconName"
      :size="iconSize"
      :class="iconOnly ? '' : 'mr-2'"
    />
    <slot />
  </button>
</template>

<script setup lang="ts">
// 通用 UI 组件：按钮，支持 variant/size/loading/block/iconOnly 等形态与图标。
//
// Phase UI-1 变更：
//   1. 新增 danger 变体（危险操作：删除/移除/清空），色值走 --kb-danger 令牌，
//      与 --kb-destructive 同源，深色模式自动跟随。
//   2. 新增 iconOnly 形态：正方形纯图标按钮。按无障碍要求，iconOnly 必须提供
//      aria-label 或 title（二者由父组件传入，Vue 属性透传会自动落到根元素上）；
//      这里额外做一次兜底，避免纯图标按钮对读屏软件不可见。
//   3. 所有颜色均走 Tailwind 语义色（已由 tailwind.config.js 映射到 --kb-* 令牌），
//      不再出现硬编码 hex。
import { computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'

interface Props {
  variant?: 'primary' | 'secondary' | 'text' | 'ghost' | 'danger'
  size?: 'sm' | 'md' | 'lg'
  type?: 'button' | 'submit' | 'reset'
  disabled?: boolean
  loading?: boolean
  block?: boolean
  iconName?: string
  /** 纯图标按钮：不渲染文字，使用正方形内边距 */
  iconOnly?: boolean
  /** 无障碍标签，iconOnly 时强烈建议提供 */
  ariaLabel?: string
  title?: string
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'primary',
  size: 'md',
  type: 'button',
  disabled: false,
  loading: false,
  block: false,
  iconOnly: false,
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

/** 纯图标按钮：正方形，保证点击热区不小于 32/36/40px */
const iconOnlySizeClass = computed(() => {
  const sizes = {
    sm: 'w-8 h-8',
    md: 'w-9 h-9',
    lg: 'w-11 h-11',
  }
  return sizes[props.size]
})

const iconSize = computed(() => {
  const sizes = { sm: 14, md: 16, lg: 18 }
  return sizes[props.size]
})

const variantClass = computed(() => {
  const variants = {
    primary: 'bg-primary-500 text-white hover:bg-primary-600 active:bg-primary-700 shadow-sm',
    secondary:
      'bg-card text-foreground border border-border hover:bg-muted active:bg-gray-100 shadow-sm',
    text: 'text-primary-500 hover:bg-primary-50 active:bg-primary-100',
    ghost:
      'bg-transparent text-primary-500 border border-primary-500 hover:bg-primary-50 active:bg-primary-100',
    // Phase UI-1：危险操作统一入口，禁止各页面手写红色按钮
    danger:
      'bg-danger-500 text-white hover:bg-danger-600 active:bg-danger-700 shadow-sm',
  }
  return variants[props.variant]
})
</script>
