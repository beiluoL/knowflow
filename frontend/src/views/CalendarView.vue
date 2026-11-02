<script setup lang="ts">
import { onMounted, computed, ref } from 'vue'
import Select from '@/components/ui/Select.vue'
import Icon from '@/components/ui/Icon.vue'
import { useCalendarStore, type CalendarMode } from '@/stores/calendar'
import type { CalendarEvent } from '@/api/calendar'
import type { TaskPayload } from '@/api/task'
import { notify, getApiError } from '@/utils/toast'
import MonthView from '@/components/calendar/MonthView.vue'
import TimeGridView from '@/components/calendar/TimeGridView.vue'
import CalendarEventDialog from '@/components/calendar/CalendarEventDialog.vue'
import MemorialDialog from '@/components/calendar/MemorialDialog.vue'
import {
  startOfWeek,
  endOfWeek,
  startOfDay,
  eachDay,
} from '@/utils/calendarDate'

const store = useCalendarStore()

const dialogOpen = ref(false)
const editingEvent = ref<CalendarEvent | null>(null)
const defaultDate = ref<Date | null>(null)
const saving = ref(false)
const memorialOpen = ref(false)

/** 图例：四类日期标记 + 休/班（与后端颜色常量保持一致）。 */
const legendItems = [
  { label: '休', color: '#E5484D', shape: 'badge' as const },
  { label: '班', color: 'var(--kf-warning)', shape: 'badge' as const },
  { label: '传统节日', color: '#E11D48', shape: 'dot' as const },
  { label: '现代节日', color: '#0EA5E9', shape: 'dot' as const },
  { label: '纪念日', color: '#8B5CF6', shape: 'dot' as const },
]

const modes: { key: CalendarMode; label: string; icon: string }[] = [
  { key: 'month', label: '月', icon: 'grid-3x3' },
  { key: 'week', label: '周', icon: 'layout-grid' },
  { key: 'day', label: '日', icon: 'calendar' },
]

const days = computed<Date[]>(() => {
  if (store.mode === 'week') return eachDay(startOfWeek(store.anchor), endOfWeek(store.anchor))
  if (store.mode === 'day') return [startOfDay(store.anchor)]
  return []
})

function openCreate(date: Date) {
  editingEvent.value = null
  defaultDate.value = date
  dialogOpen.value = true
}
function openEdit(ev: CalendarEvent) {
  editingEvent.value = ev
  defaultDate.value = null
  dialogOpen.value = true
}
function closeDialog() {
  dialogOpen.value = false
  editingEvent.value = null
}

async function onSave(payload: TaskPayload, id: number | null) {
  saving.value = true
  try {
    if (id != null) {
      await store.updateEvent(id, payload)
      notify('已保存', 'success')
    } else {
      await store.createEvent(payload)
      notify('已创建', 'success')
    }
    closeDialog()
  } catch (e) {
    notify(getApiError(e, '保存失败'), 'error')
  } finally {
    saving.value = false
  }
}

async function onDelete(id: number) {
  try {
    await store.removeEvent(id)
    notify('已删除', 'success')
    closeDialog()
  } catch (e) {
    notify(getApiError(e, '删除失败'), 'error')
  }
}

function onDayHeaderClick(date: Date) {
  store.anchor = startOfDay(date)
  store.setMode('day')
}

onMounted(async () => {
  await store.loadLists()
  await store.loadRange()
})
</script>

