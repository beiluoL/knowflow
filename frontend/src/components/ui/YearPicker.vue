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
      class="yp-control"
      :class="{
        'yp-control--error': !!formError,
        'yp-control--disabled': disabled,
        'yp-control--readonly': readonly,
      }"
      @click="onTriggerClick"
    >
      <div
        ref="triggerRef"
        class="yp-trigger"
        :class="{ 'yp-trigger--focus': focused, 'yp-trigger--open': open }"
      >
        <Icon name="calendar" :size="16" class="yp-icon" aria-hidden="true" />
        <input
          :id="inputId"
          v-model="text"
          class="yp-text"
          type="text"
          inputmode="numeric"
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
        <span class="yp-hint" aria-hidden="true">{{ displayHint }}</span>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="open"
        :id="panelId"
        ref="panelRef"
        class="yp-panel"
        role="dialog"
        aria-label="选择年份"
        :style="panelStyle"
        @keydown="onPanelKey"
        @click.stop
      >
        <div class="yp-head">
          <span class="yp-range" aria-live="polite">{{ pageLabel }}</span>
          <div class="yp-nav">
            <button type="button" class="yp-nav-btn" aria-label="上一个年份区间" @click="shiftPage(-1)">
              <Icon name="chevron-left" :size="16" />
            </button>
            <button type="button" class="yp-nav-btn" aria-label="下一个年份区间" @click="shiftPage(1)">
              <Icon name="chevron-right" :size="16" />
            </button>
          </div>
        </div>

        <div class="yp-grid" role="grid" :aria-label="pageLabel">
          <button
            v-for="year in pageYears"
            :key="year"
            type="button"
            role="gridcell"
            class="yp-year"
            :class="{
              'yp-year--selected': year === selectedYear,
              'yp-year--current': year === currentYear,
              'yp-year--disabled': isDisabled(year),
            }"
            :data-year="year"
            :tabindex="year === activeYear ? 0 : -1"
            :aria-selected="year === selectedYear"
            :aria-current="year === currentYear ? 'true' : undefined"
            :aria-disabled="isDisabled(year) ? 'true' : undefined"
            @click="choose(year)"
          >
            {{ year }}
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
import { parseLocalYear } from './date-time-format'

const PAGE_SIZE = 12

const props = withDefaults(
  defineProps<{
    /** 年份合同：YYYY；空字符串表示未选择。 */
    modelValue?: string
    label?: string
    ariaLabel?: string
    placeholder?: string
    help?: string
    error?: string
    required?: boolean
    disabled?: boolean
    readonly?: boolean
    /** 业务可设置的合法年份范围。 */
    minYear?: number
    maxYear?: number
  }>(),
  {
    modelValue: '',
    label: '',
    ariaLabel: '',
    placeholder: '选择年份',
    help: '',
    error: '',
    required: false,
    disabled: false,
    readonly: false,
    minYear: undefined,
    maxYear: undefined,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string]
}>()

const uid = useId()
const inputId = `year-picker-${uid}`
const panelId = `${inputId}-panel`

const rootRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)

const focused = ref(false)
const inputError = ref('')
const currentYear = new Date().getFullYear()

const minYear = computed(() =>
  Number.isInteger(props.minYear) ? (props.minYear as number) : currentYear - 100,
)
const maxYear = computed(() =>
  Number.isInteger(props.maxYear) ? (props.maxYear as number) : currentYear + 100,
)
const selectedYear = computed(() => parseLocalYear(props.modelValue))
const text = ref(selectedYear.value ? `${selectedYear.value}` : '')

