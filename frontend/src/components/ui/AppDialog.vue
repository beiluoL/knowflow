<template>
  <Teleport to="body">
    <Transition name="dlg-fade">
      <div
        v-if="open"
        class="dlg-mask"
        :class="{ 'dlg-mask--persistent': persistent, 'dlg-mask--scrollable': scrollable, 'dlg-mask--right': placement==='right', 'dlg-mask--confirmation': confirmation }"
        @click.self="onMaskClick"
      >
        <div
          class="dlg-card"
          :class="[`dlg-card--${variant}`, { 'dlg-card--scrollable': scrollable, 'dlg-card--right': placement==='right', 'dlg-card--confirmation': confirmation }]"
          :style="cardStyle"
          ref="cardEl"
          role="dialog"
          aria-modal="true"
          :aria-label="scrollable ? title : undefined"
          :aria-labelledby="title ? titleId : undefined"
          :aria-describedby="message || description ? descriptionId : undefined"
          :aria-busy="loading || undefined"
          :tabindex="scrollable ? -1 : undefined"
          @keydown="onDialogKey"
        >
          <button v-if="!persistent" class="dlg-close" aria-label="关闭" :disabled="loading" @click="onCancel">
            <Icon name="x" :size="18" />
          </button>

          <div class="dlg-head">
            <span v-if="icon || variantIcon" class="dlg-icon" :class="`dlg-icon--${variant}`">
              <slot name="icon">
                <Icon :name="icon || variantIcon" :size="confirmation ? 22 : 20" />
              </slot>
            </span>
            <div v-if="description" class="dlg-heading"><h3 class="dlg-title" :id="titleId">{{ title }}</h3><p class="dlg-description" :id="descriptionId">{{ description }}</p></div>
            <h3 v-else class="dlg-title" :id="titleId">{{ title }}</h3>
          </div>

          <div class="dlg-body">
            <p v-if="message" class="dlg-msg" :id="description ? undefined : descriptionId">{{ message }}</p>
            <div v-if="input" class="kb-field">
            <input
              ref="inputEl"
              v-model="inputValue"
              class="dlg-input kb-input"
              :placeholder="input.placeholder"
              :maxlength="input.maxlength"
              @keydown.enter.prevent="onInputEnter"
              :disabled="loading"
            />
            </div>
            <slot />
            <p v-if="error" class="dlg-error" role="alert">{{ error }}</p>
          </div>

          <div v-if="!hideFooter" class="dlg-foot">
            <slot name="footer">
            <button v-if="showCancel" ref="cancelBtn" v-bind="cancelButtonProps" type="button" :disabled="loading || cancelButtonProps?.disabled" class="dlg-btn dlg-btn--cancel" @click="onCancel">
              {{ cancelText }}
            </button>
            <button
              ref="confirmBtn"
              v-bind="confirmButtonProps"
              :disabled="loading || disabled || confirmButtonProps?.disabled"
              class="dlg-btn"
              :class="`dlg-btn--${confirmClass}`"
              @click="onConfirm"
            >
              {{ loading ? '处理中…' : confirmText }}
            </button>
            </slot>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch, getCurrentInstance } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import type { DialogVariant, DialogButtonProps } from '@/utils/dialog'
import {enterModal,leaveModal,isTopModal,hasModal,focusTopModal} from './modal-stack'

const props = withDefaults(
  defineProps<{
    confirmation?: boolean
    loading?: boolean
    disabled?: boolean
    error?: string
    closeOnOverlay?: boolean
    closeOnEsc?: boolean
    confirmButtonProps?: DialogButtonProps
    cancelButtonProps?: DialogButtonProps
    open?: boolean
    title?: string
    message?: string
    variant?: DialogVariant
    icon?: string
    confirmText?: string
    cancelText?: string
    hideFooter?: boolean
    showCancel?: boolean
    persistent?: boolean
    width?: string
    /** Opt-in form layout; existing confirmation dialogs keep their original behavior. */
    placement?: 'center' | 'right'
    scrollable?: boolean
    description?: string
    input?: { value?: string; placeholder?: string; maxlength?: number }
  }>(),
  {
    open: false,
    confirmation: false,
    loading: false,
    disabled: false,
    closeOnOverlay: true,
    closeOnEsc: true,
    variant: 'default',
    confirmText: '确定',
    cancelText: '取消',
    showCancel: true,
    hideFooter: false,
    persistent: false,
    scrollable: false,
    placement: 'center',
  },
)

