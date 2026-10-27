/**
 * Architecture-4.2-§23~§26/§37~§39：Shadow Mastery（影子掌握度）
 *
 * ── 为什么叫 Shadow（§23/§37）──
 * 本阶段**不覆盖**生产 Mastery（knowledge_mastery.masteryScore）。
 * 只做「如果按行为证据算，掌握画像长什么样」的并行计算，用于对比与观察。
 * 原因（§19/§39）：错误 Evidence → 污染 Mastery → 错误 Diagnosis → 错误 NextAction，
 * 在 Evidence 口径稳定 + 样本充足 + 置信度足够 + 回归全绿之前，不允许正式接入。
 *
 * ── 明确禁止（§25）──
 *   不得把六维平均成一个「万能分数」：
 *     (Recall + Understanding + Explanation + Application + Expression + Consistency) / 6
 *   这种平均会把「记忆 90 / 应用 20」和「六项都 55」算成同一结果，
 *   系统因此失去「该练什么」的判断力。本文件**只产出 Profile，不产出总分**。
 *
 * ── 复用（§24/§26）──
 *   维度得分直接复用 4.1 的 aggregateEvidence（同一口径、同一时间窗、同样本上限），
 *   最强/最弱由本文件的 profileExtremes 计算（排除 CONSISTENCY 维度，口径与 diagnosis 对齐）。
 */
import type { BehaviorEvidence } from './types'
import { aggregateEvidence, dimensionOf } from './aggregate'
import type { EvidenceAggregate, EvidenceDimension } from './types'

/** 行为掌握画像（§24：六维分开保留 + 置信度 + 样本数） */
export interface BehaviorMasteryProfile {
  /** 记忆 / 主动回忆 0~100，无证据为 null */
  recall: number | null
  /** 理解 / 底层机制 */
  understanding: number | null
  /** 讲解 / 费曼表达 */
  explanation: number | null
  /** 应用 / 场景与编程 */
  application: number | null
  /** 书面表达 */
  expression: number | null
  /** 一致性 / 稳定复现（由时间分布推导，不由单事件产出） */
  consistency: number | null
  /** 综合置信度 0~1（样本越少越低） */
  confidence: number
  /** 参与计算的证据条数 */
  sampleCount: number
}

/** 空画像（无任何证据时的返回值，六维全 null，不是 0） */
export const EMPTY_PROFILE: BehaviorMasteryProfile = {
  recall: null,
  understanding: null,
  explanation: null,
  application: null,
  expression: null,
  consistency: null,
  confidence: 0,
  sampleCount: 0,
}

/** 维度 → 画像字段名 */
export const PROFILE_KEY: Record<EvidenceDimension, keyof Omit<BehaviorMasteryProfile, 'confidence' | 'sampleCount'>> = {
  RECALL: 'recall',
  UNDERSTANDING: 'understanding',
  EXPLANATION: 'explanation',
  APPLICATION: 'application',
  EXPRESSION: 'expression',
  CONSISTENCY: 'consistency',
}

/**
 * §24 由真实 Evidence 计算行为掌握画像（纯函数）。
 *
 * 置信度口径：各维度置信度的均值（0 样本维度不拉低整体，但也不假装它存在）。
 * 与 aggregateEvidence 的 sampleCap 一致 —— 单次 100 分不会让任何维度直接等于 100。
 */
export function calculateBehaviorMastery(
  evidences: BehaviorEvidence[],
  now: number = Date.now(),
  windowDays?: number,
): BehaviorMasteryProfile {
  const agg: EvidenceAggregate =
    windowDays == null
      ? aggregateEvidence(evidences, now)
      : aggregateEvidence(evidences, now, windowDays)

  if (agg.totalEvidence === 0) return { ...EMPTY_PROFILE }

  const profile: BehaviorMasteryProfile = { ...EMPTY_PROFILE, sampleCount: agg.totalEvidence }
  let confSum = 0
  let confCount = 0
  for (const dim of agg.dimensions) {
    profile[PROFILE_KEY[dim.dimension]] = dim.score
    if (dim.sampleCount > 0) {
      confSum += dim.confidence
      confCount += 1
    }
  }
  profile.confidence = confCount === 0 ? 0 : Number((confSum / confCount).toFixed(2))
  return profile
}

