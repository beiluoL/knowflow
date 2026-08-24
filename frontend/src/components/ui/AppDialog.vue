<template>
  <Teleport to="body">
    <Transition name="dlg-fade">
      <div
        v-if="open"
        class="dlg-mask"
        :class="{ 'dlg-mask--persistent': persistent }"
        @click.self="onMaskClick"
      >
        <div
          class="dlg-card"
          :class="`dlg-card--${variant}`"
          :style="cardStyle"
          role="dialog"
          aria-modal="true"
        >
          <button v-if="!persistent" class="dlg-close" aria-label="关闭" @click="onCancel">
            <Icon name="x" :size="18" />
          </button>

          <div class="dlg-head">
            <span v-if="icon || variantIcon" class="dlg-icon" :class="`dlg-icon--${variant}`">
              <Icon :name="icon || variantIcon" :size="20" />
            </span>
            <h3 class="dlg-title">{{ title }}</h3>
          </div>

          <div class="dlg-body">
            <p v-if="message" class="dlg-msg">{{ message }}</p>
            <input
              v-if="input"
              ref="inputEl"
              v-model="inputValue"
              class="dlg-input"
              :placeholder="input.placeholder"
              :maxlength="input.maxlength"
              @keyup.enter="onConfirm"
            />
            <slot />
          </div>

          <div class="dlg-foot">
            <button v-if="showCancel" class="dlg-btn dlg-btn--cancel" @click="onCancel">
              {{ cancelText }}
            </button>
            <button
              ref="confirmBtn"
              class="dlg-btn"
              :class="`dlg-btn--${confirmClass}`"
              @click="onConfirm"
            >
              {{ confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import type { DialogVariant } from '@/utils/dialog'

const props = withDefaults(
  defineProps<{
    open?: boolean
    title?: string
    message?: string
    variant?: DialogVariant
    icon?: string
    confirmText?: string
    cancelText?: string
    showCancel?: boolean
    persistent?: boolean
    width?: string
    input?: { value?: string; placeholder?: string; maxlength?: number }
  }>(),
  {
    open: false,
    variant: 'default',
    confirmText: '确定',
    cancelText: '取消',
    showCancel: true,
    persistent: false,
  },
)

const emit = defineEmits<{
  (e: 'confirm', value: string | boolean): void
  (e: 'cancel'): void
}>()

const variantIcon = computed(() => {
  if (props.icon) return props.icon
  if (props.variant === 'info') return 'info'
  return 'alert-circle'
})
const confirmClass = computed(() => (props.variant === 'danger' ? 'danger' : 'primary'))
const inputValue = ref(props.input?.value ?? '')
const inputEl = ref<HTMLInputElement | null>(null)
const confirmBtn = ref<HTMLButtonElement | null>(null)
const cardStyle = computed(() => (props.width ? { maxWidth: props.width } : {}))

function onConfirm() {
  emit('confirm', props.input ? inputValue.value : true)
}
function onCancel() {
  emit('cancel')
}
function onMaskClick() {
  if (!props.persistent) onCancel()
}
function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape' && !props.persistent) onCancel()
}

onMounted(async () => {
  inputValue.value = props.input?.value ?? ''
  await nextTick()
  if (props.input) inputEl.value?.focus()
  else confirmBtn.value?.focus()
  window.addEventListener('keydown', onKey)
})
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<style scoped>
.dlg-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 17, 21, 0.45);
  backdrop-filter: saturate(120%);
}
.dlg-card {
  position: relative;
  width: 100%;
  max-width: 420px;
  padding: 22px 22px 18px;
  background: var(--kb-card);
  color: var(--kb-card-foreground);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
}
.dlg-close {
  position: absolute;
  top: 12px;
  right: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: var(--kb-radius-md);
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.dlg-close:hover {
  background: var(--kb-background);
  color: var(--kb-foreground);
}
.dlg-close:active {
  transform: translateY(1px);
}
.dlg-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  padding-right: 28px;
}
.dlg-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--kb-foreground);
}
.dlg-icon {
  display: inline-flex;
  flex-shrink: 0;
}
.dlg-icon--danger {
  color: var(--kb-destructive);
}
.dlg-icon--info,
.dlg-icon--primary {
  color: var(--kb-primary);
}
.dlg-body {
  font-size: 14px;
  color: var(--kb-muted-foreground);
}
.dlg-msg {
  margin: 0 0 12px;
  line-height: 1.6;
  white-space: pre-line;
}
.dlg-input {
  box-sizing: border-box;
  width: 100%;
  padding: 9px 12px;
  font-size: 14px;
  color: var(--kb-foreground);
  background: var(--kb-background);
  border: 1px solid var(--kb-input);
  border-radius: var(--kb-radius-md);
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.dlg-input::placeholder {
  color: var(--kb-muted-foreground);
}
.dlg-input:focus {
  border-color: var(--kb-ring);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--kb-ring) 25%, transparent);
}
.dlg-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}
.dlg-btn {
  padding: 8px 18px;
  font-size: 14px;
  font-weight: 600;
  border: 1px solid transparent;
  border-radius: var(--kb-radius-md);
  cursor: pointer;
  transition: filter 0.15s ease, background 0.15s ease, transform 0.05s ease;
}
.dlg-btn--cancel {
  background: transparent;
  border-color: var(--kb-border);
  color: var(--kb-foreground);
}
.dlg-btn--cancel:hover {
  background: var(--kb-background);
}
.dlg-btn--primary {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.dlg-btn--primary:hover {
  filter: brightness(0.95);
}
.dlg-btn--danger {
  background: var(--kb-destructive);
  color: var(--kb-destructive-foreground);
}
.dlg-btn--danger:hover {
  filter: brightness(0.95);
}
.dlg-btn:active {
  transform: translateY(1px);
}

.dlg-fade-enter-active,
.dlg-fade-leave-active {
  transition: opacity 0.18s ease;
}
.dlg-fade-enter-from,
.dlg-fade-leave-to {
  opacity: 0;
}
.dlg-fade-enter-active .dlg-card,
.dlg-fade-leave-active .dlg-card {
  transition: transform 0.18s ease;
}
.dlg-fade-enter-from .dlg-card,
.dlg-fade-leave-to .dlg-card {
  transform: translateY(8px) scale(0.98);
}
</style>
