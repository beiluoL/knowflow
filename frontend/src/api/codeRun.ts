// 代码在线运行沙箱请求层（SC1-IDE-01/02/03、SC1-AI-01/02/03）
import { apiPost } from './request'

export type CodeRunStatus =
  | 'SUCCESS'
  | 'COMPILE_ERROR'
  | 'RUNTIME_ERROR'
  | 'TIMEOUT'
  | 'INTERNAL_ERROR'

export interface CodeRunPayload {
  language: string
  code: string
  stdin?: string
  timeLimitMs?: number
  /** SC1-IDE-02：工作区模式，运行用户持久目录中的入口文件 */
  workspace?: boolean
  /** 工作区模式入口文件名（如 main.py / Main.java） */
  entryFile?: string
}

export interface CodeRunResultDTO {
  status: CodeRunStatus
  output: string | null
  error: string | null
  exitCode: number | null
  timeUsedMs: number | null
}

export interface CodeAssistPayload {
  language: string
  code: string
  error?: string | null
  output?: string | null
  question?: string
}

export interface CodeAssistResultDTO {
  /** 服务端是否存在可用 AI 配置 */
  configured: boolean
  /** AI 解释 / 建议文本 */
  answer: string
}

// ===== SC1-AI-03 代码错题归集 =====
export interface CodeMistakeCollectPayload {
  language: string
  error: string
  code: string
  questionId?: number | null
}
export interface CodeMistakeRelatedDocDTO {
  id: number
  title: string
  snippet: string
}
export interface CodeMistakeCollectResultDTO {
  mistakeId: number
  errorType: string
  errorSummary: string
  collected: boolean
  relatedDocs: CodeMistakeRelatedDocDTO[]
}

// ===== SC1-AI-02 自动化代码评估 =====
export interface CodeAssessPayload {
  questionId?: number | null
  language: string
  code: string
  testCasesJson?: string
  timeLimitMs?: number
}
export interface CodeAssessStaticIssueDTO {
  rule: string
  message: string
  line: number
}
export interface CodeAssessResultDTO {
  score: number
  level: string
  passedTests: number
  totalTests: number
  passed: boolean
  staticIssues: CodeAssessStaticIssueDTO[]
  aiReport: string | null
  aiConfigured: boolean
  summary: string
}

// ===== 轻量在线调试（推进 2.1） =====
export interface CodeDebugPayload {
  language: string
  code: string
  timeLimitMs?: number
}
export interface CodeDebugTraceStepDTO {
  line: number
  event: string
  vars: string
}
export interface CodeDebugResultDTO {
  status: string
  trace: CodeDebugTraceStepDTO[]
  errorLine: number | null
  error: string | null
  output: string | null
  timeUsedMs: number | null
}

export const codeRunApi = {
  /** 在线运行代码，后端基于系统运行时真实执行（Python/Java/JavaScript/C++） */
  run: (payload: CodeRunPayload) => apiPost<CodeRunResultDTO>('/code/run', payload),
  /** AI 编程助手内联协助：解释错误 / 回答编程问题（SC1-AI-01） */
  aiAssist: (payload: CodeAssistPayload) => apiPost<CodeAssistResultDTO>('/code/ai-assist', payload),
  /** 代码运行异常自动归集到错题本（SC1-AI-03） */
  collectMistake: (payload: CodeMistakeCollectPayload) =>
    apiPost<CodeMistakeCollectResultDTO>('/mistakes/collect-code', payload),
  /** 自动化代码评估：动态评测 + 静态检查 + AI 报告（SC1-AI-02） */
  assess: (payload: CodeAssessPayload) => apiPost<CodeAssessResultDTO>('/code/assess', payload),
  /** 轻量在线调试运行：逐行追踪 + 错误行号定位（推进 2.1） */
  debug: (payload: CodeDebugPayload) => apiPost<CodeDebugResultDTO>('/code/debug', payload),
}
