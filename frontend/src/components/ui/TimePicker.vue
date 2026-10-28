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
      class="tp-control"
      :class="{
        'tp-control--error': !!formError,
        'tp-control--disabled': disabled,
        'tp-control--readonly': readonly,
      }"
      @click="onTriggerClick"
    >
      <div
        ref="triggerRef"
        class="tp-trigger"
        :class="{ 'tp-trigger--focus': focused, 'tp-trigger--open': open }"
      >
        <Icon name="clock" :size="16" class="tp-icon" aria-hidden="true" />
        <input
          :id="inputId"
          v-model="text"
          class="tp-text"
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
          @focus="onInputFocus"
          @blur="onInputBlur"
          @keydown="onInputKey"
        />
        <span class="tp-hint" aria-hidden="true">{{ displayHint }}</span>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="open"
        :id="panelId"
        ref="panelRef"
        class="tp-panel"
        role="dialog"
        aria-label="选择时间"
        :style="panelStyle"
        @keydown="onPanelKey"
        @click.stop
      >
        <div class="tp-head">
          <span class="tp-title">选择时间</span>
          <span class="tp-preview" aria-live="polite">{{ draftText }}</span>
        </div>

        <TimeColumns
          ref="columnsRef"
          :hour="draftHour"
          :minute="draftMinute"
          :second="draftSecond"
          :precision="precision"
          :show-seconds="showSeconds"
          @update:hour="draftHour = $event"
          @update:minute="draftMinute = $event"
          @update:second="draftSecond = $event"
        />

        <p v-if="rangeError" class="tp-range" role="alert">{{ rangeError }}</p>

        <div class="tp-foot">
          <button
            v-if="showClear"
            type="button"
            class="tp-btn tp-btn--link"
            @click="clear"
          >
            清除
          </button>
          <span v-else class="tp-foot-spacer"></span>
          <div class="tp-actions">
            <button type="button" class="tp-btn tp-btn--ghost" @click="close(true)">取消</button>
            <button
              type="button"
              class="tp-btn tp-btn--primary"
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
import { computed, nextTick, ref, useId, watch } from 'vue'
import Icon from './Icon.vue'
import FormField from './FormField.vue'
import TimeColumns from './TimeColumns.vue'
import { useAnchoredPanel } from './use-anchored-panel'
import {
  formatTimeString,
  hasMeaningfulSeconds,
  isTimeInRange,
  parseTimeInput,
  parseTimeString,
  type TimePrecision,
} from './time-picker-utils'

