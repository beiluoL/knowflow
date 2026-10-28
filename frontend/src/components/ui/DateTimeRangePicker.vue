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
      class="dtrp-control"
      :class="{
        'dtrp-control--error': !!formError,
        'dtrp-control--disabled': disabled,
        'dtrp-control--readonly': readonly,
      }"
      @click="onTriggerClick"
    >
      <div
        ref="triggerRef"
        class="dtrp-trigger"
        :class="{ 'dtrp-trigger--focus': focused, 'dtrp-trigger--open': open }"
      >
        <Icon name="calendar" :size="16" class="dtrp-icon" aria-hidden="true" />
        <input
          :id="inputId"
          class="dtrp-text"
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
        <span class="dtrp-hint" aria-hidden="true">{{ displayHint }}</span>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="open"
        :id="panelId"
        ref="panelRef"
        class="dtrp-panel"
        :class="{
          'dtrp-panel--compact': compact,
          'dtrp-panel--second': showSeconds,
        }"
        role="dialog"
        aria-label="选择日期时间范围"
        :aria-describedby="statusId"
        :style="panelStyle"
        @keydown="onPanelKey"
        @click.stop
      >
        <div class="dtrp-head">
          <span class="dtrp-title">选择日期时间范围</span>
          <span class="dtrp-preview" aria-live="polite">{{ draftPreview }}</span>
        </div>

        <div v-if="compact" class="dtrp-tabs" role="tablist" aria-label="选择开始或结束">
          <button
            type="button"
            role="tab"
            class="dtrp-tab"
            :class="{ 'dtrp-tab--active': active === 'start' }"
            :aria-selected="active === 'start'"
            @click="setActive('start')"
          >
            开始
          </button>
          <button
            type="button"
            role="tab"
            class="dtrp-tab"
            :class="{ 'dtrp-tab--active': active === 'end' }"
            :aria-selected="active === 'end'"
            @click="setActive('end')"
          >
            结束
          </button>
        </div>

        <div v-if="compact" class="dtrp-subtabs" role="tablist" aria-label="日期或时间">
          <button
            type="button"
            role="tab"
            class="dtrp-subtab"
            :class="{ 'dtrp-subtab--active': tab === 'date' }"
            :aria-selected="tab === 'date'"
            @click="tab = 'date'"
          >
            日期
          </button>
          <button
            type="button"
            role="tab"
            class="dtrp-subtab"
            :class="{ 'dtrp-subtab--active': tab === 'time' }"
            :aria-selected="tab === 'time'"
            @click="tab = 'time'"
          >
            时间
          </button>
        </div>

        <div class="dtrp-body">
          <div v-show="!compact || tab === 'date'" class="dtrp-date">
            <DatePanel
              ref="datePanelRef"
              :model-value="draftStart.date"
              :min="minDate"
              :max="maxDate"
              :show-footer="false"
              range
              :range-start="draftStart.date"
              :range-end="draftEnd.date"
              @select="onSelectDate"
            />
          </div>

          <div v-show="!compact || tab === 'time'" class="dtrp-time">
            <div v-if="!compact" class="dtrp-tabs" role="tablist" aria-label="选择开始或结束">
              <button
                type="button"
                role="tab"
                class="dtrp-tab"
                :class="{ 'dtrp-tab--active': active === 'start' }"
                :aria-selected="active === 'start'"
                @click="setActive('start')"
              >
                开始
              </button>
              <button
                type="button"
                role="tab"
                class="dtrp-tab"
                :class="{ 'dtrp-tab--active': active === 'end' }"
                :aria-selected="active === 'end'"
                @click="setActive('end')"
              >
                结束
              </button>
            </div>

            <TimeColumns
              :hour="activeDraft.hour"
              :minute="activeDraft.minute"
              :second="activeDraft.second"
              :precision="precision"
              :show-seconds="showSeconds"
              @update:hour="onHour"
              @update:minute="onMinute"
              @update:second="onSecond"
            />
          </div>
        </div>

        <p
          :id="statusId"
          class="dtrp-status"
          :class="{ 'dtrp-status--error': !!validationError }"
          role="status"
        >
          {{ statusText }}
        </p>

        <div class="dtrp-foot">
          <button v-if="showClear" type="button" class="dtrp-btn dtrp-btn--link" @click="clear">
            清除
          </button>
          <span v-else class="dtrp-foot-spacer"></span>
          <div class="dtrp-actions">
            <button type="button" class="dtrp-btn dtrp-btn--ghost" @click="close(true)">
              取消
            </button>
            <button
              type="button"
              class="dtrp-btn dtrp-btn--primary"
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import Icon from './Icon.vue'
import FormField from './FormField.vue'
import DatePanel from './DatePanel.vue'
import TimeColumns from './TimeColumns.vue'
import { useAnchoredPanel } from './use-anchored-panel'
import { coerceDateString, parseDateString, todayDateString } from './date-picker-utils'
import {
  formatDateTimeForDisplay,
  formatDateTimePartsForDisplay,
  parseLocalDateTime,
  serializeLocalDateTime,
} from './date-time-format'
import type { DateTimeParts, TimePrecision } from './date-time-format'
import {
  formatTimeString,
  hasMeaningfulSeconds,
  timeToSeconds,
} from './time-picker-utils'
import type { DateTimeRangeValue } from './date-time-types'