const { open, panelStyle, openPanel: openAnchoredPanel, closePanel } =
  useAnchoredPanel(triggerRef, panelRef, {
    width: 300,
    fallbackHeight: 300,
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
const displayHint = computed(() => (text.value ? '' : 'YYYY'))
const activeYear = ref(selectedYear.value ?? currentYear)

const pageStart = computed(() => {
  const span = maxYear.value - minYear.value + 1
  if (span <= PAGE_SIZE) return minYear.value
  const offset = Math.max(0, activeYear.value - minYear.value)
  return minYear.value + Math.floor(offset / PAGE_SIZE) * PAGE_SIZE
})
const pageYears = computed(() =>
  Array.from({ length: PAGE_SIZE }, (_, index) => pageStart.value + index).filter(
    (year) => year >= minYear.value && year <= maxYear.value,
  ),
)
const pageLabel = computed(() => {
  const years = pageYears.value
  if (!years.length) return ''
  return `${years[0]} — ${years[years.length - 1]}`
})

function isDisabled(year: number): boolean {
  return year < minYear.value || year > maxYear.value
}

function clampYear(year: number): number {
  return Math.max(minYear.value, Math.min(maxYear.value, year))
}

function choose(year: number): void {
  if (isDisabled(year)) return
  activeYear.value = year
  emitValue(String(year))
  close(true)
}

function emitValue(value: string): void {
  text.value = value
  inputError.value = ''
  if (value === props.modelValue) return
  emit('update:modelValue', value)
  emit('change', value)
}

async function openPanel(): Promise<void> {
  if (props.disabled || props.readonly) return
  activeYear.value = selectedYear.value ?? clampYear(currentYear)
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

function normalizeYearInput(raw: string): number | null {
  const cleaned = raw.trim().replace(/年$/, '')
  if (!/^\d{4}$/.test(cleaned)) return null
  return Number(cleaned)
}

function commitText(showError: boolean): void {
  if (props.disabled || props.readonly) return
  const raw = text.value.trim()
  if (!raw) {
    if (props.required) {
      if (showError) inputError.value = '请选择年份'
      text.value = selectedYear.value ? `${selectedYear.value}` : ''
      return
    }
    emitValue('')
    return
  }
  const year = normalizeYearInput(raw)
  if (year === null) {
    if (showError) inputError.value = '请输入四位年份'
    else {
      text.value = selectedYear.value ? `${selectedYear.value}` : ''
      inputError.value = ''
    }
    return
  }
  if (isDisabled(year)) {
    if (showError) inputError.value = '该年份超出可选范围'
    else {
      text.value = selectedYear.value ? `${selectedYear.value}` : ''
      inputError.value = ''
    }
    return
  }
  emitValue(String(year))
}

function shiftPage(delta: number): void {
  const next = pageStart.value + delta * PAGE_SIZE
  activeYear.value = clampYear(
    Math.min(Math.max(next, minYear.value), maxYear.value),
  )
}

function moveActive(delta: number): void {
  activeYear.value = clampYear(activeYear.value + delta)
}

function focusActive(): void {
  panelRef.value
    ?.querySelector<HTMLElement>(`[data-year="${activeYear.value}"]`)
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
      moveActive(-3)
      focusActive()
      return
    case 'ArrowDown':
      event.preventDefault()
      moveActive(3)
      focusActive()
      return
    case 'PageUp':
      event.preventDefault()
      moveActive(-PAGE_SIZE)
      focusActive()
      return
    case 'PageDown':
      event.preventDefault()
      moveActive(PAGE_SIZE)
      focusActive()
      return
    case 'Home':
      event.preventDefault()
      activeYear.value = minYear.value
      focusActive()
      return
    case 'End':
      event.preventDefault()
      activeYear.value = maxYear.value
      focusActive()
      return
    case 'Enter':
      event.preventDefault()
      event.stopPropagation()
      choose(activeYear.value)
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
    const year = parseLocalYear(value)
    text.value = year ? `${year}` : ''
    if (year) activeYear.value = year
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
.yp-control {
  width: 100%;
  min-width: 0;
}

.yp-trigger {
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

.yp-trigger:hover {
  background: var(--kf-field-hover);
}

.yp-trigger--focus,
.yp-trigger--open {
  background: var(--kf-field-active);
}

.yp-control--error .yp-trigger {
  background: var(--kf-field-error-bg);
}

.yp-control--disabled .yp-trigger {
  background: var(--kf-field-disabled);
  color: var(--kf-muted-foreground);
  cursor: not-allowed;
}

.yp-control--readonly .yp-trigger {
  background: var(--kf-field-readonly);
}

.yp-icon {
  flex-shrink: 0;
  color: var(--kf-muted-foreground);
}

.yp-text {
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

.yp-text::placeholder {
  color: var(--kf-field-placeholder);
  opacity: 1;
}

.yp-text:focus,
.yp-text:focus-visible {
  border: 0;
  background: transparent;
  box-shadow: none;
  outline: 0;
}

.yp-control--disabled .yp-text {
  cursor: not-allowed;
}

.yp-hint {
  flex-shrink: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}

.yp-panel {
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

.yp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-2);
}

.yp-range {
  font-size: var(--kf-fs-body-md);
  line-height: var(--kf-lh-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.yp-nav {
  display: flex;
  align-items: center;
  gap: var(--kf-space-1);
}

.yp-nav-btn {
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

.yp-nav-btn:hover {
  background: var(--kf-menu-hover);
  color: var(--kf-foreground);
}

.yp-nav-btn:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: -2px;
}

.yp-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--kf-space-2);
}

.yp-year {
  position: relative;
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

.yp-year:hover {
  background: var(--kf-menu-hover);
}

.yp-year--current {
  color: var(--kf-primary);
  font-weight: 600;
}

.yp-year--selected,
.yp-year--selected:hover {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  font-weight: 600;
}

.yp-year--disabled,
.yp-year--disabled:hover {
  background: transparent;
  color: var(--kf-muted-foreground);
  opacity: 0.38;
  cursor: not-allowed;
}

.yp-year:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: -2px;
}

.yp-year--selected:focus-visible {
  outline-color: var(--kf-primary-foreground);
}

@media (forced-colors: active) {
  .yp-nav-btn:focus-visible,
  .yp-year:focus-visible {
    outline: 2px solid Highlight;
  }
}
</style>
