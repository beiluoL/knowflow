// 代码题库请求层：封装前台题目列表、详情与提交验证接口调用。
import { apiGet, apiPost } from './request'
import type { CodeQuestionVO, CodeSubmitResultVO } from './types'

export const codeQuestionApi = {
  /** 已发布题目列表（支持按难度/语言/关键词筛选） */
  list: (params: { keyword?: string; difficulty?: number; language?: string } = {}) =>
    apiGet<CodeQuestionVO[]>('/code-questions', params),
  /** 题目详情（仅已发布） */
  detail: (id: number) => apiGet<CodeQuestionVO>(`/code-questions/${id}`),
  /** 旧版记录接口：接收客户端统计并累计记录，不是可信服务端判题；工作台不以此上报通过结果。 */
  submit: (id: number, payload: { code: string; language: string; total?: number; passCount?: number }) =>
    apiPost<CodeSubmitResultVO>(`/code-questions/${id}/submit`, payload),
}
