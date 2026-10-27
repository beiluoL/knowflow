// Phase 3G 统一题库中心 · 算法题（CODE）请求层
// 用户侧 /api/code-bank/**（隐藏用例与参考解答不下发）；管理侧 /api/admin/code-problems/**（ADMIN）
import { apiGet, apiPost } from './request'

export type JudgeStatusCode =
  | 'QUEUED'
  | 'COMPILING'
  | 'RUNNING'
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'COMPILE_ERROR'
  | 'RUNTIME_ERROR'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'SYSTEM_ERROR'
  | 'JUDGE_UNAVAILABLE'

export interface CodeTopic {
  code: string
  name: string
  parentCode?: string | null
  level: number
  sortOrder: number
}

export interface CodeProblemListItem {
  id: number
  title: string
  difficulty: number
  topicCode?: string
  topicName?: string
  tags?: string
  /** NONE / ATTEMPTED / SOLVED（当前用户） */
  myStatus: 'NONE' | 'ATTEMPTED' | 'SOLVED'
  submissionCount?: number
}

export interface CodeTestCasePublic {
  orderNo: number
  input: string
  expectedOutput: string
  weight?: number
}

export interface CodeProblemDetail {
  id: number
  title: string
  content: string
  difficulty: number
  topicCode?: string
  topicName?: string
  tags?: string
  supportedLanguages?: string
  functionSignature?: string
  starterCode?: string
  constraints?: string
  examples?: string
  timeLimitMs?: number
  memoryLimitMb?: number
  judgeMode?: string
  publicCases: CodeTestCasePublic[]
  draftCode?: string | null
  /** 题解：仅本人已 ACCEPTED 后返回 */
  solutionExplanation?: string
  complexityAnalysis?: string
  followUps?: string
  commonMistakes?: string
  solved: boolean
  judgeExecutionEnabled: boolean
  judgeMode2?: string
}

export interface CodeCaseResult {
  hidden: boolean
  orderNo: number
  input?: string
  expected?: string
  actual?: string
  passed: boolean
  timeMs?: number
  status: string
}

export interface CodeSubmitResult {
  submissionId: number
  judgeStatus: JudgeStatusCode
  /** 平台失败时为 null（未判完绝不记 0） */
  score: number | null
  passedCount?: number
  totalCount?: number
  /** 整题最大执行耗时（ms），可能不返回 */
  executionTimeMs?: number
  compileOutput?: string
  caseResults?: CodeCaseResult[]
  executionEnabled: boolean
  message?: string
}

export interface CodeSubmission {
  id: number
  questionId: number
  language: string
  sourceCode?: string
  submissionType: 'RUN' | 'SUBMIT'
  judgeStatus: JudgeStatusCode
  score: number | null
  passedCount?: number
  totalCount?: number
  executionTimeMs?: number
  compileOutput?: string
  caseResults?: CodeCaseResult[]
  createTime?: string
  contextType?: string
}

export interface PageData<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** Phase 3H 两次提交确定性 diff（无 AI）。 */
export interface CodeSubmissionDiffRef {
  id: number
  judgeStatus: JudgeStatusCode
  score: number | null
  passedCount?: number
  totalCount?: number
  executionTimeMs?: number
  memoryUsedKb?: number
  createTime?: string
}

export interface CodeDiffLine {
  type: 'EQUAL' | 'ADDED' | 'REMOVED'
  oldLine: number | null
  newLine: number | null
  text: string
}

export interface CodeSubmissionDiff {
  from: CodeSubmissionDiffRef
  to: CodeSubmissionDiffRef
  lines: CodeDiffLine[]
  changedLines: number
  truncated: boolean
}

export interface AdminCodeCase {
  input: string
  expectedOutput: string
  visibility: 'PUBLIC' | 'HIDDEN'
  weight?: number
  orderNo?: number
}

export interface AdminSaveProblemRequest {
  id?: number
  title: string
  content: string
  difficulty: number
  topicCode?: string
  tags?: string
  canonicalKey?: string
  supportedLanguages?: string
  functionSignature?: string
  starterCode?: string
  constraints?: string
  examples?: string
  timeLimitMs?: number
  memoryLimitMb?: string | number
  judgeMode?: string
  checker?: string
  toleranceValue?: number
  referenceSolution?: string
  solutionExplanation?: string
  complexityAnalysis?: string
  followUps?: string
  commonMistakes?: string
  /** 0 草稿 / 1 发布（仍需 APPROVED 才对用户可见） */
  status: number
  cases: AdminCodeCase[]
}

