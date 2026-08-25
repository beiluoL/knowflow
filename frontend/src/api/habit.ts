// 习惯打卡请求层：封装习惯 CRUD、今日打卡、撤销打卡与进度查询接口调用。
import { apiGet, apiPost, apiPut, apiDelete } from './request'

/** 单日打卡进度 */
export interface DayProgress {
  date: string
  count: number
  completed: boolean
}

/** 习惯视图对象（含今日打卡与进度可视化） */
export interface HabitVO {
  id: number
  name: string
  description: string
  icon: string
  color: string
  frequency: 'daily' | 'weekly'
  targetCount: number
  reminderTime: string | null
  startDate: string | null
  active: number
  sortOrder: number
  todayCount: number
  completedToday: boolean
  streak: number
  bestStreak: number
  totalDays: number
  weekly: DayProgress[]
  monthly: DayProgress[]
}

/** 习惯创建/更新参数 */
export interface HabitPayload {
  name?: string
  description?: string
  icon?: string
  color?: string
  frequency?: 'daily' | 'weekly'
  targetCount?: number
  reminderTime?: string | null
  startDate?: string | null
  active?: number
  sortOrder?: number
}

export const habitApi = {
  /** 当前用户全部启用习惯（含今日打卡与进度） */
  list: () => apiGet<HabitVO[]>('/habits'),

  /** 新建习惯 */
  create: (payload: HabitPayload) => apiPost<number>('/habits', payload),

  /** 更新习惯 */
  update: (id: number, payload: HabitPayload) => apiPut<void>(`/habits/${id}`, payload),

  /** 删除习惯 */
  remove: (id: number) => apiDelete<void>(`/habits/${id}`),

  /** 今日打卡（幂等累加） */
  checkIn: (id: number) => apiPost<HabitVO>(`/habits/${id}/checkin`),

  /** 撤销今日最近一次打卡 */
  undo: (id: number) => apiPost<HabitVO>(`/habits/${id}/undo`),

  /** 获取单个习惯详情 */
  get: (id: number) => apiGet<HabitVO>(`/habits/${id}`),
}
