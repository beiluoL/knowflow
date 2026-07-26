// 管理后台请求层：封装概览、文档/用户/分类/闪卡管理等接口调用。
import { apiGet, apiPost, apiPut, apiDelete } from './request'
import type {
  AdminOverviewVO,
  CategoryVO,
  CategoryInput,
  DocPageResult,
  DocInput,
  UserVO,
} from './types'

export interface PageQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  role?: string
  status?: number
  categoryId?: number
  difficulty?: number
}

export const adminApi = {
  overview: () => apiGet<AdminOverviewVO>('/admin/overview'),

  // 文档管理
  docs: (params: PageQuery = {}) => apiGet<DocPageResult>('/admin/docs', params),
  createDoc: (data: DocInput) => apiPost<void>('/admin/docs', data),
  updateDoc: (id: number, data: DocInput) => apiPut<void>(`/admin/docs/${id}`, data),
  removeDoc: (id: number) => apiDelete<void>(`/admin/docs/${id}`),

  // 用户管理
  users: (params: PageQuery = {}) => apiGet<{ records: UserVO[]; total: number }>('/admin/users', params),
  createUser: (data: Partial<UserVO> & { password?: string }) => apiPost<void>('/admin/users', data),
  updateUser: (id: number, data: Partial<UserVO>) => apiPut<void>(`/admin/users/${id}`, data),
  removeUser: (id: number) => apiDelete<void>(`/admin/users/${id}`),

  // 分类管理
  categories: () => apiGet<CategoryVO[]>('/admin/categories'),
  createCategory: (data: CategoryInput) => apiPost<CategoryVO>('/admin/categories', data),
  updateCategory: (id: number, data: CategoryInput) => apiPut<void>(`/admin/categories/${id}`, data),
  removeCategory: (id: number) => apiDelete<void>(`/admin/categories/${id}`),

  // 闪卡管理
  flashcards: (params: PageQuery = {}) =>
    apiGet<{ records: unknown[]; total: number }>('/admin/learning/flashcards', params),
  createFlashcard: (data: unknown) => apiPost<void>('/admin/learning/flashcards', data),
  updateFlashcard: (id: number, data: unknown) => apiPut<void>(`/admin/learning/flashcards/${id}`, data),
  removeFlashcard: (id: number) => apiDelete<void>(`/admin/learning/flashcards/${id}`),
}
