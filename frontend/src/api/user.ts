// 用户模块请求层：封装个人资料、统计与资料更新接口调用。
import { apiGet, apiPut } from './request'
import type { UserVO, UserStatsVO, UpdateProfilePayload } from './types'

export const userApi = {
  profile: () => apiGet<UserVO>('/user/profile'),
  stats: () => apiGet<UserStatsVO>('/user/stats'),
  updateProfile: (data: UpdateProfilePayload) => apiPut<UserVO>('/user/profile', data),
}