const COMPACT_BREAKPOINT = 720

const props = withDefaults(
  defineProps<{
    /** 日期时间范围合同：两个 YYYY-MM-DDTHH:mm；precision="second" 时为 YYYY-MM-DDTHH:mm:ss。 */
    modelValue?: DateTimeRangeValue
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
    /** 可选边界，本地日期时间字符串；同时约束两个端点。 */
    min?: string
    max?: string
  }>(),
  {
    modelValue: () => ({ start: '', end: '' }),
    precision: 'minute',
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
  'update:modelValue': [value: DateTimeRangeValue]
  change: [value: DateTimeRangeValue]
}>()

const uid = useId()
const inputId = `datetime-range-picker-${uid}`
const panelId = `${inputId}-panel`
const statusId = `${inputId}-status`

const rootRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const datePanelRef = ref<InstanceType<typeof DatePanel> | null>(null)

const focused = ref(false)
const active = ref<'start' | 'end'>('start')
const tab = ref<'date' | 'time'>('date')
const viewportWidth = ref(1280)
const draftStart = ref<DateTimeParts>({ date: '', hour: 9, minute: 0, second: 0 })
const draftEnd = ref<DateTimeParts>({ date: '', hour: 10, minute: 0, second: 0 })

const parsedStart = computed(() => parseLocalDateTime(props.modelValue?.start ?? ''))
const parsedEnd = computed(() => parseLocalDateTime(props.modelValue?.end ?? ''))
const minParts = computed(() => parseLocalDateTime(props.min))
const maxParts = computed(() => parseLocalDateTime(props.max))
const minDate = computed(() => coerceDateString(props.min))
const maxDate = computed(() => coerceDateString(props.max))

const effectivePrecision = computed<TimePrecision>(() =>
  props.precision === 'second' ||
  [parsedStart.value, parsedEnd.value, draftStart.value, draftEnd.value].some(
    (parts) => !!parts && hasMeaningfulSeconds(parts),
  )
    ? 'second'
    : 'minute',
)
const showSeconds = computed(() => effectivePrecision.value === 'second')
const compact = computed(() => viewportWidth.value < COMPACT_BREAKPOINT)

const { open, panelStyle, openPanel: openAnchoredPanel, closePanel } =
  useAnchoredPanel(triggerRef, panelRef, {
    width: () => (compact.value ? 336 : showSeconds.value ? 604 : 564),
    fallbackHeight: compact.value ? 470 : 432,
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

const displayText = computed(() => {
  const start = parsedStart.value
    ? formatDateTimeForDisplay(props.modelValue.start, effectivePrecision.value)
    : ''
  const end = parsedEnd.value
    ? formatDateTimeForDisplay(props.modelValue.end, effectivePrecision.value)
    : ''
  if (start && end) return `${start} — ${end}`
  if (start) return `${start} — …`
  return ''
})
const displayHint = computed(() =>
  displayText.value ? '' : showSeconds.value ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD HH:mm',
)

const activeDraft = computed(() => (active.value === 'start' ? draftStart.value : draftEnd.value))

function formatDraft(parts: DateTimeParts): string {
  if (!parts.date) return `待选日期 ${formatTimeString(parts, effectivePrecision.value)}`
  return formatDateTimePartsForDisplay(parts, effectivePrecision.value)
}

const draftPreview = computed(
  () => `${formatDraft(draftStart.value)} — ${formatDraft(draftEnd.value)}`,
)

function compareDateTime(a: DateTimeParts, b: DateTimeParts): number {
  if (a.date !== b.date) return a.date < b.date ? -1 : 1
  return timeToSeconds(a) - timeToSeconds(b)
}

function isOutOfBounds(parts: DateTimeParts): boolean {
  if (minParts.value && compareDateTime(parts, minParts.value) < 0) return true
  if (maxParts.value && compareDateTime(parts, maxParts.value) > 0) return true
  return false
}

const validationError = computed(() => {
  if (!draftStart.value.date || !draftEnd.value.date) return '请选择完整日期时间'
  if (isOutOfBounds(draftStart.value) || isOutOfBounds(draftEnd.value)) {
    return '该时间超出可选范围'
  }
  if (compareDateTime(draftStart.value, draftEnd.value) > 0) return '结束时间不能早于开始时间'
  return ''
})
const statusText = computed(
  () => validationError.value || '点击日期与时间可分别调整开始和结束',
)
const canConfirm = computed(() => !validationError.value)

const showClear = computed(
  () =>
    props.clearable &&
    !props.required &&
    !props.disabled &&
    !props.readonly &&
    (!!parsedStart.value || !!parsedEnd.value),
)

function readViewportWidth(): number {
  if (typeof window === 'undefined') return 1280
  const visual = window.visualViewport?.width
  return Math.round(visual || window.innerWidth || 1280)
}

function updateViewportWidth(): void {
  viewportWidth.value = readViewportWidth()
}

onMounted(() => {
  updateViewportWidth()
  window.addEventListener('resize', updateViewportWidth)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateViewportWidth)
})

function isDateInBounds(date: string): boolean {
  if (!parseDateString(date)) return false
  if (minDate.value && date < minDate.value) return false
  if (maxDate.value && date > maxDate.value) return false
  return true
}

function defaultDate(): string {
  const today = todayDateString()
  if (isDateInBounds(today)) return today
  return minDate.value || maxDate.value || today
}

function seedDraft(): void {
  draftStart.value = parsedStart.value
    ? { ...parsedStart.value }
    : { date: defaultDate(), hour: 9, minute: 0, second: 0 }
  draftEnd.value = parsedEnd.value
    ? { ...parsedEnd.value }
    : { date: '', hour: 10, minute: 0, second: 0 }
  active.value = 'start'
  if (compact.value) tab.value = 'date'
}

function onSelectDate(value: string): void {
  if (active.value === 'start') {
    draftStart.value.date = value
    if (!draftEnd.value.date) {
      active.value = 'end'
      if (compact.value) tab.value = 'date'
    }
    return
  }
  draftEnd.value.date = value
}

/**
 * 切换开始/结束边界时把日历视图带到该端点所在月；
 * 跨月区间下无需再逐月点击箭头，就能直接调整另一端的日期。
 */
function setActive(boundary: 'start' | 'end'): void {
  active.value = boundary
  const target = boundary === 'start' ? draftStart.value.date : draftEnd.value.date
  if (target) datePanelRef.value?.syncTo(target)
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

function emitValue(value: DateTimeRangeValue): void {
  if (value.start === props.modelValue?.start && value.end === props.modelValue?.end) return
  emit('update:modelValue', value)
  emit('change', value)
}

async function openPanel(focusCalendar = false): Promise<void> {
  if (props.disabled || props.readonly) return
  updateViewportWidth()
  seedDraft()
  await openAnchoredPanel()
  if (focusCalendar) datePanelRef.value?.focusActive()
}

function close(restoreFocus = false): void {
  closePanel(restoreFocus)
}

function confirm(): void {
  if (props.disabled || props.readonly || !canConfirm.value) return
  emitValue({
    start: serializeLocalDateTime(draftStart.value, effectivePrecision.value),
    end: serializeLocalDateTime(draftEnd.value, effectivePrecision.value),
  })
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
    else datePanelRef.value?.focusActive()
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
    if (!open.value) return
    seedDraft()
    void nextTick(() => {
      if (draftStart.value.date) datePanelRef.value?.syncTo(draftStart.value.date)
    })
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
  if (!value) return
  void nextTick(() => {
    datePanelRef.value?.refreshToday()
    if (draftStart.value.date) datePanelRef.value?.syncTo(draftStart.value.date)
  })
})
</script>

<style scoped>
.dtrp-control {
  width: 100%;
  min-width: 0;
}

.dtrp-trigger {
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

.dtrp-trigger:hover {
  background: var(--kf-field-hover);
}

.dtrp-trigger--focus,
.dtrp-trigger--open {
  background: var(--kf-field-active);
}

.dtrp-control--error .dtrp-trigger {
  background: var(--kf-field-error-bg);
}

.dtrp-control--disabled .dtrp-trigger {
  background: var(--kf-field-disabled);
  color: var(--kf-muted-foreground);
  cursor: not-allowed;
}

.dtrp-control--readonly .dtrp-trigger {
  background: var(--kf-field-readonly);
}

.dtrp-icon {
  flex-shrink: 0;
  color: var(--kf-muted-foreground);
}

.dtrp-text {
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

.dtrp-text::placeholder {
  color: var(--kf-field-placeholder);
  opacity: 1;
}

.dtrp-text:focus,
.dtrp-text:focus-visible {
  border: 0;
  background: transparent;
  box-shadow: none;
  outline: 0;
}

.dtrp-hint {
  flex-shrink: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}

.dtrp-panel {
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

.dtrp-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--kf-space-2);
  min-width: 0;
}

.dtrp-title {
  font-size: var(--kf-fs-body-md);
  line-height: var(--kf-lh-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
  white-space: nowrap;
}

.dtrp-preview {
  min-width: 0;
  overflow: hidden;
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  font-variant-numeric: tabular-nums;
  color: var(--kf-primary);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dtrp-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--kf-space-4);
  align-items: start;
  min-width: 0;
}

.dtrp-date {
  min-width: 0;
}

.dtrp-time {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  width: 200px;
  padding-left: var(--kf-space-4);
  border-left: 1px solid var(--kf-border);
}

.dtrp-panel--second:not(.dtrp-panel--compact) .dtrp-time {
  width: 240px;
}

.dtrp-tabs {
  display: flex;
  gap: 2px;
  padding: 2px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-field-surface);
}

.dtrp-tab {
  display: inline-flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-width: 64px;
  height: 30px;
  padding: 0 var(--kf-space-3);
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

.dtrp-tab:hover {
  color: var(--kf-foreground);
}

.dtrp-tab--active {
  background: var(--kf-popover);
  color: var(--kf-foreground);
  font-weight: 600;
}

.dtrp-tab:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: -2px;
}

.dtrp-subtabs {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-field-surface);
}

.dtrp-subtab {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 64px;
  height: 30px;
  padding: 0 var(--kf-space-3);
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

.dtrp-subtab:hover {
  color: var(--kf-foreground);
}

.dtrp-subtab--active {
  background: var(--kf-popover);
  color: var(--kf-foreground);
  font-weight: 600;
}

.dtrp-subtab:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: -2px;
}

.dtrp-panel--compact .dtrp-body {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
}

.dtrp-panel--compact .dtrp-time {
  width: 100%;
  padding-top: var(--kf-space-3);
  padding-left: 0;
  border-top: 1px solid var(--kf-border);
  border-left: 0;
}

.dtrp-status {
  margin: 0;
  min-height: var(--kf-lh-caption);
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-muted-foreground);
}

.dtrp-status--error {
  color: var(--kf-state-error);
}

.dtrp-foot {
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

.dtrp-foot-spacer {
  display: block;
}

.dtrp-actions {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  margin-left: auto;
}

.dtrp-btn {
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

.dtrp-btn:hover {
  background: var(--kf-menu-hover);
}

.dtrp-btn--link {
  padding: 0 var(--kf-space-2);
  color: var(--kf-muted-foreground);
}

.dtrp-btn--ghost {
  color: var(--kf-muted-foreground);
}

.dtrp-btn--primary {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  font-weight: 600;
}

.dtrp-btn--primary:hover {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  opacity: 0.92;
}

.dtrp-btn:disabled {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  opacity: 0.45;
  cursor: not-allowed;
}

.dtrp-btn:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 1px;
}

@media (forced-colors: active) {
  .dtrp-btn:focus-visible,
  .dtrp-tab:focus-visible,
  .dtrp-subtab:focus-visible {
    outline: 2px solid Highlight;
  }
}
</style>
