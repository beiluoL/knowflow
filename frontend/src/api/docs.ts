// 文档模块请求层：封装文档列表、详情、收藏、阅读进度等后端接口调用。
import { apiGet, apiPost, apiPut, apiDelete, apiUpload } from './request'
import type {
  DocPageResult,
  DocVO,
  DocDetailVO,
  ReadProgressPayload,
  DocInput,
  DocUploadMeta,
  DocQuery,
  LearningFlashcard,
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
  // 文件型文档上传（PDF/Word/PPT 等）：服务端抽取正文并入库，支持上传进度回调
  upload: (file: File, meta: DocUploadMeta, onProgress?: (percent: number) => void) =>
    apiUpload<DocDetailVO>('/docs/upload', file, meta, onProgress),
  // B③ AI 生成文档摘要并回填
  generateSummary: (id: number) => apiPost<string>(`/docs/${id}/ai-summary`),
  // B③ AI 基于文档内容生成复习闪卡并落库（可指定归属路径/章节）
  generateFlashcards: (id: number, pathId?: number, chapterId?: number) =>
    apiPost<LearningFlashcard[]>(`/docs/${id}/ai-flashcards`, null, {
      params: { pathId, chapterId },
    }),
}
