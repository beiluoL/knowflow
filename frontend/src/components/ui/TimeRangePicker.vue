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
      class="trp-control"
      :class="{
        'trp-control--error': !!formError,
        'trp-control--disabled': disabled,
        'trp-control--readonly': readonly,
      }"
      @click="onTriggerClick"
    >
      <div
        ref="triggerRef"
        class="trp-trigger"
        :class="{ 'trp-trigger--focus': focused, 'trp-trigger--open': open }"
      >
        <Icon name="clock" :size="16" class="trp-icon" aria-hidden="true" />
        <input
          :id="inputId"
          class="trp-text"
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
        <span class="trp-hint" aria-hidden="true">{{ displayHint }}</span>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="open"
        :id="panelId"
        ref="panelRef"
        class="trp-panel"
        role="dialog"
        aria-label="选择时间范围"
        :aria-describedby="statusId"
        :style="panelStyle"
        @keydown="onPanelKey"
        @click.stop
      >
        <div class="trp-head">
          <span class="trp-title">选择时间范围</span>
          <span class="trp-preview" aria-live="polite">{{ draftText }}</span>
        </div>

        <div class="trp-tabs" role="tablist" aria-label="选择开始或结束时间">
          <button
            type="button"
            role="tab"
            class="trp-tab"
            :class="{ 'trp-tab--active': active === 'start' }"
            :aria-selected="active === 'start'"
            @click="active = 'start'"
          >
            <span class="trp-tab-label">开始</span>
            <span class="trp-tab-value">{{ startText }}</span>
          </button>
          <button
            type="button"
            role="tab"
            class="trp-tab"
            :class="{ 'trp-tab--active': active === 'end' }"
            :aria-selected="active === 'end'"
            @click="active = 'end'"
          >
            <span class="trp-tab-label">结束</span>
            <span class="trp-tab-value">{{ endText }}</span>
          </button>
        </div>

        <TimeColumns
          ref="columnsRef"
          :hour="activeDraft.hour"
          :minute="activeDraft.minute"
          :second="activeDraft.second"
          :precision="precision"
          :show-seconds="showSeconds"
          @update:hour="onHour"
          @update:minute="onMinute"
          @update:second="onSecond"
        />

        <p
          :id="statusId"
          class="trp-status"
          :class="{ 'trp-status--error': !!rangeError }"
          role="status"
        >
          {{ statusText }}
        </p>

        <div class="trp-foot">
          <button v-if="showClear" type="button" class="trp-btn trp-btn--link" @click="clear">
            清除
          </button>
          <span v-else class="trp-foot-spacer"></span>
          <div class="trp-actions">
            <button type="button" class="trp-btn trp-btn--ghost" @click="close(true)">取消</button>
            <button
              type="button"
              class="trp-btn trp-btn--primary"
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
  parseTimeString,
  timeFromSeconds,
  timeToSeconds,
  type TimeParts,
  type TimePrecision,
} from './time-picker-utils'
import type { TimeRangeValue } from './date-time-types'