const emit = defineEmits<{
  (e: 'confirm', value: string | boolean): void
  (e: 'cancel'): void
}>()

const modalId = getCurrentInstance()!.uid
const titleId = `dialog-title-${modalId}`
const descriptionId = `dialog-description-${modalId}`
const cancelBtn = ref<HTMLButtonElement | null>(null)
const variantIcon = computed(() => {
  if (props.icon) return props.icon
  if (props.variant === 'info') return 'info'
  return 'alert-circle'
})
const confirmClass = computed(() => (props.variant === 'danger' ? 'danger' : 'primary'))
const inputValue = ref(props.input?.value ?? '')
const inputEl = ref<HTMLInputElement | null>(null)
const confirmBtn = ref<HTMLButtonElement | null>(null)
const cardEl = ref<HTMLElement | null>(null)
const cardStyle = computed(() => (props.width ? { maxWidth: props.width } : {}))
let previousFocus: HTMLElement | null = null
let previousOverflow: string | null = null
let mounted = false

function onConfirm() {
  if (props.loading || props.disabled || props.confirmButtonProps?.disabled) return
  emit('confirm', props.input ? inputValue.value : true)
}
function onInputEnter(e: KeyboardEvent) { if (!e.isComposing && !props.confirmation) onConfirm() }
function onCancel() {
  if (props.loading) return
  emit('cancel')
}
function onMaskClick() {
  if (!props.persistent && props.closeOnOverlay) onCancel()
}
function onKey(e: KeyboardEvent) {
  if (!props.open || (hasModal() && !isTopModal(modalId))) return
  // An owned selection popup consumes Escape before its parent modal.
  if(e.key==='Escape' && e.target instanceof Element && e.target.closest('.kb-select-panel, .cascade-tree')) return
  if (props.scrollable) {
    if (!props.open || e.key !== 'Escape') return
    // Capture before an underlying FocusShell can interpret Escape as "exit study".
    e.preventDefault(); e.stopPropagation()
    if (!props.persistent && props.closeOnEsc) onCancel()
  } else if (e.key === 'Escape' && props.closeOnEsc && !props.persistent) {
    onCancel()
  }
}

function focusable() {
  return Array.from(cardEl.value?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], summary, [tabindex="0"]') ?? []).filter(el => {
    const closed = el.closest('details:not([open])')
    return !el.closest('[hidden]') && (!closed || closed.querySelector('summary') === el) && getComputedStyle(el).display !== 'none' && getComputedStyle(el).visibility !== 'hidden'
  })
}
function onDialogKey(e: KeyboardEvent) {
  if (!props.scrollable || !isTopModal(modalId) || e.key !== 'Tab') return
  const nodes = focusable(), first = nodes[0], last = nodes.at(-1)
  if (!first) { e.preventDefault(); cardEl.value?.focus(); return }
  if (e.shiftKey && (document.activeElement === first || document.activeElement === cardEl.value)) { e.preventDefault(); last?.focus() }
  else if (!e.shiftKey && (document.activeElement === last || document.activeElement === cardEl.value)) { e.preventDefault(); first.focus() }
}
function containFocus(e: FocusEvent) {
  if (props.open && props.scrollable && isTopModal(modalId) && !cardEl.value?.contains(e.target as Node)) cardEl.value?.focus()
}
async function activateForm() {
  if (!mounted || !props.scrollable || !props.open || previousOverflow !== null) return
  previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  previousOverflow = document.body.style.overflow
  enterModal(modalId,cardEl.value)
  document.addEventListener('focusin', containFocus)
  await nextTick()
  if (mounted && props.open && isTopModal(modalId)) {
    if(props.input) inputEl.value?.focus()
    else if(props.confirmation && cancelBtn.value && !cancelBtn.value.disabled) cancelBtn.value.focus()
    else cardEl.value?.focus()
  }
}
function releaseForm() {
  if (previousOverflow === null) return
  document.removeEventListener('focusin', containFocus)
  leaveModal(modalId)
  previousOverflow = null
  if (previousFocus?.isConnected && !previousFocus.matches(':disabled')) previousFocus.focus()
  else focusTopModal()
  previousFocus = null
}
watch(() => props.open, open => { if (open) void activateForm(); else releaseForm() })

