<script setup lang="ts">
import { onMounted, computed, ref } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { useCalendarStore, type CalendarMode } from '@/stores/calendar'
import type { CalendarEvent } from '@/api/calendar'
import type { TaskPayload } from '@/api/task'
import { notify, getApiError } from '@/utils/toast'
import MonthView from '@/components/calendar/MonthView.vue'
import TimeGridView from '@/components/calendar/TimeGridView.vue'
import CalendarEventDialog from '@/components/calendar/CalendarEventDialog.vue'
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
        <h1 class="kb-h1 mb-1">日历</h1>
        <p class="kb-body-sm" style="font-size: 14px;">管理你的任务与时间安排</p>
      </div>
      <button class="cal-new" @click="openCreate(startOfDay(store.anchor))">
        <Icon name="plus" :size="16" />
        <span>新建事件</span>
      </button>
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
          <select
            class="cal-select"
            :value="store.filter.status ?? ''"
            @change="store.setFilter({ status: ($event.target as HTMLSelectElement).value === '' ? null : Number(($event.target as HTMLSelectElement).value) })"
          >
            <option value="">全部状态</option>
            <option value="0">待办</option>
            <option value="1">已完成</option>
          </select>
          <select
            class="cal-select"
            :value="store.filter.listId ?? ''"
            @change="store.setFilter({ listId: ($event.target as HTMLSelectElement).value === '' ? null : Number(($event.target as HTMLSelectElement).value) })"
          >
            <option value="">全部分类</option>
            <option v-for="l in store.lists" :key="l.id" :value="l.id">{{ l.name }}</option>
          </select>
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
        :anchor="store.anchor"
        @event-click="openEdit"
        @day-click="openCreate"
      />
      <TimeGridView
        v-else
        :events="store.events"
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
  </div>
</template>

<style scoped>
.cal-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding-bottom: 8px;
}
.cal-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 14px;
}
.cal-new {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--kb-primary);
  color: #fff;
  border: none;
  font-size: 14px;
  font-weight: 600;
  padding: 10px 16px;
  border-radius: 10px;
  cursor: pointer;
  transition: filter 0.15s;
}
.cal-new:hover {
  filter: brightness(1.06);
}
.cal-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.cal-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}
.cal-nav-btn {
  width: 34px;
  height: 34px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 9px;
  color: #4b5563;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}
.cal-nav-btn:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.cal-today {
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #4b5563;
  font-size: 14px;
  font-weight: 600;
  padding: 7px 14px;
  border-radius: 9px;
  cursor: pointer;
  transition: all 0.15s;
}
.cal-today:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.cal-title {
  font-size: 17px;
  font-weight: 700;
  color: #1f2937;
  margin-left: 6px;
}
.cal-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.cal-seg {
  display: inline-flex;
  background: #f1f3f6;
  border-radius: 10px;
  padding: 3px;
}
.cal-seg-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: #6b7280;
  font-size: 13px;
  font-weight: 600;
  padding: 7px 13px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}
.cal-seg-btn.active {
  background: #fff;
  color: var(--kb-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}
.cal-filter {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.cal-filter-ic {
  color: #9aa3b2;
}
.cal-select {
  border: 1px solid #e5e7eb;
  border-radius: 9px;
  padding: 7px 10px;
  font-size: 13px;
  color: #4b5563;
  background: #fff;
  outline: none;
  cursor: pointer;
}
.cal-select:focus {
  border-color: var(--kb-primary);
}
.cal-reset {
  width: 30px;
  height: 30px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 8px;
  color: #9aa3b2;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cal-reset:hover {
  color: #ef4444;
  border-color: #fca5a5;
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
