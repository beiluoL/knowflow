/**
 * Architecture-4.2 §23~§39：Shadow Mastery（影子掌握度）测试。
 *
 * 重点验证：
 *  - §23 计算出的画像只是“如果按行为证据算，掌握长什么样”，不覆盖生产 Mastery；
 *  - §25 六维分开保留，绝不平均成一个总分（不淹没“该练什么”的判断力）；
 *  - §24/§26 置信度由样本规模决定；样本不足时 confidence 低、且 readyForReview=false；
 *  - scoredDimensions 降序、profileExtremes 弱≠强。
 */
import { describe, it, expect } from 'vitest'
import type { BehaviorEvidence, EvidenceDimension } from '../types'
import {
  calculateBehaviorMastery,
  scoredDimensions,
  profileExtremes,
  buildMasteryReviewReport,
  EMPTY_PROFILE,
  MIN_SAMPLES_FOR_REVIEW,
  MIN_CONFIDENCE_FOR_REVIEW,
} from '../shadow-mastery'

const NOW = new Date('2026-09-06T12:00:00.000Z').getTime()
const at = (daysAgo: number) => new Date(NOW - daysAgo * 86_400_000).toISOString()

function evidence(dimension: EvidenceDimension, score: number, i: number): BehaviorEvidence {
  return {
    evidenceId: `evt:${i}:${dimension}`,
    eventId: i,
    userId: 1,
    sourceType: 'SCENARIO_COMPLETED',
    sourceId: i,
    dimension,
    score,
    confidence: 0.9,
    timestamp: at(0),
    knowledgeId: null,
    label: 't',
  } as BehaviorEvidence
}

describe('§23-§26 Shadow Mastery 画像', () => {
  it('无证据 → 全 null、置信度 0、样本 0（不是 0 分）', () => {
    const p = calculateBehaviorMastery([], NOW)
    expect(p).toEqual(EMPTY_PROFILE)
    expect(p.recall).toBeNull()
    expect(p.confidence).toBe(0)
    expect(p.sampleCount).toBe(0)
  })

  it('只覆盖部分维度 → 其余仍为 null（不补 0）', () => {
    const evs = [evidence('RECALL', 90, 1), evidence('RECALL', 80, 2)]
    const p = calculateBehaviorMastery(evs, NOW)
    expect(p.recall).not.toBeNull()
    expect(p.understanding).toBeNull()
    expect(p.application).toBeNull()
  })

  it('§25 绝不产出总分 / 平均：六维独立保留', () => {
    const evs = [
      evidence('RECALL', 90, 1),
      evidence('RECALL', 80, 2),
      evidence('RECALL', 70, 3),
      evidence('APPLICATION', 20, 4),
      evidence('APPLICATION', 30, 5),
    ]
    const p = calculateBehaviorMastery(evs, NOW)
    // 记忆高、应用低，二者都不会被平均淹没
    expect(p.recall!).toBeGreaterThan(50)
    expect(p.application!).toBeLessThan(50)
  })

  it('scoredDimensions 按分数降序，且包含参与维度', () => {
    const evs = [
      evidence('RECALL', 90, 1),
      evidence('RECALL', 80, 2),
      evidence('RECALL', 70, 3),
      evidence('APPLICATION', 20, 4),
    ]
    const scored = scoredDimensions(calculateBehaviorMastery(evs, NOW))
    expect(scored[0].dimension).toBe('RECALL')
    expect(scored.map((d) => d.dimension)).toContain('APPLICATION')
    // 整体单调不增（CONSISTENCY 由时间分布推导，可能介于二者之间）
    for (let i = 1; i < scored.length; i++) {
      expect(scored[i - 1].score).toBeGreaterThanOrEqual(scored[i].score)
    }
  })

  it('profileExtremes：弱 ≠ 强', () => {
    const evs = [
      evidence('RECALL', 90, 1),
      evidence('RECALL', 80, 2),
      evidence('RECALL', 70, 3),
      evidence('APPLICATION', 20, 4),
    ]
    const ext = profileExtremes(calculateBehaviorMastery(evs, NOW))
    expect(ext.strongest?.dimension).toBe('RECALL')
    expect(ext.weakest?.dimension).toBe('APPLICATION')
  })

  it('§39 样本不足 → readyForReview=false', () => {
    const evs = [evidence('RECALL', 80, 1)]
    const r = buildMasteryReviewReport(70, evs, NOW)
    expect(r.sampleCount).toBe(1)
    expect(r.readyForReview).toBe(false)
  })

  it('§39 样本与置信度双达标 → readyForReview=true', () => {
    const evs = [
      evidence('RECALL', 80, 1),
      evidence('RECALL', 75, 2),
      evidence('RECALL', 85, 3),
      evidence('RECALL', 70, 4),
    ]
    const r = buildMasteryReviewReport(70, evs, NOW)
    expect(r.sampleCount).toBe(MIN_SAMPLES_FOR_REVIEW)
    expect(r.confidence).toBeGreaterThanOrEqual(MIN_CONFIDENCE_FOR_REVIEW)
    expect(r.readyForReview).toBe(true)
  })
})
