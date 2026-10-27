<template>
  <!-- §二十五：错误态视觉沿用既有 .kb-state-* 令牌类，与 EmptyState 同源（禁止再造一套） -->
  <div class="kb-state kb-state-error" role="alert" aria-live="assertive">
    <div class="kb-state-icon">
      <Icon :name="icon" :size="32" aria-hidden="true" />
    </div>
    <!-- ① 发生了什么 -->
    <h3 class="kb-state-title">{{ title }}</h3>
    <div class="kb-state-desc">
      <slot>
        <p>{{ description }}</p>
      </slot>
    </div>
    <!-- ② 是否影响当前页面（Interaction OS §六：没有这一行，用户无法判断要不要离开页面） -->
    <p v-if="scope" class="kb-state-scope">
      <Icon name="info" :size="13" aria-hidden="true" />
      <span>{{ scope }}</span>
    </p>
    <!-- ③ 怎么解决 -->
    <p v-if="hint" class="kb-state-hint">{{ hint }}</p>
    <div v-if="$slots.action || showRetry" class="kb-state-action">
      <slot name="action">
        <button v-if="showRetry" type="button" class="kb-state-retry" @click="$emit('retry')">
          <Icon name="refresh-cw" :size="14" aria-hidden="true" />
          <span>{{ retryText }}</span>
        </button>
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
// U5：错误态组件，与 EmptyState/SkeletonList 同级，配合 StateView 统一三态。
// 复用全局 .kb-state-* CSS 类，与 EmptyState 视觉一致；默认提供「重试」按钮。
//
// Interaction OS（UI/UX 5.1）§六 增强：
//   错误态必须回答三件事 —— ① 发生了什么（title）② 是否影响当前页面（scope）
//   ③ 怎么解决（hint）。此前只有 title + description，用户看到「加载失败」后
//   既不知道是否只坏了一块，也不知道下一步该干什么。
//   文案请统一通过 `utils/error-message.ts` 的 `describePageError()` 生成，
//   避免把 `err.message` / `status code 502` 直接渲染给用户。
//   `scope` / `hint` 缺省为空串 → 不渲染（向后兼容既有调用方）。
import Icon from '@/components/ui/Icon.vue'

interface Props {
  /** 图标名，默认 alert-circle */
  icon?: string
  /** ① 发生了什么 */
  title?: string
  /** 补充说明文本，可通过默认插槽自定义 */
  description?: string
  /** ② 是否影响当前页面（如「只是这一块没加载出来，页面其他内容仍可正常使用。」） */
  scope?: string
  /** ③ 怎么解决（如「请确认后端服务已启动，并检查网络连接是否正常。」） */
  hint?: string
  /** 是否显示「重试」按钮 */
  showRetry?: boolean
  /** 重试按钮文案 */
  retryText?: string
}

withDefaults(defineProps<Props>(), {
  icon: 'alert-circle',
  title: '加载失败',
  description: '请稍后重试，或检查网络后再次尝试',
  scope: '',
  hint: '',
  showRetry: true,
  retryText: '重试',
})

defineEmits<{
  retry: []
}>()
</script>

<style scoped>
/* §二 ②：影响范围与解决建议 —— 弱化层级，保持「专业、安静」的视觉（§二十二） */
.kb-state-scope {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin: 10px 0 0;
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
  max-width: 360px;
  text-align: left;
}
.kb-state-hint {
  margin: 6px 0 0;
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
  max-width: 360px;
  text-align: left;
}
</style>
