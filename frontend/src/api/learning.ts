// 学习模块请求层：封装学习路径、章节、闪卡与复习计划等接口调用。
import { apiGet, apiPost, apiPut, apiDelete, apiDeleteWithBody } from './request'
import type {
  LearningPathVO,
  LearningChapterVO,
  FlashcardVO,
  LearningTaskVO,
  LearningTaskInput,
  LearningPathInput,
  ChapterInput,
  FlashcardInput,
  FlashcardGenerateInput,
  DailyActivityVO,
  LearningEventVO,
  IPage,
  MasteryDistributionVO,
  CategoryMasteryVO,
  PersonalizedPathVO,
  ChapterDagVO,
  LearningCertificateVO,
  LearningPlanVO,
  PlanGenerateParams,
  PlanGenerateResult,
} from './types'

export const learningApi = {
  // 读
  paths: () => apiGet<LearningPathVO[]>('/learning/paths'),
  pathDetail: (id: number) => apiGet<LearningPathVO>(`/learning/paths/${id}`),
  enroll: (id: number) => apiPost<void>(`/learning/paths/${id}/enroll`),
  chapters: (pathId: number) => apiGet<LearningChapterVO[]>(`/learning/paths/${pathId}/chapters`),
  chapterDetail: (id: number) => apiGet<LearningChapterVO>(`/learning/chapters/${id}`),
  completeChapter: (id: number) => apiPost<void>(`/learning/chapters/${id}/complete`),
  // L-FORM-01 章节嵌入视频：上报观看进度（0-100），返回当前累计进度
  updateVideoProgress: (id: number, progress: number) =>
    apiPost<number>(`/learning/chapters/${id}/video-progress?progress=${progress}`),
  // L-PATH-03 DAG 可视化：章节依赖关系图
  dag: (pathId: number) => apiGet<ChapterDagVO>(`/learning/paths/${pathId}/dag`),
  flashcards: (pathId?: number, chapterId?: number) =>
    apiGet<FlashcardVO[]>('/learning/flashcards', { pathId, chapterId }),
  // C① 学习活跃度热力图（按日期聚合）
  dailyActivity: (days = 120) => apiGet<DailyActivityVO[]>('/learning/stats/daily-activity', { days }),
  // Phase 1：统一学习行为事件查询（只读，按事件类型/分页过滤）
  learningEvents: (params?: { eventType?: string; current?: number; size?: number }) =>
    apiGet<IPage<LearningEventVO>>('/learning/events', params),
  // C① 掌握分布看板
  mastery: () => apiGet<MasteryDistributionVO>('/learning/stats/mastery'),
  // C① 分类维度掌握度（含薄弱项）
  categoryMastery: () => apiGet<CategoryMasteryVO[]>('/learning/category-mastery'),
  // SM-2 间隔重复复习：quality ∈ [0,5]，<3 重置，3=有印象，5=完全掌握
  reviewFlashcard: (id: number, quality: number) =>
    apiPost<void>(`/learning/flashcards/${id}/review?quality=${quality}`),
  // 今日到期复习闪卡（若后端未提供该接口则前端会自动 fallback 到 flashcards()）
  dueFlashcards: () => apiGet<FlashcardVO[]>('/learning/flashcards/due'),
  tasks: () => apiGet<LearningTaskVO[]>('/learning/tasks'),
  createTask: (data: LearningTaskInput) => apiPost<void>('/learning/tasks', data),
  updateTaskStatus: (id: number, status: number) =>
    apiPut<void>(`/learning/tasks/${id}/status?status=${status}`),
  deleteTask: (id: number) => apiDelete<void>(`/learning/tasks/${id}`),

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

  // ============================================================
  // 用户级「我的闪卡」
  // ============================================================
  myFlashcards: (params?: {
    keyword?: string
    category?: string
    difficulty?: number
    categoryId?: number
    sourceType?: string
  }) => apiGet<FlashcardVO[]>('/learning/my/flashcards', params),
  myFlashcardDetail: (id: number) => apiGet<FlashcardVO>(`/learning/my/flashcards/${id}`),
  createMyFlashcard: (data: FlashcardInput) => apiPost<FlashcardVO>('/learning/my/flashcards', data),
  updateMyFlashcard: (id: number, data: FlashcardInput) => apiPut<void>(`/learning/my/flashcards/${id}`, data),
  deleteMyFlashcard: (id: number) => apiDelete<void>(`/learning/my/flashcards/${id}`),
  deleteMyFlashcards: (ids: number[]) =>
    apiDeleteWithBody<void>('/learning/my/flashcards', { ids }),
  generateMyFlashcards: (data: FlashcardGenerateInput) =>
    apiPost<FlashcardVO[]>('/learning/my/flashcards/generate', data),
  importMyFlashcards: (cards: FlashcardInput[]) =>
    apiPost<{ inserted: number }>('/learning/my/flashcards/import', cards),
  exportMyFlashcards: () => apiGet<FlashcardVO[]>('/learning/my/flashcards/export'),

  // ============================================================
  // 个性化学习路径
  // ============================================================
  personalizedPath: (data: { goal: string; level?: string; dailyMinutes?: number }) =>
    apiPost<PersonalizedPathVO>('/learning/personalized-path', data),
  /** 重新生成个性化学习路径（删除旧缓存，AI 重新生成并持久化） */
  regeneratePersonalizedPath: (data: { goal: string; level?: string; dailyMinutes?: number }) =>
    apiPost<PersonalizedPathVO>('/learning/personalized-path/regenerate', data),
  /** 我的个性化路径历史（按创建时间倒序） */
  personalizedPaths: () => apiGet<PersonalizedPathVO[]>('/learning/personalized-paths'),
  /** 采用个性化路径：落地为真实学习路径并自动报名，返回落地路径 ID */
  adoptPersonalizedPath: (id: number) =>
    apiPost<{ pathId: number }>(`/learning/personalized-path/${id}/adopt`),
  /** 删除我的一条个性化路径推荐 */
  deletePersonalizedPath: (id: number) =>
    apiDelete<void>(`/learning/personalized-path/${id}`),

  // ============================================================
  // 数字证书（G-CERT-01）
  // ============================================================
  /** 我的数字证书列表 */
  certificates: () => apiGet<LearningCertificateVO[]>('/learning/certificates'),
  /** 数字证书详情 */
  certificateDetail: (id: number) => apiGet<LearningCertificateVO>(`/learning/certificates/${id}`),
  /** 按验证码验证证书（可匿名核验真伪） */
  verifyCertificate: (certNo: string) =>
    apiGet<LearningCertificateVO>(`/learning/certificates/verify?certNo=${encodeURIComponent(certNo)}`),

  // ============================================================
  // F3 · 学习计划智能编排
  // ============================================================
  /** 今日计划（懒生成：首次调用会自动生成当日计划） */
  getTodayPlan: () => apiGet<LearningPlanVO>('/learning/plan/today'),
  /** 生成范围计划（默认下一周 7 天） */
  generatePlan: (params?: PlanGenerateParams) =>
    apiPost<PlanGenerateResult>('/learning/plan/generate', params ?? {}),
  /** 导出日历（.ics 文件下载，不走 apiGet 拦截器，避开 Result 解包） */
  async exportCalendar(params?: { date?: string; range?: number }) {
    const query = new URLSearchParams()
    if (params?.date) query.set('date', params.date)
    if (params?.range != null) query.set('range', String(params.range))
    const q = query.toString() ? `?${query.toString()}` : ''
    const res = await fetch(`/api/learning/plan/calendar.ics${q}`, {
      headers: (() => {
        const h: Record<string, string> = {}
        const token = localStorage.getItem('token')
        if (token) h.Authorization = `Bearer ${token}`
        return h
      })(),
    })
    if (!res.ok) {
      const text = await res.text().catch(() => '')
      throw new Error(`日历导出失败：${res.status} ${text || res.statusText}`)
    }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'knowflow-plan.ics'
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 5000)
  },
}
