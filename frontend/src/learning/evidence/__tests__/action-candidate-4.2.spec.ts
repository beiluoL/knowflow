/**
 * Architecture-4.2 §26/§27/§15：候选动作绑定知识点上下文测试。
 *
 * 重点验证：
 *  - §26 候选动作带上 knowledgeId / knowledgeName，并产出带知识点的深链路由；
 *  - §27 诊断被绑定到具体知识点（标题「在「X」上…」、原因「X：…」），无名称时不伪造；
 *  - §15 knowledgeId 透传到 action.route 的 ?knowledgeId=N；
 *  - 无知识点时标题回落「这个知识点」，路由不带 query（不编造）。
 */
import { describe, it, expect } from 'vitest'
import type { AppDefinition } from '@/apps/types'
import type { BehaviorEvidence, EvidenceDimension, EvidenceAggregate } from '../types'
import type { BehaviorDiagnosis } from '../diagnosis'
import { aggregateEvidence } from '../aggregate'
import { buildStrategyCandidate, buildTopCandidate } from '../action-candidate'

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
function many(dimension: EvidenceDimension, score: number, n = 4): BehaviorEvidence[] {
  return Array.from({ length: n }, (_, i) =>
    evidence({ evidenceId: `e${i}`, eventId: i + 1, dimension, score, timestamp: at(i) }),
  )
}
function app(key: string): AppDefinition {
  return { key, name: key, route: `/app/${key}` } as AppDefinition
}
const diag = (type: BehaviorDiagnosis['type'], score = 30): BehaviorDiagnosis => ({
  type,
  title: 't',
  reason: 'r',
  dimension: 'RECALL',
  score,
  sampleCount: 4,
  confidence: 0.9,
  severity: 'HIGH',
  priority: 80,
})

describe('§26/§27 候选动作绑定知识点上下文', () => {
  it('knowledgeId + knowledgeName → 路由带深链、动作带知识点、诊断聚焦知识点', () => {
    const c = buildStrategyCandidate(diag('LOW_EXPLANATION'), [], {
      knowledgeId: 42,
      knowledgeName: 'JVM GC',
    })
    expect(c.action.knowledgeId).toBe(42)
    expect(c.action.knowledgeName).toBe('JVM GC')
    expect(c.action.route).toContain('knowledgeId=42')
    expect(c.action.title).toContain('JVM GC')
    expect(c.action.reason).toContain('JVM GC')
    // §27 诊断被 contextualize：原因加「JVM GC：」前缀（标题则加「在「JVM GC」上」前缀，见 contextualize-diagnosis.spec）
    expect(c.action.reason).toContain('JVM GC：')
  })

  it('仅 learningNodeTitle 无 knowledge → 不编造知识点名，标题用「这个知识点」', () => {
    const c = buildStrategyCandidate(diag('LOW_APPLICATION'), [], {
      learningNodeTitle: 'GC 算法',
    })
    expect(c.action.knowledgeId).toBeUndefined()
    expect(c.action.knowledgeName).toBeUndefined()
    expect(c.action.route).not.toContain('knowledgeId')
    expect(c.action.title).toContain('这个知识点')
    expect(c.action.reason).toContain('GC 算法')
  })

  it('无知识点上下文 → 诊断原样返回（不伪造）', () => {
    const c = buildStrategyCandidate(diag('LOW_APPLICATION'), [])
    expect(c.action.title).not.toContain('在「')
    expect(c.diagnosis.title).toBe('t')
  })

  it('buildTopCandidate：传 ctx 知识点 → 产出候选并绑定知识点深链', () => {
    const agg = aggregateEvidence([...many('EXPLANATION', 30), ...many('APPLICATION', 30)], NOW)
    const diagnoses: BehaviorDiagnosis[] = [
      { type: 'LOW_APPLICATION', title: '应用能力偏弱', reason: 'r', dimension: 'APPLICATION', score: 30, sampleCount: 4, confidence: 0.9, severity: 'HIGH', priority: 85 },
      { type: 'LOW_EXPLANATION', title: '讲解能力偏弱', reason: 'r', dimension: 'EXPLANATION', score: 30, sampleCount: 4, confidence: 0.9, severity: 'HIGH', priority: 75 },
    ]
    const c = buildTopCandidate(agg, diagnoses, [app('coding')], {
      goal: { id: 7, title: '目标' } as never,
      knowledgeId: 42,
      knowledgeName: 'JVM GC',
    })
    expect(c).not.toBeNull()
    expect(c!.action.route).toContain('knowledgeId=42')
    expect(c!.action.knowledgeId).toBe(42)
  })

  it('buildTopCandidate：无诊断 → null（调用方维持既有行为，§36）', () => {
    const empty: EvidenceAggregate = aggregateEvidence([], NOW)
    expect(buildTopCandidate(empty, [], [])).toBeNull()
  })
})
