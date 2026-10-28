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
      class="dp-control"
      :class="{
        'dp-control--error': !!formError,
        'dp-control--disabled': disabled,
        'dp-control--readonly': readonly,
      }"
      @click="onTriggerClick"
    >
      <div
        ref="triggerRef"
        class="dp-trigger"
        :class="{ 'dp-trigger--focus': focused, 'dp-trigger--open': open }"
      >
        <Icon name="calendar" :size="16" class="dp-icon" aria-hidden="true" />
        <input
          :id="inputId"
          v-model="text"
          class="dp-text"
          type="text"
          inputmode="numeric"
          autocomplete="off"
          :placeholder="placeholder"
          :disabled="disabled"
          :readonly="readonly"
          :required="required"
          :pattern="required ? '\\d{4}-\\d{2}-\\d{2}' : undefined"
          :aria-label="ariaLabel || (!label ? placeholder : undefined)"
          :aria-invalid="formError ? 'true' : undefined"
          :aria-describedby="descriptionIds"
          :aria-haspopup="'dialog'"
          :aria-expanded="open"
          :aria-controls="open ? panelId : undefined"
          @focus="onInputFocus"
          @blur="onInputBlur"
          @input="onTextInput"
          @keydown="onInputKey"
        />
        <span class="dp-hint" aria-hidden="true">{{ displayHint }}</span>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="open"
        :id="panelId"
        ref="panelRef"
        class="dp-panel"
        role="dialog"
        aria-label="选择日期"
        :style="panelStyle"
        @keydown="onPanelKey"
        @click.stop
      >
        <DatePanel
          ref="datePanelRef"
          :model-value="selectedDate"
          :min="minDate"
          :max="maxDate"
          :locale="locale"
          :week-starts-on="weekStartsOn"
          :show-clear="showClear"
          @select="selectDate"
          @clear="clear"
        />
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
import { coerceDateString, parseDateString, todayDateString } from './date-picker-utils'

const PANEL_WIDTH = 336

const props = withDefaults(
  defineProps<{
    /** 日期合同：YYYY-MM-DD；空字符串表示未选择。 */
    modelValue?: string
    label?: string
    ariaLabel?: string
    placeholder?: string
    help?: string
    error?: string
    required?: boolean
    disabled?: boolean
    readonly?: boolean
    clearable?: boolean
    min?: string
    max?: string
    locale?: string
    weekStartsOn?: number
  }>(),
  {
    modelValue: '',
    label: '',
    ariaLabel: '',
    placeholder: '选择日期',
    help: '',
    error: '',
    required: false,
    disabled: false,
    readonly: false,
    clearable: false,
    min: '',
    max: '',
    locale: 'zh-CN',
    weekStartsOn: 0,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string]
}>()

const uid = useId()
const inputId = `date-picker-${uid}`
const panelId = `${inputId}-panel`

const rootRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const datePanelRef = ref<InstanceType<typeof DatePanel> | null>(null)

const today = ref(todayDateString())
const minDate = computed(() => coerceDateString(props.min))
const maxDate = computed(() => coerceDateString(props.max))
const selectedDate = computed(() => coerceDateString(props.modelValue))

function isDisabledDate(value: string): boolean {
  if (!parseDateString(value)) return true
  if (minDate.value && value < minDate.value) return true
  if (maxDate.value && value > maxDate.value) return true
  return false
}

function isSelectable(value: string): boolean {
  return !!parseDateString(value) && !isDisabledDate(value)
}

function initialDate(): string {
  const current = selectedDate.value
  if (current) return current
  const now = today.value
  if (isSelectable(now)) return now
  if (minDate.value && now < minDate.value) return minDate.value
  if (maxDate.value && now > maxDate.value) return maxDate.value
  return now
}

