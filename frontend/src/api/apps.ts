// 应用平台请求层（Architecture-3，§29-33）：UserApp 状态的最小 API。
// 后端 user_app = 真源；本层只操作当前用户（后端 SecurityContext 决定，§256-260）。
import { apiGet, apiPut } from './request'
import type { UserAppVO } from '@/apps/types'

export interface UserAppPatch {
  installed?: boolean
  enabled?: boolean
  sortOrder?: number
}

export const appsApi = {
  getUserApps: () => apiGet<UserAppVO[]>('/apps/user-apps'),
  updateUserApp: (appKey: string, patch: UserAppPatch) => apiPut<UserAppVO>(`/apps/user-apps/${appKey}`, patch),
}
