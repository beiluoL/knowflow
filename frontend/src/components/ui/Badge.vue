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
//
// Interaction OS（UI/UX 5.1）§十八：语义变体扩展。
// 原先只有 primary/success/warning/danger/default，页面为了表达「待复习 / 学习中 / AI 生成」
// 只能各写一套自定义 CSS（例如 SmartQuiz 里就有一套独立的 .badge-* 类）。
// 现在统一为 8 个语义变体，全部走 --kf-* 令牌（深色模式自动跟随），
// 页面禁止再自行定义状态颜色与文案 —— 语义→变体的映射见 `learning/badge-semantics.ts`。
import { computed } from 'vue'

interface Props {
  variant?:
    | 'primary' // 品牌主色（普通强调）
    | 'success' // 已完成 / 已掌握
    | 'warning' // 需注意（薄弱）
    | 'danger' // 异常 / 错误
    | 'info' // 提示 / 进行中的中性信息
    | 'learning' // 学习中（主色实底系）
    | 'review' // 待复习（到期待办）
    | 'ai' // AI 生成 / AI 推荐（高光橘，令牌既有设计用途）
    | 'neutral' // 未开始 / 未知
    | 'default' // 兼容旧调用（等同 neutral 的旧配色）
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default',
})

const variantClass = computed(() => {
  const variants: Record<NonNullable<Props['variant']>, string> = {
    primary: 'bg-primary-50 text-primary-700',
    default: 'bg-gray-100 text-foreground',
    success: 'bg-[var(--kf-success-subtle)] text-[var(--kf-state-success)]',
    warning: 'bg-[var(--kf-warning-subtle)] text-[var(--kf-state-warning)]',
    danger: 'bg-[var(--kf-danger-subtle)] text-[var(--kf-state-error)]',
    info: 'bg-[var(--kf-primary-subtle)] text-[var(--kf-state-info)]',
    learning: 'bg-[var(--kf-primary-soft)] text-[var(--kf-primary)]',
    review: 'bg-[var(--kf-warning-subtle)] text-[var(--kf-state-warning)]',
    ai: 'bg-[var(--kf-highlight-subtle)] text-[var(--kf-highlight)]',
    neutral: 'bg-[var(--kf-muted)] text-[var(--kf-muted-foreground)]',
  }
  return variants[props.variant]
})
</script>