const focused = ref(false)
const text = ref(selectedDate.value)
const inputError = ref('')
const { open, panelStyle, position, openPanel: openAnchoredPanel, closePanel } =
  useAnchoredPanel(triggerRef, panelRef, {
    width: PANEL_WIDTH,
    fallbackHeight: 392,
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
const displayHint = computed(() => (selectedDate.value ? '' : 'YYYY-MM-DD'))
const weekStartsOn = computed(() => {
  const raw = Number(props.weekStartsOn)
  return Number.isInteger(raw) && raw >= 0 && raw <= 6 ? raw : 0
})
const showClear = computed(
  () =>
    props.clearable &&
    !props.required &&
    !props.disabled &&
    !props.readonly &&
    !!selectedDate.value,
)

function emitValue(value: string): void {
  const next = coerceDateString(value)
  text.value = next
  inputError.value = ''
  if (next === selectedDate.value) return
  emit('update:modelValue', next)
  emit('change', next)
}

function syncPanel(value: string): void {
  datePanelRef.value?.syncTo(value)
}

function focusActiveDay(): void {
  datePanelRef.value?.focusActive()
}

async function openPanel(focusGrid = false): Promise<void> {
  if (props.disabled || props.readonly) return
  today.value = todayDateString()
  await openAnchoredPanel()
  syncPanel(initialDate())
  position()
  if (focusGrid) focusActiveDay()
}

function close(restoreFocus = false): void {
  closePanel(restoreFocus)
}

function onTriggerClick(): void {
  if (props.disabled || props.readonly) return
  if (!open.value) openPanel(false)
}

function onInputFocus(): void {
  focused.value = true
}

function onInputBlur(): void {
  focused.value = false
  commitText(false)
}

function onTextInput(): void {
  inputError.value = ''
  if (parseDateString(text.value.trim())) syncPanel(text.value.trim())
}

function commitText(showError = true): void {
  if (props.disabled || props.readonly) return
  const raw = text.value.trim()
  if (!raw) {
    if (props.required) {
      if (showError) inputError.value = '请选择日期'
      text.value = selectedDate.value
      return
    }
    emitValue('')
    return
  }
  if (!parseDateString(raw)) {
    if (showError) {
      inputError.value = '请输入有效日期（YYYY-MM-DD）'
    } else {
      text.value = selectedDate.value
      inputError.value = ''
    }
    return
  }
  if (isDisabledDate(raw)) {
    if (showError) {
      inputError.value = '该日期超出可选范围'
    } else {
      text.value = selectedDate.value
      inputError.value = ''
    }
    return
  }
  emitValue(raw)
}

function selectDate(value: string): void {
  if (isDisabledDate(value)) return
  emitValue(value)
  close(true)
}

function clear(): void {
  if (props.disabled || props.readonly || props.required) return
  emitValue('')
  close(true)
}

function onInputKey(event: KeyboardEvent): void {
  if (event.isComposing || props.disabled || props.readonly) return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    if (!open.value) openPanel(true)
    else focusActiveDay()
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
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    close(true)
  }
}

watch(
  () => props.modelValue,
  (value) => {
    const next = coerceDateString(value)
    text.value = next
    inputError.value = ''
    if (open.value) syncPanel(next)
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
.dp-control {
  width: 100%;
  min-width: 0;
}

.dp-trigger {
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

.dp-trigger:hover {
  background: var(--kf-field-hover);
}

.dp-trigger--focus,
.dp-trigger--open {
  background: var(--kf-field-active);
}

.dp-control--error .dp-trigger {
  background: var(--kf-field-error-bg);
}

.dp-control--disabled .dp-trigger {
  background: var(--kf-field-disabled);
  color: var(--kf-muted-foreground);
  cursor: not-allowed;
}

.dp-control--readonly .dp-trigger {
  background: var(--kf-field-readonly);
}

.dp-icon {
  flex-shrink: 0;
  color: var(--kf-muted-foreground);
}

.dp-text {
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
}

.dp-text::placeholder {
  color: var(--kf-field-placeholder);
  opacity: 1;
}

.dp-text:focus,
.dp-text:focus-visible {
  border: 0;
  background: transparent;
  box-shadow: none;
  outline: 0;
}

.dp-control--disabled .dp-text {
  cursor: not-allowed;
}

.dp-hint {
  flex-shrink: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}

.dp-panel {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
  background: var(--kf-popover);
  color: var(--kf-popover-foreground);
  box-shadow: var(--shadow-dropdown-3);
  overflow: auto;
  font-family: var(--font-sans);
}
</style>