/** 画像中「有真实分数」的维度（按分数降序），供 UI 展示掌握画像条 */
export function scoredDimensions(
  profile: BehaviorMasteryProfile,
): { dimension: EvidenceDimension; score: number }[] {
  const out: { dimension: EvidenceDimension; score: number }[] = []
  for (const [dim, key] of Object.entries(PROFILE_KEY) as [EvidenceDimension, keyof BehaviorMasteryProfile][]) {
    const v = profile[key]
    if (typeof v === 'number' && Number.isFinite(v)) out.push({ dimension: dim, score: v })
  }
  return out.sort((a, b) => b.score - a.score)
}

/**
 * §26 最强 / 最弱维度（由行为画像推导，排除 CONSISTENCY，与 diagnosis.strongestAndWeakest 口径对齐）。
 * 需要聚合结果（含样本信息）时使用本函数；只有画像时用 scoredDimensions。
 */
export function profileExtremes(
  profile: BehaviorMasteryProfile,
): { strongest: { dimension: EvidenceDimension; score: number } | null; weakest: { dimension: EvidenceDimension; score: number } | null } {
  const scored = scoredDimensions(profile).filter((d) => d.dimension !== 'CONSISTENCY')
  if (scored.length === 0) return { strongest: null, weakest: null }
  const strongest = scored[0]
  const weakest = scored[scored.length - 1]
  return {
    strongest,
    weakest: strongest.dimension === weakest.dimension ? null : weakest,
  }
}

/* ============================ §38 Mastery Review Report ============================ */

/** 内部调试报告（§38：普通用户 UI 不展示，只用于观察差异） */
export interface MasteryReviewReport {
  /** 现有生产掌握度 0~100（knowledge_mastery；无则 null） */
  existingMastery: number | null
  /** 行为证据画像（Shadow，不含总分） */
  behaviorProfile: BehaviorMasteryProfile
  /** 最强维度（真实分数） */
  strongest: { dimension: EvidenceDimension; score: number } | null
  /** 最弱维度（真实分数） */
  weakest: { dimension: EvidenceDimension; score: number } | null
  /** 置信度 0~1 */
  confidence: number
  /** 样本数 */
  sampleCount: number
  /** 是否具备「可考虑正式接入」的初步条件（§39：样本与置信度双达标） */
  readyForReview: boolean
  /** 人可读的差异说明（不写「AI 认为」，只陈述真实数字关系） */
  note: string
}

/** §39 正式接入的前置门槛：至少 4 条证据 且 置信度 ≥ 0.6 */
export const MIN_SAMPLES_FOR_REVIEW = 4
export const MIN_CONFIDENCE_FOR_REVIEW = 0.6

/**
 * §38 生成 Mastery 对比报告（纯函数，只观察不写入）。
 * @param existingMastery 现有生产掌握度（来自 /learning/mastery，可空）
 * @param evidences 真实 Evidence
 */
export function buildMasteryReviewReport(
  existingMastery: number | null,
  evidences: BehaviorEvidence[],
  now: number = Date.now(),
): MasteryReviewReport {
  const behaviorProfile = calculateBehaviorMastery(evidences, now)
  const extremes = profileExtremes(behaviorProfile)
  const readyForReview =
    behaviorProfile.sampleCount >= MIN_SAMPLES_FOR_REVIEW &&
    behaviorProfile.confidence >= MIN_CONFIDENCE_FOR_REVIEW

  let note: string
  if (behaviorProfile.sampleCount === 0) {
    note = '暂无行为证据，不做任何掌握度推断。'
  } else if (existingMastery == null) {
    note = `已积累 ${behaviorProfile.sampleCount} 条行为证据（置信度 ${behaviorProfile.confidence}），当前无生产掌握度可比对。`
  } else if (extremes.weakest) {
    note = `现有掌握度 ${existingMastery}；行为画像中最弱的一项低于最强项，说明能力结构不均衡，暂不适合用单一分数覆盖。`
  } else {
    note = `现有掌握度 ${existingMastery}；行为证据样本 ${behaviorProfile.sampleCount} 条（置信度 ${behaviorProfile.confidence}），维度间差异不大。`
  }

  return {
    existingMastery,
    behaviorProfile,
    strongest: extremes.strongest,
    weakest: extremes.weakest,
    confidence: behaviorProfile.confidence,
    sampleCount: behaviorProfile.sampleCount,
    readyForReview,
    note,
  }
}

/** 便捷：直接从聚合结果取某维度分（与 aggregate.dimensionOf 同义，供调用方少引一个模块） */
export function profileScore(agg: EvidenceAggregate, dimension: EvidenceDimension): number | null {
  return dimensionOf(agg, dimension)?.score ?? null
}