const props = withDefaults(
  defineProps<{
    /** 时间范围合同：两个 HH:mm；precision="second" 时为 HH:mm:ss；空字符串表示未选择。 */
    modelValue?: TimeRangeValue
    precision?: TimePrecision
    /** 允许结束时间早于开始时间（跨午夜），但合同仍只是两个纯时间字符串。 */
    allowOvernight?: boolean
    label?: string
    ariaLabel?: string
    placeholder?: string
    help?: string
    error?: string
    required?: boolean
    disabled?: boolean
    readonly?: boolean
    clearable?: boolean
    /** 最小/最大时间，格式同 modelValue；同时约束两个端点。 */
    min?: string
    max?: string
  }>(),
  {
    modelValue: () => ({ start: '', end: '' }),
    precision: 'minute',
    allowOvernight: false,
    label: '',
    ariaLabel: '',
    placeholder: '开始时间 — 结束时间',
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
  'update:modelValue': [value: TimeRangeValue]
  change: [value: TimeRangeValue]
}>()

const uid = useId()
const inputId = `time-range-picker-${uid}`
const panelId = `${inputId}-panel`
const statusId = `${inputId}-status`

const rootRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const columnsRef = ref<InstanceType<typeof TimeColumns> | null>(null)

const focused = ref(false)
const active = ref<'start' | 'end'>('start')
const draftStart = ref<TimeParts>({ hour: 9, minute: 0, second: 0 })
const draftEnd = ref<TimeParts>({ hour: 10, minute: 0, second: 0 })

const parsedStart = computed(() => parseTimeString(props.modelValue?.start ?? ''))
const parsedEnd = computed(() => parseTimeString(props.modelValue?.end ?? ''))

const effectivePrecision = computed<TimePrecision>(() =>
  props.precision === 'second' ||
  (parsedStart.value && hasMeaningfulSeconds(parsedStart.value)) ||
  (parsedEnd.value && hasMeaningfulSeconds(parsedEnd.value)) ||
  hasMeaningfulSeconds(draftStart.value) ||
  hasMeaningfulSeconds(draftEnd.value)
    ? 'second'
    : 'minute',
)
const showSeconds = computed(() => effectivePrecision.value === 'second')

const { open, panelStyle, openPanel: openAnchoredPanel, closePanel } =
  useAnchoredPanel(triggerRef, panelRef, {
    width: () => (showSeconds.value ? 288 : 240),
    fallbackHeight: 420,
    zIndex: 1200,
    rootRef,
  })

const formError = computed(() => props.error)
const descriptionIds = computed(() => {
  const ids: string[] = []
  if (props.help) ids.push(`${inputId}-help`)
  if (formError.value) ids.push(`${inputId}-error`)
  return ids.join(' ') || undefined
})

function boundaryText(parts: TimeParts): string {
  return formatTimeString(parts, effectivePrecision.value)
}

const displayText = computed(() => {
  const start = parsedStart.value ? boundaryText(parsedStart.value) : ''
  const end = parsedEnd.value ? boundaryText(parsedEnd.value) : ''
  if (start && end) return `${start} — ${end}`
  if (start) return `${start} — …`
  return ''
})
const displayHint = computed(() =>
  displayText.value ? '' : showSeconds.value ? 'HH:mm:ss' : 'HH:mm',
)

const startText = computed(() => boundaryText(draftStart.value))
const endText = computed(() => boundaryText(draftEnd.value))
const activeDraft = computed(() => (active.value === 'start' ? draftStart.value : draftEnd.value))

const isOvernight = computed(
  () => timeToSeconds(draftEnd.value) < timeToSeconds(draftStart.value),
)
const draftText = computed(
  () =>
    `${startText.value} — ${endText.value}${
      props.allowOvernight && isOvernight.value ? '（次日）' : ''
    }`,
)

const rangeError = computed(() => {
  if (
    !isTimeInRange(draftStart.value, props.min, props.max) ||
    !isTimeInRange(draftEnd.value, props.min, props.max)
  ) {
    return '该时间超出可选范围'
  }
  if (!props.allowOvernight && isOvernight.value) return '结束时间不能早于开始时间'
  return ''
})
const statusText = computed(() => {
  if (rangeError.value) return rangeError.value
  if (props.allowOvernight && isOvernight.value) return `结束时间为次日 ${endText.value}`
  return '切换开始/结束可分别调整两端时间'
})
const canConfirm = computed(() => !rangeError.value)

const showClear = computed(
  () =>
    props.clearable &&
    !props.required &&
    !props.disabled &&
    !props.readonly &&
    (!!parsedStart.value || !!parsedEnd.value),
)

function clampToRange(parts: TimeParts): TimeParts {
  const minParts = parseTimeString(props.min)
  const maxParts = parseTimeString(props.max)
  let seconds = timeToSeconds(parts)
  if (minParts) seconds = Math.max(seconds, timeToSeconds(minParts))
  if (maxParts) seconds = Math.min(seconds, timeToSeconds(maxParts))
  return timeFromSeconds(seconds)
}

function seedDraft(): void {
  draftStart.value = clampToRange(parsedStart.value ?? { hour: 9, minute: 0, second: 0 })
  draftEnd.value = clampToRange(parsedEnd.value ?? { hour: 10, minute: 0, second: 0 })
  if (!parsedEnd.value && !props.allowOvernight && isOvernight.value) {
    draftEnd.value = { ...draftStart.value }
  }
  active.value = 'start'
}

function onHour(value: number): void {
  activeDraft.value.hour = value
}

function onMinute(value: number): void {
  activeDraft.value.minute = value
}

function onSecond(value: number): void {
  activeDraft.value.second = value
}

function emitValue(value: TimeRangeValue): void {
  if (value.start === props.modelValue?.start && value.end === props.modelValue?.end) return
  emit('update:modelValue', value)
  emit('change', value)
}

async function openPanel(focusColumns = false): Promise<void> {
  if (props.disabled || props.readonly) return
  seedDraft()
  await openAnchoredPanel()
  if (focusColumns) columnsRef.value?.focusActive()
}

function close(restoreFocus = false): void {
  closePanel(restoreFocus)
}

function confirm(): void {
  if (props.disabled || props.readonly || !canConfirm.value) return
  emitValue({ start: startText.value, end: endText.value })
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
    else columnsRef.value?.focusActive()
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
    if (open.value) seedDraft()
  },
  { deep: true },
)

