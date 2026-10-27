<template>
  <div ref="rootRef" class="dp-calendar" @keydown="onKey">
    <div class="dp-head">
      <h2 class="dp-title" aria-live="polite">{{ monthTitle }}</h2>
      <div class="dp-nav">
        <button type="button" class="dp-nav-btn" aria-label="上个月" @click="shiftMonth(-1)">
          <Icon name="chevron-left" :size="16" />
        </button>
        <button type="button" class="dp-nav-btn" aria-label="下个月" @click="shiftMonth(1)">
          <Icon name="chevron-right" :size="16" />
        </button>
      </div>
    </div>

    <div class="dp-weekdays" aria-hidden="true">
      <span v-for="label in weekdayLabels" :key="label" class="dp-weekday">{{ label }}</span>
    </div>

    <div class="dp-grid" role="grid" :aria-label="monthTitle">
      <div v-for="(week, row) in grid" :key="row" class="dp-week" role="row">
        <div
          v-for="date in week"
          :key="date"
          class="dp-cell"
          :class="{
            'dp-cell--in-range': isInRange(date),
            'dp-cell--range-start': isRangeStart(date),
            'dp-cell--range-end': isRangeEnd(date),
          }"
          role="gridcell"
          :aria-selected="isSelected(date) || isInRange(date) || isRangeStart(date) || isRangeEnd(date)"
        >
          <button
            type="button"
            class="dp-day"
            :class="{
              'dp-day--outside': isOutside(date),
              'dp-day--today': isToday(date),
              'dp-day--selected': isSelected(date),
              'dp-day--disabled': isDisabledDate(date),
              'dp-day--range-start': isRangeStart(date),
              'dp-day--range-end': isRangeEnd(date),
            }"
            :data-date="date"
            :tabindex="activeDate === date ? 0 : -1"
            :aria-label="dayAriaLabel(date)"
            :aria-current="isToday(date) ? 'date' : undefined"
            :aria-disabled="isDisabledDate(date) ? 'true' : undefined"
            @click="selectDate(date)"
            @mouseenter="onDayHover(date)"
            @mouseleave="onDayHoverEnd"
          >
            {{ dayOf(date) }}
            <span v-if="isToday(date)" class="dp-today-dot" aria-hidden="true"></span>
          </button>
        </div>
      </div>
    </div>

    <div v-if="showFooter" class="dp-foot">
      <button v-if="showClear" type="button" class="dp-link" @click="emit('clear')">清除</button>
      <span v-else class="dp-foot-spacer"></span>
      <button
        type="button"
        class="dp-link dp-link--primary"
        :disabled="!canSelectToday"
        @click="selectToday"
      >
        今天
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import Icon from './Icon.vue'
import {
  addDays,
  addMonths,
  buildMonthGrid,
  coerceDateString,
  daysInMonth,
  formatYmd,
  isSameMonth,
  parseDateString,
  toLocalDate,
  todayDateString,
  weekdayOf,
} from './date-picker-utils'

const WEEKDAYS_ZH = ['日', '一', '二', '三', '四', '五', '六']

const props = withDefaults(
  defineProps<{
    modelValue?: string
    min?: string
    max?: string
    locale?: string
    weekStartsOn?: number
    showFooter?: boolean
    showClear?: boolean
    /** 范围模式：额外渲染区间高亮与悬停预览。 */
    range?: boolean
    rangeStart?: string
    rangeEnd?: string
  }>(),
  {
    modelValue: '',
    min: '',
    max: '',
    locale: 'zh-CN',
    weekStartsOn: 0,
    showFooter: true,
    showClear: false,
    range: false,
    rangeStart: '',
    rangeEnd: '',
  },
)

const emit = defineEmits<{
  select: [value: string]
  clear: []
  hover: [value: string | null]
}>()

const rootRef = ref<HTMLElement | null>(null)
const today = ref(todayDateString())
const hoverDate = ref<string | null>(null)
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

const seed = initialDate()
const seedParts = parseDateString(seed)
const viewYear = ref(seedParts?.year ?? new Date().getFullYear())
const viewMonth = ref(seedParts?.month ?? new Date().getMonth() + 1)
const activeDate = ref(seed)

