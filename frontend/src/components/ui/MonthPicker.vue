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
      class="mp-control"
      :class="{
        'mp-control--error': !!formError,
        'mp-control--disabled': disabled,
        'mp-control--readonly': readonly,
      }"
      @click="onTriggerClick"
    >
      <div
        ref="triggerRef"
        class="mp-trigger"
        :class="{ 'mp-trigger--focus': focused, 'mp-trigger--open': open }"
      >
        <Icon name="calendar" :size="16" class="mp-icon" aria-hidden="true" />
        <input
          :id="inputId"
          v-model="text"
          class="mp-text"
          type="text"
          autocomplete="off"
          :placeholder="placeholder"
          :disabled="disabled"
          :readonly="readonly"
          :required="required"
          :aria-label="ariaLabel || (!label ? placeholder : undefined)"
          :aria-invalid="formError ? 'true' : undefined"
          :aria-describedby="descriptionIds"
          aria-haspopup="dialog"
          :aria-expanded="open"
          :aria-controls="open ? panelId : undefined"
          @focus="focused = true"
          @blur="onBlur"
          @keydown="onInputKey"
        />
        <span class="mp-hint" aria-hidden="true">{{ displayHint }}</span>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="open"
        :id="panelId"
        ref="panelRef"
        class="mp-panel"
        role="dialog"
        aria-label="选择年月"
        :style="panelStyle"
        @keydown="onPanelKey"
        @click.stop
      >
        <div class="mp-head">
          <button type="button" class="mp-nav-btn" aria-label="上一年" @click="shiftYear(-1)">
            <Icon name="chevron-left" :size="16" />
          </button>
          <span class="mp-year" aria-live="polite">{{ viewYear }} 年</span>
          <button type="button" class="mp-nav-btn" aria-label="下一年" @click="shiftYear(1)">
            <Icon name="chevron-right" :size="16" />
          </button>
        </div>

        <div class="mp-grid" role="grid" :aria-label="`${viewYear} 年`">
          <button
            v-for="month in months"
            :key="month"
            type="button"
            role="gridcell"
            class="mp-month"
            :class="{
              'mp-month--selected': isSelected(month),
              'mp-month--current': isCurrent(month),
              'mp-month--disabled': isDisabled(month),
            }"
            :data-month="month"
            :tabindex="isActive(month) ? 0 : -1"
            :aria-selected="isSelected(month)"
            :aria-current="isCurrent(month) ? 'true' : undefined"
            :aria-disabled="isDisabled(month) ? 'true' : undefined"
            @click="choose(month)"
          >
            {{ month }}月
          </button>
        </div>
      </div>
    </Teleport>
  </FormField>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import Icon from './Icon.vue'
import FormField from './FormField.vue'
import { useAnchoredPanel } from './use-anchored-panel'
import { formatMonthForDisplay, parseLocalMonth } from './date-time-format'

const months = Array.from({ length: 12 }, (_, index) => index + 1)
const now = new Date()

const props = withDefaults(
  defineProps<{
    /** 年月合同：YYYY-MM；空字符串表示未选择。 */
    modelValue?: string
    label?: string
    ariaLabel?: string
    placeholder?: string
    help?: string
    error?: string
    required?: boolean
    disabled?: boolean
    readonly?: boolean
    /** 业务可设置的合法年月边界，格式 YYYY-MM。 */
    minMonth?: string
    maxMonth?: string
  }>(),
  {
    modelValue: '',
    label: '',
    ariaLabel: '',
    placeholder: '选择年月',
    help: '',
    error: '',
    required: false,
    disabled: false,
    readonly: false,
    minMonth: '',
    maxMonth: '',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string]
}>()

const uid = useId()
const inputId = `month-picker-${uid}`
const panelId = `${inputId}-panel`

const rootRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)

const focused = ref(false)
const inputError = ref('')

const selected = computed(() => parseLocalMonth(props.modelValue))
const minParts = computed(() => parseLocalMonth(props.minMonth) ?? { year: now.getFullYear() - 50, month: 1 })
const maxParts = computed(() => parseLocalMonth(props.maxMonth) ?? { year: now.getFullYear() + 50, month: 12 })
const minMonth = computed(() => minParts.value.year * 12 + (minParts.value.month - 1))
const maxMonth = computed(() => maxParts.value.year * 12 + (maxParts.value.month - 1))

const viewYear = ref(selected.value?.year ?? now.getFullYear())
const activeMonth = ref(selected.value?.month ?? now.getMonth() + 1)
const text = ref(selected.value ? formatMonthForDisplay(selected.value) : '')

const { open, panelStyle, openPanel: openAnchoredPanel, closePanel } =
  useAnchoredPanel(triggerRef, panelRef, {
    width: 320,
    fallbackHeight: 320,
    zIndex: 1200,
    rootRef,
  })

