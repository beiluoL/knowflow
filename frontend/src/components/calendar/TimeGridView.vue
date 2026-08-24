<script setup lang="ts">
import { computed } from 'vue'
import type { CalendarEvent } from '@/api/calendar'
import {
  isSameDay,
  formatDate,
  parseLocal,
  eventDate,
  weekdayLabels,
} from '@/utils/calendarDate'

const props = defineProps<{
  events: CalendarEvent[]
  days: Date[]
}>()

const emit = defineEmits<{
  (e: 'event-click', ev: CalendarEvent): void
  (e: 'day-click', date: Date): void
  (e: 'day-header-click', date: Date): void
}>()

const HOUR_H = 52
const HOUR_MIN = 60
const labels = weekdayLabels()
const today = new Date()

interface Placed {
  ev: CalendarEvent
  top: number
  height: number
  left: number
  width: number
}

// 事件按天分桶（全天 / 定时）
const dayMap = computed<Record<string, { allDay: CalendarEvent[]; timed: CalendarEvent[] }>>(() => {
  const map: Record<string, { allDay: CalendarEvent[]; timed: CalendarEvent[] }> = {}
  for (const day of props.days) map[formatDate(day)] = { allDay: [], timed: [] }
  for (const ev of props.events) {
    const d = eventDate(ev)
    if (!d) continue
    const key = formatDate(d)
    if (map[key]) (ev.allDay ? map[key].allDay : map[key].timed).push(ev)
  }
  return map
})

function startMin(ev: CalendarEvent): number {
  const t = parseLocal(ev.startTime || '')
  if (!t) return 0
  return t.getHours() * 60 + t.getMinutes()
}
function endMin(ev: CalendarEvent): number {
  const t = parseLocal(ev.endTime || '')
  const s = parseLocal(ev.startTime || '')
  if (!t || !s) return startMin(ev) + 60
  // 跨天则截断到当日 24:00
  if (t.getDate() !== s.getDate() || t.getMonth() !== s.getMonth()) return 24 * 60
  return t.getHours() * 60 + t.getMinutes()
}

// 重叠列布局（贪心区间划分）
function layoutTimed(day: Date): Placed[] {
  const evs = (dayMap.value[formatDate(day)]?.timed || []).slice().sort((a, b) => startMin(a) - startMin(b))
  const cols: { evs: CalendarEvent[]; maxEnd: number }[] = []
  const rec: { ev: CalendarEvent; col: number }[] = []
  for (const ev of evs) {
    const s = startMin(ev)
    const e = endMin(ev)
    let placed = false
    for (let c = 0; c < cols.length; c++) {
      if (cols[c].maxEnd <= s) {
        cols[c].evs.push(ev)
        cols[c].maxEnd = Math.max(cols[c].maxEnd, e)
        rec.push({ ev, col: c })
        placed = true
        break
      }
    }
    if (!placed) {
      cols.push({ evs: [ev], maxEnd: e })
      rec.push({ ev, col: cols.length - 1 })
    }
  }
  const total = cols.length
  return rec.map(({ ev, col }) => {
    const s = startMin(ev)
    const e = Math.max(endMin(ev), s + 30)
    return {
      ev,
      top: (s / HOUR_MIN) * HOUR_H,
      height: Math.max(((e - s) / HOUR_MIN) * HOUR_H, 22),
      left: (col / total) * 100,
      width: 100 / total - 1.2,
    }
  })
}

function chipColor(ev: CalendarEvent): string {
  return ev.listColor || (ev.important ? '#F59E0B' : '#3B6FE0')
}

// 当前时间指示线（仅今日列）
const nowTop = computed(() => {
  const n = new Date()
  return ((n.getHours() * 60 + n.getMinutes()) / HOUR_MIN) * HOUR_H
})

function onBgClick(day: Date, e: MouseEvent) {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const y = e.clientY - rect.top
  let mins = Math.round((y / HOUR_H) * 60 / 30) * 30
  mins = Math.max(0, Math.min(mins, 24 * 60 - 30))
  const dt = new Date(day)
  dt.setHours(0, 0, 0, 0)
  dt.setMinutes(mins)
  emit('day-click', dt)
}

const hours = Array.from({ length: 24 }, (_, i) => i)
</script>

