<template>
  <FormField
    :control-id="inputId"
    :label="label"
    :required="required"
    :help="help"
    :error="formError"
  >
    <div
      ref="rootRef"
      class="drp-control"
      :class="{
        'drp-control--error': !!formError,
        'drp-control--disabled': disabled,
        'drp-control--readonly': readonly,
      }"
      @click="onTriggerClick"
    >
      <div
        ref="triggerRef"
        class="drp-trigger"
        :class="{ 'drp-trigger--focus': focused, 'drp-trigger--open': open }"
      >
        <Icon name="calendar" :size="16" class="drp-icon" aria-hidden="true" />
        <input
          :id="inputId"
          class="drp-text"
          type="text"
          autocomplete="off"
          readonly
          :value="displayText"
          :placeholder="placeholder"
          :disabled="disabled"
          :aria-label="ariaLabel || (!label ? placeholder : undefined)"
          :aria-invalid="formError ? 'true' : undefined"
          :aria-describedby="descriptionIds"
          aria-haspopup="dialog"
          :aria-expanded="open"
          :aria-controls="open ? panelId : undefined"
          @focus="focused = true"
          @blur="focused = false"
          @keydown="onInputKey"
        />
        <span class="drp-hint" aria-hidden="true">{{ displayHint }}</span>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="open"
        :id="panelId"
        ref="panelRef"
        class="drp-panel"
        role="dialog"
        aria-label="选择日期范围"
        :style="panelStyle"
        @keydown="onPanelKey"
        @click.stop
      >
        <div class="drp-head">
          <span class="drp-title">选择日期范围</span>
          <span class="drp-preview" aria-live="polite">{{ draftText }}</span>
        </div>

        <DatePanel
          ref="datePanelRef"
          :model-value="draftStart"
          :min="min"
          :max="max"
          :show-footer="false"
          range
          :range-start="draftStart"
          :range-end="draftEnd"
          @select="selectDate"
        />

        <p class="drp-status" :class="{ 'drp-status--error': !!rangeError }" role="status">
          {{ rangeError || phaseHint }}
        </p>

        <div class="drp-foot">
          <button v-if="showClear" type="button" class="drp-btn drp-btn--link" @click="clear">
            清除
          </button>
          <span v-else class="drp-foot-spacer"></span>
          <div class="drp-actions">
            <button type="button" class="drp-btn drp-btn--ghost" @click="close(true)">取消</button>
            <button
              type="button"
              class="drp-btn drp-btn--primary"
              :disabled="!canConfirm"
              @click="confirm"
            >
              确认
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </FormField>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import Icon from './Icon.vue'
import FormField from './FormField.vue'
import DatePanel from './DatePanel.vue'
import { useAnchoredPanel } from './use-anchored-panel'
import { coerceDateString, parseDateString } from './date-picker-utils'
import { formatDateForDisplay } from './date-time-format'
import type { DateRangeValue } from './date-time-types'

const props = withDefaults(
  defineProps<{
    /** 日期范围合同：两个 YYYY-MM-DD；空字符串表示该端点未选择。 */
    modelValue?: DateRangeValue
    label?: string
    ariaLabel?: string
    placeholder?: string
    help?: string
    error?: string
    required?: boolean
    disabled?: boolean
    readonly?: boolean
    clearable?: boolean
    /** 合法日期边界，格式 YYYY-MM-DD。 */
    min?: string
    max?: string
  }>(),
  {
    modelValue: () => ({ start: '', end: '' }),
    label: '',
    ariaLabel: '',
    placeholder: '开始日期 — 结束日期',
    help: '',
    error: '',
    required: false,
    disabled: false,
    readonly: false,
    clearable: false,
    min: '',
    max: '',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: DateRangeValue]
  change: [value: DateRangeValue]
}>()

const uid = useId()
const inputId = `date-range-picker-${uid}`
const panelId = `${inputId}-panel`

const rootRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const datePanelRef = ref<InstanceType<typeof DatePanel> | null>(null)

const focused = ref(false)
const inputError = ref('')
const draftStart = ref('')
const draftEnd = ref('')

const { open, panelStyle, openPanel: openAnchoredPanel, closePanel } =
  useAnchoredPanel(triggerRef, panelRef, {
    width: 336,
    fallbackHeight: 470,
    zIndex: 1200,
    rootRef,
  })

const committedStart = computed(() => coerceDateString(props.modelValue?.start ?? ''))
const committedEnd = computed(() => coerceDateString(props.modelValue?.end ?? ''))

function rangeText(start: string, end: string): string {
  const from = formatDateForDisplay(start)
  const to = formatDateForDisplay(end)
  if (from && to) return `${from} — ${to}`
  if (from) return `${from} — …`
  return ''
}

const displayText = computed(() => rangeText(committedStart.value, committedEnd.value))
const displayHint = computed(() => (displayText.value ? '' : 'YYYY-MM-DD'))

const draftText = computed(() => rangeText(draftStart.value, draftEnd.value))
const phaseHint = computed(() => {
  if (!draftStart.value) return '请选择开始日期'
  if (!draftEnd.value) return '请选择结束日期'
  return '可重新点击开始或结束日期调整区间'
})
const rangeError = computed(() => {
  if (!draftStart.value || !draftEnd.value) return ''
  if (draftStart.value > draftEnd.value) return '开始日期不能晚于结束日期'
  if (!parseDateString(draftStart.value) || !parseDateString(draftEnd.value)) return '日期格式无效'
  return ''
})
const canConfirm = computed(() => !!draftStart.value && !!draftEnd.value && !rangeError.value)
const showClear = computed(
  () =>
    props.clearable &&
    !props.required &&
    !props.disabled &&
    !props.readonly &&
    (!!committedStart.value || !!committedEnd.value),
)

const formError = computed(() => props.error || inputError.value)
const descriptionIds = computed(() => {
  const ids: string[] = []
  if (props.help) ids.push(`${inputId}-help`)
  if (formError.value) ids.push(`${inputId}-error`)
  return ids.join(' ') || undefined
})

function emitValue(value: DateRangeValue): void {
  inputError.value = ''
  if (value.start === props.modelValue?.start && value.end === props.modelValue?.end) return
  emit('update:modelValue', value)
  emit('change', value)
}

function selectDate(value: string): void {
  if (!draftStart.value || draftEnd.value) {
    draftStart.value = value
    draftEnd.value = ''
    return
  }
  if (value < draftStart.value) {
    // 保持合同有序：把更早的日期调整为开始，原开始成为结束。
    draftEnd.value = draftStart.value
    draftStart.value = value
    return
  }
  draftEnd.value = value
}

async function openPanel(focusCalendar = false): Promise<void> {
  if (props.disabled || props.readonly) return
  inputError.value = ''
  draftStart.value = committedStart.value
  draftEnd.value =
    committedEnd.value && committedEnd.value >= committedStart.value ? committedEnd.value : ''
  await openAnchoredPanel()
  if (focusCalendar) datePanelRef.value?.focusActive()
}

function close(restoreFocus = false): void {
  closePanel(restoreFocus)
}

function confirm(): void {
  if (props.disabled || props.readonly || !canConfirm.value) return
  emitValue({ start: draftStart.value, end: draftEnd.value })
  close(true)
}

function clear(): void {
  if (props.disabled || props.readonly || props.required) return
  emitValue({ start: '', end: '' })
  close(true)
}

function onTriggerClick(): void {
  if (props.disabled || props.readonly) return
  if (!open.value) void openPanel()
}

function onInputKey(event: KeyboardEvent): void {
  if (event.isComposing || props.disabled || props.readonly) return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp' || event.key === 'Enter') {
    event.preventDefault()
    if (!open.value) void openPanel(true)
    return
  }
  if (event.key === 'Escape' && open.value) {
    event.preventDefault()
    close(true)
  }
}