const props = withDefaults(
  defineProps<{
    /** 时间合同：HH:mm；precision="second" 时为 HH:mm:ss；空字符串表示未选择。 */
    modelValue?: string
    precision?: TimePrecision
    label?: string
    ariaLabel?: string
    placeholder?: string
    help?: string
    error?: string
    required?: boolean
    disabled?: boolean
    readonly?: boolean
    clearable?: boolean
    /** 最小/最大时间，格式同 modelValue。 */
    min?: string
    max?: string
  }>(),
  {
    modelValue: '',
    precision: 'minute',
    label: '',
    ariaLabel: '',
    placeholder: '选择时间',
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
  'update:modelValue': [value: string]
  change: [value: string]
}>()

const uid = useId()
const inputId = `time-picker-${uid}`
const panelId = `${inputId}-panel`

const rootRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const columnsRef = ref<InstanceType<typeof TimeColumns> | null>(null)

const focused = ref(false)
const inputError = ref('')

const parsedValue = computed(() => parseTimeString(props.modelValue))
const effectivePrecision = computed<TimePrecision>(() =>
  props.precision === 'second' || (parsedValue.value && hasMeaningfulSeconds(parsedValue.value))
    ? 'second'
    : 'minute',
)
const showSeconds = computed(() => effectivePrecision.value === 'second')
const text = ref('')
const draftHour = ref(0)
const draftMinute = ref(0)
const draftSecond = ref(0)

const { open, panelStyle, openPanel: openAnchoredPanel, closePanel } =
  useAnchoredPanel(triggerRef, panelRef, {
    width: () => (showSeconds.value ? 288 : 240),
    fallbackHeight: 330,
    zIndex: 1200,
    rootRef,
  })

function displayOf(parts: ReturnType<typeof parseTimeString>): string {
  if (!parts) return ''
  return formatTimeString(parts, effectivePrecision.value)
}

function syncText(): void {
  text.value = displayOf(parsedValue.value)
}

syncText()

const formError = computed(() => props.error || inputError.value)
const descriptionIds = computed(() => {
  const ids: string[] = []
  if (props.help) ids.push(`${inputId}-help`)
  if (formError.value) ids.push(`${inputId}-error`)
  return ids.join(' ') || undefined
})
const displayHint = computed(() =>
  parsedValue.value ? '' : showSeconds.value ? 'HH:mm:ss' : 'HH:mm',
)
const showClear = computed(
  () =>
    props.clearable &&
    !props.required &&
    !props.disabled &&
    !props.readonly &&
    !!parsedValue.value,
)

const draftText = computed(() =>
  formatTimeString(
    { hour: draftHour.value, minute: draftMinute.value, second: draftSecond.value },
    effectivePrecision.value,
  ),
)
const draftParts = computed(() => ({
  hour: draftHour.value,
  minute: draftMinute.value,
  second: draftSecond.value,
}))
const rangeError = computed(() =>
  isTimeInRange(draftParts.value, props.min, props.max) ? '' : '该时间超出可选范围',
)
const canConfirm = computed(() => !rangeError.value)

function emitValue(value: string): void {
  text.value = value
  inputError.value = ''
  if (value === props.modelValue) return
  emit('update:modelValue', value)
  emit('change', value)
}

function seedDraft(): void {
  const parts = parseTimeString(props.modelValue)
  const fallback = parts ?? {
    hour: new Date().getHours(),
    minute: new Date().getMinutes(),
    second: 0,
  }
  const seed = isTimeInRange(fallback, props.min, props.max)
    ? fallback
    : parseTimeString(props.min) ?? parseTimeString(props.max) ?? fallback
  draftHour.value = seed.hour
  draftMinute.value = seed.minute
  draftSecond.value = seed.second
}

async function openPanel(focusColumns = false): Promise<void> {
  if (props.disabled || props.readonly) return
  seedDraft()
  await openAnchoredPanel()
  if (focusColumns) {
    columnsRef.value?.focusActive()
  }
}

function close(restoreFocus = false): void {
  closePanel(restoreFocus)
}

function confirm(): void {
  if (props.disabled || props.readonly || !canConfirm.value) return
  emitValue(formatTimeString(draftParts.value, effectivePrecision.value))
  close(true)
}

function clear(): void {
  if (props.disabled || props.readonly || props.required) return
  emitValue('')
  close(true)
}

function onTriggerClick(): void {
  if (props.disabled || props.readonly) return
  if (!open.value) void openPanel(false)
}

function onInputFocus(): void {
  focused.value = true
}

function onInputBlur(): void {
  focused.value = false
  commitText(false)
}

function commitText(showError = true): void {
  if (props.disabled || props.readonly) return
  const raw = text.value.trim()
  if (!raw) {
    if (props.required) {
      if (showError) inputError.value = '请选择时间'
      text.value = displayOf(parsedValue.value)
      return
    }
    emitValue('')
    return
  }
  const loose = parseTimeInput(raw)
  if (!loose || !parseTimeString(formatTimeString(loose, 'second'))) {
    if (showError) inputError.value = '请输入有效时间（HH:mm）'
    else text.value = displayOf(parsedValue.value)
    if (!showError) inputError.value = ''
    return
  }
  if (!isTimeInRange(loose, props.min, props.max)) {
    if (showError) inputError.value = '该时间超出可选范围'
    else {
      text.value = displayOf(parsedValue.value)
      inputError.value = ''
    }
    return
  }
  const precision: TimePrecision =
    props.precision === 'second' || hasMeaningfulSeconds(loose) ? 'second' : 'minute'
  emitValue(formatTimeString(loose, precision))
}

function onInputKey(event: KeyboardEvent): void {
  if (event.isComposing || props.disabled || props.readonly) return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    if (!open.value) void openPanel(true)
    else columnsRef.value?.focusActive()
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
    return
  }
  if (event.key === 'Enter') {
    event.preventDefault()
    event.stopPropagation()
    confirm()
  }
}

