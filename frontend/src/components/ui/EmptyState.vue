<template>
  <!-- Interaction OS（UI/UX 5.1）§二十一：空态是「当前结果」的陈述，用 role=status 让
       屏幕阅读器在状态变化时播报（与 ErrorState 的 role=alert 区分严重度） -->
  <div
    class="flex flex-col items-center justify-center py-16 px-4 text-center"
    role="status"
    aria-live="polite"
  >
    <div
      class="w-16 h-16 rounded-full flex items-center justify-center mb-4"
      :class="iconBgClass"
    >
      <Icon :name="icon" :size="32" :class="iconClass" />
    </div>
    <!-- ① 发生了什么 -->
    <h3 class="mb-2 font-semibold text-foreground" :style="{ fontSize: 'var(--kf-fs-body-lg)' }">
      {{ title }}
    </h3>
    <!-- ② 为什么为空（+ ③ 下一步由 action 插槽表达，见下） -->
    <div
      class="max-w-sm text-muted-foreground"
      :style="{ fontSize: 'var(--kf-fs-body-md)', lineHeight: 'var(--kf-lh-body-md)' }"
    >
      <slot>
        <p>{{ description }}</p>
      </slot>
    </div>
    <!-- ③ 下一步怎么办：由调用方通过 #action 插槽传入按钮（不在此组件内发起任何请求） -->
    <div v-if="$slots.action" class="mt-6 flex items-center justify-center gap-3 flex-wrap">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
// 通用 UI 组件：空状态占位。
//
// 三要素（强制）：① 发生了什么（title）② 为什么为空（默认插槽/description）③ 下一步怎么办（#action 插槽）。
// 本组件**只负责展示**，不发起任何请求、不修改任何业务数据；
// 操作一律通过 #action 插槽由页面用 router / emit / callback 承接。
//
// Phase UI-1 变更：
//   原先使用 Tailwind 原色板硬编码（bg-blue-50 / bg-yellow-50 / bg-green-50 / text-warning-500…），
//   与同级的 ErrorState.vue（走 .kf-state-* 令牌类）实现不一致，深色模式必然分叉。
//   现全部改为 --kf-* 语义令牌，与 ErrorState 视觉体系对齐。
import { computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'

interface Props {
  icon?: string
  title: string
  /** 为什么为空的说明文案；也可用默认插槽自定义为富文本 */
  description?: string
  variant?: 'default' | 'info' | 'warning' | 'success'
}

const props = withDefaults(defineProps<Props>(), {
  icon: 'folder-open',
  description: '暂无数据',
  variant: 'default',
})

const iconBgClass = computed(() => {
  switch (props.variant) {
    case 'info':
      return 'bg-[var(--kf-primary-subtle)]'
    case 'warning':
      return 'bg-[var(--kf-warning-subtle)]'
    case 'success':
      return 'bg-[var(--kf-success-subtle)]'
    default:
      return 'bg-[var(--kf-muted)]'
  }
})

const iconClass = computed(() => {
  switch (props.variant) {
    case 'info':
      return 'text-[var(--kf-state-info)]'
    case 'warning':
      return 'text-[var(--kf-state-warning)]'
    case 'success':
      return 'text-[var(--kf-state-success)]'
    default:
      return 'text-[var(--kf-muted-foreground)]'
  }
})
</script>
