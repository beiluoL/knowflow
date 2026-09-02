/**
 * Architecture-7：AI 学习教练类型（§14/§46-48/§300）。
 *
 * 设计边界（与 Architecture-6 一致）：
 *  - 只消费 Architecture-6 的 Diagnosis / Strategy / NextAction，不重新造一套（§46-48）；
 *  - Coach Context 只含真实、最小化、隐私过滤后的学习数据（§15-23：默认不传经济/PII）；
 *  - AI 只返回 appKey / knowledgeId / actionType / reason，CTA 由本地系统生成（§313-316）。
 */
import type { DiagnosisType, LearningDiagnosis, Severity } from '../intelligence/types'
import type { NextLearningAction } from '../types'
import type { KnowledgeMasteryVO, LearningTaskVO, LearningPathVO } from '@/api/types'

/** 对话模式（§297）：普通助手 / 学习教练。禁止 any（§298-299）。 */
export type CoachMode = 'GENERAL' | 'LEARNING_COACH'

/** 构建 Coach Context 的输入：全部来自页面已加载的真实数据，不新增 API（§473-476）。 */
export interface CoachContextInput {
  reviewRequired: KnowledgeMasteryVO[]
  weakPoints: KnowledgeMasteryVO[]
  tasks: LearningTaskVO[]
  currentPath?: LearningPathVO | null
  nextAction?: NextLearningAction | null
  diagnoses?: LearningDiagnosis[]
  /** 可选：最近学习动态摘要（真实数据时提供；缺则 Coach 如实告知不足，§87/§347）。 */
  recentActivity?: CoachActivityItem[]
}

/** 最近学习动态（轻量摘要，非原始 LearningEvent 列表）。 */
export interface CoachActivityItem {
  type: string
  label: string
  at?: string
}

/** 经最小化、隐私过滤后的学习教练上下文（可序列化，送入 LLM 的「数据」）。 */
export interface LearningCoachContext {
  /** 是否存在任何真实学习数据（决定是否走「空数据」诚实分支，§87/§347）。 */
  hasData: boolean
  nextAction?: {
    type: string
    title: string
    reason: string
    knowledgeName?: string
  }
  diagnoses: Array<{
    type: DiagnosisType
    title: string
    reason: string
    severity: Severity
    knowledgeName?: string
  }>
  reviewRequired: Array<{ knowledgeName: string; masteryScore: number; nextReviewAt?: string | null }>
  weakPoints: Array<{ knowledgeName: string; masteryScore: number; weaknessTypes: string[] }>
  tasks: Array<{ title: string; status?: number; deadline?: string | null }>
  currentPath?: { title: string; progress?: number } | null
  recentActivity?: CoachActivityItem[]
  /** 供 UI 直接展示的「真实学习状态」摘要（来自本地数据，不依赖 AI，§367）。 */
  summary: {
    reviewCount: number
    weakCount: number
    taskCount: number
    hasPath: boolean
  }
}

/** AI 结构化建议：AI 仅返回这些字段，CTA 由本地系统生成（§313-316）。 */
export interface CoachSuggestion {
  appKey?: string
  knowledgeId?: number
  actionType?: string
  reason?: string
}

/** Coach 完整响应（解析后）。 */
export interface CoachResponse {
  message: string
  suggestion?: CoachSuggestion
  /** 经校验后的建议（含 route / valid），UI 据此决定是否展示 CTA（§196/§416）。 */
  validation?: CoachSuggestionValidation
}

/** 建议校验结果（§125/§196-202）：非法 → valid:false，UI 不显示 CTA，但文本仍展示。 */
export interface CoachSuggestionValidation {
  valid: boolean
  appKey?: string
  /** 校验通过的真实路由（来自本地 Registry，非 AI 提供，§126-127）。 */
  route?: string
  reason?: string
  error?: string
}
