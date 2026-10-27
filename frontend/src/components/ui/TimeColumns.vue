<template>
  <div class="tp-columns"
    :class="{ 'tp-columns--second': showSeconds }"
    @keydown="onKey"
  >
    <div v-for="column in columns" :key="column.key" class="tp-col">
      <span class="tp-col-label" aria-hidden="true">{{ column.label }}</span>
      <div
        :ref="(el) => setListRef(column.key, el)"
        class="tp-list"
        role="listbox"
        :aria-label="column.fullLabel"
        :data-column="column.key"
      >
        <button
          v-for="option in column.options"
          :key="option"
          type="button"
          role="option"
          class="tp-option"
          :class="{ 'tp-option--selected': option === column.value }"
          :data-value="option"
          :tabindex="activeColumn === column.key && option === column.value ? 0 : -1"
          :aria-selected="option === column.value"
          @click="pick(column.key, option)"
          @focus="activeColumn = column.key"
        >
          {{ column.format(option) }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { buildTimeOptions, type TimePrecision } from './time-picker-utils'

type ColumnKey = 'hour' | 'minute' | 'second'

const props = withDefaults(
  defineProps<{
    hour: number
    minute: number
    second?: number
    precision?: TimePrecision
    /** 值来自外部含秒数据时强制展示秒列，避免静默丢失。 */
    showSeconds?: boolean
  }>(),
  {
    second: 0,
    precision: 'minute',
    showSeconds: false,
  },
)

const emit = defineEmits<{
  'update:hour': [value: number]
  'update:minute': [value: number]
  'update:second': [value: number]
}>()

const listRefs = new Map<ColumnKey, HTMLElement>()
const activeColumn = ref<ColumnKey>('hour')

const showSeconds = computed(() => props.precision === 'second' || props.showSeconds)

const pad = (value: number): string => String(value).padStart(2, '0')

const columns = computed(() => {
  const list: Array<{
    key: ColumnKey
    label: string
    fullLabel: string
    max: number
    value: number
    options: number[]
    format: (value: number) => string
  }> = [
    {
      key: 'hour',
      label: '时',
      fullLabel: '小时',
      max: 23,
      value: props.hour,
      options: buildTimeOptions(23),
      format: pad,
    },
    {
      key: 'minute',
      label: '分',
      fullLabel: '分钟',
      max: 59,
      value: props.minute,
      options: buildTimeOptions(59),
      format: pad,
    },
  ]
  if (showSeconds.value) {
    list.push({
      key: 'second',
      label: '秒',
      fullLabel: '秒',
      max: 59,
      value: props.second,
      options: buildTimeOptions(59),
      format: pad,
    })
  }
  return list
})

function setListRef(key: ColumnKey, element: unknown): void {
  if (element instanceof HTMLElement) listRefs.set(key, element)
  else listRefs.delete(key)
}

function valueOf(key: ColumnKey): number {
  if (key === 'hour') return props.hour
  if (key === 'minute') return props.minute
  return props.second
}

function maxOf(key: ColumnKey): number {
  return key === 'hour' ? 23 : 59
}

function setValue(key: ColumnKey, value: number): void {
  const safe = Math.max(0, Math.min(maxOf(key), value))
  if (key === 'hour') emit('update:hour', safe)
  else if (key === 'minute') emit('update:minute', safe)
  else emit('update:second', safe)
}

function pick(key: ColumnKey, value: number): void {
  activeColumn.value = key
  setValue(key, value)
}

function scrollIntoView(key: ColumnKey): void {
  const list = listRefs.get(key)
  if (!list) return
  const option = list.querySelector<HTMLElement>(`[data-value="${valueOf(key)}"]`)
  if (!option) return
  const top = option.offsetTop - list.clientHeight / 2 + option.offsetHeight / 2
  list.scrollTop = Math.max(0, top)
}

function focusColumn(key: ColumnKey): void {
  activeColumn.value = key
  const list = listRefs.get(key)
  if (!list) return
  scrollIntoView(key)
  list.querySelector<HTMLElement>(`[data-value="${valueOf(key)}"]`)?.focus({
    preventScroll: true,
  })
}

function focusActive(): void {
  void nextTick(() => focusColumn(activeColumn.value))
}

function move(key: ColumnKey, delta: number): void {
  const max = maxOf(key)
  const current = valueOf(key)
  const next = (current + delta + max + 1) % (max + 1)
  setValue(key, next)
  void nextTick(() => {
    scrollIntoView(key)
    listRefs
      .get(key)
      ?.querySelector<HTMLElement>(`[data-value="${next}"]`)
      ?.focus({ preventScroll: true })
  })
}

function moveColumn(delta: number): void {
  const order: ColumnKey[] = showSeconds.value
    ? ['hour', 'minute', 'second']
    : ['hour', 'minute']
  const index = order.indexOf(activeColumn.value)
  const next = order[(index + delta + order.length) % order.length]
  focusColumn(next)
}

function onKey(event: KeyboardEvent): void {
  if (event.isComposing) return
  switch (event.key) {
    case 'ArrowUp':
      event.preventDefault()
      move(activeColumn.value, -1)
      return
    case 'ArrowDown':
      event.preventDefault()
      move(activeColumn.value, 1)
      return
    case 'ArrowLeft':
      event.preventDefault()
      moveColumn(-1)
      return
    case 'ArrowRight':
      event.preventDefault()
      moveColumn(1)
      return
    case 'Home':
      event.preventDefault()
      pick(activeColumn.value, 0)
      focusActive()
      return
    case 'End':
      event.preventDefault()
      pick(activeColumn.value, maxOf(activeColumn.value))
      focusActive()
      return
    default:
      return
  }
}

defineExpose({ focusActive, focusColumn, scrollIntoView })
</script>

<style scoped>
.tp-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--kb-space-2);
  min-width: 0;
}

.tp-columns--second {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.tp-col {
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-1);
  min-width: 0;
}

.tp-col-label {
  text-align: center;
  font-size: var(--kb-fs-caption);
  line-height: var(--kb-lh-caption);
  color: var(--kb-muted-foreground);
}

.tp-list {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 168px;
  padding: 4px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-field-surface);
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
}

.tp-option {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 30px;
  padding: 0;
  border: 0;
  border-radius: var(--kb-radius-xs);
  background: transparent;
  color: var(--kb-foreground);
  font: inherit;
  font-size: var(--kb-fs-body-sm);
  font-variant-numeric: tabular-nums;
  line-height: 1;
  cursor: pointer;
  transition: background-color var(--kb-control-motion) ease, color var(--kb-control-motion) ease;
}

.tp-option:hover {
  background: var(--kb-menu-hover);
}

.tp-option--selected,
.tp-option--selected:hover {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-weight: 600;
}

.tp-option:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: -2px;
}

.tp-option--selected:focus-visible {
  outline-color: var(--kb-primary-foreground);
}

@media (forced-colors: active) {
  .tp-option:focus-visible {
    outline: 2px solid Highlight;
  }
}
</style>
