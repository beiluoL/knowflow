// 在线答题请求层：封装拉取练习题、提交判分与答题统计接口调用。
import { apiGet, apiPost } from './request'

/** 在线练习题目（含答案与解析，支持「看答案」模式） */
export interface QuizPracticeVO {
  id: number
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
}

/** 单题判分明细 */
export interface QuizResultItem {
  questionId: number
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

export const quizApi = {
  /** 拉取在线练习题目（仅已发布） */
  questions: (params: {
    categoryId?: number
    difficulty?: number
    questionType?: string
    count?: number
  } = {}) => apiGet<QuizPracticeVO[]>('/quiz/questions', params),

  /** 提交作答并自动判分（答错自动同步错题本） */
  submit: (answers: QuizAnswerItem[]) =>
    apiPost<QuizSubmitResult>('/quiz/submit', { answers }),

  /** 我的答题统计 */
  stats: () => apiGet<QuizStats>('/quiz/stats'),
}