<template>
  <div class="time-grid">
    <!-- 顶部：星期 + 日期 + 全天条 -->
    <div class="tg-top" :style="{ gridTemplateColumns: `56px repeat(${days.length}, 1fr)` }">
      <div class="tg-corner"></div>
      <div
        v-for="day in days"
        :key="'h' + formatDate(day)"
        class="tg-dayhead"
        :class="{ 'is-today': isSameDay(day, today) }"
        @click="emit('day-header-click', day)"
      >
        <span class="tg-dh-w">周{{ labels[(day.getDay() + 6) % 7] }}</span>
        <span class="tg-dh-d" :class="{ 'today-num': isSameDay(day, today) }">{{ day.getDate() }}</span>
      </div>

      <div class="tg-allday-label">全天</div>
      <div
        v-for="day in days"
        :key="'a' + formatDate(day)"
        class="tg-allday-cell"
        @click="emit('day-click', day)"
      >
        <div
          v-for="ev in (dayMap[formatDate(day)]?.allDay || [])"
          :key="ev.id"
          class="c-chip c-allday"
          :class="{ done: ev.status === 1 }"
          :style="{ '--c': chipColor(ev) }"
          @click.stop="emit('event-click', ev)"
        >
          <span class="c-chip-title">{{ ev.title }}</span>
        </div>
      </div>
    </div>

    <!-- 可滚动时间轴 -->
    <div class="tg-scroll">
      <div class="tg-body" :style="{ gridTemplateColumns: `56px repeat(${days.length}, 1fr)`, height: `${24 * HOUR_H}px` }">
        <!-- 小时刻度 -->
        <div class="tg-hours">
          <div v-for="h in hours" :key="h" class="tg-hour" :style="{ height: `${HOUR_H}px` }">
            <span v-if="h > 0" class="tg-hour-label">{{ h }}:00</span>
          </div>
        </div>

        <!-- 每日列 -->
        <div
          v-for="day in days"
          :key="'d' + formatDate(day)"
          class="tg-day"
          :style="{ backgroundSize: `100% ${HOUR_H}px` }"
          @click="onBgClick(day, $event)"
        >
          <!-- 当前时间线 -->
          <div v-if="isSameDay(day, today)" class="tg-now" :style="{ top: `${nowTop}px` }">
            <span class="tg-now-dot"></span>
          </div>

          <!-- 定时事件 -->
          <div
            v-for="p in layoutTimed(day)"
            :key="p.ev.id"
            class="c-event"
            :class="{ done: p.ev.status === 1 }"
            :style="{
              top: p.top + 'px',
              height: p.height + 'px',
              left: p.left + '%',
              width: p.width + '%',
              '--c': chipColor(p.ev),
            }"
            @click.stop="emit('event-click', p.ev)"
          >
            <div class="c-event-time" v-if="p.ev.startTime">
              {{ parseLocal(p.ev.startTime)!.getHours().toString().padStart(2, '0') }}:{{
                parseLocal(p.ev.startTime)!.getMinutes().toString().padStart(2, '0')
              }}
              <template v-if="p.ev.endTime">
                - {{ parseLocal(p.ev.endTime)!.getHours().toString().padStart(2, '0') }}:{{
                  parseLocal(p.ev.endTime)!.getMinutes().toString().padStart(2, '0')
                }}
              </template>
            </div>
            <div class="c-event-title">{{ p.ev.title }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.time-grid {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  border: 1px solid #eef0f4;
  border-radius: 16px;
  overflow: hidden;
}
.tg-top {
  display: grid;
  border-bottom: 1px solid #eef0f4;
  background: #fafbfc;
  flex-shrink: 0;
}
.tg-corner {
  border-right: 1px solid #f1f3f6;
}
.tg-dayhead {
  text-align: center;
  padding: 8px 0 6px;
  border-right: 1px solid #f1f3f6;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.tg-dayhead:hover {
  background: #f0f5ff;
}
.tg-dayhead.is-today {
  background: #f0f5ff;
}
.tg-dh-w {
  font-size: 11px;
  color: #6b7280;
}
.tg-dh-d {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}
.tg-dh-d.today-num {
  background: var(--kb-primary);
  color: #fff;
}
.tg-allday-label {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #9aa3b2;
  border-right: 1px solid #f1f3f6;
  border-top: 1px solid #f1f3f6;
}
.tg-allday-cell {
  border-right: 1px solid #f1f3f6;
  border-top: 1px solid #f1f3f6;
  padding: 3px 4px;
  min-height: 30px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  cursor: pointer;
}

.tg-scroll {
  flex: 1;
  overflow-y: auto;
  position: relative;
}
.tg-body {
  display: grid;
  position: relative;
}
.tg-hours {
  border-right: 1px solid #f1f3f6;
}
.tg-hour {
  position: relative;
  border-top: 1px solid #f1f3f6;
}
.tg-hour-label {
  position: absolute;
  top: -7px;
  right: 6px;
  font-size: 10px;
  color: #9aa3b2;
}
.tg-day {
  position: relative;
  border-right: 1px solid #f1f3f6;
  background-image: repeating-linear-gradient(
    to bottom,
    #f1f3f6 0,
    #f1f3f6 1px,
    transparent 1px,
    transparent 100%
  );
  cursor: pointer;
}
.tg-day:last-child {
  border-right: none;
}
.tg-now {
  position: absolute;
  left: 0;
  right: 0;
  height: 2px;
  background: #ef4444;
  z-index: 5;
  pointer-events: none;
}
.tg-now-dot {
  position: absolute;
  left: -4px;
  top: -3px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
}

.c-event {
  position: absolute;
  border-radius: 7px;
  padding: 3px 6px;
  font-size: 12px;
  color: #fff;
  background: var(--c, #3b6fe0);
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
  transition: filter 0.12s;
}
.c-event:hover {
  filter: brightness(1.05);
}
.c-event.done {
  opacity: 0.55;
  text-decoration: line-through;
}
.c-event-time {
  font-weight: 600;
  opacity: 0.94;
  font-size: 11px;
}
.c-event-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.c-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 12px;
  color: #fff;
  background: var(--c, #3b6fe0);
  cursor: pointer;
  overflow: hidden;
}
.c-chip.done {
  opacity: 0.55;
  text-decoration: line-through;
}
.c-chip-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.c-allday {
  width: 100%;
}
</style>
