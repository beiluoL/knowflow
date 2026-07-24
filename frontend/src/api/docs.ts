import { apiGet, apiPost, apiPut, apiDelete } from './request'
import type {
  DocPageResult,
  DocVO,
  DocDetailVO,
  ReadProgressPayload,
  DocInput,
  DocQuery,
} from './types'

export const docsApi = {
  list: (params: DocQuery = {}) => apiGet<DocPageResult>('/docs', params),
  detail: (id: number) => apiGet<DocDetailVO>(`/docs/${id}`),
  recommend: () => apiGet<DocVO[]>('/docs/recommend'),
  favorites: () => apiGet<DocVO[]>('/docs/favorites'),
  recent: () => apiGet<DocVO[]>('/docs/recent'),
  toggleFavorite: (id: number) => apiPost<void>(`/docs/${id}/favorite`),
  updateProgress: (data: ReadProgressPayload) => apiPost<void>('/docs/progress', data),
  create: (data: DocInput) => apiPost<DocVO>('/docs', data),
  update: (id: number, data: DocInput) => apiPut<void>(`/docs/${id}`, data),
  remove: (id: number) => apiDelete<void>(`/docs/${id}`),
}
