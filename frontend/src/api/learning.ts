import type { FlashcardImportRequest, FlashcardImportPreview, FlashcardImportOutcome, FlashcardContentExport } from './flashcard-exchange-types'
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
  FlashcardNoteInput,
  FlashcardNoteVO,
  FlashcardSchedulePreview, FlashcardFsrsResult, FlashcardReviewSettings,
  FlashcardGenerateInput,
  DailyActivityVO,
  LearningEventVO,
  IPage,
  PageResult,
  MasteryDistributionVO,
  CategoryMasteryVO,
  KnowledgeMasteryVO,
  KnowledgeMasteryDetailVO,
  MasteryDiagnosticsVO,
  PersonalizedPathVO,
  ChapterDagVO,
  LearningCertificateVO,
  LearningPlanVO,
  PlanGenerateParams,
  PlanGenerateResult,
  WeeklyReportVO,
  UserLearningPrefInput,
  UserLearningPrefVO,
  LearningReportData,
  LearningEventSummaryVO,
  LearningGoalVO,
  LearningGoalInput,
  ScenarioSummaryVO,
  ScenarioDetailVO,
  ScenarioSubmitInput,
  ScenarioSubmitResultVO,
  FeynmanRubricVO,
  FeynmanSubmitInput,
  FeynmanSubmitResultVO,
  FirstPrinciplesRubricVO,
  FirstPrinciplesSubmitInput,
  FirstPrinciplesSubmitResultVO,
  ChapterKnowledgeVO,
} from './types'