const weekStartsOn = computed(() => {
  const raw = Number(props.weekStartsOn)
  return Number.isInteger(raw) && raw >= 0 && raw <= 6 ? raw : 0
})
const weekdayLabels = computed(() => {
  const base = props.locale.toLowerCase().startsWith('zh')
    ? WEEKDAYS_ZH
    : Array.from({ length: 7 }, (_, index) =>
        new Intl.DateTimeFormat(props.locale, { weekday: 'short' }).format(
          new Date(2024, 0, 7 + index),
        ),
      )
  return Array.from({ length: 7 }, (_, index) => base[(index + weekStartsOn.value) % 7])
})
const monthTitle = computed(() => {
  if (props.locale.toLowerCase().startsWith('zh')) {
    return `${viewYear.value} 年 ${viewMonth.value} 月`
  }
  return new Intl.DateTimeFormat(props.locale, { year: 'numeric', month: 'long' }).format(
    new Date(viewYear.value, viewMonth.value - 1, 1),
  )
})
const grid = computed(() => buildMonthGrid(viewYear.value, viewMonth.value, weekStartsOn.value))
const canSelectToday = computed(() => isSelectable(today.value))
const fullDateFormatter = computed(
  () =>
    new Intl.DateTimeFormat(props.locale, {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      weekday: 'long',
    }),
)

function dayAriaLabel(value: string): string {
  const date = toLocalDate(value)
  return date ? fullDateFormatter.value.format(date) : value
}

function dayOf(value: string): number {
  return parseDateString(value)?.day ?? 0
}

function isSelected(value: string): boolean {
  return value === selectedDate.value
}

const rangeFrom = computed(() => {
  if (!props.range) return ''
  const start = coerceDateString(props.rangeStart)
  const end = coerceDateString(props.rangeEnd)
  if (start && end) return start <= end ? start : end
  return start
})

const rangeTo = computed(() => {
  if (!props.range) return ''
  const start = coerceDateString(props.rangeStart)
  const end = coerceDateString(props.rangeEnd)
  if (start && end) return start <= end ? end : start
  if (start && hoverDate.value && hoverDate.value > start) return hoverDate.value
  return ''
})

function isRangeStart(value: string): boolean {
  return !!rangeFrom.value && value === rangeFrom.value
}

function isRangeEnd(value: string): boolean {
  return !!rangeTo.value && value === rangeTo.value
}

function isInRange(value: string): boolean {
  return !!rangeFrom.value && !!rangeTo.value && value > rangeFrom.value && value < rangeTo.value
}

function onDayHover(value: string): void {
  if (!props.range) return
  hoverDate.value = value
  emit('hover', value)
}

function onDayHoverEnd(): void {
  if (!props.range) return
  hoverDate.value = null
  emit('hover', null)
}

function isToday(value: string): boolean {
  return value === today.value
}

function isOutside(value: string): boolean {
  return !isSameMonth(value, viewYear.value, viewMonth.value)
}

function syncTo(value: string): void {
  const next = coerceDateString(value) || initialDate()
  const parts = parseDateString(next)
  if (!parts) return
  viewYear.value = parts.year
  viewMonth.value = parts.month
  activeDate.value = next
}

function refreshToday(): void {
  today.value = todayDateString()
}

function focusActive(): void {
  rootRef.value
    ?.querySelector<HTMLElement>(`[data-date="${activeDate.value}"]`)
    ?.focus({ preventScroll: true })
}

function selectDate(value: string): void {
  if (isDisabledDate(value)) return
  activeDate.value = value
  hoverDate.value = null
  emit('select', value)
}

function selectToday(): void {
  if (!canSelectToday.value) return
  selectDate(today.value)
}

function shiftMonth(amount: number): void {
  const current = parseDateString(activeDate.value)
  const next = addMonths(
    formatYmd({ year: viewYear.value, month: viewMonth.value, day: 1 }),
    amount,
  )
  if (!next) return
  const parts = parseDateString(next)
  if (!parts) return
  viewYear.value = parts.year
  viewMonth.value = parts.month
  const day = Math.min(current?.day ?? 1, daysInMonth(parts.year, parts.month))
  activeDate.value = formatYmd({ year: parts.year, month: parts.month, day })
}

function moveActive(value: string | null): void {
  if (!value) return
  const parts = parseDateString(value)
  if (!parts) return
  activeDate.value = value
  viewYear.value = parts.year
  viewMonth.value = parts.month
  void nextTick(() => focusActive())
}

function onKey(event: KeyboardEvent): void {
  if (event.isComposing) return
  const weekday = weekdayOf(activeDate.value)
  if (weekday === null) return
  const weekIndex = (weekday - weekStartsOn.value + 7) % 7
  let next: string | null = null
  switch (event.key) {
    case 'ArrowLeft':
      next = addDays(activeDate.value, -1)
      break
    case 'ArrowRight':
      next = addDays(activeDate.value, 1)
      break
    case 'ArrowUp':
      next = addDays(activeDate.value, -7)
      break
    case 'ArrowDown':
      next = addDays(activeDate.value, 7)
      break
    case 'Home':
      next = addDays(activeDate.value, -weekIndex)
      break
    case 'End':
      next = addDays(activeDate.value, 6 - weekIndex)
      break
    case 'PageUp':
      next = addMonths(activeDate.value, -1)
      break
    case 'PageDown':
      next = addMonths(activeDate.value, 1)
      break
    default:
      return
  }
  event.preventDefault()
  moveActive(next)
}

