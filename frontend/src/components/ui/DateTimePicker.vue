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
      class="dtp-control"
      :class="{
        'dtp-control--error': !!formError,
        'dtp-control--disabled': disabled,
        'dtp-control--readonly': readonly,
      }"
      @click="onTriggerClick"
    >
      <div
        ref="triggerRef"
        class="dtp-trigger"
        :class="{ 'dtp-trigger--focus': focused, 'dtp-trigger--open': open }"
      >
        <Icon name="calendar" :size="16" class="dtp-icon" aria-hidden="true" />
        <input
          :id="inputId"
          v-model="text"
          class="dtp-text"
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
          @focus="onInputFocus"
          @blur="onInputBlur"
          @keydown="onInputKey"
        />
        <span class="dtp-hint" aria-hidden="true">{{ displayHint }}</span>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="open"
        :id="panelId"
        ref="panelRef"
        class="dtp-panel"
        :class="{
          'dtp-panel--compact': compact,
          'dtp-panel--second': showSeconds,
        }"
        role="dialog"
        aria-label="选择日期时间"
        :style="panelStyle"
        @keydown="onPanelKey"
        @click.stop
      >
        <div class="dtp-head">
          <span class="dtp-title">日期时间</span>
          <span class="dtp-preview" aria-live="polite">{{ draftPreview }}</span>
        </div>

        <div v-if="compact" class="dtp-tabs" role="tablist" aria-label="日期或时间">
          <button
            type="button"
            role="tab"
            class="dtp-tab"
            :class="{ 'dtp-tab--active': tab === 'date' }"
            :aria-selected="tab === 'date'"
            @click="tab = 'date'"
          >
            日期
          </button>
          <button
            type="button"
            role="tab"
            class="dtp-tab"
            :class="{ 'dtp-tab--active': tab === 'time' }"
            :aria-selected="tab === 'time'"
            @click="tab = 'time'"
          >
            时间
          </button>
        </div>

        <div class="dtp-body">
          <div v-show="!compact || tab === 'date'" class="dtp-date">
            <DatePanel
              ref="datePanelRef"
              :model-value="draftDate"
              :min="min"
              :max="max"
              :locale="locale"
              :week-starts-on="weekStartsOn"
              :show-footer="false"
              @select="draftDate = $event"
            />
          </div>

          <div v-show="!compact || tab === 'time'" class="dtp-time">
            <span class="dtp-time-label">时间</span>
            <TimeColumns
              :hour="draftHour"
              :minute="draftMinute"
              :second="draftSecond"
              :precision="precision"
              :show-seconds="showSeconds"
              @update:hour="draftHour = $event"
              @update:minute="draftMinute = $event"
              @update:second="draftSecond = $event"
            />
          </div>
        </div>

        <div class="dtp-foot">
          <button
            v-if="showClear"
            type="button"
            class="dtp-btn dtp-btn--link"
            @click="clear"
          >
            清除
          </button>
          <span v-else class="dtp-foot-spacer"></span>
          <div class="dtp-actions">
            <button type="button" class="dtp-btn dtp-btn--ghost" @click="close(true)">取消</button>
            <button type="button" class="dtp-btn dtp-btn--primary" @click="confirm">
              确认
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </FormField>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
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
  resolveDateTimePrecision,
  serializeLocalDateTime,
} from './date-time-format'
import type { TimePrecision } from './time-picker-utils'

const COMPACT_BREAKPOINT = 720

