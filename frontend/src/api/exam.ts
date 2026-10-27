// Java + AI 智能考试系统（Exam OS）请求层
// 对应后端 /api/exam/**：模板 → 组卷 → 作答 → 评分 → 分析 → 错题
import { apiGet, apiPost } from './request'

export type QuestionType =
  | 'SINGLE_CHOICE'
  | 'MULTIPLE_CHOICE'
  | 'TRUE_FALSE'
  | 'FILL_BLANK'
  | 'SHORT_ANSWER'
  | 'CODE'

export type ComposeStrategy =
  | 'RANDOM'
  | 'FREQUENCY'
  | 'BALANCED'
  | 'GRADIENT'
  | 'MISTAKE'
  | 'WEAK'
  | 'SCENARIO'
  | 'REVIEW'

export interface ExamTemplate {
  id: number
  templateKey: string
  name: string
  description?: string
  domain: string
  topics: string[]
  durationMinutes: number
  totalScore: number
  passScore: number
  mode: 'PRACTICE' | 'EXAM'
  version?: string
  typeDistribution: Record<string, number>
  multiPolicy?: string
  available?: boolean
  shortage?: string
}

export interface ExamPaper {
  id: number
  name: string
  domain?: string
  topics: string[]
  source: 'TEMPLATE' | 'SMART' | 'AI'
  templateKey?: string
  strategy?: string
  seedValue?: number
  durationMinutes: number
  totalScore: number
  passScore: number
  mode: 'PRACTICE' | 'EXAM'
  shuffleQuestion?: number
  shuffleOption?: number
  allowPause?: number
  allowChange?: number
  reviewStatus?: string
  ownerUserId?: number | null
  questionCount: number
  createdAt?: string
  aiInfo?: Record<string, unknown>
  /** Phase 3H：true 表示 AI 出卷失败/质量不足后自动降级为确定性题库组卷。 */
  aiFallback?: boolean
  /** 降级原因（AI 失败分类或校验不足说明），仅 aiFallback=true 时非空。 */
  fallbackReason?: string
}

export interface TakeQuestion {
  paperQuestionId: number
  ordinal: number
  title: string
  content: string
  questionType: QuestionType
  options: string[]
  score: number
  difficulty: number
  topic?: string
  /** Phase 3G：CODE 题公开作答信息（starterCode/签名/公开用例，已剔除参考解与隐藏用例） */
  codeProblem?: Record<string, unknown> & {
    functionSignature?: string
    starterCode?: string
    constraints?: string
    examples?: string
    timeLimitMs?: number
    memoryLimitMb?: number
    publicCases?: { orderNo: number; input: string; expectedOutput: string }[]
  }
}

export interface AttemptVO {
  attemptId: number
  paperId: number
  paperName: string
  mode: string
  status: string
  startTime: string
  serverDeadline: string
  serverNow: string
  durationMinutes: number
  totalScore: number
  passScore: number
  questionCount: number
  gradingStatus: string
  draftRevision: number
  draft: Record<string, string>
  marked: number[]
  questions: TakeQuestion[]
  deadlineExpired: boolean
}

export interface RubricDimension {
  key: string
  name: string
  weight: number
  score: number
  maxScore: number
}

export interface ResultItem {
  paperQuestionId: number
  ordinal: number
  topic?: string
  title: string
  content: string
  questionType: QuestionType
  options: string[]
  score: number | null
  maxScore: number
  difficulty: number
  userAnswer?: string
  correctAnswer?: string
  correctAnswerText?: string
  explanation?: string
  correct: boolean | null
  gradeType?: string
  graded: boolean
  rubric?: RubricDimension[]
  hitPoints?: string[]
  missingPoints?: string[]
  errors?: string[]
  suggestions?: string[]
  confidence?: number
  aiGradeNote?: string
}

export interface GroupAnalysis {
  key: string
  name: string
  count: number
  gradedCount: number
  score: number
  maxScore: number
  correctCount: number
  /** 得分率，0–100 整数百分比；null = 样本量不足 */
  rate: number | null
}

export interface ExamResult {
  attemptId: number
  paperId: number
  paperName: string
  mode: string
  status: string
  gradingStatus: string
  totalScore: number | null
  maxScore: number | null
  paperMaxScore: number
  correctCount: number
  gradedCount: number
  questionCount: number
  passed: boolean
  passScore: number
  submitTime?: string
  durationSeconds?: number
  deadlineExpired: boolean
  aiAvailable: boolean
  aiMessage?: string
  items: ResultItem[]
  typeAnalysis: GroupAnalysis[]
  topicAnalysis: GroupAnalysis[]
}

export interface AttemptListItem {
  attemptId: number
  paperId: number
  paperName: string
  mode: string
  status: string
  gradingStatus?: string
  totalScore: number | null
  maxScore: number | null
  paperMaxScore: number
  correctCount: number
  gradedCount: number
  questionCount: number
  startTime: string
  submitTime?: string
  durationSeconds?: number
  passScore: number
  passed: boolean
}

