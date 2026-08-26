<template>
  <!-- loading：骨架屏占位 -->
  <SkeletonList
    v-if="status === 'loading'"
    :rows="rows"
    :type="type"
    :cols="cols"
  />
  <!-- error：错误态 + 重试 -->
  <ErrorState
    v-else-if="status === 'error'"
    :icon="errorIcon"
    :title="errorTitle"
    :description="errorDescription"
    :show-retry="showRetry"
    :retry-text="retryText"
    @retry="$emit('retry')"
  >
    <template v-if="$slots['error-action']" #action>
      <slot name="error-action" />
    </template>
  </ErrorState>
  <!-- empty：空态 -->
  <EmptyState
    v-else-if="status === 'empty'"
    :icon="emptyIcon"
    :title="emptyTitle"
    :variant="emptyVariant"
  >
    <template v-if="$slots['empty-description']" #default>
      <slot name="empty-description" />
    </template>
    <template v-else #default>
      <p>{{ emptyDescription }}</p>
    </template>
    <template v-if="$slots['empty-action']" #action>
      <slot name="empty-action" />
    </template>
  </EmptyState>
  <!-- ready：实际内容 -->
  <slot v-else />
</template>

<script setup lang="ts">
// U5：统一三态容器。
// 通过 status prop 切换 loading/error/empty/ready 四态，内部委托 SkeletonList/ErrorState/EmptyState。
// ready 态渲染默认插槽内容。新增页面优先使用本组件替代手写 v-if 三态逻辑。
import SkeletonList from '@/components/ui/SkeletonList.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

interface Props {
  /** 状态：loading | error | empty | ready */
  status: 'loading' | 'error' | 'empty' | 'ready'
  /** loading 态：骨架行数 */
  rows?: number
  /** loading 态：骨架形态 list/card/line */
  type?: 'list' | 'card' | 'line'
  /** loading 态：card 形态列数 */
  cols?: number
  /** error 态：图标 */
  errorIcon?: string
  /** error 态：标题 */
  errorTitle?: string
  /** error 态：描述 */
  errorDescription?: string
  /** error 态：是否显示重试按钮 */
  showRetry?: boolean
  /** error 态：重试按钮文案 */
  retryText?: string
  /** empty 态：图标 */
  emptyIcon?: string
  /** empty 态：标题 */
  emptyTitle?: string
  /** empty 态：描述 */
  emptyDescription?: string
  /** empty 态：视觉变体 */
  emptyVariant?: 'default' | 'info' | 'warning' | 'success'
}

withDefaults(defineProps<Props>(), {
  rows: 5,
  type: 'list',
  cols: 1,
  errorIcon: 'alert-circle',
  errorTitle: '加载失败',
  errorDescription: '请稍后重试，或检查网络后再次尝试',
  showRetry: true,
  retryText: '重试',
  emptyIcon: 'folder-open',
  emptyTitle: '暂无数据',
  emptyDescription: '',
  emptyVariant: 'default',
})

defineEmits<{
  retry: []
}>()
</script>