watch(
  () => props.disabled,
  (value) => {
    if (value) close()
  },
)

watch(open, (value) => {
  if (value) void nextTick(() => columnsRef.value?.focusActive())
})
</script>

<style scoped>
.trp-control {
  width: 100%;
  min-width: 0;
}

.trp-trigger {
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

.trp-trigger:hover {
  background: var(--kf-field-hover);
}

.trp-trigger--focus,
.trp-trigger--open {
  background: var(--kf-field-active);
}

.trp-control--error .trp-trigger {
  background: var(--kf-field-error-bg);
}

.trp-control--disabled .trp-trigger {
  background: var(--kf-field-disabled);
  color: var(--kf-muted-foreground);
  cursor: not-allowed;
}

.trp-control--readonly .trp-trigger {
  background: var(--kf-field-readonly);
}

.trp-icon {
  flex-shrink: 0;
  color: var(--kf-muted-foreground);
}

.trp-text {
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
  text-overflow: ellipsis;
}

.trp-text::placeholder {
  color: var(--kf-field-placeholder);
  opacity: 1;
}

.trp-text:focus,
.trp-text:focus-visible {
  border: 0;
  background: transparent;
  box-shadow: none;
  outline: 0;
}

.trp-hint {
  flex-shrink: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}

.trp-panel {
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

.trp-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}

.trp-title {
  font-size: var(--kf-fs-body-md);
  line-height: var(--kf-lh-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
  white-space: nowrap;
}

.trp-preview {
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  font-variant-numeric: tabular-nums;
  color: var(--kf-primary);
  font-weight: 600;
  white-space: nowrap;
}

.trp-tabs {
  display: flex;
  gap: 2px;
  padding: 2px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-field-surface);
}

.trp-tab {
  display: inline-flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: var(--kf-space-2);
  min-width: 0;
  height: 30px;
  padding: 0 var(--kf-space-2);
  border: 0;
  border-radius: var(--kf-radius-xs);
  background: transparent;
  color: var(--kf-muted-foreground);
  font: inherit;
  font-size: var(--kf-fs-body-sm);
  line-height: 1;
  cursor: pointer;
  transition: background-color var(--kf-control-motion) ease, color var(--kf-control-motion) ease;
}

.trp-tab:hover {
  color: var(--kf-foreground);
}

.trp-tab--active {
  background: var(--kf-popover);
  color: var(--kf-foreground);
  font-weight: 600;
}

.trp-tab-value {
  font-variant-numeric: tabular-nums;
}

.trp-tab:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: -2px;
}

.trp-status {
  margin: 0;
  min-height: var(--kf-lh-caption);
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-muted-foreground);
}

.trp-status--error {
  color: var(--kf-state-error);
}

.trp-foot {
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

.trp-foot-spacer {
  display: block;
}

.trp-actions {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  margin-left: auto;
}

.trp-btn {
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

.trp-btn:hover {
  background: var(--kf-menu-hover);
}

.trp-btn--link {
  height: 32px;
  padding: 0 var(--kf-space-2);
  color: var(--kf-muted-foreground);
}

.trp-btn--ghost {
  color: var(--kf-muted-foreground);
}

.trp-btn--primary {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  font-weight: 600;
}

.trp-btn--primary:hover {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  opacity: 0.92;
}

.trp-btn:disabled {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  opacity: 0.45;
  cursor: not-allowed;
}

.trp-btn:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 1px;
}

@media (forced-colors: active) {
  .trp-btn:focus-visible,
  .trp-tab:focus-visible {
    outline: 2px solid Highlight;
  }
}
</style>
