/**
 * Architecture-4.1：Evidence（行为证据）核心类型。
 *
 * ── 与既有 `intelligence/types.ts` 的区别（为什么要新开一套，而不是复用 LearningEvidence）──
 * 既有 `LearningEvidence / EvidenceType`（WEAK_MASTERY / REVIEW_DUE / QUIZ_ACCURACY …）回答的是
 * **「凭什么这么诊断」**——它是 Diagnosis 的支撑材料，没有分数、没有维度。
 * 本文件的 `BehaviorEvidence` 回答的是 **「这次行为说明用户某项能力有多强」**——
 * 它必须有 dimension（能力维度）+ score（0~100）+ confidence（置信度），是 Mastery / Strategy 的输入。
 * 两者语义不同、生命周期不同，强行合并会让「证据」一词同时承担两种含义（§6 反重复枚举的精神：
 * 不制造同义重复，而不是把两个不同概念塞进同一个枚举）。
 *
 * ── Event vs Evidence ──
 *   LearningEvent = 发生了什么（事实，后端落库，不可变）
 *   Evidence      = 这件事说明用户掌握情况如何（解释，由 Resolver 确定性推导，可重算）
 * Evidence 永远是 Event 的派生结果：删掉全部 Evidence，可用事件重算，不改变任何真相源。
 *
 * ── 诚实边界（§33/§34）──
 *   Evidence 只从事件 metadata 中「已存在的真实数字」换算；
 *   没有可靠评分来源的行为（如 SmartWriting）不产出 Evidence，绝不伪造。
 *   Evidence 不直接改写 Mastery（§19），本阶段只独立运行。
 */
import type { LearningEventVO } from '@/api/types'

/** 能力维度（§6；CONSISTENCY 由聚合层从「证据时间分布」推导，不由单个事件产出） */
export type EvidenceDimension =
  | 'RECALL' // 记忆 / 主动回忆
  | 'UNDERSTANDING' // 理解 / 底层机制
  | 'EXPLANATION' // 讲解 / 费曼表达
  | 'APPLICATION' // 应用 / 场景与编程
  | 'EXPRESSION' // 书面表达
  | 'CONSISTENCY' // 一致性 / 稳定复现

/** 证据来源事件类型（真实后端 LearningEventType 的子集，非全部事件都能产出 Evidence） */
export type EvidenceSourceType =
  | 'QUESTION_ANSWERED'
  | 'FLASHCARD_REVIEWED'
  | 'RECALL_COMPLETED'
  | 'SCENARIO_COMPLETED'
  | 'CODE_SUBMITTED'
  | 'FEYNMAN_EXPRESSED'
  | 'FIRST_PRINCIPLES_DERIVED'

/**
 * 一条行为证据（§5）。
 * evidenceId 采用「事件 id + 维度」复合键（evt:<id>:<dimension>），
 * 保证同一事件可从任意维度重算并稳定去重（幂等，不新增表）。
 */
export interface BehaviorEvidence {
  /** 复合稳定 ID：evt:<eventId>:<dimension> */
  evidenceId: string
  /** 来源事件主键 */
  eventId: number
  /** 所属用户（逻辑外键 sys_user.id） */
  userId: number
  /** 来源事件类型 */
  sourceType: EvidenceSourceType
  /** 来源资源 ID（题目 / 闪卡 / 场景 / 0 表示无实体） */
  sourceId: number
  /** 能力维度 */
  dimension: EvidenceDimension
  /** 该维度得分 0~100（真实换算，非模型猜测） */
  score: number
  /** 置信度 0~1：由样本规模 / 评分口径确定性决定（§5/§18） */
  confidence: number
  /** 事件发生时间（ISO 字符串，原样透传） */
  timestamp?: string | null
  /** 知识点 ID：仅当事件 metadata 真实携带时才有值，否则 null（不猜测、不映射） */
  knowledgeId: number | null
  /** 用户可读的主题标签（如费曼主题 / 场景标题），用于展示与排障 */
  label: string
}

/** 解析器统一入参：补齐 LearningEventVO 缺字段（如 knowledgeId）时用，缺省即 null */
export interface EvidenceResolveOptions {
  /** 事件 metadata 未携带真实 knowledgeId 时的兜底；不传则 knowledgeId 一律为 null（§33 禁止伪造） */
  defaultKnowledgeId?: number | null
}

/** 聚合后的单维度结果（§18） */
export interface DimensionScore {
  dimension: EvidenceDimension
  /** 加权后得分 0~100；无样本时为 null（不是 0，避免「没数据=很差」的误读） */
  score: number | null
  /** 该维度的证据条数 */
  sampleCount: number
  /** 综合置信度 0~1（样本数越多、时间越近越高） */
  confidence: number
}

/** 聚合结果（§18） */
export interface EvidenceAggregate {
  /** 六维得分（CONSISTENCY 由时间分布推导，不由单事件产出） */
  dimensions: DimensionScore[]
  /** 参与聚合的证据总条数 */
  totalEvidence: number
  /** 证据覆盖的不同天数（一致性推导用） */
  activeDays: number
  /** 最近一次证据时间（ISO，无证据时 null） */
  lastEvidenceAt: string | null
}

/** 按维度取值的便捷视图（score 为 null 表示该维度无证据） */
export type DimensionScoreMap = Record<EvidenceDimension, number | null>

/** 六维顺序（展示与测试稳定顺序） */
export const EVIDENCE_DIMENSIONS: EvidenceDimension[] = [
  'RECALL',
  'UNDERSTANDING',
  'EXPLANATION',
  'APPLICATION',
  'EXPRESSION',
  'CONSISTENCY',
]

/** 安全的 metadata 解析：非对象 / 非法 JSON 一律返回 null（容错，绝不抛错打断 UI） */
export function parseEventMetadata(ev: LearningEventVO): Record<string, unknown> | null {
  if (!ev?.metadata) return null
  try {
    const raw = JSON.parse(ev.metadata) as unknown
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null
    return raw as Record<string, unknown>
  } catch {
    return null
  }
}

/** 取 metadata 中的有限数字；缺失 / NaN / 非数字一律返回 null（禁止把 undefined 当 0） */
export function num(meta: Record<string, unknown> | null, key: string): number | null {
  if (!meta) return null
  const v = Number(meta[key])
  return Number.isFinite(v) ? v : null
}

/** 事件时间 → 毫秒时间戳；无法解析返回 null */
export function eventTime(ev: LearningEventVO): number | null {
  if (!ev?.createTime) return null
  const t = new Date(ev.createTime).getTime()
  return Number.isFinite(t) ? t : null
}

/** 0~100 封顶取整（防止脏数据越界） */
export function clampScore(raw: number): number {
  if (!Number.isFinite(raw)) return 0
  return Math.max(0, Math.min(100, Math.round(raw)))
}
