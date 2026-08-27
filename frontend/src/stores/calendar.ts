// 日历统一数据源与状态管理：月 / 周 / 日三视图与范围查询共用同一状态。
// store id 固定为 'calendar'（项目红线：Pinia store ID 一旦设定不可修改）。
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { listCalendarRange, listDateMarks, type CalendarEvent, type DateMark } from '@/api/calendar'
import {
  createTask,
  updateTask,
  deleteTask,
  listTaskLists,
  type TaskListVO,
  type TaskPayload,
} from '@/api/task'
import { notify, getApiError } from '@/utils/toast'
import {
  startOfDay,
  endOfDay,
  startOfWeek,
  endOfWeek,
  startOfMonth,
  endOfMonth,
  addDays,
  addMonths,
  toISO,
  toDateInput,
  monthTitle,
  weekTitle,
  dayTitle,
} from '@/utils/calendarDate'

export type CalendarMode = 'month' | 'week' | 'day'

export const useCalendarStore = defineStore('calendar', () => {
  // ===== 状态 =====
  const mode = ref<CalendarMode>('month')
  const anchor = ref<Date>(startOfDay(new Date()))
  const events = ref<CalendarEvent[]>([])
  /** 日期标记（节假日/节日/纪念日），三视图共享，保证视图切换数据一致。 */
  const marks = ref<DateMark[]>([])
  const lists = ref<TaskListVO[]>([])
  const filter = ref<{ status: number | null; listId: number | null }>({
    status: null,
    listId: null,
  })
  const loading = ref(false)

  // ===== 派生：当前可见时间窗（供范围查询与视图渲染） =====
  const range = computed(() => {
    if (mode.value === 'month') {
      const gridStart = startOfWeek(startOfMonth(anchor.value))
      const gridEnd = endOfWeek(endOfMonth(anchor.value))
      return { start: gridStart, end: gridEnd }
    }
    if (mode.value === 'week') {
      const s = startOfWeek(anchor.value)
      return { start: s, end: endOfWeek(s) }
    }
    const s = startOfDay(anchor.value)
    return { start: s, end: endOfDay(s) }
  })

  const title = computed(() => {
    if (mode.value === 'month') return monthTitle(anchor.value)
    if (mode.value === 'week') return weekTitle(range.value.start, range.value.end)
    return dayTitle(anchor.value)
  })

  // ===== 动作 =====
  async function loadLists() {
    try {
      lists.value = await listTaskLists()
    } catch {
      lists.value = []
    }
  }

  /** 按当前 range + filter 拉取事件与日期标记（绝不全量）；供三视图共享。 */
  async function loadRange() {
    loading.value = true
    const startIso = toISO(range.value.start)
    const endIso = toISO(range.value.end)
    try {
      const [evs, ms] = await Promise.all([
        listCalendarRange({
          start: startIso,
          end: endIso,
          status: filter.value.status,
          listId: filter.value.listId,
        }),
        listDateMarks(toDateInput(range.value.start), toDateInput(range.value.end)),
      ])
      events.value = evs
      marks.value = ms
    } catch (e) {
      events.value = []
      marks.value = []
      notify(getApiError(e, '加载日历失败'), 'error')
    } finally {
      loading.value = false
    }
  }

  function setMode(m: CalendarMode) {
    mode.value = m
    loadRange()
  }

  function navigate(dir: -1 | 1 | 'today') {
    if (dir === 'today') {
      anchor.value = startOfDay(new Date())
    } else if (mode.value === 'month') {
      anchor.value = addMonths(anchor.value, dir)
    } else {
      anchor.value = addDays(anchor.value, dir * (mode.value === 'week' ? 7 : 1))
    }
    loadRange()
  }

  function setFilter(partial: Partial<{ status: number | null; listId: number | null }>) {
    filter.value = { ...filter.value, ...partial }
    loadRange()
  }

  function resetFilter() {
    filter.value = { status: null, listId: null }
    loadRange()
  }

  // ===== CRUD：复用 Task 接口，保证三视图与数据同源 =====
  async function createEvent(payload: TaskPayload): Promise<number> {
    const id = await createTask(payload)
    await loadRange()
    return id
  }

  async function updateEvent(id: number, payload: TaskPayload) {
    await updateTask(id, payload)
    await loadRange()
  }

  async function removeEvent(id: number) {
    await deleteTask(id)
    await loadRange()
  }

  return {
    mode,
    anchor,
    events,
    marks,
    lists,
    filter,
    loading,
    range,
    title,
    loadLists,
    loadRange,
    setMode,
    navigate,
    setFilter,
    resetFilter,
    createEvent,
    updateEvent,
    removeEvent,
  }
})