export interface BankAvailability {
  total: number
  byType: Record<string, number>
  byTopic: Record<string, number>
  requested: Record<string, number>
  available: Record<string, number>
  sufficient: boolean
  message?: string
}

export interface ExamMistake {
  id: number
  question: string
  wrongAnswer?: string
  correctAnswer?: string
  topic?: string
  difficulty?: number
  reviewCount?: number
  mastered: boolean
  lastReviewTime?: string
  createTime?: string
  questionId?: number | null
}

export interface PageData<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface DashboardData {
  attemptCount: number
  gradedPaperCount: number
  avgRate: number
  mistakeCount: number
  pendingMistakeCount: number
  recentAttempts: AttemptListItem[]
  recentMistakes: ExamMistake[]
  weakTopics: GroupAnalysis[]
  templates: ExamTemplate[]
}

export interface AiGradeResult {
  attemptId: number
  status: string
  gradingStatus: string
  gradedAi: number
  pending: number
  aiAvailable: boolean
  message?: string
}

export interface ComposeRequest {
  name?: string
  domain?: string
  topics?: string[]
  includeAnswered?: boolean
  strategy?: ComposeStrategy
  seed?: number
  typeDistribution?: Record<string, number>
  difficulties?: number[]
  questionCount?: number
  totalScore?: number
  durationMinutes?: number
  passScore?: number
  mode?: 'PRACTICE' | 'EXAM'
  shuffleQuestion?: number
  shuffleOption?: number
  allowPause?: number
  allowChange?: number
  multiPolicy?: 'ALL_OR_NOTHING' | 'PARTIAL_CREDIT'
}

export const examApi = {
  dashboard: () => apiGet<DashboardData>('/exam/dashboard'),
  templates: () => apiGet<ExamTemplate[]>('/exam/templates'),
  papers: (mineOnly = false) => apiGet<ExamPaper[]>('/exam/papers', { mineOnly }),
  availability: (req: ComposeRequest) => apiPost<BankAvailability>('/exam/bank/availability', req),
  compose: (req: ComposeRequest) => apiPost<ExamPaper>('/exam/papers/compose', req),
  composeAi: (req: Record<string, unknown>) => apiPost<ExamPaper>('/exam/papers/ai', req),
  createFromTemplate: (key: string, req?: Record<string, unknown>) =>
    apiPost<ExamPaper>(`/exam/templates/${key}/papers`, req ?? {}),
  startAttempt: (paperId: number) => apiPost<AttemptVO>(`/exam/papers/${paperId}/attempts`),
  getAttempt: (attemptId: number) => apiGet<AttemptVO>(`/exam/attempts/${attemptId}`),
  saveDraft: (attemptId: number, answers: Record<string, string>, marked: number[], expectedRevision: number) =>
    apiPost<AttemptVO>(`/exam/attempts/${attemptId}/draft`, { answers, marked, expectedRevision }),
  submit: (
    attemptId: number,
    payload: {
      answers: Record<string, string>
      marked?: number[]
      timeCosts?: Record<string, number>
      expectedRevision?: number
      clientVersion?: string
    }
  ) => apiPost<ExamResult>(`/exam/attempts/${attemptId}/submit`, payload),
  result: (attemptId: number) => apiGet<ExamResult>(`/exam/attempts/${attemptId}/result`),
  attempts: () => apiGet<AttemptListItem[]>('/exam/attempts'),
  aiGrade: (attemptId: number) => apiPost<AiGradeResult>(`/exam/attempts/${attemptId}/ai-grade`),
  /** Phase 3G：对交卷后仍 PENDING_JUDGE 的代码题重试判题（幂等，graded 题不会重复计分） */
  rejudgeCode: (attemptId: number) => apiPost<ExamResult>(`/exam/attempts/${attemptId}/rejudge-code`),
  mistakes: (params: Record<string, unknown>) =>
    apiGet<PageData<ExamMistake>>('/exam/mistakes', params),
  masterMistake: (mistakeId: number) =>
    apiPost<boolean>(`/exam/mistakes/${mistakeId}/master`),
}

export const QUESTION_TYPE_LABELS: Record<QuestionType, string> = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  TRUE_FALSE: '判断题',
  FILL_BLANK: '填空题',
  SHORT_ANSWER: '主观题',
  CODE: '算法代码题',
}

export const TOPIC_LABELS: Record<string, string> = {
  HASHMAP: 'HashMap',
  CONCURRENT_HASHMAP: 'ConcurrentHashMap',
  AQS: 'AQS / 锁',
  THREADPOOL: '线程池',
  JVM: 'JVM / GC',
  SPRING: 'Spring / 事务',
  MYSQL: 'MySQL',
  REDIS: 'Redis',
  RAG: 'RAG / Embedding',
  AGENT: 'Tool Calling / Agent',
  PROJECT: '项目架构',
}
