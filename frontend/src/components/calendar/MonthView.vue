<script setup lang="ts">
import { computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import type { CalendarEvent, DateMark } from '@/api/calendar'
import {
  eachDay,
  isSameDay,
  formatDate,
  weekdayLabels,
  parseLocal,
  eventDate,
} from '@/utils/calendarDate'

const props = defineProps<{
  events: CalendarEvent[]
  marks: DateMark[]
  anchor: Date
}>()

const emit = defineEmits<{
  (e: 'event-click', ev: CalendarEvent): void
  (e: 'day-click', date: Date): void
}>()

const today = new Date()
const labels = weekdayLabels()

// 仅取当前 anchor 所在自然月，用于非本月日期置灰
const currentMonth = computed(() => props.anchor.getMonth())

// 月视图网格：从周一开始，6 行 7 列 = 42 格
const gridDays = computed(() => {
  const first = new Date(props.anchor.getFullYear(), props.anchor.getMonth(), 1)
  const gridStart = new Date(first)
  const offset = (first.getDay() + 6) % 7
  gridStart.setDate(first.getDate() - offset)
  return eachDay(gridStart, new Date(gridStart.getFullYear(), gridStart.getMonth(), gridStart.getDate() + 41))
})

// 事件按归属日期分桶
const byDay = computed<Record<string, CalendarEvent[]>>(() => {
  const map: Record<string, CalendarEvent[]> = {}
  for (const ev of props.events) {
    const d = eventDate(ev)
    if (!d) continue
    const key = formatDate(d)
    ;(map[key] ||= []).push(ev)
  }
  for (const k of Object.keys(map)) {
    map[k].sort((a, b) => (a.startTime || '').localeCompare(b.startTime || ''))
  }
  return map
})

function eventsOf(day: Date): CalendarEvent[] {
  return byDay.value[formatDate(day)] || []
}

// ===== 日期标记（节假日 / 节日 / 纪念日） =====

const marksByDay = computed<Record<string, DateMark[]>>(() => {
  const map: Record<string, DateMark[]> = {}
  for (const m of props.marks) {
    ;(map[m.date] ||= []).push(m)
  }
  return map
})

/** 该日「休/班」角标（holiday 类型且 subLabel 为休/班）。 */
function holidayBadge(day: Date): { label: string; work: boolean } | null {
  const m = (marksByDay.value[formatDate(day)] || []).find(
    (x) => x.type === 'holiday' && (x.subLabel === '休' || x.subLabel === '班')
  )
  if (!m) return null
  return { label: m.subLabel as string, work: m.subLabel === '班' }
}

/** 该日节日标签（传统/现代/纪念日），最多展示 2 条。 */
function festivalMarks(day: Date): DateMark[] {
  return (marksByDay.value[formatDate(day)] || []).filter((x) => x.type !== 'holiday').slice(0, 2)
}

function festivalCount(day: Date): number {
  return (marksByDay.value[formatDate(day)] || []).filter((x) => x.type !== 'holiday').length
}

function chipColor(ev: CalendarEvent): string {
  return ev.listColor || (ev.important ? '#F59E0B' : '#3B6FE0')
}
</script>

<template>
  <div class="c-month">
    <!-- 星期表头 -->
    <div class="c-month-head">
      <div v-for="(lb, i) in labels" :key="i" class="c-month-head-cell" :class="{ weekend: i >= 5 }">
        {{ lb }}
      </div>
    </div>

    <!-- 日期网格 -->
    <div class="c-month-grid">
      <div
        v-for="day in gridDays"
        :key="formatDate(day)"
        class="c-month-cell"
        :class="{
          'is-today': isSameDay(day, today),
          'is-other': day.getMonth() !== currentMonth,
        }"
        @click="emit('day-click', day)"
      >
        <div class="c-month-cell-top">
          <div class="c-month-date-wrap">
            <span class="c-month-date" :class="{ 'today-num': isSameDay(day, today) }">
              {{ day.getDate() }}
            </span>
            <span
              v-if="holidayBadge(day)"
              class="c-badge"
              :class="holidayBadge(day)!.work ? 'is-work' : 'is-rest'"
            >
              {{ holidayBadge(day)!.label }}
            </span>
          </div>
          <button
            class="c-month-add"
            title="新建事件"
            @click.stop="emit('day-click', day)"
          >
            <Icon name="plus" :size="13" />
          </button>
        </div>

        <div v-if="festivalMarks(day).length" class="c-month-marks">
          <span
            v-for="m in festivalMarks(day)"
            :key="m.type + m.name"
            class="c-mark"
            :style="{ '--mc': m.color }"
            :title="m.subLabel || m.name"
          >
            <i class="c-mark-dot"></i>
            <span class="c-mark-name">{{ m.name }}</span>
          </span>
          <span v-if="festivalCount(day) > 2" class="c-mark-more">+{{ festivalCount(day) - 2 }}</span>
        </div>

        <div class="c-month-events">
          <template v-for="ev in eventsOf(day).slice(0, 3)" :key="ev.id">
            <div
              class="c-chip"
              :class="{ done: ev.status === 1 }"
              :style="{ '--c': chipColor(ev) }"
              @click.stop="emit('event-click', ev)"
            >
              <span v-if="!ev.allDay && ev.startTime" class="c-chip-time">
                {{ parseLocal(ev.startTime)!.getHours().toString().padStart(2, '0') }}:{{
                  parseLocal(ev.startTime)!.getMinutes().toString().padStart(2, '0')
                }}
              </span>
              <span class="c-chip-title">{{ ev.title }}</span>
            </div>
          </template>
          <div
            v-if="eventsOf(day).length > 3"
            class="c-more"
            @click.stop="emit('day-click', day)"
          >
            +{{ eventsOf(day).length - 3 }} 更多
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.c-month {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  border: 1px solid #eef0f4;
  border-radius: 16px;
  overflow: hidden;
}
.c-month-head {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  border-bottom: 1px solid #eef0f4;
  background: #fafbfc;
}
.c-month-head-cell {
  padding: 10px 0;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
}
.c-month-head-cell.weekend {
  color: #ef4444;
}
.c-month-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  grid-template-rows: repeat(6, 1fr);
  min-height: 560px;
}
.c-month-cell {
  border-right: 1px solid #f1f3f6;
  border-bottom: 1px solid #f1f3f6;
  padding: 6px 6px 4px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  cursor: pointer;
  transition: background 0.15s;
  min-height: 92px;
  overflow: hidden;
}
.c-month-cell:hover {
  background: #f7f9ff;
}
.c-month-cell:nth-child(7n) {
  border-right: none;
}
.c-month-cell.is-other .c-month-date {
  color: #c4c9d4;
}
.c-month-cell.is-today {
  background: #f0f5ff;
}
.c-month-cell-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.c-month-date-wrap {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}
.c-month-date {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  flex-shrink: 0;
}
.c-month-date.today-num {
  background: var(--kb-primary);
  color: #fff;
}
/* 「休/班」角标：Apple 日历风格的小徽章 */
.c-badge {
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  padding: 2px 4px;
  border-radius: 4px;
  color: #fff;
  flex-shrink: 0;
}
.c-badge.is-rest {
  background: #e5484d;
}
.c-badge.is-work {
  background: #f59e0b;
}
/* 节日标签行（传统/现代/纪念日）：彩色圆点 + 名称 */
.c-month-marks {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 16px;
  overflow: hidden;
}
.c-mark {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  min-width: 0;
  cursor: default;
}
.c-mark-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--mc, #0ea5e9);
  flex-shrink: 0;
}
.c-mark-name {
  font-size: 11px;
  color: #6b7280;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 56px;
}
.c-mark-more {
  font-size: 10px;
  color: #9aa3b2;
  flex-shrink: 0;
}
.c-month-add {
  opacity: 0;
  border: none;
  background: transparent;
  color: #9aa3b2;
  cursor: pointer;
  padding: 2px;
  border-radius: 6px;
  transition: opacity 0.15s;
}
.c-month-cell:hover .c-month-add {
  opacity: 1;
}
.c-month-add:hover {
  background: #e6ecff;
  color: var(--kb-primary);
}
.c-month-events {
  display: flex;
  flex-direction: column;
  gap: 3px;
  overflow: hidden;
}
.c-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.3;
  color: #fff;
  background: var(--c, #3b6fe0);
  cursor: pointer;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.c-chip.done {
  opacity: 0.55;
  text-decoration: line-through;
}
.c-chip-time {
  font-weight: 600;
  opacity: 0.92;
}
.c-chip-title {
  overflow: hidden;
  text-overflow: ellipsis;
}
.c-more {
  font-size: 11px;
  color: #6b7280;
  padding: 1px 6px;
  cursor: pointer;
}
.c-more:hover {
  color: var(--kb-primary);
}
</style>