watch(
  () => props.modelValue,
  (value) => {
    inputError.value = ''
    if (parseTimeString(value)) {
      const parts = parseTimeString(value)!
      text.value = formatTimeString(
        parts,
        props.precision === 'second' || hasMeaningfulSeconds(parts) ? 'second' : 'minute',
      )
    } else {
      text.value = ''
    }
    if (open.value) seedDraft()
  },
)

watch(
  () => props.disabled,
  (value) => {
    if (value) close()
  },
)

watch(
  () => open.value,
  (value) => {
    if (!value) return
    void nextTick(() => columnsRef.value?.focusActive())
  },
)
</script>

<style scoped>
.tp-control {
  width: 100%;
  min-width: 0;
}

.tp-trigger {
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

.tp-trigger:hover {
  background: var(--kf-field-hover);
}

.tp-trigger--focus,
.tp-trigger--open {
  background: var(--kf-field-active);
}

.tp-control--error .tp-trigger {
  background: var(--kf-field-error-bg);
}

.tp-control--disabled .tp-trigger {
  background: var(--kf-field-disabled);
  color: var(--kf-muted-foreground);
  cursor: not-allowed;
}

.tp-control--readonly .tp-trigger {
  background: var(--kf-field-readonly);
}

.tp-icon {
  flex-shrink: 0;
  color: var(--kf-muted-foreground);
}

.tp-text {
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

.tp-text::placeholder {
  color: var(--kf-field-placeholder);
  opacity: 1;
}

.tp-text:focus,
.tp-text:focus-visible {
  border: 0;
  background: transparent;
  box-shadow: none;
  outline: 0;
}

.tp-control--disabled .tp-text {
  cursor: not-allowed;
}

.tp-hint {
  flex-shrink: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}

.tp-panel {
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

.tp-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--kf-space-2);
}

.tp-title {
  font-size: var(--kf-fs-body-md);
  line-height: var(--kf-lh-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
}

.tp-preview {
  font-size: var(--kf-fs-body-md);
  line-height: var(--kf-lh-body-md);
  font-variant-numeric: tabular-nums;
  color: var(--kf-primary);
  font-weight: 600;
}

.tp-range {
  margin: 0;
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-state-error);
}

.tp-foot {
  position: sticky;
  bottom: calc(-1 * var(--kf-space-4));
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-2);
  margin: 0 calc(-1 * var(--kf-space-4)) calc(-1 * var(--kf-space-4));
  padding: var(--kf-space-3) var(--kf-space-4);
  border-top: 1px solid var(--kf-border);
  background: var(--kf-popover);
}

.tp-foot-spacer {
  display: block;
}

.tp-actions {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  margin-left: auto;
}

.tp-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 var(--kf-space-3);
  border: 0;
  border-radius: var(--kf-radius-sm);
  background: transparent;
  color: var(--kf-foreground);
  font: inherit;
  font-size: var(--kf-fs-body-sm);
  line-height: 1;
  cursor: pointer;
  transition: background-color var(--kf-control-motion) ease, color var(--kf-control-motion) ease;
}

.tp-btn:hover {
  background: var(--kf-menu-hover);
}

.tp-btn--link {
  height: auto;
  padding: var(--kf-space-1) var(--kf-space-2);
  color: var(--kf-muted-foreground);
}

.tp-btn--ghost {
  color: var(--kf-muted-foreground);
}

.tp-btn--primary {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  font-weight: 600;
}

.tp-btn--primary:hover {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  opacity: 0.92;
}

.tp-btn:disabled {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  opacity: 0.45;
  cursor: not-allowed;
}

.tp-btn--link:disabled {
  background: transparent;
  color: var(--kf-muted-foreground);
}

.tp-btn:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 1px;
}

@media (forced-colors: active) {
  .tp-btn:focus-visible {
    outline: 2px solid Highlight;
  }
}
</style>
