<template>
  <div
    class="pointer-events-none fixed right-4 top-4 z-50 flex w-80 max-w-[90vw] flex-col gap-3"
    aria-live="polite"
  >
    <transition-group name="toast">
      <div
        v-for="t in toastState.toasts" :key="t.id"
        class="pointer-events-auto flex items-start gap-3 rounded-lg border-l-4 bg-white px-4 py-3 shadow-lg"
        :class="toastClass(t.type)"
        role="status"
        :aria-live="t.type === 'error' ? 'assertive' : 'polite'"
      >
        <Icon :name="toastIcon(t.type)" :size="18" :class="toastText(t.type)" />
        <span class="flex-1 text-sm text-gray-800">{{ t.message }}</span>
        <button
          type="button"
          class="text-gray-400 transition-colors hover:text-gray-600"
          aria-label="关闭提示"
          @click="dismiss(t.id)"
        >
          ×
        </button>
      </div>
    </transition-group>
  </div>

  <div
    v-for="c in toastState.confirms" :key="c.id"
    class="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 px-4"
    role="alertdialog"
    aria-modal="true"
  >
    <div class="w-full max-w-sm rounded-xl bg-white p-6 shadow-xl" :data-confirm-id="c.id">
      <p class="mb-5 text-sm text-gray-800">{{ c.message }}</p>
      <!-- Prompt 输入框（仅当配置了 prompt 时显示） -->
      <input
        v-if="c.prompt"
        :data-prompt-id="c.id"
        ref="registerPromptInput"
        :value="c.prompt.defaultValue"
        :placeholder="c.prompt.placeholder"
        type="text"
        class="mb-5 w-full rounded-md border border-gray-200 px-3 py-2 text-sm outline-none transition-colors focus:border-primary-400 focus:ring-2 focus:ring-primary-100"
        @keydown.enter="onPromptEnter($event, c)"
      />
      <div class="flex justify-end gap-3">
        <button
          type="button"
          class="rounded-md px-4 py-2 text-sm text-gray-600 transition-colors hover:bg-gray-100"
          @click="c.resolve(false)"
        >
          取消
        </button>
        <button
          type="button"
          class="rounded-md bg-primary-500 px-4 py-2 text-sm text-white transition-opacity hover:opacity-90"
          @click="onConfirmClick(c)"
        >
          确定
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 通用 UI 组件：全局消息宿主，渲染 toast 提示与确认弹窗，并从全局 toast 状态读取数据。
import { watch, type ComponentPublicInstance } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { dismiss, toastState, type ToastType, type ConfirmItem } from '@/utils/toast'

/**
 * prompt 输入框的引用映射：key 是 confirmId，value 是对应的 DOM input
 * 注意：template ref + v-for 中 ref 是函数时会被循环调用注册
 */
const promptInputMap = new Map<number, HTMLInputElement>()

/** 注册 prompt 输入框引用（用于 v-for 循环 + ref 函数回调） */
const registerPromptInput = (el: Element | ComponentPublicInstance | null) => {
  if (el && el instanceof HTMLInputElement) {
    const idStr = el.dataset.promptId
    if (idStr !== undefined) {
      const id = Number(idStr)
      if (!isNaN(id)) {
        promptInputMap.set(id, el)
        // 注册时自动聚焦
        requestAnimationFrame(() => el.focus())
      }
    }
  }
}

// 监听 confirms 变化，当移除时清理引用
watch(
  () => toastState.confirms.map((c) => c.id).join(','),
  (idsStr, oldIdsStr) => {
    const ids = new Set(idsStr.split(',').filter(Boolean).map(Number))
    const oldIds = new Set((oldIdsStr ?? '').split(',').filter(Boolean).map(Number))
    // 删除已不存在的引用
    for (const id of oldIds) {
      if (!ids.has(id)) promptInputMap.delete(id)
    }
  }
)

/** 输入框回车提交 */
const onPromptEnter = (e: KeyboardEvent, c: ConfirmItem) => {
  const value = (e.target as HTMLInputElement).value
  c.resolve(true, value)
}

/** 确定按钮点击处理：区分普通确认与 prompt 提交 */
const onConfirmClick = (c: ConfirmItem) => {
  if (c.prompt) {
    const inputEl = promptInputMap.get(c.id)
    c.resolve(true, inputEl?.value ?? c.prompt.defaultValue ?? '')
  } else {
    c.resolve(true)
  }
}

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
