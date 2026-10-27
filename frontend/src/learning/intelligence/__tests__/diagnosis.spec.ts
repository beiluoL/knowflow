/**
 * Architecture-6：Diagnosis 单元测试（§225-227）
 *
 * 覆盖：
 *  - REVIEW_OVERDUE / WEAK_KNOWLEDGE / HEALTHY_PROGRESS（真实数据，必然触发）
 *  - REPEATED_MISTAKE / LOW_ACCURACY / PATH_STALLED / INACTIVE_LEARNING（仅当真实数据可得才触发，§68/§148/§239）
 *  - Evidence 必须带真实数据（§26-29/§166-167）：缺数据不显示、不伪造数字
 *  - 优先级排序（§43）
 */
import { describe, it, expect } from 'vitest'
import { diagnoseLearningState } from '../diagnosis'
import type { LearningIntelligenceContext, LearningDiagnosis } from '../types'
import type { KnowledgeMasteryVO, LearningPathVO, LearningEventVO } from '@/api/types'

function mastery(partial: Partial<KnowledgeMasteryVO>): KnowledgeMasteryVO {
  return {
    knowledgeId: 1,
    name: 'AQS',
    type: null,
    categoryId: null,
    categoryName: null,
    masteryScore: 42,
    confidenceScore: 40,
    learningStatus: 'WEAK',
    forgettingRisk: 0.6,
    weaknessTypes: [],
    reason: null,
    lastLearnedAt: null,
    lastReviewedAt: null,
    lastAssessedAt: null,
    nextReviewAt: null,
    ...partial,
  }
}

function baseCtx(partial: Partial<LearningIntelligenceContext> = {}): LearningIntelligenceContext {
  return {
    reviewRequired: [],
    weakPoints: [],
    tasks: [],
    currentPath: null,
    ...partial,
  }
}

function typesOf(ds: LearningDiagnosis[]): string[] {
  return ds.map((d) => d.type)
}

describe('REVIEW_OVERDUE（真实数据 §19）', () => {
  it('有待复习 → 推导 REVIEW_OVERDUE，证据为 REVIEW_DUE', () => {
    const ds = diagnoseLearningState(baseCtx({ reviewRequired: [mastery({ name: 'AQS', learningStatus: 'REVIEW_REQUIRED' })] }))
    const d = ds.find((x) => x.type === 'REVIEW_OVERDUE')
    expect(d).toBeDefined()
    expect(d!.evidence.some((e) => e.type === 'REVIEW_DUE')).toBe(true)
    expect(d!.severity).toBe('HIGH')
    expect(d!.priority).toBe(100)
  })

  it('多个待复习 → 标题含数量', () => {
    const ds = diagnoseLearningState(
      baseCtx({
        reviewRequired: [
          mastery({ knowledgeId: 1, name: 'AQS', learningStatus: 'REVIEW_REQUIRED' }),
          mastery({ knowledgeId: 2, name: 'CAS', learningStatus: 'REVIEW_REQUIRED' }),
        ],
      }),
    )
    const d = ds.find((x) => x.type === 'REVIEW_OVERDUE')!
    expect(d.title).toContain('2 个')
  })
})

describe('WEAK_KNOWLEDGE（真实数据 §63-66）', () => {
  it('有薄弱知识点 → 推导 WEAK_KNOWLEDGE，证据含真实掌握度', () => {
    const ds = diagnoseLearningState(baseCtx({ weakPoints: [mastery({ masteryScore: 37, weaknessTypes: ['ERROR_RATE'] })] }))
    const d = ds.find((x) => x.type === 'WEAK_KNOWLEDGE')
    expect(d).toBeDefined()
    expect(d!.evidence.some((e) => e.type === 'WEAK_MASTERY' && e.value === 37)).toBe(true)
    expect(d!.reason).toContain('错误率')
    expect(d!.severity).toBe('MEDIUM')
    expect(d!.priority).toBe(80)
  })

  it('掌握度数字来自真实数据，不伪造（§28）', () => {
    const ds = diagnoseLearningState(baseCtx({ weakPoints: [mastery({ name: 'CAS', masteryScore: 51 })] }))
    const d = ds.find((x) => x.type === 'WEAK_KNOWLEDGE')!
    expect(d.evidence.find((e) => e.type === 'WEAK_MASTERY')!.value).toBe(51)
    expect(d.reason).not.toMatch(/23%|42%/) // 不应出现与真实 51% 不符的伪造数字
  })

  it('无薄弱知识点 → 不推导 WEAK_KNOWLEDGE、无对应证据', () => {
    const ds = diagnoseLearningState(baseCtx())
    expect(typesOf(ds)).not.toContain('WEAK_KNOWLEDGE')
  })
})

describe('HEALTHY_PROGRESS（§25）', () => {
  it('无任何问题 → 仅 HEALTHY_PROGRESS，不混入其他诊断', () => {
    const ds = diagnoseLearningState(baseCtx())
    expect(ds).toHaveLength(1)
    expect(ds[0].type).toBe('HEALTHY_PROGRESS')
    expect(ds[0].priority).toBe(0)
  })
})

