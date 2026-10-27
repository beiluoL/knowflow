<template>
  <div
    class="pointer-events-none fixed right-4 top-4 z-50 flex w-80 max-w-[90vw] flex-col gap-3"
    aria-live="polite"
  >
    <transition-group name="toast">
      <div
        v-for="t in toastState.toasts" :key="t.id"
        class="pointer-events-auto flex items-start gap-3 rounded-lg border-l-4 bg-card px-4 py-3 shadow-lg"
        :class="toastClass(t.type)"
        role="status"
        :aria-live="t.type === 'error' ? 'assertive' : 'polite'"
      >
        <Icon :name="toastIcon(t.type)" :size="18" :class="toastText(t.type)" />
        <span class="flex-1 text-sm text-foreground">{{ t.message }}</span>
        <button
          type="button"
          class="text-muted-foreground transition-colors hover:text-muted-foreground"
          aria-label="关闭提示"
          @click="dismiss(t.id)"
        >
          ×
        </button>
      </div>
    </transition-group>
  </div>

</template>

<script setup lang="ts">
// 通用 UI 组件：全局消息宿主，渲染 toast 提示与确认弹窗，并从全局 toast 状态读取数据。
import Icon from '@/components/ui/Icon.vue'
import { dismiss, toastState, type ToastType } from '@/utils/toast'

const toastClass = (type: ToastType): string => {
  switch (type) {
    case 'success':
      return 'border-emerald-500'
    case 'error':
      return 'border-red-500'
    case 'warning':
      return 'border-amber-500'
    default:
      return 'border-primary-500'
  }
}

const toastText = (type: ToastType): string => {
  switch (type) {
    case 'success':
      return 'text-emerald-500'
    case 'error':
      return 'text-red-500'
    case 'warning':
      return 'text-amber-500'
    default:
      return 'text-primary-500'
  }
}

const toastIcon = (type: ToastType): string => {
  switch (type) {
    case 'success':
      return 'check-circle'
    case 'error':
      return 'x-circle'
    case 'warning':
      return 'alert-triangle'
    default:
      return 'info'
  }
}
</script>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 0.25s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(16px);
}
</style>
