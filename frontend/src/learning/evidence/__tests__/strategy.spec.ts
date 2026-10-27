/**
 * Architecture-4.1：Strategy Resolver + App Availability + Goal Context 测试（§22~§26）
 *
 * 重点验证：
 *  - §22 Diagnosis → Strategy 的映射固定；
 *  - §23 一个 Strategy 可以映射到多个 App，不是一对一硬绑；
 *  - §24 必须过滤 UserApp 可用性，全部不可用时必须 fallback，
 *        绝不允许 NextAction 指向被停用的 App；
 *  - §26 候选动作绑定 Goal 上下文（goalId/节点/知识点均来自真实数据）。
 */
import { describe, it, expect } from 'vitest'
import type { AppDefinition } from '@/apps/types'
import type { BehaviorDiagnosis } from '../diagnosis'
import type { EvidenceStrategyType } from '../strategy'
import {
  FALLBACK_TARGET,
  STRATEGY_TARGETS,
  availableTargets,
  firstAvailableTarget,
  resolveEvidenceStrategy,
  strategyOfDiagnosisType,
  targetRoute,
} from '../strategy'
import { buildStrategyCandidate, buildTopCandidate } from '../action-candidate'
import { aggregateEvidence } from '../aggregate'
import { diagnoseFromEvidence } from '../diagnosis'
import type { BehaviorEvidence, EvidenceAggregate } from '../types'

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
function many(dimension: BehaviorEvidence['dimension'], score: number, n = 4): BehaviorEvidence[] {
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

describe('§22 Diagnosis → Strategy', () => {
  it('映射固定（记忆→主动回忆 / 理解→第一性原理 / 讲解→费曼 / 应用→场景 / 表达→写作）', () => {
    expect(resolveEvidenceStrategy(diag('LOW_RECALL'))).toBe('ACTIVE_RECALL')
    expect(resolveEvidenceStrategy(diag('LOW_UNDERSTANDING'))).toBe('FIRST_PRINCIPLES')
    expect(resolveEvidenceStrategy(diag('LOW_EXPLANATION'))).toBe('FEYNMAN')
    expect(resolveEvidenceStrategy(diag('LOW_APPLICATION'))).toBe('APPLICATION')
    expect(resolveEvidenceStrategy(diag('LOW_EXPRESSION'))).toBe('WRITING_REVIEW')
    expect(resolveEvidenceStrategy(diag('LOW_CONSISTENCY'))).toBe('SPACED_REVIEW')
  })

  it('按诊断类型直接解析（无需构造完整诊断对象）', () => {
    expect(strategyOfDiagnosisType('LOW_EXPLANATION')).toBe('FEYNMAN')
  })
})

describe('§23 一个 Strategy 多个 App（不一对一硬绑）', () => {
  it('ACTIVE_RECALL 同时支持闪卡页 + flashcard App + quiz App', () => {
    const keys = STRATEGY_TARGETS.ACTIVE_RECALL.map((t) => (t.kind === 'APP' ? t.appKey : t.path))
    expect(keys.length).toBeGreaterThanOrEqual(3)
    expect(keys).toContain('flashcard')
    expect(keys).toContain('quiz')
  })

  it('APPLICATION 同时支持场景页 + coding App', () => {
    const keys = STRATEGY_TARGETS.APPLICATION.map((t) => (t.kind === 'APP' ? t.appKey : t.path))
    expect(keys).toContain('/learning/scenario')
    expect(keys).toContain('coding')
  })
})

describe('§24 App 可用性与 fallback', () => {
  it('核心页面（ROUTE）不依赖 App 启用状态，永远可用', () => {
    const targets = availableTargets('FEYNMAN', [])
    expect(targets[0].kind).toBe('ROUTE')
    expect(targetRoute(targets[0])).toBe('/learning/feynman')
  })

  it('未安装的 App 目标被过滤掉', () => {
    const targets = availableTargets('ACTIVE_RECALL', [])
    expect(targets.some((t) => t.kind === 'APP')).toBe(false)
    expect(targets[0].kind).toBe('ROUTE')
  })

  it('已安装的 App 会出现在候选中', () => {
    const targets = availableTargets('ACTIVE_RECALL', [app('flashcard'), app('quiz')])
    expect(targets.filter((t) => t.kind === 'APP').map((t) => (t as { appKey: string }).appKey)).toEqual([
      'flashcard',
      'quiz',
    ])
  })

  it('全部 App 不可用时仍然有可用目标（核心页面），不会指向 disabled App', () => {
    for (const s of Object.keys(STRATEGY_TARGETS) as EvidenceStrategyType[]) {
      const targets = availableTargets(s, [])
      expect(targets.length).toBeGreaterThan(0)
      for (const t of targets) {
        // 没有任何 App 安装时，候选里绝不允许出现 App 型目标
        expect(t.kind).toBe('ROUTE')
      }
    }
  })

  it('未知 App key 的路由回落到学习中心（Registry 白名单校验）', () => {
    expect(targetRoute({ kind: 'APP', appKey: 'not-exist', label: 'x' })).toBe(FALLBACK_TARGET.path)
  })

  it('firstAvailableTarget 返回首个可用目标', () => {
    expect(firstAvailableTarget('APPLICATION', [app('coding')]).kind).toBe('ROUTE') // 核心页优先
    expect(targetRoute(firstAvailableTarget('APPLICATION', [app('coding')]))).toBe('/learning/scenario')
  })
})

describe('§25/§26 Strategy → Action Candidate（含 Goal 上下文）', () => {
  it('候选动作携带诊断、策略与目标；路由落在白名单内', () => {
    const c = buildStrategyCandidate(diag('LOW_APPLICATION'), [app('coding')])
    expect(c.strategy).toBe('APPLICATION')
    expect(c.diagnosis.type).toBe('LOW_APPLICATION')
    expect(c.action.route).toBe('/learning/scenario')
    expect(c.fallback).toBe(false)
  })

  it('绑定 Goal 上下文：标题与原因中出现真实目标名', () => {
    const c = buildStrategyCandidate(diag('LOW_APPLICATION'), [], {
      goal: { id: 7, title: '7 天掌握 JVM GC', status: 'ACTIVE' } as never,
      learningNodeTitle: 'GC 算法',
    })
    expect(c.action.reason).toContain('7 天掌握 JVM GC')
    expect(c.action.reason).toContain('GC 算法')
  })

  it('绑定真实 knowledgeId：动作带上知识点上下文，供路由 query 透传', () => {
    const c = buildStrategyCandidate(diag('LOW_EXPLANATION'), [], {
      knowledgeId: 42,
      knowledgeName: 'JVM GC',
    })
    expect(c.action.knowledgeId).toBe(42)
    expect(c.action.knowledgeName).toBe('JVM GC')
    expect(c.action.title).toContain('JVM GC')
  })

  it('无知识点上下文时不伪造：knowledgeId 为空也不编造知识点名', () => {
    const c = buildStrategyCandidate(diag('LOW_EXPLANATION'), [])
    expect(c.action.knowledgeId).toBeUndefined()
    expect(c.action.knowledgeName).toBeUndefined()
  })

  it('无诊断 / 无证据 → buildTopCandidate 返回 null（调用方维持既有行为）', () => {
    const empty: EvidenceAggregate = aggregateEvidence([], NOW)
    expect(buildTopCandidate(empty, [], [])).toBeNull()

    const agg = aggregateEvidence(many('RECALL', 90), NOW)
    expect(buildTopCandidate(agg, [], [])).toBeNull() // 有证据但无诊断
  })

  it('有诊断 → 返回优先级最高的候选', () => {
    const agg = aggregateEvidence([...many('EXPLANATION', 30), ...many('APPLICATION', 30)], NOW)
    const diagnoses = diagnoseFromEvidence(agg)
    const c = buildTopCandidate(agg, diagnoses, [app('coding')])
    expect(c!.diagnosis.type).toBe('LOW_APPLICATION') // APPLICATION priority 85 > EXPLANATION 75
    expect(c!.strategy).toBe('APPLICATION')
  })
})