watch(
  () => props.modelValue,
  (value) => {
    syncTo(value)
  },
)

defineExpose({ syncTo, refreshToday, focusActive })
</script>

<style scoped>
.dp-calendar {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.dp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kb-space-2);
  margin-bottom: var(--kb-space-3);
}

.dp-title {
  margin: 0;
  font-size: var(--kb-fs-body-md);
  line-height: var(--kb-lh-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
  white-space: nowrap;
}

.dp-nav {
  display: flex;
  align-items: center;
  gap: var(--kb-space-1);
}

.dp-nav-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: var(--kb-radius-sm);
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background-color var(--kb-control-motion) ease, color var(--kb-control-motion) ease;
}

.dp-nav-btn:hover {
  background: var(--kb-menu-hover);
  color: var(--kb-foreground);
}

.dp-nav-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: -2px;
}

.dp-weekdays,
.dp-week {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}

.dp-weekdays {
  margin-bottom: var(--kb-space-1);
}

.dp-weekday {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 28px;
  font-size: var(--kb-fs-caption);
  line-height: var(--kb-lh-caption);
  color: var(--kb-muted-foreground);
}

.dp-grid {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dp-week {
  gap: 2px;
}

.dp-cell {
  display: flex;
  min-width: 0;
  background: transparent;
}

.dp-cell--in-range {
  background: var(--kb-primary-soft);
}

.dp-cell--range-start {
  border-top-left-radius: var(--kb-radius-sm);
  border-bottom-left-radius: var(--kb-radius-sm);
}

.dp-cell--range-end {
  border-top-right-radius: var(--kb-radius-sm);
  border-bottom-right-radius: var(--kb-radius-sm);
}

.dp-day {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 36px;
  padding: 0;
  border: 0;
  border-radius: var(--kb-radius-sm);
  background: transparent;
  color: var(--kb-foreground);
  font: inherit;
  font-size: var(--kb-fs-body-sm);
  line-height: 1;
  cursor: pointer;
  transition: background-color var(--kb-control-motion) ease, color var(--kb-control-motion) ease;
}

.dp-day:hover {
  background: var(--kb-menu-hover);
}

.dp-day--outside {
  color: var(--kb-muted-foreground);
  opacity: 0.55;
}

.dp-day--today {
  background: var(--kb-primary-soft);
  font-weight: 600;
}

.dp-day--selected,
.dp-day--selected:hover {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-weight: 600;
}

.dp-day--range-start,
.dp-day--range-start:hover,
.dp-day--range-end,
.dp-day--range-end:hover {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-weight: 600;
}

.dp-day--disabled,
.dp-day--disabled:hover {
  background: transparent;
  color: var(--kb-muted-foreground);
  opacity: 0.38;
  cursor: not-allowed;
}

.dp-day:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: -2px;
}

.dp-day--selected:focus-visible {
  outline-color: var(--kb-primary-foreground);
}

.dp-day--disabled:focus-visible {
  outline-color: var(--kb-muted-foreground);
}

.dp-today-dot {
  position: absolute;
  bottom: 5px;
  left: 50%;
  width: 3px;
  height: 3px;
  margin-left: -1.5px;
  border-radius: 50%;
  background: var(--kb-primary);
}

.dp-day--selected .dp-today-dot {
  background: var(--kb-primary-foreground);
}

.dp-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--kb-space-3);
  padding-top: var(--kb-space-3);
  border-top: 1px solid var(--kb-border);
}

.dp-foot-spacer {
  display: block;
}

.dp-link {
  display: inline-flex;
  align-items: center;
  padding: var(--kb-space-1) var(--kb-space-2);
  border: 0;
  border-radius: var(--kb-radius-sm);
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
  cursor: pointer;
}

.dp-link:hover {
  background: var(--kb-menu-hover);
  color: var(--kb-foreground);
}

.dp-link--primary {
  color: var(--kb-primary);
}

.dp-link:disabled {
  background: transparent;
  color: var(--kb-muted-foreground);
  opacity: 0.5;
  cursor: not-allowed;
}

.dp-link:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 1px;
}

@media (forced-colors: active) {
  .dp-nav-btn:focus-visible,
  .dp-day:focus-visible,
  .dp-link:focus-visible {
    outline: 2px solid Highlight;
    outline-offset: -2px;
  }
}
</style>