describe('可选诊断：仅当真实数据可得才触发（§68/§148/§239）', () => {
  it('未提供 repeatedMistakeKnowledgeIds → 不推导 REPEATED_MISTAKE（不伪造连续错 3 次）', () => {
    const ds = diagnoseLearningState(baseCtx({ weakPoints: [mastery({ knowledgeId: 5 })] }))
    expect(typesOf(ds)).not.toContain('REPEATED_MISTAKE')
  })

  it('提供 repeatedMistakeKnowledgeIds → 推导 REPEATED_MISTAKE', () => {
    const ds = diagnoseLearningState(baseCtx({ weakPoints: [mastery({ knowledgeId: 5 })], repeatedMistakeKnowledgeIds: [5] }))
    expect(typesOf(ds)).toContain('REPEATED_MISTAKE')
    expect(ds.find((x) => x.type === 'REPEATED_MISTAKE')!.knowledgeId).toBe(5)
  })

  it('未提供 lowAccuracyKnowledgeIds → 不推导 LOW_ACCURACY', () => {
    const ds = diagnoseLearningState(baseCtx())
    expect(typesOf(ds)).not.toContain('LOW_ACCURACY')
  })

  it('提供 lowAccuracyKnowledgeIds → 推导 LOW_ACCURACY', () => {
    const ds = diagnoseLearningState(baseCtx({ lowAccuracyKnowledgeIds: [7] }))
    expect(typesOf(ds)).toContain('LOW_ACCURACY')
  })

  it('未提供 stalledPathIds → 不推导 PATH_STALLED', () => {
    const path: LearningPathVO = { id: 3, title: 'Java 并发', enrolled: true, progress: 40 } as LearningPathVO
    const ds = diagnoseLearningState(baseCtx({ currentPath: path }))
    expect(typesOf(ds)).not.toContain('PATH_STALLED')
  })

  it('提供 stalledPathIds 且命中当前路径 → 推导 PATH_STALLED', () => {
    const path: LearningPathVO = { id: 3, title: 'Java 并发', enrolled: true, progress: 40 } as LearningPathVO
    const ds = diagnoseLearningState(baseCtx({ currentPath: path, stalledPathIds: [3] }))
    expect(typesOf(ds)).toContain('PATH_STALLED')
  })

  it('未提供 recentEvents → 不推导 INACTIVE_LEARNING（不制造焦虑，§74-75）', () => {
    const ds = diagnoseLearningState(baseCtx())
    expect(typesOf(ds)).not.toContain('INACTIVE_LEARNING')
  })

  it('最近 1 天有学习 → 不推导 INACTIVE_LEARNING', () => {
    const events: LearningEventVO[] = [{ id: 1, userId: 1, eventType: 'QUIZ', createTime: new Date().toISOString() } as LearningEventVO]
    const ds = diagnoseLearningState(baseCtx({ recentEvents: events }))
    expect(typesOf(ds)).not.toContain('INACTIVE_LEARNING')
  })

  it('最近 10 天无学习 → 推导 INACTIVE_LEARNING（温和提示，§76）', () => {
    const events: LearningEventVO[] = [
      { id: 1, userId: 1, eventType: 'QUIZ', createTime: new Date(Date.now() - 10 * 86400000).toISOString() } as LearningEventVO,
    ]
    const ds = diagnoseLearningState(baseCtx({ recentEvents: events }))
    expect(typesOf(ds)).toContain('INACTIVE_LEARNING')
    expect(ds.find((x) => x.type === 'INACTIVE_LEARNING')!.reason).not.toContain('退步')
  })
})

describe('优先级排序（§43/§158）', () => {
  it('review + weak 同时存在 → REVIEW_OVERDUE 排第一', () => {
    const ds = diagnoseLearningState(
      baseCtx({
        reviewRequired: [mastery({ learningStatus: 'REVIEW_REQUIRED' })],
        weakPoints: [mastery({})],
      }),
    )
    expect(ds[0].type).toBe('REVIEW_OVERDUE')
  })

  it('REPEATED_MISTAKE 优先级高于 WEAK_KNOWLEDGE', () => {
    const ds = diagnoseLearningState(
      baseCtx({ weakPoints: [mastery({ knowledgeId: 5 })], repeatedMistakeKnowledgeIds: [5] }),
    )
    const repeated = ds.findIndex((x) => x.type === 'REPEATED_MISTAKE')
    const weak = ds.findIndex((x) => x.type === 'WEAK_KNOWLEDGE')
    expect(repeated).toBeGreaterThanOrEqual(0)
    expect(weak).toBeGreaterThanOrEqual(0)
    expect(repeated).toBeLessThan(weak)
  })
})

describe('Evidence 真实性原则（§26-29/§166-167）', () => {
  it('HEALTHY 无证据时不展示空证据行', () => {
    const ds = diagnoseLearningState(baseCtx())
    expect(ds[0].evidence).toHaveLength(0)
  })
})
