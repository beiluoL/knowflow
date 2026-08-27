// 日历事件接口封装：基于 Task 的统一数据源，按时间区间查询（绝不拉全量）。
import { apiGet, apiPost, apiPut, apiDelete } from './request'

// =====================================================================
// 日期标记（节假日 / 传统节日 / 现代节日 / 纪念日）——与后端 DateMarkVO 对齐
// =====================================================================

/** 标记类型：holiday 法定节假日（休/班）、lunar 传统节日、modern 现代节日、memorial 纪念日。 */
export type DateMarkType = 'holiday' | 'lunar' | 'modern' | 'memorial'

/** 四类标记主题色（与后端 CalendarMarkServiceImpl 常量一致，勿单独改动）。 */
export const MARK_COLORS: Record<DateMarkType, string> = {
  holiday: '#E5484D', // 休（红）
  lunar: '#E11D48', // 传统节日（玫红）
  modern: '#0EA5E9', // 现代节日（蓝）
  memorial: '#8B5CF6', // 纪念日（紫）
}

/** 调休上班日（班）颜色。 */
export const MARK_WORK_COLOR = '#F59E0B'

export interface DateMark {
  type: DateMarkType
  /** yyyy-MM-dd */
  date: string
  /** 标记名称（如「国庆节」「中秋节」「母亲节」） */
  name: string
  /** holiday 为「休」/「班」；lunar 为农历月日（如「八月十五」） */
  subLabel: string | null
  color: string
  memorialId: number | null
}

/** 自定义纪念日（与后端 MemorialVO 对齐）。 */
export interface Memorial {
  id: number
  name: string
  /** fixed 固定日期 / yearly 每年重复 */
  type: 'fixed' | 'yearly'
  /** MM-dd */
  monthDay: string
  /** yyyy-MM-dd（type=fixed 时有值） */
  fixedDate: string | null
  color: string | null
  note: string | null
  createTime: string
  updateTime: string
}

export interface MemorialPayload {
  name: string
  type: 'fixed' | 'yearly'
  monthDay?: string
  fixedDate?: string
  color?: string
  note?: string
}

/** 按日期区间查询标记（start/end 为 yyyy-MM-dd）。 */
export function listDateMarks(start: string, end: string) {
  return apiGet<DateMark[]>('/calendar/marks', { start, end })
}

export function listMemorials() {
  return apiGet<Memorial[]>('/calendar/memorials')
}

export function createMemorial(payload: MemorialPayload) {
  return apiPost<number>('/calendar/memorials', payload)
}

export function updateMemorial(id: number, payload: MemorialPayload) {
  return apiPut<void>(`/calendar/memorials/${id}`, payload)
}

export function deleteMemorial(id: number) {
  return apiDelete<void>(`/calendar/memorials/${id}`)
}

// =====================================================================
// 日历事件（CalendarEvent 及范围查询）
// =====================================================================

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