export const learningApi = {
  favorite: (id: number) => apiGet<{ favorited: boolean }>(`/learning/chapters/${id}/favorite`),
  toggleChapterFavorite: (id: number) => apiPost<{ favorited: boolean }>(`/learning/chapters/${id}/favorite`),
  createChapterNote: (data: { chapterId: number; pathId: number; title: string; content: string }) => apiPost<{ id: number }>('/learning/notes', data),
  // 读
  paths: () => apiGet<LearningPathVO[]>('/learning/paths'),
  pathDetail: (id: number) => apiGet<LearningPathVO>(`/learning/paths/${id}`),
  enroll: (id: number) => apiPost<void>(`/learning/paths/${id}/enroll`),
  chapters: (pathId: number) => apiGet<LearningChapterVO[]>(`/learning/paths/${pathId}/chapters`),
  /**
   * 按文档 ID 反查关联章节（docIds 逗号分隔）。后端无独立端点，
   * 前端扫描当前用户可见路径的章节（仅用户显式点击「进入章节学习」时触发，非 N+1 热路径）。
   */
  chaptersByDoc: async (docId: number) => {
    const paths = await apiGet<LearningPathVO[]>('/learning/paths')
    const all = await Promise.all(
      paths.map((p) => apiGet<LearningChapterVO[]>(`/learning/paths/${p.id}/chapters`).catch(() => [] as LearningChapterVO[])),
    )
    return all
      .flat()
      .filter((c) => c.docIds?.split(',').map((s) => s.trim()).includes(String(docId)) ?? false)
  },
  chapterDetail: (id: number) => apiGet<LearningChapterVO>(`/learning/chapters/${id}`),
  saveReadingPosition: (id: number, anchor: string, expectedRevision: number, expectedContentHash: string, presentationHash: string) =>
    apiPost<LearningChapterVO>(`/learning/chapters/${id}/reading-position`, { anchor, expectedRevision, expectedContentHash, presentationHash }),
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
  // Phase 1：学习事件统计摘要（今日 + 近7日，统一口径）
  learningEventSummary: () => apiGet<LearningEventSummaryVO>('/learning/events/summary'),
  // 学习报告（按周期聚合签到/闪卡/错题/代码/阅读/测验）
  getReport: (period: string) => apiGet<LearningReportData>('/learning/report', { period }),
  // C① 掌握分布看板
  mastery: () => apiGet<MasteryDistributionVO>('/learning/stats/mastery'),
  // C① 分类维度掌握度（含薄弱项）
  categoryMastery: () => apiGet<CategoryMasteryVO[]>('/learning/category-mastery'),

  // ============================================================
  // Phase 2-B：知识点掌握度引擎
  // ============================================================
  /** 我的全部知识点掌握度（按 masteryScore 倒序）。 */
  knowledgeMastery: () => apiGet<KnowledgeMasteryVO[]>('/learning/mastery'),
  /** 薄弱知识点（learningStatus = WEAK）。 */
  knowledgeMasteryWeak: () => apiGet<KnowledgeMasteryVO[]>('/learning/mastery/weak'),
  /** 需复习知识点（learningStatus = REVIEW_REQUIRED）。 */
  knowledgeMasteryReviewRequired: () => apiGet<KnowledgeMasteryVO[]>('/learning/mastery/review-required'),
  /** 单知识点掌握度详情（信号明细 + 原始计数器 + 解释）。 */
  knowledgeMasteryDetail: (id: number) => apiGet<KnowledgeMasteryDetailVO>(`/learning/mastery/${id}`),
  /** 引擎可观察性诊断（映射统计 / 未映射资源）。 */
  knowledgeMasteryDiagnostics: () => apiGet<MasteryDiagnosticsVO>('/learning/mastery/diagnostics'),
  /** 重新计算并回填（构建映射 + 重算全部知识点掌握度）。 */
  knowledgeMasteryRecalculate: () => apiPost<void>('/learning/mastery/recalculate'),

  // ============================================================
  // Architecture-4.0：学习目标（学习闭环起点）
  // ============================================================
  /** 我的全部学习目标（ACTIVE 在前）。 */
  goals: () => apiGet<LearningGoalVO[]>('/learning/goals'),
  /** 当前进行中学习目标（无则 null）。 */
  activeGoal: () => apiGet<LearningGoalVO | null>('/learning/goals/active'),
  /** 创建学习目标（pathId 非空则后端校验关联并自动报名）。 */
  createGoal: (data: LearningGoalInput) => apiPost<LearningGoalVO>('/learning/goals', data),
  /** 覆盖式更新目标（状态流转 / 换路径 / pathId=null 解除关联）。 */
  updateGoal: (id: number, data: LearningGoalInput) =>
    apiPut<LearningGoalVO>(`/learning/goals/${id}`, data),
  /** 逻辑删除目标。 */
  removeGoal: (id: number) => apiDelete<void>(`/learning/goals/${id}`),

  // ============================================================
  // Architecture-4.0-4：场景练习（Scenario Lab，Practice 节点）
  // ============================================================
  /** 上架场景列表（可按知识点/难度过滤） */
  scenarios: (params?: { knowledgeId?: number; difficulty?: number }) => {
    const q = new URLSearchParams()
    if (params?.knowledgeId != null) q.set('knowledgeId', String(params.knowledgeId))
    if (params?.difficulty != null) q.set('difficulty', String(params.difficulty))
    const query = q.toString()
    return apiGet<ScenarioSummaryVO[]>(`/learning/scenarios${query ? `?${query}` : ''}`)
  },
  // ============================================================
  // Architecture-4.2：章节 → 知识点上下文（Knowledge Context）
  // ============================================================
  /**
   * 解析某章节对应的真实知识点（docIds → resource_knowledge_mapping(DOC,ACCEPTED) → kg_entity）。
   * 现有端点均无法回答「这一章对应哪些知识点」：/learning/mastery 无章节维度、
   * /knowledge/entity-graph 为 AI 抽取图谱口径且需按 docId 逐次调用。故本只读端点，
   * 不新增表/字段。章节存在但无 ACCEPTED 映射时返回空 knowledge 数组（如实提示，禁止猜测）。
   */
  chapterKnowledge: (chapterId: number) =>
    apiGet<ChapterKnowledgeVO>(`/learning/chapters/${chapterId}/knowledge`),

  /** 场景详情（含要点名称；keywords 不下发） */
  scenarioDetail: (id: number) => apiGet<ScenarioDetailVO>(`/learning/scenarios/${id}`),
  /** 提交场景作答（确定性关键词覆盖评分 → SCENARIO_COMPLETED 事件） */
  submitScenario: (id: number, data: ScenarioSubmitInput) =>
    apiPost<ScenarioSubmitResultVO>(`/learning/scenarios/${id}/submit`, data),

  // ============================================================
  // Architecture-4.0-7：费曼表达四维评分（Expression 节点）
  // ============================================================
  /** 费曼四维评分 rubric（服务端常量：维度 × 自检准则 × 计分口径） */
  feynmanRubric: () => apiGet<FeynmanRubricVO>('/learning/feynman/rubric'),
  /** 提交费曼表达（确定性自检计分 → FEYNMAN_EXPRESSED 事件） */
  submitFeynman: (data: FeynmanSubmitInput) =>
    apiPost<FeynmanSubmitResultVO>('/learning/feynman/submit', data),

  // ============================================================
  // Architecture-4.1：第一性原理推导（Understanding 节点）
  // ============================================================
  /** 第一性原理六环节 rubric（服务端常量：环节定义 + 计分口径） */
  firstPrinciplesRubric: () => apiGet<FirstPrinciplesRubricVO>('/learning/first-principles/rubric'),
  /** 提交第一性原理推导（结构完整度评估 → FIRST_PRINCIPLES_DERIVED 事件） */
  submitFirstPrinciples: (data: FirstPrinciplesSubmitInput) =>
    apiPost<FirstPrinciplesSubmitResultVO>('/learning/first-principles/submit', data),

  // SM-2 间隔重复复习：quality ∈ [0,5]，<3 重置，3=有印象，5=完全掌握
  // knowledgeId 可选：调用方确知真实知识点时透传，后端写入 FLASHCARD_REVIEWED 事件 metadata，
  // 供 Evidence 解析器把这条证据归属到具体知识点（不传时行为与旧版完全一致）。
  reviewFlashcard: (id: number, quality: number, knowledgeId?: number | null) => {
    const q = new URLSearchParams({ quality: String(quality) })
    if (knowledgeId != null) q.set('knowledgeId', String(knowledgeId))
    return apiPost<void>(`/learning/flashcards/${id}/review?${q.toString()}`)
  },
  // 当前用户待复习队列：新卡 + 已到期卡，后端按用户隔离并限制最多 100 张
  dueFlashcards: () => apiGet<FlashcardVO[]>('/learning/flashcards/due'),
  tasks: () => apiGet<LearningTaskVO[]>('/learning/tasks', { zoneId: Intl.DateTimeFormat().resolvedOptions().timeZone }),
  createTask: (data: LearningTaskInput) => apiPost<void>('/learning/tasks', data),
  updateTaskStatus: (id: number, status: number, source?: 'TASK' | 'LEARNING') =>
    apiPut<void>(`/learning/tasks/${id}/status?status=${status}${source ? '&source='+source : ''}`),
  deleteTask: (id: number, source?: 'TASK' | 'LEARNING') => apiDelete<void>(`/learning/tasks/${id}${source ? '?source='+source : ''}`),

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
    pageNum?: number
    pageSize?: number
  }) => apiGet<PageResult<FlashcardVO>>('/learning/my/flashcards', params),
  myFlashcardDetail: (id: number) => apiGet<FlashcardVO>(`/learning/my/flashcards/${id}`),
  flashcardSchedule: (id: number) => apiGet<FlashcardSchedulePreview>(`/learning/flashcards/${id}/schedule`),
  reviewFlashcardFsrs: (id: number, input: { rating: number; revision: number; settingsRevision: number; requestId: string; knowledgeId?: number | null; sessionId: string }) => apiPost<FlashcardFsrsResult>(`/learning/flashcards/${id}/fsrs-review`, input),
  flashcardSettings: () => apiGet<FlashcardReviewSettings>('/learning/my/flashcards/settings'),
  saveFlashcardSettings: (input: FlashcardReviewSettings) => apiPut<FlashcardReviewSettings>('/learning/my/flashcards/settings', input),
  flashcardStatistics: () => apiGet<{ settings: FlashcardReviewSettings; today: { newCards: number; reviews: number; completed: number } }>('/learning/my/flashcards/statistics'),
  flashcardSession: (id: string) => apiGet<{ sessionId: string; reviews: { cardId: number; rating: number; reviewedAt: string }[] }>(`/learning/flashcards/sessions/${id}`),
  previewFlashcardImport: (input: FlashcardImportRequest) => apiPost<FlashcardImportPreview>('/learning/my/flashcard-exchange/preview', input, { timeout: 60000 }),
  confirmFlashcardImport: (input: {request:FlashcardImportRequest;token:string;skipProblems:boolean}) => apiPost<FlashcardImportOutcome>('/learning/my/flashcard-exchange/confirm', input, { timeout:60000 }),
  exportFlashcardContent: (format:'csv'|'tsv') => apiGet<FlashcardContentExport>('/learning/my/flashcard-exchange/export', {format}),
  previewFlashcardNote: (data: FlashcardNoteInput) => apiPost<{ cards: { ordinal: number; front: string; back: string }[] }>('/learning/my/flashcard-notes/preview', data),
  createFlashcardNote: (data: FlashcardNoteInput) => apiPost<FlashcardNoteVO>('/learning/my/flashcard-notes', data),
  updateFlashcardNote: (id: number, data: FlashcardNoteInput) => apiPut<FlashcardNoteVO>(`/learning/my/flashcard-notes/${id}`, data),
  flashcardNoteByCard: (id: number) => apiGet<FlashcardNoteVO | null>(`/learning/my/flashcard-notes/by-card/${id}`),
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
  // Phase 0：学习周报 / 学习偏好（对应后端 WeeklyReport / UserLearningPref，此前前端缺 API 层）
  // ============================================================
  /** 获取当前周周报（不存在则自动生成） */
  getWeeklyReport: () => apiGet<WeeklyReportVO>('/learning/weekly-report'),
  /** 历史周报列表（最近 8 周） */
  listWeeklyReports: () => apiGet<WeeklyReportVO[]>('/learning/weekly-report/list'),
  /** 手动生成当前周周报 */
  generateWeeklyReport: () => apiPost<WeeklyReportVO>('/learning/weekly-report/generate'),
  /** 获取学习偏好（不存在则返回默认值） */
  getPrefs: () => apiGet<UserLearningPrefVO>('/learning/prefs'),
  /** 保存学习偏好（upsert） */
  savePrefs: (data: UserLearningPrefInput) => apiPost<UserLearningPrefVO>('/learning/prefs', data),

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

  /** Phase 3H：统一学习分析（纯离线口径，零模型调用；未评分主观题不入正确率分母）。 */
  analyticsOverview: (windowDays = 30) =>
    apiGet<LearningAnalyticsOverview>('/learning/analytics/overview', { windowDays }),
}