onMounted(async () => {
  mounted = true
  inputValue.value = props.input?.value ?? ''
  if (props.scrollable) await activateForm()
  await nextTick()
  if (!mounted) return
  if (!props.scrollable) {
    if (props.input) inputEl.value?.focus()
    else confirmBtn.value?.focus()
  }
  window.addEventListener('keydown', onKey, props.scrollable)
})
onUnmounted(() => { mounted = false; window.removeEventListener('keydown', onKey, props.scrollable); releaseForm() })
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
  box-shadow: var(--kb-menu-shadow);
}
.dlg-close {
  position: absolute;
  top: 12px;
  right: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--kb-btn-height-sm);
  height: var(--kb-btn-height-sm);
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
  box-shadow: none;
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

/* Only the opted-in settings form receives the viewport-contained layout. */
.dlg-mask--scrollable { padding:var(--kb-space-4);overscroll-behavior:contain; }
.dlg-card--scrollable { display:flex;flex-direction:column;max-height:min(calc(var(--kb-space-8) * 22.5),calc(100dvh - var(--kb-space-4) * 2));min-height:0;min-width:0;padding:0;overflow:hidden; }
.dlg-card--scrollable .dlg-head { flex:none;align-items:flex-start;gap:var(--kb-space-3);padding:var(--kb-space-5);padding-inline-end:calc(var(--kb-space-8) * 2);margin:0;border-bottom:var(--kb-studio-rule) solid var(--kb-border); }
.dlg-card--scrollable .dlg-icon { color:var(--kb-primary);align-items:center;min-height:calc(var(--kb-fs-h4) * var(--kb-lh-h4)); }
.dlg-card--scrollable .dlg-heading { min-width:0; }
.dlg-card--scrollable .dlg-title { font-size:var(--kb-fs-h4);line-height:var(--kb-lh-h4);white-space:nowrap; }
.dlg-description { margin-top:var(--kb-space-1);font-size:var(--kb-fs-caption);line-height:var(--kb-lh-body-sm);color:var(--kb-muted-foreground); }
.dlg-card--scrollable .dlg-close { top:var(--kb-space-4);right:var(--kb-space-4);width:var(--kb-space-8);height:var(--kb-space-8); }
.dlg-card--scrollable .dlg-body { flex:1 1 auto;min-height:0;overflow-y:auto;overscroll-behavior:contain;padding:var(--kb-space-5);scroll-padding-block:var(--kb-space-4); }
.dlg-card--scrollable .dlg-foot { flex:none;align-items:center;flex-wrap:wrap;gap:var(--kb-space-3);margin:0;padding:var(--kb-space-4) var(--kb-space-5);border-top:var(--kb-studio-rule) solid var(--kb-border); }
.dlg-card--scrollable:focus { outline:none; }
.dlg-card--scrollable .dlg-close:focus-visible { outline:var(--kb-field-focus-width) solid var(--kb-ring);outline-offset:var(--kb-space-1); }
.dlg-mask.dlg-mask--right{padding:0;justify-content:flex-end;align-items:stretch}
.dlg-card.dlg-card--right{height:100dvh;max-height:100dvh;border-radius:0;border-block:0;border-right:0}
.dlg-card--right .dlg-body{padding:0}
.dlg-card--right .dlg-foot:empty{display:none}
.dlg-card--right .dlg-title{font-size:var(--kb-fs-body-lg);line-height:var(--kb-lh-body-lg)}
</style>