<template>
  <div class="cal-page animate-fade-in">
    <!-- 头部 -->
    <div class="cal-head">
      <div class="cal-head-left">
        <h1 class="kf-h1 mb-1">日历</h1>
        <p class="kf-body-sm" style="font-size: 14px;">管理你的任务与时间安排</p>
      </div>
      <div class="cal-head-actions">
        <button class="cal-memorial" @click="memorialOpen = true">
          <Icon name="heart" :size="16" />
          <span>纪念日</span>
        </button>
        <button class="cal-new" @click="openCreate(startOfDay(store.anchor))">
          <Icon name="plus" :size="16" />
          <span>新建事件</span>
        </button>
      </div>
    </div>

    <!-- 图例 -->
    <div class="cal-legend">
      <span class="cal-legend-title">图例</span>
      <span v-for="item in legendItems" :key="item.label" class="cal-legend-item">
        <i
          v-if="item.shape === 'badge'"
          class="cal-legend-shape is-badge"
          :style="{ background: item.color }"
        >{{ item.label }}</i>
        <template v-else>
          <i class="cal-legend-shape is-dot" :style="{ background: item.color }"></i>
          {{ item.label }}
        </template>
      </span>
    </div>

    <!-- 工具栏 -->
    <div class="cal-toolbar">
      <div class="cal-nav">
        <button class="cal-nav-btn" title="上一年/周/日" @click="store.navigate(-1)">
          <Icon name="chevron-left" :size="18" />
        </button>
        <button class="cal-today" @click="store.navigate('today')">今天</button>
        <button class="cal-nav-btn" title="下一年/周/日" @click="store.navigate(1)">
          <Icon name="chevron-right" :size="18" />
        </button>
        <span class="cal-title">{{ store.title }}</span>
      </div>

      <div class="cal-tools">
        <!-- 视图切换 -->
        <div class="cal-seg">
          <button
            v-for="m in modes"
            :key="m.key"
            class="cal-seg-btn"
            :class="{ active: store.mode === m.key }"
            @click="store.setMode(m.key)"
          >
            <Icon :name="m.icon" :size="14" />
            <span>{{ m.label }}</span>
          </button>
        </div>

        <!-- 过滤器 -->
        <div class="cal-filter">
          <Icon name="filter" :size="14" class="cal-filter-ic" />
          <Select :model-value="store.filter.status" :options="[{ value: null, label: '全部状态' }, { value: 0, label: '待办' }, { value: 1, label: '已完成' }]" aria-label="任务状态" variant="pill" @update:model-value="store.setFilter({ status: $event })" />
          <Select :model-value="store.filter.listId" :options="[{ value: null, label: '全部分类' }, ...store.lists.map(l => ({ value: l.id, label: l.name }))]" aria-label="任务分类" variant="pill" @update:model-value="store.setFilter({ listId: $event })" />
          <button
            v-if="store.filter.status !== null || store.filter.listId !== null"
            class="cal-reset"
            title="清除筛选"
            @click="store.resetFilter()"
          >
            <Icon name="x" :size="14" />
          </button>
        </div>
      </div>
    </div>

    <!-- 视图区 -->
    <div class="cal-main">
      <MonthView
        v-if="store.mode === 'month'"
        :events="store.events"
        :marks="store.marks"
        :anchor="store.anchor"
        @event-click="openEdit"
        @day-click="openCreate"
      />
      <TimeGridView
        v-else
        :events="store.events"
        :marks="store.marks"
        :days="days"
        @event-click="openEdit"
        @day-click="openCreate"
        @day-header-click="onDayHeaderClick"
      />
    </div>

    <!-- 事件对话框 -->
    <CalendarEventDialog
      :open="dialogOpen"
      :event="editingEvent"
      :lists="store.lists"
      :default-date="defaultDate"
      @close="closeDialog"
      @save="onSave"
      @delete="onDelete"
    />

    <!-- 纪念日管理 -->
    <MemorialDialog
      :open="memorialOpen"
      @close="memorialOpen = false"
      @changed="store.loadRange()"
    />
  </div>
</template>