const formError = computed(() => props.error || inputError.value)
const descriptionIds = computed(() => {
  const ids: string[] = []
  if (props.help) ids.push(`${inputId}-help`)
  if (formError.value) ids.push(`${inputId}-error`)
  return ids.join(' ') || undefined
})
const displayHint = computed(() => (text.value ? '' : 'YYYY-MM'))

function monthKey(year: number, month: number): number {
  return year * 12 + (month - 1)
}

function isDisabled(month: number): boolean {
  const key = monthKey(viewYear.value, month)
  return key < minMonth.value || key > maxMonth.value
}

function isSelected(month: number): boolean {
  return (
    !!selected.value &&
    selected.value.year === viewYear.value &&
    selected.value.month === month
  )
}

function isCurrent(month: number): boolean {
  return viewYear.value === now.getFullYear() && month === now.getMonth() + 1
}

function isActive(month: number): boolean {
  return activeMonth.value === month
}

function emitValue(value: string): void {
  const parsed = value ? parseLocalMonth(value) : null
  text.value = parsed ? formatMonthForDisplay(parsed) : ''
  inputError.value = ''
  if (value === props.modelValue) return
  emit('update:modelValue', value)
  emit('change', value)
}

function choose(month: number): void {
  if (isDisabled(month)) return
  activeMonth.value = month
  emitValue(`${viewYear.value}-${String(month).padStart(2, '0')}`)
  close(true)
}

async function openPanel(): Promise<void> {
  if (props.disabled || props.readonly) return
  if (selected.value) {
    viewYear.value = selected.value.year
    activeMonth.value = selected.value.month
  } else {
    viewYear.value = now.getFullYear()
    activeMonth.value = now.getMonth() + 1
  }
  await openAnchoredPanel()
}

function close(restoreFocus = false): void {
  closePanel(restoreFocus)
}

function onTriggerClick(): void {
  if (props.disabled || props.readonly) return
  if (!open.value) void openPanel()
}

function onBlur(): void {
  focused.value = false
  commitText(false)
}

function normalizeMonthInput(raw: string): { year: number; month: number } | null {
  const cleaned = raw.trim()
  const direct = parseLocalMonth(cleaned)
  if (direct) return direct
  const chinese = /^(\d{4})\s*年\s*(\d{1,2})\s*月?$/.exec(cleaned)
  if (!chinese) return null
  const month = Number(chinese[2])
  if (month < 1 || month > 12) return null
  return { year: Number(chinese[1]), month }
}

function commitText(showError: boolean): void {
  if (props.disabled || props.readonly) return
  const raw = text.value.trim()
  if (!raw) {
    if (props.required) {
      if (showError) inputError.value = '请选择年月'
      text.value = selected.value ? formatMonthForDisplay(selected.value) : ''
      return
    }
    emitValue('')
    return
  }
  const parsed = normalizeMonthInput(raw)
  if (!parsed) {
    if (showError) inputError.value = '请输入有效年月（YYYY-MM）'
    else {
      text.value = selected.value ? formatMonthForDisplay(selected.value) : ''
      inputError.value = ''
    }
    return
  }
  const key = monthKey(parsed.year, parsed.month)
  if (key < minMonth.value || key > maxMonth.value) {
    if (showError) inputError.value = '该年月超出可选范围'
    else {
      text.value = selected.value ? formatMonthForDisplay(selected.value) : ''
      inputError.value = ''
    }
    return
  }
  emitValue(`${parsed.year}-${String(parsed.month).padStart(2, '0')}`)
}

function shiftYear(delta: number): void {
  const next = viewYear.value + delta
  if (monthKey(next, activeMonth.value) < minMonth.value || monthKey(next, activeMonth.value) > maxMonth.value) {
    return
  }
  viewYear.value = next
}

function moveActive(delta: number): void {
  const key = Math.max(minMonth.value, Math.min(maxMonth.value, monthKey(viewYear.value, activeMonth.value) + delta))
  viewYear.value = Math.floor(key / 12)
  activeMonth.value = (key % 12) + 1
}

function focusActive(): void {
  panelRef.value
    ?.querySelector<HTMLElement>(`[data-month="${activeMonth.value}"]`)
    ?.focus({ preventScroll: true })
}

function onInputKey(event: KeyboardEvent): void {
  if (event.isComposing || props.disabled || props.readonly) return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    if (!open.value) void openPanel()
    else focusActive()
    return
  }
  if (event.key === 'Enter') {
    event.preventDefault()
    commitText(true)
    return
  }
  if (event.key === 'Escape' && open.value) {
    event.preventDefault()
    close(true)
  }
}

