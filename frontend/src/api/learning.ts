// 学习模块请求层：封装学习路径、章节、闪卡与复习计划等接口调用。
import { apiGet, apiPost, apiPut, apiDelete } from './request'
import type {
  LearningPathVO,
  LearningChapterVO,
  FlashcardVO,
  LearningTaskVO,
  LearningPathInput,
  ChapterInput,
  FlashcardInput,
} from './types'

export const learningApi = {
  // 读
  paths: () => apiGet<LearningPathVO[]>('/learning/paths'),
  pathDetail: (id: number) => apiGet<LearningPathVO>(`/learning/paths/${id}`),
  enroll: (id: number) => apiPost<void>(`/learning/paths/${id}/enroll`),
  chapters: (pathId: number) => apiGet<LearningChapterVO[]>(`/learning/paths/${pathId}/chapters`),
  chapterDetail: (id: number) => apiGet<LearningChapterVO>(`/learning/chapters/${id}`),
  completeChapter: (id: number) => apiPost<void>(`/learning/chapters/${id}/complete`),
  flashcards: (pathId?: number, chapterId?: number) =>
    apiGet<FlashcardVO[]>('/learning/flashcards', { pathId, chapterId }),
  // SM-2 间隔重复复习：quality ∈ [0,5]，<3 重置，3=有印象，5=完全掌握
  reviewFlashcard: (id: number, quality: number) =>
    apiPost<void>(`/learning/flashcards/${id}/review?quality=${quality}`),
  tasks: () => apiGet<LearningTaskVO[]>('/learning/tasks'),

  // 管理员写操作
  createPath: (data: LearningPathInput) => apiPost<LearningPathVO>('/admin/learning/paths', data),
  updatePath: (id: number, data: LearningPathInput) => apiPut<void>(`/admin/learning/paths/${id}`, data),
  removePath: (id: number) => apiDelete<void>(`/admin/learning/paths/${id}`),
  createChapter: (data: ChapterInput) => apiPost<LearningChapterVO>('/admin/learning/chapters', data),
  updateChapter: (id: number, data: ChapterInput) => apiPut<void>(`/admin/learning/chapters/${id}`, data),
  removeChapter: (id: number) => apiDelete<void>(`/admin/learning/chapters/${id}`),
  createFlashcard: (data: FlashcardInput) => apiPost<FlashcardVO>('/admin/learning/flashcards', data),
  updateFlashcard: (id: number, data: FlashcardInput) => apiPut<void>(`/admin/learning/flashcards/${id}`, data),
  removeFlashcard: (id: number) => apiDelete<void>(`/admin/learning/flashcards/${id}`),
}
