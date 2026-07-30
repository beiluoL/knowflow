// 成就/勋章系统请求层：成就列表含进度、自动解锁、概览统计与最近解锁时间线。
import { apiGet } from './request'
import type { AchievementPageVO } from './types'

export const achievementApi = {
  /** 我的成就页（列表 + 统计 + 最近解锁） */
  myAchievements: () => apiGet<AchievementPageVO>('/achievements'),
}
