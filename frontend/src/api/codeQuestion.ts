// 代码题库请求层：封装前台题目列表、详情与提交验证接口调用。
import { apiGet, apiPost } from './request'
import type { CodeQuestionVO, CodeSubmitResultVO } from './types'

export const codeQuestionApi = {
  /** 已发布题目列表（支持按难度/语言/关键词筛选） */
  list: (params: { keyword?: string; difficulty?: number; language?: string } = {}) =>
    apiGet<CodeQuestionVO[]>('/code-questions', params),
  /** 题目详情（仅已发布） */
  detail: (id: number) => apiGet<CodeQuestionVO>(`/code-questions/${id}`),
  /** 提交答案：后端运行测试用例校验，并累计 submitCount/passCount */
  submit: (id: number, payload: { code: string; language: string }) =>
    apiPost<CodeSubmitResultVO>(`/code-questions/${id}/submit`, payload),
}
