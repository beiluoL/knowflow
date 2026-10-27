/**
 * Architecture-4.1：Diagnosis + Root Cause 测试（§20/§21）
 *
 * 铁律验证：
 *  - 无 Evidence → 无诊断（空数组，绝不返回「状态良好」占位，§20）；
 *  - 有哪种维度的低分证据 → 才输出哪种能力诊断；
 *  - 根因是确定性规则（同样的分数分布 → 同样的文案），不含 AI 判断。
 */
import { describe, it, expect } from 'vitest'
import type { BehaviorEvidence, EvidenceAggregate } from '../types'
import { aggregateEvidence } from '../aggregate'
import {
  CRITICAL_SCORE_THRESHOLD,
  DIMENSION_LABEL,
  LOW_SCORE_THRESHOLD,
  diagnoseFromEvidence,
  resolveRootCause,
  strongestAndWeakest,
} from '../diagnosis'

const NOW = new Date('2026-09-06T12:00:00.000Z').getTime()
const at = (daysAgo: number) => new Date(NOW - daysAgo * 86_400_000).toISOString()

function evidence(over: Partial<BehaviorEvidence> & { dimension: BehaviorEvidence['dimension']; score: number }): BehaviorEvidence {
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

/** 造 n 条同维度同分证据（绕开样本上限，便于断言阈值行为） */
function many(dimension: BehaviorEvidence['dimension'], score: number, n = 4): BehaviorEvidence[] {
  return Array.from({ length: n }, (_, i) =>
    evidence({ evidenceId: `e${i}`, eventId: i + 1, dimension, score, timestamp: at(i) }),
  )
}

const aggOf = (list: BehaviorEvidence[]): EvidenceAggregate => aggregateEvidence(list, NOW)

describe('§20 无 Evidence → 无诊断', () => {
  it('空证据 → 空数组（不返回「状态良好」，不编造）', () => {
    expect(diagnoseFromEvidence(aggOf([]))).toEqual([])
    const agg = aggOf([])
    expect(agg.totalEvidence).toBe(0)
  })

  it('只有高分证据 → 无诊断', () => {
    const agg = aggOf([
      ...many('RECALL', 90),
      ...many('APPLICATION', 85),
    ])
    expect(diagnoseFromEvidence(agg)).toEqual([])
  })

  it('某维度无证据 → 该维度不输出诊断（score=null 不视为 0 分）', () => {
    const agg = aggOf(many('RECALL', 20))
    const types = diagnoseFromEvidence(agg).map((d) => d.type)
    expect(types).toContain('LOW_RECALL')
    expect(types).not.toContain('LOW_APPLICATION')
    expect(types).not.toContain('LOW_EXPLANATION')
  })
})

describe('§20 有低分 Evidence → 对应能力诊断', () => {
  it('Feynman 低分（EXPLANATION）→ LOW_EXPLANATION', () => {
    const agg = aggOf([...many('EXPLANATION', 56), ...many('RECALL', 85)])
    const list = diagnoseFromEvidence(agg)
    expect(list.map((d) => d.type)).toContain('LOW_EXPLANATION')
    const d = list.find((x) => x.type === 'LOW_EXPLANATION')!
    expect(d.title).toBe(`${DIMENSION_LABEL.EXPLANATION}能力偏弱`)
    expect(d.score).toBe(56)
  })

  it('Scenario 低分（APPLICATION）→ LOW_APPLICATION', () => {
    const agg = aggOf(many('APPLICATION', 30))
    const d = diagnoseFromEvidence(agg)[0]
    expect(d.type).toBe('LOW_APPLICATION')
    expect(d.severity).toBe('HIGH') // < 40
  })

  it('低于阈值才算弱：59 → 诊断，60 → 不诊断', () => {
    expect(diagnoseFromEvidence(aggOf(many('RECALL', 59))).map((d) => d.type)).toContain('LOW_RECALL')
    expect(diagnoseFromEvidence(aggOf(many('RECALL', 60)))).toEqual([])
    expect(LOW_SCORE_THRESHOLD).toBe(60)
    expect(CRITICAL_SCORE_THRESHOLD).toBe(40)
  })

  it('严重度：40 以下 HIGH，40~59 MEDIUM', () => {
    expect(diagnoseFromEvidence(aggOf(many('RECALL', 20)))[0].severity).toBe('HIGH')
    expect(diagnoseFromEvidence(aggOf(many('RECALL', 50)))[0].severity).toBe('MEDIUM')
  })

  it('多个维度同时偏弱 → 按 priority 降序（应用 > 讲解）', () => {
    const agg = aggOf([...many('EXPLANATION', 30), ...many('APPLICATION', 30)])
    const types = diagnoseFromEvidence(agg).map((d) => d.type)
    expect(types.indexOf('LOW_APPLICATION')).toBeLessThan(types.indexOf('LOW_EXPLANATION'))
  })
})

describe('§21 根因（确定性规则，不用 AI）', () => {
  it('LOW_RECALL + 一致性低 → 指向「练习间隔过长」', () => {
    const agg = aggOf([evidence({ dimension: 'RECALL', score: 30 })]) // 单条 → 一致性必然低
    expect(resolveRootCause('LOW_RECALL', agg)).toContain('练习间隔过长')
  })

  it('LOW_RECALL + 一致性够高 → 指向「缺少主动回忆练习」', () => {
    // 连续 4 天练习 → 一致性达标，此时根因应指向「回忆练习」本身而非节奏
    const agg = aggOf([...many('RECALL', 30), ...many('UNDERSTANDING', 80)])
    expect(resolveRootCause('LOW_RECALL', agg)).toContain('主动回忆')
  })

  it('LOW_APPLICATION + 理解/讲解稳定 → 「理论稳定但场景练习不足」', () => {
    const agg = aggOf([...many('APPLICATION', 30), ...many('UNDERSTANDING', 85)])
    expect(resolveRootCause('LOW_APPLICATION', agg)).toContain('场景练习不足')
  })

  it('LOW_APPLICATION + 理解也弱 → 「先补齐理解」', () => {
    const agg = aggOf([...many('APPLICATION', 30), ...many('UNDERSTANDING', 30)])
    expect(resolveRootCause('LOW_APPLICATION', agg)).toContain('先补齐理解')
  })

  it('LOW_EXPLANATION + 理解尚可 → 「表达与知识之间存在断层」', () => {
    const agg = aggOf([...many('EXPLANATION', 40), ...many('UNDERSTANDING', 80)])
    expect(resolveRootCause('LOW_EXPLANATION', agg)).toContain('断层')
  })

  it('LOW_EXPLANATION + 理解也弱 → 「理解还不够扎实」', () => {
    const agg = aggOf([...many('EXPLANATION', 40), ...many('UNDERSTANDING', 30)])
    expect(resolveRootCause('LOW_EXPLANATION', agg)).toContain('理解还不够扎实')
  })

  it('同样输入 → 同样根因（确定性，可复现）', () => {
    const a = aggOf([...many('APPLICATION', 30), ...many('UNDERSTANDING', 85)])
    const b = aggOf([...many('APPLICATION', 30), ...many('UNDERSTANDING', 85)])
    expect(resolveRootCause('LOW_APPLICATION', a)).toBe(resolveRootCause('LOW_APPLICATION', b))
  })
})

describe('优势 / 需加强（§28 状态卡）', () => {
  it('无 Evidence → 两者皆 null（不伪造优势）', () => {
    expect(strongestAndWeakest(aggOf([]))).toEqual({ strongest: null, weakest: null })
  })

  it('多维度有分 → 最高为优势、最低为需加强', () => {
    const agg = aggOf([...many('RECALL', 90), ...many('APPLICATION', 30)])
    const { strongest, weakest } = strongestAndWeakest(agg)
    expect(strongest!.dimension).toBe('RECALL')
    expect(weakest!.dimension).toBe('APPLICATION')
  })

  it('CONSISTENCY 不参与「优势/需加强」比较（它不是能力分）', () => {
    const agg = aggOf([...many('RECALL', 85), ...many('CONSISTENCY', 10)])
    const { strongest, weakest } = strongestAndWeakest(agg)
    expect(strongest!.dimension).toBe('RECALL')
    expect(weakest).toBeNull() // 只剩 RECALL 一个能力维度
  })
})
