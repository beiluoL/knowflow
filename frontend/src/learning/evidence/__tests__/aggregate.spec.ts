/**
 * Architecture-4.1：Evidence Aggregation 测试（§18）
 *
 * 重点验证 §18 的三条硬规则：
 *  1. 禁止单次满分：单条 100 分证据聚合后必须被样本上限压到 50；
 *  2. 时间衰减：同等分数下，越近的证据权重越大；
 *  3. 无证据维度 score = null（不是 0，避免「没数据=很差」的误读）。
 */
import { describe, it, expect } from 'vitest'
import type { BehaviorEvidence, EvidenceDimension } from '../types'
import {
  aggregateEvidence,
  deriveConsistency,
  dimensionOf,
  sampleCap,
  sampleConfidence,
  timeWeight,
  toScoreMap,
} from '../aggregate'

const NOW = new Date('2026-09-06T12:00:00.000Z').getTime()
const at = (daysAgo: number) => new Date(NOW - daysAgo * 86_400_000).toISOString()

function evidence(over: Partial<BehaviorEvidence> & { dimension: EvidenceDimension; score: number }): BehaviorEvidence {
  return {
    evidenceId: 'evt:1:' + over.dimension,
    eventId: 1,
    userId: 1,
    sourceType: 'SCENARIO_COMPLETED',
    sourceId: 1,
    confidence: 0.9,
    timestamp: at(0),
    knowledgeId: null,
    label: 'test',
    ...over,
  }
}

describe('样本上限（§18 规则 1：禁止单次 100 → 掌握 100）', () => {
  it('n=1 → 50，n=2 → 66.7，n=3 → 83.3，n≥4 → 100（取整发生在最终得分上）', () => {
    expect(sampleCap(1)).toBe(50)
    expect(sampleCap(2)).toBeCloseTo(66.67, 1)
    expect(sampleCap(3)).toBeCloseTo(83.33, 1)
    expect(sampleCap(4)).toBe(100)
    expect(sampleCap(10)).toBe(100)
  })

  it('单条 100 分证据聚合后最高只到 50（绝不直达满分）', () => {
    const agg = aggregateEvidence([evidence({ dimension: 'APPLICATION', score: 100 })], NOW)
    expect(dimensionOf(agg, 'APPLICATION')!.score).toBe(50)
  })

  it('四条 100 分 → 100；四条 0 分 → 0（多次证据才能把分推满/推到底）', () => {
    const high = aggregateEvidence(
      [1, 2, 3, 4].map((i) => evidence({ evidenceId: `e${i}`, eventId: i, dimension: 'RECALL', score: 100 })),
      NOW,
    )
    expect(dimensionOf(high, 'RECALL')!.score).toBe(100)

    const low = aggregateEvidence(
      [1, 2, 3, 4].map((i) => evidence({ evidenceId: `e${i}`, eventId: i, dimension: 'RECALL', score: 0 })),
      NOW,
    )
    expect(dimensionOf(low, 'RECALL')!.score).toBe(0)
  })
})

describe('时间衰减（§18 规则 2）', () => {
  it('半衰期 7 天：0 天=1，7 天≈0.5，14 天≈0.25', () => {
    expect(timeWeight(at(0), NOW)).toBeCloseTo(1, 5)
    expect(timeWeight(at(7), NOW)).toBeCloseTo(0.5, 5)
    expect(timeWeight(at(14), NOW)).toBeCloseTo(0.25, 5)
  })

  it('同等样本下，近期低分 + 远期高分 的聚合分低于 近期高分 + 远期低分', () => {
    const recentBad = aggregateEvidence(
      [
        evidence({ eventId: 1, dimension: 'RECALL', score: 20, timestamp: at(0) }),
        evidence({ eventId: 2, dimension: 'RECALL', score: 100, timestamp: at(20) }),
      ],
      NOW,
    )
    const recentGood = aggregateEvidence(
      [
        evidence({ eventId: 3, dimension: 'RECALL', score: 100, timestamp: at(0) }),
        evidence({ eventId: 4, dimension: 'RECALL', score: 20, timestamp: at(20) }),
      ],
      NOW,
    )
    expect(dimensionOf(recentBad, 'RECALL')!.score).toBeLessThan(
      dimensionOf(recentGood, 'RECALL')!.score!,
    )
  })

  it('窗外证据不参与聚合（默认 30 天）', () => {
    const agg = aggregateEvidence(
      [evidence({ dimension: 'RECALL', score: 100, timestamp: at(60) })],
      NOW,
    )
    expect(agg.totalEvidence).toBe(0)
    expect(dimensionOf(agg, 'RECALL')!.score).toBeNull()
  })
})

