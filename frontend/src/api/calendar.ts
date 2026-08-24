// 日历事件接口封装：基于 Task 的统一数据源，按时间区间查询（绝不拉全量）。
import { apiGet } from './request'

/** 日历事件（与后端 CalendarEventVO 对齐）。 */
export interface CalendarEvent {
  id: number
  listId: number | null
  parentId: number
  title: string
  notes: string | null
  /** 0 待办 / 1 已完成。 */
  status: number
  someday: boolean
  important: number
  urgent: number
  stage: number
  /** yyyy-MM-dd 或 null（全天事件用）。 */
  scheduledDate: string | null
  dueDate: string | null
  /** yyyy-MM-dd HH:mm 或 null（定时事件用）。 */
  startTime: string | null
  endTime: string | null
  /** 是否全天：startTime 为空即视为全天。 */
  allDay: boolean
  /** 所属清单名称。 */
  listName: string | null
  /** 所属清单颜色。 */
  listColor: string | null
}

/**
 * 范围查询：按 start/end（ISO 本地时间，如 2026-08-01T00:00:00）筛选与区间重叠的
 * 定时事件，以及 scheduled_date 落在区间日期范围内的全天事件。
 */
export function listCalendarRange(params: {
  start: string
  end: string
  status?: number | null
  listId?: number | null
}) {
  return apiGet<CalendarEvent[]>('/tasks/range', {
    start: params.start,
    end: params.end,
    ...(params.status != null ? { status: params.status } : {}),
    ...(params.listId != null ? { listId: params.listId } : {}),
  })
}
