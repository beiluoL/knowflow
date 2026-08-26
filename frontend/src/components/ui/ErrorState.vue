<template>
  <div class="kb-state kb-state-error">
    <div class="kb-state-icon">
      <Icon :name="icon" :size="32" />
    </div>
    <h3 class="kb-state-title">{{ title }}</h3>
    <div class="kb-state-desc">
      <slot>
        <p>{{ description }}</p>
      </slot>
    </div>
    <div v-if="$slots.action || showRetry" class="kb-state-action">
      <slot name="action">
        <button v-if="showRetry" type="button" class="kb-state-retry" @click="$emit('retry')">
          <Icon name="refresh-cw" :size="14" />
          <span>{{ retryText }}</span>
        </button>
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
// U5：错误态组件，与 EmptyState/SkeletonList 同级，配合 StateView 统一三态。
// 复用全局 .kb-state-* CSS 类，与 EmptyState 视觉一致；默认提供「重试」按钮。
import Icon from '@/components/ui/Icon.vue'

interface Props {
  /** 图标名，默认 alert-triangle */
  icon?: string
  /** 标题 */
  title?: string
  /** 描述文本，可通过默认插槽自定义 */
  description?: string
  /** 是否显示「重试」按钮 */
  showRetry?: boolean
  /** 重试按钮文案 */
  retryText?: string
}

withDefaults(defineProps<Props>(), {
  icon: 'alert-circle',
  title: '加载失败',
  description: '请稍后重试，或检查网络后再次尝试',
  showRetry: true,
  retryText: '重试',
})

defineEmits<{
  retry: []
}>()
</script>