const props = withDefaults(
  defineProps<{
    /** 本地日期时间合同：YYYY-MM-DDTHH:mm；precision="second" 时为 YYYY-MM-DDTHH:mm:ss。 */
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
    /** 可选日期边界，格式 YYYY-MM-DD。 */
    min?: string
    max?: string
    locale?: string
    weekStartsOn?: number
  }>(),
  {
    modelValue: '',
    precision: 'minute',
    label: '',
    ariaLabel: '',
    placeholder: '选择日期时间',
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
const inputId = `datetime-picker-${uid}`
const panelId = `${inputId}-panel`

const rootRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const datePanelRef = ref<InstanceType<typeof DatePanel> | null>(null)

const focused = ref(false)
const inputError = ref('')
const tab = ref<'date' | 'time'>('date')
const viewportWidth = ref(1280)

const parsedValue = computed(() => parseLocalDateTime(props.modelValue))
const effectivePrecision = computed<TimePrecision>(() =>
  resolveDateTimePrecision(props.precision, parsedValue.value),
)
const showSeconds = computed(() => effectivePrecision.value === 'second')
const compact = computed(() => viewportWidth.value < COMPACT_BREAKPOINT)

const draftDate = ref(todayDateString())
const draftHour = ref(0)
const draftMinute = ref(0)
const draftSecond = ref(0)

const text = ref(
  parsedValue.value ? formatDateTimeForDisplay(props.modelValue, props.precision) : '',
)
const minDate = computed(() => coerceDateString(props.min))
const maxDate = computed(() => coerceDateString(props.max))

const { open, panelStyle, openPanel: openAnchoredPanel, closePanel } =
  useAnchoredPanel(triggerRef, panelRef, {
    width: () => (compact.value ? 336 : showSeconds.value ? 604 : 564),
    fallbackHeight: compact.value ? 470 : 432,
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
const displayHint = computed(() =>
  text.value ? '' : showSeconds.value ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD HH:mm',
)
const showClear = computed(
  () =>
    props.clearable &&
    !props.required &&
    !props.disabled &&
    !props.readonly &&
    !!parsedValue.value,
)
const draftPreview = computed(() =>
  formatDateTimePartsForDisplay(
    {
      date: draftDate.value,
      hour: draftHour.value,
      minute: draftMinute.value,
      second: draftSecond.value,
    },
    effectivePrecision.value,
  ),
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

function seedDraft(): void {
  const parts = parsedValue.value
  if (parts) {
    draftDate.value = parts.date
    draftHour.value = parts.hour
    draftMinute.value = parts.minute
    draftSecond.value = parts.second
    return
  }
  const now = new Date()
  const today = todayDateString()
  const fallbackDate = isDateInBounds(today) ? today : minDate.value || maxDate.value || today
  draftDate.value = fallbackDate
  draftHour.value = now.getHours()
  draftMinute.value = now.getMinutes()
  draftSecond.value = 0
}

function isDateInBounds(value: string): boolean {
  if (!parseDateString(value)) return false
  if (minDate.value && value < minDate.value) return false
  if (maxDate.value && value > maxDate.value) return false
  return true
}

function displayOf(value: string): string {
  const parts = parseLocalDateTime(value)
  if (!parts) return ''
  return formatDateTimeForDisplay(value, resolveDateTimePrecision(props.precision, parts))
}

function emitValue(value: string): void {
  text.value = displayOf(value)
  inputError.value = ''
  if (value === props.modelValue) return
  emit('update:modelValue', value)
  emit('change', value)
}

function restoreText(): void {
  text.value = displayOf(props.modelValue)
}

async function openPanel(focusCalendar = false): Promise<void> {
  if (props.disabled || props.readonly) return
  updateViewportWidth()
  seedDraft()
  if (compact.value) tab.value = 'date'
  await openAnchoredPanel()
  if (focusCalendar) datePanelRef.value?.focusActive()
}

function close(restoreFocus = false): void {
  closePanel(restoreFocus)
}

function confirm(): void {
  if (props.disabled || props.readonly) return
  if (!isDateInBounds(draftDate.value)) {
    inputError.value = '该日期超出可选范围'
    return
  }
  emitValue(
    serializeLocalDateTime(
      {
        date: draftDate.value,
        hour: draftHour.value,
        minute: draftMinute.value,
        second: draftSecond.value,
      },
      effectivePrecision.value,
    ),
  )
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
      if (showError) inputError.value = '请选择日期时间'
      restoreText()
      return
    }
    emitValue('')
    return
  }
  const parsed = parseLocalDateTime(raw)
  if (!parsed) {
    if (showError) inputError.value = '请输入有效日期时间（YYYY-MM-DD HH:mm）'
    else {
      restoreText()
      inputError.value = ''
    }
    return
  }
  if (!isDateInBounds(parsed.date)) {
    if (showError) inputError.value = '该日期超出可选范围'
    else {
      restoreText()
      inputError.value = ''
    }
    return
  }
  const precision = resolveDateTimePrecision(props.precision, parsed)
  emitValue(serializeLocalDateTime(parsed, precision))
}

function onInputKey(event: KeyboardEvent): void {
  if (event.isComposing || props.disabled || props.readonly) return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    if (!open.value) void openPanel(true)
    else datePanelRef.value?.focusActive()
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
    inputError.value = ''
    text.value = displayOf(value)
    if (open.value) {
      seedDraft()
      datePanelRef.value?.syncTo(draftDate.value)
    }
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
    if (value) datePanelRef.value?.syncTo(draftDate.value)
  },
)
</script>

<style scoped>
.dtp-control {
  width: 100%;
  min-width: 0;
}

.dtp-trigger {
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

.dtp-trigger:hover {
  background: var(--kf-field-hover);
}

.dtp-trigger--focus,
.dtp-trigger--open {
  background: var(--kf-field-active);
}

.dtp-control--error .dtp-trigger {
  background: var(--kf-field-error-bg);
}

.dtp-control--disabled .dtp-trigger {
  background: var(--kf-field-disabled);
  color: var(--kf-muted-foreground);
  cursor: not-allowed;
}

.dtp-control--readonly .dtp-trigger {
  background: var(--kf-field-readonly);
}

.dtp-icon {
  flex-shrink: 0;
  color: var(--kf-muted-foreground);
}

.dtp-text {
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

.dtp-text::placeholder {
  color: var(--kf-field-placeholder);
  opacity: 1;
}

.dtp-text:focus,
.dtp-text:focus-visible {
  border: 0;
  background: transparent;
  box-shadow: none;
  outline: 0;
}

.dtp-control--disabled .dtp-text {
  cursor: not-allowed;
}

.dtp-hint {
  flex-shrink: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}

.dtp-panel {
  --dtp-pad-x: var(--kf-space-4);
  --dtp-pad-y: var(--kf-space-4);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--dtp-pad-y) var(--dtp-pad-x);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
  background: var(--kf-popover);
  color: var(--kf-popover-foreground);
  box-shadow: var(--shadow-dropdown-3);
  overflow: auto;
  font-family: var(--font-sans);
}

.dtp-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--kf-space-2);
}

.dtp-title {
  font-size: var(--kf-fs-body-md);
  line-height: var(--kf-lh-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
  white-space: nowrap;
}

.dtp-preview {
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

.dtp-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--kf-space-4);
  align-items: start;
}

.dtp-date {
  min-width: 0;
}

.dtp-time {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  width: 200px;
  padding-left: var(--kf-space-4);
  border-left: 1px solid var(--kf-border);
}

.dtp-panel--second:not(.dtp-panel--compact) .dtp-time {
  width: 240px;
}

.dtp-time-label {
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-muted-foreground);
}

