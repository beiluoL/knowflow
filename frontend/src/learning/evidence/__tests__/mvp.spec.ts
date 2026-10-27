/**
 * Architecture-4.1：JVM GC MVP 端到端（§31）
 *
 * 要证明的那件事：
 *   **一次真实学习行为 → Evidence → Diagnosis → Strategy → NextAction 真的会变。**
 *
 * 四步：
 *   A 无 Evidence                → 下一步 = 目标路线（既有行为，§36 不变）
 *   B 提交费曼（四维 56 分）      → EXPLANATION Evidence → LOW_EXPLANATION → 下一步 = 费曼表达
 *   C 再提交一次场景（75 分）     → APPLICATION Evidence → LOW_APPLICATION 优先级更高 → 下一步 = 场景练习
 *   D 场景连做 3 次（100 分）     → APPLICATION 达标，LOW_APPLICATION 消失     → 下一步回到费曼表达
 *
 * 同时守护 §36：ctx 不含 evidence 时，resolveNextAction 的行为必须与 4.1 之前完全一致。
 */
import { describe, it, expect } from 'vitest'
import type { LearningEventVO, LearningGoalVO, LearningPathVO } from '@/api/types'
import { resolveNextAction } from '../../action-priority'
import { resolveActionRoute } from '../../route'
import { resolveEvidenceBatch } from '../resolver'
import { aggregateEvidence } from '../aggregate'
import { diagnoseFromEvidence } from '../diagnosis'
import { buildTopCandidate } from '../action-candidate'
import type { LearningActionContext, NextLearningAction } from '../../types'
import type { AppDefinition } from '@/apps/types'

const NOW = new Date('2026-09-06T12:00:00.000Z').getTime()
const at = (daysAgo: number) => new Date(NOW - daysAgo * 86_400_000).toISOString()

const goal: LearningGoalVO = {
  id: 7,
  title: '7 天掌握 JVM GC',
  status: 'ACTIVE',
  pathId: 101,
} as LearningGoalVO

const gcPath: LearningPathVO = {
  id: 101,
  title: 'JVM GC 深入',
  enrolled: true,
  progress: 0,
  chapterCount: 8,
  completedChapters: 0,
} as LearningPathVO

const apps: AppDefinition[] = [{ key: 'coding', name: '代码练习', route: '/learning/code-practice' } as AppDefinition]

/** 事件构造器：模拟后端真实落库的 metadata */
let seq = 0
function event(eventType: string, metadata: Record<string, unknown>, daysAgo = 0): LearningEventVO {
  seq += 1
  return {
    id: seq,
    userId: 1,
    eventType,
    resourceType: 'TEST',
    resourceId: 1,
    metadata: JSON.stringify(metadata),
    createTime: at(daysAgo),
  } as LearningEventVO
}

const FEYNMAN = event(
  'FEYNMAN_EXPRESSED',
  { topic: 'JVM 年轻代 GC', overall: 56, accuracy: 75, clarity: 25, completeness: 75, structure: 50 },
  3,
)

/** 跑完整条链：事件 → 证据 → 聚合 → 诊断 → 候选 → NextAction */
function run(events: LearningEventVO[]): { action: NextLearningAction; types: string[] } {
  const evidences = resolveEvidenceBatch(events)
  const agg = aggregateEvidence(evidences, NOW)
  const diagnoses = diagnoseFromEvidence(agg)
  const candidate = buildTopCandidate(agg, diagnoses, apps, { goal })
  const action = resolveNextAction({
    reviewRequired: [],
    weakPoints: [],
    tasks: [],
    currentPath: null,
    goal,
    goalPath: gcPath,
    installedApps: apps,
    evidence: candidate,
  })
  return { action, types: diagnoses.map((d) => d.type) }
}

describe('§31 JVM GC MVP：用户行为真的能改变下一步', () => {
  it('A · 无 Evidence 时，下一步 = 目标路线（既有优先级不变）', () => {
    const { action } = run([])
    expect(action.type).toBe('LEARNING_PATH')
    expect(action.route).toBe('/learning/path/101')
    expect(action.reason).toContain('7 天掌握 JVM GC')
  })

  it('B · 费曼 56 分 → LOW_EXPLANATION → 下一步 = 费曼表达（不再只是继续看路线）', () => {
    const { action, types } = run([FEYNMAN])
    expect(types).toContain('LOW_EXPLANATION')
    expect(action.type).toBe('EVIDENCE_PRACTICE')
    expect(resolveActionRoute(action)).toBe('/learning/feynman')
  })

  it('C · 再补一次场景 75 分 → LOW_APPLICATION 优先级更高 → 下一步 = 场景练习', () => {
    const scenario = event('SCENARIO_COMPLETED', { scenarioId: 9, scenarioTitle: 'GC 调优', score: 75, matched: 3, total: 4 }, 1)
    const { action, types } = run([FEYNMAN, scenario])
    expect(types).toContain('LOW_APPLICATION')
    expect(types.indexOf('LOW_APPLICATION')).toBeLessThan(types.indexOf('LOW_EXPLANATION'))
    expect(action.type).toBe('EVIDENCE_PRACTICE')
    expect(resolveActionRoute(action)).toBe('/learning/scenario')
  })

  it('D · 场景连做 3 次满分 → APPLICATION 达标，LOW_APPLICATION 消失 → 下一步回到费曼', () => {
    const more = [1, 2, 3].map((i) =>
      event('SCENARIO_COMPLETED', { scenarioId: 9, scenarioTitle: 'GC 调优', score: 100, matched: 4, total: 4 }, i - 1),
    )
    const { action, types } = run([FEYNMAN, ...more])
    expect(types).not.toContain('LOW_APPLICATION')
    expect(types).toContain('LOW_EXPLANATION')
    expect(resolveActionRoute(action)).toBe('/learning/feynman')
  })
})

describe('§36 回归：不注入 evidence 时既有行为完全不变', () => {
  it('同一上下文，evidence 为 undefined 时仍返回目标路线动作', () => {
    const base: LearningActionContext = {
      reviewRequired: [],
      weakPoints: [],
      tasks: [],
      currentPath: null,
      goal,
      goalPath: gcPath,
      installedApps: apps,
    }
    const withoutEvidence = resolveNextAction(base)
    expect(withoutEvidence.type).toBe('LEARNING_PATH')

    const withNull = resolveNextAction({ ...base, evidence: null })
    expect(withNull).toEqual(withoutEvidence)
  })

  it('完整链路可重放：把事件按任意顺序喂入，结论一致（Evidence 是可重算的派生结果）', () => {
    const scenario = event('SCENARIO_COMPLETED', { scenarioId: 9, score: 75, matched: 3, total: 4 }, 1)
    const a = run([FEYNMAN, scenario])
    const b = run([scenario, FEYNMAN])
    expect(resolveActionRoute(a.action)).toBe(resolveActionRoute(b.action))
    expect(a.types).toEqual(b.types)
  })
})
