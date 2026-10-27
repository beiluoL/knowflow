<template>
  <!-- loading：骨架屏占位 -->
  <!-- Interaction OS（UI/UX 5.1）§四：默认骨架是「列表行」，但真实内容往往不是列表。
       页面可通过 #skeleton 插槽传入**近似真实内容布局**的骨架（如闪卡 = 进度条 + 卡片 + 操作条），
       避免出现「中间一个转圈 / 巨大空白 / 页面跳动」。 -->
  <div v-if="status === 'loading' || status === 'retrying'" role="status" aria-live="polite" aria-busy="true">
    <span class="sr-only">{{ status === 'retrying' ? '正在重新加载…' : '正在加载…' }}</span>
  <slot name="skeleton">
    <SkeletonList
      :rows="rows"
      :type="type"
      :cols="cols"
    />
  </slot>
  </div>
  <!-- error：错误态 + 重试 -->
  <ErrorState
    v-else-if="status === 'error'"
    :icon="errorIcon"
    :title="errorTitle"
    :description="errorHint && errorDescription === '请稍后重试，或检查网络后再次尝试' ? '' : errorDescription"
    :scope="errorScope"
    :hint="errorHint"
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
// 统一异步五态容器。
// loading/retrying/error/empty/ready；委托 SkeletonList/ErrorState/EmptyState。
// ready 态渲染默认插槽内容。新增页面优先使用本组件替代手写 v-if 三态逻辑。
//
// Interaction OS（UI/UX 5.1）§三/§四/§六 增强：
//   - `#skeleton` 插槽：允许页面提供**近似真实内容布局**的骨架（§四），
//     未提供时回落 SkeletonList（向后兼容）。
//   - `errorScope` / `errorHint`：透传给 ErrorState，补齐「是否影响当前页面 / 怎么解决」。
//   - 错误文案统一由 `utils/error-message.ts` 的 `describePageError()` 生成。
import SkeletonList from '@/components/ui/SkeletonList.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

interface Props {
  /** 状态：loading | retrying | error | empty | ready */
  status: 'loading' | 'error' | 'empty' | 'ready' | 'retrying'
  /** loading 态：骨架行数（未提供 #skeleton 插槽时生效） */
  rows?: number
  /** loading 态：骨架形态 list/card/line（未提供 #skeleton 插槽时生效） */
  type?: 'list' | 'card' | 'line'
  /** loading 态：card 形态列数 */
  cols?: number
  /** error 态：图标 */
  errorIcon?: string
  /** error 态：标题（① 发生了什么） */
  errorTitle?: string
  /** error 态：描述 */
  errorDescription?: string
  /** error 态：② 是否影响当前页面（Interaction OS §六） */
  errorScope?: string
  /** error 态：③ 怎么解决（Interaction OS §六） */
  errorHint?: string
  /** error 态：是否显示重试按钮 */
  showRetry?: boolean
  /** error 态：重试按钮文案 */
  retryText?: string
  /** empty 态：图标 */
  emptyIcon?: string
  /** empty 态：标题 */
  emptyTitle?: string
  /** empty 态：描述（② 为什么为空） */
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
  errorScope: '',
  errorHint: '',
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
