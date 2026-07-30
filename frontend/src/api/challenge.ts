// 编程挑战请求层：赛道列表/详情、关卡提交、排行榜与个人统计接口调用。
import { apiGet, apiPost } from './request'
import type {
  ChallengeVO,
  ChallengeDetailVO,
  ChallengeSubmitResultVO,
  ChallengeRankVO,
  ChallengeStatsVO,
} from './types'

export const challengeApi = {
  /** 已发布挑战赛道列表（登录后附带我的进度） */
  list: () => apiGet<ChallengeVO[]>('/challenges'),
  /** 挑战详情（含关卡地图与我的各关状态） */
  detail: (id: number) => apiGet<ChallengeDetailVO>(`/challenges/${id}`),
  /** 提交关卡：前端执行测试用例后上报，后端判定星级/积分/解锁 */
  submitLevel: (
    challengeId: number,
    levelId: number,
    payload: { code: string; total: number; passCount: number; durationSeconds?: number },
  ) => apiPost<ChallengeSubmitResultVO>(`/challenges/${challengeId}/levels/${levelId}/submit`, payload),
  /** 排行榜：challengeId 为空返回总榜 */
  leaderboard: (params: { challengeId?: number; limit?: number } = {}) =>
    apiGet<ChallengeRankVO[]>('/challenges/leaderboard', params),
  /** 我的挑战累计统计 */
  myStats: () => apiGet<ChallengeStatsVO>('/challenges/my/stats'),
}
