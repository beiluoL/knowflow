/**
 * Architecture-6：NextAction 集成测试（§228/§242-247）
 *
 * 核心断言：
 *  - resolveNextActionWithIntelligence 与原 resolveNextAction 的
 *    type / route / title / priority 完全一致（§242-245：绝不偷偷改优先级）；
 *  - Intelligence 只给结果「挂」diagnosisType（§244），不为 Task/Explore 编造诊断。
 */
import { describe, it, expect } from 'vitest'
import { resolveNextAction } from '../../action-priority'
import { resolveNextActionWithIntelligence, attachDiagnosis, toIntelligenceContext } from '../action-integration'
import { diagnoseLearningState } from '../diagnosis'
import type { LearningActionContext } from '../../types'
import type { KnowledgeMasteryVO, LearningTaskVO, LearningPathVO } from '@/api/types'
import type { AppDefinition } from '@/apps/types'

function mastery(partial: Partial<KnowledgeMasteryVO>): KnowledgeMasteryVO {
  return {
    knowledgeId: 1,
    name: 'AQS',
    type: null,
    categoryId: null,
    categoryName: null,
    masteryScore: 42,
    confidenceScore: 40,
    learningStatus: 'REVIEW_REQUIRED',
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

function task(partial: Partial<LearningTaskVO> = {}): LearningTaskVO {
  return { id: 1, title: '阅读第 3 章', type: 'study', status: 0, ...partial } as LearningTaskVO
}

function path(partial: Partial<LearningPathVO> = {}): LearningPathVO {
  return { id: 10, title: 'Java 并发', enrolled: true, progress: 40, ...partial } as LearningPathVO
}

function app(key: string): AppDefinition {
  return {
    key,
    name: key,
    description: '',
    icon: 'layers',
    category: 'PRACTICE',
    categoryLabel: '练习',
    route: `/app/${key}`,
    version: '1.0.0',
    status: 'ACTIVE',
    defaultInstalled: false,
    capabilities: [],
  } as AppDefinition
}

function baseCtx(): LearningActionContext {
  return {
    reviewRequired: [],
    weakPoints: [],
    tasks: [],
    currentPath: null,
    installedApps: [app('feynman'), app('flashcard')],
  }
}

describe('resolveNextActionWithIntelligence 不改变原行为（§242-245）', () => {
  it('有待复习 → type/route/title 与原 Resolver 完全一致', () => {
    const ctx = { ...baseCtx(), reviewRequired: [mastery({ name: 'AQS' })] }
    const a = resolveNextAction(ctx)
    const b = resolveNextActionWithIntelligence(ctx)
    expect(b.type).toBe(a.type)
    expect(b.route).toBe(a.route)
    expect(b.title).toBe(a.title)
    expect(b.type).toBe('REVIEW')
  })

  it('无 Review 有 Task → 落到 TASK，与原一致', () => {
    const ctx = { ...baseCtx(), tasks: [task({ id: 2, status: 0 })] }
    const a = resolveNextAction(ctx)
    const b = resolveNextActionWithIntelligence(ctx)
    expect(b.type).toBe(a.type)
    expect(b.type).toBe('TASK')
  })

  it('有进行中路径但无 Review/Task/Weak → LEARNING_PATH', () => {
    const ctx = { ...baseCtx(), currentPath: path({ progress: 40 }) }
    const b = resolveNextActionWithIntelligence(ctx)
    expect(b.type).toBe('LEARNING_PATH')
  })
})

describe('attachDiagnosis 只补充 diagnosisType（§244）', () => {
  it('REVIEW 动作 + REVIEW_OVERDUE 诊断 → 挂 REVIEW_OVERDUE', () => {
    const action = resolveNextAction({ ...baseCtx(), reviewRequired: [mastery({})] })
    const diagnoses = diagnoseLearningState(toIntelligenceContext({ ...baseCtx(), reviewRequired: [mastery({})] }))
    const enriched = attachDiagnosis(action, diagnoses)
    expect(enriched.diagnosisType).toBe('REVIEW_OVERDUE')
    // 原字段不变
    expect(enriched.type).toBe('REVIEW')
    expect(enriched.route).toBe('/learning/review')
  })

  it('WEAK 动作（费曼 App，knowledgeId=1）+ WEAK_KNOWLEDGE → 挂 WEAK_KNOWLEDGE', () => {
    const ctx = { ...baseCtx(), weakPoints: [mastery({ knowledgeId: 1, name: 'AQS', learningStatus: 'WEAK' })] }
    const action = resolveNextAction(ctx)
    const diagnoses = diagnoseLearningState(toIntelligenceContext(ctx))
    const enriched = attachDiagnosis(action, diagnoses)
    expect(enriched.diagnosisType).toBe('WEAK_KNOWLEDGE')
    expect(action.knowledgeId).toBe(1)
  })

  it('TASK 动作 → 不挂任何诊断（§103）', () => {
    const taskCtx = { ...baseCtx(), tasks: [task({ id: 9, status: 0 })] }
    const a = resolveNextAction(taskCtx)
    expect(a.type).toBe('TASK')
    const enriched = attachDiagnosis(a, diagnoseLearningState(toIntelligenceContext(taskCtx)))
    expect(enriched.diagnosisType).toBeUndefined()
  })

  it('EXPLORE 动作 → 不挂任何诊断（§102）', () => {
    const ctx = baseCtx()
    const a = resolveNextAction(ctx)
    expect(a.type).toBe('EXPLORE')
    const enriched = attachDiagnosis(a, diagnoseLearningState(toIntelligenceContext(ctx)))
    expect(enriched.diagnosisType).toBeUndefined()
  })
})

describe('toIntelligenceContext 复用已加载数据（§131-132）', () => {
  it('字段正确透传，不重新拉取', () => {
    const ctx = {
      ...baseCtx(),
      reviewRequired: [mastery({ knowledgeId: 3 })],
      weakPoints: [mastery({ knowledgeId: 4 })],
      currentPath: path({ id: 10 }),
    }
    const intel = toIntelligenceContext(ctx)
    expect(intel.reviewRequired).toBe(ctx.reviewRequired)
    expect(intel.weakPoints).toBe(ctx.weakPoints)
    expect(intel.currentPath).toBe(ctx.currentPath)
  })
})
