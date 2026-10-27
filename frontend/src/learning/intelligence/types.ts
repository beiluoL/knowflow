/**
 * Architecture-6：Learning Intelligence 类型（§11/§30-31/§43）
 *
 * 设计边界（§18/§33-35）：
 *  - Learning Intelligence 是「推导结果」，不是新的真相源；
 *  - KnowledgeMastery / LearningEvent / Review 才是真相源（§18）；
 *  - 本文件所有类型均为纯数据描述，不调用任何 API、不持有状态。
 *
 * Diagnosis 类型上限 6~8 个（§10）。最终采用 7 个，对应真实可推导的状态：
 *  REVIEW_OVERDUE / WEAK_KNOWLEDGE / REPEATED_MISTAKE / LOW_ACCURACY
 *  / PATH_STALLED / INACTIVE_LEARNING / HEALTHY_PROGRESS
 */
import type { KnowledgeMasteryVO, LearningPathVO, LearningTaskVO, LearningEventVO } from '@/api/types'

/** 诊断结论类型（§9/§43，优先级见 diagnosis.ts） */
export type DiagnosisType =
  | 'REVIEW_OVERDUE' // 有待复习知识点（reviewRequired > 0）
  | 'WEAK_KNOWLEDGE' // 有薄弱知识点（weakPoints 中存在）
  | 'REPEATED_MISTAKE' // 同一知识点近期重复出错（需真实 mapping，§67-69）
  | 'LOW_ACCURACY' // 近期练习正确率偏低（需真实聚合，§70-72）
  | 'PATH_STALLED' // 学习路径进度停滞（需进度变化时间，§23）
  | 'INACTIVE_LEARNING' // 最近一段时间没有学习记录（需真实事件，§73-74）
  | 'HEALTHY_PROGRESS' // 复习/巩固/任务均正常推进

/** 证据类型（§30，仅真实可获得的才出现，§166-167） */
export type EvidenceType =
  | 'WEAK_MASTERY' // 掌握度偏弱
  | 'REVIEW_DUE' // 已进入复习队列
  | 'MISTAKE_COUNT' // 近期相关题目出错次数
  | 'QUIZ_ACCURACY' // 近期练习正确率
  | 'PATH_PROGRESS' // 路径进度
  | 'RECENT_ACTIVITY' // 最近学习活跃度

/** 严重度（产品标签，非伪精确评分，§159-162） */
export type Severity = 'HIGH' | 'MEDIUM' | 'LOW'

/** 单条证据（真实数据，§26-29：无数字就不写数字，禁止伪造） */
export interface LearningEvidence {
  type: EvidenceType
  /** 用户可读的描述，如「掌握度 42%」 */
  label: string
  /** 可选的量化值（真实，如 42）；无则不展示 */
  value?: number
}

/** 一条学习诊断结论（§31：必须带证据，禁止裸 type） */
export interface LearningDiagnosis {
  type: DiagnosisType
  /** 用户可见标题，如「AQS 需要复习」 */
  title: string
  /** 用户可见原因，真实数据驱动，禁止「AI 认为…」（§261/§267-269） */
  reason: string
  /** 支撑证据（真实数据，§26-29） */
  evidence: LearningEvidence[]
  /** 关联知识点（若有） */
  knowledgeId?: number
  /** 关联知识点名称（若有） */
  knowledgeName?: string
  /** 严重度标签（§160-162） */
  severity: Severity
  /** 排序权重，越大越优先（§43/§158） */
  priority: number
}

/**
 * Learning Intelligence 上下文（§36-38）。
 * 最小字段集：只含真实必要数据，不构造巨型 GlobalUserLearningContext（§37）。
 * 不把 wallet/coin/product 纳入（经济不是学习能力，§38-42）。
 * 可选字段（recentEvents / repeatedMistakeKnowledgeIds 等）仅当真实数据可得时才提供；
 * 不提供则不推导对应 Diagnosis，避免伪造（§68/§148/§239）。
 */
export interface LearningIntelligenceContext {
  /** 待复习知识点（GET /learning/mastery/review-required） */
  reviewRequired: KnowledgeMasteryVO[]
  /** 薄弱知识点（GET /learning/mastery/weak） */
  weakPoints: KnowledgeMasteryVO[]
  /** 今日任务（GET /learning/tasks） */
  tasks: LearningTaskVO[]
  /** 当前进行中的学习路径（已报名且 0<progress<100） */
  currentPath?: LearningPathVO | null
  /** 可选：最近学习事件（用于 INACTIVE 推导；不传则不推导，§144-148） */
  recentEvents?: LearningEventVO[]
  /** 可选：近期重复出错的 knowledgeId（需真实 mapping；缺则 REPEATED_MISTAKE 不触发，§67-69） */
  repeatedMistakeKnowledgeIds?: number[]
  /** 可选：近期正确率偏低的 knowledgeId（需真实聚合；缺则 LOW_ACCURACY 不触发，§70-72） */
  lowAccuracyKnowledgeIds?: number[]
  /** 可选：停滞路径的 id 集合（需后端进度变化时间；缺则 PATH_STALLED 不触发，§23） */
  stalledPathIds?: number[]
}
