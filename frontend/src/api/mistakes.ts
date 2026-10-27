// 错题模块请求层：封装错题列表、详情、标记掌握与统计接口调用。
import { apiGet, apiPut, apiPost } from './request'
import type { MistakeVO, MistakePageResult, MistakeStats } from './types'

export interface MistakeListParams {
  category?: string
  mastered?: number
  pageNum?: number
  pageSize?: number
  /** Phase 3H：题型筛选。 */
  questionType?: string
  /** Phase 3H：知识点编码。 */
  topicCode?: string
  /** Phase 3H：来源（QUIZ / CODE，支持 code 前缀沙箱归集）。 */
  sources?: string[]
  /** Phase 3H：错误原因（CODE 题）：WRONG_ANSWER / COMPILE_ERROR / RUNTIME_ERROR / TIME_LIMIT_EXCEEDED / MEMORY_LIMIT_EXCEEDED。 */
  errorReason?: string
  /** Phase 3H：复习状态：pending / mastered / due。 */
  reviewStatus?: 'pending' | 'mastered' | 'due'
  /** Phase 3H：时间范围 ISO。 */
  from?: string
  to?: string
}

export const mistakesApi = {
  list: (params: MistakeListParams = {}) => apiGet<MistakePageResult>('/mistakes', params),

  detail: (id: number) => apiGet<MistakeVO>(`/mistakes/${id}`),

  markMastered: (id: number) => apiPut<void>(`/mistakes/${id}/mastered`),

  add: (data: Partial<MistakeVO>) => apiPost<void>('/mistakes', data),

  stats: () => apiGet<MistakeStats>('/mistakes/stats'),
}