export interface AdminCodeProblem {
  full: boolean
  id: number
  title: string
  content: string
  difficulty: number
  topicCode?: string
  tags?: string
  canonicalKey?: string
  status: number
  reviewStatus?: string
  version?: number
  creatorId?: number
  /** 扩展字段 JSON 字符串（含参考解答，ADMIN 专属） */
  problemJson?: string
  cases?: AdminCodeCase[]
}

export interface CodeAiAssistRequest {
  /** 指定提交；为空时使用最近一次提交/当前草稿（仅作 AI 输入，绝不触发判题）。 */
  submissionId?: number
  /** review 代码点评（默认）/ explain 代码解释 / hint 分级提示。 */
  kind?: 'review' | 'explain' | 'hint'
  /** hint 等级 1-5。 */
  hintLevel?: number
}

export interface CodeAiAssistVO {
  questionId: number
  submissionId?: number
  kind?: string
  hintLevel?: number
  /** AI / BASIC（降级或关闭）。BASIC 永远不改判题结论。 */
  mode: 'AI' | 'BASIC'
  failureKind?: string | null
  content?: string
  judgeStatus?: JudgeStatusCode
  score?: number | null
  passedCount?: number
  totalCount?: number
  compileOutput?: string
  executionTimeMs?: number
  /** 仅本人 ACCEPTED 后返回。 */
  solutionExplanation?: string
  complexityAnalysis?: string
  commonMistakes?: string
  judgeExecutionEnabled: boolean
}

export const codeBankApi = {
  topics: () => apiGet<CodeTopic[]>('/code-bank/topics'),
  problems: (params: { keyword?: string; topicCode?: string; difficulty?: number; page?: number; pageSize?: number }) =>
    apiGet<PageData<CodeProblemListItem>>('/code-bank/problems', params),
  detail: (id: number) => apiGet<CodeProblemDetail>(`/code-bank/problems/${id}`),
  saveDraft: (id: number, payload: { language: string; sourceCode: string }) =>
    apiPost<{ saved: boolean }>(`/code-bank/problems/${id}/draft`, payload),
  submit: (id: number, payload: { language: string; sourceCode: string; type: 'RUN' | 'SUBMIT' }) =>
    apiPost<CodeSubmitResult>(`/code-bank/problems/${id}/submissions`, payload),
  mySubmissions: (params: { questionId?: number; page?: number; pageSize?: number }) =>
    apiGet<PageData<CodeSubmission>>('/code-bank/submissions', params),
  submission: (id: number) => apiGet<CodeSubmission>(`/code-bank/submissions/${id}`),
  /** Phase 3H：对比本题两条正式提交（服务端按时间自动定向）。 */
  submissionDiff: (problemId: number, fromId: number, toId: number) =>
    apiGet<CodeSubmissionDiff>(`/code-bank/problems/${problemId}/submission-diff`, { fromId, toId }),
  /** Phase 3H：AI 代码辅助（提示/解释/点评）；AI 不可用时服务端返回 BASIC 事实数据，不触发判题。 */
  aiAssist: (id: number, payload: CodeAiAssistRequest) =>
    apiPost<CodeAiAssistVO>(`/code-bank/problems/${id}/ai-assist`, payload),
}

export const adminCodeProblemApi = {
  list: (params: {
    page?: number
    pageSize?: number
    keyword?: string
    topicCode?: string
    difficulty?: number
    reviewStatus?: string
    status?: number
  }) => apiGet<PageData<AdminCodeProblem>>('/admin/code-problems', params),
  detail: (id: number) => apiGet<AdminCodeProblem>(`/admin/code-problems/${id}`),
  save: (payload: AdminSaveProblemRequest) =>
    apiPost<{ id: number }>('/admin/code-problems/save', payload),
  review: (id: number, decision: 'APPROVED' | 'REJECTED', comment?: string) =>
    apiPost<{ reviewed: boolean }>(`/admin/code-problems/${id}/review`, { decision, comment }),
  publish: (id: number, published: boolean) =>
    apiPost<{ published: boolean }>(`/admin/code-problems/${id}/publish?published=${published}`),
}

export const JUDGE_STATUS_LABELS: Record<JudgeStatusCode, string> = {
  QUEUED: '排队中',
  COMPILING: '编译中',
  RUNNING: '运行中',
  ACCEPTED: '通过',
  WRONG_ANSWER: '答案错误',
  COMPILE_ERROR: '编译错误',
  RUNTIME_ERROR: '运行错误',
  TIME_LIMIT_EXCEEDED: '超时',
  MEMORY_LIMIT_EXCEEDED: '超内存',
  SYSTEM_ERROR: '系统错误',
  JUDGE_UNAVAILABLE: '判题服务未就绪',
}

export const DIFFICULTY_LABELS = ['简单', '中等', '困难']
