/**
 * Architecture-4.1：Evidence Aggregation（§18）
 *
 * 输入：一段时间窗内的真实 Evidence（来自 resolveEvidenceBatch）。
 * 输出：六维得分（Recall / Understanding / Explanation / Application / Expression / Consistency）。
 *
 * 三条硬规则（§18）：
 *  1. **禁止单次满分**：单条证据再高，聚合分也被「样本上限」压住（n=1→50，n=2→67，n=3→83，n≥4→100）。
 *     这条规则明确防止「一次 100 分 → 掌握度直接 100」的污染链。
 *  2. **时间衰减**：半衰期 7 天，越近的证据权重越高（0.5^(age/7)）。
 *  3. **置信度分离**：分数与置信度分开输出——样本少时分数不低但置信度低，
 *     Diagnosis 层据此「少样本不下重诊断」，而不是假装数据很充分。
 *
 * CONSISTENCY 不来自任何单个事件（没有事件能直接证明一致性），
 * 它由「证据覆盖天数 × 分数稳定度」推导，文档与 UI 中均如实标注其含义。
 */
import type {
  BehaviorEvidence,
  DimensionScore,
  DimensionScoreMap,
  EvidenceAggregate,
  EvidenceDimension,
} from './types'
import { EVIDENCE_DIMENSIONS, eventTime } from './types'

/** 默认聚合窗口：30 天 */
export const DEFAULT_WINDOW_DAYS = 30
/** 时间衰减半衰期（天） */
const HALF_LIFE_DAYS = 7
/** 达到「满置信」所需的样本数 */
const FULL_CONF_SAMPLES = 3

/** 单条证据的时间衰减权重（0~1，越近越大；无时间戳按最旧处理=最低权重，不冒充新鲜） */
export function timeWeight(timestamp: string | null | undefined, now: number): number {
  const t = timestamp ? new Date(timestamp).getTime() : NaN
  if (!Number.isFinite(t)) return 0.5
  const ageDays = Math.max(0, (now - t) / 86_400_000)
  return Math.pow(0.5, ageDays / HALF_LIFE_DAYS)
}

/**
 * 样本上限（§18 规则 1）：样本越少，聚合分被压得越低。
 * n=1 → 50，n=2 → 67，n=3 → 83，n≥4 → 100。
 */
export function sampleCap(sampleCount: number): number {
  if (sampleCount <= 0) return 0
  return 50 + 50 * Math.min(1, (sampleCount - 1) / 3)
}

/** 样本置信因子（0~1）：3 条及以上视为充分 */
export function sampleConfidence(sampleCount: number): number {
  if (sampleCount <= 0) return 0
  return Math.min(1, sampleCount / FULL_CONF_SAMPLES)
}

/** 标准差（总体）；样本 < 2 时为 0 */
function stdev(values: number[]): number {
  if (values.length < 2) return 0
  const mean = values.reduce((a, b) => a + b, 0) / values.length
  const variance = values.reduce((a, b) => a + (b - mean) ** 2, 0) / values.length
  return Math.sqrt(variance)
}

/** 一天内可能有多条证据 → 用「天数」而非「条数」衡量节奏连续性 */
function countActiveDays(items: BehaviorEvidence[]): number {
  const days = new Set<string>()
  for (const e of items) {
    const t = eventTime({ createTime: e.timestamp } as Parameters<typeof eventTime>[0])
    if (t == null) continue
    days.add(new Date(t).toDateString())
  }
  return days.size
}

/**
 * CONSISTENCY 推导：证据覆盖天数（节奏连续性）× 分数稳定度。
 * 仅表示「近期是否连续、稳定地在练」，不是能力高低分。
 */
export function deriveConsistency(items: BehaviorEvidence[], activeDays: number): number | null {
  if (items.length === 0) return null
  const dayPart = Math.min(1, activeDays / FULL_CONF_SAMPLES)
  const stability = 1 - Math.min(1, stdev(items.map((e) => e.score)) / 40)
  return Math.max(0, Math.min(100, Math.round(100 * dayPart * (0.5 + 0.5 * stability))))
}

/**
 * §18 聚合入口。
 * @param evidences 证据列表（建议已按时间升序）
 * @param now 当前时间戳（注入以便单测确定性）
 * @param windowDays 时间窗（天），窗外证据不参与
 */
export function aggregateEvidence(
  evidences: BehaviorEvidence[],
  now: number = Date.now(),
  windowDays: number = DEFAULT_WINDOW_DAYS,
): EvidenceAggregate {
  const all = evidences ?? []
  const inWindow = all.filter((e) => {
    const t = eventTime({ createTime: e.timestamp } as Parameters<typeof eventTime>[0])
    if (t == null) return true // 无时间戳不丢弃（保守保留），但权重按 0.5 计
    return now - t <= windowDays * 86_400_000
  })

  const activeDays = countActiveDays(inWindow)
  const lastEvidenceAt = inWindow.reduce<string | null>((acc, e) => {
    // 只在有真实时间戳的证据之间比较；无时间戳的证据不参与「最近一次」判定（不伪造时间）
    const t = eventTime({ createTime: e.timestamp } as Parameters<typeof eventTime>[0])
    if (t == null) return acc
    const accT = acc ? new Date(acc).getTime() : -Infinity
    return t > accT ? (e.timestamp ?? acc) : acc
  }, null)

  const dimensions: DimensionScore[] = EVIDENCE_DIMENSIONS.map((dimension) => {
    if (dimension === 'CONSISTENCY') {
      const score = deriveConsistency(inWindow, activeDays)
      return {
        dimension,
        score,
        sampleCount: inWindow.length,
        confidence: score == null ? 0 : Number(sampleConfidence(inWindow.length).toFixed(2)),
      }
    }
    const items = inWindow.filter((e) => e.dimension === dimension)
    if (items.length === 0) {
      return { dimension, score: null, sampleCount: 0, confidence: 0 }
    }
    let weighted = 0
    let totalWeight = 0
    for (const e of items) {
      const w = timeWeight(e.timestamp, now) * Math.max(0.1, e.confidence)
      weighted += e.score * w
      totalWeight += w
    }
    const raw = totalWeight > 0 ? weighted / totalWeight : items[items.length - 1].score
    const capped = Math.min(raw, sampleCap(items.length))
    const avgConf = items.reduce((a, b) => a + b.confidence, 0) / items.length
    return {
      dimension,
      score: Math.max(0, Math.min(100, Math.round(capped))),
      sampleCount: items.length,
      confidence: Number((sampleConfidence(items.length) * avgConf).toFixed(2)),
    }
  })

  return {
    dimensions,
    totalEvidence: inWindow.length,
    activeDays,
    lastEvidenceAt,
  }
}

/** 聚合结果 → 按维度取分的便捷 Map（无证据的维度为 null，不是 0） */
export function toScoreMap(agg: EvidenceAggregate): DimensionScoreMap {
  const map = {} as DimensionScoreMap
  for (const d of EVIDENCE_DIMENSIONS) map[d] = null
  for (const d of agg.dimensions) map[d.dimension] = d.score
  return map
}

/** 取某一维度的聚合结果（找不到返回 null） */
export function dimensionOf(agg: EvidenceAggregate, dimension: EvidenceDimension): DimensionScore | null {
  return agg.dimensions.find((d) => d.dimension === dimension) ?? null
}