describe('置信度与空维度（§18 规则 3）', () => {
  it('无证据的维度 score 为 null，sampleCount 为 0（不是 0 分）', () => {
    const agg = aggregateEvidence([evidence({ dimension: 'APPLICATION', score: 80 })], NOW)
    expect(dimensionOf(agg, 'RECALL')!.score).toBeNull()
    expect(dimensionOf(agg, 'RECALL')!.sampleCount).toBe(0)
    const map = toScoreMap(agg)
    expect(map.RECALL).toBeNull()
    expect(map.APPLICATION).toBe(50) // 单条样本封顶
  })

  it('样本越多置信度越高，且封顶 1', () => {
    expect(sampleConfidence(1)).toBeCloseTo(1 / 3, 5)
    expect(sampleConfidence(3)).toBe(1)
    expect(sampleConfidence(10)).toBe(1)
  })

  it('置信度 = 样本因子 × 证据自身置信度（自检类证据更低）', () => {
    const agg = aggregateEvidence(
      [
        evidence({ eventId: 1, dimension: 'EXPLANATION', score: 50, confidence: 0.6 }),
        evidence({ eventId: 2, dimension: 'EXPLANATION', score: 50, confidence: 0.6 }),
        evidence({ eventId: 3, dimension: 'EXPLANATION', score: 50, confidence: 0.6 }),
      ],
      NOW,
    )
    const dim = dimensionOf(agg, 'EXPLANATION')!
    expect(dim.confidence).toBeCloseTo(0.6, 2)
  })
})

describe('CONSISTENCY 推导', () => {
  it('无证据 → null', () => {
    expect(deriveConsistency([], 0)).toBeNull()
    expect(dimensionOf(aggregateEvidence([], NOW), 'CONSISTENCY')!.score).toBeNull()
  })

  it('只在 1 天练习 → 一致性明显低于连续 3 天练习', () => {
    const oneDay = aggregateEvidence(
      [1, 2, 3].map((i) => evidence({ eventId: i, dimension: 'RECALL', score: 90, timestamp: at(0) })),
      NOW,
    )
    const threeDays = aggregateEvidence(
      [1, 2, 3].map((i) => evidence({ eventId: i, dimension: 'RECALL', score: 90, timestamp: at(i) })),
      NOW,
    )
    expect(oneDay.activeDays).toBe(1)
    expect(threeDays.activeDays).toBe(3)
    expect(dimensionOf(oneDay, 'CONSISTENCY')!.score!).toBeLessThan(
      dimensionOf(threeDays, 'CONSISTENCY')!.score!,
    )
  })

  it('分数忽高忽低 → 一致性低于分数稳定（同为 3 天）', () => {
    const stable = aggregateEvidence(
      [1, 2, 3].map((i) => evidence({ eventId: i, dimension: 'RECALL', score: 80, timestamp: at(i) })),
      NOW,
    )
    const unstable = aggregateEvidence(
      [
        evidence({ eventId: 1, dimension: 'RECALL', score: 10, timestamp: at(1) }),
        evidence({ eventId: 2, dimension: 'RECALL', score: 100, timestamp: at(2) }),
        evidence({ eventId: 3, dimension: 'RECALL', score: 20, timestamp: at(3) }),
      ],
      NOW,
    )
    expect(dimensionOf(unstable, 'CONSISTENCY')!.score!).toBeLessThan(
      dimensionOf(stable, 'CONSISTENCY')!.score!,
    )
  })
})

describe('聚合结果整体口径', () => {
  it('totalEvidence / activeDays / lastEvidenceAt 均来自真实证据', () => {
    const agg = aggregateEvidence(
      [
        evidence({ eventId: 1, dimension: 'RECALL', score: 70, timestamp: at(2) }),
        evidence({ eventId: 2, dimension: 'APPLICATION', score: 40, timestamp: at(0) }),
      ],
      NOW,
    )
    expect(agg.totalEvidence).toBe(2)
    expect(agg.activeDays).toBe(2)
    expect(agg.lastEvidenceAt).toBe(at(0))
  })

  it('六维齐全（缺失维度也返回，score 为 null）', () => {
    const agg = aggregateEvidence([], NOW)
    expect(agg.dimensions).toHaveLength(6)
    expect(agg.dimensions.every((d) => d.score === null)).toBe(true)
  })
})
