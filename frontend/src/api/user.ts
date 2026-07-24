import { apiGet, apiPut } from './request'
import type { UserVO, UserStatsVO, UpdateProfilePayload } from './types'

export const userApi = {
  profile: () => apiGet<UserVO>('/user/profile'),
  stats: () => apiGet<UserStatsVO>('/user/stats'),
  updateProfile: (data: UpdateProfilePayload) => apiPut<UserVO>('/user/profile', data),
}
