// 在线答题请求层：封装拉取练习题、提交判分与答题统计接口调用。
import { apiGet, apiPost } from './request'

/** 在线练习题目（含答案与解析，支持「看答案」模式） */
export interface QuizPracticeVO {
  id: number
  version?: number
  contentHash?: string
  title: string
  content: string
  questionType: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'FILL_BLANK' | 'TRUE_FALSE' | 'SHORT_ANSWER'
  options: string[]
  answer: string
  explanation?: string
  difficulty: number
  tags?: string
}

/** 单题提交项 */
export interface QuizAnswerItem {
  questionId: number
  userAnswer: string
  timeCost?: number
  expectedContentHash?: string
  baselineAnswerRecordId?: number
}

/** 单题判分明细 */
export interface QuizResultItem {
  questionId: number
  answerRecordId?: number
  questionVersion?: number
  contentHash?: string
  userAnswer: string
  correctAnswer: string
  correct: boolean
  explanation?: string
  score: number
}

/** 提交判分结果 */
export interface QuizSubmitResult {
  total: number
  correct: number
  wrong: number
  accuracy: number
  syncedMistakes: number
  items: QuizResultItem[]
}

/** 累计答题统计 */
export interface QuizStats {
  total: number
  correct: number
  wrong: number
  accuracy: number
}

/** Phase 3H：题目讲解结果（AI 个性化 / BASIC 标准解析降级）。 */
export interface QuizExplainVO {
  questionId: number
  /** AI = 模型个性化讲解；BASIC = AI 不可用，回退题库已有解析。 */
  mode: 'AI' | 'BASIC'
  /** BASIC 时的失败分类（AI_UNCONFIGURED / AI_TIMEOUT …），可空。 */
  failureKind?: string | null
  /** 讲解正文；AI 失败且题库无解析时为 null（前端显式「暂无解析」，禁止编造）。 */
  content?: string | null
  answer?: string
  hasExplanation: boolean
  explanation?: string | null
  topicCode?: string
  questionType?: string
}

/** Phase 3H：规则引擎智能推荐题目。 */
export interface SmartPracticeItem {
  id: number
  title: string
  content: string
  questionType: string
  options?: string[]
  answer: string
  explanation?: string
  /** 选题原因：DUE_REVIEW / HIGH_FREQ_MISTAKE / WEAK_TOPIC / TARGET_TOPIC / NEW_UNANSWERED / GENERAL_POOL。 */
  reason: string
  difficulty?: number
  tags?: string
  topicCode?: string
}

export interface SmartPracticeVO {
  ruleVersion: string
  bankVersion?: string
  sufficient: boolean
  message?: string
  reasonCounts?: Record<string, number>
  items: SmartPracticeItem[]
}

export interface QuizLearningReview {
  questionId: number
  practiceAvailable: boolean
  chapterId?: number | null
  baselineAnswerRecordId?: number | null
  attempts: { answerRecordId: number; userAnswer: string; correct: boolean; answeredAt?: string; eventId?: number; snapshotState?: string; snapshot?: { title?: string | null; content: string | null; questionType?: string; options?: string | null; version?: number; answer?: string; explanation?: string; gradingRule?: string; contentHash?: string } | null }[]
  cards: { cardId: number; front: string; knowledgeId: number; mappingStatus: string }[]
  reviews: { eventId: number; cardId: number; knowledgeId: number; rating: number; dueAt: string; algorithm: string }[]
  mistakes: { mistakeId: number; mastered: number }[]
  comparison: { paired: boolean; comparable: boolean; reviewConfirmed?: boolean; note: string; firstCorrect?: boolean; secondCorrect?: boolean; firstAnswerRecordId?: number; secondAnswerRecordId?: number }
  knowledgeIds: number[]
}

export const quizApi = {
  learningReview: (questionId: number) => apiGet<QuizLearningReview>(`/quiz/questions/${questionId}/learning-review`),
  createReviewCard: (answerId: number, front: string, back: string) => apiPost<{cardId:number;mappingStatus:string}>(`/quiz/answers/${answerId}/flashcard`, {front,back}),
  /** 拉取在线练习题目（仅已发布） */
  questions: (params: {
    questionId?: number
    categoryId?: number
    difficulty?: number
    questionType?: string
    count?: number
  } = {}) => apiGet<QuizPracticeVO[]>('/quiz/questions', params),

  /** 提交作答并自动判分（答错自动同步错题本） */
  submit: (answers: QuizAnswerItem[], requestId?: string) =>
    apiPost<QuizSubmitResult>('/quiz/submit', { answers, requestId }),

  /** 我的答题统计 */
  stats: () => apiGet<QuizStats>('/quiz/stats'),

  /** Phase 3H：题目讲解（AI 不可用时服务端自动回退 BASIC 标准解析，绝不编造）。 */
  explain: (questionId: number, userAnswer?: string) =>
    apiGet<QuizExplainVO>('/quiz/explain', { questionId, userAnswer }),

  /** Phase 3H：规则引擎智能推荐（确定性，零模型调用）。 */
  smartRecommend: (params: {
    topics?: string
    difficulties?: string
    count?: number
    seed?: number
  }) => apiGet<SmartPracticeVO>('/quiz/smart-recommend', params),
}
