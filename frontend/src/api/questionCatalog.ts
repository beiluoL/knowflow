import { apiGet, apiPost, apiPut } from './request'

export type CatalogSource = 'QUIZ' | 'SCENARIO' | 'LEGACY_CODE' | 'CODE_PROBLEM'
export interface CatalogQuestion {
  editToken?: string | null
  catalogId: string
  sourceType: CatalogSource
  sourceQuestionId: number
  canonicalKey?: string | null
  questionType: string
  title: string
  categoryId?: number | null
  knowledgePointIds?: number[] | null
  knowledgePointCodes?: string[] | null
  sourceDifficulty?: number | null
  canonicalEvidence?: boolean
  duplicateCount?: number
  difficulty?: number | null
  tags?: string | string[] | null
  status?: number | null
  reviewStatus?: string | null
  sourceVersion?: number | string | null
  createdAt?: string | null
  updatedAt?: string | null
  editorPath?: string | null
  capabilities?: string[]
  restrictions?: string[]
}
export interface CatalogQuery {
  page: number
  pageSize: number
  keyword?: string
  topicCode?: string
  questionType?: string
  sourceType?: string
  difficulty?: number
  status?: number
  reviewStatus?: string
  categoryId?: number
}
export interface CatalogPage {
  records: CatalogQuestion[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}
export interface CatalogTopic { code: string; name: string; parentCode: string | null; questionCount: number }
export interface CatalogAudit { id: number; actorId: number; sourceType: string; sourceQuestionId: number; action: string; beforeState: string | object | null; afterState: string | object | null; createdAt: string | null }
export interface CatalogAuditPage { records: CatalogAudit[]; total: number; pageNum: number; pageSize: number; pages: number }
export interface CatalogBasicInput {
  title: string
  difficulty: number
  tags: string
  expectedSourceVersion: number | null
  expectedUpdatedAt: string | null
  expectedEditToken?: string | null
  categoryId?: number | null
  topicCode?: string | null
  knowledgePointIds?: number[]
}
export const CATALOG_EDITORS: Record<CatalogSource, string> = {
  QUIZ: '/admin/quiz', SCENARIO: '/admin/scenarios',
  LEGACY_CODE: '/admin/code-questions', CODE_PROBLEM: '/admin/code-problems',
}
export const questionCatalogApi = {
  list: (params: CatalogQuery) => apiGet<CatalogPage>('/admin/question-catalog', params),
  detail: (id: string) => apiGet<{ item: CatalogQuestion; payload: unknown }>(`/admin/question-catalog/${encodeURIComponent(id)}`),
  facets: () => apiGet<{ topics: CatalogTopic[] }>('/admin/question-catalog/facets'),
  audit: (page: number, pageSize = 20) => apiGet<CatalogAuditPage>('/admin/question-catalog/audit', { page, pageSize }),
  judgeStatus: () => apiGet<{ mode: string; executionEnabled: boolean; productionSandboxReady: boolean }>('/admin/question-catalog/judge-status'),
  updateBasic: (catalogId: string, data: CatalogBasicInput) => apiPut<void>(`/admin/question-catalog/${encodeURIComponent(catalogId)}/basic`, data),
  batchStatus: (catalogIds: string[], published: boolean) => apiPost<void>('/admin/question-catalog/batch-status', { catalogIds, published }),
  review: (id: number, decision: 'APPROVED' | 'REJECTED', comment: string, context: { expectedSourceVersion: number | null; expectedEditToken: string }) =>
    apiPost<void>(`/admin/quiz-questions/${id}/review`, { decision, comment, ...context }),
}