.dtp-panel--compact .dtp-body {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
}

.dtp-panel--compact .dtp-time {
  width: 100%;
  padding-top: var(--kf-space-3);
  padding-left: 0;
  border-top: 1px solid var(--kf-border);
  border-left: 0;
}

.dtp-tabs {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-field-surface);
}

.dtp-tab {
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

.dtp-tab:hover {
  color: var(--kf-foreground);
}

.dtp-tab--active {
  background: var(--kf-popover);
  color: var(--kf-foreground);
  font-weight: 600;
}

.dtp-tab:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: -2px;
}

.dtp-foot {
  position: sticky;
  /* 与面板纵向 padding 共用变量：两者不一致会恒定溢出几个像素并裁掉确认栏。 */
  bottom: calc(-1 * var(--dtp-pad-y));
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-2);
  margin: 0 calc(-1 * var(--dtp-pad-x)) calc(-1 * var(--dtp-pad-y));
  padding: var(--kf-space-3) var(--dtp-pad-x);
  border-top: 1px solid var(--kf-border);
  background: var(--kf-popover);
}

.dtp-foot-spacer {
  display: block;
}

.dtp-actions {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  margin-left: auto;
}

.dtp-btn {
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

.dtp-btn:hover {
  background: var(--kf-menu-hover);
}

.dtp-btn--link {
  height: auto;
  padding: var(--kf-space-1) var(--kf-space-2);
  color: var(--kf-muted-foreground);
}

.dtp-btn--ghost {
  color: var(--kf-muted-foreground);
}

.dtp-btn--primary {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  font-weight: 600;
}

.dtp-btn--primary:hover {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  opacity: 0.92;
}

.dtp-btn:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 1px;
}

/* 视口高度受限时先压缩纵向留白，避免为十几像素出现整体滚动条。 */
@media (max-height: 800px) {
  .dtp-panel {
    --dtp-pad-y: var(--kf-space-2);
    gap: var(--kf-space-2);
  }
  .dtp-foot {
    padding: var(--kf-space-2) var(--dtp-pad-x);
  }
}

@media (forced-colors: active) {
  .dtp-btn:focus-visible,
  .dtp-tab:focus-visible {
    outline: 2px solid Highlight;
  }
}
</style>