// ===== Phase 3H：统一学习分析（与后端 LearningAnalyticsVO 对齐）=====

export interface LearningAnalyticsBucket {
  key: string
  label: string
  total: number
  correct: number
  /** 0-100，无样本时为 null。 */
  rate: number | null
}

export interface LearningAnalyticsDayPoint {
  date: string
  answered: number
  correct: number
  rate: number | null
}

export interface LearningAnalyticsQuizSummary {
  totalAnswered: number
  gradedCount: number
  correctCount: number
  /** 0-100；无已评分记录为 null。 */
  correctRate: number | null
  /** 未评分（主观题待 AI/人工）数量，不计入正确率。 */
  ungradedCount: number
  avgTimeCostSeconds: number | null
}

export interface LearningAnalyticsCodeSummary {
  totalSubmissions: number
  acceptedSubmissionCount: number
  attemptedQuestions: number
  firstPassQuestions: number
  /** 每题最早正式提交即 ACCEPTED 的占比；平台失败不占首次负例。 */
  firstPassRate: number | null
  verdictDistribution: LearningAnalyticsBucket[]
  /** Judge 平台失败剔除数（不计用户错误）。 */
  excludedPlatformFailure: number
}

export interface LearningAnalyticsMistakeSummary {
  total: number
  pending: number
  mastered: number
  dueToday: number
  weeklyNew: number
  avgReviewCount: number | null
  maxReviewCount: number | null
}

export interface LearningAnalyticsOverview {
  windowDays: number
  quiz: LearningAnalyticsQuizSummary
  code: LearningAnalyticsCodeSummary
  mistake: LearningAnalyticsMistakeSummary
  byType: LearningAnalyticsBucket[]
  byTopic: LearningAnalyticsBucket[]
  dailyTrend: LearningAnalyticsDayPoint[]
}