<style scoped>
.dlg-mask--confirmation{background:var(--kb-confirm-overlay);backdrop-filter:none}
.dlg-card.dlg-card--confirmation{max-width:var(--kb-confirm-width);border-radius:var(--kb-confirm-radius);background:var(--kb-card);box-shadow:var(--kb-confirm-shadow);border:var(--kb-studio-rule) solid var(--kb-border)}
.dlg-card--confirmation .dlg-head{align-items:center;padding:var(--kb-confirm-padding);padding-inline-end:calc(var(--kb-confirm-padding) * 2 + var(--kb-space-4));border:0;gap:var(--kb-space-3)}
.dlg-card--confirmation .dlg-title{white-space:normal;overflow-wrap:anywhere;font-size:var(--kb-fs-h3);font-weight:var(--kb-fw-h3);line-height:var(--kb-lh-h3)}
.dlg-card--confirmation .dlg-icon{color:var(--kb-primary);align-items:center;min-height:0}
.dlg-card--confirmation .dlg-icon--danger{color:var(--kb-destructive)}
.dlg-card--confirmation .dlg-body{padding:0 var(--kb-confirm-padding);font-size:var(--kb-fs-body-md);line-height:var(--kb-lh-body-md)}
.dlg-card--confirmation .dlg-msg{margin:0;line-height:var(--kb-lh-body-lg)}
.dlg-card--confirmation .dlg-foot{padding:var(--kb-confirm-padding);border:0;gap:var(--kb-space-3);justify-content:flex-end}
.dlg-card--confirmation .dlg-btn{min-height:var(--kb-confirm-button-height);min-width:var(--kb-confirm-button-min-width);padding:var(--kb-space-3) var(--kb-space-4);border-radius:var(--kb-radius-control,var(--kb-radius-md));font-size:var(--kb-fs-body-md);line-height:var(--kb-lh-body-md);font-weight:var(--kb-fw-label);box-shadow:none}
.dlg-card--confirmation .dlg-btn--cancel{border:var(--kb-studio-rule) solid var(--kb-border);color:var(--kb-foreground);background:var(--kb-card)}
.dlg-card--confirmation .dlg-close{top:var(--kb-space-5);right:var(--kb-space-5);width:var(--kb-confirm-button-height);height:var(--kb-confirm-button-height)}
.dlg-card--confirmation .dlg-btn:disabled,.dlg-close:disabled{opacity:.55;cursor:not-allowed;transform:none}
.dlg-error{margin-top:var(--kb-space-3);color:var(--kb-destructive);font-size:var(--kb-fs-body-md);line-height:var(--kb-lh-body-md);white-space:pre-line;overflow-wrap:anywhere}
.dlg-card--confirmation button:focus-visible{outline:var(--kb-field-focus-width) solid var(--kb-ring);outline-offset:var(--kb-space-1)}
@media(max-width:639px){.dlg-card--confirmation .dlg-head{padding:var(--kb-space-5);padding-inline-end:calc(var(--kb-space-8) * 2.5)}.dlg-card--confirmation .dlg-body{padding-inline:var(--kb-space-5)}.dlg-card--confirmation .dlg-foot{padding:var(--kb-space-5)}.dlg-card--confirmation .dlg-btn{min-width:0;flex:1}.dlg-card--confirmation .dlg-close{top:var(--kb-space-3);right:var(--kb-space-3)}}
@media(prefers-reduced-motion:reduce){.dlg-fade-enter-active,.dlg-fade-leave-active,.dlg-fade-enter-active .dlg-card,.dlg-fade-leave-active .dlg-card{transition:none}}
</style>