<style scoped>
.cal-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding-bottom: var(--kf-space-2);
}
.cal-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--kf-space-3);
  margin-bottom: var(--kf-space-3);
}
.cal-head-actions {
  display: flex;
  align-items: center;
  gap: var(--kf-space-3);
}
.cal-memorial {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-2);
  height: var(--kf-btn-height-md);
  padding: 0 var(--kf-space-4);
  background: var(--kf-card);
  color: #8b5cf6;
  border: 1px solid #ddd6fe;
  border-radius: var(--kf-btn-radius);
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  cursor: pointer;
  transition:
    background-color var(--kf-motion-fast) var(--kf-ease-standard),
    border-color var(--kf-motion-fast) var(--kf-ease-standard);
}
.cal-memorial:hover {
  background: #f5f3ff;
  border-color: #c4b5fd;
}
.cal-legend {
  display: flex;
  align-items: center;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
  padding: 0 0 var(--kf-space-3);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.cal-legend-title {
  font-weight: 600;
  color: var(--kf-muted-foreground);
}
.cal-legend-item {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-1);
}
.cal-legend-shape.is-dot {
  width: 6px;
  height: 6px;
  border-radius: var(--kf-radius-full);
}
.cal-legend-shape.is-badge {
  width: var(--kf-space-4);
  height: var(--kf-space-4);
  border-radius: var(--kf-radius-xs);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--kf-accent-foreground);
  font-size: var(--kf-fs-xs);
  font-weight: 700;
  font-style: normal;
  line-height: 1;
}
.cal-new {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--kf-space-2);
  height: var(--kf-btn-height-md);
  padding: 0 var(--kf-space-4);
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  border: none;
  border-radius: var(--kf-btn-radius);
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  cursor: pointer;
  transition: filter var(--kf-motion-fast) var(--kf-ease-standard);
}
.cal-new:hover {
  filter: brightness(1.06);
}
.cal-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
  margin-bottom: var(--kf-space-3);
}
.cal-nav {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
}
.cal-nav-btn {
  width: var(--kf-btn-height-sm);
  height: var(--kf-btn-height-sm);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  border-radius: var(--kf-radius-sm);
  color: var(--kf-muted-foreground);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition:
    border-color var(--kf-motion-fast) var(--kf-ease-standard),
    color var(--kf-motion-fast) var(--kf-ease-standard);
}
.cal-nav-btn:hover {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}
.cal-today {
  height: var(--kf-btn-height-sm);
  padding: 0 var(--kf-space-3);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-muted-foreground);
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  cursor: pointer;
  transition:
    border-color var(--kf-motion-fast) var(--kf-ease-standard),
    color var(--kf-motion-fast) var(--kf-ease-standard);
}
.cal-today:hover {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}
.cal-title {
  font-size: var(--kf-fs-body-lg);
  font-weight: 700;
  color: var(--kf-foreground);
  margin-left: var(--kf-space-2);
}
.cal-tools {
  display: flex;
  align-items: center;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.cal-seg {
  display: inline-flex;
  background: var(--kf-border);
  border-radius: var(--kf-radius-md);
  padding: var(--kf-space-1);
}
.cal-seg-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-2);
  border: none;
  background: transparent;
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  padding: var(--kf-space-1) var(--kf-space-3);
  border-radius: var(--kf-radius-sm);
  cursor: pointer;
  transition:
    background-color var(--kf-motion-fast) var(--kf-ease-standard),
    color var(--kf-motion-fast) var(--kf-ease-standard);
}
.cal-seg-btn.active {
  background: var(--kf-card);
  color: var(--kf-primary);
  box-shadow: var(--kf-shadow-sm);
}
.cal-filter {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-2);
}
.cal-filter-ic {
  color: var(--kf-muted-foreground);
}
.cal-reset {
  width: var(--kf-btn-height-sm);
  height: var(--kf-btn-height-sm);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  border-radius: var(--kf-radius-sm);
  color: var(--kf-muted-foreground);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition:
    border-color var(--kf-motion-fast) var(--kf-ease-standard),
    color var(--kf-motion-fast) var(--kf-ease-standard);
}
.cal-reset:hover {
  color: var(--kf-danger);
  border-color: var(--kf-danger-tint);
}
.cal-main {
  flex: 1;
  min-height: 540px;
  display: flex;
  flex-direction: column;
}
.cal-main > * {
  flex: 1;
  min-height: 0;
}
</style>