function onPanelKey(event: KeyboardEvent): void {
  if (event.isComposing) return
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    close(true)
  }
}

watch(
  () => props.modelValue,
  () => {
    inputError.value = ''
  },
  { deep: true },
)

watch(
  () => props.disabled,
  (value) => {
    if (value) close()
  },
)
</script>

<style scoped>
.drp-control {
  width: 100%;
  min-width: 0;
}

.drp-trigger {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
  width: 100%;
  height: var(--kb-input-height);
  padding: 0 var(--kb-field-padding);
  border: 0;
  border-radius: var(--kb-field-radius);
  background: var(--kb-field-surface);
  color: var(--kb-foreground);
  cursor: pointer;
  transition: background-color var(--kb-control-motion) ease;
}

.drp-trigger:hover {
  background: var(--kb-field-hover);
}

.drp-trigger--focus,
.drp-trigger--open {
  background: var(--kb-field-active);
}

.drp-control--error .drp-trigger {
  background: var(--kb-field-error-bg);
}

.drp-control--disabled .drp-trigger {
  background: var(--kb-field-disabled);
  color: var(--kb-muted-foreground);
  cursor: not-allowed;
}

.drp-control--readonly .drp-trigger {
  background: var(--kb-field-readonly);
}

.drp-icon {
  flex-shrink: 0;
  color: var(--kb-muted-foreground);
}

.drp-text {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  outline: 0;
  font: inherit;
  font-size: var(--kb-fs-body-md);
  line-height: var(--kb-lh-body-md);
  color: inherit;
  cursor: inherit;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
}

.drp-text::placeholder {
  color: var(--kb-field-placeholder);
  opacity: 1;
}

.drp-text:focus,
.drp-text:focus-visible {
  border: 0;
  background: transparent;
  box-shadow: none;
  outline: 0;
}

.drp-hint {
  flex-shrink: 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}

.drp-panel {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-3);
  padding: var(--kb-space-4);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  background: var(--kb-popover);
  color: var(--kb-popover-foreground);
  box-shadow: var(--shadow-dropdown-3);
  overflow: auto;
  font-family: var(--font-sans);
}

.drp-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--kb-space-2);
}

.drp-title {
  font-size: var(--kb-fs-body-md);
  line-height: var(--kb-lh-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
}

.drp-preview {
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
  color: var(--kb-primary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.drp-status {
  margin: 0;
  min-height: var(--kb-lh-body-sm);
  font-size: var(--kb-fs-caption);
  line-height: var(--kb-lh-caption);
  color: var(--kb-muted-foreground);
}

.drp-status--error {
  color: var(--kb-state-error);
}

.drp-foot {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kb-space-2);
  padding-top: var(--kb-space-3);
  border-top: 1px solid var(--kb-border);
  background: var(--kb-popover);
}

.drp-foot-spacer {
  display: block;
}

.drp-actions {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
}

.drp-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 var(--kb-space-3);
  border: 0;
  border-radius: var(--kb-radius-sm);
  background: transparent;
  color: var(--kb-muted-foreground);
  font: inherit;
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--kb-control-motion) ease, color var(--kb-control-motion) ease;
}

.drp-btn:hover {
  background: var(--kb-menu-hover);
  color: var(--kb-foreground);
}

.drp-btn--link {
  padding: 0 var(--kb-space-1);
}

.drp-btn--primary {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

.drp-btn--primary:hover {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  filter: brightness(0.95);
}

.drp-btn--primary:disabled {
  background: var(--kb-field-disabled);
  color: var(--kb-muted-foreground);
  cursor: not-allowed;
  filter: none;
}

.drp-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 1px;
}

@media (forced-colors: active) {
  .drp-btn:focus-visible {
    outline: 2px solid Highlight;
  }
}
</style>