function onPanelKey(event: KeyboardEvent): void {
  if (event.isComposing) return
  switch (event.key) {
    case 'ArrowLeft':
      event.preventDefault()
      moveActive(-1)
      focusActive()
      return
    case 'ArrowRight':
      event.preventDefault()
      moveActive(1)
      focusActive()
      return
    case 'ArrowUp':
      event.preventDefault()
      moveActive(-4)
      focusActive()
      return
    case 'ArrowDown':
      event.preventDefault()
      moveActive(4)
      focusActive()
      return
    case 'PageUp':
      event.preventDefault()
      moveActive(-12)
      focusActive()
      return
    case 'PageDown':
      event.preventDefault()
      moveActive(12)
      focusActive()
      return
    case 'Home':
      event.preventDefault()
      viewYear.value = minParts.value.year
      activeMonth.value = minParts.value.month
      focusActive()
      return
    case 'End':
      event.preventDefault()
      viewYear.value = maxParts.value.year
      activeMonth.value = maxParts.value.month
      focusActive()
      return
    case 'Enter':
      event.preventDefault()
      event.stopPropagation()
      choose(activeMonth.value)
      return
    case 'Escape':
      event.preventDefault()
      event.stopPropagation()
      close(true)
      return
    default:
      return
  }
}

watch(
  () => props.modelValue,
  (value) => {
    inputError.value = ''
    const parsed = parseLocalMonth(value)
    text.value = parsed ? formatMonthForDisplay(parsed) : ''
    if (parsed) {
      viewYear.value = parsed.year
      activeMonth.value = parsed.month
    }
  },
)

watch(
  () => props.disabled,
  (value) => {
    if (value) close()
  },
)
</script>

<style scoped>
.mp-control {
  width: 100%;
  min-width: 0;
}

.mp-trigger {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  width: 100%;
  height: var(--kf-input-height);
  padding: 0 var(--kf-field-padding);
  border: 0;
  border-radius: var(--kf-field-radius);
  background: var(--kf-field-surface);
  color: var(--kf-foreground);
  cursor: pointer;
  transition: background-color var(--kf-control-motion) ease;
}

.mp-trigger:hover {
  background: var(--kf-field-hover);
}

.mp-trigger--focus,
.mp-trigger--open {
  background: var(--kf-field-active);
}

.mp-control--error .mp-trigger {
  background: var(--kf-field-error-bg);
}

.mp-control--disabled .mp-trigger {
  background: var(--kf-field-disabled);
  color: var(--kf-muted-foreground);
  cursor: not-allowed;
}

.mp-control--readonly .mp-trigger {
  background: var(--kf-field-readonly);
}

.mp-icon {
  flex-shrink: 0;
  color: var(--kf-muted-foreground);
}

.mp-text {
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
  font-size: var(--kf-fs-body-md);
  line-height: var(--kf-lh-body-md);
  color: inherit;
  cursor: inherit;
  font-variant-numeric: tabular-nums;
}

.mp-text::placeholder {
  color: var(--kf-field-placeholder);
  opacity: 1;
}

.mp-text:focus,
.mp-text:focus-visible {
  border: 0;
  background: transparent;
  box-shadow: none;
  outline: 0;
}

.mp-control--disabled .mp-text {
  cursor: not-allowed;
}

.mp-hint {
  flex-shrink: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}

.mp-panel {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
  background: var(--kf-popover);
  color: var(--kf-popover-foreground);
  box-shadow: var(--shadow-dropdown-3);
  overflow: auto;
  font-family: var(--font-sans);
}

.mp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-2);
}

.mp-year {
  font-size: var(--kf-fs-body-md);
  line-height: var(--kf-lh-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
  font-variant-numeric: tabular-nums;
}

.mp-nav-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: var(--kf-radius-sm);
  background: transparent;
  color: var(--kf-muted-foreground);
  cursor: pointer;
  transition: background-color var(--kf-control-motion) ease, color var(--kf-control-motion) ease;
}

.mp-nav-btn:hover {
  background: var(--kf-menu-hover);
  color: var(--kf-foreground);
}

.mp-nav-btn:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: -2px;
}

.mp-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--kf-space-2);
}

.mp-month {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  padding: 0;
  border: 0;
  border-radius: var(--kf-radius-sm);
  background: transparent;
  color: var(--kf-foreground);
  font: inherit;
  font-size: var(--kf-fs-body-sm);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: background-color var(--kf-control-motion) ease, color var(--kf-control-motion) ease;
}

.mp-month:hover {
  background: var(--kf-menu-hover);
}

.mp-month--current {
  color: var(--kf-primary);
  font-weight: 600;
}

.mp-month--selected,
.mp-month--selected:hover {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  font-weight: 600;
}

.mp-month--disabled,
.mp-month--disabled:hover {
  background: transparent;
  color: var(--kf-muted-foreground);
  opacity: 0.38;
  cursor: not-allowed;
}

.mp-month:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: -2px;
}

.mp-month--selected:focus-visible {
  outline-color: var(--kf-primary-foreground);
}

@media (forced-colors: active) {
  .mp-nav-btn:focus-visible,
  .mp-month:focus-visible {
    outline: 2px solid Highlight;
  }
}
</style>
