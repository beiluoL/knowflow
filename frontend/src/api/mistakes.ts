// 错题模块请求层：封装错题列表、详情、标记掌握与统计接口调用。
import { apiGet, apiPut, apiPost } from './request'
import type { MistakeVO, MistakePageResult, MistakeStats } from './types'

export const mistakesApi = {
  list: (params: {
    category?: string
    mastered?: number
    pageNum?: number
    pageSize?: number
  } = {}) => apiGet<MistakePageResult>('/mistakes', params),

  detail: (id: number) => apiGet<MistakeVO>(`/mistakes/${id}`),

  markMastered: (id: number) => apiPut<void>(`/mistakes/${id}/mastered`),

  add: (data: Partial<MistakeVO>) => apiPost<void>('/mistakes', data),

  stats: () => apiGet<MistakeStats>('/mistakes/stats'),
}
