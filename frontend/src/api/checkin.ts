// 每日打卡请求层：封装打卡与打卡状态查询接口调用。
import { apiGet, apiPost } from './request'

/** 打卡结果 */
export interface CheckInResult {
  checkedToday: boolean
  alreadyChecked: boolean
  continuousDays: number
  rewardExp: number
  rewardEnergy: number
}

/** 打卡状态 */
export interface CheckInStatus {
  checkedToday: boolean
  continuousDays: number
  totalDays: number
  /** 本月已打卡的日期号列表 */
  monthCheckedDays: number[]
}

export const checkinApi = {
  /** 今日打卡 */
  checkIn: () => apiPost<CheckInResult>('/checkin'),

  /** 打卡状态（今日是否已打卡、连续天数、本月日历） */
  status: () => apiGet<CheckInStatus>('/checkin/status'),
}
